# Classification and Reuse

Before touching the problem catalog registry, classify the failure condition. Most runtime events are not new problem types, and many are not errors at all.

## Decision Tree

```text
Observable HTTP 4xx/5xx response? -> API
Local transport / network / parser failure in browser? -> CLIENT
Operation completed (fully or partially)? -> Result
User intentionally aborted? -> Cancellation
```

Evaluate sequentially and halt at the first matching branch.

### API Problems

The backend answered (or will answer) with a `4xx` or `5xx` status under `/api`. This is the only category transformed into RFC 9457 `application/problem+json`. Every `API_*` entry mandates `httpStatus`, `title`, and `detail`.

### CLIENT Problems

The failure occurred entirely inside the browser without a valid HTTP response (network timeout, connection refusal, unparseable payload). `CLIENT_*` entries **do not** have `httpStatus`, `title`, or `detail`, because they are not HTTP responses.

### Operational Results

The operation executed and produced a structured outcome, including partial success. A batch ingestion where some files succeed and others fail returns `200 OK` with a detailed result model, not an HTTP problem.

### User Cancellation

The operator intentionally aborted an in-flight operation. This is standard control flow (`CANCELLED`), requiring no Problem Details, no `ApiError`, and no error logging.

## Internal Motives vs. Public Catalog

Internal reason enums (`TIMEOUT`, `CONNECTION`, `HTTP_STATUS`, `INVALID_RESPONSE`) belong to internal module exceptions. Multiple internal reasons frequently map to the same public problem type, which is expected. The catalog captures what consumers observe, not internal Java exception taxonomies.

Domain and application layers remain HTTP-agnostic. Application exceptions never carry HTTP status codes, URNs, or TraceIDs; mapping occurs strictly in the REST API layer mappers.

## Reuse is the Standard Path

A problem type represents a **global semantic meaning**. Ingestion and Worklist share `API_ARCHIVE_UNAVAILABLE` because meaning, status, and client remediation actions are identical.

Before proposing a new problem type, evaluate existing entries for:

1. **Semantic meaning:** Would the client interpret the error identically?
2. **HTTP status code:** Is the HTTP status identical?
3. **Remediation action:** Should the client take the exact same corrective step?

If all three match, reuse the existing entry.

## When to Create a New Type

Only when **at least one** of the following differs from all existing catalog entries:

- Public semantic meaning;
- HTTP status code;
- Client remediation action;
- Retry policy;
- Custom extension schema.
