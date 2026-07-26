# Tema de login BlackICE (Keycloak) — Plano de Implementação

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Substituir a marca `j4care` na tela de login do client `blackice-quarkus` por uma identidade própria do BlackICE, sem tocar no login do DCM4CHEE Archive e sem copiar templates FreeMarker.

**Architecture:** Um tema filho de `keycloak.v2` vive em `infra/keycloak/themes/blackice/`, é bind-montado no container do Keycloak e aplicado via atributo `login_theme` só no client `blackice-quarkus`. Todo o conteúdo textual (wordmark, assinatura, rótulos em PT-BR) sai de um message bundle; toda a aparência sai de uma folha de estilo empilhada sobre a do tema pai. Nenhum arquivo `.ftl` é copiado.

**Tech Stack:** Keycloak 25.0.6 (`dcm4che/keycloak:25.0.6`), tema PatternFly v5 (`keycloak.v2`), Docker Compose, Admin REST via `curl -k` + `python3` dentro do container.

**Spec:** `docs/superpowers/specs/2026-07-26-keycloak-login-theme-design.md`

## Global Constraints

- **Nome do tema:** `blackice`. Diretório: `infra/keycloak/themes/blackice/login/`.
- **`theme.properties` tem exatamente duas linhas:** `parent=keycloak.v2` e `styles=css/styles.css css/blackice.css`.
- **Nunca criar um arquivo chamado `resources/css/styles.css` neste tema.** Esse nome resolve pela cadeia de pais até o `keycloak.v2`; criá-lo sombreia o pai e perde os ajustes dele. É o bug que o `j4care` tem.
- **Escopo é por client, nunca por realm.** Só `blackice-quarkus` recebe `login_theme`. O realm `dcm4chee` mantém `loginTheme: j4care` para o Archive.
- **Nenhum webfont, nenhum recurso externo.** Só `system-ui` e `ui-monospace`.
- **Tokens (valores exatos):** `--bi-bg:#06080b` · `--bi-surface:#0d1117` · `--bi-field:#080b0f` · `--bi-border:#1a222e` · `--bi-border-in:#1e2735` · `--bi-text:#e6edf5` · `--bi-text-dim:#8b98a9` · `--bi-text-mute:#4e5a6b` · `--bi-accent:#56c8e8` · `--bi-accent-ink:#04161e` · `--bi-radius:6px` · `--bi-radius-lg:10px` · `--bi-space-1:6px` · `--bi-space-2:18px` · `--bi-space-3:32px`.
- **O acento aparece em exatamente três lugares:** metade do wordmark, a régua sob ele, e a borda do campo em foco. Nada mais.
- **Fundo nunca é `#000`.**
- **Ambiente:** todos os comandos assumem a stack de pé (`infra-keycloak-1` rodando). No Git Bash, exportar `MSYS_NO_PATHCONV=1` antes de qualquer `docker exec` com caminho absoluto, senão o Git Bash converte `/opt/...` em `C:/Program Files/Git/opt/...`.
- **Commits:** seguir a convenção do repositório — gitmoji + descrição curta em português, sem trailer de co-autor.

---

## Estrutura de arquivos

| Arquivo | Responsabilidade |
| :-- | :-- |
| `infra/keycloak/themes/blackice/login/theme.properties` | Declara o pai e a pilha de folhas de estilo. Duas linhas, nunca mais. |
| `infra/keycloak/themes/blackice/login/messages/messages_en.properties` | Todo o **texto**: wordmark, assinatura, rótulos em PT-BR. |
| `infra/keycloak/themes/blackice/login/resources/css/blackice.css` | Toda a **aparência**: tokens + estilização dos seletores PatternFly. |
| `infra/dcm4chee/compose.yml` (modificar) | Bind-mount do tema + env dev-only de cache. |
| `infra/keycloak/configure-blackice.sh` (modificar) | Aplica `login_theme=blackice` no client, idempotente. |
| `infra/keycloak/README.md` (modificar) | Documenta o tema, o escopo por client e as armadilhas. |

