# Graph Report - BlackICE  (2026-08-22)

## Corpus Check
- 193 files · ~109,116 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 1135 nodes · 1755 edges · 167 communities (91 shown, 76 thin omitted)
- Extraction: 90% EXTRACTED · 10% INFERRED · 0% AMBIGUOUS · INFERRED: 171 edges (avg confidence: 0.78)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `dca3e974`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- IngestResult
- Code
- .ingest
- devDependencies
- useWorklist.ts
- Convenções de autoria de agentes
- BlackICE — Login same-origin: tirar o Keycloak da barra de endereços
- StowResponseParser
- Manual DICOM Import
- useIngestBatch.ts
- compilerOptions
- compilerOptions
- Commit Curator Design
- SessionResource.java
- Community Label Quality Gate
- WorklistResourceTest
- DICOMweb Verbs
- FakeXHR
- Backend modular com Clean Architecture
- mvnw
- BackendArchitectureTest
- Keycloak Service
- Authenticated DICOM Ingest Flow
- Decisões
- IngestPage.vue
- synthetic-dicom.ts
- Global Constraints
- Bootstrap do Keycloak no Windows — Design
- DICOM Domain Review
- CsrfResource.java
- CsrfResourceTest.java
- ingest.types.ts
- DICOM Domain Pack
- Commit Policy
- Keycloak same-origin — Implementation Plan
- Global Constraints
- Agentes Antigravity — desenho
- worklist.api.spec.ts
- Cross-platform Launchers
- Global Constraints
- OIDC BFF Configuration
- SessionResourceTest.java
- Q: Como o frontend Vue obtém a sessão autenticada do backend?
- Q: Quais regras do BlackICE governam StudyInstanceUID e os verbos DICOMweb?
- Q: Onde ficam as instruções operacionais do Graphify no BlackICE?
- BlackICE Login Theme
- dicom-domain-reviewer/agent.md
- Commit Conventions Domain Pack
- BlackICE Keycloak login theme
- Commit Curator Implementation Plan
- Bootstrap do Keycloak no Windows Implementation Plan
- arc-audience Mapper
- Read-only Docker API Proxy
- Graphify Architecture
- Graphify Pipeline
- dicom-viewer-frontend/agent.md
- quarkus-backend/agent.md
- Nginx Static SPA Server
- useIngestBatch.spec.ts
- Incremental re-extraction runbook
- Graphify Project Integration
- Keycloak Same-Origin Migration
- DICOMweb Authorization
- Folder Watcher
- Graph Exports
- Confidence Rubric
- Graph Query
- Incremental Re-extraction
- commit-curator/agent.md
- .agents/skills/agent-authoring/SKILL.md
- Backend Container Build Artifacts
- Apache Maven 3.9.16 Distribution
- Backend BFF
- playwright.config.ts
- BlackICE Favicon
- index.ts
- HomePage.vue
- IngestResult.vue
- session.types.ts
- .claude/skills/agent-authoring/SKILL.md
- Multi-Agent Feature
- DCM4CHEE 5.34.3 Baseline
- Pixel Data Preservation
- Git Domain Pack
- DCM4CHEE 5.34.3 Baseline
- Canonical three-file Compose configuration
- Feature-first monorepo structure
- Focus outline accessibility
- Docker API compatibility proxy
- configure-blackice.sh script
- configure-blackice-container.sh
- Cross-Repository Graph Merge
- Media Transcription
- Human Business Gate
- Backend Ignore Rules
- Temurin Java 21 Toolchain
- Maven 3 Toolchain
- Quarkus JVM Container
- Quarkus Legacy JAR Container
- Quarkus Native Micro Container
- Frontend Docker Context Rules
- Frontend Ignore Rules
- Vue Mount Point
- Node 24 Toolchain
- Bluesky icon
- Discord icon
- Documentation icon
- GitHub icon
- Social icon
- X icon
- Keycloak Login E2E Contract
- Claude Code Guidance
- Graphify Add and Watch
- Graphify Exports
- Semantic Extraction
- Cross-repository Merge
- Graphify Hooks
- Graphify Query
- Graphify Transcription
- Graphify Output Exclusion
- BlackICE Monorepo Structure
- User Token Propagation
- PatientID with IssuerOfPatientID
- Vue BFF Session
- Vue Feature-First Conventions
- Historical Specs and Plans
- Codex Subagent Configuration
- BlackICE PACS Architecture Decisions
- Graphify Merge Driver
- Graphify Local State
- Graphify Output Recursion Exclusion
- Frontend SPA Service
- Product PostgreSQL database
- dev.blackice:blackice-backend
- BlackICE MVP
- StudySearchRequest
- HttpQidoStudyGatewayTest
- .parse
- Worklist e busca via QIDO-RS — design do MVP #2
- Phase 1 — Backend QIDO vertical slice
- useWorklist.spec.ts

