# Graph Report - BlackICE  (2026-08-09)

## Corpus Check
- 80 files · ~61,440 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 352 nodes · 293 edges · 87 communities (36 shown, 51 thin omitted)
- Extraction: 95% EXTRACTED · 5% INFERRED · 0% AMBIGUOUS · INFERRED: 16 edges (avg confidence: 0.87)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `0b9cc18a`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- Área de 2026-08-07-keycloak-same-origin-design.md
- Área de tsconfig.node.json
- Área de tsconfig.app.json
- Área de devDependencies
- Área de package.json
- Área de DICOM Domain Review
- Área de BlackICE PACS Architecture
- Área de SessionResource.java
- Área de Automatic Label Reuse Invalidation
- Área de DICOMweb Verbs
- Área de Safe Commit Scope
- Área de In-Place Keycloak Realm Rename
- Área de mvnw
- Área de DICOM Domain Pack
- Área de 2026-08-07-keycloak-same-origin.md
- Área de Graphify Pipeline
- Área de keycloak-login.spec.ts
- Área de SessionResourceTest.java
- Área de query_20260727_184232_como_o_frontend_vue_obtém_a_sessão_autenticada_do.
- Área de query_20260727_184243_quais_regras_do_blackice_governam_studyinstanceuid
- Área de query_20260727_184305_onde_ficam_as_instruções_operacionais_do_graphify.
- Área de Folder Watcher
- Área de Graphify Precommit Update
- Área de BlackICE Keycloak login theme
- Área de 2026-08-09-commit-curator.md
- Área de BlackICE Keycloak Login Theme
- Área de Graphify Output Exclusion
- Área de Backend Container Build Artifacts
- Área de tsconfig.json
- Área de Cluster-only refresh
- Área de Authorization Code with PKCE
- Área de Shared Realm Audience
- Área de Graph Exports
- Área de Confidence Rubric
- Área de Cross-Repository Graph Merge
- Área de AGENTS.md Integration
- Área de Graph Traversal
- Área de Transcription Domain Hint
- Área de Graphify Local State
- Área de Backend Java and Maven Toolchain
- Área de Backend BFF
- Área de playwright.config.ts
- Área de BlackICE Favicon
- Área de index.ts
- Área de HomePage.vue
- Área de session.types.ts
- Área de DCM4CHEE 5.34.3 Baseline
- Área de Pixel Data Preservation
- Área de DCM4CHEE 5.34.3 Baseline
- Área de Canonical three-file Compose configuration
- Área de Feature-first monorepo structure
- Área de Focus outline accessibility
- Área de Docker API compatibility proxy
- Área de configure-blackice.sh
- Área de Git Commit Conventions
- Área de Multi-Agent Mode
- Área de Vue Mount Point
- Área de Bluesky icon
- Área de Discord icon
- Área de Documentation icon
- Área de GitHub icon
- Área de Social icon
- Área de X icon
- Área de Graphify Add and Watch
- Área de Graphify Exports
- Área de Semantic Extraction
- Área de Cross-repository Merge
- Área de Graphify Hooks
- Área de Graphify Query
- Área de Graphify Transcription
- Área de User Token Propagation
- Área de PatientID with IssuerOfPatientID
- Área de Portuguese Gitmoji Commit Message
- Área de Vue BFF Session
- Área de Vue Feature-First Conventions
- Área de Historical Specs and Plans
- Área de Codex Subagent Configuration
- Área de BlackICE PACS Architecture Decisions
- Área de Frontend SPA Service
- Área de Product PostgreSQL database
- Área de dev.blackice:blackice-backend
- Área de Serena Vue Language Server Configuration

## God Nodes (most connected - your core abstractions)
1. `compilerOptions` - 15 edges
2. `compilerOptions` - 11 edges
3. `BlackICE — Login same-origin: tirar o Keycloak da barra de endereços` - 10 edges
4. `Commit Curator — Design` - 8 edges
5. `Fase 1 — Same-origin atrás do Traefik` - 8 edges
6. `SessionResource` - 6 edges
7. `scripts` - 6 edges
8. `Keycloak Service` - 6 edges
9. `Fase 2 — Renomear o realm para `blackice`` - 5 edges
10. `DICOM Domain Review` - 5 edges

## Surprising Connections (you probably didn't know these)
- `Codex DICOM Reviewer Agent` --semantically_similar_to--> `DICOM Domain Review`  [INFERRED] [semantically similar]
  .codex/agents/dicom-domain-reviewer.toml → .claude/agents/dicom/dicom-domain-reviewer.md
