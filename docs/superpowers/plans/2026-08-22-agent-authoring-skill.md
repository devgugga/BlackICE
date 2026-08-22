# Agent Authoring Skill Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Criar um Domain Pack neutro e skills finas para criar ou alterar agentes nas três plataformas com seleção de modelo atual, compatível e econômica.

**Architecture:** `docs/domains/agent-authoring/` concentrará a política portável. `.claude/skills/agent-authoring/` e `.agents/skills/agent-authoring/` serão somente pontos de descoberta que encaminham para esse pack. A pesquisa de modelos ocorre a cada mudança; não existe catálogo fixo versionado.

**Tech Stack:** Markdown, YAML frontmatter, TOML e CLIs locais de Claude Code, Codex e Antigravity quando disponíveis.

**Spec:** `docs/superpowers/specs/2026-08-22-agent-authoring-skill-design.md`

## Global Constraints

- `docs/domains/agent-authoring/` é a fonte única da verdade; wrappers não duplicam suas regras.
- Pesquisar fontes oficiais atuais de Anthropic, OpenAI e Google antes de criar ou alterar um agente.
- Selecionar o modelo/tier e esforço de menor custo que sejam elegíveis para a tarefa e compatíveis com o runtime.
- Não alterar modelos de agentes existentes nem criar commits sem autorização humana explícita.
- Não criar subagente de autoria nesta primeira versão, nem scripts sem necessidade determinística comprovada.

---

### Task 1: Criar o Domain Pack canônico

**Files:**
- Create: `docs/domains/agent-authoring/README.md`
- Create: `docs/domains/agent-authoring/conventions.md`
- Create: `docs/domains/agent-authoring/platform-layouts.md`
- Create: `docs/domains/agent-authoring/model-selection.md`
- Create: `docs/domains/agent-authoring/validation.md`

**Interfaces:**
- Consumes: a spec aprovada em `docs/superpowers/specs/2026-08-22-agent-authoring-skill-design.md` e a convenção em `docs/domains/README.md`.
- Produces: regras canônicas que os dois `SKILL.md` referenciam por caminho absoluto no repositório.

- [ ] **Step 1: Executar a checagem estrutural em estado RED**

Run:

```bash
test -f docs/domains/agent-authoring/model-selection.md
```

Expected: falha com código diferente de zero, pois o pack ainda não existe.

- [ ] **Step 2: Escrever os cinco documentos focados**

Definir: escopo e limites; regra de wrapper fino; layouts e restrições de descoberta; pesquisa atual por fornecedor + roteamento pelo menor candidato elegível; e checklist de validação. Não listar modelos nem preços fixos.

- [ ] **Step 3: Verificar que a estrutura ficou GREEN**

Run:

```bash
for file in README.md conventions.md platform-layouts.md model-selection.md validation.md; do
  test -s "docs/domains/agent-authoring/$file"
done
```

Expected: código zero; cada documento canônico existe e não está vazio.

### Task 2: Criar wrappers de descoberta mínimos

**Files:**
- Create: `.claude/skills/agent-authoring/SKILL.md`
- Create: `.agents/skills/agent-authoring/SKILL.md`

**Interfaces:**
- Consumes: todos os documentos de `docs/domains/agent-authoring/` criados na Task 1.
- Produces: uma skill `agent-authoring` descobrível por Claude e uma compartilhada por Codex/Antigravity.

- [ ] **Step 1: Executar a checagem estrutural em estado RED**

Run:

```bash
test -f .claude/skills/agent-authoring/SKILL.md && test -f .agents/skills/agent-authoring/SKILL.md
```

Expected: falha com código diferente de zero, pois os wrappers ainda não existem.

- [ ] **Step 2: Escrever wrappers com frontmatter válido**

Usar o mesmo nome e uma descrição discriminante. Cada corpo deverá dizer para ler e aplicar o Domain Pack antes de fazer pesquisa ou editar agentes; não deve repetir o fluxo de seleção nem valores de modelo.