## God Nodes (most connected - your core abstractions)
1. `StudySearchRequest` - 28 edges
2. `ValidatedDicom` - 24 edges
3. `UploadedDicom` - 23 edges
4. `IngestResult` - 20 edges
5. `files` - 20 edges
6. `WorklistResourceTest` - 19 edges
7. `HttpQidoStudyGatewayTest` - 19 edges
8. `IngestStudiesUseCase` - 18 edges
9. `StudySummary` - 18 edges
10. `QidoStudyResponseParser` - 18 edges

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

## Communities (167 total, 76 thin omitted)

### Community 0 - "IngestResult"
Cohesion: 0.07
Nodes (35): IngestHttpStatusResolver, IngestResource, Blocking, Inject, Logger, Path, Produces, Response (+27 more)

### Community 1 - "Code"
Cohesion: 0.20
Nodes (10): RejectedFile, Code, DUPLICATE_IDENTICAL, MALFORMED_DICOM, MISSING_SERIES_INSTANCE_UID, MISSING_SOP_CLASS_UID, MISSING_SOP_INSTANCE_UID, MISSING_STUDY_INSTANCE_UID (+2 more)

### Community 2 - ".ingest"
Cohesion: 0.06
Nodes (41): ArchiveUnavailableException, Reason, CONNECTION, HTTP_STATUS, INTERRUPTED, TIMEOUT, UploadedDicom, DicomArchiveGateway (+33 more)

### Community 3 - "devDependencies"
Cohesion: 0.05
Nodes (39): dependencies, vue, vue-router, devDependencies, jsdom, @playwright/test, @types/node, typescript (+31 more)

### Community 4 - "useWorklist.ts"
Cohesion: 0.06
Nodes (25): emit, EMPTY_FILTERS, PAGE_SIZE, SearchStudies, useWorklist(), WorklistComposable, WorklistPhase, safeError() (+17 more)

### Community 5 - "Convenções de autoria de agentes"
Cohesion: 0.09
Nodes (21): Convenções de autoria de agentes, Escopo e autorização, Fonte única de verdade, Forma de um wrapper, Fronteiras, Classificação e roteamento, Pesquisa obrigatória, Proposta e escalonamento (+13 more)

### Community 6 - "BlackICE — Login same-origin: tirar o Keycloak da barra de endereços"
Cohesion: 0.09
Nodes (21): Alternativas descartadas, `apps/backend/src/main/resources/application.properties`, BlackICE — Login same-origin: tirar o Keycloak da barra de endereços, Custo honesto, Custo honesto (revisado), Decisões, Estado atual (verificado em 2026-08-07, stack no ar), Fase 1 — Same-origin atrás do Traefik (+13 more)

### Community 7 - "StowResponseParser"
Cohesion: 0.15
Nodes (12): HttpDicomArchiveGateway, ApplicationScoped, HttpClient, Inject, ApplicationScoped, Inject, JsonNode, ObjectMapper (+4 more)

### Community 8 - "Manual DICOM Import"
Cohesion: 0.13
Nodes (20): BlackICE PACS, DCM4CHEE Archive, DICOM Identity Invariants, DICOMweb Operations, Keycloak OIDC SSO, Quarkus Product Backend, Vue Authenticated SPA, EVO-001 Async Ingestion (+12 more)

