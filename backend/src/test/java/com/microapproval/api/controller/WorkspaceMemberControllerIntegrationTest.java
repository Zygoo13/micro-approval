package com.microapproval.api.controller;

import com.microapproval.api.entity.MembershipStatus;
import com.microapproval.api.entity.User;
import com.microapproval.api.entity.Workspace;
import com.microapproval.api.entity.WorkspaceMember;
import com.microapproval.api.entity.WorkspaceRole;
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

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class WorkspaceMemberControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private WorkspaceMemberRepository workspaceMemberRepository;

    @Test
    void activeMemberListsActiveAndPendingMembersButNotRemovedMembers() throws Exception {
        Fixture fixture = createFixture("list");
        User activeUser = createUser("list-active");
        User pendingUser = createUser("list-pending");
        User removedUser = createUser("list-removed");
        createMembership(fixture.workspace(), activeUser, WorkspaceRole.MEMBER, MembershipStatus.ACTIVE);
        createMembership(fixture.workspace(), pendingUser, WorkspaceRole.REVIEWER, MembershipStatus.PENDING);
        createMembership(fixture.workspace(), removedUser, WorkspaceRole.AUDITOR, MembershipStatus.REMOVED);

        String response = mockMvc.perform(get(memberPath(fixture.workspace()))
                        .with(user(activeUser.getEmail())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].role").value("OWNER"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(response)
                .contains(activeUser.getEmail(), pendingUser.getEmail(), fixture.owner().getEmail())
                .doesNotContain(removedUser.getEmail());
    }

    @Test
    void nonMemberAndRemovedMemberCannotListMembers() throws Exception {
        Fixture fixture = createFixture("hidden-list");
        User outsider = createUser("hidden-outsider");
        User removed = createUser("hidden-removed");
        createMembership(fixture.workspace(), removed, WorkspaceRole.MEMBER, MembershipStatus.REMOVED);

        mockMvc.perform(get(memberPath(fixture.workspace())).with(user(outsider.getEmail())))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(memberPath(fixture.workspace())).with(user(removed.getEmail())))
                .andExpect(status().isNotFound());
    }

    @Test
    void ownerAddsReviewerAndAdmin() throws Exception {
        Fixture fixture = createFixture("owner-add");
        User reviewer = createUser("owner-add-reviewer");
        User admin = createUser("owner-add-admin");

        addMember(fixture, fixture.owner(), reviewer, WorkspaceRole.REVIEWER)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("REVIEWER"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        addMember(fixture, fixture.owner(), admin, WorkspaceRole.ADMIN)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"));

        assertThat(workspaceMemberRepository.findByWorkspaceIdAndUserId(
                fixture.workspace().getId(),
                admin.getId()
        )).isPresent();
    }

    @Test
    void adminAddsMemberButCannotAddAdminOrOwner() throws Exception {
        Fixture fixture = createFixture("admin-add");
        User admin = createUser("admin-add-caller");
        User member = createUser("admin-add-member");
        User anotherAdmin = createUser("admin-add-admin");
        User secondOwner = createUser("admin-add-owner");
        createMembership(fixture.workspace(), admin, WorkspaceRole.ADMIN, MembershipStatus.ACTIVE);

        addMember(fixture, admin, member, WorkspaceRole.MEMBER)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("MEMBER"));
        addMember(fixture, admin, anotherAdmin, WorkspaceRole.ADMIN)
                .andExpect(status().isForbidden());
        addMember(fixture, admin, secondOwner, WorkspaceRole.OWNER)
                .andExpect(status().isBadRequest());
    }

    @Test
    void addRejectsUnknownUserAndExistingActiveOrPendingMembership() throws Exception {
        Fixture fixture = createFixture("add-conflict");
        User active = createUser("add-active");
        User pending = createUser("add-pending");
        createMembership(fixture.workspace(), active, WorkspaceRole.MEMBER, MembershipStatus.ACTIVE);
        createMembership(fixture.workspace(), pending, WorkspaceRole.REVIEWER, MembershipStatus.PENDING);

        mockMvc.perform(post(memberPath(fixture.workspace()))
                        .with(user(fixture.owner().getEmail()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"missing@example.com","role":"MEMBER"}
                                """))
                .andExpect(status().isNotFound());
        addMember(fixture, fixture.owner(), active, WorkspaceRole.REVIEWER)
                .andExpect(status().isConflict());
        addMember(fixture, fixture.owner(), pending, WorkspaceRole.MEMBER)
                .andExpect(status().isConflict());
    }

    @Test
    void removedMembershipIsReactivatedWithoutCreatingAnotherRow() throws Exception {
        Fixture fixture = createFixture("reactivate");
        User targetUser = createUser("reactivate-target");
        LocalDateTime oldJoinedAt = LocalDateTime.now().minusDays(10);
        WorkspaceMember removed = createMembership(
                fixture.workspace(),
                targetUser,
                WorkspaceRole.MEMBER,
                MembershipStatus.REMOVED
        );
        removed.setJoinedAt(oldJoinedAt);
        workspaceMemberRepository.saveAndFlush(removed);

        addMember(fixture, fixture.owner(), targetUser, WorkspaceRole.AUDITOR)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.membershipId").value(removed.getId()))
                .andExpect(jsonPath("$.role").value("AUDITOR"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        WorkspaceMember reactivated = workspaceMemberRepository
                .findByWorkspaceIdAndUserId(fixture.workspace().getId(), targetUser.getId())
                .orElseThrow();
        long rowCount = workspaceMemberRepository.findAll().stream()
                .filter(member -> member.getWorkspace().getId().equals(fixture.workspace().getId()))
                .filter(member -> member.getUser().getId().equals(targetUser.getId()))
                .count();
        assertThat(rowCount).isOne();
        assertThat(reactivated.getJoinedAt()).isAfter(oldJoinedAt);
    }

    @Test
    void ownerCanPromoteMemberAndAdminCanChangeStandardRoles() throws Exception {
        Fixture fixture = createFixture("change-role");
        User admin = createUser("change-admin");
        User firstTarget = createUser("change-first");
        User secondTarget = createUser("change-second");
        WorkspaceMember adminMembership = createMembership(
                fixture.workspace(), admin, WorkspaceRole.ADMIN, MembershipStatus.ACTIVE
        );
        WorkspaceMember firstMembership = createMembership(
                fixture.workspace(), firstTarget, WorkspaceRole.MEMBER, MembershipStatus.ACTIVE
        );
        WorkspaceMember secondMembership = createMembership(
                fixture.workspace(), secondTarget, WorkspaceRole.MEMBER, MembershipStatus.ACTIVE
        );

        changeRole(fixture, fixture.owner(), firstMembership, WorkspaceRole.ADMIN)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"));
        changeRole(fixture, admin, secondMembership, WorkspaceRole.REVIEWER)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("REVIEWER"));

        assertThat(adminMembership.getRole()).isEqualTo(WorkspaceRole.ADMIN);
    }

    @Test
    void adminCannotManageAdminOrOwnerAndOwnerRoleCannotBeAssigned() throws Exception {
        Fixture fixture = createFixture("protected-role");
        User admin = createUser("protected-admin");
        User anotherAdmin = createUser("protected-other-admin");
        User member = createUser("protected-member");
        createMembership(fixture.workspace(), admin, WorkspaceRole.ADMIN, MembershipStatus.ACTIVE);
        WorkspaceMember anotherAdminMembership = createMembership(
                fixture.workspace(), anotherAdmin, WorkspaceRole.ADMIN, MembershipStatus.ACTIVE
        );
        WorkspaceMember memberMembership = createMembership(
                fixture.workspace(), member, WorkspaceRole.MEMBER, MembershipStatus.ACTIVE
        );

        changeRole(fixture, admin, anotherAdminMembership, WorkspaceRole.REVIEWER)
                .andExpect(status().isForbidden());
        changeRole(fixture, admin, fixture.ownerMembership(), WorkspaceRole.MEMBER)
                .andExpect(status().isBadRequest());
        changeRole(fixture, fixture.owner(), memberMembership, WorkspaceRole.OWNER)
                .andExpect(status().isBadRequest());
    }

    @Test
    void removedMembershipCannotChangeRoleAndRegularMemberCannotManageRoles() throws Exception {
        Fixture fixture = createFixture("denied-role");
        User regular = createUser("denied-regular");
        User removed = createUser("denied-removed");
        User target = createUser("denied-target");
        createMembership(fixture.workspace(), regular, WorkspaceRole.MEMBER, MembershipStatus.ACTIVE);
        WorkspaceMember removedMembership = createMembership(
                fixture.workspace(), removed, WorkspaceRole.MEMBER, MembershipStatus.REMOVED
        );
        WorkspaceMember targetMembership = createMembership(
                fixture.workspace(), target, WorkspaceRole.MEMBER, MembershipStatus.ACTIVE
        );

        changeRole(fixture, fixture.owner(), removedMembership, WorkspaceRole.REVIEWER)
                .andExpect(status().isConflict());
        changeRole(fixture, regular, targetMembership, WorkspaceRole.AUDITOR)
                .andExpect(status().isForbidden());
    }

    @Test
    void ownerRemovesAdminBySoftDelete() throws Exception {
        Fixture fixture = createFixture("owner-remove");
        User admin = createUser("owner-remove-admin");
        WorkspaceMember adminMembership = createMembership(
                fixture.workspace(), admin, WorkspaceRole.ADMIN, MembershipStatus.ACTIVE
        );

        removeMember(fixture, fixture.owner(), adminMembership)
                .andExpect(status().isNoContent());

        WorkspaceMember persisted = workspaceMemberRepository.findById(adminMembership.getId())
                .orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(MembershipStatus.REMOVED);
    }

    @Test
    void adminRemovesMemberButCannotRemoveAdminOrOwner() throws Exception {
        Fixture fixture = createFixture("admin-remove");
        User admin = createUser("admin-remove-caller");
        User otherAdmin = createUser("admin-remove-other");
        User member = createUser("admin-remove-member");
        createMembership(fixture.workspace(), admin, WorkspaceRole.ADMIN, MembershipStatus.ACTIVE);
        WorkspaceMember otherAdminMembership = createMembership(
                fixture.workspace(), otherAdmin, WorkspaceRole.ADMIN, MembershipStatus.ACTIVE
        );
        WorkspaceMember memberMembership = createMembership(
                fixture.workspace(), member, WorkspaceRole.MEMBER, MembershipStatus.ACTIVE
        );

        removeMember(fixture, admin, memberMembership)
                .andExpect(status().isNoContent());
        removeMember(fixture, admin, otherAdminMembership)
                .andExpect(status().isForbidden());
        removeMember(fixture, admin, fixture.ownerMembership())
                .andExpect(status().isBadRequest());
    }

    @Test
    void selfRemoveAndRepeatedRemoveAreRejectedAndRemovedMemberLosesAccess() throws Exception {
        Fixture fixture = createFixture("remove-state");
        User admin = createUser("remove-self-admin");
        User member = createUser("remove-access-member");
        WorkspaceMember adminMembership = createMembership(
                fixture.workspace(), admin, WorkspaceRole.ADMIN, MembershipStatus.ACTIVE
        );
        WorkspaceMember memberMembership = createMembership(
                fixture.workspace(), member, WorkspaceRole.MEMBER, MembershipStatus.ACTIVE
        );

        removeMember(fixture, admin, adminMembership)
                .andExpect(status().isBadRequest());
        removeMember(fixture, fixture.owner(), memberMembership)
                .andExpect(status().isNoContent());
        removeMember(fixture, fixture.owner(), memberMembership)
                .andExpect(status().isConflict());
        mockMvc.perform(get("/api/workspaces/{workspaceId}", fixture.workspace().getId())
                        .with(user(member.getEmail())))
                .andExpect(status().isNotFound());
    }

    @Test
    void ownerInvariantAndAuthenticationRemainEnforced() throws Exception {
        Fixture fixture = createFixture("invariant");
        User member = createUser("invariant-member");
        createMembership(fixture.workspace(), member, WorkspaceRole.MEMBER, MembershipStatus.ACTIVE);

        mockMvc.perform(get(memberPath(fixture.workspace())))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(memberPath(fixture.workspace()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"member@example.com","role":"MEMBER"}
                                """))
                .andExpect(status().isForbidden());

        long ownerCount = workspaceMemberRepository.findAll().stream()
                .filter(membership -> membership.getWorkspace().getId()
                        .equals(fixture.workspace().getId()))
                .filter(membership -> membership.getRole() == WorkspaceRole.OWNER)
                .filter(membership -> membership.getStatus() == MembershipStatus.ACTIVE)
                .count();
        assertThat(ownerCount).isOne();
    }

    private org.springframework.test.web.servlet.ResultActions addMember(
            Fixture fixture,
            User caller,
            User target,
            WorkspaceRole role
    ) throws Exception {
        return mockMvc.perform(post(memberPath(fixture.workspace()))
                .with(user(caller.getEmail()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"%s","role":"%s"}
                        """.formatted(target.getEmail(), role)));
    }

    private org.springframework.test.web.servlet.ResultActions changeRole(
            Fixture fixture,
            User caller,
            WorkspaceMember target,
            WorkspaceRole role
    ) throws Exception {
        return mockMvc.perform(patch(
                        "/api/workspaces/{workspaceId}/members/{memberId}/role",
                        fixture.workspace().getId(),
                        target.getId()
                )
                .with(user(caller.getEmail()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"role":"%s"}
                        """.formatted(role)));
    }

    private org.springframework.test.web.servlet.ResultActions removeMember(
            Fixture fixture,
            User caller,
            WorkspaceMember target
    ) throws Exception {
        return mockMvc.perform(delete(
                        "/api/workspaces/{workspaceId}/members/{memberId}",
                        fixture.workspace().getId(),
                        target.getId()
                )
                .with(user(caller.getEmail())));
    }

    private String memberPath(Workspace workspace) {
        return "/api/workspaces/" + workspace.getId() + "/members";
    }

    private Fixture createFixture(String label) {
        User owner = createUser(label + "-owner");
        Workspace workspace = workspaceRepository.save(Workspace.builder()
                .name("Workspace " + label)
                .owner(owner)
                .build());
        WorkspaceMember ownerMembership = createMembership(
                workspace,
                owner,
                WorkspaceRole.OWNER,
                MembershipStatus.ACTIVE
        );
        return new Fixture(owner, workspace, ownerMembership);
    }

    private User createUser(String label) {
        String unique = UUID.randomUUID().toString();
        return userRepository.save(User.builder()
                .fullName("Test " + label)
                .email(label + "-" + unique + "@example.com")
                .passwordHash("test-password-hash")
                .build());
    }

    private WorkspaceMember createMembership(
            Workspace workspace,
            User user,
            WorkspaceRole role,
            MembershipStatus status
    ) {
        return workspaceMemberRepository.save(WorkspaceMember.builder()
                .workspace(workspace)
                .user(user)
                .role(role)
                .status(status)
                .build());
    }

    private record Fixture(
            User owner,
            Workspace workspace,
            WorkspaceMember ownerMembership
    ) {
    }
}
