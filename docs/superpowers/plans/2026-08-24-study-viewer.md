# Study Viewer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Entregar o MVP #3 do BlackICE: abrir um estudo da Worklist e visualizar séries DICOM 2D single-frame em um viewport `Stack` do Cornerstone3D, com metadados curados, WADO-RS proxied, ferramentas básicas e medições temporárias.

**Architecture:** O Quarkus cria o módulo `dev.blackice.viewer`: QIDO-RS descobre e classifica a hierarquia, WADO-RS Retrieve Series Metadata alimenta um DTO curado e WADO-RS Retrieve Frames é transmitido por um proxy estreito. A feature Vue `viewer` mantém estado de navegação e UI; uma fronteira imperativa isolada integra Cornerstone3D sem colocar objetos WebGL na reatividade. Browser e API permanecem same-origin sob sessão BFF.

**Tech Stack:** Java 21, Quarkus 3.37.4, dcm4che 5.34.3, JUnit 5, Mockito, ArchUnit, Vue 3.5.39, Vue Router 4.6.4, TypeScript 6.0.2, Vite 8.1.1, Vitest 4.1.10, Playwright 1.62.0, Cornerstone3D 5.8.2.

**Spec:** `docs/superpowers/specs/2026-08-24-study-viewer-design.md`

## Global Constraints

- Leia a spec e `docs/domains/dicom/{semantics,dicomweb}.md` antes de cada tarefa que toque DICOM.
- QIDO-RS descobre/classifica; WADO-RS `/metadata` recupera metadata de renderização; WADO-RS `/frames/1` recupera pixels. Não troque os papéis.
- Preserve exatamente `StudyInstanceUID`, `SeriesInstanceUID` e `SOPInstanceUID`; valide com `UIDUtils.isValid` e nunca gere ou normalize essas identidades.
- `PatientID` nunca é global sem `IssuerOfPatientID`.
- A allowlist inicial contém somente CR Image Storage (`1.2.840.10008.5.1.4.1.1.1`), DX For Presentation (`1.2.840.10008.5.1.4.1.1.1.1`), CT Image Storage (`1.2.840.10008.5.1.4.1.1.2`) e MR Image Storage (`1.2.840.10008.5.1.4.1.1.4`).
- `NumberOfFrames` ausente ou igual a `1` é single-frame; maior que `1` é `MULTI_FRAME`; valor malformado invalida a resposta.
- O proxy usa `Accept: multipart/related; type="application/octet-stream"; transfer-syntax=*` e preserva `Content-Type`, boundary, headers das partes e bytes sem transcodificar.
- Nenhum pixel entra no PostgreSQL, em cache persistente ou em logs. O Archive nunca é exposto ao browser e o token nunca entra no JavaScript.
- Logs e erros não carregam UIDs, identificadores clínicos, imageIds, URLs concretas, payloads ou mensagens de exceção. Use template de rota e TraceID.
- QIDO tem timeout de 10 s; WADO metadata/frame, 60 s; não há retry automático.
- Cornerstone carrega primeiro o frame interativo; prefetch mantém até três frames seguintes e no máximo duas requisições de background da série ativa.
- O viewer completo inicializa com largura `>= 768px` em paisagem ou largura `>= 1024px`; abaixo disso não importa o runtime, não solicita instâncias e não solicita frames.
- Objetos Cornerstone permanecem fora de `reactive()` e de `ref()` profundo. Cleanup é idempotente e remove viewport, ToolGroup, listeners, filas, cache e anotações da página.
- Backend: Javadoc, comentários, nomes de testes e mensagens em inglês. Documentação e UI continuam em PT-BR.
- Use TDD: teste falhando, execução que confirme a falha, implementação mínima e nova execução passando.
- Não edite catálogo ou artefatos gerados manualmente; use `.problem-catalog/`.
- Não atualize Graphify durante os ciclos. Faça uma única atualização semântica final após código, testes, revisões e gates estáveis.
- Não crie commit sem autorização humana explícita. Se autorizado, use escopo seguro e `docs/domains/git/commit-conventions.md`; caso contrário, pare ao fim de cada tarefa e reporte o diff verificado.

---

## Phase 0 — Published client error contract

O tipo cliente é publicado antes das fases de produto porque os tipos gerados
são uma dependência de compilação do frontend. A validação integrada do catálogo
continua fazendo parte do fechamento da Phase 3, como exige a spec.

### Task 1: Publish `CLIENT_DICOM_IMAGE_UNSUPPORTED`

**Files:**
- Modify through tooling: `docs/contracts/problems/catalog.json`
- Regenerate: `docs/contracts/problems/catalog.lock.json`
- Regenerate: `docs/contracts/problems/catalog.md`
- Regenerate: `apps/backend/src/main/java/dev/blackice/shared/api/problem/generated/ProblemType.java`
- Regenerate: `apps/backend/src/main/java/dev/blackice/shared/api/problem/generated/ProblemExtensions.java`
- Regenerate: `apps/frontend/src/shared/api/problems/problem-types.generated.ts`
- Regenerate: `apps/frontend/src/shared/api/problems/problem-extensions.generated.ts`
- Modify: `apps/frontend/src/shared/api/problems/problem-messages.pt-BR.ts`
- Test: `apps/frontend/src/shared/api/problems/problem-messages.pt-BR.spec.ts`

**Interfaces:**
- Consumes: entrada CLIENT aprovada na spec, sem status/title/detail/extensão.
- Produces: `ProblemCode` e `ProblemType.CLIENT_DICOM_IMAGE_UNSUPPORTED` gerados; mensagem PT-BR exaustiva.

- [ ] **Step 1: Run the catalog gate before mutation**

Run:

```bash
cd .problem-catalog
mise exec -- pnpm check
```

Expected: `catálogo, lock e artefatos gerados estão consistentes`.

- [ ] **Step 2: Add the approved entry with the official tool**

Run:

```bash
cd .problem-catalog
mise exec -- pnpm run add -- \
  --code CLIENT_DICOM_IMAGE_UNSUPPORTED \
  --scope CLIENT \
  --description "O browser recebeu uma imagem DICOM válida, mas o loader configurado não conseguiu decodificá-la ou renderizá-la." \
  --retry-policy NEVER \
  --owner frontend
mise exec -- pnpm generate
```

Expected: the first command prints the generated UUIDv5 URN; generation lists the changed generated artifacts. Never copy a UUID into the command.

- [ ] **Step 3: Write the failing exhaustive message test**

