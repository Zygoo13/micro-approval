package com.microapproval.api.service;

import com.microapproval.api.dto.CreatePersonalSessionRequest;
import com.microapproval.api.dto.DecisionVoteRequest;
import com.microapproval.api.dto.MicroDecisionResponse;
import com.microapproval.api.dto.PersonalSessionResponse;
import com.microapproval.api.entity.*;
import com.microapproval.api.exception.ForbiddenOperationException;
import com.microapproval.api.exception.InvalidOperationException;
import com.microapproval.api.exception.ResourceNotFoundException;
import com.microapproval.api.exception.ConflictException;
import com.microapproval.api.repository.MicroDecisionRepository;
import com.microapproval.api.repository.ReviewSessionRepository;
import com.microapproval.api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PersonalSessionService {

    private final ReviewSessionRepository sessionRepository;
    private final MicroDecisionRepository decisionRepository;
    private final UserRepository userRepository;
    private final ReviewAnalysisPipeline reviewAnalysisPipeline;

    // TẠO PHIÊN CÁ NHÂN
    @Transactional
    public PersonalSessionResponse createSession(CreatePersonalSessionRequest request, String userEmail) {
        // Tìm user dựa trên email truyền từ Controller xuống [5]
        User currentUser = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng"));

        // Tạo mới Review Session sử dụng Enum cho các trạng thái và chế độ [5]
        ReviewSession session = ReviewSession.builder()
                .title(request.getTitle())
                .workspaceType(WorkspaceType.PERSONAL)
                .mode(request.getMode())
                .rawContent(request.getRawContent())
                .promptContent(request.getPromptContent())
                .submittedBy(currentUser)
                .status(SessionStatus.PENDING)
                .aiTokenUsed(0)
                .aiAnalysisStatus(AiAnalysisStatus.NOT_REQUESTED)
                .build();

        session = sessionRepository.save(session);

        List<MicroDecision> decisions = reviewAnalysisPipeline.analyze(session, currentUser);
        if (decisions.isEmpty()) {
            session.setStatus(SessionStatus.APPROVED);
            session.setCompletedAt(LocalDateTime.now());
            sessionRepository.save(session);
        } else {
            decisionRepository.saveAll(decisions);
        }

        return toResponseFromEntities(session, decisions);
    }

    // LẤY DANH SÁCH PHIÊN CÁ NHÂN (Quy tắc bảo mật số 10)
    @Transactional(readOnly = true)
    public List<PersonalSessionResponse> getMyPersonalSessions(String userEmail) {
        User currentUser = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng"));

        // Lọc nghiêm ngặt: Chỉ lấy của chính user đó và thuộc không gian PERSONAL [3]
        return sessionRepository.findAllBySubmittedByIdAndWorkspaceTypeOrderByCreatedAtDesc(
                currentUser.getId(), WorkspaceType.PERSONAL
        ).stream().map(session -> toResponse(session, List.of())).toList();
    }

    // CẬP NHẬT QUYẾT ĐỊNH THẺ CÁ NHÂN (VOTE)
    @Transactional
    public MicroDecisionResponse voteDecision(String decisionId, DecisionVoteRequest request, String userEmail) {
        User currentUser = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng"));

        MicroDecision decision = decisionRepository.findById(decisionId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thẻ quyết định"));

        ReviewSession session = decision.getSession();

        // BR-04: Xác thực quyền riêng tư tuyệt đối [3, 4]
        if (session.getWorkspaceType() != WorkspaceType.PERSONAL ||
                !session.getSubmittedBy().getId().equals(currentUser.getId())) {
            throw new ForbiddenOperationException("Bạn không có quyền truy cập thẻ quyết định này");
        }

        // BR-18: Xác thực trạng thái thẻ - Không thể sửa thẻ đã xử lý [4]
        if (decision.getHumanDecision() != DecisionStatus.PENDING) {
            throw new ConflictException("Thẻ này đã được xử lý, không thể thay đổi quyết định (BR-18)");
        }

        if (request.getHumanDecision() == DecisionStatus.PENDING) {
            throw new InvalidOperationException("Trạng thái vote không hợp lệ");
        }

        // Cập nhật quyết định cho thẻ
        decision.setHumanDecision(request.getHumanDecision());
        decision.setReviewerNote(request.getReviewerNote());
        decision.setDecidedBy(currentUser);
        decision.setDecidedAt(LocalDateTime.now());

        decisionRepository.save(decision);

        // Kiểm tra và tự động cập nhật trạng thái của toàn phiên
        checkAndUpdateSessionStatus(session);

        return MicroDecisionResponse.from(decision);
    }

    // KIỂM TRA TRẠNG THÁI PHIÊN SAU KHI VOTE
    private void checkAndUpdateSessionStatus(ReviewSession session) {
        List<MicroDecision> allDecisions = decisionRepository.findAllBySessionIdOrderByDisplayOrderAsc(session.getId());

        boolean isAllProcessed = true;
        boolean hasRejected = false;

        for (MicroDecision d : allDecisions) {
            if (d.getHumanDecision() == DecisionStatus.PENDING) {
                isAllProcessed = false;
                break;
            }
            if (d.getHumanDecision() == DecisionStatus.REJECTED) {
                hasRejected = true;
            }
        }

        // Nếu tất cả thẻ đã được vote xong
        if (isAllProcessed) {
            // BR-03, BR-05: Có >= 1 thẻ REJECTED -> Session = REJECTED, ngược lại APPROVED [4, 6]
            session.setStatus(hasRejected ? SessionStatus.REJECTED : SessionStatus.APPROVED);
            session.setCompletedAt(LocalDateTime.now());
            sessionRepository.save(session);
        }
    }

    @Transactional(readOnly = true)
    public PersonalSessionResponse getSessionDetail(String sessionId, String userEmail) {
        User currentUser = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng"));

        // Tìm kiếm và ép điều kiện sở hữu + không gian Personal để chống xem chéo dữ liệu
        ReviewSession session = sessionRepository.findByIdAndSubmittedByIdAndWorkspaceType(
                sessionId, currentUser.getId(), WorkspaceType.PERSONAL
        ).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy phiên kiểm duyệt cá nhân"));

        // Truy vấn danh sách các thẻ quyết định thuộc về phiên này
        List<MicroDecision> decisions = decisionRepository.findBySessionIdOrderByDisplayOrderAsc(sessionId);
        return toResponse(session, decisions.stream().map(MicroDecisionResponse::from).toList());
    }

    @Transactional
    public void deleteSession(String sessionId, String userEmail) {
        User currentUser = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng"));
        ReviewSession session = sessionRepository.findByIdAndSubmittedByIdAndWorkspaceType(
                sessionId, currentUser.getId(), WorkspaceType.PERSONAL
        ).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy phiên kiểm duyệt cá nhân"));
        sessionRepository.delete(session);
    }

    private PersonalSessionResponse toResponseFromEntities(ReviewSession session, List<MicroDecision> decisions) {
        return toResponse(session, decisions.stream().map(MicroDecisionResponse::from).toList());
    }

    private PersonalSessionResponse toResponse(ReviewSession session, List<MicroDecisionResponse> decisions) {
        return PersonalSessionResponse.from(session, decisions);
    }

}
