# Graph Report - BlackICE  (2026-08-22)

## Corpus Check
- 96 files · ~72,613 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 468 nodes · 376 edges · 123 communities (51 shown, 72 thin omitted)
- Extraction: 98% EXTRACTED · 2% INFERRED · 0% AMBIGUOUS · INFERRED: 8 edges (avg confidence: 0.86)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `cf4692a9`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- Keycloak Same Origin Design
- BlackICE Architecture Principles
- Node TypeScript Configuration
- Frontend TypeScript Configuration
- Frontend Development Dependencies
- Commit Curator Architecture
- Frontend Package Configuration
- Backend Session Endpoint
- Graphify Label Validation
- DICOMweb Application Foundation
- Human Business Gate
- Maven Wrapper Script
- Same Origin Service Topology
- Authenticated DICOM Ingestion
- Windows Keycloak Bootstrap Design
- Domain Pack Boundaries
- DICOM Identity Domain Pack
- Graphify Commit Synchronization
- Same Origin Implementation Plan
- Commit Conventions Domain Pack
- Keycloak Login E2E Helpers
- Cross Platform Keycloak Bootstrap
- OIDC BFF Session Security
- Session Endpoint Test
- Frontend Session Query
- DICOM UID Query
- Graphify Operations Query
- BlackICE Login Theme
- Login Theme Regression Tests
- Commit Curator Implementation Plan
- Windows Bootstrap Implementation Plan
- Keycloak Client Configuration
- Read Only Docker Proxy
- Graphify Project Setup
- Graphify Processing Pipeline
- Vue SPA Nginx Serving
- TypeScript Project References
- Graphify Incremental Integrity
- Graphify Integration Design
- PKCE Same Origin Migration
- DICOMweb Realm Authorization
- Graphify Watch Ingestion
- Graphify Export Server
- Graph Extraction Quality
- Graph Query Expansion
- Incremental Graph Replacement
- Quarkus Container Artifacts
- Maven Wrapper Distribution
- Backend BFF Session
- Playwright Test Configuration
- BlackICE Favicon Asset
- Vue Router Module
- Home Page User
- Session Response Types
- Multi Agent Limits
- Secure DCM4CHEE Deployment
- DICOM Pixel Preservation
- Git Domain Convention
- DCM4CHEE Security Stack
- Compose Stack Structure
- Feature First Architecture
- Keyboard Focus Accessibility
- Traefik Docker Discovery
- Container Realm Configuration
- POSIX Realm Configuration
- Cross Repository Merge
- Media Content Transcription
- Backend Ignore Configuration
- Java Runtime Toolchain
- Maven Build Toolchain
- Quarkus JVM Image
- Quarkus Legacy Image
- Quarkus Native Image
- Frontend Docker Context
- Frontend Ignore Configuration
- Vue Application Mount
- Node Runtime Toolchain
- Bluesky Social Icon
- Discord Social Icon
- Documentation Social Icon
- GitHub Social Icon
- Generic Social Icon
- X Social Icon
- Keycloak Login Contract
- Claude Code Instructions
- Graphify Add Watch Guide
- Graphify Export Guide
- Graphify Semantic Extraction
- Graphify Merge Guide
- Graphify Hook Guide
- Graphify Query Guide
- Graphify Transcription Guide
- Graphify Output Exclusions
- BlackICE Monorepo Layout
- OIDC User Token Propagation
- Patient Identifier Issuer
- Vue BFF Authentication
- Vue Feature Conventions
- Historical Project Plans
- Codex Agent Configuration
- PACS Architecture Decisions
- Graphify Merge Driver
- Graphify Local Metadata
- Graphify Recursion Exclusion
- Frontend SPA Container
- Product PostgreSQL Database
- BlackICE Backend Artifact
- BlackICE MVP Scope
- Decisões
- Global Constraints
- Agentes Antigravity — desenho
- Global Constraints
- dicom-domain-reviewer/agent.md
- dicom-viewer-frontend/agent.md
- quarkus-backend/agent.md
- commit-curator/agent.md

