# Worklist and QIDO-RS Search Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver an authenticated Worklist that loads recent studies, filters them through QIDO-RS, and paginates 20 results at a time without counts or product-database persistence.

**Architecture:** Add a clean `dev.blackice.worklist` Quarkus module whose application layer owns validation and pagination while a DICOMweb adapter owns QIDO query construction and DICOM JSON parsing. Add a colocated Vue `features/worklist` client, composable, page, and focused presentation components; the browser consumes only the curated `/api/studies` contract.

**Tech Stack:** Java 21, Quarkus 3.37.4, Jackson, Java `HttpClient`, JUnit 5, Mockito, REST Assured, ArchUnit, Vue 3.5, TypeScript 6, Vite 8, Vitest 4, Vue Test Utils, Playwright 1.62, DCM4CHEE Archive 5.34.3, pnpm 11.22.0.

**Spec:** `docs/superpowers/specs/2026-08-22-worklist-qido-rs-design.md`

## Global Constraints

- Read the spec, `docs/architecture/project-structure.md`, `docs/domains/dicom/dicomweb.md`, `docs/domains/dicom/semantics.md`, `docs/domains/quarkus/conventions.md`, and `docs/domains/vue/conventions.md` before editing.
- QIDO-RS searches metadata; this feature must never call WADO-RS, retrieve pixel data, or persist Worklist metadata in the product PostgreSQL database.
- `StudyInstanceUID` comes from the Archive and is mandatory. Never generate or repair it.
- Do not treat `PatientID` as globally unique; keep `IssuerOfPatientID` beside it.
- The public page size is 20; API `limit` defaults to 20 and is bounded to 1–100; `offset` is bounded to 0–99,999.
- Fetch `limit + 1`, return at most `limit`, compute `hasNext` from the extra match, and never call a count endpoint.
- Send `orderby=-StudyDate,-StudyTime,StudyInstanceUID` and only the required `includefield` values.
- Patient name is prefix matching; Patient ID and modality are exact; user-provided `*`, `?`, and control characters are invalid.
- The QIDO timeout defaults to 10 seconds. Neither backend nor frontend retries automatically.
- Backend Javadoc, code comments, test names, and generated messages are English. Repository documentation and UI copy remain Portuguese.
- Do not log query strings, PatientName, PatientID, DICOM JSON, Archive bodies, or access tokens.
- Every DICOM/DICOMweb task receives a read-only `dicom-domain-reviewer` review before its phase gate.
- Stop for a human gate after Phase 1, Phase 2, and Phase 3. Do not start the next phase without approval.
- Commit steps below are templates only. Do not execute them without explicit human authorization. If authorized, follow `docs/architecture/graphify.md` for the checkout type, run the Graphify semantic update required by `AGENTS.md`, review `graphify-out/`, and preserve the normal-checkout two-commit graph synchronization protocol.
- The approved spec, plan, and `EVO-005` begin execution as uncommitted documentation. Before any authorized code commit, obtain explicit authorization to commit those documents first so Graphify never records an uncommitted design state. If commits remain unauthorized, leave all commit and Graphify-update steps pending until the final gate.

## File Map

### Backend production

- Create `apps/backend/src/main/java/dev/blackice/worklist/application/input/StudySearchRequest.java`: normalized, validated product query.
- Create `apps/backend/src/main/java/dev/blackice/worklist/application/result/StudySummary.java`: curated study metadata.
- Create `apps/backend/src/main/java/dev/blackice/worklist/application/result/StudyPage.java`: items and page metadata.
- Create `apps/backend/src/main/java/dev/blackice/worklist/application/port/StudyQueryGateway.java`: Archive query port.
- Create `apps/backend/src/main/java/dev/blackice/worklist/application/usecase/SearchStudiesUseCase.java`: one-extra-result pagination.
- Create `apps/backend/src/main/java/dev/blackice/worklist/application/exception/InvalidStudySearchException.java`: validation failure.
- Create `apps/backend/src/main/java/dev/blackice/worklist/application/exception/ArchiveSearchException.java`: safe upstream failure taxonomy.
- Create `apps/backend/src/main/java/dev/blackice/worklist/infrastructure/dicomweb/QidoQueryBuilder.java`: product filters to QIDO URI.
- Create `apps/backend/src/main/java/dev/blackice/worklist/infrastructure/dicomweb/QidoStudyResponseParser.java`: DICOM JSON to curated results.
- Create `apps/backend/src/main/java/dev/blackice/worklist/infrastructure/dicomweb/HttpQidoStudyGateway.java`: authenticated, timed, single-call QIDO adapter.
- Create `apps/backend/src/main/java/dev/blackice/worklist/api/WorklistErrorResponse.java`: stable safe error payload.
- Create `apps/backend/src/main/java/dev/blackice/worklist/api/WorklistResource.java`: authenticated HTTP boundary.
- Create focused `package-info.java` files for each new package with its responsibility.
- Modify `apps/backend/src/main/resources/application.properties`: add `blackice.worklist.request-timeout=10S`.
- Modify `apps/backend/src/test/java/dev/blackice/architecture/BackendArchitectureTest.java`: protect Worklist boundaries.

### Backend tests

- Create `apps/backend/src/test/java/dev/blackice/worklist/application/input/StudySearchRequestTest.java`.
- Create `apps/backend/src/test/java/dev/blackice/worklist/application/usecase/SearchStudiesUseCaseTest.java`.
- Create `apps/backend/src/test/java/dev/blackice/worklist/infrastructure/dicomweb/QidoQueryBuilderTest.java`.
- Create `apps/backend/src/test/java/dev/blackice/worklist/infrastructure/dicomweb/QidoStudyResponseParserTest.java`.
- Create `apps/backend/src/test/java/dev/blackice/worklist/infrastructure/dicomweb/HttpQidoStudyGatewayTest.java`.
- Create `apps/backend/src/test/java/dev/blackice/worklist/api/WorklistResourceTest.java`.

### Frontend production and tests

- Create `apps/frontend/src/features/worklist/worklist.types.ts`: public and UI types.
- Create `apps/frontend/src/features/worklist/worklist.api.ts` and `.spec.ts`: cancellable API client.
- Create `apps/frontend/src/features/worklist/useWorklist.ts` and `.spec.ts`: feature state machine.
- Create `apps/frontend/src/features/worklist/WorklistFilters.vue`: accessible filter form.
- Create `apps/frontend/src/features/worklist/StudyList.vue`: table/card presentation.
- Create `apps/frontend/src/features/worklist/StudyPagination.vue`: previous/next controls.
- Create `apps/frontend/src/features/worklist/WorklistPage.vue` and `.spec.ts`: orchestration and rendered states.
- Modify `apps/frontend/src/app/router/index.ts`: register `/studies`.
- Modify `apps/frontend/src/features/home/HomePage.vue`: link to the Worklist.
- Modify `apps/frontend/vite.config.ts`, `package.json`, and `pnpm-lock.yaml`: add the jsdom Vue component-test harness and pin pnpm.

### Integration and operational files

- Modify `apps/frontend/e2e/fixtures/synthetic-dicom.ts`: accept synthetic study metadata without changing default callers.
- Create `apps/frontend/e2e/worklist.spec.ts`: STOW-to-QIDO, filters, pagination, responsive layout, and concurrent read/write scenario.
- Modify `apps/frontend/playwright.config.ts`: include the Worklist suite.
- Modify `apps/frontend/Dockerfile`: use `pnpm-lock.yaml` and pnpm 11.22.0.
- Modify `README.md`, `apps/frontend/README.md`, `apps/backend/README.md`, and `docs/architecture/project-structure.md`: pnpm commands and Worklist operations.

---

## Phase 1 — Backend QIDO vertical slice

### Task 1: Application contract, validation, and pagination

