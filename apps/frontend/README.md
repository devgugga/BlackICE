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

## E2E do tema de login do Keycloak

O E2E consome uma stack BlackICE já ativa; ele não inicia, aguarda nem derruba
serviços. A responsabilidade é da execução local ou do job de CI.

Os manifests canônicos ficam em `infra/`: `compose.yml` (fundação
compartilhada), `dcm4chee/compose.yml` (archive e dependências) e
`compose.apps.yml` (aplicações BlackICE). A partir da raiz do repositório, suba
a stack e aguarde frontend, backend e o início do OIDC responderem. A checagem
não segue redirects: `200` em `/`, `401` esperado em `/api/me` sem sessão e
`302` de `/api/login` para o endpoint de autorização do Keycloak.

```powershell
Set-Location infra
docker compose -f compose.yml -f dcm4chee/compose.yml -f compose.apps.yml up -d --build

$isReady = $false
foreach ($attempt in 1..30) {
  $rootStatus = & curl.exe -sS --max-redirs 0 -o NUL -w '%{http_code}' http://blackice.localhost/
  $meStatus = & curl.exe -sS --max-redirs 0 -o NUL -w '%{http_code}' http://blackice.localhost/api/me
  $loginHeaders = (& curl.exe -sS --max-redirs 0 -D - -o NUL http://blackice.localhost/api/login) -join "`n"
  $loginStatus = [regex]::Match($loginHeaders, '(?im)^HTTP/\S+\s+(\d{3})\b').Groups[1].Value
  $location = [regex]::Match($loginHeaders, '(?im)^location:\s*(\S+)').Groups[1].Value

  if (
    $rootStatus -eq '200' -and
    $meStatus -eq '401' -and
    $loginStatus -eq '302' -and
    $location -match '^http://blackice\.localhost/auth/realms/dcm4chee/protocol/openid-connect/auth\?'
  ) {
    $isReady = $true
    break
  }

  Start-Sleep -Seconds 2
}

if (-not $isReady) {
  throw 'Stack não ficou pronta: esperado / = 200, /api/me = 401 e /api/login = 302 para o Keycloak.'
}
```

Depois, a partir de `apps/frontend`, instale as dependências e compare sempre
na imagem Linux pinada — nunca no browser de um runner Linux arbitrário:

```powershell
Set-Location ../apps/frontend
mise exec -- npm ci
docker run --rm --network host `
  -v "${PWD}:/work" `
  -w /work `
  -e CI=true `
  -e BLACKICE_E2E_URL=http://blackice.localhost `
  mcr.microsoft.com/playwright:v1.62.0-noble `
  npx playwright test e2e/keycloak-login.spec.ts
```

O alvo padrão é `http://blackice.localhost`. Para outro ambiente, substitua o
valor de `BLACKICE_E2E_URL` no comando Docker. Para atualizar baselines, use a
mesma imagem pinada e acrescente `--update-snapshots`; execuções normais nunca
criam snapshots ausentes.

### Contrato para qualquer CI

O job deve:

1. em `infra/`, subir a stack com `docker compose -f compose.yml -f dcm4chee/compose.yml -f compose.apps.yml up -d --build`;
2. aguardar, sem seguir redirects, `/ = 200`, `/api/me = 401` sem sessão e `/api/login = 302` com `Location` para `http://blackice.localhost/auth/realms/dcm4chee/protocol/openid-connect/auth?...`;
3. em `apps/frontend/`, executar `npm ci`;
4. executar o E2E com `CI=true` dentro de `mcr.microsoft.com/playwright:v1.62.0-noble`;
5. publicar `apps/frontend/playwright-report/` e `apps/frontend/test-results/playwright/` em falhas;
6. no cleanup do próprio job, em `infra/`, executar `docker compose -f compose.yml -f dcm4chee/compose.yml -f compose.apps.yml down`.

Os snapshots são baselines Linux. Gere atualizações somente na imagem
`mcr.microsoft.com/playwright:v1.62.0-noble`, usando `--update-snapshots`, e
revise os PNGs antes de versioná-los.

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
