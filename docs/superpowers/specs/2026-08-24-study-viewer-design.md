# Viewer de estudo com Cornerstone3D — design do MVP #3

**Data:** 2026-08-24

**Status:** aprovado para planejamento

## Objetivo

Entregar a terceira vertical slice do BlackICE: partir de um estudo exibido na
Worklist, abrir suas séries de imagens 2D single-frame e visualizá-las em um
viewport `Stack` do Cornerstone3D. O fluxo permanece autenticado pelo BFF, usa
QIDO-RS para descobrir e classificar a hierarquia, WADO-RS Retrieve Metadata
para obter os metadados de renderização da série ativa e WADO-RS Retrieve Frames
para os pixels. O DCM4CHEE e o access token nunca são expostos ao JavaScript.

O MVP valida navegação clínica básica, troca de séries, window/level, zoom, pan,
rolagem da pilha e uma medição de comprimento temporária. Ele não pretende ser
uma estação diagnóstica completa.

## Escopo aprovado

- ação explícita **Abrir estudo** em cada resultado da Worklist;
- rota protegida `/studies/{StudyInstanceUID}`;
- um único viewport `Stack` ativo;
- seletor lateral recolhível com todas as séries do estudo;
- séries 2D single-frame compatíveis com o loader configurado;
- objetos incompatíveis visíveis e desabilitados, com motivo seguro;
- seleção automática da primeira série compatível;
- carregamento lazy da lista completa de instâncias da série ativa;
- prioridade para o primeiro frame e prefetch limitado à série ativa;
- ferramentas WindowLevel, Zoom, Pan, StackScroll, Length e Reset;
- medições mantidas apenas enquanto a página do viewer permanece montada;
- desktop e tablet em paisagem com viewer completo;
- telas estreitas com metadados e orientação para usar uma tela maior, sem
  inicializar o Cornerstone;
- retorno à Worklist preservando filtros, offset e a página de resultados da
  sessão de navegação.

## Fora de escopo

- multi-frame, cine e reprodução temporal (`EVO-007`);
- MPR, volume rendering e qualquer viewport 3D;
- layouts configuráveis ou múltiplos viewports (`EVO-008`);
- persistência, compartilhamento ou auditoria de medições; a futura persistência
  interoperável como DICOM SR está em `EVO-009`;
- viewer simplificado para smartphones (`EVO-010`);
- thumbnails de série;
- comparação com estudo anterior;
- objetos SEG, SR, PR, encapsulated PDF e outros objetos não-imagem;
- cache persistente, CDN, service worker ou armazenamento de pixels no browser,
  Quarkus ou PostgreSQL;
- retentativas automáticas de QIDO ou WADO;
- transcodificação ou re-encode de pixel data pelo BlackICE.

## Decisões arquiteturais

O viewer segue a arquitetura híbrida aprovada para o produto:

```text
WorklistPage
  -> /studies/{StudyInstanceUID}
  -> ViewerPage
       -> REST curado de estudo/séries/instâncias
       -> imageIds wadors: de mesma origem
       -> proxy WADO estreito
            -> DCM4CHEE interno
```

- O browser consome metadados curados, não DICOM JSON bruto.
- O único contrato com forma DICOMweb exposto pela API é a recuperação estreita
  do frame usado pelo Cornerstone.
- O cookie BFF acompanha as requisições same-origin. O Quarkus recupera o token
  OIDC server-side e o encaminha ao Archive.
- O DCM4CHEE continua responsável por armazenamento, query/retrieve e pixel data.
- O Quarkus não persiste pixels, não recompõe instâncias e não implementa um
  segundo PACS.

## Navegação e preservação da Worklist

A Worklist acrescentará **Abrir estudo** a cada linha/cartão. A ação usa
exatamente o `StudyInstanceUID` retornado pelo Archive e faz `push` da rota
`/studies/{uid}`; não existe ID interno alternativo.