**Files:**
- Create: `apps/backend/src/main/java/dev/blackice/worklist/application/input/StudySearchRequest.java`
- Create: `apps/backend/src/main/java/dev/blackice/worklist/application/result/StudySummary.java`
- Create: `apps/backend/src/main/java/dev/blackice/worklist/application/result/StudyPage.java`
- Create: `apps/backend/src/main/java/dev/blackice/worklist/application/port/StudyQueryGateway.java`
- Create: `apps/backend/src/main/java/dev/blackice/worklist/application/usecase/SearchStudiesUseCase.java`
- Create: `apps/backend/src/main/java/dev/blackice/worklist/application/exception/InvalidStudySearchException.java`
- Create: package documentation under `apps/backend/src/main/java/dev/blackice/worklist/application/`
- Test: `apps/backend/src/test/java/dev/blackice/worklist/application/input/StudySearchRequestTest.java`
- Test: `apps/backend/src/test/java/dev/blackice/worklist/application/usecase/SearchStudiesUseCaseTest.java`

**Interfaces:**
- Consumes: no Worklist production code.
- Produces: `StudySearchRequest`, `StudySummary`, `StudyPage`, `StudyQueryGateway.search(StudySearchRequest, int, String)`, and `SearchStudiesUseCase.search(StudySearchRequest, String)`.

- [ ] **Step 1: Write failing validation tests**

```java
@Test
void normalizes_filters_and_keeps_open_date_range() {
    StudySearchRequest request = new StudySearchRequest(
        "  MARIA  ", "  123  ", "ct", LocalDate.of(2026, 8, 1), null, 20, 0);

    assertEquals("MARIA", request.patientName());
    assertEquals("123", request.patientId());
    assertEquals("CT", request.modality());
    assertEquals(LocalDate.of(2026, 8, 1), request.dateFrom());
    assertNull(request.dateTo());
}

@ParameterizedTest
@MethodSource("invalidRequests")
void rejects_invalid_filters(Executable invalidRequest) {
    assertThrows(InvalidStudySearchException.class, invalidRequest);
}
```

Define `invalidRequests()` as `Stream<Executable>`. The provider must cover
`limit` 0 and 101, `offset` -1 and 100,000,
reversed dates, `*`/`?` in patient fields, control characters, PatientName or
PatientID longer than 64 characters, and a modality outside `[A-Z0-9_]{1,16}`.

- [ ] **Step 2: Run the focused test and confirm RED**

Run from `apps/backend/`:

```bash
mise exec -- mvn -Dtest=StudySearchRequestTest test
```

Expected: compilation fails because the Worklist application types do not exist.

- [ ] **Step 3: Add the validated request and immutable result records**

```java
public record StudySearchRequest(
    String patientName,
    String patientId,
    String modality,
    LocalDate dateFrom,
    LocalDate dateTo,
    int limit,
    int offset
) {
    public static final int DEFAULT_LIMIT = 20;
    public static final int MAX_LIMIT = 100;
    public static final int MAX_OFFSET = 99_999;

    public StudySearchRequest {
        patientName = normalize(patientName);
        patientId = normalize(patientId);
        modality = normalize(modality);
        modality = modality == null ? null : modality.toUpperCase(Locale.ROOT);
        validateText("patientName", patientName, 64);
        validateText("patientId", patientId, 64);
        if (modality != null && !modality.matches("[A-Z0-9_]{1,16}")) {
            throw new InvalidStudySearchException("INVALID_MODALITY");
        }
        if (dateFrom != null && dateTo != null && dateFrom.isAfter(dateTo)) {
            throw new InvalidStudySearchException("INVALID_DATE_RANGE");
        }
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new InvalidStudySearchException("INVALID_LIMIT");
        }
        if (offset < 0 || offset > MAX_OFFSET) {
            throw new InvalidStudySearchException("INVALID_OFFSET");
        }
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) return null;
        return value.strip();
    }

    private static void validateText(String field, String value, int maximumLength) {
        if (value == null) return;
        boolean invalid = value.length() > maximumLength || value.chars()
            .anyMatch(character -> Character.isISOControl(character) || character == '*' || character == '?');
        if (invalid) throw new InvalidStudySearchException("INVALID_" + field.toUpperCase(Locale.ROOT));
    }
}
```

Create these exact result shapes:

```java
public record StudySummary(
    String studyInstanceUid,
    String patientName,
    String patientId,
    String patientIdIssuer,
    String studyDate,
    String studyTime,
    List<String> modalities,
    String description,
    Integer seriesCount,
    Integer instanceCount
) {
    public StudySummary {
        modalities = modalities == null ? List.of() : List.copyOf(modalities);
    }
}

public record StudyPage(List<StudySummary> items, PageMetadata page) {
    public StudyPage {
        items = List.copyOf(items);
    }

    public record PageMetadata(int limit, int offset, boolean hasPrevious, boolean hasNext) {}
}
```

- [ ] **Step 4: Write the failing use-case pagination test**

```java
@Test
void requests_one_extra_match_and_builds_page_without_count() {
    CapturingGateway gateway = new CapturingGateway(studies(21));
    SearchStudiesUseCase useCase = new SearchStudiesUseCase(gateway);
    StudySearchRequest request = request(20, 40);

    StudyPage result = useCase.search(request, "user-token");

    assertEquals(21, gateway.fetchLimit);
    assertEquals(1, gateway.calls);
    assertEquals("user-token", gateway.token);
    assertEquals(20, result.items().size());
    assertEquals(new StudyPage.PageMetadata(20, 40, true, true), result.page());
}

@Test
void final_partial_page_has_no_next_page() {
    StudyPage result = new SearchStudiesUseCase(new CapturingGateway(studies(7)))
        .search(request(20, 20), "user-token");

    assertEquals(7, result.items().size());
    assertTrue(result.page().hasPrevious());
    assertFalse(result.page().hasNext());
}
```

In the test class, define `request(int limit, int offset)` to return an empty
filter request, `studies(int count)` with `IntStream.range(0, count)` and valid
UIDs `1.2.840.<index + 1>`, and `CapturingGateway implements StudyQueryGateway`
with public test fields `calls`, `fetchLimit`, and `token`. Its `search` stores
those arguments and returns the immutable list passed to its constructor.

- [ ] **Step 5: Implement the port and use case minimally**

```java
public interface StudyQueryGateway {
    List<StudySummary> search(StudySearchRequest request, int fetchLimit, String accessToken);
}

@ApplicationScoped
public class SearchStudiesUseCase {
    private final StudyQueryGateway gateway;

    @Inject
    public SearchStudiesUseCase(StudyQueryGateway gateway) {
        this.gateway = Objects.requireNonNull(gateway, "gateway must not be null");
    }

    public StudyPage search(StudySearchRequest request, String accessToken) {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(accessToken, "accessToken must not be null");
        List<StudySummary> matches = gateway.search(request, request.limit() + 1, accessToken);
        boolean hasNext = matches.size() > request.limit();
        List<StudySummary> items = matches.stream().limit(request.limit()).toList();
        return new StudyPage(items, new StudyPage.PageMetadata(
            request.limit(), request.offset(), request.offset() > 0, hasNext));
    }
}
```

- [ ] **Step 6: Run the application tests and confirm GREEN**

```bash
mise exec -- mvn -Dtest=StudySearchRequestTest,SearchStudiesUseCaseTest test
```

Expected: both test classes pass with no failures.

- [ ] **Step 7: Conditional focused commit**

After explicit commit authorization and the required Graphify workflow:

```bash
git add apps/backend/src/main/java/dev/blackice/worklist/application apps/backend/src/test/java/dev/blackice/worklist/application graphify-out
git commit -m "✨ implementa núcleo paginado da worklist"
```

### Task 2: QIDO URI builder and DICOM JSON parser

