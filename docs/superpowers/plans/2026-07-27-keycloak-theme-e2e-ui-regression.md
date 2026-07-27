# E2E de regressão UI/UX do tema Keycloak — Plano de Implementação

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Adicionar uma suíte Playwright CI-ready que exercite o login real do
BlackICE e corrija o ícone de erro atualmente `3px` abaixo do centro.

**Architecture:** O Playwright vive no projeto Node já existente em
`apps/frontend`, acessa a stack Compose por URL e combina assertions
estruturais/geométricas com snapshots do card. O tema continua filho de
`keycloak.v2`; a correção inicial é CSS-only porque o markup nativo já contém os
elementos necessários.

**Tech Stack:** Keycloak 25.0.6, tema `keycloak.v2`, PatternFly v5,
Playwright Test 1.62.0, Chromium, TypeScript, Docker Compose.

**Spec:** `docs/superpowers/specs/2026-07-27-keycloak-theme-e2e-ui-regression-design.md`

## Global Constraints

- O comando público é `npm run test:e2e:keycloak`, executado em
  `apps/frontend/`.
- A URL vem de `BLACKICE_E2E_URL`; o padrão é
  `http://blackice.localhost`.
- Usar somente Chromium nos projetos `chromium-desktop` (`1920×1080`) e
  `chromium-mobile` (`390×844`).
- Usar `workers: 1` e uma repetição quando `CI` estiver definido; localmente,
  usar workers padrão e nenhuma repetição.
- Preservar trace, screenshot e vídeo em falhas.
- A tolerância geométrica máxima para centralização vertical é `1px`.
- Os snapshots cobrem somente o retângulo composto por `#kc-header` e
  `.pf-v5-c-login__main`.
- Gerar e validar baselines no mesmo ambiente Linux, usando a imagem oficial
  `mcr.microsoft.com/playwright:v1.62.0-noble`.
- Não usar credenciais reais; enviar uma única combinação propositalmente
  inválida por projeto.
- Não criar templates FreeMarker nesta implementação.
- Não alterar o login do Archive nem o tema `j4care`.
- Não subir nem derrubar a stack dentro do teste.
- Não criar commit ou fazer stage sem pedido explícito do dono do repositório.

---

## Estrutura de arquivos

| Arquivo | Responsabilidade |
| :-- | :-- |
| `apps/frontend/playwright.config.ts` | Configuração CI, projetos Chromium, URL, artefatos e snapshots. |
| `apps/frontend/e2e/keycloak-login.spec.ts` | Fluxo OIDC real, assertions de foco, erro, geometria e snapshot. |
| `apps/frontend/e2e/keycloak-login.spec.ts-snapshots/*.png` | Baselines Linux por projeto Playwright. |
| `apps/frontend/package.json` | Dependência Playwright e comando público. |
| `apps/frontend/package-lock.json` | Resolução reproduzível da dependência. |
| `infra/keycloak/themes/blackice/login/resources/css/blackice.css` | Correção CSS mínima do status icon. |
| `apps/frontend/README.md` | Uso local e contrato provider-agnostic para CI. |

### Task 1: Harness Playwright e caracterização geométrica

**Files:**

- Create: `apps/frontend/playwright.config.ts`
- Create: `apps/frontend/e2e/keycloak-login.spec.ts`
- Modify: `apps/frontend/package.json`
- Modify: `apps/frontend/package-lock.json`

**Interfaces:**

- Consumes: stack acessível em `BLACKICE_E2E_URL` ou
  `http://blackice.localhost`; tema `blackice` servido pelo Keycloak.
- Produces: comando `npm run test:e2e:keycloak`, projetos
  `chromium-desktop` e `chromium-mobile`, helper
  `expectVerticallyCentered(subject, container, label)` e teste E2E que falha
  com o deslocamento atual de `3px`.

- [ ] **Step 1: Registrar o baseline dos testes frontend**

Run:

```powershell
cd apps/frontend
mise exec -- npm test
```

