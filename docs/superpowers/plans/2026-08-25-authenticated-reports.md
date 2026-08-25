# Authenticated Reports Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Entregar o MVP #4 do BlackICE: um laudo textual autenticado por estudo, persistido no PostgreSQL do produto, editável somente pelo autor enquanto rascunho, finalizável de forma irreversível e integrado ao viewer com concorrência otimista.

**Architecture:** O novo módulo Quarkus `dev.blackice.reports` segue `api → application ← infrastructure`: regras e casos de uso independem de HTTP e persistência; Panache/Flyway implementam o armazenamento; um adaptador QIDO-RS mínimo confirma o estudo somente na criação. A feature Vue `reports` mantém o conteúdo local, ETag e estado de edição, enquanto `ViewerPage` apenas compõe um painel adaptativo que não controla o lifecycle do Cornerstone.

**Tech Stack:** Java 21, Quarkus 3.37.4, Hibernate ORM with Panache, Flyway, PostgreSQL 17, dcm4che 5.34.3, JUnit 5, Mockito, ArchUnit, Vue 3.5.39, Vue Router 4.6.4, TypeScript 6.0.2, Vite 8.1.1, Vitest 4.1.10 e Playwright 1.62.0.

**Spec:** `docs/superpowers/specs/2026-08-25-authenticated-reports-design.md`

## Global Constraints

- Leia a spec, `docs/architecture/evolution-backlog.md`, `docs/domains/dicom/{semantics,dicomweb}.md` e `docs/domains/quarkus/conventions.md` antes de cada tarefa que toque DICOM, persistência ou autorização.
- Preserve exatamente o `StudyInstanceUID`; valide com `UIDUtils.isValid`, não aplique `trim`, não normalize componentes e nunca gere outro UID.
- QIDO-RS é usado somente para confirmar a existência do estudo durante `POST`. `GET` e `PUT` consultam somente PostgreSQL. Nenhuma transação de banco permanece aberta durante QIDO.
- Existe no máximo um laudo por `StudyInstanceUID`. `DRAFT → DRAFT` e `DRAFT → FINAL` são as únicas mutações; `FINAL` é terminal.
- Somente o `sub` criador pode mutar. Qualquer sessão com role `auth` pode ler. `preferred_username` é apenas o snapshot de exibição, com fallback para `sub`.
- Conteúdo é texto simples exato: não vazio/whitespace, no máximo 32.000 code points Unicode, sem Markdown, HTML, template ou autosave.
- `id`, `authorId` e `version` nunca entram no JSON. A versão viaja apenas em ETag forte; o frontend preserva e devolve o header completo sem interpretá-lo.
- `POST` e `PUT` exigem o double-submit CSRF existente também para JSON. O access token continua somente no backend.
- Respostas do recurso usam `Cache-Control: no-store`; `X-Trace-ID` permanece sob o filtro global. Conteúdo, UID, autor, token, URL concreta e payload externo nunca entram em logs ou problemas.
- Abaixo de 1024 px o Cornerstone não inicializa e o laudo ocupa a tela. Entre 1024 e 1439 px o painel é drawer fechado por padrão. A partir de 1440 px ele abre lado a lado e preserva ao menos 720 px úteis para imagens.
- Fechar/reabrir o painel usa a mesma instância montada. Não recrie `RenderingEngine`, viewport, `ToolGroup`, filas ou medições.
- `EVO-011` e `EVO-012` permanecem fora do escopo. Não implemente Markdown rico, autosave, exclusão, invalidação ou auditoria.
- Backend: Javadoc, comentários, nomes de testes e mensagens internas em inglês. Documentação e UI continuam em PT-BR.
- Use TDD em cada tarefa: escreva o teste, execute e confirme a falha esperada, implemente o mínimo e execute novamente até passar.
- Não edite catálogo, lock ou artefatos gerados manualmente; use `.problem-catalog/`.
- Não atualize Graphify durante os ciclos. Faça uma única atualização semântica final após implementação, testes, revisões DICOM e gates estáveis.
- Não crie commit sem autorização humana explícita. Os limites de tarefa e fase são pontos de revisão, não autorização para commit.

---

## Phase 0 — Published problem contract

### Task 1: Publish the two approved conflict types

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
- Produces `API_RESOURCE_CONFLICT` with HTTP 409 and `API_RESOURCE_VERSION_CONFLICT` with HTTP 412.
- Produces exhaustive PT-BR messages without rendering operator-facing `detail` text.

- [ ] **Step 1: Validate the catalog before mutation**

Working directory: `.problem-catalog/`

```bash
rtk mise exec -- pnpm check
```

Expected: `catálogo, lock e artefatos gerados estão consistentes`.

- [ ] **Step 2: Add exactly the two entries authorized by the spec**

Working directory: `.problem-catalog/`

```bash
rtk mise exec -- pnpm run add -- \
  --code API_RESOURCE_CONFLICT \
  --scope API \
  --description "A operação solicitada conflita com o estado atual do recurso." \
  --http-status 409 \
  --title "Resource conflict" \
  --detail "The resource state conflicts with the requested operation." \
  --retry-policy MANUAL \
  --owner platform

rtk mise exec -- pnpm run add -- \
  --code API_RESOURCE_VERSION_CONFLICT \
  --scope API \
  --description "A versão informada pelo cliente não corresponde à versão atual do recurso." \
  --http-status 412 \
  --title "Resource version conflict" \
  --detail "The resource was changed by another request. Reload it and review your changes." \
  --retry-policy MANUAL \
  --owner platform

rtk mise exec -- pnpm generate
```

Expected: each `add` prints a UUIDv5 URN derived by the tool; generation lists the catalog, lock, Markdown, Java and TypeScript artifacts. No command accepts a handwritten UUID.

- [ ] **Step 3: Add failing message assertions**

Add:

```ts
expect(problemMessage('API_RESOURCE_CONFLICT')).toBe(
  'O laudo está em um estado que não permite esta operação.',
);
expect(problemMessage('API_RESOURCE_VERSION_CONFLICT')).toBe(
  'O laudo foi alterado em outra sessão. Revise a versão atual antes de continuar.',
);
```

Working directory: `apps/frontend/`

```bash
rtk mise exec -- pnpm test -- src/shared/api/problems/problem-messages.pt-BR.spec.ts
```

Expected: FAIL because the exhaustive message map lacks both generated codes.