## God Nodes (most connected - your core abstractions)
1. `compilerOptions` - 15 edges
2. `compilerOptions` - 11 edges
3. `BlackICE — Login same-origin: tirar o Keycloak da barra de endereços` - 10 edges
4. `Commit Curator Design` - 9 edges
5. `Fase 1 — Same-origin atrás do Traefik` - 8 edges
6. `Manual DICOM Import` - 8 edges
7. `Authenticated DICOM Ingest Flow` - 8 edges
8. `Bootstrap do Keycloak no Windows — Design` - 7 edges
9. `SessionResource` - 6 edges
10. `scripts` - 6 edges

## Surprising Connections (you probably didn't know these)
- `Graphify Commit Prerequisite` --conceptually_related_to--> `Linked Worktree Manual Graph Update`  [INFERRED]
  docs/domains/git/commit-conventions.md → .graphify/setup.ps1
- `DICOM UID Validation` --semantically_similar_to--> `DICOM Identity Invariants`  [INFERRED] [semantically similar]
  docs/superpowers/specs/2026-08-09-manual-dicom-import-design.md → AGENTS.md
- `DCM4CHEE 5.34.3 Baseline` --conceptually_related_to--> `DCM4CHEE Secure Stack`  [INFERRED]
  docs/superpowers/plans/2026-07-23-dcm4chee-5-34-3.md → infra/dcm4chee/README.md
- `In-Place Keycloak Realm Rename` --rationale_for--> `Keycloak Service`  [EXTRACTED]
  docs/superpowers/plans/2026-08-07-keycloak-same-origin.md → infra/dcm4chee/compose.yml
- `Feature Colocation` --conceptually_related_to--> `Quarkus Product Backend`  [EXTRACTED]
  docs/architecture/project-structure.md → AGENTS.md

## Import Cycles
- None detected.

## Hyperedges (group relationships)
- **Commit Curator Domain Pack Pattern** — docs_superpowers_plans_2026_08_09_commit_curator_commit_curator_agent, docs_superpowers_plans_2026_08_09_commit_curator_git_domain_pack, docs_superpowers_plans_2026_08_09_commit_curator_branch_commit_policy, docs_superpowers_plans_2026_08_09_commit_curator_graphify_commit_prerequisite [EXTRACTED 1.00]
- **Keycloak Cross-platform Configuration Flow** — docs_superpowers_plans_2026_08_09_keycloak_windows_bootstrap_shared_posix_configuration_core, docs_superpowers_plans_2026_08_09_keycloak_windows_bootstrap_cross_platform_launchers, docs_superpowers_plans_2026_08_09_keycloak_windows_bootstrap_three_file_compose_configuration, docs_superpowers_plans_2026_08_09_keycloak_windows_bootstrap_keycloak_admin_rest [EXTRACTED 1.00]
- **Manual DICOM Ingest BFF Flow** — docs_superpowers_plans_2026_08_09_manual_dicom_import_authenticated_dicom_ingest_flow, docs_superpowers_plans_2026_08_09_manual_dicom_import_frontend_ingest_workflow, docs_superpowers_plans_2026_08_09_manual_dicom_import_metadata_validation_and_study_grouping, docs_superpowers_plans_2026_08_09_manual_dicom_import_streaming_stow_rs_gateway, docs_superpowers_plans_2026_08_09_manual_dicom_import_csrf_and_oidc_protection [EXTRACTED 1.00]
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

## Communities (123 total, 72 thin omitted)

### Community 0 - "Keycloak Same Origin Design"
Cohesion: 0.09
Nodes (21): Alternativas descartadas, `apps/backend/src/main/resources/application.properties`, BlackICE — Login same-origin: tirar o Keycloak da barra de endereços, Custo honesto, Custo honesto (revisado), Decisões, Estado atual (verificado em 2026-08-07, stack no ar), Fase 1 — Same-origin atrás do Traefik (+13 more)

