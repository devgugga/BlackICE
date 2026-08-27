# BlackICE Frontend

<p align="center">
  Language / Idioma: <b>🇺🇸 English</b> | <a href="README.pt-BR.md">🇧🇷 Português</a>
</p>

BlackICE Single Page Application (SPA) built with Vue 3, Vite, and TypeScript.

## Toolchain

`mise.toml` pins Node 24 and pnpm:

```powershell
mise install
mise exec -- node --version
mise exec -- pnpm --version
mise exec -- pnpm install --frozen-lockfile
```

## Testing & Building

```powershell
mise exec -- pnpm test
mise exec -- pnpm build
mise exec -- pnpm test:e2e:keycloak
mise exec -- pnpm test:e2e:ingest
mise exec -- pnpm test:e2e:worklist
mise exec -- pnpm test:e2e:viewer
mise exec -- pnpm test:e2e:reports
mise exec -- pnpm exec playwright test e2e/problem-details.spec.ts
```

The Playwright end-to-end (E2E) test suite validates complete clinical workflows using in-memory generated synthetic DICOM fixtures, ensuring that no real patient information is ever handled or committed.

### E2E Test Viewport Matrix:
- **Desktop 1920×1080 (`chromium-desktop`)**: Split layout with Cornerstone viewport (width >= 720px) and side-by-side clinical report editor;
- **Laptop 1366×768 (`chromium-1366`)**: Drawer overlay closed by default, opening and closing smoothly without triggering viewport canvas distortion;
- **Tablet 1024×768 (`chromium-1024`)**: Drawer overlay responsive layout matching 1366×768 behavior;
- **Mobile 390×844 (`chromium-mobile`)**: Report-only layout with active Capability Gate (0 DICOM instance or frame network requests).

### Test User Personas:
- `dr.teste` / `teste123`: Primary radiologist / authoring persona;
- `dr.leitor` / `teste123`: Second clinical actor for testing read-only locks, concurrency conflicts, and 403 Forbidden enforcement on unauthorized mutations.

## Frontend Capabilities & Views

### 1. DICOM Ingestion (`/ingest`)
Manual upload interface for direct STOW-RS transmission:
- **Finite State Machine Lifecycle**:
  - `SELECTING`: Multi-file drag-and-drop or file picker selection;
  - `READY`: Local client-side DICOM validation (essential headers) before transmission;
  - `UPLOADING`: Determinate progress bar with real-time percentage and cancellation support;
  - `PROCESSING`: Network upload completed, awaiting Archive persistence and ingestion response;
  - `COMPLETE` / `ERROR` / `CANCELLED`: Consolidated summary grouped by study with reset actions.

### 2. Clinical Worklist & Search (`/studies`)
Paginated DICOM study discovery via the BFF (`GET /api/studies`):
- **Combinable Filters**: Patient Name (`SILVA^JOAO`), Patient ID with Issuer (`ID^^^ISSUER`), Modality (`CT`, `MR`, `CR`, `OT`), and Date Range (YYYY-MM-DD);
- **Count-Free Pagination**: 20-item pages with lookahead detection requesting 21 records to determine `hasNext` without slow database count queries;
- **Responsive Views**: Full tabular data layout on desktop (`study-table`) and compact summary cards on small screens (`study-cards`).

### 3. Cornerstone3D Medical Viewport (`/viewer/:studyUid`)
Interactive medical imaging viewport integrated with Cornerstone3D 5.x:
- **Rendering & WADO-RS Proxy**: Consumes the secure `/api/dicomweb/.../frames/1` endpoint with authenticated streaming;
- **Diagnostic Tooling**: Contrast/Brightness adjustments (*Window/Level*), *Zoom*, *Pan*, *Reset*, and series navigator;
- **Capability Gate**: On mobile screens (< 768px), disables the heavy WebGL/Cornerstone pipeline to prioritize report review and metadata inspection with bandwidth and CPU savings;
- **Viewport Canvas Isolation**: Side drawer interactions preserve exact WebGL canvas dimensions without geometric mutation.

### 4. Clinical Diagnostic Reports (`/studies/:studyUid/report` & Side Drawer)
Clinical diagnostic reporting module with relational persistence and concurrency guarantees:
- **Reactive Editor**: Text / Markdown editor with live character counter and strict 32,000 character limit;
- **Document Lifecycle**: Smooth state transitions between editable drafts (`DRAFT`) and finalized clinical records (`FINAL`);
- **Optimistic Concurrency (`ETag` / `If-Match`)**: Every mutation validates the document version; on concurrent conflicts, the UI catches `412 Precondition Failed` and offers safe reload without data loss;
- **Accessible Finalization Modal**: Focus-managed modal dialogue with keyboard trap, Escape dismissal, and explicit confirmation;
- **Multi-Actor Isolation**: Visual authorship indicators (`dr.teste`) with disabled controls and 403 mutation guards for secondary actors (`dr.leitor`);
- **Draft Preservation**: Local client-side preservation safeguarding drafts against accidental tab navigation.

## Error Handling & RFC 9457 Problem Catalog

`src/shared/api/problems/` is the single centralized error boundary for the SPA:

- `parse-problem.ts` maps any HTTP `4xx/5xx` response into a catalog-typed `ApiError`, or a local `CLIENT_*` failure if the response violates the contract. Both `fetch` and XHR share this parsing core;
- `problem-messages.pt-BR.ts` contains user-friendly localized messages. The mapping is exhaustive by design: adding a new problem code breaks the TypeScript build until a localized text is provided;
- `*.generated.ts` are automatically produced by `.problem-catalog/` tooling and are never manually modified;
- Backend operator details (`detail`) are strictly hidden from end-users; the `TraceID` is presented as a copyable support reference on failures;
- Retry actions are only presented when `retryPolicy === 'MANUAL'`;
- Operator-initiated cancellations are treated as natural flow control, never as errors.

## Local Development

```powershell
mise exec -- pnpm dev
```

## E2E Testing with Local Stack

E2E tests consume an existing running BlackICE stack; they do not spin up or tear down services automatically.

Canonical Compose files reside in `infra/`: `compose.yml` (shared foundation), `dcm4chee/compose.yml` (Archive and dependencies), and `compose.apps.yml` (BlackICE applications). From the repository root, start the stack and wait for the frontend, backend, and OIDC endpoints to become healthy:

```powershell
Set-Location infra
docker compose -f compose.yml -f dcm4chee/compose.yml -f compose.apps.yml up -d --build

$isReady = $false
foreach ($attempt in 1..30) {
  $rootStatus = & curl.exe -sS --max-redirs 0 -o NUL -w '%{http_code}' http://blackice.localhost/
  $meStatus = & curl.exe -sS --max-redirs 0 -o NUL -w '%{http_code}' http://blackice.localhost/api/me
  $loginHeaders = (& curl.exe -sS --max-redirs 0 -D - -o NUL http://blackice.localhost/api/login) -join "`n"
  $loginStatus = [regex]::Match($loginHeaders, '(?im)^HTTP/\S+\s+(\d{3})\b').Groups[1].Value
  $location = [regex]::Match($loginHeaders, '(?im)^location:\s*(\S+)').Groups[1].Value

  if (
    $rootStatus -eq '200' -and
    $meStatus -eq '401' -and
    $loginStatus -eq '302' -and
    $location -match '^http://blackice\.localhost/auth/realms/blackice/protocol/openid-connect/auth\?'
  ) {
    $isReady = $true
    break
  }

  Start-Sleep -Seconds 2
}

if (-not $isReady) {
  throw 'Stack is not ready: expected / = 200, /api/me = 401, and /api/login = 302 to Keycloak.'
}
```

Then, from `apps/frontend`, install dependencies and run E2E suites inside the pinned Playwright Linux Docker container:

### Keycloak Login Theme E2E
```powershell
Set-Location ../apps/frontend
mise exec -- pnpm install --frozen-lockfile
docker run --rm --network host `
  -v "${PWD}:/work" `
  -w /work `
  -e CI=true `
  -e BLACKICE_E2E_URL=http://blackice.localhost `
  mcr.microsoft.com/playwright:v1.62.0-noble `
  npx playwright test e2e/keycloak-login.spec.ts
```

### Worklist & Ingestion E2E
```powershell
Set-Location ../apps/frontend
mise exec -- pnpm install --frozen-lockfile
docker run --rm --network host `
  -v "${PWD}:/work" `
  -w /work `
  -e CI=true `
  -e BLACKICE_E2E_URL=http://blackice.localhost `
  mcr.microsoft.com/playwright:v1.62.0-noble `
  npx playwright test e2e/worklist.spec.ts
```

### Archive Concurrent Lock Contention Verification
During concurrent STOW-RS ingestion and QIDO-RS search runs, verify the absence of blocking locks in Archive PostgreSQL by running from `infra/`:

```bash
docker compose -f compose.yml -f dcm4chee/compose.yml -f compose.apps.yml exec -T arc-db sh -lc \
  'for sample in $(seq 1 20); do psql -At -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1 -c "SELECT count(*) FROM pg_locks WHERE NOT granted;"; sleep 0.5; done'
```

**Interpretation**: All 20 output lines must return `0`. Normal granted locks (`AccessShareLock`) are expected; any non-zero count (`> 0`) indicates blocking lock contention.

### CI Contract

The CI job must:

1. In `infra/`, launch the stack with `docker compose -f compose.yml -f dcm4chee/compose.yml -f compose.apps.yml up -d --build`;
2. Wait without following redirects for `/ = 200`, `/api/me = 401` without session, and `/api/login = 302` pointing to Keycloak;
3. In `apps/frontend/`, run `pnpm install --frozen-lockfile`;
4. Execute E2E tests with `CI=true` inside `mcr.microsoft.com/playwright:v1.62.0-noble`;
5. Publish `apps/frontend/playwright-report/` and test artifacts upon failure;
6. In job cleanup, run `docker compose ... down`.

## Architecture & Code Organization

The application shell lives in `src/app/`. Each clinical capability is modularized under `src/features/<name>/`, colocation of API endpoints, types, components, composables, and tests. Internal paths use the `@/` alias, and routes are declared in `src/app/router/index.ts`.

Refer to the [canonical project structure](../../docs/architecture/project-structure.md) before adding a feature and [Vue conventions](../../docs/domains/vue/conventions.md) before touching the viewer or session flows.

## BFF Session

The frontend communicates with the backend on the same origin. `/api/me` provides session details and `/api/login` initiates login when unauthenticated. The session is managed via a secure `HttpOnly` cookie; no access tokens are exposed to JavaScript.