- [ ] **Step 4: Add the safe PT-BR messages and validate every generated consumer**

Add the same two strings to `PROBLEM_MESSAGES`, then run:

Working directory: `.problem-catalog/`

```bash
rtk mise exec -- pnpm test
rtk mise exec -- pnpm check
```

Working directory: `apps/frontend/`

```bash
rtk mise exec -- pnpm test -- src/shared/api/problems/problem-messages.pt-BR.spec.ts
rtk mise exec -- pnpm build
```

Expected: all commands exit 0; both API entries have no extension schema and keep the immutable values from the spec.

- [ ] **Step 5: Stop at the task review boundary**

Review only catalog/generated/message changes. Do not commit without a new explicit human instruction.

---

## Phase 1 — Domain, application and PostgreSQL

### Task 2: Implement exact report value objects and lifecycle rules

**Files:**
- Create: `apps/backend/src/main/java/dev/blackice/reports/package-info.java`
- Create: `apps/backend/src/main/java/dev/blackice/reports/domain/ReportStatus.java`
- Create: `apps/backend/src/main/java/dev/blackice/reports/domain/Report.java`
- Create: `apps/backend/src/main/java/dev/blackice/reports/application/input/ReportStudyRef.java`
- Create: `apps/backend/src/main/java/dev/blackice/reports/application/input/ReportActor.java`
- Create: `apps/backend/src/main/java/dev/blackice/reports/application/input/ReportContent.java`
- Create: `apps/backend/src/main/java/dev/blackice/reports/application/exception/InvalidReportRequestException.java`
- Create: `apps/backend/src/main/java/dev/blackice/reports/application/exception/ReportPayloadTooLargeException.java`
- Create matching `package-info.java` files for the concrete packages above.
- Test: `apps/backend/src/test/java/dev/blackice/reports/application/input/ReportInputsTest.java`
- Test: `apps/backend/src/test/java/dev/blackice/reports/domain/ReportTest.java`

**Interfaces:**

```java
public enum ReportStatus { DRAFT, FINAL }

public record ReportStudyRef(String studyInstanceUid) {}
public record ReportActor(String subject, String displayName) {}
public record ReportContent(String value) {
    public static final int MAX_CODE_POINTS = 32_000;
}

public record Report(
    String studyInstanceUid,
    String authorId,
    String authorDisplayName,
    ReportStatus status,
    String content,
    long version,
    Instant createdAt,
    Instant updatedAt,
    Instant finalizedAt
) {
    public static Report create(ReportStudyRef study, ReportActor actor,
                                ReportContent content, ReportStatus status, Instant now);
    public Report revise(ReportContent content, ReportStatus targetStatus, Instant now);
}
```

- [ ] **Step 1: Write failing input validation tests**

Cover null/invalid UID, valid UID preservation, null/blank actor fields, empty/Unicode-whitespace content, 32.000 code points accepted and 32.001 rejected. Include an astral character so the test proves code points rather than UTF-16 length.

```java
assertEquals(uid, new ReportStudyRef(uid).studyInstanceUid());
assertThrows(InvalidReportRequestException.class, () -> new ReportStudyRef(" 1.2.3"));
assertEquals(32_000, new ReportContent("😀".repeat(32_000)).value().codePointCount(0, 64_000));
assertThrows(ReportPayloadTooLargeException.class,
    () -> new ReportContent("😀".repeat(32_001)));
```

Working directory: `apps/backend/`

```bash
rtk mise exec -- mvn -Dtest=ReportInputsTest test -Dquarkus.http.test-port=8082
```

Expected: FAIL because the report inputs do not exist.

- [ ] **Step 2: Implement validation without changing the supplied text**

Use `UIDUtils.isValid(studyInstanceUid)` directly on the original value. Count with `String.codePointCount`; classify whitespace with `Character.isWhitespace` or `Character.isSpaceChar`, but persist the original `String` unchanged.

Run the focused test again. Expected: PASS.

- [ ] **Step 3: Write failing lifecycle tests**

Cover creation as DRAFT, direct creation as FINAL, immutable author/study/createdAt, DRAFT save, DRAFT finalization, finalizedAt set once, version increment once per accepted revision and every mutation from FINAL rejected.

```java
Report draft = Report.create(study, actor, content, ReportStatus.DRAFT, createdAt);
Report saved = draft.revise(revised, ReportStatus.DRAFT, updatedAt);
Report finalized = saved.revise(finalText, ReportStatus.FINAL, finalizedAt);

assertEquals(2L, finalized.version());
assertEquals(finalizedAt, finalized.finalizedAt());
assertThrows(InvalidReportRequestException.class,
    () -> finalized.revise(otherText, ReportStatus.DRAFT, finalizedAt.plusSeconds(1)));
```

Run:

```bash
rtk mise exec -- mvn -Dtest=ReportTest test -Dquarkus.http.test-port=8082
```

Expected: FAIL before `Report` exists or enforces the terminal state.

- [ ] **Step 4: Implement the immutable state machine and rerun both tests**

Creation uses version `0`; `updatedAt == createdAt`; direct FINAL sets `finalizedAt == createdAt`. `revise` accepts only a current DRAFT and target DRAFT/FINAL, returns a new record with version `+1`, and never mutates identity or creation time.

Expected: both focused test classes PASS.

### Task 3: Implement ports and use cases with optimistic concurrency semantics

**Files:**
- Create: `apps/backend/src/main/java/dev/blackice/reports/application/input/CreateReportCommand.java`
- Create: `apps/backend/src/main/java/dev/blackice/reports/application/input/UpdateReportCommand.java`
- Create: `apps/backend/src/main/java/dev/blackice/reports/application/result/StudyReportResult.java`
- Create: `apps/backend/src/main/java/dev/blackice/reports/application/port/ReportRepository.java`
- Create: `apps/backend/src/main/java/dev/blackice/reports/application/port/StudyExistenceGateway.java`
- Create: `apps/backend/src/main/java/dev/blackice/reports/application/exception/ReportAccessDeniedException.java`
- Create: `apps/backend/src/main/java/dev/blackice/reports/application/exception/ReportConflictException.java`
- Create: `apps/backend/src/main/java/dev/blackice/reports/application/exception/ReportNotFoundException.java`
- Create: `apps/backend/src/main/java/dev/blackice/reports/application/exception/ReportVersionConflictException.java`
- Create: `apps/backend/src/main/java/dev/blackice/reports/application/exception/StudyNotFoundException.java`
- Create: `apps/backend/src/main/java/dev/blackice/reports/application/usecase/GetStudyReportUseCase.java`
- Create: `apps/backend/src/main/java/dev/blackice/reports/application/usecase/CreateStudyReportUseCase.java`
- Create: `apps/backend/src/main/java/dev/blackice/reports/application/usecase/UpdateStudyReportUseCase.java`
- Create matching `package-info.java` files for new concrete packages.
- Test: `apps/backend/src/test/java/dev/blackice/reports/application/usecase/GetStudyReportUseCaseTest.java`
- Test: `apps/backend/src/test/java/dev/blackice/reports/application/usecase/CreateStudyReportUseCaseTest.java`
- Test: `apps/backend/src/test/java/dev/blackice/reports/application/usecase/UpdateStudyReportUseCaseTest.java`