**Files:**
- Create: `apps/backend/src/main/java/dev/blackice/worklist/infrastructure/dicomweb/QidoQueryBuilder.java`
- Create: `apps/backend/src/main/java/dev/blackice/worklist/infrastructure/dicomweb/QidoStudyResponseParser.java`
- Create: `apps/backend/src/main/java/dev/blackice/worklist/infrastructure/dicomweb/package-info.java`
- Create: `apps/backend/src/test/java/dev/blackice/worklist/infrastructure/dicomweb/QidoQueryBuilderTest.java`
- Create: `apps/backend/src/test/java/dev/blackice/worklist/infrastructure/dicomweb/QidoStudyResponseParserTest.java`

**Interfaces:**
- Consumes: `StudySearchRequest` and `StudySummary` from Task 1.
- Produces: `QidoQueryBuilder.build(String, StudySearchRequest, int): URI` and `QidoStudyResponseParser.parse(String): List<StudySummary>`.

- [ ] **Step 1: Write failing query-builder tests**

```java
@Test
void maps_curated_filters_to_bounded_ordered_qido_query() {
    StudySearchRequest request = new StudySearchRequest(
        "MARIA", "123", "ct", LocalDate.of(2026, 8, 1),
        LocalDate.of(2026, 8, 22), 20, 40);

    URI uri = new QidoQueryBuilder().build(BASE_URL, request, 21);
    String query = URLDecoder.decode(uri.getRawQuery(), StandardCharsets.UTF_8);

    assertTrue(query.contains("PatientName=MARIA*"));
    assertTrue(query.contains("PatientID=123"));
    assertTrue(query.contains("ModalitiesInStudy=CT"));
    assertTrue(query.contains("StudyDate=20260801-20260822"));
    assertTrue(query.contains("limit=21"));
    assertTrue(query.contains("offset=40"));
    assertTrue(query.contains("orderby=-StudyDate,-StudyTime,StudyInstanceUID"));
    assertTrue(query.contains("includefield=00100021,00081030,00201206,00201208"));
    assertFalse(query.contains("includefield=all"));
}

@Test
void omits_empty_filters_and_encodes_open_date_ranges() {
    URI uri = new QidoQueryBuilder().build(BASE_URL,
        new StudySearchRequest(null, null, null, null, LocalDate.of(2026, 8, 22), 20, 0), 21);
    assertTrue(URLDecoder.decode(uri.getRawQuery(), StandardCharsets.UTF_8)
        .contains("StudyDate=-20260822"));
}
```

Define `BASE_URL` as
`http://127.0.0.1:8080/dcm4chee-arc/aets/DCM4CHEE/rs` in the test class.

- [ ] **Step 2: Run the builder test and confirm RED**

```bash
mise exec -- mvn -Dtest=QidoQueryBuilderTest test
```

Expected: compilation fails because `QidoQueryBuilder` is absent.

- [ ] **Step 3: Implement deterministic query construction**

Use a `LinkedHashMap<String, String>` so tests and logs are deterministic. Join
parameters with an RFC-3986-style encoder based on `URLEncoder`, replacing `+`
with `%20`. The builder must add filters only when present and always add these
fixed parameters:

```java
parameters.put("limit", Integer.toString(fetchLimit));
parameters.put("offset", Integer.toString(request.offset()));
parameters.put("orderby", "-StudyDate,-StudyTime,StudyInstanceUID");
parameters.put("includefield", "00100021,00081030,00201206,00201208");
```

Build the date value with the exact cases below:

```java
private String studyDate(StudySearchRequest request) {
    DateTimeFormatter dicomDate = DateTimeFormatter.BASIC_ISO_DATE;
    if (request.dateFrom() != null && request.dateTo() != null) {
        return dicomDate.format(request.dateFrom()) + "-" + dicomDate.format(request.dateTo());
    }
    if (request.dateFrom() != null) return dicomDate.format(request.dateFrom()) + "-";
    if (request.dateTo() != null) return "-" + dicomDate.format(request.dateTo());
    return null;
}
```

- [ ] **Step 4: Write failing DICOM JSON parsing tests**

```java
@Test
void parses_person_name_multivalue_modalities_and_counts() {
    String body = """
        [{
          "0020000D":{"vr":"UI","Value":["1.2.840.1"]},
          "00100010":{"vr":"PN","Value":[{"Alphabetic":"MARIA^SILVA"}]},
          "00100020":{"vr":"LO","Value":["123"]},
          "00100021":{"vr":"LO","Value":["HOSPITAL-A"]},
          "00080020":{"vr":"DA","Value":["20260822"]},
          "00080030":{"vr":"TM","Value":["103512.250"]},
          "00080061":{"vr":"CS","Value":["CT","SR"]},
          "00081030":{"vr":"LO","Value":["CT CHEST"]},
          "00201206":{"vr":"IS","Value":[3]},
          "00201208":{"vr":"IS","Value":[187]}
        }]
        """;

    StudySummary study = new QidoStudyResponseParser(new ObjectMapper()).parse(body).getFirst();
    assertEquals("1.2.840.1", study.studyInstanceUid());
    assertEquals("MARIA^SILVA", study.patientName());
    assertEquals("2026-08-22", study.studyDate());
    assertEquals("10:35:12.250", study.studyTime());
    assertEquals(List.of("CT", "SR"), study.modalities());
    assertEquals(3, study.seriesCount());
    assertEquals(187, study.instanceCount());
}

@Test
void rejects_missing_uid_wrong_vr_and_invalid_count() {
    QidoStudyResponseParser parser = new QidoStudyResponseParser(new ObjectMapper());
    assertThrows(IllegalArgumentException.class,
        () -> parser.parse("[{\"00100010\":{\"vr\":\"PN\",\"Value\":[]}}]"));
    assertThrows(IllegalArgumentException.class,
        () -> parser.parse("[{\"0020000D\":{\"vr\":\"LO\",\"Value\":[\"1.2.3\"]}}]"));
    assertThrows(IllegalArgumentException.class,
        () -> parser.parse("[{\"0020000D\":{\"vr\":\"UI\",\"Value\":[\"1.2.3\"]},\"00201206\":{\"vr\":\"IS\",\"Value\":[\"many\"]}}]"));
}
```

Also cover an empty array; missing optional fields; `Ideographic` then
`Phonetic` fallback when `Alphabetic` is absent; counts encoded as JSON strings;
invalid root objects; and a UID longer than 64 characters or outside
`[0-9]+(\\.[0-9]+)+`.

- [ ] **Step 5: Run the parser test and confirm RED**

```bash
mise exec -- mvn -Dtest=QidoStudyResponseParserTest test
```

Expected: compilation fails because the parser is absent.

- [ ] **Step 6: Implement strict tag-aware parsing**

Inject `ObjectMapper`, require an array root, require tag `0020000D` with VR
`UI`, and validate the UID before constructing `StudySummary`. Define constants
for every tag and these focused helpers:

```java
private JsonNode attribute(JsonNode dataset, String tag, String expectedVr)
private String firstText(JsonNode dataset, String tag, String expectedVr)
private String personName(JsonNode dataset)
private List<String> texts(JsonNode dataset, String tag, String expectedVr)
private Integer integer(JsonNode dataset, String tag)
private String dicomDate(JsonNode dataset)
private String dicomTime(JsonNode dataset)
private String requireStudyUid(JsonNode dataset)
```

`personName` selects `Alphabetic`, then `Ideographic`, then `Phonetic`.
`dicomDate` converts `yyyyMMdd` to ISO. `dicomTime` accepts DICOM TM precision
`HH`, `HHmm`, `HHmmss`, and optional fractional seconds, returning a valid ISO
time string without inventing omitted components. Optional absent attributes
return `null` or `List.of()`; present attributes with a wrong VR or malformed
value throw `IllegalArgumentException`.

