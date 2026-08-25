# Estrutura do projeto BlackICE

Esta é a fonte canônica operacional da estrutura do repositório. Humanos e
agentes devem lê-la antes de criar ou mover código.

## Monorepo

```text
BlackICE/
├─ apps/
│  ├─ backend/
│  └─ frontend/
├─ infra/
│  ├─ compose.yml
│  ├─ compose.apps.yml
│  ├─ dcm4chee/
│  │  └─ compose.yml
│  ├─ keycloak/
│  └─ traefik/
├─ docs/
│  ├─ architecture/
│  ├─ contracts/
│  ├─ domains/
│  └─ superpowers/
├─ .problem-catalog/
├─ .github/
│  └─ workflows/
├─ .claude/
├─ .codex/
├─ .agents/
│  ├─ agents/
│  └─ skills/
│     ├─ agent-authoring/
│     └─ graphify/
├─ .graphify/
├─ .graphifyignore
├─ graphify-out/
├─ AGENTS.md
├─ CLAUDE.md
├─ README.md
└─ .gitignore
```

- `apps/` contém os executáveis do produto. `apps/backend/` é a API/BFF
  Quarkus; `apps/frontend/` é a SPA Vue.
- `infra/` contém somente composição e configuração operacional local.
- `docs/` contém arquitetura, Domain Packs e registros históricos de specs e
  planos.
- `docs/architecture/evolution-backlog.md` registra melhorias adiadas, seus
  gatilhos de retomada e a spec de origem; não autoriza implementação sem
  priorização humana.
- `docs/contracts/` contém contratos publicados que atravessam backend e
  frontend. `docs/contracts/problems/` é o registry de Problem Details: fonte
  machine-readable, schema, lock e catálogo humano gerado.
- `.problem-catalog/` é o tooling Node que valida esse registry e gera os
  artefatos Java e TypeScript. É tooling de engenharia, como `.graphify/`, não
  código de produto nem uma terceira aplicação.
- `.github/workflows/verify.yml` executa a verificação do catálogo antes dos
  builds do backend e do frontend.
- `.claude/`, `.codex/` e `.agents/agents/` são pontos de descoberta específicos,
  respectivamente, para Claude, Codex e Antigravity. Seus wrappers apontam para
  o conhecimento neutro em `docs/domains/`.
- `.claude/skills/` e `.agents/skills/` descobrem workflows repetíveis. A primeira
  é específica de Claude; a segunda é compartilhada por Codex e Antigravity.
  Ambas são wrappers para o conhecimento neutro em `docs/domains/`.
- `.agents/skills/graphify/`, `.graphifyignore` e `graphify-out/` são tooling de
  engenharia e de agentes: respectivamente a skill compartilhada, a política do
  corpus e os artefatos portáveis do grafo. Não são código de aplicação nem
  infraestrutura de produção.
- `.graphify/` contém somente o adaptador, os testes e o overlay reproduzível da
  versão do Graphify fixada pelo projeto.

Não crie outra pasta raiz de aplicação. Código de produto pertence a uma
aplicação em `apps/`.

## Estrutura atual dos módulos

O backend é modular e possui `ingest`, `reports`, `security`, `session`, `viewer` e `worklist`:

```text
apps/backend/src/
├─ main/
│  ├─ java/dev/blackice/
│  │  ├─ ingest/
│  │  │  ├─ api/
│  │  │  ├─ application/
│  │  │  │  ├─ input/
│  │  │  │  ├─ usecase/
│  │  │  │  ├─ validation/
│  │  │  │  ├─ result/
│  │  │  │  ├─ exception/
│  │  │  │  └─ port/
│  │  │  └─ infrastructure/
│  │  │     ├─ dicom/
│  │  │     └─ dicomweb/
│  │  ├─ reports/
│  │  │  ├─ api/
│  │  │  ├─ application/
│  │  │  │  ├─ exception/
│  │  │  │  ├─ input/
│  │  │  │  ├─ port/
│  │  │  │  ├─ result/
│  │  │  │  └─ usecase/
│  │  │  ├─ domain/
│  │  │  └─ infrastructure/
│  │  │     ├─ dicomweb/
│  │  │     └─ persistence/
│  │  ├─ security/
│  │  │  ├─ api/
│  │  │  ├─ application/
│  │  │  └─ infrastructure/oidc/
│  │  ├─ session/
│  │  │  └─ api/
│  │  ├─ shared/
│  │  │  ├─ api/problem/
│  │  │  │  └─ generated/
│  │  │  └─ infrastructure/telemetry/
│  │  ├─ viewer/
│  │  │  ├─ api/
│  │  │  ├─ application/
│  │  │  │  ├─ exception/
│  │  │  │  ├─ input/
│  │  │  │  ├─ port/
│  │  │  │  ├─ result/
│  │  │  │  └─ usecase/
│  │  │  └─ infrastructure/
│  │  │     └─ dicomweb/
│  │  └─ worklist/
│  │     ├─ api/
│  │     ├─ application/
│  │     │  ├─ input/
│  │     │  ├─ usecase/
│  │     │  ├─ result/
│  │     │  ├─ exception/
│  │     │  └─ port/
│  │     └─ infrastructure/dicomweb/
│  └─ resources/
│     ├─ application.properties
│     └─ db/migration/
│        └─ V1__create_reports.sql
└─ test/java/dev/blackice/
   ├─ architecture/
   ├─ ingest/
   ├─ reports/
   ├─ security/
   ├─ session/
   ├─ viewer/
   └─ worklist/
```