**Interfaces:**

```java
public interface ReportRepository {
    Optional<Report> findByStudyInstanceUid(ReportStudyRef study);
    Report insert(Report report);
    boolean updateIfVersionMatches(Report revised, long expectedVersion);
}

public interface StudyExistenceGateway {
    boolean exists(ReportStudyRef study, String accessToken);
}

public record StudyReportResult(
    String studyInstanceUid,
    String authorDisplayName,
    ReportStatus status,
    String content,
    boolean editable,
    Instant createdAt,
    Instant updatedAt,
    Instant finalizedAt,
    long version
) {}
```

- [ ] **Step 1: Write failing GET use-case tests**

Prove absent returns `Optional.empty`, an existing report is readable by a different authenticated actor, `editable` is true only for author + DRAFT, and no study-existence gateway is a GET dependency.

Run:

```bash
rtk mise exec -- mvn -Dtest=GetStudyReportUseCaseTest test -Dquarkus.http.test-port=8082
```

Expected: FAIL because the port/result/use case do not exist.

- [ ] **Step 2: Implement GET and its public projection**

The use case receives `ReportStudyRef` and current `ReportActor`, loads only the repository and derives `editable` without exposing `authorId`.

- [ ] **Step 3: Write failing creation tests**

Cover local conflict before QIDO, archive existence called exactly once with the exact UID and server-side token, nonexistent study, DRAFT creation, direct FINAL creation, immutable author snapshot and a repository unique-race translated to `ReportConflictException`.

```java
verify(gateway).exists(eq(study), eq("server-token"));
verify(repository).insert(argThat(report ->
    report.authorId().equals("subject-1") && report.status() == ReportStatus.FINAL));
```

Run the focused test and confirm FAIL.

- [ ] **Step 4: Implement creation without a surrounding transaction**

`CreateStudyReportUseCase` must not carry `@Transactional`: perform repository existence check, QIDO call, then insert. Keep transaction ownership inside repository adapter methods so external I/O never occurs in a DB transaction.

- [ ] **Step 5: Write failing update tests**

Cover missing report, other author, already FINAL, stale expected version, DRAFT save, atomic DRAFT→FINAL, and an atomic adapter miss after the initial read. Verify QIDO is not present in the update constructor and cannot be called.

```java
when(repository.updateIfVersionMatches(any(), eq(4L))).thenReturn(false);
assertThrows(ReportVersionConflictException.class,
    () -> useCase.execute(commandWithExpectedVersion(4L)));
```

- [ ] **Step 6: Implement update ordering and rerun all application tests**

Order checks as: load → author → terminal state → supplied version → pure transition → conditional update. A false conditional update is always `ReportVersionConflictException`; nothing retries or overwrites.

```bash
rtk mise exec -- mvn \
  -Dtest='ReportInputsTest,ReportTest,GetStudyReportUseCaseTest,CreateStudyReportUseCaseTest,UpdateStudyReportUseCaseTest' \
  test -Dquarkus.http.test-port=8082
```

Expected: PASS and no import from Jakarta REST, Panache or `reports.infrastructure` under `reports.application`.

### Task 4: Add Flyway/PostgreSQL and the atomic persistence adapter

**Files:**
- Modify: `apps/backend/pom.xml`
- Modify: `apps/backend/src/main/resources/application.properties`
- Create: `apps/backend/src/main/resources/db/migration/V1__create_reports.sql`
- Create: `apps/backend/src/main/java/dev/blackice/reports/infrastructure/persistence/ReportEntity.java`
- Create: `apps/backend/src/main/java/dev/blackice/reports/infrastructure/persistence/PanacheReportRepository.java`
- Create: `apps/backend/src/main/java/dev/blackice/reports/infrastructure/persistence/package-info.java`
- Modify: `infra/compose.apps.yml`
- Test: `apps/backend/src/test/java/dev/blackice/reports/infrastructure/persistence/PanacheReportRepositoryTest.java`

**Interfaces:**
- Adds `quarkus-hibernate-orm-panache`, `quarkus-jdbc-postgresql`, `quarkus-flyway` and `flyway-database-postgresql`.
- Maps the domain port to PostgreSQL with a unique study constraint and one conditional update by exact expected version.

- [ ] **Step 1: Write the migration and a failing PostgreSQL integration test**

The migration creates `reports` with:

```sql
id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
study_instance_uid VARCHAR(64) NOT NULL,
author_id TEXT NOT NULL,
author_display_name TEXT NOT NULL,
status VARCHAR(16) NOT NULL,
content TEXT NOT NULL,
version BIGINT NOT NULL,
created_at TIMESTAMPTZ NOT NULL,
updated_at TIMESTAMPTZ NOT NULL,
finalized_at TIMESTAMPTZ NULL,
CONSTRAINT uq_reports_study_instance_uid UNIQUE (study_instance_uid),
CONSTRAINT ck_reports_status CHECK (status IN ('DRAFT', 'FINAL')),
CONSTRAINT ck_reports_content_length CHECK (char_length(content) BETWEEN 1 AND 32000),
CONSTRAINT ck_reports_finalized_at CHECK (
  (status = 'DRAFT' AND finalized_at IS NULL) OR
  (status = 'FINAL' AND finalized_at IS NOT NULL)
)
```

The test uses Quarkus Dev Services PostgreSQL, never H2, and initially fails because dependencies/config/adapter are missing.

- [ ] **Step 2: Configure datasource, Flyway and schema validation**

Add:

```properties
quarkus.datasource.db-kind=postgresql
quarkus.flyway.migrate-at-start=true
quarkus.hibernate-orm.schema-management.strategy=validate
```

Do not set a fixed test JDBC URL; Dev Services supplies the real PostgreSQL test container. In `compose.apps.yml`, add `QUARKUS_DATASOURCE_JDBC_URL`, username and password from `PRODUCT_DB*`, add `product-db` to `depends_on` with `condition: service_healthy`, and preserve the single `product-db` declared in `compose.yml`.

- [ ] **Step 3: Implement entity mapping and short transactional methods**

Use `@Entity(name = "ReportEntity")`, table `reports`, explicit column names and no cascade relationships. Repository methods own their transactions. The update must be one conditional statement:

```java
int changed = ReportEntity.update(
    "content = ?1, status = ?2, updatedAt = ?3, finalizedAt = ?4, " +
    "version = version + 1 where studyInstanceUid = ?5 and version = ?6",
    revised.content(), revised.status(), revised.updatedAt(), revised.finalizedAt(),
    revised.studyInstanceUid(), expectedVersion
);
return changed == 1;
```

Translate only PostgreSQL unique-constraint `23505` for `uq_reports_study_instance_uid` into `ReportConflictException`; unexpected persistence failures continue to the sanitized 500 boundary.

- [ ] **Step 4: Prove schema and concurrency behavior**

Test migration startup, exact round trip, one row per UID, direct FINAL coherence, a successful versioned update and two concurrent updates where exactly one wins. Verify no delete method exists in the application port.

```bash
rtk mise exec -- mvn -Dtest=PanacheReportRepositoryTest test -Dquarkus.http.test-port=8082
```

Expected: PASS against Dev Services PostgreSQL; winner version is `1`, loser returns false, and only the winner content is stored.

- [ ] **Step 5: Verify Phase 1 and stop for the human gate**

```bash
rtk mise exec -- mvn \
  -Dtest='ReportInputsTest,ReportTest,GetStudyReportUseCaseTest,CreateStudyReportUseCaseTest,UpdateStudyReportUseCaseTest,PanacheReportRepositoryTest' \
  test -Dquarkus.http.test-port=8082
```

Expected: PASS. Present schema, lifecycle, authorship and concurrency evidence to the human. Do not begin Phase 2 until approved.

---

## Phase 2 — QIDO existence and authenticated HTTP API

### Task 5: Implement the minimal QIDO-RS study-existence adapter

**Files:**
- Create: `apps/backend/src/main/java/dev/blackice/reports/application/exception/ArchiveStudyLookupException.java`
- Create: `apps/backend/src/main/java/dev/blackice/reports/infrastructure/dicomweb/ReportQidoQueryBuilder.java`
- Create: `apps/backend/src/main/java/dev/blackice/reports/infrastructure/dicomweb/ReportQidoResponseParser.java`
- Create: `apps/backend/src/main/java/dev/blackice/reports/infrastructure/dicomweb/HttpStudyExistenceGateway.java`
- Create: `apps/backend/src/main/java/dev/blackice/reports/infrastructure/dicomweb/package-info.java`
- Modify: `apps/backend/src/main/resources/application.properties`
- Test: `apps/backend/src/test/java/dev/blackice/reports/infrastructure/dicomweb/ReportQidoQueryBuilderTest.java`
- Test: `apps/backend/src/test/java/dev/blackice/reports/infrastructure/dicomweb/ReportQidoResponseParserTest.java`
- Test: `apps/backend/src/test/java/dev/blackice/reports/infrastructure/dicomweb/HttpStudyExistenceGatewayTest.java`

**Interfaces:**

```text
GET {blackice.dicomweb.base-url}/studies
  ?StudyInstanceUID={exact UID}
  &limit=1
  &includefield=0020000D
Accept: application/dicom+json
Authorization: Bearer {session access token}
traceparent: {active W3C context}
```

- [ ] **Step 1: Write failing deterministic query tests**

Assert exact endpoint, RFC 3986 encoding, one result limit, only StudyInstanceUID requested, preservation of the original UID and rejection of blank base URL. Confirm no import from viewer/worklist infrastructure.

- [ ] **Step 2: Implement the query builder and rerun its test**

Use a `LinkedHashMap` for stable order and the existing RFC 3986 encoding pattern; never log the resulting URI.

- [ ] **Step 3: Write failing response-parser tests**

Cover `[]` as false, one DICOM JSON object with tag `0020000D`, missing/wrong VR/value as invalid, mismatched UID as invalid and malformed/non-array JSON as invalid. The parser receives the expected UID only to validate equality and never returns metadata.

- [ ] **Step 4: Implement the parser and HTTP adapter**

Map 200 empty/valid arrays and 204 to false; treat Archive 404 as confirmed absence. Map 401/403 to structured auth reasons, 5xx/timeout/connection to unavailable reasons, and wrong content type/body/unexpected 2xx to invalid response. Propagate the exact access token and inject W3C trace through `W3cTraceContextInjector`.

- [ ] **Step 5: Run focused adapter tests**

```bash
rtk mise exec -- mvn -Dtest='ReportQido*Test,HttpStudyExistenceGatewayTest' test -Dquarkus.http.test-port=8082
```

Expected: PASS; requests use QIDO only, never WADO/STOW, and no test assertion/log prints the bearer or concrete clinical URI.

### Task 6: Build actor, ETag, representation and JSON-CSRF boundaries

**Files:**
- Create: `apps/backend/src/main/java/dev/blackice/reports/api/ReportRequest.java`
- Create: `apps/backend/src/main/java/dev/blackice/reports/api/ReportResponse.java`
- Create: `apps/backend/src/main/java/dev/blackice/reports/api/ReportRepresentationMapper.java`
- Create: `apps/backend/src/main/java/dev/blackice/reports/api/ReportEtag.java`
- Create: `apps/backend/src/main/java/dev/blackice/reports/api/CurrentReportActor.java`
- Create: `apps/backend/src/main/java/dev/blackice/reports/api/package-info.java`
- Modify: `apps/backend/src/main/resources/application.properties`
- Modify: `apps/backend/src/main/java/dev/blackice/shared/api/problem/ApiProblemResponseFilter.java`
- Modify: `apps/backend/src/test/java/dev/blackice/shared/api/problem/ApiProblemHttpTest.java`
- Test: `apps/backend/src/test/java/dev/blackice/reports/api/ReportRepresentationMapperTest.java`
- Test: `apps/backend/src/test/java/dev/blackice/reports/api/ReportEtagTest.java`
- Test: `apps/backend/src/test/java/dev/blackice/reports/api/CurrentReportActorTest.java`

