# Agent Authoring Conventions

## Architecture Boundaries

- **Domain Pack:** Canonical, tool-agnostic domain knowledge and policies.
- **Skill:** Repeatable workflow running inside the main agent context; drives generation or modification when requested.
- **Subagent:** Isolated role with recurring responsibility, focused context, and minimal tool permissions. Never create a subagent simply to run a single skill.

Use a skill before creating a new subagent when the task requires interactive context, comparative options, or human gates. Create a subagent only when there is a distinct, recurring responsibility that benefits from isolated context and constrained permissions.

## Single Source of Truth

1. Create or update domain knowledge under `docs/domains/<domain>/` before authoring tool-specific wrappers.
2. Instruct the agent or skill prompt to read the canonical markdown documents; never duplicate domain rules, clinical checklists, or DICOM invariants inside prompts.
3. Keep inside the wrapper strictly tool-specific configurations: identifier, trigger description, model routing, reasoning effort, tools, and permissions.
4. When correcting a rule, edit the canonical markdown file once; all agents inherit the fix immediately.

## Scope and Authorization

Before creating or editing an agent, specify its role, consumers, required tools, and potential side effects. Modifying an agent does not authorize product code changes, external permissions, branch creation, or commits.

Request human confirmation before expanding permissions, increasing model tier costs, modifying existing core agents, or taking domain decisions subject to clinical review gates.

## Wrapper Anatomy

A concise description must specify **when** the agent triggers. The body instructs the agent on which canonical documents govern its work. Do not hardcode model pricing catalogs or commit conventions when they already belong to their respective Domain Packs.

Consult [`platform-layouts.md`](./platform-layouts.md) for valid paths and schemas, and [`model-selection.md`](./model-selection.md) for model routing.