Expected: a suíte Vitest existente passa antes da instalação do Playwright.

- [ ] **Step 2: Instalar a versão pinada do Playwright Test**

Run:

```powershell
cd apps/frontend
mise exec -- npm install --save-dev --save-exact @playwright/test@1.62.0
```

Expected: `package.json` contém
`"@playwright/test": "1.62.0"` em `devDependencies` e o lockfile é atualizado.

- [ ] **Step 3: Adicionar o comando público**

Em `apps/frontend/package.json`, adicionar a `scripts`:

```json
"test:e2e:keycloak": "playwright test e2e/keycloak-login.spec.ts"
```

O bloco completo de scripts fica:

```json
"scripts": {
  "dev": "vite",
  "build": "vue-tsc -b && vite build",
  "preview": "vite preview",
  "test": "vitest run",
  "test:e2e:keycloak": "playwright test e2e/keycloak-login.spec.ts"
}
```

- [ ] **Step 4: Criar a configuração do Playwright**

Create `apps/frontend/playwright.config.ts`:

```ts
import { defineConfig } from '@playwright/test';

const isCi = Boolean(process.env.CI);

export default defineConfig({
  testDir: './e2e',
  testMatch: 'keycloak-login.spec.ts',
  fullyParallel: false,
  workers: isCi ? 1 : undefined,
  retries: isCi ? 1 : 0,
  outputDir: 'test-results/playwright',
  reporter: [
    ['line'],
    ['html', { open: 'never', outputFolder: 'playwright-report' }],
  ],
  snapshotPathTemplate:
    '{testDir}/{testFilePath}-snapshots/{arg}-{projectName}{ext}',
  use: {
    baseURL: process.env.BLACKICE_E2E_URL ?? 'http://blackice.localhost',
    ignoreHTTPSErrors: true,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
  },
  projects: [
    {
      name: 'chromium-desktop',
      use: {
        browserName: 'chromium',
        viewport: { width: 1920, height: 1080 },
      },
    },
    {
      name: 'chromium-mobile',
      use: {
        browserName: 'chromium',
        viewport: { width: 390, height: 844 },
      },
    },
  ],
});
```

- [ ] **Step 5: Escrever o teste E2E sem snapshot ainda**

Create `apps/frontend/e2e/keycloak-login.spec.ts`:

```ts
import {
  expect,
  test,
  type Locator,
  type Page,
} from '@playwright/test';

const INVALID_USERNAME = 'blackice-e2e-missing';
const INVALID_PASSWORD = 'not-a-real-secret';
const MAX_CENTER_DELTA_PX = 1;

async function openBlackiceLogin(page: Page): Promise<void> {
  const target = String(
    test.info().project.use.baseURL ?? 'http://blackice.localhost',
  );

  try {
    await page.goto('/', { waitUntil: 'domcontentloaded' });
  } catch (error) {
    const detail = error instanceof Error ? error.message : String(error);
    throw new Error(
      `Stack BlackICE indisponível em ${target}. ` +
        `Suba os três Compose canônicos antes do E2E. ${detail}`,
    );
  }

  await expect(page).toHaveTitle('Acesso ao BlackICE');
  await expect(
    page.locator('link[href*="/login/blackice/css/blackice.css"]'),
  ).toHaveCount(1);
}

async function requiredBox(
  locator: Locator,
  label: string,
): Promise<NonNullable<Awaited<ReturnType<Locator['boundingBox']>>>> {
  const box = await locator.boundingBox();
  if (!box) {
    throw new Error(`${label} não possui bounding box visível`);
  }
  return box;
}

async function expectVerticallyCentered(
  subject: Locator,
  container: Locator,
  label: string,
): Promise<void> {
  const [subjectBox, containerBox] = await Promise.all([
    requiredBox(subject, `${label}: elemento`),
    requiredBox(container, `${label}: contêiner`),
  ]);
  const subjectCenter = subjectBox.y + subjectBox.height / 2;
  const containerCenter = containerBox.y + containerBox.height / 2;
  const delta = Math.abs(subjectCenter - containerCenter);

  expect(
    delta,
    `${label}: centro do elemento=${subjectCenter}px, ` +
      `centro do contêiner=${containerCenter}px, diferença=${delta}px`,
  ).toBeLessThanOrEqual(MAX_CENTER_DELTA_PX);
}

test('mantém foco acessível sem outline interno duplicado', async ({ page }) => {
  await openBlackiceLogin(page);

  const username = page.getByLabel('Usuário', { exact: true });
  const password = page.getByLabel('Senha', { exact: true });
  const usernameControl = username.locator('..');
  const passwordControl = password.locator('..');

  await expect(username).toBeFocused();
  await expect(username).toHaveCSS('outline-style', 'none');
  await expect(usernameControl).toHaveCSS(
    'border-color',
    'rgb(86, 200, 232)',
  );
  await expect(usernameControl).not.toHaveCSS('box-shadow', 'none');

  await username.press('Tab');
  await expect(password).toBeFocused();
  await expect(password).toHaveCSS('outline-style', 'none');
  await expect(passwordControl).toHaveCSS(
    'border-color',
    'rgb(86, 200, 232)',
  );
  await expect(passwordControl).not.toHaveCSS('box-shadow', 'none');
});

async function showInvalidCredentials(page: Page) {
  await openBlackiceLogin(page);

  const username = page.getByLabel('Usuário', { exact: true });
  const password = page.getByLabel('Senha', { exact: true });
  const submit = page.getByRole('button', { name: 'Entrar', exact: true });

  await username.fill(INVALID_USERNAME);
  await password.fill(INVALID_PASSWORD);
  await submit.click();

  const error = page.locator('#input-error');
  const usernameControl = username.locator('..');
  const statusIcon = usernameControl.locator(
    '.pf-v5-c-form-control__icon.pf-m-status i',
  );
  const passwordGroup = page
    .locator('.pf-v5-c-input-group')
    .filter({ has: password });
  const passwordToggle = passwordGroup.locator('[data-password-toggle]');
  const card = page.locator('.pf-v5-c-login__main');

  await expect(error).toBeVisible();

  return {
    card,
    error,
    password,
    passwordGroup,
    passwordToggle,
    statusIcon,
    username,
    usernameControl,
  };
}

test('centraliza o ícone de erro do usuário', async ({ page }) => {
  const {
    password,
    statusIcon,
    username,
    usernameControl,
  } = await showInvalidCredentials(page);

  await expect(username).toHaveAttribute('aria-invalid', 'true');
  await expect(password).toHaveAttribute('aria-invalid', 'true');
  await expect(statusIcon).toBeVisible();

  await expectVerticallyCentered(
    statusIcon,
    usernameControl,
    'Ícone de erro do usuário',
  );
});
```

- [ ] **Step 6: Instalar somente o Chromium local**

Run:

```powershell
cd apps/frontend
mise exec -- npx playwright install chromium
```

Expected: Chromium compatível com Playwright 1.62.0 é instalado.

- [ ] **Step 7: Listar os testes e os dois projetos**

Run:

```powershell
cd apps/frontend
mise exec -- npx playwright test e2e/keycloak-login.spec.ts --list
```

Expected: quatro casos listados — dois testes em cada um dos projetos
`chromium-desktop` e `chromium-mobile`.

- [ ] **Step 8: Rodar o caso geométrico e confirmar RED**

Precondition: a stack canônica já está ativa.

Run:

```powershell
cd apps/frontend
mise exec -- npx playwright test e2e/keycloak-login.spec.ts --project=chromium-desktop --grep "centraliza o ícone"
```

Expected: FAIL em `Ícone de erro do usuário`, reportando centros equivalentes a
`419.0625px` e `422.0625px`, com diferença `3px`. As assertions anteriores
passam; a falha não pode ser de conexão, seletor ou certificado.

- [ ] **Step 9: Confirmar que o harness não quebrou testes unitários ou build**

Run:

```powershell
cd apps/frontend
mise exec -- npm test
mise exec -- npm run build
```

