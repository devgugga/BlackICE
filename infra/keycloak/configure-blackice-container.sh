#!/bin/sh
# Executado dentro do container keycloak; recebe as variáveis do launcher host.
set -eu

KB=https://localhost:8843/auth
api="$KB/admin/realms/$REALM"

trap 'rm -f /tmp/client.json' EXIT

TOK=$(curl -f -k -s -X POST "$KB/realms/master/protocol/openid-connect/token" \
  -d grant_type=password -d client_id=admin-cli \
  -d "username=$KEYCLOAK_ADMIN" -d "password=$KEYCLOAK_ADMIN_PASSWORD" \
  | grep -o '"access_token":"[^"]*"' | sed 's/.*:"//;s/"//')
[ -n "$TOK" ] || { echo "TOKEN FAIL"; exit 1; }
AH="Authorization: Bearer $TOK"

# 1) client confidencial blackice-quarkus (standard flow + PKCE)
CLIENTS=$(curl -f -k -s -H "$AH" "$api/clients?clientId=blackice-quarkus")
if [ -z "$(printf '%s' "$CLIENTS" | grep -o '"clientId":"blackice-quarkus"' || true)" ]; then
  curl -f -k -s -H "$AH" -H "Content-Type: application/json" -X POST "$api/clients" -d '{
    "clientId":"blackice-quarkus","enabled":true,"publicClient":false,
    "standardFlowEnabled":true,"directAccessGrantsEnabled":false,"serviceAccountsEnabled":false,
    "secret":"'"$QUARKUS_OIDC_SECRET"'",
    "redirectUris":["'"$APP_ORIGIN"'/api/*"],"webOrigins":["'"$APP_ORIGIN"'"],
    "attributes":{"pkce.code.challenge.method":"S256"}
  }' >/dev/null
  echo "client blackice-quarkus: criado"
else echo "client blackice-quarkus: já existe"; fi

CID=$(printf '%s' "$CLIENTS" | grep -o '"id":"[^"]*"' | head -1 | sed 's/.*:"//;s/"//')
if [ -z "$CID" ]; then
  CLIENTS=$(curl -f -k -s -H "$AH" "$api/clients?clientId=blackice-quarkus")
  CID=$(printf '%s' "$CLIENTS" | grep -o '"id":"[^"]*"' | head -1 | sed 's/.*:"//;s/"//')
fi
[ -n "$CID" ] || { echo "CLIENT FAIL"; exit 1; }

# 2) audience mapper -> dcm4chee-arc-rs (audience compartilhado; evita token exchange)
MAPPERS=$(curl -f -k -s -H "$AH" "$api/clients/$CID/protocol-mappers/models")
if [ -z "$(printf '%s' "$MAPPERS" | grep -o '"name":"arc-audience"' || true)" ]; then
  curl -f -k -s -H "$AH" -H "Content-Type: application/json" -X POST "$api/clients/$CID/protocol-mappers/models" -d '{
    "name":"arc-audience","protocol":"openid-connect","protocolMapper":"oidc-audience-mapper",
    "config":{"included.client.audience":"'"$ARC_CLIENT"'","access.token.claim":"true","id.token.claim":"false"}
  }' >/dev/null
  echo "audience mapper arc-audience -> $ARC_CLIENT: criado"
else echo "audience mapper arc-audience: já existe"; fi

# 3) usuário de teste dr.teste
USERS=$(curl -f -k -s -H "$AH" "$api/users?username=dr.teste&exact=true")
if [ -z "$(printf '%s' "$USERS" | grep -o '"username":"dr.teste"' || true)" ]; then
  curl -f -k -s -H "$AH" -H "Content-Type: application/json" -X POST "$api/users" -d '{
    "username":"dr.teste","enabled":true,"firstName":"Teste","lastName":"Radiologista",
    "credentials":[{"type":"password","value":"teste123","temporary":false}]
  }' >/dev/null
  echo "usuário dr.teste: criado"
else echo "usuário dr.teste: já existe"; fi

# 4) tema de login SÓ neste client (o realm segue em j4care, para o Archive).
CUR=$(curl -f -k -s -H "$AH" "$api/clients/$CID" | python3 -c 'import sys,json;print(json.load(sys.stdin).get("attributes",{}).get("login_theme",""))')
if [ "$CUR" != "blackice" ]; then
  curl -f -k -s -H "$AH" "$api/clients/$CID" > /tmp/client.json
  python3 -c '
import json
c = json.load(open("/tmp/client.json"))
c.setdefault("attributes", {})["login_theme"] = "blackice"
json.dump(c, open("/tmp/client.json", "w"))
'
  curl -f -k -s -H "$AH" -H "Content-Type: application/json" -X PUT "$api/clients/$CID" -d @/tmp/client.json >/dev/null
  echo "login_theme=blackice no client blackice-quarkus: aplicado"
else echo "login_theme=blackice: já aplicado"; fi

echo "OK: config base pronta. Roles NÃO atribuídas (gate humano — ver README)."
