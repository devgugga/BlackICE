# Tratamento de erros e catálogo de problemas — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Substituir atomicamente os contratos de erro atuais por Problem Details RFC 9457, catálogo oficial com URNs UUIDv5 e TraceID W3C ponta a ponta.

**Architecture:** `docs/contracts/problems/catalog.json` será a fonte canônica e um tooling Node ESM gerará Java, TypeScript, lock e documentação. O Quarkus traduzirá falhas nas fronteiras HTTP sem contaminar a aplicação, enquanto um parser compartilhado no Vue validará os problemas e fornecerá mensagens PT-BR tipadas. OpenTelemetry será usado somente para criar e propagar contexto; toda exportação remota ficará desabilitada.

**Tech Stack:** Node 24, pnpm 11.22.0, JSON Schema 2020-12, Java 21, Quarkus 3.37.4, OpenTelemetry, Jakarta REST, Vert.x, Vue 3.5, TypeScript 6, Vitest 4 e Playwright 1.62.

**Spec:** `docs/superpowers/specs/2026-08-23-error-handling-problem-catalog-design.md`

## Global Constraints

- Leia a spec inteira e `docs/architecture/project-structure.md` antes da primeira tarefa.
- Não existe compatibilidade: backend, frontend e testes trocam de contrato na mesma branch; não criar alias, adapter, flag ou versão paralela.
- Tipos usam `urn:uuid`, com UUIDv5 derivado de `blackice.problem.v1:{code}` dentro de um único namespace persistido.
- Nunca fornecer UUID por parâmetro ao comando `add`; nunca editar arquivos gerados manualmente.
- Domínio e aplicação não importam HTTP, OpenTelemetry nem tipos Jakarta REST.
- `API_*` representa resposta HTTP; `CLIENT_*` representa falha local do browser; cancelamento intencional não é problema.
- Todo erro JSON `4xx/5xx` sob `/api` usa `application/problem+json`; `/api/login` mantém seu redirect OIDC intencional.
- Toda resposta `/api`, inclusive sucesso, recebe `X-Trace-ID`; o corpo de problema contém o mesmo `traceId`.
- Não confiar em `X-Trace-ID` recebido. `traceparent` W3C é a única entrada canônica e deve continuar até o DICOMweb.
- Configurar `quarkus.otel.exporter.otlp.enabled=false` e `quarkus.observability.dev-resources=false`; não adicionar Collector, Jaeger, Tempo ou backend de traces.
- Não registrar corpo, query clínica, token, filename, UID, payload DICOMweb ou mensagem bruta de exceção.
- Alterações nos adapters DICOMweb e na validação DICOM exigem o revisor `dicom-domain-reviewer` antes do gate da Fase 3.
- Cada fase termina em gate humano. Não iniciar a fase seguinte sem aprovação.
- Em cada commit, adicionar somente os arquivos enumerados na tarefa; preservar mudanças concorrentes de outros agentes.
- Antes de cada alegação de conclusão, executar os comandos de verificação da tarefa; antes de commit, atualizar Graphify conforme `docs/architecture/graphify.md`.
- Os títulos indicados nas etapas de commit não substituem o corpo obrigatório:
  criar o commit de produto conforme `docs/domains/git/commit-conventions.md` e,
  em seguida, criar o segundo commit focado do Graphify com a mensagem canônica.

---

## Mapa de arquivos

### Fonte canônica e tooling

- `docs/domains/problem-catalog/README.md`: entrada do Domain Pack e roteamento para as políticas.
- `docs/domains/problem-catalog/classification.md`: decisão API, CLIENT, resultado ou cancelamento e regra de reutilização.
- `docs/domains/problem-catalog/registry.md`: gramática, campos, UUIDv5, lock, depreciação e gates.
- `docs/domains/problem-catalog/security.md`: textos e extensões seguros, dados proibidos e política de logging.
- `docs/contracts/problems/catalog.json`: registry machine-readable.
- `docs/contracts/problems/catalog.schema.json`: estrutura e condicionais API/CLIENT.
- `docs/contracts/problems/catalog.lock.json`: snapshot dos campos imutáveis e fingerprints.
- `docs/contracts/problems/catalog.md`: catálogo humano gerado.
- `docs/contracts/problems/extensions/dicom-validation-violations.schema.json`: extensão `violations`.
- `.problem-catalog/mise.toml`: Node e pnpm fixados para o tooling.
- `.problem-catalog/package.json`: comandos `test`, `check`, `generate`, `add` e `deprecate`.
- `.problem-catalog/pnpm-lock.yaml`: dependências exatas do tooling.
- `.problem-catalog/src/catalog.js`: leitura, normalização, ordenação e validação do registry.
- `.problem-catalog/src/uuid-v5.js`: derivação RFC 4122/9562 com SHA-1 e bits de versão/variante.
- `.problem-catalog/src/lock.js`: criação e comparação do lock append-only.
- `.problem-catalog/src/generate-java.js`: geração de `ProblemType.java` e `ProblemExtensions.java`.
- `.problem-catalog/src/generate-typescript.js`: geração dos tipos e extensões TypeScript.
- `.problem-catalog/src/generate-markdown.js`: geração de `catalog.md`.
- `.problem-catalog/src/cli.js`: CLI não interativa.
- `.problem-catalog/test/*.test.js`: testes unitários, golden files e fixtures negativas.
- `.agents/skills/problem-catalog/SKILL.md` e `.claude/skills/problem-catalog/SKILL.md`: wrappers finos do workflow.

### Backend

- `dev.blackice.shared.api.problem.generated`: metadados do catálogo gerados.
- `dev.blackice.shared.api.problem.ApiProblem`: representação RFC 9457.
- `dev.blackice.shared.api.problem.ApiProblemFactory`: cria payload seguro e valida extensões.
- `dev.blackice.shared.api.problem.ProblemResponseFactory`: produz `Response` com media type correto.
- `dev.blackice.shared.api.problem.TraceContext`: lê TraceID e SpanID do span ativo.
- `dev.blackice.shared.api.problem.ApiTraceResponseFilter`: adiciona `X-Trace-ID` a respostas REST.
- `dev.blackice.shared.api.problem.ApiProblemExceptionMappers`: mapeia falhas Jakarta REST e fallback inesperado.
- `dev.blackice.shared.api.problem.ApiProblemResponseFilter`: converte respostas vazias do framework.
- `dev.blackice.shared.api.problem.ApiHttpFailureHandler`: cobre falhas Vert.x anteriores ao REST.
- `dev.blackice.security.api.ApiJavaScriptRequestChecker`: pede desafio `401` para APIs e preserva `/api/login`.
- `dev.blackice.shared.infrastructure.telemetry.W3cTraceContextInjector`: injeta `traceparent` nos dois adapters JDK.
- `ingest.api.IngestResponseMapper` e `worklist.api.WorklistExceptionMappers`: traduções específicas das features.

### Frontend

- `src/shared/api/problems/problem-types.generated.ts`: catálogo gerado para runtime e tipos.
- `src/shared/api/problems/problem-extensions.generated.ts`: extensões discriminadas geradas.
- `src/shared/api/problems/api-problem.ts`: shape RFC recebido.
- `src/shared/api/problems/api-error.ts`: erro seguro usado pelas features.
- `src/shared/api/problems/parse-problem.ts`: parser comum de `fetch` e XHR.
- `src/shared/api/problems/problem-messages.pt-BR.ts`: mensagens exaustivas.
- APIs, composables, páginas e testes de `session`, `worklist` e `ingest`: consumidores migrados.

---

## Fase 1 — catálogo, codegen e workflow de agentes

### Task 1: Validar o registry e derivar identidades

