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

## Configure the environment

Create the local environment file before using Compose:

```powershell
Copy-Item .env.example .env
```

Fill every required blank value in `.env`. Generate independent secrets instead
of copying examples from documentation:

```powershell
$jwt = [Convert]::ToBase64String(
  [System.Security.Cryptography.RandomNumberGenerator]::GetBytes(48)
)
$encryption = [Convert]::ToBase64String(
  [System.Security.Cryptography.RandomNumberGenerator]::GetBytes(32)
)
```

Use `$jwt` for `JWT_SECRET`, `$encryption` for
`AI_CREDENTIAL_ENCRYPTION_KEY`, and choose unique MySQL passwords. `.env` is
ignored by Git; only the non-secret [.env.example](.env.example) is committed.

## Local development

### Local database

Start the repository's MySQL 8 container:

```powershell
docker compose up -d mysql
docker compose ps
```

The Compose file creates the schema and application user configured in `.env`.
MySQL is published on `MYSQL_HOST_PORT` (3306 by default) so the host backend can
connect during development. Data lives in the named `mysql_data` volume.

## Environment variables

The defaults match `docker-compose.yml` and local ports. Override secrets and
deployment-specific values through environment variables or a secret manager.

| Variable | Local default | Purpose |
|---|---|---|
| `DB_HOST` | `localhost` | MySQL host |
| `DB_PORT` | `3306` | MySQL port |
| `DB_NAME` | `micro_approval_db` | Database/schema |
| `DB_USER` | value from `.env` | Database user |
| `DB_PASS` | value from `.env` | Database password |
| `JWT_SECRET` | local development fallback | JWT HMAC secret; mandatory override outside local development |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:3000,http://127.0.0.1:3000` | Comma-separated exact frontend origins; never use `*` for the authenticated deployment |
| `WORKSPACE_INVITATION_EXPIRATION_DAYS` | `7` | Invitation lifetime |
| `AI_ANALYSIS_ENABLED` | `true` | Enables the Rule → optional user-AI pipeline |
| `AI_CREDENTIAL_ENCRYPTION_KEY` | empty | Base64-encoded 32-byte AES key; required before users can save provider keys |
| `RULE_ENGINE_MAX_CARDS_PER_SESSION` | `10` | Deterministic card cap |
| `AI_ANALYSIS_MAX_CARDS_PER_SESSION` | `10` | Total analysis card cap |

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
When the Compose MySQL user differs from the application's local defaults, set
`DB_NAME`, `DB_USER` and `DB_PASS` in the backend terminal to the matching
values from `.env` before starting Spring Boot.

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

## Full Docker production baseline

With `.env` configured, build and start the complete stack from the repository
root:

```powershell
docker compose config
docker compose up --build -d
docker compose ps
```

Open `http://localhost:3000`, or the value selected with `FRONTEND_PORT`.
The browser sends `/gateway/*` requests to the frontend container. Nginx
rewrites them to `/api/*` and proxies them to `backend:8080` on the internal
Compose network. The backend is intentionally not published to the host; this
keeps browser traffic same-origin and avoids exposing an unnecessary port.
If `FRONTEND_PORT` changes, update `CORS_ALLOWED_ORIGINS` to the corresponding
exact `localhost`/`127.0.0.1` origins. Do not replace the list with `*`.

The container path is:

```text
Browser -> frontend (Nginx) -> backend (Spring Boot) -> mysql
```

The backend waits for the MySQL healthcheck, then Flyway migrates a fresh
database through V14 and Hibernate validates the resulting schema. No schema
SQL import or Hibernate `create`/`update` mode is used.

Useful operational commands:

```powershell
docker compose logs -f backend
docker compose logs -f frontend
docker compose logs -f mysql
docker compose restart backend
docker compose restart frontend
docker compose down
```

`docker compose restart` and `docker compose down` preserve `mysql_data`.
Running `docker compose down -v` permanently deletes the Compose database
volume; only use it for an explicitly disposable environment. To verify or test
without touching another local stack, choose a separate project name, ports and
volume namespace, for example `docker compose -p micro-approval-e2e ...`.

## Default ports

| Service | Port |
|---|---:|
| MySQL | 3306 |
| Spring Boot API | 8080 (host development; internal-only in full Docker) |
| Vite/Nginx frontend | 3000 |

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

The stabilization baseline is 109 backend tests and 136 frontend tests across
14 files, with type-check, lint, production build, Flyway V14 validation, and
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
- The MVP does not include notifications, realtime updates, GitHub integration,
  monitoring, backup/restore automation, CI/CD, or production deployment.
- Local integration tests use the configured MySQL instance; isolated CI should
  provide a disposable database (for example, a dedicated schema or
  Testcontainers) before parallel test execution.

## Production baseline limitations

This Compose stack is a **Docker production baseline**, not a complete
production deployment. It does not provide HTTPS termination, an external
secret manager, database backup/restore automation, centralized logging,
monitoring/alerting, orchestration, rolling deployment or autoscaling. Add those
capabilities in later production phases without placing backend secrets in the
frontend build.