Expected: Vitest e build Vue/TypeScript passam.

### Task 2: Corrigir o status icon no CSS

**Files:**

- Modify:
  `infra/keycloak/themes/blackice/login/resources/css/blackice.css`
- Test: `apps/frontend/e2e/keycloak-login.spec.ts`

**Interfaces:**

- Consumes: assertion `expectVerticallyCentered` da Task 1 e markup nativo
  `.pf-v5-c-form-control__icon.pf-m-status`.
- Produces: glifo de erro centralizado com diferença máxima de `1px`, sem
  template FreeMarker.

- [ ] **Step 1: Registrar a hipótese confirmada ao lado da regra**

Na seção de campos/erros de `blackice.css`, adicionar:

```css
/* O PatternFly aplica padding-top: 6px ao status icon: o container fica
   centralizado, mas o glifo termina 3px abaixo do centro do campo. Tornar o
   status icon um flex container sem esse padding centraliza o filho sem
   alterar o markup nativo do Keycloak. */
.pf-v5-c-form-control__icon.pf-m-status {
  display: flex;
  align-items: center;
  padding-top: 0;
}
```

- [ ] **Step 2: Reexecutar o teste geométrico desktop**

Como o tema está montado com cache desabilitado, recarregar é feito pelo novo
contexto do Playwright.

Run:

```powershell
cd apps/frontend
mise exec -- npx playwright test e2e/keycloak-login.spec.ts --project=chromium-desktop --grep "centraliza o ícone"
```

Expected: PASS; a diferença reportável entre o ícone e o campo é no máximo
`1px`.

- [ ] **Step 3: Executar a suíte nos dois viewports**

Run:

```powershell
cd apps/frontend
mise exec -- npm run test:e2e:keycloak
```

Expected: quatro testes passam; desktop e mobile validam foco, outline e
alinhamento do status icon.

- [ ] **Step 4: Conferir escopo do diff**

Run:

```powershell
git diff --check -- infra/keycloak/themes/blackice/login/resources/css/blackice.css
git diff -- infra/keycloak/themes/blackice/login/resources/css/blackice.css
```

Expected: `git diff --check` sem saída; o CSS contém somente a regra explicada
acima além de mudanças preexistentes do usuário.

### Task 3: Legibilidade do erro e controles auxiliares

**Files:**

- Modify: `apps/frontend/e2e/keycloak-login.spec.ts`
- Modify:
  `infra/keycloak/themes/blackice/login/resources/css/blackice.css`

**Interfaces:**

- Consumes: helper `showInvalidCredentials` e centralização verde das Tasks
  1–2.
- Produces: mensagem e ícone de erro em `rgb(252, 165, 165)`, contenção do
  texto no card e botão de visibilidade centralizado.

- [ ] **Step 1: Ampliar o teste de erro com legibilidade e contenção**

Substituir o teste `centraliza o ícone de erro do usuário` pelo teste abaixo.
Assim cada projeto envia credenciais inválidas uma única vez:

```ts
test('mantém erro legível e controles alinhados dentro do card', async ({
  page,
}) => {
  const {
    card,
    error,
    password,
    passwordGroup,
    passwordToggle,
    statusIcon,
    username,
    usernameControl,
  } = await showInvalidCredentials(page);

  await expect(username).toHaveAttribute('aria-invalid', 'true');
  await expect(password).toHaveAttribute('aria-invalid', 'true');
  await expect(statusIcon).toBeVisible();
  await expect(passwordToggle).toBeVisible();
  await expectVerticallyCentered(
    statusIcon,
    usernameControl,
    'Ícone de erro do usuário',
  );
  await expectVerticallyCentered(
    passwordToggle,
    passwordGroup,
    'Botão de visibilidade da senha',
  );

  const [errorBox, cardBox] = await Promise.all([
    requiredBox(error, 'Mensagem de erro'),
    requiredBox(card, 'Card de login'),
  ]);
  expect(errorBox.x).toBeGreaterThanOrEqual(cardBox.x);
  expect(errorBox.x + errorBox.width).toBeLessThanOrEqual(
    cardBox.x + cardBox.width,
  );
  await expect(error).toHaveCSS('color', 'rgb(252, 165, 165)');
  await expect(statusIcon).toHaveCSS('color', 'rgb(252, 165, 165)');
});
```

