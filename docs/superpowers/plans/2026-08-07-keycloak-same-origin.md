# Keycloak same-origin — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Tirar o Keycloak da barra de endereços — o login deixa de acontecer em `https://localhost:8843/realms/dcm4chee/…` (host estranho, porta, aviso de certificado, nome de produto de terceiro) e passa a acontecer em `http://blackice.localhost/auth/realms/blackice/…`.

**Architecture:** Duas fases com gate humano entre elas. **Fase 1** põe o Keycloak atrás do Traefik no mesmo origin da aplicação, sob o path `/auth`, servindo HTTP na rede interna; o fluxo Authorization Code + PKCE e o `application-type=web-app` do `quarkus-oidc` ficam intactos. **Fase 2** troca o nome do realm de `dcm4chee` para `blackice` **renomeando-o in place** pela Admin REST, sem apagar volume (a mecânica original — criar o realm novo ao lado via `--import-realm` — trava o boot; ver o bloco antes do Step 3 da Task 2). Nenhuma linha de Java ou Vue muda.

**Tech Stack:** Docker Compose (3 arquivos), Traefik v3.1, Keycloak 25.0.6 (`dcm4che/keycloak`, hostname feature **v2**), DCM4CHEE Archive 5.34.3, Quarkus (`quarkus-oidc`), Playwright.

**Spec:** `docs/superpowers/specs/2026-08-07-keycloak-same-origin-design.md`

## Global Constraints

- **Path público do Keycloak: `/auth`.** `KC_HTTP_RELATIVE_PATH` e o componente de path de `KC_HOSTNAME` têm de ser o **mesmo** valor.
- **`KC_HTTP_RELATIVE_PATH` é o root path do servidor inteiro** — vale também para o listener HTTPS 8843. Todo consumidor do Keycloak (Archive, Admin REST, backend) precisa do `/auth` no endereço.
- **`KC_HTTPS_PORT: 8843` permanece.** É o backchannel do Archive. Nunca remover.
- **`KC_HOSTNAME` tem de ser URL completa**, porque `KC_HOSTNAME_BACKCHANNEL_DYNAMIC: 'true'` exige isso na hostname feature v2.
- **Router Traefik `auth` precisa de `priority` explícita maior que 1.** O router `spa` é `Host(${APP_HOST})` com `priority=1` e casa com *tudo* naquele host.
- **Nunca commitar sem pedido explícito do humano** (`AGENTS.md`). Os passos de commit deste plano são executados **após** o humano autorizar no gate da tarefa.
- **Antes de cada commit, rodar a atualização semântica do Graphify pela skill** (`AGENTS.md`) e revisar o diff de `graphify-out/`.
- **`ARC_CLIENT="dcm4chee-arc-rs"` não muda em nenhuma fase.** É o `clientId` dentro do realm, criado pelo import a partir de `${RS_CLIENT_ID}` (default da imagem, verificado em 2026-08-07). Não aparece em URL.
- **Comandos de stack** rodam de `infra/` com os três arquivos sempre na mesma ordem:
  `docker compose -f compose.yml -f dcm4chee/compose.yml -f compose.apps.yml <cmd>`
- **Comandos `curl`/`grep` deste plano assumem Git Bash** (disponível no ambiente). Os blocos PowerShell existentes no `apps/frontend/README.md` são editados como texto, não executados durante a implementação.

---

## Estrutura de arquivos

Nenhum arquivo é criado. Sete são modificados, em duas fases:

| Arquivo | Responsabilidade | Fase 1 | Fase 2 |
| :-- | :-- | :--: | :--: |
| `infra/dcm4chee/compose.yml` | Serviços `keycloak` (env + labels Traefik) e `arc` (endereço do Keycloak) | ✅ | ✅ |
| `infra/compose.apps.yml` | Env do `backend`: URL de backchannel do OIDC | ✅ | ✅ |
| `apps/backend/src/main/resources/application.properties` | Config OIDC base (valor de dev mode / host) | ✅ | ✅ |
| `infra/keycloak/configure-blackice.sh` | Base da Admin REST e nome do realm | ✅ | ✅ |
| `apps/frontend/README.md` | Readiness probe do E2E (assere o `Location` do `/api/login`) | ✅ | ✅ |
| `infra/keycloak/README.md` | Documentação do realm e da config do Keycloak | ✅ | ✅ |
| `infra/.env.example` | Comentário sobre o papel de `DCM4CHEE_HOST` | ✅ | — |

---

### Task 1: Fase 1 — Keycloak same-origin sob `/auth`

Mudança atômica: os estados intermediários quebram o login (mover o path do Keycloak sem mover os consumidores dá 404 em todos eles). Por isso é uma tarefa só, com um commit só.

