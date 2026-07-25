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

As variáveis OIDC usadas pela configuração atual são:

- `QUARKUS_OIDC_SECRET`: secret do client `blackice-quarkus`;
- `QUARKUS_OIDC_ENCRYPTION_SECRET`: chave de ao menos 32 caracteres para
  criptografar o estado da sessão;
- `QUARKUS_OIDC_AUTH_SERVER_URL`: override opcional do issuer configurado em
  `application.properties`.

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
