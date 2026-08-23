# Domain Pack: Catálogo de problemas ✗

Fonte da verdade sobre **como classificar, reutilizar, criar e depreciar** os
tipos de problema publicados pelo BlackICE.

**Project-scoped:** o método é geral, mas este pack cita o registry, o tooling e
os owners deste repositório. Ao transferir, leve o método e recrie o registry.

## Documentos

- [`classification.md`](./classification.md) — a árvore decisória entre API,
  CLIENT, resultado e cancelamento, e quando reutilizar em vez de criar.
- [`registry.md`](./registry.md) — gramática dos códigos, campos obrigatórios,
  identidade UUIDv5, lock, depreciação e os gates que autorizam cada comando.
- [`security.md`](./security.md) — o que pode aparecer em texto público, em
  extensão e em log; e o que nunca pode.

## O que este pack **não** contém

A lista de problemas. Ela vive no registry, e duplicá-la aqui criaria uma
segunda fonte da verdade que envelheceria:

- `docs/contracts/problems/catalog.json` — fonte machine-readable;
- `docs/contracts/problems/catalog.md` — a mesma lista, gerada para leitura
  humana;
- `docs/contracts/problems/catalog.lock.json` — o que já está publicado e
  congelado.

Para consultar um problema, leia o catálogo. Para saber o que pode ser feito com
ele, leia este pack.

## Tooling

`.problem-catalog/` é um pacote Node ESM isolado, com Node e pnpm fixados. É
tooling de engenharia, não código de produto. Comandos:

```bash
cd .problem-catalog
mise exec -- pnpm check       # valida sem escrever
mise exec -- pnpm generate    # regenera catálogo, lock, markdown, Java e TypeScript
mise exec -- pnpm add ...     # acrescenta uma entrada aprovada
mise exec -- pnpm deprecate ...
```

## Quem consome este pack

- Claude: `.claude/skills/problem-catalog/SKILL.md`
- Codex e Antigravity: `.agents/skills/problem-catalog/SKILL.md`

Ambas são wrappers finos: mandam ler e aplicar estes documentos. Nenhuma delas
repete uma regra daqui nem enumera códigos.

## Regra de ouro

> Nenhum humano e nenhum agente escreve um UUID, edita um arquivo gerado ou
> altera um campo imutável. A identidade é derivada pelo tooling; o resto é
> decisão humana registrada em spec.
