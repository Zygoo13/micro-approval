# Project structure

## Architectural decision

The repository currently uses a **light layer-first structure** in the backend and a small page-oriented React structure in the frontend. This is retained deliberately: the implemented Personal Workspace is still one bounded feature, and a wholesale package move would create a large import-only diff without changing business behaviour.

For future modules, use **feature-first boundaries inside the existing application**, while keeping truly cross-cutting code in shared locations. Do not introduce extra hexagonal/CQRS layers until a module has a real second adapter, complex workflow, or independent deployment need.

The canonical business specification is currently maintained at `C:\Users\AD\Desktop\prj new ban chinh\nghiep-vu-micro-approval-v3-final.md`. Before team collaboration or deployment, copy/version that document under `docs/` in a separate documentation-only change so it is available to every contributor.

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
│   │   └── service/         # Personal workflow, Rule Engine, AI integration
│   ├── src/main/resources/
│   │   ├── application.yaml # Runtime configuration only; no real secrets
│   │   └── db/migration/    # Immutable Flyway migrations
│   └── src/test/java/       # Tests mirroring the production package root
├── frontend/
│   ├── src/
│   │   ├── lib/             # HTTP client and presentation labels
│   │   ├── pages/           # Route-level screens for the current small UI
│   │   ├── App.tsx          # Route composition and authenticated layout
│   │   ├── types.ts         # API/UI contract types
│   │   └── styles.css       # Shared MVP styling
│   ├── vite.config.ts       # Development proxy only
│   └── package.json
├── docker-compose.yml       # Local MySQL only
├── README.md                # Setup and verification entry point
└── PROJECT_STRUCTURE.md     # This architectural convention
```

## Responsibilities and dependency rules

### Backend

- `controller` may depend on `dto` and `service`; it must not access repositories directly.
- `service` owns use cases, authorization decisions, transaction boundaries, and orchestration between Rule and AI engines.
- `repository` is the only persistence abstraction used by services. Keep query methods named for the business scope they enforce.
- `entity` contains persistence-backed business state; it must not depend on controllers or DTOs.
- `dto` is the HTTP contract. Do not return entities from controllers.
- `security`, `config`, and `exception` are cross-cutting and remain outside a business feature package.
- Every database change is a new forward-only Flyway migration. Do not edit an applied migration.

### Frontend

- `pages` contains route-level composition only. When a screen grows reusable controls, move those controls into its feature folder.
- `lib/api.ts` is the only frontend HTTP boundary; pages must not call `fetch` directly.
- `types.ts` mirrors public API contracts. Keep provider secrets out of types and browser storage.
- Keep Vite proxy configuration development-only; production must inject the API origin through deployment configuration when required.

## Rules for new modules

When a new module has an HTTP endpoint, a use case, and persistence, create a focused feature package rather than adding more generic top-level files. Example for Team Workspace:

```text
backend/src/main/java/com/microapproval/api/team/
├── TeamController.java
├── TeamService.java
├── dto/
├── model/           # Team-owned entities/value objects only
└── repository/
```

Keep shared entities only when they are genuinely shared. `ReviewSession` and `MicroDecision` are shared foundations for Personal and Team; Team-specific membership, assignment, and role policies belong in `team`.

For the frontend, add the corresponding boundary when a feature grows beyond one screen:

```text
frontend/src/features/team/
├── api.ts
├── types.ts
├── components/
└── pages/
```

Route registration remains in `App.tsx` until route count makes a dedicated `routes/` directory useful.

## Extension roadmap

| Module | Placement | Initial responsibility |
|---|---|---|
| Rule Engine | `rule/` when it gains team rules/admin APIs | Pattern lifecycle, priority, validation, and deterministic findings |
| AI Engine | `ai/` when providers/configuration expand | Provider adapters, encrypted credentials, structured-output validation, usage accounting |
| Team Workspace | `team/` | Teams, membership, roles, projects, assignment policy |
| Notification | `notification/` | Domain events and in-app delivery; do not couple it directly to controllers |
| Audit | `audit/` | Read-only projections, audit links, export policy, data redaction |
| Webhook | `webhook/` | Provider-specific signature verification and translation into application commands |

## Deliberately not introduced yet

- Separate deployable services or a multi-module Maven build.
- Generic repository/service base classes.
- CQRS/event sourcing.
- A frontend state-management library.

These add operational or conceptual cost without a current requirement. Revisit only when Team Workspace, asynchronous webhooks, or audit/export workloads make the existing boundaries insufficient.
