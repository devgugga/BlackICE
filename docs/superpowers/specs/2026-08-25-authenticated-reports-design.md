# Laudos autenticados — design do MVP #4

- **Data:** 2026-08-25
- **Status:** design aprovado; implementação depende de plano e gates por fase
- **Escopo:** quarto e último fluxo ponta a ponta do MVP BlackICE

## Objetivo

Entregar um laudo textual autenticado, persistido no banco de produto e vinculado
ao estudo por `StudyInstanceUID`. O fluxo deve permitir criar e editar um
rascunho, finalizá-lo de forma irreversível e consultá-lo junto às imagens sem
armazenar pixels ou reimplementar capacidades do DCM4CHEE.

O resultado fecha a vertical slice:

```text
ingestão STOW-RS
→ Worklist QIDO-RS
→ viewer QIDO/WADO-RS
→ laudo autenticado no PostgreSQL do produto
```

## Decisões aprovadas

- Existe no máximo um laudo por `StudyInstanceUID`.
- O laudo pode ser criado como rascunho ou diretamente como finalizado.
- Um rascunho é editável; um laudo finalizado é imutável.
- Somente o autor cria, edita e finaliza seu laudo; qualquer usuário com role
  `auth` pode consultá-lo.
- A autoria usa o `sub` OIDC como identidade estável e captura o
  `preferred_username` como nome de exibição no momento da criação.
- O conteúdo do MVP é texto simples com quebras de linha, sem Markdown, HTML ou
  template clínico preenchido automaticamente.
- O salvamento é explícito. Não existe autosave no MVP.
- Abrir um estudo sem laudo não cria registro vazio. A persistência começa no
  primeiro salvamento ou na finalização direta.
- A criação confirma via QIDO-RS que o estudo existe. Leituras e alterações
  posteriores dependem somente do PostgreSQL.
- Concorrência usa ETag/`If-Match`; uma versão obsoleta nunca sobrescreve a atual.
- Nenhum laudo, inclusive rascunho, pode ser excluído no MVP.
- O painel de laudo integra o viewer sem reduzir o viewport abaixo de uma largura
  segura e permanece utilizável em modo somente-laudo abaixo do gate do viewer.

## Escopo

### Backend

- módulo de negócio `dev.blackice.reports`;
- persistência PostgreSQL com schema versionado por Flyway;
- contratos `GET`, `POST` e `PUT` sob `/api/studies/{studyInstanceUid}/report`;
- validação QIDO-RS da existência do estudo na criação;
- autoria, autorização, transição de estado e concorrência otimista;
- respostas Problem Details catalogadas, TraceID e logs sanitizados.

### Frontend

- feature `src/features/reports/` com API, tipos, composable, componentes e testes;
- painel adaptativo composto pelo viewer;
- edição explícita de rascunho, finalização confirmada e leitura imutável;
- preservação local de texto não salvo e recuperação segura de conflitos;
- modo somente-laudo em telas nas quais o Cornerstone3D não inicializa.

### Infraestrutura e verificação

- conexão do backend ao `product-db` já existente na composição local;
- migrations automáticas e health/readiness coerentes com a dependência do banco;
- E2E sintético atravessando os quatro fluxos do MVP;
- execução da suíte Playwright no gate de CI do MVP;
- revisão DICOM e gate humano ao fim de cada fase.

## Fora de escopo

- Markdown, preview, toolbar, comandos, atalhos e autosave: `EVO-011`;
- exclusão física ou lógica, invalidação, motivo e trilha de auditoria:
  `EVO-012`;
- múltiplos laudos, revisões, adendos, coautoria ou assinatura digital;
- RBAC clínico além da role técnica `auth`: `EVO-003`;
- templates por modalidade, frases prontas ou conteúdo estruturado;
- exportação PDF, impressão, compartilhamento ou integração RIS/HIS;
- persistência do laudo como DICOM SR; `EVO-009` cobre somente medições do viewer
  e não autoriza converter o laudo textual do produto em SR;
- viewer mobile com pixels: `EVO-010`.

## Invariantes DICOM

- `StudyInstanceUID` vem do Archive ou da aquisição e é preservado exatamente.
- O backend valida a sintaxe sem aparar, normalizar, mudar zeros ou gerar outro UID.
- O laudo referencia o estudo por `StudyInstanceUID`, nunca por ID interno do
  DCM4CHEE e nunca por `PatientID`.
- A confirmação de existência usa QIDO-RS. O módulo de laudos não usa STOW-RS nem
  WADO-RS.
