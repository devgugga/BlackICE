# Backend Modular Clean Architecture Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Migrar o backend de `dev.blackice.features` para módulos de negócio com fronteiras internas de Clean Architecture, sem alterar o comportamento público.

**Architecture:** `ingest`, `security` e `session` serão módulos no pacote raiz. `ingest` terá `api`, `application`, `application.port` e adaptadores `infrastructure`; o caso de uso só conhecerá portas e devolve seu próprio resultado, sem importar a borda HTTP. `security.application.AccessTokenProvider` torna-se a fronteira pública para que `ingest` não dependa do adaptador OIDC.

**Tech Stack:** Java 21, Quarkus 3.37.4, JUnit 5, Mockito, REST Assured, DCM4CHE 5.34.3 e ArchUnit 1.4.2.

**Spec:** `docs/superpowers/specs/2026-08-22-backend-modular-clean-architecture-design.md`

## Global Constraints

- Não altere rotas, payloads HTTP, limites de upload, configuração OIDC/Keycloak nem a semântica STOW-RS.
- O DCM4CHEE continua sendo acessado apenas por DICOMweb; UIDs recebidos nunca são gerados e pixel data não vai ao banco do Quarkus.
- Não crie `shared`, módulos vazios ou pacotes técnicos globais.
- `application` não importa `infrastructure`; módulos só consomem APIs públicas de outros módulos.
- `application` não importa `api`; recursos HTTP traduzem apenas entradas e saídas do caso de uso.
- Javadoc, comentários, nomes de testes e mensagens do backend são escritos em inglês; Javadoc documenta contratos/invariantes e comentários explicam apenas decisões não óbvias.
- Não faça commit sem nova autorização humana explícita.
- Antes de eventual commit, atualize e revise `graphify-out/` conforme `docs/architecture/graphify.md`.

---

### Task 1: Criar a guarda arquitetural que falha contra a estrutura atual

**Files:**
- Modify: `apps/backend/pom.xml`
- Create: `apps/backend/src/test/java/dev/blackice/architecture/BackendArchitectureTest.java`

**Interfaces:**
- Consumes: bytecode dos pacotes `dev.blackice` produzido pelo Maven.
- Produces: regras ArchUnit que proíbem `dev.blackice.features..`, dependência de `application` em `infrastructure` e consumo de infraestrutura de outro módulo.

- [x] **Step 1: Adicionar a dependência de teste ArchUnit**

Inclua a propriedade e a dependência abaixo, sem plugin Maven adicional:

```xml
<archunit.version>1.4.2</archunit.version>

<dependency>
    <groupId>com.tngtech.archunit</groupId>
    <artifactId>archunit-junit5</artifactId>
    <version>${archunit.version}</version>
    <scope>test</scope>
</dependency>
```

- [x] **Step 2: Escrever o teste arquitetural em estado RED**

Crie `BackendArchitectureTest` com `ClassFileImporter`, ignorando testes, e
importe `dev.blackice`. A primeira regra deve falhar no estado atual:

```java
@Test
void codigo_de_produto_nao_reside_no_legado_features() {
    noClasses()
        .should().resideInAPackage("dev.blackice.features..")
        .check(classes);
}
```

Acrescente as regras que serão verdes após a migração:

```java
@Test
void application_nao_depende_de_infrastructure() {
    noClasses().that().resideInAPackage("dev.blackice..application..")
        .should().dependOnClassesThat().resideInAPackage("dev.blackice..infrastructure..")
        .check(classes);
}

@Test
void application_nao_depende_da_borda_http() {
    noClasses().that().resideInAPackage("dev.blackice..application..")
        .should().dependOnClassesThat().resideInAPackage("dev.blackice..api..")
        .check(classes);
}

@Test
void modulo_nao_consume_infrastructure_de_outro_modulo() {
    noClasses().that().resideOutsideOfPackage("dev.blackice.ingest.infrastructure..")
        .and().resideOutsideOfPackage("dev.blackice.security.infrastructure..")
        .should().dependOnClassesThat()
        .resideInAnyPackage(
            "dev.blackice.ingest.infrastructure..",
            "dev.blackice.security.infrastructure.."
        )
        .check(classes);
}
```

Use `ClassFileImporter().withImportOption(new ImportOption.DoNotIncludeTests())`
para inicializar `classes`. Não use anotação de regra implícita: cada método
deve executar `check(classes)` para que a mensagem de falha seja direta.

