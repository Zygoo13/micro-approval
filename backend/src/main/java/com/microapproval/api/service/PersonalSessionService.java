package com.microapproval.api.service;

import com.microapproval.api.dto.CreatePersonalSessionRequest;
import com.microapproval.api.dto.DecisionVoteRequest;
import com.microapproval.api.entity.*;
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

    // TẠO PHIÊN CÁ NHÂN
    @Transactional
    public ReviewSession createSession(CreatePersonalSessionRequest request, String userEmail) {
        // Tìm user dựa trên email truyền từ Controller xuống [5]
        User currentUser = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy User"));

        // Tạo mới Review Session sử dụng Enum cho các trạng thái và chế độ [5]
        ReviewSession session = ReviewSession.builder()
                .title(request.getTitle())
                .workspaceType(WorkspaceType.PERSONAL)
                .mode(request.getMode())
                .rawContent(request.getRawContent())
                .promptContent(request.getPromptContent())
                .submittedBy(currentUser)
                .status(SessionStatus.PENDING)
                .build();

        session = sessionRepository.save(session);

        // Chạy Mock Engine để sinh thẻ rủi ro
        generateMockDecisionCards(session);

        return session;
    }

    // LẤY DANH SÁCH PHIÊN CÁ NHÂN (Quy tắc bảo mật số 10)
    public List<ReviewSession> getMyPersonalSessions(String userEmail) {
        User currentUser = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy User"));

        // Lọc nghiêm ngặt: Chỉ lấy của chính user đó và thuộc không gian PERSONAL [3]
        return sessionRepository.findAllBySubmittedByIdAndWorkspaceTypeOrderByCreatedAtDesc(
                currentUser.getId(), WorkspaceType.PERSONAL
        );
    }

    // CẬP NHẬT QUYẾT ĐỊNH THẺ CÁ NHÂN (VOTE)
    @Transactional
    public MicroDecision voteDecision(String decisionId, DecisionVoteRequest request, String userEmail) {
        User currentUser = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy User"));

        MicroDecision decision = decisionRepository.findById(decisionId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy Thẻ quyết định"));

        ReviewSession session = decision.getSession();

        // BR-04: Xác thực quyền riêng tư tuyệt đối [3, 4]
        if (session.getWorkspaceType() != WorkspaceType.PERSONAL ||
                !session.getSubmittedBy().getId().equals(currentUser.getId())) {
            throw new RuntimeException("Bạn không có quyền truy cập thẻ quyết định này");
        }

        // BR-18: Xác thực trạng thái thẻ - Không thể sửa thẻ đã xử lý [4]
        if (decision.getHumanDecision() != DecisionStatus.PENDING) {
            throw new RuntimeException("Thẻ này đã được xử lý, không thể thay đổi quyết định (BR-18)");
        }

        if (request.getHumanDecision() == DecisionStatus.PENDING) {
            throw new RuntimeException("Trạng thái vote không hợp lệ");
        }

        // Cập nhật quyết định cho thẻ
        decision.setHumanDecision(request.getHumanDecision());
        decision.setReviewerNote(request.getReviewerNote());
        decision.setDecidedBy(currentUser);
        decision.setDecidedAt(LocalDateTime.now());

        decisionRepository.save(decision);

        // Kiểm tra và tự động cập nhật trạng thái của toàn phiên
        checkAndUpdateSessionStatus(session);

        return decision;
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

    // --- MOCK LOGIC TẠO THẺ ---
    private void generateMockDecisionCards(ReviewSession session) {
        MicroDecision card1 = MicroDecision.builder()
                .session(session)
                .engineType(EngineType.RULE_BASED)
                .riskCategory(RiskCategory.SECURITY)
                .riskLevel(RiskLevel.HIGH)
                .codeSnippet("SELECT * FROM users WHERE username = '" + " + userInput + " + "';")
                .questionText("Phát hiện lỗi nối chuỗi SQL (SQL Injection). Bạn có chắc chắn đoạn code này đã được sanitize an toàn chưa?")
                .humanDecision(DecisionStatus.PENDING)
                .displayOrder(1)
                .build();

        MicroDecision card2 = MicroDecision.builder()
                .session(session)
                .engineType(EngineType.RULE_BASED)
                .riskCategory(RiskCategory.DEPENDENCY)
                .riskLevel(RiskLevel.MEDIUM)
                .codeSnippet("import fast-json-parser;")
                .questionText("Thư viện fast-json-parser mới được thêm vào. Bạn đã kiểm tra các lỗ hổng CVE của thư viện này chưa?")
                .humanDecision(DecisionStatus.PENDING)
                .displayOrder(2)
                .build();

        decisionRepository.saveAll(List.of(card1, card2));
    }
    // XEM CHI TIẾT PHIÊN CÁ NHÂN (Xác thực quyền sở hữu nghiêm ngặt)
    public ReviewSession getSessionDetail(String sessionId, String userEmail) {
        User currentUser = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy User"));

        // Tìm kiếm và ép điều kiện sở hữu + không gian Personal để chống xem chéo dữ liệu
        ReviewSession session = sessionRepository.findByIdAndSubmittedByIdAndWorkspaceType(
                sessionId, currentUser.getId(), WorkspaceType.PERSONAL
        ).orElseThrow(() -> new RuntimeException("Không tìm thấy phiên kiểm duyệt cá nhân hoặc bạn không có quyền truy cập"));

        // Truy vấn danh sách các thẻ quyết định thuộc về phiên này
        List<MicroDecision> decisions = decisionRepository.findBySessionIdOrderByDisplayOrderAsc(sessionId);
        session.setDecisions(decisions);

        return session;
    }
}