Add to `problem-messages.pt-BR.spec.ts`:

```ts
expect(problemMessage('CLIENT_DICOM_IMAGE_UNSUPPORTED')).toBe(
  'Esta imagem DICOM não é compatível com este visualizador. Selecione outra série.',
);
```

Run:

```bash
cd apps/frontend
mise exec -- pnpm test -- src/shared/api/problems/problem-messages.pt-BR.spec.ts
```

Expected: FAIL because the generated code has no message in the exhaustive map.

- [ ] **Step 4: Add the safe PT-BR message**

Add to `PROBLEM_MESSAGES`:

```ts
CLIENT_DICOM_IMAGE_UNSUPPORTED:
  'Esta imagem DICOM não é compatível com este visualizador. Selecione outra série.',
```

- [ ] **Step 5: Validate generated artifacts and frontend types**

Run:

```bash
cd .problem-catalog
mise exec -- pnpm test
mise exec -- pnpm check
cd ../apps/frontend
mise exec -- pnpm test -- src/shared/api/problems/problem-messages.pt-BR.spec.ts
mise exec -- pnpm build
```

Expected: all commands exit 0. Review the generated URN and verify CLIENT has no `httpStatus`, `title` or `detail`.

- [ ] **Step 6: Stop at the task review boundary**

Review only catalog/generated/message files. Do not commit unless the human has explicitly authorized commits.

---

## Phase 1 — Backend QIDO/WADO

### Task 2: Build the viewer application contracts and pure rules

**Files:**
- Create: `apps/backend/src/main/java/dev/blackice/viewer/package-info.java`
- Create: `apps/backend/src/main/java/dev/blackice/viewer/application/input/ViewerStudyRef.java`
- Create: `apps/backend/src/main/java/dev/blackice/viewer/application/input/ViewerSeriesRef.java`
- Create: `apps/backend/src/main/java/dev/blackice/viewer/application/input/ViewerInstanceRef.java`
- Create: `apps/backend/src/main/java/dev/blackice/viewer/application/result/StudyMetadata.java`
- Create: `apps/backend/src/main/java/dev/blackice/viewer/application/result/SeriesMetadata.java`
- Create: `apps/backend/src/main/java/dev/blackice/viewer/application/result/InstanceIdentityMetadata.java`
- Create: `apps/backend/src/main/java/dev/blackice/viewer/application/result/ViewerSeriesSummary.java`
- Create: `apps/backend/src/main/java/dev/blackice/viewer/application/result/StudyViewerSummary.java`
- Create: `apps/backend/src/main/java/dev/blackice/viewer/application/result/SeriesAvailability.java`
- Create: `apps/backend/src/main/java/dev/blackice/viewer/application/result/UnsupportedReason.java`
- Create: `apps/backend/src/main/java/dev/blackice/viewer/application/result/SeriesSupport.java`
- Create: `apps/backend/src/main/java/dev/blackice/viewer/application/exception/InvalidViewerRequestException.java`
- Create: `apps/backend/src/main/java/dev/blackice/viewer/application/exception/InvalidArchiveMetadataException.java`
- Create: `apps/backend/src/main/java/dev/blackice/viewer/application/port/StudyHierarchyGateway.java`
- Create: `apps/backend/src/main/java/dev/blackice/viewer/application/usecase/SeriesSupportClassifier.java`
- Create: `apps/backend/src/main/java/dev/blackice/viewer/application/usecase/GetStudyViewerUseCase.java`
- Create: matching `package-info.java` files only for the concrete packages above.
- Test: `apps/backend/src/test/java/dev/blackice/viewer/application/input/ViewerRefsTest.java`
- Test: `apps/backend/src/test/java/dev/blackice/viewer/application/usecase/SeriesSupportClassifierTest.java`
- Test: `apps/backend/src/test/java/dev/blackice/viewer/application/usecase/GetStudyViewerUseCaseTest.java`

**Interfaces:**
- Consumes: exact DICOM UIDs and Archive metadata supplied by a port.
- Produces:

```java
public interface StudyHierarchyGateway {
    StudyMetadata findStudy(ViewerStudyRef study, String accessToken);
    List<SeriesMetadata> findSeries(ViewerStudyRef study, String accessToken);
    List<InstanceIdentityMetadata> findInstances(ViewerStudyRef study, String accessToken);
}

public record InstanceIdentityMetadata(
    String seriesInstanceUid,
    String sopInstanceUid,
    String sopClassUid,
    Integer numberOfFrames
) {}

public record SeriesSupport(
    SeriesAvailability availability,
    UnsupportedReason unsupportedReason
) {}

public StudyViewerSummary execute(ViewerStudyRef study, String accessToken);
```

- [ ] **Step 1: Write failing UID validation tests**

```java
@ParameterizedTest
@ValueSource(strings = {"", "1..2", "1.02.3", "abc", "1.2."})
void rejects_invalid_study_uids_without_normalizing_them(String uid) {
    assertThrows(InvalidViewerRequestException.class, () -> new ViewerStudyRef(uid));
}

@Test
void preserves_valid_uid_exactly() {
    assertEquals("1.2.840.10008.1.2", new ViewerStudyRef("1.2.840.10008.1.2").studyInstanceUid());
}
```

Run:

```bash
cd apps/backend
mise exec -- mvn -Dtest=ViewerRefsTest test -Dquarkus.http.test-port=8082
```

Expected: FAIL because the reference records do not exist.

- [ ] **Step 2: Implement immutable validated references**

Use `UIDUtils.isValid` in compact constructors and preserve the original string:

```java
public record ViewerSeriesRef(String studyInstanceUid, String seriesInstanceUid) {
    public ViewerSeriesRef {
        if (!UIDUtils.isValid(studyInstanceUid) || !UIDUtils.isValid(seriesInstanceUid)) {
            throw new InvalidViewerRequestException();
        }
    }
}
```

Implement equivalent study and instance references. Messages stay generic and never echo a UID.

```java
public final class InvalidViewerRequestException extends RuntimeException {
    public InvalidViewerRequestException() {
        super("INVALID_VIEWER_REQUEST");
    }
}
```

- [ ] **Step 3: Write failing classification tests**

Cover exact cases:

```java
assertEquals(SeriesAvailability.SUPPORTED,
    classifier.classify(List.of(ctSingleFrame())).availability());
assertEquals(UnsupportedReason.MULTI_FRAME,
    classifier.classify(List.of(ctWithFrames(2))).unsupportedReason());
assertEquals(UnsupportedReason.NON_IMAGE_OBJECT,
    classifier.classify(List.of(srInstance())).unsupportedReason());
assertEquals(UnsupportedReason.IMAGE_SOP_CLASS_UNSUPPORTED,
    classifier.classify(List.of(secondaryCapture())).unsupportedReason());
assertThrows(InvalidArchiveMetadataException.class,
    () -> classifier.classify(List.of(ctWithFrames(0))));
```

Also test all four allowlisted UIDs, mixed SOP Classes, empty series, missing identity, and order independence.

- [ ] **Step 4: Implement the classifier and result records**

Use an immutable allowlist:

```java
private static final Set<String> SUPPORTED_IMAGE_SOP_CLASSES = Set.of(
    "1.2.840.10008.5.1.4.1.1.1",
    "1.2.840.10008.5.1.4.1.1.1.1",
    "1.2.840.10008.5.1.4.1.1.2",
    "1.2.840.10008.5.1.4.1.1.4"
);
```

Classify a series only after seeing every instance. Never branch on `Modality`.
Invalid metadata and an empty series throw `InvalidArchiveMetadataException`.
For valid metadata, precedence is deterministic: any multi-frame instance yields
`MULTI_FRAME`; otherwise a known SR/SEG/PR/encapsulated-document SOP Class yields
`NON_IMAGE_OBJECT`; otherwise any UID outside the image allowlist yields
`IMAGE_SOP_CLASS_UNSUPPORTED`; only an entirely allowlisted series is
`SUPPORTED`. Detect the known non-image families by SOP Class UID, never by
modality.

- [ ] **Step 5: Write failing orchestration and ordering tests**

Mock the port and assert:

```java
StudyViewerSummary result = useCase.execute(new ViewerStudyRef(STUDY_UID), "token");
assertEquals(List.of("1.2.3.1", "1.2.3.2", "1.2.3.9"),
    result.series().stream().map(ViewerSeriesSummary::seriesInstanceUid).toList());
assertEquals(SeriesAvailability.SUPPORTED, result.series().getFirst().availability());
```

Cover numeric `SeriesNumber`, nulls-last, UID tie-break, series with zero classification instances, unknown series UID in the instance scan, and a known `instanceCount` inconsistent with the scan.

- [ ] **Step 6: Implement `GetStudyViewerUseCase` minimally**

Group `InstanceIdentityMetadata` by exact `SeriesInstanceUID`, reject identities outside the returned hierarchy, classify each group, and build immutable results. Sort with:

```java
Comparator.comparing(ViewerSeriesSummary::seriesNumber,
        Comparator.nullsLast(Integer::compareTo))
    .thenComparing(ViewerSeriesSummary::seriesInstanceUid);
```

- [ ] **Step 7: Run focused and architecture tests**

```bash
cd apps/backend
mise exec -- mvn -Dtest='ViewerRefsTest,SeriesSupportClassifierTest,GetStudyViewerUseCaseTest,BackendArchitectureTest' test -Dquarkus.http.test-port=8082
```

Expected: PASS. Stop for review; no commit without explicit authorization.

### Task 3: Implement paginated QIDO hierarchy discovery

**Files:**
- Create: `apps/backend/src/main/java/dev/blackice/viewer/application/exception/ArchiveViewerException.java`
- Create: `apps/backend/src/main/java/dev/blackice/viewer/infrastructure/dicomweb/QidoViewerQueryBuilder.java`
- Create: `apps/backend/src/main/java/dev/blackice/viewer/infrastructure/dicomweb/QidoViewerResponseParser.java`
- Create: `apps/backend/src/main/java/dev/blackice/viewer/infrastructure/dicomweb/HttpStudyHierarchyGateway.java`
- Create: package docs for the new concrete packages.
- Modify: `apps/backend/src/main/resources/application.properties`
- Test: `apps/backend/src/test/java/dev/blackice/viewer/infrastructure/dicomweb/QidoViewerQueryBuilderTest.java`
- Test: `apps/backend/src/test/java/dev/blackice/viewer/infrastructure/dicomweb/QidoViewerResponseParserTest.java`
- Test: `apps/backend/src/test/java/dev/blackice/viewer/infrastructure/dicomweb/HttpStudyHierarchyGatewayTest.java`

**Interfaces:**
- Consumes: `StudyHierarchyGateway` from Task 2.
- Produces: authenticated QIDO calls for one study, its series, and all instance identities; `ArchiveViewerException.Reason` values `AUTHENTICATION`, `ACCESS_DENIED`, `NOT_FOUND`, `UNAVAILABLE`, `TIMEOUT`, `CONNECTION`, `INVALID_RESPONSE`.

- [ ] **Step 1: Write failing query-builder tests**

Assert exact upstream shapes:

```text
/studies?StudyInstanceUID={uid}&limit=2&includefield=...
/studies/{uid}/series?includefield=...
/studies/{uid}/instances?limit=500&offset=0&orderby=SeriesInstanceUID,SOPInstanceUID&includefield=...
```

The instance query includes `SeriesInstanceUID`, `SOPInstanceUID`, `SOPClassUID` and `NumberOfFrames`. Assert percent encoding, no `includefield=all`, and no clinical query in exception text.

- [ ] **Step 2: Implement the URI builder**

Create separate methods:

```java
URI study(String baseUrl, ViewerStudyRef ref);
URI series(String baseUrl, ViewerStudyRef ref);
URI instances(String baseUrl, ViewerStudyRef ref, int limit, int offset);
```

Use a page size constant/config of 500. Do not concatenate unvalidated user input before constructing the reference record.

- [ ] **Step 3: Write failing parser fixtures**

Use DICOM JSON fixtures for PN, UI, IS and optional values. Assert required UID preservation, `NumberOfFrames` absent/1/>1/malformed, duplicate SOP UID rejection, unknown series rejection at the use case, and no body fragment in thrown messages.

- [ ] **Step 4: Implement the DICOM JSON parser**

Use Jackson and explicit tag constants. Required UID helper:

```java
private String requiredUid(JsonNode dataset, String tag) {
    String value = firstString(dataset, tag);
    if (value == null || !UIDUtils.isValid(value)) throw invalid();
    return value;
}
```

Do not share code with Worklist unless extraction leaves both parsers simpler and all existing Worklist tests unchanged.

- [ ] **Step 5: Write failing HTTP gateway tests**