Os filtros aplicados e o `offset` serão representados na query string canônica da
Worklist. A feature manterá em memória somente a última página concluída, indexada
pela query normalizada. Ao retornar pelo histórico do browser ou pelo botão
**Voltar à Worklist**, uma entrada cujo cache corresponda à URL é restaurada sem
nova busca. Uma URL sem cache executa a busca normalmente. Uma nova busca,
limpeza ou paginação substitui essa entrada. O cache não sobrevive a reload e não
contém pixels.

Quando o viewer for aberto por deep link, sem uma entrada anterior da Worklist, o
botão volta para `/studies`. Não será aceito um `returnUrl` arbitrário vindo da
URL.

## Experiência do viewer

### Layout

A página usa o layout visual aprovado:

- cabeçalho com ação de retorno e os metadados essenciais do estudo;
- rail lateral esquerdo recolhível com as séries;
- viewport único ocupando a área principal;
- toolbar superior ao viewport;
- estado de carregamento e falha confinado à área afetada.

O rail lista número, modalidade, descrição e quantidade de instâncias quando
disponíveis. A ausência de um atributo opcional aparece como “Não informado”. A
seleção visual e o foco por teclado são distintos e acessíveis.

Séries incompatíveis permanecem visíveis, desabilitadas e com um motivo de alto
nível, por exemplo “Objeto multi-frame ainda não suportado” ou “Tipo de objeto não
suportado”. A UI não mostra SOP Class UID, UID clínico nem resposta do Archive em
mensagens de erro.

### Ferramentas

A toolbar oferece:

- **Janela/nível**;
- **Zoom**;
- **Pan**;
- **Navegar imagens**;
- **Comprimento**;
- **Redefinir visualização**.

Uma ferramenta de interação por vez fica vinculada ao ponteiro primário. A roda
do mouse e o gesto configurado no tablet navegam a pilha. Trocar a ferramenta
altera bindings no mesmo `ToolGroup`; não recria o viewport. Reset restaura
propriedades de apresentação e enquadramento, mas não apaga medições.

As anotações Length são mantidas em memória por série enquanto a mesma
`ViewerPage` estiver montada, permitindo trocar de série e voltar. Sair da página
ou recarregá-la elimina todas as anotações. Nenhuma chamada de API grava medição.
Quando `PixelSpacing` válido estiver disponível, o comprimento é apresentado em
milímetros; sem calibração espacial, a ferramenta identifica explicitamente a
medição como não calibrada e usa pixels, sem inventar espaçamento físico.
A ausência de `PixelSpacing` não é preenchida no contrato de produto. O adapter
pode usar a escala computacional unitária exigida internamente pelo Cornerstone
somente se marcar explicitamente `hasPixelSpacing=false` ou
`usingDefaultValues=true`; esse valor nunca é apresentado ou tratado como
milímetros.

### Gate responsivo

O gate é baseado no viewport CSS, não em user-agent. O Cornerstone pode ser
inicializado quando a largura é pelo menos 768 px e a orientação é paisagem, ou
quando a largura é pelo menos 1024 px independentemente da orientação. Abaixo
desse gate, a página mostra cabeçalho, metadados e uma orientação para usar tela
maior; não importa dinamicamente o runtime do viewer, não cria
`RenderingEngine`, não solicita instâncias e não solicita frames.

Se uma janela já aberta cruza o gate para baixo, o viewer executa o mesmo cleanup
do unmount. Ao cruzar para cima, é inicializado novamente a partir do estado
reativo de seleção, sem restaurar anotações descartadas pelo cleanup.

## Frontend Vue e Cornerstone3D

A feature ficará em `apps/frontend/src/features/viewer/`, contendo:

