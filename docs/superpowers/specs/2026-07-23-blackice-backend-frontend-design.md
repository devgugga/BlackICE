# BlackICE — Design de arquitetura back-end e front-end

> Design formal aprovado em brainstorming (2026-07-23). Fecha os pontos técnicos
> que ficaram em aberto no rascunho de escopo
> ([`2026-07-22-blackice-pacs-decisions.md`](2026-07-22-blackice-pacs-decisions.md)).
> Baseline de infraestrutura em
> [`docs/architecture/dcm4chee-archive.md`](../../architecture/dcm4chee-archive.md).

## Decisões fechadas

| # | Decisão | Escolha |
| :-- | :-- | :-- |
| 1 | Acesso do browser ao pixel data (WADO-RS) | **Proxy pelo Quarkus** — DCM4CHEE nunca exposto ao browser |
| 2 | Formato da API que o Quarkus expõe | **Híbrido** — REST de produto curado + caminho WADO estreito para o viewer |
| 3 | Fluxo OIDC / identidade | **BFF/cookie** — Quarkus é o cliente OIDC; token nunca vive no JS |
| 4 | Topologia de deploy | **Mesma origem** via Traefik (SPA e `/api` sob um hostname) |

Princípio que orienta 1–2: **minimizar superfície**, não só esconder o archive.
O Quarkus expõe apenas as operações que o produto usa — não republica o conjunto
completo de verbos DICOMweb.

## Topologia (deploy)

Traefik é o único ponto exposto. Sob um hostname:

- `/` → SPA Vue (estático).
- `/api/*` → Quarkus.
- DCM4CHEE, Keycloak, Postgres → **rede interna**, sem rota pública direta ao browser.

```
Browser ──┬─ GET /            (SPA)            ┐
          └─ /api/* (cookie HttpOnly)          │ Traefik (TLS, 1 hostname)
                     │                          ┘
                     ▼
                  Quarkus ──DICOMweb (token propagado/trocado)──► DCM4CHEE
                     │                                              │
                     ├─ PostgreSQL (domínio de produto)            └─ Keycloak (nativo)
                     └─ Keycloak (cliente OIDC, Code+PKCE)
```

**Consequência da mesma origem:** o CORS (antigo ponto 3 do rascunho) praticamente
desaparece, e o cookie de sessão acompanha as requisições WADO do viewer com zero
configuração no Cornerstone. Separar em `app.` / `api.` no futuro reintroduz
`withCredentials: true` no loader + CORS-com-credenciais (origem específica, nunca `*`).

## Backend Quarkus

### API de produto (curada)

| Método | Rota | Papel | DICOMweb interno |
| :-- | :-- | :-- | :-- |
| `GET` | `/api/studies` | Worklist paginada/filtrada | QIDO-RS (`limit`/`offset` no servidor) |
| `GET` | `/api/studies/{studyInstanceUid}` | Detalhe do estudo (séries/instâncias) | QIDO-RS |
| `POST` | `/api/studies` | Ingestão de `.dcm` (multipart) | STOW-RS |
| `GET` | `/api/studies/{studyInstanceUid}/report` | Ler laudo | — (Postgres) |
| `POST` | `/api/studies/{studyInstanceUid}/report` | Criar laudo | — (Postgres) |
| `PUT` | `/api/studies/{studyInstanceUid}/report` | Editar laudo | — (Postgres) |
| `GET` | `/api/me` | Identidade da sessão (guarda de rota do SPA) | — |

- **Paginação** sempre no servidor sobre QIDO-RS — nunca trazer tudo para filtrar em memória.
- **Ingestão** encaminha para STOW-RS e **verifica `FailedSOPSequence`** antes de
  reportar sucesso. É `multipart/related` passthrough (ajuste de RESTEasy, não é fork).

### Caminho WADO estreito (forma DICOMweb, só para o viewer)

- `GET /api/dicomweb/studies/{studyInstanceUid}/series/{seriesInstanceUid}/...`
  no formato que o loader `wadors:` do Cornerstone3D consome direto.
- **Streaming** dos bytes de pixel — nunca buffer do estudo inteiro em memória.
- É o único ponto onde a API tem forma DICOMweb; o resto é REST de produto.

### BFF / OIDC

- `quarkus-oidc` em **modo web-app**: executa Authorization Code + PKCE, guarda o
  token **server-side**, entrega ao browser só um **cookie de sessão HttpOnly**.
- O token **nunca** vive no JavaScript (mitiga XSS).
- `GET /api/me` → 200 com claims mínimos se autenticado, 401 se não. O SPA usa isso
  como guarda de rota (401 ⇒ redireciona ao login do Quarkus).

### ⚠️ Propagação de token ao DCM4CHEE — a verificar na implementação

No modo web-app **não há bearer de entrada** para propagar — só o cookie. E o token
obtido no login tem *audience* do cliente Quarkus; o DCM4CHEE valida contra o **seu
próprio** cliente Keycloak. Logo, "propagar o token do usuário" **não é automático**:
o archive pode rejeitar por audience.

Saídas a avaliar na implementação (gate humano decide):

