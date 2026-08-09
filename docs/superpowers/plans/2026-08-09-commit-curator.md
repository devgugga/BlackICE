# Commit Curator Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Criar agentes Claude Code e Codex que produzam commits locais em português, com gitmoji correto e política segura por branch.

**Architecture:** Centralizar as regras em `docs/domains/git/commit-conventions.md`. Criar wrappers finos nos formatos nativos de Claude Code e Codex, e registrar o wrapper Claude em `CLAUDE.md`; nenhum wrapper replica as regras do domínio.

**Tech Stack:** Git, Markdown, YAML frontmatter, TOML e Gitmoji.

## Global Constraints

- Em branches de trabalho, fazer commit local automático depois de uma tarefa verificada; nunca fazer push.
- Em `main`, exigir autorização humana explícita, salvo autorização anterior inequívoca no contexto ativo.
- Não incluir qualquer trailer `Co-authored-by` ou atribuição de coautoria.
- Usar português; o título é `gitmoji verbo resultado: contexto` e o corpo contém somente seções aplicáveis.
- Escolher o gitmoji pelo significado publicado em https://gitmoji.dev/.
- Codex usa `gpt-5.6-terra` com esforço `low`; Claude Code usa `haiku`.
- Antes de um commit que altere arquivos versionados, atualizar e revisar `graphify-out/` conforme `AGENTS.md`.

---

### Task 1: Domain Pack e wrappers de commit

**Files:**
- Create: `docs/domains/git/README.md`
- Create: `docs/domains/git/commit-conventions.md`
- Create: `.codex/agents/commit-curator.toml`
- Create: `.claude/agents/git/commit-curator.md`
- Modify: `docs/domains/README.md`
- Modify: `CLAUDE.md`
- Include in the focused commit: `docs/superpowers/specs/2026-08-09-commit-curator-design.md`
- Include in the focused commit: `docs/superpowers/plans/2026-08-09-commit-curator.md`

**Interfaces:**
- Consumes: `AGENTS.md`, `docs/domains/README.md`, `docs/architecture/graphify.md` e o estado/diff Git da tarefa.
- Produces: O agente `commit-curator`, com comportamento equivalente em Claude Code e Codex.

- [ ] **Step 1: Criar o Domain Pack neutro**

Escrever `docs/domains/git/README.md` declarando que o pack é reutilizável e que
`commit-conventions.md` é sua fonte de verdade. Escrever
`commit-conventions.md` com a política de branch, seleção segura do escopo,
formato de título/corpo, consulta a Gitmoji, proibição absoluta de coautoria,
proibição de push e pré-requisito Graphify.

- [ ] **Step 2: Criar wrappers finos**

Criar o wrapper Codex com `name = "commit-curator"`,
`model = "gpt-5.6-terra"`, `model_reasoning_effort = "low"` e
`developer_instructions` que mandem ler o Domain Pack antes de agir. Criar o
wrapper Claude em `.claude/agents/git/commit-curator.md`, com frontmatter
`name: commit-curator`, `model: haiku` e ferramentas Git necessárias, cujo
corpo mande ler o mesmo Domain Pack antes de agir.

- [ ] **Step 3: Registrar os pontos de descoberta**

Adicionar `git/` à tabela de packs em `docs/domains/README.md` e
`commit-curator` à tabela de subagentes em `CLAUDE.md`, descrevendo que ele
normaliza commits locais com Gitmoji e que sua execução em `main` precisa de
autorização.

- [ ] **Step 4: Validar a estrutura e as invariantes**

Executar:

```powershell
Get-Content -Raw .codex/agents/commit-curator.toml | ConvertFrom-Toml | Out-Null
Select-String -Path docs/domains/git/commit-conventions.md, .codex/agents/commit-curator.toml, .claude/agents/git/commit-curator.md -Pattern 'Co-authored-by|Co-authored-by:'
Select-String -Path .codex/agents/commit-curator.toml, .claude/agents/git/commit-curator.md -Pattern 'docs/domains/git/commit-conventions.md'
git diff --check
```

Esperar parse TOML sem erro, ocorrências da proibição no documento canônico,
referências dos dois wrappers ao mesmo arquivo e `git diff --check` sem saída.

- [ ] **Step 5: Atualizar o Graphify antes de qualquer commit**

Executar `graphify . --update`, revisar o diff em `graphify-out/` e incluir
somente os artefatos portáveis que representem a nova relação entre Domain Pack
e wrappers. Não criar commit sem a política de branch e autorização aplicáveis.

- [ ] **Step 6: Criar o commit local da tarefa**

Como a execução ocorre em uma branch de trabalho, incluir os arquivos da tarefa,
a especificação, o plano e somente os artefatos portáveis do Graphify no commit
local. Não fazer push e não incluir trailers de coautoria.