**Files:**
- Create: `.problem-catalog/mise.toml`
- Create: `.problem-catalog/package.json`
- Create: `.problem-catalog/pnpm-lock.yaml`
- Create: `.problem-catalog/src/catalog.js`
- Create: `.problem-catalog/src/uuid-v5.js`
- Create: `.problem-catalog/src/lock.js`
- Create: `.problem-catalog/src/cli.js`
- Create: `.problem-catalog/test/catalog.test.js`
- Create: `.problem-catalog/test/uuid-v5.test.js`
- Create: `.problem-catalog/test/lock.test.js`
- Create: `.problem-catalog/test/fixtures/valid-catalog.json`
- Create: `.problem-catalog/test/fixtures/invalid-api-without-http.json`
- Create: `.problem-catalog/test/fixtures/invalid-client-with-http.json`
- Create: `docs/contracts/problems/catalog.schema.json`
- Create: `docs/contracts/problems/extensions/dicom-validation-violations.schema.json`

**Interfaces:**
- Produces: `loadCatalog(path): Promise<Catalog>`, `validateCatalog(catalog): ValidationResult`, `deriveProblemUrn(namespaceUuid, code): string`, `createLock(catalog, extensionSchemas): CatalogLock` e CLI `check|add|deprecate`.
- `CatalogEntry` usa exatamente `type`, `code`, `scope`, `description`, `httpStatus`, `title`, `detail`, `retryPolicy`, `owner`, `extensionsSchemaRef`, `status` e `replacedBy`.

- [ ] **Step 1: Criar o pacote fixado e escrever testes que falham**

```json
{
  "name": "@blackice/problem-catalog",
  "private": true,
  "type": "module",
  "packageManager": "pnpm@11.22.0",
  "scripts": {
    "test": "node --test",
    "check": "node src/cli.js check",
    "generate": "node src/cli.js generate",
    "add": "node src/cli.js add",
    "deprecate": "node src/cli.js deprecate"
  },
  "dependencies": {
    "ajv": "8.17.1",
    "ajv-formats": "3.0.1"
  }
}
```

Em `uuid-v5.test.js`, usar o vetor conhecido namespace DNS
`6ba7b810-9dad-11d1-80b4-00c04fd430c8` + `www.widgets.com` e exigir
`urn:uuid:21f7f8de-8051-5b89-8680-0195ef798b6a`. Em `catalog.test.js`, exigir
que API sem HTTP e CLIENT com HTTP sejam rejeitados. Em `lock.test.js`, exigir
rejeição de remoção, mudança de `code`, `type`, `scope`, `httpStatus`,
`retryPolicy` e fingerprint da extensão.

- [ ] **Step 2: Executar os testes e confirmar RED**

Run: `cd .problem-catalog && mise install && mise exec -- pnpm install && mise exec -- pnpm test`

Expected: FAIL porque os módulos e schemas ainda não existem.

- [ ] **Step 3: Implementar UUIDv5 e validação condicional mínima**

```js
import { createHash } from 'node:crypto';

function namespaceBytes(uuid) {
  return Buffer.from(uuid.replaceAll('-', ''), 'hex');
}

function formatUuid(bytes) {
  const hex = Buffer.from(bytes).toString('hex');
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}

export function deriveProblemUrn(namespaceUuid, code) {
  const name = `blackice.problem.v1:${code}`;
  const bytes = createHash('sha1')
    .update(namespaceBytes(namespaceUuid))
    .update(Buffer.from(name, 'utf8'))
    .digest();
  bytes[6] = (bytes[6] & 0x0f) | 0x50;
  bytes[8] = (bytes[8] & 0x3f) | 0x80;
  return `urn:uuid:${formatUuid(bytes.subarray(0, 16))}`;
}
```

O schema deve usar `if/then` para exigir os campos HTTP em `scope=API` e
proibi-los em `scope=CLIENT`; deve rejeitar `AUTOMATIC`, owners desconhecidos,
códigos fora de `^(API|CLIENT)_[A-Z0-9]+(?:_[A-Z0-9]+)+$`, URNs não UUID e
extensions inexistentes.

- [ ] **Step 4: Implementar lock e transições append-only**

```js
export function assertAllowedTransition(previousLock, nextCatalog, schemas) {
  assertNoRemovedEntries(previousLock, nextCatalog);
  assertImmutableFields(previousLock, nextCatalog);
  assertOnlyActiveToDeprecated(previousLock, nextCatalog);
  assertExtensionFingerprints(previousLock, schemas);
}
```

`deprecate --code CODE --replaced-by CODE` só aceita substituto existente e
ativo. `add` exige todos os campos semânticos, deriva a URN e recusa qualquer
flag de UUID.

- [ ] **Step 5: Executar testes e confirmar GREEN**

Run: `cd .problem-catalog && mise exec -- pnpm test`

Expected: todos os testes Node passam sem escrita fora de diretório temporário.

- [ ] **Step 6: Commit da tarefa**

```bash
git add .problem-catalog docs/contracts/problems/catalog.schema.json docs/contracts/problems/extensions
```

Criar o commit com o título
`✨ cria validador e identidades do catálogo de problemas` e corpo obrigatório
conforme a convenção canônica.

### Task 2: Popular o catálogo e gerar Java, TypeScript, lock e Markdown

**Files:**
- Create: `docs/contracts/problems/catalog.json`
- Create: `docs/contracts/problems/catalog.lock.json`
- Create: `docs/contracts/problems/catalog.md`
- Create: `.problem-catalog/src/generate-java.js`
- Create: `.problem-catalog/src/generate-typescript.js`
- Create: `.problem-catalog/src/generate-markdown.js`
- Create: `.problem-catalog/test/generation.test.js`
- Create: `.problem-catalog/test/fixtures/golden/ProblemType.java`
- Create: `.problem-catalog/test/fixtures/golden/ProblemExtensions.java`
- Create: `.problem-catalog/test/fixtures/golden/problem-types.generated.ts`
- Create: `.problem-catalog/test/fixtures/golden/problem-extensions.generated.ts`
- Create: `apps/backend/src/main/java/dev/blackice/shared/api/problem/generated/ProblemType.java`
- Create: `apps/backend/src/main/java/dev/blackice/shared/api/problem/generated/ProblemExtensions.java`
- Create: `apps/frontend/src/shared/api/problems/problem-types.generated.ts`
- Create: `apps/frontend/src/shared/api/problems/problem-extensions.generated.ts`

**Interfaces:**
- Consumes: `deriveProblemUrn`, `validateCatalog` e `createLock` da Task 1.
- Produces: enum Java `ProblemType`, interface selada Java `ProblemExtensions`, constantes TypeScript `PROBLEM_TYPES`, unions `ProblemCode|ApiProblemCode|ClientProblemCode` e schemas TypeScript de extensão.

- [ ] **Step 1: Escrever golden tests e testes de idempotência**

```js
test('generate is byte-for-byte deterministic', async () => {
  const first = await generateInMemory(validCatalog);
  const second = await generateInMemory(validCatalog);
  assert.deepEqual(second, first);
  assert.equal(Object.values(first).some((text) => /generatedAt|timestamp/i.test(text)), false);
});

test('check reports drift without writing', async () => {
  const result = await checkGeneratedFiles({ mutate: false });
  assert.equal(result.ok, false);
  assert.deepEqual(result.changedPaths, ['apps/frontend/src/shared/api/problems/problem-types.generated.ts']);
});
```

- [ ] **Step 2: Executar os testes e confirmar RED**

Run: `cd .problem-catalog && mise exec -- pnpm test`

Expected: FAIL porque os geradores e arquivos golden ainda não existem.

- [ ] **Step 3: Implementar geradores determinísticos**

`ProblemType` deve expor `URI type()`, `String code()`, `ProblemScope scope()`,
`Integer httpStatus()`, `String title()`, `String detail()` e
`RetryPolicy retryPolicy()`. `ProblemExtensions` deve permitir somente `None` e
`DicomValidationViolations(List<Violation>)`, fornecer
`static ProblemExtensions none()`, e definir `Violation(int itemIndex, String
code, String message)`.

No TypeScript, gerar o shape abaixo com `as const`:

```ts
export const PROBLEM_TYPES = {
  API_ARCHIVE_UNAVAILABLE: {
    type: 'urn:uuid:67b5e980-2f1b-5bd5-8c75-436097503108',
    scope: 'API',
    httpStatus: 503,
    retryPolicy: 'MANUAL',
  },
} as const;

export type ProblemCode = keyof typeof PROBLEM_TYPES;
```