- [ ] **Step 7: Run both infrastructure unit tests and confirm GREEN**

```bash
mise exec -- mvn -Dtest=QidoQueryBuilderTest,QidoStudyResponseParserTest test
```

Expected: all query and parser cases pass.

- [ ] **Step 8: Conditional focused commit**

After explicit commit authorization and the required Graphify workflow:

```bash
git add apps/backend/src/main/java/dev/blackice/worklist/infrastructure/dicomweb apps/backend/src/test/java/dev/blackice/worklist/infrastructure/dicomweb graphify-out
git commit -m "✨ adiciona tradução QIDO-RS da worklist"
```

### Task 3: Authenticated HTTP QIDO gateway and failure taxonomy

**Files:**
- Create: `apps/backend/src/main/java/dev/blackice/worklist/application/exception/ArchiveSearchException.java`
- Create: `apps/backend/src/main/java/dev/blackice/worklist/infrastructure/dicomweb/HttpQidoStudyGateway.java`
- Create: `apps/backend/src/test/java/dev/blackice/worklist/infrastructure/dicomweb/HttpQidoStudyGatewayTest.java`
- Modify: `apps/backend/src/main/resources/application.properties`

**Interfaces:**
- Consumes: `StudyQueryGateway`, `QidoQueryBuilder`, `QidoStudyResponseParser`, and Task 1 application types.
- Produces: an `@ApplicationScoped` implementation of `StudyQueryGateway`; `ArchiveSearchException.Reason` values `QUERY_TOO_BROAD`, `INVALID_RESPONSE`, `TIMEOUT`, `CONNECTION`, and `HTTP_STATUS`.

- [ ] **Step 1: Write failing adapter tests with a local HTTP server**

Follow `HttpDicomArchiveGatewayTest`'s `HttpServer` setup and add these cases:

```java
@Test
void sends_one_authenticated_qido_request_and_parses_response() {
    AtomicInteger calls = new AtomicInteger();
    AtomicReference<Headers> headers = new AtomicReference<>();
    AtomicReference<String> rawQuery = new AtomicReference<>();
    server.createContext("/dcm4chee-arc/aets/DCM4CHEE/rs/studies", exchange -> {
        calls.incrementAndGet();
        headers.set(exchange.getRequestHeaders());
        rawQuery.set(exchange.getRequestURI().getRawQuery());
        respond(exchange, 200, "application/dicom+json", VALID_QIDO_BODY);
    });

    List<StudySummary> result = gateway(Duration.ofSeconds(2))
        .search(request(), 21, "user-token");

    assertEquals(1, calls.get());
    assertEquals("Bearer user-token", headers.get().getFirst("Authorization"));
    assertEquals("application/dicom+json", headers.get().getFirst("Accept"));
    assertTrue(URLDecoder.decode(rawQuery.get(), StandardCharsets.UTF_8).contains("limit=21"));
    assertEquals(1, result.size());
}
```

Add distinct assertions for upstream 413, other non-2xx status, wrong content
type, invalid JSON, timeout, refused connection, and interrupted execution. Error
messages must equal the stable reason name and must not contain the Archive body,
query filters, or token.

Define `VALID_QIDO_BODY` as a one-item DICOM JSON array containing UI tag
`0020000D` with value `1.2.3`. Implement test helpers `respond(HttpExchange, int,
String, String)`, `request()` returning an empty-filter 20/0 request, and
`gateway(Duration)` using the local server URL, real builder/parser, and an
injected `HttpClient`.

- [ ] **Step 2: Run the adapter test and confirm RED**

```bash
mise exec -- mvn -Dtest=HttpQidoStudyGatewayTest test
```

Expected: compilation fails because the gateway and exception are absent.

- [ ] **Step 3: Implement the safe exception taxonomy**

```java
public final class ArchiveSearchException extends RuntimeException {
    public enum Reason { QUERY_TOO_BROAD, INVALID_RESPONSE, TIMEOUT, CONNECTION, HTTP_STATUS }

    private final Reason reason;

    public ArchiveSearchException(Reason reason, Throwable cause) {
        super(reason.name(), cause);
        this.reason = Objects.requireNonNull(reason, "reason must not be null");
    }

    public Reason reason() {
        return reason;
    }
}
```

- [ ] **Step 4: Implement the gateway with one blocking GET**

Use constructor injection for base URL, `blackice.worklist.request-timeout`,
builder, parser, and a package-visible test constructor accepting `HttpClient`.
The request is exactly:

```java
HttpRequest request = HttpRequest.newBuilder(uri)
    .timeout(requestTimeout)
    .header("Authorization", "Bearer " + accessToken)
    .header("Accept", "application/dicom+json")
    .GET()
    .build();
```

Map HTTP 413 to `QUERY_TOO_BROAD`; other non-2xx to `HTTP_STATUS`; wrong content
type and parse failures to `INVALID_RESPONSE`; connect/read timeout to `TIMEOUT`;
connection I/O to `CONNECTION`. Restore the interrupted flag before mapping an
`InterruptedException`. Never include `response.body()` in an exception or log.

Add the separate interactive timeout:

```properties
blackice.worklist.request-timeout=10S
```

- [ ] **Step 5: Run the gateway and existing STOW adapter tests**

```bash
mise exec -- mvn -Dtest=HttpQidoStudyGatewayTest,HttpDicomArchiveGatewayTest test
```

Expected: both QIDO and STOW adapter suites pass; no STOW behavior changes.

- [ ] **Step 6: Conditional focused commit**

After explicit commit authorization and the required Graphify workflow:

```bash
git add apps/backend/src/main/java/dev/blackice/worklist/application/exception/ArchiveSearchException.java apps/backend/src/main/java/dev/blackice/worklist/infrastructure/dicomweb/HttpQidoStudyGateway.java apps/backend/src/test/java/dev/blackice/worklist/infrastructure/dicomweb/HttpQidoStudyGatewayTest.java apps/backend/src/main/resources/application.properties graphify-out
git commit -m "✨ integra worklist ao QIDO-RS autenticado"
```

### Task 4: Authenticated Worklist HTTP boundary and architecture rules

**Files:**
- Create: `apps/backend/src/main/java/dev/blackice/worklist/api/WorklistErrorResponse.java`
- Create: `apps/backend/src/main/java/dev/blackice/worklist/api/WorklistResource.java`
- Create: `apps/backend/src/main/java/dev/blackice/worklist/api/package-info.java`
- Create: `apps/backend/src/main/java/dev/blackice/worklist/package-info.java`
- Create: `apps/backend/src/test/java/dev/blackice/worklist/api/WorklistResourceTest.java`
- Modify: `apps/backend/src/test/java/dev/blackice/architecture/BackendArchitectureTest.java`
- Modify: `apps/backend/README.md`

**Interfaces:**
- Consumes: `SearchStudiesUseCase`, `StudySearchRequest`, `StudyPage`, `ArchiveSearchException`, and `AccessTokenProvider`.
- Produces: authenticated `GET /api/studies` and stable `{ "code": "...", "message": "..." }` errors.

- [ ] **Step 1: Write failing resource authorization and success tests**

```java
@Test
void anonymous_get_receives_authentication_challenge() {
    given().redirects().follow(false).when().get("/api/studies").then().statusCode(302);
}

@Test
@TestSecurity(user = "user", roles = "viewer")
void user_without_auth_role_receives_403() {
    given().when().get("/api/studies").then().statusCode(403);
}

@Test
@TestSecurity(user = "dr.teste", roles = "auth")
void valid_query_returns_curated_page_and_calls_use_case_once() {
    when(accessToken.accessToken()).thenReturn("user-token");
    when(useCase.search(any(), eq("user-token"))).thenReturn(page());

    given().queryParam("patientName", "MARIA")
        .queryParam("limit", 20).queryParam("offset", 0)
        .when().get("/api/studies")
        .then().statusCode(200)
        .body("items[0].studyInstanceUid", equalTo("1.2.3"))
        .body("page.limit", equalTo(20))
        .body("page.hasNext", equalTo(false));

    verify(useCase, times(1)).search(any(), eq("user-token"));
}
```

