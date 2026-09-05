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

### Local Host / Windows PowerShell
```powershell
cd infra
docker compose -f compose.yml -f dcm4chee/compose.yml -f compose.apps.yml up -d --build
```

### Agent Sandbox (Nested Podman Runtime)
Inside an isolated agent sandbox (`mode = "nested"` in `.agent-sandbox.toml`), Podman runs rootless. The sandbox maps port 80 to 18080 on the host loopback:

```bash
cd infra
podman compose -f compose.yml -f compose.apps.yml up -d --build
# Or include the DICOM archive stack:
# podman compose -f compose.yml -f dcm4chee/compose.yml -f compose.apps.yml up -d --build
```

### Service Access & Published Ports
When running under `agent-sandbox`:
- **Traefik Ingress**: `http://localhost:18080` (mapped to internal container port 80). Routes to frontend Vue SPA and backend Quarkus BFF.
- **Traefik Dashboard**: `http://localhost:8081` (unauthenticated monitoring).
- **Keycloak Auth**: `https://localhost:8843/auth` (within compose stack / routed via Traefik).

## Keycloak Provisioning & Personas

After launching the stack, run the idempotent configuration script to provision the `blackice-quarkus` client, the `arc-audience` mapper, and test personas with the `auth` realm role:

```bash
bash infra/keycloak/configure-blackice.sh
```

### Test Personas
- **`dr.teste`**: Password `teste123` — Diagnostic radiologist persona with `auth` role.
- **`dr.leitor`**: Password `teste123` — Second-opinion reviewer persona with `auth` role.
- **Client**: `blackice-quarkus` configured with confidential access, Authorization Code flow with PKCE (S256), and dedicated `blackice` login theme.

## Validating Compose Configuration

```bash
cd infra
podman compose -f compose.yml -f dcm4chee/compose.yml -f compose.apps.yml config --quiet
```
