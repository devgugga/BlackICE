# Plano 0 — Fundação (stack + auth) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Subir a stack completa (DCM4CHEE 5.34.3-secure + Keycloak + LDAP + Postgres + Traefik), com Quarkus como BFF/OIDC e um SPA Vue vazio autenticado servido na mesma origem, para que um usuário consiga logar via Keycloak e cair numa tela autenticada.

**Architecture:** Traefik é a única porta exposta (um hostname): `/` → SPA estático, `/api/*` → Quarkus. Quarkus é cliente OIDC em modo web-app (BFF): faz Authorization Code + PKCE, guarda o token server-side e devolve só cookie de sessão HttpOnly. DCM4CHEE, Keycloak, LDAP e Postgres ficam na rede interna. Ver o design em [`docs/superpowers/specs/2026-07-23-blackice-backend-frontend-design.md`](../specs/2026-07-23-blackice-backend-frontend-design.md).

**Tech Stack:** Docker Compose, Traefik v3, DCM4CHEE Archive 5.34.3-secure, Keycloak 25.0.6, PostgreSQL 17, Quarkus 3.x (Java 21) com `quarkus-oidc`, Vue 3 + Vite + TypeScript + Vue Router.

## Global Constraints

- **Imagens pinadas (nunca `latest`)** — exatamente como em [`docs/architecture/dcm4chee-archive.md`](../../architecture/dcm4chee-archive.md): `dcm4che/dcm4chee-arc-psql:5.34.3-secure`, `dcm4che/slapd-dcm4chee:2.6.10-34.3`, `dcm4che/postgres-dcm4chee:17.4-34`, `dcm4che/keycloak:25.0.6`.
- **BFF:** o access token NUNCA chega ao browser. Só cookie de sessão HttpOnly.
- **Mesma origem:** SPA e `/api` sob um único hostname via Traefik. Sem CORS entre eles.
- **DCM4CHEE nunca exposto ao browser** — só o Quarkus fala DICOMweb com ele (rede interna).
- **Não commitar segredos.** Segredos em `infra/.env` (git-ignored); versionar `infra/.env.example`.
- **Repo monorepo:** `infra/` (compose + config), `backend/` (Quarkus), `frontend/` (Vue). Docs já existem em `docs/`.
- Nada é commitado no repo do produto sem pedido explícito do humano (regra do AGENTS.md). Os `git commit` deste plano são da própria implementação da fundação e valem como o "pedido explícito" ao executar este plano.

---

### Task 1: Skeleton do monorepo + Traefik + Postgres do produto

**Files:**
- Create: `infra/docker-compose.yml`
- Create: `infra/.env.example`
- Create: `infra/.env` (git-ignored)
- Create: `.gitignore` (append rules)
- Create: `frontend/.gitkeep`, `backend/.gitkeep`

**Interfaces:**
- Produces: rede Docker `blackice`, serviço `traefik` (portas 80/8081-dashboard), serviço `product-db` (Postgres do produto, DB `blackice`, porta interna 5432). Hostname base `blackice.localhost`.

- [ ] **Step 1: Criar `.gitignore` na raiz (append)**

```gitignore
# infra secrets
infra/.env
# node / vite
frontend/node_modules/
frontend/dist/
# quarkus / maven
backend/target/
```

- [ ] **Step 2: Criar `infra/.env.example`**

```dotenv
# Hostname base servido pelo Traefik (mesma origem)
APP_HOST=blackice.localhost

# Postgres do PRODUTO (Quarkus) — distinto do Postgres do DCM4CHEE
PRODUCT_DB=blackice
PRODUCT_DB_USER=blackice
PRODUCT_DB_PASSWORD=change-me-product

# Segredo do client OIDC do Quarkus (definido na Task 3)
QUARKUS_OIDC_SECRET=change-me-oidc
```

- [ ] **Step 3: Copiar para `.env` real**

Run: `cp infra/.env.example infra/.env`
(edite `infra/.env` com valores reais; ele é git-ignored)

- [ ] **Step 4: Criar `infra/docker-compose.yml` (base: Traefik + Postgres do produto)**