Add tests for default limit/offset, invalid ISO dates, reversed dates, wildcards,
invalid modality, 413, 502, and 503. Verify no CSRF header is necessary for GET.

- [ ] **Step 2: Run the resource test and confirm RED**

```bash
mise exec -- mvn -Dtest=WorklistResourceTest test
```

Expected: compilation fails because the Worklist API boundary is absent.

- [ ] **Step 3: Implement the resource and safe error payload**

```java
public record WorklistErrorResponse(String code, String message) {}

@Path("/api/studies")
@RolesAllowed("auth")
public class WorklistResource {
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Blocking
    public Response search(
        @QueryParam("patientName") String patientName,
        @QueryParam("patientId") String patientId,
        @QueryParam("modality") String modality,
        @QueryParam("dateFrom") String dateFrom,
        @QueryParam("dateTo") String dateTo,
        @DefaultValue("20") @QueryParam("limit") int limit,
        @DefaultValue("0") @QueryParam("offset") int offset
    ) {
        String requestId = UUID.randomUUID().toString();
        long started = System.nanoTime();
        MDC.put("requestId", requestId);
        try {
            StudySearchRequest request = request(patientName, patientId, modality, dateFrom, dateTo, limit, offset);
            StudyPage page = useCase.search(request, accessTokenProvider.accessToken());
            LOG.infov("worklist search finished: limit={0}, offset={1}, hasPatientName={2}, hasPatientId={3}, hasModality={4}, hasDateRange={5}, results={6}, durationMs={7}",
                limit, offset, patientName != null, patientId != null, modality != null,
                dateFrom != null || dateTo != null, page.items().size(), elapsedMillis(started));
            return Response.ok(page).header("X-Request-ID", requestId).build();
        } catch (InvalidStudySearchException | DateTimeParseException exception) {
            return error(400, "INVALID_SEARCH", "Review the supplied search filters.", requestId);
        } catch (ArchiveSearchException exception) {
            return archiveError(exception.reason(), requestId);
        } finally {
            MDC.remove("requestId");
        }
    }
}
```

`archiveError` maps `QUERY_TOO_BROAD` to 413/`SEARCH_TOO_BROAD`,
`INVALID_RESPONSE` and `HTTP_STATUS` to 502/`ARCHIVE_INVALID_RESPONSE`, and
`TIMEOUT` and `CONNECTION` to 503/`ARCHIVE_UNAVAILABLE`. It must not serialize
the exception cause.

- [ ] **Step 4: Extend ArchUnit rules before running the module suite**

Add exact rules mirroring ingest:

```java
@Test
void worklist_application_has_no_production_classes_in_root_package() {
    noClasses().should().resideInAPackage("dev.blackice.worklist.application").check(classes);
}

@Test
void worklist_module_does_not_consume_security_infrastructure() {
    noClasses().that().resideInAPackage("dev.blackice.worklist..")
        .should().dependOnClassesThat().resideInAPackage("dev.blackice.security.infrastructure..")
        .check(classes);
}
```

Document `blackice.worklist.request-timeout` and the GET contract in the backend
README without copying the DICOM Domain Pack.

- [ ] **Step 5: Run the full backend verification**

```bash
mise exec -- mvn test -Dquarkus.http.test-port=8082
mise exec -- mvn package -DskipTests
```

Expected: all backend tests pass and the JVM package builds.

- [ ] **Step 6: Request DICOM correctness review**

Dispatch the project-scoped `dicom-domain-reviewer` in read-only mode with the
spec and the complete backend diff. Resolve every correctness finding, rerun the
focused affected tests, then rerun Step 5. The reviewer must explicitly confirm
QIDO/STOW/WADO roles, UID handling, Patient ID issuer handling, DICOM JSON VRs,
pagination, token propagation, and absence of pixel data.

- [ ] **Step 7: Conditional focused commit**

After explicit commit authorization and the required Graphify workflow:

```bash
git add apps/backend/src/main/java/dev/blackice/worklist/api apps/backend/src/main/java/dev/blackice/worklist/package-info.java apps/backend/src/test/java/dev/blackice/worklist/api apps/backend/src/test/java/dev/blackice/architecture/BackendArchitectureTest.java apps/backend/README.md graphify-out
git commit -m "✨ expõe busca autenticada de estudos"
```

### Phase 1 human gate

Present the backend contract, test output, DICOM review, and the actual QIDO URI
captured by `HttpQidoStudyGatewayTest`. Stop and obtain human approval before any
frontend task.

---

## Phase 2 — Frontend Worklist

### Task 5: Typed cancellable Worklist API client

**Files:**
- Create: `apps/frontend/src/features/worklist/worklist.types.ts`
- Create: `apps/frontend/src/features/worklist/worklist.api.ts`
- Create: `apps/frontend/src/features/worklist/worklist.api.spec.ts`

**Interfaces:**
- Consumes: the Phase 1 `GET /api/studies` contract.
- Produces: `searchStudies(params, signal)`, `WorklistError`, `WorklistFilters`, `StudySummary`, `StudyPage`, and `StudySearchParams`.

- [ ] **Step 1: Write failing URL, success, error, and abort tests**

```ts
it('envia somente filtros preenchidos e paginação', async () => {
  const fetchFn = vi.fn().mockResolvedValue(okResponse(page));
  await searchStudies(
    { filters: { patientName: 'MARIA', patientId: '', modality: 'CT', dateFrom: '', dateTo: '' }, limit: 20, offset: 40 },
    new AbortController().signal,
    fetchFn,
  );
  expect(fetchFn).toHaveBeenCalledWith(
    '/api/studies?patientName=MARIA&modality=CT&limit=20&offset=40',
    { credentials: 'include', signal: expect.any(AbortSignal) },
  );
});

it.each([[400, 'INVALID_SEARCH'], [413, 'SEARCH_TOO_BROAD'], [502, 'ARCHIVE_INVALID_RESPONSE'], [503, 'ARCHIVE_UNAVAILABLE']])(
  'preserva status %s e código seguro %s', async (status, code) => {
    const fetchFn = vi.fn().mockResolvedValue(errorResponse(status, code));
    await expect(searchStudies(emptyParams, undefined, fetchFn))
      .rejects.toMatchObject({ status, code });
  },
);
```

- [ ] **Step 2: Run the focused test and confirm RED**

```bash
mise exec -- pnpm test -- worklist.api.spec.ts
```

Expected: module resolution fails because the client is absent.

- [ ] **Step 3: Add exact TypeScript contracts**

```ts
export interface WorklistFilters {
  patientName: string;
  patientId: string;
  modality: string;
  dateFrom: string;
  dateTo: string;
}

export interface StudySummary {
  studyInstanceUid: string;
  patientName: string | null;
  patientId: string | null;
  patientIdIssuer: string | null;
  studyDate: string | null;
  studyTime: string | null;
  modalities: readonly string[];
  description: string | null;
  seriesCount: number | null;
  instanceCount: number | null;
}

export interface StudyPage {
  items: readonly StudySummary[];
  page: { limit: number; offset: number; hasPrevious: boolean; hasNext: boolean };
}

export interface StudySearchParams {
  filters: WorklistFilters;
  limit: number;
  offset: number;
}

export class WorklistError extends Error {
  constructor(readonly status: number, readonly code: string) {
    super(code);
    this.name = 'WorklistError';
  }
}
```

- [ ] **Step 4: Implement the API client without retries**

