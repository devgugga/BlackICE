# Graph Report - BlackICE  (2026-08-09)

## Corpus Check
- 77 files · ~60,154 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 299 nodes · 253 edges · 72 communities (29 shown, 43 thin omitted)
- Extraction: 98% EXTRACTED · 2% INFERRED · 0% AMBIGUOUS · INFERRED: 5 edges (avg confidence: 0.92)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `cdd94cce`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- compilerOptions
- compilerOptions
- devDependencies
- package.json
- BlackICE Keycloak login theme
- SessionResource.java
- DICOMweb Verbs
- Community Label Quality Gate
- Community Label Quality Gate
- mvnw
- Quarkus Product Backend
- Incremental re-extraction runbook
- BFF Session Contract
- keycloak-login.spec.ts
- Complete incremental update
- SessionResourceTest.java
- DICOM Patient Study Series Instance Hierarchy
- Domain Packs
- Feature-First Architecture
- tsconfig.json
- DICOM Domain Reviewer
- DCM4CHEE 5.34.3 Baseline
- DCM4CHEE Archive 5.34.3
- DICOM Invariants
- playwright.config.ts
- BlackICE Favicon
- index.ts
- HomePage.vue
- session.types.ts
- AST-only update
- Domain Packs
- Canonical three-file Compose configuration
- Feature-first monorepo structure
- Project-scoped Graphify integration
- Focus outline accessibility
- Docker API compatibility proxy
- configure-blackice.sh
- BlackICE Architecture
- Graphify Add and Watch
- Graphify Exports
- Semantic Extraction
- Cross-repository Merge
- Graphify Hooks
- Graphify Query
- Graphify Transcription
- Vue Mount Point
- Bluesky icon
- Discord icon
- Documentation icon
- GitHub icon
- Social icon
- X icon
- Graphify Add and Watch
- Graphify Exports
- Semantic Extraction
- Cross-repository Merge
- Graphify Hooks
- Graphify Query
- Graphify Transcription
- BlackICE Skills
- Keycloak OIDC SSO
- Frontend SPA service
- Product PostgreSQL database
- Secure DCM4CHEE archive stack
- dev.blackice:blackice-backend
- BlackICE — Login same-origin: tirar o Keycloak da barra de endereços
- Keycloak same-origin — Implementation Plan

## God Nodes (most connected - your core abstractions)
1. `compilerOptions` - 15 edges
2. `compilerOptions` - 11 edges
3. `BlackICE — Login same-origin: tirar o Keycloak da barra de endereços` - 10 edges
4. `Fase 1 — Same-origin atrás do Traefik` - 8 edges
5. `SessionResource` - 6 edges
6. `scripts` - 6 edges
7. `Fase 2 — Renomear o realm para `blackice`` - 5 edges
8. `DICOMweb Verbs` - 5 edges
9. `Keycloak same-origin — Implementation Plan` - 4 edges
10. `include` - 4 edges

## Surprising Connections (you probably didn't know these)
- `Domain Packs Single Source of Truth` --semantically_similar_to--> `Domain Packs`  [INFERRED] [semantically similar]
  docs/domains/README.md → AGENTS.md
- `Vue BFF Session` --semantically_similar_to--> `BFF Session Contract`  [INFERRED] [semantically similar]
  docs/domains/vue/conventions.md → apps/frontend/README.md
- `Incremental re-extraction runbook` --semantically_similar_to--> `Incremental re-extraction runbook`  [INFERRED] [semantically similar]
  .agents/skills/graphify/references/update.md → .claude/skills/graphify/references/update.md
- `HTTP Contract Boundary` --conceptually_related_to--> `Quarkus Product Backend`  [EXTRACTED]
  docs/architecture/project-structure.md → AGENTS.md
- `HTTP Contract Boundary` --conceptually_related_to--> `Vue SPA`  [EXTRACTED]
  docs/architecture/project-structure.md → AGENTS.md

## Import Cycles
- None detected.