Using `HttpServer`, assert bearer token, `traceparent`, `Accept: application/dicom+json`, timeouts, 401/403/404/5xx mapping, invalid media type, and pagination `offset=0,500,1000` until a short page. Assert duplicate `SOPInstanceUID` across pages is invalid.

- [ ] **Step 6: Implement the gateway and config**

Add:

```properties
blackice.viewer.qido-request-timeout=10S
blackice.viewer.classification-page-size=500
```

Use `HttpClient`, `W3cTraceContextInjector`, `HttpResponse.BodyHandlers.ofString(UTF_8)` and no generic catch. A study query returning zero items is `NOT_FOUND`; more than one exact UID match is `INVALID_RESPONSE`.

- [ ] **Step 7: Run focused backend tests**

```bash
cd apps/backend
mise exec -- mvn -Dtest='QidoViewerQueryBuilderTest,QidoViewerResponseParserTest,HttpStudyHierarchyGatewayTest,GetStudyViewerUseCaseTest' test -Dquarkus.http.test-port=8082
```

Expected: PASS. Stop for review; no commit without explicit authorization.

### Task 4: Retrieve and curate active-series metadata with WADO-RS

**Files:**
- Create: `apps/backend/src/main/java/dev/blackice/viewer/application/result/ViewerInstance.java`
- Create: `apps/backend/src/main/java/dev/blackice/viewer/application/result/ViewerSeriesInstances.java`
- Create: `apps/backend/src/main/java/dev/blackice/viewer/application/port/SeriesMetadataGateway.java`
- Create: `apps/backend/src/main/java/dev/blackice/viewer/application/usecase/SpatialInstanceOrder.java`
- Create: `apps/backend/src/main/java/dev/blackice/viewer/application/usecase/GetSeriesInstancesUseCase.java`
- Create: `apps/backend/src/main/java/dev/blackice/viewer/infrastructure/dicomweb/WadoSeriesMetadataParser.java`
- Create: `apps/backend/src/main/java/dev/blackice/viewer/infrastructure/dicomweb/HttpSeriesMetadataGateway.java`
- Test: `apps/backend/src/test/java/dev/blackice/viewer/infrastructure/dicomweb/WadoSeriesMetadataParserTest.java`
- Test: `apps/backend/src/test/java/dev/blackice/viewer/application/usecase/SpatialInstanceOrderTest.java`
- Test: `apps/backend/src/test/java/dev/blackice/viewer/application/usecase/GetSeriesInstancesUseCaseTest.java`
- Test: `apps/backend/src/test/java/dev/blackice/viewer/infrastructure/dicomweb/HttpSeriesMetadataGatewayTest.java`

**Interfaces:**
- Consumes:

```java
public interface SeriesMetadataGateway {
    List<ViewerInstance> retrieve(ViewerSeriesRef series, String accessToken);
}
```

- Produces:

```java
public ViewerSeriesInstances execute(ViewerSeriesRef series, String accessToken);
```

with every field from the spec DTO, arrays immutable, and exact UIDs.

- [ ] **Step 1: Write failing WADO metadata parser tests**

Fixtures must cover CT monochrome metadata, multivalued window values, optional `PixelSpacing`, conditional `PlanarConfiguration`, missing required Image Pixel attributes, `BulkDataURI` ignored, mismatched study/series UID, duplicate SOP UID, multi-frame, and non-allowlisted SOP Class.

Key assertion:

```java
ViewerInstance image = parser.parse(body, ref).getFirst();
assertArrayEquals(new double[]{0.5, 0.5}, image.pixelSpacing());
assertEquals(List.of(40.0), image.windowCenter());
```

- [ ] **Step 2: Implement the curated parser**

Read `StudyInstanceUID`, `SeriesInstanceUID`, `SOPInstanceUID`, `SOPClassUID`, `InstanceNumber`, Image Pixel, Image Plane, VOI and Modality LUT attributes. Return `null` only for conditional/optional values. Do not emit raw DICOM JSON or `BulkDataURI`.

- [ ] **Step 3: Write failing spatial-order tests**

Cover finite vectors, normalization, orthogonality, `1e-4` orientation tolerance, increasing projection, equal-position tie-break, NaN/infinity, missing geometry and fallback ordering.

```java
assertEquals(List.of("1.2.3.1", "1.2.3.2"),
    order.sort(images).stream().map(ViewerInstance::sopInstanceUid).toList());
```

- [ ] **Step 4: Implement spatial ordering and use case**

Normalize row/column vectors, reject near-zero norms, require `abs(dot(row,col)) <= 1e-4`, use the first valid cross product as canonical normal, and sort by projected position. If any instance fails geometry validation, use the complete fallback `InstanceNumber` nulls-last then SOP UID.

- [ ] **Step 5: Write failing WADO metadata gateway tests**

Assert exactly one authenticated request:

```http
GET /studies/{study}/series/{series}/metadata
Accept: application/dicom+json
```

Cover 401/403/404/5xx, timeout, connection, wrong media type, malformed JSON and unexpected runtime propagation.

- [ ] **Step 6: Implement `HttpSeriesMetadataGateway`**

Use `blackice.dicomweb.request-timeout=60S`, bearer identity and trace context. Parse only 2xx `application/dicom+json`; never log the concrete URI.

- [ ] **Step 7: Run focused tests**

```bash
cd apps/backend
mise exec -- mvn -Dtest='WadoSeriesMetadataParserTest,SpatialInstanceOrderTest,GetSeriesInstancesUseCaseTest,HttpSeriesMetadataGatewayTest' test -Dquarkus.http.test-port=8082
```

Expected: PASS. Stop for review; no commit without explicit authorization.

### Task 5: Expose the curated viewer metadata API

**Files:**
- Create: `apps/backend/src/main/java/dev/blackice/viewer/api/ViewerResource.java`
- Create: `apps/backend/src/main/java/dev/blackice/viewer/api/ViewerExceptionMappers.java`
- Create: `apps/backend/src/main/java/dev/blackice/viewer/api/package-info.java`
- Modify: `apps/backend/src/test/java/dev/blackice/architecture/BackendArchitectureTest.java`
- Modify: `apps/backend/src/main/resources/application.properties`
- Test: `apps/backend/src/test/java/dev/blackice/viewer/api/ViewerResourceTest.java`

**Interfaces:**
- Consumes: `GetStudyViewerUseCase`, `GetSeriesInstancesUseCase`, `AccessTokenProvider`.
- Produces:

```http
GET /api/studies/{studyUid}
GET /api/studies/{studyUid}/series/{seriesUid}/instances
```