- `ViewerPage.vue`: composição dos estados da página e do gate responsivo;
- `StudyHeader.vue`: retorno e metadados do estudo;
- `SeriesRail.vue`: seleção, recolhimento e estados das séries;
- `ViewerToolbar.vue`: ferramenta ativa e reset;
- `DicomViewport.vue`: fronteira imperativa com Cornerstone;
- `useStudyViewer.ts`: carregamento, seleção, cancelamento e gerações obsoletas;
- `viewer.api.ts`: clientes dos contratos REST curados;
- `viewer.types.ts`: tipos de transporte e apresentação;
- um adapter Cornerstone focado em inicialização, metadata provider, tools e
  cleanup, testável sem tornar os objetos WebGL parte do estado Vue.

Serão adicionados `@cornerstonejs/core`, `@cornerstonejs/tools` e
`@cornerstonejs/dicom-image-loader` em versões mutuamente compatíveis, resolvidas
uma única vez pelo lockfile. `init()` do core, tools e image loader será
idempotente e executado uma vez por aplicação, somente depois do gate responsivo.

`RenderingEngine`, viewport, `ToolGroup` e objetos do loader não entram em
`reactive()` ou `ref()` profundo. O adapter os mantém fora da reatividade, usando
estado imperativo e, quando houver exposição ao componente, `markRaw` ou
`shallowRef`. Somente IDs, fases, índice atual, ferramenta ativa e dados de UI são
reativos.

Ao selecionar outra série, o composable:

1. cancela a consulta de instâncias anterior;
2. incrementa uma geração lógica para ignorar respostas atrasadas;
3. solicita ao backend os metadados completos que ele obtém por WADO-RS
   `GET /studies/{studyUid}/series/{seriesUid}/metadata`;
4. monta os `imageId`s `wadors:` na ordem recebida;
5. substitui a stack no viewport existente;
6. carrega o primeiro frame com prioridade;
7. permite prefetch limitado apenas dos próximos frames da série ativa.

O cancelamento intencional continua sendo controle de fluxo, não `ApiError`.
Mudanças de série removem da fila as solicitações pendentes da série anterior.
Não existe prefetch de séries inativas.

No unmount, na saída do gate responsivo e em falha de inicialização parcial, o
adapter aborta requisições, remove listeners e metadata providers locais, limpa a
fila pertencente ao viewer, remove as anotações da sessão, desassocia o
`ToolGroup`, desabilita o viewport e destrói o `RenderingEngine` criado pela
página. A operação é idempotente.

## Backend Quarkus

O backend ganhará o módulo `dev.blackice.viewer`, independente de `worklist` e
`ingest`, com os pacotes concretos necessários:

- `api` para recursos HTTP, DTOs e tradução de falhas;
- `application` para casos de uso, classificação e resultados;
- `application.port` para query de metadados e recuperação de frames;
- `infrastructure.dicomweb` para QIDO-RS, WADO-RS e parsing DICOM JSON.

A direção permanece `api -> application <- infrastructure`. Aplicação e domínio
não conhecem Jakarta REST, status HTTP, URNs, `HttpClient` nem o formato DICOM
JSON. A infraestrutura depende somente do contrato público
`security.application.AccessTokenProvider` para receber o token obtido pela
fronteira.

Um leitor de atributos DICOM JSON pode ser extraído para
`shared.infrastructure.dicomweb` somente se Worklist e Viewer realmente tiverem
dois consumidores do mesmo comportamento. DTOs, regras de obrigatoriedade e
classificadores continuam pertencendo às features; não será criada uma camada
genérica antecipadamente.

## Contrato HTTP

Todas as rotas exigem sessão autenticada e realm role `auth`. Como são GETs, não
exigem token CSRF.

### Resumo do estudo e das séries

```http
GET /api/studies/{studyUid}
Accept: application/json
```

Resposta:

```json
{
  "studyInstanceUid": "1.2.840.113619.2.55.3.604688435.123.1599720123.467",
  "patientName": "MARIA^SILVA",
  "patientId": "123",
  "patientIdIssuer": "HOSPITAL-A",
  "studyDate": "2026-08-22",
  "studyTime": "10:35:12",
  "description": "CT CHEST",
  "series": [
    {
      "seriesInstanceUid": "1.2.840.113619.2.55.3.604688435.124",
      "seriesNumber": 2,
      "modality": "CT",
      "description": "AXIAL",
      "instanceCount": 187,
      "availability": "SUPPORTED",
      "unsupportedReason": null
    },
    {
      "seriesInstanceUid": "1.2.840.113619.2.55.3.604688435.125",
      "seriesNumber": 900,
      "modality": "SR",
      "description": "REPORT",
      "instanceCount": 1,
      "availability": "UNSUPPORTED",
      "unsupportedReason": "NON_IMAGE_OBJECT"
    }
  ]
}
```

`StudyInstanceUID` e cada `SeriesInstanceUID` são obrigatórios e preservados
exatamente. Os dados de paciente e descrição são opcionais. `PatientID` sempre é
interpretado junto de `IssuerOfPatientID`, nunca como identidade global.

O adapter consulta o estudo e suas séries por QIDO-RS. Para não classificar por
`Modality`, ele também obtém, por QIDO-RS em nível de instância, somente os
atributos mínimos necessários para classificar cada série: `SeriesInstanceUID`,
`SOPClassUID` e `NumberOfFrames`. Essa varredura inicial não recupera pixels nem
os metadados geométricos usados para montar a stack. A infraestrutura deve
paginar a consulta até classificar todas as instâncias do estudo; truncamento ou
inconsistência de contagem invalida a resposta em vez de produzir uma série
falsamente compatível.

Uma série é `SUPPORTED` somente quando todas as suas instâncias pertencem à
allowlist explícita de SOP Classes de imagem 2D suportadas pelo adapter e cada
`NumberOfFrames` está ausente ou vale exatamente 1. A allowlist é identificada
por SOP Class UID, coberta por fixtures, e não por `Modality`. O baseline do MVP
é:

| SOP Class | UID |
| :-- | :-- |
| Computed Radiography Image Storage | `1.2.840.10008.5.1.4.1.1.1` |
| Digital X-Ray Image Storage — For Presentation | `1.2.840.10008.5.1.4.1.1.1.1` |
| CT Image Storage | `1.2.840.10008.5.1.4.1.1.2` |
| MR Image Storage | `1.2.840.10008.5.1.4.1.1.4` |

Um SOP Class fora da allowlist produz `NON_IMAGE_OBJECT` ou
`IMAGE_SOP_CLASS_UNSUPPORTED`, conforme sua classificação. `NumberOfFrames > 1`
produz `MULTI_FRAME`. Metadado obrigatório ausente, UID inválido ou valor
malformado é resposta inválida do Archive, não uma série incompatível. Ampliar a
allowlist exige fixture de metadata e pixel data que prove decodificação no
browser, mas não autoriza multi-frame nem os objetos excluídos deste MVP.

As séries são ordenadas por `SeriesNumber` numérico crescente; ausentes vêm
depois. `SeriesInstanceUID` é o desempate determinístico. A primeira série
`SUPPORTED` é selecionada pelo frontend. Se nenhuma for compatível, a página
continua mostrando estudo e rail, mas não cria stack nem pede frames.

### Instâncias da série ativa

```http
GET /api/studies/{studyUid}/series/{seriesUid}/instances
Accept: application/json
```

Resposta:

```json
{
  "studyInstanceUid": "1.2.840.113619.2.55.3.604688435.123.1599720123.467",
  "seriesInstanceUid": "1.2.840.113619.2.55.3.604688435.124",
  "instances": [
    {
      "sopInstanceUid": "1.2.840.113619.2.55.3.604688435.126",
      "sopClassUid": "1.2.840.10008.5.1.4.1.1.2",
      "instanceNumber": 1,
      "rows": 512,
      "columns": 512,
      "samplesPerPixel": 1,
      "photometricInterpretation": "MONOCHROME2",
      "bitsAllocated": 16,
      "bitsStored": 12,
      "highBit": 11,
      "pixelRepresentation": 1,
      "planarConfiguration": null,
      "imagePositionPatient": [-127.5, -127.5, -100.0],
      "imageOrientationPatient": [1, 0, 0, 0, 1, 0],
      "pixelSpacing": [0.5, 0.5],
      "frameOfReferenceUid": "1.2.840.113619.2.55.3.604688435.127",
      "rescaleIntercept": -1024,
      "rescaleSlope": 1,
      "windowCenter": [40],
      "windowWidth": [400]
    }
  ]
}
```

