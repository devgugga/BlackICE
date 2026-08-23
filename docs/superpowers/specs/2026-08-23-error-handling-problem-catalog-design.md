# Tratamento de erros e catálogo de problemas — Design

**Data:** 2026-08-23
**Status:** design aprovado para implementação
**Escopo:** backend Quarkus, frontend Vue, contrato HTTP, TraceID, tooling e workflow de agentes

## Contexto

O BlackICE possui três formas concorrentes de representar falhas:

- a ingestão devolve respostas vazias para alguns erros, usa `IngestResult` em
  outros e transforma mensagens de `UploadError` em códigos no frontend;
- a Worklist possui o payload próprio
  `{ "code": "INVALID_SEARCH", "message": "Review the supplied search filters." }`;
- erros produzidos por autenticação, autorização, CSRF, roteamento e pelo runtime
  Quarkus não compartilham um contrato público único.

Além da inconsistência para o frontend, o código cria UUIDs de correlação dentro
de recursos individuais, mistura códigos com mensagens e captura exceções amplas
como indisponibilidade do Archive. A continuação desse padrão faria cada feature
inventar sua própria taxonomia e dificultaria correlação, localização e testes.

Esta iniciativa substitui integralmente os contratos atuais antes que o sistema
seja publicado. Não haverá camada de compatibilidade porque o BlackICE nunca foi
colocado no ar e não possui consumidores externos.

## Objetivos

1. Adotar Problem Details conforme RFC 9457 para todo erro JSON `4xx/5xx` sob
   `/api`.
2. Manter um catálogo oficial, machine-readable e compartilhado por Java,
   TypeScript, documentação e agentes.
3. Identificar tipos com URNs `urn:uuid` estáveis e códigos legíveis.
4. Preservar TraceID W3C desde a entrada HTTP até logs, resposta e chamadas
   DICOMweb.
5. Separar problemas públicos, falhas locais do browser, resultados de operação
   e taxonomias internas de exceção.
6. Garantir geração determinística, imutabilidade de identidades e extensões
   tipadas.
7. Dar aos agentes um workflow previsível para reutilizar, criar e depreciar
   tipos sem inventar UUIDs ou regras.

## Não objetivos

- manter aliases, payloads antigos, feature flags ou versões paralelas;
- OpenTelemetry Collector, Jaeger, Tempo, armazenamento ou dashboards de traces;
- retentativas automáticas;
- criar um registry de eventos internos de logging;
- carregar o catálogo em runtime;
- criar um subagente curador nesta versão;
- alterar semântica DICOM, UIDs, pixel data ou Transfer Syntax;
- expor exceções, causas, stack traces, tokens, URLs internas, payloads
  DICOMweb ou identificadores clínicos.

Collector, retenção e visualização de traces estão registrados como `EVO-006`
no backlog central. Esta spec não duplica sua justificativa nem autoriza sua
implementação.

## Princípios

### Um tipo representa um significado global

Tipos são reutilizados quando significado, política HTTP e ação esperada do
consumidor são os mesmos. Ingestão e Worklist, por exemplo, compartilham
`API_ARCHIVE_UNAVAILABLE`. O módulo que primeiro introduziu um problema pode ser
seu owner, mas o nome não recebe prefixo de feature sem necessidade semântica.

Um novo tipo só é criado quando muda pelo menos um destes elementos:

- significado público;
- status HTTP;
- ação esperada do cliente;
- retry policy;
- schema de extensões.

### O catálogo contém somente problemas observáveis

Motivos internos como `TIMEOUT`, `CONNECTION` e `HTTP_STATUS` continuam nas
exceções ou resultados internos dos módulos. Eles podem convergir para um mesmo
problema público. O catálogo não é uma lista de todas as exceções Java.

Cancelamento solicitado pelo usuário também não é problema. Ele permanece um
evento de controle que leva a `CANCELLED`, sem Problem Details nem log de erro.

### Domínio e aplicação não conhecem HTTP

Exceções de aplicação não carregam status, URN, TraceID ou objetos Jakarta REST.
Mappers na fronteira `api` traduzem exceções e resultados para o contrato
público. O código compartilhado não importa exceções internas das features.

## Arquitetura

```text
Política          docs/domains/problem-catalog/
Contrato          docs/contracts/problems/
Tooling           .problem-catalog/
Workflow agente   .agents/skills/ + .claude/skills/
Código gerado     apps/backend/ + apps/frontend/
```

### Política canônica