1. **Audience compartilhado (recomendado para o MVP)** — um *audience mapper* no
   cliente Quarkus adiciona o clientId do DCM4CHEE ao claim `aud`; o mesmo token da
   sessão é aceito pelo archive. Config declarativa, sem round-trip extra. Justo aqui
   porque tudo é domínio de confiança único, rede interna, e o token nunca toca o
   browser (BFF) — o risco que o exchange mitiga (replay em serviço não-pretendido)
   já está praticamente eliminado.
2. **Token exchange (fallback)** — trocar o token da sessão por um token audienciado
   ao archive (`quarkus-oidc-token-propagation` expõe `exchange-token`). Padrão mais
   correto de confinamento (RFC 8693), mas custo operacional maior no Keycloak.
   Preferir se um dia o DCM4CHEE sair para um realm/domínio de confiança separado.

Verificar na implementação (independe da escolha): que o DCM4CHEE valide por `aud` e
aceite o mapper, e que o usuário tenha as **realm roles** DICOM necessárias — este
segundo ponto é ortogonal ao audience e é onde o setup costuma travar.

Em ambos os casos, o access token da sessão web-app é recuperado server-side
(ex.: injeção de `AccessTokenCredential`) e usado na chamada DICOMweb. **Nunca** usar
credencial de serviço fixa para ações que representam um usuário (quebra a auditoria
do archive).

> Este ponto **invalida** a linha do `docs/domains/quarkus/conventions.md` que listava
> `quarkus-oidc-token-propagation` como resolvido — aquela linha assumia fluxo Bearer.
> O doc de convenções será corrigido para refletir a realidade BFF.
> Conhecimento de Quarkus datado de jan/2026: tratar como **a verificar**, não veredito.

### CSRF (novo requisito do BFF)

Cookie de sessão HttpOnly ⇒ toda chamada que muda estado (ingestão STOW, criar/editar
laudo) fica exposta a CSRF. Design inclui:

- Cookies de sessão com atributo **`SameSite`** (Lax/Strict conforme fluxo de login).
- **Filtro CSRF do Quarkus** nos endpoints mutantes (`POST`/`PUT`).

No fluxo Bearer isso não existiria; com BFF é obrigatório.

## Frontend Vue

- **Vue 3 + Vite + TypeScript**, `<script setup>`; Pinia (estado); Vue Router.
- **Sem cliente OIDC no browser** — autenticação é o cookie de sessão. Não há token
  em JS, não há refresh no browser, não há `beforeSend` de token nos loaders.
- **Guarda de rota:** rotas protegidas checam `/api/me`; 401 ⇒ redireciona ao login.
- **Views do MVP:**
  - Worklist — lista paginada via `/api/studies`, busca/filtro.
  - Viewer — Cornerstone3D em componentes próprios; objetos do Cornerstone **fora
    da reatividade do Vue** (`shallowRef`/`markRaw`); cleanup no `onUnmounted`;
    imageIds `wadors:` apontando para `/api/dicomweb/...` (cookie vai junto).
  - Laudo — editor de texto vinculado ao estudo, via `/api/studies/{uid}/report`.
- Convenções detalhadas em [`docs/domains/vue/`](../../domains/vue/).

## Modelagem de laudo

Sem mudança em relação a `docs/domains/quarkus/conventions.md`:

```
Report (Laudo)
  id
  studyInstanceUid   ← vínculo com o estudo (UID DICOM do archive; nunca gerado)
  authorId           ← subject do token OIDC
  status             ← rascunho | finalizado
  content            ← texto do laudo
  createdAt / updatedAt
```

Vínculo por **`StudyInstanceUID`**, não por ID interno do archive — sobrevive a
re-sincronizações.

## Invariantes preservadas (fonte: `docs/domains/dicom/`)

- UIDs vêm do archive/aquisição — nunca inventados.
- Papéis DICOMweb não se trocam: QIDO=buscar, WADO=recuperar, STOW=armazenar.
- Nenhum pixel data no banco do Quarkus — o cofre é o DCM4CHEE.
- Token do usuário propagado/trocado ao chamar o archive (nunca credencial de serviço).

## MVP — 4 fluxos ponta-a-ponta (inalterado)

1. Ingestão — upload `.dcm` → `POST /api/studies` → STOW-RS.
2. Worklist + busca — `GET /api/studies` → QIDO-RS paginado.
3. Viewer — Cornerstone3D via `/api/dicomweb/...` (WADO-RS proxied).
4. Laudos + auth — laudo por `StudyInstanceUID`; sessão BFF/cookie.

## Pontos que a implementação deve resolver (gate humano)

- **Audience/token exchange** entre Quarkus e DCM4CHEE (seção acima) — decisão de
  configuração Keycloak, apresentada no gate.
- Ajuste de RESTEasy para `multipart/related` no STOW passthrough.
- Estratégia de streaming do WADO (backpressure/tamanho de buffer).
- Postgres do produto: schema e migrações (Flyway) — fora do escopo desta fase.

## Fora de escopo (YAGNI para o MVP)

- Hardening completo de LGPD/produção (entra como "notas de produção").
- Cache/CDN de imagens, pré-busca de séries.
- Multi-tenant / issuer de PatientID.
