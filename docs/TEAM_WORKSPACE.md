# Shared Workspace

## Domain decision

`Workspace` is the system's only collaboration aggregate:

```text
Workspace
└── WorkspaceMember
```

The specification's term "Team Workspace" means a Shared Workspace. It does not
mean a separate `Team` aggregate. The old V1 `teams` and `team_members` tables
modeled the same identity, ownership, membership, and authorization boundary and
therefore were migrated rather than retained as a parallel model.

## Scope

The implemented slices cover workspace creation, membership-gated discovery,
direct administration of already registered users, and end-to-end invitations:

1. An authenticated user creates a workspace.
2. The workspace and its creator membership are persisted in one transaction.
3. The creator receives role `OWNER` and status `ACTIVE`.
4. Users list only workspaces where their membership is `ACTIVE`.
5. Only an active member can read workspace details.
6. Active members can list visible workspace memberships.
7. OWNER and ADMIN can add, change, or remove members within their role limits.
8. A removed registered user can be reactivated without creating a duplicate row.
9. OWNER or ADMIN invites an email within their role boundary.
10. The matching authenticated recipient accepts or rejects the invitation.
11. Accept creates or reactivates an ACTIVE membership in the same transaction.
12. OWNER, ADMIN, or REVIEWER creates a Shared Review Session.
13. The shared Rule → AI pipeline produces Decision Cards using system rules
    plus rules scoped to that workspace.
14. Every ACTIVE member can list and read Shared sessions and their cards.
15. OWNER or ADMIN assigns eligible ACTIVE workspace members as session reviewers.
16. Reviewer removal is soft, re-assignment reuses the same row, and each
    roster mutation writes a transactional audit event.

Email delivery, public invitation links, self-leave, ownership transfer,
Team voting, notifications,
webhooks, and integrations remain outside these slices.

## Frontend

Shared Review Sessions are available through protected routes:

```text
/workspaces/:workspaceId/sessions
/workspaces/:workspaceId/sessions/new
/workspaces/:workspaceId/sessions/:sessionId
```

Workspace Detail embeds the session list with loading, empty, error/retry, and
permission-aware create states. OWNER, ADMIN, and REVIEWER see creation actions;
MEMBER and AUDITOR remain read-only. The create form supports RAW_SNIPPET,
GIT_DIFF, and INTENT_MATCHING with backend-aligned validation and a visible
synchronous-analysis state. Detail renders Rule and AI Decision Cards with
textual source labels. AI FALLBACK is a non-fatal outcome and retains Rule
cards. Shared Session Detail also renders the active reviewer roster for every
ACTIVE role. OWNER and ADMIN receive candidate-filtered assign controls and a
keyboard-usable, required-reason removal form; REVIEWER, MEMBER, and AUDITOR see
the same roster read-only. Candidate options include only ACTIVE OWNER, ADMIN,
or REVIEWER memberships not already present in the active roster. Voting,
comments, and session closing remain outside this slice.

The React application provides four Team Workspace route patterns:

- `/workspaces`: loading, error, empty, and active-workspace list states.
- `/workspaces/new`: client validation and workspace creation.
- `/workspaces/:workspaceId`: membership-gated workspace detail.
- `/invitations`: authenticated recipient invitation inbox.

The frontend reuses the existing fetch client, JWT storage, protected layout, and
CSS design language. Workspace calls use `/gateway/workspaces`; the Vite proxy
rewrites `/gateway` to the backend `/api` prefix. No second HTTP client or UI
framework was introduced.

The detail page displays only fields returned by the backend. Its Members
section loads real memberships and supports permission-aware add, role-change,
and soft-remove actions without a full page reload. OWNER can manage every
non-owner role; ADMIN can manage only REVIEWER, MEMBER, and AUDITOR; other roles
have a read-only list. Backend authorization remains authoritative.

Member UI calls the four `/api/workspaces/{workspaceId}/members` endpoints via
the existing `/gateway/workspaces` client and Vite rewrite. It handles loading,
empty, retry, validation, 400/401/403/404/409, network, and server error states.
The responsive table becomes labeled member cards on small screens.

OWNER and ADMIN also receive a workspace Invitation section. It loads invitation
history, creates invitations for registered or future email addresses, and
keeps revoked rows in the history. OWNER may invite/revoke every non-owner role;
ADMIN may invite REVIEWER/MEMBER/AUDITOR and cannot revoke an ADMIN invitation.
Other roles neither render the section nor call its administration API.