`docs/domains/problem-catalog/` documenta:

- critérios de classificação e reutilização;
- gramática de códigos;
- campos obrigatórios e proibidos;
- segurança de mensagens e extensões;
- imutabilidade, depreciação e gates;
- workflow de geração e validação.

Esse Domain Pack é neutro de ferramenta. Nenhum wrapper de agente duplica seu
conteúdo.

### Contrato canônico

`docs/contracts/problems/` contém:

```text
catalog.json
catalog.schema.json
catalog.lock.json
catalog.md
extensions/
  dicom-validation-violations.schema.json
```

- `catalog.json` é a fonte da verdade machine-readable;
- `catalog.schema.json` valida estrutura e campos condicionais;
- `catalog.lock.json` protege identidades e semântica publicada;
- schemas em `extensions/` definem membros adicionais por tipo;
- `catalog.md` é documentação humana gerada.

### Tooling project-scoped

`.problem-catalog/` é um pacote Node ESM isolado, com Node e pnpm fixados em
`mise.toml`. Ele possui CLI, testes e somente as dependências necessárias para
JSON Schema e geração. Esse diretório é tooling de engenharia, não aplicação de
produto.

### Artefatos gerados

Backend:

```text
apps/backend/src/main/java/dev/blackice/shared/api/problem/generated/
  ProblemType.java
  ProblemExtensions.java
```

Frontend:

```text
apps/frontend/src/shared/api/problems/
  problem-types.generated.ts
  problem-extensions.generated.ts
```

Esses são os nomes canônicos dos artefatos gerados. Todos recebem cabeçalho
`DO NOT EDIT`. O catálogo não é lido em runtime.

O uso de `shared` é justificado por dois consumidores reais: ingestão e
Worklist. Mappers específicos continuam em `ingest.api`, `worklist.api` e nas
demais features.

## Registry e identidade

### Documento raiz

O documento raiz possui `schemaVersion` inteiro, `namespaceUuid` em formato
UUID, a lista fechada inicial de owners (`platform`, `security`, `ingest`,
`worklist` e `frontend`) e `entries` como array de problemas.

O UUID do namespace é criado uma única vez pelo tooling durante o bootstrap do
catálogo e gravado explicitamente. A string usada para derivação é:

```text
blackice.problem.v1:{code}
```

Cada entrada recebe UUIDv5 determinístico dentro desse namespace e persiste o
resultado como `urn:uuid:<uuid>`. O comando `add` nunca aceita UUID informado
manualmente.

Esses UUIDs identificam tipos de problema HTTP/cliente. Eles não são UIDs DICOM,
não usam raiz DICOM e nunca entram em tags, estudos, séries ou instâncias.

### Gramática de códigos

```text
{SCOPE}_{SUBJECT}_{CONDITION}
```

Regras:

- `SCOPE` é `API` ou `CLIENT`;
- código em `UPPER_SNAKE_CASE`;
- sujeito precede a condição;
- status HTTP não aparece no nome;
- feature não aparece quando o significado é reutilizável;
- código publicado é imutável.

Exemplos válidos:

```text
API_ARCHIVE_UNAVAILABLE
API_SEARCH_TOO_BROAD
CLIENT_NETWORK_UNAVAILABLE
```

### Entrada API

```json
{
  "type": "urn:uuid:18ecaf25-83a9-5f21-81d2-2ca4ec6765a1",
  "code": "API_ARCHIVE_UNAVAILABLE",
  "scope": "API",
  "description": "Indisponibilidade temporária do Archive.",
  "httpStatus": 503,
  "title": "Archive unavailable",
  "detail": "The imaging archive is temporarily unavailable.",
  "retryPolicy": "MANUAL",
  "owner": "platform",
  "extensionsSchemaRef": null,
  "status": "active",
  "replacedBy": null
}
```

Entradas `API` exigem `httpStatus`, `title` e `detail`. Os textos públicos do
backend são em inglês, estáveis, seguros e não incorporam `Exception.message`.

### Entrada CLIENT

Entradas `CLIENT` exigem `type`, `code`, `scope`, `description`, `retryPolicy`,
`owner`, `status` e `replacedBy`. Elas proíbem `httpStatus`, `title` e `detail`
porque não representam respostas HTTP.

### Retry policy

Valores permitidos:

- `NEVER`: repetir a mesma operação sem mudança não é ação indicada;
- `MANUAL`: a UI pode oferecer retentativa quando a operação suportar;
- `AUTOMATIC`: reservado e proibido nesta versão.

