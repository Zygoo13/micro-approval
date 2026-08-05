package com.microapproval.api.controller;

import com.microapproval.api.entity.MembershipStatus;
import com.microapproval.api.entity.User;
import com.microapproval.api.entity.Workspace;
import com.microapproval.api.entity.WorkspaceInvitation;
import com.microapproval.api.entity.WorkspaceInvitationStatus;
import com.microapproval.api.entity.WorkspaceMember;
import com.microapproval.api.entity.WorkspaceRole;
import com.microapproval.api.repository.UserRepository;
import com.microapproval.api.repository.WorkspaceInvitationRepository;
import com.microapproval.api.repository.WorkspaceMemberRepository;
import com.microapproval.api.repository.WorkspaceRepository;
import com.microapproval.api.exception.ConflictException;
import com.microapproval.api.service.WorkspaceInvitationService;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class WorkspaceInvitationControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private WorkspaceMemberRepository memberRepository;

    @Autowired
    private WorkspaceInvitationRepository invitationRepository;

    @Autowired
    private WorkspaceInvitationService invitationService;

    @Test
    void ownerInvitesReviewerAndAdminAndNormalizesEmail() throws Exception {
        TestWorkspace test = createWorkspace("owner-create");

        mockMvc.perform(post("/api/workspaces/{workspaceId}/invitations", test.workspace().getId())
                        .with(user(test.owner().getEmail()))
                        .contentType("application/json")
                        .content(invitationPayload("  New.User@Example.COM  ", "REVIEWER")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("new.user@example.com"))
                .andExpect(jsonPath("$.role").value("REVIEWER"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.expiresAt").exists());

        mockMvc.perform(post("/api/workspaces/{workspaceId}/invitations", test.workspace().getId())
                        .with(user(test.owner().getEmail()))
                        .contentType("application/json")
                        .content(invitationPayload("admin-invite@example.com", "ADMIN")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    void adminInvitesStandardRoleButCannotInviteAdminAndNobodyCanInviteOwner() throws Exception {
        TestWorkspace test = createWorkspace("admin-create");
        User admin = createUser("admin-create-manager");
        createMembership(test.workspace(), admin, WorkspaceRole.ADMIN, MembershipStatus.ACTIVE);

        mockMvc.perform(post("/api/workspaces/{workspaceId}/invitations", test.workspace().getId())
                        .with(user(admin.getEmail()))
                        .contentType("application/json")
                        .content(invitationPayload("member-invite@example.com", "MEMBER")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/workspaces/{workspaceId}/invitations", test.workspace().getId())
                        .with(user(admin.getEmail()))
                        .contentType("application/json")
                        .content(invitationPayload("another-admin@example.com", "ADMIN")))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/workspaces/{workspaceId}/invitations", test.workspace().getId())
                        .with(user(test.owner().getEmail()))
                        .contentType("application/json")
                        .content(invitationPayload("second-owner@example.com", "OWNER")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void activeAndPendingMembershipsCannotBeInvitedButRemovedCan() throws Exception {
        TestWorkspace test = createWorkspace("membership-create");
        User active = createUser("active-invite");
        User pending = createUser("pending-invite");
        User removed = createUser("removed-invite");
        createMembership(test.workspace(), active, WorkspaceRole.MEMBER, MembershipStatus.ACTIVE);
        createMembership(test.workspace(), pending, WorkspaceRole.MEMBER, MembershipStatus.PENDING);
        createMembership(test.workspace(), removed, WorkspaceRole.MEMBER, MembershipStatus.REMOVED);

        assertCreateConflict(test, active.getEmail());
        assertCreateConflict(test, pending.getEmail());

        mockMvc.perform(post("/api/workspaces/{workspaceId}/invitations", test.workspace().getId())
                        .with(user(test.owner().getEmail()))
                        .contentType("application/json")
                        .content(invitationPayload(removed.getEmail(), "REVIEWER")))
                .andExpect(status().isCreated());
    }

    @Test
    void unregisteredEmailCanBeInvitedAndDuplicatePendingConflicts() throws Exception {
        TestWorkspace test = createWorkspace("unregistered-create");
        String email = "future-user-" + UUID.randomUUID() + "@example.com";

        mockMvc.perform(post("/api/workspaces/{workspaceId}/invitations", test.workspace().getId())
                        .with(user(test.owner().getEmail()))
                        .contentType("application/json")
                        .content(invitationPayload(email, "AUDITOR")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/workspaces/{workspaceId}/invitations", test.workspace().getId())
                        .with(user(test.owner().getEmail()))
                        .contentType("application/json")
                        .content(invitationPayload(email.toUpperCase(), "MEMBER")))
                .andExpect(status().isConflict());

        User newlyRegistered = createUserWithEmail("Future User", email);
        WorkspaceInvitation invitation = invitationRepository
                .findAllWithWorkspaceAndInviterByEmail(email)
                .getFirst();
        mockMvc.perform(post("/api/workspace-invitations/{id}/accept", invitation.getId())
                        .with(user(newlyRegistered.getEmail())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));
        assertThat(memberRepository.existsByWorkspaceIdAndUserId(
                test.workspace().getId(),
                newlyRegistered.getId()
        )).isTrue();
    }

    @Test
    void databasePreventsConcurrentPendingDuplicatesAndRepositoryUsesWriteLocks() throws Exception {
        TestWorkspace test = createWorkspace("constraint-create");
        String email = "constraint-" + UUID.randomUUID() + "@example.com";
        createInvitation(test.workspace(), test.owner(), email, WorkspaceRole.MEMBER, null);

        WorkspaceInvitation duplicate = invitation(test.workspace(), test.owner(), email,
                WorkspaceRole.REVIEWER, null);
        assertThatThrownBy(() -> invitationRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);

        Method lockMethod = WorkspaceInvitationRepository.class.getMethod(
                "findWithWorkspaceAndInviterByIdForUpdate",
                String.class
        );
        assertThat(lockMethod.getAnnotation(org.springframework.data.jpa.repository.Lock.class).value())
                .isEqualTo(LockModeType.PESSIMISTIC_WRITE);
    }

    @Test
    void ownerAndAdminListInvitationsWhileMemberAndOutsiderAreDenied() throws Exception {
        TestWorkspace test = createWorkspace("list-invitations");
        User admin = createUser("list-admin");
        User member = createUser("list-member");
        User outsider = createUser("list-outsider");
        createMembership(test.workspace(), admin, WorkspaceRole.ADMIN, MembershipStatus.ACTIVE);
        createMembership(test.workspace(), member, WorkspaceRole.MEMBER, MembershipStatus.ACTIVE);
        createInvitation(test.workspace(), test.owner(), "listed@example.com", WorkspaceRole.REVIEWER, null);

        mockMvc.perform(get("/api/workspaces/{workspaceId}/invitations", test.workspace().getId())
                        .with(user(test.owner().getEmail())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].email").value("listed@example.com"));

        mockMvc.perform(get("/api/workspaces/{workspaceId}/invitations", test.workspace().getId())
                        .with(user(admin.getEmail())))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/workspaces/{workspaceId}/invitations", test.workspace().getId())
                        .with(user(member.getEmail())))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/workspaces/{workspaceId}/invitations", test.workspace().getId())
                        .with(user(outsider.getEmail())))
                .andExpect(status().isNotFound());
    }

    @Test
    void mineReturnsOnlyRecipientEmailAndComputesExpiredStatus() throws Exception {
        TestWorkspace test = createWorkspace("mine-invitations");
        User recipient = createUser("mine-recipient");
        createInvitation(test.workspace(), test.owner(), recipient.getEmail(), WorkspaceRole.MEMBER,
                LocalDateTime.now().minusDays(1));
        createInvitation(test.workspace(), test.owner(), "someone-else@example.com", WorkspaceRole.MEMBER,
                null);

        mockMvc.perform(get("/api/workspace-invitations/mine")
                        .with(user(recipient.getEmail())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].workspaceName").value(test.workspace().getName()))
                .andExpect(jsonPath("$[0].status").value("EXPIRED"));
    }

    @Test
    void recipientAcceptCreatesActiveMembershipAndPreservesSingleOwner() throws Exception {
        TestWorkspace test = createWorkspace("accept-new");
        User recipient = createUser("accept-recipient");
        WorkspaceInvitation invitation = createInvitation(
                test.workspace(), test.owner(), recipient.getEmail(), WorkspaceRole.REVIEWER, null
        );

        mockMvc.perform(post("/api/workspace-invitations/{id}/accept", invitation.getId())
                        .with(user(recipient.getEmail())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));

        WorkspaceMember membership = memberRepository
                .findByWorkspaceIdAndUserId(test.workspace().getId(), recipient.getId())
                .orElseThrow();
        assertThat(membership.getStatus()).isEqualTo(MembershipStatus.ACTIVE);
        assertThat(membership.getRole()).isEqualTo(WorkspaceRole.REVIEWER);
        assertThat(countActiveOwners(test.workspace().getId())).isEqualTo(1);
    }

    @Test
    void acceptReactivatesRemovedMembershipUsingSameRow() throws Exception {
        TestWorkspace test = createWorkspace("accept-removed");
        User recipient = createUser("accept-removed-recipient");
        WorkspaceMember removed = createMembership(
                test.workspace(), recipient, WorkspaceRole.MEMBER, MembershipStatus.REMOVED
        );
        LocalDateTime oldJoinedAt = LocalDateTime.now().minusDays(10);
        removed.setJoinedAt(oldJoinedAt);
        WorkspaceInvitation invitation = createInvitation(
                test.workspace(), test.owner(), recipient.getEmail(), WorkspaceRole.AUDITOR, null
        );

        mockMvc.perform(post("/api/workspace-invitations/{id}/accept", invitation.getId())
                        .with(user(recipient.getEmail())))
                .andExpect(status().isOk());

        WorkspaceMember reactivated = memberRepository
                .findByWorkspaceIdAndUserId(test.workspace().getId(), recipient.getId())
                .orElseThrow();
        assertThat(reactivated.getId()).isEqualTo(removed.getId());
        assertThat(reactivated.getStatus()).isEqualTo(MembershipStatus.ACTIVE);
        assertThat(reactivated.getRole()).isEqualTo(WorkspaceRole.AUDITOR);
        assertThat(reactivated.getJoinedAt()).isAfter(oldJoinedAt);
    }

    @Test
    void wrongRecipientCannotSeeOrAcceptInvitation() throws Exception {
        TestWorkspace test = createWorkspace("accept-wrong-user");
        User recipient = createUser("right-recipient");
        User wrongUser = createUser("wrong-recipient");
        WorkspaceInvitation invitation = createInvitation(
                test.workspace(), test.owner(), recipient.getEmail(), WorkspaceRole.MEMBER, null
        );

        mockMvc.perform(post("/api/workspace-invitations/{id}/accept", invitation.getId())
                        .with(user(wrongUser.getEmail())))
                .andExpect(status().isNotFound());

        assertThat(memberRepository.existsByWorkspaceIdAndUserId(
                test.workspace().getId(),
                wrongUser.getId()
        )).isFalse();
    }

    @Test
    void expiredInvitationCannotBeAcceptedAndPersistsExpiredStatus() throws Exception {
        TestWorkspace test = createWorkspace("accept-expired");
        User recipient = createUser("expired-recipient");
        WorkspaceInvitation invitation = createInvitation(
                test.workspace(), test.owner(), recipient.getEmail(), WorkspaceRole.MEMBER,
                LocalDateTime.now().minusMinutes(1)
        );

        mockMvc.perform(post("/api/workspace-invitations/{id}/accept", invitation.getId())
                        .with(user(recipient.getEmail())))
                .andExpect(status().isGone());

        assertThat(invitationRepository.findById(invitation.getId()).orElseThrow().getStatus())
                .isEqualTo(WorkspaceInvitationStatus.EXPIRED);
        assertThat(memberRepository.existsByWorkspaceIdAndUserId(
                test.workspace().getId(), recipient.getId()
        )).isFalse();
    }

    @Test
    void recipientRejectsOnceWithoutCreatingMembership() throws Exception {
        TestWorkspace test = createWorkspace("reject-invitation");
        User recipient = createUser("reject-recipient");
        WorkspaceInvitation invitation = createInvitation(
                test.workspace(), test.owner(), recipient.getEmail(), WorkspaceRole.MEMBER, null
        );

        mockMvc.perform(post("/api/workspace-invitations/{id}/reject", invitation.getId())
                        .with(user(recipient.getEmail())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));

        mockMvc.perform(post("/api/workspace-invitations/{id}/reject", invitation.getId())
                        .with(user(recipient.getEmail())))
                .andExpect(status().isConflict());

        assertThat(memberRepository.existsByWorkspaceIdAndUserId(
                test.workspace().getId(), recipient.getId()
        )).isFalse();
    }

    @Test
    void ownerRevokesPendingAndRecipientCannotAcceptIt() throws Exception {
        TestWorkspace test = createWorkspace("revoke-owner");
        User recipient = createUser("revoke-owner-recipient");
        WorkspaceInvitation invitation = createInvitation(
                test.workspace(), test.owner(), recipient.getEmail(), WorkspaceRole.ADMIN, null
        );

        mockMvc.perform(post("/api/workspaces/{workspaceId}/invitations/{id}/revoke",
                        test.workspace().getId(), invitation.getId())
                        .with(user(test.owner().getEmail())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REVOKED"));

        mockMvc.perform(post("/api/workspace-invitations/{id}/accept", invitation.getId())
                        .with(user(recipient.getEmail())))
                .andExpect(status().isConflict());
    }

    @Test
    void adminRevokesStandardRoleButCannotRevokeAdminInvitation() throws Exception {
        TestWorkspace test = createWorkspace("revoke-admin");
        User admin = createUser("revoke-admin-manager");
        createMembership(test.workspace(), admin, WorkspaceRole.ADMIN, MembershipStatus.ACTIVE);
        WorkspaceInvitation standard = createInvitation(
                test.workspace(), test.owner(), "standard-revoke@example.com", WorkspaceRole.MEMBER, null
        );
        WorkspaceInvitation adminInvitation = createInvitation(
                test.workspace(), test.owner(), "admin-revoke@example.com", WorkspaceRole.ADMIN, null
        );

        mockMvc.perform(post("/api/workspaces/{workspaceId}/invitations/{id}/revoke",
                        test.workspace().getId(), standard.getId())
                        .with(user(admin.getEmail())))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/workspaces/{workspaceId}/invitations/{id}/revoke",
                        test.workspace().getId(), adminInvitation.getId())
                        .with(user(admin.getEmail())))
                .andExpect(status().isForbidden());
    }

    @Test
    void acceptedAndRejectedInvitationsCannotTransitionAgain() throws Exception {
        TestWorkspace test = createWorkspace("processed-state");
        User acceptedRecipient = createUser("accepted-state-recipient");
        User rejectedRecipient = createUser("rejected-state-recipient");
        WorkspaceInvitation accepted = createInvitation(
                test.workspace(), test.owner(), acceptedRecipient.getEmail(), WorkspaceRole.MEMBER, null
        );
        WorkspaceInvitation rejected = createInvitation(
                test.workspace(), test.owner(), rejectedRecipient.getEmail(), WorkspaceRole.MEMBER, null
        );

        mockMvc.perform(post("/api/workspace-invitations/{id}/accept", accepted.getId())
                        .with(user(acceptedRecipient.getEmail())))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/workspace-invitations/{id}/reject", rejected.getId())
                        .with(user(rejectedRecipient.getEmail())))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/workspace-invitations/{id}/accept", accepted.getId())
                        .with(user(acceptedRecipient.getEmail())))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/workspace-invitations/{id}/accept", rejected.getId())
                        .with(user(rejectedRecipient.getEmail())))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/workspaces/{workspaceId}/invitations/{id}/revoke",
                        test.workspace().getId(), accepted.getId())
                        .with(user(test.owner().getEmail())))
                .andExpect(status().isConflict());
    }

    @Test
    void invitationEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/workspace-invitations/mine"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/workspaces/workspace-id/invitations"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/workspace-invitations/invitation-id/accept"))
                .andExpect(status().isForbidden());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentAcceptCreatesOneMembershipAndOneAcceptedTransition() throws Exception {
        TestWorkspace test = createWorkspace("concurrent-accept");
        User recipient = createUser("concurrent-recipient");
        WorkspaceInvitation invitation = createInvitation(
                test.workspace(), test.owner(), recipient.getEmail(), WorkspaceRole.REVIEWER, null
        );
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try {
            var task = (java.util.concurrent.Callable<String>) () -> {
                ready.countDown();
                start.await();
                try {
                    invitationService.acceptInvitation(invitation.getId(), recipient.getEmail());
                    return "ACCEPTED";
                } catch (ConflictException exception) {
                    return "CONFLICT";
                }
            };
            Future<String> first = executor.submit(task);
            Future<String> second = executor.submit(task);
            ready.await();
            start.countDown();

            assertThat(List.of(first.get(), second.get()))
                    .containsExactlyInAnyOrder("ACCEPTED", "CONFLICT");
            assertThat(memberRepository.findAll().stream()
                    .filter(member -> member.getWorkspace().getId().equals(test.workspace().getId()))
                    .filter(member -> member.getUser().getId().equals(recipient.getId())))
                    .hasSize(1);
            assertThat(invitationRepository.findById(invitation.getId()).orElseThrow().getStatus())
                    .isEqualTo(WorkspaceInvitationStatus.ACCEPTED);
            assertThat(countActiveOwners(test.workspace().getId())).isEqualTo(1);
        } finally {
            executor.shutdownNow();
            workspaceRepository.deleteById(test.workspace().getId());
            userRepository.deleteById(recipient.getId());
            userRepository.deleteById(test.owner().getId());
        }
    }

    private void assertCreateConflict(TestWorkspace test, String email) throws Exception {
        mockMvc.perform(post("/api/workspaces/{workspaceId}/invitations", test.workspace().getId())
                        .with(user(test.owner().getEmail()))
                        .contentType("application/json")
                        .content(invitationPayload(email, "MEMBER")))
                .andExpect(status().isConflict());
    }

    private TestWorkspace createWorkspace(String label) {
        User owner = createUser(label + "-owner");
        Workspace workspace = workspaceRepository.save(Workspace.builder()
                .name("Workspace " + label)
                .owner(owner)
                .build());
        createMembership(workspace, owner, WorkspaceRole.OWNER, MembershipStatus.ACTIVE);
        return new TestWorkspace(workspace, owner);
    }

    private User createUser(String label) {
        return createUserWithEmail(
                "Test " + label,
                label + "-" + UUID.randomUUID() + "@example.com"
        );
    }

    private User createUserWithEmail(String fullName, String email) {
        return userRepository.save(User.builder()
                .fullName(fullName)
                .email(email)
                .passwordHash("test-password-hash")
                .build());
    }

    private WorkspaceMember createMembership(
            Workspace workspace,
            User user,
            WorkspaceRole role,
            MembershipStatus status
    ) {
        return memberRepository.save(WorkspaceMember.builder()
                .workspace(workspace)
                .user(user)
                .role(role)
                .status(status)
                .build());
    }

    private WorkspaceInvitation createInvitation(
            Workspace workspace,
            User inviter,
            String email,
            WorkspaceRole role,
            LocalDateTime expiresAt
    ) {
        return invitationRepository.save(invitation(workspace, inviter, email, role, expiresAt));
    }

    private WorkspaceInvitation invitation(
            Workspace workspace,
            User inviter,
            String email,
            WorkspaceRole role,
            LocalDateTime expiresAt
    ) {
        LocalDateTime now = LocalDateTime.now();
        return WorkspaceInvitation.builder()
                .id(UUID.randomUUID().toString())
                .workspace(workspace)
                .email(email.toLowerCase())
                .role(role)
                .status(WorkspaceInvitationStatus.PENDING)
                .invitedBy(inviter)
                .expiresAt(expiresAt == null ? now.plusDays(7) : expiresAt)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    private long countActiveOwners(String workspaceId) {
        return memberRepository.findAll().stream()
                .filter(member -> member.getWorkspace().getId().equals(workspaceId))
                .filter(member -> member.getRole() == WorkspaceRole.OWNER)
                .filter(member -> member.getStatus() == MembershipStatus.ACTIVE)
                .count();
    }

    private String invitationPayload(String email, String role) {
        return """
                {
                  "email": "%s",
                  "role": "%s"
                }
                """.formatted(email, role);
    }

    private record TestWorkspace(Workspace workspace, User owner) {
    }
}
