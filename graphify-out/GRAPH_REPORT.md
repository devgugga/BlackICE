# Graph Report - .  (2026-08-23)

## Corpus Check
- 267 files · ~143,271 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 1608 nodes · 2416 edges · 224 communities (123 shown, 101 thin omitted)
- Extraction: 92% EXTRACTED · 8% INFERRED · 0% AMBIGUOUS · INFERRED: 184 edges (avg confidence: 0.78)
- Token cost: 0 input · 0 output

## Community Hubs (Navigation)
- Worklist QIDO Gateway
- Ingest Resource and Token
- Worklist Resource and Search
- Worklist Vue Components
- Catalog Schema Entry Fields
- Frontend Package Manifest
- DICOM Violations Extension Schema
- Generated Java ProblemType
- QIDO Study Response Parser
- Java and TypeScript Generators
- Agent Authoring Knowledge
- Problem Catalog Governance Docs
- Catalog Loading and Validation
- Catalog CLI Commands
- Ingest Studies Use Case
- Worklist QIDO Design Spec
- Worklist QIDO Implementation Plan
- Keycloak Same-origin Design
- STOW Response Parser
- DICOMweb Domain Knowledge
- Catalog Lock Enforcement
- CI Verification and Toolchain
- Frontend Ingest Upload API
- Node TypeScript Config
- Golden ProblemType Fixture
- STOW Archive Gateway Tests
- App TypeScript Config
- STOW Result Records
- Generated Frontend Problem Types
- Golden TypeScript Fixtures
- Catalog Tooling Manifest
- DICOM Batch Validator Tests
- DICOM Validation Issue Codes
- DICOM Validation Ports
- Catalog CLI Test Harness
- UUIDv5 Identity Derivation
- Keycloak and DCM4CHEE Infra
- Session Resource Endpoint
- Backend Architecture Guard
- E2E Login and Worklist Specs
- Graphify Community Labeling
- Generated ProblemExtensions
- Catalog Schema Root
- Ingest API Test Doubles
- Catalog Owner Definitions
- Catalog Entries Array Schema
- Backend Clean Architecture Design
- Maven Wrapper Script
- Synthetic DICOM E2E Fixtures
- Catalog Entry Required Fields
- Manual DICOM Import Plan
- Agent Authoring Skill Design
- Archive Gateway Port
- Ingest Page Component
- Catalog Markdown Generator
- Catalog Root Properties
- Backend Architecture Plan
- Commit Curator Design
- Keycloak Windows Bootstrap Design
- CSRF Resource Endpoint
- CSRF Resource Tests
- Ingest Frontend Types
- Keycloak Same-origin Plan
- Agent Authoring Skill Plan
- Antigravity Agents Design
- Claude Subagent Wrappers
- Archive Unavailable Reasons
- Keycloak Theme Plans
- Keycloak Bootstrap Launchers
- Antigravity Agents Plan
- Backend Toolchain and Config
- Session Resource Tests
- Worklist Composable Tests
- Project Structure Rules
- Catalog HTTP Status Schema
- Session Query Note
- DICOM UID Rules Query
- Graphify Instructions Query
- Antigravity DICOM Reviewer
- Git and Graphify Packs
- Catalog Code Pattern Schema
- Catalog Entry Definition
- Catalog Public Text Schema
- Backend Modular Architecture Docs
- Vue Frontend Domain Knowledge
- Commit Curator Plan
- Keycloak Bootstrap Plan
- TraceID Propagation Plan
- Problem Catalog Identity Design
- Quarkus Problem Boundary Design
- Antigravity Viewer Agent
- Antigravity Quarkus Agent
- Ingest Batch Composable Tests
- Claude Commit Curator
- Graphify Update Runbook
- DICOM Hierarchy Semantics
- Historical Specs Index
- Shared Problem Contract Plan
- Catalog Lock Plan
- Problem Code Generation Plan
- Frontend Problem Parser Design
- Graphify Watch and Ingest
- Graphify Export Targets
- Graphify Query Expansion
- Graphify Incremental Merge
- Graphify Setup Overlay
- Antigravity Commit Curator
- Codex Agent Authoring Skill
- Maven Wrapper Distribution
- Frontend Container Build
- Playwright Configuration
- BlackICE Favicon Asset
- Frontend Router
- Home Page Component
- Ingest Result Component
- Session Frontend Types
- Claude Agent Authoring Skill
- DICOM Violations Minimalism
- Thin Wrapper Convention
- Model Routing and Validation
- Pixel Data and Transfer Syntax
- Catalog Immutability Policy
- Sanitized Logging and TraceID
- Monorepo Structure Docs
- Agent Platform Plans
- Frontend Problem Parser Plan
- Keycloak Admin REST Script
- Backend and Worklist Designs
- Shared Problem Contract Design
- Generated Types Frontend Design
- Keycloak Container Script
- Keycloak Host Script
- Antigravity Commit Agent File
- Antigravity DICOM Reviewer File
- Antigravity Viewer Agent File
- Antigravity Quarkus Agent File
- Graphify Extraction Spec
- Graphify Repository Merge
- Graphify Post-commit Hook
- Graphify Media Transcription
- Claude Skills Index
- Codex DICOM Reviewer
- Codex Commit Curator
- Codex Quarkus Agent
- Codex Viewer Agent
- Codex Agent Configuration
- Graphify Commit Protocol
- Backend Docker Ignore
- Backend Git Ignore
- Backend JVM Container
- Backend Legacy JAR Container
- Backend Native Micro Container
- Backend Native Container
- Blocking Annotation
- GET Annotation
- Logger Facility
- Path Annotation
- Produces Annotation
- JAX-RS Response Type
- RolesAllowed Annotation
- Java HttpClient
- Jackson JsonNode
- Jackson ObjectMapper
- QuarkusTest Annotation
- TestSecurity Annotation
- AfterEach Hook
- Test HttpServer
- Frontend Docker Ignore
- Frontend Git Ignore
- Frontend Mount Point
- Frontend Node Versions
- Frontend Lockfile
- Social Icon Sprite
- Frontend Toolchain Readme
- Ingest Interface States
- Claude Code Configuration
- Graphify Add and Watch
- Graphify Export Reference
- Graphify Extraction Reference
- Graphify Cross-repo Merge
- Graphify Hooks Reference
- Graphify Query Reference
- Graphify Transcription Reference
- Graphify Claude Ignore
- Agent Platform Layouts
- PatientID Issuer Rule
- Codex Subagent Notes
- PACS Architecture Decisions
- Shared Realm Audience
- Graphify Integration Design
- Commit Curator Note
- Agent Authoring Note
- Antigravity Agents Note
- Graphify Merge Driver
- Graphify Local State
- Graphify Recursion Exclusion
- Backend Maven Project
- Archive Failure Reason