Usar `AUTOMATIC` no futuro exige uma política própria de idempotência, backoff,
limites e observabilidade aprovada em spec.

### Imutabilidade e depreciação

Após publicação, são imutáveis:

- `type`;
- `code`;
- `scope`;
- `httpStatus` quando aplicável;
- `retryPolicy`;
- fingerprint do schema de extensões.

Descrição, owner, `title` e `detail` podem receber correções editoriais que não
mudem a semântica. Uma entrada só transita de `active` para `deprecated`; não é
apagada nem reativada. `replacedBy` aponta para o código substituto quando
existir. UUIDs nunca são reciclados.

O lock contém os campos imutáveis e a fingerprint do schema. Geração recusa
mutação ou remoção de entradas bloqueadas. Mudança semântica cria novo tipo.

## Catálogo inicial

Os UUIDs abaixo não são escritos na spec: serão atribuídos mecanicamente pelo
bootstrap aprovado. Todos os demais campos semânticos já estão autorizados.

### Problemas API

| Code | HTTP | Retry | Owner | Title | Detail | Extensão |
| :-- | --: | :-- | :-- | :-- | :-- | :-- |
| `API_REQUEST_INVALID` | 400 | `NEVER` | platform | Invalid request | The request is invalid or malformed. | — |
| `API_UPLOAD_EMPTY` | 400 | `NEVER` | ingest | Empty upload | Select at least one file to upload. | — |
| `API_SEARCH_INVALID` | 400 | `NEVER` | worklist | Invalid search | Review the supplied search filters. | — |
| `API_AUTHENTICATION_REQUIRED` | 401 | `NEVER` | security | Authentication required | Authentication is required to access this resource. | — |
| `API_ACCESS_DENIED` | 403 | `NEVER` | security | Access denied | You do not have permission to access this resource. | — |
| `API_CSRF_INVALID` | 403 | `MANUAL` | security | Request verification failed | The request could not be verified. | — |
| `API_RESOURCE_NOT_FOUND` | 404 | `NEVER` | platform | Resource not found | The requested resource was not found. | — |
| `API_METHOD_NOT_ALLOWED` | 405 | `NEVER` | platform | Method not allowed | The requested method is not allowed for this resource. | — |
| `API_REPRESENTATION_NOT_ACCEPTABLE` | 406 | `NEVER` | platform | Representation not acceptable | The requested response format is not supported. | — |
| `API_PAYLOAD_TOO_LARGE` | 413 | `NEVER` | platform | Payload too large | The request exceeds the permitted size. | — |
| `API_SEARCH_TOO_BROAD` | 413 | `NEVER` | worklist | Search too broad | Refine the search filters and try again. | — |
| `API_MEDIA_TYPE_UNSUPPORTED` | 415 | `NEVER` | platform | Unsupported media type | The request media type is not supported. | — |
| `API_DICOM_VALIDATION_FAILED` | 422 | `NEVER` | ingest | DICOM validation failed | None of the uploaded files passed validation. | `dicom-validation-violations` |
| `API_INTERNAL_ERROR` | 500 | `MANUAL` | platform | Internal server error | An unexpected error occurred. | — |
| `API_ARCHIVE_RESPONSE_INVALID` | 502 | `MANUAL` | platform | Invalid Archive response | The imaging archive returned an unexpected response. | — |
| `API_ARCHIVE_UNAVAILABLE` | 503 | `MANUAL` | platform | Archive unavailable | The imaging archive is temporarily unavailable. | — |

O status `403` de `API_CSRF_INVALID` é deliberado: a requisição foi compreendida,
mas sua execução foi recusada por falha na verificação de segurança. Ele
substitui o `400` atual.

### Problemas CLIENT

| Code | Retry | Owner | Significado |
| :-- | :-- | :-- | :-- |
| `CLIENT_NETWORK_UNAVAILABLE` | `MANUAL` | frontend | A requisição não alcançou o backend. |
| `CLIENT_REQUEST_TIMEOUT` | `MANUAL` | frontend | O browser observou timeout. |
| `CLIENT_RESPONSE_INVALID` | `MANUAL` | frontend | A resposta não corresponde ao contrato. |
| `CLIENT_CSRF_COOKIE_MISSING` | `MANUAL` | frontend | O endpoint respondeu sem criar o cookie CSRF esperado. |
| `CLIENT_UNEXPECTED_ERROR` | `MANUAL` | frontend | Fallback sanitizado para falha local desconhecida. |

