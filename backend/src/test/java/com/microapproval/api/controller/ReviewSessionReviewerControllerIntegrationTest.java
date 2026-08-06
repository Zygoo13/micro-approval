package com.microapproval.api.controller;

import com.jayway.jsonpath.JsonPath;
import com.microapproval.api.entity.AiAnalysisStatus;
import com.microapproval.api.entity.AnalysisMode;
import com.microapproval.api.entity.MembershipStatus;
import com.microapproval.api.entity.ReviewSession;
import com.microapproval.api.entity.ReviewSessionReviewer;
import com.microapproval.api.entity.ReviewSessionReviewerStatus;
import com.microapproval.api.entity.SessionStatus;
import com.microapproval.api.entity.User;
import com.microapproval.api.entity.Workspace;
import com.microapproval.api.entity.WorkspaceMember;
import com.microapproval.api.entity.WorkspaceRole;
import com.microapproval.api.entity.WorkspaceType;
import com.microapproval.api.repository.ReviewSessionRepository;
import com.microapproval.api.repository.ReviewSessionReviewerRepository;
import com.microapproval.api.repository.TeamReviewAuditEventRepository;
import com.microapproval.api.repository.UserRepository;
import com.microapproval.api.repository.WorkspaceMemberRepository;
import com.microapproval.api.repository.WorkspaceRepository;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ReviewSessionReviewerControllerIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private WorkspaceRepository workspaceRepository;
    @Autowired private WorkspaceMemberRepository memberRepository;
    @Autowired private ReviewSessionRepository sessionRepository;
    @Autowired private ReviewSessionReviewerRepository reviewerRepository;
    @Autowired private TeamReviewAuditEventRepository auditRepository;

    @Test
    void allActiveRolesCanViewOnlyAssignedReviewers() throws Exception {
        Fixture fixture = fixture("view");
        WorkspaceMember reviewer = membership(fixture.workspace(), "view-reviewer", WorkspaceRole.REVIEWER, MembershipStatus.ACTIVE);
        WorkspaceMember member = membership(fixture.workspace(), "view-member", WorkspaceRole.MEMBER, MembershipStatus.ACTIVE);
        WorkspaceMember auditor = membership(fixture.workspace(), "view-auditor", WorkspaceRole.AUDITOR, MembershipStatus.ACTIVE);
        ReviewSessionReviewer assigned = assignment(fixture, reviewer, ReviewSessionReviewerStatus.ASSIGNED);
        assignment(fixture, fixture.ownerMembership(), ReviewSessionReviewerStatus.REMOVED);

        for (User caller : new User[]{fixture.owner(), member.getUser(), auditor.getUser()}) {
            mockMvc.perform(get(path(fixture)).with(user(caller.getEmail())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].assignmentId").value(assigned.getId()))
                    .andExpect(jsonPath("$[0].workspaceMemberId").value(reviewer.getId()))
                    .andExpect(jsonPath("$[0].workspaceRole").value("REVIEWER"));
        }
    }

    @Test
    void inactiveAndNonMembersCannotDiscoverRoster() throws Exception {
        Fixture fixture = fixture("hidden");
        User outsider = createUser("hidden-outsider");
        WorkspaceMember pending = membership(fixture.workspace(), "hidden-pending", WorkspaceRole.REVIEWER, MembershipStatus.PENDING);
        WorkspaceMember removed = membership(fixture.workspace(), "hidden-removed", WorkspaceRole.REVIEWER, MembershipStatus.REMOVED);

        for (User caller : new User[]{outsider, pending.getUser(), removed.getUser()}) {
            mockMvc.perform(get(path(fixture)).with(user(caller.getEmail())))
                    .andExpect(status().isNotFound());
        }
        mockMvc.perform(get(path(fixture))).andExpect(status().isForbidden());
    }

    @Test
    void personalAndCrossWorkspaceSessionsAreHidden() throws Exception {
        Fixture fixture = fixture("scope");
        ReviewSession personal = sessionRepository.save(session(
                fixture.owner(), null, WorkspaceType.PERSONAL
        ));
        Fixture other = fixture("scope-other");

        mockMvc.perform(get(path(fixture.workspace().getId(), personal.getId()))
                        .with(user(fixture.owner().getEmail())))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(path(other.workspace().getId(), fixture.session().getId()))
                        .with(user(other.owner().getEmail())))
                .andExpect(status().isNotFound());
    }

    @Test
    void ownerAndAdminAssignEveryEligibleRoleIncludingThemselves() throws Exception {
        Fixture fixture = fixture("eligible");
        WorkspaceMember admin = membership(fixture.workspace(), "eligible-admin", WorkspaceRole.ADMIN, MembershipStatus.ACTIVE);
        WorkspaceMember secondAdmin = membership(fixture.workspace(), "eligible-second-admin", WorkspaceRole.ADMIN, MembershipStatus.ACTIVE);
        WorkspaceMember reviewer = membership(fixture.workspace(), "eligible-reviewer", WorkspaceRole.REVIEWER, MembershipStatus.ACTIVE);

        assign(fixture, fixture.owner(), fixture.ownerMembership())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workspaceRole").value("OWNER"));
        assign(fixture, fixture.owner(), admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workspaceRole").value("ADMIN"));
        assign(fixture, admin.getUser(), secondAdmin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workspaceRole").value("ADMIN"));
        assign(fixture, admin.getUser(), reviewer)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workspaceRole").value("REVIEWER"));

        assertThat(reviewerRepository.findAll()).hasSize(4);
        assertThat(auditRepository.countBySessionId(fixture.session().getId())).isEqualTo(4);
    }

    @Test
    void ineligibleRoleOrMembershipStateReturnsBadRequest() throws Exception {
        Fixture fixture = fixture("invalid-target");
        WorkspaceMember member = membership(fixture.workspace(), "invalid-member", WorkspaceRole.MEMBER, MembershipStatus.ACTIVE);
        WorkspaceMember auditor = membership(fixture.workspace(), "invalid-auditor", WorkspaceRole.AUDITOR, MembershipStatus.ACTIVE);
        WorkspaceMember pending = membership(fixture.workspace(), "invalid-pending", WorkspaceRole.REVIEWER, MembershipStatus.PENDING);
        WorkspaceMember removed = membership(fixture.workspace(), "invalid-removed", WorkspaceRole.REVIEWER, MembershipStatus.REMOVED);

        for (WorkspaceMember target : new WorkspaceMember[]{member, auditor, pending, removed}) {
            assign(fixture, fixture.owner(), target).andExpect(status().isBadRequest());
        }
        assertThat(reviewerRepository.findAll()).isEmpty();
    }

    @Test
    void membershipFromAnotherWorkspaceIsHidden() throws Exception {
        Fixture fixture = fixture("member-scope");
        Fixture other = fixture("member-scope-other");

        assign(fixture, fixture.owner(), other.ownerMembership())
                .andExpect(status().isNotFound());
    }

    @Test
    void duplicateConflictsAndRemovedAssignmentReactivatesSameRow() throws Exception {
        Fixture fixture = fixture("reactivate");
        WorkspaceMember reviewer = membership(fixture.workspace(), "reactivate-reviewer", WorkspaceRole.REVIEWER, MembershipStatus.ACTIVE);
        String first = assign(fixture, fixture.owner(), reviewer)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String assignmentId = JsonPath.read(first, "$.assignmentId");

        assign(fixture, fixture.owner(), reviewer).andExpect(status().isConflict());
        remove(fixture, fixture.owner(), assignmentId, "Đổi người review")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REMOVED"));
        assign(fixture, fixture.owner(), reviewer)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignmentId").value(assignmentId))
                .andExpect(jsonPath("$.status").value("ASSIGNED"))
                .andExpect(jsonPath("$.removedAt").doesNotExist())
                .andExpect(jsonPath("$.removalReason").doesNotExist());

        assertThat(reviewerRepository.findAll()).hasSize(1);
        assertThat(auditRepository.countBySessionId(fixture.session().getId())).isEqualTo(3);
    }

    @Test
    void regularActiveMemberCannotMutateAndOutsiderIsHidden() throws Exception {
        Fixture fixture = fixture("mutation-auth");
        WorkspaceMember reviewer = membership(fixture.workspace(), "mutation-reviewer", WorkspaceRole.REVIEWER, MembershipStatus.ACTIVE);
        WorkspaceMember member = membership(fixture.workspace(), "mutation-member", WorkspaceRole.MEMBER, MembershipStatus.ACTIVE);
        User outsider = createUser("mutation-outsider");

        assign(fixture, member.getUser(), reviewer).andExpect(status().isForbidden());
        assign(fixture, outsider, reviewer).andExpect(status().isNotFound());

        String body = assign(fixture, fixture.owner(), reviewer)
                .andReturn().getResponse().getContentAsString();
        String assignmentId = JsonPath.read(body, "$.assignmentId");
        remove(fixture, member.getUser(), assignmentId, "Không có quyền")
                .andExpect(status().isForbidden());
        remove(fixture, outsider, assignmentId, "Không được thấy")
                .andExpect(status().isNotFound());
    }

    @Test
    void removeIsAuditedSoftDeleteWithRequiredReasonAndRepeatConflict() throws Exception {
        Fixture fixture = fixture("remove");
        WorkspaceMember admin = membership(fixture.workspace(), "remove-admin", WorkspaceRole.ADMIN, MembershipStatus.ACTIVE);
        String body = assign(fixture, fixture.owner(), admin)
                .andReturn().getResponse().getContentAsString();
        String assignmentId = JsonPath.read(body, "$.assignmentId");

        remove(fixture, admin.getUser(), assignmentId, "  Không còn tham gia phiên  ")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REMOVED"))
                .andExpect(jsonPath("$.removedByUserId").value(admin.getUser().getId()))
                .andExpect(jsonPath("$.removalReason").value("Không còn tham gia phiên"));
        remove(fixture, fixture.owner(), assignmentId, "Lặp lại")
                .andExpect(status().isConflict());
        remove(fixture, fixture.owner(), assignmentId, "   ")
                .andExpect(status().isBadRequest());

        ReviewSessionReviewer persisted = reviewerRepository.findById(assignmentId).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(ReviewSessionReviewerStatus.REMOVED);
        assertThat(persisted.getRemovalReason()).isEqualTo("Không còn tham gia phiên");
        assertThat(reviewerRepository.findAll()).hasSize(1);
        assertThat(auditRepository.countBySessionId(fixture.session().getId())).isEqualTo(2);
    }

    @Test
    void assignmentFromAnotherSessionAndPersonalSessionAreHiddenOnRemove() throws Exception {
        Fixture fixture = fixture("remove-scope");
        WorkspaceMember reviewer = membership(fixture.workspace(), "remove-scope-reviewer", WorkspaceRole.REVIEWER, MembershipStatus.ACTIVE);
        ReviewSession otherSession = sessionRepository.save(session(
                fixture.owner(), fixture.workspace(), WorkspaceType.SHARED
        ));
        ReviewSessionReviewer otherAssignment = reviewerRepository.save(ReviewSessionReviewer.builder()
                .session(otherSession)
                .workspaceMember(reviewer)
                .assignedBy(fixture.owner())
                .status(ReviewSessionReviewerStatus.ASSIGNED)
                .build());
        ReviewSession personal = sessionRepository.save(session(
                fixture.owner(), null, WorkspaceType.PERSONAL
        ));

        mockMvc.perform(post(path(fixture.workspace().getId(), personal.getId()))
                        .with(user(fixture.owner().getEmail()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"workspaceMemberId\":\"%s\"}".formatted(reviewer.getId())))
                .andExpect(status().isNotFound());

        remove(fixture, fixture.owner(), otherAssignment.getId(), "Sai session")
                .andExpect(status().isNotFound());
        mockMvc.perform(post(
                        "/api/workspaces/{workspaceId}/sessions/{sessionId}/reviewers/{assignmentId}/remove",
                        fixture.workspace().getId(), personal.getId(), otherAssignment.getId()
                ).with(user(fixture.owner().getEmail()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Personal không hợp lệ\"}"))
                .andExpect(status().isNotFound());
    }

    private org.springframework.test.web.servlet.ResultActions assign(
            Fixture fixture,
            User caller,
            WorkspaceMember target
    ) throws Exception {
        return mockMvc.perform(post(path(fixture))
                .with(user(caller.getEmail()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"workspaceMemberId\":\"%s\"}".formatted(target.getId())));
    }

    private org.springframework.test.web.servlet.ResultActions remove(
            Fixture fixture,
            User caller,
            String assignmentId,
            String reason
    ) throws Exception {
        return mockMvc.perform(post(path(fixture) + "/{assignmentId}/remove", assignmentId)
                .with(user(caller.getEmail()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"%s\"}".formatted(reason)));
    }

    private String path(Fixture fixture) {
        return path(fixture.workspace().getId(), fixture.session().getId());
    }

    private String path(String workspaceId, String sessionId) {
        return "/api/workspaces/" + workspaceId + "/sessions/" + sessionId + "/reviewers";
    }

    private Fixture fixture(String label) {
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
        return new Fixture(owner, workspace, ownerMembership, session);
    }

    private WorkspaceMember membership(
            Workspace workspace,
            String label,
            WorkspaceRole role,
            MembershipStatus status
    ) {
        return memberRepository.save(WorkspaceMember.builder()
                .workspace(workspace)
                .user(createUser(label))
                .role(role)
                .status(status)
                .build());
    }

    private ReviewSessionReviewer assignment(
            Fixture fixture,
            WorkspaceMember member,
            ReviewSessionReviewerStatus status
    ) {
        ReviewSessionReviewer assignment = ReviewSessionReviewer.builder()
                .session(fixture.session())
                .workspaceMember(member)
                .assignedBy(fixture.owner())
                .status(status)
                .build();
        if (status == ReviewSessionReviewerStatus.REMOVED) {
            assignment.setRemovedAt(java.time.LocalDateTime.now());
            assignment.setRemovedBy(fixture.owner());
            assignment.setRemovalReason("Test removed");
        }
        return reviewerRepository.save(assignment);
    }

    private ReviewSession session(User submitter, Workspace workspace, WorkspaceType type) {
        return ReviewSession.builder()
                .title("Session " + UUID.randomUUID())
                .workspaceType(type)
                .workspace(workspace)
                .mode(AnalysisMode.RAW_SNIPPET)
                .rawContent("return true;")
                .submittedBy(submitter)
                .status(SessionStatus.PENDING)
                .aiAnalysisStatus(AiAnalysisStatus.NOT_REQUESTED)
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
            ReviewSession session
    ) {
    }
}
