package com.microapproval.api.controller;

import com.jayway.jsonpath.JsonPath;
import com.microapproval.api.entity.AiProviderConfiguration;
import com.microapproval.api.entity.AiProviderType;
import com.microapproval.api.entity.AnalysisMode;
import com.microapproval.api.entity.MembershipStatus;
import com.microapproval.api.entity.ReviewSession;
import com.microapproval.api.entity.RiskCategory;
import com.microapproval.api.entity.RiskLevel;
import com.microapproval.api.entity.RulePattern;
import com.microapproval.api.entity.SessionStatus;
import com.microapproval.api.entity.User;
import com.microapproval.api.entity.Workspace;
import com.microapproval.api.entity.WorkspaceMember;
import com.microapproval.api.entity.WorkspaceRole;
import com.microapproval.api.entity.WorkspaceType;
import com.microapproval.api.repository.AiProviderConfigurationRepository;
import com.microapproval.api.repository.MicroDecisionRepository;
import com.microapproval.api.repository.ReviewSessionRepository;
import com.microapproval.api.repository.RulePatternRepository;
import com.microapproval.api.repository.UserRepository;
import com.microapproval.api.repository.WorkspaceMemberRepository;
import com.microapproval.api.repository.WorkspaceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

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
class SharedReviewSessionControllerIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private WorkspaceRepository workspaceRepository;
    @Autowired private WorkspaceMemberRepository memberRepository;
    @Autowired private ReviewSessionRepository sessionRepository;
    @Autowired private MicroDecisionRepository decisionRepository;
    @Autowired private RulePatternRepository rulePatternRepository;
    @Autowired private AiProviderConfigurationRepository aiConfigurationRepository;

    @Test
    void ownerAdminAndReviewerCanCreateButOtherRolesCannot() throws Exception {
        User owner = createUser("creator-owner");
        Workspace workspace = createWorkspace(owner, "Creator roles");
        User admin = activeMember(workspace, "creator-admin", WorkspaceRole.ADMIN);
        User reviewer = activeMember(workspace, "creator-reviewer", WorkspaceRole.REVIEWER);
        User member = activeMember(workspace, "creator-member", WorkspaceRole.MEMBER);
        User auditor = activeMember(workspace, "creator-auditor", WorkspaceRole.AUDITOR);
        User removed = member(workspace, "creator-removed", WorkspaceRole.REVIEWER, MembershipStatus.REMOVED);
        User pending = member(workspace, "creator-pending", WorkspaceRole.REVIEWER, MembershipStatus.PENDING);
        User outsider = createUser("creator-outsider");

        create(workspace, owner, "Owner session", "DELETE FROM owner_table")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.workspaceType").value("SHARED"))
                .andExpect(jsonPath("$.createdByUserId").value(owner.getId()));
        create(workspace, admin, "Admin session", "DELETE FROM admin_table")
                .andExpect(status().isCreated());
        create(workspace, reviewer, "Reviewer session", "DELETE FROM reviewer_table")
                .andExpect(status().isCreated());
        create(workspace, member, "Member session", "DELETE FROM member_table")
                .andExpect(status().isForbidden());
        create(workspace, auditor, "Auditor session", "DELETE FROM audit_table")
                .andExpect(status().isForbidden());
        create(workspace, removed, "Removed session", "DELETE FROM removed_table")
                .andExpect(status().isNotFound());
        create(workspace, pending, "Pending session", "DELETE FROM pending_table")
                .andExpect(status().isNotFound());
        create(workspace, outsider, "Outsider session", "DELETE FROM outsider_table")
                .andExpect(status().isNotFound());
    }

    @Test
    void supportsRawGitDiffAndIntentUsingTheExistingInputContract() throws Exception {
        User owner = createUser("input-owner");
        Workspace workspace = createWorkspace(owner, "Input modes");

        createWithBody(workspace, owner, """
                {"title":" Raw ","mode":"RAW_SNIPPET","rawContent":"int total = 1;"}
                """).andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Raw"))
                .andExpect(jsonPath("$.mode").value("RAW_SNIPPET"));
        createWithBody(workspace, owner, """
                {"title":"Diff","mode":"GIT_DIFF","rawContent":"+ int total = 2;"}
                """).andExpect(status().isCreated())
                .andExpect(jsonPath("$.mode").value("GIT_DIFF"));
        createWithBody(workspace, owner, """
                {"title":"Intent","mode":"INTENT_MATCHING","rawContent":"return total;","promptContent":"Must reject negative totals"}
                """).andExpect(status().isCreated())
                .andExpect(jsonPath("$.promptContent").value("Must reject negative totals"));
    }

    @Test
    void invalidRequestsAreRejectedBeforePersistence() throws Exception {
        User owner = createUser("invalid-owner");
        Workspace workspace = createWorkspace(owner, "Invalid request");

        createWithBody(workspace, owner, """
                {"title":"   ","mode":"RAW_SNIPPET","rawContent":"   "}
                """).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.title").exists())
                .andExpect(jsonPath("$.errors.rawContent").exists());
        createWithBody(workspace, owner, """
                {"title":"Missing intent","mode":"INTENT_MATCHING","rawContent":"return total;"}
                """).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.modeContentValid").exists());
        createWithBody(workspace, owner, """
                {"title":"Unexpected intent","mode":"GIT_DIFF","rawContent":"+ return total;","promptContent":"not allowed"}
                """).andExpect(status().isBadRequest());
    }

    @Test
    void systemAndCurrentWorkspaceRulesRunWithoutCrossWorkspaceLeakage() throws Exception {
        User owner = createUser("rules-owner");
        Workspace workspaceA = createWorkspace(owner, "Rules A");
        Workspace workspaceB = createWorkspace(owner, "Rules B");
        rulePatternRepository.save(rule(workspaceA, "Workspace A policy", "A_ONLY_TOKEN"));
        rulePatternRepository.save(rule(workspaceB, "Workspace B policy", "B_ONLY_TOKEN"));

        String body = create(workspaceA, owner, "Scoped rules",
                "DELETE FROM audit_events; A_ONLY_TOKEN; B_ONLY_TOKEN")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.decisions.length()").value(2))
                .andReturn().getResponse().getContentAsString();

        String sessionId = JsonPath.read(body, "$.id");
        ReviewSession persisted = sessionRepository.findById(sessionId).orElseThrow();
        assertThat(persisted.getWorkspaceType()).isEqualTo(WorkspaceType.SHARED);
        assertThat(persisted.getWorkspace().getId()).isEqualTo(workspaceA.getId());
        assertThat(persisted.getSubmittedBy().getId()).isEqualTo(owner.getId());
        assertThat(decisionRepository.findBySessionIdOrderByDisplayOrderAsc(sessionId))
                .extracting(decision -> decision.getQuestionText())
                .contains("Workspace A policy")
                .doesNotContain("Workspace B policy");
        assertThat(decisionRepository.findBySessionIdOrderByDisplayOrderAsc(sessionId))
                .allMatch(decision -> decision.getSession().getId().equals(sessionId));
    }

    @Test
    void aiFailureKeepsRuleCardsAndUsesExistingFallbackSemantics() throws Exception {
        User owner = createUser("fallback-owner");
        Workspace workspace = createWorkspace(owner, "Fallback");
        aiConfigurationRepository.save(AiProviderConfiguration.builder()
                .userId(owner.getId())
                .provider(AiProviderType.OPENAI)
                .model("test-model")
                .apiKeyCiphertext("not-a-valid-ciphertext")
                .apiKeySuffix("test")
                .enabled(true)
                .build());

        create(workspace, owner, "Fallback session", "DELETE FROM audit_events; remaining code")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.aiAnalysisStatus").value("FALLBACK"))
                .andExpect(jsonPath("$.aiAnalysisError").isNotEmpty())
                .andExpect(jsonPath("$.decisions.length()").value(1))
                .andExpect(jsonPath("$.decisions[0].engineType").value("RULE_BASED"));
    }

    @Test
    void listReturnsOnlySharedSessionsFromRequestedWorkspaceNewestFirst() throws Exception {
        User owner = createUser("list-owner");
        User member = createUser("list-member");
        Workspace workspaceA = createWorkspace(owner, "List A");
        Workspace workspaceB = createWorkspace(owner, "List B");
        createMembership(workspaceA, member, WorkspaceRole.MEMBER, MembershipStatus.ACTIVE);
        createMembership(workspaceB, member, WorkspaceRole.MEMBER, MembershipStatus.ACTIVE);
        create(workspaceA, owner, "Older A", "int older = 1;").andExpect(status().isCreated());
        create(workspaceB, owner, "Only B", "int b = 1;").andExpect(status().isCreated());
        create(workspaceA, owner, "Newest A", "int newest = 1;").andExpect(status().isCreated());
        sessionRepository.save(ReviewSession.builder()
                .title("Personal hidden")
                .workspaceType(WorkspaceType.PERSONAL)
                .mode(AnalysisMode.RAW_SNIPPET)
                .rawContent("personal")
                .submittedBy(owner)
                .status(SessionStatus.APPROVED)
                .build());

        mockMvc.perform(get("/api/workspaces/{workspaceId}/sessions", workspaceA.getId())
                        .with(user(member.getEmail())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].title").value("Newest A"))
                .andExpect(jsonPath("$[1].title").value("Older A"))
                .andExpect(jsonPath("$[0].workspaceId").value(workspaceA.getId()))
                .andExpect(jsonPath("$[0].workspaceType").value("SHARED"));
    }

    @Test
    void activeMemberCanReadDetailButOtherScopesAreHidden() throws Exception {
        User owner = createUser("detail-owner");
        User member = createUser("detail-member");
        User outsider = createUser("detail-outsider");
        Workspace workspaceA = createWorkspace(owner, "Detail A");
        Workspace workspaceB = createWorkspace(owner, "Detail B");
        createMembership(workspaceA, member, WorkspaceRole.AUDITOR, MembershipStatus.ACTIVE);
        String body = create(workspaceA, owner, "Detail session", "DELETE FROM audit_events")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String sessionId = JsonPath.read(body, "$.id");
        ReviewSession personal = sessionRepository.save(ReviewSession.builder()
                .title("Personal")
                .workspaceType(WorkspaceType.PERSONAL)
                .mode(AnalysisMode.RAW_SNIPPET)
                .rawContent("personal")
                .submittedBy(owner)
                .status(SessionStatus.APPROVED)
                .build());

        mockMvc.perform(get("/api/workspaces/{workspaceId}/sessions/{sessionId}",
                        workspaceA.getId(), sessionId).with(user(member.getEmail())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decisions.length()").value(1))
                .andExpect(jsonPath("$.decisions[0].engineType").value("RULE_BASED"));
        mockMvc.perform(get("/api/workspaces/{workspaceId}/sessions/{sessionId}",
                        workspaceB.getId(), sessionId).with(user(owner.getEmail())))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/workspaces/{workspaceId}/sessions/{sessionId}",
                        workspaceA.getId(), personal.getId()).with(user(member.getEmail())))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/workspaces/{workspaceId}/sessions/{sessionId}",
                        workspaceA.getId(), sessionId).with(user(outsider.getEmail())))
                .andExpect(status().isNotFound());
    }

    @Test
    void nonMemberCannotListAndEndpointsRequireAuthentication() throws Exception {
        User owner = createUser("security-owner");
        User outsider = createUser("security-outsider");
        Workspace workspace = createWorkspace(owner, "Security");
        User pending = member(
                workspace,
                "security-pending",
                WorkspaceRole.MEMBER,
                MembershipStatus.PENDING
        );
        User removed = member(
                workspace,
                "security-removed",
                WorkspaceRole.MEMBER,
                MembershipStatus.REMOVED
        );

        mockMvc.perform(get("/api/workspaces/{workspaceId}/sessions", workspace.getId())
                        .with(user(outsider.getEmail())))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/workspaces/{workspaceId}/sessions", workspace.getId())
                        .with(user(pending.getEmail())))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/workspaces/{workspaceId}/sessions", workspace.getId())
                        .with(user(removed.getEmail())))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/workspaces/{workspaceId}/sessions", workspace.getId()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/workspaces/{workspaceId}/sessions", workspace.getId())
                        .contentType("application/json")
                        .content(validBody("Unauthenticated", "return 1;")))
                .andExpect(status().isForbidden());
    }

    @Test
    void databaseEnforcesPersonalAndSharedWorkspaceScope() {
        User owner = createUser("constraint-owner");
        Workspace workspace = createWorkspace(owner, "Constraint");
        ReviewSession invalidPersonal = ReviewSession.builder()
                .title("Invalid personal")
                .workspaceType(WorkspaceType.PERSONAL)
                .workspace(workspace)
                .mode(AnalysisMode.RAW_SNIPPET)
                .rawContent("invalid")
                .submittedBy(owner)
                .status(SessionStatus.PENDING)
                .build();

        assertThatThrownBy(() -> sessionRepository.saveAndFlush(invalidPersonal))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private org.springframework.test.web.servlet.ResultActions create(
            Workspace workspace,
            User caller,
            String title,
            String content
    ) throws Exception {
        return createWithBody(workspace, caller, validBody(title, content));
    }

    private org.springframework.test.web.servlet.ResultActions createWithBody(
            Workspace workspace,
            User caller,
            String body
    ) throws Exception {
        return mockMvc.perform(post("/api/workspaces/{workspaceId}/sessions", workspace.getId())
                .with(user(caller.getEmail()))
                .contentType("application/json")
                .content(body));
    }

    private String validBody(String title, String content) {
        return """
                {"title":"%s","mode":"RAW_SNIPPET","rawContent":"%s"}
                """.formatted(title, content);
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
        Workspace workspace = workspaceRepository.save(Workspace.builder()
                .name(name)
                .owner(owner)
                .build());
        createMembership(workspace, owner, WorkspaceRole.OWNER, MembershipStatus.ACTIVE);
        return workspace;
    }

    private User activeMember(Workspace workspace, String label, WorkspaceRole role) {
        return member(workspace, label, role, MembershipStatus.ACTIVE);
    }

    private User member(
            Workspace workspace,
            String label,
            WorkspaceRole role,
            MembershipStatus status
    ) {
        User user = createUser(label);
        createMembership(workspace, user, role, status);
        return user;
    }

    private void createMembership(
            Workspace workspace,
            User user,
            WorkspaceRole role,
            MembershipStatus status
    ) {
        memberRepository.save(WorkspaceMember.builder()
                .workspace(workspace)
                .user(user)
                .role(role)
                .status(status)
                .build());
    }

    private RulePattern rule(Workspace workspace, String question, String pattern) {
        return RulePattern.builder()
                .workspaceId(workspace.getId())
                .name(question)
                .priority(5)
                .pattern(pattern)
                .riskCategory(RiskCategory.BUSINESS_LOGIC)
                .riskLevel(RiskLevel.MEDIUM)
                .questionTemplate(question)
                .isActive(true)
                .build();
    }
}
