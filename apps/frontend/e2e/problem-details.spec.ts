import { expect, test } from '@playwright/test';

import {
  PROBLEM_TYPES,
  type ApiProblemCode,
} from '../src/shared/api/problems/problem-types.generated';
import { createSyntheticDicom } from './fixtures/synthetic-dicom';

/**
 * Fronteira de erro ponta a ponta.
 *
 * <p>Verifica o que o usuário realmente vê quando algo falha: mensagem em
 * português vinda do mapa central, TraceID como referência, retentativa apenas
 * quando ajuda, e nenhum texto interno do backend na tela.
 */

const TRACE_ID_PATTERN = /^[0-9a-f]{32}$/;

function problemBody(
  code: ApiProblemCode,
  title: string,
  detail: string,
  extensions: Record<string, unknown> = {},
): Record<string, unknown> {
  const definition = PROBLEM_TYPES[code];
  return {
    type: definition.type,
    title,
    status: definition.httpStatus,
    detail,
    code,
    ...extensions,
  };
}

async function login(page: import('@playwright/test').Page): Promise<void> {
  await page.goto('/');
  await page.getByLabel('Usuário', { exact: true }).fill('dr.teste');
  await page.getByLabel('Senha', { exact: true }).fill('teste123');
  await page.getByRole('button', { name: 'Entrar' }).click();
  await page.waitForURL((url) => !url.pathname.startsWith('/realms/'));
}

test('toda resposta de erro sob /api é Problem Details correlacionado', async ({ request }) => {
  const response = await request.get('/api/me', {
    headers: { traceparent: '00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01' },
    maxRedirects: 0,
  });

  expect(response.status()).toBe(401);
  expect(response.headers()['content-type']).toContain('application/problem+json');

  const problem = (await response.json()) as Record<string, unknown>;
  expect(problem.code).toBe('API_AUTHENTICATION_REQUIRED');
  expect(problem.status).toBe(401);
  expect(problem.type).toBe(PROBLEM_TYPES.API_AUTHENTICATION_REQUIRED.type);
  expect(problem.traceId).toBe('4bf92f3577b34da6a3ce929d0e0e4736');
  expect(response.headers()['x-trace-id']).toBe('4bf92f3577b34da6a3ce929d0e0e4736');
  expect(problem.instance).toBeUndefined();
  expect(problem.retryPolicy).toBeUndefined();
  expect(response.headers()['x-request-id']).toBeUndefined();
});

test('uma rota /api inexistente também é catalogada', async ({ request }) => {
  const response = await request.get('/api/rota-que-nao-existe', { maxRedirects: 0 });

  expect(response.status()).toBe(404);
  expect(response.headers()['content-type']).toContain('application/problem+json');
  expect((await response.json()).code).toBe('API_RESOURCE_NOT_FOUND');
});

test('a navegação explícita para /api/login continua iniciando o OIDC', async ({ request }) => {
  const response = await request.get('/api/login', { maxRedirects: 0 });

  expect([302, 303]).toContain(response.status());
  expect(response.headers()['content-type'] ?? '').not.toContain('application/problem+json');
  const location = response.headers()['location'];
  expect(location).toBeTruthy();
  expect(new URL(location!, response.url()).pathname).toBe(
    '/auth/realms/blackice/protocol/openid-connect/auth',
  );
});

test('a Worklist mostra mensagem PT-BR, TraceID e retentativa manual', async ({ page }) => {
  await login(page);

  // Intercepta a busca com um problema catalogado real do Archive.
  let requestCount = 0;
  await page.route('**/api/studies?*', (route) => {
    requestCount += 1;
    return route.fulfill({
      status: 503,
      contentType: 'application/problem+json',
      headers: { 'X-Trace-ID': 'a'.repeat(32) },
      body: JSON.stringify(
        problemBody(
          'API_ARCHIVE_UNAVAILABLE',
          'Archive unavailable',
          'The imaging archive is temporarily unavailable.',
          { traceId: 'a'.repeat(32) },
        ),
      ),
    });
  });

  await page.goto('/studies');
  const alert = page.getByRole('alert');

  await expect(alert).toContainText('O Archive está temporariamente indisponível.');
  await expect(alert.getByRole('button', { name: 'Tentar novamente' })).toBeVisible();
  await expect(alert.locator('code')).toHaveText(TRACE_ID_PATTERN);

  // O texto do operador, em inglês, nunca chega à tela.
  await expect(page.locator('body')).not.toContainText('The imaging archive');

  await alert.getByRole('button', { name: 'Tentar novamente' }).click();
  await expect.poll(() => requestCount).toBe(2);
});

