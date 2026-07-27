# BlackICE — Design do tema de login do Keycloak

> Design aprovado em brainstorming em 2026-07-26. Cobre a identidade visual do
> produto (tokens) e sua primeira aplicação: a tela de login do Keycloak. A
> aplicação dos mesmos tokens no SPA é um **segundo spec**, fora deste.

## Objetivo

Substituir a marca `j4care` da tela de login por uma identidade própria do
BlackICE, sem tocar no login do DCM4CHEE Archive e sem copiar templates
FreeMarker.

A tela de login é hoje a primeira superfície do produto com design. Os tokens
definidos aqui são a identidade do BlackICE, não decoração de uma tela: o SPA os
herda depois sem retradução.

## Estado atual (verificado em 2026-07-26)

- Realm `dcm4chee` com `loginTheme: j4care`, compartilhado entre o BFF
  (`blackice-quarkus`) e a UI do Archive (`dcm4chee-arc-ui`).
- `j4care` é um tema CSS-only (127 linhas) com `parent=keycloak.v2`
  (PatternFly v5), que troca logo, background e o grid do card.
- Nenhum client do realm usa o atributo `login_theme`.
- Keycloak 25.0.6, imagem `dcm4che/keycloak:25.0.6`, comando
  `kc.sh start --import-realm` — **modo produção, cache de tema ligado**.
- `internationalizationEnabled: false`, `supportedLocales: []`.

## Decisões

| Decisão | Escolha | Motivo |
| :-- | :-- | :-- |
| Direção visual | Escuro sóbrio, identidade por ofício e não por ornamento | Um PACS com dado de paciente não pode parecer brinquedo; ornamento cyberpunk é o que denuncia amadorismo |
| Assinatura do nome | Só a expansão de ICE, em texto apagado | Paga a referência do Cyberpunk inteira em uma linha que também se lê como legenda técnica |
| Escopo | `login_theme` no client `blackice-quarkus` | Archive segue `j4care`; `AGENTS.md` manda não perturbar o DCM4CHEE |
| Técnica | Tema filho CSS + message bundle, zero FreeMarker | Suficiente (provado abaixo) e sem dívida de template a cada upgrade |
| Idioma | PT-BR sobrescrevendo `messages_en.properties` | Ligar i18n é setting de realm e atingiria o Archive |
| Cache de tema | Desligado sempre neste compose | `infra/` deste repo é ambiente local de dev |

## Direção visual

Escuro de sala de laudos, contínuo com o viewer Cornerstone3D. A identidade vem
de quatro decisões, não de enfeite:

1. **O preto não é `#000`.** Fundo `#06080b` com azul dentro; card `#0d1117`.
   Duas camadas, não uma. Preto puro chapado é o denunciador nº 1 de tema amador.
2. **O acento saturado aparece quatro vezes.** Metade do wordmark, uma régua de
   34px sob ele, a borda do campo em foco, e o preenchimento do botão primário.
   Espalhar vira neon. A textura de fundo descrita abaixo usa o mesmo RGB a
   5–11% de opacidade — isso é textura, não acento, e não conta para essa regra.
3. **O ritmo é fixo.** Campo de 40px de altura, respiro de 18px entre campos,
   padding de 32px no card.
4. **A assinatura é texto, não enfeite.** "Intrusion Countermeasures
   Electronics" por extenso, 8.5px, `--bi-text-mute`, sob o wordmark.

### Tokens

Custom properties no `:root` de `blackice.css`, no formato que o SPA importa depois.

```css
:root {
  --bi-bg:        #06080b;
  --bi-surface:   #0d1117;
  --bi-field:     #080b0f;
  --bi-border:    #1a222e;
  --bi-border-in: #1e2735;
  --bi-text:      #e6edf5;
  --bi-text-dim:  #8b98a9;
  --bi-text-mute: #4e5a6b;
  --bi-accent:    #56c8e8;
  --bi-accent-ink:#04161e;   /* texto sobre o acento */

  --bi-radius:    6px;
  --bi-radius-lg: 10px;
  --bi-space-1:   6px;
  --bi-space-2:   18px;
  --bi-space-3:   32px;
}
```

