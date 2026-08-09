# Importação manual DICOM via STOW-RS — design do MVP #1

**Data:** 2026-08-09  
**Status:** aprovado para planejamento

## Objetivo

Entregar uma vertical slice autenticada que permita importar manualmente arquivos
DICOM pelo navegador, valide o lote no backend e armazene as instâncias no
DCM4CHEE por STOW-RS.

Este fluxo existe para demonstração, laboratório e importação de estudos externos.
Ele não substitui a aquisição clínica: modalidades consultam uma Modality Worklist
por C-FIND e enviam imagens diretamente ao Archive por C-STORE. A lista de estudos
do BlackICE, por sua vez, será baseada em QIDO-RS e não é uma Modality Worklist.

## Escopo aprovado

- seleção múltipla de arquivos `.dcm`;
- validação DICOM no backend;
- envio apenas dos arquivos localmente válidos;
- agrupamento dos válidos por `StudyInstanceUID`;
- uma chamada STOW-RS por estudo;
- continuação dos demais estudos após uma falha;
- resultado consolidado por arquivo e estudo;
- processamento síncrono com concorrência limitada;
- autorização pela realm role `auth`;
- proteção CSRF para a sessão BFF baseada em cookie;
- lote de demonstração com até 500 arquivos e 500 MB por padrão, com limites
  configuráveis.

## Arquitetura

```text
IngestPage
  -> POST /api/studies
  -> validação local DICOM
  -> agrupamento por StudyInstanceUID
  -> STOW-RS multipart/related por estudo
  -> consolidação do resultado
  -> resposta à SPA
```

### Frontend

A feature `apps/frontend/src/features/ingest/` possuirá sua página, cliente HTTP,
tipos, componentes e testes. O router central apenas registrará a rota.

A página terá cinco estados:

1. seleção por área de arrastar e soltar ou seletor múltiplo;
2. revisão da quantidade, tamanho e nomes dos arquivos;
3. progresso percentual da transferência;
4. processamento indeterminado no backend;
5. resultado consolidado, expansível por estudo.

O cliente poderá usar `XMLHttpRequest` para expor progresso real sem adicionar uma
biblioteca HTTP. O usuário poderá cancelar enquanto a transferência estiver em
curso. Depois que o backend iniciar STOW-RS, o MVP não prometerá cancelamento
transacional. Também não haverá retentativa automática.

### Backend

A feature `dev.blackice.features.ingest` será plana e autocontida. Ela conterá
somente os colaboradores exigidos pelo fluxo:

- resource multipart protegido por role e CSRF;
- validador de metadados DICOM;
- orquestrador de agrupamento e processamento;
- cliente DICOMweb para STOW-RS;
- interpretador da resposta do Archive;
- DTOs do contrato público.

Os arquivos multipart serão materializados temporariamente em disco. O backend não
carregará o lote inteiro em memória e removerá os temporários em blocos de limpeza
executados tanto em sucesso quanto em erro ou cancelamento.

A concorrência entre estudos será configurável e começará em `1`. Isso é uma fila
efêmera dentro da requisição, não um sistema de jobs persistentes.

## Identidade e autorização

O endpoint exigirá sessão autenticada e realm role `auth`. O Quarkus recuperará o
access token da sessão web-app e reutilizará esse token na chamada ao DCM4CHEE.

O mapper `arc-audience` já inclui `dcm4chee-arc-rs` no audience do token. Portanto,
o MVP não fará token exchange nem usará credenciais fixas de serviço. O Archive
receberá a identidade do usuário responsável pela importação.

## CSRF

O backend usará `quarkus-rest-csrf` com Double Submit Cookie e token assinado.

1. A SPA chama `GET /api/csrf`.
2. O filtro cria o cookie CSRF.
3. A SPA lê o valor permitido ao JavaScript.
4. `POST /api/studies` envia o mesmo valor em `X-CSRF-TOKEN`.
5. O filtro compara header e cookie antes de entregar a requisição ao resource.

O cookie de sessão OIDC permanece HttpOnly. Somente o cookie CSRF precisa ser
legível pelo JavaScript; ele não contém credenciais de autenticação.

## Validação DICOM

O backend lerá somente os metadados necessários e não decodificará, alterará ou
re-encodará pixel data. Cada arquivo precisa:

- ser estruturalmente legível como objeto DICOM;
- conter `StudyInstanceUID`;
- conter `SeriesInstanceUID`;
- conter `SOPInstanceUID`;
- conter `SOPClassUID`.

Os UIDs vêm do objeto recebido e nunca são gerados ou corrigidos pelo BlackICE.

Para `SOPInstanceUID` repetido no lote:

- cópias byte a byte idênticas mantêm somente a primeira ocorrência;
- conteúdos diferentes com o mesmo UID rejeitam todas as ocorrências desse UID,
  pois representam uma colisão de identidade DICOM.

Um arquivo rejeitado localmente não impede o envio dos demais. Os válidos são
agrupados por `StudyInstanceUID`; arquivos de estudos diferentes geram chamadas
STOW-RS independentes.

## Integração STOW-RS

Cada grupo será enviado como `multipart/related; type="application/dicom"`. O
cliente solicitará uma representação de resposta que permita interpretar os SOPs
referenciados e falhos.

O backend nunca tratará o status HTTP isoladamente como sucesso. Ele verificará
`ReferencedSOPSequence` e `FailedSOPSequence`, associando o resultado ao
`SOPInstanceUID` de cada arquivo. Uma falha em um estudo não interrompe os grupos
seguintes.

## Contrato HTTP

### Obter proteção CSRF

```http
GET /api/csrf
```

Cria ou renova o cookie CSRF e pode responder sem conteúdo de negócio.

### Importar arquivos

```http
POST /api/studies
Content-Type: multipart/form-data
X-CSRF-TOKEN: <token>
```

O formulário terá uma coleção `files`. A resposta terá esta estrutura mínima:

```json
{
  "outcome": "PARTIAL",
  "summary": {
    "received": 120,
    "locallyValid": 116,
    "locallyRejected": 4,
    "archiveAccepted": 114,
    "archiveRejected": 2
  },
  "studies": [],
  "locallyRejectedFiles": []
}
```

`studies` discriminará o `StudyInstanceUID`, estado do grupo e resultados por SOP.
`locallyRejectedFiles` informará nome do arquivo, código estável e mensagem segura.
A resposta não exporá PatientName, PatientID, pixel data nem metadados clínicos
desnecessários.

### Política de status

- `200`: processamento concluído, inclusive com resultado parcial no corpo;
- `400`: requisição ou token CSRF inválido;
- `401`: sessão ausente;
- `403`: role `auth` ausente;
- `413`: quantidade ou tamanho acima do limite;
- `422`: nenhum arquivo localmente válido;
- `502` ou `503`: Archive indisponível antes que algum estudo seja processado.

Uma falha de infraestrutura posterior a algum grupo processado será representada
no resultado parcial `200`, preservando o que de fato ocorreu em cada estudo.

## Tratamento de falhas e privacidade

- timeouts do Archive são configuráveis;
- nenhum erro provoca rollback fictício de instâncias já aceitas;
- o frontend distingue rejeição local, rejeição STOW e falha de infraestrutura;
- não há retentativa automática no MVP;
- logs usam um identificador de requisição e contadores de tamanho, quantidade e
  duração;
- logs não contêm PatientName, PatientID, pixel data ou datasets DICOM completos.

## Testes

### Backend

- objetos sintéticos válidos, corrompidos e sem tags obrigatórias;
- múltiplos estudos no mesmo lote;
- duplicatas idênticas e colisões de `SOPInstanceUID`;
- lotes parcialmente válidos e totalmente inválidos;
- interpretação de `ReferencedSOPSequence` e `FailedSOPSequence`;
- timeout, indisponibilidade e rejeição do Archive;
- sessão, role `auth`, CSRF, limites e limpeza de temporários;
- teste de integração com o DCM4CHEE do ambiente Compose.

### Frontend

- seleção, remoção e limites do lote;
- estados de transferência, processamento, sucesso e resultado parcial;
- cancelamento durante a transferência;
- apresentação segura dos erros;
- cenário Playwright autenticado com DICOMs sintéticos.

Qualquer implementação DICOM/DICOMweb passará pelo revisor de domínio antes do
gate humano.

## Fora do MVP e evolução

Os itens adiados são mantidos em
[`docs/architecture/evolution-backlog.md`](../../architecture/evolution-backlog.md):

- `EVO-001`: jobs assíncronos e ingestão resiliente;
- `EVO-002`: token exchange entre domínios de confiança;
- `EVO-003`: RBAC de produto;
- `EVO-004`: fluxo clínico agendado com MWL, MPPS e C-STORE direto.

## Critérios de aceite

1. Um usuário com role `auth` seleciona vários DICOMs e acompanha o upload.
2. Arquivos inválidos são informados sem impedir os válidos.
3. Estudos diferentes são agrupados e enviados separadamente.
4. Falha em um estudo não impede o processamento dos demais.
5. O resultado STOW diferencia SOPs aceitos e rejeitados.
6. O DCM4CHEE registra a identidade do usuário do login.
7. CSRF, limites e limpeza de temporários são verificados por testes.
8. Nenhum pixel data é re-encodado ou armazenado pelo Quarkus.
