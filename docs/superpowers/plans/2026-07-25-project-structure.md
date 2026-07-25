# BlackICE Project Structure Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> `superpowers:subagent-driven-development` (recommended) or
> `superpowers:executing-plans` to implement this plan task-by-task. Steps use
> checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reorganizar o BlackICE em um monorepo profissional com aplicações sob
`apps/`, código feature-first, infraestrutura previsível e documentação
replicável por humanos e agentes.

**Architecture:** `apps/backend` e `apps/frontend` contêm os executáveis;
`infra/` e `docs/` permanecem como áreas próprias. Backend e frontend agrupam
código por feature, começam planos e só extraem compartilhamentos após reuso
real.

**Tech Stack:** Java 21, Quarkus 3.37.4, Maven, Vue 3, TypeScript 6, Vite 8,
Vitest 4, Docker Compose, Traefik e DCM4CHEE 5.34.3.

## Global Constraints

- Implementar o design aprovado em
  `docs/superpowers/specs/2026-07-25-project-structure-design.md`.
- Não alterar rotas HTTP, payloads, fluxo OIDC/BFF, serviços, redes ou volumes.
- Não criar antecipadamente features vazias nem diretórios `shared/`.
- Preservar `StudyInstanceUID` e todas as invariantes de
  `docs/domains/dicom/`; esta mudança não altera semântica DICOM.
- Não versionar `.env`, certificados, tokens ou outros segredos.
- Não reescrever specs e planos históricos; documentar como interpretar paths
  antigos.
- Não criar commit sem autorização explícita do usuário. Os gates abaixo
  substituem os passos de commit até essa autorização existir.

---

### Task 1: Agrupar as aplicações em `apps/`

**Files:**

- Move: `backend/` → `apps/backend/`
- Move: `frontend/` → `apps/frontend/`
- Modify: `.gitignore`
- Modify: `infra/docker-compose.app.yml`

**Interfaces:**

- Preserva: projeto Maven, projeto npm, artefatos `target/` e `dist/`
- Produz: caminhos canônicos `apps/backend` e `apps/frontend`
- Produz: contextos Docker temporariamente válidos antes da renomeação dos
  arquivos Compose na Task 4

- [ ] **Step 1: Confirmar baseline limpa e registrar os testes atuais**

Run:

```powershell
git status --short
Set-Location backend
mise exec -- mvn -B test
Set-Location ../frontend
mise exec -- npm test
mise exec -- npm run build
Set-Location ..
```

Expected: Maven, Vitest e Vite terminam com sucesso. No status, somente esta spec
e este plano podem aparecer como alterações já conhecidas; se houver outras
mudanças, preservá-las e parar para resolver qualquer sobreposição.

- [ ] **Step 2: Validar os alvos antes da movimentação**

Run:

```powershell
$repo = (Resolve-Path .).Path
$backendSource = (Resolve-Path backend).Path
$frontendSource = (Resolve-Path frontend).Path
if ($backendSource -notlike "$repo\backend") { throw "backend inesperado" }
if ($frontendSource -notlike "$repo\frontend") { throw "frontend inesperado" }
if (Test-Path apps/backend) { throw "apps/backend já existe" }
if (Test-Path apps/frontend) { throw "apps/frontend já existe" }
```

Expected: nenhuma exceção.

- [ ] **Step 3: Mover as duas aplicações**

Run:

```powershell
New-Item -ItemType Directory -Path apps
Move-Item -LiteralPath backend -Destination apps/backend
Move-Item -LiteralPath frontend -Destination apps/frontend
```

Expected: `apps/backend/pom.xml` e `apps/frontend/package.json` existem; não há
mais `backend/` ou `frontend/` na raiz.

- [ ] **Step 4: Atualizar ignores da raiz**

Substituir em `.gitignore`:

```gitignore
apps/frontend/node_modules/
apps/frontend/dist/
apps/backend/target/
```

Manter as regras de `infra/.env`, `.superpowers/` e editor.

- [ ] **Step 5: Atualizar contextos Docker ainda no Compose existente**

Em `infra/docker-compose.app.yml`, usar:

```yaml
services:
  backend:
    build:
      context: ../apps/backend
      dockerfile: src/main/docker/Dockerfile.jvm
  frontend:
    build: ../apps/frontend
```

Não alterar labels, environment, `depends_on`, networks ou nomes dos serviços.

- [ ] **Step 6: Validar os novos caminhos**

Run:

```powershell
Set-Location apps/backend
mise exec -- mvn -B test
Set-Location ../frontend
mise exec -- npm test
mise exec -- npm run build
Set-Location ../..
docker compose -f infra/docker-compose.yml -f infra/dcm4chee/docker-compose.dcm4chee.yml -f infra/docker-compose.app.yml config --quiet
git diff --check
```

Expected: todos os comandos passam. Gate humano: apresentar a movimentação e os
resultados antes da Task 2.

---

### Task 2: Migrar o backend para feature packages

**Files:**

- Move/rename:
  `apps/backend/src/main/java/dev/blackice/api/MeResource.java` →
  `apps/backend/src/main/java/dev/blackice/features/session/SessionResource.java`
- Move:
  `apps/backend/src/main/java/dev/blackice/api/SessionResponse.java` →
  `apps/backend/src/main/java/dev/blackice/features/session/SessionResponse.java`
- Move/rename:
  `apps/backend/src/test/java/dev/blackice/api/MeResourceTest.java` →
  `apps/backend/src/test/java/dev/blackice/features/session/SessionResourceTest.java`

**Interfaces:**

- Preserva: `GET /api/me`
- Preserva: `GET /api/login`
- Preserva: JSON `{ subject, username, roles }`
- Produz: pacote público `dev.blackice.features.session`

- [ ] **Step 1: Mover o teste para o pacote de destino**

O arquivo final deve começar com:

```java
package dev.blackice.features.session;

@QuarkusTest
class SessionResourceTest {
```

Preservar o caso `anonimo_recebe_401()` sem alterar sua asserção.

- [ ] **Step 2: Executar o teste como caracterização antes da migração**

Run:

```powershell
Set-Location apps/backend
mise exec -- mvn -B test -Dtest=SessionResourceTest
Set-Location ../..
```

Expected: passa chamando `/api/me`; o teste é HTTP e deliberadamente não depende
do nome ou pacote Java da classe de produção.

- [ ] **Step 3: Mover e renomear as classes de produção**

`SessionResource.java` deve declarar:

```java
package dev.blackice.features.session;

@Path("/api")
public class SessionResource {
```

`SessionResponse.java` deve declarar:

```java
package dev.blackice.features.session;

public record SessionResponse(
        String subject,
        String username,
        List<String> roles) {}
```

Preservar o corpo de `me()` e `login()`; não alterar autenticação ou claims.

- [ ] **Step 4: Verificar ausência do pacote técnico antigo**

Run:

```powershell
rg "dev\.blackice\.api|class MeResource|MeResourceTest" apps/backend
```

Expected: nenhuma ocorrência.

- [ ] **Step 5: Executar testes e empacotamento**

Run:

```powershell
Set-Location apps/backend
mise exec -- mvn -B test
mise exec -- mvn -B -DskipTests package
Set-Location ../..
```

Expected: `BUILD SUCCESS` nos dois comandos. Gate humano: confirmar que as rotas
e o payload não mudaram antes da Task 3.

---

### Task 3: Migrar o frontend para features e configurar `@/`

**Files:**

- Move: `apps/frontend/src/App.vue` →
  `apps/frontend/src/app/App.vue`
- Move: `apps/frontend/src/router/index.ts` →
  `apps/frontend/src/app/router/index.ts`
- Move/rename: `apps/frontend/src/views/HomeView.vue` →
  `apps/frontend/src/features/home/HomePage.vue`
- Move/rename: `apps/frontend/src/lib/session.ts` →
  `apps/frontend/src/features/session/session.api.ts`
- Move/rename: `apps/frontend/src/lib/session.spec.ts` →
  `apps/frontend/src/features/session/session.api.spec.ts`
- Create: `apps/frontend/src/features/session/session.types.ts`
- Modify: `apps/frontend/src/main.ts`
- Modify: `apps/frontend/vite.config.ts`
- Modify: `apps/frontend/tsconfig.app.json`

**Interfaces:**

- Preserva: `fetchSession(): Promise<SessionResponse | null>`
- Preserva: guarda de rota e navegação para `/api/login`
- Produz: alias `@/*` → `src/*`
- Produz: features `session` e `home`

- [ ] **Step 1: Extrair o tipo de sessão**

Criar `session.types.ts`:

```ts
export interface SessionResponse {
  subject: string
  username: string
  roles: string[]
}
```

Em `session.api.ts`, importar com:

```ts
import type { SessionResponse } from '@/features/session/session.types'
```

