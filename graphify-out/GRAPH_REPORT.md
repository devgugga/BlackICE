# Graph Report - .  (2026-07-27)

## Corpus Check
- 87 files · ~59,731 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 270 nodes · 226 edges · 70 communities (27 shown, 43 thin omitted)
- Extraction: 98% EXTRACTED · 2% INFERRED · 0% AMBIGUOUS · INFERRED: 5 edges (avg confidence: 0.92)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `624b2c76`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- mvnw
- dev.blackice:blackice-backend
- SessionResource.java
- SessionResourceTest.java
- keycloak-login.spec.ts
- package.json
- devDependencies
- playwright.config.ts
- index.ts
- HomePage.vue
- session.types.ts
- compilerOptions
- tsconfig.json
- compilerOptions
- configure-blackice.sh
- Graphify Add and Watch
- Graphify Exports
- Semantic Extraction
- Cross-repository Merge
- Graphify Hooks
- Graphify Query
- Graphify Transcription
- DICOM Domain Reviewer
- BlackICE Skills
- Graphify Add and Watch
- Graphify Exports
- Semantic Extraction
- Cross-repository Merge
- Graphify Hooks
- Graphify Query
- Graphify Transcription
- Quarkus Product Backend
- Domain Packs
- DICOM Invariants
- BlackICE Architecture
- BFF Session Contract
- Vue Mount Point
- DCM4CHEE 5.34.3 Baseline
- Feature-First Architecture
- DICOMweb Verbs
- DICOM Patient Study Series Instance Hierarchy
- Feature-first monorepo structure
- Canonical three-file Compose configuration
- BlackICE Keycloak login theme
- Project-scoped Graphify integration
- Focus outline accessibility
- DCM4CHEE Archive 5.34.3
- Keycloak OIDC SSO
- Frontend SPA service
- Docker API compatibility proxy
- Product PostgreSQL database
- Secure DCM4CHEE archive stack
- BlackICE Favicon
- Bluesky icon
- Discord icon
- Documentation icon
- GitHub icon
- Social icon
- X icon
- Incremental re-extraction runbook
- Domain Packs
- Complete incremental update
- AST-only update
- Community Label Quality Gate
- Community Label Quality Gate

## God Nodes (most connected - your core abstractions)
1. `compilerOptions` - 15 edges
2. `compilerOptions` - 11 edges
3. `SessionResource` - 6 edges
4. `scripts` - 6 edges
5. `DICOMweb Verbs` - 5 edges
6. `include` - 4 edges
7. `Quarkus Product Backend` - 4 edges
8. `Quarkus BFF` - 4 edges
9. `Community Labels` - 4 edges
10. `Community Label Quality Gate` - 4 edges

## Surprising Connections (you probably didn't know these)
- `Domain Packs` --semantically_similar_to--> `Domain Packs Single Source of Truth`  [INFERRED] [semantically similar]
  AGENTS.md → docs/domains/README.md
- `BFF Session Contract` --semantically_similar_to--> `Vue BFF Session`  [INFERRED] [semantically similar]
  apps/frontend/README.md → docs/domains/vue/conventions.md
- `Incremental re-extraction runbook` --semantically_similar_to--> `Incremental re-extraction runbook`  [INFERRED] [semantically similar]
  .agents/skills/graphify/references/update.md → .claude/skills/graphify/references/update.md
- `Quarkus Product Backend` --conceptually_related_to--> `HTTP Contract Boundary`  [EXTRACTED]
  AGENTS.md → docs/architecture/project-structure.md
- `Vue SPA` --conceptually_related_to--> `HTTP Contract Boundary`  [EXTRACTED]
  AGENTS.md → docs/architecture/project-structure.md

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

## Communities (70 total, 43 thin omitted)

### Community 9 - "mvnw"
Cohesion: 0.33
Nodes (6): mvnw script, set_java_home(), verbose(), die(), exec_maven(), clean()

### Community 5 - "SessionResource.java"
Cohesion: 0.29
Nodes (9): SessionResource, Path, SecurityIdentity, JsonWebToken, Response, GET, PermitAll, Authenticated (+1 more)

### Community 15 - "SessionResourceTest.java"
Cohesion: 0.60
Nodes (3): SessionResourceTest, QuarkusTest, Test

### Community 13 - "keycloak-login.spec.ts"
Cohesion: 0.53
Nodes (5): openBlackiceLogin(), requiredBox(), expectVerticallyCentered(), screenshotLoginCard(), showInvalidCredentials()

### Community 3 - "package.json"
Cohesion: 0.12
Nodes (15): name, private, version, type, scripts, dev, build, preview (+7 more)

### Community 2 - "devDependencies"
Cohesion: 0.12
Nodes (17): devDependencies, @playwright/test, @playwright/test, @types/node, @types/node, @vitejs/plugin-vue, @vitejs/plugin-vue, @vue/tsconfig (+9 more)

### Community 1 - "compilerOptions"
Cohesion: 0.11
Nodes (18): extends, @vue/tsconfig/tsconfig.dom.json, compilerOptions, baseUrl, ignoreDeprecations, paths, tsBuildInfoFile, types (+10 more)

