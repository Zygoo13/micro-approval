# Micro Approval

Micro Approval is a code-review workflow that turns deterministic and AI-assisted findings into individual Decision Cards. The current implementation delivers the Personal Workspace; Team Workspace and integration modules remain planned.

## Current capabilities

- Email/password authentication with BCrypt and JWT.
- Private Personal Workspace with Raw Snippet, Intent Matching, and Git Diff inputs.
- Database-configured Rule Engine, evaluated before AI.
- Per-user AI configuration for OpenAI or Google Gemini. API keys are encrypted at rest with AES-256-GCM and are never returned to the browser.
- Decision Cards, one-time approve/reject, review notes, and personal session history.
- Flyway-managed MySQL schema.

See [PROJECT_STRUCTURE.md](PROJECT_STRUCTURE.md) for the architectural map, conventions, and module roadmap.

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

## Server secrets

Never commit production secrets. Configure these values through your deployment secret store:

- `JWT_SECRET`
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