Tipografia: `system-ui` para o corpo; `ui-monospace` para labels e microcopy.
Wordmark em 25px/600 com `letter-spacing: .13em`. Sem webfont — nenhuma
dependência externa entra na tela de autenticação.

### Superfície do fundo

Grid técnico de 44px em `rgba(86,200,232,.05)`, com `mask-image` radial que o
apaga nas bordas, mais um brilho radial frio no topo. É a única textura da tela.

## Arquitetura do tema

```text
infra/keycloak/themes/blackice/login/
├── theme.properties
├── resources/css/blackice.css
└── messages/messages_en.properties
```

### `theme.properties`

```properties
parent=keycloak.v2
styles=css/styles.css css/blackice.css
```

**`css/styles.css` não é arquivo nosso.** Resolve pela cadeia de pais até o
`keycloak.v2` (verificado: HTTP 200, 581 bytes, com os ajustes de
`#kc-header-wrapper` e do grid do container). Nosso arquivo tem nome distinto e
empilha por cima.

> **Armadilha:** `styles=` numa propriedade filha **substitui** a lista do pai,
> não concatena. O `j4care` nomeia seu arquivo `styles.css`, sombreia o do pai e
> perde esses ajustes. Não repetir.

`stylesCommon` (PatternFly v5) é herdado sem alteração.

### A assinatura via message bundle

`keycloak.v2/login/template.ftl:61` renderiza o header assim:

```ftl
<div id="kc-header-wrapper" class="${properties.kcHeaderWrapperClass!}">
  ${kcSanitize(msg("loginTitleHtml",(realm.displayNameHtml!'')))?no_esc}</div>
```

`loginTitleHtml` é **chave de mensagem** (base: `loginTitleHtml={0}`, hoje
renderiza `dcm4chee`). Sobrescrevê-la no bundle do tema injeta o wordmark sem
tocar em template.

**`kcSanitize` foi testado empiricamente neste build** com um tema descartável:
`<span class="…">`, `<div class="…">`, `<br/>`, `<b>` e `<em>` chegaram
**intactos ao HTML, com os atributos `class` preservados**. Logo o wordmark de
duas cores e a linha de expansão saem os dois daqui, como texto real —
selecionável e traduzível, em vez de `content:` em CSS.

```properties
loginTitleHtml=<span class="bi-wm">BLACK<span class="bi-wm-ice">ICE</span></span>\
<span class="bi-exp">Intrusion Countermeasures Electronics</span>
```

> **Escrita do `.properties`:** o probe validou o markup na forma `class\="…"`
> (`=` escapado). Dentro de um valor o `=` cru também é legal em properties
> Java, mas foi a forma escapada que passou pelo teste — a implementação deve
> re-verificar o HTML renderizado com a forma que de fato for escrita. Além
> disso o valor passa por `MessageFormat` (a chave aceita `{0}`): apóstrofo
> nessa microcópia precisa ser dobrado (`''`).

`.bi-wm` e `.bi-exp` recebem `display: block` no CSS (o bundle entrega spans
inline). A régua de 34px entre os dois é `.bi-wm::after` — é ornamento puro, não
texto, e não tem por que estar no bundle.

### Textos em PT-BR

O mesmo `messages_en.properties` sobrescreve os rótulos. O realm tem i18n
desligado, então `en` é o único bundle que o Keycloak consulta.

| Chave | Valor |
| :-- | :-- |
| `loginAccountTitle` | Acesso ao sistema |
| `username` / `usernameOrEmail` | Usuário |
| `password` | Senha |
| `doLogIn` | Entrar |

> **Esquisitice assumida:** arquivo chamado `messages_en.properties` com
> conteúdo em português. É o preço de manter tudo escopado no client — ligar
> i18n de verdade é setting de realm e adicionaria seletor de idioma também no
> login do Archive. Se um dia o realm ganhar i18n, o conteúdo migra para
> `messages_pt_BR.properties` sem mais nada mudar.

