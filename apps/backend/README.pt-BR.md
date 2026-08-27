# Backend BlackICE

<p align="center">
  Language / Idioma: <a href="README.md">🇺🇸 English</a> | <b>🇧🇷 Português</b>
</p>

Backend de produto e BFF do BlackICE, implementado com Quarkus e Java 21. Ele
mantém o domínio próprio, integra a autenticação OIDC e acessa o DCM4CHEE apenas
por DICOMweb.

## Toolchain

O `mise.toml` fixa Temurin Java 21 e Maven 3:

```powershell
mise install
mise exec -- java -version
mise exec -- mvn -version
```

## Testar e construir

```powershell
mise exec -- mvn test
mise exec -- mvn package
```

## Desenvolvimento

Copie `.env.example` para `.env`, defina os dois segredos e carregue essas
variáveis no shell. Depois execute:

```powershell
mise exec -- mvn quarkus:dev
```

As variáveis OIDC, banco de dados e DICOMweb usadas pela configuração atual são:

- `QUARKUS_OIDC_SECRET`: secret do client `blackice-quarkus`;
- `QUARKUS_OIDC_ENCRYPTION_SECRET`: chave de ao menos 32 caracteres para
  criptografar o estado da sessão;
- `QUARKUS_OIDC_AUTH_SERVER_URL`: override opcional do issuer configurado em
  `application.properties`;
- `QUARKUS_CSRF_TOKEN_SIGNATURE_KEY`: chave de ao menos 32 caracteres para assinar
  tokens CSRF emitidos por `/api/csrf`;
- `QUARKUS_DATASOURCE_JDBC_URL`: URL JDBC do PostgreSQL do produto (`product-db`),
  padrão `jdbc:postgresql://product-db:5432/${PRODUCT_DB}`;
- `QUARKUS_DATASOURCE_USERNAME`: usuário do PostgreSQL do produto;
- `QUARKUS_DATASOURCE_PASSWORD`: senha do PostgreSQL do produto;
- `BLACKICE_DICOMWEB_BASE_URL`: URL base do endpoint DICOMweb do DCM4CHEE Archive
  (padrão `http://arc:8080/dcm4chee-arc/aets/DCM4CHEE/rs`).

Banco de dados de produto e migrações:
- O banco de produto (`product-db`, PostgreSQL 17) armazena exclusivamente dados de negócio (laudos clínicos);
- Migrações Flyway são executadas automaticamente na inicialização (`quarkus.flyway.migrate-at-start=true`), gerenciando a tabela `reports` e seus constraints (`V1__create_reports.sql`);
- O Hibernate ORM valida o schema na inicialização (`quarkus.hibernate-orm.schema-management.strategy=validate`).

Propriedades configuráveis para importação, busca DICOM e laudos:
- `blackice.ingest.max-files` (padrão 500);
- `blackice.ingest.max-total-bytes` (padrão 524288000 = 500 MB);
- `blackice.ingest.max-concurrent-studies` (padrão 1);
- `blackice.dicomweb.request-timeout` (padrão 60S);
- `blackice.worklist.request-timeout` (padrão 10S);
- `blackice.reports.archive-request-timeout` (padrão 10S).

Dependências-chave das features `ingest`, `worklist` e `reports`:
- `org.dcm4che:dcm4che-core:5.34.3`: parser de metadados DICOM (bulk-data excluído);
- `io.quarkus:quarkus-rest-csrf`: proteção CSRF de endpoints mutantes;
- `io.quarkus:quarkus-hibernate-orm-panache`: persistência JPA/Hibernate Panache;
- `io.quarkus:quarkus-jdbc-postgresql`: driver JDBC PostgreSQL;
- `io.quarkus:quarkus-flyway`: migrações relacionais versionadas.

Contratos de API REST (BFF & Domínio de Produto):

### 1. Sessão e Proteção CSRF (`session` / `security`)
- `GET /api/me`: Consulta dados da sessão ativa (`SessionResponse`: `authenticated`, `username`, `roles`, `exp`); retorna `401 Unauthorized` com Problem Details se a sessão não existir ou estiver expirada (`@PermitAll`);
- `GET /api/login`: Endpoint de entrada para autenticação OIDC (`@Authenticated`), gerando redirect `302 Found` para o Keycloak Authorization Endpoint;
- `GET /api/csrf`: Emite token CSRF assinado via HMAC (`{"token":"<signature>"}`) necessário nos headers de mutação (`X-CSRF-Token`).

### 2. Importação DICOM (`ingest`)
- `POST /api/studies`: Recebe upload `multipart/form-data` de arquivos `.dcm`;
- Valida integridade de metadados em memória usando `dcm4che-core` sem carregar bulk pixel data;
- Encaminha STOW-RS autenticado ao Archive respeitando `blackice.ingest.max-files` (padrão 500), `blackice.ingest.max-total-bytes` (padrão 500 MB) e controle de concorrência.

### 3. Worklist e Busca DICOM (`worklist`)
- `GET /api/studies`: Consulta paginada de estudos via QIDO-RS (`@RolesAllowed("auth")`);
- Aceita quatro filtros opcionais combináveis: `patientName`, `patientId`, `modality`, `dateFrom`/`dateTo`, além de `limit` (padrão 20) e `offset` (padrão 0);
- Devolve metadados curados (`StudyPage`) com `items` e metadados de navegação (`limit`, `offset`, `hasPrevious`, `hasNext`).