## God Nodes (most connected - your core abstractions)
1. `StudySearchRequest` - 28 edges
2. `ValidatedDicom` - 24 edges
3. `ProblemType` - 24 edges
4. `UploadedDicom` - 23 edges
5. `IngestResult` - 20 edges
6. `files` - 20 edges
7. `WorklistResourceTest` - 19 edges
8. `HttpQidoStudyGatewayTest` - 19 edges
9. `IngestStudiesUseCase` - 18 edges
10. `StudySummary` - 18 edges

## Surprising Connections (you probably didn't know these)
- `DICOM Business Invariants` --semantically_similar_to--> `UUIDv5 Problem Identity`  [INFERRED] [semantically similar]
  AGENTS.md → docs/domains/problem-catalog/registry.md
- `Forbidden Data In Error Surfaces` --semantically_similar_to--> `DICOM Business Invariants`  [INFERRED] [semantically similar]
  docs/domains/problem-catalog/security.md → AGENTS.md
- `Problem Catalog Skill (Codex and Antigravity)` --semantically_similar_to--> `Problem Catalog Skill (Claude)`  [INFERRED] [semantically similar]
  .agents/skills/problem-catalog/SKILL.md → .claude/skills/problem-catalog/SKILL.md
- `Graphify project setup` --references--> `Graphify skill`  [EXTRACTED]
  .graphify/setup.ps1 → .agents/skills/graphify/SKILL.md
- `Skill Creation Convention` --references--> `Problem Catalog Skill (Claude)`  [INFERRED]
  docs/domains/README.md → .claude/skills/problem-catalog/SKILL.md

## Import Cycles
- None detected.