- Nenhum pixel, dataset DICOM ou payload QIDO é persistido no PostgreSQL do
  produto.
- O `id` interno do laudo e as URNs de Problem Details não são UIDs DICOM.

## Arquitetura

### Backend modular

O módulo começa com as fronteiras concretas abaixo, sem pacotes vazios:

```text
dev.blackice.reports
├─ api/                       contrato HTTP e mapeamento de problemas
├─ application/
│  ├─ input/                 comandos independentes de HTTP
│  ├─ result/                projeção pública independente de transporte
│  ├─ exception/             falhas de aplicação sem status ou URN
│  ├─ port/                  persistência e existência do estudo
│  └─ usecase/               consultar, criar, salvar e finalizar
├─ domain/                   Report, ReportStatus e transições puras
└─ infrastructure/
   ├─ persistence/           Panache/PostgreSQL
   └─ dicomweb/              confirmação mínima via QIDO-RS
```

A direção permanece `api → application ← infrastructure`. Entidades Panache,
Jakarta REST, status HTTP, ETag e Problem Details não entram no domínio ou nos
casos de uso.

O adaptador QIDO do módulo não importa implementações internas de `viewer` ou
`worklist`. Ele implementa uma porta pequena de existência de estudo e reutiliza
somente contratos públicos realmente compartilhados, como o provedor de access
token do módulo `security` e o injetor W3C de trace.

### Frontend por feature

```text
apps/frontend/src/features/reports/
├─ ReportPanel.vue
├─ ReportPanel.spec.ts
├─ ReportEditor.vue
├─ ReportEditor.spec.ts
├─ report.api.ts
├─ report.api.spec.ts
├─ report.types.ts
├─ useStudyReport.ts
└─ useStudyReport.spec.ts
```

`ViewerPage` compõe `ReportPanel`, mas não absorve regra de laudo. O painel não
importa objetos Cornerstone nem controla o ciclo de vida do viewport.

## Modelo de domínio e persistência

### Report

```text
Report
  id                    BIGINT IDENTITY interno
  studyInstanceUid      identidade exata do estudo
  authorId              sub OIDC capturado na criação
  authorDisplayName     snapshot de preferred_username
  status                DRAFT | FINAL
  content               texto simples
  version               concorrência otimista
  createdAt             instante UTC do servidor
  updatedAt             instante UTC do servidor
  finalizedAt           instante UTC ou null
```

### Regras

- `studyInstanceUid`, `authorId` e `authorDisplayName` são imutáveis.
- `studyInstanceUid` possui constraint `UNIQUE` no banco.
- `content` deve conter ao menos um code point que não seja whitespace.
- O conteúdo suporta no máximo 32.000 code points Unicode.
- A validação de vazio não altera o valor persistido: espaços internos, quebras
  de linha e caracteres são preservados exatamente.
- `createdAt` nunca muda; `updatedAt` muda em todo salvamento aceito;
  `finalizedAt` é preenchido uma única vez ao entrar em `FINAL`.
- Um laudo `FINAL` é terminal e nenhuma mutação posterior é aceita.
- Não há coluna de exclusão, tabela de revisão ou tabela de auditoria no MVP.

### Máquina de estados

```text
ausente ── criar rascunho ──> DRAFT
ausente ── finalizar direto ─> FINAL
DRAFT  ── salvar ────────────> DRAFT
DRAFT  ── finalizar ─────────> FINAL
FINAL  ── qualquer mutação ──> conflito
```

### Migration

A primeira migration usa `BIGINT GENERATED BY DEFAULT AS IDENTITY` como chave
primária interna, `VARCHAR(64)` para o UID exato, `TEXT` para autoria e conteúdo,
`VARCHAR` com check fechado para o status, `BIGINT` para a versão e
`TIMESTAMPTZ` para os instantes. Ela cria:

- unicidade e `NOT NULL` em `study_instance_uid`;
- checks de status, tamanho do conteúdo e coerência entre `status` e
  `finalized_at`;
- `NOT NULL` nos campos de autoria, conteúdo, versão e timestamps obrigatórios.

A aplicação usa PostgreSQL real em testes de integração por Dev Services ou
Testcontainers; não troca a semântica por um banco em memória.

O backend passa a incluir as extensões Quarkus de Panache, JDBC PostgreSQL e
Flyway. `compose.apps.yml` fornece ao backend URL, usuário e senha do
`product-db`, sem duplicar o banco que já existe em `compose.yml`.

## Identidade, autenticação e autorização

