---
type: "query"
date: "2026-08-24T15:13:33.537205+00:00"
question: "Pode seguir com o brainstorming do MVP #3 viewer"
contributor: "graphify"
outcome: "useful"
source_nodes: ["WADO-RS Viewer", "Cornerstone3D Viewer", "WorklistPage.vue", "QIDO-RS", "WADO-RS"]
---

# Q: Pode seguir com o brainstorming do MVP #3 viewer

## Answer

Expanded from original query via graph vocab: [viewer, cornerstone, wado, qido, study, series, instance, proxy, origin, stack, worklist, dicomweb]. O contexto atual fixa o MVP #3 como viewer Cornerstone3D autenticado: a Worklist fornece StudyInstanceUID; o backend expõe REST curado para metadados via QIDO-RS e um proxy WADO-RS estreito, same-origin e com streaming; o browser não recebe token nem acessa o DCM4CHEE diretamente; o frontend usa viewport Stack e libera recursos no unmount. Ainda faltam decisões de escopo sobre tipos de estudo, séries, instâncias, multi-frame e ferramentas.

## Outcome

- Signal: useful

## Source Nodes

- WADO-RS Viewer
- Cornerstone3D Viewer
- WorklistPage.vue
- QIDO-RS
- WADO-RS