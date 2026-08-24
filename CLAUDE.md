@AGENTS.md

# Notas específicas do Claude Code

As regras do projeto estão em `AGENTS.md` (importado acima) e o conhecimento de
domínio em `docs/domains/`. Este arquivo cobre só o que é específico do Claude Code.

## Subagentes disponíveis (`.claude/agents/`)

| Subagente | Papel | Quando usar |
| :-- | :-- | :-- |
| `dicom-domain-reviewer` | Validador **read-only** de semântica DICOM/DICOMweb | Proativamente, após escrever qualquer código que toque DICOM (ingestão, query, retrieve, mapeamento de tags). Pré-filtra erros de domínio antes do gate humano. |
| `dicom-viewer-frontend` | Especialista implementador Cornerstone3D + Vue | Ao construir/alterar o viewer ou componentes que renderizam imagem médica. |
| `quarkus-backend` | Implementador do backend Quarkus (**project-scoped**, não transfere) | Ao construir endpoints, clients DICOMweb, entidades de laudo, config OIDC. |
| `commit-curator` | Normaliza commits locais com Gitmoji | Ao concluir tarefa verificada em branch de trabalho; em `main`, requer autorização humana. |

Todos são **wrappers finos**: leem `docs/domains/<domínio>/*.md` e aplicam. Para
mudar comportamento, edite o doc de domínio, não o subagente.

## Skills (`.claude/skills/`)

Home pronta para workflows repetíveis (ainda não criados). Convenção: nomeie por
domínio — `dicom-*`, `vue-*`, `quarkus-*` — e faça a skill referenciar
`docs/domains/<domínio>/`. Ex. futuros: `dicom-scaffold-endpoint`,
`vue-add-viewer-tool`. Ver `docs/domains/README.md` para o padrão de criação.

## Portabilidade com Codex

Os subagentes Codex vivem em `.codex/agents/*.toml` (formato próprio) e apontam
para os **mesmos** `docs/domains/`. Ao editar conhecimento, edite só os docs —
ambos os lados herdam. Ao criar um subagente novo, espelhe nos dois formatos.

## graphify

This project has a knowledge graph at graphify-out/ with god nodes, community structure, and cross-file relationships.

Guidance (optional):
- For codebase questions, consider `graphify query "<question>"` when graphify-out/graph.json exists. `graphify path "<A>" "<B>"` and `graphify explain "<concept>"` can provide focused relationship and concept views.
- You may instead use other appropriate approaches, including direct source browsing, targeted search, and project documentation.
- If graphify-out/wiki/index.md exists, consider it for broad navigation; consider GRAPH_REPORT.md for broad architecture review or when focused graph queries are insufficient.
- Follow the canonical update timing and commit protocol in `AGENTS.md` and `docs/architecture/graphify.md`.
- A post-commit hook intentionally changes tracked `graphify-out/` artifacts after code commits; review and commit that graph synchronization separately. Follow the canonical protocol in `docs/architecture/graphify.md#fluxo-de-commits-com-o-hook`; do not disable the hook just to keep the worktree clean.