### Community 9 - "useIngestBatch.ts"
Cohesion: 0.14
Nodes (12): fetchCsrfToken(), readCookie(), UploadError, uploadStudies(), XhrFactory, DEFAULT_API, DEFAULT_LIMITS, IngestApi (+4 more)

### Community 10 - "compilerOptions"
Cohesion: 0.10
Nodes (19): compilerOptions, allowImportingTsExtensions, erasableSyntaxOnly, lib, module, moduleDetection, noEmit, noFallthroughCasesInSwitch (+11 more)

### Community 11 - "compilerOptions"
Cohesion: 0.11
Nodes (18): compilerOptions, allowArbitraryExtensions, baseUrl, erasableSyntaxOnly, ignoreDeprecations, noFallthroughCasesInSwitch, noUnusedLocals, noUnusedParameters (+10 more)

### Community 12 - "Commit Curator Design"
Cohesion: 0.13
Nodes (16): Branch Commit Policy, Commit Curator Agent, Git Domain Pack, Graphify Commit Prerequisite, Arquitetura, Branch-scoped Commit Authorization, Commit Curator Design, Commit Message Format (+8 more)

### Community 13 - "SessionResource.java"
Cohesion: 0.29
Nodes (9): Authenticated, GET, Path, Response, SessionResource, SessionResponse, JsonWebToken, PermitAll (+1 more)

### Community 14 - "Community Label Quality Gate"
Cohesion: 0.17
Nodes (12): Automatic Label Reuse Invalidation, Cluster-Only Workflow, Clustered Communities, Community Label Quality Gate, Community Labels, Community Membership Signatures, Current Community Summaries, Exact Community-Key Coverage (+4 more)

### Community 15 - "WorklistResourceTest"
Cohesion: 0.06
Nodes (36): AccessTokenCredential, AccessTokenProvider, CurrentAccessToken, ApplicationScoped, Override, WorklistErrorResponse, Blocking, GET (+28 more)

### Community 16 - "DICOMweb Verbs"
Cohesion: 0.17
Nodes (12): DICOMweb Verbs, QIDO-RS, STOW-RS, WADO-RS, CSRF Protection, Quarkus BFF, Quarkus Domain Pack, Cornerstone3D Viewer (+4 more)

### Community 18 - "Backend modular com Clean Architecture"
Cohesion: 0.18
Nodes (10): Backend modular com Clean Architecture, Critérios de aceite, Decisão, Documentação de código, Fora de escopo, Limites e direção de dependências, Migração do fluxo de ingestão, Módulos de sessão e segurança (+2 more)

### Community 19 - "mvnw"
Cohesion: 0.33
Nodes (6): mvnw script, clean(), die(), exec_maven(), set_java_home(), verbose()

### Community 20 - "BackendArchitectureTest"
Cohesion: 0.28
Nodes (3): BackendArchitectureTest, Test, JavaClasses

### Community 21 - "Keycloak Service"
Cohesion: 0.20
Nodes (10): In-Place Keycloak Realm Rename, Backend BFF Service, OIDC Frontchannel Backchannel Split, DCM4CHEE Archive Service, DCM4CHEE Archive PostgreSQL Service, Keycloak Service, LDAP Service, Keycloak MariaDB Service (+2 more)

### Community 22 - "Authenticated DICOM Ingest Flow"
Cohesion: 0.20
Nodes (10): Authenticated DICOM Ingest Flow, CSRF and OIDC Protection, DICOM Domain Review Gate, DICOM UID Preservation, Durable Jobs EVO-001, Frontend Ingest Workflow, Manual DICOM Import, Metadata Validation and Study Grouping (+2 more)

### Community 23 - "Decisões"
Cohesion: 0.20
Nodes (9): Alterações documentais previstas, Conhecimento neutro e wrappers, Decisões, Desenho — skill de autoria de agentes, Fora de escopo, Objetivo, Segurança, governança e validação, Seleção de modelo no momento da mudança (+1 more)

