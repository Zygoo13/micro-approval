package com.microapproval.api.service;

import com.microapproval.api.dto.CreateSharedReviewSessionRequest;
import com.microapproval.api.dto.MicroDecisionResponse;
import com.microapproval.api.dto.SharedReviewSessionDetailResponse;
import com.microapproval.api.dto.SharedReviewSessionSummaryResponse;
import com.microapproval.api.entity.AiAnalysisStatus;
import com.microapproval.api.entity.MicroDecision;
import com.microapproval.api.entity.ReviewSession;
import com.microapproval.api.entity.SessionStatus;
import com.microapproval.api.entity.User;
import com.microapproval.api.entity.WorkspaceMember;
import com.microapproval.api.entity.WorkspaceType;
import com.microapproval.api.exception.ResourceNotFoundException;
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
public class SharedReviewSessionService {

    private final ReviewSessionRepository sessionRepository;
    private final MicroDecisionRepository decisionRepository;
    private final UserRepository userRepository;
    private final WorkspaceAccessService workspaceAccessService;
    private final ReviewAnalysisPipeline reviewAnalysisPipeline;

    @Transactional
    public SharedReviewSessionDetailResponse createSession(
            String workspaceId,
            CreateSharedReviewSessionRequest request,
            String callerEmail
    ) {
        User caller = requireUser(callerEmail);
        WorkspaceMember membership = workspaceAccessService
                .requireSharedSessionCreator(workspaceId, caller.getId());

        ReviewSession session = ReviewSession.builder()
                .title(request.title())
                .workspaceType(WorkspaceType.SHARED)
                .workspace(membership.getWorkspace())
                .mode(request.mode())
                .rawContent(request.rawContent())
                .promptContent(request.promptContent())
                .submittedBy(caller)
                .status(SessionStatus.PENDING)
                .aiTokenUsed(0)
                .aiAnalysisStatus(AiAnalysisStatus.NOT_REQUESTED)
                .build();
        session = sessionRepository.save(session);

        List<MicroDecision> decisions = reviewAnalysisPipeline.analyze(session, caller);
        if (decisions.isEmpty()) {
            session.setStatus(SessionStatus.APPROVED);
            session.setCompletedAt(LocalDateTime.now());
            sessionRepository.save(session);
        } else {
            decisionRepository.saveAll(decisions);
        }

        return detailResponse(session, decisions);
    }

    @Transactional(readOnly = true)
    public List<SharedReviewSessionSummaryResponse> getSessions(
            String workspaceId,
            String callerEmail
    ) {
        User caller = requireUser(callerEmail);
        workspaceAccessService.requireActiveMembership(workspaceId, caller.getId());
        return sessionRepository
                .findAllWithSubmitterByWorkspaceIdAndType(workspaceId, WorkspaceType.SHARED)
                .stream()
                .map(SharedReviewSessionSummaryResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public SharedReviewSessionDetailResponse getSessionDetail(
            String workspaceId,
            String sessionId,
            String callerEmail
    ) {
        User caller = requireUser(callerEmail);
        workspaceAccessService.requireActiveMembership(workspaceId, caller.getId());
        ReviewSession session = sessionRepository
                .findWithSubmitterAndWorkspaceByIdAndWorkspaceIdAndType(
                        sessionId,
                        workspaceId,
                        WorkspaceType.SHARED
                )
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy Shared Review Session"
                ));
        return detailResponse(
                session,
                decisionRepository.findBySessionIdOrderByDisplayOrderAsc(sessionId)
        );
    }

    private SharedReviewSessionDetailResponse detailResponse(
            ReviewSession session,
            List<MicroDecision> decisions
    ) {
        return SharedReviewSessionDetailResponse.from(
                session,
                decisions.stream().map(MicroDecisionResponse::from).toList()
        );
    }

    private User requireUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng"));
    }
}
