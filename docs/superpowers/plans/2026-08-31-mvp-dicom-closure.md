# MVP DICOM Closure Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fechar o MVP corrigindo identidade QIDO, completude de paginação, metadata WADO, consolidação STOW, geometria espacial, streaming HTTP e o E2E concorrente, com todos os gates verdes.

**Architecture:** As correções permanecem dentro dos módulos de feature existentes. Parsers DICOM JSON validam identidade, VR e VM na fronteira; gateways recusam respostas parciais; casos de uso só classificam dados comprovadamente completos; o frontend publica apenas metadata física recebida. O safety-net HTTP respeita respostas já iniciadas sem bufferizar WADO.

**Tech Stack:** Java 21, Quarkus 3.37, dcm4che, JUnit 5, Mockito, Vue 3, TypeScript, Vitest, Playwright, Docker Compose e Graphify.

**Spec:** `docs/superpowers/specs/2026-08-31-mvp-dicom-closure-design.md`

## Global Constraints

- UIDs DICOM são identidade exata; nunca aplicar `trim`, case folding ou correção.
- Não criar UIDs de aplicação; `2.25.<inteiro-UUID>` é exclusivo das fixtures que criam novos objetos DICOM sintéticos.
- Não re-encodar, transcodificar, persistir nem bufferizar Pixel Data completo.
- Não criar ou alterar tipos no catálogo; reutilizar `API_ARCHIVE_RESPONSE_INVALID` (`urn:uuid:8a220e49-3e80-5e59-83e5-43483c4a6dd8`).
- Não registrar UID, PatientID, payload DICOM, token ou URL interna em mensagem pública ou log.
- Preservar as mudanças preexistentes em `apps/frontend/e2e/fixtures/synthetic-dicom.ts` e `apps/frontend/e2e/worklist.spec.ts` até a Task 8.
- Cada mudança de produção segue RED → GREEN → REFACTOR e recebe commit pequeno com Gitmoji e corpo conforme `docs/domains/git/commit-conventions.md`.
- Não fazer push, merge, rebase ou alteração destrutiva de histórico.
- Não atualizar `graphify-out/` entre tarefas; executar uma única atualização semântica final na Task 9 e commitá-la separadamente.
- Toda tarefa backend que toca DICOM recebe revisão DICOM antes de ser considerada concluída.

---

### Task 1: Identidade QIDO exata

**Files:**
- Modify: `apps/backend/src/main/java/dev/blackice/viewer/infrastructure/dicomweb/QidoViewerResponseParser.java:230-261`
- Modify: `apps/backend/src/main/java/dev/blackice/viewer/infrastructure/dicomweb/HttpStudyHierarchyGateway.java:85-112`
- Test: `apps/backend/src/test/java/dev/blackice/viewer/infrastructure/dicomweb/QidoViewerResponseParserTest.java`
- Test: `apps/backend/src/test/java/dev/blackice/viewer/infrastructure/dicomweb/HttpStudyHierarchyGatewayTest.java`

**Interfaces:**
- Consumes: `ViewerStudyRef.studyInstanceUid()` e DICOM JSON com `vr: "UI"`.
- Produces: `requiredUid(JsonNode, String)` que exige VM 1 e preserva texto; `findStudy` que recusa UID divergente com `ArchiveViewerException.Reason.INVALID_RESPONSE`.

- [ ] **Step 1: Escrever testes RED do parser**

Adicionar casos que rejeitem espaço e VM múltipla sem revelar o UID na mensagem:

```java
@ParameterizedTest
@ValueSource(strings = {" 1.2.3", "1.2.3 ", "\t1.2.3"})
void rejects_uid_that_would_require_normalization(String uid) {
    String json = "[{\"0020000D\":{\"vr\":\"UI\",\"Value\":[\"" + uid + "\"]}}]";
    InvalidResponseException error = assertThrows(
        InvalidResponseException.class, () -> parser.parseStudy(json));
    assertFalse(error.getMessage().contains("1.2.3"));
}

@Test
void rejects_uid_with_vm_greater_than_one() {
    String json = "[{\"0020000D\":{\"vr\":\"UI\",\"Value\":[\"1.2.3\",\"1.2.4\"]}}]";
    assertThrows(InvalidResponseException.class, () -> parser.parseStudy(json));
}
```