Texto e aparência ficam separados de propósito: mudar uma palavra não deve exigir tocar em CSS, e vice-versa.

---

### Task 1: Fiação do tema (esqueleto + mount + escopo)

Prova que o caminho inteiro funciona — arquivo no disco → mount → tema descoberto → aplicado só ao client — **antes** de qualquer design. O CSS aqui é uma sentinela deliberadamente feia; ela some na Task 3.

**Files:**
- Create: `infra/keycloak/themes/blackice/login/theme.properties`
- Create: `infra/keycloak/themes/blackice/login/resources/css/blackice.css`
- Modify: `infra/dcm4chee/compose.yml` (serviço `keycloak`)
- Modify: `infra/keycloak/configure-blackice.sh` (nova seção 4, antes do `echo` final)

**Interfaces:**
- Consumes: nada.
- Produces: tema `blackice` descoberto pelo Keycloak e ativo no client `blackice-quarkus`. As Tasks 2 e 3 escrevem dentro de `infra/keycloak/themes/blackice/login/`.

- [ ] **Step 1: Escrever a verificação que deve falhar**

Cria `/tmp/check-theme.sh` no host (arquivo descartável, não versionado):

```bash
cat > /tmp/check-theme.sh <<'EOF'
#!/usr/bin/env bash
# Busca a página de login do client blackice-quarkus e imprime os CSS servidos.
# PKCE é obrigatório: sem code_challenge_method o Keycloak devolve 302 de erro.
export MSYS_NO_PATHCONV=1
docker exec infra-keycloak-1 sh -c '
CC=E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM
curl -k -s "https://localhost:8843/realms/dcm4chee/protocol/openid-connect/auth?response_type=code&client_id=blackice-quarkus&scope=openid&redirect_uri=http%3A%2F%2Fblackice.localhost%2Fapi%2Flogin&state=x&nonce=y&code_challenge=$CC&code_challenge_method=S256"
' > /tmp/login.html
echo "--- CSS servidos ---"
grep -o 'href="[^"]*\.css"' /tmp/login.html
echo "--- header ---"
sed -n '/kc-header-wrapper/,+1p' /tmp/login.html
EOF
chmod +x /tmp/check-theme.sh
```

- [ ] **Step 2: Rodar e confirmar que falha**

Run: `bash /tmp/check-theme.sh`

Expected: os CSS listados apontam para `.../login/j4care/css/styles.css` e **não existe** nenhum `.../login/blackice/css/blackice.css`. O header mostra `dcm4chee`.

- [ ] **Step 3: Criar o `theme.properties`**

Create `infra/keycloak/themes/blackice/login/theme.properties`:

```properties
parent=keycloak.v2
styles=css/styles.css css/blackice.css
```

`css/styles.css` NÃO é arquivo deste tema — resolve pela cadeia de pais até o `keycloak.v2`. `styles=` substitui a lista do pai em vez de concatenar, por isso o arquivo do pai é re-listado aqui explicitamente.

- [ ] **Step 4: Criar o CSS sentinela**

Create `infra/keycloak/themes/blackice/login/resources/css/blackice.css`:

```css
/* SENTINELA — prova que a folha do tema está sendo servida e empilhada.
   Substituída inteiramente na Task 3. */
#kc-header-wrapper {
  outline: 3px dashed magenta;
}
```

- [ ] **Step 5: Montar o tema e desligar o cache no compose**

Modify `infra/dcm4chee/compose.yml`, serviço `keycloak`. Adicionar ao bloco `environment:` existente (junto das outras vars, mantendo a indentação de 6 espaços):

```yaml
      # DEV-ONLY: a imagem roda `kc.sh start` (modo produção), que cacheia temas
      # e templates. Sem estas três, editar theme.properties ou CSS não surte
      # efeito até um restart do container (~21s). Num deploy real, remover as
      # três e assar o tema dentro da imagem em vez de bind-montar.
      KC_SPI_THEME_CACHE_THEMES: 'false'
      KC_SPI_THEME_CACHE_TEMPLATES: 'false'
      KC_SPI_THEME_STATIC_MAX_AGE: '-1'
```