### Community 0 - "compilerOptions"
Cohesion: 0.10
Nodes (19): compilerOptions, tsBuildInfoFile, target, lib, ES2023, types, node, skipLibCheck (+11 more)

### Community 20 - "DICOM Domain Reviewer"
Cohesion: 0.67
Nodes (3): DICOM Domain Reviewer, Quarkus Backend, DICOM Viewer Frontend

### Community 10 - "Quarkus Product Backend"
Cohesion: 0.43
Nodes (7): BlackICE PACS, DCM4CHEE Archive, Quarkus Product Backend, Vue SPA, Keycloak OIDC SSO, HTTP Contract Boundary, User Token Propagation

### Community 17 - "Domain Packs"
Cohesion: 0.50
Nodes (4): Domain Packs, Claude-Codex Agent Portability, Domain Packs Single Source of Truth, Codex Subagents Implementation Plan

### Community 12 - "BFF Session Contract"
Cohesion: 0.33
Nodes (6): Backend BFF, HttpOnly Session Cookie, Frontend SPA, Keycloak Login E2E Contract, BFF Session Contract, Vue BFF Session

### Community 21 - "DCM4CHEE 5.34.3 Baseline"
Cohesion: 0.67
Nodes (3): DCM4CHEE 5.34.3 Baseline, Secure Archive Deployment, DCM4CHEE 5.34.3 Documentation Plan

### Community 18 - "Feature-First Architecture"
Cohesion: 0.50
Nodes (4): BlackICE Monorepo Structure, Feature-First Architecture, Vue Feature-First Conventions, Historical Specs and Plans

### Community 6 - "DICOMweb Verbs"
Cohesion: 0.15
Nodes (13): DICOM Domain Pack, DICOMweb Verbs, QIDO-RS, WADO-RS, STOW-RS, Quarkus Domain Pack, Quarkus BFF, CSRF Protection (+5 more)

### Community 16 - "DICOM Patient Study Series Instance Hierarchy"
Cohesion: 0.40
Nodes (5): DICOM Patient Study Series Instance Hierarchy, DICOM UID Identity, Patient Identity with Issuer, Transfer Syntax Preservation, Report Model

### Community 4 - "BlackICE Keycloak login theme"
Cohesion: 0.14
Nodes (14): BlackICE Keycloak login theme, Playwright Keycloak theme UI regression, Vue 3 Vite authenticated SPA, Cornerstone3D viewer, Narrow WADO viewer path, BFF OIDC session cookie, Quarkus CSRF protection, keycloak.v2 child theme (+6 more)

### Community 22 - "DCM4CHEE Archive 5.34.3"
Cohesion: 0.67
Nodes (3): DCM4CHEE Archive 5.34.3, Quarkus product backend, Curated DICOMweb product API

### Community 11 - "Incremental re-extraction runbook"
Cohesion: 0.33
Nodes (6): Incremental re-extraction runbook, Incremental merge integrity, Cluster-only refresh, Incremental re-extraction runbook, Incremental merge integrity, Cluster-only refresh

### Community 14 - "Complete incremental update"
Cohesion: 0.33
Nodes (6): Graphify CLI, Project-scoped installation, Graphify skill, Complete incremental update, Semantic extraction, Artifact regeneration

### Community 7 - "Community Label Quality Gate"
Cohesion: 0.17
Nodes (12): Graphify skill, Community Labels, Clustered Communities, Current Community Summaries, Community Label Quality Gate, Exact Community-Key Coverage, Placeholder Label Rejection, Non-Distinct Label Rejection (+4 more)

### Community 8 - "Community Label Quality Gate"
Cohesion: 0.17
Nodes (12): Graphify skill, Community Labels, Clustered Communities, Current Community Summaries, Community Label Quality Gate, Exact Community-Key Coverage, Placeholder Label Rejection, Non-Distinct Label Rejection (+4 more)

## Knowledge Gaps
- **136 isolated node(s):** `dev.blackice:blackice-backend`, `name`, `private`, `version`, `type` (+131 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **43 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `devDependencies` connect `devDependencies` to `package.json`?**
  _High betweenness centrality (0.010) - this node is a cross-community bridge._
- **What connects `dev.blackice:blackice-backend`, `name`, `private` to the rest of the system?**
  _136 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `package.json` be split into smaller, more focused modules?**
  _Cohesion score 0.125 - nodes in this community are weakly interconnected._
- **Should `devDependencies` be split into smaller, more focused modules?**
  _Cohesion score 0.11764705882352941 - nodes in this community are weakly interconnected._
- **Should `compilerOptions` be split into smaller, more focused modules?**
  _Cohesion score 0.10526315789473684 - nodes in this community are weakly interconnected._
- **Should `compilerOptions` be split into smaller, more focused modules?**
  _Cohesion score 0.1 - nodes in this community are weakly interconnected._
- **Should `BlackICE Keycloak login theme` be split into smaller, more focused modules?**
  _Cohesion score 0.14285714285714285 - nodes in this community are weakly interconnected._