- [x] **Step 3: Executar o teste e confirmar a falha esperada**

Run: `mise exec -- mvn test -Dtest=BackendArchitectureTest`

Expected: FAIL no teste `codigo_de_produto_nao_reside_no_legado_features`,
listando classes em `dev.blackice.features`. As demais regras podem passar
vazias neste momento; a falha relevante é a do pacote legado.

- [x] **Step 4: Não modificar código de produção até a falha ser observada**

Registre no output da tarefa que o RED ocorreu pelo pacote legado. Essa guarda
é o teste que prova a migração estrutural; não escreva ainda exceções à regra.

### Task 2: Migrar ingestão para portas e adaptadores, deixando a guarda verde

**Files:**
- Move: todos os arquivos de `apps/backend/src/main/java/dev/blackice/features/ingest/`
- Move: todos os arquivos de `apps/backend/src/test/java/dev/blackice/features/ingest/`
- Move: todos os arquivos de `apps/backend/src/main/java/dev/blackice/features/session/`
- Move: todos os arquivos de `apps/backend/src/test/java/dev/blackice/features/session/`
- Create: `apps/backend/src/main/java/dev/blackice/ingest/application/port/DicomBatchValidator.java`
- Create: `apps/backend/src/main/java/dev/blackice/ingest/infrastructure/dicom/Dcm4cheDicomBatchValidator.java`
- Create: `apps/backend/src/main/java/dev/blackice/security/application/AccessTokenProvider.java`
- Modify: `apps/backend/src/main/java/dev/blackice/ingest/application/IngestService.java`
- Test: `apps/backend/src/test/java/dev/blackice/architecture/BackendArchitectureTest.java`

**Interfaces:**
- Consumes: `DicomBatchValidator#validate(List<UploadedDicom>)` e
  `DicomArchiveGateway#storeStudy(String, List<ValidatedDicom>, String)`.
- Produces: `IngestService#ingest(List<UploadedDicom>, String)` sem dependência
  de DCM4CHE ou de adaptador DICOMweb.

- [x] **Step 1: Escrever o teste RED da fronteira de token**

No teste existente de `CurrentAccessToken`, declare a implementação por meio de
uma interface ainda inexistente:

```java
CurrentAccessToken current = new CurrentAccessToken();
current.credential = new AccessTokenCredential("valid-token-123");
AccessTokenProvider tokenProvider = current;

assertEquals("valid-token-123", tokenProvider.accessToken());
```

Atualize os cenários de credencial nula e token nulo/em branco para chamar
`accessToken()` pela interface. Ainda não mova classes de produção.

Run: `mise exec -- mvn test -Dtest=CurrentAccessTokenTest`

Expected: FAIL de compilação porque `AccessTokenProvider` e `accessToken()` não
existem. Essa falha define a API pública entre segurança e ingestão.

- [x] **Step 2: Criar a porta de token e adaptar o bean OIDC**

Crie em `security.application`:

```java
public interface AccessTokenProvider {
    String accessToken();
}
```

Mova `CurrentAccessToken` para `security.infrastructure.oidc`, implemente essa
interface e renomeie `value()` para `accessToken()`, preservando a validação e
`NotAuthorizedException`. Mantenha `@ApplicationScoped` e a injeção de
`AccessTokenCredential` somente nessa implementação.

- [x] **Step 3: Criar a porta de validação da aplicação**

Crie a interface em `ingest.application.port` antes de mover a implementação:

```java
public interface DicomBatchValidator {
    DicomBatchValidation validate(List<UploadedDicom> uploads);
}
```

Importe `DicomBatchValidation` e `UploadedDicom` de `ingest.application`.
Não a anote com CDI: o bean é a implementação de infraestrutura.

- [x] **Step 4: Mover os contratos e o caso de uso para `ingest.application`**

Mova, atualizando `package` e imports:

```text
ArchiveUnavailableException.java
DicomBatchValidation.java
DicomValidationIssue.java
IngestExecution.java
IngestResponse.java
StowInstanceResult.java
StowStudyResult.java
UploadedDicom.java
ValidatedDicom.java
IngestService.java
```

Mova `DicomArchiveGateway` para `ingest.application.port`. Em `IngestService`,
importe ambas as portas desse pacote. Preserve integralmente a lógica de
validação, agrupamento, concorrência, falhas e códigos sugeridos; apenas os
nomes de pacote podem mudar.

- [x] **Step 5: Mover a implementação DCM4CHE para infraestrutura**