- [ ] **Step 2: Escrever teste RED do gateway**

Em `HttpStudyHierarchyGatewayTest`, responder um único estudo válido com UID diferente de `STUDY_REF` e exigir `INVALID_RESPONSE`:

```java
@Test
void find_study_rejects_different_study_instance_uid() {
    server.createContext("/dcm4chee-arc/aets/DCM4CHEE/rs/studies",
        exchange -> respond(exchange, 200, DICOM_JSON,
        "[{\"0020000D\":{\"vr\":\"UI\",\"Value\":[\"1.2.3.999\"]}}]"));
    ArchiveViewerException error = assertThrows(ArchiveViewerException.class,
        () -> gateway(Duration.ofSeconds(2), 500).findStudy(STUDY_REF, "token"));
    assertEquals(ArchiveViewerException.Reason.INVALID_RESPONSE, error.reason());
    assertFalse(error.getMessage().contains(STUDY_UID));
}
```

- [ ] **Step 3: Executar RED**

Run:

```bash
cd apps/backend
mise exec -- mvn -Dtest=QidoViewerResponseParserTest,HttpStudyHierarchyGatewayTest test -Dquarkus.http.test-port=8082
```

Expected: os novos testes falham porque `firstText()` aplica `trim()`, aceita o primeiro valor e `findStudy()` devolve UID divergente.

- [ ] **Step 4: Implementar preservação e comparação exata**

Fazer `requiredUid` ler o item único sem normalização:

```java
private String requiredUid(JsonNode dataset, String tag) {
    JsonNode attr = attribute(dataset, tag, "UI");
    JsonNode values = attr == null ? null : attr.get("Value");
    if (values == null || !values.isArray() || values.size() != 1
        || !values.get(0).isTextual()) {
        throw new InvalidResponseException("Missing or invalid DICOM UID");
    }
    String value = values.get(0).asText();
    if (value.isEmpty() || !value.equals(value.strip()) || !UIDUtils.isValid(value)) {
        throw new InvalidResponseException("Missing or invalid DICOM UID");
    }
    return value;
}
```

Não alterar a normalização de campos textuais não identitários nesta tarefa. Depois do parse em `findStudy`:

```java
if (!study.studyInstanceUid().equals(result.studyInstanceUid())) {
    throw new ArchiveViewerException(ArchiveViewerException.Reason.INVALID_RESPONSE);
}
```

- [ ] **Step 5: Executar GREEN e commit**

Repetir o comando da Step 3. Esperado: todos os testes focados passam. Commitar somente os quatro arquivos da tarefa com título iniciado por `🐛`.

---

### Task 2: Paginação QIDO comprovadamente completa

**Files:**
- Modify: `apps/backend/src/main/java/dev/blackice/viewer/infrastructure/dicomweb/QidoViewerResponseParser.java:119-159,287-342`
- Modify: `apps/backend/src/main/java/dev/blackice/viewer/infrastructure/dicomweb/HttpStudyHierarchyGateway.java:114-175`
- Modify: `apps/backend/src/main/java/dev/blackice/viewer/application/usecase/GetStudyViewerUseCase.java:93-105`
- Test: `apps/backend/src/test/java/dev/blackice/viewer/infrastructure/dicomweb/QidoViewerResponseParserTest.java`
- Test: `apps/backend/src/test/java/dev/blackice/viewer/infrastructure/dicomweb/HttpStudyHierarchyGatewayTest.java`
- Test: `apps/backend/src/test/java/dev/blackice/viewer/application/usecase/GetStudyViewerUseCaseTest.java`

