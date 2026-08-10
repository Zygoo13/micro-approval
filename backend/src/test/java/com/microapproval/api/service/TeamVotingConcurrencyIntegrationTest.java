package com.microapproval.api.service;

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
import com.microapproval.api.entity.TeamDecisionStatus;
import com.microapproval.api.entity.TeamVoteDecision;
import com.microapproval.api.entity.User;
import com.microapproval.api.entity.Workspace;
import com.microapproval.api.entity.WorkspaceMember;
import com.microapproval.api.entity.WorkspaceRole;
import com.microapproval.api.entity.WorkspaceType;
import com.microapproval.api.exception.ConflictException;
import com.microapproval.api.exception.ForbiddenOperationException;
import com.microapproval.api.repository.DecisionCardVoteRepository;
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
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class TeamVotingConcurrencyIntegrationTest extends AbstractMySqlIntegrationTest {

    @Autowired private TeamVotingService votingService;
    @Autowired private ReviewSessionReviewerService reviewerService;
    @Autowired private DecisionCardVoteRepository voteRepository;
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
    private String assignmentAId;
    private String emailA;
    private String emailB;
    private String ownerEmail;
    private List<String> userIds;

    @BeforeEach
    void createCommittedFixture() {
        transactionTemplate.executeWithoutResult(status -> {
            User owner = createUser("vote-concurrency-owner");
            User reviewerA = createUser("vote-concurrency-a");
            User reviewerB = createUser("vote-concurrency-b");
            Workspace workspace = workspaceRepository.save(Workspace.builder()
                    .name("Concurrent Team Voting")
                    .owner(owner)
                    .build());
            memberRepository.save(member(workspace, owner, WorkspaceRole.OWNER));
            WorkspaceMember memberA = memberRepository.save(member(
                    workspace, reviewerA, WorkspaceRole.REVIEWER
            ));
            WorkspaceMember memberB = memberRepository.save(member(
                    workspace, reviewerB, WorkspaceRole.REVIEWER
            ));
            ReviewSession session = sessionRepository.save(ReviewSession.builder()
                    .title("Concurrent voting session")
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
            ReviewSessionReviewer assignmentA = reviewerRepository.save(assignment(
                    session, memberA, owner
            ));
            reviewerRepository.save(assignment(session, memberB, owner));

            workspaceId = workspace.getId();
            sessionId = session.getId();
            cardId = card.getId();
            assignmentAId = assignmentA.getId();
            emailA = reviewerA.getEmail();
            emailB = reviewerB.getEmail();
            ownerEmail = owner.getEmail();
            userIds = List.of(owner.getId(), reviewerA.getId(), reviewerB.getId());
        });
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
    void twoReviewersVotingAtOnceBothPersistAndProduceApprovedAggregate() throws Exception {
        AtomicInteger successes = new AtomicInteger();
        runTogether(
                () -> createApproved(emailA, successes),
                () -> createApproved(emailB, successes)
        );

        assertThat(successes).hasValue(2);
        assertThat(voteRepository.countByDecisionCardSessionId(sessionId)).isEqualTo(2);
        assertThat(auditRepository.countBySessionId(sessionId)).isEqualTo(2);
        assertThat(cardRepository.findById(cardId).orElseThrow().getTeamDecision())
                .isEqualTo(TeamDecisionStatus.APPROVED);
        assertThat(sessionRepository.findById(sessionId).orElseThrow().getStatus())
                .isEqualTo(SessionStatus.APPROVED);
    }

    @Test
    void duplicateConcurrentCreateForSameReviewerCreatesOneRowAndOneConflict() throws Exception {
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger conflicts = new AtomicInteger();

        runTogether(
                () -> createOwnVoteAfterBarrier(successes, conflicts),
                () -> createOwnVoteAfterBarrier(successes, conflicts)
        );

        assertThat(successes).hasValue(1);
        assertThat(conflicts).hasValue(1);
        assertThat(voteRepository.countByDecisionCardSessionId(sessionId)).isOne();
        assertThat(auditRepository.countBySessionId(sessionId)).isOne();
    }

    @Test
    void sameVoteConcurrentUpdatesAllowOneWinnerAndReturnOneStaleConflict() throws Exception {
        SessionVotingResponse initial = votingService.upsertOwnVote(
                workspaceId, sessionId, cardId,
                new UpsertTeamVoteRequest(TeamVoteDecision.APPROVED, null, null),
                emailA
        );
        long version = initial.cards().getFirst().votes().getFirst().version();
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger conflicts = new AtomicInteger();

        runTogether(
                () -> updateVote(TeamVoteDecision.REJECTED, "Reject concurrently", version,
                        successes, conflicts),
                () -> updateVote(TeamVoteDecision.APPROVED, "Approve concurrently", version,
                        successes, conflicts)
        );

        assertThat(successes).hasValue(1);
        assertThat(conflicts).hasValue(1);
        assertThat(voteRepository.countByDecisionCardSessionId(sessionId)).isOne();
        assertThat(auditRepository.countBySessionId(sessionId)).isEqualTo(2);
        assertThat(voteRepository.findAll().getFirst().getVersion()).isEqualTo(version + 1);
    }

    @Test
    void voteRacingReviewerRemovalNeverCountsRemovedReviewer() throws Exception {
        AtomicInteger voteSuccess = new AtomicInteger();
        AtomicInteger voteForbidden = new AtomicInteger();

        runTogether(
                () -> {
                    try {
                        votingService.upsertOwnVote(
                                workspaceId, sessionId, cardId,
                                new UpsertTeamVoteRequest(
                                        TeamVoteDecision.REJECTED,
                                        "Race rejection",
                                        null
                                ),
                                emailA
                        );
                        voteSuccess.incrementAndGet();
                    } catch (ForbiddenOperationException exception) {
                        voteForbidden.incrementAndGet();
                    }
                },
                () -> reviewerService.removeReviewer(
                        workspaceId,
                        sessionId,
                        assignmentAId,
                        new RemoveSessionReviewerRequest("Concurrent removal"),
                        ownerEmail
                )
        );

        assertThat(voteSuccess.get() + voteForbidden.get()).isOne();
        assertThat(reviewerRepository.findById(assignmentAId).orElseThrow().getStatus())
                .isEqualTo(ReviewSessionReviewerStatus.REMOVED);
        SessionVotingResponse result = votingService.getSessionVotes(
                workspaceId, sessionId, ownerEmail
        );
        assertThat(result.reviewerCount()).isOne();
        assertThat(result.cards().getFirst().votes())
                .allMatch(vote -> !vote.reviewerAssignmentId().equals(assignmentAId)
                        || !vote.counted());
        assertThat(result.cards().getFirst().teamDecision()).isEqualTo(TeamDecisionStatus.PENDING);
        assertThat(result.sessionStatus()).isEqualTo(SessionStatus.PENDING);
    }

    private void createApproved(String email, AtomicInteger successes) {
        votingService.upsertOwnVote(
                workspaceId, sessionId, cardId,
                new UpsertTeamVoteRequest(TeamVoteDecision.APPROVED, null, null),
                email
        );
        successes.incrementAndGet();
    }

    private void createOwnVoteAfterBarrier(
            AtomicInteger successes,
            AtomicInteger conflicts
    ) {
        try {
            votingService.upsertOwnVote(
                    workspaceId, sessionId, cardId,
                    new UpsertTeamVoteRequest(TeamVoteDecision.APPROVED, null, null),
                    emailA
            );
            successes.incrementAndGet();
        } catch (ConflictException exception) {
            conflicts.incrementAndGet();
        }
    }

    private void updateVote(
            TeamVoteDecision decision,
            String note,
            long version,
            AtomicInteger successes,
            AtomicInteger conflicts
    ) {
        try {
            votingService.upsertOwnVote(
                    workspaceId, sessionId, cardId,
                    new UpsertTeamVoteRequest(decision, note, version),
                    emailA
            );
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
