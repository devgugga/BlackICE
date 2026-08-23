# Graph Report - .  (2026-08-23)

## Corpus Check
- 227 files · ~127,132 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 1143 nodes · 1738 edges · 195 communities (97 shown, 98 thin omitted)
- Extraction: 90% EXTRACTED · 10% INFERRED · 0% AMBIGUOUS · INFERRED: 168 edges (avg confidence: 0.78)
- Token cost: 0 input · 0 output

## Community Hubs (Navigation)
- Ingest HTTP Flow
- Worklist HTTP Boundary
- Frontend Worklist State
- QIDO Search Gateway
- Frontend Package Tooling
- QIDO Failure Tests
- QIDO Response Parsing
- Agent Authoring Domain
- Ingest Use Case Failures
- Worklist Design Specification
- Worklist Implementation Plan
- Keycloak Same-Origin Design
- STOW Response Parsing
- PACS Architecture Concepts
- Frontend Ingest State
- Vite Node Configuration
- STOW Gateway Publishing
- Vue TypeScript Configuration
- DICOM Archive Port Tests
- DICOM Batch Validator Tests
- DICOM Validation Errors
- Ingest Validation Model
- Infrastructure Security Stack
- Session HTTP Boundary
- Backend Architecture Tests
- Frontend E2E Tests
- Graphify Community Labels
- Ingest API Tests
- Modular Backend Design
- Maven Wrapper Script
- Synthetic DICOM Tests
- Ingest Architecture Concepts
- Agent Authoring Design
- STOW HTTP Gateway
- Ingest Page UI
- Modular Backend Plan
- Commit Curator Design
- Keycloak Bootstrap Design
- CSRF HTTP Resource
- CSRF Resource Tests
- Ingest Type Contracts
- Keycloak Same-Origin Plan
- Agent Authoring Plan
- Antigravity Agents Design
- Domain Review Boundaries
- Archive Failure Reasons
- Keycloak Evolution Plans
- Cross-Platform Keycloak Bootstrap
- Antigravity Agents Plan
- Backend Runtime Configuration
- Session Resource Tests
- Worklist Composable Tests
- Session Graph Query
- DICOM Identity Query
- Graphify Operations Query
- DICOM Reviewer Agent
- Git Graphify Workflow
- Backend Architecture Roadmap
- Vue Viewer Architecture
- Commit Curator Plan
- Keycloak Bootstrap Plan
- Trace Delivery Plan
- Problem Catalog Governance
- Trace Propagation Architecture
- DICOM Viewer Agent
- Quarkus Backend Agent
- Ingest Composable Tests
- Commit Message Policy
- Graphify Incremental Workflow
- DICOM Identity Model
- Historical Architecture Plans
- Problem Response Backend
- Problem Catalog Identity
- Problem Catalog Codegen
- Frontend Problem Extensions
- Graphify Watch Ingestion
- Graph Export Services
- Graph Query Expansion
- Incremental Node Replacement
- Project Graphify Setup
- Commit Curator Wrapper
- Agent Instruction Entry Points
- Agent Authoring Skill
- Maven Distribution Wrapper
- Frontend Container Runtime
- Playwright Configuration
- BlackICE Visual Identity
- Vue Router Entry
- Home Page Session
- Ingest Result Display
- Session Type Contract
- Claude Agent Authoring
- Project Structure Convention
- Thin Agent Wrappers
- Agent Model Validation
- Pixel Data Integrity
- Project Structure Records
- Agent Platform Plans
- Frontend Problem Contract
- Keycloak Admin Container
- Current Backend Specifications
- API Problem Types
- Generated Problem Types
- Keycloak POSIX Wrapper
- Keycloak Configuration Script
- Commit Curator Definition
- DICOM Reviewer Definition
- Viewer Agent Definition
- Backend Agent Definition
- Semantic Extraction Concept
- Cross-Repository Graph Merge
- Post-Commit Graph Hook
- Media Transcription Pipeline
- Claude Skill Collection
- Codex DICOM Reviewer
- Codex Commit Curator
- Codex Quarkus Backend
- Codex Viewer Frontend
- Codex Multi-Agent Settings
- Backend Container Artifacts
- Backend Ignore Configuration
- JVM Container Runtime
- Legacy JAR Container
- Native Micro Container
- Native Container Runtime
- Blocking Annotation
- GET Annotation
- Logger Dependency
- Path Annotation
- Produces Annotation
- HTTP Response Type
- Roles Allowed Annotation
- HTTP Client Dependency
- JSON Node Type
- JSON Mapper Type
- Quarkus Test Annotation
- Test Security Annotation
- After Each Annotation
- Embedded HTTP Server
- Frontend Docker Context
- Frontend Ignore Configuration
- Frontend Application Mount
- Node pnpm Versions
- Frontend Dependency Manifest
- Social Icon Sprite
- Frontend Toolchain Config
- Ingest Interface States
- Graphify Add Watch
- Graphify Export Workflows
- Semantic Extraction Workflow
- Cross-Repository Merge Workflow
- Graphify Hook Workflow
- Graph Query Workflow
- Media Transcription Workflow
- Graph Output Exclusion
- Platform Discovery Convention
- Patient Identifier Semantics
- Codex Subagent Configuration
- PACS Architecture Decisions
- Shared Realm Audience
- Project Graphify Integration
- Commit Curator System
- Agent Authoring System
- Antigravity Agent System
- Graphify Merge Driver
- Graphify Local State
- Graph Output Recursion
- Backend Maven Coordinates
- Archive Reason Enum

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
- `Graphify project setup` --references--> `Graphify skill`  [EXTRACTED]
  .graphify/setup.ps1 → .agents/skills/graphify/SKILL.md