- Todos os endpoints exigem role `auth`.
- `authorId` vem exclusivamente do `sub` do token OIDC da sessão BFF.
- `authorDisplayName` usa `preferred_username`; na ausência do claim, usa `sub`
  como fallback defensivo, igual ao contrato de sessão existente.
- Nenhum campo de autoria é aceito do body ou de headers definidos pelo cliente.
- Qualquer usuário com role `auth` pode executar `GET`.
- Somente `authorId == sub atual` pode executar `PUT`.
- O autor da criação é sempre a identidade atual; não existe criação em nome de
  terceiro.
- `POST` e `PUT` exigem a proteção CSRF já usada pelo BFF.
- O access token nunca chega ao JavaScript. Ele é recuperado no backend somente
  para confirmar a existência do estudo via QIDO durante `POST`.

## Contrato HTTP

### Representação pública

```json
{
  "studyInstanceUid": "1.2.840.113619.2.55.3.604688123.123.1700000000.1",
  "authorDisplayName": "dr.teste",
  "status": "DRAFT",
  "content": "Achados do exame.\n\nConclusão.",
  "editable": true,
  "createdAt": "2026-08-25T15:00:00Z",
  "updatedAt": "2026-08-25T15:03:00Z",
  "finalizedAt": null
}
```

- `authorId`, `id` interno e `version` não aparecem no JSON.
- `editable` é calculado para a sessão atual e só é verdadeiro para autor +
  `DRAFT`.
- A versão viaja somente como ETag forte. O frontend trata o valor completo do
  header como opaco: não interpreta, incrementa ou sintetiza.
- Toda resposta do recurso, inclusive `204`, usa `Cache-Control: no-store` e
  `X-Trace-ID`.

### Consultar

```http
GET /api/studies/{studyInstanceUid}/report
```

Resultados:

- `200 OK`, JSON e `ETag` quando o laudo existe;
- `204 No Content` quando não existe;
- Problem Details catalogado para UID inválido, autenticação ou falha inesperada.

O `GET` consulta somente PostgreSQL. Não revalida o estudo no Archive. Portanto,
um laudo já persistido continua acessível quando QIDO/WADO estiver indisponível.

### Criar ou finalizar diretamente

```http
POST /api/studies/{studyInstanceUid}/report
Content-Type: application/json
X-CSRF-TOKEN: ...

{
  "content": "Texto do laudo.",
  "status": "DRAFT"
}
```

`status` é obrigatório e aceita `DRAFT` ou `FINAL`. O fluxo é:

1. validar UID, body, status e conteúdo;
2. verificar localmente se já existe laudo;
3. confirmar por QIDO-RS que o estudo existe, usando o UID exato;
4. inserir em transação curta;
5. deixar a constraint de unicidade resolver uma corrida entre criações.

O adaptador QIDO solicita apenas o necessário para confirmar o estudo, propaga
identidade e `traceparent`, exige resposta DICOM JSON válida e nunca persiste o
payload.

Resultados:

- `201 Created`, `Location` para a mesma rota, JSON e `ETag`;
- `404` quando o Archive confirma que o estudo não existe;
- `409` quando o laudo já existe ou uma corrida perde a constraint;
- falhas catalogadas de autenticação, autorização, CSRF, validação ou Archive.

Nenhuma transação de banco permanece aberta durante a chamada QIDO.

### Salvar ou finalizar rascunho

```http
PUT /api/studies/{studyInstanceUid}/report
Content-Type: application/json
If-Match: "etag-opaco-recebido-no-get-ou-post"
X-CSRF-TOKEN: ...

{
  "content": "Texto revisado.",
  "status": "FINAL"
}
```

`If-Match` é obrigatório e deve repetir exatamente o ETag conhecido pelo
cliente. O caso de uso aceita `DRAFT → DRAFT` e `DRAFT → FINAL` na mesma
transação. A mudança para `FINAL` persiste o conteúdo enviado e o status de forma
atômica.

Resultados:

- `200 OK`, JSON e novo `ETag`;
- `400` para `If-Match` ausente ou malformado;
- `403` quando a sessão não pertence ao autor;
- `404` quando não existe laudo;
- `409` quando o laudo já está finalizado;
- `412` quando a versão atual diverge do `If-Match`;
- demais falhas catalogadas.

## Concorrência

O adaptador de persistência efetua update condicionado pela versão atual. A
comparação não é apenas uma leitura seguida de escrita: a própria operação de
update precisa provar atomicamente a versão esperada.