### Community 24 - "IngestPage.vue"
Cohesion: 0.22
Nodes (4): batch, limitWarning, totalBytes, totalFiles

### Community 25 - "synthetic-dicom.ts"
Cohesion: 0.40
Nodes (7): createSyntheticDicom(), element(), LONG_VR, paddedText(), SyntheticDicomMetadata, text(), us()

### Community 26 - "Global Constraints"
Cohesion: 0.25
Nodes (7): Backend Modular Clean Architecture Implementation Plan, Global Constraints, Task 1: Criar a guarda arquitetural que falha contra a estrutura atual, Task 2: Migrar ingestão para portas e adaptadores, deixando a guarda verde, Task 3: Documentar as fronteiras no código sem comentários redundantes, Task 4: Atualizar a documentação operacional e fechar a verificação, Task 5: Detalhar o módulo de ingestão por responsabilidade

### Community 27 - "Bootstrap do Keycloak no Windows — Design"
Cohesion: 0.25
Nodes (7): Bootstrap do Keycloak no Windows — Design, Decisão, Erros e segurança, Fluxo, Fora de escopo, Objetivo, Testes

### Community 28 - "DICOM Domain Review"
Cohesion: 0.29
Nodes (7): DICOM Domain Review, Human Semantic Gate, DICOMweb Backend Boundary, BlackICE Quarkus Backend, Cornerstone3D Viewer, Vue Reactivity Boundary, Thin Skill Pattern

### Community 29 - "CsrfResource.java"
Cohesion: 0.48
Nodes (5): CsrfResource, Authenticated, GET, Path, Response

### Community 30 - "CsrfResourceTest.java"
Cohesion: 0.48
Nodes (4): CsrfResourceTest, QuarkusTest, Test, TestSecurity

### Community 31 - "ingest.types.ts"
Cohesion: 0.29
Nodes (5): IngestOutcome, IngestResponse, InstanceStatus, StudyStatus, UploadHandle

### Community 32 - "DICOM Domain Pack"
Cohesion: 0.29
Nodes (7): DICOM Domain Pack, DICOM Domain Reviewer, DICOM Patient Study Series Instance Hierarchy, DICOM UID Identity, StudyInstanceUID, Report Model, Domain Pack Wrappers

### Community 33 - "Commit Policy"
Cohesion: 0.29
Nodes (7): Commit Policy, Graph Synchronization Commit, Graphify Commit Prerequisite, Safe Commit Scope, Canonical Graphify Skill Hashes, Graphify 0.9.32, Linked Worktree Manual Graph Update

### Community 34 - "Keycloak same-origin — Implementation Plan"
Cohesion: 0.29
Nodes (6): Estrutura de arquivos, Global Constraints, Keycloak same-origin — Implementation Plan, Self-review, Task 1: Fase 1 — Keycloak same-origin sob `/auth`, Task 2: Fase 2 — renomear o realm para `blackice`

### Community 35 - "Global Constraints"
Cohesion: 0.29
Nodes (6): Agent Authoring Skill Implementation Plan, Global Constraints, Task 1: Criar o Domain Pack canônico, Task 2: Criar wrappers de descoberta mínimos, Task 3: Integrar a convenção à documentação do repositório, Task 4: Validar a skill e as modificações

### Community 36 - "Agentes Antigravity — desenho"
Cohesion: 0.29
Nodes (6): Agentes Antigravity — desenho, Configuração, Conhecimento e documentação, Escopo, Objetivo, Verificação

### Community 37 - "worklist.api.spec.ts"
Cohesion: 0.22
Nodes (8): expectVerticallyCentered(), openBlackiceLogin(), requiredBox(), screenshotLoginCard(), showInvalidCredentials(), emptyFilters, emptyParams, page

### Community 38 - "Cross-platform Launchers"
Cohesion: 0.33
Nodes (6): Cross-platform Launchers, Keycloak Admin REST, Keycloak Windows Bootstrap, Pester Contract Test, Shared POSIX Configuration Core, Three-file Compose Configuration

