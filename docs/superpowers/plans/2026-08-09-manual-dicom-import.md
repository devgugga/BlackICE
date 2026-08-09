# Manual DICOM Import Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> `superpowers:subagent-driven-development` (recommended) or
> `superpowers:executing-plans` to implement this plan task-by-task. Steps use
> checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver an authenticated browser flow that validates multiple DICOM
files in Quarkus, groups valid instances by `StudyInstanceUID`, stores each study
through STOW-RS, and reports local and Archive results without re-encoding pixel
data.

**Architecture:** The Vue feature uploads one `multipart/form-data` request to a
feature-first Quarkus endpoint. Quarkus spools parts to disk, validates metadata
with dcm4che, groups instances by study, and uses a streaming Java HTTP client to
send one `multipart/related` STOW-RS request per study. Processing stays
synchronous with bounded per-request concurrency; durable jobs remain `EVO-001`.

**Tech Stack:** Java 21, Quarkus 3.37.4, dcm4che 5.34.3, Java `HttpClient`, Jackson,
JUnit 5, RestAssured, Vue 3.5, TypeScript 6, Vite 8, Vitest 4, Playwright 1.62,
DCM4CHEE Archive 5.34.3, Keycloak OIDC BFF.

## Global Constraints

- Read `docs/domains/dicom/`, `docs/domains/quarkus/`, and
  `docs/domains/vue/` before editing their respective areas.
- Keep all backend classes flat under
  `dev.blackice.features.ingest`; do not create global technical layers.
- Keep all frontend API, types, state, components, and tests under
  `apps/frontend/src/features/ingest/`; the router only composes the page.
- Never generate or normalize `StudyInstanceUID`, `SeriesInstanceUID`, or
  `SOPInstanceUID`; use the exact values read from the uploaded objects.
- Never decode, transcode, alter, or persist pixel data in Quarkus.
- Never load the complete 500 MB request or a complete DICOM file into heap.
- Reuse the logged-in user's access token containing the existing
  `arc-audience`; do not perform token exchange or use a service credential.
- Require realm role `auth` and a valid CSRF cookie/header pair for upload.
- Defaults are 500 files, 500 MB total, and one concurrent study; all three are
  configurable.
- Use only synthetic DICOM objects in tests; do not commit real patient data.
- A DICOM/DICOMweb reviewer must approve the implementation before the human
  phase gate.
- Do not commit without explicit human authorization. When authorized, use the
  specialized `commit-curator` agent and keep Graphify synchronized according to
  `docs/architecture/graphify.md`.

## File Structure

### Backend files

- Modify `apps/backend/pom.xml`: CSRF, DICOM parser, test-security, and Mockito
  dependencies plus the pinned dcm4che repository/version.
- Modify `apps/backend/src/main/resources/application.properties`: DICOMweb,
  upload limits, concurrency, timeout, temporary uploads, and CSRF properties.
- Create `apps/backend/src/main/java/dev/blackice/features/ingest/UploadedDicom.java`:
  immutable filename/path/size input.
- Create `apps/backend/src/main/java/dev/blackice/features/ingest/ValidatedDicom.java`:
  validated UIDs, path, size, and SHA-256.
- Create `apps/backend/src/main/java/dev/blackice/features/ingest/DicomValidationIssue.java`:
  stable local validation codes.
- Create `apps/backend/src/main/java/dev/blackice/features/ingest/DicomBatchValidation.java`:
  ordered valid-study groups and issues.
- Create `apps/backend/src/main/java/dev/blackice/features/ingest/DicomBatchValidator.java`:
  metadata-only parsing, UID checks, duplicate handling, and grouping.
- Create `apps/backend/src/main/java/dev/blackice/features/ingest/StowInstanceResult.java`:
  accepted, warning, rejected, or unconfirmed SOP result.
- Create `apps/backend/src/main/java/dev/blackice/features/ingest/StowStudyResult.java`:
  one Archive call result.
- Create `apps/backend/src/main/java/dev/blackice/features/ingest/StowResponseParser.java`:
  DICOM JSON response parser.
- Create `apps/backend/src/main/java/dev/blackice/features/ingest/MultipartRelatedBodyPublisher.java`:
  streaming STOW multipart publisher.
- Create `apps/backend/src/main/java/dev/blackice/features/ingest/DicomArchiveGateway.java`:
  Archive boundary.
- Create `apps/backend/src/main/java/dev/blackice/features/ingest/ArchiveUnavailableException.java`:
  safe transport-failure signal without response bodies or tokens.
- Create `apps/backend/src/main/java/dev/blackice/features/ingest/HttpDicomArchiveGateway.java`:
  authenticated STOW-RS implementation.
- Create `apps/backend/src/main/java/dev/blackice/features/ingest/IngestResponse.java`:
  public response contract and nested DTOs.
- Create `apps/backend/src/main/java/dev/blackice/features/ingest/IngestExecution.java`:
  response plus suggested HTTP status.
- Create `apps/backend/src/main/java/dev/blackice/features/ingest/IngestService.java`:
  bounded orchestration across study groups.
- Create `apps/backend/src/main/java/dev/blackice/features/ingest/CurrentAccessToken.java`:
  server-side OIDC access-token adapter.
- Create `apps/backend/src/main/java/dev/blackice/features/ingest/CsrfResource.java`:
  authenticated CSRF token creation path.
- Create `apps/backend/src/main/java/dev/blackice/features/ingest/IngestResource.java`:
  role-protected multipart endpoint.
- Add mirrored tests under
  `apps/backend/src/test/java/dev/blackice/features/ingest/`.

### Frontend files

- Create `apps/frontend/src/features/ingest/ingest.types.ts`: response and UI
  types matching the backend contract.
- Create `apps/frontend/src/features/ingest/ingest.api.ts`: CSRF fetch and XHR
  upload with progress/abort.
- Create `apps/frontend/src/features/ingest/ingest.api.spec.ts`: cookie, header,
  progress, error, and cancellation tests.
- Create `apps/frontend/src/features/ingest/useIngestBatch.ts`: local file and
  phase state machine.
- Create `apps/frontend/src/features/ingest/useIngestBatch.spec.ts`: limits and
  transition tests.
- Create `apps/frontend/src/features/ingest/IngestPage.vue`: page composition.
- Create `apps/frontend/src/features/ingest/IngestFileList.vue`: preflight list.
- Create `apps/frontend/src/features/ingest/IngestResult.vue`: grouped result.
- Modify `apps/frontend/src/app/router/index.ts`: protected `/ingest` route.
- Modify `apps/frontend/src/features/home/HomePage.vue`: navigation link.
- Create `apps/frontend/e2e/fixtures/synthetic-dicom.ts`: in-memory synthetic
  Secondary Capture fixture.