- [ ] **Step 2: Rodar e confirmar RED pela cor do PatternFly**

Run:

```powershell
cd apps/frontend
mise exec -- npx playwright test e2e/keycloak-login.spec.ts --project=chromium-desktop --grep "mantém erro legível"
```

Expected: as assertions de centralização e contenção passam; FAIL em
`toHaveCSS('color', 'rgb(252, 165, 165)')`, recebendo
`rgb(201, 25, 11)`.

- [ ] **Step 3: Aplicar a cor de erro legível**

No mesmo bloco de erros de `blackice.css`, adicionar:

```css
/* O vermelho padrão do PatternFly (#c9190b) perde contraste no card escuro.
   Usamos a mesma cor clara já aplicada aos alertas inline do tema. */
.pf-v5-c-form-control__icon.pf-m-status,
.pf-v5-c-helper-text__item.pf-m-error {
  color: #fca5a5;
}
```

- [ ] **Step 4: Reexecutar o teste de legibilidade**

Run:

```powershell
cd apps/frontend
mise exec -- npx playwright test e2e/keycloak-login.spec.ts --project=chromium-desktop --grep "mantém erro legível"
```

Expected: PASS; mensagem e status icon usam `rgb(252, 165, 165)`.

- [ ] **Step 5: Executar os quatro casos nos dois viewports**

Run:

```powershell
cd apps/frontend
mise exec -- npm run test:e2e:keycloak
```

Expected: quatro testes passam — dois em desktop e dois em mobile. Cada projeto
faz uma única submissão inválida.

### Task 4: Snapshots Linux e contrato CI-ready

**Files:**

- Modify: `apps/frontend/e2e/keycloak-login.spec.ts`
- Create:
  `apps/frontend/e2e/keycloak-login.spec.ts-snapshots/invalid-credentials-card-chromium-desktop.png`
- Create:
  `apps/frontend/e2e/keycloak-login.spec.ts-snapshots/invalid-credentials-card-chromium-mobile.png`
- Modify: `apps/frontend/README.md`

**Interfaces:**

- Consumes: fluxo verde e CSS corrigido das Tasks 1–3.
- Produces: baselines Linux do card, comparação visual híbrida e instruções
  provider-agnostic para CI.

- [ ] **Step 1: Adicionar o helper de captura do card composto**

Em `apps/frontend/e2e/keycloak-login.spec.ts`, adicionar depois de
`expectVerticallyCentered`:

```ts
async function screenshotLoginCard(page: Page): Promise<Buffer> {
  const header = page.locator('#kc-header');
  const main = page.locator('.pf-v5-c-login__main');
  const [headerBox, mainBox] = await Promise.all([
    requiredBox(header, 'Header do login'),
    requiredBox(main, 'Corpo do login'),
  ]);

  const x = Math.floor(Math.min(headerBox.x, mainBox.x));
  const y = Math.floor(Math.min(headerBox.y, mainBox.y));
  const right = Math.ceil(
    Math.max(headerBox.x + headerBox.width, mainBox.x + mainBox.width),
  );
  const bottom = Math.ceil(
    Math.max(headerBox.y + headerBox.height, mainBox.y + mainBox.height),
  );

  return page.screenshot({
    animations: 'disabled',
    caret: 'hide',
    clip: {
      x,
      y,
      width: right - x,
      height: bottom - y,
    },
  });
}
```

- [ ] **Step 2: Adicionar a assertion visual ao teste de erro**

Ao fim do teste `mantém erro legível e controles alinhados dentro do card`,
adicionar:

```ts
  expect(await screenshotLoginCard(page)).toMatchSnapshot(
    'invalid-credentials-card.png',
    { maxDiffPixelRatio: 0.001 },
  );
```

