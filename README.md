# Micro Approval

Micro Approval is a code-review workflow that turns deterministic and AI-assisted findings into individual Decision Cards. The current implementation delivers the Personal Workspace and the first end-to-end slice of Shared Workspace.

## Current capabilities

- Email/password authentication with BCrypt and JWT.
- Private Personal Workspace with Raw Snippet, Intent Matching, and Git Diff inputs.
- Database-configured Rule Engine, evaluated before AI.
- Per-user AI configuration for OpenAI or Google Gemini. API keys are encrypted at rest with AES-256-GCM and are never returned to the browser.
- Decision Cards, one-time approve/reject, review notes, and personal session history.
- Shared Workspace creation plus active-membership list/detail access in the backend and web UI.
- Member administration for registered users in the backend and web UI, with
  OWNER/ADMIN role-aware actions, soft removal, and membership reactivation.
- End-to-end Workspace Invitation lifecycle for registered or future users,
  with permission-aware administration, `/invitations`, accept/reject/revoke,
  expiration UI, and transactional membership activation.
- Backend Shared Review Sessions for Raw Snippet, Intent Matching, and Git Diff,
  reusing the Rule → AI pipeline with workspace-isolated rules and Decision Cards.
- Frontend Shared Review Session list, create, and detail flows with protected
  workspace routes, role-aware creation, Decision Cards, and AI fallback states.
- Backend reviewer assignment for Shared Sessions with ACTIVE-role eligibility,
  soft removal/reactivation, concurrency protection, and transactional audit events.
- Frontend reviewer roster on Shared Session Detail with OWNER/ADMIN assignment
  controls, required-reason removal, candidate filtering, and read-only member views.
- Backend Team Voting for Shared Decision Cards with per-assignment mutable votes,
  unanimous quorum, materialized card/session aggregates, stale-vote protection,
  optimistic conflicts, lifecycle recalculation, and transactional audit events.
- Frontend Team Voting on Shared Session Detail with transparent vote/note lists,
  backend-authoritative card/session aggregates, assignment-aware My Vote controls,
  stale-vote reconfirmation, and conflict refresh. Personal Sessions remain isolated.
- Backend Shared Session close/reopen lifecycle for OWNER/ADMIN, with frozen
  APPROVED/REJECTED results, mutation guards, lifecycle versioning, and
  transactional SESSION_CLOSED/SESSION_REOPENED audit events. Shared Session
  Detail provides permission-aware close/reopen controls, closed metadata, and
  read-only vote/reviewer views; an Audit Timeline API/UI remains pending.
- Flyway-managed MySQL schema.

See [PROJECT_STRUCTURE.md](PROJECT_STRUCTURE.md) for the architectural map, conventions, and module roadmap.
See [docs/TEAM_WORKSPACE.md](docs/TEAM_WORKSPACE.md) for the Team Workspace schema, API, access rules, and test scope.

## Collaboration domain

`Workspace` is the only collaboration aggregate. `WorkspaceMember` carries a
user's role and membership status within that workspace. The former
`teams`/`team_members` model was the legacy name for the same concept and is
migrated and removed by Flyway V6.

The historical specification term "Team Workspace" means Shared Workspace; it
does not represent a separate `Team` entity. Flyway V7 also renames the
`ReviewSession.workspaceType` discriminator from `TEAM` to `SHARED`.

## Local development

```powershell
docker compose up -d

cd backend
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-25.0.2'
./mvnw.cmd spring-boot:run

cd ../frontend
npm install
npm run dev
```

Open `http://localhost:3000`.

`POST /api/v1/auth/register` and `POST /api/v1/auth/login` are public. All
Workspace, Member Management, Invitation, and Shared Session APIs require a valid JWT. Local development
accepts both `http://localhost:3000` and `http://127.0.0.1:3000`; override the
exact deployment origins with `CORS_ALLOWED_ORIGINS` as a comma-separated list.

## Server secrets

Never commit production secrets. Configure these values through your deployment secret store:

- `JWT_SECRET`
- `CORS_ALLOWED_ORIGINS`: exact trusted frontend origins; do not use `*` for a
  deployed authenticated application.
- `WORKSPACE_INVITATION_EXPIRATION_DAYS`: invitation lifetime; defaults to 7.
- `AI_CREDENTIAL_ENCRYPTION_KEY`: a Base64-encoded 32-byte key used to encrypt users' provider keys.
- Database variables: `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASS`.

Generate an encryption key locally once, keep it in a secret manager, and use the same value after every restart:

```powershell
$key = [Convert]::ToBase64String([System.Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
```

Changing or losing this key makes previously encrypted provider keys unreadable. Re-enter those provider keys after a deliberate rotation.

## Verification

```powershell
cd backend
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-25.0.2'
./mvnw.cmd test

cd ../frontend
npm run build
```
