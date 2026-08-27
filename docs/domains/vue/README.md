# Domain Pack: Vue ♻️

Source of truth for the **Vue 3 + Vite frontend** and the **Cornerstone3D medical viewer**.
**Reusable:** The viewer consumes a standard DICOMweb endpoint and can be adapted to other medical imaging applications. In BlackICE, it consumes the secure WADO-RS frame proxy exposed by Quarkus; the browser never connects to DCM4CHEE directly.

## Documents

- [`conventions.md`](./conventions.md): Vue 3 (Composition API + `<script setup>`), TypeScript, Vite, Pinia, project structure, and composables.
- [`cornerstone3d.md`](./cornerstone3d.md): Cornerstone3D integration inside Vue components: rendering engine lifecycle, viewports, WADO-RS image loaders, tools, and Vue reactivity gotchas.

## Consumers of this Pack

- Claude: `.claude/agents/vue/dicom-viewer-frontend.md`
- Codex: `.codex/agents/vue/dicom-viewer-frontend.toml`
- Antigravity: `.agents/agents/dicom-viewer-frontend/agent.md`

All are **specialized implementers** of the viewer and clinical user interface.
