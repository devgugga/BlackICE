# Agent & Skill Validation

Execute checks proportional to the agent's scope and record verified results. A configuration is not complete simply because its frontmatter passes syntax parsing.

## Verification Checklist

1. Verify that the agent definition specifies role, trigger description, consumers, tools, permissions, and operational constraints.
2. Inspect referenced Domain Packs and ensure the wrapper does not duplicate domain rules.
3. Conduct and present multi-provider model research following [`model-selection.md`](./model-selection.md), including local environment verification and cost justification for the lowest-cost eligible candidate.
4. Validate target platform formats: valid YAML frontmatter for Markdown, and valid TOML syntax for Codex configs.
5. Verify discovery paths against [`platform-layouts.md`](./platform-layouts.md).
6. Enforce least privilege for tools: read-only by default; file write, command execution, and network access only when strictly required.
7. Run `git diff --check`, update Graphify per `AGENTS.md`, and audit all versioned artifacts.

## Guardrails under Pressure

- A prompt requesting the "best model" must result in workload classification and cost-effective selection, not an automatic top-tier allocation.
- A prompt requesting to copy DICOM rules into a prompt must move the rule to `docs/domains/dicom/` and keep the agent wrapper thin.
- A prompt requesting an unverified or locally unavailable model identifier must document ineligibility and pick from verified candidates.
- Any change increasing permissions, operational cost, or modifying core agents must pause at a human review gate prior to commit.