**Interfaces:**
- Consumes: `SeriesMetadata.instanceCount()`, `HttpResponse.headers().allValues("Warning")` e páginas de `InstanceIdentityMetadata`.
- Produces: contagem de série obrigatória e positiva; paginação guiada por warn-code `299`; classificação somente após igualdade de contagens.

- [ ] **Step 1: Escrever testes RED de contagem**

Substituir a aceitação de `instanceCount == null` por rejeição e adicionar VM/VR/valor inválidos:

```java
@Test
void rejects_series_without_number_of_series_related_instances() {
    String json = "[{\"0020000E\":{\"vr\":\"UI\",\"Value\":[\"1.2.3.1\"]}}]";
    assertThrows(InvalidResponseException.class, () -> parser.parseSeries(json));
}

@ParameterizedTest
@ValueSource(strings = {"0", "-1", "1.5", "abc"})
void rejects_non_positive_or_non_integral_related_instance_count(String count) {
    String json = "[{\"0020000E\":{\"vr\":\"UI\",\"Value\":[\"1.2.3.1\"]},"
        + "\"00201209\":{\"vr\":\"IS\",\"Value\":[\"" + count + "\"]}}]";
    assertThrows(InvalidResponseException.class, () -> parser.parseSeries(json));
}
```

Adicionar casos separados para `vr: "UL"` e `Value: [1,2]`.

- [ ] **Step 2: Escrever testes RED de Warning 299**

Usar o servidor HTTP real do teste:

```java
exchange.getResponseHeaders().add("Warning",
    "299 dcm4chee: There are 1 additional results that can be requested");
```

Cobrir:

```text
find_instances_continues_after_non_empty_page_with_warning_299
find_instances_advances_by_received_size_when_archive_max_results_is_smaller
find_instances_rejects_empty_page_with_warning_299
find_series_rejects_warning_299
find_study_rejects_warning_299
```

Os testes de continuação exigem offsets observados `0, 2` para duas páginas de tamanho 2+1, e `0, 1` quando `pageSize=2` mas o Archive devolve uma página de um item acompanhada de `299`.

- [ ] **Step 3: Escrever teste RED do caso de uso**

```java
@Test
void rejects_series_without_declared_instance_count() {
    String seriesUid = "1.2.3.1";
    when(gateway.findStudy(studyRef, TOKEN)).thenReturn(sampleStudyMetadata());
    when(gateway.findSeries(studyRef, TOKEN)).thenReturn(List.of(
        new SeriesMetadata(seriesUid, 1, "CT", "AXIAL", null)));
    when(gateway.findInstances(studyRef, TOKEN)).thenReturn(List.of(
        new InstanceIdentityMetadata(seriesUid, "1.2.3.1.1", CT_SOP_CLASS, 1)));
    assertThrows(InvalidArchiveMetadataException.class,
        () -> useCase.execute(studyRef, TOKEN));
}
```

- [ ] **Step 4: Executar RED**

```bash
cd apps/backend
mise exec -- mvn -Dtest=QidoViewerResponseParserTest,HttpStudyHierarchyGatewayTest,GetStudyViewerUseCaseTest test -Dquarkus.http.test-port=8082
```

Expected: ausência/VM inválida ainda é aceita, `299` é ignorado e série sem contagem ainda é classificada.

- [ ] **Step 5: Implementar contagem e parser de Warning**

Criar helper de contagem que exija `IS`, VM 1 e inteiro positivo. No gateway, reconhecer warn-code somente no início do valor:

```java
private static final Pattern WARNING_299 = Pattern.compile("^\\s*299(?:\\s|$)");

private static boolean hasMoreResults(HttpResponse<?> response) {
    return response.headers().allValues("Warning").stream()
        .anyMatch(value -> WARNING_299.matcher(value).find());
}
```

Em `findStudy` e `findSeries`, `hasMoreResults(response)` lança `INVALID_RESPONSE`. Em `findInstances`:

```java
boolean moreResults = hasMoreResults(response);
if (pageInstances.isEmpty() && moreResults) {
    throw new ArchiveViewerException(ArchiveViewerException.Reason.INVALID_RESPONSE);
}
// validar duplicatas antes de avançar
if (!moreResults) {
    break;
}
offset += pageInstances.size();
```

No caso de uso, `instanceCount == null` e qualquer desigualdade invalidam antes de `classifier.classify(...)`.

- [ ] **Step 6: Executar GREEN e commit**

Repetir Step 4; executar também `QidoViewerQueryBuilderTest`. Esperado: todos passam. Commitar somente arquivos da tarefa com `🐛`.

---

### Task 3: VR, VM e rescale no metadata WADO

**Files:**
- Modify: `apps/backend/src/main/java/dev/blackice/viewer/infrastructure/dicomweb/WadoSeriesMetadataParser.java`
- Test: `apps/backend/src/test/java/dev/blackice/viewer/infrastructure/dicomweb/WadoSeriesMetadataParserTest.java`

**Interfaces:**
- Consumes: DICOM JSON WADO Retrieve Series Metadata e allowlist de SOP Class existente.
- Produces: `ViewerInstance` com atributos validados; CT exige par rescale; outros SOP Classes preservam par opcional.

- [ ] **Step 1: Escrever testes RED de VR/VM**

Adicionar testes table-driven que mutem uma fixture válida e exijam `InvalidArchiveMetadataException` para:

```text
StudyInstanceUID vr=LO ou Value com dois itens
Rows vr=IS
ImagePositionPatient vr=FL ou VM=2
WindowCenter vr=IS
RescaleSlope Value com dois itens
```

Cada expectativa deve chamar `parser.parse(json, SERIES_REF)` e não inspecionar métodos privados.

- [ ] **Step 2: Escrever testes RED de rescale**

```java
@Test
void rejects_ct_without_rescale_pair() throws Exception {
    assertThrows(InvalidArchiveMetadataException.class,
        () -> parser.parse(removeTag(baseDatasetJson(CT_SOP_CLASS, SOP_UID), "00281052"), SERIES_REF));
    assertThrows(InvalidArchiveMetadataException.class,
        () -> parser.parse(removeTag(baseDatasetJson(CT_SOP_CLASS, SOP_UID), "00281053"), SERIES_REF));
}

@Test
void preserves_absent_rescale_pair_for_mr() throws Exception {
    String body = removeTag(
        removeTag(baseDatasetJson(MR_SOP_CLASS, SOP_UID), "00281052"),
        "00281053");
    ViewerInstance instance = parser.parse(body, SERIES_REF).getFirst();
    assertNull(instance.rescaleIntercept());
    assertNull(instance.rescaleSlope());
}

@Test
void rejects_zero_rescale_slope() throws Exception {
    String body = replaceValue(baseDatasetJson(CT_SOP_CLASS, SOP_UID), "00281053", "[0]");
    assertThrows(InvalidArchiveMetadataException.class,
        () -> parser.parse(body, SERIES_REF));
}
```

Adicionar helpers de fixture que alteram a árvore JSON, sem regex ou substituição
ambígua de texto, junto dos imports `JsonProcessingException`, `JsonNode`,
`ObjectMapper` e `ObjectNode` de Jackson:

```java
private String removeTag(String body, String tag) throws JsonProcessingException {
    JsonNode root = new ObjectMapper().readTree(body);
    ((ObjectNode) root.get(0)).remove(tag);
    return root.toString();
}

private String replaceValue(String body, String tag, String valueJson)
    throws JsonProcessingException {
    ObjectMapper mapper = new ObjectMapper();
    JsonNode root = mapper.readTree(body);
    ((ObjectNode) root.get(0).get(tag)).set("Value", mapper.readTree(valueJson));
    return root.toString();
}
```

- [ ] **Step 3: Executar RED**

```bash
cd apps/backend
mise exec -- mvn -Dtest=WadoSeriesMetadataParserTest test
```

