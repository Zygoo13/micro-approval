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
and direct administration of already registered users:

1. An authenticated user creates a workspace.
2. The workspace and its creator membership are persisted in one transaction.
3. The creator receives role `OWNER` and status `ACTIVE`.
4. Users list only workspaces where their membership is `ACTIVE`.
5. Only an active member can read workspace details.
6. Active members can list visible workspace memberships.
7. OWNER and ADMIN can add, change, or remove members within their role limits.
8. A removed registered user can be reactivated without creating a duplicate row.

Invitations, self-leave, ownership transfer, Team review sessions, notifications,
webhooks, and integrations remain outside these slices.

## Frontend

The React application provides three authenticated routes:

- `/workspaces`: loading, error, empty, and active-workspace list states.
- `/workspaces/new`: client validation and workspace creation.
- `/workspaces/:workspaceId`: membership-gated workspace detail.

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
The responsive table becomes labeled member cards on small screens. Invitation,
unregistered users, self-leave, ownership transfer, and Team Sessions remain
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

Workspace names are stored with a maximum of 100 characters. Descriptions are
validated at 1,000 characters by the API and stored as nullable `TEXT` so a
legacy description is never truncated. Hibernate remains configured with
`ddl-auto: validate`; Flyway is the only schema owner.

## Domain model

- `Workspace`: the workspace aggregate root and explicit owner reference.
- `WorkspaceMember`: membership relation represented as an entity, not `ManyToMany`.
- `WorkspaceRole`: `OWNER`, `ADMIN`, `REVIEWER`, `MEMBER`, `AUDITOR`.
- `MembershipStatus`: `PENDING`, `ACTIVE`, `REMOVED`.

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

## API

Authentication registration and login are public:

| Method | Path | Result |
|---|---|---|
| `POST` | `/api/v1/auth/register` | Register a user and return `201 Created` |
| `POST` | `/api/v1/auth/login` | Authenticate a user and return `200 OK` |

The frontend reaches these paths through `/gateway/v1/auth/*`. Local CORS
configuration accepts the exact `localhost:3000` and `127.0.0.1:3000` origins;
deployments configure trusted origins through `CORS_ALLOWED_ORIGINS`.

All Workspace and Member Management endpoints require a valid JWT:

| Method | Path | Result |
|---|---|---|
| `POST` | `/api/workspaces` | Create a workspace and return `201 Created` |
| `GET` | `/api/workspaces` | List the current user's active workspaces |
| `GET` | `/api/workspaces/{workspaceId}` | Return detail for an active member |
| `GET` | `/api/workspaces/{workspaceId}/members` | List `ACTIVE` and `PENDING` memberships |
| `POST` | `/api/workspaces/{workspaceId}/members` | Add or reactivate a registered user; return `200 OK` |
| `PATCH` | `/api/workspaces/{workspaceId}/members/{memberId}/role` | Change a member role |
| `DELETE` | `/api/workspaces/{workspaceId}/members/{memberId}` | Soft-remove a member; return `204 No Content` |

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
- `PENDING`: reserved for a future invitation flow; appears in member lists and
  prevents a duplicate direct add.
- `REMOVED`: cannot access the workspace, is hidden from member lists, and can
  be reactivated by a later add.

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

## Main implementation files

- `entity/Workspace.java`, `entity/WorkspaceMember.java`, and their enums.
- `repository/WorkspaceRepository.java` and `WorkspaceMemberRepository.java`.
- `service/WorkspaceAccessService.java` and `WorkspaceService.java`.
- `service/WorkspaceMemberService.java`.
- `controller/WorkspaceController.java` and `WorkspaceMemberController.java`.
- `dto/CreateWorkspaceRequest.java`, `WorkspaceResponse.java`, and
  `WorkspaceSummaryResponse.java`.
- `dto/AddWorkspaceMemberRequest.java`,
  `UpdateWorkspaceMemberRoleRequest.java`, and `WorkspaceMemberResponse.java`.
- `db/migration/V5__create_workspaces_and_memberships.sql`.
- `db/migration/V6__migrate_legacy_teams_to_workspaces.sql`.
- `db/migration/V7__rename_team_workspace_type_to_shared.sql`.

## Next Team Workspace work

Continue in separate vertical slices:

1. Invitation lifecycle and pending-membership activation.
2. Projects owned by a workspace.
3. Shared Workspace review-session creation and assignment.
4. Notifications, audit projections, and integrations.

All future collaboration features must use `Workspace` and `WorkspaceMember`.
Do not reintroduce a parallel Team persistence model.
