# Vue 3 — convenções de frontend

Stack: **Vue 3 + Vite + TypeScript**, Composition API com `<script setup>`, Pinia
para estado, Vue Router. SPA autenticado (sem SSR).

## Estilo de componente

- Sempre `<script setup lang="ts">`. Sem Options API.
- Lógica reutilizável de uma feature vai para **composables** colocalizados
  nessa feature, não para mixins nem para dentro de componentes gigantes.
- Componente faz uma coisa. Se um `.vue` passa de ~200–250 linhas ou mistura
  responsabilidades (fetch + render + estado global), quebre.
- Props tipadas via `defineProps<{...}>()`; eventos via `defineEmits<{...}>()`.

## Estrutura feature-first

```
src/
  app/
    App.vue
    router/
      index.ts
  features/
    <name>/
      # API, tipos, composables, componentes e testes do fluxo
  main.ts
```

- `app/` contém apenas o shell e a composição de rotas.
- API, tipos, stores, composables, componentes e testes de um fluxo ficam
  colocalizados em `features/<name>/`.
- Testes unitários ficam ao lado do arquivo testado.
- Imports internos usam o alias `@/`, configurado no TypeScript e no Vite.
- Código só é promovido a `shared/` depois de ter dois consumidores reais.

A árvore vigente e a receita para adicionar uma feature estão em
[`docs/architecture/project-structure.md`](../../architecture/project-structure.md).

## Estado

- **Pinia** para estado compartilhado quando a feature exigir; mantenha o store
  dentro da feature enquanto ele pertencer a um único fluxo.
- Estado local do componente com `ref`/`reactive`; nada de store global para o que
  é efêmero de um componente.
- Não guarde objetos grandes/externos (WebGL, RenderingEngine do Cornerstone) em
  estado **reativo** — ver `cornerstone3d.md` (gotcha de reatividade).

## Data fetching e auth

- O frontend usa a sessão BFF via cookie HttpOnly. Nenhum access token vive no
  JavaScript; chamadas usam mesma origem e `/api/me` é a fonte da sessão.
- Clients HTTP ficam na feature que os utiliza e enviam as credenciais de cookie
  quando necessário.
- QIDO-RS (busca/worklist) é paginado no servidor — passe `limit`/`offset`, não
  traga tudo. Ver `docs/domains/dicom/dicomweb.md`.

## Qualidade

- TypeScript estrito. Tipos começam colocalizados na feature e só migram para
  `shared/` com pelo menos dois consumidores reais.
- Componentes de imagem médica: cuidado com memória — sempre limpe recursos no
  `onUnmounted` (ver viewer).