**Files:**
- Modify: `infra/dcm4chee/compose.yml` (serviço `keycloak`: bloco `environment` linhas 42-69 e adição de `labels`; serviço `arc`: `environment` linhas 105-113)
- Modify: `infra/compose.apps.yml:14`
- Modify: `apps/backend/src/main/resources/application.properties:8-17`
- Modify: `infra/keycloak/configure-blackice.sh:32`
- Modify: `apps/frontend/README.md:56` e `apps/frontend/README.md:95`
- Modify: `infra/keycloak/README.md` (seção "Por que `curl` e não `kcadm`")
- Modify: `infra/.env.example` (comentário de `DCM4CHEE_HOST`)
- Test: `apps/frontend/e2e/keycloak-login.spec.ts` (**não modificar** — é a rede de segurança; deve passar sem alteração)

**Interfaces:**
- Consumes: nada (primeira tarefa).
- Produces: Keycloak servindo em `http://blackice.localhost/auth` (browser) e `http://keycloak:8080/auth` (rede interna); Admin REST em `https://localhost:8843/auth/admin`. A Task 2 depende desses três endereços.

- [ ] **Step 1: Confirmar o estado quebrado de partida — NÃO tente um baseline verde**

> **Este passo mudou em 2026-08-07 e o motivo importa.** O plano original mandava capturar um baseline verde do Playwright antes de mexer em nada. **Isso é impossível hoje**, e não por culpa desta mudança: o login está quebrado na `main`.
>
> `quarkus.oidc.tls.verification=none` é **inerte no Quarkus 3.37.4** (a propriedade migrou para o TLS registry). Depois do reset do ambiente, o Keycloak gerou um cert self-signed novo, e o backend não consegue completar o discovery: `PKIX path building failed`, `OIDC server is not available`, e `/api/login` responde **401 em vez de 302**. `curl -k` de dentro do container do backend devolve 200 no mesmo endereço, então não é rede — é validação de TLS ligada.
>
> **A Fase 1 é a correção**: ela move o backend para `http://keycloak:8080/auth/…`, sem TLS no caminho.

Confirme o ponto de partida (esses valores entram no seu report):

```bash
curl -sS --max-redirs 0 -D - -o /dev/null http://blackice.localhost/api/login | head -1
```

Expected: `HTTP/1.1 401 Unauthorized`. Se vier `302`, o ambiente mudou desde o planejamento — **pare e reporte**, porque as expectativas do Step 3 dependem disto.

**A referência do snapshot é o PNG commitado** em `apps/frontend/e2e/keycloak-login.spec.ts-snapshots/`, que está em git no commit `2e340a6`. Ele é a verdade; o Step 1 original só reconfirmava que ela ainda valia. Não rode o Playwright agora — sem login não há tela para fotografar.

- [ ] **Step 2: Escrever o script de asserção da Fase 1**

Os scripts de asserção ficam **fora do repo** — não são entregáveis. Defina o diretório uma vez, na mesma sessão de shell usada pelos passos seguintes:

```bash
export SCRATCH="$HOME/AppData/Local/Temp/blackice-same-origin"
mkdir -p "$SCRATCH"
```

Criar `$SCRATCH/assert-fase1.sh`:

```bash
#!/usr/bin/env bash
# Asserções da Fase 1.
set -u
fail=0
chk() { # chk <descrição> <esperado> <obtido>
  if [ "$2" = "$3" ]; then echo "ok   $1"; else echo "FALHA $1"; echo "  esperado: $2"; echo "  obtido:   $3"; fail=1; fi
}

BASE=http://blackice.localhost

# A. discovery no novo endereço, com issuer e authorization_endpoint same-origin
disc=$(curl -sS "$BASE/auth/realms/dcm4chee/.well-known/openid-configuration")
iss=$(echo "$disc" | tr ',' '\n' | sed -n 's/.*"issuer":"\([^"]*\)".*/\1/p')
aep=$(echo "$disc" | tr ',' '\n' | sed -n 's/.*"authorization_endpoint":"\([^"]*\)".*/\1/p')
chk "issuer same-origin" "$BASE/auth/realms/dcm4chee" "$iss"
chk "authorization_endpoint same-origin" "$BASE/auth/realms/dcm4chee/protocol/openid-connect/auth" "$aep"

# B. /api/login redireciona para o novo endereço (302, sem seguir)
loc=$(curl -sS --max-redirs 0 -D - -o /dev/null "$BASE/api/login" \
      | sed -n 's/^[Ll]ocation: *//p' | tr -d '\r' | cut -d'?' -f1)
chk "Location do /api/login" "$BASE/auth/realms/dcm4chee/protocol/openid-connect/auth" "$loc"

# C. a SPA continua na raiz (o router auth não roubou o Host inteiro)
chk "SPA na raiz" "200" "$(curl -sS -o /dev/null -w '%{http_code}' "$BASE/")"
chk "/api/me anônimo" "401" "$(curl -sS -o /dev/null -w '%{http_code}' "$BASE/api/me")"

# D. o workaround de TLS saiu do backend
chk "tls.verification removido" "0" \
  "$(grep -c 'tls.verification' apps/backend/src/main/resources/application.properties || true)"

# E. backchannel do Archive vivo no novo path.
# `curl` existe em /usr/bin/curl neste container (verificado em 2026-08-07) — a
# asserção roda de verdade, não vira no-op silencioso. Esta é a ÚNICA cobertura
# do risco de `KC_HTTP_RELATIVE_PATH` quebrar o listener HTTPS 8843.
chk "backchannel do Archive" "200" \
  "$(docker exec infra-arc-1 sh -c 'curl -sk -o /dev/null -w "%{http_code}" https://keycloak:8843/auth/realms/dcm4chee/.well-known/openid-configuration')"

# F. e o Archive de fato não está reclamando de OIDC depois do restart
errs=$(docker logs infra-arc-1 --since 5m 2>&1 | grep -iE 'oidc|realms/' | grep -icE 'error|fail|404')
chk "Archive sem erro de OIDC no log" "0" "$errs"

exit $fail
```