- `DCM4CHEE 5.34.3 Baseline` --conceptually_related_to--> `DCM4CHEE secure stack`  [INFERRED]
  docs/superpowers/plans/2026-07-23-dcm4chee-5-34-3.md → infra/dcm4chee/README.md
- `Worklist Search` --references--> `QIDO-RS`  [EXTRACTED]
  apps/frontend/README.md → docs/domains/dicom/dicomweb.md
- `BFF Session` --conceptually_related_to--> `OIDC Token Propagation`  [INFERRED]
  apps/frontend/README.md → docs/domains/dicom/dicomweb.md
- `Feature-first Frontend` --conceptually_related_to--> `BFF Session`  [INFERRED]
  docs/domains/vue/conventions.md → apps/frontend/README.md

## Import Cycles
- None detected.

## Hyperedges (group relationships)
- **Generated cross-stack problem contract** — docs_superpowers_specs_2026_08_23_error_handling_problem_catalog_design_problem_catalog, docs_superpowers_specs_2026_08_23_error_handling_problem_catalog_design_problem_types_generated, docs_superpowers_specs_2026_08_23_error_handling_problem_catalog_design_quarkus, docs_superpowers_specs_2026_08_23_error_handling_problem_catalog_design_vue_frontend [EXTRACTED 1.00]
- **Problem contract flow** — docs_superpowers_plans_2026_08_23_error_handling_problem_catalog_problem_catalog, docs_superpowers_plans_2026_08_23_error_handling_problem_catalog_codegen, docs_superpowers_plans_2026_08_23_error_handling_problem_catalog_problem_type_java, docs_superpowers_plans_2026_08_23_error_handling_problem_catalog_problem_types_typescript [EXTRACTED 1.00]
- **DICOMweb Operations** — docs_domains_dicom_dicomweb_qido_rs, docs_domains_dicom_dicomweb_wado_rs, docs_domains_dicom_dicomweb_stow_rs [EXTRACTED 1.00]
- **Keycloak Login Theme Delivery** — docs_superpowers_plans_2026_07_26_keycloak_login_theme_keycloak_login_theme_plan, docs_superpowers_plans_2026_07_27_keycloak_input_focus_outline_focus_outline_plan, docs_superpowers_plans_2026_07_27_keycloak_theme_e2e_ui_regression_theme_e2e_plan, docs_superpowers_specs_2026_07_26_keycloak_login_theme_design_keycloak_theme_design [INFERRED 0.85]
- **BlackICE Keycloak login theme** — infra_keycloak_readme_blackice_keycloak_configuration, infra_keycloak_themes_blackice_login_messages_messages_en_blackice_login_messages, infra_keycloak_themes_blackice_login_resources_css_blackice_blackice_login_theme, infra_keycloak_themes_blackice_login_theme_blackice_theme_properties [EXTRACTED 1.00]
- **BlackICE Compose stack** — infra_readme_infrastructure_stack, infra_compose_shared_infrastructure_services, infra_dcm4chee_compose_dcm4chee_services, infra_compose_apps_product_application_services [EXTRACTED 1.00]
- **Keycloak Cross-platform Configuration Flow** — docs_superpowers_plans_2026_08_09_keycloak_windows_bootstrap_shared_posix_configuration_core, docs_superpowers_plans_2026_08_09_keycloak_windows_bootstrap_cross_platform_launchers, docs_superpowers_plans_2026_08_09_keycloak_windows_bootstrap_three_file_compose_configuration, docs_superpowers_plans_2026_08_09_keycloak_windows_bootstrap_keycloak_admin_rest [EXTRACTED 1.00]
- **Manual DICOM Ingest BFF Flow** — docs_superpowers_plans_2026_08_09_manual_dicom_import_authenticated_dicom_ingest_flow, docs_superpowers_plans_2026_08_09_manual_dicom_import_frontend_ingest_workflow, docs_superpowers_plans_2026_08_09_manual_dicom_import_metadata_validation_and_study_grouping, docs_superpowers_plans_2026_08_09_manual_dicom_import_streaming_stow_rs_gateway, docs_superpowers_plans_2026_08_09_manual_dicom_import_csrf_and_oidc_protection [EXTRACTED 1.00]
- **Manual Import Security Flow** — docs_superpowers_specs_2026_08_09_manual_dicom_import_design_manual_dicom_import, docs_superpowers_specs_2026_08_09_manual_dicom_import_design_csrf_protection [EXTRACTED 1.00]
- **DICOM Delivery Roles** — _claude_agents_dicom_dicom_domain_reviewer_dicom_domain_review, _claude_agents_quarkus_quarkus_backend_quarkus_backend, _claude_agents_vue_dicom_viewer_frontend_cornerstone_viewer [INFERRED 0.85]
- **DICOM Patient Identity Safety** — docs_domains_dicom_semantics_dicom_patient_study_series_instance_hierarchy, docs_domains_dicom_semantics_dicom_uid_identity, docs_domains_dicom_semantics_patientid_with_issuer [EXTRACTED 1.00]

