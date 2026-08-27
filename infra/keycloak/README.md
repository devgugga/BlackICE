# Keycloak Configuration for BlackICE (`blackice` realm)

Configuration applied to the **same realm** used by the Archive. This single-realm architecture sustains the **shared audience** model (see [design specs](../../docs/superpowers/specs/2026-07-23-blackice-backend-frontend-design.md)). A token issued by another realm would fail validation against DCM4CHEE Archive, and falling back to a service account would eliminate authentic per-user audit trails in DICOM records.

## Resources Created by `configure-blackice.sh` (Idempotent)

- **`blackice-quarkus`**: Confidential client (BFF). Standard Authorization Code flow + PKCE (S256), `directAccessGrants` OFF, `serviceAccounts` OFF. Redirect URI `http://${APP_HOST}/api/*`, webOrigin `http://${APP_HOST}`. Secret = `QUARKUS_OIDC_SECRET` (from `infra/.env`).
- **`arc-audience`**: Audience mapper on the client above, injecting **`dcm4chee-arc-rs`** (the client protecting the DICOMweb REST API) into the `aud` token claim.
- **`dr.teste` / `teste123`**: Primary clinical test user (author / radiologist).
- **`dr.leitor` / `teste123`**: Secondary clinical test user (reader / second actor), created to validate multi-actor isolation in E2E suites, read-only guards on third-party reports, and 403 Forbidden enforcement on unauthorized mutations.

The script idempotently assigns the realm role **`auth`** to both `dr.teste` and `dr.leitor`.

### Why `curl` Instead of `kcadm`

Keycloak uses a self-signed certificate without Subject Alternative Names (SAN) for `localhost`, which causes `kcadm` hostname verification to fail. We interact with the **Admin REST API via `curl -k`** executed **inside the `keycloak` container**, obtaining the admin token directly from container environment variables. Keycloak serves HTTP on port `8080` internally (behind Traefik at `http://${APP_HOST}/auth`) and HTTPS on port `8843` (Archive backchannel). The root path is `/auth`.

## Role Strategy

The `blackice` realm features custom roles **`auth`** and **`root`**. Decision: **`dr.teste` and `dr.leitor` receive the `auth` role** (standard authenticated users representing radiologists). Neither test persona receives elevated root privileges.

## End-to-End Auth Validation

A token for `dr.teste` issued through `blackice-quarkus`:
- Carries `"aud":"dcm4chee-arc-rs"` and `"preferred_username":"dr.teste"`;
- When calling `GET http://arc:8080/dcm4chee-arc/aets/DCM4CHEE/rs/studies`, returns **`204 No Content`** (authenticated and authorized), not `401 Unauthorized`.

This validates that the **shared audience pattern works** and that the **`auth` role successfully authorizes DICOMweb requests**. `directAccessGrants` remains strictly disabled in production flow.

## `blackice` Login Theme

Located at `infra/keycloak/themes/blackice/login/`, mounted read-only into `/opt/keycloak/themes/blackice`. Child theme of `keycloak.v2` (PatternFly v5), without copying FreeMarker templates:

- **Localization**: UI labels and title strings live in `messages/messages_en.properties`;
- **Styling**: `resources/css/blackice.css` defines the product design tokens (`--bi-*`).

## Applying Configuration

Via Bash:

```sh
bash infra/keycloak/configure-blackice.sh
```

Via PowerShell:

```powershell
pwsh -File infra/keycloak/configure-blackice.ps1
```
