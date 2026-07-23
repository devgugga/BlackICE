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

Os três são **wrappers finos**: leem `docs/domains/<domínio>/*.md` e aplicam. Para
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