## Communities (195 total, 98 thin omitted)

### Community 0 - "Ingest HTTP Flow"
Cohesion: 0.06
Nodes (42): AccessTokenCredential, IngestHttpStatusResolver, IngestResource, Blocking, Inject, Logger, Path, Produces (+34 more)

### Community 1 - "Worklist HTTP Boundary"
Cohesion: 0.07
Nodes (30): WorklistErrorResponse, AccessTokenProvider, Inject, WorklistResource, StudyQueryGateway, PageMetadata, StudyPage, StudySummary (+22 more)

### Community 2 - "Frontend Worklist State"
Cohesion: 0.06
Nodes (25): emit, EMPTY_FILTERS, PAGE_SIZE, SearchStudies, useWorklist(), WorklistComposable, WorklistPhase, safeError() (+17 more)

### Community 3 - "QIDO Search Gateway"
Cohesion: 0.11
Nodes (17): InvalidStudySearchException, StudySearchRequest, HttpQidoStudyGateway, ApplicationScoped, Inject, Override, ApplicationScoped, QidoQueryBuilder (+9 more)

### Community 4 - "Frontend Package Tooling"
Cohesion: 0.05
Nodes (39): dependencies, vue, vue-router, devDependencies, jsdom, @playwright/test, @types/node, typescript (+31 more)

### Community 5 - "QIDO Failure Tests"
Cohesion: 0.16
Nodes (14): AfterEach, ArchiveSearchException, Reason, CONNECTION, HTTP_STATUS, INVALID_RESPONSE, QUERY_TOO_BROAD, TIMEOUT (+6 more)

### Community 6 - "QIDO Response Parsing"
Cohesion: 0.15
Nodes (12): ApplicationScoped, Inject, StudySummary, QidoStudyResponseParser, BeforeEach, ParameterizedTest, Test, QidoStudyResponseParserTest (+4 more)

### Community 7 - "Agent Authoring Domain"
Cohesion: 0.08
Nodes (23): Agent authoring skill, Claude agent authoring skill, Convenções de autoria de agentes, Escopo e autorização, Fonte única de verdade, Forma de um wrapper, Fronteiras, Classificação e roteamento (+15 more)

