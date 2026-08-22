# Domain Pack: DICOM ♻️

Fonte da verdade sobre **semântica DICOM e DICOMweb** para o BlackICE.
**Reutilizável:** este pack é agnóstico de stack (não menciona Quarkus/Vue) e deve
ser copiado inteiro para outros projetos de imagem médica (ex.: o PACS em Django).

## Documentos

- [`semantics.md`](./semantics.md) — modelo de dados, UIDs, hierarquia, tags,
  invariantes que **não podem** ser violadas (correção de negócio).
- [`dicomweb.md`](./dicomweb.md) — os três verbos REST (STOW/QIDO/WADO), quando
  usar cada um, formatos de request/response, autenticação.

## Quem consome este pack

- Claude: `.claude/agents/dicom/dicom-domain-reviewer.md`
- Codex: `.codex/agents/dicom/dicom-domain-reviewer.toml`

Ambos são revisores **read-only** de correção de domínio. Eles não implementam;
apontam violações destas regras antes do gate humano.