Expected: VR é ignorado e CT incompleto/zero ainda é aceito.

- [ ] **Step 4: Implementar validação central de atributos**

Substituir `attribute(dataset, tag)` por:

```java
private JsonNode attribute(JsonNode dataset, String tag, String expectedVr) {
    JsonNode node = dataset.get(tag);
    if (node == null || node.isNull() || node.isMissingNode()) return null;
    if (!node.isObject()) {
        throw new InvalidArchiveMetadataException("Tag node must be a JSON object");
    }
    JsonNode vr = node.get("vr");
    if (vr == null || !vr.isTextual() || !expectedVr.equals(vr.asText())) {
        throw new InvalidArchiveMetadataException("Tag has unexpected vr");
    }
    return node;
}
```

Passar VR explícito em todos os leitores conforme a tabela da spec. Helpers escalares exigem `Value.size() == 1`; arrays geométricos exigem VM exata; Window aceita `1-n`. UIDs usam texto exato sem `trim`.

Adicionar a constante abaixo e incluir o par DS `00281052=-1024` e
`00281053=1` em `baseDatasetJson` e `datasetMissingTag`, para que todas as
fixtures CT existentes permaneçam válidas e cada teste continue falhando pela
regra que nomeia:

```java
private static final String CT_IMAGE_STORAGE = "1.2.840.10008.5.1.4.1.1.2";
```

Aplicar regra do par:

```java
boolean hasIntercept = rescaleIntercept != null;
boolean hasSlope = rescaleSlope != null;
if (hasIntercept != hasSlope || (hasSlope && rescaleSlope == 0.0d)
    || (CT_IMAGE_STORAGE.equals(sopClassUid) && !hasIntercept)) {
    throw new InvalidArchiveMetadataException("Invalid modality rescale metadata");
}
```

Mensagens permanecem genéricas, sem valores recebidos.

- [ ] **Step 5: Executar GREEN e commit**

Repetir Step 3. Esperado: todos passam. Commitar parser e teste com `🐛`.

---

### Task 4: Metadata provider sem defaults físicos

**Files:**
- Modify: `apps/frontend/src/features/viewer/cornerstone/viewer-metadata.ts:80-84`
- Test: `apps/frontend/src/features/viewer/cornerstone/viewer-metadata.spec.ts`

**Interfaces:**
- Consumes: `ViewerInstance.rescaleIntercept` e `.rescaleSlope` nulos ou ambos presentes.
- Produces: `modalityLutModule` exato quando calibrado; `undefined` quando o par está ausente.

- [ ] **Step 1: Escrever teste RED**

```typescript
it('does not invent modality LUT values when rescale is absent', () => {
  const instance = { ...baseInstance, rescaleIntercept: null, rescaleSlope: null };
  const [imageId] = registerInstancesMetadata([instance], STUDY_UID, SERIES_UID);
  expect(getViewerMetadata('modalityLutModule', imageId)).toBeUndefined();
});
```

- [ ] **Step 2: Executar RED**

```bash
cd apps/frontend
mise exec -- pnpm test -- src/features/viewer/cornerstone/viewer-metadata.spec.ts
```

Expected: recebe `{rescaleIntercept: 0, rescaleSlope: 1}`.

- [ ] **Step 3: Implementar retorno condicional**

```typescript
case 'modalityLutModule': {
  if (instance.rescaleIntercept === null || instance.rescaleSlope === null) {
    return undefined;
  }
  return {
    rescaleIntercept: instance.rescaleIntercept,
    rescaleSlope: instance.rescaleSlope,
  };
}
```

- [ ] **Step 4: Executar GREEN, suíte viewer e commit**

```bash
cd apps/frontend
mise exec -- pnpm test -- src/features/viewer/cornerstone/viewer-metadata.spec.ts src/features/viewer/cornerstone/viewer-runtime.spec.ts
```

Esperado: todos passam. Commitar os dois arquivos com `🐛`.

---

### Task 5: Precedência global do resultado STOW

