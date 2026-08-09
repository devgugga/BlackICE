# Graph Report - .  (2026-08-09)

## Corpus Check
- 118 files · ~71,021 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 413 nodes · 329 edges · 113 communities (44 shown, 69 thin omitted)
- Extraction: 98% EXTRACTED · 2% INFERRED · 0% AMBIGUOUS · INFERRED: 6 edges (avg confidence: 0.83)
- Token cost: 0 input · 0 output

## Community Hubs (Navigation)
- Keycloak Same-Origin Migration
- Manual DICOM Import
- Node TypeScript Configuration
- Frontend TypeScript Configuration
- Frontend Test Toolchain
- Frontend Package Configuration
- Quarkus Session Resource
- Graphify Community Labels
- DICOMweb Viewer Architecture
- Commit Curator Workflow
- Specialized DICOM Agents
- Maven Wrapper Script
- BlackICE Service Topology
- Windows Keycloak Bootstrap
- DICOM Human Review
- DICOM Identity Hierarchy
- Graphify Commit Policy
- Keycloak Same-Origin Plan
- Commit Domain Wrappers
- Keycloak Login Tests
- Quarkus OIDC Configuration
- Quarkus Session Tests
- Session Graph Query
- DICOM Semantics Query
- Graphify Location Query
- BlackICE Login Theme
- Login Theme Regression
- Windows Bootstrap Plan
- Shared Audience Authorization
- Docker API Proxy
- Graphify Project Architecture
- Graphify Incremental Pipeline
- Vue Nginx Delivery
- TypeScript Project References
- Incremental Merge Runbook
- Graphify Project Integration
- Keycloak PKCE Migration
- DICOMweb Role Authorization
- Graphify Ingestion Watcher
- Graph Export Services
- Graph Extraction Schema
- Graph Query Expansion
- Replace Re-Extract Semantics
- Maven Distribution Wrapper
- BFF Session Cookie
- Playwright CI Configuration
- BlackICE Brand Assets
- Vue Router Configuration
- Authenticated Home Page
- Session Type Contract
- Multi-Agent Configuration
- DCM4CHEE Deployment Baseline
- Pixel Transfer Preservation
- Git Domain Conventions
- Secure DCM4CHEE Stack
- Compose Stack Configuration
- Feature-First Architecture
- Focus Accessibility Design
- Traefik Docker Routing
- Container Keycloak Bootstrap
- Shell Keycloak Bootstrap
- Cross-Repository Graph Merge
- Media Transcription Pipeline
- Backend Container Artifacts
- Backend Ignore Rules
- Java Toolchain
- Maven Toolchain
- Quarkus JVM Container
- Quarkus Legacy Container
- Quarkus Native Micro Container
- Quarkus Native Container
- Frontend Docker Context
- Frontend Ignore Rules
- Vue Mount Point
- Node Toolchain
- Bluesky Social Icon
- Discord Social Icon
- Documentation Navigation Icon
- GitHub Social Icon
- Generic Social Icon
- X Social Icon
- Keycloak Login Contract
- Claude Code Guidance
- Graphify Add Watch
- Graphify Export Reference
- Semantic Extraction Reference
- Cross-Repository Merge Reference
- Graphify Hooks Reference
- Graphify Query Reference
- Graphify Transcription Reference
- Graphify Output Exclusion
- BlackICE Monorepo Structure
- User Token Propagation
- Patient Identifier Issuer
- Vue BFF Session
- Vue Feature Conventions
- Historical Design Records
- Codex Agent Configuration
- PACS Architecture Decisions
- Graphify Merge Driver
- Graphify Local State
- Graphify Recursion Guard
- Frontend SPA Service
- Product PostgreSQL Database
- Quarkus Backend Artifact
- BlackICE MVP Scope

## God Nodes (most connected - your core abstractions)
1. `compilerOptions` - 15 edges
2. `compilerOptions` - 11 edges
3. `BlackICE — Login same-origin: tirar o Keycloak da barra de endereços` - 10 edges
4. `Commit Curator — Design` - 8 edges
5. `Fase 1 — Same-origin atrás do Traefik` - 8 edges
6. `Manual DICOM Import` - 8 edges
7. `Bootstrap do Keycloak no Windows — Design` - 7 edges
8. `SessionResource` - 6 edges
9. `scripts` - 6 edges
10. `Keycloak Service` - 6 edges

## Surprising Connections (you probably didn't know these)
- `Human Business Gate` --semantically_similar_to--> `Human Semantic Gate`  [INFERRED] [semantically similar]
  AGENTS.md → .codex/agents/dicom-domain-reviewer.toml
- `Graphify Commit Prerequisite` --conceptually_related_to--> `Linked Worktree Manual Graph Update`  [INFERRED]
  docs/domains/git/commit-conventions.md → .graphify/setup.ps1