## Hyperedges (group relationships)
- **Problem Catalog Governance Pack** — docs_domains_problem_catalog_readme_problem_catalog_domain_pack, docs_domains_problem_catalog_classification_decision_tree, docs_domains_problem_catalog_registry_code_grammar, docs_domains_problem_catalog_security_forbidden_data [EXTRACTED 1.00]
- **Problem Classification Categories** — docs_domains_problem_catalog_classification_api_scope, docs_domains_problem_catalog_classification_client_scope, docs_domains_problem_catalog_classification_operation_result, docs_domains_problem_catalog_classification_user_cancellation [EXTRACTED 1.00]
- **Catalog-Gated Verification Pipeline** — _github_workflows_verify_catalog_job, _github_workflows_verify_backend_job, _github_workflows_verify_frontend_job, docs_domains_problem_catalog_registry_continuous_verification [EXTRACTED 1.00]
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

## Communities (224 total, 101 thin omitted)

### Community 0 - "Worklist QIDO Gateway"
Cohesion: 0.07
Nodes (31): AfterEach, ArchiveSearchException, Reason, CONNECTION, HTTP_STATUS, INVALID_RESPONSE, QUERY_TOO_BROAD, TIMEOUT (+23 more)

### Community 1 - "Ingest Resource and Token"
Cohesion: 0.06
Nodes (42): AccessTokenCredential, IngestHttpStatusResolver, IngestResource, Blocking, Inject, Logger, Path, Produces (+34 more)

### Community 2 - "Worklist Resource and Search"
Cohesion: 0.07
Nodes (30): WorklistErrorResponse, AccessTokenProvider, Inject, WorklistResource, StudyQueryGateway, PageMetadata, StudyPage, StudySummary (+22 more)

### Community 3 - "Worklist Vue Components"
Cohesion: 0.06
Nodes (25): emit, EMPTY_FILTERS, PAGE_SIZE, SearchStudies, useWorklist(), WorklistComposable, WorklistPhase, safeError() (+17 more)

### Community 4 - "Catalog Schema Entry Fields"
Cohesion: 0.05
Nodes (41): $ref, description, minLength, type, $ref, properties, description, pattern (+33 more)

### Community 5 - "Frontend Package Manifest"
Cohesion: 0.05
Nodes (39): dependencies, vue, vue-router, devDependencies, jsdom, @playwright/test, @types/node, typescript (+31 more)

### Community 6 - "DICOM Violations Extension Schema"
Cohesion: 0.05
Nodes (38): additionalProperties, description, enum, description, $id, description, minimum, type (+30 more)

### Community 7 - "Generated Java ProblemType"
Cohesion: 0.06
Nodes (31): URI, ProblemScope, API, CLIENT, ProblemType, API_ACCESS_DENIED, API_ARCHIVE_RESPONSE_INVALID, API_ARCHIVE_UNAVAILABLE (+23 more)

### Community 8 - "QIDO Study Response Parser"
Cohesion: 0.15
Nodes (12): ApplicationScoped, Inject, StudySummary, QidoStudyResponseParser, BeforeEach, ParameterizedTest, Test, QidoStudyResponseParserTest (+4 more)

### Community 9 - "Java and TypeScript Generators"
Cohesion: 0.14
Nodes (31): componentType(), constant(), deprecationTag(), extensionVariant(), generateJava(), generateJavaExtensions(), header(), JAVA_PACKAGE (+23 more)

### Community 10 - "Agent Authoring Knowledge"
Cohesion: 0.08
Nodes (23): Agent authoring skill, Claude agent authoring skill, Convenções de autoria de agentes, Escopo e autorização, Fonte única de verdade, Forma de um wrapper, Fronteiras, Classificação e roteamento (+15 more)

### Community 11 - "Problem Catalog Governance Docs"
Cohesion: 0.09
Nodes (27): Problem Catalog Skill (Codex and Antigravity), Problem Catalog Skill (Claude), DICOM Business Invariants, Domain Packs Agent Architecture, Evolution Backlog, Human Gate Workflow, Problem Catalog Governance Pointer, API Problem Types Table (+19 more)

### Community 12 - "Catalog Loading and Validation"
Cohesion: 0.12
Nodes (22): API_KEY_ORDER, canonicalJson(), CLIENT_KEY_ORDER, CONTRACTS_DIR, fingerprint(), loadCatalog(), loadExtensionSchemas(), normalizeCatalog() (+14 more)

### Community 13 - "Catalog CLI Commands"
Cohesion: 0.18
Nodes (22): loadSchema(), serializeCatalog(), structuralValidator(), validateCatalog(), addEntry(), bootstrapCatalog(), buildEntry(), checkWorkspace() (+14 more)

