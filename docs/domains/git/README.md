# Domain Pack: Git ♻️

Fonte da verdade sobre a criação segura e padronizada de commits locais.
**Reutilizável:** este pack é independente de ferramenta e projeto; pode ser
copiado com seus wrappers para outros repositórios.

## Documento canônico

- [`commit-conventions.md`](./commit-conventions.md) — política de branch,
  seleção de escopo, formato da mensagem, Gitmoji, Graphify e limites de
  execução.

## Quem consome este pack

- Claude: `.claude/agents/git/commit-curator.md`
- Codex: `.codex/agents/commit-curator.toml`

Ambos são wrappers finos: leem e aplicam `commit-conventions.md`; regras de
commit não são duplicadas nos agentes.