O contrato inclui os atributos necessários ao metadata provider do Cornerstone e
permite `null` somente nos atributos condicionais ou opcionais conforme o IOD.
Valores multivalorados permanecem arrays. O parser não inventa orientação,
espaçamento, janela, rescale ou Frame of Reference. A ausência de um atributo
necessário ao decoder para aquela instância torna a resposta do Archive inválida.

Para produzir essa resposta, o backend usa WADO-RS Retrieve Series Metadata:

```http
GET /studies/{studyUid}/series/{seriesUid}/metadata
Accept: application/dicom+json
```

O Archive devolve um dataset de metadata para cada instância, sem Pixel Data. O
adapter valida a hierarquia e os atributos de cada dataset, descarta qualquer
`BulkDataURI` que não faça parte do contrato aprovado e publica somente o DTO
curado acima. O DICOM JSON integral nunca atravessa a fronteira para a SPA.

O endpoint confirma que o `seriesUid` pertence ao `studyUid`. Cada
`SOPInstanceUID` é obrigatório, único na resposta e preservado exatamente. Uma
instância multi-frame ou de SOP Class divergente do suporte aprovado invalida a
hipótese feita pelo resumo; a resposta é recusada em vez de entregar uma stack
parcial silenciosa.

A ordenação espacial é usada quando todas as instâncias possuem
`ImageOrientationPatient` e `ImagePositionPatient` válidos. Os seis valores de
orientação e os três valores de posição precisam ser finitos; os vetores linha e
coluna precisam ser aproximadamente unitários e ortogonais; e todas as
orientações precisam ser equivalentes à primeira dentro de tolerância numérica
de `1e-4` após normalização. A normal canônica é o produto vetorial da primeira
orientação válida. As posições são ordenadas pela projeção crescente, com
`InstanceNumber` e `SOPInstanceUID` como desempates. Se qualquer condição falhar,
aplica-se integralmente o fallback por `InstanceNumber` numérico e depois
`SOPInstanceUID`. O fallback é determinístico e não fabrica geometria.

### Proxy do primeiro frame

```http
GET /api/dicomweb/studies/{studyUid}/series/{seriesUid}/instances/{sopUid}/frames/1
Accept: multipart/related; type="application/octet-stream"; transfer-syntax=*
```

O frontend constrói o `imageId`:

```text
wadors:/api/dicomweb/studies/{studyUid}/series/{seriesUid}/instances/{sopUid}/frames/1
```

Os três UIDs são validados sintaticamente na fronteira. O Quarkus preserva a
hierarquia no caminho WADO-RS, encaminha o access token da sessão, o contexto W3C
e um `Accept` fixo compatível com o loader:
`multipart/related; type="application/octet-stream"; transfer-syntax=*`. Ele
solicita o frame 1 e transmite a resposta por streaming sem alterar bytes. O
`Content-Type` externo completo e o corpo multipart, inclusive os headers
`Content-Type` e `transfer-syntax` de cada parte, são preservados. Resposta sem
boundary ou media type compatível é inválida. O proxy não bufferiza a instância
completa, não interpreta pixel data, não altera Transfer Syntax e não recodifica
bytes.

Somente os headers necessários ao contrato são repassados. A resposta ao browser
inclui `Cache-Control: private, no-store` e `X-Trace-ID`; não expõe URL interna,
headers de autenticação ou detalhes do Archive.

Antes do primeiro byte ser comprometido, uma falha pode ser convertida em Problem
Details. Depois que o streaming começou, o status HTTP não pode ser trocado; uma
conexão interrompida ou multipart truncado é classificado pelo frontend como
falha `CLIENT_*`.