- Create `apps/frontend/e2e/manual-dicom-import.spec.ts`: authenticated E2E.
- Modify `apps/frontend/playwright.config.ts`: include the new E2E file.
- Modify `apps/frontend/package.json`: E2E script.

### Operational and documentation files

- Modify `infra/compose.apps.yml`: internal Archive URL and CSRF signature key.
- Modify `infra/.env.example`: non-secret CSRF variable name and example value.
- Modify `apps/backend/README.md` and `apps/frontend/README.md`: run/test flow.
- Modify `README.md`: distinguish manual STOW import from modality C-STORE.

---

### Task 1: DICOM metadata validation and grouping

**Files:**

- Modify: `apps/backend/pom.xml`
- Create: `apps/backend/src/main/java/dev/blackice/features/ingest/UploadedDicom.java`
- Create: `apps/backend/src/main/java/dev/blackice/features/ingest/ValidatedDicom.java`
- Create: `apps/backend/src/main/java/dev/blackice/features/ingest/DicomValidationIssue.java`
- Create: `apps/backend/src/main/java/dev/blackice/features/ingest/DicomBatchValidation.java`
- Create: `apps/backend/src/main/java/dev/blackice/features/ingest/DicomBatchValidator.java`
- Test: `apps/backend/src/test/java/dev/blackice/features/ingest/DicomBatchValidatorTest.java`

**Interfaces:**

- Consumes: `List<UploadedDicom>` in browser multipart order.
- Produces:
  `DicomBatchValidator.validate(List<UploadedDicom>): DicomBatchValidation`.
- `DicomBatchValidation.validStudies()` is an insertion-ordered
  `Map<String, List<ValidatedDicom>>` keyed by exact `StudyInstanceUID`.

- [ ] **Step 1: Pin the DICOM parser dependency**

Add the official dcm4che repository, a `dcm4che.version` property equal to
`5.34.3`, and `org.dcm4che:dcm4che-core`. Do not add image-codec modules.

```xml
<dcm4che.version>5.34.3</dcm4che.version>

<repository>
    <id>www.dcm4che.org</id>
    <name>dcm4che Repository</name>
    <url>https://www.dcm4che.org/maven2</url>
</repository>

<dependency>
    <groupId>org.dcm4che</groupId>
    <artifactId>dcm4che-core</artifactId>
    <version>${dcm4che.version}</version>
</dependency>
```

- [ ] **Step 2: Write failing validator tests using synthetic objects**

Create test helpers with dcm4che itself; fixed UIDs avoid inventing identities in
application code.

```java
private Path dicom(String study, String series, String sop, byte pixel) throws Exception {
    Attributes ds = new Attributes();
    ds.setString(Tag.SOPClassUID, VR.UI, UID.SecondaryCaptureImageStorage);
    ds.setString(Tag.SOPInstanceUID, VR.UI, sop);
    ds.setString(Tag.StudyInstanceUID, VR.UI, study);
    ds.setString(Tag.SeriesInstanceUID, VR.UI, series);
    ds.setString(Tag.Modality, VR.CS, "OT");
    ds.setInt(Tag.Rows, VR.US, 1);
    ds.setInt(Tag.Columns, VR.US, 1);
    ds.setInt(Tag.SamplesPerPixel, VR.US, 1);
    ds.setString(Tag.PhotometricInterpretation, VR.CS, "MONOCHROME2");
    ds.setInt(Tag.BitsAllocated, VR.US, 8);
    ds.setInt(Tag.BitsStored, VR.US, 8);
    ds.setInt(Tag.HighBit, VR.US, 7);
    ds.setInt(Tag.PixelRepresentation, VR.US, 0);
    ds.setBytes(Tag.PixelData, VR.OB, new byte[] {pixel, 0});
    Path path = temp.resolve(sop + ".dcm");
    try (DicomOutputStream out = new DicomOutputStream(path.toFile())) {
        out.writeDataset(ds.createFileMetaInformation(UID.ExplicitVRLittleEndian), ds);
    }
    return path;
}
```

Cover these exact cases:

- two valid studies become two ordered groups;
- corrupted bytes produce `MALFORMED_DICOM`;
- each absent mandatory UID produces its matching stable code;
- identical bytes with one SOP UID keep the first and mark later copies
  `DUPLICATE_IDENTICAL`;
- different bytes with one SOP UID reject every occurrence as
  `SOP_UID_COLLISION`;
- valid objects retain exact UID strings and paths.

- [ ] **Step 3: Run the focused test and verify RED**

Run:

```powershell
mise exec -- mvn -Dtest=DicomBatchValidatorTest test
```

Expected: compilation fails because the ingest validation types do not exist.

- [ ] **Step 4: Implement immutable validation contracts**

Use these exact record shapes:

```java
public record UploadedDicom(Path path, String filename, long size) {}

public record ValidatedDicom(
    Path path,
    String filename,
    long size,
    String studyInstanceUid,
    String seriesInstanceUid,
    String sopInstanceUid,
    String sopClassUid,
    String sha256
) {}

public record DicomValidationIssue(String filename, Code code, String message) {
    public enum Code {
        MALFORMED_DICOM,
        MISSING_STUDY_INSTANCE_UID,
        MISSING_SERIES_INSTANCE_UID,
        MISSING_SOP_INSTANCE_UID,
        MISSING_SOP_CLASS_UID,
        DUPLICATE_IDENTICAL,
        SOP_UID_COLLISION
    }
}

public record DicomBatchValidation(
    Map<String, List<ValidatedDicom>> validStudies,
    List<DicomValidationIssue> issues
) {}
```

- [ ] **Step 5: Implement metadata-only parsing and deterministic duplicates**

`DicomBatchValidator` must use bulk-data exclusion and a streaming SHA-256:

```java
try (DicomInputStream in = new DicomInputStream(upload.path().toFile())) {
    in.setIncludeBulkData(DicomInputStream.IncludeBulkData.NO);
    Attributes ds = in.readDataset();
    String study = required(ds, Tag.StudyInstanceUID, MISSING_STUDY_INSTANCE_UID);
    String series = required(ds, Tag.SeriesInstanceUID, MISSING_SERIES_INSTANCE_UID);
    String sop = required(ds, Tag.SOPInstanceUID, MISSING_SOP_INSTANCE_UID);
    String sopClass = required(ds, Tag.SOPClassUID, MISSING_SOP_CLASS_UID);
    return new ValidatedDicom(
        upload.path(), upload.filename(), upload.size(),
        study, series, sop, sopClass, sha256(upload.path()));
}
```

First collect candidates by SOP UID. For a group with one member, keep it. For
multiple members with one hash, keep the earliest multipart occurrence and reject
the rest as identical duplicates. For multiple hashes, reject every member as a
UID collision. Finally group retained instances in a `LinkedHashMap` by study UID.

- [ ] **Step 6: Run validator tests and backend regression suite**

Run:

```powershell
mise exec -- mvn -Dtest=DicomBatchValidatorTest test
mise exec -- mvn test
```