### Extensão de violações DICOM

`API_DICOM_VALIDATION_FAILED` possui no nível raiz:

```json
{
  "violations": [
    {
      "itemIndex": 0,
      "code": "MISSING_STUDY_INSTANCE_UID",
      "message": "Required DICOM attribute is missing."
    }
  ]
}
```

O schema exige:

- `itemIndex` inteiro não negativo, correspondente à ordem do upload;
- `code` entre os códigos públicos de validação DICOM aprovados;
- `message` segura em inglês.

O backend não devolve filename nessa extensão porque nomes de arquivo podem
conter informação identificável. O frontend associa `itemIndex` aos arquivos
que já mantém localmente. O schema inicial reconhece:

- `MALFORMED_DICOM`;
- `MISSING_STUDY_INSTANCE_UID`;
- `MISSING_SERIES_INSTANCE_UID`;
- `MISSING_SOP_INSTANCE_UID`;
- `MISSING_SOP_CLASS_UID`;
- `DUPLICATE_IDENTICAL`;
- `SOP_UID_COLLISION`.

Esses códigos são detalhes tipados da extensão, não novos Problem Types.

## Contrato HTTP RFC 9457

Todo erro JSON `4xx/5xx` sob `/api` usa `application/problem+json`:

```json
{
  "type": "urn:uuid:18ecaf25-83a9-5f21-81d2-2ca4ec6765a1",
  "title": "Archive unavailable",
  "status": 503,
  "detail": "The imaging archive is temporarily unavailable.",
  "code": "API_ARCHIVE_UNAVAILABLE",
  "traceId": "4bf92f3577b34da6a3ce929d0e0e4736"
}
```

Regras:

- `type`, `code` e `status` devem identificar a mesma entrada;
- `title` e `detail` vêm do catálogo, nunca da exceção;
- `traceId` é extension member no nível raiz;
- extensões específicas também ficam no nível raiz;
- `instance` é omitido nesta versão, pois é opcional e não será confundido com
  TraceID;
- `retryPolicy` não é repetido no payload: clientes gerados o resolvem pelo
  tipo;
- respostas não JSON fora de `/api` não pertencem a este contrato;
- o redirect intencional de `/api/login` para OIDC não é erro e fica fora.

### Cobertura de framework

O mesmo contrato cobre erros originados antes do método do recurso:

- autenticação ausente;
- acesso negado;
- CSRF inválido;
- parâmetros, multipart ou JSON malformados;
- rota API inexistente;
- método não permitido;
- representação ou media type incompatível;
- limite global do corpo.

A implementação poderá combinar exception mappers Quarkus REST e filtro Vert.x
conforme a fase real em que cada resposta nasce. A escolha interna não altera o
contrato e só é aceita quando testes de integração cobrirem toda a matriz.

## TraceID end-to-end

`traceparent` W3C é a forma canônica de propagação.

```text
browser/proxy → Quarkus → DICOMweb
                    ↓
             logs e resposta
```

Regras:

- Quarkus continua um `traceparent` válido recebido ou cria novo trace;
- `X-Trace-ID` enviado pelo cliente nunca substitui o contexto canônico;
- toda resposta `/api`, inclusive sucesso, inclui `X-Trace-ID`;
- todo Problem Details inclui o mesmo `traceId` no corpo;
- logs incluem `traceId` e `spanId` como campos estruturados;
- chamadas DICOMweb injetam `traceparent` explicitamente enquanto os adapters
  usarem `java.net.http.HttpClient`;
- o frontend mostra TraceID somente em falhas, como referência copiável;
- `X-Request-ID` e UUIDs criados dentro dos recursos são removidos;
- a saída OTLP e os Dev Services de observabilidade ficam desabilitados; o
  contexto continua sendo criado e propagado sem instalar Collector.

O TraceID identifica uma execução distribuída. Ele não é guardado dentro de
exceções de domínio ou aplicação.

## Backend Quarkus

### Fronteira compartilhada

```text
dev.blackice.shared.api.problem
├── generated
├── ApiProblem
├── ProblemResponseFactory
├── TraceContext
├── ApiProblemFallbackMapper
└── ApiTraceFilter
```

- `ProblemType` gerado expõe URN, code, status, textos e retry policy;
- `ApiProblem` representa os membros RFC 9457;
- `ProblemResponseFactory` só aceita tipo catalogado e extensão tipada;
- `TraceContext` lê o span OpenTelemetry ativo;
- o fallback converte falha inesperada em `API_INTERNAL_ERROR`;
- o filtro aplica `X-Trace-ID` a todas as respostas `/api`.

