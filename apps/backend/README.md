# Backend BlackICE

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

As variáveis OIDC e DICOMweb usadas pela configuração atual são:

- `QUARKUS_OIDC_SECRET`: secret do client `blackice-quarkus`;
- `QUARKUS_OIDC_ENCRYPTION_SECRET`: chave de ao menos 32 caracteres para
  criptografar o estado da sessão;
- `QUARKUS_OIDC_AUTH_SERVER_URL`: override opcional do issuer configurado em
  `application.properties`;
- `QUARKUS_CSRF_TOKEN_SIGNATURE_KEY`: chave de ao menos 32 caracteres para assinar
  tokens CSRF emitidos por `/api/csrf`;
- `BLACKICE_DICOMWEB_BASE_URL`: URL base do endpoint DICOMweb do DCM4CHEE Archive
  (padrão `http://arc:8080/dcm4chee-arc/aets/DCM4CHEE/rs`).

Propriedades configuráveis para importação e busca DICOM:
- `blackice.ingest.max-files` (padrão 500);
- `blackice.ingest.max-total-bytes` (padrão 524288000 = 500 MB);
- `blackice.ingest.max-concurrent-studies` (padrão 1);
- `blackice.dicomweb.request-timeout` (padrão 60S);
- `blackice.worklist.request-timeout` (padrão 10S).

Dependências-chave das features `ingest` e `worklist`:
- `org.dcm4che:dcm4che-core:5.34.3`: parser de metadados DICOM (bulk-data excluído);
- `io.quarkus:quarkus-rest-csrf`: proteção CSRF de endpoints mutantes.

Contrato `GET /api/studies` (Worklist):
- Endpoint autenticado (`@RolesAllowed("auth")`) para consulta paginada de estudos via QIDO-RS;
- Aceita quatro filtros opcionais combináveis: `patientName`, `patientId`, `modality`, `dateFrom`/`dateTo`, além de `limit` (padrão 20) e `offset` (padrão 0);
- Devolve metadados curados (`StudyPage`) com `items` e metadados de página (`limit`, `offset`, `hasPrevious`, `hasNext`);
- Traduz falhas em payloads de erro seguros `{ "code": "...", "message": "..." }` com identificador de correlação `X-Request-ID`.

Regras operacionais e de verificação:
- **Dados sintéticos**: Todos os testes e fixtures utilizam dados DICOM puramente sintéticos; nenhum dado real de paciente é permitido no repositório;
- **Observação de locks**: Concorrência entre STOW-RS e QIDO-RS é verificada no PostgreSQL do Archive (`arc-db`) via consulta de locks bloqueantes (`SELECT count(*) FROM pg_locks WHERE NOT granted;`), onde todas as amostras devem retornar `0`;
- **Evolução de paginação**: Estratégias futuras de cursor, snapshot ou projeções dedicadas de leitura são governadas pelo item `EVO-005` do backlog de evolução.

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