Expected: all tests pass and the existing anonymous-session test remains green.

- [ ] **Step 7: Prepare the focused commit gate**

Run the code-only Graphify update, review its diff, and request the specialized
commit curator with the proposed message:

```text
✨ valida lotes DICOM antes da importação
```

---

### Task 2: Streaming STOW-RS client and response interpretation

**Files:**

- Create: `apps/backend/src/main/java/dev/blackice/features/ingest/StowInstanceResult.java`
- Create: `apps/backend/src/main/java/dev/blackice/features/ingest/StowStudyResult.java`
- Create: `apps/backend/src/main/java/dev/blackice/features/ingest/StowResponseParser.java`
- Create: `apps/backend/src/main/java/dev/blackice/features/ingest/MultipartRelatedBodyPublisher.java`
- Create: `apps/backend/src/main/java/dev/blackice/features/ingest/DicomArchiveGateway.java`
- Create: `apps/backend/src/main/java/dev/blackice/features/ingest/ArchiveUnavailableException.java`
- Create: `apps/backend/src/main/java/dev/blackice/features/ingest/HttpDicomArchiveGateway.java`
- Test: `apps/backend/src/test/java/dev/blackice/features/ingest/StowResponseParserTest.java`
- Test: `apps/backend/src/test/java/dev/blackice/features/ingest/HttpDicomArchiveGatewayTest.java`

**Interfaces:**

- Consumes:
  `DicomArchiveGateway.storeStudy(String, List<ValidatedDicom>, String)`.
- Produces `StowStudyResult` containing one result for every submitted SOP UID.
- Infrastructure failures throw `ArchiveUnavailableException`; DICOM rejections
  are values, not exceptions.

- [ ] **Step 1: Write failing DICOM JSON parser tests**

Use the standard DICOM JSON tags:

- `00081199`: Referenced SOP Sequence;
- `00081198`: Failed SOP Sequence;
- `00081155`: Referenced SOP Instance UID;
- `00081197`: Failure Reason;
- `00081196`: Warning Reason.

Test an accepted SOP, a failed SOP, a warning, and a submitted UID absent from both
sequences. The missing UID must become `UNCONFIRMED`, never implicit success.

```java
@Test
void uid_omitido_pelo_archive_nunca_vira_sucesso() {
    String body = """
        {"00081199":{"vr":"SQ","Value":[
          {"00081155":{"vr":"UI","Value":["1.2.3.1"]}}
        ]}}
        """;
    StowStudyResult result = parser.parse(
        "1.2.3", body, new LinkedHashSet<>(List.of("1.2.3.1", "1.2.3.2")));
    assertEquals(StowInstanceResult.Status.ACCEPTED, result.instances().get(0).status());
    assertEquals(StowInstanceResult.Status.UNCONFIRMED, result.instances().get(1).status());
}
```

- [ ] **Step 2: Run the parser test and verify RED**

```powershell
mise exec -- mvn -Dtest=StowResponseParserTest test
```

Expected: compilation fails because the STOW result types do not exist.

- [ ] **Step 3: Implement the STOW result contracts and parser**

```java
public record StowInstanceResult(
    String sopInstanceUid,
    Status status,
    Integer reason
) {
    public enum Status { ACCEPTED, WARNING, REJECTED, UNCONFIRMED }
}

public record StowStudyResult(
    String studyInstanceUid,
    List<StowInstanceResult> instances
) {}
```

`StowResponseParser.parse(String studyUid, String body,
Set<String> submittedSopUids)` must index sequence items by exact UID, prefer a
failed item over a referenced item if the Archive returns contradictory data, and
append `UNCONFIRMED` entries for all submitted UIDs not mentioned by the response.

Define the transport exception without retaining sensitive response content:

```java
public final class ArchiveUnavailableException extends RuntimeException {
    public enum Reason { TIMEOUT, CONNECTION, HTTP_STATUS, INTERRUPTED }

    private final Reason reason;

    public ArchiveUnavailableException(Reason reason, Throwable cause) {
        super(reason.name(), cause);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
```

- [ ] **Step 4: Write failing streaming-gateway tests**

Start a JDK `HttpServer` on an ephemeral port and assert:

- method is `POST` and path ends in `/studies/<StudyInstanceUID>`;
- `Authorization` is `Bearer user-token`;
- top-level media type is
  `multipart/related; type="application/dicom"; boundary=<value>`;
- every part has `Content-Type: application/dicom`;
- both source byte sequences reach the server;
- a non-2xx response throws `ArchiveUnavailableException` without logging body
  content.

```java
@Test
void envia_token_e_multipart_related_para_o_estudo() throws Exception {
    AtomicReference<Headers> headers = new AtomicReference<>();
    AtomicReference<String> path = new AtomicReference<>();
    server.createContext("/rs/studies/1.2.3", exchange -> {
        headers.set(exchange.getRequestHeaders());
        path.set(exchange.getRequestURI().getPath());
        exchange.getRequestBody().transferTo(OutputStream.nullOutputStream());
        byte[] response = "{}".getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, response.length);
        exchange.getResponseBody().write(response);
        exchange.close();
    });
    gateway.storeStudy("1.2.3", files, "user-token");
    assertEquals("/rs/studies/1.2.3", path.get());
    assertEquals("Bearer user-token", headers.get().getFirst("Authorization"));
    assertTrue(headers.get().getFirst("Content-Type").startsWith("multipart/related"));
}
```

- [ ] **Step 5: Implement a zero-buffer multipart publisher**

```java
static HttpRequest.BodyPublisher publish(
    List<ValidatedDicom> files,
    String boundary
) throws FileNotFoundException {
    List<HttpRequest.BodyPublisher> parts = new ArrayList<>();
    for (ValidatedDicom file : files) {
        parts.add(HttpRequest.BodyPublishers.ofByteArray((
            "--" + boundary + "\r\n" +
            "Content-Type: application/dicom\r\n\r\n"
        ).getBytes(StandardCharsets.US_ASCII)));
        parts.add(HttpRequest.BodyPublishers.ofFile(file.path()));
        parts.add(HttpRequest.BodyPublishers.ofByteArray("\r\n".getBytes(StandardCharsets.US_ASCII)));
    }
    parts.add(HttpRequest.BodyPublishers.ofByteArray((
        "--" + boundary + "--\r\n"
    ).getBytes(StandardCharsets.US_ASCII)));
    return HttpRequest.BodyPublishers.concat(parts.toArray(HttpRequest.BodyPublisher[]::new));
}
```

Do not include `Content-Disposition` and do not read file bytes into an aggregate
array.

- [ ] **Step 6: Implement the Archive boundary and HTTP adapter**

```java
public interface DicomArchiveGateway {
    StowStudyResult storeStudy(
        String studyInstanceUid,
        List<ValidatedDicom> files,
        String accessToken
    );
}
```