```ts
export async function searchStudies(
  params: StudySearchParams,
  signal?: AbortSignal,
  fetchFn: typeof fetch = fetch,
): Promise<StudyPage> {
  const query = new URLSearchParams();
  for (const [key, value] of Object.entries(params.filters)) {
    if (value.trim()) query.set(key, value.trim());
  }
  query.set('limit', String(params.limit));
  query.set('offset', String(params.offset));

  const response = await fetchFn(`/api/studies?${query}`, { credentials: 'include', signal });
  if (!response.ok) {
    const body = await safeError(response);
    throw new WorklistError(response.status, body.code);
  }
  return await response.json() as StudyPage;
}
```

`safeError` returns `UNKNOWN_ERROR` when the body is missing or malformed and
never includes raw response text in the thrown message.

In the test file, define `emptyParams` with empty strings, limit 20, and offset
zero; `page` as an empty `StudyPage`; `okResponse(body)` as a `Response` with
status 200 and JSON content type; and `errorResponse(status, code)` as the safe
`WorklistErrorResponse` JSON shape returned by the backend.

- [ ] **Step 5: Run the API tests and confirm GREEN**

```bash
mise exec -- pnpm test -- worklist.api.spec.ts
```

Expected: URL, response, safe error, network failure, and abort cases pass.

- [ ] **Step 6: Conditional focused commit**

After explicit commit authorization and the required Graphify workflow:

```bash
git add apps/frontend/src/features/worklist/worklist.types.ts apps/frontend/src/features/worklist/worklist.api.ts apps/frontend/src/features/worklist/worklist.api.spec.ts graphify-out
git commit -m "✨ adiciona cliente da worklist"
```

### Task 6: Worklist state machine and stale-response protection

**Files:**
- Create: `apps/frontend/src/features/worklist/useWorklist.ts`
- Create: `apps/frontend/src/features/worklist/useWorklist.spec.ts`

**Interfaces:**
- Consumes: `searchStudies`, `StudyPage`, and `WorklistFilters` from Task 5.
- Produces: `useWorklist(api?)` with read-only state and `loadRecent`, `search`, `clear`, `next`, `previous`, `retry`, and `dispose` actions.

- [ ] **Step 1: Write failing lifecycle and cancellation tests**

```ts
it('carrega estudos recentes com vinte itens e offset zero', async () => {
  const api = vi.fn().mockResolvedValue(page({ offset: 0, hasNext: true }));
  const worklist = useWorklist(api);
  await worklist.loadRecent();
  expect(api).toHaveBeenCalledWith(
    { filters: EMPTY_FILTERS, limit: 20, offset: 0 }, expect.any(AbortSignal));
  expect(worklist.phase.value).toBe('READY');
});

it('aborta a anterior e ignora resposta obsoleta', async () => {
  const first = deferred<StudyPage>();
  const second = deferred<StudyPage>();
  const api = vi.fn().mockReturnValueOnce(first.promise).mockReturnValueOnce(second.promise);
  const worklist = useWorklist(api);
  const firstRun = worklist.search({ ...EMPTY_FILTERS, patientName: 'A' });
  const firstSignal = api.mock.calls[0][1] as AbortSignal;
  const secondRun = worklist.search({ ...EMPTY_FILTERS, patientName: 'B' });
  expect(firstSignal.aborted).toBe(true);
  second.resolve(pageWithPatient('B'));
  await secondRun;
  first.resolve(pageWithPatient('A'));
  await firstRun;
  expect(worklist.items.value[0]?.patientName).toBe('B');
});
```

Define `EMPTY_FILTERS` as the five empty-string fields. Define
`deferred<T>()` to return `{ promise, resolve, reject }`, with `resolve` and
`reject` captured from a new `Promise<T>`. Define `page`, `pageWithPatient`, and
the study fixture with the exact Task 5 TypeScript interfaces.

Also cover empty results, manual retry with the last applied parameters,
`clear()` returning to empty filters and offset zero, next/previous arithmetic,
disabled navigation predicates while loading, safe errors, AbortError silence,
and `dispose()` aborting the active request.

- [ ] **Step 2: Run the composable test and confirm RED**

```bash
mise exec -- pnpm test -- useWorklist.spec.ts
```

Expected: module resolution fails because the composable is absent.

- [ ] **Step 3: Implement the state machine with a request generation**

Use these exact public phases and API type:

```ts
export type WorklistPhase = 'IDLE' | 'LOADING' | 'READY' | 'EMPTY' | 'ERROR';
export type SearchStudies = (params: StudySearchParams, signal?: AbortSignal) => Promise<StudyPage>;
```

The central loader must abort the previous controller and guard every mutation:

```ts
async function load(filters: WorklistFilters, offset: number): Promise<void> {
  activeController?.abort();
  const controller = new AbortController();
  activeController = controller;
  const generation = ++activeGeneration;
  phase.value = 'LOADING';
  errorCode.value = null;
  appliedFilters.value = { ...filters };
  appliedOffset.value = offset;
  try {
    const result = await api({ filters: { ...filters }, limit: PAGE_SIZE, offset }, controller.signal);
    if (generation !== activeGeneration) return;
    items.value = result.items;
    page.value = result.page;
    phase.value = result.items.length === 0 ? 'EMPTY' : 'READY';
  } catch (error) {
    if (generation !== activeGeneration || isAbort(error)) return;
    phase.value = 'ERROR';
    errorCode.value = error instanceof WorklistError ? error.code : 'NETWORK_ERROR';
  } finally {
    if (generation === activeGeneration) activeController = null;
  }
}
```

`next()` only loads when `page.hasNext`; `previous()` uses
`Math.max(0, page.offset - page.limit)`; `retry()` reuses the applied filters and
offset; `dispose()` increments the generation and aborts.

- [ ] **Step 4: Run the composable and API tests**

```bash
mise exec -- pnpm test -- worklist.api.spec.ts useWorklist.spec.ts
```

Expected: both suites pass without unhandled promise rejections.

- [ ] **Step 5: Conditional focused commit**

After explicit commit authorization and the required Graphify workflow:

```bash
git add apps/frontend/src/features/worklist/useWorklist.ts apps/frontend/src/features/worklist/useWorklist.spec.ts graphify-out
git commit -m "✨ gerencia estado paginado da worklist"
```

### Task 7: Accessible responsive Worklist page

**Files:**
- Create: `apps/frontend/src/features/worklist/WorklistFilters.vue`
- Create: `apps/frontend/src/features/worklist/StudyList.vue`
- Create: `apps/frontend/src/features/worklist/StudyPagination.vue`
- Create: `apps/frontend/src/features/worklist/WorklistPage.vue`
- Create: `apps/frontend/src/features/worklist/WorklistPage.spec.ts`
- Modify: `apps/frontend/src/app/router/index.ts`
- Modify: `apps/frontend/src/features/home/HomePage.vue`
- Modify: `apps/frontend/vite.config.ts`
- Modify: `apps/frontend/package.json`
- Modify: `apps/frontend/pnpm-lock.yaml`

**Interfaces:**
- Consumes: the Task 5 types and Task 6 composable.
- Produces: protected `/studies`, a filter form, table/card results, and previous/next controls.

- [ ] **Step 1: Add the Vue component-test harness**

From `apps/frontend/`:

```bash
mise exec -- pnpm add -D @vue/test-utils@2.4.6 jsdom@26.1.0
```

Set the default Vitest environment while retaining the E2E exclusion:

```ts
test: {
  environment: 'jsdom',
  exclude: ['e2e/**'],
},
```

- [ ] **Step 2: Write failing page behavior tests**

Mock `searchStudies`, mount `WorklistPage`, and assert the rendered contract:

```ts
it('carrega recentes ao montar e busca somente no submit', async () => {
  searchStudiesMock.mockResolvedValue(pageWithOneStudy());
  const wrapper = mount(WorklistPage);
  await flushPromises();
  expect(searchStudiesMock).toHaveBeenCalledTimes(1);

  await wrapper.get('[name="patientName"]').setValue('MARIA');
  expect(searchStudiesMock).toHaveBeenCalledTimes(1);
  await wrapper.get('form').trigger('submit');
  await flushPromises();
  expect(searchStudiesMock).toHaveBeenCalledTimes(2);
});

it('mostra campos ausentes como não informado e preserva o uid somente como chave', async () => {
  searchStudiesMock.mockResolvedValue(pageWithMissingOptionals());
  const wrapper = mount(WorklistPage);
  await flushPromises();
  expect(wrapper.text()).toContain('Não informado');
  expect(wrapper.find('[data-testid="open-study"]').exists()).toBe(false);
});
```

At test-module scope, use `vi.mock('@/features/worklist/worklist.api')`, import
the mocked `searchStudies`, and cast it with `vi.mocked(searchStudies)`. Define
`pageWithOneStudy()` and `pageWithMissingOptionals()` as valid `StudyPage`
fixtures so the tests do not use unchecked partial objects.

Add rendered cases for loading status, Archive error with **Tentar novamente**,
empty Archive, no filtered results, clear filters, Enter submit, button disabled
states, and previous/next events.

- [ ] **Step 3: Run the page test and confirm RED**

```bash
mise exec -- pnpm test -- WorklistPage.spec.ts
```

Expected: component imports fail because the page does not exist.

- [ ] **Step 4: Implement the filter form**

`WorklistFilters.vue` uses `<script setup lang="ts">`, a typed `modelValue`, and
typed `update:modelValue`, `search`, and `clear` emits. The modality options are
exactly:

```ts
const MODALITIES = ['', 'CT', 'MR', 'US', 'CR', 'DX', 'MG', 'NM', 'PT', 'XA', 'RF', 'OT'] as const;
```

Use a semantic `<form @submit.prevent="$emit('search')">`, labels associated by
`for`/`id`, date inputs, **Buscar** submit, and **Limpar filtros** button. Input
events update a copied model; clear emits the exact empty filter object.

- [ ] **Step 5: Implement results and pagination components**

`StudyList.vue` receives `readonly StudySummary[]`. Render a semantic table with
the six approved columns and a card list with the same values. Keep both in the
DOM but switch presentation through CSS:

```css
.study-cards { display: none; }
@media (max-width: 720px) {
  .study-table { display: none; }
  .study-cards { display: grid; gap: 1rem; }
}
```

Mark the table container as `data-testid="study-table"`, each table body row as
`data-testid="study-row"`, the card container as `data-testid="study-cards"`,
and each card as `data-testid="study-card"`. These identifiers support layout
and pagination assertions without coupling tests to CSS class names.

Use these pure presentation rules:

```ts
const informed = (value: string | number | null) => value ?? 'Não informado';
const patientIdentifier = (study: StudySummary) =>
  [study.patientId, study.patientIdIssuer].filter(Boolean).join(' · ') || 'Não informado';
const counts = (study: StudySummary) =>
  `${study.seriesCount ?? 'Não informado'} séries · ${study.instanceCount ?? 'Não informado'} instâncias`;
```

`StudyPagination.vue` receives `hasPrevious`, `hasNext`, and `loading`, emits
`previous`/`next`, and disables both buttons while loading.

- [ ] **Step 6: Implement page orchestration and routing**

`WorklistPage.vue` owns only the editable draft and delegates request state to
`useWorklist`. It calls `loadRecent()` in `onMounted` and `dispose()` in
`onUnmounted`. Use `role="status"` for loading/empty states and `role="alert"`
for errors. Distinguish “Nenhum estudo disponível.” from “Nenhum estudo encontrado
para os filtros informados.” based on whether applied filters are empty.

Map stable error codes to Portuguese UI copy: `INVALID_SEARCH` asks the user to
review filters; `SEARCH_TOO_BROAD` asks for narrower filters;
`ARCHIVE_INVALID_RESPONSE` reports an invalid Archive response;
`ARCHIVE_UNAVAILABLE` and `NETWORK_ERROR` report temporary unavailability. Every
error state renders **Tentar novamente**. Show “Mais recentes primeiro” beside
the results heading, use an `aria-live="polite"` status region, and add visible
`:focus-visible` outlines to inputs, links, and buttons.

Register and link the page:

```ts
import WorklistPage from '@/features/worklist/WorklistPage.vue';

{ path: '/studies', name: 'worklist', component: WorklistPage, meta: { protected: true } },
```

```vue
<RouterLink to="/studies">Worklist</RouterLink>
```

- [ ] **Step 7: Run frontend tests and build**

```bash
mise exec -- pnpm test
mise exec -- pnpm build
```

Expected: all Vitest suites pass; `vue-tsc` and Vite build without errors.

- [ ] **Step 8: Conditional focused commit**

After explicit commit authorization and the required Graphify workflow:

```bash
git add apps/frontend/src/features/worklist apps/frontend/src/app/router/index.ts apps/frontend/src/features/home/HomePage.vue apps/frontend/vite.config.ts apps/frontend/package.json apps/frontend/pnpm-lock.yaml graphify-out
git commit -m "✨ implementa interface da worklist"
```

### Phase 2 human gate

Present desktop and 390×844 screenshots, the request sequence proving no search
on each keystroke, Vitest output, and build output. Stop and obtain human approval
before integration work.

---

## Phase 3 — Real-stack validation and operational completion

### Task 8: Repair the pnpm container path and add the STOW-to-QIDO E2E

**Files:**
- Modify: `apps/frontend/package.json`
- Modify: `apps/frontend/mise.toml`
- Modify: `apps/frontend/Dockerfile`
- Modify: `apps/frontend/playwright.config.ts`
- Modify: `apps/frontend/e2e/fixtures/synthetic-dicom.ts`
- Create: `apps/frontend/e2e/worklist.spec.ts`

**Interfaces:**
- Consumes: the complete backend/frontend vertical slice and existing Keycloak login.
- Produces: reproducible pnpm image build and a real DCM4CHEE Worklist scenario.

- [ ] **Step 1: Pin pnpm and repair the Docker build inputs**

Add to `package.json`:

```json
"packageManager": "pnpm@11.22.0"
```

Pin `apps/frontend/mise.toml`:

```toml
[tools]
node = "24"
pnpm = "11.22.0"
```

Replace the build-stage dependency setup with:

```dockerfile
FROM node:24-alpine AS build
WORKDIR /app
RUN npm install --global pnpm@11.22.0
COPY package.json pnpm-lock.yaml ./
RUN pnpm install --frozen-lockfile
COPY . .
RUN pnpm build
```

- [ ] **Step 2: Prove the container regression is fixed**

```bash
docker build -t blackice-frontend-worklist-test .
```

Expected: dependencies install from `pnpm-lock.yaml` and the Vite build completes.
Do not remove images or caches automatically; cleanup is a human operational choice.

- [ ] **Step 3: Extend the synthetic DICOM fixture without breaking callers**

Add an optional metadata contract:

```ts
export interface SyntheticDicomMetadata {
  patientName?: string;
  patientId?: string;
  patientIdIssuer?: string;
  studyDate?: string;
  studyTime?: string;
  modality?: string;
  studyDescription?: string;
}

export function createSyntheticDicom(
  studyUid: string,
  seriesUid: string,
  sopUid: string,
  metadata: SyntheticDicomMetadata = {},
): Buffer
```

Use the current values as defaults, then add tags `(0010,0021)` LO and
`(0008,1030)` LO when issuer/description are present. Continue using only
synthetic ASCII values and caller-provided valid DICOM UIDs.

- [ ] **Step 4: Write the real-stack Worklist test**