### Community 39 - "Global Constraints"
Cohesion: 0.33
Nodes (5): Antigravity Agents Implementation Plan, Global Constraints, Task 1: Criar os wrappers Antigravity, Task 2: Documentar o ponto de descoberta, Task 3: Confirmar descoberta pelo Antigravity

### Community 40 - "OIDC BFF Configuration"
Cohesion: 0.40
Nodes (5): Encrypted HttpOnly Session Cookie, Keycloak BlackICE Realm Endpoint, OIDC BFF Configuration, PKCE S256 Requirement, Realm Role Mapping

### Community 41 - "SessionResourceTest.java"
Cohesion: 0.60
Nodes (3): QuarkusTest, Test, SessionResourceTest

### Community 42 - "Q: Como o frontend Vue obtém a sessão autenticada do backend?"
Cohesion: 0.40
Nodes (4): Answer, Outcome, Q: Como o frontend Vue obtém a sessão autenticada do backend?, Source Nodes

### Community 43 - "Q: Quais regras do BlackICE governam StudyInstanceUID e os verbos DICOMweb?"
Cohesion: 0.40
Nodes (4): Answer, Outcome, Q: Quais regras do BlackICE governam StudyInstanceUID e os verbos DICOMweb?, Source Nodes

### Community 44 - "Q: Onde ficam as instruções operacionais do Graphify no BlackICE?"
Cohesion: 0.40
Nodes (4): Answer, Outcome, Q: Onde ficam as instruções operacionais do Graphify no BlackICE?, Source Nodes

### Community 45 - "BlackICE Login Theme"
Cohesion: 0.40
Nodes (5): BlackICE Login Theme, keycloak.v2 Parent Theme, BlackICE Login Wordmark Messages, BlackICE Login Visual Tokens, BlackICE Theme Configuration

### Community 46 - "dicom-domain-reviewer/agent.md"
Cohesion: 0.50
Nodes (3): Antes de revisar, Papel, Revisão

### Community 47 - "Commit Conventions Domain Pack"
Cohesion: 0.50
Nodes (4): Domain Packs, Canonical Commit Body, Commit Conventions Domain Pack, Claude Commit Curator

### Community 48 - "BlackICE Keycloak login theme"
Cohesion: 0.50
Nodes (4): BlackICE Keycloak login theme, Playwright Keycloak theme UI regression, keycloak.v2 child theme, Hybrid UI regression strategy

### Community 49 - "Commit Curator Implementation Plan"
Cohesion: 0.50
Nodes (3): Commit Curator Implementation Plan, Global Constraints, Task 1: Domain Pack e wrappers de commit

### Community 50 - "Bootstrap do Keycloak no Windows Implementation Plan"
Cohesion: 0.50
Nodes (3): Bootstrap do Keycloak no Windows Implementation Plan, Global Constraints, Task 1: Launchers multiplataforma com núcleo único

### Community 51 - "arc-audience Mapper"
Cohesion: 0.67
Nodes (4): arc-audience Mapper, blackice-quarkus Confidential BFF Client, BlackICE Shared Realm, dcm4chee-arc-rs DICOMweb Client

### Community 52 - "Read-only Docker API Proxy"
Cohesion: 0.50
Nodes (4): Read-only Docker API Proxy, Docker Daemon Socket, Read-only Docker Discovery Endpoints, Traefik Docker Provider

### Community 53 - "Graphify Architecture"
Cohesion: 0.67
Nodes (3): Post-Commit Graph Hook, Graphify Architecture, Project-Scoped Graphify Setup

### Community 54 - "Graphify Pipeline"
Cohesion: 0.67
Nodes (3): Graphify Pipeline, Incremental Update, Semantic Extraction

### Community 57 - "Nginx Static SPA Server"
Cohesion: 0.67
Nodes (3): Nginx Static SPA Server, Vue Router History Fallback, Vue SPA Build

### Community 59 - "Incremental re-extraction runbook"
Cohesion: 0.67
Nodes (3): Cluster-only refresh, Incremental re-extraction runbook, Incremental merge integrity