### 4. Visualizador e Proxy WADO-RS (`viewer`)
- `GET /api/studies/{studyUid}`: Retorna metadados estruturados do estudo (séries, modalidades e contagem de instâncias);
- `GET /api/studies/{studyUid}/series/{seriesUid}/instances`: Lista SOP Instance UIDs ordenados de uma série específica;
- `GET /api/dicomweb/studies/{studyUid}/series/{seriesUid}/instances/{sopUid}/frames/1`: Proxy seguro de frame WADO-RS; consome o Archive com credenciais seguras e transmite o stream de imagem para o Cornerstone3D sem expor portas do DCM4CHEE ao browser.

### 5. Laudos Clínicos (`reports`)
- `GET /api/studies/{studyUid}/report`: Consulta o laudo associado ao estudo; retorna `204 No Content` quando não houver laudo cadastrado, ou `200 OK` com payload do laudo e header `ETag` forte opaco (`"<version>"`);
- `POST /api/studies/{studyUid}/report`: Cria o primeiro laudo (`DRAFT` ou `FINAL`); valida a existência do estudo no Archive via QIDO-RS e retorna `201 Created` com header `ETag`;
- `PUT /api/studies/{studyUid}/report`: Atualiza rascunho ou finaliza o laudo com controle de concorrência otimista via header `If-Match: "<etag>"`; retorna `200 OK` com novo `ETag`, `412 Precondition Failed` em conflito de versão concorrente, e `403 Forbidden` se o laudo já estiver finalizado ou pertencer a outro autor;
- Limite de conteúdo de 32.000 caracteres/code points validado no backend (`API_PAYLOAD_TOO_LARGE`).

Contrato de erro (todas as rotas `/api`):
- Todo erro JSON `4xx/5xx` sai em `application/problem+json` com um tipo do
  catálogo em `docs/contracts/problems/`; `type`, `code` e `status` sempre
  identificam a mesma entrada;
- Toda resposta `/api`, inclusive de sucesso, carrega `X-Trace-ID`, e o corpo
  do problema repete o mesmo valor em `traceId`. `X-Request-ID` não existe mais;
- `traceparent` W3C é a única entrada canônica de correlação e é propagado até
  as chamadas DICOMweb; um `X-Trace-ID` enviado pelo cliente é sempre
  substituído;
- `title` e `detail` vêm do catálogo, em inglês e voltados ao operador. Eles
  nunca derivam de uma exceção, e o frontend não os renderiza (as mensagens ao
  usuário são em PT-BR e vivem em `apps/frontend/src/shared/api/problems/`);
- `GET /api/login` mantém seu redirect OIDC intencional e fica fora deste
  contrato;
- Alterar, criar ou depreciar um tipo passa pela skill `problem-catalog` e pelo
  tooling em `.problem-catalog/`; nunca edite um arquivo gerado.

Verificação do catálogo:

```bash
cd .problem-catalog && mise exec -- pnpm check
```

Verificação atômica do contrato, a partir da raiz do repositório:

```bash
cd .problem-catalog && mise exec -- pnpm check
cd ../apps/backend && mise exec -- mvn test -Dquarkus.http.test-port=8082
cd ../frontend && mise exec -- pnpm test && mise exec -- pnpm build
```

Regras operacionais e de verificação:
- **Dados sintéticos**: Todos os testes e fixtures utilizam dados DICOM puramente sintéticos; nenhum dado real de paciente é permitido no repositório;
- **Observação de locks**: Concorrência entre STOW-RS e QIDO-RS é verificada no PostgreSQL do Archive (`arc-db`) via consulta de locks bloqueantes (`SELECT count(*) FROM pg_locks WHERE NOT granted;`), onde todas as amostras devem retornar `0`;
- **Evolução de paginação**: Estratégias futuras de cursor, snapshot ou projeções dedicadas de leitura são governadas pelo item [EVO-005](../../docs/architecture/evolution-backlog.md#evo-005) do backlog de evolução;
- **Evolução de laudos**: Editor Markdown rico com autosave e invalidação lógica auditada de laudos são governados respectivamente pelos itens [EVO-011](../../docs/architecture/evolution-backlog.md#evo-011) e [EVO-012](../../docs/architecture/evolution-backlog.md#evo-012) do backlog de evolução.

Não versione `.env` nem segredos.

## Organização

O código é organizado por módulo de negócio em
`src/main/java/dev/blackice/<module>/`; os testes espelham os respectivos
pacotes em `src/test/java`. Um módulo começa simples e só cria fronteiras
internas quando há responsabilidade real: `api` para HTTP,
`application`/`application.port` para casos de uso e contratos, e
`infrastructure` para adaptadores externos. A direção é
`api -> application <- infrastructure`.

Quando um fluxo realmente precisar, `application` pode separar `input`,
`usecase`, `validation`, `result` e `exception`. A ingestão usa essa divisão:
o caso de uso devolve um resultado independente de HTTP e a API converte-o no
status da resposta. Módulos menores permanecem planos até haver uma fronteira
real.

Não crie pacotes técnicos globais nem `shared` por antecipação. Um `domain/`
pertence ao módulo e só surge quando existir regra de negócio pura independente
de framework e I/O.

Leia a [estrutura canônica](../../docs/architecture/project-structure.md) antes
de adicionar um módulo e as
[convenções Quarkus](../../docs/domains/quarkus/conventions.md) antes de alterar
integrações OIDC ou DICOMweb.

O browser recebe somente o cookie de sessão HttpOnly do BFF. Tokens OIDC não
devem ser expostos ao JavaScript.