- [ ] **Step 3: Rodar as asserções para vê-las falhar**

Da raiz do repo:

```bash
bash "$SCRATCH/assert-fase1.sh"
```

Expected, com o motivo de cada falha — **leia com atenção, porque duas falham por razões diferentes do que parece**:

| Asserção | Resultado | Por quê |
| :-- | :-- | :-- |
| A `issuer`/`authorization_endpoint` | FALHA | O path `/auth` ainda não existe: o `curl` devolve vazio |
| B `Location` do `/api/login` | FALHA | **Não** porque falta o `/auth` — porque o backend responde **401**, sem `Location` nenhum. É o TLS quebrado descrito no Step 1, não a ausência do prefixo |
| C SPA na raiz / `/api/me` | passa | Não dependem do OIDC |
| D `tls.verification` removido | FALHA | A linha ainda está no arquivo |
| E backchannel do Archive | FALHA | O path `/auth` ainda não existe na 8843 |
| F Archive sem erro de OIDC | passa | O Archive valida token, e ninguém logou ainda |

**Se A ou E passarem agora**, o ambiente não está no estado que este plano assume — pare e investigue. **Se B devolver 302** em vez de 401, alguém consertou o TLS por outro caminho: pare e reporte, porque isso muda o que a Fase 1 está corrigindo.

- [ ] **Step 4: Mover o Keycloak para `/auth` e pô-lo atrás do Traefik**

Em `infra/dcm4chee/compose.yml`, serviço `keycloak`, acrescentar ao bloco `environment` (as chaves existentes, incluindo `KC_HTTPS_PORT: 8843` e as três `KC_SPI_THEME_*`, **permanecem**) e adicionar um bloco `labels` novo:

```yaml
      # Same-origin (spec 2026-08-07): o Keycloak passa a ser servido pelo
      # Traefik em http://${APP_HOST}/auth. KC_HTTP_ENABLED liga o listener
      # HTTP em :8080 na rede interna; o listener HTTPS 8843 continua vivo,
      # porque é o backchannel do Archive.
      # ATENÇÃO: KC_HTTP_RELATIVE_PATH é o root path do SERVIDOR INTEIRO —
      # vale para a 8843 também. Todo consumidor precisa do /auth no endereço.
      KC_HTTP_ENABLED: 'true'
      KC_HTTP_RELATIVE_PATH: /auth
      # URL completa é exigida por KC_HOSTNAME_BACKCHANNEL_DYNAMIC (hostname v2).
      KC_HOSTNAME: http://${APP_HOST}/auth
      # NÃO é o que faz o proxy funcionar: com hostname fixo, as frontend URLs
      # saem dele, não dos headers. Isto é para o Keycloak confiar nos
      # X-Forwarded-* do Traefik ao resolver IP do cliente e esquema.
      KC_PROXY_HEADERS: xforwarded
    labels:
      - "traefik.enable=true"
      - "traefik.http.routers.auth.rule=Host(`${APP_HOST}`) && PathPrefix(`/auth`)"
      - "traefik.http.routers.auth.entrypoints=web"
      # priority > 1 é obrigatório: o router `spa` é Host(${APP_HOST}) com
      # priority=1 e casa com TUDO neste host, inclusive /auth.
      - "traefik.http.routers.auth.priority=10"
      - "traefik.http.services.auth.loadbalancer.server.port=8080"
```

- [ ] **Step 5: Mover o Archive para o novo path**

No mesmo arquivo, serviço `arc`, substituir as duas linhas existentes:

```yaml
      AUTH_SERVER_URL: https://keycloak:8843/auth
      UI_AUTH_SERVER_URL: https://${DCM4CHEE_HOST}:8843/auth
```

> Sem isto, `${env.AUTH_SERVER_URL}/realms/${env.REALM_NAME}` do
> `dcm4chee-arc-oidc.xml` resolve para um 404 e o Archive perde a validação de
> token. O transporte não muda: continua HTTPS na 8843, rede interna.

- [ ] **Step 6: Mover o backchannel do backend**

Em `infra/compose.apps.yml`, serviço `backend`, substituir a linha 14:

```yaml
      QUARKUS_OIDC_AUTH_SERVER_URL: http://keycloak:8080/auth/realms/dcm4chee
```

**E reescrever o bloco de comentário das linhas 6-13**, que descreve o mecanismo antigo e fica falso com esta mudança: ele cita `keycloak:8843`, `KC_HOSTNAME=https://${DCM4CHEE_HOST}:8843` e um `authorization_endpoint` resolvendo para `localhost:8843`. Preserve o *motivo* de a variável existir (o split frontchannel/backchannel continua sendo a razão) e corrija os detalhes: o backend alcança o Keycloak em `http://keycloak:8080/auth` na rede interna, sem TLS; as URLs voltadas ao browser saem do `KC_HOSTNAME` fixo, então o `authorization_endpoint` do discovery aponta para a origem do Traefik; e `KC_HOSTNAME_BACKCHANNEL_DYNAMIC=true` é o que mantém o backchannel resolvendo pelo endereço de quem chamou.

> Comentário que descreve o transporte errado é pior que comentário nenhum, e este fica exatamente em cima do mecanismo que a tarefa muda.

- [ ] **Step 7: Apagar o workaround de TLS no backend**

Em `apps/backend/src/main/resources/application.properties`, substituir as linhas 8-17 (o bloco de comentário `ATENCAO - DEV/TEST APENAS` inteiro, a `auth-server-url` e a `tls.verification`) por:

```properties
# Endereço voltado ao HOST (dev mode). Em compose, sobrescrito por
# QUARKUS_OIDC_AUTH_SERVER_URL para o endereço interno da rede — `blackice.localhost`
# resolve para 127.0.0.1 e, dentro do container, isso é o próprio container.
#
# Não há mais `tls.verification=none`: o Keycloak é servido pelo Traefik em HTTP
# same-origin, então o cert self-signed sem SAN saiu do caminho.
# Em produção o TLS entra no entrypoint do Traefik, não voltando o Keycloak à 8843.
quarkus.oidc.auth-server-url=http://blackice.localhost/auth/realms/dcm4chee
quarkus.oidc.client-id=blackice-quarkus
quarkus.oidc.credentials.secret=${QUARKUS_OIDC_SECRET}
quarkus.oidc.application-type=web-app
```

Verifique que **nenhuma** linha `quarkus.oidc.tls.verification` sobrou.

- [ ] **Step 8: Mover a base da Admin REST**

Em `infra/keycloak/configure-blackice.sh`, substituir a linha 32:

```sh
KB=https://localhost:8843/auth
```

> A Admin REST se move junto com o `KC_HTTP_RELATIVE_PATH`. Sem isto o script
> morre em `TOKEN FAIL`.

- [ ] **Step 9: Validar a sintaxe do Compose e subir**

> **Armadilha verificada em 2026-08-07.** `src/main/docker/Dockerfile.jvm` copia o `target/` **já construído** — ele não compila nada. Mudar `application.properties` e rodar `up -d --build` **não** coloca a mudança na imagem: o Maven nunca roda, e o layer do `target/` pode nem invalidar o cache. É preciso reconstruir o jar primeiro e forçar o build da imagem.

De `apps/backend/`:

```bash
./mvnw package -DskipTests
```

Depois, de `infra/`:

```bash
docker compose -f compose.yml -f dcm4chee/compose.yml -f compose.apps.yml config --quiet
docker compose -f compose.yml -f dcm4chee/compose.yml -f compose.apps.yml build --no-cache backend
docker compose -f compose.yml -f dcm4chee/compose.yml -f compose.apps.yml up -d
```

Expected: `config --quiet` sem saída (sucesso); `up` recria `keycloak`, `arc` e `backend`. O Keycloak leva ~25s para responder.

Antes de rodar as asserções, confirme que o Keycloak **aceitou** a config de hostname:

```bash
docker logs infra-keycloak-1 2>&1 | grep -iE 'hostname|relative-path' | head
```

Expected: nenhuma linha de erro. `KC_HOSTNAME` com componente de path depende de a hostname feature v2 aceitar path na URL completa, e de ele bater com `KC_HTTP_RELATIVE_PATH`. Se o Keycloak tiver rejeitado a config, é aqui que aparece — e isso distingue "Keycloak recusou" de "roteamento do Traefik errado" quando a asserção A falhar.

- [ ] **Step 10: Rodar as asserções para vê-las passar**

```bash
bash "$SCRATCH/assert-fase1.sh"
```

Expected: **seis** linhas `ok` (A-F), exit 0. Se **B** falhar mas **A** passar, o router `spa` está engolindo `/auth` — confira a `priority=10`. Se **E** ou **F** falharem, o `arc` não pegou o novo `AUTH_SERVER_URL`: rode `docker compose … up -d --force-recreate arc`.

- [ ] **Step 11: Provar que a Admin REST sob `/auth` funciona**

Da raiz do repo:

```bash
bash infra/keycloak/configure-blackice.sh
```

