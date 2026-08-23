# Worklist e busca via QIDO-RS — design do MVP #2

**Data:** 2026-08-22

**Status:** aprovado para planejamento

## Objetivo

Entregar uma vertical slice autenticada que liste os estudos mais recentes do
DCM4CHEE, permita filtrá-los e pagine os resultados no Archive por QIDO-RS. Um
estudo importado pelo fluxo do MVP #1 deve aparecer nessa Worklist sem
sincronização ou persistência intermediária no banco do produto.

Esta entrega cobre somente busca e listagem de metadados. Ela não recupera pixel
data, não abre uma página vazia de estudo e não antecipa o viewer. A ação de abrir
um estudo será acrescentada no MVP #3, usando o `StudyInstanceUID` já publicado
por este contrato.

## Escopo aprovado

- carregamento automático dos estudos mais recentes ao abrir a Worklist;
- filtros por nome do paciente, Patient ID, modalidade e intervalo de datas;
- aplicação dos filtros somente por **Buscar**, Enter ou **Limpar filtros**;
- 20 estudos por página, com navegação **Anterior** e **Próxima**;
- ausência de contagem total e de navegação por número de página;
- paginação executada pelo DCM4CHEE, nunca em memória no Quarkus ou no browser;
- resposta de produto curada, sem expor DICOM JSON à SPA;
- interface em tabela no desktop e cartões em telas estreitas;
- autorização pela realm role `auth` e propagação da identidade do usuário ao
  Archive;
- ausência de persistência de metadados da Worklist no PostgreSQL do produto.

## Arquitetura

```text
WorklistPage
  -> GET /api/studies
  -> validação dos filtros e da paginação
  -> porta de busca de estudos
  -> QIDO-RS GET /studies no DCM4CHEE
  -> conversão de application/dicom+json
  -> resposta de produto curada
```

### Frontend

A feature `apps/frontend/src/features/worklist/` conterá sua página, cliente
HTTP, tipos, composable, componentes e testes. O router central apenas registrará
`/studies`; regras de busca e paginação permanecerão na feature.

A página possuirá:

- formulário com nome do paciente, Patient ID, modalidade, data inicial e data
  final;
- botões **Buscar** e **Limpar filtros**;
- lista ordenada dos estudos mais recentes;
- controles **Anterior** e **Próxima**;
- estados distintos de carregamento, lista vazia, busca sem resultados e falha
  temporária;
- ação manual **Tentar novamente**, sem retentativa automática.

Uma busca nova e a limpeza dos filtros reiniciam o offset em zero. A limpeza
também recarrega os estudos recentes. Enquanto houver uma consulta ativa, os
controles de paginação ficam desabilitados. Uma nova intenção de busca ou
navegação aborta a requisição anterior no browser e ignora qualquer resposta
obsoleta; o MVP não presume que o cancelamento do cliente interromperá uma
consulta que o Archive já começou a executar.

No desktop, os resultados serão exibidos em tabela. Em telas estreitas, cada
estudo será apresentado em cartão com a mesma informação essencial. Esta entrega
não inclui linha clicável, botão desabilitado nem rota provisória para o viewer.

### Backend

O backend ganhará o módulo `dev.blackice.worklist`, separado de `ingest`, com as
fronteiras concretas exigidas pelo fluxo:

- `api` para o endpoint e a tradução de status HTTP;
- `application` para o caso de uso, filtros, paginação e resultado;
- `application.port` para o contrato de busca consumido pelo caso de uso;
- `infrastructure.dicomweb` para montar QIDO-RS, propagar o token e interpretar
  DICOM JSON.

A direção permanece `api -> application <- infrastructure`. A aplicação não
conhece Jakarta REST, clientes HTTP ou tags no formato JSON. O adaptador usará o
contrato público `AccessTokenProvider` do módulo `security`, mas não sua
infraestrutura OIDC.

O `GET /api/studies` coexistirá com o `POST /api/studies` da ingestão. Os verbos
têm responsabilidades diferentes: GET busca metadados por QIDO-RS; POST armazena
objetos por STOW-RS.

## Identidade e semântica DICOM

`StudyInstanceUID` é a identidade estável do estudo e vem obrigatoriamente da
resposta do Archive. O BlackICE não gera, corrige nem substitui esse UID.

`PatientID` não será tratado como globalmente único. A resposta publicará também
`IssuerOfPatientID` quando o Archive o fornecer, e a interface exibirá ambos em
conjunto. A ausência do issuer será indicada visualmente como dado não informado,
sem fabricar um valor.

QIDO-RS será usado somente para busca e metadados. Esta feature não chamará
WADO-RS nem recuperará frames ou pixel data.

## Filtros

O contrato de produto aceita filtros independentes do nome de tags DICOM:

- `patientName`: busca por prefixo; o adaptador acrescenta `*` ao final;
- `patientId`: correspondência exata;
- `modality`: uma modalidade em `ModalitiesInStudy` por consulta;
- `dateFrom` e `dateTo`: limites inclusivos de `StudyDate`.