- `BlackICE MVP` --semantically_similar_to--> `BlackICE PACS Architecture`  [INFERRED] [semantically similar]
  README.md → AGENTS.md
- `Domain Packs Single Source of Truth` --semantically_similar_to--> `Domain Packs`  [INFERRED] [semantically similar]
  docs/domains/README.md → AGENTS.md
- `Codex Quarkus Backend Agent` --semantically_similar_to--> `BlackICE Quarkus Backend`  [INFERRED] [semantically similar]
  .codex/agents/quarkus-backend.toml → .claude/agents/quarkus/quarkus-backend.md
- `Codex DICOM Viewer Agent` --semantically_similar_to--> `Cornerstone3D Viewer`  [INFERRED] [semantically similar]
  .codex/agents/dicom-viewer-frontend.toml → .claude/agents/vue/dicom-viewer-frontend.md

## Import Cycles
- None detected.

## Hyperedges (group relationships)
- **Graphify Lifecycle** — _agents_skills_graphify_skill_graphify_pipeline, _agents_skills_graphify_skill_semantic_extraction, _agents_skills_graphify_skill_incremental_update [EXTRACTED 1.00]
- **DICOM Delivery Roles** — _claude_agents_dicom_dicom_domain_reviewer_dicom_domain_review, _claude_agents_quarkus_quarkus_backend_quarkus_backend, _claude_agents_vue_dicom_viewer_frontend_cornerstone_viewer [INFERRED 0.85]
- **Graphify Project Configuration** — _gitattributes_graphify_merge_driver, _gitignore_graphify_local_state, _graphify_setup_project_scoped_setup, _graphifyignore_generated_corpus_exclusion [INFERRED 0.85]
- **BlackICE Product Architecture** — agents_blackice_pacs_architecture, apps_backend_src_main_resources_application_quarkus_oidc_bff_session, apps_frontend_dockerfile_vue_spa_nginx_delivery, docs_architecture_project_structure_monorepo_structure [EXTRACTED 1.00]
- **Graphify Project Governance** — graphifyignore_graphify_corpus_policy, docs_architecture_graphify_project_scoped_graphify, agents_domain_packs [EXTRACTED 1.00]
- **DICOM Patient Identity Safety** — docs_domains_dicom_semantics_dicom_patient_study_series_instance_hierarchy, docs_domains_dicom_semantics_dicom_uid_identity, docs_domains_dicom_semantics_patientid_with_issuer [EXTRACTED 1.00]
- **BlackICE Same-Origin OIDC Flow** — infra_compose_apps_backend_bff, infra_compose_apps_oidc_frontchannel_backchannel_split, infra_dcm4chee_compose_keycloak, infra_dcm4chee_compose_traefik_auth_router [EXTRACTED 1.00]
- **DCM4CHEE Secure Service Stack** — infra_dcm4chee_compose_ldap, infra_dcm4chee_compose_mariadb, infra_dcm4chee_compose_keycloak, infra_dcm4chee_compose_arc_db, infra_dcm4chee_compose_arc [EXTRACTED 1.00]
- **Domain Pack Knowledge Model** — docs_domains_quarkus_quarkus_domain_pack, docs_domains_vue_vue_domain_pack [EXTRACTED 1.00]
- **DICOMweb Operation Roles** — docs_domains_dicom_dicomweb_qido_rs, docs_domains_dicom_dicomweb_wado_rs, docs_domains_dicom_dicomweb_stow_rs [EXTRACTED 1.00]
- **Keycloak login quality workflow** — docs_superpowers_plans_2026_07_26_keycloak_login_theme_blackice_login_theme, docs_superpowers_plans_2026_07_27_keycloak_input_focus_outline_focus_outline_accessibility, docs_superpowers_plans_2026_07_27_keycloak_theme_e2e_ui_regression_playwright_keycloak_theme_regression [INFERRED 0.85]
- **Canonical BlackICE Compose stack** — infra_readme_three_compose_files, infra_compose_traefik_routing, infra_compose_product_postgres_database [EXTRACTED 1.00]
- **Frontend icon sprite collection** — apps_frontend_public_icons_bluesky_icon, apps_frontend_public_icons_discord_icon, apps_frontend_public_icons_documentation_icon, apps_frontend_public_icons_github_icon, apps_frontend_public_icons_social_icon, apps_frontend_public_icons_x_icon [EXTRACTED 1.00]

## Communities (87 total, 51 thin omitted)

### Community 0 - "Área de 2026-08-07-keycloak-same-origin-design.md"
Cohesion: 0.09
Nodes (21): Alternativas descartadas, `apps/backend/src/main/resources/application.properties`, BlackICE — Login same-origin: tirar o Keycloak da barra de endereços, Custo honesto, Custo honesto (revisado), Decisões, Estado atual (verificado em 2026-08-07, stack no ar), Fase 1 — Same-origin atrás do Traefik (+13 more)

