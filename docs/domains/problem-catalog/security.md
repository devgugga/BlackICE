# Security: Error Text, Extensions, and Logs

BlackICE processes Protected Health Information (PHI). Error payloads and logs are potential data leakage vectors that propagate through screenshots, support tickets, and telemetry.

## Never Allowed in Responses, Extensions, or Logs

- Request body payloads;
- Clinical query parameters or search filters;
- Tokens, session cookies, authorization headers, or secrets;
- Original uploaded filenames (which frequently embed patient names or dates);
- Clinical identifiers (`PatientID`, `PatientName`, `StudyInstanceUID`, `SeriesInstanceUID`, `SOPInstanceUID`);
- DICOMweb raw datasets or payload snippets;
- Internal infrastructure URLs, hostnames, or private ports;
- `Exception.getMessage()`, root causes, stack traces, or Java class names.

## Public Error Text

`title` and `detail` come directly from the catalog, are written in English, and remain constant. They describe the **class** of problem rather than transient runtime state.

Public error strings never interpolate raw user inputs. If dynamic contextual data is strictly required, it belongs in a validated extension schema.

## Extension Schemas

An extension schema exists only when the API client must **take automated action** based on specific metadata.

Example: `dicom-validation-violations` carries `itemIndex`, `code`, and `message`, but **never** `filename`. The client matches `itemIndex` against its local in-memory upload queue, ensuring PHI never traverses the public wire in error responses.

## Traceability (`TraceID`)

The `TraceID` safely bridges client-side issue reporting and server-side observability.

- Every `/api` HTTP response includes `X-Trace-ID`;
- Problem Details JSON payloads mirror this value in the `traceId` extension;
- Standard W3C `traceparent` headers drive trace propagation;
- The UI exposes `traceId` on error notifications for diagnostic reference.

## Server-Side Logging

Log **once**, at the HTTP perimeter:

- Expected client errors (`4xx`): `INFO` or `WARN`, omitting stack traces.
- Known upstream timeouts / unavailable services: `WARN` with a sanitized message.
- Unexpected system exceptions (`500`): A single `ERROR` with sanitized metadata and stack trace.

Always log route templates (e.g. `/api/studies`) rather than raw request URLs with query strings (to prevent PHI leakage in search parameters).