Expected: termina em `OK: config base pronta. Roles NÃO atribuídas (gate humano — ver README).` e as quatro linhas anteriores dizem `já existe` / `já aplicado` (o script é idempotente e o realm não mudou nesta fase). Qualquer `TOKEN FAIL` significa que o `KB` do Step 8 está errado.

- [ ] **Step 12: Atualizar o readiness probe do E2E**

Em `apps/frontend/README.md:56`, substituir por:

```powershell
    $location -match '^http://blackice\.localhost/auth/realms/dcm4chee/protocol/openid-connect/auth\?'
```

E em `apps/frontend/README.md:95`, substituir a URL citada no contrato de CI por
`http://blackice.localhost/auth/realms/dcm4chee/protocol/openid-connect/auth?...`.

> Este probe é executável e é o que trava o CI se a Fase 1 regredir. Não é
> documentação decorativa.

- [ ] **Step 13: Atualizar a documentação de infra**

Em `infra/keycloak/README.md`, na seção **"Por que `curl` e não `kcadm`"**, substituir a última frase (`Keycloak roda só em **HTTPS na 8843**.`) por:

```markdown
O Keycloak serve **HTTP em :8080 na rede interna** (atrás do Traefik, em
`http://${APP_HOST}/auth`) **e HTTPS na 8843** (backchannel do Archive e Admin
REST). O root path do servidor é `/auth` — vale para os dois listeners, e por
isso a base da Admin REST no script é `https://localhost:8843/auth`.
```

Em `infra/.env.example`, o comentário de `DCM4CHEE_HOST` deixou de ser verdade
sobre o `KC_HOSTNAME`. Substituir por:

```
# Hostname usado pelo Archive (ARCHIVE_HOST / UI_AUTH_SERVER_URL). O KC_HOSTNAME
# do Keycloak NÃO vem mais daqui: desde o spec 2026-08-07 ele é ${APP_HOST}/auth,
# servido pelo Traefik no mesmo origin da aplicação.
DCM4CHEE_HOST=localhost
```

- [ ] **Step 14: Rodar o E2E — o snapshot de pixel é a prova**

Esta é a **primeira** vez que o Playwright roda nesta tarefa (o Step 1 não pôde rodar — o login estava quebrado). O snapshot commitado é a referência.

De `apps/frontend`:

```bash
docker run --rm --network host -v "$PWD:/work" -w /work -e CI=true \
  -e BLACKICE_E2E_URL=http://blackice.localhost \
  mcr.microsoft.com/playwright:v1.62.0-noble \
  npx playwright test e2e/keycloak-login.spec.ts
```

Expected: 4 passed, **sem** `--update-snapshots`. Snapshot inalterado prova que a
mudança foi puramente de transporte: mesmo tema, mesmo layout, outro endereço.

**Se o snapshot falhar, não atualize a baseline.** Nada nesta fase deveria mudar pixel.

Sem um baseline "antes" para comparar, use este procedimento para desambiguar — e **só** neste caso de falha:

```bash
# 1. guarde as mudanças de infra sem perdê-las
git stash push -- infra/ apps/backend/src/main/resources/application.properties
# 2. suba a stack no estado anterior e veja se o teste passava antes
#    (vai falhar por 401 no login — o que já prova que a divergência NÃO é sua)
# 3. restaure
git stash pop
```

Se o teste não passava antes por causa do 401, a divergência de pixel é anterior à sua mudança: **reporte como DONE_WITH_CONCERNS** com as duas imagens (`test-results/playwright/`), não tente consertar.

- [ ] **Step 15: GATE HUMANO — conferência no browser**

Peça ao humano para abrir `http://blackice.localhost/` e logar com `dr.teste` / `teste123`. Ele deve confirmar, explicitamente:

1. **Nenhum aviso de certificado** em momento algum. *(Este é o objetivo da fase.)*
2. A URL da tela de login começa com `http://blackice.localhost/auth/`.
3. O tema BlackICE está intacto (wordmark, cores, campos).
4. O login conclui e volta para o SPA.

Não prossiga sem essa confirmação.

- [ ] **Step 16: Atualizar o Graphify e commitar**

Rodar a atualização semântica `--update` pela skill do Graphify e revisar o diff de `graphify-out/` (`AGENTS.md`). Depois, **com autorização explícita do humano**:

```bash
git add infra/dcm4chee/compose.yml infra/compose.apps.yml \
        apps/backend/src/main/resources/application.properties \
        infra/keycloak/configure-blackice.sh infra/keycloak/README.md \
        infra/.env.example apps/frontend/README.md \
        docs/superpowers/specs/2026-08-07-keycloak-same-origin-design.md \
        docs/superpowers/plans/2026-08-07-keycloak-same-origin.md \
        graphify-out/
git commit -m "🔐 serve o Keycloak same-origin em /auth atrás do Traefik"
```

---

### Task 2: Fase 2 — renomear o realm para `blackice`

Só depois do gate humano da Task 1.