Em `412`, o backend não altera nada. O frontend mantém o conteúdo local e oferece
duas ações explícitas:

- copiar/manter o texto local;
- recarregar a versão atual do servidor, com confirmação antes de descartar o
  texto local.

Não existe merge automático nem política de última gravação vence.

## Catálogo de problemas

### Reutilização obrigatória

| Code | Ocorrência neste fluxo |
| :-- | :-- |
| `API_AUTHENTICATION_REQUIRED` | sessão ausente ou token rejeitado |
| `API_ACCESS_DENIED` | usuário autenticado sem autoria para mutar |
| `API_CSRF_INVALID` | mutação sem verificação CSRF válida |
| `API_REQUEST_INVALID` | UID, JSON, status, conteúdo ou `If-Match` inválido/ausente |
| `API_PAYLOAD_TOO_LARGE` | conteúdo acima de 32.000 code points |
| `API_RESOURCE_NOT_FOUND` | estudo inexistente na criação ou laudo inexistente no `PUT` |
| `API_ARCHIVE_UNAVAILABLE` | timeout ou conexão QIDO indisponível |
| `API_ARCHIVE_RESPONSE_INVALID` | resposta QIDO fora do contrato esperado |
| `API_INTERNAL_ERROR` | falha inesperada sanitizada |
| `CLIENT_CSRF_COOKIE_MISSING` | browser não recebeu cookie CSRF esperado |
| `CLIENT_NETWORK_UNAVAILABLE` | requisição não alcançou o backend |
| `CLIENT_REQUEST_TIMEOUT` | timeout observado pelo browser |
| `CLIENT_RESPONSE_INVALID` | resposta recebida fora do contrato público |
| `CLIENT_UNEXPECTED_ERROR` | fallback local sanitizado |

`204 No Content` é ausência normal, não Problem Details. Cancelamento de saída ou
fechamento do painel é controle de fluxo, não falha catalogada.

### Novos tipos autorizados por esta spec

Esta seção é o gate humano de `add` para exatamente os dois tipos abaixo. O
tooling oficial deriva as URNs UUIDv5; ninguém escreve UUID manualmente.

#### `API_RESOURCE_CONFLICT`

| Campo | Valor autorizado |
| :-- | :-- |
| `scope` | `API` |
| `description` | `A operação solicitada conflita com o estado atual do recurso.` |
| `httpStatus` | `409` |
| `title` | `Resource conflict` |
| `detail` | `The resource state conflicts with the requested operation.` |
| `retryPolicy` | `MANUAL` |
| `owner` | `platform` |
| `extensionsSchemaRef` | `null` |
| `status` | `active` |
| `replacedBy` | `null` |

Usos autorizados: criação quando o laudo já existe, corrida de unicidade e
mutação de laudo finalizado.

#### `API_RESOURCE_VERSION_CONFLICT`

| Campo | Valor autorizado |
| :-- | :-- |
| `scope` | `API` |
| `description` | `A versão informada pelo cliente não corresponde à versão atual do recurso.` |
| `httpStatus` | `412` |
| `title` | `Resource version conflict` |
| `detail` | `The resource was changed by another request. Reload it and review your changes.` |
| `retryPolicy` | `MANUAL` |
| `owner` | `platform` |
| `extensionsSchemaRef` | `null` |
| `status` | `active` |
| `replacedBy` | `null` |

Uso autorizado: precondição `If-Match` divergente em alteração de rascunho.

Nenhum outro tipo novo, extensão, depreciação ou alteração de campo imutável é
autorizado por esta spec.

## Privacidade, logging e cache

O conteúdo do laudo é dado clínico. Nunca aparece em Problem Details, extensão,
log, métrica, evento de telemetria, mensagem de exceção ou nome de teste com dado
real.

Também não entram em logs: `StudyInstanceUID`, `authorId`, nome do autor, URL
concreta, query QIDO, token, cookie ou payload de banco/Archive. Logs usam code,
status, `traceId`, método, template de rota, duração e transição genérica, sem
identificadores clínicos.

Respostas usam `Cache-Control: no-store`. O frontend não persiste conteúdo em
`localStorage`, `sessionStorage`, IndexedDB ou cache global; o texto não salvo
vive apenas na memória do componente enquanto a página estiver aberta.

Como o MVP aceita somente texto simples, a apresentação usa escaping padrão do
Vue e `white-space: pre-wrap`. Não existe `v-html` nem sanitizador de HTML neste
fluxo.

## Experiência do usuário

### Layout adaptativo

