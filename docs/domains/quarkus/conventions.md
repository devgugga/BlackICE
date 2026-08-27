# Quarkus: Backend Conventions & Architecture

Quarkus serves as the **product backend and Backend-For-Frontend (BFF)**: maintaining business domain entities (reports, business metadata, user permissions) on a dedicated PostgreSQL database, while communicating with DCM4CHEE **strictly via DICOMweb**. For DICOM semantics, consult `docs/domains/dicom/`.

## Modular Structure

The backend is structured by business capability under `dev.blackice.<module>`. A module starts flat and introduces subpackages only when distinct boundaries emerge: `api` for HTTP/REST resources, `application` for use cases, `application.port` for input/output contracts, and `infrastructure` for external adapters. When workflows contain separated concerns, `application` divides into `input`, `usecase`, `validation`, `result`, and `exception`. Dependency flow is strictly `api -> application <- infrastructure`. Application logic never imports REST resources or concrete infrastructure implementations; the HTTP boundary maps domain `result` models into HTTP response codes and payloads.

`domain/` is internal to a module and exists solely for pure business models, domain entities, and framework-independent invariants. Tests mirror package structure in `src/test/java`. Never create global layered packages such as `controller/`, `service/`, or `repository/`, nor premature `shared` packages without at least two distinct consumers.

The canonical operational guide lives in [`docs/architecture/project-structure.md`](../../architecture/project-structure.md).

## Java Documentation and Comments

All Javadoc, code comments, test names, and backend-generated messages must be written in English. Use Javadoc for public contracts, DICOM or security invariants, and non-obvious adapter behaviors. Comments explain architectural rationale or constraints that the code cannot self-express; avoid narrating self-evident statements.

## Extensions and Roles

- `quarkus-rest` (Jakarta REST): High-performance REST APIs for the Vue frontend.
- `quarkus-hibernate-orm-panache` + `quarkus-jdbc-postgresql`: Product domain persistence.
- `quarkus-oidc` in **web-app (BFF) mode**: Executes Authorization Code flow + PKCE, storing encrypted session state and tokens across secure `HttpOnly` cookies. With `split-tokens`, tokens are divided across browser cookies without requiring server-side session stores. Access tokens are never exposed to client-side JavaScript.
- `quarkus-rest-client` (MicroProfile REST Client): Strongly typed client for DCM4CHEE DICOMweb REST services (QIDO-RS, WADO-RS, STOW-RS).
- **Identity Propagation to DCM4CHEE:** In web-app mode, there is no inbound bearer token (only encrypted cookies). The session access token is unpacked server-side by Quarkus during request dispatch and attached as a Bearer token in DICOMweb calls. To satisfy DCM4CHEE token validation, Keycloak uses a **shared audience** (`blackice-quarkus` includes `dcm4chee-arc-rs` in its `aud` claim).

## Boundary of Responsibility (Critical)

- **Never** store raw pixel data in the Quarkus product database. The single source of truth for medical imaging objects is DCM4CHEE.
- The Quarkus database strictly stores **business records**: diagnostic reports, authors, timestamps, draft/final states, and references to studies anchored on **`StudyInstanceUID`** (stable DICOM primary key; see `docs/domains/dicom/semantics.md`). Never invent this UID; use the one returned by the archive.
- Study search/worklists act as an **aggregation proxy** over QIDO-RS; paginate on the server (`limit`/`offset`) rather than buffering records into memory.
- Ingestion forwards to **STOW-RS** and **inspects `FailedSOPSequence`** in the response before acknowledging success.

## Authentication and Security

- Endpoints protected with `@Authenticated` and Keycloak realm roles via `quarkus-oidc`.
- When invoking DCM4CHEE, **forward the authenticated user's identity** via token propagation; never use a static service account for actions representing human operators to preserve the DICOM audit trail.
- **CSRF Protection:** State-changing endpoints (`POST`, `PUT`, `DELETE`) enforce signed HMAC CSRF tokens (`X-CSRF-Token` header) alongside `SameSite` cookies.

## Report Domain Model

```
Report
  id                 ← UUID primary key
  studyInstanceUid   ← Study reference in DCM4CHEE (stable DICOM UID, not physical FK)
  authorId           ← User subject claim from OIDC token
  status             ← DRAFT | FINAL
  content            ← Diagnostic report text (max 32,000 characters)
  version            ← Version counter for ETag / If-Match optimistic concurrency
  createdAt / updatedAt
```

- Clinical reports reference studies **strictly by `StudyInstanceUID`**, ensuring integrity across archive re-synchronization.

## Review Checklist

- [ ] Is raw pixel data leaking into the Quarkus product database?
- [ ] Are clinical reports linked by `StudyInstanceUID` (not internal archive database IDs)?
- [ ] Is the authenticated user's OIDC token propagated to DCM4CHEE?
- [ ] Does STOW ingestion check `FailedSOPSequence` before reporting success?
- [ ] Are QIDO queries paginated server-side via `limit`/`offset`?
- [ ] Are mutating endpoints protected by CSRF verification?
