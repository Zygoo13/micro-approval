package com.microapproval.api.service;

import com.microapproval.api.dto.CloseSharedReviewSessionRequest;
import com.microapproval.api.dto.RemoveSessionReviewerRequest;
import com.microapproval.api.dto.SessionVotingResponse;
import com.microapproval.api.dto.UpsertTeamVoteRequest;
import com.microapproval.api.entity.AiAnalysisStatus;
import com.microapproval.api.entity.AnalysisMode;
import com.microapproval.api.entity.EngineType;
import com.microapproval.api.entity.MembershipStatus;
import com.microapproval.api.entity.MicroDecision;
import com.microapproval.api.entity.ReviewSession;
import com.microapproval.api.entity.ReviewSessionReviewer;
import com.microapproval.api.entity.ReviewSessionReviewerStatus;
import com.microapproval.api.entity.RiskCategory;
import com.microapproval.api.entity.RiskLevel;
import com.microapproval.api.entity.SessionStatus;
import com.microapproval.api.entity.TeamReviewAuditEventType;
import com.microapproval.api.entity.TeamVoteDecision;
import com.microapproval.api.entity.User;
import com.microapproval.api.entity.Workspace;
import com.microapproval.api.entity.WorkspaceMember;
import com.microapproval.api.entity.WorkspaceRole;
import com.microapproval.api.entity.WorkspaceType;
import com.microapproval.api.exception.ConflictException;
import com.microapproval.api.repository.MicroDecisionRepository;
import com.microapproval.api.repository.ReviewSessionRepository;
import com.microapproval.api.repository.ReviewSessionReviewerRepository;
import com.microapproval.api.repository.TeamReviewAuditEventRepository;
import com.microapproval.api.repository.UserRepository;
import com.microapproval.api.repository.WorkspaceMemberRepository;
import com.microapproval.api.repository.WorkspaceRepository;
import com.microapproval.api.support.AbstractMySqlIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class SharedReviewSessionLifecycleConcurrencyIntegrationTest extends AbstractMySqlIntegrationTest {

    @Autowired private SharedReviewSessionLifecycleService lifecycleService;
    @Autowired private TeamVotingService votingService;
    @Autowired private ReviewSessionReviewerService reviewerService;
    @Autowired private TeamReviewAuditEventRepository auditRepository;
    @Autowired private ReviewSessionReviewerRepository reviewerRepository;
    @Autowired private MicroDecisionRepository cardRepository;
    @Autowired private ReviewSessionRepository sessionRepository;
    @Autowired private WorkspaceRepository workspaceRepository;
    @Autowired private WorkspaceMemberRepository memberRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private TransactionTemplate transactionTemplate;

    private String workspaceId;
    private String sessionId;
    private String cardId;
    private String reviewerAssignmentId;
    private String ownerEmail;
    private String reviewerEmail;
    private long reviewerVoteVersion;
    private List<String> userIds;

    @BeforeEach
    void createCommittedTerminalFixture() {
        transactionTemplate.executeWithoutResult(status -> {
            User owner = createUser("lifecycle-race-owner");
            User reviewer = createUser("lifecycle-race-reviewer");
            Workspace workspace = workspaceRepository.save(Workspace.builder()
                    .name("Lifecycle race")
                    .owner(owner)
                    .build());
            WorkspaceMember ownerMember = memberRepository.save(member(
                    workspace, owner, WorkspaceRole.OWNER
            ));
            WorkspaceMember reviewerMember = memberRepository.save(member(
                    workspace, reviewer, WorkspaceRole.REVIEWER
            ));
            ReviewSession session = sessionRepository.save(ReviewSession.builder()
                    .title("Lifecycle race session")
                    .workspaceType(WorkspaceType.SHARED)
                    .workspace(workspace)
                    .mode(AnalysisMode.RAW_SNIPPET)
                    .rawContent("return true;")
                    .submittedBy(owner)
                    .status(SessionStatus.PENDING)
                    .aiAnalysisStatus(AiAnalysisStatus.NOT_REQUESTED)
                    .build());
            MicroDecision card = cardRepository.save(MicroDecision.builder()
                    .session(session)
                    .engineType(EngineType.RULE_BASED)
                    .riskCategory(RiskCategory.SECURITY)
                    .riskLevel(RiskLevel.HIGH)
                    .questionText("Safe?")
                    .displayOrder(0)
                    .build());
            reviewerRepository.save(assignment(session, ownerMember, owner));
            ReviewSessionReviewer reviewerAssignment = reviewerRepository.save(
                    assignment(session, reviewerMember, owner)
            );

            workspaceId = workspace.getId();
            sessionId = session.getId();
            cardId = card.getId();
            reviewerAssignmentId = reviewerAssignment.getId();
            ownerEmail = owner.getEmail();
            reviewerEmail = reviewer.getEmail();
            userIds = List.of(owner.getId(), reviewer.getId());
        });
        votingService.upsertOwnVote(
                workspaceId, sessionId, cardId,
                new UpsertTeamVoteRequest(TeamVoteDecision.APPROVED, null, null),
                ownerEmail
        );
        SessionVotingResponse result = votingService.upsertOwnVote(
                workspaceId, sessionId, cardId,
                new UpsertTeamVoteRequest(TeamVoteDecision.APPROVED, null, null),
                reviewerEmail
        );
        reviewerVoteVersion = result.cards().getFirst().votes().stream()
                .filter(vote -> vote.reviewerAssignmentId().equals(reviewerAssignmentId))
                .findFirst().orElseThrow().version();
    }

    @AfterEach
    void removeCommittedFixture() {
        transactionTemplate.executeWithoutResult(status -> {
            auditRepository.deleteBySessionId(sessionId);
            workspaceRepository.deleteById(workspaceId);
            workspaceRepository.flush();
            userRepository.deleteAllById(userIds);
            userRepository.flush();
        });
    }

    @Test
    void concurrentCloseHasOneWinnerOneConflictAndOneAuditEvent() throws Exception {
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger conflicts = new AtomicInteger();

        runTogether(
                () -> close(successes, conflicts),
                () -> close(successes, conflicts)
        );

        assertThat(successes).hasValue(1);
        assertThat(conflicts).hasValue(1);
        assertThat(sessionRepository.findById(sessionId).orElseThrow().getClosedAt()).isNotNull();
        assertThat(auditRepository.countBySessionIdAndEventType(
                sessionId, TeamReviewAuditEventType.SESSION_CLOSED
        )).isOne();
    }

    @Test
    void concurrentReopenHasOneWinnerOneConflictAndOneAuditEvent() throws Exception {
        lifecycleService.closeSession(
                workspaceId, sessionId, new CloseSharedReviewSessionRequest(null), ownerEmail
        );
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger conflicts = new AtomicInteger();

        runTogether(
                () -> reopen(successes, conflicts),
                () -> reopen(successes, conflicts)
        );

        assertThat(successes).hasValue(1);
        assertThat(conflicts).hasValue(1);
        assertThat(sessionRepository.findById(sessionId).orElseThrow().getClosedAt()).isNull();
        assertThat(auditRepository.countBySessionIdAndEventType(
                sessionId, TeamReviewAuditEventType.SESSION_REOPENED
        )).isOne();
    }

    @Test
    void reopenRacingCloseIsSerializedWithoutPartialStateOrDuplicateAudit() throws Exception {
        lifecycleService.closeSession(
                workspaceId, sessionId, new CloseSharedReviewSessionRequest(null), ownerEmail
        );
        AtomicInteger reopenSuccesses = new AtomicInteger();
        AtomicInteger closeSuccesses = new AtomicInteger();
        AtomicInteger closeConflicts = new AtomicInteger();

        runTogether(
                () -> lifecycleService.reopenSession(workspaceId, sessionId, ownerEmail),
                () -> close(closeSuccesses, closeConflicts)
        );
        reopenSuccesses.incrementAndGet();

        assertThat(reopenSuccesses).hasValue(1);
        assertThat(closeSuccesses.get() + closeConflicts.get()).isOne();
        assertThat(auditRepository.countBySessionIdAndEventType(
                sessionId, TeamReviewAuditEventType.SESSION_REOPENED
        )).isOne();
        assertThat(auditRepository.countBySessionIdAndEventType(
                sessionId, TeamReviewAuditEventType.SESSION_CLOSED
        )).isEqualTo(closeSuccesses.get() + 1L);
        ReviewSession persisted = sessionRepository.findById(sessionId).orElseThrow();
        assertThat(persisted.getClosedAt() != null).isEqualTo(closeSuccesses.get() == 1);
    }

    @Test
    void closeRacingVoteCannotMutateAfterClosure() throws Exception {
        AtomicInteger voteSuccesses = new AtomicInteger();
        AtomicInteger voteConflicts = new AtomicInteger();

        runTogether(
                () -> lifecycleService.closeSession(
                        workspaceId, sessionId, new CloseSharedReviewSessionRequest("race"), ownerEmail
                ),
                () -> {
                    try {
                        votingService.upsertOwnVote(
                                workspaceId, sessionId, cardId,
                                new UpsertTeamVoteRequest(
                                        TeamVoteDecision.REJECTED,
                                        "Concurrent rejection",
                                        reviewerVoteVersion
                                ),
                                reviewerEmail
                        );
                        voteSuccesses.incrementAndGet();
                    } catch (ConflictException exception) {
                        voteConflicts.incrementAndGet();
                    }
                }
        );

        ReviewSession persisted = sessionRepository.findById(sessionId).orElseThrow();
        assertThat(persisted.getClosedAt()).isNotNull();
        assertThat(voteSuccesses.get() + voteConflicts.get()).isOne();
        assertThat(auditRepository.countBySessionIdAndEventType(
                sessionId, TeamReviewAuditEventType.SESSION_CLOSED
        )).isOne();
        if (voteConflicts.get() == 1) {
            assertThat(persisted.getStatus()).isEqualTo(SessionStatus.APPROVED);
        } else {
            assertThat(persisted.getStatus()).isEqualTo(SessionStatus.REJECTED);
        }
    }

    @Test
    void closeRacingReviewerRemovalProducesAConsistentFrozenSnapshot() throws Exception {
        AtomicInteger removalSuccesses = new AtomicInteger();
        AtomicInteger removalConflicts = new AtomicInteger();

        runTogether(
                () -> lifecycleService.closeSession(
                        workspaceId, sessionId, new CloseSharedReviewSessionRequest(null), ownerEmail
                ),
                () -> {
                    try {
                        reviewerService.removeReviewer(
                                workspaceId,
                                sessionId,
                                reviewerAssignmentId,
                                new RemoveSessionReviewerRequest("Concurrent removal"),
                                ownerEmail
                        );
                        removalSuccesses.incrementAndGet();
                    } catch (ConflictException exception) {
                        removalConflicts.incrementAndGet();
                    }
                }
        );

        ReviewSession persisted = sessionRepository.findById(sessionId).orElseThrow();
        assertThat(persisted.getClosedAt()).isNotNull();
        assertThat(removalSuccesses.get() + removalConflicts.get()).isOne();
        assertThat(auditRepository.countBySessionIdAndEventType(
                sessionId, TeamReviewAuditEventType.SESSION_CLOSED
        )).isOne();
        ReviewSessionReviewer assignment = reviewerRepository
                .findById(reviewerAssignmentId).orElseThrow();
        if (removalConflicts.get() == 1) {
            assertThat(assignment.getStatus()).isEqualTo(ReviewSessionReviewerStatus.ASSIGNED);
        } else {
            assertThat(assignment.getStatus()).isEqualTo(ReviewSessionReviewerStatus.REMOVED);
        }
    }

    @Test
    void staleLifecycleVersionCannotOverwriteTheAuthoritativeClosedRow() {
        ReviewSession stale = transactionTemplate.execute(status ->
                sessionRepository.findById(sessionId).orElseThrow()
        );
        long staleVersion = stale.getLifecycleVersion();
        lifecycleService.closeSession(
                workspaceId, sessionId, new CloseSharedReviewSessionRequest(null), ownerEmail
        );
        stale.setTitle("Stale writer");

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status ->
                sessionRepository.saveAndFlush(stale)
        )).isInstanceOf(ObjectOptimisticLockingFailureException.class);

        ReviewSession authoritative = sessionRepository.findById(sessionId).orElseThrow();
        assertThat(authoritative.getLifecycleVersion()).isGreaterThan(staleVersion);
        assertThat(authoritative.getClosedAt()).isNotNull();
        assertThat(authoritative.getTitle()).isEqualTo("Lifecycle race session");
    }

    private void close(AtomicInteger successes, AtomicInteger conflicts) {
        try {
            lifecycleService.closeSession(
                    workspaceId, sessionId, new CloseSharedReviewSessionRequest(null), ownerEmail
            );
            successes.incrementAndGet();
        } catch (ConflictException exception) {
            conflicts.incrementAndGet();
        }
    }

    private void reopen(AtomicInteger successes, AtomicInteger conflicts) {
        try {
            lifecycleService.reopenSession(workspaceId, sessionId, ownerEmail);
            successes.incrementAndGet();
        } catch (ConflictException exception) {
            conflicts.incrementAndGet();
        }
    }

    private void runTogether(Runnable first, Runnable second) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> firstFuture = executor.submit(() -> afterBarrier(ready, start, first));
            Future<?> secondFuture = executor.submit(() -> afterBarrier(ready, start, second));
            ready.await();
            start.countDown();
            firstFuture.get();
            secondFuture.get();
        } finally {
            executor.shutdownNow();
        }
    }

    private void afterBarrier(CountDownLatch ready, CountDownLatch start, Runnable action) {
        ready.countDown();
        try {
            start.await();
            action.run();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    private WorkspaceMember member(Workspace workspace, User user, WorkspaceRole role) {
        return WorkspaceMember.builder()
                .workspace(workspace)
                .user(user)
                .role(role)
                .status(MembershipStatus.ACTIVE)
                .build();
    }

    private ReviewSessionReviewer assignment(
            ReviewSession session, WorkspaceMember member, User assignedBy
    ) {
        return ReviewSessionReviewer.builder()
                .session(session)
                .workspaceMember(member)
                .assignedBy(assignedBy)
                .status(ReviewSessionReviewerStatus.ASSIGNED)
                .build();
    }

    private User createUser(String label) {
        return userRepository.save(User.builder()
                .fullName("Test " + label)
                .email(label + "-" + UUID.randomUUID() + "@example.com")
                .passwordHash("test-password-hash")
                .build());
    }
}