A URN do exemplo pertence somente ao fixture golden. O catálogo real recebe o
namespace aleatório criado por `generate` no primeiro bootstrap e todas as URNs
reais são recalculadas e persistidas pelo tooling.

- [ ] **Step 4: Criar o catálogo inicial usando somente a CLI**

Inicializar o documento vazio com `generate` e executar `add` para os 21 códigos
aprovados: `API_REQUEST_INVALID`, `API_UPLOAD_EMPTY`, `API_SEARCH_INVALID`,
`API_AUTHENTICATION_REQUIRED`, `API_ACCESS_DENIED`, `API_CSRF_INVALID`,
`API_RESOURCE_NOT_FOUND`, `API_METHOD_NOT_ALLOWED`,
`API_REPRESENTATION_NOT_ACCEPTABLE`, `API_PAYLOAD_TOO_LARGE`,
`API_SEARCH_TOO_BROAD`, `API_MEDIA_TYPE_UNSUPPORTED`,
`API_DICOM_VALIDATION_FAILED`, `API_INTERNAL_ERROR`,
`API_ARCHIVE_RESPONSE_INVALID`, `API_ARCHIVE_UNAVAILABLE`,
`CLIENT_NETWORK_UNAVAILABLE`, `CLIENT_REQUEST_TIMEOUT`,
`CLIENT_RESPONSE_INVALID`, `CLIENT_CSRF_COOKIE_MISSING` e
`CLIENT_UNEXPECTED_ERROR`. Usar status, textos, retry policies, owners e extensão
exatamente como enumerados na spec.

- [ ] **Step 5: Gerar artefatos e confirmar GREEN**

Run: `cd .problem-catalog && mise exec -- pnpm generate && mise exec -- pnpm test && mise exec -- pnpm check`

Expected: geração sem timestamps; segunda geração não altera bytes; `check`
retorna exit code 0.

- [ ] **Step 6: Commit da tarefa**

```bash
git add docs/contracts/problems .problem-catalog apps/backend/src/main/java/dev/blackice/shared/api/problem/generated apps/frontend/src/shared/api/problems
```

Criar o commit com o título
`✨ gera catálogo oficial de problemas para Java e TypeScript` e corpo
obrigatório conforme a convenção canônica.

### Task 3: Publicar o Domain Pack, skills e verificação contínua

**Files:**
- Create: `docs/domains/problem-catalog/README.md`
- Create: `docs/domains/problem-catalog/classification.md`
- Create: `docs/domains/problem-catalog/registry.md`
- Create: `docs/domains/problem-catalog/security.md`
- Create: `.agents/skills/problem-catalog/SKILL.md`
- Create: `.claude/skills/problem-catalog/SKILL.md`
- Create: `.github/workflows/verify.yml`
- Modify: `AGENTS.md`
- Modify: `docs/domains/README.md`
- Modify: `docs/architecture/project-structure.md`

**Interfaces:**
- Consumes: CLI e contrato das Tasks 1–2.
- Produces: workflow tool-agnostic para agentes e job CI que executa `pnpm check` antes dos builds.

- [ ] **Step 1: Escrever teste documental do wrapper fino**

```bash
test "$(rg -l 'docs/domains/problem-catalog' .agents/skills/problem-catalog/SKILL.md .claude/skills/problem-catalog/SKILL.md | wc -l)" -eq 2
! rg -n 'API_ARCHIVE_UNAVAILABLE|UUIDv5 implementation|httpStatus.*503' .agents/skills/problem-catalog .claude/skills/problem-catalog
```

Expected antes dos arquivos: FAIL.

- [ ] **Step 2: Criar o Domain Pack sem duplicar a spec**

Documentar a árvore decisória:

```text
Resposta HTTP observável? API
Falha local de transporte/parser/browser? CLIENT
Operação concluída completa ou parcialmente? resultado
Usuário cancelou? cancelamento
```

Documentar busca por significado/status/ação, imutabilidade, depreciação, dados
proibidos e a exigência de spec aprovada para `add` ou `deprecate`.

- [ ] **Step 3: Criar e testar as skills**

Durante a execução, usar `skill-creator` e `superpowers:writing-skills`. Cada
wrapper deve mandar ler completamente o Domain Pack, consultar o catálogo,
mostrar reutilização, executar a CLI, gerar, validar, exibir diff e parar sem gate.
Nenhum wrapper contém a lista de códigos ou regras copiadas.

- [ ] **Step 4: Adicionar CI com três jobs explícitos**

Cada job do workflow `verify.yml` usa `actions/checkout@v4` e
`jdx/mise-action@v3` com cache habilitado. Os jobs executam:

```yaml
- run: cd .problem-catalog && mise exec -- pnpm install --frozen-lockfile
- run: cd .problem-catalog && mise exec -- pnpm test && mise exec -- pnpm check
- run: cd apps/backend && mise exec -- mvn test -Dquarkus.http.test-port=8082
- run: cd apps/frontend && mise exec -- pnpm install --frozen-lockfile && mise exec -- pnpm test && mise exec -- pnpm build
```

- [ ] **Step 5: Validar documentação, skills e CI**

Run: `cd .problem-catalog && mise exec -- pnpm check && cd .. && git diff --check && rg -n 'problem-catalog' AGENTS.md docs/domains/README.md docs/architecture/project-structure.md .agents/skills .claude/skills`

Expected: `check` e whitespace passam; as duas skills apontam para o Domain Pack.

- [ ] **Step 6: Commit e Gate 1**

```bash
git add AGENTS.md docs/domains/README.md docs/domains/problem-catalog docs/architecture/project-structure.md .agents/skills/problem-catalog .claude/skills/problem-catalog .github/workflows/verify.yml
```

Criar o commit com o título
`📝 documenta governança do catálogo de problemas` e corpo obrigatório conforme
a convenção canônica.

Apresentar ao humano catálogo, namespace, URNs geradas, lock, generated files,
skills e resultado do CI local. Parar até aprovação do Gate 1.

---

## Fase 2 — Problem Details e TraceID no Quarkus

### Task 4: Criar o contrato compartilhado e a fronteira de trace

**Files:**
- Modify: `apps/backend/pom.xml`
- Modify: `apps/backend/src/main/resources/application.properties`
- Create: `apps/backend/src/main/java/dev/blackice/shared/api/problem/ApiProblem.java`
- Create: `apps/backend/src/main/java/dev/blackice/shared/api/problem/ApiProblemFactory.java`
- Create: `apps/backend/src/main/java/dev/blackice/shared/api/problem/ProblemResponseFactory.java`
- Create: `apps/backend/src/main/java/dev/blackice/shared/api/problem/TraceContext.java`
- Create: `apps/backend/src/main/java/dev/blackice/shared/api/problem/ApiTraceResponseFilter.java`
- Create: `apps/backend/src/test/java/dev/blackice/shared/api/problem/ApiProblemFactoryTest.java`
- Create: `apps/backend/src/test/java/dev/blackice/shared/api/problem/ApiTraceResponseFilterTest.java`
- Modify: `apps/backend/src/test/java/dev/blackice/architecture/BackendArchitectureTest.java`

**Interfaces:**
- Produces: `ApiProblemFactory.create(ProblemType, ProblemExtensions): ApiProblem`, `ProblemResponseFactory.response(ProblemType, ProblemExtensions): Response`, `TraceContext.traceId(): String` e `TraceContext.spanId(): String`.
- `ApiProblem` possui membros fixos `URI type`, `String title`, `int status`, `String detail`, `String code`, `String traceId` e `@JsonAnyGetter Map<String,Object> extensions`.

- [ ] **Step 1: Escrever testes RED do payload e da extensão**

```java
ApiProblem problem = factory.create(
    ProblemType.API_DICOM_VALIDATION_FAILED,
    new ProblemExtensions.DicomValidationViolations(List.of(
        new ProblemExtensions.Violation(0, "MALFORMED_DICOM", "The file is not valid DICOM.")
    ))
);
assertEquals("API_DICOM_VALIDATION_FAILED", problem.code());
assertEquals(422, problem.status());
assertEquals(traceId, problem.traceId());
assertFalse(problem.extensions().containsKey("filename"));
```