- [ ] **Step 3: Verificar frontmatter e ausência de cópia de política**

Run:

```bash
for file in .claude/skills/agent-authoring/SKILL.md .agents/skills/agent-authoring/SKILL.md; do
  sed -n '1,8p' "$file" | grep -qx -- '---'
  rg -q '^name: agent-authoring$' "$file"
  rg -q '^description: Use when ' "$file"
  ! rg -q 'gpt-|claude-|gemini-' "$file"
done
```

Expected: código zero; ambos possuem frontmatter e não carregam uma escolha estática de modelo.

### Task 3: Integrar a convenção à documentação do repositório

**Files:**
- Modify: `AGENTS.md`
- Modify: `docs/architecture/project-structure.md`
- Modify: `docs/domains/README.md`
- Modify: `.claude/skills/README.md`

**Interfaces:**
- Consumes: os caminhos reais da Task 1 e da Task 2.
- Produces: documentação de arquitetura que identifica o Domain Pack como fonte de verdade e os dois caminhos de descoberta.

- [ ] **Step 1: Atualizar a árvore e a receita de criação**

Acrescentar `agent-authoring/` ao conjunto de Domain Packs, descrever `.agents/skills/` como descoberta compartilhada de Codex/Antigravity e explicar que `.claude/skills/` é o wrapper Claude. Não reintroduzir `.agents` como fonte de verdade.

- [ ] **Step 2: Executar verificações de referências**

Run:

```bash
rg -q 'docs/domains/agent-authoring/' AGENTS.md docs/architecture/project-structure.md docs/domains/README.md .claude/skills/README.md
rg -q '\.agents/skills/' AGENTS.md docs/architecture/project-structure.md docs/domains/README.md
! rg -q 'fonte da verdade.*\.agents' AGENTS.md docs/architecture/project-structure.md docs/domains/README.md
```

Expected: código zero; a documentação aponta para o pack neutro, não para uma pasta de ferramenta.

### Task 4: Validar a skill e as modificações

**Files:**
- Verify: `docs/domains/agent-authoring/`
- Verify: `.claude/skills/agent-authoring/SKILL.md`
- Verify: `.agents/skills/agent-authoring/SKILL.md`
- Verify: documentação modificada na Task 3

**Interfaces:**
- Consumes: todos os artefatos das Tasks 1–3.
- Produces: evidência verificável de formato, referências e diff limpo.

- [ ] **Step 1: Validar ambos os wrappers com o validador oficial de skills**

Run:

```bash
SKILL_CREATOR=/home/v/.config/orca/codex-accounts/664a9978-50f2-43bf-aef0-cfb3e4c3af6f/home/skills/.system/skill-creator
python3 "$SKILL_CREATOR/scripts/quick_validate.py" .claude/skills/agent-authoring
python3 "$SKILL_CREATOR/scripts/quick_validate.py" .agents/skills/agent-authoring
```

Expected: ambos concluem sem erros de nome, frontmatter ou scaffolding pendente.

- [ ] **Step 2: Executar a auditoria de referências e de formatação**

Run:

```bash
rg -n 'docs/domains/agent-authoring/' .claude/skills/agent-authoring/SKILL.md .agents/skills/agent-authoring/SKILL.md
git diff --check
```

Expected: os dois wrappers referenciam apenas o pack e o diff não contém erro de espaço em branco.

- [ ] **Step 3: Verificar descoberta local sem pressupor uma ferramenta ausente**

Run somente os CLIs presentes:

```bash
command -v claude >/dev/null && claude --help >/dev/null || true
command -v codex >/dev/null && codex --help >/dev/null || true
command -v agy >/dev/null && agy --help >/dev/null || true
```

Expected: os comandos disponíveis respondem sem erro; registrar explicitamente qualquer CLI ausente, sem tratá-lo como falha da documentação.
