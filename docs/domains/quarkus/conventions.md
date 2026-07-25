# Quarkus — convenções de backend

O Quarkus é o backend **de produto**: domínio próprio (metadados de negócio,
laudos, permissões) em PostgreSQL, consumindo o DCM4CHEE **só via DICOMweb**.
Semântica DICOM: ver `docs/domains/dicom/` — este backend deve respeitá-la.

## Estrutura feature-first

O backend é organizado por feature em
`dev.blackice.features.<name>`. Rotas, DTOs e colaboradores permanecem junto da
feature; testes espelham esse pacote em `src/test/java`. Não crie pacotes
técnicos globais `controller/`, `service/` ou `repository/`.

A estrutura operacional e a receita para adicionar uma feature estão em
[`docs/architecture/project-structure.md`](../../architecture/project-structure.md).
Este Domain Pack mantém as convenções específicas de Quarkus sem duplicar a
regra geral.

## Extensões e papéis

- `quarkus-rest` (Jakarta REST) — API para o frontend Vue.
- `quarkus-hibernate-orm-panache` + `quarkus-jdbc-postgresql` — domínio próprio.
- `quarkus-oidc` em **modo web-app (BFF)** — executa Authorization Code + PKCE,
  guarda estado e tokens em cookies criptografados HttpOnly no browser. Com
  `split-tokens`, os tokens são distribuídos entre cookies; não existe store
  server-side. O access token nunca é acessível ao JavaScript. Ver
  [`docs/superpowers/specs/2026-07-23-blackice-backend-frontend-design.md`](../../superpowers/specs/2026-07-23-blackice-backend-frontend-design.md).
- `quarkus-rest-client` (MicroProfile REST Client) — client tipado para o DICOMweb
  do DCM4CHEE (QIDO/WADO/STOW).
- **Identidade para o DCM4CHEE (a verificar na implementação):** no modo web-app
  **não há bearer de entrada** — só o cookie. O access token da sessão é recuperado
  pelo Quarkus a partir dos cookies criptografados durante o processamento no
  servidor e usado na chamada DICOMweb. Como esse token tem *audience* do cliente
  Quarkus e o archive valida contra o **seu próprio** cliente Keycloak, é preciso
  **audience compartilhado** ou **token exchange**
  (`quarkus-oidc-token-propagation` expõe `exchange-token`). Decisão de config
  Keycloak vai ao gate humano.

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
- Ao chamar o DCM4CHEE, **encaminhe a identidade do usuário** (propagação ou token
  exchange — ver Extensões acima) — nunca uma credencial de serviço fixa para ações
  que representam um usuário, senão a auditoria do archive perde o autor real.
- **CSRF (obrigatório no BFF):** como a sessão é cookie HttpOnly, todo endpoint que
  muda estado (STOW, criar/editar laudo) precisa de proteção CSRF — cookies
  `SameSite` + filtro CSRF do Quarkus.

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
