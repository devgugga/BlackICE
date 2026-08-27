# BlackICE Infrastructure

The three Compose manifests are composed together:

- `compose.yml`: Shared foundation (`traefik`, `docker-proxy`, and `product-db`).
- `dcm4chee/compose.yml`: Secured DICOM Archive stack and dependencies (`ldap`, `mariadb`, `keycloak`, `arc-db`, and `arc`).
- `compose.apps.yml`: BlackICE product applications (`backend` Quarkus BFF and `frontend` Vue SPA).

Create `infra/.env` locally from `.env.example`. The `.env.example` file is an example template; never commit `.env` or plaintext secrets.

## Product Database (`product-db`)

The `product-db` service (PostgreSQL 17) is strictly dedicated to BlackICE business domain data (clinical reports), segregated from the Archive database (`arc-db`) and Keycloak database (`mariadb`):

- **Environment Variables**: `PRODUCT_DB`, `PRODUCT_DB_USER`, `PRODUCT_DB_PASSWORD` (configured in `infra/.env`);
- **Healthcheck**: `pg_isready -U ${PRODUCT_DB_USER} -d ${PRODUCT_DB}` (interval 5s, timeout 3s, 10 retries);
- **Readiness & Migrations**: The `backend` container awaits `service_healthy` from `product-db` and automatically runs Flyway migrations (`V1__create_reports.sql`) on startup before exposing REST endpoints.

## Starting the Stack

```powershell
cd infra
docker compose -f compose.yml -f dcm4chee/compose.yml -f compose.apps.yml up -d --build
```

## Keycloak Provisioning

After launching the stack, run the idempotent configuration script to provision the `blackice-quarkus` client, the `arc-audience` mapper, and the test personas `dr.teste` and `dr.leitor` with the `auth` realm role:

```bash
bash infra/keycloak/configure-blackice.sh
```

## Validating Compose Configuration

```powershell
cd infra
docker compose -f compose.yml -f dcm4chee/compose.yml -f compose.apps.yml config --quiet
```