### Community 14 - "Ingest Studies Use Case"
Cohesion: 0.25
Nodes (7): ArchiveUnavailableException, IngestStudiesUseCase, ApplicationScoped, StudyAttempt, StudyTask, IngestStudiesUseCaseTest, Test

### Community 15 - "Worklist QIDO Design Spec"
Cohesion: 0.09
Nodes (22): Arquitetura, Autenticação, privacidade e observabilidade, Backend, Backend, Buscar estudos, Contrato HTTP, Critérios de aceite, Desempenho e concorrência (+14 more)

### Community 16 - "Worklist QIDO Implementation Plan"
Cohesion: 0.09
Nodes (21): Backend production, Backend tests, File Map, Frontend production and tests, Global Constraints, Integration and operational files, Phase 1 — Backend QIDO vertical slice, Phase 1 human gate (+13 more)

### Community 17 - "Keycloak Same-origin Design"
Cohesion: 0.09
Nodes (21): Alternativas descartadas, `apps/backend/src/main/resources/application.properties`, BlackICE — Login same-origin: tirar o Keycloak da barra de endereços, Custo honesto, Custo honesto (revisado), Decisões, Estado atual (verificado em 2026-08-07, stack no ar), Fase 1 — Same-origin atrás do Traefik (+13 more)

### Community 18 - "STOW Response Parser"
Cohesion: 0.20
Nodes (8): ApplicationScoped, Inject, JsonNode, ObjectMapper, StowResponseParser, BeforeEach, Test, StowResponseParserTest

### Community 19 - "DICOMweb Domain Knowledge"
Cohesion: 0.12
Nodes (21): BFF Session, Worklist Search, Archive Ecosystem, DCM4CHEE 5.34.3 Secure Baseline, Evolution Backlog, Worklist Pagination Evolution, OIDC Token Propagation, QIDO-RS (+13 more)

### Community 20 - "Catalog Lock Enforcement"
Cohesion: 0.18
Nodes (18): compareCodes(), assertAllowedTransition(), assertExtensionFingerprints(), assertImmutableFields(), assertNoRemovedEntries(), assertOnlyActiveToDeprecated(), compareLock(), createLock() (+10 more)

### Community 21 - "CI Verification and Toolchain"
Cohesion: 0.13
Nodes (20): Backend Quarkus Job, Catalog Verification Job, Frontend Vue Job, Verify CI Workflow, Tooling node_modules Exclusion, Pinned Catalog Toolchain, Pinned Validation Dependencies, BlackICE PACS (+12 more)

### Community 22 - "Frontend Ingest Upload API"
Cohesion: 0.14
Nodes (12): fetchCsrfToken(), readCookie(), UploadError, uploadStudies(), XhrFactory, DEFAULT_API, DEFAULT_LIMITS, IngestApi (+4 more)

### Community 23 - "Node TypeScript Config"
Cohesion: 0.10
Nodes (19): compilerOptions, allowImportingTsExtensions, erasableSyntaxOnly, lib, module, moduleDetection, noEmit, noFallthroughCasesInSwitch (+11 more)

### Community 24 - "Golden ProblemType Fixture"
Cohesion: 0.12
Nodes (15): URI, ProblemScope, API, CLIENT, ProblemType, API_ARCHIVE_UNAVAILABLE, API_DICOM_VALIDATION_FAILED, API_SEARCH_INVALID (+7 more)

### Community 25 - "STOW Archive Gateway Tests"
Cohesion: 0.29
Nodes (6): ValidatedDicom, Override, MultipartRelatedBodyPublisher, HttpDicomArchiveGatewayTest, Test, BodyPublisher

### Community 26 - "App TypeScript Config"
Cohesion: 0.11
Nodes (18): compilerOptions, allowArbitraryExtensions, baseUrl, erasableSyntaxOnly, ignoreDeprecations, noFallthroughCasesInSwitch, noUnusedLocals, noUnusedParameters (+10 more)

### Community 27 - "STOW Result Records"
Cohesion: 0.19
Nodes (8): StowInstanceResult, StowStudyResult, FakeDicomArchiveGateway, BeforeEach, Override, AfterEach, BeforeEach, HttpServer

### Community 28 - "Generated Frontend Problem Types"
Cohesion: 0.12
Nodes (15): DICOM_VALIDATION_VIOLATION_CODES, DicomValidationViolation, DicomValidationViolationCode, DicomValidationViolations, ProblemExtensionsByCode, ProblemExtensionsFor, ApiProblemCode, ClientProblemCode (+7 more)