`HttpDicomArchiveGateway` is `@ApplicationScoped`, injects
`blackice.dicomweb.base-url` and `blackice.dicomweb.request-timeout`, builds a
fresh random boundary per study, sets `Accept: application/dicom+json`, and calls
`StowResponseParser` only for 2xx responses. Append
`/studies/{StudyInstanceUID}` to the configured base URL; this makes the Archive
verify that every instance belongs to the locally validated target study.

- [ ] **Step 7: Run focused and complete backend tests**

```powershell
mise exec -- mvn -Dtest=StowResponseParserTest,HttpDicomArchiveGatewayTest test
mise exec -- mvn test
```

Expected: all tests pass with no full-file buffering assertion failures.

- [ ] **Step 8: Prepare the focused commit gate**

Update Graphify and request the commit curator with:

```text
✨ integra armazenamento STOW-RS autenticado
```

---

### Task 3: Bounded multi-study orchestration and public response

**Files:**

- Create: `apps/backend/src/main/java/dev/blackice/features/ingest/IngestResponse.java`
- Create: `apps/backend/src/main/java/dev/blackice/features/ingest/IngestExecution.java`
- Create: `apps/backend/src/main/java/dev/blackice/features/ingest/IngestService.java`
- Test: `apps/backend/src/test/java/dev/blackice/features/ingest/IngestServiceTest.java`

**Interfaces:**

- Consumes: uploads, current user token, validator, and Archive gateway.
- Produces:
  `IngestService.ingest(List<UploadedDicom>, String): IngestExecution`.
- `IngestExecution.suggestedStatus()` is `200`, `422`, or `503`.

- [ ] **Step 1: Write failing orchestration tests with fakes**

Cover:

- zero valid files returns 422 and never calls the gateway;
- two study groups both run when the first throws `ArchiveUnavailableException`;
- one successful group plus one infrastructure failure returns 200/PARTIAL;
- all attempted groups unavailable returns 503/FAILED;
- local rejects plus Archive rejects produce exact summary counts;
- a configured maximum of one never executes two fake calls simultaneously.

```java
@Test
void continua_depois_de_falha_de_um_estudo() {
    gateway.fail("1.2.3", new ArchiveUnavailableException(
        ArchiveUnavailableException.Reason.CONNECTION, new IOException("offline")));
    IngestExecution execution = service.ingest(twoStudyUploads(), "user-token");
    assertEquals(List.of("1.2.3", "1.2.4"), gateway.calledStudyUids());
    assertEquals(200, execution.suggestedStatus());
    assertEquals(IngestResponse.Outcome.PARTIAL, execution.response().outcome());
}
```

- [ ] **Step 2: Run the service test and verify RED**

```powershell
mise exec -- mvn -Dtest=IngestServiceTest test
```

Expected: compilation fails because `IngestService` and response DTOs do not exist.

- [ ] **Step 3: Define the exact JSON-facing response records**

```java
public record IngestResponse(
    Outcome outcome,
    Summary summary,
    List<StudyResult> studies,
    List<RejectedFile> locallyRejectedFiles
) {
    public enum Outcome { COMPLETE, PARTIAL, FAILED }
    public record Summary(
        int received,
        int locallyValid,
        int locallyRejected,
        int archiveAccepted,
        int archiveRejected
    ) {}
    public record StudyResult(
        String studyInstanceUid,
        StudyStatus status,
        List<InstanceResult> instances,
        String errorCode
    ) {}
    public enum StudyStatus { COMPLETE, PARTIAL, FAILED }
    public record InstanceResult(
        String sopInstanceUid,
        StowInstanceResult.Status status,
        Integer reason
    ) {}
    public record RejectedFile(
        String filename,
        DicomValidationIssue.Code code,
        String message
    ) {}
}

public record IngestExecution(int suggestedStatus, IngestResponse response) {}
```

`errorCode` is `null` for a completed Archive call and
`ARCHIVE_UNAVAILABLE` for transport failures. It never contains an exception
message or response body.

Count `ACCEPTED` and `WARNING` in `archiveAccepted`. Count `REJECTED` and
`UNCONFIRMED` in `archiveRejected`; the latter prevents a missing Archive
confirmation from becoming false success. Define `StudyAttempt` as a private
record inside `IngestService`:

```java
private record StudyAttempt(
    String studyInstanceUid,
    StowStudyResult result,
    ArchiveUnavailableException failure
) {}
```

- [ ] **Step 4: Implement bounded execution with Java 21 virtual threads**

Inject `blackice.ingest.max-concurrent-studies`, create one per-request virtual
thread executor, and guard gateway calls with a `Semaphore`. Preserve the
validator's study order when joining futures.

```java
try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
    Semaphore permits = new Semaphore(maxConcurrentStudies);
    List<Future<StudyAttempt>> futures = groups.entrySet().stream()
        .map(entry -> executor.submit(() -> {
            permits.acquire();
            try {
                return store(entry.getKey(), entry.getValue(), accessToken);
            } finally {
                permits.release();
            }
        }))
        .toList();
    attempts = joinInSubmissionOrder(futures);
}
```

Restore the interrupt flag when interrupted and return a safe infrastructure
failure for that group. Do not retry STOW automatically.

- [ ] **Step 5: Run service and regression tests**

```powershell
mise exec -- mvn -Dtest=IngestServiceTest test
mise exec -- mvn test
```

Expected: all orchestration, count, and concurrency tests pass.

- [ ] **Step 6: Prepare the focused commit gate**

Update Graphify and request the commit curator with:

```text
✨ orquestra importação DICOM por estudo
```

---

### Task 4: Authenticated multipart resource, CSRF, limits, and cleanup

**Files:**

- Modify: `apps/backend/pom.xml`
- Modify: `apps/backend/src/main/resources/application.properties`
- Create: `apps/backend/src/main/java/dev/blackice/features/ingest/CurrentAccessToken.java`
- Create: `apps/backend/src/main/java/dev/blackice/features/ingest/CsrfResource.java`
- Create: `apps/backend/src/main/java/dev/blackice/features/ingest/IngestResource.java`
- Test: `apps/backend/src/test/java/dev/blackice/features/ingest/CsrfResourceTest.java`
- Test: `apps/backend/src/test/java/dev/blackice/features/ingest/IngestResourceTest.java`

**Interfaces:**

- `GET /api/csrf` requires authentication and returns 204 while the CSRF filter
  creates the cookie.
- `POST /api/studies` consumes `multipart/form-data` field `files` and returns
  `IngestResponse` with the service's suggested status.

- [ ] **Step 1: Add security dependencies and exact configuration**

Add:

```xml
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-rest-csrf</artifactId>
</dependency>
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-test-security</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-junit5-mockito</artifactId>
    <scope>test</scope>
</dependency>
```

Configure:

```properties
blackice.dicomweb.base-url=${BLACKICE_DICOMWEB_BASE_URL:http://arc:8080/dcm4chee-arc/aets/DCM4CHEE/rs}
blackice.dicomweb.request-timeout=60S
blackice.ingest.max-files=500
blackice.ingest.max-total-bytes=524288000
blackice.ingest.max-concurrent-studies=1

quarkus.http.limits.max-body-size=500M
quarkus.http.limits.max-form-attribute-size=500M
quarkus.http.body.delete-uploaded-files-on-end=true

quarkus.rest-csrf.create-token-path=/api/csrf
quarkus.rest-csrf.cookie-http-only=false
quarkus.rest-csrf.token-signature-key=${QUARKUS_CSRF_TOKEN_SIGNATURE_KEY}
%test.quarkus.rest-csrf.token-signature-key=0123456789abcdef0123456789abcdef
```

- [ ] **Step 2: Write failing resource/security tests**

Use `@TestSecurity` and `@InjectMock` to cover:

- anonymous GET `/api/csrf` returns 401;
- authenticated GET returns 204 and a `csrf-token` cookie;
- anonymous upload returns 401;
- authenticated user without `auth` returns 403;
- missing CSRF header returns 400;
- matching cookie/header reaches `IngestService`;
- 501 mocked `FileUpload` parts and a mocked cumulative size above 524288000
  return 413 without allocating a 500 MB test body and before service execution;
- an empty multipart collection returns 400;
- returned status/body exactly match `IngestExecution`;
- `X-Request-ID` is a UUID and the framework-managed upload path no longer exists
  after the request finishes.

Use the cookie from the GET in the positive request:

```java
String csrf = given()
    .when().get("/api/csrf")
    .then().statusCode(204)
    .extract().cookie("csrf-token");

given()
    .cookie("csrf-token", csrf)
    .header("X-CSRF-TOKEN", csrf)
    .multiPart("files", "one.dcm", new byte[] {1}, "application/dicom")
    .when().post("/api/studies")
    .then().statusCode(200);
```

- [ ] **Step 3: Run the resource tests and verify RED**

```powershell
mise exec -- mvn -Dtest=CsrfResourceTest,IngestResourceTest test
```

Expected: compilation fails because resources and token adapter do not exist.

- [ ] **Step 4: Implement the server-side token adapter**

```java
@ApplicationScoped
public class CurrentAccessToken {
    @Inject AccessTokenCredential credential;

    public String value() {
        String token = credential == null ? null : credential.getToken();
        if (token == null || token.isBlank()) {
            throw new NotAuthorizedException("Bearer");
        }
        return token;
    }
}
```

Never return or log this value.

- [ ] **Step 5: Implement CSRF and multipart resources**

```java
@Path("/api/csrf")
@Authenticated
public class CsrfResource {
    @GET
    public Response create() {
        return Response.noContent().build();
    }
}

@Path("/api/studies")
@RolesAllowed("auth")
public class IngestResource {
    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    @Blocking
    public Response ingest(@RestForm("files") List<FileUpload> files) {
        List<UploadedDicom> uploads = validateRequestLimits(files);
        String requestId = UUID.randomUUID().toString();
        long started = System.nanoTime();
        MDC.put("requestId", requestId);
        try {
            LOG.infov("manual DICOM import started: files={0}, bytes={1}",
                uploads.size(), uploads.stream().mapToLong(UploadedDicom::size).sum());
            IngestExecution execution = service.ingest(uploads, accessToken.value());
            LOG.infov("manual DICOM import finished: accepted={0}, rejected={1}, durationMs={2}",
                execution.response().summary().archiveAccepted(),
                execution.response().summary().archiveRejected(),
                Duration.ofNanos(System.nanoTime() - started).toMillis());
            return Response.status(execution.suggestedStatus())
                .header("X-Request-ID", requestId)
                .entity(execution.response())
                .build();
        } finally {
            MDC.remove("requestId");
        }
    }
}
```

Use `FileUpload.uploadedFile()`, `fileName()`, and `size()`; reject the request
before calling the service when count or total size exceeds config. Do not move
uploads out of Quarkus' managed upload directory, so end-of-request cleanup stays
effective. Logs contain only counts, byte totals, result totals, duration, and the
request ID; never filenames, DICOM tags, response bodies, or tokens.

- [ ] **Step 6: Run security, backend, and package gates**

```powershell
mise exec -- mvn -Dtest=CsrfResourceTest,IngestResourceTest test
mise exec -- mvn test
mise exec -- mvn package
```

Expected: all tests pass and the Quarkus package builds.

- [ ] **Step 7: Prepare the focused commit gate**

Update Graphify and request the commit curator with:

```text
🔒 protege importação DICOM autenticada
```

---

### Task 5: Frontend upload transport and state machine

**Files:**

- Create: `apps/frontend/src/features/ingest/ingest.types.ts`
- Create: `apps/frontend/src/features/ingest/ingest.api.ts`
- Create: `apps/frontend/src/features/ingest/ingest.api.spec.ts`
- Create: `apps/frontend/src/features/ingest/useIngestBatch.ts`
- Create: `apps/frontend/src/features/ingest/useIngestBatch.spec.ts`

**Interfaces:**

- Produces `fetchCsrfToken(): Promise<string>`.
- Produces
  `uploadStudies(files, csrfToken, onProgress): UploadHandle`.
- Produces `useIngestBatch()` for UI composition.

- [ ] **Step 1: Define TypeScript contracts matching backend names exactly**

```ts
export type IngestOutcome = 'COMPLETE' | 'PARTIAL' | 'FAILED';
export type InstanceStatus = 'ACCEPTED' | 'WARNING' | 'REJECTED' | 'UNCONFIRMED';
export type StudyStatus = 'COMPLETE' | 'PARTIAL' | 'FAILED';

export interface IngestResponse {
  outcome: IngestOutcome;
  summary: {
    received: number;
    locallyValid: number;
    locallyRejected: number;
    archiveAccepted: number;
    archiveRejected: number;
  };
  studies: Array<{
    studyInstanceUid: string;
    status: StudyStatus;
    instances: Array<{
      sopInstanceUid: string;
      status: InstanceStatus;
      reason: number | null;
    }>;
    errorCode: 'ARCHIVE_UNAVAILABLE' | null;
  }>;
  locallyRejectedFiles: Array<{
    filename: string;
    code: string;
    message: string;
  }>;
}

export interface UploadHandle {
  promise: Promise<IngestResponse>;
  abort(): void;
}
```

- [ ] **Step 2: Write failing API tests with a fake XMLHttpRequest**

Cover cookie parsing, GET `/api/csrf`, `withCredentials`,
`X-CSRF-TOKEN`, repeated `files` form parts, progress rounding, JSON parsing,
non-2xx error bodies, network errors, and `abort()`.