### Seletores a estilizar

Do HTML renderizado hoje: `.login-pf body` (fundo), `#kc-header-wrapper`
(wordmark), `.pf-v5-c-login__main` (card), `.pf-v5-c-login__main-header h1`,
`#kc-form-login`, `#username`, `#password` (`.pf-v5-c-form-control`), `#kc-login`
(botão), `.pf-v5-c-login__main-footer`.

`#kc-header::before` fica livre — é onde o `j4care` pinta a logo dele, e não
herdamos do `j4care`.

## Escopo por client

Atributo `login_theme=blackice` no client `blackice-quarkus`, aplicado pelo
`infra/keycloak/configure-blackice.sh` — mesmo padrão Admin REST idempotente via
`curl -k` dentro do container que o script já usa.

> **Armadilha verificada:** para **remover** o atributo é preciso enviar
> `login_theme: ""` no PUT. Omitir a chave retorna 204 e não apaga nada — o
> valor antigo permanece. O script precisa disso para ser reversível.

O login do Archive (`dcm4chee-arc-ui`) continua em `j4care`, sem alteração.

## Compose

Em `infra/dcm4chee/compose.yml`, serviço `keycloak`:

```yaml
volumes:
  - "../keycloak/themes/blackice:/opt/keycloak/themes/blackice:ro"
environment:
  # DEV-ONLY: a imagem roda `kc.sh start` (modo produção), que cacheia temas e
  # templates. Sem isto, editar CSS ou theme.properties não surte efeito até um
  # restart (~21s). Num deploy real, remover as três e assar o tema na imagem.
  KC_SPI_THEME_CACHE_THEMES: 'false'
  KC_SPI_THEME_CACHE_TEMPLATES: 'false'
  KC_SPI_THEME_STATIC_MAX_AGE: '-1'
```

Mount read-only: o Keycloak não tem por que escrever no tema.

## Verificação

1. `docker compose … config --quiet` passa.
2. Requisição de autorização com PKCE (o Keycloak exige `code_challenge_method`)
   retorna 200 e o HTML contém:
   - `href="/resources/<v>/login/blackice/css/blackice.css"` **e**
     `…/login/blackice/css/styles.css` (prova que empilhou, não sombreou);
   - o wordmark em `#kc-header-wrapper`, com os `class` preservados e idêntico
     ao markup pretendido (diff, não inspeção a olho).
3. Login do Archive continua em `j4care` — **checagem humana**: o `curl` com
   PKCE acima é do client `blackice-quarkus`; o Archive é outro client
   (`dcm4chee-arc-ui`), com outro redirect URI, e não sai da mesma requisição.
   Abrir a UI do Archive e confirmar que a marca `j4care` segue lá.
4. Conferência visual no browser do humano, e login ponta-a-ponta com
   `dr.teste` até o SPA.

> O cert do Keycloak é self-signed e **sem SAN** para `localhost`. O browser
> embutido do agente não passa dele — a verificação visual é do humano, no
> browser dele, aceitando o aviso de certificado.

## Fora de escopo

- **Aplicação dos tokens no SPA** — segundo spec. A `HomePage.vue` continua como
  está até lá.
- Telas secundárias do Keycloak (recuperação de senha, OTP, consentimento). Elas
  herdam o `blackice.css` e ficam legíveis, mas não são desenhadas aqui.
- Favicon e logo em imagem: o wordmark é texto puro por escolha.
- Qualquer mudança em setting de realm.

## Riscos

| Risco | Mitigação |
| :-- | :-- |
| Upgrade do Keycloak muda classes do PatternFly | Estilizamos poucos seletores e nenhum template copiado; o conserto é CSS |
| `kcSanitize` endurecer em versão futura e passar a remover `class` | Fallback conhecido: `#kc-header-wrapper span { color: … }`, que não depende de atributo |
| Alguém aplicar o tema no realm por engano | O tema está no client; `configure-blackice.sh` é a única via versionada |
