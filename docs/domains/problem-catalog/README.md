# Domain Pack: Problem Catalog ✗

Source of truth for **classifying, reusing, creating, and deprecating** RFC 9457 Problem Details types in BlackICE.

**Project-scoped:** The methodology is portable, but this pack references the registry, tooling, and governance specific to this repository.

## Documents

- [`classification.md`](./classification.md): Decision tree between API, CLIENT, result, and cancellation failures, and when to reuse vs. create a new type.
- [`registry.md`](./registry.md): Code grammar, mandatory fields, deterministic UUIDv5 identity, lock file, deprecation, and human gates.
- [`security.md`](./security.md): Public client exposure rules, extension boundaries, and log masking requirements.

## What this Pack does NOT contain

The list of concrete problem types. That list lives exclusively in the registry:

- `docs/contracts/problems/catalog.json`: Machine-readable canonical source;
- `docs/contracts/problems/catalog.md`: Generated human-readable documentation;
- `docs/contracts/problems/catalog.lock.json`: Published, frozen types.

To inspect existing problems, view the catalog. To understand catalog governance, read this pack.

## Tooling

`.problem-catalog/` is an isolated Node ESM package with pinned Node and pnpm versions:

```bash
cd .problem-catalog
mise exec -- pnpm check       # Validates catalog and lock without writing
mise exec -- pnpm generate    # Regenerates lock, markdown, Java, and TypeScript artifacts
mise exec -- pnpm add ...     # Adds an approved problem entry
mise exec -- pnpm deprecate ...
```

## Consumers of this Pack

- Claude: `.claude/skills/problem-catalog/SKILL.md`
- Codex & Antigravity: `.agents/skills/problem-catalog/SKILL.md`

Both are thin wrappers that instruct agents to read and apply these documents.

## Golden Rule

> Neither human nor AI agent ever invents a UUID manually, edits a generated file, or alters an immutable field. Identity is derived deterministically by tooling; all changes require an approved specification.
