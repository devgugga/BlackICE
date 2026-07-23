# Codex Subagents Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Tornar a configuração de subagentes do Codex explícita e manter os agentes como wrappers finos dos Domain Packs.

**Architecture:** `.codex/config.toml` define os padrões e o limite de concorrência do projeto. Cada arquivo em `.codex/agents/` define o papel, as referências canônicas e as fronteiras do agente; regras de domínio permanecem em `docs/domains/`.

**Tech Stack:** Codex project configuration (TOML) e Markdown.

## Global Constraints

- Os agentes ficam diretamente em `.codex/agents/`, pois descoberta recursiva de subdiretórios não é documentada.
- Sem duplicar regras DICOM, Vue ou Quarkus que já vivem em `docs/domains/`.
- O revisor DICOM permanece `read-only` e usa raciocínio `high`.

---

### Task 1: Configurar política de subagentes

**Files:**
- Create: `.codex/config.toml`

- [x] **Step 1: Criar os padrões do projeto**

```toml
[agents]
max_concurrent_threads_per_session = 3
default_subagent_model = "gpt-5.6-terra"
default_subagent_reasoning_effort = "medium"
```

- [x] **Step 2: Validar a estrutura TOML declarada**

Run: `node -e "..."`
Expected: exit code 0.

### Task 2: Simplificar e fixar os agentes especializados

**Files:**
- Modify: `.codex/agents/dicom-domain-reviewer.toml`
- Modify: `.codex/agents/dicom-viewer-frontend.toml`
- Modify: `.codex/agents/quarkus-backend.toml`

- [x] **Step 1: Fixar o modelo e esforço adequados por papel**

```toml
model = "gpt-5.6-sol"
model_reasoning_effort = "medium"
```

- [x] **Step 2: Substituir regras duplicadas por referências aos Domain Packs**

Cada `developer_instructions` preserva papel e limites, e instrui a leitura dos documentos canônicos relevantes antes do trabalho.

- [x] **Step 3: Validar todos os TOMLs**

Run: `node -e "..."`
Expected: exit code 0.