- [ ] **Step 3: Confirmar que os baselines ainda não existem**

Run no ambiente Linux pinado, sem `CI=true` para que retries não absorvam a
falha esperada. Executar a partir de `apps/frontend/`:

```powershell
docker run --rm --network host `
  -v "${PWD}:/work" `
  -w /work `
  -e BLACKICE_E2E_URL=http://blackice.localhost `
  mcr.microsoft.com/playwright:v1.62.0-noble `
  npx playwright test e2e/keycloak-login.spec.ts --grep "mantém erro"
```

Expected: FAIL informando que os snapshots não existem e escrevendo as imagens
recebidas; a geometria continua verde.

- [ ] **Step 4: Gerar os dois baselines no ambiente Linux pinado**

Com a stack acessível pela rede do host, executar a partir de
`apps/frontend/`:

```powershell
docker run --rm --network host `
  -v "${PWD}:/work" `
  -w /work `
  -e CI=true `
  -e BLACKICE_E2E_URL=http://blackice.localhost `
  mcr.microsoft.com/playwright:v1.62.0-noble `
  npx playwright test e2e/keycloak-login.spec.ts --grep "mantém erro" --update-snapshots
```

Expected: dois PNGs criados nos caminhos declarados em **Files**, um por
projeto, e os dois casos passam.

- [ ] **Step 5: Provar que os baselines passam sem atualização**

Run:

```powershell
docker run --rm --network host `
  -v "${PWD}:/work" `
  -w /work `
  -e CI=true `
  -e BLACKICE_E2E_URL=http://blackice.localhost `
  mcr.microsoft.com/playwright:v1.62.0-noble `
  npx playwright test e2e/keycloak-login.spec.ts
```

Expected: quatro testes passam sem escrever ou atualizar snapshots.

- [ ] **Step 6: Documentar execução local e CI**

Acrescentar a `apps/frontend/README.md`:

````markdown
## E2E do tema de login do Keycloak

Pré-condição: a stack completa deve estar ativa pelos três arquivos Compose
canônicos descritos no README raiz.

Instale o Chromium compatível e execute:

```powershell
mise exec -- npx playwright install chromium
mise exec -- npm run test:e2e:keycloak
```

O alvo padrão é `http://blackice.localhost`. Para outro ambiente:

```powershell
$env:BLACKICE_E2E_URL='https://blackice.example.test'
mise exec -- npm run test:e2e:keycloak
```

### Contrato para qualquer CI

O job deve:

1. subir a stack;
2. executar `npm ci`;
3. executar `npx playwright install --with-deps chromium`;
4. definir `CI=true`;
5. executar `npm run test:e2e:keycloak`;
6. publicar `playwright-report/` e `test-results/playwright/` em falhas;
7. derrubar a stack no cleanup do próprio job.

Os snapshots são baselines Linux. Gere atualizações somente na imagem
`mcr.microsoft.com/playwright:v1.62.0-noble`, usando
`--update-snapshots`, e revise os PNGs antes de versioná-los.
````

- [ ] **Step 7: Executar a verificação completa**

Run:

```powershell
cd apps/frontend
mise exec -- npm test
mise exec -- npm run build
docker run --rm --network host `
  -v "${PWD}:/work" `
  -w /work `
  -e CI=true `
  -e BLACKICE_E2E_URL=http://blackice.localhost `
  mcr.microsoft.com/playwright:v1.62.0-noble `
  npx playwright test e2e/keycloak-login.spec.ts
```

Expected: Vitest, build e os quatro E2E passam; saída sem warnings novos.

- [ ] **Step 8: Revisar artefatos e não criar commit**

Run:

```powershell
git diff --check
git status --short
```

Expected: apenas spec, plano, configuração/teste Playwright, lockfile,
documentação, dois PNGs e a correção CSS pertencentes a este trabalho, além de
eventuais mudanças preexistentes do usuário. Não executar `git add` nem
`git commit`.