### Community 1 - "Área de tsconfig.node.json"
Cohesion: 0.10
Nodes (19): compilerOptions, allowImportingTsExtensions, erasableSyntaxOnly, lib, module, moduleDetection, noEmit, noFallthroughCasesInSwitch (+11 more)

### Community 2 - "Área de tsconfig.app.json"
Cohesion: 0.11
Nodes (18): compilerOptions, allowArbitraryExtensions, baseUrl, erasableSyntaxOnly, ignoreDeprecations, noFallthroughCasesInSwitch, noUnusedLocals, noUnusedParameters (+10 more)

### Community 3 - "Área de devDependencies"
Cohesion: 0.12
Nodes (17): devDependencies, @playwright/test, @types/node, typescript, vite, @vitejs/plugin-vue, vitest, vue-tsc (+9 more)

### Community 4 - "Área de package.json"
Cohesion: 0.12
Nodes (15): dependencies, vue, vue-router, name, private, scripts, build, dev (+7 more)

### Community 5 - "Área de DICOM Domain Review"
Cohesion: 0.17
Nodes (13): DICOM Domain Review, Human Semantic Gate, DICOMweb Backend Boundary, BlackICE Quarkus Backend, Cornerstone3D Viewer, Vue Reactivity Boundary, Thin Skill Pattern, Codex DICOM Reviewer Agent (+5 more)

### Community 6 - "Área de BlackICE PACS Architecture"
Cohesion: 0.19
Nodes (13): BlackICE PACS Architecture, DICOM Data Integrity Invariants, Domain Packs, Quarkus OIDC BFF Session, Vue SPA Nginx Delivery, Frontend Node Toolchain, Keycloak Login E2E Contract, Claude Code Agent Wrappers (+5 more)

### Community 7 - "Área de SessionResource.java"
Cohesion: 0.29
Nodes (9): SessionResource, SessionResponse, Authenticated, GET, JsonWebToken, Path, PermitAll, Response (+1 more)

### Community 8 - "Área de Automatic Label Reuse Invalidation"
Cohesion: 0.17
Nodes (12): Automatic Label Reuse Invalidation, Cluster-Only Workflow, Clustered Communities, Community Label Quality Gate, Community Labels, Community Membership Signatures, Current Community Summaries, Exact Community-Key Coverage (+4 more)

### Community 9 - "Área de DICOMweb Verbs"
Cohesion: 0.17
Nodes (12): DICOMweb Verbs, QIDO-RS, STOW-RS, WADO-RS, CSRF Protection, Quarkus BFF, Quarkus Domain Pack, Cornerstone3D Viewer (+4 more)

### Community 10 - "Área de Safe Commit Scope"
Cohesion: 0.17
Nodes (11): Safe Commit Scope, Commit Curator, Git Domain Pack, Commit Curator Implementation, Arquitetura, Commit Curator — Design, Comportamento, Limites (+3 more)

### Community 11 - "Área de In-Place Keycloak Realm Rename"
Cohesion: 0.18
Nodes (11): In-Place Keycloak Realm Rename, Backend BFF Service, OIDC Frontchannel Backchannel Split, DCM4CHEE Archive Service, DCM4CHEE Archive PostgreSQL Service, Keycloak Service, LDAP Service, Keycloak MariaDB Service (+3 more)

### Community 12 - "Área de mvnw"
Cohesion: 0.33
Nodes (6): mvnw script, clean(), die(), exec_maven(), set_java_home(), verbose()

### Community 13 - "Área de DICOM Domain Pack"
Cohesion: 0.29
Nodes (7): DICOM Domain Pack, DICOM Domain Reviewer, DICOM Patient Study Series Instance Hierarchy, DICOM UID Identity, StudyInstanceUID, Report Model, Domain Pack Wrappers

### Community 14 - "Área de 2026-08-07-keycloak-same-origin.md"
Cohesion: 0.29
Nodes (6): Estrutura de arquivos, Global Constraints, Keycloak same-origin — Implementation Plan, Self-review, Task 1: Fase 1 — Keycloak same-origin sob `/auth`, Task 2: Fase 2 — renomear o realm para `blackice`

### Community 15 - "Área de Graphify Pipeline"
Cohesion: 0.33
Nodes (6): Graphify Pipeline, Incremental Update, Semantic Extraction, Graphify Merge Driver, Canonical Skill Integrity Guard, Project-Scoped Graphify Setup

