# Infraestrutura BlackICE

Os três arquivos Compose são usados juntos:

- `compose.yml`: fundação compartilhada (`traefik`, `docker-proxy` e `product-db`).
- `dcm4chee/compose.yml`: archive seguro e dependências (`ldap`, `mariadb`, `keycloak`, `arc-db` e `arc`).
- `compose.apps.yml`: aplicações BlackICE (`backend` Quarkus e `frontend` SPA Vue).

Crie `infra/.env` localmente a partir de `.env.example`. O arquivo
`.env.example` é apenas o modelo; nunca versione `.env`.

## Banco de dados de produto (`product-db`)

O serviço `product-db` (PostgreSQL 17) é dedicado exclusivamente aos dados de negócio
do produto BlackICE (laudos clínicos), separado do banco do Archive (`arc-db`) e do Keycloak (`mariadb`):

- **Variáveis**: `PRODUCT_DB`, `PRODUCT_DB_USER`, `PRODUCT_DB_PASSWORD` (de `infra/.env`);
- **Healthcheck**: `pg_isready -U ${PRODUCT_DB_USER} -d ${PRODUCT_DB}` (intervalo 5s, timeout 3s, 10 retentativas);
- **Prontidão e migrações**: O container `backend` aguarda a condição `service_healthy` do `product-db` e executa automaticamente as migrações Flyway (`V1__create_reports.sql`) no startup antes de expor os endpoints.

## Subir a stack

```powershell
cd infra
docker compose -f compose.yml -f dcm4chee/compose.yml -f compose.apps.yml up -d --build
```

## Configurar Keycloak

Após subir a stack, execute o configurador idempotente para provisionar o client `blackice-quarkus`, o mapper `arc-audience` e os usuários de teste `dr.teste` e `dr.leitor` com a role `auth`:

```bash
bash infra/keycloak/configure-blackice.sh
```

## Validar a configuração

```powershell
cd infra
docker compose -f compose.yml -f dcm4chee/compose.yml -f compose.apps.yml config --quiet
```
