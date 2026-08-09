# BlackICE — Login same-origin: tirar o Keycloak da barra de endereços

> Design aprovado em brainstorming em 2026-08-07. Duas fases, com gate humano
> entre elas. Não altera nenhuma linha de Java ou Vue.

## Objetivo

Hoje o login leva o usuário para

```
https://localhost:8843/realms/dcm4chee/login-actions/authenticate?execution=…&tab_id=…
```

Isso denuncia cinco coisas ao mesmo tempo. Este spec ataca quatro delas:

| # | O que denuncia | Fase |
| :-- | :-- | :-- |
| 1 | Host `localhost` ≠ `blackice.localhost` | 1 |
| 2 | Porta `:8843` | 1 |
| 3 | Aviso de certificado (self-signed, sem SAN) | 1 |
| 4 | O nome do produto de terceiro, `dcm4chee`, no caminho | 2 |
| 5 | Houve uma navegação de página inteira | **fora de escopo** |

O item 3 é o mais grave: um aviso de certificado não parece "terceiro", parece
**quebrado**. O item 4 é o que o dono do repo apontou como incômodo principal.

Resultado final:

```
http://blackice.localhost/auth/realms/blackice/login-actions/authenticate?execution=…&tab_id=…
```

Mesmo origin, sem porta, sem aviso de certificado, e **nenhum nome de produto de
terceiro em lugar algum da URL**.

> **O que sobra, assumido conscientemente:** `realms` e a forma
> `login-actions/authenticate?execution=` continuam identificando Keycloak para
> quem conhece Keycloak. Não há configuração que mude isso, e nenhuma reescrita
> de proxy enganaria essa pessoa. O ganho é remover a marca de terceiro, não
> criar disfarce.

## Estado atual (verificado em 2026-08-07, stack no ar)

- **O BFF já existe e está correto.** `quarkus.oidc.application-type=web-app`
  com `token-state-manager.split-tokens` e `encryption-secret`: os tokens vivem
  num cookie HttpOnly criptografado e o JS nunca lê o access token. **O problema
  nunca foi a arquitetura — é só a URL da tela de login.**
- **A UI do Archive não está exposta ao browser.** `docker port infra-arc-1` não
  devolve nada e `Config.Labels` do container não tem nenhuma label `traefik.*`.
  O DCM4CHEE só fala com o Keycloak por *backchannel*
  (`AUTH_SERVER_URL: https://keycloak:8843`, dentro da rede `blackice`).
  **Consequência: mover o origin voltado ao browser do Keycloak não quebra
  nenhum login do DCM4CHEE** — não há login dele exposto para quebrar. A
  restrição do `AGENTS.md` não morde aqui. (O backchannel **é** afetado pelo
  novo path e ganha duas linhas de env — ver Fase 1, serviço `arc`.)