The fake factory is injected; production defaults to `new XMLHttpRequest()`:

```ts
export type XhrFactory = () => XMLHttpRequest;

export function uploadStudies(
  files: readonly File[],
  csrfToken: string,
  onProgress: (percent: number) => void,
  xhrFactory: XhrFactory = () => new XMLHttpRequest(),
): UploadHandle;
```

- [ ] **Step 3: Run API tests and verify RED**

```powershell
mise exec -- npm test -- ingest.api.spec.ts
```

Expected: module resolution fails because `ingest.api.ts` does not exist.

- [ ] **Step 4: Implement CSRF cookie reading and XHR transport**

```ts
export async function fetchCsrfToken(
  readCookies: () => string = () => document.cookie,
): Promise<string> {
  const response = await fetch('/api/csrf', { credentials: 'include' });
  if (!response.ok) throw new Error(`CSRF_TOKEN_FAILED:${response.status}`);
  const token = readCookie(readCookies(), 'csrf-token');
  if (!token) throw new Error('CSRF_COOKIE_MISSING');
  return token;
}
```

In `uploadStudies`, append every file under `files`, set
`xhr.withCredentials = true`, set the CSRF header after `open`, switch progress
from `xhr.upload.onprogress`, resolve only after a successful JSON response, and
reject with a typed `UploadError(status, response)` otherwise.

- [ ] **Step 5: Write failing state-machine tests**

The state phases are exactly:

```ts
export type IngestPhase =
  | 'SELECTING'
  | 'READY'
  | 'UPLOADING'
  | 'PROCESSING'
  | 'COMPLETE'
  | 'ERROR'
  | 'CANCELLED';
```

Test 500-file and 524288000-byte boundaries, removal, transfer progress,
`xhr.upload.onload` transition to `PROCESSING`, completion, error, cancellation,
and reset.

- [ ] **Step 6: Implement `useIngestBatch` with local refs only**

Do not add Pinia. Expose readonly state plus `addFiles`, `removeFile`, `start`,
`cancel`, and `reset`. Reject additions that would cross count or byte limits and
return a stable `MAX_FILES` or `MAX_TOTAL_BYTES` code for the page to render.

```ts
export interface IngestBatch {
  phase: Readonly<Ref<IngestPhase>>;
  files: Readonly<Ref<readonly File[]>>;
  progress: Readonly<Ref<number>>;
  response: Readonly<Ref<IngestResponse | null>>;
  errorCode: Readonly<Ref<string | null>>;
  addFiles(incoming: readonly File[]): 'MAX_FILES' | 'MAX_TOTAL_BYTES' | null;
  removeFile(index: number): void;
  start(): Promise<void>;
  cancel(): void;
  reset(): void;
}

export function useIngestBatch(
  limits = { maxFiles: 500, maxTotalBytes: 524_288_000 },
): IngestBatch;
```

- [ ] **Step 7: Run frontend tests and build**

```powershell
mise exec -- npm test
mise exec -- npm run build
```

Expected: all API/state tests pass and strict TypeScript builds.

- [ ] **Step 8: Prepare the focused commit gate**

Update Graphify and request the commit curator with:

```text
✨ adiciona transporte do upload DICOM
```

---

### Task 6: Import page, result presentation, and navigation

**Files:**

- Create: `apps/frontend/src/features/ingest/IngestPage.vue`
- Create: `apps/frontend/src/features/ingest/IngestFileList.vue`
- Create: `apps/frontend/src/features/ingest/IngestResult.vue`
- Modify: `apps/frontend/src/app/router/index.ts`
- Modify: `apps/frontend/src/features/home/HomePage.vue`

**Interfaces:**

- Consumes `useIngestBatch()` and `IngestResponse` from Task 5.
- Produces protected route `/ingest` and an accessible manual-import workflow.

- [ ] **Step 1: Build the focused file-list component**

`IngestFileList.vue` receives `files: readonly File[]`, emits
`remove(index: number)`, displays filename and formatted byte size, and uses a
real button with an accessible label `Remover <filename>`.

```vue
<script setup lang="ts">
defineProps<{ files: readonly File[] }>();
defineEmits<{ remove: [index: number] }>();
const formatBytes = (bytes: number) =>
  new Intl.NumberFormat('pt-BR', { style: 'unit', unit: 'megabyte', maximumFractionDigits: 2 })
    .format(bytes / 1_048_576);
</script>

<template>
  <ul aria-label="Arquivos selecionados">
    <li v-for="(file, index) in files" :key="`${file.name}-${file.size}-${index}`">
      <span>{{ file.name }}</span>
      <span>{{ formatBytes(file.size) }}</span>
      <button type="button" :aria-label="`Remover ${file.name}`" @click="$emit('remove', index)">
        Remover
      </button>
    </li>
  </ul>
</template>
```

- [ ] **Step 2: Build the result component**

`IngestResult.vue` receives one `IngestResponse`, renders the five summary
counters, and uses one `<details>` per study. Use these user-facing categories:

- `Armazenado` for `ACCEPTED`;
- `Armazenado com aviso` for `WARNING`;
- `Rejeitado pelo Archive` for `REJECTED`;
- `Sem confirmação do Archive` for `UNCONFIRMED`;
- `Rejeitado antes do envio` for local issues;
- `Archive indisponível` for `ARCHIVE_UNAVAILABLE`.

Do not render PatientName or PatientID.

```vue
<script setup lang="ts">
import type { IngestResponse } from '@/features/ingest/ingest.types';
defineProps<{ result: IngestResponse }>();
const labels = {
  ACCEPTED: 'Armazenado',
  WARNING: 'Armazenado com aviso',
  REJECTED: 'Rejeitado pelo Archive',
  UNCONFIRMED: 'Sem confirmação do Archive',
} as const;
</script>

<template>
  <section aria-labelledby="ingest-result-title">
    <h2 id="ingest-result-title">Resultado da importação</h2>
    <p>{{ result.summary.archiveAccepted }} armazenados</p>
    <p>{{ result.summary.archiveRejected }} rejeitados ou sem confirmação</p>
    <details v-for="study in result.studies" :key="study.studyInstanceUid">
      <summary>Estudo {{ study.studyInstanceUid }} — {{ study.status }}</summary>
      <ul>
        <li v-for="instance in study.instances" :key="instance.sopInstanceUid">
          {{ instance.sopInstanceUid }} — {{ labels[instance.status] }}
        </li>
      </ul>
    </details>
  </section>
</template>
```

- [ ] **Step 3: Compose the page states**

`IngestPage.vue` must include:

- drop zone with keyboard-operable file input and
  `accept=".dcm,application/dicom"`;
- selected count and total size;
- remove controls before upload;
- determinate `<progress>` during `UPLOADING`;
- indeterminate processing message during `PROCESSING`;
- cancel button only while `UPLOADING`;
- disabled new submission until complete, error, cancellation, or reset;
- result and `Nova importação` reset button.