E substituir o bloco `volumes:` do serviço `keycloak` por:

```yaml
    volumes:
      - dcm4chee-keycloak-data:/opt/keycloak/data
      # Tema de login do BlackICE. Read-only: o Keycloak não escreve aqui.
      # O caminho é relativo ao diretório de trabalho do `docker compose`
      # (infra/), não ao diretório deste arquivo — Compose resolve binds
      # relativos ao cwd/--project-directory, não por-arquivo.
      - "./keycloak/themes/blackice:/opt/keycloak/themes/blackice:ro"
```

- [ ] **Step 6: Aplicar o tema só ao client, no script de configuração**

Modify `infra/keycloak/configure-blackice.sh`. Inserir esta seção entre a seção 3 (usuário `dr.teste`) e o `echo "OK: ..."` final, dentro do heredoc `INNER`:

```sh
# 4) tema de login SÓ neste client (o realm segue em j4care, para o Archive).
#    Não há endpoint de patch parcial: é preciso GET da representação inteira,
#    mexer no atributo e PUT de volta. jq não existe nesta imagem; python3 sim.
#    Para REMOVER o atributo é preciso mandar "" — omitir a chave devolve 204
#    e não apaga nada.
CUR=$(curl -k -s -H "$AH" "$api/clients/$CID" | python3 -c 'import sys,json;print(json.load(sys.stdin).get("attributes",{}).get("login_theme",""))')
if [ "$CUR" != "blackice" ]; then
  curl -k -s -H "$AH" "$api/clients/$CID" > /tmp/client.json
  python3 -c '
import json
c = json.load(open("/tmp/client.json"))
c.setdefault("attributes", {})["login_theme"] = "blackice"
json.dump(c, open("/tmp/client.json", "w"))
'
  curl -k -s -H "$AH" -H "Content-Type: application/json" -X PUT "$api/clients/$CID" -d @/tmp/client.json >/dev/null
  rm -f /tmp/client.json
  echo "login_theme=blackice no client blackice-quarkus: aplicado"
else echo "login_theme=blackice: já aplicado"; fi
```

- [ ] **Step 7: Recriar o Keycloak e aplicar a configuração**

O mount e as env novas exigem **recriar** o container, não apenas reiniciar. Rodar a partir da raiz do repositório:

```bash
export MSYS_NO_PATHCONV=1
ROOT=$(git rev-parse --show-toplevel)
(cd "$ROOT/infra" && docker compose -f compose.yml -f dcm4chee/compose.yml -f compose.apps.yml up -d keycloak)
for i in $(seq 1 40); do sleep 3; docker exec infra-keycloak-1 sh -c 'curl -k -s -o /dev/null -w "%{http_code}" https://localhost:8843/realms/dcm4chee/.well-known/openid-configuration' | grep -q 200 && { echo "up"; break; }; done
bash "$ROOT/infra/keycloak/configure-blackice.sh"
```

- [ ] **Step 8: Rodar a verificação e confirmar que passa**

Run: `bash /tmp/check-theme.sh`

Expected — as três linhas de CSS, nesta ordem:
- `.../common/keycloak/node_modules/@patternfly-v5/patternfly/patternfly.min.css`
- `.../login/blackice/css/styles.css` ← herdado do pai, prova que empilhou
- `.../login/blackice/css/blackice.css` ← nosso, prova que o mount pegou

Se `blackice.css` aparecer mas `styles.css` não, o `styles=` do `theme.properties` está errado. Se nenhum dos dois aparecer, o `login_theme` não foi aplicado — conferir a saída do `configure-blackice.sh`.

- [ ] **Step 9: Confirmar que o Archive não foi afetado**

Run:

```bash
export MSYS_NO_PATHCONV=1
docker exec infra-keycloak-1 sh -c '
adm=$(curl -k -s -d "client_id=admin-cli" -d "username=$KEYCLOAK_ADMIN" -d "password=$KEYCLOAK_ADMIN_PASSWORD" -d "grant_type=password" https://localhost:8843/realms/master/protocol/openid-connect/token | sed -n "s/.*\"access_token\":\"\([^\"]*\)\".*/\1/p")
curl -k -s -H "Authorization: Bearer $adm" https://localhost:8843/admin/realms/dcm4chee | python3 -c "import sys,json;print(\"realm loginTheme =\", json.load(sys.stdin).get(\"loginTheme\"))"
'
```

Expected: `realm loginTheme = j4care`.

- [ ] **Step 10: Commit**

```bash
git add infra/keycloak/themes/blackice infra/dcm4chee/compose.yml infra/keycloak/configure-blackice.sh
git commit -m "🎨 aplica tema de login próprio ao client do bff"
```

---

### Task 2: Texto — wordmark, assinatura e rótulos em PT-BR

Todo o conteúdo textual da tela num arquivo só. Nenhum CSS aqui.

**Files:**
- Create: `infra/keycloak/themes/blackice/login/messages/messages_en.properties`

**Interfaces:**
- Consumes: o tema `blackice` ativo no client (Task 1).
- Produces: as classes `bi-wm`, `bi-wm-ice` e `bi-exp` no HTML de `#kc-header-wrapper`. A Task 3 estiliza exatamente esses três nomes.

- [ ] **Step 1: Escrever a verificação que deve falhar**

```bash
cat > /tmp/check-text.sh <<'EOF'
#!/usr/bin/env bash
export MSYS_NO_PATHCONV=1
docker exec infra-keycloak-1 sh -c '
CC=E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM
curl -k -s "https://localhost:8843/realms/dcm4chee/protocol/openid-connect/auth?response_type=code&client_id=blackice-quarkus&scope=openid&redirect_uri=http%3A%2F%2Fblackice.localhost%2Fapi%2Flogin&state=x&nonce=y&code_challenge=$CC&code_challenge_method=S256"
' > /tmp/login.html
echo "--- wordmark ---"
grep -o 'class="bi-wm".*bi-exp[^<]*<[^>]*>[^<]*' /tmp/login.html || echo "(ausente)"
echo "--- rótulos ---"
grep -o '<span class="pf-v5-c-form__label-text">[^<]*' /tmp/login.html
grep -o 'id="kc-login"[^>]*value="[^"]*"' /tmp/login.html
EOF
chmod +x /tmp/check-text.sh
```

- [ ] **Step 2: Rodar e confirmar que falha**

Run: `bash /tmp/check-text.sh`

Expected: wordmark `(ausente)`; rótulos em inglês (`Username or email`, `Password`) e o botão com `value="Sign In"`.

- [ ] **Step 3: Escrever o message bundle**

Create `infra/keycloak/themes/blackice/login/messages/messages_en.properties`:

```properties
# Conteúdo em PT-BR num arquivo chamado _en de propósito.
# O realm dcm4chee tem internationalizationEnabled=false e supportedLocales=[],
# então `en` é o único bundle que o Keycloak consulta. Ligar i18n de verdade é
# setting de REALM e adicionaria seletor de idioma também no login do Archive,
# que este trabalho não pode tocar. Se um dia o realm ganhar i18n, este conteúdo
# migra para messages_pt_BR.properties sem mais nada mudar.
#
# loginTitleHtml passa por kcSanitize (que preserva <span> e class, verificado
# nesta build) e por MessageFormat — apóstrofo aqui precisa ser dobrado ('').
# O `=` dentro dos atributos vai escapado como \= : foi a forma testada.
loginTitleHtml=<span class\="bi-wm">BLACK<span class\="bi-wm-ice">ICE</span></span><span class\="bi-exp">Intrusion Countermeasures Electronics</span>

loginAccountTitle=Acesso ao sistema
usernameOrEmail=Usuário
username=Usuário
password=Senha
doLogIn=Entrar
```