test('uma busca inválida não oferece retentativa', async ({ page }) => {
  await login(page);

  await page.route('**/api/studies?*', (route) =>
    route.fulfill({
      status: 400,
      contentType: 'application/problem+json',
      headers: { 'X-Trace-ID': 'd'.repeat(32) },
      body: JSON.stringify(
        problemBody(
          'API_SEARCH_INVALID',
          'Invalid search',
          'Review the supplied search filters.',
          { traceId: 'd'.repeat(32) },
        ),
      ),
    }),
  );

  await page.goto('/studies');
  const alert = page.getByRole('alert');

  await expect(alert).toContainText('Revise os filtros de busca informados.');
  await expect(alert.getByRole('button', { name: 'Tentar novamente' })).toHaveCount(0);
});

test('a ingestão lista violações do 422 pelo arquivo local, sem filename do backend', async ({
  page,
}) => {
  await login(page);

  await page.route('**/api/studies', async (route) => {
    if (route.request().method() !== 'POST') return route.fallback();
    return route.fulfill({
      status: 422,
      contentType: 'application/problem+json',
      headers: { 'X-Trace-ID': 'b'.repeat(32) },
      body: JSON.stringify(
        problemBody(
          'API_DICOM_VALIDATION_FAILED',
          'DICOM validation failed',
          'None of the uploaded files passed validation.',
          {
            traceId: 'b'.repeat(32),
            violations: [
              { itemIndex: 0, code: 'MALFORMED_DICOM', message: 'The file is not valid DICOM.' },
            ],
          },
        ),
      ),
    });
  });

  await page.goto('/ingest');
  await page.locator('input[type="file"]').setInputFiles([
    {
      name: 'exame-local.dcm',
      mimeType: 'application/dicom',
      buffer: createSyntheticDicom('2.25.900.1', '2.25.900.1.1', '2.25.900.1.1.1', {
        patientName: 'PROBLEM^DETAILS',
        patientId: 'PROBLEM-001',
        modality: 'OT',
      }),
    },
  ]);
  await page.getByRole('button', { name: 'Importar' }).click();

  const alert = page.getByRole('alert');
  await expect(alert).toContainText('Nenhum arquivo enviado passou pela validação DICOM.');

  // O nome vem da seleção local, não da resposta.
  await expect(page.getByTestId('ingest-violations')).toContainText('exame-local.dcm');
  await expect(alert.locator('code')).toHaveText(TRACE_ID_PATTERN);
  await expect(page.locator('body')).not.toContainText(
    'None of the uploaded files passed validation.',
  );
});

test('a ingestão não oferece reenvio quando o resultado do Archive é incerto', async ({ page }) => {
  await login(page);

  const traceId = 'e'.repeat(32);
  await page.route('**/api/studies', async (route) => {
    if (route.request().method() !== 'POST') return route.fallback();
    return route.fulfill({
      status: 502,
      contentType: 'application/problem+json',
      headers: { 'X-Trace-ID': traceId },
      body: JSON.stringify({
        type: PROBLEM_TYPES.API_ARCHIVE_OUTCOME_UNKNOWN.type,
        title: 'Archive outcome unknown',
        status: 502,
        detail: 'The imaging archive outcome could not be confirmed.',
        code: 'API_ARCHIVE_OUTCOME_UNKNOWN',
        traceId,
      }),
    });
  });

  await page.goto('/ingest');
  await page.locator('input[type="file"]').setInputFiles([
    {
      name: 'resultado-incerto.dcm',
      mimeType: 'application/dicom',
      buffer: createSyntheticDicom('2.25.901.1', '2.25.901.1.1', '2.25.901.1.1.1'),
    },
  ]);
  await page.getByRole('button', { name: 'Importar' }).click();

  const alert = page.getByRole('alert');
  await expect(alert).toContainText(
    'Não foi possível confirmar o resultado no Archive. Use a referência exibida para solicitar verificação.',
  );
  await expect(alert.locator('code')).toHaveText(traceId);
  await expect(page.locator('body')).not.toContainText(
    'The imaging archive outcome could not be confirmed.',
  );
  await expect(alert.getByRole('button', { name: 'Tentar novamente' })).toHaveCount(0);
});

test('sessão expirada leva ao login sem tratar o 401 como falha da API', async ({ page }) => {
  await page.route('**/api/me', (route) =>
    route.fulfill({
      status: 401,
      contentType: 'application/problem+json',
      headers: { 'X-Trace-ID': 'c'.repeat(32) },
      body: JSON.stringify(
        problemBody(
          'API_AUTHENTICATION_REQUIRED',
          'Authentication required',
          'Authentication is required to access this resource.',
          { traceId: 'c'.repeat(32) },
        ),
      ),
    }),
  );

  const loginRequest = page.waitForRequest((request) =>
    new URL(request.url()).pathname.endsWith('/api/login'),
  );
  await page.goto('/studies');

  // Ausência de sessão não é erro na tela: é redirecionamento para entrar.
  expect(new URL((await loginRequest).url()).pathname).toBe('/api/login');
  await expect(page.getByRole('alert')).toHaveCount(0);
});
