#!/usr/bin/env bash
# Configura o realm `blackice` do Keycloak para o BFF do BlackICE.
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

docker compose -f "$ROOT/infra/compose.yml" -f "$ROOT/infra/dcm4chee/compose.yml" -f "$ROOT/infra/compose.apps.yml" \
  exec -T -e "QUARKUS_OIDC_SECRET=$QUARKUS_OIDC_SECRET" -e "APP_ORIGIN=http://${APP_HOST}" \
  -e "ARC_CLIENT=dcm4chee-arc-rs" -e "REALM=blackice" keycloak sh -s \
  < "$ROOT/infra/keycloak/configure-blackice-container.sh"
