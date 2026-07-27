# Graph Report - .  (2026-07-27)

## Corpus Check
- 87 files · ~58,981 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 237 nodes · 202 edges · 61 communities (26 shown, 35 thin omitted)
- Extraction: 98% EXTRACTED · 2% INFERRED · 0% AMBIGUOUS · INFERRED: 4 edges (avg confidence: 0.91)
- Token cost: 0 input · 0 output

## Community Hubs (Navigation)
- Node TypeScript Config
- App TypeScript Config
- Test Dependencies
- Frontend Package Metadata
- Keycloak Login Theme
- Backend Session Resource
- DICOMweb Domain Rules
- Maven Wrapper Script
- PACS Architecture
- Authenticated BFF Design
- Keycloak Login E2E
- Graphify Core Workflow
- Session Resource Tests
- Graphify Semantic Workflow
- DICOM Data Semantics
- Portable Domain Packs
- Feature First Architecture
- TypeScript Project References
- Agent Domain Wrappers
- DCM4CHEE Baseline Plan
- Graphify Local Tooling
- DCM4CHEE Product Integration
- DICOM Review Gates
- Playwright Configuration
- BlackICE Favicon
- Frontend Router
- Home Page
- Session API Types
- Compose Stack Configuration
- Monorepo Structure Plan
- Graphify Integration Plan
- Focus Accessibility Plan
- Docker Routing Proxy
- Theme Configuration Script
- Architecture Documentation
- Graphify Add Watch
- Graphify Exports
- Graphify Hooks
- Graphify Queries
- Vue Mount Point
- Bluesky Icon
- Discord Icon
- Documentation Icon
- GitHub Icon
- Social Icon
- X Icon
- Graphify Add Watch Copy
- Graphify Exports Copy
- Graphify Hooks Copy
- Graphify Queries Copy
- BlackICE Skills Index
- Keycloak OIDC SSO
- Frontend SPA Service
- Product PostgreSQL
- Secure DCM4CHEE Stack
- Backend Maven Coordinates

## God Nodes (most connected - your core abstractions)
1. `compilerOptions` - 15 edges
2. `compilerOptions` - 11 edges
3. `SessionResource` - 6 edges
4. `scripts` - 6 edges
5. `DICOMweb Verbs` - 5 edges
6. `include` - 4 edges
7. `Graphify` - 4 edges
8. `Graphify` - 4 edges
9. `Quarkus Product Backend` - 4 edges
10. `Quarkus BFF` - 4 edges

## Surprising Connections (you probably didn't know these)
- `Domain Packs Single Source of Truth` --semantically_similar_to--> `Domain Packs`  [INFERRED] [semantically similar]
  docs/domains/README.md → AGENTS.md
- `Vue BFF Session` --semantically_similar_to--> `BFF Session Contract`  [INFERRED] [semantically similar]
  docs/domains/vue/conventions.md → apps/frontend/README.md
- `HTTP Contract Boundary` --conceptually_related_to--> `Quarkus Product Backend`  [EXTRACTED]
  docs/architecture/project-structure.md → AGENTS.md
- `HTTP Contract Boundary` --conceptually_related_to--> `Vue SPA`  [EXTRACTED]
  docs/architecture/project-structure.md → AGENTS.md
- `User Token Propagation` --conceptually_related_to--> `Keycloak OIDC SSO`  [EXTRACTED]
  docs/domains/dicom/dicomweb.md → AGENTS.md

## Import Cycles
- None detected.