Também exigir rejeição quando a extensão não pertence ao tipo e ausência de
`instance` e `retryPolicy` na serialização.

- [ ] **Step 2: Executar os testes e confirmar RED**

Run: `cd apps/backend && mise exec -- mvn -Dtest=ApiProblemFactoryTest,ApiTraceResponseFilterTest test -Dquarkus.http.test-port=8082`

Expected: FAIL porque as classes ainda não existem.

- [ ] **Step 3: Adicionar OpenTelemetry sem exporter**

Adicionar `io.quarkus:quarkus-opentelemetry` e configurar:

```properties
quarkus.application.name=blackice-backend
quarkus.otel.exporter.otlp.enabled=false
quarkus.observability.dev-resources=false
quarkus.log.console.format=%d{yyyy-MM-dd'T'HH:mm:ss.SSSXXX} %-5p traceId=%X{traceId} spanId=%X{spanId} [%c{2.}] (%t) %s%e%n
```

- [ ] **Step 4: Implementar factory, response e filtro de trace**

```java
public Response response(ProblemType type, ProblemExtensions extensions) {
    ApiProblem problem = apiProblemFactory.create(type, extensions);
    return Response.status(problem.status())
        .type("application/problem+json")
        .header("X-Trace-ID", problem.traceId())
        .entity(problem)
        .build();
}
```

O filtro só atua em paths `/api` e sempre substitui qualquer `X-Trace-ID`
anterior pelo ID do span ativo. Nenhuma classe de aplicação importa este pacote.

- [ ] **Step 5: Confirmar GREEN e regras de arquitetura**

Run: `cd apps/backend && mise exec -- mvn -Dtest=ApiProblemFactoryTest,ApiTraceResponseFilterTest,BackendArchitectureTest test -Dquarkus.http.test-port=8082`

Expected: testes passam; ArchUnit continua proibindo dependência `application -> api`.

- [ ] **Step 6: Commit da tarefa**

```bash
git add apps/backend/pom.xml apps/backend/src/main/resources/application.properties apps/backend/src/main/java/dev/blackice/shared/api/problem/ApiProblem.java apps/backend/src/main/java/dev/blackice/shared/api/problem/ApiProblemFactory.java apps/backend/src/main/java/dev/blackice/shared/api/problem/ProblemResponseFactory.java apps/backend/src/main/java/dev/blackice/shared/api/problem/TraceContext.java apps/backend/src/main/java/dev/blackice/shared/api/problem/ApiTraceResponseFilter.java apps/backend/src/test/java/dev/blackice/shared/api/problem/ApiProblemFactoryTest.java apps/backend/src/test/java/dev/blackice/shared/api/problem/ApiTraceResponseFilterTest.java apps/backend/src/test/java/dev/blackice/architecture/BackendArchitectureTest.java
```

Criar o commit com o título
`✨ adiciona Problem Details e contexto de trace compartilhados` e corpo
obrigatório conforme a convenção canônica.

### Task 5: Cobrir falhas do framework, segurança e fallback

**Files:**
- Create: `apps/backend/src/main/java/dev/blackice/shared/api/problem/ApiProblemExceptionMappers.java`
- Create: `apps/backend/src/main/java/dev/blackice/shared/api/problem/ApiProblemResponseFilter.java`
- Create: `apps/backend/src/main/java/dev/blackice/shared/api/problem/ApiHttpFailureHandler.java`
- Create: `apps/backend/src/main/java/dev/blackice/security/api/ApiJavaScriptRequestChecker.java`
- Modify: `apps/backend/src/main/resources/application.properties`
- Create: `apps/backend/src/test/java/dev/blackice/shared/api/problem/ApiProblemProbeResource.java`
- Create: `apps/backend/src/test/java/dev/blackice/shared/api/problem/ApiProblemHttpTest.java`
- Modify: `apps/backend/src/test/java/dev/blackice/security/api/CsrfResourceTest.java`
- Modify: `apps/backend/src/test/java/dev/blackice/session/api/SessionResourceTest.java`

**Interfaces:**
- Consumes: factories e `TraceContext` da Task 4.
- Produces: cobertura catalogada para `400/401/403/404/405/406/413/415/500` antes das traduções de feature.

- [ ] **Step 1: Escrever a matriz HTTP RED**

Criar endpoints de teste `/api/problem-probe/json`, `/api/problem-probe/fail` e
`/api/problem-probe/secured`. Para cada cenário, afirmar status, media type,
`type`, `title`, `detail`, `code`, `traceId`, igualdade com `X-Trace-ID` e ausência
de `X-Request-ID`:

```java
given().header("traceparent", "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01")
    .when().get("/api/problem-probe/fail")
    .then().statusCode(500)
    .contentType("application/problem+json")
    .body("code", equalTo("API_INTERNAL_ERROR"))
    .body("traceId", equalTo("4bf92f3577b34da6a3ce929d0e0e4736"))
    .header("X-Trace-ID", equalTo("4bf92f3577b34da6a3ce929d0e0e4736"));
```

Cobrir JSON malformado 400, sessão anônima 401, role negada 403, CSRF ausente
403, rota 404, método 405, Accept 406, corpo global 413 e media type 415. Exigir
que `/api/login` continue retornando redirect e não Problem Details.

- [ ] **Step 2: Executar e confirmar RED**

Run: `cd apps/backend && mise exec -- mvn -Dtest=ApiProblemHttpTest,CsrfResourceTest,SessionResourceTest test -Dquarkus.http.test-port=8082`

Expected: FAIL com respostas vazias, redirects indevidos para API e media types antigos.

- [ ] **Step 3: Implementar a classificação REST e Vert.x**

`ApiProblemExceptionMappers` mapeia exceções Jakarta REST específicas e `Throwable`.
`ApiProblemResponseFilter` preserva qualquer `ApiProblem` existente e converte
respostas vazias. Para POST/PUT/PATCH/DELETE 400, cookie/header CSRF ausente ou
divergente seleciona `API_CSRF_INVALID` e muda o status para 403; os demais 400
usam `API_REQUEST_INVALID`.

`ApiHttpFailureHandler` observa `Router`, registra failure handler somente para
`/api/*`, preserva headers de desafio necessários, escreve JSON via Jackson e
classifica falhas anteriores ao REST. Ele chama `next()` para `/api/login`.

- [ ] **Step 4: Fazer o OIDC distinguir navegação de API**

```java
@ApplicationScoped
public final class ApiJavaScriptRequestChecker implements JavaScriptRequestChecker {
    public boolean isJavaScriptRequest(RoutingContext context) {
        String path = context.normalizedPath();
        return path.startsWith("/api/") && !path.equals("/api/login");
    }

    public ChallengeData getChallenge(RoutingContext context) {
        return new ChallengeData(401, "WWW-Authenticate", "OIDC");
    }
}
```

O failure handler completa esse desafio com `API_AUTHENTICATION_REQUIRED` sem
alterar o fluxo de `/api/login`.

Configurar `quarkus.oidc.authentication.java-script-auto-redirect=false` para
que o checker possa substituir o redirect automático somente nas rotas API.

- [ ] **Step 5: Implementar logging único e sanitizado**

Falhas 4xx esperadas usam `INFO` ou `WARN`; falha inesperada usa um único `ERROR`
com stack trace. O log usa somente code, status, traceId, método e template de
rota; não incluir URI com query nem mensagens das exceções.

- [ ] **Step 6: Confirmar GREEN e Gate 2**

Run: `cd apps/backend && mise exec -- mvn -Dtest=ApiProblemHttpTest,CsrfResourceTest,SessionResourceTest test -Dquarkus.http.test-port=8082`

Expected: matriz passa e `/api/login` permanece redirect.