## Hyperedges (group relationships)
- **BlackICE Core Architecture** — agents_dcm4chee_archive, agents_quarkus_product_backend, agents_vue_spa, agents_keycloak_oidc [EXTRACTED 1.00]
- **Domain Pack Knowledge Model** — docs_domains_dicom_dicom_domain_pack, docs_domains_quarkus_quarkus_domain_pack, docs_domains_vue_vue_domain_pack [EXTRACTED 1.00]
- **DICOMweb Operation Roles** — docs_domains_dicom_dicomweb_qido_rs, docs_domains_dicom_dicomweb_wado_rs, docs_domains_dicom_dicomweb_stow_rs [EXTRACTED 1.00]
- **Keycloak login quality workflow** — docs_superpowers_plans_2026_07_26_keycloak_login_theme_blackice_login_theme, docs_superpowers_plans_2026_07_27_keycloak_input_focus_outline_focus_outline_accessibility, docs_superpowers_plans_2026_07_27_keycloak_theme_e2e_ui_regression_playwright_keycloak_theme_regression [INFERRED 0.85]
- **BlackICE MVP architecture** — docs_superpowers_specs_2026_07_22_blackice_pacs_decisions_dcm4chee_archive, docs_superpowers_specs_2026_07_22_blackice_pacs_decisions_quarkus_product_backend, docs_superpowers_specs_2026_07_22_blackice_pacs_decisions_vue_spa, docs_superpowers_specs_2026_07_22_blackice_pacs_decisions_cornerstone3d_viewer, docs_superpowers_specs_2026_07_22_blackice_pacs_decisions_keycloak_oidc_sso [EXTRACTED 1.00]
- **Canonical BlackICE Compose stack** — infra_readme_three_compose_files, infra_compose_traefik_routing, infra_compose_product_postgres_database, infra_dcm4chee_compose_keycloak_service, infra_dcm4chee_compose_archive_service, infra_compose_apps_frontend_spa_service [EXTRACTED 1.00]
- **Frontend icon sprite collection** — apps_frontend_public_icons_bluesky_icon, apps_frontend_public_icons_discord_icon, apps_frontend_public_icons_documentation_icon, apps_frontend_public_icons_github_icon, apps_frontend_public_icons_social_icon, apps_frontend_public_icons_x_icon [EXTRACTED 1.00]

## Communities (72 total, 43 thin omitted)

### Community 0 - "compilerOptions"
Cohesion: 0.10
Nodes (19): compilerOptions, allowImportingTsExtensions, erasableSyntaxOnly, lib, module, moduleDetection, noEmit, noFallthroughCasesInSwitch (+11 more)

### Community 1 - "compilerOptions"
Cohesion: 0.11
Nodes (18): compilerOptions, allowArbitraryExtensions, baseUrl, erasableSyntaxOnly, ignoreDeprecations, noFallthroughCasesInSwitch, noUnusedLocals, noUnusedParameters (+10 more)

### Community 2 - "devDependencies"
Cohesion: 0.12
Nodes (17): devDependencies, @playwright/test, @types/node, typescript, vite, @vitejs/plugin-vue, vitest, vue-tsc (+9 more)

### Community 3 - "package.json"
Cohesion: 0.12
Nodes (15): dependencies, vue, vue-router, name, private, scripts, build, dev (+7 more)

### Community 4 - "BlackICE Keycloak login theme"
Cohesion: 0.14
Nodes (14): BlackICE Keycloak login theme, Playwright Keycloak theme UI regression, Cornerstone3D viewer, Vue 3 Vite authenticated SPA, BFF OIDC session cookie, Quarkus CSRF protection, Narrow WADO viewer path, keycloak.v2 child theme (+6 more)

### Community 5 - "SessionResource.java"
Cohesion: 0.29
Nodes (9): SessionResource, SessionResponse, Authenticated, GET, JsonWebToken, Path, PermitAll, Response (+1 more)

### Community 6 - "DICOMweb Verbs"
Cohesion: 0.15
Nodes (13): DICOM Domain Pack, DICOMweb Verbs, QIDO-RS, STOW-RS, WADO-RS, CSRF Protection, Quarkus BFF, Quarkus Domain Pack (+5 more)

### Community 7 - "Community Label Quality Gate"
Cohesion: 0.17
Nodes (12): Automatic Label Reuse Invalidation, Cluster-Only Workflow, Clustered Communities, Community Label Quality Gate, Community Labels, Community Membership Signatures, Current Community Summaries, Exact Community-Key Coverage (+4 more)

### Community 8 - "Community Label Quality Gate"
Cohesion: 0.17
Nodes (12): Automatic Label Reuse Invalidation, Cluster-Only Workflow, Clustered Communities, Community Label Quality Gate, Community Labels, Community Membership Signatures, Current Community Summaries, Exact Community-Key Coverage (+4 more)

### Community 9 - "mvnw"
Cohesion: 0.33
Nodes (6): mvnw script, clean(), die(), exec_maven(), set_java_home(), verbose()

### Community 10 - "Quarkus Product Backend"
Cohesion: 0.43
Nodes (7): BlackICE PACS, DCM4CHEE Archive, Keycloak OIDC SSO, Quarkus Product Backend, Vue SPA, HTTP Contract Boundary, User Token Propagation

