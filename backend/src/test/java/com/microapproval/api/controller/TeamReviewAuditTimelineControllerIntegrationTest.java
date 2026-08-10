package com.microapproval.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.microapproval.api.dto.AssignSessionReviewerRequest;
import com.microapproval.api.dto.CloseSharedReviewSessionRequest;
import com.microapproval.api.dto.RemoveSessionReviewerRequest;
import com.microapproval.api.dto.SessionAuditEventResponse;
import com.microapproval.api.dto.SessionAuditTimelineResponse;
import com.microapproval.api.dto.SessionReviewerResponse;
import com.microapproval.api.dto.SessionVotingResponse;
import com.microapproval.api.dto.UpsertTeamVoteRequest;
import com.microapproval.api.entity.AiAnalysisStatus;
import com.microapproval.api.entity.AnalysisMode;
import com.microapproval.api.entity.EngineType;
import com.microapproval.api.entity.MembershipStatus;
import com.microapproval.api.entity.MicroDecision;
import com.microapproval.api.entity.ReviewSession;
import com.microapproval.api.entity.RiskCategory;
import com.microapproval.api.entity.RiskLevel;
import com.microapproval.api.entity.SessionStatus;
import com.microapproval.api.entity.TeamReviewAuditEvent;
import com.microapproval.api.entity.TeamReviewAuditEventType;
import com.microapproval.api.entity.TeamVoteDecision;
import com.microapproval.api.entity.User;
import com.microapproval.api.entity.Workspace;
import com.microapproval.api.entity.WorkspaceMember;
import com.microapproval.api.entity.WorkspaceRole;
import com.microapproval.api.entity.WorkspaceType;
import com.microapproval.api.repository.MicroDecisionRepository;
import com.microapproval.api.repository.ReviewSessionRepository;
import com.microapproval.api.repository.TeamReviewAuditEventRepository;
import com.microapproval.api.repository.UserRepository;
import com.microapproval.api.repository.WorkspaceMemberRepository;
import com.microapproval.api.repository.WorkspaceRepository;
import com.microapproval.api.support.AbstractMySqlIntegrationTest;
import com.microapproval.api.service.ReviewSessionReviewerService;
import com.microapproval.api.service.SharedReviewSessionLifecycleService;
import com.microapproval.api.service.TeamVotingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TeamReviewAuditTimelineControllerIntegrationTest extends AbstractMySqlIntegrationTest {

    private static final ObjectMapper JSON = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private WorkspaceRepository workspaceRepository;
    @Autowired private WorkspaceMemberRepository memberRepository;
    @Autowired private ReviewSessionRepository sessionRepository;
    @Autowired private MicroDecisionRepository cardRepository;
    @Autowired private TeamReviewAuditEventRepository auditRepository;
    @Autowired private ReviewSessionReviewerService reviewerService;
    @Autowired private TeamVotingService votingService;
    @Autowired private SharedReviewSessionLifecycleService lifecycleService;

    @Test
    void everyActiveWorkspaceRoleCanReadTimeline() throws Exception {
        Fixture fixture = fixture("active-roles", true, "safe raw content");
        auditEvent(fixture, "00000000-0000-0000-0000-000000000001",
                TeamReviewAuditEventType.REVIEWER_ASSIGNED, LocalDateTime.now());

        for (WorkspaceRole role : WorkspaceRole.values()) {
            User caller = role == WorkspaceRole.OWNER
                    ? fixture.owner()
                    : membership(fixture, "active-" + role, role, MembershipStatus.ACTIVE).getUser();

            mockMvc.perform(get(auditPath(fixture)).with(user(caller.getEmail())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.sessionId").value(fixture.session().getId()))
                    .andExpect(jsonPath("$.events.length()").value(1));
        }
    }

    @Test
    void inactiveNonMemberPersonalAndCrossWorkspaceResourcesAreHidden() throws Exception {
        Fixture fixture = fixture("hidden-scope", false, "safe raw content");
        User pending = membership(
                fixture, "pending", WorkspaceRole.REVIEWER, MembershipStatus.PENDING
        ).getUser();
        User removed = membership(
                fixture, "removed", WorkspaceRole.MEMBER, MembershipStatus.REMOVED
        ).getUser();
        User nonMember = createUser("non-member");

        for (User caller : List.of(pending, removed, nonMember)) {
            mockMvc.perform(get(auditPath(fixture)).with(user(caller.getEmail())))
                    .andExpect(status().isNotFound());
        }

        ReviewSession personal = sessionRepository.save(session(
                fixture.owner(), null, WorkspaceType.PERSONAL, "personal raw"
        ));
        mockMvc.perform(get("/api/workspaces/{workspaceId}/sessions/{sessionId}/audit",
                        fixture.workspace().getId(), personal.getId())
                        .with(user(fixture.owner().getEmail())))
                .andExpect(status().isNotFound());

        Workspace otherWorkspace = workspaceRepository.save(Workspace.builder()
                .name("Other audit workspace")
                .owner(fixture.owner())
                .build());
        memberRepository.save(WorkspaceMember.builder()
                .workspace(otherWorkspace)
                .user(fixture.owner())
                .role(WorkspaceRole.OWNER)
                .status(MembershipStatus.ACTIVE)
                .build());
        ReviewSession otherSession = sessionRepository.save(session(
                fixture.owner(), otherWorkspace, WorkspaceType.SHARED, "other raw"
        ));
        mockMvc.perform(get("/api/workspaces/{workspaceId}/sessions/{sessionId}/audit",
                        fixture.workspace().getId(), otherSession.getId())
                        .with(user(fixture.owner().getEmail())))
                .andExpect(status().isNotFound());
    }

    @Test
    void mapsCompleteReviewerVoteAndLifecycleHistoryNewestFirst() throws Exception {
        Fixture fixture = fixture("full-history", true, "SUPER_SECRET_RAW_SOURCE");
        WorkspaceMember reviewer = membership(
                fixture, "timeline-reviewer", WorkspaceRole.REVIEWER, MembershipStatus.ACTIVE
        );
        WorkspaceMember member = membership(
                fixture, "timeline-member", WorkspaceRole.MEMBER, MembershipStatus.ACTIVE
        );

        SessionReviewerResponse assigned = reviewerService.assignReviewer(
                fixture.workspace().getId(), fixture.session().getId(),
                new AssignSessionReviewerRequest(reviewer.getId()),
                fixture.owner().getEmail()
        );
        SessionVotingResponse created = votingService.upsertOwnVote(
                fixture.workspace().getId(), fixture.session().getId(), fixture.card().getId(),
                new UpsertTeamVoteRequest(TeamVoteDecision.REJECTED, "Initial rejection", null),
                reviewer.getUser().getEmail()
        );
        long createdVersion = created.cards().getFirst().votes().getFirst().version();
        SessionVotingResponse updated = votingService.upsertOwnVote(
                fixture.workspace().getId(), fixture.session().getId(), fixture.card().getId(),
                new UpsertTeamVoteRequest(
                        TeamVoteDecision.APPROVED, "Risk resolved", createdVersion
                ),
                reviewer.getUser().getEmail()
        );
        reviewerService.removeReviewer(
                fixture.workspace().getId(), fixture.session().getId(), assigned.assignmentId(),
                new RemoveSessionReviewerRequest("Rotation complete"),
                fixture.owner().getEmail()
        );
        SessionReviewerResponse reactivated = reviewerService.assignReviewer(
                fixture.workspace().getId(), fixture.session().getId(),
                new AssignSessionReviewerRequest(reviewer.getId()),
                fixture.owner().getEmail()
        );
        assertThat(reactivated.assignmentId()).isEqualTo(assigned.assignmentId());

        long staleAssignmentVoteVersion = updated.cards().getFirst().votes().getFirst().version();
        votingService.upsertOwnVote(
                fixture.workspace().getId(), fixture.session().getId(), fixture.card().getId(),
                new UpsertTeamVoteRequest(
                        TeamVoteDecision.APPROVED, "Reconfirmed", staleAssignmentVoteVersion
                ),
                reviewer.getUser().getEmail()
        );
        lifecycleService.closeSession(
                fixture.workspace().getId(), fixture.session().getId(),
                new CloseSharedReviewSessionRequest("Release approved"),
                fixture.owner().getEmail()
        );

        SessionAuditTimelineResponse closedTimeline = getTimeline(fixture, member.getUser(), 0, 100);
        assertThat(closedTimeline.events())
                .extracting(SessionAuditEventResponse::eventType)
                .contains(TeamReviewAuditEventType.SESSION_CLOSED);

        lifecycleService.reopenSession(
                fixture.workspace().getId(), fixture.session().getId(), fixture.owner().getEmail()
        );

        String responseBody = mockMvc.perform(get(auditPath(fixture))
                        .param("size", "100")
                        .with(user(member.getUser().getEmail())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(8))
                .andReturn().getResponse().getContentAsString();
        SessionAuditTimelineResponse timeline = JSON.readValue(
                responseBody, SessionAuditTimelineResponse.class
        );

        assertThat(timeline.events())
                .extracting(SessionAuditEventResponse::eventType)
                .containsExactly(
                        TeamReviewAuditEventType.SESSION_REOPENED,
                        TeamReviewAuditEventType.SESSION_CLOSED,
                        TeamReviewAuditEventType.VOTE_UPDATED,
                        TeamReviewAuditEventType.REVIEWER_REACTIVATED,
                        TeamReviewAuditEventType.REVIEWER_REMOVED,
                        TeamReviewAuditEventType.VOTE_UPDATED,
                        TeamReviewAuditEventType.VOTE_CREATED,
                        TeamReviewAuditEventType.REVIEWER_ASSIGNED
                );

        SessionAuditEventResponse assignedEvent = event(timeline, TeamReviewAuditEventType.REVIEWER_ASSIGNED, 0);
        assertThat(assignedEvent.targetUserId()).isEqualTo(reviewer.getUser().getId());
        assertThat(assignedEvent.targetDisplayName()).isEqualTo(reviewer.getUser().getFullName());
        assertThat(assignedEvent.targetAssignmentId()).isEqualTo(assigned.assignmentId());
        assertThat(assignedEvent.change().newValue().status()).isEqualTo("ASSIGNED");

        SessionAuditEventResponse removedEvent = event(timeline, TeamReviewAuditEventType.REVIEWER_REMOVED, 0);
        assertThat(removedEvent.reason()).isEqualTo("Rotation complete");
        assertThat(removedEvent.change().oldValue().status()).isEqualTo("ASSIGNED");
        assertThat(removedEvent.change().newValue().status()).isEqualTo("REMOVED");

        SessionAuditEventResponse reactivatedEvent = event(
                timeline, TeamReviewAuditEventType.REVIEWER_REACTIVATED, 0
        );
        assertThat(reactivatedEvent.targetAssignmentId()).isEqualTo(assigned.assignmentId());
        assertThat(reactivatedEvent.change().newValue().status()).isEqualTo("ASSIGNED");

        SessionAuditEventResponse createdVote = event(timeline, TeamReviewAuditEventType.VOTE_CREATED, 0);
        assertThat(createdVote.decisionCardId()).isEqualTo(fixture.card().getId());
        assertThat(createdVote.decisionCardSummary()).isEqualTo("Decision Card #1");
        assertThat(createdVote.change().newValue().decision()).isEqualTo("REJECTED");
        assertThat(createdVote.change().newValue().note()).isEqualTo("Initial rejection");

        SessionAuditEventResponse firstUpdate = timeline.events().stream()
                .filter(item -> item.eventType() == TeamReviewAuditEventType.VOTE_UPDATED)
                .filter(item -> item.change().oldValue().decision().equals("REJECTED"))
                .findFirst().orElseThrow();
        assertThat(firstUpdate.change().newValue().decision()).isEqualTo("APPROVED");
        assertThat(firstUpdate.change().oldValue().note()).isEqualTo("Initial rejection");
        assertThat(firstUpdate.change().newValue().note()).isEqualTo("Risk resolved");

        SessionAuditEventResponse closedEvent = event(timeline, TeamReviewAuditEventType.SESSION_CLOSED, 0);
        assertThat(closedEvent.reason()).isEqualTo("Release approved");
        assertThat(closedEvent.change().newValue().status()).isEqualTo("APPROVED");
        assertThat(closedEvent.change().newValue().closed()).isTrue();
        assertThat(closedEvent.change().newValue().closedAt()).isNotNull();
        assertThat(closedEvent.change().newValue().lifecycleVersion()).isNotNull();

        SessionAuditEventResponse reopenedEvent = event(
                timeline, TeamReviewAuditEventType.SESSION_REOPENED, 0
        );
        assertThat(reopenedEvent.change().oldValue().closed()).isTrue();
        assertThat(reopenedEvent.change().newValue().closed()).isFalse();
        assertThat(reopenedEvent.change().newValue().reopenedAt()).isNotNull();

        assertThat(responseBody)
                .doesNotContain("SUPER_SECRET_RAW_SOURCE", "rawContent", "oldValueJson", "newValueJson");
    }

    @Test
    void sameTimestampOrderingAndPaginationAreDeterministic() throws Exception {
        Fixture fixture = fixture("pagination", false, "safe raw content");
        LocalDateTime sameTimestamp = LocalDateTime.of(2026, 8, 7, 1, 2, 3, 456_000_000);
        auditEvent(fixture, "00000000-0000-0000-0000-000000000001",
                TeamReviewAuditEventType.REVIEWER_ASSIGNED, sameTimestamp);
        auditEvent(fixture, "00000000-0000-0000-0000-000000000002",
                TeamReviewAuditEventType.REVIEWER_REMOVED, sameTimestamp);
        auditEvent(fixture, "00000000-0000-0000-0000-000000000003",
                TeamReviewAuditEventType.REVIEWER_REACTIVATED, sameTimestamp);

        SessionAuditTimelineResponse first = getTimeline(fixture, fixture.owner(), 0, 2);
        assertThat(first.events()).extracting(SessionAuditEventResponse::eventId)
                .containsExactly(
                        "00000000-0000-0000-0000-000000000003",
                        "00000000-0000-0000-0000-000000000002"
                );
        assertThat(first.page()).isZero();
        assertThat(first.size()).isEqualTo(2);
        assertThat(first.totalElements()).isEqualTo(3);
        assertThat(first.totalPages()).isEqualTo(2);
        assertThat(first.hasNext()).isTrue();

        SessionAuditTimelineResponse second = getTimeline(fixture, fixture.owner(), 1, 2);
        assertThat(second.events()).extracting(SessionAuditEventResponse::eventId)
                .containsExactly("00000000-0000-0000-0000-000000000001");
        assertThat(second.hasNext()).isFalse();
    }

    @Test
    void unsafeAndMalformedLegacyPayloadsAreNeverExposed() throws Exception {
        Fixture fixture = fixture("payload-safety", true, "RAW CODE MUST STAY PRIVATE");
        TeamReviewAuditEvent unsafe = TeamReviewAuditEvent.builder()
                .id("00000000-0000-0000-0000-000000000010")
                .session(fixture.session())
                .actor(fixture.owner())
                .eventType(TeamReviewAuditEventType.VOTE_UPDATED)
                .decisionCard(fixture.card())
                .oldValueJson("{\"decision\":\"APPROVED\",\"apiKey\":\"sk-private\","
                        + "\"password\":\"secret-password\",\"rawContent\":\"private diff\"}")
                .newValueJson("not-json")
                .createdAt(LocalDateTime.now())
                .build();
        auditRepository.saveAndFlush(unsafe);

        String body = mockMvc.perform(get(auditPath(fixture))
                        .with(user(fixture.owner().getEmail())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.events[0].change.oldValue.decision").value("APPROVED"))
                .andExpect(jsonPath("$.events[0].change.newValue").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain(
                "sk-private",
                "secret-password",
                "private diff",
                "RAW CODE MUST STAY PRIVATE",
                "questionText",
                "codeSnippet",
                "apiKey",
                "password",
                "rawContent"
        );
    }

    @Test
    void removedTargetAndAssignmentRemainReadableAndOptionalFieldsStayNullSafe() throws Exception {
        Fixture fixture = fixture("removed-target", false, "safe raw content");
        WorkspaceMember target = membership(
                fixture, "removed-target-user", WorkspaceRole.REVIEWER, MembershipStatus.REMOVED
        );
        TeamReviewAuditEvent event = TeamReviewAuditEvent.builder()
                .session(fixture.session())
                .actor(fixture.owner())
                .targetUser(target.getUser())
                .eventType(TeamReviewAuditEventType.REVIEWER_REMOVED)
                .reason("Membership removed")
                .createdAt(LocalDateTime.now())
                .build();
        auditRepository.saveAndFlush(event);

        mockMvc.perform(get(auditPath(fixture)).with(user(fixture.owner().getEmail())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.events[0].targetUserId").value(target.getUser().getId()))
                .andExpect(jsonPath("$.events[0].targetDisplayName").value(target.getUser().getFullName()))
                .andExpect(jsonPath("$.events[0].targetAssignmentId").doesNotExist())
                .andExpect(jsonPath("$.events[0].decisionCardId").doesNotExist())
                .andExpect(jsonPath("$.events[0].change").doesNotExist());
    }

    @Test
    void emptyTimelineDefaultsAndInvalidPaginationAreHandled() throws Exception {
        Fixture fixture = fixture("empty", false, "safe raw content");

        mockMvc.perform(get(auditPath(fixture)).with(user(fixture.owner().getEmail())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.events.length()").value(0))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalPages").value(0))
                .andExpect(jsonPath("$.hasNext").value(false));

        for (String[] params : List.of(
                new String[]{"-1", "20"},
                new String[]{"0", "0"},
                new String[]{"0", "101"}
        )) {
            mockMvc.perform(get(auditPath(fixture))
                            .param("page", params[0])
                            .param("size", params[1])
                            .with(user(fixture.owner().getEmail())))
                    .andExpect(status().isBadRequest());
        }
    }

    private SessionAuditTimelineResponse getTimeline(
            Fixture fixture, User caller, int page, int size
    ) throws Exception {
        String body = mockMvc.perform(get(auditPath(fixture))
                        .param("page", String.valueOf(page))
                        .param("size", String.valueOf(size))
                        .with(user(caller.getEmail())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JSON.readValue(body, SessionAuditTimelineResponse.class);
    }

    private SessionAuditEventResponse event(
            SessionAuditTimelineResponse timeline,
            TeamReviewAuditEventType type,
            int occurrence
    ) {
        return timeline.events().stream()
                .filter(item -> item.eventType() == type)
                .skip(occurrence)
                .findFirst()
                .orElseThrow();
    }

    private String auditPath(Fixture fixture) {
        return "/api/workspaces/%s/sessions/%s/audit".formatted(
                fixture.workspace().getId(), fixture.session().getId()
        );
    }

    private TeamReviewAuditEvent auditEvent(
            Fixture fixture,
            String id,
            TeamReviewAuditEventType type,
            LocalDateTime createdAt
    ) {
        return auditRepository.saveAndFlush(TeamReviewAuditEvent.builder()
                .id(id)
                .session(fixture.session())
                .actor(fixture.owner())
                .eventType(type)
                .createdAt(createdAt)
                .build());
    }

    private Fixture fixture(String label, boolean withCard, String rawContent) {
        User owner = createUser(label + "-owner");
        Workspace workspace = workspaceRepository.save(Workspace.builder()
                .name("Audit workspace " + label)
                .owner(owner)
                .build());
        memberRepository.save(WorkspaceMember.builder()
                .workspace(workspace)
                .user(owner)
                .role(WorkspaceRole.OWNER)
                .status(MembershipStatus.ACTIVE)
                .build());
        ReviewSession session = sessionRepository.save(session(
                owner, workspace, WorkspaceType.SHARED, rawContent
        ));
        MicroDecision card = withCard ? cardRepository.save(card(session)) : null;
        return new Fixture(owner, workspace, session, card);
    }

    private WorkspaceMember membership(
            Fixture fixture,
            String label,
            WorkspaceRole role,
            MembershipStatus status
    ) {
        return memberRepository.save(WorkspaceMember.builder()
                .workspace(fixture.workspace())
                .user(createUser(label))
                .role(role)
                .status(status)
                .build());
    }

    private ReviewSession session(
            User owner,
            Workspace workspace,
            WorkspaceType type,
            String rawContent
    ) {
        return ReviewSession.builder()
                .title("Audit " + UUID.randomUUID())
                .workspaceType(type)
                .workspace(workspace)
                .mode(AnalysisMode.RAW_SNIPPET)
                .rawContent(rawContent)
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
                .questionText("Private audit question must not be projected")
                .codeSnippet("private code must not be projected")
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
            ReviewSession session,
            MicroDecision card
    ) {
    }
}