### Community 60 - "Graphify Project Integration"
Cohesion: 0.67
Nodes (3): Graphify Project Integration, Incremental Graph Maintenance, Graphify Integration Design

### Community 61 - "Keycloak Same-Origin Migration"
Cohesion: 0.67
Nodes (3): Authorization Code with PKCE, Keycloak Same-Origin Migration, Keycloak Same-Origin Design

### Community 62 - "DICOMweb Authorization"
Cohesion: 0.67
Nodes (3): Shared Realm Audience, auth Realm Role, DICOMweb Authorization

### Community 153 - "StudySearchRequest"
Cohesion: 0.11
Nodes (17): InvalidStudySearchException, StudySearchRequest, HttpQidoStudyGateway, ApplicationScoped, HttpClient, Inject, Override, ApplicationScoped (+9 more)

### Community 154 - "HttpQidoStudyGatewayTest"
Cohesion: 0.16
Nodes (14): ArchiveSearchException, Reason, CONNECTION, HTTP_STATUS, INVALID_RESPONSE, QUERY_TOO_BROAD, TIMEOUT, HttpQidoStudyGatewayTest (+6 more)

### Community 155 - ".parse"
Cohesion: 0.14
Nodes (12): ApplicationScoped, Inject, JsonNode, ObjectMapper, StudySummary, QidoStudyResponseParser, BeforeEach, ParameterizedTest (+4 more)

### Community 156 - "Worklist e busca via QIDO-RS — design do MVP #2"
Cohesion: 0.09
Nodes (22): Arquitetura, Autenticação, privacidade e observabilidade, Backend, Backend, Buscar estudos, Contrato HTTP, Critérios de aceite, Desempenho e concorrência (+14 more)

### Community 157 - "Phase 1 — Backend QIDO vertical slice"
Cohesion: 0.09
Nodes (21): Backend production, Backend tests, File Map, Frontend production and tests, Global Constraints, Integration and operational files, Phase 1 — Backend QIDO vertical slice, Phase 1 human gate (+13 more)

### Community 158 - "useWorklist.spec.ts"
Cohesion: 0.60
Nodes (3): createStudy(), page(), pageWithPatient()

## Knowledge Gaps
- **385 isolated node(s):** `dev.blackice:blackice-backend`, `TIMEOUT`, `CONNECTION`, `HTTP_STATUS`, `INTERRUPTED` (+380 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **76 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `AccessTokenProvider` connect `WorklistResourceTest` to `IngestResult`?**
  _High betweenness centrality (0.046) - this node is a cross-community bridge._
- **Why does `StudySearchRequest` connect `StudySearchRequest` to `HttpQidoStudyGatewayTest`, `WorklistResourceTest`?**
  _High betweenness centrality (0.023) - this node is a cross-community bridge._
- **Why does `StudySummary` connect `WorklistResourceTest` to `StudySearchRequest`, `HttpQidoStudyGatewayTest`, `.parse`?**
  _High betweenness centrality (0.020) - this node is a cross-community bridge._
- **Are the 8 inferred relationships involving `StudySearchRequest` (e.g. with `.invalidRequests()` and `.normalizes_filters_and_keeps_open_date_range()`) actually correct?**
  _`StudySearchRequest` has 8 INFERRED edges - model-reasoned connections that need verification._
- **Are the 8 inferred relationships involving `ValidatedDicom` (e.g. with `.validate()` and `.connection_failure_throws_archive_unavailable_with_connection_reason()`) actually correct?**
  _`ValidatedDicom` has 8 INFERRED edges - model-reasoned connections that need verification._
- **Are the 7 inferred relationships involving `UploadedDicom` (e.g. with `.ingest()` and `.corrupt_bytes_produce_malformed_dicom_issue()`) actually correct?**
  _`UploadedDicom` has 7 INFERRED edges - model-reasoned connections that need verification._
- **Are the 5 inferred relationships involving `IngestResult` (e.g. with `.partial_result_returns_200()` and `.total_local_rejection_returns_422()`) actually correct?**
  _`IngestResult` has 5 INFERRED edges - model-reasoned connections that need verification._