### Mappers por módulo

- `ingest.api` traduz upload vazio, lote inválido e indisponibilidade total;
- `worklist.api` traduz busca inválida, consulta ampla e falhas do Archive;
- a fronteira de segurança traduz autenticação, autorização e CSRF;
- o compartilhado não importa exceções das features.

### Captura de exceções

Capturas genéricas que convertem qualquer bug em falha de conexão são removidas.
Adapters traduzem somente exceções externas conhecidas. Exceções inesperadas
sobem ao fallback `500`.

Logging ocorre uma vez:

- erros esperados do cliente: `INFO` ou `WARN`, sem stack trace;
- indisponibilidade externa conhecida: `WARN` com razão segura;
- falha inesperada: `ERROR` com stack trace interno;
- corpo, query clínica, token, filename, UID e payload DICOMweb não são logados.

Exceções inesperadas também obedecem à regra geral de não incorporar dados de
paciente em mensagens ou causas criadas pela aplicação.

### Resultado da ingestão

- sucesso completo ou parcial: `200` com `IngestResult`;
- nenhum arquivo localmente válido: `422` com
  `API_DICOM_VALIDATION_FAILED` e `violations`;
- nenhum estudo armazenado por indisponibilidade do Archive: `503` com
  `API_ARCHIVE_UNAVAILABLE`;
- upload vazio, limites e falhas inesperadas usam seus Problems catalogados;
- resposta `503` não inclui UIDs, causas nem detalhes do Archive.

## Frontend Vue

### Fronteira compartilhada

```text
src/shared/api/problems/
├── problem-types.generated.ts
├── problem-extensions.generated.ts
├── api-problem.ts
├── api-error.ts
├── parse-problem.ts
└── problem-messages.pt-BR.ts
```

`ApiError` expõe:

- `type`;
- `code`;
- `scope`;
- `status` apenas para API;
- `traceId` quando disponível;
- `retryPolicy` resolvida pelo catálogo gerado.

### Parsing

- `fetch` e XHR usam o mesmo parser;
- combinação `type + code + status` é validada;
- corpo e `X-Trace-ID` divergentes produzem `CLIENT_RESPONSE_INVALID`;
- resposta malformada ou tipo desconhecido produz `CLIENT_RESPONSE_INVALID`;
- TraceID válido do header é preservado quando o corpo não é confiável;
- texto bruto da resposta nunca entra em `Error.message`;
- rede e timeout criam problemas `CLIENT_*`;
- abort solicitado pelo usuário permanece controle de fluxo.

### Apresentação

- mensagens PT-BR ficam em mapa central e exaustivo por `ProblemCode`;
- TypeScript falha quando tipo user-facing não possui mensagem;
- features controlam layout e ação, não significado ou texto básico;
- `MANUAL` permite botão de retentativa somente onde a operação suportar;
- nenhuma retentativa é automática;
- `detail` do backend não é renderizado diretamente;
- TraceID aparece como referência copiável.

### Substituição dos contratos atuais

- `WorklistErrorResponse` e `WorklistError` são removidos;
- `UploadError` deixa de usar `message` como código e é substituído pelo contrato
  compartilhado;
- `UNKNOWN_ERROR`, `UPLOAD_FAILED:<status>`, `CSRF_TOKEN_FAILED:<status>` e
  demais strings ad hoc são removidas;
- sessão interpreta `API_AUTHENTICATION_REQUIRED` como ausência de sessão;
- backend, frontend e testes são migrados na mesma iniciativa.

Não haverá adapters, aliases, flags ou versão de compatibilidade.

## Gerador

### Comandos

```text
check       valida sem escrever
generate    regenera Java, TypeScript, Markdown e lock
add         adiciona entrada aprovada e calcula UUIDv5
deprecate   deprecia entrada e registra substituição
```

Todos são não interativos. `add` exige os campos necessários por flags, não
aceita UUID e não grava placeholders.

### Garantias

- ordenação determinística por `code`;
- line endings e formatação canônicos;
- ausência de timestamps nos artefatos;
- geração byte-a-byte estável;
- rejeição de code ou URN duplicado;
- verificação de UUIDv5 contra namespace e code;
- validação condicional de `API` e `CLIENT`;
- referência de extensão existente;
- fingerprint de extensão compatível com lock;
- proibição de mutação, remoção, reativação ou reciclagem;
- comparação em memória no comando read-only `check`;
- fixtures positivas e negativas.

