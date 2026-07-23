# DICOMweb — STOW / QIDO / WADO

Os serviços REST do DICOM. O DCM4CHEE expõe todos. **Cada verbo tem um papel; não
troque um pelo outro.** Regra mnemônica: **S**TOW=guardar, **Q**IDO=buscar,
**WADO**=recuperar pixels.

## QIDO-RS — buscar (query)

`GET` que **procura** e retorna **metadados JSON** (`application/dicom+json`).
Nunca retorna pixels. É o motor da **worklist e da busca**.

- `GET /studies?PatientID=...&StudyDate=...&ModalitiesInStudy=CT&limit=20&offset=0`
- `GET /studies/{StudyInstanceUID}/series`
- `GET /studies/{StudyInstanceUID}/series/{SeriesInstanceUID}/instances`
- Filtre com atributos DICOM como query params; pagine com `limit`/`offset`.
- Peça campos extras com `includefield`. Resposta é array de objetos onde cada tag
  é `"0020000D": {"vr":"UI","Value":["1.2.3..."]}`.

**Use para:** listar/filtrar estudos, montar a árvore estudo→série→instância antes
de abrir o viewer. **Não use** para trazer imagem.

## WADO-RS — recuperar (retrieve)

`GET` que **entrega os dados**, incluindo **pixel data** (`multipart/related`) ou
metadados/rendered.

- Instância DICOM completa: `GET /studies/{u}/series/{u}/instances/{u}`
- Frames de pixel: `GET /studies/{u}/series/{u}/instances/{u}/frames/{n}`
- Metadados de um estudo/série: `.../metadata`
- Imagem já renderizada (consumo web simples): `.../rendered` (JPEG/PNG)
- Bulk data por referência: endpoint `/bulkdata`.

**Use para:** alimentar o viewer (Cornerstone consome `imageId` do tipo
`wadors:.../frames/1`). **Não use** para buscar/filtrar — isso é QIDO.

## STOW-RS — armazenar (store)

`POST` que **envia objetos DICOM** para o archive.

- `POST /studies` com corpo `multipart/related; type="application/dicom"`, cada
  parte é um arquivo DICOM (`.dcm`). Opcional: `POST /studies/{StudyInstanceUID}`
  para forçar o estudo alvo.
- Resposta é um dataset de referência listando SOPs aceitos/rejeitados
  (`ReferencedSOPSequence` / `FailedSOPSequence`). **Sempre verifique falhas** —
  200 não garante que tudo entrou.

**Use para:** a ingestão do MVP (arrastar `.dcm` → backend → STOW).

## Autenticação (Keycloak / OIDC)

- O DCM4CHEE protegido por Keycloak espera **Bearer token** OIDC.
- O token do usuário deve ser **propagado** na chamada DICOMweb (ex.: o backend
  encaminha o access token recebido do frontend). Não use credenciais de serviço
  fixas para ações que representam um usuário — quebra a auditoria.
- CORS: se o browser chamar DICOMweb direto, o servidor precisa liberar os headers;
  se passar pelo backend (proxy), o CORS é entre browser↔backend.

## Erros comuns (o revisor sinaliza)

- [ ] Usar WADO onde deveria ser QIDO (buscar trazendo pixel) ou vice-versa.
- [ ] Ignorar `FailedSOPSequence` numa resposta de STOW (falsa sensação de sucesso).
- [ ] Montar `imageId` do viewer a partir de QIDO em vez de WADO-RS.
- [ ] Não propagar o token OIDC do usuário na chamada DICOMweb.
- [ ] Paginar QIDO no cliente (trazer tudo e cortar) em vez de `limit`/`offset`.
