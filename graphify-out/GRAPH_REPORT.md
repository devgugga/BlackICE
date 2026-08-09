# Graph Report - C:\Users\gusgo\Projects\BlackICE  (2026-08-09)

## Corpus Check
- 111 files · ~66,869 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 352 nodes · 300 edges · 89 communities (35 shown, 54 thin omitted)
- Extraction: 95% EXTRACTED · 5% INFERRED · 0% AMBIGUOUS · INFERRED: 14 edges (avg confidence: 0.86)
- Token cost: 0 input · 0 output

## Community Hubs (Navigation)
- Fluxo de commit Graphify
- Design same-origin Keycloak
- Configuração TypeScript Node
- Configuração TypeScript Aplicação
- Dependências de desenvolvimento Frontend
- Dependências Vue da aplicação
- Gates de domínio DICOM
- Recurso de sessão Quarkus
- Rótulos de comunidade Graphify
- Verbos e segurança DICOMweb
- Serviços Keycloak e backend
- Script Maven Wrapper
- Identidade e hierarquia DICOM
- Plano Keycloak same-origin
- Pipeline incremental Graphify
- Teste E2E Keycloak
- Arquitetura autenticada BlackICE
- Teste de recurso sessão
- Consulta Graphify sessão
- Consulta Graphify DICOM
- Consulta operacional Graphify
- Containers backend Quarkus
- Tema Keycloak e Playwright
- Identidade visual Keycloak
- Exclusões de corpus Graphify
- Artefatos de container backend
- Referências TypeScript compartilhadas
- Runbook de atualização incremental
- Integração do projeto Graphify
- Migração Keycloak same-origin
- Audiência e autorização DICOMweb
- Monitoramento e ingestão URL
- Exportações e servidor MCP
- Esquema de fragmento Graphify
- Consulta e expansão Graphify
- Reextração incremental Graphify
- Ferramentas Java e Maven
- Sessão BFF com cookie
- Configuração Playwright
- Favicon BlackICE
- Roteador Vue
- Página inicial Vue
- Tipos de sessão Vue
- Baseline DCM4CHEE seguro
- Dados de pixel DICOM
- Pilha DCM4CHEE segura
- Stack Compose canônico
- Estrutura monorepo feature-first
- Acessibilidade do foco
- Proxy Docker e Traefik
- Configuração automatizada Keycloak
- Merge entre repositórios
- Transcrição de mídia
- Execução multiagente
- Regras de ignore backend
- Contexto Docker frontend
- Regras de ignore frontend
- Ponto de montagem Vue
- Ícone Bluesky
- Ícone Discord
- Ícone de documentação
- Ícone GitHub
- Ícone social genérico
- Ícone X social
- Comandos Graphify add watch
- Exportações Graphify
- Extração semântica Graphify
- Merge Graphify entre repositórios
- Hooks Graphify
- Consultas Graphify
- Transcrição Graphify
- Propagação de token usuário
- PatientID e issuer
- Sessão Vue via BFF
- Convenções feature-first Vue
- Especificações e planos históricos
- Configuração de subagentes Codex
- Decisões de arquitetura PACS
- Regras de ignore repositório
- Política de exclusão Graphify
- Serviço SPA frontend
- Banco PostgreSQL de produto
- Coordenadas Maven backend
- Fluxos MVP BlackICE

## God Nodes (most connected - your core abstractions)
1. `compilerOptions` - 15 edges
2. `compilerOptions` - 11 edges
3. `Commit Curator Design` - 11 edges
4. `BlackICE — Login same-origin: tirar o Keycloak da barra de endereços` - 10 edges
5. `Fase 1 — Same-origin atrás do Traefik` - 8 edges
6. `SessionResource` - 6 edges
7. `scripts` - 6 edges
8. `Commit Curator Implementation Plan` - 6 edges
9. `Keycloak Service` - 6 edges
10. `Commit Conventions` - 6 edges

## Surprising Connections (you probably didn't know these)
- `Codex DICOM Reviewer Agent` --semantically_similar_to--> `DICOM Domain Review`  [INFERRED] [semantically similar]
  .codex/agents/dicom-domain-reviewer.toml → .claude/agents/dicom/dicom-domain-reviewer.md
