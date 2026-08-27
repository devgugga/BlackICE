# Model Selection & Routing

## Core Principle

For every created or modified agent, verify the current model availability across Anthropic, OpenAI, and Google before selecting. In each targeted platform, select the **lowest-cost eligible model or tier** capable of executing the task. Newer or larger models are not, on their own, justification for higher token costs.

Do not maintain a static pricing or model leaderboard in this document, as model capabilities and pricing change rapidly.

## Mandatory Research Protocol

In the same session proposing the change, consult the official documentation for each provider:

| Provider | Documentation Focus | Local Environment Verification |
| :-- | :-- | :-- |
| Anthropic | Subagent configurations, supported models, and costs | Account plan allowances and CLI alias support |
| OpenAI | Codex subagent configuration, model identifiers, and pricing | Codex CLI version and supported model/reasoning tiers |
| Google | Antigravity custom agents and skills documentation | `agy models` and frontmatter tier mappings (`flash_lite`, `flash`, `inherit`, `pro`) |

An option is ineligible if unavailable in the active environment, deprecated, or rejected by that platform's schema validator.

## Workload Classification

1. Identify tools, permissions, blast radius, and domain judgment requirements.
2. Classify the workload:
   - **Simple / Mechanical:** Repetitive transformations, documentation formatting, structured lookups.
   - **Routine:** Standard feature implementation, isolated bugfixes with explicit test coverage.
   - **Complex / High Risk:** Multi-step refactors, architectural changes, security, or domain-critical semantics.
   - **Specialized:** Deep multi-factor reasoning (always subject to human gates when touching clinical data).
3. Select the lowest-cost candidate capable of fulfilling the task with minimal required tool permissions.

Critical tasks never bypass human review gates; scaling up model tiers does not replace domain validation.

## Escalation Governance

Never alter an existing agent's model tier without explicit human authorization. Only propose escalation with concrete evidence (reproducible test failure, tool limitation, or context deficiency) and disclose cost implications beforehand.