- [ ] **Step 4: Rodar a verificação e confirmar que passa**

Run: `bash /tmp/check-text.sh`

Expected:
- o wordmark presente, com `class="bi-wm"`, `class="bi-wm-ice"` e `class="bi-exp"` **preservados** no HTML;
- rótulos `Usuário` e `Senha`;
- botão com `value="Entrar"`.

Se as classes vierem removidas mas as tags sobreviverem, o `kcSanitize` desta build está mais estrito que o testado: usar o fallback do spec — estilizar via `#kc-header-wrapper span` na Task 3, sem depender de atributo.

- [ ] **Step 5: Conferir o markup renderizado contra o pretendido**

Run:

```bash
sed -n '/kc-header-wrapper/,+1p' /tmp/login.html
```

Comparar caractere a caractere com a linha `loginTitleHtml` do bundle. É um diff visual, não "parece certo": um `<span>` a menos aqui quebra a Task 3 inteira.

- [ ] **Step 6: Commit**

```bash
git add infra/keycloak/themes/blackice/login/messages/messages_en.properties
git commit -m "🌐 traduz a tela de login e injeta o wordmark do blackice"
```

---

### Task 3: Aparência — tokens e folha de estilo

Substitui a sentinela pelo design real.

**Files:**
- Modify: `infra/keycloak/themes/blackice/login/resources/css/blackice.css` (substituir o conteúdo inteiro)

**Interfaces:**
- Consumes: as classes `bi-wm`, `bi-wm-ice`, `bi-exp` (Task 2) e a pilha de folhas de estilo (Task 1).
- Produces: as custom properties `--bi-*` no `:root`, que o spec do SPA vai importar depois.

**Seletores reais desta build** (extraídos do HTML renderizado, não presumidos):

| Elemento | Seletor |
| :-- | :-- |
| Fundo da página | `.login-pf body` |
| Grid do container | `.pf-v5-c-login__container` |
| Cabeçalho/wordmark | `#kc-header-wrapper` |
| Card | `.pf-v5-c-login__main` |
| Título do card | `.pf-v5-c-login__main-header h1` |
| Rótulo do campo | `.pf-v5-c-form__label-text` |
| **Moldura do campo** | `.pf-v5-c-form-control` (é um `<span>` que **envolve** o `<input>` — a borda e o fundo moram nele, não no input) |
| Botão "olho" da senha | `.pf-v5-c-input-group .pf-v5-c-button.pf-m-control` |
| Botão de submit | `#kc-login` |

- [ ] **Step 1: Escrever a verificação que deve falhar**

```bash
cat > /tmp/check-css.sh <<'EOF'
#!/usr/bin/env bash
export MSYS_NO_PATHCONV=1
V=$(docker exec infra-keycloak-1 sh -c '
CC=E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM
curl -k -s "https://localhost:8843/realms/dcm4chee/protocol/openid-connect/auth?response_type=code&client_id=blackice-quarkus&scope=openid&redirect_uri=http%3A%2F%2Fblackice.localhost%2Fapi%2Flogin&state=x&nonce=y&code_challenge=$CC&code_challenge_method=S256"
' | grep -o '/resources/[^/]*/login/blackice/css/blackice.css' | head -1)
echo "url: $V"
docker exec infra-keycloak-1 sh -c "curl -k -s https://localhost:8843$V" > /tmp/blackice.css
echo "--- tokens ---"
for t in --bi-bg --bi-surface --bi-accent --bi-text --bi-radius; do
  grep -q -- "$t:" /tmp/blackice.css && echo "OK  $t" || echo "FALTA $t"
done
echo "--- sentinela removida? ---"
grep -q 'magenta' /tmp/blackice.css && echo "FALHA: sentinela ainda presente" || echo "OK sentinela removida"
echo "--- preto puro? ---"
grep -qiE '#000([^0-9a-f]|$)|#000000' /tmp/blackice.css && echo "FALHA: #000 encontrado" || echo "OK sem #000"
EOF
chmod +x /tmp/check-css.sh
```

