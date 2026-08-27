# Domain Pack: Git ♻️

Source of truth for standardized, safe local Git commits.
**Reusable:** This pack is tool- and project-agnostic; it can be copied with its agent wrappers into other repositories.

## Canonical Document

- [`commit-conventions.md`](./commit-conventions.md): Branching policy, scope selection, Gitmoji conventions, Graphify commit workflow, and execution guardrails.

## Consumers of this Pack

- Claude: `.claude/agents/git/commit-curator.md`
- Codex: `.codex/agents/git/commit-curator.toml`
- Antigravity: `.agents/agents/commit-curator/agent.md`

All are thin wrappers: they load and apply `commit-conventions.md`. Commit conventions are never duplicated inside agent files.