**Files:**
- Modify: `apps/backend/src/main/java/dev/blackice/ingest/infrastructure/dicomweb/StowResponseParser.java:81-143`
- Test: `apps/backend/src/test/java/dev/blackice/ingest/infrastructure/dicomweb/StowResponseParserTest.java`

**Interfaces:**
- Consumes: múltiplos datasets STOW com resultados por SOP UID.
- Produces: merge monotônico `REJECTED > WARNING > ACCEPTED`.

- [ ] **Step 1: Escrever teste RED contraditório entre datasets**

```java
@Test
void rejected_result_is_not_overwritten_by_later_accepted_dataset() {
    String body = """
        [
          {"00081198":{"vr":"SQ","Value":[{"00081155":{"vr":"UI","Value":["1.2.3.1"]},"00081197":{"vr":"US","Value":[272]}}]}},
          {"00081199":{"vr":"SQ","Value":[{"00081155":{"vr":"UI","Value":["1.2.3.1"]}}]}}
        ]
        """;
    StowStudyResult result = parser.parse(STUDY_UID, List.of("1.2.3.1"), body);
    assertEquals(StowInstanceResult.Status.REJECTED, result.instances().getFirst().status());
}
```

Adicionar o inverso (accepted depois rejected) e warning depois accepted.

- [ ] **Step 2: Executar RED**

```bash
cd apps/backend
mise exec -- mvn -Dtest=StowResponseParserTest test
```

Expected: rejeição anterior vira `ACCEPTED` no primeiro caso.

- [ ] **Step 3: Implementar merge por severidade**

Centralizar toda escrita no mapa:

```java
private static void mergeResult(Map<String, StowInstanceResult> results,
                                StowInstanceResult candidate) {
    results.merge(candidate.sopInstanceUid(), candidate,
        (current, incoming) -> severity(incoming.status()) > severity(current.status())
            ? incoming : current);
}

private static int severity(StowInstanceResult.Status status) {
    return switch (status) {
        case ACCEPTED -> 0;
        case WARNING -> 1;
        case REJECTED -> 2;
        case UNCONFIRMED -> throw new IllegalArgumentException("UNCONFIRMED is not parsed from STOW");
    };
}
```

Substituir todos os `parsedInstances.put` por `mergeResult`.

- [ ] **Step 4: Executar GREEN e commit**

Repetir Step 2. Esperado: todos passam. Commitar parser e teste com `🐛`.

---

### Task 6: Fallback para orientação não unitária

**Files:**
- Modify: `apps/backend/src/main/java/dev/blackice/viewer/application/usecase/SpatialInstanceOrder.java:116-134`
- Test: `apps/backend/src/test/java/dev/blackice/viewer/application/usecase/SpatialInstanceOrderTest.java:67-81`

**Interfaces:**
- Consumes: vetores linha/coluna de `ImageOrientationPatient`.
- Produces: geometria somente quando `abs(norm - 1) <= 1e-4`; caso contrário, fallback completo.

- [ ] **Step 1: Corrigir expectativa e observar RED**

Renomear o teste atual para `fallback_ordering_when_orientation_vectors_are_not_unit_length`, usar `instanceNumber` oposto à projeção e esperar ordem por `instanceNumber`:

```java
double[] nonUnit = {2, 0, 0, 0, 3, 0};
ViewerInstance projectedFirst = createInstance("1.2.3.1", 2, new double[]{0, 0, -10}, nonUnit);
ViewerInstance fallbackFirst = createInstance("1.2.3.2", 1, new double[]{0, 0, 10}, nonUnit);
assertEquals(List.of("1.2.3.2", "1.2.3.1"),
    order.sort(List.of(projectedFirst, fallbackFirst)).stream()
        .map(ViewerInstance::sopInstanceUid).toList());
```

Run `mise exec -- mvn -Dtest=SpatialInstanceOrderTest test`; esperado: FAIL com ordem espacial.

- [ ] **Step 2: Implementar validação de norma**

