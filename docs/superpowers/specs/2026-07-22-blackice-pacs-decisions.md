# BlackICE PACS — Decisões de escopo (rascunho)

> Capturado durante brainstorming em 2026-07-22. Ainda NÃO é o design final — o
> design formal será escrito depois de definirmos a orquestração de agentes.

## Contexto
- **Objetivo:** projeto de portfólio / aprendizado. Foco em arquitetura limpa e
  algo funcional ponta-a-ponta. Boas práticas de LGPD/segurança entram como
  "notas de produção", não como requisito de implementação do MVP.

## Arquitetura (decisões fechadas)
- **Motor DICOM:** DCM4CHEE Archive **5.34.3** (storage, C-STORE, Q/R, DICOMweb).
  A baseline e as referências oficiais de implantação estão em
  [`docs/architecture/dcm4chee-archive.md`](../../architecture/dcm4chee-archive.md).
- **Backend Quarkus:** backend de PRODUTO com domínio próprio (pacientes/metadados,
  laudos, permissões) em banco próprio (PostgreSQL). Consome o DCM4CHEE apenas para
  armazenamento, consulta e recuperação via **DICOMweb** (STOW-RS, QIDO-RS, WADO-RS).
- **Frontend:** **Vue 3 + Vite** (SPA autenticado; SSR/Nuxt não agrega aqui).
- **Viewer:** **Cornerstone3D** integrado em componentes Vue próprios.
- **Auth:** **Keycloak** (OIDC/SSO) — Quarkus via quarkus-oidc; DCM4CHEE integra
  nativamente com Keycloak → SSO único na stack.

## MVP (4 fluxos ponta-a-ponta)
1. **Ingestão de estudos** — upload de .dcm no browser → Quarkus → STOW-RS.
2. **Lista de estudos + busca** — worklist paginada/filtrada via QIDO-RS.
3. **Viewer do estudo** — Cornerstone3D (séries/imagens, zoom, window/level, medições).
4. **Laudos + auth** — módulo de laudo (texto vinculado ao estudo, no banco do Quarkus).

## Pontos técnicos a resolver no design formal
- WADO-RS: browser acessa direto o DCM4CHEE ou passa pelo proxy do Quarkus?
- Propagação do token OIDC: Vue → Quarkus → DCM4CHEE.
- CORS entre Vue, Quarkus, DCM4CHEE e Keycloak.
- Modelagem do vínculo laudo ↔ StudyInstanceUID.
