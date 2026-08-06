package com.microapproval.api.service;

import com.microapproval.api.dto.AssignSessionReviewerRequest;
import com.microapproval.api.entity.AiAnalysisStatus;
import com.microapproval.api.entity.AnalysisMode;
import com.microapproval.api.entity.MembershipStatus;
import com.microapproval.api.entity.ReviewSession;
import com.microapproval.api.entity.SessionStatus;
import com.microapproval.api.entity.User;
import com.microapproval.api.entity.Workspace;
import com.microapproval.api.entity.WorkspaceMember;
import com.microapproval.api.entity.WorkspaceRole;
import com.microapproval.api.entity.WorkspaceType;
import com.microapproval.api.exception.ConflictException;
import com.microapproval.api.repository.ReviewSessionReviewerRepository;
import com.microapproval.api.repository.TeamReviewAuditEventRepository;
import com.microapproval.api.repository.UserRepository;
import com.microapproval.api.repository.WorkspaceMemberRepository;
import com.microapproval.api.repository.WorkspaceRepository;
import com.microapproval.api.repository.ReviewSessionRepository;
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
class ReviewSessionReviewerConcurrencyIntegrationTest {

    @Autowired private ReviewSessionReviewerService reviewerService;
    @Autowired private ReviewSessionReviewerRepository reviewerRepository;
    @Autowired private TeamReviewAuditEventRepository auditRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private WorkspaceRepository workspaceRepository;
    @Autowired private WorkspaceMemberRepository memberRepository;
    @Autowired private ReviewSessionRepository sessionRepository;
    @Autowired private TransactionTemplate transactionTemplate;

    private String workspaceId;
    private String sessionId;
    private String targetMembershipId;
    private String ownerEmail;
    private List<String> userIds;

    @BeforeEach
    void createCommittedFixture() {
        transactionTemplate.executeWithoutResult(status -> {
            User owner = createUser("concurrent-owner");
            User reviewer = createUser("concurrent-reviewer");
            Workspace workspace = workspaceRepository.save(Workspace.builder()
                    .name("Concurrent assignment")
                    .owner(owner)
                    .build());
            memberRepository.save(WorkspaceMember.builder()
                    .workspace(workspace)
                    .user(owner)
                    .role(WorkspaceRole.OWNER)
                    .status(MembershipStatus.ACTIVE)
                    .build());
            WorkspaceMember target = memberRepository.save(WorkspaceMember.builder()
                    .workspace(workspace)
                    .user(reviewer)
                    .role(WorkspaceRole.REVIEWER)
                    .status(MembershipStatus.ACTIVE)
                    .build());
            ReviewSession session = sessionRepository.save(ReviewSession.builder()
                    .title("Concurrent session")
                    .workspaceType(WorkspaceType.SHARED)
                    .workspace(workspace)
                    .mode(AnalysisMode.RAW_SNIPPET)
                    .rawContent("return true;")
                    .submittedBy(owner)
                    .status(SessionStatus.PENDING)
                    .aiAnalysisStatus(AiAnalysisStatus.NOT_REQUESTED)
                    .build());
            workspaceId = workspace.getId();
            sessionId = session.getId();
            targetMembershipId = target.getId();
            ownerEmail = owner.getEmail();
            userIds = List.of(owner.getId(), reviewer.getId());
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
    void concurrentDuplicateAssignCreatesOneRowAndReturnsOneConflict() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger conflicts = new AtomicInteger();
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            List<Future<?>> futures = List.of(
                    executor.submit(() -> assignAfterBarrier(ready, start, successes, conflicts)),
                    executor.submit(() -> assignAfterBarrier(ready, start, successes, conflicts))
            );
            ready.await();
            start.countDown();
            for (Future<?> future : futures) {
                future.get();
            }
        } finally {
            executor.shutdownNow();
        }

        assertThat(successes).hasValue(1);
        assertThat(conflicts).hasValue(1);
        assertThat(reviewerRepository.countBySessionId(sessionId)).isOne();
        assertThat(auditRepository.countBySessionId(sessionId)).isOne();
    }

    private void assignAfterBarrier(
            CountDownLatch ready,
            CountDownLatch start,
            AtomicInteger successes,
            AtomicInteger conflicts
    ) {
        ready.countDown();
        try {
            start.await();
            reviewerService.assignReviewer(
                    workspaceId,
                    sessionId,
                    new AssignSessionReviewerRequest(targetMembershipId),
                    ownerEmail
            );
            successes.incrementAndGet();
        } catch (ConflictException exception) {
            conflicts.incrementAndGet();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    private User createUser(String label) {
        return userRepository.save(User.builder()
                .fullName("Test " + label)
                .email(label + "-" + UUID.randomUUID() + "@example.com")
                .passwordHash("test-password-hash")
                .build());
    }
}
