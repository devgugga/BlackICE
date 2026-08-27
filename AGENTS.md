# BlackICE: AGENTS.md

> **Canonical, tool-agnostic source of truth** for all AI agents working in this repository (Claude Code, OpenAI Codex, Google Antigravity, GLM, etc.). `CLAUDE.md` imports this file. Keep core behavioral rules here; tool-specific configurations simply link to this file and `docs/domains/`.

## What is BlackICE

A modern, high-fidelity Healthcare PACS (*Picture Archiving and Communication System*) platform.

- **DICOM Core Engine:** DCM4CHEE Archive 5.x handles heavy DICOM workflows (storage, C-STORE, DIMSE query/retrieve, and DICOMweb). **We do not re-implement this core engine.**
- **Backend:** Quarkus 3 (Java 21) serves as the **product backend & BFF**, maintaining business domain models (patient metadata, clinical reports, optimistic concurrency, and permissions) in a dedicated PostgreSQL database. It communicates with DCM4CHEE **strictly over DICOMweb** (STOW-RS, QIDO-RS, WADO-RS).
- **Frontend:** Vue 3 + Vite + TypeScript (authenticated SPA) with medical viewport rendering powered by Cornerstone3D.
- **Auth:** Keycloak (OIDC/SSO): Quarkus integrates via `quarkus-oidc`; DCM4CHEE integrates natively with Keycloak in a shared-audience realm.

MVP (4 end-to-end flows): (1) ingestion via STOW-RS, (2) study worklist & search via QIDO-RS, (3) medical study viewer with Cornerstone3D, (4) clinical reports with ETag concurrency + OIDC authentication.

## Repository Structure

`docs/architecture/project-structure.md` is the canonical operational guide. Review it before creating or relocating code.

- `apps/backend/`: Quarkus API & BFF.
- `apps/frontend/`: Vue 3 + Vite SPA.
- `infra/`: Local container orchestration and compose topology.
- `docs/`: Architecture documentation, Domain Packs, and design records.

Do not introduce new application root directories or global layered directories (such as `controller/`, `service/`, or `repository/`).

## Agent Architecture: "Domain Packs"

Domain knowledge lives **once**, in neutral Markdown, and is reused by all AI agents and tools. See `docs/domains/README.md`.

```
docs/domains/<domain>/    ← Portable knowledge base (Single Source of Truth)
.claude/agents/<domain>/  ← Claude Code subagents (thin wrappers referencing docs)
.codex/agents/<domain>/   ← OpenAI Codex subagents (thin wrappers referencing docs)
.agents/agents/<name>/    ← Antigravity subagents (thin wrappers referencing docs)
.claude/skills/           ← Claude skills (repeatable workflows by domain)
.agents/skills/           ← Codex / Antigravity skills (thin wrappers referencing docs)
```

**Golden Rule:** An agent or skill never duplicates domain rules. Its instructions state "apply `docs/domains/<x>/*.md`". If a rule changes, edit the markdown doc once, and all agent wrappers inherit it immediately.

Current domains: `dicom/` (reusable), `vue/` (reusable), `quarkus/` (project-scoped), `git/` (reusable), `agent-authoring/` (reusable), and `problem-catalog/` (project-scoped).

## RFC 9457 Problem Catalog

Every JSON `4xx/5xx` HTTP error under `/api` is a cataloged Problem Details type (RFC 9457). The policy lives in `docs/domains/problem-catalog/`; the registry and generated artifacts live in `docs/contracts/problems/`; and the tooling lives in `.problem-catalog/`. Use the `problem-catalog` skill before adding, reusing, or deprecating a problem type.

Never invent UUIDs manually, never edit generated files directly, and never alter immutable fields.

## Graphify

Review `docs/architecture/graphify.md` before installing, updating, or querying the knowledge graph. Use the project-scoped skill in `.agents/skills/graphify/` or `.claude/skills/graphify/`.

During feature development, do not update Graphify across every intermediate test/edit cycle. Once implementation, tests, and reviews are stable, run a single final update (`--update`), review the diff in `graphify-out/`, and commit.

## DICOM Invariants (Summary; details in `docs/domains/dicom/`)

These are **clinical correctness rules**. Violating them corrupts patient data:

1. **UIDs originate from the archive/acquisition (never invent them).** `StudyInstanceUID`, `SeriesInstanceUID`, and `SOPInstanceUID` represent identity. When creating new objects, generate valid DICOM UIDs with a registered root.
2. **Respect the DICOM hierarchy:** Patient → Study → Series → Instance. A series belongs to exactly one `Modality`. `StudyInstanceUID` is the primary stable key for a study.
3. **Respect DICOMweb verbs:** Query = QIDO-RS; Retrieve pixels = WADO-RS; Store = STOW-RS. Do not interchange them.
4. **`PatientID` is not globally unique** without its Issuer.
5. **Do not re-encode pixel data** without understanding the Transfer Syntax.

## Workflow (Roles & Human Gates)

The human engineer (repo owner) is the **orchestrator and business domain validator**. Agents implement; decisions involving DICOM semantics and clinical data integrity are **presented at checkpoints/gates for human validation**, never auto-decided.

Loop: Brainstorming → Phased Implementation Plan → Subagent Execution → Human Gate at phase conclusion. Specs reside in `docs/superpowers/specs/`.

## Evolution Backlog

Before planning a feature or refactor, consult `docs/architecture/evolution-backlog.md`. When an architectural improvement is deferred to protect MVP focus, create or update a cataloged entry (with ID, rationale, and objective resume trigger).

An entry in the backlog **does not authorize immediate implementation**. Agents must propose it to the human engineer for prioritization first.

## General Conventions

- Keep commits small, focused, and purposeful; do not commit without explicit human authorization.
- When touching DICOM/DICOMweb logic, review `docs/domains/dicom/` and consult the `dicom-domain-reviewer` before gates.
- Never store raw pixel data in the Quarkus product database; store references (UIDs) and business metadata (clinical reports).

## Graphify

This project maintains a knowledge graph at `graphify-out/` with god nodes, community structure, and cross-file relationships.

When the user types `/graphify`, use the installed graphify skill or instructions before doing anything else.

Guidance (optional):
- For codebase questions, consider `graphify query "<question>"` when `graphify-out/graph.json` exists. `graphify path "<A>" "<B>"` and `graphify explain "<concept>"` provide focused relationship and concept views.
- You may also use targeted search, file inspection, and project documentation.
- Review and synchronize `graphify-out/` according to `docs/architecture/graphify.md`.