Manter a implementação atual de `fetchSession`.

- [ ] **Step 2: Mover e atualizar o teste colocalizado**

`session.api.spec.ts` deve importar:

```ts
import { fetchSession } from '@/features/session/session.api'
```

Preservar os casos de `401` e `200`.

- [ ] **Step 3: Executar o teste antes do alias**

Run:

```powershell
Set-Location apps/frontend
mise exec -- npm test
Set-Location ../..
```

Expected: falha de resolução de `@/`, comprovando que a configuração seguinte é
necessária.

- [ ] **Step 4: Configurar alias no TypeScript e Vite**

Adicionar a `compilerOptions` de `tsconfig.app.json`:

```json
"baseUrl": ".",
"paths": {
  "@/*": ["./src/*"]
}
```

Em `vite.config.ts`, adicionar:

```ts
import { fileURLToPath, URL } from 'node:url'

resolve: {
  alias: {
    '@': fileURLToPath(new URL('./src', import.meta.url)),
  },
},
```

Preservar integralmente o proxy de `/api`.

- [ ] **Step 5: Mover shell, router e página**

Usar estes imports finais:

```ts
// src/main.ts
import App from '@/app/App.vue'
import router from '@/app/router'

// src/app/router/index.ts
import HomePage from '@/features/home/HomePage.vue'
import { fetchSession } from '@/features/session/session.api'

// src/features/home/HomePage.vue
import { fetchSession } from '@/features/session/session.api'
```

Renomear apenas os símbolos `HomeView` → `HomePage`; preservar template, guarda e
comportamento de sessão.

- [ ] **Step 6: Confirmar que estruturas genéricas antigas desapareceram**

Run:

```powershell
rg "src/(lib|views|router)|\.\./(?:lib|views|router)|HomeView" apps/frontend
```

Expected: nenhuma referência de código ativa.

- [ ] **Step 7: Executar teste e build**

Run:

```powershell
Set-Location apps/frontend
mise exec -- npm test
mise exec -- npm run build
Set-Location ../..
```

Expected: Vitest passa os dois casos e `vue-tsc`/Vite terminam com sucesso. Gate
humano: apresentar a árvore `src/` antes da Task 4.

---

### Task 4: Normalizar a estrutura de infraestrutura

**Files:**

- Move/rename: `infra/docker-compose.yml` → `infra/compose.yml`
- Move/rename: `infra/docker-compose.app.yml` → `infra/compose.apps.yml`
- Move/rename:
  `infra/dcm4chee/docker-compose.dcm4chee.yml` →
  `infra/dcm4chee/compose.yml`
- Move:
  `infra/docker-proxy/nginx.conf` →
  `infra/traefik/docker-api-proxy/nginx.conf`
- Modify: `infra/compose.yml`
- Modify: `infra/dcm4chee/README.md`
- Create: `infra/README.md`

**Interfaces:**

- Preserva: serviços `docker-proxy`, `traefik`, `product-db`, `backend`,
  `frontend`, `ldap`, `mariadb`, `keycloak`, `arc-db` e `arc`
- Preserva: rede `blackice` e todos os nomes de volumes
- Produz: comando Compose canônico com três arquivos

- [ ] **Step 1: Mover os arquivos e o proxy**

Criar somente os diretórios necessários e remover os diretórios antigos quando
ficarem vazios. Não alterar `infra/.env`, `infra/.env.example` ou
`infra/keycloak/`.

- [ ] **Step 2: Atualizar o mount do proxy**

Em `infra/compose.yml`, usar:

```yaml
volumes:
  - "./traefik/docker-api-proxy/nginx.conf:/etc/nginx/nginx.conf:ro"
```

Preservar o mount do socket, entrypoint, labels e opções do Traefik.

- [ ] **Step 3: Criar o README canônico de infraestrutura**

`infra/README.md` deve documentar:

```powershell
cd infra
docker compose -f compose.yml -f dcm4chee/compose.yml -f compose.apps.yml up -d --build
```

Também deve listar:

- `compose.yml`: fundação compartilhada;
- `dcm4chee/compose.yml`: archive seguro e dependências;
- `compose.apps.yml`: aplicações BlackICE;
- `.env.example` como modelo, nunca `.env` versionado;
- comando equivalente de `config --quiet` para validação.

- [ ] **Step 4: Atualizar o README do DCM4CHEE**

Trocar apenas comandos e referências de paths:

```powershell
docker compose -f infra/compose.yml -f infra/dcm4chee/compose.yml -f infra/compose.apps.yml ...
```

