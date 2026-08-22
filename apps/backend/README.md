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

Propriedades configuráveis para importação DICOM:
- `blackice.ingest.max-files` (padrão 500);
- `blackice.ingest.max-total-bytes` (padrão 524288000 = 500 MB);
- `blackice.ingest.max-concurrent-studies` (padrão 1);
- `blackice.dicomweb.request-timeout` (padrão 60S).

Dependências-chave da feature `ingest`:
- `org.dcm4che:dcm4che-core:5.34.3`: parser de metadados DICOM (bulk-data excluído);
- `io.quarkus:quarkus-rest-csrf`: proteção CSRF de endpoints mutantes.

Não versione `.env` nem segredos.

## Organização

O código é organizado em `src/main/java/dev/blackice/features/<name>/`. Rotas,
DTOs e colaboradores ficam dentro da feature, e os testes espelham o pacote em
`src/test/java`. Não crie pacotes técnicos globais.

Leia a [estrutura canônica](../../docs/architecture/project-structure.md) antes
de adicionar uma feature e as
[convenções Quarkus](../../docs/domains/quarkus/conventions.md) antes de alterar
integrações OIDC ou DICOMweb.

O browser recebe somente o cookie de sessão HttpOnly do BFF. Tokens OIDC não
devem ser expostos ao JavaScript.
