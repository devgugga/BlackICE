---
type: "query"
date: "2026-07-27T18:43:05.282329+00:00"
question: "Onde ficam as instruções operacionais do Graphify no BlackICE?"
contributor: "graphify"
outcome: "useful"
source_nodes: ["Graphify skill", "Complete incremental update", "Graphify Hooks"]
---

# Q: Onde ficam as instruções operacionais do Graphify no BlackICE?

## Answer

O workflow project-scoped fica em .agents/skills/graphify/SKILL.md para Codex/Agents e no espelho .claude/skills/graphify/SKILL.md para Claude. O runbook de reextração incremental e cluster-only fica em references/update.md, e a integração automática por hooks fica em references/hooks.md. A documentação de arquitetura do projeto registra instalação, rotas incrementais, limitações e workarounds do Graphify 0.9.28.

## Outcome

- Signal: useful

## Source Nodes

- Graphify skill
- Complete incremental update
- Graphify Hooks