- Em viewport com largura de pelo menos 1440 px, o painel abre por padrão ao lado
  das imagens. Pode ser redimensionado dentro de limites que preservam pelo menos
  720 px úteis para o viewport e uma largura utilizável para o editor.
- Entre 1024 e 1439 px, o painel começa fechado e abre como drawer sobreposto. O
  Cornerstone não é redimensionado ou reinicializado.
- Abaixo de 1024 px, o Cornerstone continua bloqueado pelo gate existente e o
  laudo ocupa a largura disponível. A tela informa que imagens exigem tela maior.
- Fechar e reabrir o painel não recria viewport, RenderingEngine, `ToolGroup`,
  filas ou medições.

### Estados

- carregando;
- ausente, com editor vazio ainda não persistido;
- rascunho editável pelo autor;
- rascunho somente leitura para outro usuário;
- finalizado somente leitura;
- falha isolada com mensagem PT-BR, TraceID e ação conforme `retryPolicy`.

Falhas do laudo não removem o viewer. Falhas QIDO/WADO do viewer não removem um
laudo já persistido. Os carregamentos podem ocorrer em paralelo, com estados e
cancelamentos independentes.

### Edição

- O campo começa vazio e usa placeholder, não conteúdo clínico predefinido.
- A interface mostra contagem até 32.000 code points, nome do autor, status e
  horário do último salvamento.
- `Salvar rascunho` escolhe `POST` ou `PUT` conforme existência.
- `Finalizar` abre confirmação explícita da irreversibilidade e executa `POST`
  ou `PUT` atômico conforme existência.
- Fechar o painel preserva texto em memória.
- Navegar para outro estudo, voltar à Worklist, recarregar ou fechar a página com
  alterações abre confirmação antes da perda.
- `Escape` fecha o drawer sem descartar texto.
- Depois de finalizado, o campo editável vira apresentação textual somente
  leitura, preservando quebras de linha.

### Acessibilidade

- Painel, editor, status, contagem e ações possuem nomes acessíveis.
- Mudanças de carregamento, salvamento, conflito e finalização são anunciadas sem
  mover foco inesperadamente.
- O drawer oferece botão de fechamento visível e resposta a `Escape`.
- A confirmação de finalização prende foco enquanto aberta e devolve o foco ao
  acionador ao fechar.
- Ordem de tabulação, foco visível e contraste seguem as convenções Vue do projeto.

## Testes

### Backend — domínio e aplicação

- validação do `StudyInstanceUID` sem normalização;
- conteúdo vazio, whitespace e limite de 32.000 code points;
- captura imutável de autor e nome de exibição;
- transições ausente/DRAFT/FINAL e terminalidade de `FINAL`;
- leitura por qualquer role `auth` e mutação exclusiva do autor;
- criação direta como `FINAL`;
- nenhuma exclusão disponível;
- concorrência otimista sem última gravação vence;
- QIDO chamado somente na criação e nunca em `GET`/`PUT`.

### Backend — persistência e HTTP

- migration Flyway em PostgreSQL real;
- constraint única por `study_instance_uid` e corrida entre criações;
- update atômico condicionado por versão;
- `200`, `201`, `204`, `400`, `401`, `403`, `404`, `409`, `412`, `413`, `500`,
  `502` e `503` conforme o contrato;
- `Location`, ETag opaco, `If-Match`, `Cache-Control: no-store` e `X-Trace-ID`;
- CSRF nas mutações e ausência de CSRF no `GET`;
- mapeamento exclusivo para tipos catalogados;
- ausência de conteúdo, UID e autoria em logs e problemas;
- QIDO mínimo, bearer server-side, `traceparent` e resposta DICOM JSON válida;
- prova de que a chamada QIDO não mantém transação de banco aberta;
- regras ArchUnit para o novo módulo.

### Frontend

- parsing de `200` + ETag e `204` sem corpo;
- criação DRAFT/FINAL e atualização com `If-Match` exato;
- dirty state, confirmação de saída e fechamento sem descarte;
- finalização irreversível e apresentação somente leitura;
- rascunho de terceiro somente leitura;
- conflito `409` e versão `412` com texto local preservado;
- problems reutilizados e novos mapeados no catálogo PT-BR central;
- painel aberto/recolhido, drawer, `Escape` e retorno de foco;
- larguras 1920, 1366, 1024 e 390 px;
- modo somente-laudo abaixo de 1024 px;
- abertura repetida sem recriar ou vazar recursos Cornerstone;
- acessibilidade por teclado e anúncios de estado.

### Integração e E2E