## Concorrência e desempenho

- QIDO de estudo/séries usa timeout configurável padrão de 10 segundos.
- WADO de metadata e frame usa timeout configurável padrão de 60 segundos.
- Não há retentativa automática no backend ou frontend.
- O primeiro frame entra na prioridade interativa do request pool.
- O prefetch mantém no máximo três frames seguintes enfileirados e no máximo duas
  requisições WADO de background simultâneas, sempre na série ativa.
- Trocar de série cancela ou invalida toda requisição ainda não iniciada da série
  anterior. Uma transferência já iniciada pode terminar no transporte, mas seu
  resultado obsoleto nunca substitui a série atual.
- A varredura de classificação usa QIDO-RS paginado e somente os return keys
  mínimos suportados pelo Archive. Apenas para a série ativa, o backend usa
  WADO-RS Retrieve Series Metadata, valida cada dataset e produz o contrato
  curado do metadata provider.
- Nenhuma operação abre transação no PostgreSQL do produto.

## Tratamento de falhas e catálogo de problemas

### Tipos reutilizados

| Code | Uso no viewer |
| :-- | :-- |
| `API_REQUEST_INVALID` | UID ou parâmetro de rota malformado |
| `API_RESOURCE_NOT_FOUND` | estudo, série ou instância não encontrada na hierarquia solicitada |
| `API_AUTHENTICATION_REQUIRED` | sessão ausente ou autenticação não utilizável |
| `API_ACCESS_DENIED` | identidade sem permissão para o recurso |
| `API_ARCHIVE_UNAVAILABLE` | timeout, conexão ou indisponibilidade temporária do Archive |
| `API_ARCHIVE_RESPONSE_INVALID` | DICOM JSON, hierarquia, metadata ou resposta WADO fora do contrato |
| `API_INTERNAL_ERROR` | fallback sanitizado para falha inesperada do backend |
| `CLIENT_NETWORK_UNAVAILABLE` | browser não alcançou ou perdeu a conexão com o BFF |
| `CLIENT_REQUEST_TIMEOUT` | browser observou timeout |
| `CLIENT_RESPONSE_INVALID` | resposta HTTP recebida não corresponde ao contrato ou stream terminou truncado |
| `CLIENT_UNEXPECTED_ERROR` | fallback sanitizado para falha local desconhecida |

Falha no resumo do estudo ocupa a página. Falha ao carregar instâncias ou frame
ocupa somente o viewport e mantém cabeçalho, retorno e rail utilizáveis. Não há
retentativa automática; um botão manual só aparece quando o tipo publicado tem
`retryPolicy: MANUAL`.

### Novo tipo autorizado pela spec

O catálogo ainda não será alterado nesta fase de documentação. A implementação
do viewer está autorizada a acrescentar, exclusivamente pelo tooling oficial, a
entrada abaixo:

| Campo | Valor aprovado |
| :-- | :-- |
| `code` | `CLIENT_DICOM_IMAGE_UNSUPPORTED` |
| `scope` | `CLIENT` |
| `description` | `O browser recebeu uma imagem DICOM válida, mas o loader configurado não conseguiu decodificá-la ou renderizá-la.` |
| `retryPolicy` | `NEVER` |
| `owner` | `frontend` |
| `extensionsSchemaRef` | `null` |
| `status` | `active` |
| `replacedBy` | `null` |
| `httpStatus`, `title`, `detail` | proibidos para escopo `CLIENT` |

O tooling derivará a URN UUIDv5; ninguém escreverá UUID manualmente. Esse tipo não
representa resposta inválida, falta de rede ou objeto que o backend já classificou
como incompatível. Ele representa uma imagem DICOM válida cuja combinação de
SOP Class, Transfer Syntax ou características de pixels não pôde ser tratada pelo
runtime disponível no browser. A ação esperada é escolher outra série ou usar um
viewer compatível; repetir a mesma requisição não ajuda.