Espaços externos serão removidos. Entradas vazias serão omitidas da consulta.
Caracteres de controle e wildcards DICOM fornecidos pelo usuário serão rejeitados;
o único wildcard será o sufixo acrescentado pelo backend ao nome do paciente.
Datas chegam em ISO `YYYY-MM-DD`, são validadas como datas reais e são convertidas
para `YYYYMMDD` no QIDO-RS. Se ambas forem informadas, `dateFrom` não pode ser
posterior a `dateTo`; limites abertos são permitidos.

O frontend oferecerá **Todas** e as modalidades `CT`, `MR`, `US`, `CR`, `DX`,
`MG`, `NM`, `PT`, `XA`, `RF` e `OT`. O backend validará o valor como um único
DICOM Code String e não dependerá dessa lista visual para proteger o Archive.

## Paginação e ordenação

O contrato usa `limit` e `offset`:

- `limit` padrão 20 e máximo 100;
- `offset` padrão zero e limitado ao intervalo de 0 a 99.999 aceito pelo baseline
  do DCM4CHEE;
- a SPA usa sempre `limit=20`;
- o backend solicita `limit + 1` ao Archive, devolve no máximo `limit` itens e usa
  o excedente somente para calcular `hasNext`;
- `hasPrevious` é verdadeiro quando `offset > 0`;
- não existe chamada a `/count`, total global ou `COUNT` no banco do produto.

O adaptador enviará a extensão do DCM4CHEE
`orderby=-StudyDate,-StudyTime,StudyInstanceUID`. O UID funciona como desempate
determinístico para estudos com a mesma data e hora. Esse detalhe proprietário
fica confinado à infraestrutura; o contrato público não expõe `orderby`.

A Worklist solicitará somente os atributos necessários, usando `includefield`
quando eles não fizerem parte do retorno obrigatório:

- `StudyInstanceUID` (`0020,000D`);
- `PatientName` (`0010,0010`);
- `PatientID` (`0010,0020`);
- `IssuerOfPatientID` (`0010,0021`);
- `StudyDate` (`0008,0020`);
- `StudyTime` (`0008,0030`);
- `ModalitiesInStudy` (`0008,0061`);
- `StudyDescription` (`0008,1030`);
- `NumberOfStudyRelatedSeries` (`0020,1206`);
- `NumberOfStudyRelatedInstances` (`0020,1208`).

Não será usado `includefield=all`.

Offset é suficiente para o volume demonstrativo do MVP, mas não cria um snapshot.
Uma ingestão entre duas páginas pode deslocar resultados. Paginação estável sob
alta concorrência e alternativas escaláveis estão registradas no `EVO-005`, sem
ampliar esta implementação.

## Contrato HTTP

### Buscar estudos

```http
GET /api/studies?patientName=MARIA&patientId=123&modality=CT&dateFrom=2026-08-01&dateTo=2026-08-22&limit=20&offset=0
```

Resposta mínima:

```json
{
  "items": [
    {
      "studyInstanceUid": "1.2.840.113619.2.55.3.604688435.123.1599720123.467",
      "patientName": "MARIA^SILVA",
      "patientId": "123",
      "patientIdIssuer": "HOSPITAL-A",
      "studyDate": "2026-08-22",
      "studyTime": "10:35:12",
      "modalities": ["CT"],
      "description": "CT CHEST",
      "seriesCount": 3,
      "instanceCount": 187
    }
  ],
  "page": {
    "limit": 20,
    "offset": 0,
    "hasPrevious": false,
    "hasNext": true
  }
}
```

`StudyInstanceUID` é obrigatório. A ausência ou invalidade dessa identidade em
qualquer item invalida a resposta do Archive, em vez de produzir uma linha sem
identidade. Os demais atributos são opcionais e usam `null` ou coleção vazia no
JSON; a apresentação “Não informado” pertence ao frontend. Contagens inválidas
também são tratadas como resposta malformada, sem coerção silenciosa.

### Política de status

- `200`: página consultada, inclusive quando `items` está vazio;
- `400`: filtro, data, `limit` ou `offset` inválido;
- `401`: sessão ausente;
- `403`: role `auth` ausente;
- `413`: consulta recusada pelo Archive por amplitude; a SPA pede filtros mais
  específicos;
- `502`: resposta QIDO-RS incompatível ou DICOM JSON inválido;
- `503`: timeout, falha de conexão ou indisponibilidade do Archive.

Erros públicos terão códigos estáveis e mensagens seguras. O corpo retornado pelo
Archive não será repassado ao browser.

## Autenticação, privacidade e observabilidade

O endpoint exigirá sessão autenticada e realm role `auth`. O Quarkus recuperará o
access token da sessão BFF pelo contrato existente e o encaminhará como Bearer na
chamada QIDO-RS. Não haverá credencial fixa de serviço.

