# Micro Approval

Backend MVP for personal code-review sessions. Users submit code or a diff, the rule engine creates decision cards for known risks, and each card must be approved or rejected exactly once.

## Current scope

- Email/password registration and login with BCrypt + JWT.
- Private Personal Workspace sessions.
- Rule-based checks for SQL string interpolation, destructive SQL, hardcoded secrets, and dependency-related changes.
- Per-card decision history and automatic session completion.
- MySQL schema managed by Flyway.

Team Workspace, OpenAI analysis, GitHub webhooks, audit links, notifications, dashboards, and the frontend are planned but not implemented yet.

## Run locally

1. Start MySQL from the repository root:

   ```powershell
   docker compose up -d
   ```

2. Set a production-safe JWT secret before deployment. Local defaults are defined in `backend/src/main/resources/application.yaml`.

3. Start the API:

   ```powershell
   cd backend
   $env:JAVA_HOME = 'C:\Program Files\Java\jdk-25.0.2'
   .\mvnw.cmd spring-boot:run
   ```

The API starts at `http://localhost:8080`.

## API endpoints

| Method | Endpoint | Authentication | Purpose |
|---|---|---|---|
| POST | `/api/v1/auth/register` | No | Register and receive a JWT |
| POST | `/api/v1/auth/login` | No | Login and receive a JWT |
| POST | `/api/v1/personal/sessions` | Bearer JWT | Create and analyse a personal session |
| GET | `/api/v1/personal/sessions` | Bearer JWT | List the caller's sessions |
| GET | `/api/v1/personal/sessions/{sessionId}` | Bearer JWT | Get a session and its decision cards |
| PATCH | `/api/v1/personal/sessions/decisions/{decisionId}` | Bearer JWT | Approve or reject one card |
| DELETE | `/api/v1/personal/sessions/{sessionId}` | Bearer JWT | Delete the caller's personal session |

## Test

```powershell
cd backend
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-25.0.2'
.\mvnw.cmd test
```