- `Codex Quarkus Backend Agent` --semantically_similar_to--> `BlackICE Quarkus Backend`  [INFERRED] [semantically similar]
  .codex/agents/quarkus-backend.toml → .claude/agents/quarkus/quarkus-backend.md
- `Codex DICOM Viewer Agent` --semantically_similar_to--> `Cornerstone3D Viewer`  [INFERRED] [semantically similar]
  .codex/agents/dicom-viewer-frontend.toml → .claude/agents/vue/dicom-viewer-frontend.md
- `Graphify Output Exclusion` --semantically_similar_to--> `Generated Corpus Exclusion`  [INFERRED] [semantically similar]
  .claudeignore → .graphifyignore
- `Graphify Merge Driver` --conceptually_related_to--> `Project-Scoped Graphify Setup`  [INFERRED]
  .gitattributes → .graphify/setup.ps1

## Import Cycles
- None detected.

## Hyperedges (group relationships)
- **Graphify Project Configuration** — _gitattributes_graphify_merge_driver, _graphify_setup_project_scoped_setup, _graphifyignore_generated_corpus_exclusion [INFERRED 0.85]
- **Graphify Lifecycle** — _agents_skills_graphify_skill_graphify_pipeline, _agents_skills_graphify_skill_semantic_extraction, _agents_skills_graphify_skill_incremental_update [EXTRACTED 1.00]
- **DICOM Delivery Roles** — _claude_agents_dicom_dicom_domain_reviewer_dicom_domain_review, _claude_agents_quarkus_quarkus_backend_quarkus_backend, _claude_agents_vue_dicom_viewer_frontend_cornerstone_viewer [INFERRED 0.85]
- **DICOM Patient Identity Safety** — docs_domains_dicom_semantics_dicom_patient_study_series_instance_hierarchy, docs_domains_dicom_semantics_dicom_uid_identity, docs_domains_dicom_semantics_patientid_with_issuer [EXTRACTED 1.00]
- **BlackICE Same-Origin OIDC Flow** — infra_compose_apps_backend_bff, infra_compose_apps_oidc_frontchannel_backchannel_split, infra_dcm4chee_compose_keycloak, infra_dcm4chee_compose_traefik_auth_router [EXTRACTED 1.00]
- **DCM4CHEE Secure Service Stack** — infra_dcm4chee_compose_ldap, infra_dcm4chee_compose_mariadb, infra_dcm4chee_compose_keycloak, infra_dcm4chee_compose_arc_db, infra_dcm4chee_compose_arc [EXTRACTED 1.00]
- **DICOMweb Operation Roles** — docs_domains_dicom_dicomweb_qido_rs, docs_domains_dicom_dicomweb_wado_rs, docs_domains_dicom_dicomweb_stow_rs [EXTRACTED 1.00]
- **Keycloak login quality workflow** — docs_superpowers_plans_2026_07_26_keycloak_login_theme_blackice_login_theme, docs_superpowers_plans_2026_07_27_keycloak_input_focus_outline_focus_outline_accessibility, docs_superpowers_plans_2026_07_27_keycloak_theme_e2e_ui_regression_playwright_keycloak_theme_regression [INFERRED 0.85]
- **Canonical BlackICE Compose stack** — infra_readme_three_compose_files, infra_compose_traefik_routing, infra_compose_product_postgres_database [EXTRACTED 1.00]
- **Frontend icon sprite collection** — apps_frontend_public_icons_bluesky_icon, apps_frontend_public_icons_discord_icon, apps_frontend_public_icons_documentation_icon, apps_frontend_public_icons_github_icon, apps_frontend_public_icons_social_icon, apps_frontend_public_icons_x_icon [EXTRACTED 1.00]

## Communities (89 total, 54 thin omitted)

### Community 0 - "Fluxo de commit Graphify"
Cohesion: 0.12
Nodes (22): Post-Commit Graph Hook, Claude Commit Curator Agent, Codex Commit Curator Agent, Domain Packs, Graphify Commit Workflow, BlackICE Agent Guidance, Claude Code Guidance, Graphify Architecture (+14 more)

