# Antigravity Agents Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Expor os quatro papéis especializados do BlackICE como custom agents
descobertos pelo Google Antigravity.

**Architecture:** Cada agente será um wrapper Markdown em uma pasta própria sob
`.agents/agents/`, com frontmatter YAML oficialmente suportado pelo Antigravity.
Os prompts referenciam os Domain Packs existentes, evitando duplicação de regras
de domínio. A documentação canônica registra o novo ponto de descoberta.

**Tech Stack:** Google Antigravity custom agents, YAML, Markdown.

**Spec:** `docs/superpowers/specs/2026-08-22-antigravity-agents-design.md`

## Global Constraints

- Use apenas `model: flash`; no ambiente atual, a camada Flash é Gemini 3.7
  Flash.
- Preserve `docs/domains/` como fonte única de verdade para regras de domínio.
- Use somente o caminho oficialmente descoberto
  `.agents/agents/<nome>/agent.md`.
- Não crie commits sem solicitação explícita do mantenedor.

---

### Task 1: Criar os wrappers Antigravity

**Files:**
- Create: `.agents/agents/dicom-domain-reviewer/agent.md`
- Create: `.agents/agents/quarkus-backend/agent.md`
- Create: `.agents/agents/dicom-viewer-frontend/agent.md`
- Create: `.agents/agents/commit-curator/agent.md`

**Interfaces:**
- Consumes: Domain Packs já existentes em `docs/domains/`.
- Produces: Quatro custom agents descobertos pelo Antigravity com os mesmos
  papéis dos wrappers de Claude e Codex.

- [x] **Step 1: Escrever a configuração que deve ser descoberta**

Crie um arquivo `agent.md` por papel, com `name`, `description`, `model: flash`,
`subagent: true`, `mainAgent: false`, `commandExecutionPolicy: sandbox` e as
ferramentas mínimas do papel. O revisor DICOM usa apenas leitura/pesquisa; os
demais podem criar/editar arquivos e executar comandos.

- [x] **Step 2: Validar a sintaxe YAML**

Run:

```bash
python3 - <<'PY'
from pathlib import Path
import yaml

for path in sorted(Path('.agents/agents').glob('*/agent.md')):
    text = path.read_text(encoding='utf-8')
    assert text.startswith('---\\n')
    frontmatter = text.split('---', 2)[1]
    data = yaml.safe_load(frontmatter)
    assert data['model'] == 'flash'
    assert data['subagent'] is True
    assert data['mainAgent'] is False
    print(f'valid: {path}')
PY
```

Expected: quatro arquivos válidos, todos com o modelo Flash e papel de
subagente.

### Task 2: Documentar o ponto de descoberta

**Files:**
- Modify: `AGENTS.md`
- Modify: `docs/architecture/project-structure.md`
- Modify: `docs/domains/README.md`

**Interfaces:**
- Consumes: Estrutura canônica e convenção de Domain Packs.
- Produces: Instruções portáveis e precisas para manter wrappers Antigravity.

- [x] **Step 1: Registrar o Antigravity nas instruções e estrutura canônicas**

Inclua `.agents/agents/` como ponto de descoberta específico do Antigravity em
`AGENTS.md` e `docs/architecture/project-structure.md`, mantendo
`.agents/skills/graphify/` descrito como tooling compartilhado.

- [x] **Step 2: Atualizar a receita de wrappers**

Adicione à receita de novo domínio o wrapper Antigravity em
`.agents/agents/<nome>/agent.md`, salientando que o caminho com uma pasta por
agente é o layout oficialmente suportado.

### Task 3: Confirmar descoberta pelo Antigravity

**Files:**
- Verify: `.agents/agents/*/agent.md`

**Interfaces:**
- Consumes: Configurações criadas e `agy` instalado localmente.
- Produces: Evidência de que os quatro agentes podem ser selecionados pelo
Antigravity.

- [x] **Step 1: Executar a descoberta do workspace**

Run: `agy agent`

Expected: o comando pode não listar os quatro wrappers, pois todos têm
`mainAgent: false`: são subagentes invocáveis, não agentes principais
selecionáveis. A descoberta usa o layout suportado
`.agents/agents/<nome>/agent.md`; a sintaxe e o schema são validados no passo
de YAML.

- [x] **Step 2: Revisar o diff final**

Run: `git diff --check && git diff -- .agents/agents docs/architecture/project-structure.md docs/domains/README.md`

Expected: nenhum erro de whitespace e somente os wrappers e a documentação
planejados no diff.