`/invitations` lists invitations matching the authenticated JWT email. A
recipient can accept or reject a live PENDING invitation after confirmation.
Mutation responses update the item without a page reload; accepted items expose
an action to enter the workspace. Client time provides an immediate expiration
display and hides stale actions, while backend `410 Gone` remains authoritative
and updates the displayed state to EXPIRED. The page and section handle loading,
empty, retry, 400/401/403/404/409/410, network, and server states. Email delivery,
public links, resend, self-leave, ownership transfer, and Team Sessions remain
unimplemented.

## Persistence

Flyway migration `V5__create_workspaces_and_memberships.sql` adds:

- `workspaces`: identity, name, optional description, owner, and timestamps.
- `workspace_members`: a first-class membership with role, status, and join time.
- Foreign keys from both tables to `users`.
- A cascading foreign key from membership to workspace.
- A unique constraint on `(workspace_id, user_id)`.
- Indexes for workspace, user, status, and active-membership lookup.

Flyway migration `V6__migrate_legacy_teams_to_workspaces.sql` then:

- preserves legacy Team IDs, ownership, timestamps, repository/integration
  settings, AI enablement, and card limits in `workspaces`;
- migrates every legacy member to `workspace_members` with status `ACTIVE`;
- maps `MANAGER → ADMIN`, `REVIEWER → REVIEWER`, `DEVELOPER → MEMBER`, and
  `AUDITOR → AUDITOR`;
- makes the legacy owner the single `OWNER`, reusing the old membership ID when
  available;
- renames `projects`, `rule_patterns`, and `audit_links` scope columns from
  `team_id` to `workspace_id` and replaces their foreign keys;
- validates workspace, membership, and owner invariants before destructive DDL;
- drops `team_members` and then `teams`.

Flyway migration `V7__rename_team_workspace_type_to_shared.sql` safely widens
the review-session enum, converts existing `TEAM` rows to `SHARED`, and then
removes the old enum value. `WorkspaceType.SHARED` is now the only collaborative
session discriminator.

The local database contained no legacy Team, membership, project, team-scoped
rule, or audit-link rows before V6. A separate temporary-database rehearsal with
all legacy roles and all three dependent tables verified the data-migration path.
The rehearsal database was removed after verification. V7 was also rehearsed
with one `PERSONAL` and one legacy `TEAM` session; both rows remained and the
legacy row became `SHARED`.

Flyway migration `V8__create_workspace_invitations.sql` adds
`workspace_invitations` with workspace/inviter foreign keys, normalized email,
role and lifecycle status, expiry and response timestamps, lookup indexes, and
a generated `pending_email`. The unique `(workspace_id, pending_email)` index
allows invitation history while preventing two PENDING invitations for one
workspace/email pair.

Flyway migration `V9__support_shared_review_sessions.sql` adds nullable
`review_sessions.workspace_id`, backfills legacy SHARED rows through their
project, and fails safely if a legacy workspace cannot be inferred. Its foreign
key, workspace indexes, and CHECK constraint enforce:

```text
PERSONAL -> workspace_id IS NULL
SHARED   -> workspace_id IS NOT NULL
```

V10 upgrades review-session creation/completion timestamps to microsecond
precision so newest-first ordering remains deterministic for sessions created
within the same second. Existing Personal rows and Decision Cards are retained.

V11 creates `review_session_reviewers` and `team_review_audit_events`.
Assignment is unique per `(session_id, workspace_member_id)`, has ASSIGNED and
REMOVED states, and carries an optimistic version. Mutations serialize on the
Shared Session row. Removal keeps history and requires a reason; reactivation
clears removal metadata and preserves the assignment ID. Audit events record
assign, remove, and reactivate in the same transaction. The legacy
`review_sessions.assigned_to` column is retained and not backfilled because it
never had active multi-reviewer semantics.

Workspace names are stored with a maximum of 100 characters. Descriptions are
validated at 1,000 characters by the API and stored as nullable `TEXT` so a
legacy description is never truncated. Hibernate remains configured with
`ddl-auto: validate`; Flyway is the only schema owner.

## Domain model

- `Workspace`: the workspace aggregate root and explicit owner reference.
- `WorkspaceMember`: membership relation represented as an entity, not `ManyToMany`.
- `WorkspaceRole`: `OWNER`, `ADMIN`, `REVIEWER`, `MEMBER`, `AUDITOR`.
- `MembershipStatus`: `PENDING`, `ACTIVE`, `REMOVED`.
- `WorkspaceInvitationStatus`: `PENDING`, `ACCEPTED`, `REJECTED`, `REVOKED`,
  `EXPIRED`.