- `DICOM UID Validation` --semantically_similar_to--> `DICOM Identity Invariants`  [INFERRED] [semantically similar]
  docs/superpowers/specs/2026-08-09-manual-dicom-import-design.md → AGENTS.md
- `DCM4CHEE 5.34.3 Baseline` --conceptually_related_to--> `DCM4CHEE Secure Stack`  [INFERRED]
  docs/superpowers/plans/2026-07-23-dcm4chee-5-34-3.md → infra/dcm4chee/README.md
- `In-Place Keycloak Realm Rename` --rationale_for--> `Keycloak Service`  [EXTRACTED]
  docs/superpowers/plans/2026-08-07-keycloak-same-origin.md → infra/dcm4chee/compose.yml

## Import Cycles
- None detected.

## Hyperedges (group relationships)
- **BlackICE Product Architecture** — agents_dcm4chee_archive, agents_quarkus_product_backend, agents_vue_spa [EXTRACTED 1.00]
- **Manual Import Security Flow** — docs_superpowers_specs_2026_08_09_manual_dicom_import_design_manual_dicom_import, agents_keycloak_oidc, docs_superpowers_specs_2026_08_09_manual_dicom_import_design_csrf_protection [EXTRACTED 1.00]
- **Graphify Lifecycle** — _agents_skills_graphify_skill_graphify_pipeline, _agents_skills_graphify_skill_semantic_extraction, _agents_skills_graphify_skill_incremental_update [EXTRACTED 1.00]
- **DICOM Delivery Roles** — _claude_agents_dicom_dicom_domain_reviewer_dicom_domain_review, _claude_agents_quarkus_quarkus_backend_quarkus_backend, _claude_agents_vue_dicom_viewer_frontend_cornerstone_viewer [INFERRED 0.85]
- **DICOM Patient Identity Safety** — docs_domains_dicom_semantics_dicom_patient_study_series_instance_hierarchy, docs_domains_dicom_semantics_dicom_uid_identity, docs_domains_dicom_semantics_patientid_with_issuer [EXTRACTED 1.00]
- **BlackICE Same-Origin OIDC Flow** — infra_compose_apps_backend_bff, infra_compose_apps_oidc_frontchannel_backchannel_split, infra_dcm4chee_compose_keycloak, infra_dcm4chee_compose_traefik_auth_router [EXTRACTED 1.00]
- **DCM4CHEE Secure Service Stack** — infra_dcm4chee_compose_ldap, infra_dcm4chee_compose_mariadb, infra_dcm4chee_compose_keycloak, infra_dcm4chee_compose_arc_db, infra_dcm4chee_compose_arc [EXTRACTED 1.00]
- **DICOMweb Operation Roles** — docs_domains_dicom_dicomweb_qido_rs, docs_domains_dicom_dicomweb_wado_rs, docs_domains_dicom_dicomweb_stow_rs [EXTRACTED 1.00]
- **Keycloak login quality workflow** — docs_superpowers_plans_2026_07_26_keycloak_login_theme_blackice_login_theme, docs_superpowers_plans_2026_07_27_keycloak_input_focus_outline_focus_outline_accessibility, docs_superpowers_plans_2026_07_27_keycloak_theme_e2e_ui_regression_playwright_keycloak_theme_regression [INFERRED 0.85]
- **Canonical BlackICE Compose stack** — infra_readme_three_compose_files, infra_compose_traefik_routing, infra_compose_product_postgres_database [EXTRACTED 1.00]
- **Frontend icon sprite collection** — apps_frontend_public_icons_bluesky_icon, apps_frontend_public_icons_discord_icon, apps_frontend_public_icons_documentation_icon, apps_frontend_public_icons_github_icon, apps_frontend_public_icons_social_icon, apps_frontend_public_icons_x_icon [EXTRACTED 1.00]

## Communities (113 total, 69 thin omitted)

### Community 0 - "Keycloak Same-Origin Migration"
Cohesion: 0.09
Nodes (21): Alternativas descartadas, `apps/backend/src/main/resources/application.properties`, BlackICE — Login same-origin: tirar o Keycloak da barra de endereços, Custo honesto, Custo honesto (revisado), Decisões, Estado atual (verificado em 2026-08-07, stack no ar), Fase 1 — Same-origin atrás do Traefik (+13 more)

### Community 1 - "Manual DICOM Import"
Cohesion: 0.13
Nodes (20): BlackICE PACS, DCM4CHEE Archive, DICOM Identity Invariants, DICOMweb Operations, Keycloak OIDC SSO, Quarkus Product Backend, Vue Authenticated SPA, EVO-001 Async Ingestion (+12 more)

### Community 2 - "Node TypeScript Configuration"
Cohesion: 0.10
Nodes (19): compilerOptions, allowImportingTsExtensions, erasableSyntaxOnly, lib, module, moduleDetection, noEmit, noFallthroughCasesInSwitch (+11 more)