- [ ] **Step 2: Rodar e confirmar que falha**

Run: `bash /tmp/check-css.sh`

Expected: `FALTA` em todos os cinco tokens e `FALHA: sentinela ainda presente`.

- [ ] **Step 3: Escrever a folha de estilo**

Modify `infra/keycloak/themes/blackice/login/resources/css/blackice.css` — substituir o conteúdo inteiro por:

```css
/* Tema de login BlackICE. Empilhado sobre o styles.css do keycloak.v2,
   que continua sendo servido antes deste (ver theme.properties). */

:root {
  --bi-bg:         #06080b;
  --bi-surface:    #0d1117;
  --bi-field:      #080b0f;
  --bi-border:     #1a222e;
  --bi-border-in:  #1e2735;
  --bi-text:       #e6edf5;
  --bi-text-dim:   #8b98a9;
  --bi-text-mute:  #4e5a6b;
  --bi-accent:     #56c8e8;
  --bi-accent-ink: #04161e;

  --bi-radius:     6px;
  --bi-radius-lg:  10px;
  --bi-space-1:    6px;
  --bi-space-2:    18px;
  --bi-space-3:    32px;

  --bi-font:       system-ui, -apple-system, "Segoe UI", sans-serif;
  --bi-mono:       ui-monospace, "SF Mono", Menlo, Consolas, monospace;
}

/* --- fundo: grid técnico apagado + brilho frio no topo.
       O fundo NÃO é preto puro: #06080b tem azul dentro. --- */
.login-pf body {
  background: var(--bi-bg);
  background-image:
    radial-gradient(720px 380px at 50% -14%, rgba(86, 200, 232, .11), transparent 64%),
    linear-gradient(rgba(86, 200, 232, .05) 1px, transparent 1px),
    linear-gradient(90deg, rgba(86, 200, 232, .05) 1px, transparent 1px);
  background-size: 100% 100%, 44px 44px, 44px 44px;
  background-attachment: fixed;
  color: var(--bi-text);
  font-family: var(--bi-font);
}

.pf-v5-c-login__container { grid-template-columns: 26rem; }

/* --- wordmark (markup vem do message bundle) --- */
#kc-header-wrapper { padding: 62px 10px 26px; text-align: center; }

.bi-wm {
  display: block;
  font-size: 25px;
  font-weight: 600;
  letter-spacing: .13em;
  color: var(--bi-text);
  line-height: 1;
}
.bi-wm-ice { color: var(--bi-accent); }

/* acento 2 de 3: a régua */
.bi-wm::after {
  content: "";
  display: block;
  width: 34px;
  height: 1px;
  background: var(--bi-accent);
  opacity: .85;
  margin: 15px auto 0;
}

.bi-exp {
  display: block;
  margin-top: 13px;
  font-family: var(--bi-mono);
  font-size: 8.5px;
  letter-spacing: .2em;
  text-transform: uppercase;
  color: var(--bi-text-mute);
  line-height: 1.9;
}

/* --- card --- */
.pf-v5-c-login__main {
  background: var(--bi-surface);
  border: 1px solid var(--bi-border);
  border-radius: var(--bi-radius-lg);
  padding: var(--bi-space-3);
  box-shadow: 0 24px 60px rgba(0, 0, 0, .55), inset 0 1px 0 rgba(255, 255, 255, .04);
}

.pf-v5-c-login__main-header { padding: 0 0 var(--bi-space-2); }
.pf-v5-c-login__main-header h1 {
  font-size: 15px;
  font-weight: 500;
  color: var(--bi-text-dim);
  text-align: center;
}

/* --- campos --- */
.pf-v5-c-form__group + .pf-v5-c-form__group { margin-top: var(--bi-space-2); }

.pf-v5-c-form__label-text {
  font-family: var(--bi-mono);
  font-size: 9.5px;
  letter-spacing: .14em;
  text-transform: uppercase;
  color: var(--bi-text-dim);
}

/* A borda mora no <span class="pf-v5-c-form-control">, que envolve o <input>. */
.pf-v5-c-form-control {
  --pf-v5-c-form-control--BackgroundColor: var(--bi-field);
  --pf-v5-c-form-control--BorderTopColor: var(--bi-border-in);
  --pf-v5-c-form-control--BorderRightColor: var(--bi-border-in);
  --pf-v5-c-form-control--BorderBottomColor: var(--bi-border-in);
  --pf-v5-c-form-control--BorderLeftColor: var(--bi-border-in);
  --pf-v5-c-form-control--Color: var(--bi-text);
  background: var(--bi-field);
  border: 1px solid var(--bi-border-in);
  border-radius: var(--bi-radius);
  height: 40px;
  padding: 0 13px;
}
.pf-v5-c-form-control::before,
.pf-v5-c-form-control::after { border: 0; }

.pf-v5-c-form-control > input {
  background: transparent;
  border: 0;
  color: var(--bi-text);
  font-size: 13px;
  width: 100%;
}
.pf-v5-c-form-control > input:-webkit-autofill {
  -webkit-text-fill-color: var(--bi-text);
  -webkit-box-shadow: 0 0 0 40px var(--bi-field) inset;
}

/* acento 3 de 3: o foco */
.pf-v5-c-form-control:focus-within {
  border-color: var(--bi-accent);
  box-shadow: 0 0 0 3px rgba(86, 200, 232, .11);
}

.pf-v5-c-input-group { background: transparent; gap: var(--bi-space-1); }
.pf-v5-c-input-group .pf-v5-c-button.pf-m-control {
  background: var(--bi-field);
  border: 1px solid var(--bi-border-in);
  border-radius: var(--bi-radius);
  color: var(--bi-text-dim);
}

/* --- botão --- */
#kc-login {
  background: var(--bi-accent);
  border: 0;
  border-radius: var(--bi-radius);
  color: var(--bi-accent-ink);
  font-size: 13.5px;
  font-weight: 600;
  height: 42px;
  margin-top: var(--bi-space-1);
}
#kc-login:hover { background: #6fd3ee; }

/* --- rodapé --- */
.pf-v5-c-login__main-footer {
  margin-top: 24px;
  text-align: center;
  font-family: var(--bi-mono);
  font-size: 8.5px;
  letter-spacing: .16em;
  text-transform: uppercase;
  color: var(--bi-text-mute);
}

/* --- erros --- */
.pf-v5-c-alert.pf-m-inline {
  background: rgba(248, 113, 113, .08);
  border: 1px solid rgba(248, 113, 113, .3);
  border-radius: var(--bi-radius);
  color: #fca5a5;
}
```