### Community 16 - "Área de keycloak-login.spec.ts"
Cohesion: 0.53
Nodes (5): expectVerticallyCentered(), openBlackiceLogin(), requiredBox(), screenshotLoginCard(), showInvalidCredentials()

### Community 17 - "Área de SessionResourceTest.java"
Cohesion: 0.60
Nodes (3): SessionResourceTest, QuarkusTest, Test

### Community 18 - "Área de query_20260727_184232_como_o_frontend_vue_obtém_a_sessão_autenticada_do."
Cohesion: 0.40
Nodes (4): Answer, Outcome, Q: Como o frontend Vue obtém a sessão autenticada do backend?, Source Nodes

### Community 19 - "Área de query_20260727_184243_quais_regras_do_blackice_governam_studyinstanceuid"
Cohesion: 0.40
Nodes (4): Answer, Outcome, Q: Quais regras do BlackICE governam StudyInstanceUID e os verbos DICOMweb?, Source Nodes

### Community 20 - "Área de query_20260727_184305_onde_ficam_as_instruções_operacionais_do_graphify."
Cohesion: 0.40
Nodes (4): Answer, Outcome, Q: Onde ficam as instruções operacionais do Graphify no BlackICE?, Source Nodes

### Community 21 - "Área de Folder Watcher"
Cohesion: 0.50
Nodes (4): Folder Watcher, URL Ingestion, Incremental Re-extraction, Replace on Re-extract

### Community 22 - "Área de Graphify Precommit Update"
Cohesion: 0.50
Nodes (4): Graphify Precommit Update, Graphify Project Integration, Incremental Graph Maintenance, Graphify Integration Design

### Community 23 - "Área de BlackICE Keycloak login theme"
Cohesion: 0.50
Nodes (4): BlackICE Keycloak login theme, Playwright Keycloak theme UI regression, keycloak.v2 child theme, Hybrid UI regression strategy

### Community 24 - "Área de 2026-08-09-commit-curator.md"
Cohesion: 0.50
Nodes (3): Commit Curator Implementation Plan, Global Constraints, Task 1: Domain Pack e wrappers de commit

### Community 25 - "Área de BlackICE Keycloak Login Theme"
Cohesion: 0.50
Nodes (4): BlackICE Keycloak Login Theme, BlackICE Login Branding Messages, BlackICE Login Visual Design, Keycloak v2 Theme Inheritance

### Community 26 - "Área de Graphify Output Exclusion"
Cohesion: 0.67
Nodes (3): Graphify Output Exclusion, Claude Skill Mirror Exclusion, Generated Corpus Exclusion

### Community 27 - "Área de Backend Container Build Artifacts"
Cohesion: 1.00
Nodes (3): Backend Container Build Artifacts, Quarkus Micro Native Container, Quarkus Native Container

### Community 29 - "Área de Cluster-only refresh"
Cohesion: 0.67
Nodes (3): Cluster-only refresh, Incremental re-extraction runbook, Incremental merge integrity

### Community 30 - "Área de Authorization Code with PKCE"
Cohesion: 0.67
Nodes (3): Authorization Code with PKCE, Keycloak Same-Origin Migration, Keycloak Same-Origin Design

### Community 31 - "Área de Shared Realm Audience"
Cohesion: 0.67
Nodes (3): Shared Realm Audience, blackice-quarkus Client, DICOMweb Authorization

## Knowledge Gaps
- **179 isolated node(s):** `Objetivo`, `Arquitetura`, `Comportamento`, `Modelos`, `Limites` (+174 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **51 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `devDependencies` connect `Área de devDependencies` to `Área de package.json`?**
  _High betweenness centrality (0.006) - this node is a cross-community bridge._
- **What connects `Objetivo`, `Arquitetura`, `Comportamento` to the rest of the system?**
  _179 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Área de 2026-08-07-keycloak-same-origin-design.md` be split into smaller, more focused modules?**
  _Cohesion score 0.09090909090909091 - nodes in this community are weakly interconnected._
- **Should `Área de tsconfig.node.json` be split into smaller, more focused modules?**
  _Cohesion score 0.1 - nodes in this community are weakly interconnected._
- **Should `Área de tsconfig.app.json` be split into smaller, more focused modules?**
  _Cohesion score 0.10526315789473684 - nodes in this community are weakly interconnected._
- **Should `Área de devDependencies` be split into smaller, more focused modules?**
  _Cohesion score 0.11764705882352941 - nodes in this community are weakly interconnected._
- **Should `Área de package.json` be split into smaller, more focused modules?**
  _Cohesion score 0.125 - nodes in this community are weakly interconnected._