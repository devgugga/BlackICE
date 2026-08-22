# Domain Pack: Quarkus ✗ (project-scoped)

Fonte da verdade sobre o **backend Quarkus** do BlackICE. **Não é reutilizável:**
é project-scoped porque descreve decisões específicas do BlackICE/Quarkus. Este
pack fica para trás quando o conhecimento DICOM/Vue for copiado para outro
projeto.

## Documentos

- [`conventions.md`](./conventions.md) — Quarkus como backend de produto,
  integração OIDC/Keycloak, client DICOMweb para o DCM4CHEE, modelagem de laudos.

## Quem consome este pack

- Claude: `.claude/agents/quarkus/quarkus-backend.md`
- Codex: `.codex/agents/quarkus/quarkus-backend.toml`

Especialista **implementador** do backend. As regras de **semântica DICOM** não
estão aqui — estão em `docs/domains/dicom/`, que este backend também deve respeitar.