**Interfaces:**

```java
public record ReportRequest(String content, ReportStatus status) {}

public record ReportResponse(
    String studyInstanceUid,
    String authorDisplayName,
    ReportStatus status,
    String content,
    boolean editable,
    Instant createdAt,
    Instant updatedAt,
    Instant finalizedAt
) {}

public final class ReportEtag {
    public static EntityTag fromVersion(long version);
    public static long parseStrongSingle(String ifMatch);
}
```

- [ ] **Step 1: Write failing response and ETag tests**

Prove the response has exactly the nine public fields, excludes internal ID/author/version, serializes `finalizedAt: null`, emits a strong quoted ETag and rejects missing, weak, wildcard, multi-value or malformed `If-Match`.

- [ ] **Step 2: Implement opaque ETag encoding**

Encode the 8-byte version using base64url without padding inside a strong quoted tag. Parsing accepts only that exact server format. Frontend code will never decode it.

- [ ] **Step 3: Write failing actor tests and implement OIDC extraction**

Mock `SecurityIdentity` and `JsonWebToken`. `subject` comes from `identity.getPrincipal().getName()`; display comes from `preferred_username`, falling back to subject when null/blank. Never accept either value from request data.

- [ ] **Step 4: Extend the existing signed CSRF filter to JSON**

Set:

```properties
quarkus.rest-csrf.require-form-url-encoded=false
```

This keeps verification in Quarkus REST CSRF, including `verify-token=true` and the configured signature key, while applying it to unsafe JSON requests as well as the existing multipart upload. Update `ApiProblemResponseFilter.isCsrfProtected` to classify every unsafe API method as CSRF-protected; a missing/mismatched token becomes `API_CSRF_INVALID`, while malformed JSON carrying a valid token remains `API_REQUEST_INVALID`. Add shared HTTP regression tests for both JSON cases and preserve GET without CSRF.

- [ ] **Step 5: Run focused boundary tests**

```bash
rtk mise exec -- mvn -Dtest='ReportRepresentationMapperTest,ReportEtagTest,CurrentReportActorTest' test -Dquarkus.http.test-port=8082
```

Expected: PASS.

### Task 7: Expose GET with 200/204, no-store and no Archive dependency

**Files:**
- Create: `apps/backend/src/main/java/dev/blackice/reports/api/ReportResource.java`
- Create: `apps/backend/src/main/java/dev/blackice/reports/api/ReportExceptionMappers.java`
- Modify: `apps/backend/src/main/java/dev/blackice/shared/api/problem/ApiFailureLogger.java`
- Test: `apps/backend/src/test/java/dev/blackice/reports/api/ReportResourceGetTest.java`

**Interfaces:**

```http
GET /api/studies/{studyInstanceUid}/report
200 application/json + ETag + Cache-Control: no-store
204 no body + Cache-Control: no-store
```

- [ ] **Step 1: Write failing HTTP tests**

With mocked use cases/actor, cover anonymous 401, non-`auth` 403, invalid UID 400, absent 204 with no body/ETag, existing 200 with exact public JSON/ETag, other-user `editable=false`, TraceID and no-store on both success forms.

Also verify GET succeeds without a CSRF cookie/header and neither invokes `AccessTokenProvider` nor any QIDO gateway.

- [ ] **Step 2: Implement the GET resource path**

Use `@Path("/api/studies")`, `@RolesAllowed("auth")`, `@GET @Path("/{studyInstanceUid}/report")` and `@Blocking`. Build all response headers in one helper so 200/204 cannot diverge.

- [ ] **Step 3: Map safe application failures**

Map invalid request to `API_REQUEST_INVALID`. Continue using the shared 401/403/500 boundary. Add only closed generic reasons such as `CONFLICT` and `VERSION_CONFLICT` to `ApiFailureLogger`; never pass exception messages.

- [ ] **Step 4: Run GET tests and inspect logs**

```bash
rtk mise exec -- mvn -Dtest=ReportResourceGetTest test -Dquarkus.http.test-port=8082
```

Expected: PASS; captured events contain route `/api/studies/{studyInstanceUid}/report`, method, code and trace only, not the UID/content/author.

### Task 8: Expose POST with QIDO validation and unique-race handling

**Files:**
- Modify: `apps/backend/src/main/java/dev/blackice/reports/api/ReportResource.java`
- Modify: `apps/backend/src/main/java/dev/blackice/reports/api/ReportExceptionMappers.java`
- Test: `apps/backend/src/test/java/dev/blackice/reports/api/ReportResourcePostTest.java`
- Test: `apps/backend/src/test/java/dev/blackice/reports/application/usecase/CreateStudyReportTransactionTest.java`

**Interfaces:**

```http
POST /api/studies/{studyInstanceUid}/report
Content-Type: application/json
X-CSRF-TOKEN: {cookie value}

{"content":"Texto do laudo.","status":"DRAFT"}
```

- [ ] **Step 1: Write failing POST contract tests**

Cover 201 DRAFT, 201 direct FINAL, exact `Location`, ETag/no-store/TraceID, missing or mismatched CSRF 403, invalid body/status/content 400, 32.001 code points 413, nonexistent study 404, existing/unique-race 409, Archive invalid response 502, unavailable/timeout 503, Archive auth 401/access 403 and unexpected sanitized 500.

- [ ] **Step 2: Implement POST and exception mapping**

Create the actor only from the current identity, fetch `AccessTokenProvider.accessToken()` server-side, construct validated inputs, call the create use case and return 201. Map conflicts exclusively to `API_RESOURCE_CONFLICT`; do not add another problem type.

- [ ] **Step 3: Prove QIDO runs outside a database transaction**

In a `@QuarkusTest`, inject the real use case/repository and a mock `StudyExistenceGateway`. At gateway invocation assert `TransactionSynchronizationRegistry.getTransactionStatus() == Status.STATUS_NO_TRANSACTION`; allow true and verify the insert occurs in its later short transaction.

- [ ] **Step 4: Run POST and transaction tests**

```bash
rtk mise exec -- mvn -Dtest='ReportResourcePostTest,CreateStudyReportTransactionTest' test -Dquarkus.http.test-port=8082
```