### Community 29 - "Golden TypeScript Fixtures"
Cohesion: 0.12
Nodes (15): DICOM_VALIDATION_VIOLATION_CODES, DicomValidationViolation, DicomValidationViolationCode, DicomValidationViolations, ProblemExtensionsByCode, ProblemExtensionsFor, ApiProblemCode, ClientProblemCode (+7 more)

### Community 30 - "Catalog Tooling Manifest"
Cohesion: 0.12
Nodes (15): ajv, ajv-formats, dependencies, ajv, ajv-formats, name, packageManager, private (+7 more)

### Community 31 - "DICOM Batch Validator Tests"
Cohesion: 0.35
Nodes (6): UploadedDicom, Override, Dcm4cheDicomBatchValidatorTest, Test, files, references

### Community 32 - "DICOM Validation Issue Codes"
Cohesion: 0.15
Nodes (13): RejectedFile, Code, DUPLICATE_IDENTICAL, MALFORMED_DICOM, MISSING_SERIES_INSTANCE_UID, MISSING_SOP_CLASS_UID, MISSING_SOP_INSTANCE_UID, MISSING_STUDY_INSTANCE_UID (+5 more)

### Community 33 - "DICOM Validation Ports"
Cohesion: 0.24
Nodes (5): DicomBatchValidator, Inject, DicomBatchValidation, DicomValidationIssue, Attributes

### Community 34 - "Catalog CLI Test Harness"
Cohesion: 0.20
Nodes (12): DEFAULT_PATHS, FORBIDDEN_FLAGS, main(), resolvePaths(), serializeLock(), fixture, workspace(), fixture (+4 more)

### Community 35 - "UUIDv5 Identity Derivation"
Cohesion: 0.27
Nodes (12): RFC-4122, RFC-9562, semanticErrors(), deriveProblemUrn(), deriveUuidV5(), DNS_NAMESPACE_UUID, formatUuid(), isProblemUrn() (+4 more)

### Community 36 - "Keycloak and DCM4CHEE Infra"
Cohesion: 0.16
Nodes (14): DCM4CHEE 5.34.3 Baseline, Keycloak input focus outline fix, Keycloak theme E2E regression suite, Same-origin Keycloak login, Product application Compose services, Shared infrastructure Compose services, DCM4CHEE Compose services, DCM4CHEE secure stack (+6 more)

### Community 37 - "Session Resource Endpoint"
Cohesion: 0.29
Nodes (9): Authenticated, GET, Path, Response, SessionResource, SessionResponse, JsonWebToken, PermitAll (+1 more)

### Community 38 - "Backend Architecture Guard"
Cohesion: 0.28
Nodes (3): BackendArchitectureTest, Test, JavaClasses

### Community 39 - "E2E Login and Worklist Specs"
Cohesion: 0.22
Nodes (8): expectVerticallyCentered(), openBlackiceLogin(), requiredBox(), screenshotLoginCard(), showInvalidCredentials(), emptyFilters, emptyParams, page

### Community 40 - "Graphify Community Labeling"
Cohesion: 0.17
Nodes (12): Automatic Label Reuse Invalidation, Cluster-Only Workflow, Clustered Communities, Community Label Quality Gate, Community Labels, Community Membership Signatures, Current Community Summaries, Exact Community-Key Coverage (+4 more)

### Community 41 - "Generated ProblemExtensions"
Cohesion: 0.21
Nodes (8): DicomValidationViolations, None, ProblemExtensions, Violation, DicomValidationViolations, None, ProblemExtensions, Violation

### Community 42 - "Catalog Schema Root"
Cohesion: 0.17
Nodes (11): additionalProperties, description, $id, required, $schema, title, type, entries (+3 more)

### Community 44 - "Catalog Owner Definitions"
Cohesion: 0.18
Nodes (11): $defs, owner, uuid, enum, pattern, type, frontend, ingest (+3 more)

### Community 45 - "Catalog Entries Array Schema"
Cohesion: 0.18
Nodes (11): description, items, type, $ref, description, items, minItems, type (+3 more)

### Community 46 - "Backend Clean Architecture Design"
Cohesion: 0.18
Nodes (10): Backend modular com Clean Architecture, Critérios de aceite, Decisão, Documentação de código, Fora de escopo, Limites e direção de dependências, Migração do fluxo de ingestão, Módulos de sessão e segurança (+2 more)