REST responses use DTOs only. JPA entities and relationships are never serialized.

The database invariants are:

- every Workspace created or migrated has exactly one active `OWNER`;
- `(workspace_id, user_id)` is unique;
- the member API cannot add a second `OWNER`, change the owner's role, or remove
  the owner;
- reactivation reuses the existing `REMOVED` membership and resets `joinedAt`;
- every membership references an existing Workspace and User;
- every formerly Team-scoped project, rule, and audit link references the
  migrated Workspace with the same ID.
- an invitation can never grant `OWNER`, and only one PENDING invitation may
  exist for a normalized workspace/email pair.

## Invitation lifecycle

Invitation uses an ID plus authenticated JWT identity. There is no public token:
the current slice has no email link, and recipient authorization always compares
the normalized JWT email with the stored invitation email.

Valid transitions are:

```text
PENDING -> ACCEPTED
PENDING -> REJECTED
PENDING -> REVOKED
PENDING -> EXPIRED
```

Every repeated or cross-terminal transition returns `409 Conflict`. An expired
mutation records `EXPIRED` and returns `410 Gone`. Read responses compute an
effective EXPIRED status even before a mutation persists it. Expiration defaults
to seven days and is configured by `WORKSPACE_INVITATION_EXPIRATION_DAYS`; no
background scheduler is used.

Invitation does not create a PENDING membership. Accept creates an ACTIVE row,
or reuses an existing REMOVED row and refreshes role/join time. ACTIVE or legacy
PENDING membership conflicts safely. Invitation and membership changes share
one transaction and use pessimistic locks; database unique constraints remain
the final concurrent-write defense.

## Shared Review Sessions

Shared sessions extend the existing `ReviewSession`; no parallel table or
analysis pipeline exists. Creation accepts the same input vocabulary as
Personal Workspace: `RAW_SNIPPET`, `GIT_DIFF`, and `INTENT_MATCHING`, using
`rawContent` plus `promptContent` for intent. `workspaceId` and creator identity
come exclusively from the path and authenticated principal.

`ReviewAnalysisPipeline` runs configured rules first, removes matched ranges,
then sends remaining content to the creator's active AI provider configuration.
Personal sessions load system rules only. Shared sessions load system rules and
active rules whose `workspace_id` matches the current workspace; rules from any
other workspace are excluded by the repository query. AI exceptions preserve
Rule cards and record the existing `FALLBACK` status/error without exposing raw
provider failures.

Creation stays synchronous and follows the Personal transaction boundary:
persist session, run Rule/AI, persist cards/status, commit. A no-card session is
marked `APPROVED`; a session with pending cards remains `PENDING`. AI fallback
does not roll back Rule results, while Rule/database failures roll back the
creation transaction. The external AI call therefore occurs inside a database
transaction; this is accepted for the current synchronous MVP and should be
revisited with an explicit processing state before asynchronous execution.

Decision Cards continue to reference only `session_id`. The existing single
`human_decision`/`decided_by` model is unchanged, and Shared voting or vote
aggregation is not exposed in this slice.

## API

Authentication registration and login are public:

| Method | Path | Result |
|---|---|---|
| `POST` | `/api/v1/auth/register` | Register a user and return `201 Created` |
| `POST` | `/api/v1/auth/login` | Authenticate a user and return `200 OK` |

The frontend reaches these paths through `/gateway/v1/auth/*`. Local CORS
configuration accepts the exact `localhost:3000` and `127.0.0.1:3000` origins;
deployments configure trusted origins through `CORS_ALLOWED_ORIGINS`.

All Workspace, Member Management, and Invitation endpoints require a valid JWT:

| Method | Path | Result |
|---|---|---|
| `POST` | `/api/workspaces` | Create a workspace and return `201 Created` |
| `GET` | `/api/workspaces` | List the current user's active workspaces |
| `GET` | `/api/workspaces/{workspaceId}` | Return detail for an active member |
| `GET` | `/api/workspaces/{workspaceId}/members` | List `ACTIVE` and `PENDING` memberships |
| `POST` | `/api/workspaces/{workspaceId}/members` | Add or reactivate a registered user; return `200 OK` |
| `PATCH` | `/api/workspaces/{workspaceId}/members/{memberId}/role` | Change a member role |
| `DELETE` | `/api/workspaces/{workspaceId}/members/{memberId}` | Soft-remove a member; return `204 No Content` |
| `GET` | `/api/workspaces/{workspaceId}/invitations` | OWNER/ADMIN invitation history |
| `POST` | `/api/workspaces/{workspaceId}/invitations` | Create a PENDING invitation; return `201 Created` |
| `POST` | `/api/workspaces/{workspaceId}/invitations/{invitationId}/revoke` | Revoke a PENDING invitation |
| `GET` | `/api/workspace-invitations/mine` | Invitations matching the JWT email |
| `POST` | `/api/workspace-invitations/{invitationId}/accept` | Accept and activate membership |
| `POST` | `/api/workspace-invitations/{invitationId}/reject` | Reject without membership creation |
| `POST` | `/api/workspaces/{workspaceId}/sessions` | Create and synchronously analyze a Shared Review Session; return `201 Created` |
| `GET` | `/api/workspaces/{workspaceId}/sessions` | ACTIVE-member summaries, newest first |
| `GET` | `/api/workspaces/{workspaceId}/sessions/{sessionId}` | ACTIVE-member detail with Decision Cards |
| `GET` | `/api/workspaces/{workspaceId}/sessions/{sessionId}/reviewers` | List currently ASSIGNED reviewers for an ACTIVE member |
| `POST` | `/api/workspaces/{workspaceId}/sessions/{sessionId}/reviewers` | OWNER/ADMIN assigns or reactivates a reviewer; return `200 OK` |
| `POST` | `/api/workspaces/{workspaceId}/sessions/{sessionId}/reviewers/{reviewerAssignmentId}/remove` | OWNER/ADMIN soft-removes an assignment with a reason; return `200 OK` |

Create request:

```json
{
  "name": "Payments",
  "description": "Review workspace for the payments team"
}
```

Create/detail response:

```json
{
  "id": "workspace-uuid",
  "name": "Payments",
  "description": "Review workspace for the payments team",
  "ownerId": "user-uuid",
  "currentUserRole": "OWNER",
  "createdAt": "2026-07-25T21:00:00",
  "updatedAt": "2026-07-25T21:00:00"
}
```

List response:

```json
[
  {
    "id": "workspace-uuid",
    "name": "Payments",
    "description": "Review workspace for the payments team",
    "ownerId": "user-uuid",
    "currentUserRole": "REVIEWER",
    "createdAt": "2026-07-25T21:00:00"
  }
]
```

Names are required, trimmed, and validated before persistence. Descriptions are
trimmed; an empty description is stored as `NULL`.

Add-member request:

```json
{
  "email": "reviewer@example.com",
  "role": "REVIEWER"
}
```

Role-change request:

```json
{
  "role": "AUDITOR"
}
```

Member responses expose the membership ID, user ID, email, display name, role,
status, and join time. They never expose credentials or JPA entities.

Membership status semantics:

- `ACTIVE`: can access the workspace and appears in member lists.
- `PENDING`: legacy/direct-administration membership state; appears in member
  lists and prevents a duplicate direct add or invitation. The V8 invitation
  flow does not create this state.
- `REMOVED`: cannot access the workspace, is hidden from member lists, and can
  be reactivated by a later add.

Create-invitation request:

```json
{
  "email": "future.user@example.com",
  "role": "REVIEWER"
}
```

Workspace invitation responses include invitation/workspace IDs, normalized
email, role, effective status, inviter identity, creation/expiry/response times.
The `mine` response adds workspace name and does not expose another recipient's
email or any token/hash.

Create Shared Session request:

```json
{
  "title": "Review payment validation",
  "mode": "GIT_DIFF",
  "rawContent": "diff --git ...",
  "promptContent": null
}
```

`promptContent` is required only for `INTENT_MATCHING`. Create/detail responses
include session/workspace IDs, `workspaceType=SHARED`, mode, status, AI outcome,
creator metadata, timestamps, input, and Decision Cards. List uses a summary
without source content or cards.

## Authorization rules

`WorkspaceAccessService` is the single membership-access boundary for this slice.
It accepts only `ACTIVE` memberships.

Detail lookup returns the same `404 Not Found` response for a missing workspace,
a non-member, and an inactive member. This avoids revealing whether a private
workspace exists. List queries filter by active membership in the database.

The existing security rule `.anyRequest().authenticated()` protects these paths;
no Personal Workspace security or endpoint was changed.

Member-management policy:

| Caller | View members | Add/change/remove |
|---|---:|---|
| `OWNER` | Yes | Any non-owner role, including `ADMIN` |
| `ADMIN` | Yes | `REVIEWER`, `MEMBER`, and `AUDITOR` only |
| `REVIEWER`, `MEMBER`, `AUDITOR` | Yes | No |

No caller can add a second owner, mutate/remove the owner, or remove themselves.
Member mutations use pessimistic row locks where an authorization or membership
decision is updated. The unique database constraint remains the final defense
against concurrent duplicate adds.

Invitation policy:

| Caller | List | Invite | Revoke |
|---|---:|---|---|
| `OWNER` | Yes | `ADMIN`, `REVIEWER`, `MEMBER`, `AUDITOR` | Any PENDING invitation |
| `ADMIN` | Yes | `REVIEWER`, `MEMBER`, `AUDITOR` | PENDING standard-role invitations |
| Other active roles | No | No | No |
| Matching recipient | Own only | No | Accept or reject own PENDING invitation |

Non-members receive the existing hidden `404` contract for workspace-scoped
administration. A wrong recipient also receives `404`, so invitation IDs cannot
be used to discover another email or workspace.

Shared Session policy:

| Caller | Create | List/detail/cards |
|---|---:|---:|
| `OWNER` | Yes | Yes |
| `ADMIN` | Yes | Yes |
| `REVIEWER` | Yes | Yes |
| `MEMBER` | No (`403`) | Yes |
| `AUDITOR` | No (`403`) | Yes |
| PENDING/REMOVED/non-member | Hidden (`404`) | Hidden (`404`) |

Detail lookup includes `workspaceId + sessionId + SHARED`, so another
workspace's session and a Personal session are both hidden as `404`.

Reviewer-assignment policy:

| Caller | View active roster | Assign/reactivate/remove |
|---|---:|---:|
| `OWNER` | Yes | Yes |
| `ADMIN` | Yes | Yes |
| `REVIEWER`, `MEMBER`, `AUDITOR` | Yes | No (`403`) |
| PENDING/REMOVED/non-member | Hidden (`404`) | Hidden (`404`) |

Only an ACTIVE membership with role `OWNER`, `ADMIN`, or `REVIEWER` is an
eligible assignment target. The request uses the workspace-membership ID, not
an arbitrary user ID. Duplicate active assignment returns `409`; a removed row
is reactivated with the same assignment ID. Removal requires a non-blank reason
of at most 1,000 characters. Assignment endpoints are scoped by workspace,
session, and `SHARED`, so Personal sessions and cross-workspace identifiers are
hidden as `404`.

Errors use `400` for invalid operations, `403` for an active member without
sufficient role authority, `404` for hidden/missing workspace resources or an
unknown registered user, and `409` for membership-state conflicts.

## Tests

`WorkspaceControllerIntegrationTest` covers:

- successful creation;
- automatic `OWNER` + `ACTIVE` creator membership;
- shared transaction boundary for workspace and membership creation;
- active-only listing;
- detail access for an active member;
- hidden detail for a non-member;
- rejection of a blank name;
- database enforcement of unique membership;
- authentication on the workspace endpoints.

`WorkspaceMemberControllerIntegrationTest` covers:

- active-member listing and exclusion of removed memberships;
- hidden workspace access for non-members and removed members;
- OWNER and ADMIN add/change/remove matrices;
- rejection of unknown users, duplicate active/pending membership, and OWNER
  assignment;
- reactivation using the original membership ID;
- owner, self-removal, repeated-removal, and inactive-member invariants;
- authentication and exactly-one-owner preservation.

`WorkspaceInvitationControllerIntegrationTest` covers role-aware creation and
listing, unregistered emails, normalization, membership conflicts, generated
unique constraints, recipient-only mine/accept/reject, computed expiration,
410 persistence, revoke policy, processed-state conflicts, REMOVED reactivation,
single-owner preservation, endpoint authentication, and two concurrent accepts
producing exactly one membership.

`SharedReviewSessionControllerIntegrationTest` covers OWNER/ADMIN/REVIEWER
creation, MEMBER/AUDITOR denial, inactive/non-member hiding, all input modes,
validation, system and workspace rule execution, cross-workspace isolation, AI
fallback, Decision Card ownership, newest-first list isolation, detail scope,
Personal exclusion, authentication, and database workspace/type constraints.
`PersonalSessionAiAnalysisTest` continues to verify the shared pipeline's Rule
before AI order, token accounting, and fallback behavior for Personal sessions.

