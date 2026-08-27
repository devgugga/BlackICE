# Platform Layouts & Discovery

Discovery paths and configuration formats are defined by official platform contracts, not arbitrary repository conventions.

## Claude Code

- Versioned subagents: `.claude/agents/<domain>/<name>.md`.
- Versioned skills: `.claude/skills/<name>/SKILL.md`.
- Subagents use Markdown with YAML frontmatter. Restrict tools and permissions to the specific role; model identifiers must conform to aliases or IDs accepted by the installed runtime.

Reference: [Claude Code subagents](https://code.claude.com/docs/en/sub-agents).

## OpenAI Codex

- Versioned subagents: `.codex/agents/<domain>/<name>.toml`.
- TOML files can be grouped recursively by domain under `.codex/agents/`; preserve unique `name` keys and validate TOML syntax.
- Shared skills: `.agents/skills/<name>/SKILL.md`.

Verify local client capabilities before setting `model` or `model_reasoning_effort`.

Reference: [Codex subagents](https://learn.chatgpt.com/docs/agent-configuration/subagents).

## Google Antigravity

- Versioned custom agents: `.agents/agents/<name>/agent.md`.
- Versioned skills: `.agents/skills/<name>/SKILL.md`.
- Agents use Markdown with YAML frontmatter. Use only supported tiers (`flash_lite`, `flash`, `inherit`, `pro`), tools, and policies supported by the active Antigravity CLI.

References: [Antigravity skills](https://antigravity.google/docs/skills) and [Custom agents](https://antigravity.google/blog/introducing-custom-agents).