### Community 47 - "Maven Wrapper Script"
Cohesion: 0.33
Nodes (6): mvnw script, clean(), die(), exec_maven(), set_java_home(), verbose()

### Community 48 - "Synthetic DICOM E2E Fixtures"
Cohesion: 0.40
Nodes (7): createSyntheticDicom(), element(), LONG_VR, paddedText(), SyntheticDicomMetadata, text(), us()

### Community 49 - "Catalog Entry Required Fields"
Cohesion: 0.20
Nodes (10): required, code, description, extensionsSchemaRef, owner, replacedBy, retryPolicy, scope (+2 more)

### Community 50 - "Manual DICOM Import Plan"
Cohesion: 0.20
Nodes (10): Authenticated DICOM Ingest Flow, CSRF and OIDC Protection, DICOM Domain Review Gate, DICOM UID Preservation, Durable Jobs EVO-001, Frontend Ingest Workflow, Manual DICOM Import, Metadata Validation and Study Grouping (+2 more)

### Community 51 - "Agent Authoring Skill Design"
Cohesion: 0.20
Nodes (9): Alterações documentais previstas, Conhecimento neutro e wrappers, Decisões, Desenho — skill de autoria de agentes, Fora de escopo, Objetivo, Segurança, governança e validação, Seleção de modelo no momento da mudança (+1 more)

### Community 52 - "Archive Gateway Port"
Cohesion: 0.36
Nodes (5): DicomArchiveGateway, HttpDicomArchiveGateway, ApplicationScoped, HttpClient, Inject

### Community 53 - "Ingest Page Component"
Cohesion: 0.22
Nodes (4): batch, limitWarning, totalBytes, totalFiles

### Community 54 - "Catalog Markdown Generator"
Cohesion: 0.53
Nodes (8): apiSection(), cell(), clientSection(), deprecationSection(), extensionsSection(), generateMarkdown(), identitySection(), table()

### Community 55 - "Catalog Root Properties"
Cohesion: 0.25
Nodes (8): description, $ref, properties, namespaceUuid, schemaVersion, const, description, type

### Community 56 - "Backend Architecture Plan"
Cohesion: 0.25
Nodes (7): Backend Modular Clean Architecture Implementation Plan, Global Constraints, Task 1: Criar a guarda arquitetural que falha contra a estrutura atual, Task 2: Migrar ingestão para portas e adaptadores, deixando a guarda verde, Task 3: Documentar as fronteiras no código sem comentários redundantes, Task 4: Atualizar a documentação operacional e fechar a verificação, Task 5: Detalhar o módulo de ingestão por responsabilidade

### Community 57 - "Commit Curator Design"
Cohesion: 0.25
Nodes (7): Arquitetura, Commit Curator Design, Comportamento, Limites, Modelos, Objetivo, Validação

### Community 58 - "Keycloak Windows Bootstrap Design"
Cohesion: 0.25
Nodes (7): Bootstrap do Keycloak no Windows — Design, Decisão, Erros e segurança, Fluxo, Fora de escopo, Objetivo, Testes

### Community 59 - "CSRF Resource Endpoint"
Cohesion: 0.48
Nodes (5): CsrfResource, Authenticated, GET, Path, Response

### Community 60 - "CSRF Resource Tests"
Cohesion: 0.48
Nodes (4): CsrfResourceTest, QuarkusTest, Test, TestSecurity

### Community 61 - "Ingest Frontend Types"
Cohesion: 0.29
Nodes (5): IngestOutcome, IngestResponse, InstanceStatus, StudyStatus, UploadHandle

### Community 62 - "Keycloak Same-origin Plan"
Cohesion: 0.29
Nodes (6): Estrutura de arquivos, Global Constraints, Keycloak same-origin — Implementation Plan, Self-review, Task 1: Fase 1 — Keycloak same-origin sob `/auth`, Task 2: Fase 2 — renomear o realm para `blackice`

### Community 63 - "Agent Authoring Skill Plan"
Cohesion: 0.29
Nodes (6): Agent Authoring Skill Implementation Plan, Global Constraints, Task 1: Criar o Domain Pack canônico, Task 2: Criar wrappers de descoberta mínimos, Task 3: Integrar a convenção à documentação do repositório, Task 4: Validar a skill e as modificações