```bash
git add apps/backend/src/main/resources/application.properties apps/backend/src/main/java/dev/blackice/shared/api/problem/ApiProblemExceptionMappers.java apps/backend/src/main/java/dev/blackice/shared/api/problem/ApiProblemResponseFilter.java apps/backend/src/main/java/dev/blackice/shared/api/problem/ApiHttpFailureHandler.java apps/backend/src/main/java/dev/blackice/security/api/ApiJavaScriptRequestChecker.java apps/backend/src/test/java/dev/blackice/shared/api/problem/ApiProblemProbeResource.java apps/backend/src/test/java/dev/blackice/shared/api/problem/ApiProblemHttpTest.java apps/backend/src/test/java/dev/blackice/security/api/CsrfResourceTest.java apps/backend/src/test/java/dev/blackice/session/api/SessionResourceTest.java
```

Criar o commit com o título
`🔐 padroniza falhas de framework e segurança` e corpo obrigatório conforme a
convenção canônica.

Apresentar matriz HTTP e logs sanitizados ao humano. Parar até aprovação do Gate 2.

---

## Fase 3 — migração das features backend e propagação DICOMweb

### Task 6: Migrar Worklist para o catálogo

**Files:**
- Create: `apps/backend/src/main/java/dev/blackice/worklist/api/WorklistExceptionMappers.java`
- Modify: `apps/backend/src/main/java/dev/blackice/worklist/api/WorklistResource.java`
- Modify: `apps/backend/src/main/java/dev/blackice/worklist/application/exception/InvalidStudySearchException.java`
- Delete: `apps/backend/src/main/java/dev/blackice/worklist/api/WorklistErrorResponse.java`
- Modify: `apps/backend/src/test/java/dev/blackice/worklist/api/WorklistResourceTest.java`

**Interfaces:**
- Produces: `InvalidStudySearchException -> API_SEARCH_INVALID`, `QUERY_TOO_BROAD -> API_SEARCH_TOO_BROAD`, `INVALID_RESPONSE|HTTP_STATUS -> API_ARCHIVE_RESPONSE_INVALID`, `TIMEOUT|CONNECTION -> API_ARCHIVE_UNAVAILABLE`.

- [ ] **Step 1: Trocar as assertions para Problem Details e confirmar RED**

```java
.statusCode(413)
.contentType("application/problem+json")
.body("code", equalTo("API_SEARCH_TOO_BROAD"))
.body("status", equalTo(413))
.body("traceId", not(blankOrNullString()));
```

Adicionar caso em que `RuntimeException("patient-secret")` produz
`API_INTERNAL_ERROR`, não `API_ARCHIVE_UNAVAILABLE`, e não devolve a mensagem.

- [ ] **Step 2: Executar RED**

Run: `cd apps/backend && mise exec -- mvn -Dtest=WorklistResourceTest test -Dquarkus.http.test-port=8082`

Expected: FAIL nos códigos legados e em `WorklistErrorResponse`.

- [ ] **Step 3: Implementar mapper e simplificar o recurso**

Converter `DateTimeParseException` em `InvalidStudySearchException` na criação do
request. Remover `error()`, `archiveError()`, MDC manual, UUID e todos os catches
do recurso; deixar o mapper de feature traduzir apenas as duas exceções conhecidas.

- [ ] **Step 4: Confirmar GREEN e ausência do contrato antigo**

Run: `cd apps/backend && mise exec -- mvn -Dtest=WorklistResourceTest test -Dquarkus.http.test-port=8082 && ! rg -n 'WorklistErrorResponse|X-Request-ID|INVALID_SEARCH|ARCHIVE_INVALID_RESPONSE' src/main/java`

Expected: testes passam e a busca não encontra contrato/códigos legados.

- [ ] **Step 5: Commit da tarefa**

```bash
git add apps/backend/src/main/java/dev/blackice/worklist/api/WorklistExceptionMappers.java apps/backend/src/main/java/dev/blackice/worklist/api/WorklistResource.java apps/backend/src/main/java/dev/blackice/worklist/api/WorklistErrorResponse.java apps/backend/src/main/java/dev/blackice/worklist/application/exception/InvalidStudySearchException.java apps/backend/src/test/java/dev/blackice/worklist/api/WorklistResourceTest.java
```

Criar o commit com o título
`♻️ migra erros da Worklist para Problem Details` e corpo obrigatório conforme
a convenção canônica.

### Task 7: Migrar ingestão preservando resultados completos e parciais

**Files:**
- Create: `apps/backend/src/main/java/dev/blackice/ingest/api/IngestResponseMapper.java`
- Modify: `apps/backend/src/main/java/dev/blackice/ingest/api/IngestResource.java`
- Delete: `apps/backend/src/main/java/dev/blackice/ingest/api/IngestHttpStatusResolver.java`
- Modify: `apps/backend/src/main/java/dev/blackice/ingest/application/validation/DicomValidationIssue.java`
- Modify: `apps/backend/src/main/java/dev/blackice/ingest/application/validation/DicomBatchValidation.java`
- Modify: `apps/backend/src/main/java/dev/blackice/ingest/application/result/IngestResult.java`
- Modify: `apps/backend/src/main/java/dev/blackice/ingest/infrastructure/dicom/Dcm4cheDicomBatchValidator.java`
- Modify: `apps/backend/src/test/java/dev/blackice/ingest/api/IngestResourceTest.java`
- Delete: `apps/backend/src/test/java/dev/blackice/ingest/api/IngestHttpStatusResolverTest.java`
- Modify: `apps/backend/src/test/java/dev/blackice/ingest/infrastructure/dicom/Dcm4cheDicomBatchValidatorTest.java`

**Interfaces:**
- Produces: 200 `IngestResult` para COMPLETE/PARTIAL; 422 com `violations` quando nenhum arquivo é válido; 503 quando todos os estudos válidos falham por indisponibilidade; 400 upload vazio; 413 limites.
- `DicomValidationIssue` e `IngestResult.RejectedFile` passam a carregar `int itemIndex` internamente; somente `filename` permanece no payload de resultado 200, nunca no Problem 422.

- [ ] **Step 1: Escrever testes RED da nova matriz**

Exigir para 422:

```java
.body("code", equalTo("API_DICOM_VALIDATION_FAILED"))
.body("violations[0].itemIndex", equalTo(0))
.body("violations[0].code", equalTo("MALFORMED_DICOM"))
.body("violations[0].message", equalTo("The file is not valid DICOM."))
.body("violations[0].filename", nullValue());
```

Exigir 503 sem `studies`, UIDs, filename ou causa; exigir 200 inalterado para
completo e parcial; exigir `API_UPLOAD_EMPTY` e `API_PAYLOAD_TOO_LARGE` nos limites.

- [ ] **Step 2: Executar RED**

Run: `cd apps/backend && mise exec -- mvn -Dtest=IngestResourceTest,Dcm4cheDicomBatchValidatorTest test -Dquarkus.http.test-port=8082`

Expected: FAIL porque 422/503 ainda retornam `IngestResult` e limites têm corpo vazio.

- [ ] **Step 3: Tornar `itemIndex` determinístico na validação**

Enumerar uploads na ordem recebida antes de validar, preservar o índice em cada
issue e manter ordenação estável. Não alterar ou gerar nenhum UID DICOM.

- [ ] **Step 4: Implementar `IngestResponseMapper`**

```java
public Response toResponse(IngestResult result) {
    if (result.summary().received() > 0 && result.summary().locallyValid() == 0) {
        return problems.response(API_DICOM_VALIDATION_FAILED, violations(result));
    }
    if (allValidStudiesUnavailable(result)) {
        return problems.response(API_ARCHIVE_UNAVAILABLE, ProblemExtensions.none());
    }
    return Response.ok(result).build();
}
```

Remover UUID/MDC manual do recurso; `IngestResource` valida somente limites,
chama o caso de uso e delega ao mapper.

- [ ] **Step 5: Confirmar GREEN**

Run: `cd apps/backend && mise exec -- mvn -Dtest=IngestResourceTest,Dcm4cheDicomBatchValidatorTest test -Dquarkus.http.test-port=8082`

Expected: matriz da ingestão passa sem filename no 422.

- [ ] **Step 6: Commit da tarefa**