## Segurança, privacidade e observabilidade

- O browser nunca recebe o access token OIDC e nunca chama o DCM4CHEE diretamente.
- O proxy usa a identidade do usuário; não usa credencial fixa de serviço.
- Nenhum log, Problem Details ou erro cliente contém `StudyInstanceUID`,
  `SeriesInstanceUID`, `SOPInstanceUID`, `PatientID`, nome do paciente, query
  clínica, payload DICOM, URL interna, token ou mensagem de exceção.
- Logs usam code, status, `traceId`, método, template de rota, duração e contagens
  não clínicas. A rota concreta com UIDs não é registrada.
- Toda resposta `/api` inclui `X-Trace-ID`; todo Problem Details traz o mesmo
  `traceId`. O frontend mostra essa referência copiável somente em falhas.
- Falhas esperadas são registradas uma vez na fronteira, sem stack trace; falhas
  inesperadas têm um único log de erro correlacionado.
- O frontend usa o mapa PT-BR central por `ProblemCode`; nunca renderiza `detail`
  diretamente.

## Testes

### Backend

- validação sintática dos três tipos de UID recebidos em paths, sem normalização
  que altere identidade;
- QIDO correto para estudo, séries e classificação mínima de instâncias;
- paginação completa da varredura de classificação e detecção de truncamento;
- classificação por `SOPClassUID` e `NumberOfFrames`, nunca por `Modality`;
- estudo misto preservando séries incompatíveis como desabilitadas;
- WADO-RS Retrieve Series Metadata somente para a série ativa e parsing de todos
  os datasets retornados;
- parser de atributos obrigatórios, opcionais, multivalorados e valores inválidos;
- confirmação da hierarquia estudo → série → instância;
- ordenação pela geometria e fallback determinístico;
- propagação de bearer token e `traceparent` para QIDO e WADO;
- negociação `transfer-syntax=*`, preservação do `Content-Type` multipart, dos
  headers de cada parte, do boundary, do streaming e do `Cache-Control` do proxy;
- prova de que o proxy não bufferiza nem transcodifica o frame;
- mapeamento de falhas antes do commit da resposta e interrupção após início do
  stream;
- autenticação, role `auth`, Problem Details e ausência de identificadores
  clínicos nas mensagens e nos logs;
- access logs configurados para usar template de rota, sem a URI concreta;
- testes ArchUnit das dependências do novo módulo.

### Frontend

- parsing dos três contratos e mapeamento central dos problems;
- seleção automática da primeira série compatível;
- todas as incompatíveis visíveis, desabilitadas e não selecionáveis;
- cancelamento, generation guard e impedimento de resposta obsoleta;
- construção exata de `wadors:` a partir dos UIDs recebidos;
- metadata provider com valores DICOM sem defaults físicos inventados e fallback
  computacional explicitamente marcado como não calibrado;
- sanitização de erros emitidos pelo runtime Cornerstone antes de qualquer log ou
  apresentação, sem imageId ou URL concreta;
- primeiro frame prioritário e ausência de prefetch em série inativa;
- troca de série reutilizando viewport e `ToolGroup`;
- bindings de WindowLevel, Zoom, Pan, StackScroll e Length;
- Reset sem apagar medições;
- Length em milímetros quando calibrado e explicitamente em pixels quando não há
  `PixelSpacing` válido;
- medições preservadas entre trocas de série e eliminadas no unmount;
- objetos Cornerstone fora da reatividade profunda;
- inicialização única e cleanup idempotente em sucesso e falha parcial;
- gate responsivo sem import, inicialização, consulta de instâncias ou WADO em
  tela estreita;
- restauração da Worklist pelo par URL/cache e fallback correto em deep link;
- navegação por teclado, foco visível, nomes acessíveis e estados anunciados.

### Integração e ponta a ponta

O cenário principal ingere por STOW-RS um estudo sintético, sem dados reais de
paciente, contendo pelo menos duas séries CT Image Storage 2D single-frame. Em
seguida:

1. encontra o estudo na Worklist por QIDO-RS;
2. abre o viewer pela ação explícita;
3. recebe o resumo do estudo e seleciona a primeira série compatível;
4. renderiza o primeiro frame pelo proxy WADO;
5. navega a stack, troca de série e usa as ferramentas;
6. cria uma medição Length, troca de série e confirma sua permanência na sessão;
7. volta à Worklist e confirma filtros, offset e resultados;
8. reabre ou recarrega o viewer e confirma que a medição não foi persistida.

Um segundo fixture mistura imagem 2D suportada com objeto incompatível e verifica
que o estudo abre parcialmente sem esconder nem tentar renderizar o objeto. Os
cenários de falha cobrem QIDO indisponível, metadata inválida, WADO recusado antes
do stream, stream interrompido e decoder incompatível.

O teste de ciclo de vida abre e fecha estudos repetidamente e verifica ausência de
listeners, viewports, `ToolGroup`s, filas e contextos WebGL órfãos. O cenário de
segurança confirma same-origin, cookie BFF, ausência de token no JavaScript e
ausência de rota pública do Archive.

## Critérios de aceite

- Um estudo ingerido no MVP #1 e encontrado no MVP #2 abre imagens por QIDO +
  WADO sem sincronização intermediária.
- Apenas a série ativa recebe por WADO-RS a lista completa de metadados de
  renderização e pixels; a varredura QIDO inicial lê somente return keys mínimos
  para classificar séries.
- O primeiro frame é priorizado e nenhuma série inativa recebe prefetch.
- Séries incompatíveis permanecem visíveis e desabilitadas.
- Uma falha de frame não remove cabeçalho, retorno ou seletor de séries.
- Nenhum pixel entra no PostgreSQL, é re-encoded pelo Quarkus ou fica em cache
  persistente controlado pelo BlackICE.
- O Archive não é público, e nenhum token é acessível ao JavaScript.
- Desktop e tablet em paisagem recebem o viewer completo; telas abaixo do gate
  recebem apenas a experiência informativa.
- Sair do viewer descarta todas as medições e recursos Cornerstone da sessão.
- Voltar ao viewer repetidamente não aumenta de forma contínua a quantidade de
  contextos WebGL ou listeners ativos.
- Todos os erros observáveis usam o catálogo central e textos seguros.

## Fases e gates humanos

### Fase 1 — Backend QIDO/WADO

Implementar contratos curados, classificação, ordenação, streaming e testes do
backend. Antes de encerrar a fase, o revisor DICOM verifica hierarquia, tags,
QIDO/WADO, UIDs, Transfer Syntax e ausência de pixel persistence. O humano valida
o contrato e o comportamento com estudos mistos.

### Fase 2 — Frontend Cornerstone e UX

Implementar navegação da Worklist, feature viewer, adapter Cornerstone,
ferramentas, lifecycle e gate responsivo. O revisor DICOM verifica imageIds,
metadata provider e correspondência instância/frame. O humano valida o fluxo
visual e as ferramentas.

### Fase 3 — Integração, catálogo e documentação

Executar os cenários ponta a ponta, criar pelo tooling a entrada
`CLIENT_DICOM_IMAGE_UNSUPPORTED`, regenerar e validar os artefatos do catálogo,
atualizar documentação operacional e realizar uma única atualização semântica
final do Graphify. O revisor DICOM faz a revisão final e o humano decide se o MVP
#3 está concluído.

Nenhum gate autoriza commit automaticamente. Commits continuam dependendo de
pedido explícito do humano.

## Itens de evolução relacionados

O escopo adiado permanece centralizado no backlog, sem autorização implícita de
implementação:

- `EVO-007`: multi-frame e cine;
- `EVO-008`: layout configurável com múltiplos viewports;
- `EVO-009`: medições persistentes como DICOM SR armazenado via STOW-RS;
- `EVO-010`: viewer simplificado e responsivo para smartphones.