```vue
<script setup lang="ts">
import IngestFileList from '@/features/ingest/IngestFileList.vue';
import IngestResult from '@/features/ingest/IngestResult.vue';
import { useIngestBatch } from '@/features/ingest/useIngestBatch';

const batch = useIngestBatch();
const select = (event: Event) => {
  const input = event.target as HTMLInputElement;
  batch.addFiles(Array.from(input.files ?? []));
};
</script>

<template>
  <main>
    <h1>Importar DICOM</h1>
    <input type="file" multiple accept=".dcm,application/dicom" @change="select">
    <IngestFileList :files="batch.files.value" @remove="batch.removeFile" />
    <progress v-if="batch.phase.value === 'UPLOADING'" max="100" :value="batch.progress.value" />
    <p v-if="batch.phase.value === 'PROCESSING'" role="status">Processando no Archive…</p>
    <button type="button" :disabled="batch.phase.value !== 'READY'" @click="batch.start">Importar</button>
    <button v-if="batch.phase.value === 'UPLOADING'" type="button" @click="batch.cancel">Cancelar</button>
    <IngestResult v-if="batch.response.value" :result="batch.response.value" />
    <button v-if="['COMPLETE','ERROR','CANCELLED'].includes(batch.phase.value)" type="button" @click="batch.reset">
      Nova importação
    </button>
  </main>
</template>
```

- [ ] **Step 4: Add protected navigation**

```ts
import IngestPage from '@/features/ingest/IngestPage.vue';

routes: [
  { path: '/', name: 'home', component: HomePage, meta: { protected: true } },
  { path: '/ingest', name: 'ingest', component: IngestPage, meta: { protected: true } },
]
```

Add a `RouterLink` labelled `Importar DICOM` to `HomePage.vue` while preserving
the authenticated username display.

- [ ] **Step 5: Run frontend quality gates**

```powershell
mise exec -- npm test
mise exec -- npm run build
```

Expected: all tests pass, route imports resolve, and Vue TypeScript compiles.

- [ ] **Step 6: Prepare the focused commit gate**

Update Graphify and request the commit curator with:

```text
✨ cria interface de importação DICOM
```

---

### Task 7: Compose wiring and authenticated end-to-end proof

**Files:**

- Modify: `infra/compose.apps.yml`
- Modify: `infra/.env.example`
- Create: `apps/frontend/e2e/fixtures/synthetic-dicom.ts`
- Create: `apps/frontend/e2e/manual-dicom-import.spec.ts`
- Modify: `apps/frontend/playwright.config.ts`
- Modify: `apps/frontend/package.json`

**Interfaces:**

- Backend reaches
  `http://arc:8080/dcm4chee-arc/aets/DCM4CHEE/rs` on network `blackice`.
- E2E creates a 1×1 Secondary Capture object entirely in memory.

- [ ] **Step 1: Wire internal Archive and CSRF configuration**

Add to the backend service:

```yaml
BLACKICE_DICOMWEB_BASE_URL: http://arc:8080/dcm4chee-arc/aets/DCM4CHEE/rs
QUARKUS_CSRF_TOKEN_SIGNATURE_KEY: ${QUARKUS_CSRF_TOKEN_SIGNATURE_KEY:?defina QUARKUS_CSRF_TOKEN_SIGNATURE_KEY em infra/.env}
```

Add `arc` to `depends_on`. Add a 32-or-more-character development example to
`infra/.env.example` without changing or printing any real `.env` value.

- [ ] **Step 2: Implement an in-memory Explicit VR Little Endian fixture**

`synthetic-dicom.ts` exports
`createSyntheticDicom(studyUid, seriesUid, sopUid): Buffer`. It writes:

- 128-byte preamble and `DICM`;
- File Meta Information with Media Storage SOP Class/Instance UID and
  Explicit VR Little Endian transfer syntax;
- Secondary Capture SOP Class;
- exact study, series, and SOP UIDs passed by the test;
- synthetic PatientName/PatientID values;
- modality `OT`, one row, one column, MONOCHROME2, 8-bit unsigned pixels;
- one zero-valued pixel padded to an even value length.

Use one helper that writes short-VR headers and one that writes long-VR headers
for `OB`; write every numeric field little-endian and pad UI with NUL and other
text VRs with a space.

```ts
const SECONDARY_CAPTURE = '1.2.840.10008.5.1.4.1.1.7';
const EXPLICIT_VR_LE = '1.2.840.10008.1.2.1';
const IMPLEMENTATION_UID = '2.25.999999999999999999999999999999999999';
const LONG_VR = new Set(['OB', 'OD', 'OF', 'OL', 'OV', 'OW', 'SQ', 'SV', 'UC', 'UR', 'UT', 'UV', 'UN']);

function paddedText(value: string, vr: string): Buffer {
  const raw = Buffer.from(value, 'ascii');
  if (raw.length % 2 === 0) return raw;
  return Buffer.concat([raw, Buffer.from([vr === 'UI' ? 0 : 0x20])]);
}

function element(group: number, tag: number, vr: string, value: Buffer): Buffer {
  const long = LONG_VR.has(vr);
  const header = Buffer.alloc(long ? 12 : 8);
  header.writeUInt16LE(group, 0);
  header.writeUInt16LE(tag, 2);
  header.write(vr, 4, 2, 'ascii');
  if (long) header.writeUInt32LE(value.length, 8);
  else header.writeUInt16LE(value.length, 6);
  return Buffer.concat([header, value]);
}

function text(group: number, tag: number, vr: string, value: string): Buffer {
  return element(group, tag, vr, paddedText(value, vr));
}

function us(group: number, tag: number, value: number): Buffer {
  const data = Buffer.alloc(2);
  data.writeUInt16LE(value);
  return element(group, tag, 'US', data);
}

export function createSyntheticDicom(studyUid: string, seriesUid: string, sopUid: string): Buffer {
  const metaBody = Buffer.concat([
    element(0x0002, 0x0001, 'OB', Buffer.from([0, 1])),
    text(0x0002, 0x0002, 'UI', SECONDARY_CAPTURE),
    text(0x0002, 0x0003, 'UI', sopUid),
    text(0x0002, 0x0010, 'UI', EXPLICIT_VR_LE),
    text(0x0002, 0x0012, 'UI', IMPLEMENTATION_UID),
  ]);
  const metaLength = Buffer.alloc(4);
  metaLength.writeUInt32LE(metaBody.length);

  const dataset = Buffer.concat([
    text(0x0008, 0x0016, 'UI', SECONDARY_CAPTURE),
    text(0x0008, 0x0018, 'UI', sopUid),
    text(0x0008, 0x0020, 'DA', '20260809'),
    text(0x0008, 0x0030, 'TM', '120000'),
    text(0x0008, 0x0050, 'SH', 'SYNTHETIC'),
    text(0x0008, 0x0060, 'CS', 'OT'),
    text(0x0008, 0x0064, 'CS', 'WSD'),
    text(0x0010, 0x0010, 'PN', 'SYNTHETIC^BLACKICE'),
    text(0x0010, 0x0020, 'LO', 'SYNTHETIC'),
    text(0x0020, 0x000d, 'UI', studyUid),
    text(0x0020, 0x000e, 'UI', seriesUid),
    text(0x0020, 0x0010, 'SH', 'TEST'),
    text(0x0020, 0x0011, 'IS', '1'),
    text(0x0020, 0x0013, 'IS', '1'),
    us(0x0028, 0x0002, 1),
    text(0x0028, 0x0004, 'CS', 'MONOCHROME2'),
    us(0x0028, 0x0010, 1),
    us(0x0028, 0x0011, 1),
    us(0x0028, 0x0100, 8),
    us(0x0028, 0x0101, 8),
    us(0x0028, 0x0102, 7),
    us(0x0028, 0x0103, 0),
    element(0x7fe0, 0x0010, 'OB', Buffer.from([0, 0])),
  ]);

  return Buffer.concat([
    Buffer.alloc(128),
    Buffer.from('DICM', 'ascii'),
    element(0x0002, 0x0000, 'UL', metaLength),
    metaBody,
    dataset,
  ]);
}
```