```bash
git add apps/backend/src/main/java/dev/blackice/ingest/api/IngestResponseMapper.java apps/backend/src/main/java/dev/blackice/ingest/api/IngestResource.java apps/backend/src/main/java/dev/blackice/ingest/api/IngestHttpStatusResolver.java apps/backend/src/main/java/dev/blackice/ingest/application/validation/DicomValidationIssue.java apps/backend/src/main/java/dev/blackice/ingest/application/validation/DicomBatchValidation.java apps/backend/src/main/java/dev/blackice/ingest/application/result/IngestResult.java apps/backend/src/main/java/dev/blackice/ingest/infrastructure/dicom/Dcm4cheDicomBatchValidator.java apps/backend/src/test/java/dev/blackice/ingest/api/IngestResourceTest.java apps/backend/src/test/java/dev/blackice/ingest/api/IngestHttpStatusResolverTest.java apps/backend/src/test/java/dev/blackice/ingest/infrastructure/dicom/Dcm4cheDicomBatchValidatorTest.java
```

Criar o commit com o título
`♻️ migra falhas de ingestão para o catálogo` e corpo obrigatório conforme a
convenção canônica.

### Task 8: Propagar `traceparent` e estreitar capturas externas

**Files:**
- Create: `apps/backend/src/main/java/dev/blackice/shared/infrastructure/telemetry/W3cTraceContextInjector.java`
- Create: `apps/backend/src/test/java/dev/blackice/shared/infrastructure/telemetry/W3cTraceContextInjectorTest.java`
- Modify: `apps/backend/src/main/java/dev/blackice/ingest/infrastructure/dicomweb/HttpDicomArchiveGateway.java`
- Modify: `apps/backend/src/main/java/dev/blackice/ingest/application/usecase/IngestStudiesUseCase.java`
- Modify: `apps/backend/src/main/java/dev/blackice/worklist/infrastructure/dicomweb/HttpQidoStudyGateway.java`
- Modify: `apps/backend/src/test/java/dev/blackice/ingest/infrastructure/dicomweb/HttpDicomArchiveGatewayTest.java`
- Modify: `apps/backend/src/test/java/dev/blackice/ingest/application/usecase/IngestStudiesUseCaseTest.java`
- Modify: `apps/backend/src/test/java/dev/blackice/worklist/infrastructure/dicomweb/HttpQidoStudyGatewayTest.java`
- Modify: `apps/backend/src/test/java/dev/blackice/architecture/BackendArchitectureTest.java`

**Interfaces:**
- Produces: `W3cTraceContextInjector.inject(HttpRequest.Builder): void` usando `GlobalOpenTelemetry.getPropagators().getTextMapPropagator()` e `Context.current()`.
- Adapters continuam lançando somente `ArchiveUnavailableException` e `ArchiveSearchException` para falhas externas conhecidas; bugs inesperados escapam ao fallback 500.

- [ ] **Step 1: Escrever testes RED de propagação**

Criar span de teste com TraceID conhecido, torná-lo current, executar QIDO e STOW
contra `HttpServer` e afirmar que o header recebido começa com
`00-4bf92f3577b34da6a3ce929d0e0e4736-`. Afirmar que `X-Trace-ID` nunca é enviado
ao Archive.

- [ ] **Step 2: Escrever testes RED de exceção inesperada**

Mockar `HttpClient.send` lançando `IllegalStateException` e exigir a mesma
exceção, sem conversão para `CONNECTION`. Mockar parser lançando
`IllegalArgumentException` e continuar exigindo `INVALID_RESPONSE`/resposta STOW
inválida, pois esse é seu contrato explícito.

- [ ] **Step 3: Executar RED**

Run: `cd apps/backend && mise exec -- mvn -Dtest=W3cTraceContextInjectorTest,HttpDicomArchiveGatewayTest,HttpQidoStudyGatewayTest,IngestStudiesUseCaseTest test -Dquarkus.http.test-port=8082`

Expected: FAIL por ausência de `traceparent` e catches genéricos.

- [ ] **Step 4: Implementar propagação e remover mascaramento**

```java
public void inject(HttpRequest.Builder builder) {
    GlobalOpenTelemetry.getPropagators().getTextMapPropagator().inject(
        Context.current(), builder, HttpRequest.Builder::header
    );
}
```

Remover `catch (Exception)` dos dois gateways. Em `IngestStudiesUseCase`, capturar
somente `ArchiveUnavailableException`; ao resolver `ExecutionException`, relançar
`RuntimeException` e `Error` inesperados em vez de convertê-los em conexão.

- [ ] **Step 5: Executar testes backend completos**

Run: `cd apps/backend && mise exec -- mvn test -Dquarkus.http.test-port=8082`

Expected: todos os testes passam, inclusive ArchUnit e propagação aos dois mocks DICOMweb.

- [ ] **Step 6: Revisão DICOM e Gate 3**

Acionar `dicom-domain-reviewer` em modo read-only sobre validação, STOW e QIDO.
Corrigir qualquer violação apontada e repetir a suíte completa antes do commit.

```bash
git add apps/backend/src/main/java/dev/blackice/shared/infrastructure/telemetry/W3cTraceContextInjector.java apps/backend/src/main/java/dev/blackice/ingest/infrastructure/dicomweb/HttpDicomArchiveGateway.java apps/backend/src/main/java/dev/blackice/ingest/application/usecase/IngestStudiesUseCase.java apps/backend/src/main/java/dev/blackice/worklist/infrastructure/dicomweb/HttpQidoStudyGateway.java apps/backend/src/test/java/dev/blackice/shared/infrastructure/telemetry/W3cTraceContextInjectorTest.java apps/backend/src/test/java/dev/blackice/ingest/infrastructure/dicomweb/HttpDicomArchiveGatewayTest.java apps/backend/src/test/java/dev/blackice/ingest/application/usecase/IngestStudiesUseCaseTest.java apps/backend/src/test/java/dev/blackice/worklist/infrastructure/dicomweb/HttpQidoStudyGatewayTest.java apps/backend/src/test/java/dev/blackice/architecture/BackendArchitectureTest.java
```

Criar o commit com o título
`✨ propaga TraceID às chamadas DICOMweb` e corpo obrigatório conforme a
convenção canônica.

Apresentar suíte e parecer DICOM ao humano. Parar até aprovação do Gate 3.

---

## Fase 4 — parser compartilhado e migração do Vue

### Task 9: Criar parser, `ApiError` e mensagens PT-BR

**Files:**
- Create: `apps/frontend/src/shared/api/problems/api-problem.ts`
- Create: `apps/frontend/src/shared/api/problems/api-error.ts`
- Create: `apps/frontend/src/shared/api/problems/parse-problem.ts`
- Create: `apps/frontend/src/shared/api/problems/parse-problem.spec.ts`
- Create: `apps/frontend/src/shared/api/problems/problem-messages.pt-BR.ts`
- Create: `apps/frontend/src/shared/api/problems/problem-messages.pt-BR.spec.ts`

**Interfaces:**
- Produces: `apiErrorFromResponse(response: Response): Promise<ApiError>`, `apiErrorFromXhr(xhr: XMLHttpRequest): ApiError`, `clientError(code: ClientProblemCode, traceId?: string): ApiError`, `problemMessage(code: ProblemCode): string` e `isIntentionalAbort(error: unknown): boolean`.
- `ApiError` expõe `type`, `code`, `scope`, `status`, `traceId` e `retryPolicy`; `Error.message` recebe somente o code catalogado.

- [ ] **Step 1: Escrever testes RED do parser**

Cobrir problema válido, content type incorreto, JSON inválido, tipo desconhecido,
status divergente, code divergente, TraceID corpo/header divergente, header válido
com corpo inválido, rede, timeout e abort. Exemplo:

```ts
await expect(apiErrorFromResponse(responseWith({
  code: 'API_ARCHIVE_UNAVAILABLE',
  status: 502,
}))).resolves.toMatchObject({
  code: 'CLIENT_RESPONSE_INVALID',
  scope: 'CLIENT',
  retryPolicy: 'MANUAL',
});
```

- [ ] **Step 2: Executar RED**

Run: `cd apps/frontend && mise exec -- pnpm test -- src/shared/api/problems`

Expected: FAIL porque parser e mensagens ainda não existem.

