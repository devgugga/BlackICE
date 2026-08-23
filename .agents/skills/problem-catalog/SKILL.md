---
name: problem-catalog
description: Use when an HTTP error contract, a browser failure or a Problem Details type is added, reused, deprecated or questioned in BlackICE.
---

# Problem catalog

Before proposing or editing anything, read and apply, in full:

- `docs/domains/problem-catalog/README.md`
- `docs/domains/problem-catalog/classification.md`
- `docs/domains/problem-catalog/registry.md`
- `docs/domains/problem-catalog/security.md`

These documents are authoritative. This wrapper carries no rule, no code and no
list of types of its own.

Then, in order:

1. Classify the occurrence with the decision tree in `classification.md`.
2. Read the published catalog in `docs/contracts/problems/` — never from memory.
3. Show the reusable entries to the human before proposing a new one.
4. Confirm the approved spec authorizes what you are about to do.
5. Run the official tooling in `.problem-catalog/`; never hand-edit
   `catalog.json` or any generated file.
6. Generate, then validate, then show the diff.
7. Stop and report: codes, URNs, files changed, commands run and their real
   output.

Do not treat this skill as authorization to create commits, change product code,
alter an immutable field, deprecate an entry, or add a type the spec does not
enumerate. Each of those needs its own human gate.
