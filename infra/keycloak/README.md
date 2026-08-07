# Config Keycloak do BlackICE (realm `dcm4chee`)

Configuração aplicada ao **mesmo realm** que o Archive usa (`dcm4chee`) — condição
para o **audience compartilhado** (ver
[design](../../docs/superpowers/specs/2026-07-23-blackice-backend-frontend-design.md)),
que evita token exchange.

## O que `configure-blackice.sh` cria (idempotente)

- **`blackice-quarkus`** — client confidencial (BFF). Standard flow + PKCE (S256),
  `directAccessGrants` OFF, `serviceAccounts` OFF. Redirect `http://${APP_HOST}/api/*`,
  webOrigin `http://${APP_HOST}`. Secret = `QUARKUS_OIDC_SECRET` (de `infra/.env`).
- **`arc-audience`** — audience mapper no client acima, injetando **`dcm4chee-arc-rs`**
  (o client que protege a DICOMweb REST) no claim `aud`.
- **`dr.teste` / `teste123`** — usuário de teste.

O script **não** atribui realm roles (decisão de gate humano — ver abaixo).

### Por que `curl` e não `kcadm`

O cert do Keycloak é self-signed e **sem SAN** para `localhost`; a verificação de
hostname do `kcadm` rejeita. Usamos a **Admin REST via `curl -k`** (ignora
cert/hostname), executada **dentro do container** `keycloak`, obtendo o token admin
das vars `KEYCLOAK_ADMIN`/`KEYCLOAK_ADMIN_PASSWORD` do próprio container (nunca
impressas). O Keycloak serve **HTTP em :8080 na rede interna** (atrás do Traefik, em
`http://${APP_HOST}/auth`) **e HTTPS na 8843** (backchannel do Archive e Admin
REST). O root path do servidor é `/auth` — vale para os dois listeners, e por
isso a base da Admin REST no script é `https://localhost:8843/auth`.

## Roles (gate humano) — decisão registrada

O realm `dcm4chee` tem, de custom, só **`auth`** e **`root`** (o resto é built-in do
Keycloak; `dcm4chee-arc-rs` não expõe client-roles). Decisão: **`dr.teste` recebe a
role `auth`** (usuário autenticado normal, papel realista de radiologista).

Comando de atribuição (reproduzível; dentro do container `keycloak`):

```sh
# obtenha um token admin (adm) e o id do usuário (USERID), então:
ROLE=$(curl -k -s -H "$AH" "$api/roles/auth")
curl -k -s -H "$AH" -H "Content-Type: application/json" \
  -X POST "$api/users/$USERID/role-mappings/realm" -d "[$ROLE]"
```

Se algum fluxo (provavelmente STOW) travar por permissão, adicionar `root`.

## ✅ Validação end-to-end (audience + authz)

Antes de qualquer código Quarkus/Vue, provamos a suposição de auth mais crítica.
Habilitando `directAccessGrants` **temporariamente**, um token de `dr.teste` emitido
pelo `blackice-quarkus`:

- sai com `"aud":"dcm4chee-arc-rs"` e `"preferred_username":"dr.teste"`;
- ao chamar `GET http://arc:8080/dcm4chee-arc/aets/DCM4CHEE/rs/studies` (rede interna)
  retorna **`204`** (autenticado + autorizado; sem estudos ainda), **não `401`**.

Isso confirma: **audience compartilhado funciona** e a role **`auth` autoriza
DICOMweb**. `directAccessGrants` foi **revertido para `false`** após o teste — o BFF
não deve permitir password grant no fluxo real.

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

### Como desfazer

Remover os arquivos do tema e o bind mount **não basta**: o atributo
`login_theme=blackice` fica gravado no client `blackice-quarkus`, que vive no
banco do Keycloak (volume `dcm4chee-keycloak-data`), não no disco. Sem o tema em
`/opt/keycloak/themes/blackice`, o client fica apontando para um tema inexistente.

É preciso desfazer pela Admin REST, do mesmo jeito que `configure-blackice.sh`
aplica: GET da representação inteira do client, setar `login_theme: ""` no
atributo e PUT de volta. Omitir a chave `login_theme` no PUT devolve `204` mas
**não apaga** o atributo — só um valor vazio explícito remove (ver comentário na
seção 4 de `configure-blackice.sh`, linhas 77-78). Feito isso, o client volta a
herdar `loginTheme: j4care` do realm.

## Como aplicar

```sh
bash infra/keycloak/configure-blackice.sh   # cria client + mapper + usuário
# depois, atribua a role auth ao dr.teste (comando acima)
```

Pré-requisito: a stack do Keycloak/Archive de pé (ver `infra/dcm4chee/`).
