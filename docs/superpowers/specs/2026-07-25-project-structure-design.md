# BlackICE — Design de reorganização do monorepo

> Design aprovado em brainstorming em 2026-07-25. Esta reorganização acontece
> em uma única mudança porque o projeto ainda está no início e possui pouco
> código de aplicação.

## Objetivo

Organizar o BlackICE como um monorepo profissional e fácil de estender, com as
aplicações agrupadas em `apps/`, código orientado a features e regras explícitas
para que humanos e agentes repliquem o padrão sem inferi-lo.

A migração é apenas estrutural: contratos HTTP, autenticação, nomes de serviços
Docker, volumes e comportamento funcional permanecem inalterados.

## Estrutura do monorepo

```text
BlackICE/
├─ apps/
│  ├─ backend/
│  └─ frontend/
├─ infra/
├─ docs/
├─ .claude/
├─ .codex/
├─ AGENTS.md
├─ CLAUDE.md
├─ README.md
└─ .gitignore
```

- `apps/` contém executáveis do produto.
- `infra/` contém composição e configuração operacional local.
- `docs/` contém arquitetura, Domain Packs, specs e planos.
- `.claude/` e `.codex/` permanecem na raiz porque são pontos de descoberta das
  respectivas ferramentas.
- `.superpowers/` continua sendo área temporária ignorada pelo Git.

## Backend Quarkus

O backend usa pacotes pragmáticos por feature:

```text
apps/backend/src/
├─ main/
│  ├─ java/dev/blackice/
│  │  └─ features/
│  │     └─ session/
│  │        ├─ SessionResource.java
│  │        └─ SessionResponse.java
│  ├─ resources/application.properties
│  └─ docker/
└─ test/java/dev/blackice/
   └─ features/session/
      └─ SessionResourceTest.java
```

Regras:

- Cada feature começa como um pacote plano e autocontido.
- `Resource`, `Service`, `Repository`, `Client`, entidades e DTOs ficam junto da
  feature a que pertencem e só são criados quando necessários.
- Não existem árvores técnicas globais como `controller/`, `service/` e
  `repository/`.
- Uma feature não importa detalhes internos de outra; qualquer integração entre
  features usa uma interface pública explícita.
- `shared/` só pode receber código usado por pelo menos duas features e nunca
  contém regra de negócio exclusiva de uma delas.
- Qualquer subdivisão arquitetural mais profunda exige uma nova decisão de
  design; não se criam antecipadamente camadas `domain/application/infrastructure`.
- Testes espelham o pacote da feature em `src/test`.

A implementação atual migra de `dev.blackice.api` para
`dev.blackice.features.session`. `MeResource` passa a se chamar
`SessionResource`; `/api/me`, `/api/login` e `SessionResponse` não mudam.

## Frontend Vue

O frontend também é orientado a features, com shell mínimo em `app/`:

```text
apps/frontend/src/
├─ app/
│  ├─ App.vue
│  └─ router/
│     └─ index.ts
├─ features/
│  ├─ session/
│  │  ├─ session.api.ts
│  │  ├─ session.api.spec.ts
│  │  └─ session.types.ts
│  └─ home/
│     └─ HomePage.vue
└─ main.ts
```

Regras:

- Componentes, API, tipos, composables e testes pertencentes a um fluxo ficam
  dentro da mesma feature.
- Testes unitários ficam ao lado do arquivo testado.
- O router central apenas compõe rotas; lógica de negócio permanece nas
  features.
- Imports internos usam o alias `@/`, configurado de forma consistente no
  TypeScript e no Vite.
- Código só migra para `shared/` quando tiver ao menos dois consumidores reais.
- As futuras features `worklist`, `ingest`, `viewer` e `reports` só serão criadas
  quando receberem a primeira implementação.
- Autenticação segue o BFF aprovado: cookie de sessão HttpOnly, sem token Bearer
  no JavaScript. O frontend consulta `/api/me` e navega para `/api/login` quando
  anônimo.

## Infraestrutura

```text
infra/
├─ compose.yml
├─ compose.apps.yml
├─ .env.example
├─ README.md
├─ dcm4chee/
│  ├─ compose.yml
│  └─ README.md
├─ keycloak/
│  ├─ configure-blackice.sh
│  └─ README.md
└─ traefik/
   └─ docker-api-proxy/
      └─ nginx.conf
```

O comando canônico de subida é:

```powershell
cd infra
docker compose -f compose.yml -f dcm4chee/compose.yml -f compose.apps.yml up -d --build
```

Os contextos de build passam a apontar para `../apps/backend` e
`../apps/frontend`. Serviços, redes e volumes mantêm os nomes atuais para evitar
mudança de topologia ou recriação de dados.

## Documentação e replicação

- `docs/architecture/project-structure.md` será a fonte canônica operacional da
  estrutura, com a árvore, regras e receitas para adicionar features Quarkus e
  Vue.
- `AGENTS.md` terá um resumo obrigatório e exigirá a leitura desse documento
  antes de criar ou mover código.
- `docs/domains/quarkus/conventions.md` e
  `docs/domains/vue/conventions.md` aplicarão o padrão às respectivas stacks sem
  duplicar a regra geral.
- A convenção Vue será corrigida para remover a orientação antiga de Bearer
  token no navegador e refletir o BFF com cookie HttpOnly.
- O `README.md` da raiz será a entrada canônica para humanos e agentes, com mapa
  do repositório, comandos e links.
- Os READMEs genéricos gerados por Quarkus e Vite serão substituídos por guias
  específicos do BlackICE.
- Specs e planos anteriores permanecem como registro histórico. Uma nota
  explica que caminhos antigos devem ser interpretados conforme a estrutura
  canônica atual; o conteúdo histórico não será reescrito silenciosamente.

## Estratégia de migração e validação

- Realizar a reorganização em uma única mudança rastreável pelo Git, sem manter
  pastas duplicadas ou aliases de compatibilidade permanentes.
- Atualizar todos os imports, pacotes Java, contextos Docker, mounts,
  `.gitignore`, documentação ativa e comandos afetados.
- Executar testes Quarkus em `apps/backend`.
- Executar testes e build TypeScript/Vite em `apps/frontend`.
- Validar a combinação dos três arquivos Compose com `docker compose config`.
- Construir as imagens de backend e frontend usando os novos contextos.
- Com a stack disponível, verificar que `/api/me` retorna `401` anonimamente e
  que `/api/login` continua iniciando o fluxo OIDC.
- Buscar referências ativas aos caminhos e nomes antigos. Apenas registros
  históricos explicitamente documentados podem permanecer.
- Confirmar que nenhum volume foi renomeado e nenhum arquivo `.env` foi
  versionado.

## Critérios de aceite

1. O repositório possui uma única estrutura canônica sob `apps/`.
2. Backend e frontend seguem feature-first conforme este design.
3. Testes e builds das duas aplicações passam nos novos caminhos.
4. A configuração Compose combinada é válida e preserva serviços, redes e
   volumes.
5. O fluxo BFF atual e seus contratos HTTP não mudam.
6. Humanos e agentes conseguem criar uma nova feature seguindo apenas
   `docs/architecture/project-structure.md`.
7. A documentação ativa não orienta o uso dos caminhos antigos nem de Bearer
   token no frontend.
