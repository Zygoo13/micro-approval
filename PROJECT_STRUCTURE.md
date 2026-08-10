# Project structure

## Architectural decision

The repository currently uses a **light layer-first structure** in the backend and a small page-oriented React structure in the frontend. This is retained deliberately: the implemented Personal Workspace is still one bounded feature, and a wholesale package move would create a large import-only diff without changing business behaviour.

For future modules, use **feature-first boundaries inside the existing application**, while keeping truly cross-cutting code in shared locations. Do not introduce extra hexagonal/CQRS layers until a module has a real second adapter, complex workflow, or independent deployment need.

Repository-owned collaboration contracts are versioned under `docs/`. Do not
make the build or onboarding process depend on a contributor-specific desktop
path; any future canonical specification must be copied into `docs/` first.

The canonical collaboration model is:

```text
Workspace
└── WorkspaceMember
```

The historical `Team Workspace` label means Shared Workspace, not a second
domain aggregate. Flyway V6 migrates and removes the former `teams` and
`team_members` schema; V7 replaces the session discriminator `TEAM` with
`SHARED`.

## Current repository map

```text
micro-approval/
├── backend/
│   ├── src/main/java/com/microapproval/api/
│   │   ├── config/          # Spring, security, and typed configuration
│   │   ├── controller/      # HTTP boundary; maps requests to application services
│   │   ├── dto/             # Request/response contracts; no persistence logic
│   │   ├── entity/          # JPA entities and domain enums
│   │   ├── exception/       # Domain/application errors and HTTP error mapping
│   │   ├── repository/      # Spring Data persistence access
│   │   ├── security/        # JWT generation and authentication filter
│   │   └── service/         # Personal/Workspace workflows, access, Rule and AI engines
│   ├── src/main/resources/
│   │   ├── application.yaml # Runtime configuration only; no real secrets
│   │   └── db/migration/    # Immutable Flyway migrations
│   └── src/test/java/       # Tests mirroring the production package root
├── frontend/
│   ├── src/
│   │   ├── features/        # Feature-owned UI sections and focused tests
│   │   ├── lib/             # HTTP client and presentation labels
│   │   ├── pages/           # Route-level screens for the current small UI
│   │   ├── App.tsx          # Route composition and authenticated layout
│   │   ├── types.ts         # API/UI contract types
│   │   ├── styles.css       # Shared MVP styling
│   │   └── workspace.css    # Team Workspace slice styling
│   ├── vite.config.ts       # Development proxy only
│   └── package.json
├── docker-compose.yml       # Local MySQL only
├── docs/                    # Focused module/API documentation
├── README.md                # Setup and verification entry point
└── PROJECT_STRUCTURE.md     # This architectural convention
```

## Responsibilities and dependency rules

### Backend

- `controller` may depend on `dto` and `service`; it must not access repositories directly.
- `service` owns use cases, authorization decisions, transaction boundaries, and orchestration between Rule and AI engines. `WorkspaceMemberService` owns direct membership lifecycle use cases, `WorkspaceInvitationService` owns invitation state transitions, `SharedReviewSessionService` owns workspace-scoped session use cases, `SharedReviewSessionLifecycleService` owns close/reopen, `ReviewSessionReviewerService` owns the reviewer roster lifecycle, `TeamVotingService` owns per-reviewer vote mutations/read models, `TeamReviewAggregationService` owns deterministic Shared card/session aggregation, `TeamReviewAuditTimelineService` owns the safe read-only audit projection, and `WorkspaceAccessService` is the centralized workspace authorization policy. `ReviewAnalysisPipeline` is shared by Personal and Shared session creation.
- `repository` is the only persistence abstraction used by services. Keep query methods named for the business scope they enforce.
- `entity` contains persistence-backed business state; it must not depend on controllers or DTOs.
- `dto` is the HTTP contract. Do not return entities from controllers.
- `security`, `config`, and `exception` are cross-cutting and remain outside a business feature package.
- Every database change is a new forward-only Flyway migration. Do not edit an applied migration.

### Implemented module ownership

| Module | Current entry points | Ownership |
|---|---|---|
| Authentication | `AuthController`, `AuthService`, `security/` | Register/login, BCrypt, JWT generation and request authentication |
| Personal | `PersonalSessionController`, `PersonalSessionService` | Private sessions, Personal Decision Cards and one-user decisions |
| Rule/AI | `ReviewAnalysisPipeline`, `RuleEngineService`, AI services | Rule-first analysis, encrypted BYOK provider configuration and fallback |
| Workspace | Workspace controllers/services | Workspace aggregate, access policy and membership lifecycle |
| Invitation | Invitation controllers, `WorkspaceInvitationService` | Invite administration and recipient accept/reject lifecycle |
| Shared Session | `SharedReviewSessionController`, related services | Workspace-scoped session create/list/detail and analysis |
| Reviewer | `ReviewSessionReviewerController`, related service | Assignment, soft removal/reactivation and eligibility coordination |
| Voting | `TeamVotingController`, voting/aggregation services | Mutable reviewer votes, optimistic versioning, quorum and aggregates |
| Lifecycle | `SharedReviewSessionLifecycleService` | Close/reopen, frozen result and mutation guards |
| Audit | `TeamReviewAuditTimelineController`, related service | Safe typed newest-first timeline projection and pagination |

### Frontend

- `pages` contains route-level composition only. When a screen grows reusable controls, move those controls into its feature folder.
- `lib/api.ts` is the only frontend HTTP boundary; pages must not call `fetch` directly.
- `types.ts` mirrors public API contracts. Keep provider secrets out of types and browser storage.
- `features/workspace/TeamVotingSection.tsx` owns the Shared Session voting read/mutation
  UI. It derives My Vote visibility from the current JWT identity, active reviewer
  roster, and workspace role; the backend remains the authorization authority.
