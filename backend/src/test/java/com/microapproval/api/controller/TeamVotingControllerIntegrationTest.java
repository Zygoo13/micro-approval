package com.microapproval.api.controller;

import com.jayway.jsonpath.JsonPath;
import com.microapproval.api.dto.RemoveSessionReviewerRequest;
import com.microapproval.api.dto.UpdateWorkspaceMemberRoleRequest;
import com.microapproval.api.entity.AiAnalysisStatus;
import com.microapproval.api.entity.AnalysisMode;
import com.microapproval.api.entity.DecisionCardVote;
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
import com.microapproval.api.entity.TeamReviewAuditEventType;
import com.microapproval.api.entity.User;
import com.microapproval.api.entity.Workspace;
import com.microapproval.api.entity.WorkspaceMember;
import com.microapproval.api.entity.WorkspaceRole;
import com.microapproval.api.entity.WorkspaceType;
import com.microapproval.api.repository.DecisionCardVoteRepository;
import com.microapproval.api.repository.MicroDecisionRepository;
import com.microapproval.api.repository.ReviewSessionRepository;
import com.microapproval.api.repository.ReviewSessionReviewerRepository;
import com.microapproval.api.repository.TeamReviewAuditEventRepository;
import com.microapproval.api.repository.UserRepository;
import com.microapproval.api.repository.WorkspaceMemberRepository;
import com.microapproval.api.repository.WorkspaceRepository;
import com.microapproval.api.service.ReviewSessionReviewerService;
import com.microapproval.api.service.WorkspaceMemberService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.data.domain.Pageable;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TeamVotingControllerIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private WorkspaceRepository workspaceRepository;
    @Autowired private WorkspaceMemberRepository memberRepository;
    @Autowired private ReviewSessionRepository sessionRepository;
    @Autowired private MicroDecisionRepository cardRepository;
    @Autowired private ReviewSessionReviewerRepository reviewerRepository;
    @Autowired private DecisionCardVoteRepository voteRepository;
    @Autowired private TeamReviewAuditEventRepository auditRepository;
    @Autowired private ReviewSessionReviewerService reviewerService;
    @Autowired private WorkspaceMemberService memberService;

    @Test
    void assignedOwnerAdminAndReviewerCanVote() throws Exception {
        for (WorkspaceRole role : List.of(
                WorkspaceRole.OWNER,
                WorkspaceRole.ADMIN,
                WorkspaceRole.REVIEWER
        )) {
            Fixture fixture = fixture("eligible-" + role.name().toLowerCase(), true);
            WorkspaceMember voter = role == WorkspaceRole.OWNER
                    ? fixture.ownerMembership()
                    : membership(fixture.workspace(), "voter-" + role, role, MembershipStatus.ACTIVE);
            assignment(fixture, voter);

            vote(fixture, voter.getUser(), fixture.card().getId(), "APPROVED", null, null)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.cards[0].teamDecision").value("APPROVED"))
                    .andExpect(jsonPath("$.sessionStatus").value("APPROVED"));
        }
    }

    @Test
    void activeMembersCanReadNotesButOnlyEligibleAssignedReviewersCanVote() throws Exception {
        Fixture fixture = fixture("read-auth", true);
        WorkspaceMember reviewer = membership(fixture.workspace(), "read-reviewer", WorkspaceRole.REVIEWER, MembershipStatus.ACTIVE);
        WorkspaceMember member = membership(fixture.workspace(), "read-member", WorkspaceRole.MEMBER, MembershipStatus.ACTIVE);
        WorkspaceMember auditor = membership(fixture.workspace(), "read-auditor", WorkspaceRole.AUDITOR, MembershipStatus.ACTIVE);
        WorkspaceMember unassignedAdmin = membership(fixture.workspace(), "read-admin", WorkspaceRole.ADMIN, MembershipStatus.ACTIVE);
        assignment(fixture, reviewer);
        assignment(fixture, member);
        assignment(fixture, auditor);

        vote(fixture, reviewer.getUser(), fixture.card().getId(), "REJECTED", "  Thiếu quyền truy cập  ", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cards[0].votes[0].note").value("Thiếu quyền truy cập"));

        for (User viewer : List.of(member.getUser(), auditor.getUser())) {
            mockMvc.perform(get(votesPath(fixture)).with(user(viewer.getEmail())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.cards[0].votes[0].note").value("Thiếu quyền truy cập"));
        }
        vote(fixture, member.getUser(), fixture.card().getId(), "APPROVED", null, null)
                .andExpect(status().isForbidden());
        vote(fixture, auditor.getUser(), fixture.card().getId(), "APPROVED", null, null)
                .andExpect(status().isForbidden());
        vote(fixture, unassignedAdmin.getUser(), fixture.card().getId(), "APPROVED", null, null)
                .andExpect(status().isForbidden());
    }

    @Test
    void inactiveAndNonMembersAreHiddenAndPersonalOrCrossScopedResourcesReturn404() throws Exception {
        Fixture fixture = fixture("hidden", true);
        WorkspaceMember reviewer = membership(fixture.workspace(), "hidden-reviewer", WorkspaceRole.REVIEWER, MembershipStatus.ACTIVE);
        assignment(fixture, reviewer);
        WorkspaceMember pending = membership(fixture.workspace(), "hidden-pending", WorkspaceRole.REVIEWER, MembershipStatus.PENDING);
        WorkspaceMember removed = membership(fixture.workspace(), "hidden-removed", WorkspaceRole.REVIEWER, MembershipStatus.REMOVED);
        User outsider = createUser("hidden-outsider");

        for (User caller : List.of(pending.getUser(), removed.getUser(), outsider)) {
            mockMvc.perform(get(votesPath(fixture)).with(user(caller.getEmail())))
                    .andExpect(status().isNotFound());
            vote(fixture, caller, fixture.card().getId(), "APPROVED", null, null)
                    .andExpect(status().isNotFound());
        }

        ReviewSession personal = sessionRepository.save(session(fixture.owner(), null, WorkspaceType.PERSONAL));
        MicroDecision personalCard = cardRepository.save(card(personal, 0));
        mockMvc.perform(get(votesPath(fixture.workspace().getId(), personal.getId()))
                        .with(user(fixture.owner().getEmail())))
                .andExpect(status().isNotFound());
        vote(fixture.workspace().getId(), personal.getId(), personalCard.getId(),
                fixture.owner(), "APPROVED", null, null).andExpect(status().isNotFound());

        Fixture other = fixture("hidden-other", true);
        vote(fixture, reviewer.getUser(), other.card().getId(), "APPROVED", null, null)
                .andExpect(status().isNotFound());
    }

    @Test
    void validatesDecisionRejectedNoteAndLength() throws Exception {
        Fixture fixture = fixture("validation", true);
        assignment(fixture, fixture.ownerMembership());

        voteRaw(fixture, fixture.owner(), "{}")
                .andExpect(status().isBadRequest());
        voteRaw(fixture, fixture.owner(), "{\"decision\":\"REJECTED\",\"note\":\"   \"}")
                .andExpect(status().isBadRequest());
        voteRaw(fixture, fixture.owner(), "{\"decision\":\"INVALID\"}")
                .andExpect(status().isBadRequest());
        String tooLong = "a".repeat(2001);
        vote(fixture, fixture.owner(), fixture.card().getId(), "APPROVED", tooLong, null)
                .andExpect(status().isBadRequest());
        assertThat(voteRepository.count()).isZero();
    }

    @Test
    void createThenUpdateKeepsRowIdIncrementsVersionAndAuditsOldAndNewValues() throws Exception {
        Fixture fixture = fixture("update", true);
        assignment(fixture, fixture.ownerMembership());

        String created = vote(fixture, fixture.owner(), fixture.card().getId(),
                "REJECTED", "Lỗi quyền", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cards[0].teamDecision").value("REJECTED"))
                .andExpect(jsonPath("$.sessionStatus").value("REJECTED"))
                .andReturn().getResponse().getContentAsString();
        String voteId = JsonPath.read(created, "$.cards[0].votes[0].voteId");
        Number version = JsonPath.read(created, "$.cards[0].votes[0].version");

        String updated = vote(fixture, fixture.owner(), fixture.card().getId(),
                "APPROVED", "Đã sửa", version.longValue())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cards[0].teamDecision").value("APPROVED"))
                .andExpect(jsonPath("$.sessionStatus").value("APPROVED"))
                .andExpect(jsonPath("$.cards[0].votes[0].voteId").value(voteId))
                .andReturn().getResponse().getContentAsString();
        Number updatedVersion = JsonPath.read(updated, "$.cards[0].votes[0].version");

        assertThat(updatedVersion.longValue()).isEqualTo(version.longValue() + 1);
        assertThat(voteRepository.countByDecisionCardSessionId(fixture.session().getId())).isOne();
        var sessionAudits = auditRepository.findBySessionId(
                fixture.session().getId(), Pageable.unpaged()
        ).getContent();
        assertThat(sessionAudits)
                .extracting(event -> event.getEventType())
                .containsExactlyInAnyOrder(
                        TeamReviewAuditEventType.VOTE_CREATED,
                        TeamReviewAuditEventType.VOTE_UPDATED
                );
        var updateAudit = sessionAudits.stream()
                .filter(event -> event.getEventType() == TeamReviewAuditEventType.VOTE_UPDATED)
                .findFirst()
                .orElseThrow();
        assertThat(updateAudit.getOldValueJson()).contains("REJECTED", "Lỗi quyền");
        assertThat(updateAudit.getNewValueJson()).contains("APPROVED", "Đã sửa");

        vote(fixture, fixture.owner(), fixture.card().getId(),
                "REJECTED", "stale", version.longValue()).andExpect(status().isConflict());
    }

    @Test
    void calculatesCardAndSessionAggregateAcrossReviewersAndCards() throws Exception {
        Fixture fixture = fixture("aggregate", true);
        MicroDecision secondCard = cardRepository.save(card(fixture.session(), 1));
        WorkspaceMember reviewer = membership(fixture.workspace(), "aggregate-reviewer", WorkspaceRole.REVIEWER, MembershipStatus.ACTIVE);
        assignment(fixture, fixture.ownerMembership());
        assignment(fixture, reviewer);

        vote(fixture, fixture.owner(), fixture.card().getId(), "APPROVED", null, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cards[0].teamDecision").value("PENDING"))
                .andExpect(jsonPath("$.sessionStatus").value("IN_REVIEW"));
        vote(fixture, reviewer.getUser(), fixture.card().getId(), "APPROVED", null, null)
                .andExpect(jsonPath("$.cards[0].teamDecision").value("APPROVED"))
                .andExpect(jsonPath("$.cards[1].teamDecision").value("PENDING"))
                .andExpect(jsonPath("$.sessionStatus").value("IN_REVIEW"));
        vote(fixture, fixture.owner(), secondCard.getId(), "APPROVED", null, null)
                .andExpect(jsonPath("$.sessionStatus").value("IN_REVIEW"));
        String allApproved = vote(fixture, reviewer.getUser(), secondCard.getId(), "APPROVED", null, null)
                .andExpect(jsonPath("$.cards[1].teamDecision").value("APPROVED"))
                .andExpect(jsonPath("$.sessionStatus").value("APPROVED"))
                .andReturn().getResponse().getContentAsString();
        Number version = JsonPath.read(allApproved, "$.cards[1].votes[1].version");

        vote(fixture, reviewer.getUser(), secondCard.getId(), "REJECTED", "Không an toàn", version.longValue())
                .andExpect(jsonPath("$.cards[1].teamDecision").value("REJECTED"))
                .andExpect(jsonPath("$.sessionStatus").value("REJECTED"));
    }

    @Test
    void removeAndReactivateExcludeOldVoteUntilReviewerConfirmsAgain() throws Exception {
        Fixture fixture = fixture("lifecycle", true);
        WorkspaceMember reviewer = membership(fixture.workspace(), "lifecycle-reviewer", WorkspaceRole.REVIEWER, MembershipStatus.ACTIVE);
        assignment(fixture, fixture.ownerMembership());
        ReviewSessionReviewer reviewerAssignment = assignment(fixture, reviewer);
        vote(fixture, fixture.owner(), fixture.card().getId(), "APPROVED", null, null)
                .andExpect(status().isOk());
        String rejected = vote(fixture, reviewer.getUser(), fixture.card().getId(),
                "REJECTED", "Chưa an toàn", null)
                .andExpect(jsonPath("$.sessionStatus").value("REJECTED"))
                .andReturn().getResponse().getContentAsString();
        Number oldVoteVersion = JsonPath.read(rejected, "$.cards[0].votes[1].version");

        reviewerService.removeReviewer(
                fixture.workspace().getId(), fixture.session().getId(), reviewerAssignment.getId(),
                new RemoveSessionReviewerRequest("Đổi reviewer"), fixture.owner().getEmail()
        );
        mockMvc.perform(get(votesPath(fixture)).with(user(fixture.owner().getEmail())))
                .andExpect(jsonPath("$.reviewerCount").value(1))
                .andExpect(jsonPath("$.cards[0].teamDecision").value("APPROVED"))
                .andExpect(jsonPath("$.cards[0].votes[1].counted").value(false));

        reviewerService.assignReviewer(
                fixture.workspace().getId(), fixture.session().getId(),
                new com.microapproval.api.dto.AssignSessionReviewerRequest(reviewer.getId()),
                fixture.owner().getEmail()
        );
        mockMvc.perform(get(votesPath(fixture)).with(user(fixture.owner().getEmail())))
                .andExpect(jsonPath("$.reviewerCount").value(2))
                .andExpect(jsonPath("$.cards[0].teamDecision").value("PENDING"))
                .andExpect(jsonPath("$.sessionStatus").value("IN_REVIEW"))
                .andExpect(jsonPath("$.cards[0].votes[1].counted").value(false));

        vote(fixture, reviewer.getUser(), fixture.card().getId(),
                "APPROVED", null, oldVoteVersion.longValue())
                .andExpect(jsonPath("$.cards[0].votes[1].counted").value(true))
                .andExpect(jsonPath("$.cards[0].teamDecision").value("APPROVED"))
                .andExpect(jsonPath("$.sessionStatus").value("APPROVED"));
        assertThat(reviewerRepository.findById(reviewerAssignment.getId()).orElseThrow().getId())
                .isEqualTo(reviewerAssignment.getId());
    }

    @Test
    void losingEligibleRoleSoftRemovesAssignmentAndRecalculates() throws Exception {
        Fixture fixture = fixture("role-loss", true);
        WorkspaceMember reviewer = membership(fixture.workspace(), "role-loss-reviewer", WorkspaceRole.REVIEWER, MembershipStatus.ACTIVE);
        assignment(fixture, fixture.ownerMembership());
        ReviewSessionReviewer assignment = assignment(fixture, reviewer);
        vote(fixture, fixture.owner(), fixture.card().getId(), "APPROVED", null, null);
        vote(fixture, reviewer.getUser(), fixture.card().getId(), "REJECTED", "Rủi ro", null)
                .andExpect(jsonPath("$.sessionStatus").value("REJECTED"));

        memberService.changeMemberRole(
                fixture.workspace().getId(), reviewer.getId(),
                new UpdateWorkspaceMemberRoleRequest(WorkspaceRole.MEMBER),
                fixture.owner().getEmail()
        );

        assertThat(reviewerRepository.findById(assignment.getId()).orElseThrow().getStatus())
                .isEqualTo(ReviewSessionReviewerStatus.REMOVED);
        assertThat(cardRepository.findById(fixture.card().getId()).orElseThrow().getTeamDecision())
                .isEqualTo(TeamDecisionStatus.APPROVED);
        assertThat(sessionRepository.findById(fixture.session().getId()).orElseThrow().getStatus())
                .isEqualTo(SessionStatus.APPROVED);
    }

    @Test
    void zeroCardSessionRemainsApprovedWhenReviewerIsAssigned() {
        Fixture fixture = fixture("zero-card", false);
        reviewerService.assignReviewer(
                fixture.workspace().getId(), fixture.session().getId(),
                new com.microapproval.api.dto.AssignSessionReviewerRequest(fixture.ownerMembership().getId()),
                fixture.owner().getEmail()
        );

        assertThat(sessionRepository.findById(fixture.session().getId()).orElseThrow().getStatus())
                .isEqualTo(SessionStatus.APPROVED);
    }

    private org.springframework.test.web.servlet.ResultActions vote(
            Fixture fixture, User caller, String cardId, String decision, String note, Long version
    ) throws Exception {
        return vote(fixture.workspace().getId(), fixture.session().getId(), cardId,
                caller, decision, note, version);
    }

    private org.springframework.test.web.servlet.ResultActions vote(
            String workspaceId, String sessionId, String cardId, User caller,
            String decision, String note, Long version
    ) throws Exception {
        String noteJson = note == null ? "null" : "\"" + note + "\"";
        String versionJson = version == null ? "null" : version.toString();
        return mockMvc.perform(put(
                        "/api/workspaces/{workspaceId}/sessions/{sessionId}/cards/{cardId}/vote",
                        workspaceId, sessionId, cardId
                ).with(user(caller.getEmail()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"decision\":\"%s\",\"note\":%s,\"version\":%s}"
                        .formatted(decision, noteJson, versionJson)));
    }

    private org.springframework.test.web.servlet.ResultActions voteRaw(
            Fixture fixture, User caller, String body
    ) throws Exception {
        return mockMvc.perform(put(
                        "/api/workspaces/{workspaceId}/sessions/{sessionId}/cards/{cardId}/vote",
                        fixture.workspace().getId(), fixture.session().getId(), fixture.card().getId()
                ).with(user(caller.getEmail()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private String votesPath(Fixture fixture) {
        return votesPath(fixture.workspace().getId(), fixture.session().getId());
    }

    private String votesPath(String workspaceId, String sessionId) {
        return "/api/workspaces/" + workspaceId + "/sessions/" + sessionId + "/votes";
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
        MicroDecision decisionCard = withCard ? cardRepository.save(card(session, 0)) : null;
        return new Fixture(owner, workspace, ownerMembership, session, decisionCard);
    }

    private WorkspaceMember membership(
            Workspace workspace, String label, WorkspaceRole role, MembershipStatus status
    ) {
        return memberRepository.save(WorkspaceMember.builder()
                .workspace(workspace)
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
                .title("Session " + UUID.randomUUID())
                .workspaceType(type)
                .workspace(workspace)
                .mode(AnalysisMode.RAW_SNIPPET)
                .rawContent("return true;")
                .submittedBy(owner)
                .status(SessionStatus.PENDING)
                .aiAnalysisStatus(AiAnalysisStatus.NOT_REQUESTED)
                .build();
    }

    private MicroDecision card(ReviewSession session, int order) {
        return MicroDecision.builder()
                .session(session)
                .engineType(EngineType.RULE_BASED)
                .riskCategory(RiskCategory.SECURITY)
                .riskLevel(RiskLevel.HIGH)
                .questionText("Is this safe?")
                .codeSnippet("return true;")
                .displayOrder(order)
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