## Hyperedges (group relationships)
- **BlackICE Core Architecture** — agents_dcm4chee_archive, agents_quarkus_product_backend, agents_vue_spa, agents_keycloak_oidc [EXTRACTED 1.00]
- **DICOMweb Operation Roles** — docs_domains_dicom_dicomweb_qido_rs, docs_domains_dicom_dicomweb_wado_rs, docs_domains_dicom_dicomweb_stow_rs [EXTRACTED 1.00]
- **Domain Pack Knowledge Model** — docs_domains_dicom_dicom_domain_pack, docs_domains_quarkus_quarkus_domain_pack, docs_domains_vue_vue_domain_pack [EXTRACTED 1.00]
- **BlackICE MVP architecture** — docs_superpowers_specs_2026_07_22_blackice_pacs_decisions_dcm4chee_archive, docs_superpowers_specs_2026_07_22_blackice_pacs_decisions_quarkus_product_backend, docs_superpowers_specs_2026_07_22_blackice_pacs_decisions_vue_spa, docs_superpowers_specs_2026_07_22_blackice_pacs_decisions_cornerstone3d_viewer, docs_superpowers_specs_2026_07_22_blackice_pacs_decisions_keycloak_oidc_sso [EXTRACTED 1.00]
- **Canonical BlackICE Compose stack** — infra_readme_three_compose_files, infra_compose_traefik_routing, infra_compose_product_postgres_database, infra_dcm4chee_compose_keycloak_service, infra_dcm4chee_compose_archive_service, infra_compose_apps_frontend_spa_service [EXTRACTED 1.00]
- **Keycloak login quality workflow** — docs_superpowers_plans_2026_07_26_keycloak_login_theme_blackice_login_theme, docs_superpowers_plans_2026_07_27_keycloak_input_focus_outline_focus_outline_accessibility, docs_superpowers_plans_2026_07_27_keycloak_theme_e2e_ui_regression_playwright_keycloak_theme_regression [INFERRED 0.85]
- **Frontend icon sprite collection** — apps_frontend_public_icons_bluesky_icon, apps_frontend_public_icons_discord_icon, apps_frontend_public_icons_documentation_icon, apps_frontend_public_icons_github_icon, apps_frontend_public_icons_social_icon, apps_frontend_public_icons_x_icon [EXTRACTED 1.00]

## Communities (61 total, 35 thin omitted)

### Community 0 - "Node TypeScript Config"
Cohesion: 0.10
Nodes (19): compilerOptions, allowImportingTsExtensions, erasableSyntaxOnly, lib, module, moduleDetection, noEmit, noFallthroughCasesInSwitch (+11 more)

### Community 1 - "App TypeScript Config"
Cohesion: 0.11
Nodes (18): compilerOptions, allowArbitraryExtensions, baseUrl, erasableSyntaxOnly, ignoreDeprecations, noFallthroughCasesInSwitch, noUnusedLocals, noUnusedParameters (+10 more)

### Community 2 - "Test Dependencies"
Cohesion: 0.12
Nodes (17): devDependencies, @playwright/test, @types/node, typescript, vite, @vitejs/plugin-vue, vitest, vue-tsc (+9 more)

### Community 3 - "Frontend Package Metadata"
Cohesion: 0.12
Nodes (15): dependencies, vue, vue-router, name, private, scripts, build, dev (+7 more)

### Community 4 - "Keycloak Login Theme"
Cohesion: 0.14
Nodes (14): BlackICE Keycloak login theme, Playwright Keycloak theme UI regression, Cornerstone3D viewer, Vue 3 Vite authenticated SPA, BFF OIDC session cookie, Quarkus CSRF protection, Narrow WADO viewer path, keycloak.v2 child theme (+6 more)

### Community 5 - "Backend Session Resource"
Cohesion: 0.29
Nodes (9): SessionResource, SessionResponse, Authenticated, GET, JsonWebToken, Path, PermitAll, Response (+1 more)

### Community 6 - "DICOMweb Domain Rules"
Cohesion: 0.15
Nodes (13): DICOM Domain Pack, DICOMweb Verbs, QIDO-RS, STOW-RS, WADO-RS, CSRF Protection, Quarkus BFF, Quarkus Domain Pack (+5 more)

### Community 7 - "Maven Wrapper Script"
Cohesion: 0.33
Nodes (6): mvnw script, clean(), die(), exec_maven(), set_java_home(), verbose()