### Community 8 - "Ingest Use Case Failures"
Cohesion: 0.25
Nodes (7): ArchiveUnavailableException, IngestStudiesUseCase, ApplicationScoped, StudyAttempt, StudyTask, IngestStudiesUseCaseTest, Test

### Community 9 - "Worklist Design Specification"
Cohesion: 0.09
Nodes (22): Arquitetura, Autenticação, privacidade e observabilidade, Backend, Backend, Buscar estudos, Contrato HTTP, Critérios de aceite, Desempenho e concorrência (+14 more)

### Community 10 - "Worklist Implementation Plan"
Cohesion: 0.09
Nodes (21): Backend production, Backend tests, File Map, Frontend production and tests, Global Constraints, Integration and operational files, Phase 1 — Backend QIDO vertical slice, Phase 1 human gate (+13 more)

### Community 11 - "Keycloak Same-Origin Design"
Cohesion: 0.09
Nodes (21): Alternativas descartadas, `apps/backend/src/main/resources/application.properties`, BlackICE — Login same-origin: tirar o Keycloak da barra de endereços, Custo honesto, Custo honesto (revisado), Decisões, Estado atual (verificado em 2026-08-07, stack no ar), Fase 1 — Same-origin atrás do Traefik (+13 more)

### Community 12 - "STOW Response Parsing"
Cohesion: 0.20
Nodes (8): ApplicationScoped, Inject, JsonNode, ObjectMapper, StowResponseParser, BeforeEach, Test, StowResponseParserTest

### Community 13 - "PACS Architecture Concepts"
Cohesion: 0.12
Nodes (21): BFF Session, Worklist Search, Archive Ecosystem, DCM4CHEE 5.34.3 Secure Baseline, Evolution Backlog, Worklist Pagination Evolution, OIDC Token Propagation, QIDO-RS (+13 more)

### Community 14 - "Frontend Ingest State"
Cohesion: 0.14
Nodes (12): fetchCsrfToken(), readCookie(), UploadError, uploadStudies(), XhrFactory, DEFAULT_API, DEFAULT_LIMITS, IngestApi (+4 more)

### Community 15 - "Vite Node Configuration"
Cohesion: 0.10
Nodes (19): compilerOptions, allowImportingTsExtensions, erasableSyntaxOnly, lib, module, moduleDetection, noEmit, noFallthroughCasesInSwitch (+11 more)

### Community 16 - "STOW Gateway Publishing"
Cohesion: 0.29
Nodes (6): ValidatedDicom, Override, MultipartRelatedBodyPublisher, HttpDicomArchiveGatewayTest, Test, BodyPublisher

### Community 17 - "Vue TypeScript Configuration"
Cohesion: 0.11
Nodes (18): compilerOptions, allowArbitraryExtensions, baseUrl, erasableSyntaxOnly, ignoreDeprecations, noFallthroughCasesInSwitch, noUnusedLocals, noUnusedParameters (+10 more)

### Community 18 - "DICOM Archive Port Tests"
Cohesion: 0.19
Nodes (8): StowInstanceResult, StowStudyResult, FakeDicomArchiveGateway, BeforeEach, Override, AfterEach, BeforeEach, HttpServer

### Community 19 - "DICOM Batch Validator Tests"
Cohesion: 0.35
Nodes (6): UploadedDicom, Override, Dcm4cheDicomBatchValidatorTest, Test, files, references

### Community 20 - "DICOM Validation Errors"
Cohesion: 0.15
Nodes (13): RejectedFile, Code, DUPLICATE_IDENTICAL, MALFORMED_DICOM, MISSING_SERIES_INSTANCE_UID, MISSING_SOP_CLASS_UID, MISSING_SOP_INSTANCE_UID, MISSING_STUDY_INSTANCE_UID (+5 more)

### Community 21 - "Ingest Validation Model"
Cohesion: 0.24
Nodes (5): DicomBatchValidator, Inject, DicomBatchValidation, DicomValidationIssue, Attributes

### Community 22 - "Infrastructure Security Stack"
Cohesion: 0.16
Nodes (14): DCM4CHEE 5.34.3 Baseline, Keycloak input focus outline fix, Keycloak theme E2E regression suite, Same-origin Keycloak login, Product application Compose services, Shared infrastructure Compose services, DCM4CHEE Compose services, DCM4CHEE secure stack (+6 more)