`ReviewSessionReviewerControllerIntegrationTest` covers roster visibility,
OWNER/ADMIN authorization, eligible and invalid targets, cross-workspace and
Personal isolation, duplicate rejection, soft removal, required removal reason,
same-row reactivation, and transactional audit records.
`ReviewSessionReviewerConcurrencyIntegrationTest` runs two simultaneous assigns
and verifies one success, one `409` conflict, one assignment row, and one audit
event.

Run this slice:

```powershell
cd backend
./mvnw.cmd -Dtest=WorkspaceControllerIntegrationTest test
```

Run the complete backend regression suite:

```powershell
cd backend
./mvnw.cmd test
```

Tests require the configured MySQL database because the production schema uses
MySQL enums and Flyway migrations.

Frontend verification:

```powershell
cd frontend
npm run type-check
npm run lint
npm test
npm run build
```

The Workspace page tests cover loading, empty and populated lists, client
validation, successful creation and navigation, failed creation, and the
not-found detail state.

`WorkspaceMembersSection.test.tsx` covers member loading/render/retry,
read-only access, OWNER and ADMIN add options, client email validation, 404/409
feedback, role-change permissions and failure rollback, owner protection,
confirmation cancellation, successful removal, and failed-removal state.

`WorkspaceInvitationsSection.test.tsx` covers loading/empty/list/retry,
OWNER/ADMIN/MEMBER presentation, role options, validation, duplicate and
membership conflicts, successful create/revoke, cancelled revoke, terminal
states, and client expiration. `MyInvitationsPage.test.tsx` covers inbox states,
accept/reject, workspace navigation, confirmation cancellation, 404/409
stability, and authoritative 410 expiration. `App.test.tsx` verifies the
protected route and navigation entry.

`SessionReviewersSection.test.tsx` covers roster loading/empty/error/retry,
all five role presentations, candidate eligibility, assignment validation and
pending state, authoritative create/reactivate responses, duplicate and stale
conflicts, required-reason removal, cancellation, delayed server confirmation,
and self-remove messaging. Shared and Personal detail tests verify that the
section is mounted only after a valid Shared Session is loaded.

## Main implementation files

- `entity/Workspace.java`, `entity/WorkspaceMember.java`, and their enums.
- `repository/WorkspaceRepository.java` and `WorkspaceMemberRepository.java`.
- `service/WorkspaceAccessService.java` and `WorkspaceService.java`.
- `service/WorkspaceMemberService.java`.
- `service/WorkspaceInvitationService.java`.
- `service/ReviewAnalysisPipeline.java` and `SharedReviewSessionService.java`.
- `service/ReviewSessionReviewerService.java`.
- `controller/WorkspaceController.java` and `WorkspaceMemberController.java`.
- `controller/WorkspaceInvitationController.java` and
  `MyWorkspaceInvitationController.java`.
- `controller/SharedReviewSessionController.java`.
- `controller/ReviewSessionReviewerController.java`.
- `dto/CreateWorkspaceRequest.java`, `WorkspaceResponse.java`, and
  `WorkspaceSummaryResponse.java`.
- `dto/AddWorkspaceMemberRequest.java`,
  `UpdateWorkspaceMemberRoleRequest.java`, and `WorkspaceMemberResponse.java`.
- `db/migration/V5__create_workspaces_and_memberships.sql`.
- `db/migration/V6__migrate_legacy_teams_to_workspaces.sql`.
- `db/migration/V7__rename_team_workspace_type_to_shared.sql`.
- `db/migration/V8__create_workspace_invitations.sql`.
- `db/migration/V9__support_shared_review_sessions.sql`.
- `db/migration/V10__increase_review_session_timestamp_precision.sql`.
- `db/migration/V11__create_review_session_reviewers.sql`.
- `frontend/src/features/workspace/WorkspaceInvitationsSection.tsx`.
- `frontend/src/features/workspace/SessionReviewersSection.tsx`.
- `frontend/src/pages/MyInvitationsPage.tsx` and route `/invitations`.

## Next Team Workspace work

Continue in separate vertical slices:

1. Team voting backend using the separately designed per-reviewer decision model.
2. Team voting frontend after its API and aggregate semantics are stable.
3. Optional email delivery and authenticated invitation links.
4. Projects owned by a workspace.
5. Notifications, audit projections, and integrations.

All future collaboration features must use `Workspace` and `WorkspaceMember`.
Do not reintroduce a parallel Team persistence model.
