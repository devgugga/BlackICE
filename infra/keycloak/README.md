# Config Keycloak do BlackICE (realm `dcm4chee`)

Configuração aplicada ao **mesmo realm** que o Archive usa (`dcm4chee`) — condição
para o **audience compartilhado** (ver
[design](../../docs/superpowers/specs/2026-07-23-blackice-backend-frontend-design.md)),
que evita token exchange.

## O que `configure-blackice.sh` cria (idempotente)

- **`blackice-quarkus`** — client confidencial (BFF). Standard flow + PKCE (S256),
  `directAccessGrants` OFF, `serviceAccounts` OFF. Redirect `http://${APP_HOST}/api/*`,
  webOrigin `http://${APP_HOST}`. Secret = `QUARKUS_OIDC_SECRET` (de `infra/.env`).
- **`arc-audience`** — audience mapper no client acima, injetando **`dcm4chee-arc-rs`**
  (o client que protege a DICOMweb REST) no claim `aud`.
- **`dr.teste` / `teste123`** — usuário de teste.

O script **não** atribui realm roles (decisão de gate humano — ver abaixo).

### Por que `curl` e não `kcadm`

O cert do Keycloak é self-signed e **sem SAN** para `localhost`; a verificação de
hostname do `kcadm` rejeita. Usamos a **Admin REST via `curl -k`** (ignora
cert/hostname), executada **dentro do container** `keycloak`, obtendo o token admin
das vars `KEYCLOAK_ADMIN`/`KEYCLOAK_ADMIN_PASSWORD` do próprio container (nunca
impressas). Keycloak roda só em **HTTPS na 8843**.

## Roles (gate humano) — decisão registrada

O realm `dcm4chee` tem, de custom, só **`auth`** e **`root`** (o resto é built-in do
Keycloak; `dcm4chee-arc-rs` não expõe client-roles). Decisão: **`dr.teste` recebe a
role `auth`** (usuário autenticado normal, papel realista de radiologista).

Comando de atribuição (reproduzível; dentro do container `keycloak`):

```sh
# obtenha um token admin (adm) e o id do usuário (USERID), então:
ROLE=$(curl -k -s -H "$AH" "$api/roles/auth")
curl -k -s -H "$AH" -H "Content-Type: application/json" \
  -X POST "$api/users/$USERID/role-mappings/realm" -d "[$ROLE]"
```

Se algum fluxo (provavelmente STOW) travar por permissão, adicionar `root`.

## ✅ Validação end-to-end (audience + authz)

Antes de qualquer código Quarkus/Vue, provamos a suposição de auth mais crítica.
Habilitando `directAccessGrants` **temporariamente**, um token de `dr.teste` emitido
pelo `blackice-quarkus`:

- sai com `"aud":"dcm4chee-arc-rs"` e `"preferred_username":"dr.teste"`;
- ao chamar `GET http://arc:8080/dcm4chee-arc/aets/DCM4CHEE/rs/studies` (rede interna)
  retorna **`204`** (autenticado + autorizado; sem estudos ainda), **não `401`**.

Isso confirma: **audience compartilhado funciona** e a role **`auth` autoriza
DICOMweb**. `directAccessGrants` foi **revertido para `false`** após o teste — o BFF
não deve permitir password grant no fluxo real.

## Como aplicar

```sh
bash infra/keycloak/configure-blackice.sh   # cria client + mapper + usuário
# depois, atribua a role auth ao dr.teste (comando acima)
```

Pré-requisito: a stack do Keycloak/Archive de pé (ver `infra/dcm4chee/`).