- **O Keycloak roda hostname `v2`.** `KC_HOSTNAME_BACKCHANNEL_DYNAMIC` é opção
  exclusiva de v2 (`kc.sh start --help-all`: *"Available only when hostname:v2
  feature is enabled"*) — sob v1 o server sequer subiria. E `KC_HOSTNAME` recebe
  uma URL completa, que o discovery resolve corretamente
  (`"issuer":"https://localhost:8843/realms/dcm4chee"`). Isso importa porque v2
  é que dá a combinação em uso: **frontend URL fixa + backchannel dinâmico**.
- **O Archive resolve realm e auth server de env a cada boot.** Em
  `/opt/wildfly/standalone/configuration/dcm4chee-arc-oidc.xml`:

  ```xml
  <provider-url>${env.AUTH_SERVER_URL:https://keycloak:8443}/realms/${env.REALM_NAME:dcm4che}</provider-url>
  ```

  Nada baked. O `cn=Devices` de `dicomDeviceName=dcm4chee-arc` no LDAP também
  não guarda nome de realm nem URL de Keycloak.
- **O realm JSON é integralmente parametrizado.**
  `/opt/keycloak/data/import/dcm4che-realm.json` começa com
  `"id": "${REALM_NAME}", "realm": "${REALM_NAME}"`. O nome do realm já é um
  knob — e já foi girado uma vez (`dcm4che` → `dcm4chee`, comentado em
  `infra/dcm4chee/compose.yml`).
- **O frontend não referencia o Keycloak.** Único acoplamento:
  `window.location.href = '/api/login'` em `apps/frontend/src/app/router/index.ts`.
- **O login está QUEBRADO na `main` desde o reset do ambiente** (2026-08-07).
  `quarkus.oidc.tls.verification=none` é inerte no Quarkus 3.37.4; o backend não
  completa o discovery contra o cert self-signed novo do Keycloak e `/api/login`
  devolve **401 em vez de 302**. Ver o bloco da `application.properties` na Fase 1
  para a prova. A Fase 1 corrige isso ao remover o TLS do caminho.
- `directAccessGrantsEnabled: false` no client `blackice-quarkus`.
- Traefik v3.1 com um único entrypoint, `web:80`. Keycloak **não** está atrás
  dele; publica `8843:8843` direto no host.

## Decisões

| Decisão | Escolha | Motivo |
| :-- | :-- | :-- |
| Como esconder host/porta/cert | Keycloak atrás do Traefik, mesmo origin | Mantém Authorization Code + PKCE e o `web-app` do `quarkus-oidc` intactos |
| Prefixo | `/auth` (path), **não** subdomínio | Subdomínio continuaria sendo outro origin no browser: resolveria cert e porta, não o "saí do produto" |
| Como esconder `dcm4chee` | Renomear o realm para `blackice` | Ataca a raiz; mantém realm único, o audience mapper e a identidade por usuário chegando ao Archive |
| Criação do realm novo | ~~`--import-realm` com `REALM_NAME` trocado~~ → **rename in place pela Admin REST** | A escolha original **não funciona**: `dcm4che-realm.json` embute 112 UUIDs literais não templados por `${REALM_NAME}`, e `KEYCLOAK_ROLE.ID` é PK global — reimportar sob outro nome numa base já populada colide e trava o boot. O rename (`PUT /admin/realms/<atual>`) preserva usuários, clients, mappers e role mappings. Ver "Reversão" |
| Transporte Traefik→Keycloak | `KC_HTTP_ENABLED=true` (HTTP em :8080 na rede) | O 8843 HTTPS continua servindo o backchannel do Archive |

---

## Fase 1 — Same-origin atrás do Traefik

### Fluxo resultante

```
browser → http://blackice.localhost/                (Traefik → frontend, priority 1)
        → http://blackice.localhost/api/login       (Traefik → backend)
        → 302 http://blackice.localhost/auth/realms/dcm4chee/protocol/openid-connect/auth?…
                                                    (Traefik → keycloak:8080)
        → login (tema blackice) → 302 /api/login → 302 /
```

### `infra/dcm4chee/compose.yml`, serviço `keycloak`

```yaml
environment:
  KC_HTTP_ENABLED: 'true'            # serve HTTP em :8080 na rede interna
  KC_HTTP_RELATIVE_PATH: /auth       # TODAS as rotas passam a viver sob /auth
  KC_HOSTNAME: http://${APP_HOST}/auth
  KC_PROXY_HEADERS: xforwarded
  # KC_HTTPS_PORT: 8843 PERMANECE — é o backchannel do Archive.
labels:
  - "traefik.enable=true"
  - "traefik.http.routers.auth.rule=Host(`${APP_HOST}`) && PathPrefix(`/auth`)"
  - "traefik.http.routers.auth.entrypoints=web"
  - "traefik.http.routers.auth.priority=10"
  - "traefik.http.services.auth.loadbalancer.server.port=8080"
```

> **`priority=10` não é decoração.** O router `spa` tem
> `rule=Host(${APP_HOST})` com `priority=1` e casa com **tudo** naquele host.
> Sem prioridade explícita, `/auth` cai na SPA.

`KC_HTTP_RELATIVE_PATH` e o path de `KC_HOSTNAME` precisam ser o **mesmo** valor:
o primeiro é onde o Keycloak de fato serve, o segundo é o que ele escreve nas
URLs. `KC_HOSTNAME_BACKCHANNEL_DYNAMIC=true` continua e exige `KC_HOSTNAME` como
URL completa — que é o caso.

> **`KC_PROXY_HEADERS` não é o que faz o proxy funcionar.** Com `hostname` fixo
> em URL completa (v2), as frontend URLs saem de `hostname`, **não** dos headers
> encaminhados — para geração de URL essa opção é inerte. Ela existe aqui para o
> Keycloak confiar nos `X-Forwarded-*` do Traefik na resolução de **IP do cliente
> e esquema** (relevante para os event logs e para políticas que olham o
> originador). Está documentado para que, se a Fase 1 falhar pela metade,
> ninguém depure o botão errado.

### `infra/dcm4chee/compose.yml`, serviço `arc`

```yaml
AUTH_SERVER_URL: https://keycloak:8843/auth
UI_AUTH_SERVER_URL: https://${DCM4CHEE_HOST}:8843/auth
```

> **`KC_HTTP_RELATIVE_PATH` é o root path do servidor inteiro, não só do listener
> HTTP.** O backchannel do Archive também se move: sem estas duas linhas,
> `${env.AUTH_SERVER_URL}/realms/${env.REALM_NAME}` resolve para
> `https://keycloak:8843/realms/dcm4chee`, que passa a devolver **404**, e o
> Archive perde a validação de token. O `arc` continua falando HTTPS na 8843 —
> só o path muda.

### `apps/backend/src/main/resources/application.properties`

```properties
quarkus.oidc.auth-server-url=http://blackice.localhost/auth/realms/dcm4chee
```

e **apagar** `quarkus.oidc.tls.verification=none` junto com todo o bloco de
comentário `ATENCAO - DEV/TEST APENAS` (linhas 8–12) e seu TODO.

> **Essa linha não funciona (verificado empiricamente em 2026-08-07).**
> `quarkus.oidc.tls.verification` é **inerte no Quarkus 3.37.4** — a propriedade
> migrou para o TLS registry central. Prova: com a config commitada, o backend
> falha com `PKIX path building failed` contra `https://keycloak:8843` e loga
> `OIDC server is not available`; o mesmo container com
> `quarkus.tls.trust-all=true` sobe limpo. E `curl -k` de dentro do container
> devolve 200 no mesmo endereço, então não é rede — é validação de TLS ligada.
>
> Isto é um **defeito pré-existente na `main`**, não introduzido aqui: qualquer
> `docker compose down -v` derruba o login, porque o Keycloak gera um cert
> self-signed novo e nada no backend o aceita. O ambiente só parecia saudável
> enquanto o volume antigo do Keycloak sobrevivia.
>
> **A Fase 1 é a correção**, não um enfeite: ela tira o TLS do caminho
> backend↔Keycloak inteiramente (`http://keycloak:8080/auth/…`). A alternativa
> seria configurar o TLS registry (`quarkus.tls.trust-all=true`) — trocar um
> workaround morto por um vivo. Apagar a linha é o desfecho certo, e a razão é
> mais forte que estética.

> **Este valor é o de host (dev mode), e só.** `blackice.localhost` resolve para
> `127.0.0.1`; de **dentro** do container `backend` isso é o próprio container,
> não o Traefik — por isso o `compose.apps.yml` sobrescreve com o endereço
> interno. Não é um valor que serve aos dois modos.
>
> **Lacuna pré-existente, não introduzida aqui:** login ponta-a-ponta em dev mode
> (`quarkus:dev` + Vite) já não fecha hoje, porque `redirectUris` do client é
> `http://blackice.localhost/api/*` e o retorno cai no backend **containerizado**,
> não no de dev. Esta fase não conserta nem piora isso. Fechar o dev mode
> (redirect URI adicional para a origem do Vite, ou rodar sempre via Traefik) é
> assunto separado.

### `infra/compose.apps.yml`, serviço `backend`

```yaml
QUARKUS_OIDC_AUTH_SERVER_URL: http://keycloak:8080/auth/realms/dcm4chee
```

Backchannel interno. O `KC_HOSTNAME` fixo garante que o `authorization_endpoint`
devolvido no discovery continue voltado ao browser, como já acontece hoje.

### `infra/keycloak/configure-blackice.sh`

```sh
KB=https://localhost:8843/auth
```

A Admin REST se move com o `KC_HTTP_RELATIVE_PATH`. Sem isso o script quebra em
`TOKEN FAIL`.

### O que **não** muda na Fase 1

- Realm: `redirectUris` já é `http://blackice.localhost/api/*` e `webOrigins` já
  é `http://blackice.localhost`.
- Tema `blackice`, atributo `login_theme` no client, message bundle. A URL do CSS
  ganha o prefixo (`/auth/resources/…`), mas a asserção do E2E é substring e
  sobrevive.
- Transporte do Archive: continua HTTPS na 8843, rede interna. Só o path muda.
- Frontend: zero linhas de código (o `README.md` do frontend muda — ver
  Verificação).

---

## Fase 2 — Renomear o realm para `blackice`

Só depois do gate humano da Fase 1.

### Mecânica

> **A mecânica original deste spec estava errada e foi substituída.** Eu havia
> escrito que trocar `REALM_NAME` e reiniciar criaria o realm `blackice` **ao
> lado** do `dcm4chee` via `--import-realm`. Isso **não funciona**, e a tentativa
> **trava o boot do Keycloak**.
>
> Evidência: `/opt/keycloak/data/import/dcm4che-realm.json` embute **112 UUIDs
> literais** em campos `"id"` que **não** são templados por `${REALM_NAME}` —
> `name` e `containerId` são, `id` não. O primeiro deles é `defaultRole.id`
> (`faded8f1-de97-4ea9-b460-f22facf354b3`), e `KEYCLOAK_ROLE.ID` é PK **global**,
> não escopada por realm. O banco já tem essa linha, do import que criou o
> `dcm4chee`:
>
> ```
> ID                                    NAME                    REALM_ID
> faded8f1-de97-4ea9-b460-f22facf354b3  default-roles-dcm4chee  dcm4chee
> ```
>
> Importar o mesmo arquivo uma segunda vez sob **qualquer** outro `REALM_NAME`
> colide nessa linha. `IGNORE_EXISTING` não salva: o importer pergunta "o realm
> `blackice` já tem esse papel?" (não, é realm novo) e insere. A premissa só
> valeria num banco vazio. Eu a inferi da semântica do `--import-realm` sem
> testar contra um banco populado — o mesmo erro de método do
> `tls.verification`.

**O que substitui:** renomear o realm **in place** pela Admin REST.

```
PUT /auth/admin/realms/dcm4chee   {"realm":"blackice"}
```

Isso muda `REALM.NAME`. Nada é apagado: usuários, clients, protocol mappers,
role mappings e o atributo `login_theme` continuam presos ao mesmo realm.
Verificado após o rename: `blackice-quarkus`, `arc-audience`, `dr.teste`,
`login_theme=blackice` e `dcm4chee-arc-rs` todos intactos.

`REALM_NAME` no compose passa a **acompanhar** o nome real, para que o import de
boot encontre o realm existente e não tente criar outro. Verificado: com
`REALM_NAME: blackice` e o realm já renomeado, o Keycloak sobe em 25s sem
colisão.

A role padrão do realm mantém o nome antigo depois do rename e é renomeada
junto, senão `default-roles-dcm4chee` continuaria aparecendo no claim
`realm_access.roles` — exatamente o caminho que
`quarkus.oidc.roles.role-claim-path` lê:

```
PUT /auth/admin/realms/blackice/roles/default-roles-dcm4chee  {"name":"default-roles-blackice", …}
```

Nenhum volume é removido; os estudos DICOM (`dcm4chee-storage`,
`dcm4chee-db-data`) não são tocados.

| Onde | Mudança |
| :-- | :-- |
| `dcm4chee/compose.yml`, `keycloak` | `REALM_NAME: blackice` |
| `dcm4chee/compose.yml`, `arc` | `REALM_NAME: blackice` — o `provider-url` re-resolve no boot |
| `infra/keycloak/configure-blackice.sh` | `-e REALM="blackice"` (linha 29; já é parâmetro) |
| `application.properties` e `compose.apps.yml` | `/realms/blackice` |

`ARC_CLIENT="dcm4chee-arc-rs"` **não muda**: é o `clientId` dentro do realm,
criado pelo import a partir de `${RS_CLIENT_ID}` (default da imagem, verificado
em 2026-08-07), e não aparece em URL nenhuma.

### Custo honesto

O realm novo nasce vazio de configuração nossa. É preciso re-rodar
`configure-blackice.sh` (idempotente — recria client, audience mapper, `dr.teste`
e `login_theme`) e **re-atribuir as realm roles no gate humano**: o script não
atribui roles por decisão de projeto, e o realm novo não as herda.

### Custo honesto (revisado)

O rename **preserva** a configuração, então cai a maior parte do custo que este
spec previa: não é preciso re-rodar o `configure-blackice.sh`, e os role mappings
existentes sobrevivem. **Mas o gate humano da role continua**, porque a role
`auth` nunca chegou a ser atribuída ao `dr.teste` neste ambiente — o rename
preservou o que existia, e o que existia era só a role padrão.

### Reversão

Receita completa — **os cinco passos são obrigatórios**; fazer só os dois
primeiros deixa o login quebrado:

1. `PUT /auth/admin/realms/blackice {"realm":"dcm4chee"}` (Admin REST, de dentro
   do container `keycloak`).
2. Renomear a role padrão de volta: GET
   `/auth/admin/realms/dcm4chee/roles/default-roles-blackice`, troque só o
   `name` para `default-roles-dcm4chee`, PUT de volta. Ela viaja no claim
   `realm_access.roles`, que é o que `quarkus.oidc.roles.role-claim-path` lê.
3. `REALM_NAME: dcm4chee` nos **dois** serviços do
   `infra/dcm4chee/compose.yml` — `keycloak` e `arc`.
4. `REALM=dcm4chee` em `infra/keycloak/configure-blackice.sh`, e o realm na URL
   de `QUARKUS_OIDC_AUTH_SERVER_URL` em `infra/compose.apps.yml`.
5. `quarkus.oidc.auth-server-url` em
   `apps/backend/src/main/resources/application.properties` — e como o
   `Dockerfile.jvm` copia o `target/` pré-buildado em vez de compilar,
   isso exige `./mvnw package` **e**
   `docker compose … build --no-cache backend`, não só um restart.

Nenhum dado sai do lugar em nenhuma direção.

> **O requisito de rollback mudou de forma, não de força.** Este spec dizia "o
> realm `dcm4chee` continua existindo como escape hatch". Com o rename ele
> **não** existe mais por esse nome (`/realms/dcm4chee` devolve 404) — é o mesmo
> realm, com outro nome. O que o requisito realmente pedia (poder voltar sem
> perder nada) é atendido, e melhor: não há realm duplicado para divergir, e
> voltar não exige reconfigurar nada.

---

## Verificação

A rede de segurança já existe: `apps/frontend/e2e/keycloak-login.spec.ts` navega
para `http://blackice.localhost/`, cai no login e compara **snapshot de pixel** do
card. Mesmo tema e mesmo viewport ⇒ o snapshot deve bater **sem**
`--update-snapshots`. Se bater, a mudança foi puramente de transporte. A asserção
do CSS é substring (`link[href*="/login/blackice/css/blackice.css"]`) e sobrevive
ao novo prefixo.

**Fase 1**
1. `docker compose … config --quiet` passa.
2. `GET http://blackice.localhost/auth/realms/dcm4chee/.well-known/openid-configuration`
   devolve `issuer` e `authorization_endpoint` em `blackice.localhost/auth`
   (diff contra o esperado, não inspeção a olho).
3. `configure-blackice.sh` roda até `OK:` — prova que a Admin REST sob `/auth`
   está correta.
4. Playwright verde, snapshot inalterado.
5. Login ponta-a-ponta com `dr.teste` até o SPA, **sem exceção de certificado no
   browser do humano**. Esta é a prova do objetivo.
6. O Archive continua alcançando o Keycloak no novo path — `curl` de dentro do
   container `arc` para `https://keycloak:8843/auth/realms/dcm4chee/.well-known/openid-configuration`
   devolve 200, e o log do `arc` não tem erro de OIDC após o restart.

**Fase 2**
1. `GET .../auth/realms/blackice/.well-known/openid-configuration` responde 200.
2. O realm `dcm4chee` **não** existe mais por esse nome — `GET
   .../auth/realms/dcm4chee/.well-known/openid-configuration` responde `404`.
   É o mesmo realm renomeado; o rollback está na receita da "Reversão" acima,
   não num realm duplicado.
3. `configure-blackice.sh` com `REALM=blackice` roda até `OK:`.
4. Roles re-atribuídas — **gate humano**.
5. Login ponta-a-ponta, e a URL da tela de login **não contém a string
   `dcm4chee`**. Verificação por asserção, não por leitura.
6. **O audience do token bate com um client que existe no realm novo.** Duas
   asserções, ambas executáveis hoje:
   - `GET /admin/realms/blackice/clients?clientId=dcm4chee-arc-rs` devolve um
     hit (o import cria esse client a partir de `${RS_CLIENT_ID}`, cujo default
     na imagem é literalmente `dcm4chee-arc-rs` — verificado em 2026-08-07,
     casando com o valor hardcoded na linha 28 do `configure-blackice.sh`);
   - o `aud` do access token da sessão contém esse mesmo `clientId`.

   Este é o **único** modo de falha real da Fase 2 e ele é silencioso: se o
   mapper `arc-audience` apontar para um client inexistente no realm novo, o
   token é emitido, o login funciona, e só o DICOMweb devolve 403 — meses depois,
   quando o retrieve existir.

> **Limite conhecido:** não há como verificar o retrieve de um estudo ponta-a-
> ponta hoje. `SessionResource` expõe só `/api/me` e `/api/login`; não existe
> endpoint DICOMweb no backend ainda. A verificação completa (recuperar um estudo
> já ingerido pelo SPA) entra quando o caminho de retrieve for construído.

## Riscos

| Risco | Mitigação |
| :-- | :-- |
| Router `spa` (priority 1, casa com tudo no host) engolir `/auth` | `priority=10` explícita no router `auth`; verificação 2 pega na hora |
| Admin REST quebrar por causa do `KC_HTTP_RELATIVE_PATH` | Já contemplado (`KB=…/auth`); verificação 3 é o teste |
| **Backchannel do Archive quebrar pelo mesmo motivo** — o relative path vale para o listener HTTPS 8843 também | `AUTH_SERVER_URL`/`UI_AUTH_SERVER_URL` ganham `/auth` no mesmo commit; verificação 6 da Fase 1 é o teste |
| HTTP puro entre browser e Keycloak | Ambiente local; o app já é HTTP. **Prod exige TLS no entrypoint do Traefik — é lá que se resolve, não voltando o Keycloak para 8843** |
| Fase 2 deixar o Archive falando com o realm errado | `REALM_NAME` muda nos **dois** serviços no mesmo commit |
| ~~Import do realm novo não disparar~~ → **o import do realm novo travar o boot** | Não é "não disparar": numa base já populada o `--import-realm` sob outro nome **colide** em `KEYCLOAK_ROLE.ID` (PK global) e o Keycloak não sobe. Por isso o rename é in place. Numa base **vazia** (cold start) o problema não existe: o import cria o realm já com o nome de `REALM_NAME` |
| **`/auth` publica também o admin console e a Admin REST no origin do produto, em HTTP puro** — medido: `http://blackice.localhost/auth/admin/master/console/` responde `200`. `KC_HTTP_RELATIVE_PATH` é o root path do servidor inteiro, então o `PathPrefix(/auth)` do Traefik carrega junto tudo que mora sob ele | **Aceito conscientemente em dev local** (o Traefik já roda com `--api.insecure=true` e a stack não sai do host). Antes de qualquer exposição além de dev: TLS no entrypoint **e** estreitar a regra do router para os paths do fluxo de login (`/auth/realms`, `/auth/resources`), mantendo `/auth/admin` fora. Estreitar exige testar logout e backchannel — não foi feito nesta branch |
| Roles esquecidas após a Fase 2 | Gate humano explícito; sem elas o login conclui e a autorização falha — sintoma visível, não silencioso |
| `arc-audience` apontar para client inexistente no realm novo — **falha silenciosa**: login funciona, DICOMweb 403 muito depois | Verificação 6 da Fase 2, executável hoje |

## Alternativas descartadas

**Reescrita no proxy (`nginx sub_filter` + `proxy_redirect`).** Esconderia
`realms/dcm4chee` e deixaria intacto
`login-actions/authenticate?execution=…&tab_id=…&client_id=…`. Meio esconderijo
em troca de fragilidade permanente contra todo upgrade do Keycloak.

**Realm separado (`blackice` para o BFF, `dcm4chee` para o Archive).** Um token
emitido no realm `blackice` não valida no Archive, que confia em `dcm4chee`. A
saída seria service account, e aí o Archive perde **atribuição por usuário no
audit trail DICOM**. Num PACS isso é regressão de correção de domínio, não
trade-off de UX.

**ROPC / Direct Access Grant** ("o BFF faz o login por baixo dos panos"). Mata
também os itens 4 e 5, ao custo de: abandonar `application-type=web-app` e
reimplementar à mão o que o `token-state-manager` já faz (cookie criptografado,
split tokens, refresh, expiração, logout); perder MFA, reset de senha, required
actions e IdP social, que vivem no browser flow; ligar `directAccessGrants`; e
fazer credenciais transitarem pelo backend próprio. ROPC foi removido no
OAuth 2.1. Num PACS de portfólio, um revisor do domínio lê redirect OIDC visível
como **correto** e formulário de senha postando no backend próprio como
**achado**. O caminho legítimo para UI de login própria é a Authentication API
declarativa do Keycloak, disponível a partir do 26.2 — o 25.0.6 pinado aqui não
a tem.

## Fora de escopo

- **Item 5** (a navegação de página inteira). Só some com ROPC ou com a
  Authentication API do Keycloak ≥ 26.2. Reavaliar num upgrade, não agora.
- Remover a publicação `8843:8843` do host (limpeza separada).
- TLS / entrypoint `websecure` no Traefik — assunto de produção.
- Apagar o realm `dcm4chee` após a Fase 2.
- Migração do frontend para Nuxt. Este desenho é agnóstico a ela: a sessão
  continua sendo do Quarkus, no mesmo origin, e o Nuxt entraria como camada de
  render consumindo `/api` — não como segundo BFF. Se a intenção for o server do
  Nuxt assumir a sessão, isso é um fork arquitetural com spec próprio.
