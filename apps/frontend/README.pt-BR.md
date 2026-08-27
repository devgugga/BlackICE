# Frontend BlackICE

<p align="center">
  Language / Idioma: <a href="README.md">🇺🇸 English</a> | <b>🇧🇷 Português</b>
</p>

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
mise exec -- pnpm test:e2e:viewer
mise exec -- pnpm test:e2e:reports
mise exec -- pnpm exec playwright test e2e/problem-details.spec.ts
```

A suíte de testes E2E valida os fluxos completos utilizando fixtures DICOM
sintéticas geradas em memória, garantindo que nenhum dado real de paciente seja
manipulado ou commitado.

### Matriz de Viewports dos Testes E2E:
- **Desktop 1920×1080 (`chromium-desktop`)**: Split layout com viewport Cornerstone (largura >= 720px) e painel lateral de laudo clínico;
- **Laptop 1366×768 (`chromium-1366`)**: Drawer overlay fechado por padrão com abertura e fechamento sem mutação geométrica do viewport;
- **Tablet 1024×768 (`chromium-1024`)**: Drawer overlay com comportamento responsivo idêntico a 1366×768;
- **Mobile 390×844 (`chromium-mobile`)**: Layout somente-laudo com Capability Gate ativo (0 requisições de instâncias/frames DICOM).

### Identidades de teste:
- `dr.teste` / `teste123`: usuário principal (autor);
- `dr.leitor` / `teste123`: segundo ator para teste de permissões somente-leitura, concorrência e rejeição 403 em mutações não autorizadas.

## Funcionalidades e Telas do Frontend

### 1. Importação DICOM (`/ingest`)
Interface de upload manual para envio direto via STOW-RS:
- **Estados da máquina de estados**:
  - `SELECTING`: seleção de múltiplos arquivos `.dcm` por drag-and-drop ou seletor de arquivos;
  - `READY`: arquivos validados localmente (metadados DICOM essenciais) prontos para transmissão;
  - `UPLOADING`: barra de progresso determinada com indicador percentual e opção de cancelamento;
  - `PROCESSING`: transmissão de rede concluída, aguardando persistência e resposta do Archive;
  - `COMPLETE` / `ERROR` / `CANCELLED`: apresentação do resumo consolidado por estudo e opção de nova importação.

### 2. Worklist e Busca Clínica (`/studies`)
Consulta paginada de estudos DICOM via BFF (`GET /api/studies`):
- **Filtros combináveis**: Nome do paciente (`SILVA^JOAO`), ID do paciente com qualificação de emissor (`ID^^^ISSUER`), Modalidade (`CT`, `MR`, `CR`, `OT`, etc.) e Intervalo de datas (AAAA-MM-DD);
- **Paginação sem `COUNT`**: Páginas de 20 itens com detecção antecipada de próxima página por busca com *lookahead* de 21 registros;
- **Adaptação responsiva**: Exibição em tabela tabular completa em desktops (`study-table`) e cartões compactos em telas menores (`study-cards`).

### 3. Visualizador Médico Cornerstone3D (`/viewer/:studyUid`)
Visualizador médico interativo integrado ao Cornerstone3D 5.x:
- **Renderização e Proxy WADO-RS**: Consome o endpoint seguro `/api/dicomweb/.../frames/1` com streaming autenticado;
- **Ferramentas clínicas**: Ajuste dinâmico de contraste e brilho (*Window/Level*), *Zoom*, *Pan*, *Reset* e seletor de séries;
- **Capability Gate**: Em telas mobile (< 768px), desativa o pipeline pesado de WebGL/Cornerstone para priorizar a revisão do laudo clínico e metadados com economia de banda e CPU;
- **Isolamento de Viewport**: Integração com painel lateral de laudos via layout adaptativo sem provocar mutações geométricas indesejadas no canvas.

### 4. Módulo de Laudos Clínicos (`/studies/:studyUid/report` e painel integrado)
Editor clínico com persistência relacional e garantia de integridade:
- **Editor Reativo**: Suporte a texto/markdown com contador dinâmico de caracteres e limite estrito de 32.000 caracteres;
- **Ciclo de Vida**: Transição entre estados de rascunho (`DRAFT`) e documento oficial finalizado (`FINAL`);
- **Concorrência Otimista (`ETag` / `If-Match`)**: Toda mutação valida a versão do laudo; em caso de concorrência com outro operador, a interface captura `412 Precondition Failed` e permite recarregar a versão mais recente sem corrupção de dados;
- **Modal de Finalização Acessível**: Diálogo modal com foco gerenciado, navegação por teclado (tecla Escape) e confirmação expressa;
- **Isolamento Multi-Ator**: Identificação visual de autoria (`dr.teste`) com desabilitação de edição e proteção contra mutação por terceiros (`dr.leitor`);
- **Preservação de Rascunho**: Persistência temporária no cliente para proteção contra navegação acidental ou perda de foco.

## Tratamento de Erros e Catálogo RFC 9457

`src/shared/api/problems/` é a única fronteira de erro do SPA:

- `parse-problem.ts` transforma qualquer resposta HTTP `4xx/5xx` em `ApiError` tipado pelo catálogo, ou em falha local `CLIENT_*` quando a resposta não corresponde ao contrato. `fetch` e XHR compartilham o mesmo núcleo de parsing;
- `problem-messages.pt-BR.ts` guarda o texto em PT-BR exibido ao usuário final. O mapeamento é exaustivo por construção: um novo código no catálogo quebra o build até ganhar uma mensagem correspondente;
- `*.generated.ts` são gerados automaticamente pelo tooling em `.problem-catalog/` e nunca são editados à mão;
- O campo `detail` do backend (voltado ao operador) nunca é renderizado para o usuário comum; o `TraceID` aparece como referência copiável para suporte em caso de falhas;
- Retentativa de ação só é oferecida na interface quando a política do problema for `retryPolicy === 'MANUAL'`;
- Cancelamento solicitado pelo operador é tratado como controle de fluxo, nunca como erro.

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
na imagem Linux pinada (nunca no browser de um runner Linux arbitrário):

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