Como GET não muda estado, esta rota não exige token CSRF. Os cookies de sessão e
as demais proteções same-origin existentes permanecem inalterados.

Logs não registrarão query strings, nomes de paciente, Patient IDs nem datasets
DICOM. Serão permitidos somente identificador de correlação, status, duração,
`limit`, `offset` e presença booleana de cada filtro. O timeout da busca será
configurável, com padrão de 10 segundos, e não haverá retentativa automática no
backend ou frontend.

## Desempenho e concorrência

Cada intenção de busca ou navegação produzirá uma única chamada QIDO-RS. O
Quarkus não buscará todas as correspondências, não fará paginação em memória, não
consultará contagem total e não abrirá transação no PostgreSQL do produto.

A consulta ao Archive é somente leitura. Locks normais de leitura do PostgreSQL
podem existir, mas o critério do MVP é que eles não bloqueiem os INSERTs/UPDATEs
necessários a uma ingestão STOW concorrente. O gate integrado observará duração,
progresso das duas operações e locks bloqueantes no banco do Archive.

## Tratamento de falhas

- uma resposta obsoleta nunca substitui o resultado de uma busca mais recente;
- falha de uma página mantém os filtros preenchidos e oferece retentativa manual;
- a interface distingue lista vazia de busca sem correspondências;
- campos clínicos ausentes não invalidam um estudo que possua UID válido;
- timeouts não provocam retentativas automáticas;
- mensagens e logs não expõem o corpo da resposta DICOMweb.

## Testes

### Backend

- conversão dos filtros de produto em matching keys QIDO-RS;
- prefixo de PatientName, PatientID exato, modalidade e intervalos abertos/fechados;
- validação de datas, wildcards, `limit` e `offset`;
- ordenação determinística e lista mínima de `includefield`;
- parsing de Person Name, valores múltiplos, campos ausentes e contagens;
- UID ausente, VR incompatível e DICOM JSON malformado;
- `limit + 1`, corte da resposta e cálculo de `hasNext`/`hasPrevious`;
- garantia de uma chamada QIDO e ausência de chamada de contagem;
- propagação do token, sessão, role `auth` e política de status;
- timeout, indisponibilidade, consulta ampla e resposta inválida;
- regras ArchUnit do novo módulo.

### Frontend

- carregamento automático dos estudos recentes;
- busca somente por botão ou Enter;
- limpeza dos filtros e retorno ao offset zero;
- navegação Anterior/Próxima e bloqueio durante carregamento;
- cancelamento e descarte de respostas obsoletas;
- estados de carregamento, vazio, sem resultados, erro e retentativa;
- tabela desktop e cartões em viewport estreita;
- apresentação segura de campos opcionais.

### Integração

- importar DICOM sintético pelo STOW-RS e encontrá-lo na Worklist;
- filtrar o estudo por nome, Patient ID, modalidade e data;
- criar resultados suficientes para atravessar uma página sem usar contagem;
- executar QIDO-RS paginado enquanto outra ingestão STOW-RS progride;
- observar que a leitura não mantém lock bloqueante sobre as escritas do Archive.

Qualquer implementação DICOM/DICOMweb passará pelo revisor de domínio antes do
gate humano.

## Interface e acessibilidade

A tabela exibirá:

1. paciente;
2. Patient ID e issuer, quando disponível;
3. data e hora do estudo;
4. modalidades;
5. descrição;
6. quantidade de séries e instâncias.

Os controles possuirão labels acessíveis, foco visível e estados de carregamento
anunciáveis. A ordenação recente será indicada em texto. Botões indisponíveis
serão desabilitados sem remover sua descrição acessível.

## Fora do MVP e evolução

O item adiado é mantido em
[`docs/architecture/evolution-backlog.md`](../../architecture/evolution-backlog.md):

- `EVO-005`: paginação escalável e consistência da Worklist.

O item registra cursor/snapshot, projeção de leitura e índice de busca como opções
a avaliar quando os gatilhos objetivos ocorrerem. Ele não escolhe antecipadamente
uma solução nem autoriza sua implementação.

## Critérios de aceite

1. Ao abrir `/studies`, um usuário com role `auth` vê até 20 estudos recentes.
2. Um estudo importado pelo fluxo do MVP #1 aparece sem sincronização adicional.
3. Nome, Patient ID, modalidade e intervalo de datas filtram via QIDO-RS.
4. Uma consulta só começa por Buscar, Enter, limpeza ou navegação de página.
5. Anterior e Próxima funcionam sem consulta de total e sem paginação em memória.
6. A resposta pública não expõe DICOM JSON nem o corpo de erros do Archive.
7. `StudyInstanceUID` preserva a identidade; Patient ID é apresentado com issuer.
8. Uma ingestão STOW concorrente progride sem lock bloqueante causado pela busca.
9. Logs e erros não expõem PatientName, PatientID ou datasets DICOM.
10. Testes backend, frontend, E2E e revisão DICOM passam antes do gate humano.
