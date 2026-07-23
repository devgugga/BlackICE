# Vue 3 — convenções de frontend

Stack: **Vue 3 + Vite + TypeScript**, Composition API com `<script setup>`, Pinia
para estado, Vue Router. SPA autenticado (sem SSR).

## Estilo de componente

- Sempre `<script setup lang="ts">`. Sem Options API.
- Lógica reutilizável vai para **composables** (`src/composables/useXxx.ts`), não
  para mixins nem para dentro de componentes gigantes.
- Componente faz uma coisa. Se um `.vue` passa de ~200–250 linhas ou mistura
  responsabilidades (fetch + render + estado global), quebre.
- Props tipadas via `defineProps<{...}>()`; eventos via `defineEmits<{...}>()`.

## Estrutura de pastas (sugerida)

```
src/
  api/            # clients HTTP (ex.: wrapper do backend Quarkus / DICOMweb)
  components/     # componentes de UI reutilizáveis
  composables/    # useXxx — lógica de estado/efeitos
  features/       # módulos por fluxo: worklist/, viewer/, reports/, ingest/
  stores/         # Pinia stores
  router/
  types/          # tipos DICOM/domínio compartilhados
```

## Estado

- **Pinia** para estado compartilhado (usuário/sessão, worklist, estudo aberto).
- Estado local do componente com `ref`/`reactive`; nada de store global para o que
  é efêmero de um componente.
- Não guarde objetos grandes/externos (WebGL, RenderingEngine do Cornerstone) em
  estado **reativo** — ver `cornerstone3d.md` (gotcha de reatividade).

## Data fetching e auth

- Chamadas ao backend passam o **Bearer token** OIDC (Keycloak). Centralize a
  injeção do token no client de `api/`, não espalhe pelos componentes.
- QIDO-RS (busca/worklist) é paginado no servidor — passe `limit`/`offset`, não
  traga tudo. Ver `docs/domains/dicom/dicomweb.md`.

## Qualidade

- TypeScript estrito. Tipos de domínio DICOM em `types/`, reutilizados entre
  worklist, viewer e laudos.
- Componentes de imagem médica: cuidado com memória — sempre limpe recursos no
  `onUnmounted` (ver viewer).