- [ ] **Step 1: Write failing HTTP contract tests**

Use `@QuarkusTest`, `@InjectMock` and `@TestSecurity`. Assert exact JSON fields from the spec, 401, 403, no CSRF requirement, `X-Trace-ID`, invalid UID 400, not found 404, archive invalid 502, unavailable 503, unexpected 500 and no UID/secret in response or captured failure logs.

```java
given().when().get("/api/studies/not-a-uid")
    .then().statusCode(400)
    .body("code", equalTo("API_REQUEST_INVALID"));
```

- [ ] **Step 2: Implement `ViewerResource`**

```java
@Path("/api/studies")
@RolesAllowed("auth")
public class ViewerResource {
    @GET @Path("/{studyUid}") @Produces(MediaType.APPLICATION_JSON)
    public StudyViewerSummary study(@PathParam("studyUid") String studyUid) { ... }

    @GET @Path("/{studyUid}/series/{seriesUid}/instances")
    @Produces(MediaType.APPLICATION_JSON)
    public ViewerSeriesInstances instances(...) { ... }
}
```

Construct validated reference records before calling use cases. Log only route template, duration and non-clinical counts.

- [ ] **Step 3: Implement exception mapping**

Map `InvalidViewerRequestException` from path references to `API_REQUEST_INVALID`; Archive reasons to existing catalog types exactly as specified. Do not catch unexpected runtime failures.

- [ ] **Step 4: Lock architecture and access-log safety**

Add ArchUnit rules equivalent to Worklist for `viewer` and explicitly keep raw HTTP access logs disabled:

```properties
quarkus.http.access-log.enabled=false
```

Application logs use `/api/studies/{studyUid}` and `/api/studies/{studyUid}/series/{seriesUid}/instances`, never the concrete request URI.

- [ ] **Step 5: Run resource and architecture tests**

```bash
cd apps/backend
mise exec -- mvn -Dtest='ViewerResourceTest,BackendArchitectureTest' test -Dquarkus.http.test-port=8082
```

Expected: PASS. Stop for review; no commit without explicit authorization.

### Task 6: Stream the narrow WADO frame proxy

**Files:**
- Create: `apps/backend/src/main/java/dev/blackice/viewer/application/result/FrameStream.java`
- Create: `apps/backend/src/main/java/dev/blackice/viewer/application/port/DicomFrameGateway.java`
- Create: `apps/backend/src/main/java/dev/blackice/viewer/application/usecase/RetrieveFrameUseCase.java`
- Create: `apps/backend/src/main/java/dev/blackice/viewer/infrastructure/dicomweb/HttpDicomFrameGateway.java`
- Create: `apps/backend/src/main/java/dev/blackice/viewer/api/WadoFrameResource.java`
- Test: `apps/backend/src/test/java/dev/blackice/viewer/application/usecase/RetrieveFrameUseCaseTest.java`
- Test: `apps/backend/src/test/java/dev/blackice/viewer/infrastructure/dicomweb/HttpDicomFrameGatewayTest.java`
- Test: `apps/backend/src/test/java/dev/blackice/viewer/api/WadoFrameResourceTest.java`

**Interfaces:**
- Consumes:

```java
public interface DicomFrameGateway {
    FrameStream retrieveFirstFrame(ViewerInstanceRef instance, String accessToken);
}

public record FrameStream(String contentType, InputStream body) implements AutoCloseable { ... }
```

- Produces: streamed `multipart/related` response at the exact `/api/dicomweb/.../frames/1` route.

- [ ] **Step 1: Write failing upstream negotiation tests**

With `HttpServer`, capture and assert:

```java
assertEquals("multipart/related; type=\"application/octet-stream\"; transfer-syntax=*",
    requestHeaders.getFirst("Accept"));
assertEquals("Bearer user-token", requestHeaders.getFirst("Authorization"));
```

Return a multipart fixture with boundary and per-part `Content-Type` containing a Transfer Syntax. Assert the `FrameStream` exposes the complete outer header and exact bytes.

- [ ] **Step 2: Implement `HttpDicomFrameGateway`**

Use `HttpResponse.BodyHandlers.ofInputStream()`. Validate 2xx outer `Content-Type` with `multipart/related`, `type=application/octet-stream` and non-empty boundary without consuming/rebuilding the body. Map failures before returning the stream; close body on validation failure.

- [ ] **Step 3: Write failing proxy resource tests**

Assert authentication/role, invalid UIDs, `Cache-Control: private, no-store`, `X-Trace-ID`, byte equality, complete Content-Type, and that a body throwing after the first chunk is not replaced by Problem Details.

- [ ] **Step 4: Implement the streaming resource**

```java
StreamingOutput output = destination -> {
    try (FrameStream frame = stream) {
        frame.body().transferTo(destination);
    }
};
return Response.ok(output)
    .type(stream.contentType())
    .header("Cache-Control", "private, no-store")
    .build();
```

The resource validates the exact three-UID hierarchy and never logs the concrete route.

- [ ] **Step 5: Run the complete backend suite**

```bash
cd apps/backend
mise exec -- mvn test -Dquarkus.http.test-port=8082
```

Expected: all tests pass with zero failures/errors.

### Gate 1 — Backend DICOM/human review

- [ ] Dispatch the read-only `dicom-domain-reviewer` over Tasks 2–6.
- [ ] Show the human the curated JSON, QIDO pagination, WADO metadata request, frame negotiation and streaming tests.
- [ ] Stop until the human approves Phase 1. Do not begin frontend work on an unapproved DICOM contract.

---

## Phase 2 — Frontend Cornerstone and UX

### Task 7: Preserve Worklist navigation and add the explicit open action

**Files:**
- Create: `apps/frontend/src/features/worklist/worklist-navigation.ts`
- Create: `apps/frontend/src/features/worklist/worklist-navigation.spec.ts`
- Modify: `apps/frontend/src/features/worklist/useWorklist.ts`
- Modify: `apps/frontend/src/features/worklist/useWorklist.spec.ts`
- Modify: `apps/frontend/src/features/worklist/StudyList.vue`
- Create: `apps/frontend/src/features/worklist/StudyList.spec.ts`
- Modify: `apps/frontend/src/features/worklist/WorklistPage.vue`
- Modify: `apps/frontend/src/features/worklist/WorklistPage.spec.ts`

**Interfaces:**
- Produces:

```ts
export interface WorklistSnapshot {
  readonly key: string;
  readonly filters: WorklistFilters;
  readonly page: StudyPage;
}
export function parseWorklistQuery(query: LocationQuery): StudySearchParams;
export function canonicalWorklistQuery(params: StudySearchParams): LocationQueryRaw;
export function saveWorklistSnapshot(snapshot: WorklistSnapshot): void;
export function restoreWorklistSnapshot(key: string): WorklistSnapshot | null;
```

- [ ] **Step 1: Write failing URL/cache tests**

Test trimming, empty omission, ISO fields, modality, bounded integer offset, canonical key stability, one-entry replacement and exact-key restore. Invalid URL values fall back to empty filters/offset 0 rather than reaching the API.

- [ ] **Step 2: Implement the one-page session cache**

Keep one module-local variable, never `localStorage`/`sessionStorage`:

```ts
let latestSnapshot: WorklistSnapshot | null = null;
export const restoreWorklistSnapshot = (key: string) =>
  latestSnapshot?.key === key ? structuredClone(latestSnapshot) : null;
```

- [ ] **Step 3: Write failing component/composable tests**

Assert **Abrir estudo** exists in desktop row and mobile card, emits the exact UID without rendering it as text, URL changes after successful search/page, matching cached results restore without API call, and mismatched/deep-link URL fetches normally.

- [ ] **Step 4: Implement Worklist integration**

`StudyList` emits `open(studyInstanceUid)`. `WorklistPage` uses `router.push({ name: 'viewer', params: { studyUid } })`, synchronizes canonical query with `router.replace`, saves completed pages, and hydrates `useWorklist` from a matching snapshot.

- [ ] **Step 5: Run Worklist tests and build**

```bash
cd apps/frontend
mise exec -- pnpm test -- src/features/worklist
mise exec -- pnpm build
```

Expected: PASS. The named route may be registered in Task 10; TypeScript still compiles because the name is runtime data.

### Task 8: Implement viewer API types and cancellable state

**Files:**
- Create: `apps/frontend/src/features/viewer/viewer.types.ts`
- Create: `apps/frontend/src/features/viewer/viewer.api.ts`
- Create: `apps/frontend/src/features/viewer/viewer.api.spec.ts`
- Create: `apps/frontend/src/features/viewer/useStudyViewer.ts`
- Create: `apps/frontend/src/features/viewer/useStudyViewer.spec.ts`

**Interfaces:**
- Produces:

```ts
export type ViewerPhase = 'IDLE' | 'LOADING_STUDY' | 'READY' | 'LOADING_SERIES' | 'ERROR';
export type ViewerTool = 'WINDOW_LEVEL' | 'ZOOM' | 'PAN' | 'STACK_SCROLL' | 'LENGTH';

export interface StudyViewerController {
  loadStudy(studyUid: string): Promise<void>;
  activateSeries(seriesUid?: string): Promise<void>;
  deactivateSeries(): void;
  selectSeries(seriesUid: string, activate: boolean): Promise<void>;
  dispose(): void;
}
```

- [ ] **Step 1: Write failing API contract tests**

Assert same-origin URLs, `credentials: 'include'`, signal forwarding, exact DTO parsing, API Problem mapping, network mapping, malformed JSON to `CLIENT_RESPONSE_INVALID`, and abort passthrough.

```ts
expect(fetchFn).toHaveBeenCalledWith(
  `/api/studies/${STUDY_UID}/series/${SERIES_UID}/instances`,
  { credentials: 'include', signal },
);
```

- [ ] **Step 2: Implement strict transport clients**

Create `fetchStudyViewer` and `fetchSeriesInstances`. Validate enums and required strings/numbers after JSON parsing; do not trust `as` casting alone. Never include a UID in an `Error.message`.

- [ ] **Step 3: Write failing state-machine tests**

Cover first supported selection, all unsupported, unsupported not selectable, activation lazy, mobile `loadStudy` without `activateSeries`, abort/generation guard, series switch, stale failure ignored, manual retry policy, deactivation and dispose.

- [ ] **Step 4: Implement the composable**

Keep summary loading separate from active-series metadata. `loadStudy` selects but does not fetch instances. `activateSeries` performs the lazy call; `deactivateSeries` aborts it and clears renderable instances without clearing the selected UID.

- [ ] **Step 5: Run focused tests**

```bash
cd apps/frontend
mise exec -- pnpm test -- src/features/viewer/viewer.api.spec.ts src/features/viewer/useStudyViewer.spec.ts
mise exec -- pnpm build
```

Expected: PASS.

### Task 9: Build the responsive viewer shell

**Files:**
- Create: `apps/frontend/src/features/viewer/StudyHeader.vue`
- Create: `apps/frontend/src/features/viewer/StudyHeader.spec.ts`
- Create: `apps/frontend/src/features/viewer/SeriesRail.vue`
- Create: `apps/frontend/src/features/viewer/SeriesRail.spec.ts`
- Create: `apps/frontend/src/features/viewer/ViewerToolbar.vue`
- Create: `apps/frontend/src/features/viewer/ViewerToolbar.spec.ts`
- Create: `apps/frontend/src/features/viewer/useViewerCapability.ts`
- Create: `apps/frontend/src/features/viewer/useViewerCapability.spec.ts`

**Interfaces:**
- Produces: accessible presentation components and `canRenderViewer: Readonly<Ref<boolean>>` from the exact media gate.

- [ ] **Step 1: Write failing capability tests**

Stub `matchMedia` and assert true for 768×landscape and any width >=1024; false for 767×landscape and portrait widths below 1024. Assert listener removal on dispose.

- [ ] **Step 2: Implement capability detection**

Use one query equivalent to:

```ts
'(min-width: 1024px), (min-width: 768px) and (orientation: landscape)'
```

Expose a disposer and never inspect user-agent.

- [ ] **Step 3: Write failing component tests**

Assert the header never renders raw UID; rail shows every series, disables unsupported entries with PT-BR reason, supports collapse and keyboard focus; toolbar emits only the five tools plus reset and marks the active tool with `aria-pressed`.

- [ ] **Step 4: Implement focused components**

Use `<script setup lang="ts">`, typed props/emits, buttons for actions and CSS scoped to the approved left-rail/single-viewport layout. No thumbnails.

- [ ] **Step 5: Run component tests**

```bash
cd apps/frontend
mise exec -- pnpm test -- src/features/viewer/StudyHeader.spec.ts src/features/viewer/SeriesRail.spec.ts src/features/viewer/ViewerToolbar.spec.ts src/features/viewer/useViewerCapability.spec.ts
```