Renomeie a classe concreta atual `DicomBatchValidator` para
`Dcm4cheDicomBatchValidator` em `ingest.infrastructure.dicom` e faça-a
implementar `ingest.application.port.DicomBatchValidator`:

```java
@ApplicationScoped
public class Dcm4cheDicomBatchValidator implements DicomBatchValidator {
    @Override
    public DicomBatchValidation validate(List<UploadedDicom> uploads) {
        // corpo atual, sem alteração de regras DICOM
    }
}
```

Mova `HttpDicomArchiveGateway`, `MultipartRelatedBodyPublisher` e
`StowResponseParser` para `ingest.infrastructure.dicomweb`. O gateway continua
implementando a porta de `ingest.application.port`; o parser continua retornando
os resultados de `ingest.application`.

- [x] **Step 6: Mover a borda HTTP para `ingest.api`**

Mova `IngestResource` para `ingest.api`, atualizando os imports dos contratos
da aplicação. `IngestResponse` e `IngestExecution` permanecem em
`ingest.application`, pois são o resultado do caso de uso e não devem induzir
uma dependência inversa. Preserve
`@Path("/api/studies")`, `@RolesAllowed("auth")`, `@Blocking`, os limites e a
geração de `X-Request-ID`.

Em `IngestResource`, injete `AccessTokenProvider` e substitua a chamada antiga
por `accessTokenProvider.accessToken()`. Não importe
`security.infrastructure.oidc.CurrentAccessToken`.

- [x] **Step 7: Mover os recursos de sessão e CSRF**

Mova `SessionResource` e `SessionResponse` para `session.api`; mova
`CsrfResource` para `security.api`. Preserve as rotas `/api/me`, `/api/login` e
`/api/csrf`, suas anotações de autenticação e os status HTTP já testados.

- [x] **Step 8: Mover e ajustar os testes**

Faça os espelhos abaixo e atualize somente packages/imports e o nome concreto
do validador:

```text
DicomBatchValidatorTest.java       -> ingest.infrastructure.dicom.Dcm4cheDicomBatchValidatorTest
HttpDicomArchiveGatewayTest.java   -> ingest.infrastructure.dicomweb.HttpDicomArchiveGatewayTest
StowResponseParserTest.java        -> ingest.infrastructure.dicomweb.StowResponseParserTest
IngestServiceTest.java             -> ingest.application.IngestServiceTest
IngestResourceTest.java            -> ingest.api.IngestResourceTest
CurrentAccessTokenTest.java         -> security.infrastructure.oidc.CurrentAccessTokenTest
CsrfResourceTest.java               -> security.api.CsrfResourceTest
SessionResourceTest.java            -> session.api.SessionResourceTest
```

Nos testes de `IngestService`, use a interface
`ingest.application.port.DicomBatchValidator` nos mocks. Os fixtures de DICOM e
as asserções existentes devem continuar idênticos, pois são testes de
caracterização do comportamento que não pode mudar.

No `IngestResourceTest`, troque o mock de `CurrentAccessToken` por
`AccessTokenProvider` e atualize o nome do stub. A asserção HTTP continua a
comprovar que o token é encaminhado ao caso de uso, sem testar o mock isoladamente.

- [x] **Step 9: Executar o ciclo GREEN da migração**

Run: `mise exec -- mvn test -Dtest=BackendArchitectureTest,IngestServiceTest,Dcm4cheDicomBatchValidatorTest,HttpDicomArchiveGatewayTest,StowResponseParserTest,IngestResourceTest,CurrentAccessTokenTest,CsrfResourceTest,SessionResourceTest`

Expected: PASS. A primeira regra agora encontra zero classes no pacote legado,
e as regras de dependência verificam que a aplicação só usa as portas de
validação, archive e token.

- [x] **Step 10: Executar toda a suíte antes de avançar**

Run: `mise exec -- mvn test`

Expected: PASS, sem teste ainda em `dev.blackice.features.ingest`.

### Task 3: Documentar as fronteiras no código sem comentários redundantes

**Files:**
- Create: `apps/backend/src/main/java/dev/blackice/ingest/package-info.java`
- Create: `apps/backend/src/main/java/dev/blackice/ingest/application/package-info.java`
- Create: `apps/backend/src/main/java/dev/blackice/ingest/application/port/package-info.java`
- Create: `apps/backend/src/main/java/dev/blackice/ingest/infrastructure/package-info.java`
- Create: `apps/backend/src/main/java/dev/blackice/security/package-info.java`
- Create: `apps/backend/src/main/java/dev/blackice/session/package-info.java`
- Modify: APIs, caso de uso, portas e adaptadores públicos movidos nas Tasks 2 e 3.

