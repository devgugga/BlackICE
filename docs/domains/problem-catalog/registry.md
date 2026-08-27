# Problem Registry: Grammar, Identity, Lock, and Gates

## Code Grammar

```text
{SCOPE}_{SUBJECT}_{CONDITION}
```

- `SCOPE` is either `API` or `CLIENT`;
- Formatted in `UPPER_SNAKE_CASE`;
- The subject precedes the condition;
- HTTP status codes **never** appear in the code name (`API_ARCHIVE_UNAVAILABLE`, never `API_ERROR_503`);
- Specific features do not appear in the name when semantic meaning is reusable;
- Published codes are immutable.

## Entry Schema

Every entry defines `type`, `code`, `scope`, `description`, `retryPolicy`, `owner`, `extensionsSchemaRef`, `status`, and `replacedBy`. `API` entries add `httpStatus`, `title`, and `detail`; `CLIENT` entries must omit HTTP fields.

- `description`: Internal documentation rationale (not public text).
- `title` and `detail`: Public, English, deterministic, stable strings; **never** derived dynamically from `Exception.getMessage()`. See [`security.md`](./security.md).
- `owner`: Must match an entry from the `owners` array declared in the registry root.
- `retryPolicy`: Either `NEVER` or `MANUAL`. (`AUTOMATIC` is reserved and rejected by the schema without an approved idempotency specification).

## Deterministic Identity

The `type` is a standard `urn:uuid` with a UUIDv5 deterministically computed from:

```text
blackice.problem.v1:{code}
```

within the registry's fixed `namespaceUuid`.

- The `code` deterministically dictates the URN; there is no manual choice.
- The `add` CLI command **never** accepts manual UUID, URN, or namespace parameters.
- Renaming a code alters its URN, which is strictly prohibited for published types.
- UUIDs are never recycled.
- These UUIDs identify HTTP/client errors. **They are not DICOM UIDs**, do not use DICOM UID roots, and never appear inside DICOM objects.

## Immutability & Lock File

Once published, the following fields are frozen: `type`, `code`, `scope`, `httpStatus`, `retryPolicy`, and the extensions schema fingerprint. `catalog.lock.json` records these fields and rejects deviations during `check`.

## Deprecation Workflow

Entries transition only from `active` to `deprecated`. Entries are never deleted, never reactivated, and their UUIDs are never reused.

```bash
mise exec -- pnpm deprecate --code CODE --replaced-by CODE
```

The replacement must exist and remain active.

## Generated Artifacts

These files are outputs of `.problem-catalog` tooling and contain `DO NOT EDIT` headers:

```text
docs/contracts/problems/catalog.lock.json
docs/contracts/problems/catalog.md
apps/backend/src/main/java/dev/blackice/shared/api/problem/generated/ProblemType.java
apps/backend/src/main/java/dev/blackice/shared/api/problem/generated/ProblemExtensions.java
apps/frontend/src/shared/api/problems/problem-types.generated.ts
apps/frontend/src/shared/api/problems/problem-extensions.generated.ts
```

Direct manual edits are prohibited: code generators overwrite them, and `pnpm check` flags diffs during CI.

## Human Gates

An approved design spec detailing problem code, status, title, detail, retry policy, and extension schema serves as the gate for `add`.

Explicit human review is required for:
- Any uncataloged runtime problem type;
- Type deprecation or replacement chains;
- Any attempt to alter frozen immutable fields;
- Changes to published extension schemas.
