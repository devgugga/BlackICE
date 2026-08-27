# BlackICE Backend

<p align="center">
  Language / Idioma: <b>🇺🇸 English</b> | <a href="README.pt-BR.md">🇧🇷 Português</a>
</p>

Product domain backend and Backend-For-Frontend (BFF) for BlackICE, built with Quarkus and Java 21. It hosts the product business domain, integrates OIDC authentication with Keycloak, and communicates with DCM4CHEE exclusively over DICOMweb protocols.

## Toolchain

`mise.toml` pins Eclipse Temurin Java 21 and Maven 3:

```powershell
mise install
mise exec -- java -version
mise exec -- mvn -version
```

## Testing & Packaging

```powershell
mise exec -- mvn test
mise exec -- mvn package
```

## Local Development

Copy `.env.example` to `.env`, set the required cryptographic secrets, and export them into your shell environment before launching dev mode:

```powershell
mise exec -- mvn quarkus:dev
```

Key environment variables for OIDC, database, and DICOMweb connectivity:

- `QUARKUS_OIDC_SECRET`: Secret of the `blackice-quarkus` confidential client;
- `QUARKUS_OIDC_ENCRYPTION_SECRET`: 32+ character key used to encrypt the session state;
- `QUARKUS_OIDC_AUTH_SERVER_URL`: Optional issuer override for `application.properties`;
- `QUARKUS_CSRF_TOKEN_SIGNATURE_KEY`: 32+ character HMAC key used to sign CSRF tokens emitted by `/api/csrf`;
- `QUARKUS_DATASOURCE_JDBC_URL`: PostgreSQL JDBC URL for the product database (`product-db`), default `jdbc:postgresql://product-db:5432/${PRODUCT_DB}`;
- `QUARKUS_DATASOURCE_USERNAME`: Product database username;
- `QUARKUS_DATASOURCE_PASSWORD`: Product database password;
- `BLACKICE_DICOMWEB_BASE_URL`: Base DICOMweb REST URL of the DCM4CHEE Archive (default `http://arc:8080/dcm4chee-arc/aets/DCM4CHEE/rs`).

Product Database & Migrations:
- The product database (`product-db`, PostgreSQL 17) strictly stores business data (clinical reports);
- Flyway migrations execute automatically at application startup (`quarkus.flyway.migrate-at-start=true`), managing the `reports` table schema and constraints (`V1__create_reports.sql`);
- Hibernate ORM validates the relational schema on boot (`quarkus.hibernate-orm.schema-management.strategy=validate`).

Configurable Properties for Ingestion, Search, and Reports:
- `blackice.ingest.max-files` (default 500);
- `blackice.ingest.max-total-bytes` (default 524288000 = 500 MB);
- `blackice.ingest.max-concurrent-studies` (default 1);
- `blackice.dicomweb.request-timeout` (default 60S);
- `blackice.worklist.request-timeout` (default 10S);
- `blackice.reports.archive-request-timeout` (default 10S).

Key Dependencies across `ingest`, `worklist`, and `reports`:
- `org.dcm4che:dcm4che-core:5.34.3`: DICOM metadata parsing (bulk-data excluded);
- `io.quarkus:quarkus-rest-csrf`: HMAC-backed CSRF protection on mutating endpoints;
- `io.quarkus:quarkus-hibernate-orm-panache`: JPA / Hibernate Panache persistence;
- `io.quarkus:quarkus-jdbc-postgresql`: PostgreSQL JDBC driver;
- `io.quarkus:quarkus-flyway`: Version-controlled relational migrations.

REST API Contracts (BFF & Product Domain):

### 1. Session & CSRF Protection (`session` / `security`)
- `GET /api/me`: Returns active session state (`SessionResponse`: `authenticated`, `username`, `roles`, `exp`); returns `401 Unauthorized` with Problem Details if unauthenticated or expired (`@PermitAll`);
- `GET /api/login`: Entry point for OIDC standard authentication flow (`@Authenticated`), triggering a `302 Found` redirect to Keycloak Authorization Endpoint;
- `GET /api/csrf`: Emits an HMAC-signed CSRF token (`{"token":"<signature>"}`) required in `X-CSRF-Token` headers for mutating requests (`POST`/`PUT`).

### 2. DICOM Ingestion (`ingest`)
- `POST /api/studies`: Receives `multipart/form-data` uploads containing `.dcm` files;
- Pre-validates metadata integrity in memory using `dcm4che-core` without buffering bulk pixel data;
- Dispatches authenticated STOW-RS payloads to the Archive respecting `blackice.ingest.max-files` (500), `blackice.ingest.max-total-bytes` (500 MB), and concurrency locks.