**Files:**
- Modify: `infra/dcm4chee/compose.yml` (serviço `keycloak`: `REALM_NAME`; serviço `arc`: `REALM_NAME`)
- Modify: `infra/compose.apps.yml` (env `QUARKUS_OIDC_AUTH_SERVER_URL`)
- Modify: `apps/backend/src/main/resources/application.properties` (`quarkus.oidc.auth-server-url`)
- Modify: `infra/keycloak/configure-blackice.sh:29`
- Modify: `apps/frontend/README.md` (readiness probe, nas duas ocorrências)
- Modify: `infra/keycloak/README.md` (título e menções ao realm)
- Test: `apps/frontend/e2e/keycloak-login.spec.ts` (**não modificar**)

**Interfaces:**
- Consumes: da Task 1 — Keycloak em `http://blackice.localhost/auth` (browser), `http://keycloak:8080/auth` (interno), Admin REST em `https://localhost:8843/auth/admin`.
- Produces: realm `blackice` operante — o **mesmo** realm renomeado in place, não um segundo realm. `dcm4chee` deixa de resolver; o rollback é a receita de 5 passos na seção "Reversão" do spec.

- [ ] **Step 1: Escrever o script de asserção da Fase 2**

Em `$SCRATCH/assert-fase2.sh` (mesmo `SCRATCH` definido na Task 1, Step 2):

```bash
#!/usr/bin/env bash
set -u
fail=0
chk() { if [ "$2" = "$3" ]; then echo "ok   $1"; else echo "FALHA $1"; echo "  esperado: $2"; echo "  obtido:   $3"; fail=1; fi; }

BASE=http://blackice.localhost

# A. o realm novo existe
chk "discovery do realm blackice" "200" \
  "$(curl -sS -o /dev/null -w '%{http_code}' "$BASE/auth/realms/blackice/.well-known/openid-configuration")"

# B. o rename foi IN PLACE: o nome antigo não resolve mais
#    (corrigido em 2026-08-07 — a versão original esperava 200 aqui, sob a
#     premissa do realm criado "ao lado", que se provou impossível; ver o
#     bloco antes do Step 3)
chk "realm dcm4chee não existe mais" "404" \
  "$(curl -sS -o /dev/null -w '%{http_code}' "$BASE/auth/realms/dcm4chee/.well-known/openid-configuration")"

# C. o login vai para o realm novo e NÃO contém a string dcm4chee
loc=$(curl -sS --max-redirs 0 -D - -o /dev/null "$BASE/api/login" \
      | sed -n 's/^[Ll]ocation: *//p' | tr -d '\r')
chk "Location no realm blackice" "$BASE/auth/realms/blackice/protocol/openid-connect/auth" "$(echo "$loc" | cut -d'?' -f1)"
case "$loc" in *dcm4chee*) echo "FALHA URL de login ainda contém 'dcm4chee'"; echo "  $loc"; fail=1;;
                        *) echo "ok   URL de login sem 'dcm4chee'";; esac

# D. o client do audience EXISTE no realm novo — este é o modo de falha silencioso
adm=$(docker exec infra-keycloak-1 sh -c 'curl -sk -X POST "https://localhost:8843/auth/realms/master/protocol/openid-connect/token" -d grant_type=password -d client_id=admin-cli -d "username=$KEYCLOAK_ADMIN" -d "password=$KEYCLOAK_ADMIN_PASSWORD"' \
      | sed -n 's/.*"access_token":"\([^"]*\)".*/\1/p')
[ -n "$adm" ] || { echo "FALHA não obteve token admin"; exit 1; }
hits=$(docker exec infra-keycloak-1 sh -c "curl -sk -H 'Authorization: Bearer $adm' 'https://localhost:8843/auth/admin/realms/blackice/clients?clientId=dcm4chee-arc-rs'" \
      | grep -c '"clientId":"dcm4chee-arc-rs"')
chk "client do audience existe no realm blackice" "1" "$hits"

exit $fail
```

- [ ] **Step 2: Rodar as asserções para vê-las falhar**

```bash
bash "$SCRATCH/assert-fase2.sh"
```

Expected: **A**, **C** e **D** falham (realm `blackice` ainda não existe); **B** passa.

> **Steps 3 e 4 foram refeitos em 2026-08-07.** A mecânica original — trocar `REALM_NAME` e deixar o `--import-realm` criar o realm `blackice` ao lado do `dcm4chee` — **trava o boot do Keycloak**. `dcm4che-realm.json` embute 112 UUIDs literais não templados por `${REALM_NAME}`, e `KEYCLOAK_ROLE.ID` é PK global: reimportar o mesmo arquivo sob outro nome colide sempre. Ver o spec para a evidência.
>
> **Os Steps 3 e 4 abaixo já foram executados pelo controller** (o rename era o teste que decidia a estratégia). O realm já se chama `blackice`, a role padrão já é `default-roles-blackice`, e o Keycloak já foi recriado e sobe limpo. Estão registrados aqui como o procedimento correto, não como trabalho pendente.

- [ ] **Step 3 (FEITO): Renomear o realm in place e ajustar `REALM_NAME`**