### Community 1 - "BlackICE Architecture Principles"
Cohesion: 0.13
Nodes (20): BlackICE PACS, DCM4CHEE Archive, DICOM Identity Invariants, DICOMweb Operations, Keycloak OIDC SSO, Quarkus Product Backend, Vue Authenticated SPA, EVO-001 Async Ingestion (+12 more)

### Community 2 - "Node TypeScript Configuration"
Cohesion: 0.10
Nodes (19): compilerOptions, allowImportingTsExtensions, erasableSyntaxOnly, lib, module, moduleDetection, noEmit, noFallthroughCasesInSwitch (+11 more)

### Community 3 - "Frontend TypeScript Configuration"
Cohesion: 0.11
Nodes (18): compilerOptions, allowArbitraryExtensions, baseUrl, erasableSyntaxOnly, ignoreDeprecations, noFallthroughCasesInSwitch, noUnusedLocals, noUnusedParameters (+10 more)

### Community 4 - "Frontend Development Dependencies"
Cohesion: 0.12
Nodes (17): devDependencies, @playwright/test, @types/node, typescript, vite, @vitejs/plugin-vue, vitest, vue-tsc (+9 more)

### Community 5 - "Commit Curator Architecture"
Cohesion: 0.13
Nodes (16): Branch Commit Policy, Commit Curator Agent, Git Domain Pack, Graphify Commit Prerequisite, Arquitetura, Branch-scoped Commit Authorization, Commit Curator Design, Commit Message Format (+8 more)

### Community 6 - "Frontend Package Configuration"
Cohesion: 0.12
Nodes (15): dependencies, vue, vue-router, name, private, scripts, build, dev (+7 more)

### Community 7 - "Backend Session Endpoint"
Cohesion: 0.29
Nodes (9): SessionResource, SessionResponse, Authenticated, GET, JsonWebToken, Path, PermitAll, Response (+1 more)

### Community 8 - "Graphify Label Validation"
Cohesion: 0.17
Nodes (12): Automatic Label Reuse Invalidation, Cluster-Only Workflow, Clustered Communities, Community Label Quality Gate, Community Labels, Community Membership Signatures, Current Community Summaries, Exact Community-Key Coverage (+4 more)

### Community 9 - "DICOMweb Application Foundation"
Cohesion: 0.17
Nodes (12): DICOMweb Verbs, QIDO-RS, STOW-RS, WADO-RS, CSRF Protection, Quarkus BFF, Quarkus Domain Pack, Cornerstone3D Viewer (+4 more)

### Community 11 - "Maven Wrapper Script"
Cohesion: 0.33
Nodes (6): mvnw script, clean(), die(), exec_maven(), set_java_home(), verbose()

### Community 12 - "Same Origin Service Topology"
Cohesion: 0.20
Nodes (10): In-Place Keycloak Realm Rename, Backend BFF Service, OIDC Frontchannel Backchannel Split, DCM4CHEE Archive Service, DCM4CHEE Archive PostgreSQL Service, Keycloak Service, LDAP Service, Keycloak MariaDB Service (+2 more)

### Community 13 - "Authenticated DICOM Ingestion"
Cohesion: 0.20
Nodes (10): Authenticated DICOM Ingest Flow, CSRF and OIDC Protection, DICOM Domain Review Gate, DICOM UID Preservation, Durable Jobs EVO-001, Frontend Ingest Workflow, Manual DICOM Import, Metadata Validation and Study Grouping (+2 more)

### Community 14 - "Windows Keycloak Bootstrap Design"
Cohesion: 0.25
Nodes (7): Bootstrap do Keycloak no Windows — Design, Decisão, Erros e segurança, Fluxo, Fora de escopo, Objetivo, Testes

### Community 15 - "Domain Pack Boundaries"
Cohesion: 0.29
Nodes (7): DICOM Domain Review, Human Semantic Gate, DICOMweb Backend Boundary, BlackICE Quarkus Backend, Cornerstone3D Viewer, Vue Reactivity Boundary, Thin Skill Pattern