## Workflow dos agentes

Será criada a skill `problem-catalog` em:

- `.agents/skills/problem-catalog/` para Codex e Antigravity;
- `.claude/skills/problem-catalog/` para Claude Code.

Os wrappers são finos e mandam aplicar o Domain Pack. `AGENTS.md` recebe somente
um apontamento curto para a skill e a fonte canônica.

Workflow obrigatório:

1. Classificar a ocorrência como API, CLIENT, resultado ou cancelamento.
2. Pesquisar o catálogo por significado, status e ação do consumidor.
3. Mostrar entradas reutilizáveis antes de propor nova.
4. Confirmar autorização semântica na spec aprovada.
5. Executar o tooling oficial para reutilizar, adicionar ou depreciar.
6. Gerar e validar todos os artefatos.
7. Apresentar codes, URNs, arquivos e testes observados.
8. Parar diante de conflito ou ausência de gate.
9. Nunca editar generated files ou commitar sem autorização.

Uma spec aprovada que enumere code, significado, status, textos, retry policy e
extensão já constitui o gate humano. Entrada não prevista exige novo gate.
Depreciação, substituição ou tentativa de alterar campo imutável exige aprovação
explícita.

Não será criado subagente. A decisão segue o Domain Pack de autoria: skill é
preferível quando o workflow depende do pedido e de gate humano. Um curador
isolado só será reconsiderado após evidência de auditoria recorrente que se
beneficie de contexto ou permissões separadas.

## Testes e gates

### Catálogo e tooling

- JSON Schema e campos condicionais;
- UUIDv5 determinístico;
- unicidade e ordenação;
- lock e depreciação;
- fingerprint de extensões;
- geração idempotente;
- golden files e fixtures negativas.

### Backend

- `application/problem+json`;
- campos RFC 9457 e extensões;
- igualdade entre corpo e `X-Trace-ID`;
- matriz `400/401/403/404/405/406/413/415/422/500/502/503`;
- autenticação, autorização, CSRF, roteamento e limite global;
- ausência de dados sensíveis;
- fallback inesperado e logging único;
- propagação do mesmo TraceID ao mock DICOMweb;
- sucesso e parcial de ingestão preservados como resultado.

### Frontend

- parser de combinações válidas e inválidas;
- divergência de TraceID;
- rede, timeout, resposta inválida e erro inesperado;
- cancelamento sem problema;
- mapa PT-BR exaustivo;
- retry manual;
- páginas de ingestão e Worklist;
- sessão e redirecionamento OIDC.

### Gates humanos e de domínio

1. Catálogo, tooling, Domain Pack e skill.
2. Problem Details, OpenTelemetry e cobertura do framework.
3. Migração de mappers e captura de exceções.
4. Parser e migração do frontend.
5. Integração, documentação e remoção final dos contratos antigos.

Alterações de adapters DICOMweb passam pelo revisor DICOM antes do gate da fase.
A implementação não é liberável enquanto backend e frontend não estiverem no
novo contrato na mesma branch.

## Critérios de aceite

- todo erro JSON `4xx/5xx` de `/api` usa tipo catalogado;
- Java e TypeScript consomem somente artefatos gerados;
- nenhum UUID de tipo é criado manualmente;
- todos os problemas frontend usam `API_*` ou `CLIENT_*`;
- cancelamentos não são erros;
- TraceID é contínuo entre entrada, logs, resposta e chamada DICOMweb;
- mensagens públicas são seguras e PT-BR é centralizado;
- catálogo, lock e codegen são determinísticos;
- a skill reutiliza, cria e deprecia tipos sob os gates definidos;
- formatos, classes, headers e códigos antigos foram removidos;
- testes do tooling, backend, frontend, integração e revisão DICOM passam;
- nenhum Collector ou backend de traces foi adicionado.

## Estratégia de entrega

1. Criar Domain Pack, registry, schema, lock, gerador, generated files e skill.
2. Adicionar contexto OpenTelemetry, contrato compartilhado, fallback e cobertura
   de framework.
3. Migrar ingestão e Worklist e estreitar capturas genéricas.
4. Criar parser compartilhado e migrar ingestão, Worklist e sessão no frontend.
5. Executar testes integrados, revisão DICOM, documentação e remoção completa do
   legado.

Rollback, se necessário durante desenvolvimento, é da mudança inteira. Não será
mantida uma rota compatível dentro do produto.
