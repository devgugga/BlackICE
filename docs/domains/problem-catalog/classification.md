# Classificação e reutilização

Antes de tocar no registry, decida **o que** você tem em mãos. A maior parte das
ocorrências não é um problema novo, e boa parte não é problema nenhum.

## Árvore decisória

```text
Resposta HTTP observável? API
Falha local de transporte/parser/browser? CLIENT
Operação concluída completa ou parcialmente? resultado
Usuário cancelou? cancelamento
```

Percorra na ordem e pare na primeira resposta afirmativa.

### API

O backend respondeu — ou responderia — com um status `4xx/5xx` sob `/api`. É a
única categoria que vira `application/problem+json`. Toda entrada `API_*` tem
`httpStatus`, `title` e `detail`.

### CLIENT

A falha nasceu no browser e não existe resposta HTTP para descrever: a requisição
não chegou, estourou o tempo, ou a resposta não corresponde ao contrato. Entradas
`CLIENT_*` **não** têm `httpStatus`, `title` nem `detail`, porque não são
respostas HTTP.

Uma resposta HTTP recebida mas inválida é `CLIENT`, não `API`: o que falhou foi a
confiança no contrato, não o recurso.

### Resultado

A operação aconteceu e tem algo a relatar — inclusive sucesso parcial. Um lote de
ingestão em que alguns arquivos entraram e outros não é `200` com o resultado, não
um problema. Só vire problema quando **nada** pôde ser feito.

### Cancelamento

O usuário pediu para parar. É controle de fluxo: leva a `CANCELLED`, sem Problem
Details, sem `ApiError` e sem log de erro. Cancelamento não é falha.

## Motivos internos não entram no catálogo

`TIMEOUT`, `CONNECTION`, `HTTP_STATUS`, `INVALID_RESPONSE` e semelhantes são
razões internas de exceções e resultados dos módulos. Vários deles convergem para
o mesmo problema público, e isso é correto — o catálogo descreve o que o
consumidor observa, não a taxonomia de exceções Java.

Domínio e aplicação não conhecem HTTP: uma exceção de aplicação nunca carrega
status, URN ou TraceID. A tradução acontece nos mappers da fronteira `api`.

## Reutilizar é o caso normal

Um tipo representa **um significado global**. Ingestão e Worklist compartilham
`API_ARCHIVE_UNAVAILABLE` porque significado, status e ação esperada são os
mesmos. O nome não recebe prefixo de feature sem necessidade semântica; o módulo
que introduziu o problema pode ser o `owner`, e isso basta.

Antes de propor uma entrada nova, procure no catálogo por:

1. **significado** — o consumidor entenderia a mesma coisa?
2. **status HTTP** — a resposta seria a mesma?
3. **ação esperada** — o consumidor faria a mesma coisa a seguir?

Se as três respostas forem "sim", reutilize. Mostre a entrada reutilizável ao
humano antes de sugerir qualquer alternativa.

## Quando criar um tipo novo

Só quando **pelo menos um** destes muda em relação a todas as entradas
existentes:

- significado público;
- status HTTP;
- ação esperada do cliente;
- retry policy;
- schema de extensões.

Precisão de diagnóstico interno não é motivo. Se dois casos levam o consumidor à
mesma tela e à mesma decisão, eles são o mesmo problema, e a distinção pertence
ao log ou à extensão — não a um code novo.

Criar um tipo exige spec aprovada. Ver [`registry.md`](./registry.md).