### Community 16 - "DICOM Identity Domain Pack"
Cohesion: 0.29
Nodes (7): DICOM Domain Pack, DICOM Domain Reviewer, DICOM Patient Study Series Instance Hierarchy, DICOM UID Identity, StudyInstanceUID, Report Model, Domain Pack Wrappers

### Community 17 - "Graphify Commit Synchronization"
Cohesion: 0.29
Nodes (7): Commit Policy, Graph Synchronization Commit, Graphify Commit Prerequisite, Safe Commit Scope, Canonical Graphify Skill Hashes, Graphify 0.9.32, Linked Worktree Manual Graph Update

### Community 18 - "Same Origin Implementation Plan"
Cohesion: 0.29
Nodes (6): Estrutura de arquivos, Global Constraints, Keycloak same-origin — Implementation Plan, Self-review, Task 1: Fase 1 — Keycloak same-origin sob `/auth`, Task 2: Fase 2 — renomear o realm para `blackice`

### Community 19 - "Commit Conventions Domain Pack"
Cohesion: 0.50
Nodes (4): Domain Packs, Canonical Commit Body, Commit Conventions Domain Pack, Claude Commit Curator

### Community 20 - "Keycloak Login E2E Helpers"
Cohesion: 0.53
Nodes (5): expectVerticallyCentered(), openBlackiceLogin(), requiredBox(), screenshotLoginCard(), showInvalidCredentials()

### Community 21 - "Cross Platform Keycloak Bootstrap"
Cohesion: 0.33
Nodes (6): Cross-platform Launchers, Keycloak Admin REST, Keycloak Windows Bootstrap, Pester Contract Test, Shared POSIX Configuration Core, Three-file Compose Configuration

### Community 22 - "OIDC BFF Session Security"
Cohesion: 0.40
Nodes (5): Encrypted HttpOnly Session Cookie, Keycloak BlackICE Realm Endpoint, OIDC BFF Configuration, PKCE S256 Requirement, Realm Role Mapping

### Community 23 - "Session Endpoint Test"
Cohesion: 0.60
Nodes (3): SessionResourceTest, QuarkusTest, Test

### Community 24 - "Frontend Session Query"
Cohesion: 0.40
Nodes (4): Answer, Outcome, Q: Como o frontend Vue obtém a sessão autenticada do backend?, Source Nodes

### Community 25 - "DICOM UID Query"
Cohesion: 0.40
Nodes (4): Answer, Outcome, Q: Quais regras do BlackICE governam StudyInstanceUID e os verbos DICOMweb?, Source Nodes

### Community 26 - "Graphify Operations Query"
Cohesion: 0.40
Nodes (4): Answer, Outcome, Q: Onde ficam as instruções operacionais do Graphify no BlackICE?, Source Nodes

### Community 27 - "BlackICE Login Theme"
Cohesion: 0.40
Nodes (5): BlackICE Login Theme, keycloak.v2 Parent Theme, BlackICE Login Wordmark Messages, BlackICE Login Visual Tokens, BlackICE Theme Configuration

### Community 28 - "Login Theme Regression Tests"
Cohesion: 0.50
Nodes (4): BlackICE Keycloak login theme, Playwright Keycloak theme UI regression, keycloak.v2 child theme, Hybrid UI regression strategy

### Community 29 - "Commit Curator Implementation Plan"
Cohesion: 0.50
Nodes (3): Commit Curator Implementation Plan, Global Constraints, Task 1: Domain Pack e wrappers de commit

### Community 30 - "Windows Bootstrap Implementation Plan"
Cohesion: 0.50
Nodes (3): Bootstrap do Keycloak no Windows Implementation Plan, Global Constraints, Task 1: Launchers multiplataforma com núcleo único

### Community 31 - "Keycloak Client Configuration"
Cohesion: 0.67
Nodes (4): arc-audience Mapper, blackice-quarkus Confidential BFF Client, BlackICE Shared Realm, dcm4chee-arc-rs DICOMweb Client