### Community 3 - "Frontend TypeScript Configuration"
Cohesion: 0.11
Nodes (18): compilerOptions, allowArbitraryExtensions, baseUrl, erasableSyntaxOnly, ignoreDeprecations, noFallthroughCasesInSwitch, noUnusedLocals, noUnusedParameters (+10 more)

### Community 4 - "Frontend Test Toolchain"
Cohesion: 0.12
Nodes (17): devDependencies, @playwright/test, @types/node, typescript, vite, @vitejs/plugin-vue, vitest, vue-tsc (+9 more)

### Community 5 - "Frontend Package Configuration"
Cohesion: 0.12
Nodes (15): dependencies, vue, vue-router, name, private, scripts, build, dev (+7 more)

### Community 6 - "Quarkus Session Resource"
Cohesion: 0.29
Nodes (9): SessionResource, SessionResponse, Authenticated, GET, JsonWebToken, Path, PermitAll, Response (+1 more)

### Community 7 - "Graphify Community Labels"
Cohesion: 0.17
Nodes (12): Automatic Label Reuse Invalidation, Cluster-Only Workflow, Clustered Communities, Community Label Quality Gate, Community Labels, Community Membership Signatures, Current Community Summaries, Exact Community-Key Coverage (+4 more)

### Community 8 - "DICOMweb Viewer Architecture"
Cohesion: 0.17
Nodes (12): DICOMweb Verbs, QIDO-RS, STOW-RS, WADO-RS, CSRF Protection, Quarkus BFF, Quarkus Domain Pack, Cornerstone3D Viewer (+4 more)

### Community 9 - "Commit Curator Workflow"
Cohesion: 0.17
Nodes (10): Commit Curator Implementation Plan, Global Constraints, Task 1: Domain Pack e wrappers de commit, Arquitetura, Commit Curator — Design, Comportamento, Limites, Modelos (+2 more)

### Community 10 - "Specialized DICOM Agents"
Cohesion: 0.18
Nodes (11): Human Business Gate, DICOM Domain Reviewer, DICOM Semantics, DICOMweb Conventions, Human Semantic Gate, Cornerstone3D Viewer, DICOM Viewer Frontend Agent, Vue Conventions (+3 more)

### Community 11 - "Maven Wrapper Script"
Cohesion: 0.33
Nodes (6): mvnw script, clean(), die(), exec_maven(), set_java_home(), verbose()

### Community 12 - "BlackICE Service Topology"
Cohesion: 0.20
Nodes (10): In-Place Keycloak Realm Rename, Backend BFF Service, OIDC Frontchannel Backchannel Split, DCM4CHEE Archive Service, DCM4CHEE Archive PostgreSQL Service, Keycloak Service, LDAP Service, Keycloak MariaDB Service (+2 more)

### Community 13 - "Windows Keycloak Bootstrap"
Cohesion: 0.25
Nodes (7): Bootstrap do Keycloak no Windows — Design, Decisão, Erros e segurança, Fluxo, Fora de escopo, Objetivo, Testes

### Community 14 - "DICOM Human Review"
Cohesion: 0.29
Nodes (7): DICOM Domain Review, Human Semantic Gate, DICOMweb Backend Boundary, BlackICE Quarkus Backend, Cornerstone3D Viewer, Vue Reactivity Boundary, Thin Skill Pattern

### Community 15 - "DICOM Identity Hierarchy"
Cohesion: 0.29
Nodes (7): DICOM Domain Pack, DICOM Domain Reviewer, DICOM Patient Study Series Instance Hierarchy, DICOM UID Identity, StudyInstanceUID, Report Model, Domain Pack Wrappers

### Community 16 - "Graphify Commit Policy"
Cohesion: 0.29
Nodes (7): Commit Policy, Graph Synchronization Commit, Graphify Commit Prerequisite, Safe Commit Scope, Canonical Graphify Skill Hashes, Graphify 0.9.32, Linked Worktree Manual Graph Update

### Community 17 - "Keycloak Same-Origin Plan"
Cohesion: 0.29
Nodes (6): Estrutura de arquivos, Global Constraints, Keycloak same-origin — Implementation Plan, Self-review, Task 1: Fase 1 — Keycloak same-origin sob `/auth`, Task 2: Fase 2 — renomear o realm para `blackice`

### Community 18 - "Commit Domain Wrappers"
Cohesion: 0.33
Nodes (6): Domain Packs, Canonical Commit Body, Commit Conventions Domain Pack, Claude Commit Curator, Codex Commit Curator, GPT-5.6 Luna Model

### Community 19 - "Keycloak Login Tests"
Cohesion: 0.53
Nodes (5): expectVerticallyCentered(), openBlackiceLogin(), requiredBox(), screenshotLoginCard(), showInvalidCredentials()