O frontend possui as features `session`, `home`, `ingest`, `viewer`, `worklist` e `reports`, compostas pelo shell em
`app/`:

```text
apps/frontend/src/
├─ app/
│  ├─ App.vue
│  └─ router/
│     └─ index.ts
├─ shared/
│  └─ api/
│     └─ problems/     ← parser, ApiError, mensagens PT-BR e tipos gerados
├─ features/
│  ├─ session/
│  │  ├─ session.api.ts
│  │  ├─ session.api.spec.ts
│  │  └─ session.types.ts
│  ├─ home/
│  │  └─ HomePage.vue
│  ├─ ingest/
│  │  ├─ IngestPage.vue
│  │  ├─ ingest.api.ts
│  │  ├─ ingest.api.spec.ts
│  │  ├─ ingest.types.ts
│  │  └─ useIngestBatch.ts
│  ├─ reports/
│  │  ├─ ReportEditor.vue
│  │  ├─ ReportEditor.spec.ts
│  │  ├─ ReportPanel.vue
│  │  ├─ ReportPanel.spec.ts
│  │  ├─ report.api.ts
│  │  ├─ report.api.spec.ts
│  │  ├─ report.types.ts
│  │  ├─ useReportLayout.ts
│  │  ├─ useReportLayout.spec.ts
│  │  ├─ useStudyReport.ts
│  │  └─ useStudyReport.spec.ts
│  ├─ viewer/
│  │  ├─ cornerstone/
│  │  │  ├─ cornerstone-init.ts
│  │  │  ├─ viewer-metadata.ts
│  │  │  ├─ viewer-metadata.spec.ts
│  │  │  ├─ viewer-runtime.ts
│  │  │  └─ viewer-runtime.spec.ts
│  │  ├─ DicomViewport.vue
│  │  ├─ DicomViewport.spec.ts
│  │  ├─ loadDicomViewport.ts
│  │  ├─ SeriesRail.vue
│  │  ├─ SeriesRail.spec.ts
│  │  ├─ StudyHeader.vue
│  │  ├─ StudyHeader.spec.ts
│  │  ├─ useStudyViewer.ts
│  │  ├─ useStudyViewer.spec.ts
│  │  ├─ useViewerCapability.ts
│  │  ├─ useViewerCapability.spec.ts
│  │  ├─ viewer.api.ts
│  │  ├─ viewer.api.spec.ts
│  │  ├─ ViewerPage.vue
│  │  ├─ ViewerPage.spec.ts
│  │  ├─ ViewerToolbar.vue
│  │  ├─ ViewerToolbar.spec.ts
│  │  └─ viewer.types.ts
│  └─ worklist/
│     ├─ WorklistPage.vue
│     ├─ WorklistFilters.vue
│     ├─ WorklistTable.vue
│     ├─ WorklistCards.vue
│     ├─ WorklistPagination.vue
│     ├─ worklist.api.ts
│     ├─ worklist.api.spec.ts
│     ├─ worklist.types.ts
│     ├─ useWorklist.ts
│     └─ useWorklist.spec.ts
└─ main.ts
```

## Regras de dependência

### Quarkus

- Cada módulo de negócio começa em `dev.blackice.<module>` e só cria subpacotes
  quando houver uma responsabilidade concreta.
- `api` contém recursos, a adaptação HTTP e a política de status; `application`
  contém casos de uso e modelos independentes de transporte;
  `application.port` declara contratos consumidos pelo caso de uso;
  `infrastructure` implementa portas e integra frameworks ou serviços.
- Quando a responsabilidade justificar, `application` é organizado em
  `input`, `usecase`, `validation`, `result` e `exception`. `ingest` usa essa
  divisão porque valida DICOM, orquestra STOW e produz resultados próprios; um
  módulo menor não deve criar esses pacotes por antecipação.
