package com.microapproval.api.controller;

import com.jayway.jsonpath.JsonPath;
import com.microapproval.api.entity.MembershipStatus;
import com.microapproval.api.entity.User;
import com.microapproval.api.entity.Workspace;
import com.microapproval.api.entity.WorkspaceMember;
import com.microapproval.api.entity.WorkspaceRole;
import com.microapproval.api.repository.UserRepository;
import com.microapproval.api.repository.WorkspaceMemberRepository;
import com.microapproval.api.repository.WorkspaceRepository;
import com.microapproval.api.service.WorkspaceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.UUID;

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
class WorkspaceControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private WorkspaceMemberRepository workspaceMemberRepository;

    @Test
    void createWorkspacePersistsWorkspaceAndActiveOwnerMembership() throws Exception {
        User owner = createUser("owner");

        String responseBody = mockMvc.perform(post("/api/workspaces")
                        .with(user(owner.getEmail()))
                        .contentType("application/json")
                        .content("""
                                {
                                  "name": "  Nhóm sản phẩm  ",
                                  "description": "  Workspace thử nghiệm  "
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Nhóm sản phẩm"))
                .andExpect(jsonPath("$.description").value("Workspace thử nghiệm"))
                .andExpect(jsonPath("$.ownerId").value(owner.getId()))
                .andExpect(jsonPath("$.currentUserRole").value("OWNER"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String workspaceId = JsonPath.read(responseBody, "$.id");
        Workspace persistedWorkspace = workspaceRepository.findById(workspaceId).orElseThrow();
        WorkspaceMember persistedMembership = workspaceMemberRepository
                .findByWorkspaceIdAndUserId(workspaceId, owner.getId())
                .orElseThrow();

        assertThat(persistedWorkspace.getOwner().getId()).isEqualTo(owner.getId());
        assertThat(persistedMembership.getRole()).isEqualTo(WorkspaceRole.OWNER);
        assertThat(persistedMembership.getStatus()).isEqualTo(MembershipStatus.ACTIVE);
    }

    @Test
    void createWorkspaceAndOwnerMembershipShareOneTransactionBoundary() throws Exception {
        Method method = WorkspaceService.class.getMethod(
                "createWorkspace",
                com.microapproval.api.dto.CreateWorkspaceRequest.class,
                String.class
        );

        Transactional transactional = method.getAnnotation(Transactional.class);

        assertThat(transactional)
                .as("Workspace và OWNER membership phải rollback cùng nhau")
                .isNotNull();
    }

    @Test
    void listReturnsOnlyWorkspacesWithActiveMembership() throws Exception {
        User owner = createUser("list-owner");
        User member = createUser("list-member");
        Workspace activeWorkspace = createWorkspace(owner, "Active workspace");
        Workspace pendingWorkspace = createWorkspace(owner, "Pending workspace");
        Workspace removedWorkspace = createWorkspace(owner, "Removed workspace");
        createMembership(activeWorkspace, member, MembershipStatus.ACTIVE);
        createMembership(pendingWorkspace, member, MembershipStatus.PENDING);
        createMembership(removedWorkspace, member, MembershipStatus.REMOVED);

        mockMvc.perform(get("/api/workspaces").with(user(member.getEmail())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(activeWorkspace.getId()))
                .andExpect(jsonPath("$[0].currentUserRole").value("MEMBER"));
    }

    @Test
    void activeMemberCanViewDetailButNonMemberCannot() throws Exception {
        User owner = createUser("detail-owner");
        User member = createUser("detail-member");
        User nonMember = createUser("detail-outsider");
        Workspace workspace = createWorkspace(owner, "Private workspace");
        createMembership(workspace, member, MembershipStatus.ACTIVE);

        mockMvc.perform(get("/api/workspaces/{workspaceId}", workspace.getId())
                        .with(user(member.getEmail())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(workspace.getId()))
                .andExpect(jsonPath("$.currentUserRole").value("MEMBER"));

        mockMvc.perform(get("/api/workspaces/{workspaceId}", workspace.getId())
                        .with(user(nonMember.getEmail())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Không tìm thấy workspace"));
    }

    @Test
    void blankWorkspaceNameIsRejected() throws Exception {
        User owner = createUser("validation-owner");

        mockMvc.perform(post("/api/workspaces")
                        .with(user(owner.getEmail()))
                        .contentType("application/json")
                        .content("""
                                {
                                  "name": "   ",
                                  "description": "Không hợp lệ"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").exists());
    }

    @Test
    void duplicateMembershipIsRejectedByDatabaseConstraint() {
        User owner = createUser("duplicate-owner");
        User member = createUser("duplicate-member");
        Workspace workspace = createWorkspace(owner, "Unique membership");
        createMembership(workspace, member, MembershipStatus.ACTIVE);

        WorkspaceMember duplicate = WorkspaceMember.builder()
                .workspace(workspace)
                .user(member)
                .role(WorkspaceRole.REVIEWER)
                .status(MembershipStatus.ACTIVE)
                .build();

        assertThatThrownBy(() -> workspaceMemberRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void workspaceEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/workspaces"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/workspaces")
                        .contentType("application/json")
                        .content("""
                                {
                                  "name": "Unauthenticated workspace"
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    private User createUser(String label) {
        String unique = UUID.randomUUID().toString();
        return userRepository.save(User.builder()
                .fullName("Test " + label)
                .email(label + "-" + unique + "@example.com")
                .passwordHash("test-password-hash")
                .build());
    }

    private Workspace createWorkspace(User owner, String name) {
        return workspaceRepository.save(Workspace.builder()
                .name(name)
                .owner(owner)
                .build());
    }

    private WorkspaceMember createMembership(
            Workspace workspace,
            User user,
            MembershipStatus status
    ) {
        return workspaceMemberRepository.save(WorkspaceMember.builder()
                .workspace(workspace)
                .user(user)
                .role(WorkspaceRole.MEMBER)
                .status(status)
                .build());
    }
}