### Community 64 - "Antigravity Agents Design"
Cohesion: 0.29
Nodes (6): Agentes Antigravity — desenho, Configuração, Conhecimento e documentação, Escopo, Objetivo, Verificação

### Community 65 - "Claude Subagent Wrappers"
Cohesion: 0.33
Nodes (6): DICOM Domain Review, Human Semantic Gate, DICOMweb Backend Boundary, BlackICE Quarkus Backend, Cornerstone3D Viewer, Vue Reactivity Boundary

### Community 66 - "Archive Unavailable Reasons"
Cohesion: 0.33
Nodes (5): Reason, CONNECTION, HTTP_STATUS, INTERRUPTED, TIMEOUT

### Community 67 - "Keycloak Theme Plans"
Cohesion: 0.33
Nodes (6): Foundation Stack Plan, Keycloak Login Theme Plan, Keycloak Focus Outline Plan, Keycloak Theme E2E Plan, Keycloak Same-origin Plan, Keycloak Theme Design

### Community 68 - "Keycloak Bootstrap Launchers"
Cohesion: 0.33
Nodes (6): Cross-platform Launchers, Keycloak Admin REST, Keycloak Windows Bootstrap, Pester Contract Test, Shared POSIX Configuration Core, Three-file Compose Configuration

### Community 69 - "Antigravity Agents Plan"
Cohesion: 0.33
Nodes (5): Antigravity Agents Implementation Plan, Global Constraints, Task 1: Criar os wrappers Antigravity, Task 2: Documentar o ponto de descoberta, Task 3: Confirmar descoberta pelo Antigravity

### Community 70 - "Backend Toolchain and Config"
Cohesion: 0.60
Nodes (5): Java and Maven toolchain, BlackICE backend BFF, DICOMweb gateway configuration, OIDC BFF session configuration, BlackICE PACS

### Community 71 - "Session Resource Tests"
Cohesion: 0.60
Nodes (3): QuarkusTest, Test, SessionResourceTest

### Community 72 - "Worklist Composable Tests"
Cohesion: 0.60
Nodes (3): createStudy(), page(), pageWithPatient()

### Community 73 - "Project Structure Rules"
Cohesion: 0.40
Nodes (5): Structural Antipatterns, Quarkus Module Dependency Rules, dev.blackice.shared Justified Exception, Shared Promotion Rule, Vue Feature Colocation Rules

### Community 74 - "Catalog HTTP Status Schema"
Cohesion: 0.40
Nodes (5): httpStatus, description, maximum, minimum, type

### Community 75 - "Session Query Note"
Cohesion: 0.40
Nodes (4): Answer, Outcome, Q: Como o frontend Vue obtém a sessão autenticada do backend?, Source Nodes

### Community 76 - "DICOM UID Rules Query"
Cohesion: 0.40
Nodes (4): Answer, Outcome, Q: Quais regras do BlackICE governam StudyInstanceUID e os verbos DICOMweb?, Source Nodes

### Community 77 - "Graphify Instructions Query"
Cohesion: 0.40
Nodes (4): Answer, Outcome, Q: Onde ficam as instruções operacionais do Graphify no BlackICE?, Source Nodes

### Community 78 - "Antigravity DICOM Reviewer"
Cohesion: 0.50
Nodes (3): Antes de revisar, Papel, Revisão

### Community 79 - "Git and Graphify Packs"
Cohesion: 0.50
Nodes (4): Graphify Tooling, Incremental Graph Update, Commit Scope Policy, Git Domain Pack

### Community 80 - "Catalog Code Pattern Schema"
Cohesion: 0.50
Nodes (4): description, pattern, type, code

### Community 81 - "Catalog Entry Definition"
Cohesion: 0.50
Nodes (4): entry, additionalProperties, allOf, type

### Community 82 - "Catalog Public Text Schema"
Cohesion: 0.50
Nodes (4): publicText, description, minLength, type

### Community 83 - "Backend Modular Architecture Docs"
Cohesion: 0.50
Nodes (4): Modular Architecture, Quarkus Product Backend, Report Model, Backend Modular Architecture Plan

### Community 84 - "Vue Frontend Domain Knowledge"
Cohesion: 0.50
Nodes (4): Vue Feature Architecture, WADO-RS Viewer, Vue Frontend Domain Pack, Worklist QIDO-RS Plan

### Community 85 - "Commit Curator Plan"
Cohesion: 0.50
Nodes (3): Commit Curator Implementation Plan, Global Constraints, Task 1: Domain Pack e wrappers de commit

