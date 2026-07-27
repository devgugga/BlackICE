# BlackICE — E2E de regressão UI/UX para o tema do Keycloak

## Objetivo

Criar uma suíte E2E CI-ready que exercite o login real do BlackICE no Keycloak
e detecte regressões estruturais e visuais do tema, começando pelo ícone de erro
desalinhado, pelo foco duplicado e pelos controles dos campos.

## Estado atual

- O BlackICE usa Keycloak 25.0.6 e um tema `blackice` filho de `keycloak.v2`.
- O tema é CSS-only e não sobrescreve templates FreeMarker.
- O frontend possui Vitest, mas o repositório não possui Playwright, Cypress ou
  outra suíte E2E.
- Não há configuração de um provedor de CI versionada no repositório.
- A stack canônica é iniciada pelos três arquivos Compose em `infra/`.
- O campo de usuário com erro já contém a estrutura nativa do PatternFly:
  `.pf-v5-c-form-control__utilities`,
  `.pf-v5-c-form-control__icon.pf-m-status` e o ícone Font Awesome.

## Evidência do desalinhamento

A página real foi inspecionada no Chrome no estado de credenciais inválidas.
As medidas renderizadas foram:

| Elemento | Centro vertical |
| :-- | --: |
| `.pf-v5-c-form-control` | `419.0625px` |
| `.pf-v5-c-form-control__utilities` | `419.0625px` |
| `.pf-v5-c-form-control__icon.pf-m-status` | `419.0625px` |
| `.fa-exclamation-circle` | `422.0625px` |

O glifo está exatamente `3px` abaixo do centro do campo. A causa é
`padding-top: 6px` no status icon do PatternFly: o container está centralizado,
mas o padding desloca o filho pela metade do seu valor.

## Política de customização do tema

A documentação oficial do Keycloak recomenda estender um tema existente e
aproveitar os templates nativos sempre que possível. Templates individuais
podem ser sobrescritos, porém precisam ser reconciliados com o original em
upgrades.

Decisão para o BlackICE:

1. Usar CSS para alinhamento, cores, espaçamento, foco e responsividade.
2. Usar `theme.properties` para classes, mensagens e recursos configuráveis.
3. Sobrescrever FreeMarker somente quando o HTML nativo não fornecer a
   estrutura ou a semântica necessária.

Se um `.ftl` for necessário futuramente, ele deve:

- ser copiado da tag exata `25.0.6` do repositório oficial do Keycloak;
- registrar em comentário o arquivo e a versão de origem;
- preservar action URLs, atributos ARIA, mensagens sanitizadas e controles de
  autenticação;
- ser revisado em todo upgrade do Keycloak;
- ter o comportamento alterado coberto pela suíte E2E.

O ícone de erro atual não justifica um override: o markup correto já existe e a
causa é exclusivamente CSS.

Referências oficiais:

- [Keycloak — Working with themes](https://www.keycloak.org/ui-customization/themes)
- [Keycloak 25.0.6 — `base/login/login.ftl`](https://github.com/keycloak/keycloak/blob/25.0.6/themes/src/main/resources/theme/base/login/login.ftl)
- [Keycloak 25.0.6 — `keycloak.v2/login/theme.properties`](https://github.com/keycloak/keycloak/blob/25.0.6/themes/src/main/resources/theme/keycloak.v2/login/theme.properties)

## Arquitetura do E2E

O Playwright será incorporado ao projeto Node existente em `apps/frontend/`.
Isso evita um segundo `package.json` e um segundo lockfile apenas para testes.

Estrutura prevista:

```text
apps/frontend/
├── e2e/
│   ├── keycloak-login.spec.ts
│   └── keycloak-login.spec.ts-snapshots/
├── playwright.config.ts
├── package.json
└── package-lock.json
```

Arquivos adicionais modificados:

```text
infra/keycloak/themes/blackice/login/resources/css/blackice.css
apps/frontend/README.md
```

Nenhum `.ftl` será criado na primeira implementação.

## Projetos e viewports

A primeira versão usa apenas Chromium:

| Projeto | Viewport |
| :-- | :-- |
| `chromium-desktop` | `1920×1080` |
| `chromium-mobile` | `390×844` |

Firefox e WebKit ficam fora do escopo inicial. Eles multiplicariam baselines e
diferenças de renderização antes de existir demanda real por compatibilidade
nesses motores.

## Fluxo do teste

Cada projeto:

1. acessa `BLACKICE_E2E_URL`, cujo padrão é
   `http://blackice.localhost`;
2. acompanha o redirecionamento OIDC até o login real do Keycloak;
3. confirma que o tema `blackice` foi carregado;
4. verifica estrutura, nomes acessíveis e estado inicial;
5. percorre os campos por teclado e confirma o foco do wrapper sem outline
   branco interno;
6. envia uma única combinação propositalmente inválida, sem credenciais reais;
7. aguarda `#input-error` e o estado `aria-invalid`;
8. mede a centralização vertical dos ícones com tolerância máxima de `1px`;
9. confirma que a mensagem de erro permanece contida no card;
10. verifica o botão de visibilidade da senha;
11. compara o screenshot com o baseline do projeto.

O teste não tenta autenticar um usuário real e não depende de secrets.

## Estratégia híbrida de regressão

### Assertions estruturais e geométricas

Usadas para invariantes que não podem depender de comparação visual subjetiva:

- campos e botão possuem nomes acessíveis;
- erro usa `aria-invalid`;
- diferença entre os centros verticais de campo e ícone é no máximo `1px`;
- mensagem de erro não ultrapassa a largura do card;
- input focado não possui outline próprio;
- wrapper focado mantém borda e halo do tema;
- botão de visibilidade está centralizado no campo de senha.

Essas assertions detectam o deslocamento atual de `3px` mesmo que uma tolerância
de screenshot o absorva.

### Snapshots

Cada viewport possui baseline próprio do card composto por `#kc-header` e
`.pf-v5-c-login__main`. O teste calcula o retângulo que contém esses dois
irmãos e captura somente essa região, evitando que o espaço vazio do viewport
domine a comparação. A comparação usa tolerância visual pequena para absorver
ruído irrelevante, enquanto os invariantes críticos são garantidos pelas
assertions geométricas.

Os baselines devem ser gerados e comparados no mesmo ambiente Linux. A
documentação do Playwright alerta que sistema operacional, versão do navegador,
fontes e hardware podem alterar pixels.

Referências oficiais:

- [Playwright — Visual comparisons](https://playwright.dev/docs/next/test-snapshots)
- [Playwright — Assertions](https://playwright.dev/docs/test-assertions)

## Contrato CI-ready

Comando público, executado em `apps/frontend/`:

```bash
npm run test:e2e:keycloak
```

O comando é independente de GitHub Actions, Azure Pipelines ou outro provedor.
O pipeline é responsável por:

1. iniciar a stack pelos três arquivos Compose canônicos;
2. executar `npm ci`;
3. instalar Chromium e dependências com
   `npx playwright install --with-deps chromium`;
4. executar `npm run test:e2e:keycloak`;
5. coletar o relatório e os artefatos de falha;
6. encerrar a stack em seu bloco de cleanup.

Configuração do Playwright:

- `workers: 1` quando `CI` estiver definido;
- uma repetição no CI e nenhuma repetição local;
- `ignoreHTTPSErrors: true` para o certificado local do Keycloak;
- trace, screenshot e vídeo preservados em falhas;
- relatório HTML gerado sem abrir servidor automaticamente;
- erro explícito quando a stack não estiver disponível.

O teste não sobe nem derruba containers. Isso mantém clara a responsabilidade
do pipeline e impede que uma execução local encerre a stack do desenvolvedor.

Referência oficial:

- [Playwright — Continuous Integration](https://playwright.dev/docs/ci)

## Correções iniciais cobertas

A primeira implementação deve caracterizar e corrigir, uma por vez:

1. o ícone de erro `3px` abaixo do centro;
2. o outline branco duplicado dos inputs;
3. o alinhamento do botão de exibir senha;
4. a legibilidade e contenção da mensagem de erro;
5. a consistência dos campos em desktop e mobile.

Cada correção começa com uma assertion E2E que falha pelo motivo esperado.

## Tratamento de falhas

- Falha de conexão deve informar que a stack precisa estar ativa e qual URL foi
  tentada.
- Falha geométrica deve exibir os dois centros medidos e a diferença.
- Falha visual deve preservar imagem esperada, recebida e diff.
- Falha funcional deve preservar trace, screenshot e vídeo.
- Atualização de snapshot exige execução explícita com
  `--update-snapshots` e revisão humana dos PNGs.

## Fora de escopo

- recuperação de senha;
- OTP;
- cadastro;
- consentimento;
- login do DCM4CHEE Archive, que continua com `j4care`;
- Firefox e WebKit;
- seleção de um provedor de CI;
- criação de templates FreeMarker sem necessidade estrutural comprovada.
