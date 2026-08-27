# DCM4CHEE-secure Stack

Compose setup adapted from the official guide ("Run secured archive services on a single host"):
https://github.com/dcm4che/dcm4chee-arc-light/wiki/Running-on-Docker
(Specific page: `Run-secured-archive-services-on-a-single-host.md`, obtained by cloning `https://github.com/dcm4che/dcm4chee-arc-light.wiki.git`).

The published YAML at HEAD of the upstream wiki (commit `cbdb3671`, "upgrade to 5.34.3") documents **Keycloak 26.0.6**, which uses different admin bootstrap environment variables (`KC_BOOTSTRAP_ADMIN_USERNAME`/`PASSWORD`) and is incompatible with tag 25.0.6 mandated by this project's Global Constraints. To avoid mixing incompatible variables across Keycloak generations, this Compose file was derived from commit `6d8b8afe` ("Fixed errors in the keycloak container Docker command line") documenting **Keycloak 25.0.5**, using `KEYCLOAK_ADMIN`/`KEYCLOAK_ADMIN_PASSWORD`, with image tags pinned as listed below (25.0.5 -> 25.0.6 is a drop-in patch release within the same variable scheme, confirmed in the official image README: https://github.com/dcm4che-dockerfiles/keycloak-quarkus).

## Pinned Image Tags (Global Constraints)

| Service | Image | Tag |
| :-- | :-- | :-- |
| `ldap` | `dcm4che/slapd-dcm4chee` | `2.6.10-34.3` |
| `mariadb` | `mariadb` | `10.11.4` |
| `keycloak` | `dcm4che/keycloak` | `25.0.6` |
| `arc-db` | `dcm4che/postgres-dcm4chee` | `17.4-34` |
| `arc` | `dcm4che/dcm4chee-arc-psql` | `5.34.3-secure` |

Complies with `docs/architecture/dcm4chee-archive.md` (baseline 5.34.3). No tag uses `latest`.

Attached to the `blackice` Docker network, defined in the base manifest `infra/compose.yml` (always included via `-f` when invoking Compose).

## Documented Deviations from Official Compose

1. **`mariadb` service added**: Upstream secure compose relies on MariaDB for Keycloak storage across the 5.3x series; added as mandatory service.
2. **`db` -> `arc-db`**: Renamed for consistency with BlackICE topology naming. Set `POSTGRES_HOST=arc-db` on the `arc` service.
3. **`REALM_NAME=blackice`**: Set on both `keycloak` and `arc` services. On empty databases, this variable initializes the realm directly with the `blackice` name.
4. **Named Docker volumes**: Replaced Linux host bind mounts (`/var/local/...`) with named volumes (`dcm4chee-ldap-data`, `dcm4chee-mysql-data`, `dcm4chee-keycloak-data`, `dcm4chee-db-data`, `dcm4chee-wildfly-data`, `dcm4chee-storage`, `dcm4chee-slapd-conf`).
5. **Parameterized credentials via `infra/.env`**: All secrets (`DCM4CHEE_HOST`, `KEYCLOAK_ADMIN_PASSWORD`, `DCM4CHEE_DB*`, `KEYCLOAK_DB*`) are parameterized through environment variables.
6. **No host port collisions**: Traefik uses `80` and `8081`. DCM4CHEE ports (`389`, `636`, `3306`, `8843`, `5432`, `8080`, `8443`, `9990`, `9993`, `11112`, `2762`, `2575`, `12575`) do not collide.

## Ports & Network Access

- **Keycloak Browser Route**: Served **same-origin** by Traefik at `http://blackice.localhost/auth`. No separate port or SSL certificate warning.
- **Keycloak Direct HTTPS Listener**: `https://localhost:8843/auth` (Archive backchannel; self-signed certificate, use `curl -k`). `KC_HTTP_RELATIVE_PATH=/auth` serves as the global root path.
- **Realm**: `blackice`. Announced issuer: `http://blackice.localhost/auth/realms/blackice`.
- **Keycloak Admin Console**: `https://localhost:8843/auth/admin` (username `admin`, password from `KEYCLOAK_ADMIN_PASSWORD` in `.env`).
- **Archive (WildFly)**: **No published host port** (DCM4CHEE is never directly exposed to the public browser; only Quarkus BFF talks DICOMweb to it over the internal network at `http://arc:8080/dcm4chee-arc`).
- **Default AE Title**: `DCM4CHEE`. Pre-configured realm users: `root`/`changeit`, `admin`/`changeit`, `user`/`changeit`.

## Launching the Stack

```bash
docker compose -f infra/compose.yml -f infra/dcm4chee/compose.yml -f infra/compose.apps.yml up -d
```

Dependency order (`depends_on`): `ldap`/`mariadb` -> `keycloak` -> `arc-db` -> `arc`. The Archive (WildFly) takes approximately 1-2 minutes to initialize after container boot.

## Verification

```bash
curl -sk https://localhost:8843/auth/realms/blackice/.well-known/openid-configuration | head -c 300
```
Expected: JSON containing `"issuer":"http://blackice.localhost/auth/realms/blackice"`.

Browser path via Traefik:

```bash
curl -s -o /dev/null -w "%{http_code}\n" \
  http://blackice.localhost/auth/realms/blackice/.well-known/openid-configuration
```
Expected: `200`.

Internal DICOMweb query test:

```bash
docker compose -f infra/compose.yml -f infra/dcm4chee/compose.yml -f infra/compose.apps.yml \
  exec arc curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/dcm4chee-arc/aets/DCM4CHEE/rs/studies
```
Expected: `401` (QIDO requires Bearer token, confirming that DICOMweb is up and protected).