### Community 86 - "Keycloak Bootstrap Plan"
Cohesion: 0.50
Nodes (3): Bootstrap do Keycloak no Windows Implementation Plan, Global Constraints, Task 1: Launchers multiplataforma com núcleo único

### Community 87 - "TraceID Propagation Plan"
Cohesion: 0.50
Nodes (4): ApiTraceResponseFilter, DICOM domain reviewer gate, W3C TraceID propagation, W3cTraceContextInjector

### Community 88 - "Problem Catalog Identity Design"
Cohesion: 0.50
Nodes (4): Catalog lock, Problem catalog, Problem catalog skill, UUIDv5 problem identity

### Community 89 - "Quarkus Problem Boundary Design"
Cohesion: 0.50
Nodes (4): DICOM domain review, Quarkus API boundary, W3C TraceID propagation, W3cTraceContextInjector

### Community 93 - "Claude Commit Curator"
Cohesion: 0.67
Nodes (3): Canonical Commit Body, Commit Conventions Domain Pack, Claude Commit Curator

### Community 94 - "Graphify Update Runbook"
Cohesion: 0.67
Nodes (3): Cluster-only refresh, Incremental re-extraction runbook, Incremental merge integrity

### Community 95 - "DICOM Hierarchy Semantics"
Cohesion: 0.67
Nodes (3): DICOM Patient Study Series Instance Hierarchy, DICOM UID Identity, StudyInstanceUID

### Community 96 - "Historical Specs Index"
Cohesion: 0.67
Nodes (3): Graphify Integration Plan, Commit Curator Plan, Historical Specs and Plans

### Community 97 - "Shared Problem Contract Plan"
Cohesion: 0.67
Nodes (3): ApiProblem, ApiProblemFactory, ProblemResponseFactory

### Community 98 - "Catalog Lock Plan"
Cohesion: 0.67
Nodes (3): Append-only catalog lock, Problem catalog, UUIDv5 problem URN

### Community 99 - "Problem Code Generation Plan"
Cohesion: 0.67
Nodes (3): Problem catalog code generation, Java ProblemType, TypeScript problem types

### Community 100 - "Frontend Problem Parser Design"
Cohesion: 0.67
Nodes (3): DICOM validation violations, Shared problem parser, Problem Details RFC 9457

## Knowledge Gaps
- **602 isolated node(s):** `dev.blackice:blackice-backend`, `TIMEOUT`, `CONNECTION`, `HTTP_STATUS`, `INTERRUPTED` (+597 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **101 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `StudySearchRequest` connect `Worklist QIDO Gateway` to `Worklist Resource and Search`?**
  _High betweenness centrality (0.016) - this node is a cross-community bridge._
- **Why does `IngestResult` connect `Ingest Resource and Token` to `DICOM Validation Issue Codes`, `DICOM Validation Ports`, `STOW Result Records`, `Ingest Studies Use Case`?**
  _High betweenness centrality (0.012) - this node is a cross-community bridge._
- **Why does `ArchiveUnavailableException` connect `Ingest Studies Use Case` to `DICOM Validation Ports`, `Archive Unavailable Reasons`, `Archive Gateway Port`, `STOW Archive Gateway Tests`, `STOW Result Records`?**
  _High betweenness centrality (0.010) - this node is a cross-community bridge._
- **Are the 8 inferred relationships involving `StudySearchRequest` (e.g. with `.invalidRequests()` and `.normalizes_filters_and_keeps_open_date_range()`) actually correct?**
  _`StudySearchRequest` has 8 INFERRED edges - model-reasoned connections that need verification._
- **Are the 8 inferred relationships involving `ValidatedDicom` (e.g. with `.validate()` and `.connection_failure_throws_archive_unavailable_with_connection_reason()`) actually correct?**
  _`ValidatedDicom` has 8 INFERRED edges - model-reasoned connections that need verification._
- **Are the 7 inferred relationships involving `UploadedDicom` (e.g. with `.ingest()` and `.corrupt_bytes_produce_malformed_dicom_issue()`) actually correct?**
  _`UploadedDicom` has 7 INFERRED edges - model-reasoned connections that need verification._
- **Are the 5 inferred relationships involving `IngestResult` (e.g. with `.partial_result_returns_200()` and `.total_local_rejection_returns_422()`) actually correct?**
  _`IngestResult` has 5 INFERRED edges - model-reasoned connections that need verification._