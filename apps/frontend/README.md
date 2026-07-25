# Frontend BlackICE

SPA do BlackICE construída com Vue 3, Vite e TypeScript.

## Toolchain

O `mise.toml` fixa Node 24:

```powershell
mise install
mise exec -- node --version
mise exec -- npm ci
```

## Testar e construir

```powershell
mise exec -- npm test
mise exec -- npm run build
```

## Desenvolvimento

```powershell
mise exec -- npm run dev
```

## Organização

O shell mínimo fica em `src/app/`. Cada fluxo fica em
`src/features/<name>/`, com API, tipos, componentes, composables e testes
colocalizados. Imports internos usam `@/`, e páginas são registradas pelo router
em `src/app/router/index.ts`.

Leia a [estrutura canônica](../../docs/architecture/project-structure.md) antes
de adicionar uma feature e as
[convenções Vue](../../docs/domains/vue/conventions.md) antes de alterar o
viewer ou o fluxo de sessão.

## Sessão BFF

O frontend chama o backend na mesma origem. `/api/me` é a fonte da sessão e
`/api/login` inicia o login quando o usuário está anônimo. A sessão é enviada em
cookie HttpOnly; nenhum access token fica disponível no browser ou é persistido
pelo JavaScript.
