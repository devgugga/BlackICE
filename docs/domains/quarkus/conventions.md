# Quarkus — convenções de backend

O Quarkus é o backend **de produto**: domínio próprio (metadados de negócio,
laudos, permissões) em PostgreSQL, consumindo o DCM4CHEE **só via DICOMweb**.
Semântica DICOM: ver `docs/domains/dicom/` — este backend deve respeitá-la.

## Extensões e papéis

- `quarkus-rest` (Jakarta REST) — API para o frontend Vue.
- `quarkus-hibernate-orm-panache` + `quarkus-jdbc-postgresql` — domínio próprio.
- `quarkus-oidc` — valida o Bearer token do Keycloak nos endpoints.
- `quarkus-rest-client` (MicroProfile REST Client) — client tipado para o DICOMweb
  do DCM4CHEE (QIDO/WADO/STOW).
- `quarkus-oidc-token-propagation` (ou filtro equivalente) — **propaga** o access
  token do usuário nas chamadas ao DCM4CHEE.

## Fronteira de responsabilidade (crítico)

- **Não** armazene pixel data no banco do Quarkus. O cofre de imagens é o DCM4CHEE.
- O banco do Quarkus guarda **referências + negócio**: o laudo, o autor, timestamps,
  e o vínculo com o estudo via **`StudyInstanceUID`** (chave estável — ver
  `docs/domains/dicom/semantics.md`). Nunca gere esse UID; use o que veio do archive.
- Busca/worklist é **proxy/agregação** sobre QIDO-RS; pagine no servidor
  (`limit`/`offset`), não traga tudo para filtrar em memória.
- Ingestão encaminha para **STOW-RS** e **verifica `FailedSOPSequence`** na resposta
  antes de reportar sucesso.

## Autenticação e propagação de token

- Endpoints protegidos com `@Authenticated`/roles do Keycloak via `quarkus-oidc`.
- Ao chamar o DCM4CHEE, **encaminhe o token do usuário** (token propagation) — não
  use uma credencial de serviço fixa para ações que representam um usuário, senão a
  auditoria do archive perde o autor real.

## Modelagem de laudo (MVP)

```
Report (Laudo)
  id
  studyInstanceUid   ← vínculo com o estudo no DCM4CHEE (não FK física; UID DICOM)
  authorId           ← subject do token OIDC
  status             ← rascunho/finalizado
  content            ← texto do laudo
  createdAt / updatedAt
```

- O laudo referencia o estudo **por `StudyInstanceUID`**, não por um ID interno do
  archive. Assim sobrevive a re-sincronizações.

## Config e dev

- Config em `application.properties`; use Dev Services para Postgres/Keycloak em dev.
- Perfis (`%dev`, `%prod`) para URLs do DCM4CHEE/Keycloak.

## Checklist de revisão (além do checklist DICOM)

- [ ] Pixel data vazando para o banco do Quarkus?
- [ ] Laudo vinculado por `StudyInstanceUID` (não por ID interno do archive)?
- [ ] Token do usuário propagado ao DCM4CHEE (não credencial de serviço)?
- [ ] Resposta do STOW checando falhas antes de reportar sucesso?
- [ ] QIDO paginado no servidor?
