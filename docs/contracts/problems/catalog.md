<!-- DO NOT EDIT. -->
<!-- Gerado por .problem-catalog a partir de docs/contracts/problems/catalog.json. -->
<!-- Altere o catálogo e execute `pnpm generate` em .problem-catalog/. -->

# Catálogo de problemas

Fonte da verdade machine-readable: `docs/contracts/problems/catalog.json`.
Política e workflow: `docs/domains/problem-catalog/`.

Namespace do registry: `5d0f53d8-109e-44c0-b027-bd724c9a13d3`.

## Problemas API

Toda resposta de erro JSON `4xx/5xx` sob `/api` usa um destes tipos, com
media type `application/problem+json`.

| Code | HTTP | Retry | Owner | Title | Detail | Extensão | Status |
| :-- | --: | :-- | :-- | :-- | :-- | :-- | :-- |
| `API_ACCESS_DENIED` | 403 | `NEVER` | security | Access denied | You do not have permission to access this resource. | — | active |
| `API_ARCHIVE_RESPONSE_INVALID` | 502 | `MANUAL` | platform | Invalid Archive response | The imaging archive returned an unexpected response. | — | active |
| `API_ARCHIVE_UNAVAILABLE` | 503 | `MANUAL` | platform | Archive unavailable | The imaging archive is temporarily unavailable. | — | active |
| `API_AUTHENTICATION_REQUIRED` | 401 | `NEVER` | security | Authentication required | Authentication is required to access this resource. | — | active |
| `API_CSRF_INVALID` | 403 | `MANUAL` | security | Request verification failed | The request could not be verified. | — | active |
| `API_DICOM_VALIDATION_FAILED` | 422 | `NEVER` | ingest | DICOM validation failed | None of the uploaded files passed validation. | `dicom-validation-violations` | active |
| `API_INTERNAL_ERROR` | 500 | `MANUAL` | platform | Internal server error | An unexpected error occurred. | — | active |
| `API_MEDIA_TYPE_UNSUPPORTED` | 415 | `NEVER` | platform | Unsupported media type | The request media type is not supported. | — | active |
| `API_METHOD_NOT_ALLOWED` | 405 | `NEVER` | platform | Method not allowed | The requested method is not allowed for this resource. | — | active |
| `API_PAYLOAD_TOO_LARGE` | 413 | `NEVER` | platform | Payload too large | The request exceeds the permitted size. | — | active |
| `API_REPRESENTATION_NOT_ACCEPTABLE` | 406 | `NEVER` | platform | Representation not acceptable | The requested response format is not supported. | — | active |
| `API_REQUEST_INVALID` | 400 | `NEVER` | platform | Invalid request | The request is invalid or malformed. | — | active |
| `API_RESOURCE_NOT_FOUND` | 404 | `NEVER` | platform | Resource not found | The requested resource was not found. | — | active |
| `API_SEARCH_INVALID` | 400 | `NEVER` | worklist | Invalid search | Review the supplied search filters. | — | active |
| `API_SEARCH_TOO_BROAD` | 413 | `NEVER` | worklist | Search too broad | Refine the search filters and try again. | — | active |
| `API_UPLOAD_EMPTY` | 400 | `NEVER` | ingest | Empty upload | Select at least one file to upload. | — | active |

## Problemas CLIENT

Falhas locais do browser. Não são respostas HTTP e por isso não possuem
`httpStatus`, `title` nem `detail`.

| Code | Retry | Owner | Significado | Status |
| :-- | :-- | :-- | :-- | :-- |
| `CLIENT_CSRF_COOKIE_MISSING` | `MANUAL` | frontend | O endpoint respondeu sem criar o cookie CSRF esperado. | active |
| `CLIENT_NETWORK_UNAVAILABLE` | `MANUAL` | frontend | A requisição não alcançou o backend. | active |
| `CLIENT_REQUEST_TIMEOUT` | `MANUAL` | frontend | O browser observou timeout. | active |
| `CLIENT_RESPONSE_INVALID` | `MANUAL` | frontend | A resposta não corresponde ao contrato. | active |
| `CLIENT_UNEXPECTED_ERROR` | `MANUAL` | frontend | Fallback sanitizado para falha local desconhecida. | active |