### Community 1 - "Design same-origin Keycloak"
Cohesion: 0.09
Nodes (21): Alternativas descartadas, `apps/backend/src/main/resources/application.properties`, BlackICE — Login same-origin: tirar o Keycloak da barra de endereços, Custo honesto, Custo honesto (revisado), Decisões, Estado atual (verificado em 2026-08-07, stack no ar), Fase 1 — Same-origin atrás do Traefik (+13 more)

### Community 2 - "Configuração TypeScript Node"
Cohesion: 0.10
Nodes (19): compilerOptions, allowImportingTsExtensions, erasableSyntaxOnly, lib, module, moduleDetection, noEmit, noFallthroughCasesInSwitch (+11 more)

### Community 3 - "Configuração TypeScript Aplicação"
Cohesion: 0.11
Nodes (18): compilerOptions, allowArbitraryExtensions, baseUrl, erasableSyntaxOnly, ignoreDeprecations, noFallthroughCasesInSwitch, noUnusedLocals, noUnusedParameters (+10 more)

### Community 4 - "Dependências de desenvolvimento Frontend"
Cohesion: 0.12
Nodes (17): devDependencies, @playwright/test, @types/node, typescript, vite, @vitejs/plugin-vue, vitest, vue-tsc (+9 more)

### Community 5 - "Dependências Vue da aplicação"
Cohesion: 0.12
Nodes (15): dependencies, vue, vue-router, name, private, scripts, build, dev (+7 more)

### Community 6 - "Gates de domínio DICOM"
Cohesion: 0.17
Nodes (13): DICOM Domain Review, Human Semantic Gate, DICOMweb Backend Boundary, BlackICE Quarkus Backend, Cornerstone3D Viewer, Vue Reactivity Boundary, Thin Skill Pattern, Codex DICOM Reviewer Agent (+5 more)

### Community 7 - "Recurso de sessão Quarkus"
Cohesion: 0.29
Nodes (9): SessionResource, SessionResponse, Authenticated, GET, JsonWebToken, Path, PermitAll, Response (+1 more)

### Community 8 - "Rótulos de comunidade Graphify"
Cohesion: 0.17
Nodes (12): Automatic Label Reuse Invalidation, Cluster-Only Workflow, Clustered Communities, Community Label Quality Gate, Community Labels, Community Membership Signatures, Current Community Summaries, Exact Community-Key Coverage (+4 more)

### Community 9 - "Verbos e segurança DICOMweb"
Cohesion: 0.17
Nodes (12): DICOMweb Verbs, QIDO-RS, STOW-RS, WADO-RS, CSRF Protection, Quarkus BFF, Quarkus Domain Pack, Cornerstone3D Viewer (+4 more)

### Community 10 - "Serviços Keycloak e backend"
Cohesion: 0.18
Nodes (11): In-Place Keycloak Realm Rename, Backend BFF Service, OIDC Frontchannel Backchannel Split, DCM4CHEE Archive Service, DCM4CHEE Archive PostgreSQL Service, Keycloak Service, LDAP Service, Keycloak MariaDB Service (+3 more)

### Community 11 - "Script Maven Wrapper"
Cohesion: 0.33
Nodes (6): mvnw script, clean(), die(), exec_maven(), set_java_home(), verbose()

### Community 12 - "Identidade e hierarquia DICOM"
Cohesion: 0.29
Nodes (7): DICOM Domain Pack, DICOM Domain Reviewer, DICOM Patient Study Series Instance Hierarchy, DICOM UID Identity, StudyInstanceUID, Report Model, Domain Pack Wrappers

### Community 13 - "Plano Keycloak same-origin"
Cohesion: 0.29
Nodes (6): Estrutura de arquivos, Global Constraints, Keycloak same-origin — Implementation Plan, Self-review, Task 1: Fase 1 — Keycloak same-origin sob `/auth`, Task 2: Fase 2 — renomear o realm para `blackice`

### Community 14 - "Pipeline incremental Graphify"
Cohesion: 0.33
Nodes (6): Graphify Pipeline, Incremental Update, Semantic Extraction, Graphify Merge Driver, Canonical Skill Integrity Guard, Project-Scoped Graphify Setup

### Community 15 - "Teste E2E Keycloak"
Cohesion: 0.53
Nodes (5): expectVerticallyCentered(), openBlackiceLogin(), requiredBox(), screenshotLoginCard(), showInvalidCredentials()