Expected: PASS.

### Task 10: Integrate Cornerstone behind an imperative runtime

**Files:**
- Modify: `apps/frontend/package.json`
- Modify: `apps/frontend/pnpm-lock.yaml`
- Create: `apps/frontend/src/features/viewer/cornerstone/cornerstone-init.ts`
- Create: `apps/frontend/src/features/viewer/cornerstone/viewer-runtime.ts`
- Create: `apps/frontend/src/features/viewer/cornerstone/viewer-runtime.spec.ts`
- Create: `apps/frontend/src/features/viewer/cornerstone/viewer-metadata.ts`
- Create: `apps/frontend/src/features/viewer/cornerstone/viewer-metadata.spec.ts`
- Create: `apps/frontend/src/features/viewer/DicomViewport.vue`
- Create: `apps/frontend/src/features/viewer/DicomViewport.spec.ts`

**Interfaces:**
- Produces:

```ts
export interface ViewerRuntime {
  setSeries(series: ViewerSeriesInstances): Promise<void>;
  setTool(tool: ViewerTool): void;
  reset(): void;
  dispose(): void;
}

export function buildImageId(studyUid: string, seriesUid: string, sopUid: string): string;
export async function createViewerRuntime(
  element: HTMLDivElement,
  onFailure: (code: ProblemCode) => void,
): Promise<ViewerRuntime>;
```

- [ ] **Step 1: Install one compatible Cornerstone line**

Run:

```bash
cd apps/frontend
mise exec -- pnpm add --save-exact \
  @cornerstonejs/core@5.8.2 \
  @cornerstonejs/tools@5.8.2 \
  @cornerstonejs/dicom-image-loader@5.8.2 \
  @cornerstonejs/metadata@5.8.2 \
  dicom-parser@1.8.21
```

Then run `mise exec -- pnpm build` and resolve only peer errors from this pinned line; do not mix Cornerstone majors.

- [ ] **Step 2: Write failing imageId and metadata tests**

```ts
expect(buildImageId(STUDY_UID, SERIES_UID, SOP_UID)).toBe(
  `wadors:/api/dicomweb/studies/${STUDY_UID}/series/${SERIES_UID}/instances/${SOP_UID}/frames/1`,
);
```

Assert `PixelSpacing` maps to millimetres; absence maps to unit computational spacing with `hasPixelSpacing: false`/`usingDefaultValues: true`; Image Pixel, VOI, Modality LUT and Image Plane fields remain exact.

- [ ] **Step 3: Implement idempotent initialization and metadata adapter**

Use a module-level promise:

```ts
let initialization: Promise<void> | null = null;
export function initCornerstoneOnce(): Promise<void> {
  return initialization ??= initializeAll();
}
```

Register core, tools and `wadors` once. Sanitize all loader errors to catalog codes before logging or emitting; never log the original error or imageId.

- [ ] **Step 4: Write failing runtime lifecycle tests**

Mock Cornerstone modules and assert one RenderingEngine/viewport/ToolGroup, primary bindings, wheel/gesture stack navigation, setStack order, first image load before prefetch, at most 3 queued/2 background, series-switch cancellation, Length annotations retained across series, reset not clearing annotations, and idempotent dispose clearing all owned resources.

- [ ] **Step 5: Implement `ViewerRuntime` minimally**

Keep external objects in closure locals or `markRaw`/`shallowRef`, never deep reactive state. Configure WindowLevel, Zoom, Pan, StackScroll and Length once. Track every visited imageId for annotation cleanup; do not clear annotations in `setSeries`, only in `dispose`.

Map decoder/render incompatibility to `CLIENT_DICOM_IMAGE_UNSUPPORTED`; network, timeout and truncated response retain their existing CLIENT codes.

- [ ] **Step 6: Implement and test `DicomViewport.vue`**

The component receives instances and active tool, creates runtime on mount, watches only serializable props, emits catalog code failures and disposes on unmount/partial init failure.

Maintain only a reactive annotation count from Cornerstone annotation events and
publish it as `data-annotation-count` on the viewport container. It contains no
measurement values or identifiers and gives unit/E2E tests an observable cleanup
contract. The Cornerstone annotation objects themselves remain imperative.

- [ ] **Step 7: Run Cornerstone tests and build**

```bash
cd apps/frontend
mise exec -- pnpm test -- src/features/viewer/cornerstone src/features/viewer/DicomViewport.spec.ts
mise exec -- pnpm build
```

Expected: PASS with no Vue warning about proxied Cornerstone objects.

### Task 11: Assemble `ViewerPage` and the protected route

**Files:**
- Create: `apps/frontend/src/features/viewer/ViewerPage.vue`
- Create: `apps/frontend/src/features/viewer/ViewerPage.spec.ts`
- Create: `apps/frontend/src/features/viewer/loadDicomViewport.ts`
- Modify: `apps/frontend/src/app/router/index.ts`
- Create: `apps/frontend/src/app/router/index.spec.ts`

**Interfaces:**
- Consumes: Tasks 7–10.
- Produces: protected route `/studies/:studyUid` named `viewer`, complete page states and safe back behavior.

- [ ] **Step 1: Write failing page tests**

Assert summary loading, first supported selection, mixed study, all unsupported, page-level summary failure, viewport-only instance/frame failure, TraceID display, retry button only for MANUAL, deep-link back fallback, history back from Worklist, and toolbar/rail wiring.

Critical mobile assertion:

```ts
expect(fetchSeriesInstances).not.toHaveBeenCalled();
expect(loadDicomViewport).not.toHaveBeenCalled();
expect(wrapper.text()).toContain('Use uma tela maior');
```

- [ ] **Step 2: Implement the page without statically importing Cornerstone**

Use:

```ts
// loadDicomViewport.ts contains no static Cornerstone import.
export const loadDicomViewport = () => import('./DicomViewport.vue');

const DicomViewport = defineAsyncComponent(loadDicomViewport);
```

Render it only when `canRenderViewer` and active instances are ready. Watch the capability: crossing down calls `deactivateSeries` and unmounts the viewport; crossing up activates the selected supported series.

- [ ] **Step 3: Register the protected route**

```ts
{
  path: '/studies/:studyUid',
  name: 'viewer',
  component: ViewerPage,
  meta: { protected: true },
}
```

Keep authentication in the existing BFF route guard.

Implement back navigation without a URL-controlled redirect:

```ts
const backPath = window.history.state?.back;
if (backPath === '/studies' || (typeof backPath === 'string' && backPath.startsWith('/studies?'))) {
  router.back();
}
else await router.push({ name: 'worklist' });
```

Do not accept `returnUrl` from route query.

- [ ] **Step 4: Run the complete frontend suite and build**

```bash
cd apps/frontend
mise exec -- pnpm test
mise exec -- pnpm build
```

Expected: all Vitest tests pass and Vue/TypeScript build exits 0.

### Gate 2 — Frontend DICOM/UX review

- [ ] Dispatch `dicom-domain-reviewer` for imageId hierarchy, metadata mapping, frame correspondence and no invented physical calibration.
- [ ] Present desktop/tablet behavior, narrow-screen no-init evidence, tools, mixed-series rail and cleanup evidence to the human.
- [ ] Stop until the human approves Phase 2.

---

## Phase 3 — Integration, documentation and final graph

### Task 12: Prove the vertical slice end to end

**Files:**
- Modify: `apps/frontend/e2e/fixtures/synthetic-dicom.ts`
- Create: `apps/frontend/e2e/viewer.spec.ts`
- Modify: `apps/frontend/playwright.config.ts`
- Modify: `apps/frontend/package.json`

**Interfaces:**
- Consumes: running BlackICE stack, STOW MVP #1, Worklist MVP #2 and Viewer Tasks 2–11.
- Produces: repeatable synthetic CT/mixed-study viewer E2E.

- [ ] **Step 1: Write a failing synthetic CT fixture test path**

Extend the builder without changing the existing Secondary Capture default used by prior tests. Add `createSyntheticCtSlice` with CT SOP Class, Explicit VR Little Endian, FrameOfReferenceUID, ImagePositionPatient, ImageOrientationPatient, PixelSpacing, Image Pixel, rescale/window and Pixel Data. Build two CT series with at least two instances each; add one Secondary Capture series for the mixed unsupported case.

- [ ] **Step 2: Add the Playwright scenario before implementation wiring changes**

The test must:

```ts
test('opens a mixed study, renders CT, switches series and restores Worklist', async ({ page }) => {
  // ingest synthetic study through /api/studies
  // search by synthetic PatientID
  // click Abrir estudo
  // expect first supported series and rendered canvas
  // expect Secondary Capture disabled
  // switch supported series, use tools, create temporary Length
  // assert data-annotation-count becomes 1
  // return and assert filters/offset/results
  // reload viewer and assert data-annotation-count returns to 0
});
```

Add request observers asserting no Archive hostname, no non-active series frame request, first-frame priority and at most the approved prefetch window.

- [ ] **Step 3: Add lifecycle, failure and mobile scenarios**

Cover repeated open/close without growing canvases/listeners, simulated QIDO/WADO failures with safe errors, decoder incompatibility code, and the mobile project with zero instance/frame requests and no Cornerstone canvas.

- [ ] **Step 4: Register and run the E2E script**

Add:

```json
"test:e2e:viewer": "playwright test e2e/viewer.spec.ts"
```

Add `viewer.spec.ts` to `testMatch`, then run against the healthy local stack:

```bash
cd apps/frontend
mise exec -- pnpm test:e2e:viewer
```

Expected: desktop and mobile projects pass. If the stack is unavailable, stop and report the external prerequisite; do not substitute mocks for the required E2E gate.

- [ ] **Step 5: Run regression suites**

```bash
cd .problem-catalog && mise exec -- pnpm check
cd ../apps/backend && mise exec -- mvn test -Dquarkus.http.test-port=8082
cd ../frontend && mise exec -- pnpm test && mise exec -- pnpm build
```

Expected: all commands exit 0.

### Task 13: Close documentation, reviews and Graphify once

**Files:**
- Modify: `docs/architecture/project-structure.md` with the implemented `viewer` module/feature tree.
- Modify: `docs/domains/vue/cornerstone3d.md` only for verified Cornerstone 5.8.2 lifecycle/API details that differ from the current guidance.
- Update once: tracked `graphify-out/**` artifacts produced by the project-scoped Graphify workflow.

**Interfaces:**
- Consumes: stable implementation, passing tests and approved prior gates.
- Produces: final project documentation, graph synchronization and evidence bundle for the MVP gate.

- [ ] **Step 1: Update canonical structure docs**

Document only files and commands that exist. Keep `EVO-007`–`EVO-010` deferred; do not mark them implemented.

- [ ] **Step 2: Run the final DICOM review**

Dispatch `dicom-domain-reviewer` over all backend/frontend/E2E diffs. Resolve every blocking finding and rerun the affected tests before continuing.

- [ ] **Step 3: Run the complete verification matrix fresh**

```bash
cd .problem-catalog && mise exec -- pnpm test && mise exec -- pnpm check
cd ../apps/backend && mise exec -- mvn test -Dquarkus.http.test-port=8082
cd ../frontend && mise exec -- pnpm test && mise exec -- pnpm build && mise exec -- pnpm test:e2e:viewer
cd ../.. && git diff --check
```

Record exact test counts, failures and any skipped external scenario. Do not claim completion if a required command did not run.

- [ ] **Step 4: Run one semantic Graphify update**

Follow `.agents/skills/graphify/SKILL.md` and `docs/architecture/graphify.md` with `--update`. Review the entire tracked `graphify-out/` diff; in a linked worktree this step is manual and mandatory.

### Gate 3 — Final MVP decision

- [ ] Present the following evidence to the human:

- endpoint contracts and ProblemCode/URN;
- backend/frontend/E2E command outputs;
- DICOM reviewer result;
- Graphify diff summary;
- all modified/untracked files and any files deliberately excluded.

- [ ] Stop for the human decision that MVP #3 is complete. Do not commit, push or begin MVP #4 without explicit authorization.

## Plan self-review checklist

- [ ] Every approved spec requirement maps to a task or global constraint.
- [ ] Backend tasks preserve QIDO/WADO boundaries and exact UID hierarchy.
- [ ] Frontend tasks keep Cornerstone out of deep reactivity and mobile out of runtime loading.
- [ ] `CLIENT_DICOM_IMAGE_UNSUPPORTED` is generated before frontend references it.
- [ ] Tests cover success, partial support, error boundaries, privacy, cleanup and navigation restoration.
- [ ] Each phase stops at the required DICOM and human gate.
- [ ] No task authorizes EVO-007–EVO-010, commits, push or extra product scope.