**Interfaces:**
- Consumes: regras de dependência definidas nesta spec.
- Produces: documentação local que orienta manutenção sem copiar os Domain
  Packs.

- [x] **Step 1: Criar os `package-info.java` de fronteira**

Use Javadoc de uma ou duas frases por pacote. Em particular:

```java
/** Caso de uso de ingestão e contratos independentes de adaptadores. */
package dev.blackice.ingest.application;
```

O `package-info` de infraestrutura deve afirmar que implementa portas da
aplicação; o de `port` deve afirmar que nenhuma implementação concreta pertence
ali. Os pacotes de sessão e segurança devem explicar suas responsabilidades,
sem repetir a configuração Keycloak.

- [x] **Step 2: Adicionar Javadoc somente aos contratos significativos**

Documente `IngestResource`, `IngestService`, `DicomArchiveGateway`,
`DicomBatchValidator`, `AccessTokenProvider`, `HttpDicomArchiveGateway` e
`Dcm4cheDicomBatchValidator`. Cada descrição deve mencionar apenas contrato,
falha ou invariante relevante. Por exemplo, a porta do archive declara que a
resposta STOW é retornada por estudo e que indisponibilidade é representada por
`ArchiveUnavailableException`.

- [x] **Step 3: Revisar comentários existentes**

Preserve comentários que explicam CSRF, `sub` versus
`preferred_username`, sequência DICOM e comportamento de temporários. Remova
somente comentários que narrem uma instrução óbvia. Não mova regras detalhadas
de `docs/domains/dicom/` para Java.

- [x] **Step 4: Compilar e executar a suíte**

Run: `mise exec -- mvn test`

Expected: PASS. `package-info.java` deve compilar sem introduzir dependências
novas ou avisos relevantes.

### Task 4: Atualizar a documentação operacional e fechar a verificação

**Files:**
- Modify: `docs/architecture/project-structure.md`
- Modify: `docs/domains/quarkus/conventions.md`
- Modify: `apps/backend/README.md`
- Modify: `docs/superpowers/specs/2026-08-22-backend-modular-clean-architecture-design.md`
- Modify: `docs/superpowers/plans/2026-08-22-backend-modular-clean-architecture.md`
- Modify: `graphify-out/` gerado pela atualização semântica.

**Interfaces:**
- Consumes: estrutura migrada e resultados da suíte Maven.
- Produces: receita canônica de criação de módulos Quarkus e grafo atualizado.

- [x] **Step 1: Reescrever a receita Quarkus canônica**

Em `project-structure.md`, substitua o texto de pacote plano
`dev.blackice.features.<name>` por `dev.blackice.<module>`. Explique que o
módulo começa simples e adiciona `api`, `application`, `application.port`,
`domain` ou `infrastructure` apenas quando a responsabilidade existir. Mantenha
a proibição de camadas globais e a regra de dois consumidores para `shared`.

- [x] **Step 2: Atualizar o Domain Pack e o README do backend**

Em `docs/domains/quarkus/conventions.md` e `apps/backend/README.md`, remova
toda referência a `dev.blackice.features`. Registre a direção
`api -> application <- infrastructure`, a regra de portas e que `domain` é
interno ao módulo e só existe para regra pura. Não copie a tabela de arquivos
da spec para esses documentos.

- [x] **Step 3: Marcar a spec e o plano conforme a execução real**

Mude o status da spec para `implementada` somente após a suíte e a atualização
do grafo passarem. Marque os checkboxes da execução no plano apenas depois de
cada comando correspondente ter retornado sucesso.

- [x] **Step 4: Executar a atualização Graphify e revisar seu diff**

Leia `docs/architecture/graphify.md` e execute a atualização pela skill
project-scoped. Sem uma chave Gemini configurada, execute o modo AST:

```bash
graphify update .
```

Revise `git diff -- graphify-out/` para confirmar que os nós removidos são os
pacotes `features` e que os novos nós representam `ingest`, `security` e
`session`, sem arquivos fora do escopo.

- [x] **Step 5: Executar a verificação final**

Run: `git diff --check && mise exec -- mvn test && mise exec -- mvn package && git status --short`

