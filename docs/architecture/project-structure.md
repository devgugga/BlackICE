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
│  ├─ domains/
│  └─ superpowers/
├─ .claude/
├─ .codex/
├─ .graphify/
├─ .agents/skills/graphify/
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
- `.claude/` e `.codex/` são pontos de descoberta específicos das ferramentas.
  Seus wrappers apontam para o conhecimento neutro em `docs/domains/`.
- `.agents/skills/graphify/`, `.graphifyignore` e `graphify-out/` são tooling de
  engenharia e de agentes: respectivamente a skill compartilhada, a política do
  corpus e os artefatos portáveis do grafo. Não são código de aplicação nem
  infraestrutura de produção.
- `.graphify/` contém somente o adaptador, os testes e o overlay reproduzível da
  versão do Graphify fixada pelo projeto.

Não crie outra pasta raiz de aplicação. Código de produto pertence a uma
aplicação em `apps/`.

## Estrutura atual das features

O backend possui a feature `session`:

```text
apps/backend/src/
├─ main/
│  ├─ java/dev/blackice/
│  │  └─ features/
│  │     └─ session/
│  │        ├─ SessionResource.java
│  │        └─ SessionResponse.java
│  └─ resources/
│     └─ application.properties
└─ test/
   └─ java/dev/blackice/
      └─ features/
         └─ session/
            └─ SessionResourceTest.java
```

O frontend possui as features `session` e `home`, compostas pelo shell em
`app/`:

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

## Regras de dependência

### Quarkus

- Cada feature começa como um pacote plano e autocontido em
  `dev.blackice.features.<name>`.
- Resources, services, repositories, clients, entidades e DTOs ficam na feature
  que os possui e só surgem quando necessários.
- Uma feature não importa detalhes internos de outra. A integração entre
  features exige uma interface pública explícita.
- Testes espelham o pacote da feature sob `src/test/java`.
- Não crie camadas globais `controller/`, `service/` ou `repository/`, nem
  antecipe árvores `domain/application/infrastructure`.

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

## Adicionar feature Quarkus

1. Crie `apps/backend/src/main/java/dev/blackice/features/<name>/`.
2. Crie apenas os Resources, DTOs e colaboradores exigidos pelo primeiro fluxo,
   todos dentro desse pacote.
3. Crie o pacote espelhado
   `apps/backend/src/test/java/dev/blackice/features/<name>/` e adicione o teste
   do comportamento.
4. Mantenha rotas e DTOs junto da feature. Se outra feature precisar integrar,
   exponha uma interface pública explícita.
5. Execute `mise exec -- mvn test` em `apps/backend/`.

Não crie primeiro uma feature vazia nem distribua seu código em pacotes técnicos
globais.

## Adicionar feature Vue

1. Crie `apps/frontend/src/features/<name>/` quando houver a primeira
   implementação real.
2. Colocalize nesse diretório a página, os componentes, a API, os tipos,
   composables e testes pertencentes ao fluxo.
3. Registre a página no router em `apps/frontend/src/app/router/index.ts`; deixe
   a lógica de negócio na feature.
4. Use imports internos com `@/`, inclusive entre o router e a página.
5. Execute `mise exec -- npm test` e `mise exec -- npm run build` em
   `apps/frontend/`.

Não crie features vazias para trabalho futuro.

## Quando promover para `shared/`

Código só pode migrar para `shared/` com ao menos dois consumidores reais. Até
lá, ele pertence à feature que o utiliza. A promoção deve preservar uma
responsabilidade clara; `shared/utils/` genérico não é uma arquitetura.

## Antipadrões

- features vazias criadas por antecipação;
- diretórios `utils/` genéricos;
- árvores globais `controller/`, `service/` e `repository/`;
- código exclusivo de uma feature em `shared/`;
- conhecimento de domínio copiado dos Domain Packs para agentes ou READMEs.