### Community 20 - "Quarkus OIDC Configuration"
Cohesion: 0.40
Nodes (5): Encrypted HttpOnly Session Cookie, Keycloak BlackICE Realm Endpoint, OIDC BFF Configuration, PKCE S256 Requirement, Realm Role Mapping

### Community 21 - "Quarkus Session Tests"
Cohesion: 0.60
Nodes (3): SessionResourceTest, QuarkusTest, Test

### Community 22 - "Session Graph Query"
Cohesion: 0.40
Nodes (4): Answer, Outcome, Q: Como o frontend Vue obtém a sessão autenticada do backend?, Source Nodes

### Community 23 - "DICOM Semantics Query"
Cohesion: 0.40
Nodes (4): Answer, Outcome, Q: Quais regras do BlackICE governam StudyInstanceUID e os verbos DICOMweb?, Source Nodes

### Community 24 - "Graphify Location Query"
Cohesion: 0.40
Nodes (4): Answer, Outcome, Q: Onde ficam as instruções operacionais do Graphify no BlackICE?, Source Nodes

### Community 25 - "BlackICE Login Theme"
Cohesion: 0.40
Nodes (5): BlackICE Login Theme, keycloak.v2 Parent Theme, BlackICE Login Wordmark Messages, BlackICE Login Visual Tokens, BlackICE Theme Configuration

### Community 26 - "Login Theme Regression"
Cohesion: 0.50
Nodes (4): BlackICE Keycloak login theme, Playwright Keycloak theme UI regression, keycloak.v2 child theme, Hybrid UI regression strategy

### Community 27 - "Windows Bootstrap Plan"
Cohesion: 0.50
Nodes (3): Bootstrap do Keycloak no Windows Implementation Plan, Global Constraints, Task 1: Launchers multiplataforma com núcleo único

### Community 28 - "Shared Audience Authorization"
Cohesion: 0.67
Nodes (4): arc-audience Mapper, blackice-quarkus Confidential BFF Client, BlackICE Shared Realm, dcm4chee-arc-rs DICOMweb Client

### Community 29 - "Docker API Proxy"
Cohesion: 0.50
Nodes (4): Read-only Docker API Proxy, Docker Daemon Socket, Read-only Docker Discovery Endpoints, Traefik Docker Provider

### Community 30 - "Graphify Project Architecture"
Cohesion: 0.67
Nodes (3): Post-Commit Graph Hook, Graphify Architecture, Project-Scoped Graphify Setup

### Community 31 - "Graphify Incremental Pipeline"
Cohesion: 0.67
Nodes (3): Graphify Pipeline, Incremental Update, Semantic Extraction

### Community 32 - "Vue Nginx Delivery"
Cohesion: 0.67
Nodes (3): Nginx Static SPA Server, Vue Router History Fallback, Vue SPA Build

### Community 34 - "Incremental Merge Runbook"
Cohesion: 0.67
Nodes (3): Cluster-only refresh, Incremental re-extraction runbook, Incremental merge integrity

### Community 35 - "Graphify Project Integration"
Cohesion: 0.67
Nodes (3): Graphify Project Integration, Incremental Graph Maintenance, Graphify Integration Design

### Community 36 - "Keycloak PKCE Migration"
Cohesion: 0.67
Nodes (3): Authorization Code with PKCE, Keycloak Same-Origin Migration, Keycloak Same-Origin Design

### Community 37 - "DICOMweb Role Authorization"
Cohesion: 0.67
Nodes (3): Shared Realm Audience, auth Realm Role, DICOMweb Authorization

## Knowledge Gaps
- **225 isolated node(s):** `dev.blackice:blackice-backend`, `name`, `private`, `version`, `type` (+220 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **69 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `devDependencies` connect `Frontend Test Toolchain` to `Frontend Package Configuration`?**
  _High betweenness centrality (0.004) - this node is a cross-community bridge._
- **What connects `dev.blackice:blackice-backend`, `name`, `private` to the rest of the system?**
  _225 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Keycloak Same-Origin Migration` be split into smaller, more focused modules?**
  _Cohesion score 0.09090909090909091 - nodes in this community are weakly interconnected._
- **Should `Manual DICOM Import` be split into smaller, more focused modules?**
  _Cohesion score 0.12631578947368421 - nodes in this community are weakly interconnected._
- **Should `Node TypeScript Configuration` be split into smaller, more focused modules?**
  _Cohesion score 0.1 - nodes in this community are weakly interconnected._
- **Should `Frontend TypeScript Configuration` be split into smaller, more focused modules?**
  _Cohesion score 0.10526315789473684 - nodes in this community are weakly interconnected._
- **Should `Frontend Test Toolchain` be split into smaller, more focused modules?**
  _Cohesion score 0.11764705882352941 - nodes in this community are weakly interconnected._