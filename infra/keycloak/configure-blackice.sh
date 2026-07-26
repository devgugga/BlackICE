#!/usr/bin/env bash
# Configura o realm `dcm4chee` do Keycloak para o BFF do BlackICE.
#
# Roda no HOST; carrega infra/.env (QUARKUS_OIDC_SECRET, APP_HOST) e executa a
# Admin REST via `curl -k` DENTRO do container keycloak.
#
# Por que curl e não kcadm: o cert do Keycloak é self-signed e SEM SAN para
# `localhost`, então a verificação de hostname do kcadm falha. `curl -k` ignora
# cert/hostname. O token admin é obtido de dentro do container usando as vars
# KEYCLOAK_ADMIN / KEYCLOAK_ADMIN_PASSWORD do próprio container (nunca impressas).
#
# Idempotente: cria só o que ainda não existe.
# NÃO atribui realm roles ao usuário — isso é decisão do gate humano (ver README).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
ENV_FILE="$ROOT/infra/.env"
[ -f "$ENV_FILE" ] || { echo "faltando $ENV_FILE"; exit 1; }
set -a; . "$ENV_FILE"; set +a
: "${QUARKUS_OIDC_SECRET:?defina QUARKUS_OIDC_SECRET em infra/.env}"
: "${APP_HOST:?defina APP_HOST em infra/.env}"

DC="docker compose -f $ROOT/infra/compose.yml -f $ROOT/infra/dcm4chee/compose.yml -f $ROOT/infra/compose.apps.yml"

$DC exec -T \
  -e QUARKUS_OIDC_SECRET="$QUARKUS_OIDC_SECRET" \
  -e APP_ORIGIN="http://${APP_HOST}" \
  -e ARC_CLIENT="dcm4chee-arc-rs" \
  -e REALM="dcm4chee" \
  keycloak sh <<'INNER'
set -eu
KB=https://localhost:8843
api="$KB/admin/realms/$REALM"

TOK=$(curl -k -s -X POST "$KB/realms/master/protocol/openid-connect/token" \
  -d grant_type=password -d client_id=admin-cli \
  -d "username=$KEYCLOAK_ADMIN" -d "password=$KEYCLOAK_ADMIN_PASSWORD" \
  | grep -o '"access_token":"[^"]*"' | sed 's/.*:"//;s/"//')
[ -n "$TOK" ] || { echo "TOKEN FAIL"; exit 1; }
AH="Authorization: Bearer $TOK"

# 1) client confidencial blackice-quarkus (standard flow + PKCE)
if [ -z "$(curl -k -s -H "$AH" "$api/clients?clientId=blackice-quarkus" | grep -o '"clientId":"blackice-quarkus"' || true)" ]; then
  curl -k -s -H "$AH" -H "Content-Type: application/json" -X POST "$api/clients" -d '{
    "clientId":"blackice-quarkus","enabled":true,"publicClient":false,
    "standardFlowEnabled":true,"directAccessGrantsEnabled":false,"serviceAccountsEnabled":false,
    "secret":"'"$QUARKUS_OIDC_SECRET"'",
    "redirectUris":["'"$APP_ORIGIN"'/api/*"],"webOrigins":["'"$APP_ORIGIN"'"],
    "attributes":{"pkce.code.challenge.method":"S256"}
  }' >/dev/null
  echo "client blackice-quarkus: criado"
else echo "client blackice-quarkus: já existe"; fi

CID=$(curl -k -s -H "$AH" "$api/clients?clientId=blackice-quarkus" | grep -o '"id":"[^"]*"' | head -1 | sed 's/.*:"//;s/"//')

# 2) audience mapper -> dcm4chee-arc-rs (audience compartilhado; evita token exchange)
if [ -z "$(curl -k -s -H "$AH" "$api/clients/$CID/protocol-mappers/models" | grep -o '"name":"arc-audience"' || true)" ]; then
  curl -k -s -H "$AH" -H "Content-Type: application/json" -X POST "$api/clients/$CID/protocol-mappers/models" -d '{
    "name":"arc-audience","protocol":"openid-connect","protocolMapper":"oidc-audience-mapper",
    "config":{"included.client.audience":"'"$ARC_CLIENT"'","access.token.claim":"true","id.token.claim":"false"}
  }' >/dev/null
  echo "audience mapper arc-audience -> $ARC_CLIENT: criado"
else echo "audience mapper arc-audience: já existe"; fi

# 3) usuário de teste dr.teste
if [ -z "$(curl -k -s -H "$AH" "$api/users?username=dr.teste&exact=true" | grep -o '"username":"dr.teste"' || true)" ]; then
  curl -k -s -H "$AH" -H "Content-Type: application/json" -X POST "$api/users" -d '{
    "username":"dr.teste","enabled":true,"firstName":"Teste","lastName":"Radiologista",
    "credentials":[{"type":"password","value":"teste123","temporary":false}]
  }' >/dev/null
  echo "usuário dr.teste: criado"
else echo "usuário dr.teste: já existe"; fi

# 4) tema de login SÓ neste client (o realm segue em j4care, para o Archive).
#    Não há endpoint de patch parcial: é preciso GET da representação inteira,
#    mexer no atributo e PUT de volta. jq não existe nesta imagem; python3 sim.
#    Para REMOVER o atributo é preciso mandar "" — omitir a chave devolve 204
#    e não apaga nada.
CUR=$(curl -k -s -H "$AH" "$api/clients/$CID" | python3 -c 'import sys,json;print(json.load(sys.stdin).get("attributes",{}).get("login_theme",""))')
if [ "$CUR" != "blackice" ]; then
  curl -k -s -H "$AH" "$api/clients/$CID" > /tmp/client.json
  python3 -c '
import json
c = json.load(open("/tmp/client.json"))
c.setdefault("attributes", {})["login_theme"] = "blackice"
json.dump(c, open("/tmp/client.json", "w"))
'
  curl -k -s -H "$AH" -H "Content-Type: application/json" -X PUT "$api/clients/$CID" -d @/tmp/client.json >/dev/null
  rm -f /tmp/client.json
  echo "login_theme=blackice no client blackice-quarkus: aplicado"
else echo "login_theme=blackice: já aplicado"; fi

echo "OK: config base pronta. Roles NÃO atribuídas (gate humano — ver README)."
INNER