Preservar tags pinadas, decisões de segurança, portas e explicações de volumes.

- [ ] **Step 5: Validar a configuração combinada**

Run:

```powershell
docker compose -f infra/compose.yml -f infra/dcm4chee/compose.yml -f infra/compose.apps.yml config --quiet
docker compose -f infra/compose.yml -f infra/dcm4chee/compose.yml -f infra/compose.apps.yml config --services
```

Expected: primeiro comando sem saída/erro; segundo lista todos os serviços
preservados.

- [ ] **Step 6: Comparar redes e volumes com a baseline**

Run:

```powershell
docker compose -f infra/compose.yml -f infra/dcm4chee/compose.yml -f infra/compose.apps.yml config
```

Expected: rede `blackice`; volumes `product-db-data`, `docker-proxy-sock` e todos
os volumes `dcm4chee-*` mantidos. Gate humano antes da documentação final.

---

### Task 5: Tornar a estrutura replicável por agentes

**Files:**

- Create: `README.md`
- Create: `docs/architecture/project-structure.md`
- Create: `docs/superpowers/README.md`
- Modify: `AGENTS.md`
- Modify: `docs/domains/quarkus/conventions.md`
- Modify: `docs/domains/quarkus/README.md`
- Modify: `docs/domains/vue/conventions.md`
- Modify: `docs/domains/vue/README.md`
- Replace: `apps/backend/README.md`
- Replace: `apps/frontend/README.md`
- Modify path references in active infrastructure documentation as found by
  the verification scan

**Interfaces:**

- Produz: `docs/architecture/project-structure.md` como fonte canônica
- Produz: receitas determinísticas para novas features Quarkus e Vue
- Preserva: Domain Packs como fonte única de conhecimento de domínio

- [ ] **Step 1: Escrever a fonte canônica da estrutura**

`docs/architecture/project-structure.md` deve conter:

1. árvore atual do monorepo;
2. responsabilidade de `apps/`, `infra/`, `docs/`, `.claude/` e `.codex/`;
3. árvore exata das features `session` e `home`;
4. regras de dependência do design aprovado;
5. receita “Adicionar feature Quarkus”:
   criar `features/<name>`, espelhar teste em `src/test`, manter rotas/DTOs na
   feature e não usar pacote técnico global;
6. receita “Adicionar feature Vue”:
   criar `features/<name>`, colocalizar API/tipos/componentes/testes, registrar
   página no router de `app/` e usar `@/`;
7. regra de promoção a `shared/`: ao menos dois consumidores reais;
8. exemplos do que não fazer: features vazias, `utils/` genérico e
   `controller/service/repository` globais.

- [ ] **Step 2: Vincular a regra em `AGENTS.md`**

Adicionar uma seção curta “Estrutura do repositório” que:

- declara `docs/architecture/project-structure.md` como fonte canônica;
- exige sua leitura antes de criar ou mover código;
- resume `apps/backend`, `apps/frontend`, `infra` e `docs`;
- proíbe novas pastas raiz de aplicação e camadas técnicas globais.

Não duplicar as receitas completas no `AGENTS.md`.

- [ ] **Step 3: Corrigir convenções Quarkus**

Adicionar feature-first e o link canônico a
`docs/domains/quarkus/conventions.md`. Em
`docs/domains/quarkus/README.md`, substituir a justificativa incorreta “o
projeto real usa Django” por “project-scoped porque descreve decisões específicas
do BlackICE/Quarkus”.

- [ ] **Step 4: Corrigir convenções Vue e autenticação**

Substituir a árvore técnica sugerida por `app/` + `features/` e registrar testes
colocalizados e alias `@/`.

Substituir a orientação antiga:

```text
Chamadas ao backend passam o Bearer token OIDC.
```

pela regra:

```text
O frontend usa a sessão BFF via cookie HttpOnly. Nenhum access token vive no
JavaScript; chamadas usam mesma origem e `/api/me` é a fonte da sessão.
```

Em `docs/domains/vue/README.md`, esclarecer que o viewer consome uma interface
DICOMweb compatível e, no BlackICE, usa o caminho WADO estreito proxied pelo
Quarkus.

- [ ] **Step 5: Criar documentação de histórico**

`docs/superpowers/README.md` deve explicar que specs e planos são registros
históricos, não instruções operacionais atuais. Paths `backend/` e `frontend/`
presentes em documentos anteriores a 2026-07-25 correspondem a
`apps/backend/` e `apps/frontend/`; novos trabalhos usam a fonte canônica.

