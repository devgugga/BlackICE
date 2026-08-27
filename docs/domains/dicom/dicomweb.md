# DICOMweb: STOW-RS / QIDO-RS / WADO-RS

Standard DICOM RESTful web services. DCM4CHEE Archive exposes all three. **Each service has a distinct purpose; do not interchange them.** Mnemonic: **S**TOW = Store, **Q**IDO = Query, **WADO** = Retrieve.

## QIDO-RS: Query (Search)

`GET` requests that **search** and return **JSON metadata** (`application/dicom+json`). Never returns pixel data. Drives the **clinical worklist and study search**.

- `GET /studies?PatientID=...&StudyDate=...&ModalitiesInStudy=CT&limit=20&offset=0`
- `GET /studies/{StudyInstanceUID}/series`
- `GET /studies/{StudyInstanceUID}/series/{SeriesInstanceUID}/instances`
- Filter using DICOM attributes as query parameters; paginate with `limit` / `offset`.
- Request extra tags with `includefield`. Response is an array of JSON objects where each tag is formatted as `"0020000D": {"vr":"UI","Value":["1.2.3..."]}`.

**Use for:** Listing and filtering studies; assembling the Study -> Series -> Instance hierarchy before launching the viewer. **Do not use** to fetch raw image data.

## WADO-RS: Retrieve (Fetch Data)

`GET` requests that **deliver actual DICOM payloads**, including **raw pixel data** (`multipart/related`) or rendered images.

- Full DICOM Instance: `GET /studies/{u}/series/{u}/instances/{u}`
- Pixel Frames: `GET /studies/{u}/series/{u}/instances/{u}/frames/{n}`
- Bulk Metadata: `.../metadata`
- Server-rendered image (basic web display): `.../rendered` (JPEG/PNG)
- Bulk data references: `/bulkdata` endpoint.

**Use for:** Feeding medical viewports (Cornerstone3D consumes `imageId` strings formatted like `wadors:.../frames/1`). **Do not use** for searching or filtering (use QIDO-RS).

## STOW-RS: Store (Ingest)

`POST` requests that **transmit DICOM objects** to the PACS Archive.

- `POST /studies` with a `multipart/related; type="application/dicom"` body, where each part is a DICOM file (`.dcm`). Optional: `POST /studies/{StudyInstanceUID}` to enforce ingestion into a targeted study.
- Response is a reference dataset enumerating accepted and rejected SOP instances (`ReferencedSOPSequence` / `FailedSOPSequence`). **Always inspect for failures**: HTTP `200` does not guarantee every instance was accepted.

**Use for:** Manual file ingestion (drag-and-drop `.dcm` -> Quarkus BFF -> STOW-RS).

## Authentication (Keycloak / OIDC)

- Keycloak-secured DCM4CHEE endpoints require a **Bearer token**.
- In BlackICE, the browser communicates exclusively with Quarkus using a secure `HttpOnly` same-origin session cookie; access tokens are never exposed to client-side JavaScript.
- Quarkus extracts the user's access token server-side and forwards it when executing DICOMweb calls. Do not use static service credentials for actions representing human operators, as this invalidates the DICOM audit trail.
- The browser never interacts with DCM4CHEE directly: all DICOMweb calls flow through the Quarkus BFF.

## Common Mistakes (What Reviewers Flag)

- [ ] Using WADO where QIDO was required (fetching pixels during queries) or vice versa.
- [ ] Ignoring `FailedSOPSequence` in STOW responses (false impression of total success).
- [ ] Constructing Cornerstone `imageId` from QIDO endpoints instead of WADO-RS frame URLs.
- [ ] Failing to propagate the authenticated user's OIDC token in downstream DICOMweb calls.
- [ ] Performing client-side pagination (fetching all records and slicing) instead of server-side `limit`/`offset`.