```yaml
services:
  traefik:
    image: traefik:v3.1
    command:
      - "--providers.docker=true"
      - "--providers.docker.exposedbydefault=false"
      - "--entrypoints.web.address=:80"
      - "--api.dashboard=true"
      - "--api.insecure=true"   # dashboard em :8081 só para DEV
    ports:
      - "80:80"
      - "8081:8080"
    volumes:
      - "/var/run/docker.sock:/var/run/docker.sock:ro"
    networks: [blackice]

  product-db:
    image: postgres:17
    environment:
      POSTGRES_DB: ${PRODUCT_DB}
      POSTGRES_USER: ${PRODUCT_DB_USER}
      POSTGRES_PASSWORD: ${PRODUCT_DB_PASSWORD}
    volumes:
      - product-db-data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ${PRODUCT_DB_USER} -d ${PRODUCT_DB}"]
      interval: 5s
      timeout: 3s
      retries: 10
    networks: [blackice]

volumes:
  product-db-data:

networks:
  blackice:
    name: blackice
```

- [ ] **Step 5: Subir e verificar**

Run:
```bash
cd infra && docker compose up -d traefik product-db && docker compose ps
```
Expected: ambos `running`; `product-db` fica `healthy` em ~15s.

- [ ] **Step 6: Verificar Traefik e Postgres**

Run: `curl -s -o /dev/null -w "%{http_code}" http://localhost:8081/api/rawdata`
Expected: `200`

Run: `docker compose -f infra/docker-compose.yml exec product-db pg_isready -U blackice -d blackice`
Expected: `... accepting connections`

- [ ] **Step 7: Commit**

```bash
git add .gitignore infra/docker-compose.yml infra/.env.example frontend/.gitkeep backend/.gitkeep
git commit -m "infra: skeleton do monorepo com Traefik e Postgres do produto"
```

---

### Task 2: Stack DCM4CHEE-secure (Keycloak + LDAP + arc + db)

> **Nota de honestidade:** o compose oficial da variante *secure* é mantido upstream e varia entre releases. NÃO reescreva o YAML de memória. Puxe o oficial e **pin** as tags do Global Constraints. A verificação abaixo é o gate real do sucesso.

**Files:**
- Create: `infra/dcm4chee/docker-compose.dcm4chee.yml` (compose oficial adaptado)
- Create: `infra/dcm4chee/README.md` (origem + tags pinadas)
- Modify: `infra/.env.example` e `infra/.env` (hosts do DCM4CHEE/Keycloak)

**Interfaces:**
- Produces: serviços `ldap` (slapd), `keycloak` (Keycloak 25.0.6, realm `dcm4chee` importado), `arc-db` (postgres-dcm4chee), `arc` (Archive 5.34.3-secure). Endpoints internos na rede `blackice`: `http://keycloak:8080`, `http://arc:8080/dcm4chee-arc`.

- [ ] **Step 1: Obter o compose seguro oficial**

