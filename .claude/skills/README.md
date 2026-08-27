# BlackICE Skills

Home for **repeatable workflows** in Claude Code. We create skills when real workflows repeat (YAGNI).

> This `README.md` is documentation only; Claude Code discovers skills under `.claude/skills/<name>/SKILL.md`.

## Conventions

- One skill per directory: `.claude/skills/<domain>-<verb>/SKILL.md`.
- Mirror across platforms: when applicable to OpenAI Codex and Google Antigravity, provide an equivalent thin wrapper under `.agents/skills/<domain>-<verb>/SKILL.md`.
- **Name by domain:** `dicom-*`, `vue-*`, `quarkus-*`.
- **Keep skills thin:** Step-by-step procedures live in the skill, but **canonical business and domain rules** live in `docs/domains/<domain>/`.

## Existing Skills

- `agent-authoring`: Creates or modifies AI agents and model selections following `docs/domains/agent-authoring/`.

## Future Candidates

- `dicom-scaffold-endpoint`: Scaffolds a DICOMweb endpoint (QIDO/WADO/STOW) in Quarkus following `docs/domains/dicom/dicomweb.md` and `docs/domains/quarkus/conventions.md`.
- `vue-add-viewer-tool`: Adds an interactive tool to the Cornerstone3D medical viewport following `docs/domains/vue/cornerstone3d.md`.
- `dicom-check-study`: Sanity-checks a study's DICOM hierarchy.
