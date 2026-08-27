# BlackICE Repository & Architecture Structure

This is the canonical operational guide for the repository topology. Human engineers and AI agents must review it before creating, moving, or refactoring code.

## Monorepo Layout

```text
BlackICE/
├─ apps/
│  ├─ backend/
│  └─ frontend/
├─ infra/
│  ├─ compose.yml
│  ├─ compose.apps.yml
│  ├─ dcm4chee/
│  │  └─ compose.yml
│  ├─ keycloak/
│  └─ traefik/
├─ docs/
│  ├─ architecture/
│  ├─ contracts/
│  ├─ domains/
│  └─ superpowers/
├─ .problem-catalog/
├─ .github/
│  └─ workflows/
├─ .claude/
├─ .codex/
├─ .agents/
│  ├─ agents/
│  └─ skills/
│     ├─ agent-authoring/
│     └─ graphify/
├─ .graphify/
├─ .graphifyignore
├─ graphify-out/
├─ AGENTS.md
├─ CLAUDE.md
├─ README.md
└─ .gitignore
```

- `apps/`: Houses product applications. `apps/backend/` is the Quarkus BFF/API; `apps/frontend/` is the Vue 3 SPA.
- `infra/`: Holds local container orchestration, Keycloak realm definitions, and proxy configuration.
- `docs/`: Holds architecture guides, portable Domain Packs, and design specifications.
- `docs/architecture/evolution-backlog.md`: Catalog of deferred architecture improvements with objective resume triggers.
- `docs/contracts/`: Shared contracts between backend and frontend. `docs/contracts/problems/` is the Problem Details registry (machine-readable catalog, JSON Schema, lock file, and human-readable documentation).
- `.problem-catalog/`: Isolated Node tooling validating the problem registry and generating Java/TypeScript artifacts.
- `.claude/`, `.codex/`, and `.agents/agents/`: Discovery paths for Claude Code, OpenAI Codex, and Google Antigravity. Agent wrappers point to the single source of truth in `docs/domains/`.

Do not introduce new application root directories. Product code belongs strictly inside `apps/`.

## Modular Architecture Overview

The Quarkus backend is modularized around business capabilities (`ingest`, `reports`, `security`, `session`, `viewer`, `worklist`):

```text
apps/backend/src/
├─ main/
│  ├─ java/dev/blackice/
│  │  ├─ ingest/
│  │  ├─ reports/
│  │  ├─ security/
│  │  ├─ session/
│  │  ├─ shared/
│  │  ├─ viewer/
│  │  └─ worklist/
│  └─ resources/
│     ├─ application.properties
│     └─ db/migration/
│        └─ V1__create_reports.sql
└─ test/java/dev/blackice/
   ├─ architecture/
   ├─ ingest/
   ├─ reports/
   ├─ security/
   ├─ session/
   ├─ viewer/
   └─ worklist/
```

The Vue 3 frontend features are organized feature-first under `src/features/` with an `app/` composition shell:

```text
apps/frontend/src/
├─ app/
│  ├─ App.vue
│  └─ router/
├─ shared/
│  └─ api/problems/
├─ features/
│  ├─ home/
│  ├─ ingest/
│  ├─ reports/
│  ├─ session/
│  ├─ viewer/
│  └─ worklist/
└─ main.ts
```

## Dependency Rules

### Quarkus

- Each business module lives under `dev.blackice.<module>`.
- Layered subpackages: `api` for HTTP/REST; `application` for use cases; `application.port` for ports; `infrastructure` for external adapters.
- Dependency flow is strictly `api -> application <- infrastructure`. Application logic never imports HTTP or concrete infrastructure implementations; one module never imports another module's internal infrastructure.
- Tests mirror production packages under `src/test/java`; package boundary rules are verified by ArchUnit tests in `dev.blackice.architecture`.
- Never create global layered packages (`controller/`, `service/`, `repository/`, `dto/`, `validator/`, `exception/`).
- `dev.blackice.shared` is justified strictly when there are at least two distinct consumers (e.g. `api.problem` and `infrastructure.telemetry`).

### Vue 3

- `app/` is strictly the composition shell (root App component, router configuration).
- Components, API clients, types, composables, and tests for a workflow are collocated inside `features/<name>/`.
- Page routes are registered in `apps/frontend/src/app/router/index.ts`.
- Unit tests sit directly beside the tested file (`*.spec.ts`).
- Internal imports use the `@/` path alias.

### Cross-System Boundaries

- The frontend depends on the backend strictly through published HTTP API contracts.
- The backend communicates with DCM4CHEE strictly via DICOMweb REST services (QIDO-RS, WADO-RS, STOW-RS) and never stores raw pixel data.
- Domain rules live in [Domain Packs](../domains/README.md).

## Promotion to `shared/`

Code is promoted to `shared/` only when there are at least two active consumers. Premature creation of generic `shared/utils/` packages is an anti-pattern.

## Anti-Patterns to Avoid

- Empty modules or features created in anticipation of future work;
- Generic `utils/` or `helpers/` dump directories;
- Global layered trees (`controller/`, `service/`, `repository/`);
- Feature-exclusive code placed prematurely in `shared/`;
- Copying domain logic from Domain Packs into agent prompts or READMEs.