Abra o wiki de deploy do dcm4chee (página "Run secured archive services on a single host", a partir de https://github.com/dcm4che/dcm4chee-arc-light/wiki/Running-on-Docker) e copie o `docker-compose.yml` da variante **secure** (serviços `ldap`, `keycloak`, `db`, `arc`) para `infra/dcm4chee/docker-compose.dcm4chee.yml`.

- [ ] **Step 2: Pinar as tags exatamente**

Edite `infra/dcm4chee/docker-compose.dcm4chee.yml` para usar SOMENTE estas tags (Global Constraints):
```yaml
  ldap:
    image: dcm4che/slapd-dcm4chee:2.6.10-34.3
  arc-db:
    image: dcm4che/postgres-dcm4chee:17.4-34
  keycloak:
    image: dcm4che/keycloak:25.0.6
  arc:
    image: dcm4che/dcm4chee-arc-psql:5.34.3-secure
```

- [ ] **Step 3: Anexar à rede `blackice`**

Garanta que cada serviço declare `networks: [blackice]` e adicione ao final do arquivo:
```yaml
networks:
  blackice:
    external: true
    name: blackice
```

- [ ] **Step 4: Documentar a origem em `infra/dcm4chee/README.md`**

```markdown
# Stack DCM4CHEE-secure

Compose adaptado do oficial (wiki "Run secured archive services on a single host"):
https://github.com/dcm4che/dcm4chee-arc-light/wiki/Running-on-Docker

Tags pinadas conforme docs/architecture/dcm4chee-archive.md (baseline 5.34.3).
Anexado à rede externa `blackice` criada por infra/docker-compose.yml.
```

- [ ] **Step 5: Subir a stack (ordem: ldap → keycloak → arc-db → arc)**

Run:
```bash
cd infra && docker compose -f docker-compose.yml -f dcm4chee/docker-compose.dcm4chee.yml up -d
```
Expected: 4 serviços novos sobem; `arc` leva 1-2 min para o WildFly ficar pronto.

- [ ] **Step 6: Verificar Keycloak (realm dcm4chee importado)**

Run:
```bash
curl -s http://localhost:8080/realms/dcm4chee/.well-known/openid-configuration | head -c 200
```
Expected: JSON com `"issuer":".../realms/dcm4chee"`. (Ajuste a porta se o compose oficial mapear o Keycloak em outra porta; anote a porta real no README.)

- [ ] **Step 7: Verificar que o Archive exige auth (não exposto sem token)**

Run:
```bash
docker compose -f infra/docker-compose.yml -f infra/dcm4chee/docker-compose.dcm4chee.yml \
  exec arc curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/dcm4chee-arc/aets/DCM4CHEE/rs/studies
```
Expected: `401` (QIDO exige Bearer). Confirma que o DICOMweb está de pé e protegido.

- [ ] **Step 8: Commit**

```bash
git add infra/dcm4chee/ infra/.env.example
git commit -m "infra: stack DCM4CHEE 5.34.3-secure (keycloak+ldap+arc) pinada"
```

---

### Task 3: Keycloak — client BFF do Quarkus + audience mapper + usuário de teste

> Configuramos DENTRO do realm `dcm4chee` (mesmo realm do Archive) — condição necessária para o **audience compartilhado** (spec, seção de token). Isso evita token exchange.

**Files:**
- Create: `infra/keycloak/configure-blackice.sh` (script idempotente via kcadm)
- Create: `infra/keycloak/README.md`

**Interfaces:**
- Consumes: Keycloak da Task 2, realm `dcm4chee`.
- Produces: client confidencial `blackice-quarkus` (standard flow + PKCE), com **audience mapper** adicionando o client do Archive ao claim `aud`; usuário de teste `dr.teste` com as roles DICOM. Secret exportado para `QUARKUS_OIDC_SECRET`.

- [ ] **Step 1: Descobrir o clientId do Archive no realm**

Run (dentro do container keycloak, autentica no admin):
```bash
docker compose -f infra/docker-compose.yml -f infra/dcm4chee/docker-compose.dcm4chee.yml exec keycloak \
  /opt/keycloak/bin/kcadm.sh config credentials --server http://localhost:8080 \
  --realm master --user admin --password admin
docker compose -f infra/docker-compose.yml -f infra/dcm4chee/docker-compose.dcm4chee.yml exec keycloak \
  /opt/keycloak/bin/kcadm.sh get clients -r dcm4chee --fields clientId
```
Expected: lista de clients; anote o clientId usado pelo Archive (tipicamente `dcm4chee-arc-ui` ou similar). Chame-o de `<ARC_CLIENT>` nos passos seguintes. (Se admin/senha diferirem, use os do compose oficial.)

- [ ] **Step 2: Escrever `infra/keycloak/configure-blackice.sh`**

```bash
#!/usr/bin/env sh
set -eu
# Executar DENTRO do container keycloak. Idempotente-ish (ignora "já existe").
KC=/opt/keycloak/bin/kcadm.sh
REALM=dcm4chee
ARC_CLIENT="${ARC_CLIENT:?defina ARC_CLIENT=<clientId do Archive>}"
APP_HOST="${APP_HOST:-blackice.localhost}"
SECRET="${QUARKUS_OIDC_SECRET:?defina QUARKUS_OIDC_SECRET}"

$KC config credentials --server http://localhost:8080 --realm master --user admin --password admin

# 1) Client confidencial do Quarkus (BFF): standard flow + PKCE, sem implicit
$KC create clients -r "$REALM" -s clientId=blackice-quarkus \
  -s enabled=true -s publicClient=false -s standardFlowEnabled=true \
  -s directAccessGrantsEnabled=false -s serviceAccountsEnabled=false \
  -s 'attributes."pkce.code.challenge.method"=S256' \
  -s "secret=$SECRET" \
  -s "redirectUris=[\"http://$APP_HOST/api/*\"]" \
  -s "webOrigins=[\"http://$APP_HOST\"]" || echo "client já existe, seguindo"

CID=$($KC get clients -r "$REALM" -q clientId=blackice-quarkus --fields id --format csv --noquotes | tail -n1)

# 2) Audience mapper: adiciona o client do Archive ao aud dos tokens do Quarkus
$KC create clients/"$CID"/protocol-mappers/models -r "$REALM" \
  -s name=arc-audience -s protocol=openid-connect \
  -s protocolMapper=oidc-audience-mapper \
  -s 'config."included.client.audience"='"$ARC_CLIENT" \
  -s 'config."access.token.claim"=true' \
  -s 'config."id.token.claim"=false' || echo "mapper já existe, seguindo"

# 3) Usuário de teste (ajuste as roles conforme as roles DICOM do realm)
$KC create users -r "$REALM" -s username=dr.teste -s enabled=true \
  -s firstName=Teste -s lastName=Radiologista || echo "user já existe"
$KC set-password -r "$REALM" --username dr.teste --new-password teste123
echo ">>> Atribua as realm roles DICOM ao dr.teste (ver README) <<<"
```

- [ ] **Step 3: Documentar as roles DICOM em `infra/keycloak/README.md`**

```markdown
# Config Keycloak do BlackICE (realm dcm4chee)

- `blackice-quarkus`: client confidencial (BFF), standard flow + PKCE.
- Audience mapper `arc-audience`: injeta o client do Archive no `aud` (audience
  compartilhado — evita token exchange; ver spec de design).
- `dr.teste` / `teste123`: usuário de teste.

## Roles DICOM (verificar no realm real)
O Archive autoriza por realm roles. Liste as existentes e atribua ao dr.teste:
  kcadm.sh get roles -r dcm4chee --fields name
Normalmente inclui algo como `user`/`administrator` do dcm4chee. Atribua as que
o Archive exige para QIDO/STOW/WADO. ESTE é o ponto que costuma travar o audience.
```

- [ ] **Step 4: Rodar o script**

Run:
```bash
docker compose -f infra/docker-compose.yml -f infra/dcm4chee/docker-compose.dcm4chee.yml exec \
  -e ARC_CLIENT=<ARC_CLIENT> -e QUARKUS_OIDC_SECRET=$(grep QUARKUS_OIDC_SECRET infra/.env | cut -d= -f2) \
  -e APP_HOST=blackice.localhost keycloak sh /dev/stdin < infra/keycloak/configure-blackice.sh
```
Expected: cria client, mapper e usuário (ou "já existe"). Sem erro fatal.

- [ ] **Step 5: Atribuir roles DICOM ao usuário de teste**

Run (substitua `<ROLE>` pelas roles listadas no README):
```bash
docker compose -f infra/docker-compose.yml -f infra/dcm4chee/docker-compose.dcm4chee.yml exec keycloak \
  /opt/keycloak/bin/kcadm.sh add-roles -r dcm4chee --uusername dr.teste --rolename <ROLE>
```
Expected: sem erro.

- [ ] **Step 6: Verificar audience no token (via password grant temporário do client BFF)**

> Só para validar o `aud`. O client BFF tem `directAccessGrants=false`, então valide o audience de forma equivalente: habilite direct grant temporariamente OU decodifique um token do fluxo real na Task 6. Verificação mínima aqui:

Run (lookup do id inline — `$CID` do script não persiste entre `exec`):
```bash
docker compose -f infra/docker-compose.yml -f infra/dcm4chee/docker-compose.dcm4chee.yml exec keycloak sh -c '
  KC=/opt/keycloak/bin/kcadm.sh
  CID=$($KC get clients -r dcm4chee -q clientId=blackice-quarkus --fields id --format csv --noquotes | tail -n1)
  $KC get clients/$CID/protocol-mappers/models -r dcm4chee --fields name'
```
Expected: inclui `arc-audience`. (As credenciais do `kcadm config credentials` do Step 1
persistem no container; se o container foi recriado, rode o config de novo.)

- [ ] **Step 7: Commit**

```bash
git add infra/keycloak/
git commit -m "infra: client BFF blackice-quarkus + audience mapper + usuario de teste"
```

---

### Task 4: Quarkus BFF — `/api/me`, `/api/login`, OIDC web-app

**Files:**
- Create: `backend/pom.xml` (ou gerado via CLI), `backend/src/main/resources/application.properties`
- Create: `backend/src/main/java/dev/blackice/api/MeResource.java`
- Create: `backend/src/main/java/dev/blackice/api/SessionResponse.java`
- Test: `backend/src/test/java/dev/blackice/api/MeResourceTest.java`

**Interfaces:**
- Consumes: Keycloak realm `dcm4chee`, client `blackice-quarkus` + secret (Task 3).
- Produces:
  - `GET /api/me` → `200 {"subject":string,"username":string,"roles":string[]}` se autenticado; `401` se anônimo (para a guarda do SPA).
  - `GET /api/login` → `@Authenticated` (dispara o redirect OIDC no browser; após login volta ao app).
  - `record SessionResponse(String subject, String username, List<String> roles)`.

- [ ] **Step 1: Gerar o projeto Quarkus**

Run:
```bash
cd backend && quarkus create app dev.blackice:blackice-backend \
  --extension='rest,rest-jackson,oidc,smallrye-health' --no-code
```
Expected: projeto criado (Quarkus 3.x, Java 21). Se `quarkus` CLI não existir, use o Maven archetype equivalente com as mesmas extensões.

- [ ] **Step 2: Configurar `application.properties`**

```properties
quarkus.http.root-path=/
# OIDC em modo web-app (BFF): sessão em cookie, token server-side
quarkus.oidc.auth-server-url=http://keycloak:8080/realms/dcm4chee
quarkus.oidc.client-id=blackice-quarkus
quarkus.oidc.credentials.secret=${QUARKUS_OIDC_SECRET}
quarkus.oidc.application-type=web-app
# Cookie de sessão endurecido (CSRF entra no Plano 2 com endpoints mutantes)
quarkus.oidc.token-state-manager.split-tokens=true
quarkus.oidc.authentication.cookie-same-site=lax
# %dev: aponta ao Keycloak via localhost quando roda fora do compose
%dev.quarkus.oidc.auth-server-url=http://localhost:8080/realms/dcm4chee
```

- [ ] **Step 3: Escrever o DTO `SessionResponse`**

```java
package dev.blackice.api;

import java.util.List;

public record SessionResponse(String subject, String username, List<String> roles) {}
```

- [ ] **Step 4: Escrever o teste que falha (`/api/me` anônimo → 401)**

```java
package dev.blackice.api;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import static io.restassured.RestAssured.given;

@QuarkusTest
class MeResourceTest {

    @Test
    void anonymo_recebe_401() {
        given().redirects().follow(false)
            .when().get("/api/me")
            .then().statusCode(401);
    }
}
```

- [ ] **Step 5: Rodar o teste e ver falhar**

Run: `cd backend && ./mvnw test -Dtest=MeResourceTest`
Expected: FAIL (endpoint ainda não existe → 404, ou redirect).

- [ ] **Step 6: Implementar `MeResource`**

```java
package dev.blackice.api;

import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.Authenticated;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import java.util.List;
import org.eclipse.microprofile.jwt.JsonWebToken;

@Path("/api")
public class MeResource {

    @Inject SecurityIdentity identity;
    @Inject JsonWebToken jwt;   // token da sessão web-app, server-side

    // Guarda do SPA: 401 (não redireciona) quando anônimo, para o fetch tratar.
    @GET @Path("/me") @PermitAll
    public Response me() {
        if (identity == null || identity.isAnonymous()) {
            return Response.status(401).build();
        }
        var roles = List.copyOf(identity.getRoles());
        // subject = claim `sub` (UUID estável); username = `preferred_username` (dr.teste).
        // getPrincipal().getName() devolve o `sub`, NÃO o username — por isso lemos o claim.
        var subject = jwt.getSubject();
        String username = jwt.getClaim("preferred_username");
        if (username == null) username = subject; // fallback defensivo
        return Response.ok(new SessionResponse(subject, username, roles)).build();
    }

    // Navegação do browser aqui dispara o redirect OIDC (web-app).
    @GET @Path("/login") @Authenticated
    public Response login() {
        return Response.seeOther(java.net.URI.create("/")).build();
    }
}
```

> Confirme na execução o que `preferred_username` traz no SEU Keycloak; se o claim
> vier diferente, ajuste aqui (é o que faz o E2E da Task 6 mostrar `dr.teste`).

- [ ] **Step 7: Rodar o teste e ver passar**

Run: `cd backend && ./mvnw test -Dtest=MeResourceTest`
Expected: PASS (anônimo recebe 401).

- [ ] **Step 8: ⚠️ Verificação load-bearing — 401 vs 302 no web-app REAL**

> `@QuarkusTest` pode não exercer o challenge OIDC web-app completo; o teste unitário
> pode passar enquanto o endpoint implantado responde **302 (redirect)** em vez de 401.
> Toda a guarda do SPA (Task 5) e o E2E (Task 6) dependem disso ser **401**. Valide
> AGORA, contra o Keycloak rodando, antes de seguir.

Run (com a stack da Task 2 de pé e `%dev` apontando ao `localhost:8080`):
```bash
cd backend && ./mvnw quarkus:dev &
sleep 25
curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/api/me
```
Expected: `401`. Se vier `302`, o `@PermitAll` não está evitando o challenge no web-app —
ajuste (ex.: `quarkus.oidc.authentication.fail-blocking-page` / rota pública específica,
ou trate `/api/**` como tenant que devolve 401) **antes** da Task 5. Este é o ponto
que, se ignorado, força retrabalho nas Tasks 5–6.

- [ ] **Step 9: Commit**

```bash
git add backend/
git commit -m "feat(backend): BFF OIDC web-app com /api/me e /api/login"
```

---

### Task 5: Vue — guarda de rota + shell autenticado

**Files:**
- Create: `frontend/` (projeto Vite Vue-TS), `frontend/src/router/index.ts`
- Create: `frontend/src/lib/session.ts`
- Create: `frontend/src/views/HomeView.vue`, `frontend/src/App.vue`
- Test: `frontend/src/lib/session.spec.ts`

**Interfaces:**
- Consumes: `GET /api/me` (Task 4).
- Produces: `fetchSession(): Promise<SessionResponse | null>` (null quando 401); guard que redireciona o browser a `/api/login` quando não autenticado; `HomeView` mostra o `username`.

- [ ] **Step 1: Gerar o projeto Vue**

Run:
```bash
cd frontend && npm create vite@latest . -- --template vue-ts && npm install && npm install vue-router@4 && npm install -D vitest
```
Expected: projeto Vue 3 + TS criado; vitest instalado.

- [ ] **Step 2: Escrever `session.ts`**

```ts
export interface SessionResponse {
  subject: string;
  username: string;
  roles: string[];
}

export async function fetchSession(): Promise<SessionResponse | null> {
  const res = await fetch('/api/me', { credentials: 'include' });
  if (res.status === 401) return null;
  if (!res.ok) throw new Error(`/api/me falhou: ${res.status}`);
  return (await res.json()) as SessionResponse;
}
```

- [ ] **Step 3: Escrever o teste que falha**

```ts
import { describe, it, expect, vi, afterEach } from 'vitest';
import { fetchSession } from './session';

afterEach(() => vi.restoreAllMocks());

describe('fetchSession', () => {
  it('retorna null em 401', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ status: 401, ok: false }));
    expect(await fetchSession()).toBeNull();
  });

  it('retorna a sessão em 200', async () => {
    const body = { subject: 's', username: 'dr.teste', roles: ['user'] };
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      status: 200, ok: true, json: () => Promise.resolve(body),
    }));
    expect(await fetchSession()).toEqual(body);
  });
});
```

- [ ] **Step 4: Rodar e ver falhar/passar**

Run: `cd frontend && npx vitest run src/lib/session.spec.ts`
Expected: PASS (o módulo já existe do Step 2; se rodar antes do Step 2, FAIL por import).

- [ ] **Step 5: Router com guarda**

```ts
// frontend/src/router/index.ts
import { createRouter, createWebHistory } from 'vue-router';
import HomeView from '../views/HomeView.vue';
import { fetchSession } from '../lib/session';

const router = createRouter({
  history: createWebHistory(),
  routes: [{ path: '/', name: 'home', component: HomeView, meta: { protected: true } }],
});

router.beforeEach(async (to) => {
  if (!to.meta.protected) return true;
  const session = await fetchSession();
  if (session) return true;
  window.location.href = '/api/login'; // dispara o redirect OIDC (browser navega)
  return false;
});

export default router;
```

- [ ] **Step 6: HomeView + App + main**

```vue
<!-- frontend/src/views/HomeView.vue -->
<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { fetchSession } from '../lib/session';
const username = ref<string>('');
onMounted(async () => { username.value = (await fetchSession())?.username ?? ''; });
</script>
<template>
  <main style="font-family: system-ui; padding: 2rem">
    <h1>BlackICE</h1>
    <p v-if="username">Autenticado como <strong>{{ username }}</strong>.</p>
  </main>
</template>
```

```vue
<!-- frontend/src/App.vue -->
<template><RouterView /></template>
```

```ts
// frontend/src/main.ts
import { createApp } from 'vue';
import App from './App.vue';
import router from './router';
createApp(App).use(router).mount('#app');
```

- [ ] **Step 7: Rodar os testes do front**

Run: `cd frontend && npx vitest run`
Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add frontend/
git commit -m "feat(frontend): guarda de rota via /api/me e shell autenticado"
```

---

### Task 6: Traefik mesma-origem + login E2E

**Files:**
- Create: `backend/src/main/docker/Dockerfile.jvm` (se não gerado), rótulos Traefik no compose
- Create: `frontend/Dockerfile` (build estático servido pelo Traefik ou nginx)
- Modify: `infra/docker-compose.yml` (serviços `backend` e `frontend` com labels Traefik)

**Interfaces:**
- Consumes: tudo anterior.
- Produces: um único host `http://blackice.localhost` → `/api/*` no Quarkus, `/` no SPA; login OIDC ponta-a-ponta funcionando.

- [ ] **Step 1: Adicionar `backend` e `frontend` ao compose com labels Traefik**

```yaml
  backend:
    build: ../backend
    environment:
      QUARKUS_OIDC_SECRET: ${QUARKUS_OIDC_SECRET}
      QUARKUS_DATASOURCE_JDBC_URL: jdbc:postgresql://product-db:5432/${PRODUCT_DB}
    labels:
      - "traefik.enable=true"
      - "traefik.http.routers.api.rule=Host(`${APP_HOST}`) && PathPrefix(`/api`)"
      - "traefik.http.routers.api.entrypoints=web"
      - "traefik.http.services.api.loadbalancer.server.port=8080"
    depends_on: [product-db, keycloak]
    networks: [blackice]

  frontend:
    build: ../frontend
    labels:
      - "traefik.enable=true"
      - "traefik.http.routers.spa.rule=Host(`${APP_HOST}`)"
      - "traefik.http.routers.spa.entrypoints=web"
      - "traefik.http.routers.spa.priority=1"       # menor prioridade que /api
      - "traefik.http.services.spa.loadbalancer.server.port=80"
    networks: [blackice]
```

- [ ] **Step 2: `frontend/Dockerfile` (build estático + nginx)**

```dockerfile
FROM node:22-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build
FROM nginx:alpine
COPY --from=build /app/dist /usr/share/nginx/html
# SPA fallback para o Vue Router (history mode)
RUN printf 'server { listen 80; location / { root /usr/share/nginx/html; try_files $uri /index.html; } }' \
  > /etc/nginx/conf.d/default.conf
```

- [ ] **Step 3: Garantir o redirect URI no Keycloak**

Confirme que o client `blackice-quarkus` tem `redirectUris` cobrindo `http://blackice.localhost/api/*` (definido na Task 3, Step 2). Se o host mudou, atualize (lookup do id inline):
Run:
```bash
docker compose -f infra/docker-compose.yml -f infra/dcm4chee/docker-compose.dcm4chee.yml exec keycloak sh -c '
  KC=/opt/keycloak/bin/kcadm.sh
  CID=$($KC get clients -r dcm4chee -q clientId=blackice-quarkus --fields id --format csv --noquotes | tail -n1)
  $KC update clients/$CID -r dcm4chee -s "redirectUris=[\"http://blackice.localhost/api/*\"]"'
```
Expected: sem erro.

- [ ] **Step 4: Subir tudo**

Run:
```bash
cd infra && docker compose -f docker-compose.yml -f dcm4chee/docker-compose.dcm4chee.yml up -d --build backend frontend
```
Expected: `backend` e `frontend` sobem e são healthy.

- [ ] **Step 5: Verificar `/api/me` anônimo pela borda (mesma origem)**

Run: `curl -s -o /dev/null -w "%{http_code}" -H "Host: blackice.localhost" http://localhost/api/me`
Expected: `401` (sem cookie).

- [ ] **Step 6: Verificação E2E manual do login (documentar resultado)**

1. Adicione ao hosts: `127.0.0.1 blackice.localhost` (ou use `--resolve`).
2. Abra `http://blackice.localhost/` no browser.
3. A guarda chama `/api/me` → 401 → navega para `/api/login` → **redireciona ao Keycloak**.
4. Logue como `dr.teste` / `teste123`.
5. Keycloak volta para `/api/*` → Quarkus seta o **cookie de sessão HttpOnly** → redireciona para `/`.
6. `HomeView` chama `/api/me` → 200 → mostra "Autenticado como dr.teste".

Expected: passo 6 exibe o username. **Verifique no DevTools que o cookie de sessão é `HttpOnly` e que NÃO há access token no `localStorage`/`sessionStorage`** (invariante BFF).

- [ ] **Step 7: Commit**

```bash
git add infra/docker-compose.yml frontend/Dockerfile backend/src/main/docker/
git commit -m "infra: Traefik mesma-origem servindo SPA e /api; login OIDC E2E"
```

---

## Self-Review

**1. Spec coverage (contra o design):**
- BFF/cookie web-app → Task 4 ✅
- Mesma origem via Traefik → Task 6 ✅
- Audience compartilhado (recomendado) → Task 3 (audience mapper no realm dcm4chee) ✅
- DCM4CHEE interno + exige auth → Task 2 (Step 7 confirma 401) ✅
- Imagens pinadas → Global Constraints + Task 2 ✅
- `/api/me` como guarda do SPA → Tasks 4 e 5 ✅
- Token nunca no JS → Task 6 Step 6 (verificação explícita) ✅
- CSRF → **fora deste plano de propósito**: só há endpoints mutantes a partir do Plano 2 (ingestão/laudo); a base `SameSite=lax` já entra na Task 4. Registrado, sem gap.

**2. Placeholder scan:** As referências `<ARC_CLIENT>`, `<ROLE>` e "copie o compose oficial" NÃO são placeholders de preguiça — são valores que só existem no ambiente real (clientId/roles do realm do Archive) e cada um tem um passo de descoberta (Task 3 Step 1/5) e verificação. É o tratamento honesto para dependência de artefato upstream versionado.

**3. Type consistency:** `SessionResponse(subject, username, roles)` idêntico no backend (record) e no `session.ts` (interface) e consumido igual na `HomeView`/guard. `fetchSession(): Promise<SessionResponse|null>` consistente entre `session.ts`, teste e router. ✅

**Riscos conhecidos deixados para o gate humano na execução:**
- Nome exato do client do Archive e das realm roles DICOM (Task 3) — é o ponto que "costuma travar o audience".
- Porta/host reais que o compose oficial mapeia para o Keycloak (Task 2 Step 6).
- Ajuste fino do modo web-app do Quarkus para responder 401 em XHR vs redirect em navegação (Task 4) — validar no E2E da Task 6.