### Community 8 - "PACS Architecture"
Cohesion: 0.43
Nodes (7): BlackICE PACS, DCM4CHEE Archive, Keycloak OIDC SSO, Quarkus Product Backend, Vue SPA, HTTP Contract Boundary, User Token Propagation

### Community 9 - "Authenticated BFF Design"
Cohesion: 0.33
Nodes (6): Backend BFF, HttpOnly Session Cookie, BFF Session Contract, Frontend SPA, Keycloak Login E2E Contract, Vue BFF Session

### Community 10 - "Keycloak Login E2E"
Cohesion: 0.53
Nodes (5): expectVerticallyCentered(), openBlackiceLogin(), requiredBox(), screenshotLoginCard(), showInvalidCredentials()

### Community 11 - "Graphify Core Workflow"
Cohesion: 0.40
Nodes (5): Semantic Extraction, Cross-repository Merge, Graphify Transcription, Incremental Extraction, Graphify

### Community 12 - "Session Resource Tests"
Cohesion: 0.60
Nodes (3): SessionResourceTest, QuarkusTest, Test

### Community 13 - "Graphify Semantic Workflow"
Cohesion: 0.40
Nodes (5): Semantic Extraction, Cross-repository Merge, Graphify Transcription, Incremental Extraction, Graphify

### Community 14 - "DICOM Data Semantics"
Cohesion: 0.40
Nodes (5): DICOM Patient Study Series Instance Hierarchy, DICOM UID Identity, Patient Identity with Issuer, Transfer Syntax Preservation, Report Model

### Community 15 - "Portable Domain Packs"
Cohesion: 0.50
Nodes (4): Domain Packs, Claude-Codex Agent Portability, Domain Packs Single Source of Truth, Codex Subagents Implementation Plan

### Community 16 - "Feature First Architecture"
Cohesion: 0.50
Nodes (4): Feature-First Architecture, BlackICE Monorepo Structure, Vue Feature-First Conventions, Historical Specs and Plans

### Community 18 - "Agent Domain Wrappers"
Cohesion: 0.67
Nodes (3): DICOM Domain Reviewer, Quarkus Backend, DICOM Viewer Frontend

### Community 19 - "DCM4CHEE Baseline Plan"
Cohesion: 0.67
Nodes (3): DCM4CHEE 5.34.3 Baseline, Secure Archive Deployment, DCM4CHEE 5.34.3 Documentation Plan

### Community 20 - "Graphify Local Tooling"
Cohesion: 0.67
Nodes (3): Graphify Corpus Security, Graphify CLI 0.9.28, Graphify Local Engineering Tool

### Community 21 - "DCM4CHEE Product Integration"
Cohesion: 0.67
Nodes (3): DCM4CHEE Archive 5.34.3, Quarkus product backend, Curated DICOMweb product API

## Knowledge Gaps
- **114 isolated node(s):** `dev.blackice:blackice-backend`, `name`, `private`, `version`, `type` (+109 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **35 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `devDependencies` connect `Test Dependencies` to `Frontend Package Metadata`?**
  _High betweenness centrality (0.013) - this node is a cross-community bridge._
- **What connects `dev.blackice:blackice-backend`, `name`, `private` to the rest of the system?**
  _114 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Node TypeScript Config` be split into smaller, more focused modules?**
  _Cohesion score 0.1 - nodes in this community are weakly interconnected._
- **Should `App TypeScript Config` be split into smaller, more focused modules?**
  _Cohesion score 0.10526315789473684 - nodes in this community are weakly interconnected._
- **Should `Test Dependencies` be split into smaller, more focused modules?**
  _Cohesion score 0.11764705882352941 - nodes in this community are weakly interconnected._
- **Should `Frontend Package Metadata` be split into smaller, more focused modules?**
  _Cohesion score 0.125 - nodes in this community are weakly interconnected._
- **Should `Keycloak Login Theme` be split into smaller, more focused modules?**
  _Cohesion score 0.14285714285714285 - nodes in this community are weakly interconnected._