- A direção é `api -> application <- infrastructure`. A aplicação não importa
  HTTP ou implementações concretas; um módulo não importa a infraestrutura
  interna de outro.
- `domain` é interno ao módulo e só surge para regras puras, identidade ou
  invariantes independentes de framework e I/O.
- Testes espelham os pacotes de produção sob `src/test/java`; regras de
  fronteira são verificadas em `dev.blackice.architecture`.
- Não crie camadas globais `controller/`, `service/`, `repository`, `dto`,
  `validator` ou `exception`.
- `dev.blackice.shared` existe por exceção justificada, não por antecipação: o
  contrato de erro e a propagação de trace têm consumidores reais em `ingest`,
  `worklist` e `viewer`. Ele hospeda apenas fronteira transversal —
  `api.problem` e `infrastructure.telemetry` — e nunca regra de uma feature;
  mappers específicos permanecem em `ingest.api`, `viewer.api`, `worklist.api` e
  nas demais features. Um `shared` novo obedece à mesma prova de dois consumidores.

### Vue

- `app/` é apenas o shell de composição: aplicação, router e configuração
  transversal mínima.
- Componentes, API, tipos, composables e testes de um fluxo ficam em
  `features/<name>/`.
- O router central compõe páginas; regra de negócio permanece nas features.
- Testes unitários ficam ao lado do arquivo testado.
- Imports internos usam o alias `@/`, configurado no TypeScript e no Vite.

### Entre áreas

- O frontend depende do backend apenas pelos contratos HTTP publicados; não
  importa código Java nem conhece detalhes internos do DCM4CHEE.
- O backend acessa o DCM4CHEE somente por DICOMweb e não armazena pixel data.
- Infraestrutura referencia os artefatos das aplicações, mas código de produto
  não depende de arquivos de `infra/`.
- Regras de domínio continuam nos [Domain Packs](../domains/README.md), sem
  duplicação em wrappers de agentes ou nesta página.

## Adicionar módulo Quarkus

1. Crie `apps/backend/src/main/java/dev/blackice/<module>/` quando houver o
   primeiro fluxo real.
2. Comece com o pacote mais simples que explique a responsabilidade. Crie `api`,
   `application`, `application.port`, `infrastructure` ou `domain` somente se a
   fronteira correspondente existir. Dentro de `application`, crie `input`,
   `usecase`, `validation`, `result` ou `exception` somente quando a separação
   tornar o fluxo mais legível.
3. Crie o pacote espelhado sob
   `apps/backend/src/test/java/dev/blackice/<module>/` e teste o comportamento.
4. Se outro módulo precisar integrar, exponha uma interface pública em
   `application`; não importe detalhes de `infrastructure`.
5. Atualize as regras ArchUnit se uma fronteira nova exigir proteção e execute
   `mise exec -- mvn test -Dquarkus.http.test-port=8082` em `apps/backend/`
   quando o dashboard Traefik local ocupar a porta padrão de teste.

Não crie primeiro um módulo vazio nem distribua seu código em pacotes técnicos
globais.

## Adicionar feature Vue

1. Crie `apps/frontend/src/features/<name>/` quando houver a primeira
   implementação real.
2. Colocalize nesse diretório a página, os componentes, a API, os tipos,
   composables e testes pertencentes ao fluxo.
3. Registre a página no router em `apps/frontend/src/app/router/index.ts`; deixe
   a lógica de negócio na feature.
4. Use imports internos com `@/`, inclusive entre o router e a página.
5. Execute `mise exec -- pnpm test` e `mise exec -- pnpm build` em
   `apps/frontend/`.

Não crie features vazias para trabalho futuro.

## Quando promover para `shared/`

Código só pode migrar para `shared/` com ao menos dois consumidores reais. Até
lá, ele pertence à feature que o utiliza. A promoção deve preservar uma
responsabilidade clara; `shared/utils/` genérico não é uma arquitetura.

`shared/api/problems/` é o caso já aprovado: o parser de Problem Details, os
tipos gerados e o mapa de mensagens PT-BR são consumidos por `session`,
`worklist`, `ingest` e `viewer`. Os arquivos `*.generated.ts` são saída de
`.problem-catalog/` e não são editados à mão.

## Antipadrões

- features vazias criadas por antecipação;
- diretórios `utils/` genéricos;
- árvores globais `controller/`, `service/` e `repository/`;
- código exclusivo de uma feature em `shared/`;
- conhecimento de domínio copiado dos Domain Packs para agentes ou READMEs.