- [ ] **Step 4: Rodar a verificação e confirmar que passa**

Run: `bash /tmp/check-css.sh`

Expected: `OK` nos cinco tokens, `OK sentinela removida`, `OK sem #000`.

Se a URL vier vazia, o cache do tema não foi desligado — conferir as três env `KC_SPI_THEME_*` da Task 1 e que o container foi **recriado**, não reiniciado.

- [ ] **Step 5: Gate visual humano**

Estas checagens provam que o CSS chegou, não que ele está bonito. Abrir `http://blackice.localhost/` no browser do humano, aceitar o aviso de certificado (o cert do Keycloak é self-signed e sem SAN para `localhost` — o browser embutido do agente não passa dele) e conferir contra o mockup aprovado:

- fundo escuro azulado com o grid apagado, sem preto chapado;
- wordmark `BLACK` claro + `ICE` ciano, régua curta abaixo, assinatura em mono apagada;
- campos de 40px com borda hairline, ciano só ao focar;
- botão ciano com texto escuro.

Ajustar o CSS pelo devtools antes de fixar no arquivo — PatternFly v5 usa custom properties próprias e alguns valores podem exigir a var da PF em vez da propriedade direta. Só seguir com aprovação explícita do humano.

- [ ] **Step 6: Commit**

```bash
git add infra/keycloak/themes/blackice/login/resources/css/blackice.css
git commit -m "💄 estiliza a tela de login com os tokens do blackice"
```

