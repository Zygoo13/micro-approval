package com.microapproval.api.controller;

import com.microapproval.api.dto.AssignSessionReviewerRequest;
import com.microapproval.api.dto.RemoveSessionReviewerRequest;
import com.microapproval.api.dto.UpdateWorkspaceMemberRoleRequest;
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
import com.microapproval.api.repository.MicroDecisionRepository;
import com.microapproval.api.repository.ReviewSessionRepository;
import com.microapproval.api.repository.ReviewSessionReviewerRepository;
import com.microapproval.api.repository.TeamReviewAuditEventRepository;
import com.microapproval.api.repository.UserRepository;
import com.microapproval.api.repository.WorkspaceMemberRepository;
import com.microapproval.api.repository.WorkspaceRepository;
import com.microapproval.api.support.AbstractMySqlIntegrationTest;
import com.microapproval.api.service.ReviewSessionReviewerService;
import com.microapproval.api.service.TeamVotingService;
import com.microapproval.api.service.WorkspaceMemberService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SharedReviewSessionLifecycleControllerIntegrationTest extends AbstractMySqlIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private WorkspaceRepository workspaceRepository;
    @Autowired private WorkspaceMemberRepository memberRepository;
    @Autowired private ReviewSessionRepository sessionRepository;
    @Autowired private MicroDecisionRepository cardRepository;
    @Autowired private ReviewSessionReviewerRepository reviewerRepository;
    @Autowired private TeamReviewAuditEventRepository auditRepository;
    @Autowired private TeamVotingService votingService;
    @Autowired private ReviewSessionReviewerService reviewerService;
    @Autowired private WorkspaceMemberService memberService;

    @Test
    void ownerClosesApprovedSessionAndAllReadModelsExposeTrimmedMetadata() throws Exception {
        Fixture fixture = fixture("owner-close", true);
        ReviewSessionReviewer ownerAssignment = assignment(fixture, fixture.ownerMembership());
        votingService.upsertOwnVote(
                fixture.workspace().getId(), fixture.session().getId(), fixture.card().getId(),
                new UpsertTeamVoteRequest(TeamVoteDecision.APPROVED, null, null),
                fixture.owner().getEmail()
        );

        mockMvc.perform(post(lifecyclePath(fixture, "close"))
                        .with(user(fixture.owner().getEmail()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"  Release accepted  \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value(fixture.session().getId()))
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.closed").value(true))
                .andExpect(jsonPath("$.closedAt").isNotEmpty())
                .andExpect(jsonPath("$.closedByUserId").value(fixture.owner().getId()))
                .andExpect(jsonPath("$.closedByDisplayName").value(fixture.owner().getFullName()))
                .andExpect(jsonPath("$.closeReason").value("Release accepted"))
                .andExpect(jsonPath("$.lifecycleVersion").isNumber());

        for (String path : new String[]{
                "/api/workspaces/%s/sessions/%s".formatted(
                        fixture.workspace().getId(), fixture.session().getId()),
                "/api/workspaces/%s/sessions/%s/votes".formatted(
                        fixture.workspace().getId(), fixture.session().getId())
        }) {
            mockMvc.perform(get(path).with(user(fixture.owner().getEmail())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.closed").value(true))
                    .andExpect(jsonPath("$.closeReason").value("Release accepted"));
        }
        mockMvc.perform(get("/api/workspaces/{workspaceId}/sessions", fixture.workspace().getId())
                        .with(user(fixture.owner().getEmail())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].closed").value(true))
                .andExpect(jsonPath("$[0].closeReason").value("Release accepted"));

        assertThat(ownerAssignment.getId()).isNotNull();
        assertThat(auditRepository.countBySessionIdAndEventType(
                fixture.session().getId(), TeamReviewAuditEventType.SESSION_CLOSED
        )).isOne();
        var event = auditRepository.findAll().stream()
                .filter(item -> item.getEventType() == TeamReviewAuditEventType.SESSION_CLOSED)
                .findFirst().orElseThrow();
        assertThat(event.getActor().getId()).isEqualTo(fixture.owner().getId());
        assertThat(event.getOldValueJson()).contains("\"closed\":false");
        assertThat(event.getNewValueJson()).contains("\"closed\":true", "APPROVED");
        assertThat(event.getReason()).isEqualTo("Release accepted");
    }

    @Test
    void adminCanCloseRejectedSessionAndReopenWhileReviewerCannot() throws Exception {
        Fixture fixture = fixture("admin-lifecycle", true);
        WorkspaceMember admin = membership(fixture, "admin", WorkspaceRole.ADMIN, MembershipStatus.ACTIVE);
        WorkspaceMember reviewer = membership(fixture, "reviewer", WorkspaceRole.REVIEWER, MembershipStatus.ACTIVE);
        assignment(fixture, admin);
        votingService.upsertOwnVote(
                fixture.workspace().getId(), fixture.session().getId(), fixture.card().getId(),
                new UpsertTeamVoteRequest(TeamVoteDecision.REJECTED, "Blocking issue", null),
                admin.getUser().getEmail()
        );

        mockMvc.perform(post(lifecyclePath(fixture, "close"))
                        .with(user(reviewer.getUser().getEmail())))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(lifecyclePath(fixture, "close"))
                        .with(user(admin.getUser().getEmail())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));
        mockMvc.perform(post(lifecyclePath(fixture, "reopen"))
                        .with(user(reviewer.getUser().getEmail())))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(lifecyclePath(fixture, "reopen"))
                        .with(user(admin.getUser().getEmail())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.closed").value(false))
                .andExpect(jsonPath("$.closedAt").doesNotExist())
                .andExpect(jsonPath("$.closedByUserId").doesNotExist())
                .andExpect(jsonPath("$.closeReason").doesNotExist());

        long voteVersion = votingService.getSessionVotes(
                        fixture.workspace().getId(), fixture.session().getId(),
                        admin.getUser().getEmail()
                ).cards().getFirst().votes().getFirst().version();
        votingService.upsertOwnVote(
                fixture.workspace().getId(), fixture.session().getId(), fixture.card().getId(),
                new UpsertTeamVoteRequest(
                        TeamVoteDecision.APPROVED, "Changed after reopen", voteVersion
                ),
                admin.getUser().getEmail()
        );
        var newAssignment = reviewerService.assignReviewer(
                fixture.workspace().getId(), fixture.session().getId(),
                new AssignSessionReviewerRequest(reviewer.getId()),
                admin.getUser().getEmail()
        );
        reviewerService.removeReviewer(
                fixture.workspace().getId(), fixture.session().getId(), newAssignment.assignmentId(),
                new RemoveSessionReviewerRequest("Verified reopened mutation"),
                admin.getUser().getEmail()
        );
        mockMvc.perform(post(lifecyclePath(fixture, "reopen"))
                        .with(user(fixture.owner().getEmail())))
                .andExpect(status().isConflict());

        assertThat(auditRepository.countBySessionIdAndEventType(
                fixture.session().getId(), TeamReviewAuditEventType.SESSION_REOPENED
        )).isOne();
    }

    @Test
    void onlyTerminalAggregateCanCloseAndZeroCardSessionIsApproved() throws Exception {
        Fixture pending = fixture("pending-close", true);
        mockMvc.perform(post(lifecyclePath(pending, "close"))
                        .with(user(pending.owner().getEmail())))
                .andExpect(status().isConflict());

        Fixture inReview = fixture("in-review-close", true);
        WorkspaceMember reviewer = membership(
                inReview, "second-reviewer", WorkspaceRole.REVIEWER, MembershipStatus.ACTIVE
        );
        assignment(inReview, inReview.ownerMembership());
        assignment(inReview, reviewer);
        votingService.upsertOwnVote(
                inReview.workspace().getId(), inReview.session().getId(), inReview.card().getId(),
                new UpsertTeamVoteRequest(TeamVoteDecision.APPROVED, null, null),
                inReview.owner().getEmail()
        );
        mockMvc.perform(post(lifecyclePath(inReview, "close"))
                        .with(user(inReview.owner().getEmail())))
                .andExpect(status().isConflict());

        Fixture zeroCard = fixture("zero-card-close", false);
        mockMvc.perform(post(lifecyclePath(zeroCard, "close"))
                        .with(user(zeroCard.owner().getEmail())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.closed").value(true));
    }

    @Test
    void closedSessionRejectsVoteAndReviewerMutationsButRemainsReadable() throws Exception {
        Fixture fixture = fixture("closed-guards", true);
        WorkspaceMember reviewer = membership(
                fixture, "guard-reviewer", WorkspaceRole.REVIEWER, MembershipStatus.ACTIVE
        );
        ReviewSessionReviewer ownerAssignment = assignment(fixture, fixture.ownerMembership());
        votingService.upsertOwnVote(
                fixture.workspace().getId(), fixture.session().getId(), fixture.card().getId(),
                new UpsertTeamVoteRequest(TeamVoteDecision.APPROVED, null, null),
                fixture.owner().getEmail()
        );
        close(fixture, fixture.owner());

        mockMvc.perform(put("/api/workspaces/{workspaceId}/sessions/{sessionId}/cards/{cardId}/vote",
                        fixture.workspace().getId(), fixture.session().getId(), fixture.card().getId())
                        .with(user(fixture.owner().getEmail()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"REJECTED\",\"note\":\"late\",\"version\":0}"))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/workspaces/{workspaceId}/sessions/{sessionId}/reviewers",
                        fixture.workspace().getId(), fixture.session().getId())
                        .with(user(fixture.owner().getEmail()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"workspaceMemberId\":\"%s\"}".formatted(reviewer.getId())))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/workspaces/{workspaceId}/sessions/{sessionId}/reviewers/{assignmentId}/remove",
                        fixture.workspace().getId(), fixture.session().getId(), ownerAssignment.getId())
                        .with(user(fixture.owner().getEmail()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"late\"}"))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/api/workspaces/{workspaceId}/sessions/{sessionId}/votes",
                        fixture.workspace().getId(), fixture.session().getId())
                        .with(user(reviewer.getUser().getEmail())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.closed").value(true));
        mockMvc.perform(get("/api/workspaces/{workspaceId}/sessions/{sessionId}/reviewers",
                        fixture.workspace().getId(), fixture.session().getId())
                        .with(user(reviewer.getUser().getEmail())))
                .andExpect(status().isOk());
    }

    @Test
    void closedSnapshotSurvivesRoleLossAndReopenRecalculatesCurrentEligibility() throws Exception {
        Fixture fixture = fixture("frozen-membership", true);
        WorkspaceMember reviewer = membership(
                fixture, "frozen-reviewer", WorkspaceRole.REVIEWER, MembershipStatus.ACTIVE
        );
        ReviewSessionReviewer ownerAssignment = assignment(fixture, fixture.ownerMembership());
        ReviewSessionReviewer reviewerAssignment = assignment(fixture, reviewer);
        votingService.upsertOwnVote(
                fixture.workspace().getId(), fixture.session().getId(), fixture.card().getId(),
                new UpsertTeamVoteRequest(TeamVoteDecision.APPROVED, null, null),
                fixture.owner().getEmail()
        );
        votingService.upsertOwnVote(
                fixture.workspace().getId(), fixture.session().getId(), fixture.card().getId(),
                new UpsertTeamVoteRequest(TeamVoteDecision.REJECTED, "Reject before close", null),
                reviewer.getUser().getEmail()
        );
        close(fixture, fixture.owner());

        memberService.changeMemberRole(
                fixture.workspace().getId(), reviewer.getId(),
                new UpdateWorkspaceMemberRoleRequest(WorkspaceRole.MEMBER),
                fixture.owner().getEmail()
        );
        assertThat(reviewerRepository.findById(reviewerAssignment.getId()).orElseThrow().getStatus())
                .isEqualTo(ReviewSessionReviewerStatus.ASSIGNED);
        assertThat(sessionRepository.findById(fixture.session().getId()).orElseThrow().getStatus())
                .isEqualTo(SessionStatus.REJECTED);

        mockMvc.perform(post(lifecyclePath(fixture, "reopen"))
                        .with(user(fixture.owner().getEmail())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
        assertThat(reviewerRepository.findById(ownerAssignment.getId())).isPresent();
        assertThat(reviewerRepository.findById(reviewerAssignment.getId())).isPresent();

        Fixture removedFixture = fixture("frozen-removal", true);
        WorkspaceMember removedReviewer = membership(
                removedFixture, "removed-reviewer", WorkspaceRole.REVIEWER, MembershipStatus.ACTIVE
        );
        assignment(removedFixture, removedFixture.ownerMembership());
        ReviewSessionReviewer removedAssignment = assignment(removedFixture, removedReviewer);
        votingService.upsertOwnVote(
                removedFixture.workspace().getId(), removedFixture.session().getId(),
                removedFixture.card().getId(),
                new UpsertTeamVoteRequest(TeamVoteDecision.APPROVED, null, null),
                removedFixture.owner().getEmail()
        );
        votingService.upsertOwnVote(
                removedFixture.workspace().getId(), removedFixture.session().getId(),
                removedFixture.card().getId(),
                new UpsertTeamVoteRequest(TeamVoteDecision.REJECTED, "Frozen reject", null),
                removedReviewer.getUser().getEmail()
        );
        close(removedFixture, removedFixture.owner());
        memberService.removeMember(
                removedFixture.workspace().getId(), removedReviewer.getId(),
                removedFixture.owner().getEmail()
        );
        assertThat(reviewerRepository.findById(removedAssignment.getId()).orElseThrow().getStatus())
                .isEqualTo(ReviewSessionReviewerStatus.ASSIGNED);
        assertThat(sessionRepository.findById(removedFixture.session().getId()).orElseThrow().getStatus())
                .isEqualTo(SessionStatus.REJECTED);
    }

    @Test
    void hiddenScopesAndReasonValidationUseExistingApiConventions() throws Exception {
        Fixture fixture = fixture("scope-validation", false);
        WorkspaceMember pending = membership(
                fixture, "pending", WorkspaceRole.ADMIN, MembershipStatus.PENDING
        );
        WorkspaceMember removed = membership(
                fixture, "removed", WorkspaceRole.ADMIN, MembershipStatus.REMOVED
        );
        WorkspaceMember member = membership(
                fixture, "member", WorkspaceRole.MEMBER, MembershipStatus.ACTIVE
        );
        WorkspaceMember auditor = membership(
                fixture, "auditor", WorkspaceRole.AUDITOR, MembershipStatus.ACTIVE
        );
        WorkspaceMember reviewer = membership(
                fixture, "reviewer", WorkspaceRole.REVIEWER, MembershipStatus.ACTIVE
        );
        for (User caller : new User[]{member.getUser(), auditor.getUser(), reviewer.getUser()}) {
            mockMvc.perform(post(lifecyclePath(fixture, "close")).with(user(caller.getEmail())))
                    .andExpect(status().isForbidden());
        }
        fixture.session().setSubmittedBy(reviewer.getUser());
        sessionRepository.saveAndFlush(fixture.session());
        mockMvc.perform(post(lifecyclePath(fixture, "close"))
                        .with(user(reviewer.getUser().getEmail())))
                .andExpect(status().isForbidden());
        User outsider = createUser("outsider");
        for (User caller : new User[]{pending.getUser(), removed.getUser(), outsider}) {
            mockMvc.perform(post(lifecyclePath(fixture, "close")).with(user(caller.getEmail())))
                    .andExpect(status().isNotFound());
        }

        ReviewSession personal = sessionRepository.save(session(
                fixture.owner(), null, WorkspaceType.PERSONAL
        ));
        mockMvc.perform(post("/api/workspaces/{workspaceId}/sessions/{sessionId}/close",
                        fixture.workspace().getId(), personal.getId())
                        .with(user(fixture.owner().getEmail())))
                .andExpect(status().isNotFound());

        Workspace otherWorkspace = workspaceRepository.save(Workspace.builder()
                .name("Other lifecycle scope")
                .owner(fixture.owner())
                .build());
        memberRepository.save(WorkspaceMember.builder()
                .workspace(otherWorkspace)
                .user(fixture.owner())
                .role(WorkspaceRole.OWNER)
                .status(MembershipStatus.ACTIVE)
                .build());
        ReviewSession otherSession = sessionRepository.save(session(
                fixture.owner(), otherWorkspace, WorkspaceType.SHARED
        ));
        mockMvc.perform(post("/api/workspaces/{workspaceId}/sessions/{sessionId}/close",
                        fixture.workspace().getId(), otherSession.getId())
                        .with(user(fixture.owner().getEmail())))
                .andExpect(status().isNotFound());

        mockMvc.perform(post(lifecyclePath(fixture, "close"))
                        .with(user(fixture.owner().getEmail()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"   \"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post(lifecyclePath(fixture, "close"))
                        .with(user(fixture.owner().getEmail()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"%s\"}".formatted("x".repeat(1001))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post(lifecyclePath(fixture, "close"))
                        .with(user(fixture.owner().getEmail())))
                .andExpect(status().isOk());
        mockMvc.perform(post(lifecyclePath(fixture, "close"))
                        .with(user(fixture.owner().getEmail())))
                .andExpect(status().isConflict());
        mockMvc.perform(post(lifecyclePath(fixture, "reopen"))
                        .with(user(fixture.owner().getEmail())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.closed").value(false));
        assertThat(auditRepository.countBySessionIdAndEventType(
                fixture.session().getId(), TeamReviewAuditEventType.SESSION_CLOSED
        )).isOne();
    }

    private void close(Fixture fixture, User caller) throws Exception {
        mockMvc.perform(post(lifecyclePath(fixture, "close")).with(user(caller.getEmail())))
                .andExpect(status().isOk());
    }

    private String lifecyclePath(Fixture fixture, String action) {
        return "/api/workspaces/%s/sessions/%s/%s".formatted(
                fixture.workspace().getId(), fixture.session().getId(), action
        );
    }

    private Fixture fixture(String label, boolean withCard) {
        User owner = createUser(label + "-owner");
        Workspace workspace = workspaceRepository.save(Workspace.builder()
                .name("Workspace " + label)
                .owner(owner)
                .build());
        WorkspaceMember ownerMembership = memberRepository.save(WorkspaceMember.builder()
                .workspace(workspace)
                .user(owner)
                .role(WorkspaceRole.OWNER)
                .status(MembershipStatus.ACTIVE)
                .build());
        ReviewSession session = sessionRepository.save(session(owner, workspace, WorkspaceType.SHARED));
        MicroDecision card = withCard ? cardRepository.save(card(session)) : null;
        return new Fixture(owner, workspace, ownerMembership, session, card);
    }

    private WorkspaceMember membership(
            Fixture fixture, String label, WorkspaceRole role, MembershipStatus status
    ) {
        return memberRepository.save(WorkspaceMember.builder()
                .workspace(fixture.workspace())
                .user(createUser(label))
                .role(role)
                .status(status)
                .build());
    }

    private ReviewSessionReviewer assignment(Fixture fixture, WorkspaceMember member) {
        return reviewerRepository.saveAndFlush(ReviewSessionReviewer.builder()
                .session(fixture.session())
                .workspaceMember(member)
                .assignedBy(fixture.owner())
                .status(ReviewSessionReviewerStatus.ASSIGNED)
                .build());
    }

    private ReviewSession session(User owner, Workspace workspace, WorkspaceType type) {
        return ReviewSession.builder()
                .title("Lifecycle " + UUID.randomUUID())
                .workspaceType(type)
                .workspace(workspace)
                .mode(AnalysisMode.RAW_SNIPPET)
                .rawContent("return true;")
                .submittedBy(owner)
                .status(SessionStatus.PENDING)
                .aiAnalysisStatus(AiAnalysisStatus.NOT_REQUESTED)
                .build();
    }

    private MicroDecision card(ReviewSession session) {
        return MicroDecision.builder()
                .session(session)
                .engineType(EngineType.RULE_BASED)
                .riskCategory(RiskCategory.SECURITY)
                .riskLevel(RiskLevel.HIGH)
                .questionText("Is this safe?")
                .codeSnippet("return true;")
                .displayOrder(0)
                .build();
    }

    private User createUser(String label) {
        return userRepository.save(User.builder()
                .fullName("Test " + label)
                .email(label + "-" + UUID.randomUUID() + "@example.com")
                .passwordHash("test-password-hash")
                .build());
    }

    private record Fixture(
            User owner,
            Workspace workspace,
            WorkspaceMember ownerMembership,
            ReviewSession session,
            MicroDecision card
    ) {
    }
}
