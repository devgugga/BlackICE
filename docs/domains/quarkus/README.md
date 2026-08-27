# Domain Pack: Quarkus ✗ (project-scoped)

Source of truth for the **Quarkus backend** in BlackICE. **Not reusable:** This pack is project-scoped because it captures specific BlackICE architectural and design decisions.

## Documents

- [`conventions.md`](./conventions.md): Quarkus as a product backend/BFF, Keycloak OIDC integration, DICOMweb client for DCM4CHEE, and clinical reports domain modeling.

## Consumers of this Pack

- Claude: `.claude/agents/quarkus/quarkus-backend.md`
- Codex: `.codex/agents/quarkus/quarkus-backend.toml`
- Antigravity: `.agents/agents/quarkus-backend/agent.md`

Backend **implementer specialists**. Core **DICOM semantic rules** live in `docs/domains/dicom/`, which this backend must strictly respect.