- [ ] **Step 3: Implementar validação estrita e fallbacks seguros**

Validar `type + code + status` contra `PROBLEM_TYPES`, exigir
`application/problem+json`, aceitar TraceID somente com 32 hexadecimais e nunca
copiar body/detail para `message`. `apiErrorFromXhr` usa `getResponseHeader` e o
mesmo núcleo puro que `apiErrorFromResponse`.

- [ ] **Step 4: Implementar mapa PT-BR exaustivo**

```ts
export const PROBLEM_MESSAGES = {
  API_REQUEST_INVALID: 'A requisição é inválida. Revise os dados informados.',
  API_UPLOAD_EMPTY: 'Selecione ao menos um arquivo para importar.',
  API_SEARCH_INVALID: 'Revise os filtros de busca informados.',
  API_AUTHENTICATION_REQUIRED: 'Sua sessão não está ativa. Entre novamente.',
  API_ACCESS_DENIED: 'Você não tem permissão para realizar esta ação.',
  API_CSRF_INVALID: 'Não foi possível verificar a requisição. Atualize a página e tente novamente.',
  API_RESOURCE_NOT_FOUND: 'O recurso solicitado não foi encontrado.',
  API_METHOD_NOT_ALLOWED: 'Esta operação não é permitida neste recurso.',
  API_REPRESENTATION_NOT_ACCEPTABLE: 'O formato de resposta solicitado não é suportado.',
  API_PAYLOAD_TOO_LARGE: 'O conteúdo enviado excede o limite permitido.',
  API_SEARCH_TOO_BROAD: 'A busca retornou muitos resultados. Use filtros mais restritos.',
  API_MEDIA_TYPE_UNSUPPORTED: 'O formato do conteúdo enviado não é suportado.',
  API_DICOM_VALIDATION_FAILED: 'Nenhum arquivo enviado passou pela validação DICOM.',
  API_INTERNAL_ERROR: 'Ocorreu uma falha inesperada. Tente novamente.',
  API_ARCHIVE_RESPONSE_INVALID: 'O Archive retornou uma resposta inválida.',
  API_ARCHIVE_UNAVAILABLE: 'O Archive está temporariamente indisponível.',
  CLIENT_NETWORK_UNAVAILABLE: 'Não foi possível alcançar o servidor.',
  CLIENT_REQUEST_TIMEOUT: 'A operação excedeu o tempo de espera.',
  CLIENT_RESPONSE_INVALID: 'O servidor retornou uma resposta inválida.',
  CLIENT_CSRF_COOKIE_MISSING: 'Não foi possível preparar a verificação de segurança.',
  CLIENT_UNEXPECTED_ERROR: 'Ocorreu uma falha inesperada no navegador.',
} satisfies Record<ProblemCode, string>;
```

- [ ] **Step 5: Confirmar GREEN e build TypeScript**

Run: `cd apps/frontend && mise exec -- pnpm test -- src/shared/api/problems && mise exec -- pnpm build`

Expected: testes passam e `satisfies Record<ProblemCode,string>` garante exaustividade.

- [ ] **Step 6: Commit da tarefa**

```bash
git add apps/frontend/src/shared/api/problems
```

Criar o commit com o título
`✨ adiciona parser tipado de Problem Details no frontend` e corpo obrigatório
conforme a convenção canônica.

### Task 10: Migrar sessão e Worklist

**Files:**
- Modify: `apps/frontend/src/features/session/session.api.ts`
- Modify: `apps/frontend/src/features/session/session.api.spec.ts`
- Modify: `apps/frontend/src/features/worklist/worklist.api.ts`
- Modify: `apps/frontend/src/features/worklist/worklist.api.spec.ts`
- Modify: `apps/frontend/src/features/worklist/worklist.types.ts`
- Modify: `apps/frontend/src/features/worklist/useWorklist.ts`
- Modify: `apps/frontend/src/features/worklist/useWorklist.spec.ts`
- Modify: `apps/frontend/src/features/worklist/WorklistPage.vue`
- Modify: `apps/frontend/src/features/worklist/WorklistPage.spec.ts`

**Interfaces:**
- `fetchSession()` retorna `null` somente para `API_AUTHENTICATION_REQUIRED`; demais falhas lançam `ApiError`.
- `useWorklist.error` é `Readonly<Ref<ApiError|null>>`; `retry()` continua disponível, mas a página só oferece botão quando `retryPolicy === 'MANUAL'`.

- [ ] **Step 1: Escrever testes RED dos consumidores**

Trocar mocks `{code,message}` por RFC 9457. Exigir rede como
`CLIENT_NETWORK_UNAVAILABLE`, timeout como `CLIENT_REQUEST_TIMEOUT`, 401 de sessão
como `null`, resposta 401 inválida como `CLIENT_RESPONSE_INVALID`, mensagens
centralizadas e TraceID visível em `<code>`.

- [ ] **Step 2: Executar RED**

Run: `cd apps/frontend && mise exec -- pnpm test -- src/features/session src/features/worklist`

Expected: FAIL por `WorklistError`, `errorCode` e parser local.

- [ ] **Step 3: Migrar APIs e composable**

```ts
if (!response.ok) throw await apiErrorFromResponse(response);
```

Remover `WorklistErrorBody`, `safeError`, `WorklistError` e `errorCode`. Em abort
intencional, não atribuir `ApiError` nem mudar a UI para ERROR.

- [ ] **Step 4: Migrar apresentação**

Usar `problemMessage(worklist.error.value.code)`, mostrar
`Referência: <code>{{ worklist.error.value.traceId }}</code>` quando disponível e
condicionar retry manual. Não renderizar `detail`.

- [ ] **Step 5: Confirmar GREEN**

Run: `cd apps/frontend && mise exec -- pnpm test -- src/features/session src/features/worklist && mise exec -- pnpm build`

Expected: testes e build passam sem `WorklistError` ou `UNKNOWN_ERROR`.

- [ ] **Step 6: Commit da tarefa**

```bash
git add apps/frontend/src/features/session/session.api.ts apps/frontend/src/features/session/session.api.spec.ts apps/frontend/src/features/worklist/worklist.api.ts apps/frontend/src/features/worklist/worklist.api.spec.ts apps/frontend/src/features/worklist/worklist.types.ts apps/frontend/src/features/worklist/useWorklist.ts apps/frontend/src/features/worklist/useWorklist.spec.ts apps/frontend/src/features/worklist/WorklistPage.vue apps/frontend/src/features/worklist/WorklistPage.spec.ts
```

Criar o commit com o título
`♻️ migra sessão e Worklist para o catálogo de problemas` e corpo obrigatório
conforme a convenção canônica.

### Task 11: Migrar ingestão e preservar cancelamento

**Files:**
- Modify: `apps/frontend/src/features/ingest/ingest.api.ts`
- Modify: `apps/frontend/src/features/ingest/ingest.api.spec.ts`
- Modify: `apps/frontend/src/features/ingest/ingest.types.ts`
- Modify: `apps/frontend/src/features/ingest/useIngestBatch.ts`
- Modify: `apps/frontend/src/features/ingest/useIngestBatch.spec.ts`
- Modify: `apps/frontend/src/features/ingest/IngestPage.vue`
- Create: `apps/frontend/src/features/ingest/IngestPage.spec.ts`

**Interfaces:**
- `UploadHandle.promise` continua `Promise<IngestResponse>` no sucesso e rejeita `ApiError` nos problemas; abort rejeita `DOMException` com name `AbortError`.
- `IngestBatch.error` é `Readonly<Ref<ApiError|null>>`, `retry(): Promise<void>` repete o lote preservado após falha `MANUAL`, e a resposta de resultado só existe para 200 completo/parcial.

- [ ] **Step 1: Escrever testes RED de fetch CSRF e XHR**

Exigir `apiErrorFromResponse` no GET CSRF, `CLIENT_CSRF_COOKIE_MISSING` quando o
204 não cria cookie, parser compartilhado no XHR, 422 com violations, 503, rede,
timeout, JSON inválido e abort sem `ApiError`.

- [ ] **Step 2: Executar RED**