## Extensões

Membros adicionais ficam no nível raiz do Problem Details, ao lado de
`traceId`.

### `dicom-validation-violations`

Schema: `docs/contracts/problems/extensions/dicom-validation-violations.schema.json`.

Usada por: `API_DICOM_VALIDATION_FAILED`.

Membros adicionais, no nível raiz do Problem Details, de API_DICOM_VALIDATION_FAILED. Não carrega filename: nomes de arquivo podem conter informação identificável. O consumidor associa itemIndex aos arquivos que já mantém localmente.

## Identidades

A URN é UUIDv5 derivado de `blackice.problem.v1:{code}` dentro do namespace
acima. Ela nunca é informada à mão e nunca é reciclada.

| Code | Type |
| :-- | :-- |
| `API_ACCESS_DENIED` | `urn:uuid:8c7666e3-0c02-5a7b-8a7e-d511314c4ccc` |
| `API_ARCHIVE_RESPONSE_INVALID` | `urn:uuid:8a220e49-3e80-5e59-83e5-43483c4a6dd8` |
| `API_ARCHIVE_UNAVAILABLE` | `urn:uuid:8dd49378-697d-5e0e-aa0f-5ab72a5e98a6` |
| `API_AUTHENTICATION_REQUIRED` | `urn:uuid:a665ee9e-36bf-599f-814a-e5d5da82f3cb` |
| `API_CSRF_INVALID` | `urn:uuid:dac0c0fc-38bf-5d19-95f4-45568abb2380` |
| `API_DICOM_VALIDATION_FAILED` | `urn:uuid:0908ac46-fe0e-5516-b4f8-e4f25882ed8e` |
| `API_INTERNAL_ERROR` | `urn:uuid:4685c43c-aff3-5a91-a688-1f05cb9bfe78` |
| `API_MEDIA_TYPE_UNSUPPORTED` | `urn:uuid:c9210550-428c-5b67-9d83-d5af3770134f` |
| `API_METHOD_NOT_ALLOWED` | `urn:uuid:ad905e04-1351-5fab-a371-1d5c50382c6a` |
| `API_PAYLOAD_TOO_LARGE` | `urn:uuid:db695bb8-95d6-56b3-b938-f76b77e2d09b` |
| `API_REPRESENTATION_NOT_ACCEPTABLE` | `urn:uuid:9f6fda99-1849-58a1-85fe-deb9a5897740` |
| `API_REQUEST_INVALID` | `urn:uuid:4ab4a9cc-9774-5c32-b0fa-83594c7bf6e1` |
| `API_RESOURCE_NOT_FOUND` | `urn:uuid:25c41e16-7fd0-51f7-9731-1a9c0c0e8dd4` |
| `API_SEARCH_INVALID` | `urn:uuid:5fdeb44a-6add-5d54-a7f4-5f15f7cdc830` |
| `API_SEARCH_TOO_BROAD` | `urn:uuid:3059d5b4-bb73-52e9-b8ed-a73539b98460` |
| `API_UPLOAD_EMPTY` | `urn:uuid:74e415dc-1966-5124-be1f-8732a25fc777` |
| `CLIENT_CSRF_COOKIE_MISSING` | `urn:uuid:7c5107ac-deab-5dc7-b9b9-f87018515842` |
| `CLIENT_NETWORK_UNAVAILABLE` | `urn:uuid:958f8ed3-f5ea-50a5-af1c-6828e25df077` |
| `CLIENT_REQUEST_TIMEOUT` | `urn:uuid:928412fe-24d7-5395-901f-4b0231d4dc8f` |
| `CLIENT_RESPONSE_INVALID` | `urn:uuid:2c52e3d7-f437-5ff7-a3b0-55b896da2ae5` |
| `CLIENT_UNEXPECTED_ERROR` | `urn:uuid:063da3c0-f056-5e8f-b20d-cb494baf8652` |