Pela Admin REST, de dentro do container `keycloak` (a base é `https://localhost:8843/auth` desde a Fase 1):

```
PUT /auth/admin/realms/dcm4chee  {"realm":"blackice"}
PUT /auth/admin/realms/blackice/roles/default-roles-dcm4chee  {"name":"default-roles-blackice", …}
```

O segundo é necessário porque a role padrão mantém o nome antigo depois do rename, e ela viaja no claim `realm_access.roles` — o caminho que `quarkus.oidc.roles.role-claim-path` lê. Para o PUT da role, faça GET da representação inteira, troque só o `name` e mande de volta.

Depois, em `infra/dcm4chee/compose.yml`, `REALM_NAME: blackice` nos serviços `keycloak` **e** `arc`, com o comentário explicando que este valor **acompanha** o nome real (ele não renomeia nada) e que trocá-lo sozinho quebra o boot.

- [ ] **Step 4 (FEITO): Recriar keycloak e confirmar que o boot sobrevive**

Este era o risco que podia afundar a estratégia: com o realm renomeado, o import de boot poderia não reconhecê-lo e tentar criar outro, recaindo na colisão. Verificado — sobe em 25s, container `running`, `/auth/realms/blackice/.well-known/openid-configuration` em 200.

<details>
<summary>Mecânica original (não funciona — mantida para registro)</summary>

- [ ] **Step 3: Trocar o nome do realm nos dois serviços**

Em `infra/dcm4chee/compose.yml`, serviço `keycloak`, substituir o valor de `REALM_NAME` e atualizar o comentário acima dele:

```yaml
      # Nome do realm importado. O default oficial da imagem é "dcm4che"; foi
      # fixado em "dcm4chee" na Task 2 e trocado para "blackice" pelo spec
      # 2026-08-07, para que o nome do produto de terceiro não apareça na URL
      # de login. `--import-realm` cria o realm novo AO LADO do antigo, que
      # fica como rollback — nenhum volume é apagado.
      REALM_NAME: blackice
```

E no serviço `arc`:

```yaml
      REALM_NAME: blackice
```

> Os dois **têm** de mudar no mesmo commit. O `provider-url` do Archive é
> `${env.AUTH_SERVER_URL}/realms/${env.REALM_NAME}`, resolvido a cada boot.

- [ ] **Step 4: Recriar keycloak e arc**

De `infra/`:

```bash
docker compose -f compose.yml -f dcm4chee/compose.yml -f compose.apps.yml config --quiet
docker compose -f compose.yml -f dcm4chee/compose.yml -f compose.apps.yml up -d --force-recreate keycloak arc
```

Expected: o Keycloak sobe e responde em ~25s. **O teste do import é a asserção A do Step 8, não o log** — o formato da linha de import desta imagem não foi observado, e ausência de log não é evidência de falha.

Se a asserção A falhar depois, use o log como **diagnóstico** (não como gate):

```bash
docker logs infra-keycloak-1 2>&1 | grep -iE "import|realm" | tail -20
docker exec infra-keycloak-1 ls /opt/keycloak/data/import/
```

O segundo comando confirma que `dcm4che-realm.json` ainda existe no volume — sem ele, `--import-realm` não tem o que importar.

</details>

- [ ] **Step 5: Apontar o script de configuração para o realm novo**

Em `infra/keycloak/configure-blackice.sh`, substituir a linha 29:

```sh
  -e REALM="blackice" \
```

A linha 28 (`-e ARC_CLIENT="dcm4chee-arc-rs"`) **não muda**.

- [ ] **Step 6: Criar client, mapper, usuário e tema no realm novo**

```bash
bash infra/keycloak/configure-blackice.sh
```

Expected: quatro linhas `criado` / `aplicado` (o realm é novo, então nada existe ainda) e `OK:` no fim.

- [ ] **Step 7: Apontar o backend para o realm novo**

Em `apps/backend/src/main/resources/application.properties`:

```properties
quarkus.oidc.auth-server-url=http://blackice.localhost/auth/realms/blackice
```

Em `infra/compose.apps.yml`:

```yaml
      QUARKUS_OIDC_AUTH_SERVER_URL: http://keycloak:8080/auth/realms/blackice
```

Depois, **reconstrua o jar antes da imagem** (mesma armadilha da Task 1 Step 9 — o Dockerfile copia `target/` pré-construído e não compila):

De `apps/backend/`:

```bash
./mvnw package -DskipTests
```

De `infra/`:

```bash
docker compose -f compose.yml -f dcm4chee/compose.yml -f compose.apps.yml build --no-cache backend
docker compose -f compose.yml -f dcm4chee/compose.yml -f compose.apps.yml up -d backend
```

- [ ] **Step 8: Rodar as asserções para vê-las passar**

```bash
bash "$SCRATCH/assert-fase2.sh"
```

Expected: cinco linhas `ok`, exit 0.