### Community 23 - "Session HTTP Boundary"
Cohesion: 0.29
Nodes (9): Authenticated, GET, Path, Response, SessionResource, SessionResponse, JsonWebToken, PermitAll (+1 more)

### Community 24 - "Backend Architecture Tests"
Cohesion: 0.28
Nodes (3): BackendArchitectureTest, Test, JavaClasses

### Community 25 - "Frontend E2E Tests"
Cohesion: 0.22
Nodes (8): expectVerticallyCentered(), openBlackiceLogin(), requiredBox(), screenshotLoginCard(), showInvalidCredentials(), emptyFilters, emptyParams, page

### Community 26 - "Graphify Community Labels"
Cohesion: 0.17
Nodes (12): Automatic Label Reuse Invalidation, Cluster-Only Workflow, Clustered Communities, Community Label Quality Gate, Community Labels, Community Membership Signatures, Current Community Summaries, Exact Community-Key Coverage (+4 more)

### Community 28 - "Modular Backend Design"
Cohesion: 0.18
Nodes (10): Backend modular com Clean Architecture, Critérios de aceite, Decisão, Documentação de código, Fora de escopo, Limites e direção de dependências, Migração do fluxo de ingestão, Módulos de sessão e segurança (+2 more)

### Community 29 - "Maven Wrapper Script"
Cohesion: 0.33
Nodes (6): mvnw script, clean(), die(), exec_maven(), set_java_home(), verbose()

### Community 30 - "Synthetic DICOM Tests"
Cohesion: 0.40
Nodes (7): createSyntheticDicom(), element(), LONG_VR, paddedText(), SyntheticDicomMetadata, text(), us()

### Community 31 - "Ingest Architecture Concepts"
Cohesion: 0.20
Nodes (10): Authenticated DICOM Ingest Flow, CSRF and OIDC Protection, DICOM Domain Review Gate, DICOM UID Preservation, Durable Jobs EVO-001, Frontend Ingest Workflow, Manual DICOM Import, Metadata Validation and Study Grouping (+2 more)

### Community 32 - "Agent Authoring Design"
Cohesion: 0.20
Nodes (9): Alterações documentais previstas, Conhecimento neutro e wrappers, Decisões, Desenho — skill de autoria de agentes, Fora de escopo, Objetivo, Segurança, governança e validação, Seleção de modelo no momento da mudança (+1 more)

### Community 33 - "STOW HTTP Gateway"
Cohesion: 0.36
Nodes (5): DicomArchiveGateway, HttpDicomArchiveGateway, ApplicationScoped, HttpClient, Inject

### Community 34 - "Ingest Page UI"
Cohesion: 0.22
Nodes (4): batch, limitWarning, totalBytes, totalFiles

### Community 35 - "Modular Backend Plan"
Cohesion: 0.25
Nodes (7): Backend Modular Clean Architecture Implementation Plan, Global Constraints, Task 1: Criar a guarda arquitetural que falha contra a estrutura atual, Task 2: Migrar ingestão para portas e adaptadores, deixando a guarda verde, Task 3: Documentar as fronteiras no código sem comentários redundantes, Task 4: Atualizar a documentação operacional e fechar a verificação, Task 5: Detalhar o módulo de ingestão por responsabilidade

### Community 36 - "Commit Curator Design"
Cohesion: 0.25
Nodes (7): Arquitetura, Commit Curator Design, Comportamento, Limites, Modelos, Objetivo, Validação

### Community 37 - "Keycloak Bootstrap Design"
Cohesion: 0.25
Nodes (7): Bootstrap do Keycloak no Windows — Design, Decisão, Erros e segurança, Fluxo, Fora de escopo, Objetivo, Testes

### Community 38 - "CSRF HTTP Resource"
Cohesion: 0.48
Nodes (5): CsrfResource, Authenticated, GET, Path, Response

### Community 39 - "CSRF Resource Tests"
Cohesion: 0.48
Nodes (4): CsrfResourceTest, QuarkusTest, Test, TestSecurity

### Community 40 - "Ingest Type Contracts"
Cohesion: 0.29
Nodes (5): IngestOutcome, IngestResponse, InstanceStatus, StudyStatus, UploadHandle

