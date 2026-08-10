# Micro Approval

Micro Approval turns deterministic and AI-assisted review findings into Decision
Cards. The repository contains the complete MVP: JWT authentication, Personal
Workspace, Shared Workspace membership and invitations, Shared Review Sessions,
reviewer assignment, Team Voting, close/reopen lifecycle, frozen results, and a
paginated Audit Timeline.

See [PROJECT_STRUCTURE.md](PROJECT_STRUCTURE.md) for architectural boundaries and
[docs/TEAM_WORKSPACE.md](docs/TEAM_WORKSPACE.md) for the Shared Workspace API,
authorization, persistence, and test contracts.

## Prerequisites

- JDK 21 or newer. The project compiles with Java release 21 and was last
  verified with JDK 25.0.2.
- Node.js `^20.19.0` or `>=22.12.0`; Node 24.14.0 and npm 11.9.0 were last
  verified.
- Docker Desktop with Docker Compose, or a local MySQL 8 instance.
- PowerShell examples below assume the repository root is the current directory.

There is intentionally no root `package.json`. Run npm commands from
`frontend/`.

## Local database

Start the repository's MySQL 8 container:

```powershell
docker compose up -d
docker compose ps
```

The compose file creates `micro_approval_db`, local user `dev_user`, and a
persistent `mysql_data` volume. These credentials are development-only. Do not
reuse them in a deployed environment, and do not run `docker compose down -v`
unless you deliberately want to erase the local database volume.

## Environment variables

The defaults match `docker-compose.yml` and local ports. Override secrets and
deployment-specific values through environment variables or a secret manager.

| Variable | Local default | Purpose |
|---|---|---|
| `DB_HOST` | `localhost` | MySQL host |
| `DB_PORT` | `3306` | MySQL port |
| `DB_NAME` | `micro_approval_db` | Database/schema |
| `DB_USER` | `dev_user` | Database user |
| `DB_PASS` | `dev_password` | Database password |
| `JWT_SECRET` | local development fallback | JWT HMAC secret; mandatory override outside local development |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:3000,http://127.0.0.1:3000` | Comma-separated exact frontend origins; never use `*` for the authenticated deployment |
| `WORKSPACE_INVITATION_EXPIRATION_DAYS` | `7` | Invitation lifetime |
| `AI_ANALYSIS_ENABLED` | `true` | Enables the Rule → optional user-AI pipeline |
| `AI_CREDENTIAL_ENCRYPTION_KEY` | empty | Base64-encoded 32-byte AES key; required before users can save provider keys |
| `RULE_ENGINE_MAX_CARDS_PER_SESSION` | `10` | Deterministic card cap |
| `AI_ANALYSIS_MAX_CARDS_PER_SESSION` | `10` | Total analysis card cap |

Generate local secrets once per environment:

```powershell
$env:JWT_SECRET = [Convert]::ToBase64String(
  [System.Security.Cryptography.RandomNumberGenerator]::GetBytes(48)
)
$env:AI_CREDENTIAL_ENCRYPTION_KEY = [Convert]::ToBase64String(
  [System.Security.Cryptography.RandomNumberGenerator]::GetBytes(32)
)
```

Keep the encryption key stable across restarts. Losing or rotating it without a
migration makes previously encrypted provider keys unreadable; users must then
enter those keys again. Provider keys are encrypted with AES-256-GCM, only their
last four characters are returned, and real keys must never be committed.

## Run the backend

```powershell
cd backend
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21' # omit/change when Java is already configured
.\mvnw.cmd spring-boot:run
```

The API starts on `http://localhost:8080`. On startup, Flyway validates and
applies every immutable migration from V1 through V14. Hibernate uses
`ddl-auto=validate`; it never creates or repairs the schema. Add a new forward
migration for every schema change and never edit a migration already applied.

The backend can run without an OpenAI/Gemini key. Users configure their own
provider, model, and key under **Thiết lập AI**. Without an active user
configuration, analysis remains Rule-only. Provider errors return a session with
Rule cards and `FALLBACK` status instead of failing the session.

## Run the frontend

Open a second terminal from the repository root:

```powershell
cd frontend
npm ci
npm run dev
```

Open `http://localhost:3000`. Vite proxies `/gateway/*` to the backend's
`/api/*` routes during development. This proxy is development-only; production
hosting must provide the equivalent API routing or an explicit deployment
configuration.

## Default ports

| Service | Port |
|---|---:|
| MySQL | 3306 |
| Spring Boot API | 8080 |
| Vite frontend | 3000 |

## Verification

Backend tests use the configured MySQL schema and clean their own fixtures:

```powershell
cd backend
.\mvnw.cmd clean test
```

Frontend quality gates:

```powershell
cd frontend
npm ci
npm run type-check
npm run lint
npm test
npm run build
```

Repository whitespace validation:

```powershell
git diff --check
```

The stabilization baseline is 109 backend tests and 134 frontend tests across
13 files, with type-check, lint, production build, Flyway V14 validation, and
Hibernate validation passing.

## Security and operational notes

- Passwords use BCrypt. Protected APIs require JWT; frontend role checks are
  presentation only and backend authorization remains authoritative.
- Set a unique `JWT_SECRET`, production database credentials, and exact CORS
  origins in every deployed environment. The repository fallback secret and
  compose credentials are local-development values only.
- API responses do not expose password hashes, provider keys, encrypted key
  material, or Authorization headers. Audit projections allowlist structured
  metadata and exclude submitted code, prompts, diffs, and provider data.
- Invitation, reviewer, vote, lifecycle, and audit resources are workspace
  scoped. Hidden or cross-workspace resources return the established 404
  contract.

## Known MVP limitations

- AI calls are synchronous and currently have no retry, circuit breaker, or
  provider-specific configurable timeout. Rule-only fallback is implemented.
- There is no account deletion API. A JWT whose user was hard-deleted manually
  in MySQL can cause public auth requests carrying that stale token to return
  403; normal application flows never hard-delete users. Clear the browser
  session before logging in again after manual database maintenance.
- The MVP does not include notifications, realtime updates, GitHub integration,
  monitoring, backup/restore automation, CI/CD, or production deployment.
- Local integration tests use the configured MySQL instance; isolated CI should
  provide a disposable database (for example, a dedicated schema or
  Testcontainers) before parallel test execution.