Expected: os dois comandos Maven PASS; `git diff --check` sem saída; `git
status --short` lista somente os arquivos da migração, documentação e
`graphify-out/`. Não faça commit: apresente o diff e a evidência ao humano para
decidir o próximo gate.

### Task 5: Detalhar o módulo de ingestão por responsabilidade

**Files:**
- Move: `ingest/application/IngestService.java` → `ingest/application/usecase/IngestStudiesUseCase.java`
- Move: `ingest/application/UploadedDicom.java` → `ingest/application/input/UploadedDicom.java`
- Move: `ingest/application/{ValidatedDicom,DicomBatchValidation,DicomValidationIssue}.java` → `ingest/application/validation/`
- Move: `ingest/application/{IngestExecution,IngestResponse,StowStudyResult,StowInstanceResult}.java` → `ingest/application/result/`, renomeando os dois primeiros para `IngestResult` e removendo o código HTTP
- Move: `ingest/application/ArchiveUnavailableException.java` → `ingest/application/exception/`
- Create: `ingest/api/IngestHttpStatusResolver.java`
- Modify: `ingest/api/IngestResource.java`, portas, adaptadores, testes e documentação arquitetural.

**Interfaces:**
- Consumes: `DicomBatchValidator.validate(List<UploadedDicom>)` e
  `DicomArchiveGateway.storeStudy(String, List<ValidatedDicom>, String)`.
- Produces: `IngestStudiesUseCase.ingest(List<UploadedDicom>, String): IngestResult`;
  `IngestHttpStatusResolver.resolve(IngestResult): int`.

- [x] **Step 1: Escrever a regra arquitetural em estado RED**

`BackendArchitectureTest` passou a proibir classes de produção diretamente em
`dev.blackice.ingest.application`:

```java
@Test
void ingest_application_nao_contem_classes_de_producao_no_pacote_raiz() {
    noClasses()
        .should().resideInAPackage("dev.blackice.ingest.application")
        .check(classes);
}
```

Run: `mise exec -- mvn test -Dtest=BackendArchitectureTest -Dquarkus.http.test-port=8082`

Expected: FAIL apontando classes de ingestão ainda no pacote raiz.

- [x] **Step 2: Mover contratos e caso de uso para subpacotes coerentes**

Crie os pacotes `input`, `validation`, `result`, `exception` e `usecase`; mova
as classes da tabela acima atualizando seus `package` e imports. Renomeie
`IngestService` para `IngestStudiesUseCase` e `IngestResponse` para
`IngestResult`. Preserve a semântica DICOM, agrupamento, concorrência e
resultados por SOP; a mudança é exclusivamente estrutural.

- [x] **Step 3: Levar a política HTTP à borda**

Remova `IngestExecution` e qualquer código HTTP da aplicação. Crie
`IngestHttpStatusResolver` em `ingest.api`, com a política documentada na spec,
e faça `IngestResource` compor o `Response` com esse status e `IngestResult`.
Mantenha rotas, payload JSON e cabeçalho `X-Request-ID` inalterados.

- [x] **Step 4: Atualizar testes e completar o ciclo GREEN**

Mova `IngestServiceTest` para `ingest.application.usecase`, atualize imports e
nomes para `IngestStudiesUseCase`/`IngestResult`. Atualize `IngestResourceTest`
para mockar o novo caso de uso e conservar as asserções HTTP. Execute a regra
arquitetural primeiro e depois a suíte direcionada:

```bash
mise exec -- mvn test -Dtest=BackendArchitectureTest,IngestStudiesUseCaseTest,IngestResourceTest -Dquarkus.http.test-port=8082
```

Expected: PASS; a política 422, 503 e 200 continua coberta pelos testes de
comportamento da aplicação e da borda HTTP.

- [x] **Step 5: Documentar fronteiras e verificar o backend completo**

Substitua o `package-info.java` plano de `application` por descrições curtas em
cada subpacote. Atualize `project-structure.md`, as convenções Quarkus e o
README para mostrar as responsabilidades sem exigir essa árvore de módulos
menores. Execute `mise exec -- mvn clean test -Dquarkus.http.test-port=8082`,
`mise exec -- mvn package -DskipTests` e `git diff --check`. Depois atualize o
grafo AST com `graphify update .` e revise o diff de `graphify-out/`. A extração
semântica `graphify . --update` deve ser tentada sem solicitar segredos; neste
ambiente ela parou por não haver chave LLM configurada.