---

### Task 4: Documentação e verificação ponta-a-ponta

**Files:**
- Modify: `infra/keycloak/README.md`

**Interfaces:**
- Consumes: tudo das Tasks 1–3.
- Produces: nada que outra task consuma.

- [ ] **Step 1: Login ponta-a-ponta**

No browser do humano: `http://blackice.localhost/` → tela de login do BlackICE → entrar com `dr.teste` / `teste123` → cair no SPA autenticado, com o nome do usuário. Confirma que o tema não quebrou o fluxo OIDC.

- [ ] **Step 2: Confirmar o Archive intocado**

O `curl` com PKCE das tasks anteriores é do client `blackice-quarkus` e **não serve** para isto: o Archive é outro client (`dcm4chee-arc-ui`), com outro redirect URI. Abrir a UI do Archive no browser e confirmar que o login dela continua com a marca `j4care` — logo e background da dcm4che, não o wordmark do BlackICE.

- [ ] **Step 3: Documentar**

Modify `infra/keycloak/README.md`, acrescentando antes da seção `## Como aplicar`:

```markdown
## Tema de login `blackice`

Vive em `infra/keycloak/themes/blackice/login/`, bind-montado read-only em
`/opt/keycloak/themes/blackice`. Tema filho de `keycloak.v2` (PatternFly v5), sem
nenhum template FreeMarker copiado — o que economiza um diff a cada upgrade.

- **Texto** (wordmark, assinatura, rótulos em PT-BR) vem de
  `messages/messages_en.properties`. `loginTitleHtml` é chave de mensagem, e o
  `kcSanitize` desta build preserva `<span>` com `class` — por isso o wordmark é
  texto real, e não `content:` em CSS.
- **Aparência** vem de `resources/css/blackice.css`, que define os tokens `--bi-*`
  do produto.

### Três armadilhas

1. **`styles=` substitui a lista do pai, não concatena.** Por isso o
   `theme.properties` re-lista `css/styles.css` (arquivo do `keycloak.v2`, não
   nosso) antes do nosso. Nunca criar um `styles.css` neste tema: isso sombreia o
   do pai e perde os ajustes dele — é o bug que o tema `j4care` tem.
2. **O escopo é por client.** `login_theme=blackice` está no client
   `blackice-quarkus`, aplicado pelo `configure-blackice.sh`. O realm segue em
   `loginTheme: j4care`, que é o que o login do Archive usa. Para **remover** o
   atributo é preciso enviar `""` no PUT — omitir a chave devolve 204 sem apagar.
3. **Cache de tema.** A imagem roda `kc.sh start` (modo produção) e cacheia temas
   e templates. As três env `KC_SPI_THEME_*` no compose desligam isso; sem elas,
   editar CSS não surte efeito até um restart. São **dev-only**: num deploy real,
   remover e assar o tema na imagem.

### Idioma

O conteúdo do bundle é PT-BR num arquivo chamado `messages_en.properties`, de
propósito. O realm tem `internationalizationEnabled: false`, então `en` é o único
bundle consultado. Ligar i18n de verdade é setting de realm e adicionaria seletor
de idioma também no login do Archive.
```

- [ ] **Step 4: Commit**

```bash
git add infra/keycloak/README.md
git commit -m "📝 documenta o tema de login e suas armadilhas"
```

---

## Fora deste plano

- **Aplicação dos tokens `--bi-*` no SPA** — segundo spec, conforme decidido no brainstorming. `HomePage.vue` fica como está.
- **Telas secundárias do Keycloak** (recuperação de senha, OTP, consentimento). Herdam `blackice.css` e ficam legíveis, mas não são desenhadas aqui.
- **Favicon e logo em imagem** — o wordmark é texto puro por escolha.
- **Qualquer mudança em setting de realm.**
