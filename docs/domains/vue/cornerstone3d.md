# Cornerstone3D dentro do Vue

Como integrar o **Cornerstone3D** (`@cornerstonejs/core`, `@cornerstonejs/tools`,
`@cornerstonejs/dicom-image-loader`) em componentes Vue 3 sem furar performance nem
vazar memória.

## Peças do Cornerstone3D

- **`RenderingEngine`** — orquestra a renderização WebGL. Normalmente uma por app.
- **Viewport** — onde a imagem aparece, ligado a um elemento DOM (uma `<div>`).
  - `Stack` viewport: navega por uma pilha 2D de `imageId`s (o caso do MVP).
  - `Volume` viewport: reconstrução 3D/MPR (avançado; fora do MVP).
- **Image loaders** — resolvem `imageId`s. Para DICOMweb use o loader
  `wadors:` (WADO-RS). Ex.: `wadors:https://host/dicomweb/studies/{u}/series/{u}/instances/{u}/frames/1`.
- **Tools + ToolGroup** — WindowLevel, Zoom, Pan, StackScroll, Length (medição), etc.
  Ferramentas são registradas e associadas a um ToolGroup, que é ligado ao viewport.

## Fluxo de montagem (padrão)

1. `init()` do core e dos tools **uma vez** na inicialização do app.
2. Configure o `dicom-image-loader` com o **header de autenticação** (Bearer token
   OIDC) via `beforeSend`/config — senão o WADO-RS do DCM4CHEE retorna 401.
3. Monte `imageId`s a partir de **WADO-RS** (nunca de QIDO — QIDO é só metadado).
   A lista de instâncias/frames vem de uma query QIDO; os pixels vêm de WADO.
4. No componente: `onMounted` → pega a `<div>` via `ref`, cria/pega a
   `RenderingEngine`, habilita o viewport nesse elemento, seta a stack, associa o
   ToolGroup, `render()`.
5. `onUnmounted` → **destrua** o viewport/rendering engine e remova listeners.
   Falhar aqui vaza contexto WebGL e trava o navegador depois de alguns estudos.

## Gotcha crítico: reatividade do Vue × objetos Cornerstone

O sistema de reatividade do Vue 3 embrulha objetos em `Proxy`. **Nunca** coloque
`RenderingEngine`, viewports, volumes ou ToolGroups em `ref`/`reactive`/`data`
reativo — o proxy quebra a identidade dos objetos WebGL e degrada performance.

- Guarde essas instâncias com **`shallowRef`** ou marque com **`markRaw`**.
- O que pode ser reativo: IDs (strings), índice do frame atual, presets de
  window/level, estado da UI (ferramenta ativa). Os **objetos** do Cornerstone, não.

## Ferramentas mínimas do MVP

WindowLevel (janela/nível), Zoom, Pan, StackScroll (navegar séries/frames) e Length
(medição básica). Um ToolGroup por viewport; troque a ferramenta ativa mudando o
binding do mouse, não recriando o viewport.

## Checklist de revisão

- [ ] `imageId` construído a partir de WADO-RS (não QIDO)?
- [ ] Token OIDC injetado no loader (`beforeSend`)?
- [ ] Objetos Cornerstone fora da reatividade (`shallowRef`/`markRaw`)?
- [ ] `onUnmounted` destrói rendering engine/viewport e limpa listeners?
- [ ] `init()` do core/tools chamado uma vez, não por componente?