Expected: PASS with no QIDO call on an already existing report and no clinical values in captured logs/problem bodies.

### Task 9: Expose PUT and close backend security/architecture coverage

**Files:**
- Modify: `apps/backend/src/main/java/dev/blackice/reports/api/ReportResource.java`
- Modify: `apps/backend/src/main/java/dev/blackice/reports/api/ReportExceptionMappers.java`
- Modify: `apps/backend/src/main/java/dev/blackice/shared/api/problem/ApiProblemExceptionMappers.java`
- Modify: `apps/backend/src/test/java/dev/blackice/shared/api/problem/ApiProblemHttpTest.java`
- Modify: `apps/backend/src/test/java/dev/blackice/architecture/BackendArchitectureTest.java`
- Test: `apps/backend/src/test/java/dev/blackice/reports/api/ReportResourcePutTest.java`

**Interfaces:**

```http
PUT /api/studies/{studyInstanceUid}/report
If-Match: "{opaque strong tag}"
X-CSRF-TOKEN: {cookie value}

{"content":"Texto revisado.","status":"FINAL"}
```

- [ ] **Step 1: Write failing PUT contract tests**

Cover 200 DRAFT save and DRAFT→FINAL with a new ETag; missing/malformed/weak/wildcard/multiple `If-Match` as 400; missing CSRF as 403; other author 403; absent 404; FINAL conflict 409; stale version 412; oversized content 413; unexpected 500; and no QIDO/access-token call for every PUT path.

- [ ] **Step 2: Implement PUT and precise problem mappings**

Parse `If-Match` before the use case. Map terminal state to `API_RESOURCE_CONFLICT` and the failed version precondition to `API_RESOURCE_VERSION_CONFLICT`. Extend the shared framework-status map so empty 409/412 responses also select these catalog entries.

- [ ] **Step 3: Extend architecture tests for the reports module**

Assert:

```java
noClasses().that().resideInAPackage("dev.blackice.reports.application..")
    .should().dependOnClassesThat().resideInAnyPackage(
        "dev.blackice.reports.api..", "dev.blackice.reports.infrastructure..");

noClasses().that().resideInAPackage("dev.blackice.reports..")
    .should().dependOnClassesThat().resideInAnyPackage(
        "dev.blackice.viewer.infrastructure..", "dev.blackice.worklist.infrastructure..");
```

Also assert the reports application has no production class in its root package and reports never consumes security infrastructure directly.

- [ ] **Step 4: Run the complete backend phase**

```bash
rtk mise exec -- mvn test -Dquarkus.http.test-port=8082
```

Expected: PASS, including HTTP 200/201/204/400/401/403/404/409/412/413/500/502/503, PostgreSQL migration, atomic concurrency, CSRF and ArchUnit.

- [ ] **Step 5: Run DICOM domain review and stop for the human gate**

Use the project DICOM domain reviewer read-only over the Phase 2 diff. It must confirm exact UID preservation, QIDO-only existence, server-side bearer propagation, W3C trace, no pixel/payload persistence and no open DB transaction during QIDO. Present review and HTTP evidence to the human; do not begin Phase 3 until approved.

---

## Phase 3 — Vue reports, adaptive viewer and final MVP E2E

### Task 10: Implement the typed report API and share CSRF safely

**Files:**
- Create: `apps/frontend/src/shared/api/csrf.ts`
- Create: `apps/frontend/src/shared/api/csrf.spec.ts`
- Modify: `apps/frontend/src/features/ingest/ingest.api.ts`
- Modify: `apps/frontend/src/features/ingest/ingest.api.spec.ts`
- Create: `apps/frontend/src/features/reports/report.types.ts`
- Create: `apps/frontend/src/features/reports/report.api.ts`
- Create: `apps/frontend/src/features/reports/report.api.spec.ts`

**Interfaces:**

```ts
export type ReportStatus = 'DRAFT' | 'FINAL';

export interface StudyReport {
  studyInstanceUid: string;
  authorDisplayName: string;
  status: ReportStatus;
  content: string;
  editable: boolean;
  createdAt: string;
  updatedAt: string;
  finalizedAt: string | null;
}

export interface ReportSnapshot { report: StudyReport; etag: string }

export function fetchStudyReport(uid: string, signal?: AbortSignal): Promise<ReportSnapshot | null>;
export function createStudyReport(uid: string, content: string, status: ReportStatus): Promise<ReportSnapshot>;
export function updateStudyReport(uid: string, content: string, status: ReportStatus,
                                  etag: string): Promise<ReportSnapshot>;
```

- [ ] **Step 1: Extract the existing CSRF reader with failing shared tests**

Move `readCookie` and `fetchCsrfToken` from the ingest feature into `shared/api/csrf.ts`, because ingest and reports now have two real consumers. Update ingest imports/tests without changing behavior.

```bash
rtk mise exec -- pnpm test -- src/shared/api/csrf.spec.ts src/features/ingest/ingest.api.spec.ts
```

Expected before extraction: FAIL due to the missing shared module; after extraction: PASS.

- [ ] **Step 2: Write failing report API tests**

Cover 204→null without parsing a body, 200+required ETag, missing ETag/invalid JSON/invalid fields as `CLIENT_RESPONSE_INVALID`, POST/PUT JSON bodies, credentials, CSRF header, exact opaque `If-Match`, AbortError preservation, network failure and every cataloged response through the shared parser.

- [ ] **Step 3: Implement strict parsing and opaque ETag transport**

The parser validates all public fields/status/timestamps but never adds `authorId`, `id` or `version`. Mutations fetch CSRF immediately before the request, preserve the complete ETag string and never decode/increment/synthesize it.

- [ ] **Step 4: Run focused frontend API tests**

```bash
rtk mise exec -- pnpm test -- src/shared/api/csrf.spec.ts src/features/ingest/ingest.api.spec.ts src/features/reports/report.api.spec.ts
```

Expected: PASS.

### Task 11: Implement report state, dirty preservation and conflict recovery

**Files:**
- Create: `apps/frontend/src/features/reports/useStudyReport.ts`
- Create: `apps/frontend/src/features/reports/useStudyReport.spec.ts`

**Interfaces:**

