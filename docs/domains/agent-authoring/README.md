# Domain Pack: autoria de agentes ♻️

Este pack define como criar ou alterar agentes e skills de Claude Code, Codex e
Antigravity de forma portável, econômica e auditável. **Reutilizável:** não
contém regras clínicas nem de produto; pode acompanhar outros repositórios que
adotem Domain Packs.

## Objetivo

Separar a configuração específica de cada ferramenta do conhecimento que guia
as decisões. O conhecimento vive aqui; agentes e skills são wrappers finos que
o leem e o aplicam.

## Documentos canônicos

- [`conventions.md`](./conventions.md) — fronteiras entre skill, subagente e
  Domain Pack.
- [`platform-layouts.md`](./platform-layouts.md) — locais e formatos válidos
  por plataforma.
- [`model-selection.md`](./model-selection.md) — pesquisa atual e seleção pelo
  menor candidato elegível.
- [`validation.md`](./validation.md) — evidências antes de concluir uma
  alteração.

## Quem consome este pack

- Claude: `.claude/skills/agent-authoring/SKILL.md`.
- Codex e Antigravity: `.agents/skills/agent-authoring/SKILL.md`.

Esses wrappers não redefinem a política: apontam para este pack. Um subagente
de autoria não existe nesta primeira versão; só será considerado quando uma
auditoria isolada e recorrente trouxer benefício comprovado.
