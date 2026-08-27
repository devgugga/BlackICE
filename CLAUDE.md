@AGENTS.md

# Claude Code Specific Notes

Project-wide behavioral rules are defined in `AGENTS.md` (imported above), and domain knowledge resides in `docs/domains/`. This document specifies Claude Code-specific bindings.

## Available Subagents (`.claude/agents/`)

| Subagent | Role | When to Use |
| :-- | :-- | :-- |
| `dicom-domain-reviewer` | Read-only validator for DICOM/DICOMweb semantics | Proactively, after writing code touching DICOM (ingestion, query, retrieve, tag mapping). Pre-filters domain issues before human gates. |
| `dicom-viewer-frontend` | Implementer specialist for Cornerstone3D + Vue 3 | When building or altering the medical viewer or imaging components. |
| `quarkus-backend` | Implementer specialist for Quarkus backend (**project-scoped**) | When building REST endpoints, DICOMweb clients, report entities, or OIDC configuration. |
| `commit-curator` | Normalizes local commits with Gitmoji | When concluding verified tasks on a development branch; on `main`, requires human confirmation. |

All subagents are **thin wrappers**: they load `docs/domains/<domain>/*.md` and apply those canonical rules. To change agent behavior, edit the domain docs, not the subagent wrappers.

## Skills (`.claude/skills/`)

Repeatable workflows for Claude Code. Convention: name by domain (`dicom-*`, `vue-*`, `quarkus-*`) and reference `docs/domains/<domain>/`.

## Portability with OpenAI Codex & Google Antigravity

Codex subagents live in `.codex/agents/*.toml` and Antigravity agents live in `.agents/agents/`. All point to the **same** `docs/domains/` source of truth. When updating domain knowledge, edit the docs once: all agents inherit the changes immediately.

## Graphify

This project maintains a knowledge graph at `graphify-out/` with god nodes, community structure, and cross-file relationships.

Guidance (optional):
- For codebase questions, consider `graphify query "<question>"` when `graphify-out/graph.json` exists. `graphify path "<A>" "<B>"` and `graphify explain "<concept>"` provide focused relationship and concept views.
- You may also use direct source browsing, targeted search, and project documentation.
- Review and synchronize `graphify-out/` according to `docs/architecture/graphify.md`.