Não modificar em massa specs e planos concluídos.

- [ ] **Step 6: Substituir READMEs genéricos**

O `README.md` raiz deve conter visão do produto, mapa do monorepo, pré-requisitos,
comandos de teste/build por aplicação, comando Compose canônico e links para
arquitetura, estrutura e Domain Packs.

`apps/backend/README.md` deve documentar Java 21/mise, `mvn test`,
`quarkus:dev`, feature packages e configuração OIDC por variáveis.

`apps/frontend/README.md` deve documentar Node 24/mise, `npm test`,
`npm run build`, `npm run dev`, features e BFF sem token no browser.

- [ ] **Step 7: Validar documentação**

Run:

```powershell
rg "Bearer token|backend/|frontend/|docker-compose\.app|docker-compose\.dcm4chee|docker-compose\.yml" AGENTS.md README.md apps infra docs/domains docs/architecture
```

Expected:

- nenhuma orientação ativa de Bearer token no frontend;
- paths antigos apenas quando explicados como históricos;
- nenhum nome antigo de Compose em documentação operacional;
- referências textuais a “backend” e “frontend” como conceitos continuam
  permitidas.

Gate humano: revisar `docs/architecture/project-structure.md` como se fosse um
agente sem contexto e confirmar que as duas receitas são reproduzíveis.

---

### Task 6: Verificação integrada e entrega

**Files:**

- Verify only: toda a árvore migrada

**Interfaces:**

- Confirma: contratos e fluxo BFF preservados
- Confirma: aplicações e Compose funcionam nos paths finais

- [ ] **Step 1: Verificar estado e higiene do diff**

Run:

```powershell
git status --short
git diff --check
git diff --stat
git diff --summary
```

Expected: somente a reorganização aprovada; sem whitespace errors; Git detecta
movimentações quando o conteúdo é suficientemente semelhante.

- [ ] **Step 2: Executar a suíte do backend**

Run:

```powershell
Set-Location apps/backend
mise exec -- mvn -B test
mise exec -- mvn -B -DskipTests package
Set-Location ../..
```

Expected: dois `BUILD SUCCESS`.

- [ ] **Step 3: Executar a suíte do frontend**

Run:

```powershell
Set-Location apps/frontend
mise exec -- npm test
mise exec -- npm run build
Set-Location ../..
```

Expected: testes Vitest e build `vue-tsc`/Vite passam.

- [ ] **Step 4: Validar e construir as imagens das aplicações**

Run:

```powershell
docker compose -f infra/compose.yml -f infra/dcm4chee/compose.yml -f infra/compose.apps.yml config --quiet
docker compose -f infra/compose.yml -f infra/dcm4chee/compose.yml -f infra/compose.apps.yml build backend frontend
```

Expected: configuração válida e duas imagens construídas pelos novos contextos.

- [ ] **Step 5: Executar smoke test quando a stack local estiver disponível**

Run:

```powershell
docker compose -f infra/compose.yml -f infra/dcm4chee/compose.yml -f infra/compose.apps.yml up -d backend frontend
curl.exe -s -o NUL -w "%{http_code}" http://localhost/api/me
curl.exe -s -o NUL -w "%{http_code}" http://localhost/api/login
```

Expected: `/api/me` retorna `401`; `/api/login` retorna redirect OIDC (`302` ou
`303`, conforme a resposta existente) sem erro `5xx`. Não derrubar nem remover
volumes como parte desta verificação.

- [ ] **Step 6: Fazer a varredura final de paths antigos**

Run:

```powershell
rg "(\.\./)?backend/|(\.\./)?frontend/|docker-compose\.app|docker-compose\.dcm4chee|infra/docker-compose\.yml" --glob "!.git/**" --glob "!.superpowers/**"
```

Expected: ocorrências somente em `docs/superpowers/` histórico e na explicação
de compatibilidade de `docs/superpowers/README.md`. Nenhuma ocorrência em
configuração, código, `AGENTS.md`, READMEs operacionais ou Domain Packs.

- [ ] **Step 7: Gate final**

Apresentar ao usuário:

- árvore final resumida;
- arquivos de governança criados;
- resultados exatos dos testes, builds, Compose e smoke test;
- qualquer verificação não executada e o motivo;
- confirmação de que nenhum commit foi criado.

Não stagear nem commitar até receber autorização explícita.
