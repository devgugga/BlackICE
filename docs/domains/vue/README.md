# Domain Pack: Vue ♻️

Fonte da verdade sobre o **frontend Vue 3 + Vite** e o **viewer Cornerstone3D**.
**Reutilizável:** o viewer consome uma interface DICOMweb compatível e pode ser
adaptado a outros projetos de imagem médica. No BlackICE, ele usa o caminho WADO
estreito proxied pelo Quarkus; o browser não acessa o DCM4CHEE diretamente.

## Documentos

- [`conventions.md`](./conventions.md) — Vue 3 (Composition API + `<script setup>`),
  TypeScript, Vite, Pinia, estrutura de pastas e composables.
- [`cornerstone3d.md`](./cornerstone3d.md) — integração do Cornerstone3D em
  componentes Vue: rendering engine, viewports, image loaders (WADO-RS),
  ferramentas, e os gotchas de reatividade.

## Quem consome este pack

- Claude: `.claude/agents/vue/dicom-viewer-frontend.md`
- Codex: `.codex/agents/vue/dicom-viewer-frontend.toml`

Ambos são especialistas **implementadores** do viewer/UI.
