---
type: "query"
date: "2026-07-27T18:42:32.787677+00:00"
question: "Como o frontend Vue obtém a sessão autenticada do backend?"
contributor: "graphify"
outcome: "useful"
source_nodes: ["HomePage.vue", "fetchSession()", "session.api.ts", "BFF Session Contract", "SessionResource", "SessionResponse"]
---

# Q: Como o frontend Vue obtém a sessão autenticada do backend?

## Answer

HomePage.vue chama fetchSession() em onMounted. Em session.api.ts, fetchSession() faz GET /api/me com credentials: include; HTTP 401 vira null, outros erros são lançados, e sucesso retorna SessionResponse. No backend, SessionResource combina @Path('/api') com @GET @Path('/me'): requisição anônima recebe 401; autenticada recebe subject, username e roles. Esse fluxo implementa o BFF Session Contract.

## Outcome

- Signal: useful

## Source Nodes

- HomePage.vue
- fetchSession()
- session.api.ts
- BFF Session Contract
- SessionResource
- SessionResponse