### 3. Clinical Worklist & Search (`worklist`)
- `GET /api/studies`: Paginated study query over QIDO-RS (`@RolesAllowed("auth")`);
- Supports four combinable optional query parameters: `patientName`, `patientId`, `modality`, `dateFrom`/`dateTo`, plus `limit` (default 20) and `offset` (default 0);
- Returns curated metadata (`StudyPage`) containing `items` and pagination metadata (`limit`, `offset`, `hasPrevious`, `hasNext`).

### 4. Medical Viewport & WADO-RS Proxy (`viewer`)
- `GET /api/studies/{studyUid}`: Returns structured study metadata (series, modalities, instance counts);
- `GET /api/studies/{studyUid}/series/{seriesUid}/instances`: Lists ordered SOP Instance UIDs for a specified series;
- `GET /api/dicomweb/studies/{studyUid}/series/{seriesUid}/instances/{sopUid}/frames/1`: Secure WADO-RS frame proxy; consumes the Archive with internal bearer tokens and streams raw frame data to Cornerstone3D without exposing DCM4CHEE ports to the browser.

### 5. Clinical Diagnostic Reports (`reports`)
- `GET /api/studies/{studyUid}/report`: Retrieves the report attached to a study; returns `204 No Content` if none exists, or `200 OK` with the report payload and a strong opaque `ETag` header (`"<version>"`);
- `POST /api/studies/{studyUid}/report`: Creates an initial report (`DRAFT` or `FINAL`); pre-validates study existence in the Archive via QIDO-RS and returns `201 Created` with `ETag`;
- `PUT /api/studies/{studyUid}/report`: Updates a draft or finalizes the report with optimistic concurrency validation via `If-Match: "<etag>"`; returns `200 OK` with a new `ETag`, `412 Precondition Failed` on concurrent version conflicts, and `403 Forbidden` if the report is already finalized or authored by another user;
- Strict payload ceiling of 32,000 characters/code points enforced (`API_PAYLOAD_TOO_LARGE`).

Error Handling Contract (All `/api` Routes):
- Every JSON `4xx/5xx` error conforms to `application/problem+json` referencing an entry in `docs/contracts/problems/`; `type`, `code`, and `status` remain immutably aligned;
- Every `/api` response, including successes, carries the `X-Trace-ID` correlation header, mirrored as `traceId` inside problem JSON bodies;
- W3C `traceparent` is the sole canonical correlation input, propagated across internal DICOMweb calls;
- `title` and `detail` originate from the problem catalog in English for operators (user-facing localized messages reside in `apps/frontend/src/shared/api/problems/`);
- `GET /api/login` retains its deliberate OIDC redirect behavior and is exempt from problem JSON wrapping;
- Creating or deprecating error types must follow the `problem-catalog` skill and `.problem-catalog/` tooling.

Problem Catalog Verification:

```bash
cd .problem-catalog && mise exec -- pnpm check
```

Atomic Contract Verification from Repository Root:

```bash
cd .problem-catalog && mise exec -- pnpm check
cd ../apps/backend && mise exec -- mvn test -Dquarkus.http.test-port=8082
cd ../frontend && mise exec -- pnpm test && mise exec -- pnpm build
```

Operational & Compliance Guidelines:
- **Synthetic Data**: All tests and fixtures use purely synthetic DICOM data; real patient data is strictly prohibited in the repository;
- **Lock Contention Monitoring**: Concurrency between STOW-RS and QIDO-RS is verified on Archive PostgreSQL (`arc-db`) by querying blocking locks (`SELECT count(*) FROM pg_locks WHERE NOT granted;`), where all samples must return `0`;
- **Pagination Evolution**: Future cursor, snapshot, or dedicated read-projection strategies are governed by item [EVO-005](../../docs/architecture/evolution-backlog.md#evo-005) in the evolution backlog;
- **Reports Evolution**: Rich Markdown editor with autosave and audited logical report invalidation are governed respectively by items [EVO-011](../../docs/architecture/evolution-backlog.md#evo-011) and [EVO-012](../../docs/architecture/evolution-backlog.md#evo-012).

Never commit `.env` or plain secrets.

## Module Structure

Code is organized by business module under `src/main/java/dev/blackice/<module>/`; test packages mirror this structure in `src/test/java`. Modules start flat and establish internal boundaries only when concrete responsibilities emerge: `api` for HTTP/REST, `application`/`application.port` for use cases and contracts, and `infrastructure` for external adapters. Dependency flow is strictly `api -> application <- infrastructure`.

Read the [canonical project structure](../../docs/architecture/project-structure.md) before adding a module and the [Quarkus conventions](../../docs/domains/quarkus/conventions.md) before modifying OIDC or DICOMweb integrations.

The browser only receives the secure HttpOnly session cookie from the BFF. OIDC access tokens are never exposed to client JavaScript.