**A asserção D é a que importa.** Se ela falhar, o mapper `arc-audience` está injetando no `aud` um client que não existe no realm `blackice` — o login continuaria funcionando normalmente e só o DICOMweb quebraria com 403, meses depois, quando o caminho de retrieve for construído. Não siga adiante com D vermelho.

- [ ] **Step 9: GATE HUMANO — atribuir as realm roles**

O realm `blackice` nasceu sem as roles atribuídas. O `configure-blackice.sh` não as atribui por decisão de projeto (`infra/keycloak/README.md`, seção "Roles (gate humano)").

Peça ao humano para atribuir a role `auth` ao `dr.teste` no realm `blackice`, pelo procedimento já documentado nesse README. Sem isso o login **conclui** e a autorização falha depois — sintoma visível, mas confuso se você não souber a causa.

- [ ] **Step 10: Atualizar o readiness probe e a documentação**

Em `apps/frontend/README.md`, nas **duas** ocorrências (linha 56 e a citação da linha 95), trocar `realms/dcm4chee` por `realms/blackice`.

Em `infra/keycloak/README.md`:
- Título: ``# Config Keycloak do BlackICE (realm `blackice`)``
- Primeiro parágrafo: o realm compartilhado com o Archive agora se chama `blackice`. Manter a explicação do **audience compartilhado** — ela continua sendo o motivo de um realm só, e é o que impede um realm separado (um token de outro realm não valida no Archive, e a saída por service account custaria a atribuição por usuário no audit trail DICOM).
- Seção "Roles (gate humano)": trocar ``O realm `dcm4chee` `` por ``O realm `blackice` ``.

- [ ] **Step 11: Rodar o E2E**

De `apps/frontend`, o mesmo comando das etapas anteriores.

Expected: 4 passed, snapshot inalterado. A asserção de CSS do teste é substring (`link[href*="/login/blackice/css/blackice.css"]`) e sobrevive tanto ao prefixo `/auth` quanto ao realm novo.

- [ ] **Step 12: GATE HUMANO — conferência final no browser**

Peça ao humano para logar de novo e confirmar:

1. A URL da tela de login é `http://blackice.localhost/auth/realms/blackice/login-actions/authenticate?...` — **sem nenhuma ocorrência de `dcm4chee`**.
2. Sem aviso de certificado.
3. Login conclui e o SPA carrega com a sessão.

- [ ] **Step 13: Atualizar o Graphify e commitar**

Rodar a atualização semântica `--update` pela skill do Graphify e revisar o diff de `graphify-out/`. Depois, **com autorização explícita do humano**:

```bash
git add infra/dcm4chee/compose.yml infra/compose.apps.yml \
        apps/backend/src/main/resources/application.properties \
        infra/keycloak/configure-blackice.sh infra/keycloak/README.md \
        apps/frontend/README.md graphify-out/
git commit -m "🔐 renomeia o realm para blackice, tirando dcm4chee da URL de login"
```

> O realm `dcm4chee` **continua existindo** no Keycloak, intacto. Apagá-lo é
> decisão separada, fora deste plano. Para reverter: volte `REALM_NAME` para
> `dcm4chee` nos dois serviços, reverta as URLs de `auth-server-url` e reinicie.

---

## Self-review

**Cobertura do spec:**

| Requisito do spec | Onde |
| :-- | :-- |
| Itens 1-3 (host, porta, cert) | Task 1, Steps 4-10; provado no Step 15 |
| Item 4 (`dcm4chee` no path) | Task 2, Steps 3-8; provado na asserção C e no Step 12 |
| Prefixo `/auth`, não subdomínio | Task 1, Step 4 |
| `priority` no router Traefik | Task 1, Step 4, com o motivo inline |
| `KC_HTTP_ENABLED`, 8843 preservada | Task 1, Step 4 |
| Archive movido para o novo path | Task 1, Step 5; asserções E e F |
| Admin REST sob `/auth` | Task 1, Step 8; provado no Step 11 |
| `tls.verification=none` apagado | Task 1, Step 7; asserção D |
| Realm renomeado in place, sem wipe e sem perder configuração | Task 2, Steps 3-4; asserção B |
| Roles re-atribuídas no gate humano | Task 2, Step 9 |
| Audience do realm novo (falha silenciosa) | Task 2, Step 1 asserção D, Step 8 |
| Snapshot Playwright como rede de segurança | Task 1, Steps 1 e 14; Task 2, Step 11 |
| Item 5 e Authentication API | Fora de escopo, por decisão registrada no spec |

**Consistência:** `/auth` é o path em todos os passos das duas tarefas; `blackice` é o realm em todos os passos da Task 2; `dcm4chee-arc-rs` é constante nas duas. As asserções da Fase 1 usam `realms/dcm4chee` (correto — a Fase 1 não renomeia nada) e as da Fase 2 usam `realms/blackice`.

**Sem placeholders:** todos os passos trazem o comando ou o conteúdo exato. Os dois pontos deixados ao humano são gates deliberados (conferência visual e atribuição de roles), não lacunas.
