# Domain Pack: Agent Authoring ♻️

This pack defines how to author, configure, and maintain AI agents and skills across Claude Code, OpenAI Codex, and Google Antigravity in a portable, token-efficient, and audited manner. **Reusable:** It contains no project-specific business rules and can be ported directly to other repositories adopting Domain Packs.

## Objective

Decouple tool-specific configurations from the core knowledge guiding agent behavior. The canonical knowledge base lives here; agents and skills are thin wrappers that consume and apply it.

## Canonical Documents

- [`conventions.md`](./conventions.md): Boundaries and distinction between skills, subagents, and Domain Packs.
- [`platform-layouts.md`](./platform-layouts.md): Valid discovery paths, schemas, and formats per AI platform.
- [`model-selection.md`](./model-selection.md): Up-to-date model research and cost-efficient model routing.
- [`validation.md`](./validation.md): Verification evidence required before concluding an agent change.

## Consumers of this Pack

- Claude: `.claude/skills/agent-authoring/SKILL.md`.
- Codex and Antigravity: `.agents/skills/agent-authoring/SKILL.md`.

These wrappers do not redefine rules: they point directly to this pack.