### Community 16 - "Arquitetura autenticada BlackICE"
Cohesion: 0.50
Nodes (5): Quarkus OIDC BFF Session, Vue SPA Nginx Delivery, Frontend Node Toolchain, Keycloak Login E2E Contract, BlackICE Monorepo Structure

### Community 17 - "Teste de recurso sessão"
Cohesion: 0.60
Nodes (3): SessionResourceTest, QuarkusTest, Test

### Community 18 - "Consulta Graphify sessão"
Cohesion: 0.40
Nodes (4): Answer, Outcome, Q: Como o frontend Vue obtém a sessão autenticada do backend?, Source Nodes

### Community 19 - "Consulta Graphify DICOM"
Cohesion: 0.40
Nodes (4): Answer, Outcome, Q: Quais regras do BlackICE governam StudyInstanceUID e os verbos DICOMweb?, Source Nodes

### Community 20 - "Consulta operacional Graphify"
Cohesion: 0.40
Nodes (4): Answer, Outcome, Q: Onde ficam as instruções operacionais do Graphify no BlackICE?, Source Nodes

### Community 21 - "Containers backend Quarkus"
Cohesion: 0.67
Nodes (4): Maven Wrapper, JVM Backend Container, Quarkus Application Layout, Legacy-JAR Backend Container

### Community 22 - "Tema Keycloak e Playwright"
Cohesion: 0.50
Nodes (4): BlackICE Keycloak login theme, Playwright Keycloak theme UI regression, keycloak.v2 child theme, Hybrid UI regression strategy

### Community 23 - "Identidade visual Keycloak"
Cohesion: 0.50
Nodes (4): BlackICE Keycloak Login Theme, BlackICE Login Branding Messages, BlackICE Login Visual Design, Keycloak v2 Theme Inheritance

### Community 24 - "Exclusões de corpus Graphify"
Cohesion: 0.67
Nodes (3): Graphify Output Exclusion, Claude Skill Mirror Exclusion, Generated Corpus Exclusion

### Community 25 - "Artefatos de container backend"
Cohesion: 1.00
Nodes (3): Backend Container Build Artifacts, Quarkus Micro Native Container, Quarkus Native Container

### Community 27 - "Runbook de atualização incremental"
Cohesion: 0.67
Nodes (3): Cluster-only refresh, Incremental re-extraction runbook, Incremental merge integrity

### Community 28 - "Integração do projeto Graphify"
Cohesion: 0.67
Nodes (3): Graphify Project Integration, Incremental Graph Maintenance, Graphify Integration Design

### Community 29 - "Migração Keycloak same-origin"
Cohesion: 0.67
Nodes (3): Authorization Code with PKCE, Keycloak Same-Origin Migration, Keycloak Same-Origin Design

### Community 30 - "Audiência e autorização DICOMweb"
Cohesion: 0.67
Nodes (3): Shared Realm Audience, blackice-quarkus Client, DICOMweb Authorization

## Knowledge Gaps
- **178 isolated node(s):** `dev.blackice:blackice-backend`, `name`, `private`, `version`, `type` (+173 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **54 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `devDependencies` connect `Dependências de desenvolvimento Frontend` to `Dependências Vue da aplicação`?**
  _High betweenness centrality (0.006) - this node is a cross-community bridge._
- **What connects `dev.blackice:blackice-backend`, `name`, `private` to the rest of the system?**
  _178 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Fluxo de commit Graphify` be split into smaller, more focused modules?**
  _Cohesion score 0.11594202898550725 - nodes in this community are weakly interconnected._
- **Should `Design same-origin Keycloak` be split into smaller, more focused modules?**
  _Cohesion score 0.09090909090909091 - nodes in this community are weakly interconnected._
- **Should `Configuração TypeScript Node` be split into smaller, more focused modules?**
  _Cohesion score 0.1 - nodes in this community are weakly interconnected._
- **Should `Configuração TypeScript Aplicação` be split into smaller, more focused modules?**
  _Cohesion score 0.10526315789473684 - nodes in this community are weakly interconnected._
- **Should `Dependências de desenvolvimento Frontend` be split into smaller, more focused modules?**
  _Cohesion score 0.11764705882352941 - nodes in this community are weakly interconnected._