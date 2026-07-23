# DCM4CHEE Archive — baseline do BlackICE

## Versão fixada

O BlackICE adota **DCM4CHEE Archive 5.34.3** como baseline da integração. A release
foi publicada pelo projeto dcm4che em **2026-04-24** e está marcada como `Latest` no
repositório oficial.

Para uma implantação Docker protegida que exponha a UI e os serviços REST, a imagem
do Archive deve ser fixada em:

```text
dcm4che/dcm4chee-arc-psql:5.34.3-secure
```

Nunca use a tag mutável `latest` em ambiente reproduzível. Ainda não há um manifesto
de infraestrutura neste repositório; portanto, esta é a baseline arquitetural. Quando
o Docker Compose ou outro provisionamento for criado, ele deve referenciar exatamente
essa tag.

## Ecossistema publicado com a release

A release 5.34.3 também publica os seguintes componentes para a sua stack Docker:

- `dcm4che/slapd-dcm4chee:2.6.10-34.3` para a configuração LDAP;
- `dcm4che/postgres-dcm4chee:17.4-34` para o PostgreSQL do Archive — a release
  publica imagens de PostgreSQL 11 a 17; o BlackICE fixa a **17.4-34** (mais recente
  suportada) como baseline. Este é o Postgres **do DCM4CHEE**, distinto do banco de
  produto do Quarkus;
- `dcm4che/keycloak:25.0.6` para o Keycloak obrigatório da baseline segura do
  BlackICE;
- WildFly 39.0.1.Final como atualização interna da release do Archive.

Essas versões são registradas como referências de compatibilidade da release, não como
uma autorização para montar a infraestrutura sem definir persistência, segredos,
certificados, backup e política de atualização.

A imagem `5.34.3-secure` pressupõe a integração OIDC com Keycloak. Assim, a stack do
BlackICE deve configurar o issuer e os clientes do Keycloak antes de expor UI ou
serviços DICOMweb; isso mantém a autenticação Bearer, a propagação de token e a
auditoria definidas pelo projeto.

## Impacto para a integração do produto

O BlackICE usa o Archive como motor DICOM e o acessa por DICOMweb: STOW-RS para
ingestão, QIDO-RS para consulta e WADO-RS para recuperação. A correção dessas
integrações continua definida no Domain Pack reutilizável em `docs/domains/dicom/`;
esta página não altera seus invariantes.

## Fontes oficiais

- [Release 5.34.3](https://github.com/dcm4che/dcm4chee-arc-light/releases/tag/5.34.3)
- [Instalação do DCM4CHEE Archive](https://github.com/dcm4che/dcm4chee-arc-light/wiki/Installation)
- [Histórico da documentação de instalação — atualização para 5.34.3](https://github.com/dcm4che/dcm4chee-arc-light/wiki/Installation/_history)
- [Binários 5.x](https://sourceforge.net/projects/dcm4che/files/dcm4chee-arc-light5/)
