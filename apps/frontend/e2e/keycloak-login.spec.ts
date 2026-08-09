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

  // O entregável do spec 2026-08-07 é a própria URL: same-origin, sob /auth,
  // no realm blackice. Sem esta asserção nada no E2E pega uma regressão que
  // devolva o login para https://localhost:8843/realms/dcm4chee/.
  await expect(page).toHaveURL(
    /^http:\/\/blackice\.localhost\/auth\/realms\/blackice\//,
  );
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

test('mantém foco acessível sem outline interno duplicado', async ({ page }) => {
  await openBlackiceLogin(page);

  const username = page.getByLabel('Usuário', { exact: true });
  const password = page.getByLabel('Senha', { exact: true });
  const usernameControl = username.locator('..');
  const passwordControl = password.locator('..');

  await expect(username).toBeFocused();
  await expect(username).toHaveCSS('outline-style', 'none');
  await expect(usernameControl).toHaveCSS(
    'border-top-color',
    'rgb(86, 200, 232)',
  );
  await expect(usernameControl).not.toHaveCSS('box-shadow', 'none');

  await username.press('Tab');
  await expect(password).toBeFocused();
  await expect(password).toHaveCSS('outline-style', 'none');
  await expect(passwordControl).toHaveCSS(
    'border-top-color',
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
  expect(await screenshotLoginCard(page)).toMatchSnapshot(
    'invalid-credentials-card.png',
    { maxDiffPixelRatio: 0.001 },
  );
});
