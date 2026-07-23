# Domain Pack: Vue ♻️

Fonte da verdade sobre o **frontend Vue 3 + Vite** e o **viewer Cornerstone3D**.
**Reutilizável:** o viewer é agnóstico ao backend (fala DICOMweb direto), então
transfere para outros projetos de imagem médica.

## Documentos

- [`conventions.md`](./conventions.md) — Vue 3 (Composition API + `<script setup>`),
  TypeScript, Vite, Pinia, estrutura de pastas e composables.
- [`cornerstone3d.md`](./cornerstone3d.md) — integração do Cornerstone3D em
  componentes Vue: rendering engine, viewports, image loaders (WADO-RS),
  ferramentas, e os gotchas de reatividade.

## Quem consome este pack

- Claude: `.claude/agents/vue/dicom-viewer-frontend.md`
- Codex: `.codex/agents/dicom-viewer-frontend.toml`

Ambos são especialistas **implementadores** do viewer/UI.
