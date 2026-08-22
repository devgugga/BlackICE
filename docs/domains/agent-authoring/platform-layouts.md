# Layouts por plataforma

Verifique a documentação oficial e o runtime instalado no momento da mudança.
Formatos e locais de descoberta são contrato da ferramenta, não convenção
inventada pelo projeto.

## Claude Code

- Subagente versionado: `.claude/agents/<dominio>/<nome>.md`.
- Skill versionada: `.claude/skills/<nome>/SKILL.md`.
- Subagentes usam Markdown com frontmatter YAML. Restrinja ferramentas e
  permissões ao papel; a configuração de modelo deve obedecer aos aliases ou
  IDs aceitos pelo runtime atual.

Referência: [subagentes do Claude Code](https://code.claude.com/docs/en/sub-agents).

## Codex

- Subagente versionado: `.codex/agents/<dominio>/<nome>.toml`.
- Neste repositório, os arquivos TOML sob `.codex/agents/` podem ser agrupados
  recursivamente por domínio; preserve `name` único e valide o TOML.
- Skill compartilhada: `.agents/skills/<nome>/SKILL.md`.

Verifique a versão local e a configuração efetiva antes de definir `model` ou
`model_reasoning_effort`: a disponibilidade depende do cliente, da conta e de
eventuais allowlists.

Referência: [subagentes do Codex](https://learn.chatgpt.com/docs/agent-configuration/subagents).

## Antigravity

- Subagente versionado: `.agents/agents/<nome>/agent.md`.
- Skill versionada: `.agents/skills/<nome>/SKILL.md`.
- Agentes usam Markdown com frontmatter YAML. Use somente os tiers, ferramentas
  e políticas aceitos pela versão atual; confira `agy models` antes de concluir
  uma escolha de modelo.

Não introduza agrupamentos adicionais abaixo de `.agents/agents/` sem confirmar
na documentação atual que serão descobertos pelo runtime.

Referências: [skills do Antigravity](https://antigravity.google/docs/skills) e
[custom agents](https://antigravity.google/blog/introducing-custom-agents).