### Community 41 - "Keycloak Same-Origin Plan"
Cohesion: 0.29
Nodes (6): Estrutura de arquivos, Global Constraints, Keycloak same-origin — Implementation Plan, Self-review, Task 1: Fase 1 — Keycloak same-origin sob `/auth`, Task 2: Fase 2 — renomear o realm para `blackice`

### Community 42 - "Agent Authoring Plan"
Cohesion: 0.29
Nodes (6): Agent Authoring Skill Implementation Plan, Global Constraints, Task 1: Criar o Domain Pack canônico, Task 2: Criar wrappers de descoberta mínimos, Task 3: Integrar a convenção à documentação do repositório, Task 4: Validar a skill e as modificações

### Community 43 - "Antigravity Agents Design"
Cohesion: 0.29
Nodes (6): Agentes Antigravity — desenho, Configuração, Conhecimento e documentação, Escopo, Objetivo, Verificação

### Community 44 - "Domain Review Boundaries"
Cohesion: 0.33
Nodes (6): DICOM Domain Review, Human Semantic Gate, DICOMweb Backend Boundary, BlackICE Quarkus Backend, Cornerstone3D Viewer, Vue Reactivity Boundary

### Community 45 - "Archive Failure Reasons"
Cohesion: 0.33
Nodes (5): Reason, CONNECTION, HTTP_STATUS, INTERRUPTED, TIMEOUT

### Community 46 - "Keycloak Evolution Plans"
Cohesion: 0.33
Nodes (6): Foundation Stack Plan, Keycloak Login Theme Plan, Keycloak Focus Outline Plan, Keycloak Theme E2E Plan, Keycloak Same-origin Plan, Keycloak Theme Design

### Community 47 - "Cross-Platform Keycloak Bootstrap"
Cohesion: 0.33
Nodes (6): Cross-platform Launchers, Keycloak Admin REST, Keycloak Windows Bootstrap, Pester Contract Test, Shared POSIX Configuration Core, Three-file Compose Configuration

### Community 48 - "Antigravity Agents Plan"
Cohesion: 0.33
Nodes (5): Antigravity Agents Implementation Plan, Global Constraints, Task 1: Criar os wrappers Antigravity, Task 2: Documentar o ponto de descoberta, Task 3: Confirmar descoberta pelo Antigravity

### Community 49 - "Backend Runtime Configuration"
Cohesion: 0.60
Nodes (5): Java and Maven toolchain, BlackICE backend BFF, DICOMweb gateway configuration, OIDC BFF session configuration, BlackICE PACS

### Community 50 - "Session Resource Tests"
Cohesion: 0.60
Nodes (3): QuarkusTest, Test, SessionResourceTest

### Community 51 - "Worklist Composable Tests"
Cohesion: 0.60
Nodes (3): createStudy(), page(), pageWithPatient()

### Community 52 - "Session Graph Query"
Cohesion: 0.40
Nodes (4): Answer, Outcome, Q: Como o frontend Vue obtém a sessão autenticada do backend?, Source Nodes

### Community 53 - "DICOM Identity Query"
Cohesion: 0.40
Nodes (4): Answer, Outcome, Q: Quais regras do BlackICE governam StudyInstanceUID e os verbos DICOMweb?, Source Nodes

### Community 54 - "Graphify Operations Query"
Cohesion: 0.40
Nodes (4): Answer, Outcome, Q: Onde ficam as instruções operacionais do Graphify no BlackICE?, Source Nodes

### Community 55 - "DICOM Reviewer Agent"
Cohesion: 0.50
Nodes (3): Antes de revisar, Papel, Revisão

### Community 56 - "Git Graphify Workflow"
Cohesion: 0.50
Nodes (4): Graphify Tooling, Incremental Graph Update, Commit Scope Policy, Git Domain Pack

### Community 57 - "Backend Architecture Roadmap"
Cohesion: 0.50
Nodes (4): Modular Architecture, Quarkus Product Backend, Report Model, Backend Modular Architecture Plan

### Community 58 - "Vue Viewer Architecture"
Cohesion: 0.50
Nodes (4): Vue Feature Architecture, WADO-RS Viewer, Vue Frontend Domain Pack, Worklist QIDO-RS Plan

### Community 59 - "Commit Curator Plan"
Cohesion: 0.50
Nodes (3): Commit Curator Implementation Plan, Global Constraints, Task 1: Domain Pack e wrappers de commit