- `features/workspace/SharedSessionLifecycleControls.tsx` owns close/reopen
  presentation and mutation state. The route page coordinates successful and
  conflicting mutations with authoritative detail, voting, and reviewer refreshes.
- `features/workspace/SessionAuditTimeline.tsx` owns the read-only Shared Session
  audit projection. It preserves backend order, appends deduplicated pages, uses
  safe fallbacks for partial legacy events, and resets to page zero after a
  successful reviewer, vote, close, or reopen mutation.
- Team Voting consumes only backend-provided quorum and aggregate fields. Vote updates
  include the current `version`; a `409` triggers an authoritative refetch without
  automatically resubmitting the user's mutation.
- Keep Vite proxy configuration development-only; production must inject the API origin through deployment configuration when required.

## Rules for new modules

Small vertical slices may continue using the existing layer-first packages, as the
first Team Workspace slice does. When a module grows into several use cases and
its own persistence model, move that module in one behavior-preserving change to
a focused feature package rather than adding more generic top-level files.
Example for a mature Team Workspace:

```text
backend/src/main/java/com/microapproval/api/workspace/
├── WorkspaceController.java
├── WorkspaceService.java
├── dto/
├── model/           # Workspace-owned entities/value objects only
└── repository/
```

Keep shared entities only when they are genuinely shared. `ReviewSession` and
`MicroDecision` are foundations for Personal and shared Workspace flows.
Membership, assignment, and role policies belong to the Workspace module.
Keep membership HTTP contracts separate from entities: member APIs return
`WorkspaceMemberResponse`, and mutation requests use dedicated validated DTOs.
Invitation controllers likewise return DTOs only; invitation expiry and
membership activation remain one transactional service concern.
`ReviewSession` is the common Personal/Shared aggregate: Personal rows have no
workspace, while Shared rows require one. Do not introduce a parallel
`shared_review_sessions` table or duplicate the Rule/AI analysis workflow.
Shared closing is a separate lifecycle dimension (`closedAt`, `closedBy`,
`closeReason`, `lifecycleVersion`) and never reuses Personal `completedAt` or
introduces a CLOSED result status. All new Shared vote/reviewer mutations must
acquire the session lock and reject a closed session before changing child rows.
`ReviewSessionReviewer` is an explicit session-level assignment entity backed
by `WorkspaceMember`; it must not be replaced with `ManyToMany` or the legacy
single-user `ReviewSession.assignedTo` field. Assignment audit rows are written
in the same transaction as roster mutations.
`DecisionCardVote` is the Team-only mutable current-vote entity. It references
one `MicroDecision` and one `ReviewSessionReviewer`, while Personal decisions
remain authoritative in `MicroDecision.humanDecision`. Team vote mutations,
audit writes, card aggregation, and session aggregation share one transaction.
Audit Timeline reads are a separate projection: query by session with
`createdAt DESC, id DESC`, page the result, and map an explicit allowlist into
typed DTOs. Never expose persistence JSON, raw content, Decision Card questions,
diffs, prompts, or provider data through the audit endpoint.
Recalculation locks in the stable order session → cards → assignments → votes;
read-only vote views use fetch-join queries without exposing JPA entities.

For the frontend, add the corresponding boundary when a feature grows beyond one screen:

- Shared Review Sessions use `features/workspace/WorkspaceSessionsSection.tsx`
  for the reusable workspace list and dedicated pages for list, create, and
  detail. Keep role helpers outside component files and keep API contracts in
  `types.ts`/`lib/api.ts`.
- Shared Session reviewer roster and mutations use
  `features/workspace/SessionReviewersSection.tsx`; the route page supplies the
  workspace role and session ID while the component owns roster, candidate,
  conflict, and required-reason removal state.
- Shared Session close/reopen uses
  `features/workspace/SharedSessionLifecycleControls.tsx`; do not infer lifecycle
  state from the voting result or introduce a client-only `CLOSED` status.

```text
frontend/src/features/workspace/
├── api.ts
├── types.ts
├── components/
└── pages/
```

Route registration remains in `App.tsx` until route count makes a dedicated `routes/` directory useful.

The current Workspace feature boundary contains `WorkspaceMembersSection`,
`WorkspaceInvitationsSection`, and their focused tests under
`frontend/src/features/workspace/`. Route-level loading and workspace metadata
remain in `WorkspaceDetailPage`; membership and invitation administration,
forms, mutation feedback, and permission-aware presentation belong to feature
components. `MyInvitationsPage` owns the authenticated recipient lifecycle at
`/invitations` because it spans workspaces rather than belonging to one detail
page.

## Extension roadmap

| Module | Placement | Initial responsibility |
|---|---|---|
| Rule Engine | `rule/` when it gains workspace rules/admin APIs | Pattern lifecycle, priority, validation, and deterministic findings |
| AI Engine | `ai/` when providers/configuration expand | Provider adapters, encrypted credentials, structured-output validation, usage accounting |
| Workspace | `workspace/` when its use cases outgrow the current layers | Workspaces, membership, roles, projects, assignment policy |
| Notification | `notification/` | Domain events and in-app delivery; do not couple it directly to controllers |
| Audit | `audit/` | Read-only projections, audit links, export policy, data redaction |
| Webhook | `webhook/` | Provider-specific signature verification and translation into application commands |

## Deliberately not introduced yet

- Separate deployable services or a multi-module Maven build.
- Generic repository/service base classes.
- CQRS/event sourcing.
- A frontend state-management library.

These add operational or conceptual cost without a current requirement. Revisit only when Team Workspace, asynchronous webhooks, or audit/export workloads make the existing boundaries insufficient.
