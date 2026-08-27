# Vue 3: Frontend Architecture & Conventions

Stack: **Vue 3 + Vite + TypeScript**, Composition API using `<script setup>`, Pinia for state management, and Vue Router. Authenticated client-side SPA (no SSR).

## Component Conventions

- Always use `<script setup lang="ts">`. Never use the legacy Options API.
- Reusable logic belongs in **composables** collocated within their respective feature folder.
- Single responsibility: If a `.vue` single file component exceeds ~200–250 lines or mixes concerns (fetching + complex layout + domain state), break it down.
- Strictly type props via `defineProps<{...}>()` and emit definitions via `defineEmits<{...}>()`.

## Feature-First Monorepo Structure

```
src/
  app/
    App.vue
    router/
      index.ts
  features/
    <name>/
      # API clients, types, composables, UI components, and unit tests
  main.ts
```

- `app/` strictly holds the application shell and route composition.
- API clients, types, stores, composables, components, and tests for a given clinical workflow are collocated inside `features/<name>/`.
- Unit tests (`*.spec.ts`) sit directly beside the tested file.
- Internal imports use the `@/` path alias configured in TypeScript and Vite.
- Code is promoted to `shared/` only when there are at least two distinct consumers.

The canonical directory layout and feature creation guide live in [`docs/architecture/project-structure.md`](../../architecture/project-structure.md).

## State Management

- **Pinia** for cross-component shared state when a feature requires it; keep stores inside the feature folder as long as they belong to that single workflow.
- Component-local state uses `ref` / `reactive`; avoid polluting global stores with ephemeral UI state.
- **Never wrap heavy non-reactive objects** (WebGL contexts, Cornerstone `RenderingEngine`, native handles) in Vue reactive proxies (`ref`/`reactive`) — see `cornerstone3d.md`.

## Data Fetching & BFF Authentication

- The frontend communicates via the same origin using secure `HttpOnly` session cookies. No OIDC access tokens are stored in JavaScript; `/api/me` is the single source of session truth.
- HTTP API clients live inside their respective feature folders and pass credentials automatically.
- QIDO-RS queries (search/worklist) must paginate on the server with `limit`/`offset`. Never fetch the entire dataset to slice client-side. See `docs/domains/dicom/dicomweb.md`.

## Quality & Memory Lifecycle

- Strict TypeScript mode enabled.
- Medical imaging viewports: prevent memory leaks by guaranteeing proper disposal of rendering engines, tool groups, and canvas contexts inside `onUnmounted` lifecycle hooks.
