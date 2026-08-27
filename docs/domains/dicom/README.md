# Domain Pack: DICOM ♻️

Source of truth for **DICOM and DICOMweb semantics** in BlackICE.
**Reusable:** This pack is stack-agnostic (does not mention Quarkus/Vue) and can be ported directly to other medical imaging systems.

## Documents

- [`semantics.md`](./semantics.md): Data model, UIDs, DICOM hierarchy, tags, and invariants that must never be violated (clinical business correctness).
- [`dicomweb.md`](./dicomweb.md): The three REST services (STOW-RS, QIDO-RS, WADO-RS), use cases, request/response structures, and authentication.

## Consumers of this Pack

- Claude: `.claude/agents/dicom/dicom-domain-reviewer.md`
- Codex: `.codex/agents/dicom/dicom-domain-reviewer.toml`
- Antigravity: `.agents/agents/dicom-domain-reviewer/agent.md`

All are **read-only domain reviewers**. They do not write code; they audit and catch domain semantic violations before human review gates.