Create 21 studies per Playwright project with a project-specific numeric UID
prefix and patient prefix, for example `WORKLISTDESKTOP^CASE01` and
`WORKLISTMOBILE^CASE01`. Import them through `/ingest`, navigate to `/studies`,
search by the common patient prefix, and assert:

```ts
const resultItem = testInfo.project.name === 'chromium-mobile' ? 'study-card' : 'study-row';
await expect(page.getByTestId(resultItem)).toHaveCount(20);
await expect(page.getByRole('button', { name: 'Próxima' })).toBeEnabled();
await page.getByRole('button', { name: 'Próxima' }).click();
await expect(page.getByTestId(resultItem)).toHaveCount(1);
await expect(page.getByRole('button', { name: 'Anterior' })).toBeEnabled();
```

Then filter one study by Patient ID, `OT`, and `2026-08-22` on both date bounds.
Assert issuer, description, series count, and instance count. On the mobile
project assert the card container is visible and the desktop table is hidden;
reverse that assertion on desktop. Capture requests and assert that none contain
`/dcm4chee-arc/` in the browser.

- [ ] **Step 5: Add the suite to scripts and Playwright matching**

```json
"test:e2e:worklist": "playwright test e2e/worklist.spec.ts"
```

Set `testMatch` to include `'worklist.spec.ts'` without changing the pinned
desktop/mobile project definitions.

- [ ] **Step 6: Run fixture regression, unit tests, and build**

```bash
mise exec -- pnpm test
mise exec -- pnpm build
```

Expected: existing ingest fixture callers compile unchanged, all Vitest tests
pass, and the production build succeeds.

- [ ] **Step 7: Conditional focused commit**

After explicit commit authorization and the required Graphify workflow:

```bash
git add apps/frontend/package.json apps/frontend/pnpm-lock.yaml apps/frontend/mise.toml apps/frontend/Dockerfile apps/frontend/playwright.config.ts apps/frontend/e2e/fixtures/synthetic-dicom.ts apps/frontend/e2e/worklist.spec.ts graphify-out
git commit -m "✅ cobre worklist ponta a ponta"
```

### Task 9: Concurrent STOW/QIDO gate, documentation, and full verification

**Files:**
- Modify: `apps/frontend/e2e/worklist.spec.ts`
- Modify: `README.md`
- Modify: `apps/frontend/README.md`
- Modify: `apps/backend/README.md`
- Modify: `docs/architecture/project-structure.md`

**Interfaces:**
- Consumes: the complete Worklist and real Compose stack.
- Produces: concurrency evidence, correct pnpm runbooks, and final MVP #2 verification.

- [ ] **Step 1: Add a concurrent browser scenario**

Use two pages in the same authenticated browser context. Page A starts an import
of 30 project-specific studies, reaches **Processando no Archive…**, and remains
on `/ingest`; Page B opens
`/studies` and runs a bounded QIDO search while Page A is still processing. The
test passes only after both operations complete:

```ts
await expect(page.getByRole('status')).toContainText('Processando no Archive');
const worklistPage = await page.context().newPage();
await worklistPage.goto('/studies');
await expect(worklistPage.getByRole('heading', { name: 'Worklist' })).toBeVisible();
await expect(worklistPage.getByText('Carregando estudos…')).toBeHidden();
await expect(page.getByRole('heading', { name: 'Resultado da importação' }))
  .toBeVisible({ timeout: 120_000 });
```

Record QIDO and STOW completion times in test attachments. Do not assert an
arbitrary millisecond ceiling in CI; the acceptance condition is forward progress
and no request timeout. The `EVO-005` P95 threshold remains a separate evolution
trigger.

- [ ] **Step 2: Start or rebuild the canonical stack**

From `infra/` with the existing local `.env`:

```bash
docker compose -f compose.yml -f dcm4chee/compose.yml -f compose.apps.yml up -d --build
```

Wait for `/`, `/api/me`, and `/api/login` using the existing readiness recipe in
`apps/frontend/README.md`. Do not use `down -v`; patient-like synthetic test data
and volumes are not deleted by this plan.

- [ ] **Step 3: Observe blocking locks during the concurrent test**

While the concurrent Playwright case runs, execute from `infra/`:

```bash
docker compose -f compose.yml -f dcm4chee/compose.yml -f compose.apps.yml exec -T arc-db sh -lc \
  'for sample in $(seq 1 20); do psql -At -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1 -c "SELECT count(*) FROM pg_locks WHERE NOT granted;"; sleep 0.5; done'
```

Expected: all 20 output lines are `0`. Normal granted
`AccessShareLock` rows are allowed; a nonzero waiting count requires capturing
`locktype`/`mode` without query text, stopping the gate, and diagnosing before
proceeding.

- [ ] **Step 4: Run the real E2E in the pinned Playwright image**

From `apps/frontend/`:

```bash
docker run --rm --network host \
  -v "${PWD}:/work" \
  -w /work \
  -e CI=true \
  -e BLACKICE_E2E_URL=http://blackice.localhost \
  mcr.microsoft.com/playwright:v1.62.0-noble \
  npx playwright test e2e/worklist.spec.ts
```

Expected: desktop and mobile projects pass; imported synthetic studies are found;
pagination crosses 20/1; filters work; concurrent STOW and QIDO both complete.

- [ ] **Step 5: Update operational documentation**

Replace frontend `npm ci`, `npm test`, and `npm run` commands with the equivalent
`pnpm install --frozen-lockfile`, `pnpm test`, and `pnpm` commands in the root
README, frontend README, and canonical project structure. Document:

- `/studies` and its four filters;
- `blackice.worklist.request-timeout` default 10 seconds;
- the Worklist E2E command;
- the synthetic-only data rule;
- the lock-observation command and interpretation;
- that `EVO-005` owns future cursor/snapshot/read-projection evaluation.

Do not copy tag semantics out of `docs/domains/dicom/`.

- [ ] **Step 6: Run the complete local verification matrix**

Backend, from `apps/backend/`:

```bash
mise exec -- mvn test -Dquarkus.http.test-port=8082
mise exec -- mvn package -DskipTests
```

Frontend, from `apps/frontend/`:

```bash
mise exec -- pnpm install --frozen-lockfile
mise exec -- pnpm test
mise exec -- pnpm build
```

Repository documentation checks, from the root:

```bash
git diff --check
! rg -n "T[B]D|T[O]DO|implement l[a]ter|fill in d[e]tails" docs/superpowers/plans/2026-08-22-worklist-qido-rs.md docs/superpowers/specs/2026-08-22-worklist-qido-rs-design.md
```

Expected: both builds succeed, every test passes, `git diff --check` is silent,
and the placeholder scan has no matches.

- [ ] **Step 7: Request final DICOM review and present the Phase 3 human gate**

Send the complete implementation diff and E2E evidence to the read-only
`dicom-domain-reviewer`. After approval, present to the human:

- backend/frontend build and test counts;
- desktop/mobile Playwright results;
- captured QIDO URI showing limit 21, offset, order, and minimal include fields;
- evidence of no count request;
- concurrent STOW/QIDO timings and lock samples;
- `EVO-005` backlog entry;
- full `git status --short` with unrelated user changes identified and preserved.

Stop for the final business gate.

- [ ] **Step 8: Conditional documentation commit**

Only after explicit commit authorization, final human approval, and the required
Graphify semantic update/review:

```bash
git add README.md apps/frontend/README.md apps/backend/README.md docs/architecture/project-structure.md docs/architecture/evolution-backlog.md docs/superpowers/specs/2026-08-22-worklist-qido-rs-design.md docs/superpowers/plans/2026-08-22-worklist-qido-rs.md graphify-out
git commit -m "📝 documenta operação da worklist QIDO-RS"
```

If the normal-checkout post-commit hook produces a new graph-only diff, review it
and, only with explicit authorization, create the required focused graph sync
commit instead of folding or discarding it.