- [ ] **Step 3: Write the authenticated Playwright scenario**

Reuse the login sequence and environment conventions from
`e2e/keycloak-login.spec.ts`. After login:

1. navigate to `/ingest`;
2. call `setInputFiles` with two `FilePayload` objects generated in memory for
   different study UIDs;
3. assert selected count is two;
4. submit and wait for processing completion;
5. assert two study groups are displayed;
6. assert each SOP is `Armazenado` or `Armazenado com aviso`;
7. assert browser requests target only `/api/csrf` and `/api/studies`, never an
   internal DCM4CHEE URL.

```ts
test('imports two synthetic studies through the BFF', async ({ page }) => {
  const requested: string[] = [];
  page.on('request', request => requested.push(request.url()));
  await page.goto('/');
  await page.getByLabel('Usuário', { exact: true }).fill('dr.teste');
  await page.getByLabel('Senha', { exact: true }).fill('teste123');
  await page.getByRole('button', { name: 'Entrar' }).click();
  await page.goto('/ingest');
  await page.locator('input[type=file]').setInputFiles([
    { name: 'study-a.dcm', mimeType: 'application/dicom', buffer:
      createSyntheticDicom('2.25.101', '2.25.1011', '2.25.10111') },
    { name: 'study-b.dcm', mimeType: 'application/dicom', buffer:
      createSyntheticDicom('2.25.102', '2.25.1021', '2.25.10211') },
  ]);
  await expect(page.getByText('2 arquivos')).toBeVisible();
  await page.getByRole('button', { name: 'Importar' }).click();
  await expect(page.getByRole('heading', { name: 'Resultado da importação' })).toBeVisible();
  await expect(page.locator('details')).toHaveCount(2);
  expect(requested.some(url => url.includes('/dcm4chee-arc/'))).toBe(false);
});
```

- [ ] **Step 4: Add the E2E script**

```json
"test:e2e:ingest": "playwright test e2e/manual-dicom-import.spec.ts"
```

Change Playwright discovery to:

```ts
testMatch: ['keycloak-login.spec.ts', 'manual-dicom-import.spec.ts'],
```

- [ ] **Step 5: Run configuration, application, and E2E gates**

Run unit/build gates first:

```powershell
mise exec -- mvn test
mise exec -- mvn package
mise exec -- npm test
mise exec -- npm run build
```

Run the Maven commands from `apps/backend/` and the npm commands from
`apps/frontend/`.

Then start the three compose files with the existing BlackICE environment and run:

```powershell
mise exec -- npm run test:e2e:ingest
```

Run the E2E command from `apps/frontend/`.

Expected: the authenticated user imports two synthetic studies and both receive
explicit STOW results.

- [ ] **Step 6: Prepare the focused commit gate**

Because this task mixes config and code, run the full semantic Graphify update by
the project skill. Request the commit curator with:

```text
✅ comprova importação DICOM ponta a ponta
```

---

### Task 8: Documentation, domain review, and final verification

**Files:**

- Modify: `apps/backend/README.md`
- Modify: `apps/frontend/README.md`
- Modify: `README.md`
- Review: all files created or modified by Tasks 1–7

**Interfaces:**

- Produces portfolio-ready run instructions and final DICOM correctness evidence.
- Does not introduce a QIDO worklist, viewer, jobs, RBAC, MWL, or C-STORE proxy.

- [ ] **Step 1: Document the exact demonstration path**

Backend README must list the five ingest config properties, the DICOM/CSRF
dependencies, and backend test commands. Frontend README must list
`npm run test:e2e:ingest`, the synthetic-data guarantee, and the UI states. Root
README must label MVP #1 as `Importação manual DICOM via STOW-RS` and explicitly
state that clinical modalities send C-STORE directly to DCM4CHEE.

- [ ] **Step 2: Run the complete deterministic verification suite**

```powershell
cd apps/backend
mise exec -- mvn test
mise exec -- mvn package
cd ../frontend
mise exec -- npm test
mise exec -- npm run build
```

Expected: every command exits zero.

- [ ] **Step 3: Run the compose smoke and inspect safe evidence**

Run the E2E from Task 7, then inspect only:

- HTTP statuses;
- study/SOP UIDs from synthetic fixtures;
- accepted/rejected counts;
- Archive audit username/subject;
- absence of patient metadata in backend logs.

Do not print access tokens, `.env`, DICOM payloads, PatientName, or PatientID.

- [ ] **Step 4: Invoke the DICOM domain reviewer**

Give the reviewer the complete patch and ask it to verify:

- UID preservation and hierarchy;
- metadata-only parsing and transfer-syntax preservation;
- correct STOW media type and response tags;
- `FailedSOPSequence` handling;
- user-token propagation;
- absence of pixel persistence and direct browser-to-Archive calls.

Treat any correctness finding as blocking, fix it with a failing regression test,
and rerun the relevant gates.

- [ ] **Step 5: Present the human phase gate**

Provide:

- tests/build commands and exit results;
- E2E result counts;
- DICOM reviewer verdict;
- exact remaining EVO references: `EVO-001`, `EVO-002`, `EVO-003`, `EVO-004`;
- confirmation that no real patient data was used.

Wait for human approval before considering MVP #1 complete.

- [ ] **Step 6: Prepare documentation and graph commits**

Run the full semantic Graphify update, review the graph diff and health, and, after
explicit human authorization, ask the commit curator to create a documentation
commit followed by a graph-only synchronization commit. Proposed documentation
message:

```text
📝 documenta importação DICOM manual
```