### Community 32 - "Read Only Docker Proxy"
Cohesion: 0.50
Nodes (4): Read-only Docker API Proxy, Docker Daemon Socket, Read-only Docker Discovery Endpoints, Traefik Docker Provider

### Community 33 - "Graphify Project Setup"
Cohesion: 0.67
Nodes (3): Post-Commit Graph Hook, Graphify Architecture, Project-Scoped Graphify Setup

### Community 34 - "Graphify Processing Pipeline"
Cohesion: 0.67
Nodes (3): Graphify Pipeline, Incremental Update, Semantic Extraction

### Community 35 - "Vue SPA Nginx Serving"
Cohesion: 0.67
Nodes (3): Nginx Static SPA Server, Vue Router History Fallback, Vue SPA Build

### Community 37 - "Graphify Incremental Integrity"
Cohesion: 0.67
Nodes (3): Cluster-only refresh, Incremental re-extraction runbook, Incremental merge integrity

### Community 38 - "Graphify Integration Design"
Cohesion: 0.67
Nodes (3): Graphify Project Integration, Incremental Graph Maintenance, Graphify Integration Design

### Community 39 - "PKCE Same Origin Migration"
Cohesion: 0.67
Nodes (3): Authorization Code with PKCE, Keycloak Same-Origin Migration, Keycloak Same-Origin Design

### Community 40 - "DICOMweb Realm Authorization"
Cohesion: 0.67
Nodes (3): Shared Realm Audience, auth Realm Role, DICOMweb Authorization

### Community 115 - "Decisões"
Cohesion: 0.20
Nodes (9): Alterações documentais previstas, Conhecimento neutro e wrappers, Decisões, Desenho — skill de autoria de agentes, Fora de escopo, Objetivo, Segurança, governança e validação, Seleção de modelo no momento da mudança (+1 more)

### Community 116 - "Global Constraints"
Cohesion: 0.29
Nodes (6): Agent Authoring Skill Implementation Plan, Global Constraints, Task 1: Criar o Domain Pack canônico, Task 2: Criar wrappers de descoberta mínimos, Task 3: Integrar a convenção à documentação do repositório, Task 4: Validar a skill e as modificações

### Community 117 - "Agentes Antigravity — desenho"
Cohesion: 0.29
Nodes (6): Agentes Antigravity — desenho, Configuração, Conhecimento e documentação, Escopo, Objetivo, Verificação

### Community 118 - "Global Constraints"
Cohesion: 0.33
Nodes (5): Antigravity Agents Implementation Plan, Global Constraints, Task 1: Criar os wrappers Antigravity, Task 2: Documentar o ponto de descoberta, Task 3: Confirmar descoberta pelo Antigravity

### Community 119 - "dicom-domain-reviewer/agent.md"
Cohesion: 0.50
Nodes (3): Antes de revisar, Papel, Revisão

## Knowledge Gaps
- **258 isolated node(s):** `dev.blackice:blackice-backend`, `name`, `private`, `version`, `type` (+253 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **72 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `devDependencies` connect `Frontend Development Dependencies` to `Frontend Package Configuration`?**
  _High betweenness centrality (0.003) - this node is a cross-community bridge._
- **What connects `dev.blackice:blackice-backend`, `name`, `private` to the rest of the system?**
  _258 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Keycloak Same Origin Design` be split into smaller, more focused modules?**
  _Cohesion score 0.09090909090909091 - nodes in this community are weakly interconnected._
- **Should `BlackICE Architecture Principles` be split into smaller, more focused modules?**
  _Cohesion score 0.12631578947368421 - nodes in this community are weakly interconnected._
- **Should `Node TypeScript Configuration` be split into smaller, more focused modules?**
  _Cohesion score 0.1 - nodes in this community are weakly interconnected._
- **Should `Frontend TypeScript Configuration` be split into smaller, more focused modules?**
  _Cohesion score 0.10526315789473684 - nodes in this community are weakly interconnected._
- **Should `Frontend Development Dependencies` be split into smaller, more focused modules?**
  _Cohesion score 0.11764705882352941 - nodes in this community are weakly interconnected._