Com dados DICOM exclusivamente sintéticos:

1. autenticar `dr.teste`;
2. ingerir um estudo por STOW-RS;
3. encontrá-lo na Worklist por QIDO-RS;
4. abrir o viewer e renderizar uma série por WADO-RS;
5. confirmar `204` e editor vazio;
6. salvar rascunho e recarregar a página;
7. editar com o ETag retornado;
8. provocar uma alteração concorrente e confirmar preservação local no `412`;
9. finalizar e verificar leitura imutável após novo reload;
10. confirmar que outro usuário autenticado lê, mas não altera;
11. repetir em workspace largo, médio e modo somente-laudo;
12. confirmar que nenhum token, pixel ou identificador clínico aparece em storage
    do browser, logs ou erros.

A suíte Playwright de laudos entra no gate de CI junto dos fluxos Keycloak,
ingestão, Worklist, Problem Details e viewer. Falhas publicam report, trace,
screenshot e vídeo conforme a política existente, usando somente fixtures
sintéticos.

## Fases e gates humanos

### Fase 1 — catálogo, domínio e persistência

- adicionar os dois tipos pelo tooling oficial e regenerar os consumidores;
- configurar PostgreSQL/Flyway e criar a migration;
- implementar domínio, portas, casos de uso e testes de persistência/concorrência;
- gate humano sobre ciclo de vida, autoria, irreversibilidade e schema.

### Fase 2 — API e QIDO de existência

- implementar recursos HTTP, ETag, CSRF, cache e mapeamento de problemas;
- implementar confirmação QIDO sem transação aberta e sem persistir payload;
- executar testes HTTP, segurança, privacidade e ArchUnit;
- revisão DICOM obrigatória de UID, QIDO, token e ausência de pixel persistence;
- gate humano sobre contratos e falhas observáveis.

### Fase 3 — frontend e integração final do MVP

- implementar feature `reports` e painel adaptativo;
- integrar ao viewer preservando lifecycle Cornerstone;
- executar testes frontend e E2E dos quatro fluxos;
- incorporar Playwright ao gate de CI e atualizar documentação operacional;
- executar revisão DICOM final e gate visual/de negócio humano;
- quando implementação, testes e revisões estiverem estáveis, executar uma única
  atualização semântica final do Graphify e revisar seu diff.

Nenhum gate autoriza commit automaticamente. Commits continuam dependendo de
pedido explícito do humano.

## Critérios de aceite

1. Um usuário com role `auth` abre um estudo e vê o laudo sem expor token ao
   JavaScript.
2. A ausência retorna `204` e não cria rascunho automaticamente.
3. O primeiro salvamento confirma via QIDO o `StudyInstanceUID` exato.
4. Um estudo inexistente não recebe laudo órfão.
5. Um rascunho sobrevive a reload e somente seu autor pode alterá-lo.
6. Outro usuário autenticado consulta o rascunho ou final, mas recebe `403` ao
   tentar mutar.
7. O autor pode criar diretamente como final ou promover um rascunho a final em
   uma operação atômica.
8. Um laudo finalizado não pode ser editado, reaberto ou excluído.
9. ETag obsoleto produz `412` sem sobrescrever o servidor nem apagar o texto local.
10. Criações concorrentes nunca produzem dois laudos para o mesmo estudo.
11. Laudo persistido continua legível/editável quando o Archive está indisponível;
    somente a criação depende de QIDO.
12. Conteúdo permanece texto simples exato, não é interpretado como HTML e não
    ultrapassa 32.000 code points.
13. Nenhum conteúdo, UID, autor, token, pixel ou payload clínico aparece em
    Problem Details, logs ou storage persistente do browser.
14. O painel não achata o viewport abaixo do limite nem recria recursos
    Cornerstone ao abrir e fechar.
15. Abaixo de 1024 px, o laudo permanece disponível e as imagens continuam
    bloqueadas pelo gate responsivo.
16. Catálogo, backend, frontend, build, E2E, CI e revisão DICOM passam antes do
    gate humano final do MVP.

## Evoluções relacionadas

- `EVO-003`: RBAC de produto com permissões clínicas próprias;
- `EVO-009`: persistência interoperável de medições como DICOM SR;
- `EVO-010`: viewer simplificado para smartphones;
- `EVO-011`: editor Markdown rico inspirado no Obsidian e autosave com botão
  manual preservado;
- `EVO-012`: invalidação lógica com permissão, motivo e auditoria.

Esses itens permanecem fora do MVP e não são autorizados por esta spec.