### Community 11 - "Incremental re-extraction runbook"
Cohesion: 0.33
Nodes (6): Cluster-only refresh, Incremental re-extraction runbook, Incremental merge integrity, Cluster-only refresh, Incremental re-extraction runbook, Incremental merge integrity

### Community 12 - "BFF Session Contract"
Cohesion: 0.33
Nodes (6): Backend BFF, HttpOnly Session Cookie, BFF Session Contract, Frontend SPA, Keycloak Login E2E Contract, Vue BFF Session

### Community 13 - "keycloak-login.spec.ts"
Cohesion: 0.53
Nodes (5): expectVerticallyCentered(), openBlackiceLogin(), requiredBox(), screenshotLoginCard(), showInvalidCredentials()

### Community 14 - "Complete incremental update"
Cohesion: 0.33
Nodes (6): Artifact regeneration, Complete incremental update, Graphify CLI, Graphify skill, Project-scoped installation, Semantic extraction

### Community 15 - "SessionResourceTest.java"
Cohesion: 0.60
Nodes (3): SessionResourceTest, QuarkusTest, Test

### Community 16 - "DICOM Patient Study Series Instance Hierarchy"
Cohesion: 0.40
Nodes (5): DICOM Patient Study Series Instance Hierarchy, DICOM UID Identity, Patient Identity with Issuer, Transfer Syntax Preservation, Report Model

### Community 17 - "Domain Packs"
Cohesion: 0.50
Nodes (4): Domain Packs, Claude-Codex Agent Portability, Domain Packs Single Source of Truth, Codex Subagents Implementation Plan

### Community 18 - "Feature-First Architecture"
Cohesion: 0.50
Nodes (4): Feature-First Architecture, BlackICE Monorepo Structure, Vue Feature-First Conventions, Historical Specs and Plans

### Community 20 - "DICOM Domain Reviewer"
Cohesion: 0.67
Nodes (3): DICOM Domain Reviewer, Quarkus Backend, DICOM Viewer Frontend

### Community 21 - "DCM4CHEE 5.34.3 Baseline"
Cohesion: 0.67
Nodes (3): DCM4CHEE 5.34.3 Baseline, Secure Archive Deployment, DCM4CHEE 5.34.3 Documentation Plan

### Community 22 - "DCM4CHEE Archive 5.34.3"
Cohesion: 0.67
Nodes (3): DCM4CHEE Archive 5.34.3, Quarkus product backend, Curated DICOMweb product API

### Community 70 - "BlackICE — Login same-origin: tirar o Keycloak da barra de endereços"
Cohesion: 0.09
Nodes (21): Alternativas descartadas, `apps/backend/src/main/resources/application.properties`, BlackICE — Login same-origin: tirar o Keycloak da barra de endereços, Custo honesto, Custo honesto (revisado), Decisões, Estado atual (verificado em 2026-08-07, stack no ar), Fase 1 — Same-origin atrás do Traefik (+13 more)

### Community 71 - "Keycloak same-origin — Implementation Plan"
Cohesion: 0.29
Nodes (6): Estrutura de arquivos, Global Constraints, Keycloak same-origin — Implementation Plan, Self-review, Task 1: Fase 1 — Keycloak same-origin sob `/auth`, Task 2: Fase 2 — renomear o realm para `blackice`

## Knowledge Gaps
- **158 isolated node(s):** `Global Constraints`, `Task 1: Fase 1 — Keycloak same-origin sob `/auth``, `Task 2: Fase 2 — renomear o realm para `blackice``, `Self-review`, `Objetivo` (+153 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **43 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `devDependencies` connect `devDependencies` to `package.json`?**
  _High betweenness centrality (0.008) - this node is a cross-community bridge._
- **What connects `Global Constraints`, `Task 1: Fase 1 — Keycloak same-origin sob `/auth``, `Task 2: Fase 2 — renomear o realm para `blackice`` to the rest of the system?**
  _158 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `compilerOptions` be split into smaller, more focused modules?**
  _Cohesion score 0.1 - nodes in this community are weakly interconnected._
- **Should `compilerOptions` be split into smaller, more focused modules?**
  _Cohesion score 0.10526315789473684 - nodes in this community are weakly interconnected._
- **Should `devDependencies` be split into smaller, more focused modules?**
  _Cohesion score 0.11764705882352941 - nodes in this community are weakly interconnected._
- **Should `package.json` be split into smaller, more focused modules?**
  _Cohesion score 0.125 - nodes in this community are weakly interconnected._
- **Should `BlackICE Keycloak login theme` be split into smaller, more focused modules?**
  _Cohesion score 0.14285714285714285 - nodes in this community are weakly interconnected._