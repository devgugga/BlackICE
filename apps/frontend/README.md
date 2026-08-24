# Frontend BlackICE

SPA do BlackICE construída com Vue 3, Vite e TypeScript.

## Toolchain

O `mise.toml` fixa Node 24 e pnpm:

```powershell
mise install
mise exec -- node --version
mise exec -- pnpm --version
mise exec -- pnpm install --frozen-lockfile
```

## Testar e construir

```powershell
mise exec -- pnpm test
mise exec -- pnpm build
mise exec -- pnpm test:e2e:keycloak
mise exec -- pnpm test:e2e:ingest
mise exec -- pnpm test:e2e:worklist
mise exec -- pnpm exec playwright test e2e/problem-details.spec.ts
```

A suíte de testes E2E valida os fluxos completos utilizando fixtures DICOM
sintéticas geradas em memória, garantindo que nenhum dado real de paciente seja
manipulado ou commitado.

### Estados da interface de importação (`/ingest`):
- `SELECTING`: seleção de múltiplos arquivos `.dcm`;
- `READY`: arquivos validados localmente prontos para envio;
- `UPLOADING`: barra de progresso determinada com opção de cancelamento;
- `PROCESSING`: envio concluído, aguardando resposta STOW-RS do Archive;
- `COMPLETE` / `ERROR` / `CANCELLED`: apresentação dos resultados detalhados por estudo e opção de nova importação.

## Tratamento de erros

`src/shared/api/problems/` é a única fronteira de erro do SPA:

- `parse-problem.ts` transforma qualquer resposta em `ApiError` tipado pelo
  catálogo, ou em falha local `CLIENT_*` quando a resposta não corresponde ao
  contrato. `fetch` e XHR usam o mesmo núcleo;
- `problem-messages.pt-BR.ts` guarda o texto exibido ao usuário. O mapa é
  exaustivo por construção: um code novo no catálogo quebra o build até ganhar
  mensagem;
- `*.generated.ts` vêm de `.problem-catalog/` e não são editados à mão;
- o `detail` do backend nunca é renderizado; o TraceID aparece como referência
  copiável apenas em falhas;
- retentativa só é oferecida quando `retryPolicy === 'MANUAL'`;
- cancelamento pedido pelo usuário é controle de fluxo, não erro.

### Worklist e busca (`/studies`):
A página de Worklist consome o endpoint `GET /api/studies` via BFF e apresenta
estudos DICOM paginados com suporte a quatro filtros combináveis:
- **Nome do paciente**: busca textual (ex.: `SILVA^JOAO`);
- **ID do paciente**: identificador do paciente (suporta qualificação com issuer `ID^^^ISSUER`);
- **Modalidade**: filtro por modalidade de estudo (ex.: `CT`, `MR`, `OT`, etc.);
- **Intervalo de datas**: campos `Data inicial` e `Data final` (formato AAAA-MM-DD).

A paginação é orientada a páginas de 20 itens (`limit=20`, `offset=0, 20, 40...`) com
detecção de próxima página por busca de 21 registros, sem consultas adicionais de contagem
(`count`), e timeout padrão de 10 segundos configurado em `blackice.worklist.request-timeout`.
A interface adapta a visualização automaticamente: tabela em telas desktop (`study-table`)
e cartões resumidos em telas móveis (`study-cards`).

A evolução de estratégias de paginação (cursor/snapshot/projeção dedicada de leitura)
é governada pelo item `EVO-005` do backlog de evolução.

## Desenvolvimento

```powershell
mise exec -- pnpm dev
```

## E2E na stack local

Os testes E2E consomem uma stack BlackICE já ativa; eles não iniciam, aguardam nem derrubam
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
    $location -match '^http://blackice\.localhost/auth/realms/blackice/protocol/openid-connect/auth\?'
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

Depois, a partir de `apps/frontend`, instale as dependências e execute os testes
na imagem Linux pinada — nunca no browser de um runner Linux arbitrário:

### E2E do Tema de Login Keycloak
```powershell
Set-Location ../apps/frontend
mise exec -- pnpm install --frozen-lockfile
docker run --rm --network host `
  -v "${PWD}:/work" `
  -w /work `
  -e CI=true `
  -e BLACKICE_E2E_URL=http://blackice.localhost `
  mcr.microsoft.com/playwright:v1.62.0-noble `
  npx playwright test e2e/keycloak-login.spec.ts
```

### E2E da Worklist e Importação Concorrente
```powershell
Set-Location ../apps/frontend
mise exec -- pnpm install --frozen-lockfile
docker run --rm --network host `
  -v "${PWD}:/work" `
  -w /work `
  -e CI=true `
  -e BLACKICE_E2E_URL=http://blackice.localhost `
  mcr.microsoft.com/playwright:v1.62.0-noble `
  npx playwright test e2e/worklist.spec.ts
```

O alvo padrão é `http://blackice.localhost`. Para outro ambiente, substitua o
valor de `BLACKICE_E2E_URL` no comando Docker. Para atualizar baselines visuais, use a
mesma imagem pinada e acrescente `--update-snapshots`; execuções normais nunca
criam snapshots ausentes.

### Observação de Locks Concorrentes no Archive
Durante a execução de operações concorrentes de importação (STOW-RS) e consulta (QIDO-RS),
monitore a ausência de locks bloqueantes no PostgreSQL do Archive executando a partir de `infra/`:

```bash
docker compose -f compose.yml -f dcm4chee/compose.yml -f compose.apps.yml exec -T arc-db sh -lc \
  'for sample in $(seq 1 20); do psql -At -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1 -c "SELECT count(*) FROM pg_locks WHERE NOT granted;"; sleep 0.5; done'
```

**Interpretação**: Todas as 20 linhas de saída devem ser `0`. Locks concedidos normais (`AccessShareLock`)
são esperados; qualquer contagem não nula (`> 0`) indica contenção bloqueante que requer diagnóstico.

### Contrato para qualquer CI

O job deve:

1. em `infra/`, subir a stack com `docker compose -f compose.yml -f dcm4chee/compose.yml -f compose.apps.yml up -d --build`;
2. aguardar, sem seguir redirects, `/ = 200`, `/api/me = 401` sem sessão e `/api/login = 302` com `Location` para `http://blackice.localhost/auth/realms/blackice/protocol/openid-connect/auth?...`;
3. em `apps/frontend/`, executar `pnpm install --frozen-lockfile`;
4. executar os testes E2E com `CI=true` dentro de `mcr.microsoft.com/playwright:v1.62.0-noble`;
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