```java
if (Math.abs(rowNorm - 1.0d) > EPSILON || Math.abs(colNorm - 1.0d) > EPSILON) {
    return null;
}
```

Manter validação de finitude, ortogonalidade e consistência existente.

- [ ] **Step 3: Executar GREEN e commit**

Repetir o teste focado; esperado: todos passam. Commitar dois arquivos com `🐛`.

---

### Task 7: Streaming WADO sem Problem Details após commit

**Files:**
- Modify: `apps/backend/src/main/java/dev/blackice/shared/api/problem/ApiHttpFailureHandler.java:44-48`
- Create: `apps/backend/src/test/java/dev/blackice/shared/api/problem/ApiHttpFailureHandlerTest.java`
- Modify: `apps/backend/src/test/java/dev/blackice/viewer/api/WadoFrameResourceTest.java:338-382`

**Interfaces:**
- Consumes: `HttpServerResponse.headWritten()` e `ended()`.
- Produces: failure handler não escreve Problem Details após headers; integração usa barreira real de commit.

- [ ] **Step 1: Escrever teste unitário RED do safety-net**

Com Mockito, configurar `isApiRequest` pela request `/api/...`, `response.headWritten() == true`, `ended() == false`, invocar o handler por método package-private e verificar:

```java
verify(context).next();
verify(response, never()).setStatusCode(anyInt());
verify(response, never()).putHeader(any(CharSequence.class), anyString());
verify(response, never()).end(anyString());
```

Tornar `writeProblemForFailure` package-private somente para o teste, sem criar API pública.

- [ ] **Step 2: Executar RED unitário**

```bash
cd apps/backend
mise exec -- mvn -Dtest=ApiHttpFailureHandlerTest test
```

Expected: o handler tenta construir/escrever `API_INTERNAL_ERROR` apesar de `headWritten()`.

- [ ] **Step 3: Implementar guarda mínima**

```java
if (!ApiJavaScriptRequestChecker.isApiRequest(context)
    || context.response().ended()
    || context.response().headWritten()) {
    context.next();
    return;
}
```

- [ ] **Step 4: Substituir teste de corrida por barreira real**

No teste Quarkus, usar `CountDownLatch releaseFailure`; o `InputStream` entrega o primeiro bloco, bloqueia na próxima leitura e só então lança. Usar JDK `HttpClient.sendAsync(..., BodyHandlers.ofInputStream())`; receber o `HttpResponse<InputStream>` com status 200 e Content-Type WADO prova que headers foram comprometidos. Depois liberar o latch e consumir o body. Aceitar somente bytes iniciados por `A` seguidos de término truncado/`IOException`; nunca aceitar `application/problem+json` nem body com `\"code\":\"API_`.

Não capturar/suprimir `IOException` em `WadoFrameResource` e não adicionar prebuffer.

- [ ] **Step 5: Executar testes focados e commit**

```bash
cd apps/backend
mise exec -- mvn -Dtest=ApiHttpFailureHandlerTest,WadoFrameResourceTest test -Dquarkus.http.test-port=8082
```

Expected: teste unitário passa; integração passa repetida três vezes quando Docker estiver disponível. Commitar somente handler e testes com `🐛`.

---

### Task 8: E2E concorrente STOW/QIDO

**Files:**
- Modify/preexisting: `apps/frontend/e2e/fixtures/synthetic-dicom.ts`
- Modify/preexisting: `apps/frontend/e2e/worklist.spec.ts`

**Interfaces:**
- Consumes: fixture DICOM sintética e stack real Keycloak → Quarkus → DCM4CHEE.
- Produces: prova temporal de QIDO concluído antes da resposta do POST STOW.

- [ ] **Step 1: Revisar o diff preexistente sem reescrevê-lo**

Confirmar no diff:

```text
pixelDataBytes é coerente com Rows × Columns × SamplesPerPixel × BitsAllocated/8
UID usa 2.25.<inteiro positivo derivado do UUID>
POST request/response e QIDO request/response usam timestamps observáveis
CASE01 fica consultável antes do POST terminar
CASE30 ainda não fica consultável nesse instante
o teste falha se o POST responder antes dessas provas
```