```ts
export type ReportPhase = 'LOADING' | 'ABSENT' | 'READY' | 'SAVING' | 'ERROR';

export interface StudyReportController {
  phase: Readonly<Ref<ReportPhase>>;
  report: Readonly<Ref<StudyReport | null>>;
  content: Ref<string>;
  dirty: Readonly<Ref<boolean>>;
  codePointCount: Readonly<Ref<number>>;
  error: Readonly<Ref<ApiError | null>>;
  load(uid: string): Promise<void>;
  saveDraft(): Promise<void>;
  finalize(): Promise<void>;
  reloadServerVersion(): Promise<void>;
  dispose(): void;
}
```

- [ ] **Step 1: Write failing state-machine tests**

Cover load absent, load existing, request cancellation on UID change/dispose, POST on first save, PUT thereafter, direct FINAL, ETag replacement only after success, code-point count, dirty reset after accepted save and read-only state for third-party/final reports.

- [ ] **Step 2: Add failing 409/412 preservation tests**

For both conflicts assert local `content` and dirty state remain unchanged. For 412, `reloadServerVersion` performs an explicit GET only after the UI calls it; no automatic merge/reload occurs.

- [ ] **Step 3: Implement the in-memory controller**

Keep the unsaved string only in Vue memory. Do not access localStorage, sessionStorage, IndexedDB or a global store. Track the last accepted server content separately so dirty comparison remains correct after close/reopen.

- [ ] **Step 4: Run the composable test**

```bash
rtk mise exec -- pnpm test -- src/features/reports/useStudyReport.spec.ts
```

Expected: PASS, including stale-request suppression and local text preservation.

### Task 12: Build the accessible editor, confirmation and adaptive panel

**Files:**
- Create: `apps/frontend/src/features/reports/ReportEditor.vue`
- Create: `apps/frontend/src/features/reports/ReportEditor.spec.ts`
- Create: `apps/frontend/src/features/reports/ReportPanel.vue`
- Create: `apps/frontend/src/features/reports/ReportPanel.spec.ts`
- Create: `apps/frontend/src/features/reports/useReportLayout.ts`
- Create: `apps/frontend/src/features/reports/useReportLayout.spec.ts`

**Interfaces:**
- `ReportEditor` is presentational and emits `update:modelValue`, `save-draft` and `finalize`.
- `ReportPanel` owns `useStudyReport`, remains mounted while visually closed and exposes no Cornerstone object.
- Layout modes are `SPLIT` (`>=1440`), `DRAWER` (`1024–1439`) and `REPORT_ONLY` (`<1024`).

- [ ] **Step 1: Write failing editor tests**

Cover empty placeholder, exact plain-text binding, 32.000 code-point counter, disabled actions for blank/oversized/saving/read-only states, author/status/last-save metadata, `white-space: pre-wrap` for final display and absence of `v-html`.

- [ ] **Step 2: Implement explicit save/finalize UI**

`Salvar rascunho` never schedules timers. `Finalizar` opens a modal explaining irreversibility; confirm calls the atomic mutation, cancel changes nothing. The modal traps Tab/Shift+Tab, closes on Escape and restores focus to the trigger.

- [ ] **Step 3: Write failing layout/panel tests**

At 1920 expect open split; at 1366 and 1024 expect closed drawer; at 390 expect full-width open report. Cover visible close button, Escape closing only drawer, closing/reopening preserving text, retry by `retryPolicy`, 409/412 localized messages, 412 reload confirmation and aria-live announcements.

- [ ] **Step 4: Implement layout state and bounded resizing**

Use `matchMedia` listeners with cleanup. In SPLIT, clamp the report width to a usable editor minimum and the measured workspace maximum that leaves the `.viewport-area` at least 720 px. Resizing changes CSS/layout only; it does not emit viewer lifecycle events.

- [ ] **Step 5: Add navigation-loss protection**

While dirty, register `beforeunload` and Vue Router leave/update guards. Navigating to another study or Worklist requires explicit confirmation; closing only the panel does not prompt or discard. Remove listeners on dispose.

- [ ] **Step 6: Run component/layout tests**

```bash
rtk mise exec -- pnpm test -- \
  src/features/reports/ReportEditor.spec.ts \
  src/features/reports/ReportPanel.spec.ts \
  src/features/reports/useReportLayout.spec.ts
```

Expected: PASS with keyboard/focus/dirty behavior covered.

### Task 13: Compose reports into ViewerPage without flattening or recreating images

**Files:**
- Modify: `apps/frontend/src/features/viewer/ViewerPage.vue`
- Modify: `apps/frontend/src/features/viewer/ViewerPage.spec.ts`
- Modify: `apps/frontend/src/features/viewer/useViewerCapability.ts`
- Modify: `apps/frontend/src/features/viewer/useViewerCapability.spec.ts`
- Modify: `apps/frontend/src/features/viewer/DicomViewport.spec.ts`

**Interfaces:**
- `ViewerPage` passes only the route `studyUid` to `ReportPanel`.
- Viewer/report loads and errors remain parallel and isolated.
- `VIEWER_MEDIA_QUERY` becomes `(min-width: 1024px)` to match the approved report-only threshold.

- [ ] **Step 1: Write failing capability regression tests**

Change expectations so 1024 is allowed and 1023, including 768 landscape, is blocked. Preserve dynamic listener cleanup and the rule that user agent is never inspected.

- [ ] **Step 2: Write failing composition/isolation tests**

Cover report loading while viewer summary loads/fails, viewer remaining visible on report error, report remaining visible on viewer error, REPORT_ONLY below 1024 and parallel cancellation on UID changes.

- [ ] **Step 3: Prove panel toggling does not recreate Cornerstone**

Mount with mocked async `DicomViewport`, open/close/reopen the report repeatedly and assert the viewport mount/init count stays `1` and unmount count stays `0` until route/component disposal. Also assert split resizing does not invoke reset/select/load calls.

- [ ] **Step 4: Restructure the page and CSS**

Render report independently of `study !== null`; use `v-show`/CSS visibility for open state and overlay positioning for DRAWER. In SPLIT, place the mounted report beside the viewer workspace. In REPORT_ONLY, show the existing larger-screen message alongside the full report and do not request series instances/frames.

- [ ] **Step 5: Run viewer and report integration tests**

```bash
rtk mise exec -- pnpm test -- \
  src/features/viewer/useViewerCapability.spec.ts \
  src/features/viewer/ViewerPage.spec.ts \
  src/features/viewer/DicomViewport.spec.ts \
  src/features/reports
```

Expected: PASS; repeated panel operations never remount the mocked viewport.

