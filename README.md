# Micro Approval

Backend MVP for personal code-review sessions. Users submit code or a diff, the rule engine creates decision cards for known risks, and each card must be approved or rejected exactly once.

## Current scope

- Email/password registration and login with BCrypt + JWT.
- Private Personal Workspace sessions.
- Rule-based checks for SQL string interpolation, destructive SQL, hardcoded secrets, and dependency-related changes.
- Per-card decision history and automatic session completion.
- MySQL schema managed by Flyway.

Team Workspace, OpenAI analysis, GitHub webhooks, audit links, notifications, dashboards, and the frontend are planned but not implemented yet.

## Configurable Rule Engine

System rules are seeded by Flyway migration `V2__seed_system_rule_patterns.sql` into `rule_patterns`. The engine loads active system rules (`team_id IS NULL`) from the database and evaluates them in ascending `priority`; rule cards are created before future AI cards.

- Configure the maximum number of cards with `RULE_ENGINE_MAX_CARDS_PER_SESSION` (default: `10`).
- Add a system rule with a new Flyway migration. Use a Java-compatible regular expression in `pattern`, a unique `priority`, risk category/level, and the Vietnamese question shown to the reviewer.
- The current four system rules are SQL interpolation, destructive SQL, hardcoded credentials, and dependency changes.

## Optional AI analysis

AI analysis is disabled by default. When enabled, deterministic rules run first and only the remaining content is sent to the configured provider. The provider must return JSON decision candidates; the backend validates the result, stores each accepted card as `AI_BASED`, and records returned token usage. If the provider or JSON parsing fails, Rule cards are still kept and the session returns `aiAnalysisStatus: FALLBACK` with a safe warning.

Choose one provider at deployment time:

```powershell
# OpenAI
$env:AI_ANALYSIS_ENABLED = 'true'
$env:AI_PROVIDER = 'openai'
$env:OPENAI_API_KEY = '...'
$env:OPENAI_MODEL = 'gpt-4.1-mini'

# Or Google Gemini
$env:AI_ANALYSIS_ENABLED = 'true'
$env:AI_PROVIDER = 'google-genai'
$env:GOOGLE_GENAI_API_KEY = '...'
$env:GOOGLE_GENAI_MODEL = 'gemini-2.5-flash'
```

`AI_ANALYSIS_MAX_CARDS_PER_SESSION` caps the total Rule and AI cards (default: `10`). Never expose either API key to the frontend.

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
## Personal AI configuration

Users can configure OpenAI or Google Gemini from **Thiết lập AI** after logging in. API keys are sent only to the backend and are stored with AES-256-GCM encryption; the key itself is never returned by the API.

Before enabling this screen in any environment, set a unique 32-byte Base64 master key on the server (keep it in a secret manager, never in Git):

```powershell
$env:AI_CREDENTIAL_ENCRYPTION_KEY = [Convert]::ToBase64String((1..32 | ForEach-Object { Get-Random -Maximum 256 }))
```

Set it persistently through your deployment secret store. Losing or rotating this value without a key-rotation migration makes previously stored API keys unreadable; disable and re-enter each user key instead.
