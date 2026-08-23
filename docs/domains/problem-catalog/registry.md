# Registry: gramática, identidade, lock e gates

## Gramática dos códigos

```text
{SCOPE}_{SUBJECT}_{CONDITION}
```

- `SCOPE` é `API` ou `CLIENT`;
- `UPPER_SNAKE_CASE`;
- o sujeito precede a condição;
- o status HTTP **não** aparece no nome — `API_ARCHIVE_UNAVAILABLE`, nunca
  `API_ERROR_503`;
- a feature não aparece quando o significado é reutilizável;
- um código publicado é imutável.

## Campos de uma entrada

Toda entrada tem `type`, `code`, `scope`, `description`, `retryPolicy`, `owner`,
`extensionsSchemaRef`, `status` e `replacedBy`. Entradas `API` acrescentam
`httpStatus`, `title` e `detail`; entradas `CLIENT` são proibidas de tê-los.

- `description` é o significado interno, em português. Não é texto público.
- `title` e `detail` são públicos, em inglês, estáveis, e **nunca** derivam de
  `Exception.getMessage()`. Ver [`security.md`](./security.md).
- `owner` sai da lista fechada declarada em `owners`, no documento raiz.
- `retryPolicy` é `NEVER` ou `MANUAL`. `AUTOMATIC` é reservado e recusado pelo
  schema: usá-lo exigiria uma política própria de idempotência, backoff, limites
  e observabilidade, aprovada em spec.

## Identidade

O `type` é uma URN `urn:uuid` com UUIDv5 derivado de:

```text
blackice.problem.v1:{code}
```

dentro do `namespaceUuid` do registry, criado uma única vez no bootstrap e nunca
recalculado. Consequências práticas:

- o `code` determina a URN; não existe escolha a fazer;
- `add` **nunca** aceita UUID, URN ou namespace por parâmetro, e recusa essas
  flags explicitamente;
- renomear um code produziria outra URN, e por isso renomear é proibido;
- UUIDs nunca são reciclados.

Esses UUIDs identificam tipos de problema HTTP e de cliente. **Não são UIDs
DICOM**, não usam raiz DICOM e nunca entram em tags, estudos, séries ou
instâncias.

## Imutabilidade

Depois de publicados, são imutáveis: `type`, `code`, `scope`, `httpStatus`,
`retryPolicy` e a fingerprint do schema de extensões. O `catalog.lock.json`
guarda exatamente esses campos e recusa qualquer divergência.

`description`, `owner`, `title` e `detail` aceitam correção editorial que **não
mude a semântica**. Se o significado muda, não é correção: é um tipo novo.

## Depreciação

Uma entrada só transita de `active` para `deprecated`. Nunca é apagada, nunca é
reativada, e seu UUID nunca é reutilizado.

```bash
mise exec -- pnpm deprecate --code CODE --replaced-by CODE
```

O substituto precisa existir e estar ativo. Um `replacedBy` sempre resolve para
uma entrada utilizável — por isso o tooling recusa depreciar uma entrada que
outra já aponta como substituta. Nesse caso, pare e leve a cadeia ao humano.

## Arquivos gerados

Estes arquivos são saída do tooling e trazem cabeçalho `DO NOT EDIT`:

```text
docs/contracts/problems/catalog.lock.json
docs/contracts/problems/catalog.md
apps/backend/src/main/java/dev/blackice/shared/api/problem/generated/ProblemType.java
apps/backend/src/main/java/dev/blackice/shared/api/problem/generated/ProblemExtensions.java
apps/frontend/src/shared/api/problems/problem-types.generated.ts
apps/frontend/src/shared/api/problems/problem-extensions.generated.ts
```

Editá-los à mão é sempre erro: a próxima geração desfaz a edição, e `check`
acusa a divergência antes disso. O catálogo não é lido em runtime — Java e
TypeScript consomem apenas os artefatos gerados.

## Workflow obrigatório

1. Classifique a ocorrência conforme [`classification.md`](./classification.md).
2. Procure no catálogo por significado, status e ação do consumidor.
3. Mostre as entradas reutilizáveis **antes** de propor uma nova.
4. Confirme que a spec aprovada autoriza a entrada.
5. Execute o tooling oficial — nunca edite `catalog.json` à mão.
6. Rode `generate` e depois `check`.
7. Apresente ao humano os codes, as URNs, os arquivos alterados e os testes
   observados.
8. Pare diante de conflito, cadeia de depreciação ou ausência de gate.

## Gates

Uma spec aprovada que enumere code, significado, status, textos, retry policy e
extensão **já constitui** o gate humano para `add`. Ela autoriza exatamente o que
enumera.

Exigem gate humano novo e explícito:

- uma entrada não prevista na spec;
- depreciação ou substituição;
- qualquer tentativa de alterar um campo imutável;
- mudança no schema de uma extensão já publicada.

Um item do backlog de evolução não autoriza implementação. Nada disso autoriza
commit: ver `docs/domains/git/commit-conventions.md`.

## Verificação contínua

`pnpm check` é read-only: valida estrutura, semântica, forma canônica, lock e os
sete artefatos, comparando em memória. Ele roda no CI antes dos builds do backend
e do frontend, então uma divergência entre catálogo e código gerado quebra a
verificação em vez de chegar ao produto.