### Task 14: Add a second actor, full MVP E2E, CI gate and operational docs

**Files:**
- Modify: `infra/keycloak/configure-blackice-container.sh`
- Modify: `infra/keycloak/README.md`
- Create: `apps/frontend/e2e/reports.spec.ts`
- Modify: `apps/frontend/playwright.config.ts`
- Modify: `apps/frontend/package.json`
- Modify: `.github/workflows/verify.yml`
- Modify: `apps/backend/README.md`
- Modify: `apps/frontend/README.md`
- Modify: `infra/README.md`
- Modify: `docs/architecture/project-structure.md`

**Interfaces:**
- Adds idempotent local test user `dr.leitor` / `teste123` with role `auth` and no product-specific elevated role.
- Adds Playwright projects for 1366×768 and 1024×768 only for `reports.spec.ts`; existing desktop 1920×1080 and mobile 390×844 remain.
- Adds `test:e2e:reports` and a CI job that exercises the composed stack.

- [ ] **Step 1: Extend Keycloak bootstrap idempotently**

Create `dr.leitor`, set a non-temporary test password and assign the existing realm role `auth` using the same safe Admin REST pattern as `dr.teste`. Update README to state both identities and why the second exists.

- [ ] **Step 2: Write the failing synthetic four-flow E2E**

Using `createSyntheticCtSlice`, execute in order:

1. login as `dr.teste`;
2. STOW one synthetic study;
3. find it in Worklist through QIDO;
4. open and observe a rendered viewer on eligible projects;
5. observe empty editor after report GET 204;
6. save DRAFT and reload;
7. open a second same-author page, update there, then prove the first stale ETag gets 412 without losing local text;
8. reload server version with confirmation, finalize and reload into immutable view;
9. open a fresh browser context as `dr.leitor`, read the report, verify mutation controls are absent and a direct malicious PUT receives 403;
10. verify no Archive URL is called by the browser and report text/UID/token/pixels are absent from localStorage, sessionStorage and IndexedDB.

- [ ] **Step 3: Add responsive E2E assertions**

At 1920 verify open resizable split and at least 720 px viewport; at 1366/1024 verify closed overlay drawer that does not change the viewport element identity/bounds after close; at 390 verify report-only and no instances/frames requests.

- [ ] **Step 4: Register scripts and project matrix**

Add:

```json
"test:e2e:reports": "playwright test e2e/reports.spec.ts"
```

Add `reports.spec.ts` to top-level `testMatch`. Configure medium/tablet projects with `testMatch: ['reports.spec.ts']` so unrelated E2E suites do not quadruple.

- [ ] **Step 5: Add the CI E2E job with failure artifacts and unconditional teardown**

The job depends on catalog/backend/frontend, copies `infra/.env.example` to the ignored `infra/.env`, builds the backend package, starts all three Compose files, waits for product DB/Keycloak/Archive/backend, runs the idempotent Keycloak configurator, installs Playwright Chromium and executes the full configured E2E suite with `CI=true`. Upload `playwright-report/` and `test-results/playwright/` on failure and run `docker compose down --volumes` under `if: always()`.

No production secret is used: CI values come only from the example file and the isolated runner is destroyed after the job.

- [ ] **Step 6: Update operational and architecture docs**

Document product datasource/Flyway startup and readiness in backend/infra READMEs, report E2E commands and viewport matrix in frontend README, and add the concrete `reports` backend module/frontend feature trees to `project-structure.md`. Keep EVO-011/EVO-012 as links, not implementation instructions.

- [ ] **Step 7: Run the local E2E gate**

Working directory: `apps/frontend/`

```bash
rtk mise exec -- pnpm test:e2e:reports
```

Expected: PASS in 1920, 1366, 1024 and 390 projects with only synthetic DICOM data.

### Task 15: Execute final verification, reviews and one Graphify update

**Files:**
- Verify all feature files above.
- Update once, after approval: `graphify-out/`

**Interfaces:**
- Produces final evidence for catalog, backend, frontend, Compose, E2E, DICOM correctness, privacy and graph synchronization.

- [ ] **Step 1: Run catalog verification**

Working directory: `.problem-catalog/`

```bash
rtk mise exec -- pnpm test
rtk mise exec -- pnpm check
```

Expected: PASS and no generated drift.

- [ ] **Step 2: Run complete backend verification**

Working directory: `apps/backend/`

```bash
rtk mise exec -- mvn test -Dquarkus.http.test-port=8082
```

Expected: PASS against Dev Services PostgreSQL, including migration, HTTP, security, QIDO and ArchUnit.

- [ ] **Step 3: Run complete frontend verification**

Working directory: `apps/frontend/`

```bash
rtk mise exec -- pnpm test
rtk mise exec -- pnpm build
```

Expected: PASS with no TypeScript/exhaustiveness error.

- [ ] **Step 4: Validate Compose and full Playwright gate**

Working directory: `infra/`

```bash
rtk docker compose -f compose.yml -f dcm4chee/compose.yml -f compose.apps.yml config --quiet
```

Working directory: `apps/frontend/`

```bash
rtk mise exec -- pnpm exec playwright test
```

Expected: Compose config exits 0 and every configured E2E flow passes.

- [ ] **Step 5: Run final read-only reviews**

Run the DICOM domain reviewer over the complete diff and a code review focused on authorization, concurrency, CSRF, clinical-data leakage, accessibility and Cornerstone lifecycle. Resolve findings through focused failing tests, then repeat Steps 1–4.

- [ ] **Step 6: Present the final human gate**

Demonstrate: 204 absent, DRAFT persistence, stale 412 preservation, direct/follow-up FINAL, other-user read-only/403, Archive-independent GET/PUT, four responsive widths and viewer non-recreation. Obtain explicit approval before Graphify.

- [ ] **Step 7: Run the single final semantic Graphify update**

After implementation, suites, reviews and the human gate are stable, follow `.agents/skills/graphify/SKILL.md` and `docs/architecture/graphify.md` to execute one incremental `. --update`. In a linked worktree this is manual. Review every `graphify-out/` diff and rerun `rtk git diff --check`.

- [ ] **Step 8: Report final status without committing**

Run:

```bash
rtk git status --short
rtk git diff --check
```

Expected: only intended source/docs/generated graph changes, no whitespace errors and no clinical fixtures beyond synthetic data. Report results and wait for explicit human commit authorization.
