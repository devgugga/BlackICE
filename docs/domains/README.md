# Domain Packs: Agent Knowledge Architecture

This directory serves as the **core knowledge base** for the project. Each subdirectory is a self-contained "Domain Pack", representing the **single source of truth** for that domain. AI agents (Claude Code, OpenAI Codex, and Google Antigravity) and skills are **thin wrappers** that read these markdown files; they never duplicate domain logic.

## Why This Architecture

- **Multi-Agent Portability**: Claude Code subagents (`.claude/agents/*.md`), Codex subagents (`.codex/agents/*.toml`), and Antigravity agents (`.agents/agents/*/agent.md`) use **different configurations**. Neutral Markdown documents in this directory are 100% portable across all AI tools.
- **Cross-Project Reusability**: Packs marked with ♻️ are portable to other medical imaging projects. Copy the domain pack and its agent wrappers, and domain knowledge remains intact.
- **Maintainability**: A domain rule lives in exactly one place. Fix it once, and all AI agents inherit the change immediately.

## Domain Overview

| Pack | Reusable | Content |
| :-- | :-- | :-- |
| `dicom/` | ♻️ Yes | DICOM / DICOMweb semantics (UIDs, hierarchical models, STOW / QIDO / WADO). |
| `vue/` | ♻️ Yes | Vue 3 + Vite patterns; Cornerstone3D medical viewport integration. |
| `quarkus/` | ✗ Project-scoped | Quarkus BFF + Keycloak OIDC + DICOMweb client conventions. |
| `git/` | ♻️ Yes | Conventions for safe local commits with Gitmoji and scoped commit messages. |
| `agent-authoring/` | ♻️ Yes | Agent & skill authoring patterns, discovery layouts, and model routing. |
| `problem-catalog/` | ✗ Project-scoped | RFC 9457 Problem Details classification, registry governance, and tooling. |

## Adding a New Domain Pack

1. Create `docs/domains/<new-domain>/` with a `README.md` and domain markdown files.
2. Create the corresponding thin agent wrappers:
   - Claude subagent: `.claude/agents/<new-domain>/<name>.md` (instructing to read `docs/domains/<new-domain>/*.md`).
   - Codex subagent: `.codex/agents/<new-domain>/<name>.toml` (`developer_instructions` instructing to read the same docs).
   - Antigravity agent: `.agents/agents/<name>/agent.md` (YAML frontmatter instructing to read the same docs).
3. Register the subagent in `CLAUDE.md` and `AGENTS.md`.

## Adding a Skill

Skills are repeatable workflows executed in the main agent context. Create `.claude/skills/<domain>-<verb>/SKILL.md` and `.agents/skills/<domain>-<verb>/SKILL.md` referencing `docs/domains/<domain>/`. Keep the skill thin: workflow steps live in the skill, while **canonical business and domain rules** live in the Domain Pack.

## Golden Rule

> Agent wrappers point to the domain documents. Domain knowledge is never copied directly into an agent wrapper or skill prompt. If you find yourself writing business domain rules inside an agent configuration file, move them into the corresponding Domain Pack.
