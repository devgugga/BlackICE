# BlackICE

BlackICE é um PACS de portfólio e aprendizado. O DCM4CHEE Archive 5.x cuida do
armazenamento e dos protocolos DICOM; o Quarkus implementa o domínio de produto
e expõe um BFF autenticado por Keycloak; o Vue 3 entrega a interface e o viewer
Cornerstone3D.

O MVP cobre quatro fluxos ponta a ponta: ingestão STOW-RS, worklist e busca
QIDO-RS, visualização WADO-RS e laudos autenticados.

## Mapa do monorepo

- `apps/backend/`: backend Quarkus e seus testes.
- `apps/frontend/`: SPA Vue 3 + Vite e seus testes.
- `infra/`: Compose e configuração local do DCM4CHEE, Keycloak e aplicações.
- `docs/`: arquitetura, Domain Packs e registros históricos de design.
- `.claude/` e `.codex/`: pontos de descoberta dos agentes; o conhecimento
  compartilhado permanece nos Domain Packs.

Antes de criar ou mover código, leia a
[estrutura canônica](docs/architecture/project-structure.md).

## Graphify

O grafo compartilhado do repositório fica em `graphify-out/`. Agentes devem
consultá-lo antes de fazer buscas amplas no código; o uso, a atualização e a
política de versionamento estão no [guia do Graphify](docs/architecture/graphify.md).

## Pré-requisitos

- Git;
- [mise](https://mise.jdx.dev/) para instalar Java 21, Maven e Node 24;
- Docker com Docker Compose v2 para a stack local.

## Backend

```powershell
cd apps/backend
mise install
mise exec -- mvn test
mise exec -- mvn package
```

Consulte [o guia do backend](apps/backend/README.md) para desenvolvimento local
e configuração OIDC.

## Frontend

```powershell
cd apps/frontend
mise install
mise exec -- npm ci
mise exec -- npm test
mise exec -- npm run build
```

Consulte [o guia do frontend](apps/frontend/README.md) para desenvolvimento
local e para o contrato de sessão BFF.

## Stack local

Crie `infra/.env` a partir de `infra/.env.example` e execute:

```powershell
cd infra
docker compose -f compose.yml -f dcm4chee/compose.yml -f compose.apps.yml up -d --build
```

Mais detalhes estão no [guia de infraestrutura](infra/README.md).

## Documentação

- [Estrutura do projeto](docs/architecture/project-structure.md): regras
  operacionais e receitas para novas features.
- [Arquitetura do DCM4CHEE](docs/architecture/dcm4chee-archive.md): baseline do
  archive.
- [Domain Packs](docs/domains/README.md): conhecimento canônico de DICOM,
  Quarkus e Vue compartilhado pelos agentes.
- [Specs e planos](docs/superpowers/README.md): registro histórico das decisões.