### Community 60 - "Keycloak Bootstrap Plan"
Cohesion: 0.50
Nodes (3): Bootstrap do Keycloak no Windows Implementation Plan, Global Constraints, Task 1: Launchers multiplataforma com núcleo único

### Community 61 - "Trace Delivery Plan"
Cohesion: 0.50
Nodes (4): ApiTraceResponseFilter, DICOM domain reviewer gate, W3C TraceID propagation, W3cTraceContextInjector

### Community 62 - "Problem Catalog Governance"
Cohesion: 0.50
Nodes (4): Catalog lock, Problem catalog, Problem catalog skill, UUIDv5 problem identity

### Community 63 - "Trace Propagation Architecture"
Cohesion: 0.50
Nodes (4): DICOM domain review, Quarkus API boundary, W3C TraceID propagation, W3cTraceContextInjector

### Community 67 - "Commit Message Policy"
Cohesion: 0.67
Nodes (3): Canonical Commit Body, Commit Conventions Domain Pack, Claude Commit Curator

### Community 68 - "Graphify Incremental Workflow"
Cohesion: 0.67
Nodes (3): Cluster-only refresh, Incremental re-extraction runbook, Incremental merge integrity

### Community 69 - "DICOM Identity Model"
Cohesion: 0.67
Nodes (3): DICOM Patient Study Series Instance Hierarchy, DICOM UID Identity, StudyInstanceUID

### Community 70 - "Historical Architecture Plans"
Cohesion: 0.67
Nodes (3): Graphify Integration Plan, Commit Curator Plan, Historical Specs and Plans

### Community 71 - "Problem Response Backend"
Cohesion: 0.67
Nodes (3): ApiProblem, ApiProblemFactory, ProblemResponseFactory

### Community 72 - "Problem Catalog Identity"
Cohesion: 0.67
Nodes (3): Append-only catalog lock, Problem catalog, UUIDv5 problem URN

### Community 73 - "Problem Catalog Codegen"
Cohesion: 0.67
Nodes (3): Problem catalog code generation, Java ProblemType, TypeScript problem types

### Community 74 - "Frontend Problem Extensions"
Cohesion: 0.67
Nodes (3): DICOM validation violations, Shared problem parser, Problem Details RFC 9457

## Knowledge Gaps
- **386 isolated node(s):** `dev.blackice:blackice-backend`, `TIMEOUT`, `CONNECTION`, `HTTP_STATUS`, `INTERRUPTED` (+381 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **98 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `StudySearchRequest` connect `QIDO Search Gateway` to `Worklist HTTP Boundary`, `QIDO Failure Tests`?**
  _High betweenness centrality (0.031) - this node is a cross-community bridge._
- **Why does `ArchiveUnavailableException` connect `Ingest Use Case Failures` to `STOW HTTP Gateway`, `Archive Failure Reasons`, `STOW Gateway Publishing`, `DICOM Archive Port Tests`, `Ingest Validation Model`?**
  _High betweenness centrality (0.020) - this node is a cross-community bridge._
- **Why does `IngestResult` connect `Ingest HTTP Flow` to `Ingest Use Case Failures`, `DICOM Archive Port Tests`, `DICOM Validation Errors`, `Ingest Validation Model`?**
  _High betweenness centrality (0.019) - this node is a cross-community bridge._
- **Are the 8 inferred relationships involving `StudySearchRequest` (e.g. with `.invalidRequests()` and `.normalizes_filters_and_keeps_open_date_range()`) actually correct?**
  _`StudySearchRequest` has 8 INFERRED edges - model-reasoned connections that need verification._
- **Are the 8 inferred relationships involving `ValidatedDicom` (e.g. with `.validate()` and `.connection_failure_throws_archive_unavailable_with_connection_reason()`) actually correct?**
  _`ValidatedDicom` has 8 INFERRED edges - model-reasoned connections that need verification._
- **Are the 7 inferred relationships involving `UploadedDicom` (e.g. with `.ingest()` and `.corrupt_bytes_produce_malformed_dicom_issue()`) actually correct?**
  _`UploadedDicom` has 7 INFERRED edges - model-reasoned connections that need verification._
- **Are the 5 inferred relationships involving `IngestResult` (e.g. with `.partial_result_returns_200()` and `.total_local_rejection_returns_422()`) actually correct?**
  _`IngestResult` has 5 INFERRED edges - model-reasoned connections that need verification._