- [ ] **Step 2: Executar validações estáticas e unitárias**

```bash
cd apps/frontend
mise exec -- pnpm test
mise exec -- pnpm exec playwright test --list
```

Esperado: Vitest passa e Playwright lista os projetos/casos sem erro de TypeScript. Se o reporter local for root-owned, usar `--reporter=line` para não escrever `playwright-report/index.html`.

- [ ] **Step 3: Executar o caso concorrente contra a stack**

```bash
cd apps/frontend
CI=true mise exec -- pnpm exec playwright test e2e/worklist.spec.ts \
  --grep "concurrent STOW import and QIDO worklist search" --reporter=line
```

Esperado: desktop e mobile passam, anexando `concurrency-timings`; o QIDO termina antes do POST.

- [ ] **Step 4: Commitar somente o teste validado**

Depois do E2E verde, commitar os dois arquivos preexistentes com `✅`. Não incluir produção, reports ou artefatos Playwright.

---

### Task 9: Gates completos, revisão final e Graphify

**Files:**
- Verify: `.problem-catalog/**`
- Verify: `apps/backend/**`
- Verify: `apps/frontend/**`
- Verify: `infra/**`
- Update once: `graphify-out/**`

**Interfaces:**
- Consumes: todos os commits das Tasks 1-8.
- Produces: evidência integral dos gates, aprovação DICOM e grafo sincronizado em commit separado.

- [ ] **Step 1: Catálogo**

```bash
cd .problem-catalog
mise install
mise exec -- pnpm install --frozen-lockfile
mise exec -- pnpm test
mise exec -- pnpm check
```

Expected: todos os testes passam, `check` sem divergência e nenhum diff gerado.

- [ ] **Step 2: Backend completo**

```bash
cd apps/backend
mise install
mise exec -- mvn test -Dquarkus.http.test-port=8082
```

Expected: zero failures/errors. Repetir três vezes o teste de streaming focado para detectar corrida residual.

- [ ] **Step 3: Frontend e build**

```bash
cd apps/frontend
mise install
mise exec -- pnpm install --frozen-lockfile
mise exec -- pnpm test
mise exec -- pnpm build
```

Expected: zero testes falhos e build exit 0.

- [ ] **Step 4: Compose e E2E completo**

```bash
cd infra
docker compose --env-file .env.example \
  -f compose.yml -f dcm4chee/compose.yml -f compose.apps.yml config --quiet
docker compose -f compose.yml -f dcm4chee/compose.yml -f compose.apps.yml up -d --build
cd ..
bash infra/keycloak/configure-blackice.sh
cd apps/frontend
CI=true mise exec -- pnpm exec playwright test --reporter=line
```

Expected: 42 ou mais testes E2E passam, incluindo autenticação, ingestão, Problem Details, laudos, viewer e worklist. Executar teardown ao final sem apagar dados fora dos volumes desta composição.

- [ ] **Step 5: Revisões**

Entregar o diff completo a um revisor geral e ao `dicom-domain-reviewer`. Nenhum achado Critical/Important e gate DICOM aprovado são obrigatórios. Correções de review retornam ao subagente responsável e repetem seus testes focados.

- [ ] **Step 6: Atualizar Graphify uma única vez**

Aplicar `.agents/skills/graphify/SKILL.md` e `docs/architecture/graphify.md`:

```bash
graphify . --update
git diff -- graphify-out/
```

Revisar todo arquivo rastreado alterado. Commitar somente `graphify-out/**` com mensagem exata `🕸️ sincroniza grafo de conhecimento`.

- [ ] **Step 7: Gate humano final**

Apresentar commits, contagens reais de testes, limitações do ambiente, parecer DICOM e quaisquer rulings do ledger. Não fazer push ou merge antes de aprovação explícita.