Run: `cd apps/frontend && mise exec -- pnpm test -- src/features/ingest`

Expected: FAIL por `UploadError` e strings ad hoc.

- [ ] **Step 3: Migrar API e composable**

```ts
xhr.onload = () => {
  if (xhr.status >= 200 && xhr.status < 300) {
    resolve(parseIngestResult(xhr.responseText));
    return;
  }
  reject(apiErrorFromXhr(xhr));
};
xhr.onerror = () => reject(clientError('CLIENT_NETWORK_UNAVAILABLE'));
xhr.ontimeout = () => reject(clientError('CLIENT_REQUEST_TIMEOUT'));
xhr.onabort = () => reject(new DOMException('The operation was aborted', 'AbortError'));
```

Remover `UploadError`, `errorCode`, `UPLOAD_FAILED:*`, `CSRF_TOKEN_FAILED:*`,
`NETWORK_ERROR`, `TIMEOUT`, `INVALID_JSON` e `ABORTED`.

- [ ] **Step 4: Migrar a página**

Renderizar mensagem pelo mapa central, TraceID copiável e botão que chama
`batch.retry()` somente para `MANUAL` quando houver arquivos selecionados. Para 422, listar
violações associando `itemIndex` ao filename que já existe localmente; nunca usar
filename recebido do backend.

- [ ] **Step 5: Confirmar GREEN, build e Gate 4**

Run: `cd apps/frontend && mise exec -- pnpm test -- src/features/ingest src/shared/api/problems && mise exec -- pnpm build`

Expected: suíte passa; cancelamento termina em CANCELLED sem problema.

```bash
git add apps/frontend/src/features/ingest/ingest.api.ts apps/frontend/src/features/ingest/ingest.api.spec.ts apps/frontend/src/features/ingest/ingest.types.ts apps/frontend/src/features/ingest/useIngestBatch.ts apps/frontend/src/features/ingest/useIngestBatch.spec.ts apps/frontend/src/features/ingest/IngestPage.vue apps/frontend/src/features/ingest/IngestPage.spec.ts
```

Criar o commit com o título
`♻️ migra ingestão para problemas tipados` e corpo obrigatório conforme a
convenção canônica.

Apresentar mensagens, TraceID e retentativa manual ao humano. Parar até aprovação do Gate 4.

---

## Fase 5 — integração, remoção do legado e documentação

### Task 12: Verificar o contrato atômico ponta a ponta

**Files:**
- Modify: `apps/frontend/e2e/manual-dicom-import.spec.ts`
- Modify: `apps/frontend/e2e/worklist.spec.ts`
- Create: `apps/frontend/e2e/problem-details.spec.ts`
- Modify: `apps/backend/README.md`
- Modify: `apps/frontend/README.md`
- Modify: `docs/architecture/project-structure.md`
- Modify: `docs/superpowers/specs/2026-08-23-error-handling-problem-catalog-design.md`

**Interfaces:**
- Consumes: catálogo, backend e frontend das fases anteriores.
- Produces: evidência integrada de contrato único e documentação operacional final.

- [ ] **Step 1: Escrever E2E RED da fronteira**

Interceptar problemas reais/mocados em ingestão e Worklist e exigir mensagem
PT-BR, TraceID visível, retry manual e ausência de detail bruto. Cobrir sessão
expirada 401 e confirmar que navegação explícita para `/api/login` ainda inicia
OIDC.

- [ ] **Step 2: Executar E2E focal e confirmar RED**

Run: `cd apps/frontend && mise exec -- pnpm exec playwright test e2e/problem-details.spec.ts`

Expected: FAIL até fixtures e seletores refletirem o contrato final.

- [ ] **Step 3: Atualizar fixtures e documentação operacional**

Documentar comandos:

```bash
cd .problem-catalog && mise exec -- pnpm check
cd apps/backend && mise exec -- mvn test -Dquarkus.http.test-port=8082
cd apps/frontend && mise exec -- pnpm test && mise exec -- pnpm build
```

Marcar a spec como implementada somente depois de todas as verificações e gates.

- [ ] **Step 4: Fazer varredura obrigatória do legado**

Run:

```bash
! rg -n 'X-Request-ID|WorklistErrorResponse|WorklistError|UploadError|UNKNOWN_ERROR|UPLOAD_FAILED:|CSRF_TOKEN_FAILED:|ARCHIVE_INVALID_RESPONSE|"INVALID_SEARCH"|"SEARCH_TOO_BROAD"' apps
```

Expected: nenhuma ocorrência em código ou testes.

- [ ] **Step 5: Executar verificação completa**

Run:

```bash
cd .problem-catalog && mise exec -- pnpm test && mise exec -- pnpm check
cd ../apps/backend && mise exec -- mvn test -Dquarkus.http.test-port=8082
cd ../frontend && mise exec -- pnpm test && mise exec -- pnpm build
```

Depois, executar os três projetos Playwright configurados quando a infraestrutura
local estiver saudável:

```bash
cd apps/frontend
mise exec -- pnpm test:e2e:keycloak
mise exec -- pnpm test:e2e:ingest
mise exec -- pnpm test:e2e:worklist
mise exec -- pnpm exec playwright test e2e/problem-details.spec.ts
```

Expected: zero falhas; backend e frontend usam o mesmo catálogo; nenhum serviço
de observabilidade foi criado.

- [ ] **Step 6: Atualizar Graphify, revisar diff e Commit final**

Seguir `.agents/skills/graphify/` com `--update`, revisar somente mudanças
semânticas esperadas e então:

```bash
git add apps/frontend/e2e/manual-dicom-import.spec.ts apps/frontend/e2e/worklist.spec.ts apps/frontend/e2e/problem-details.spec.ts apps/backend/README.md apps/frontend/README.md docs/architecture/project-structure.md docs/superpowers/specs/2026-08-23-error-handling-problem-catalog-design.md
```

Criar o commit com o título
`✅ valida tratamento de erros ponta a ponta` e corpo obrigatório conforme a
convenção canônica.

Criar o segundo commit focado dos artefatos `graphify-out/`.
Apresentar ao humano a matriz final, revisão DICOM, testes e qualquer teste E2E
bloqueado por infraestrutura. Parar no Gate 5; não fazer merge sem autorização.

---

## Self-review

- Cobertura da spec: Tasks 1–3 implementam registry, UUIDv5, lock, codegen e agentes; Tasks 4–5 implementam RFC 9457, TraceID e framework; Tasks 6–8 migram backend e DICOMweb; Tasks 9–11 migram frontend; Task 12 prova substituição atômica e remove legado.
- Consistência de tipos: `ProblemType`/`ProblemExtensions` gerados alimentam `ApiProblemFactory`; `ApiProblem` é consumido pelo parser; `ProblemCode` governa o mapa PT-BR; features guardam `ApiError`, nunca strings.
- Escopo: exporter OTLP e Dev Services estão desligados; EVO-006 permanece backlog; cancelamento continua controle de fluxo; não há compatibilidade.
- Gates: cada fase termina em aprovação humana; a Fase 3 inclui revisão DICOM read-only antes do gate.

---

## Adendo aprovado pelo humano — revisão DICOM: resultado incerto no Archive

Este adendo complementa o plano histórico sem reescrever suas tarefas. O catálogo
deve publicar `API_ARCHIVE_OUTCOME_UNKNOWN` com escopo `API`, descrição `O
resultado de uma operação no Archive não pôde ser confirmado após o início da
submissão.`, HTTP `502`, retry `NEVER`, owner `platform`, title `Archive outcome
unknown`, detail `The imaging archive outcome could not be confirmed.` e nenhuma
extensão.

- Falha de conexão comprovada antes da submissão permanece
  `API_ARCHIVE_UNAVAILABLE`.
- Após o início da submissão, timeout, interrupção ou reset não pode afirmar que
  nada foi armazenado.
- Resultados confirmados são preservados; estudos incertos são representados sem
  oferecer retry.
- Sem resultado confirmável, usar `API_ARCHIVE_OUTCOME_UNKNOWN`; não oferecer
  retentativa automática nem manual.
