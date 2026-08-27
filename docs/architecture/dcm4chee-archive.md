# DCM4CHEE Archive: Architectural Baseline

## Pinned Release

BlackICE pins **DCM4CHEE Archive 5.34.3** as its core integration baseline.

For secure containerized deployment exposing DICOMweb REST services, the Archive image is pinned to:

```text
dcm4che/dcm4chee-arc-psql:5.34.3-secure
```

Never use mutable `latest` tags in reproducible environments.

## Component Topology

The 5.34.3 release topology includes:

- `dcm4che/slapd-dcm4chee:2.6.10-34.3`: LDAP schema and configuration store;
- `dcm4che/postgres-dcm4chee:17.4-34`: PostgreSQL database dedicated exclusively to DCM4CHEE metadata (strictly isolated from the Quarkus product database);
- `dcm4che/keycloak:25.0.6`: Keycloak OIDC identity server;
- `mariadb:10.11.4`: Database backing Keycloak;
- WildFly 39.0.1.Final internal application server runtime.

## DICOMweb Boundary

BlackICE treats DCM4CHEE strictly as an upstream DICOM engine accessed via DICOMweb (STOW-RS for ingestion, QIDO-RS for queries, and WADO-RS for frame retrieval). Detailed DICOM invariants and semantics live in `docs/domains/dicom/`.
