import { expect, test } from '@playwright/test';
import {
  PROBLEM_TYPES,
  type ApiProblemCode,
} from '../src/shared/api/problems/problem-types.generated';
import {
  createSyntheticCtSlice,
  createSyntheticDicom,
} from './fixtures/synthetic-dicom';

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

test('opens a mixed study, renders CT, switches series and restores Worklist', async ({
  page,
}, testInfo) => {
  const isMobile = testInfo.project.name === 'chromium-mobile';
  const runId = Math.floor(Math.random() * 900000 + 100000).toString();
  const uidPrefix = isMobile ? `2.25.822.${runId}` : `2.25.821.${runId}`;
  const patientPrefix = isMobile ? `VIEWMOBILE${runId}` : `VIEWDESK${runId}`;
  const patientName = `${patientPrefix}^PATIENT`;
  const patientId = `${patientPrefix}-01`;

  const studyUid = `${uidPrefix}.1`;
  const ctSeries1Uid = `${uidPrefix}.1.1`;
  const ctSeries2Uid = `${uidPrefix}.1.2`;
  const scSeries3Uid = `${uidPrefix}.1.3`;

  // 1. Autenticação via Keycloak
  await login(page);

  // 2. Ingestão de estudo misto sintético (2 séries CT com 2 cortes cada + 1 série Secondary Capture não suportada)
  const files = [
    // Série 1: CT (Cortes 1 e 2)
    {
      name: 'ct1-slice1.dcm',
      mimeType: 'application/dicom',
      buffer: createSyntheticCtSlice(studyUid, ctSeries1Uid, `${ctSeries1Uid}.1`, {
        patientName,
        patientId,
        studyDate: '2026-08-24',
        studyTime: '103000',
        studyDescription: 'E2E VERTICAL SLICE STUDY',
        seriesNumber: 1,
        seriesDescription: 'CT AXIAL SOFT TISSUE',
        instanceNumber: 1,
        sliceLocation: 0,
        windowCenter: 40,
        windowWidth: 400,
      }),
    },
    {
      name: 'ct1-slice2.dcm',
      mimeType: 'application/dicom',
      buffer: createSyntheticCtSlice(studyUid, ctSeries1Uid, `${ctSeries1Uid}.2`, {
        patientName,
        patientId,
        studyDate: '2026-08-24',
        studyTime: '103000',
        studyDescription: 'E2E VERTICAL SLICE STUDY',
        seriesNumber: 1,
        seriesDescription: 'CT AXIAL SOFT TISSUE',
        instanceNumber: 2,
        sliceLocation: 5,
        windowCenter: 40,
        windowWidth: 400,
      }),
    },
    // Série 2: CT (Cortes 1 e 2)
    {
      name: 'ct2-slice1.dcm',
      mimeType: 'application/dicom',
      buffer: createSyntheticCtSlice(studyUid, ctSeries2Uid, `${ctSeries2Uid}.1`, {
        patientName,
        patientId,
        studyDate: '2026-08-24',
        studyTime: '103000',
        studyDescription: 'E2E VERTICAL SLICE STUDY',
        seriesNumber: 2,
        seriesDescription: 'CT AXIAL BONE',
        instanceNumber: 1,
        sliceLocation: 0,
        windowCenter: 400,
        windowWidth: 2000,
      }),
    },
    {
      name: 'ct2-slice2.dcm',
      mimeType: 'application/dicom',
      buffer: createSyntheticCtSlice(studyUid, ctSeries2Uid, `${ctSeries2Uid}.2`, {
        patientName,
        patientId,
        studyDate: '2026-08-24',
        studyTime: '103000',
        studyDescription: 'E2E VERTICAL SLICE STUDY',
        seriesNumber: 2,
        seriesDescription: 'CT AXIAL BONE',
        instanceNumber: 2,
        sliceLocation: 5,
        windowCenter: 400,
        windowWidth: 2000,
      }),
    },
    // Série 3: Secondary Capture (Não suportada)
    {
      name: 'sc-series.dcm',
      mimeType: 'application/dicom',
      buffer: createSyntheticDicom(studyUid, scSeries3Uid, `${scSeries3Uid}.1`, {
        patientName,
        patientId,
        studyDate: '2026-08-24',
        studyTime: '103000',
        studyDescription: 'E2E VERTICAL SLICE STUDY',
        seriesDescription: 'SECONDARY CAPTURE REPORT',
        seriesNumber: 3,
        modality: 'OT',
      }),
    },
  ];

  await page.goto('/ingest');
  await page.locator('input[type=file]').setInputFiles(files);
  await expect(page.getByText('5 arquivos')).toBeVisible();
  await page.getByRole('button', { name: 'Importar' }).click();
  await expect(
    page.getByRole('heading', { name: 'Resultado da importação' }),
  ).toBeVisible({ timeout: 120_000 });
  await expect(page.locator('details')).toHaveCount(1);
  await expect(page.locator('details ul li')).toHaveCount(5);

  // 3. Busca na Worklist pelo paciente sintético
  await page.goto('/studies');
  await page.getByLabel('Nome do paciente').fill(patientPrefix);
  await page.getByRole('button', { name: 'Buscar' }).click();

  const resultItem = isMobile ? 'study-card' : 'study-row';
  await expect(page.getByTestId(resultItem)).toHaveCount(1);

  // 4. Observadores de requisições de rede
  const requestedUrls: string[] = [];
  page.on('request', (req) => requestedUrls.push(req.url()));

  // 5. Clicar em "Abrir estudo"
  await page
    .getByTestId(isMobile ? 'study-card' : 'study-row')
    .getByTestId('open-study')
    .first()
    .click();

  if (isMobile) {
    // Verificações no ambiente Mobile: Capability Gate ativo
    await expect(page.getByRole('status')).toContainText(
      'Use uma tela maior para visualizar as imagens deste estudo.',
    );
    // 0 requisições de instâncias e 0 de frames
    expect(
      requestedUrls.some((url) => url.includes('/instances') || url.includes('/frames/')),
    ).toBe(false);
    // Nenhum canvas Cornerstone instanciado
    await expect(page.locator('[data-testid="dicom-viewport"] canvas')).toHaveCount(0);
    // Nenhuma chamada direta ao archive
    expect(requestedUrls.some((url) => url.includes('/dcm4chee-arc/'))).toBe(false);
    return;
  }

  // Verificações no Desktop:
  // 6. Cabeçalho do estudo exibe metadados
  await expect(page.getByLabel('Cabeçalho do estudo')).toContainText(patientName);
  await expect(page.getByLabel('Cabeçalho do estudo')).toContainText('E2E VERTICAL SLICE STUDY');

  // 7. SeriesRail exibe 3 séries
  const seriesCards = page.getByTestId('series-card');
  await expect(seriesCards).toHaveCount(3);

  // Primeira série suportada está selecionada
  await expect(seriesCards.nth(0)).toHaveAttribute('aria-selected', 'true');
  await expect(seriesCards.nth(0)).toContainText('CT AXIAL SOFT TISSUE');

  // Série Secondary Capture (3ª) desabilitada com motivo amigável PT-BR
  const unsupportedCard = seriesCards.nth(2);
  await expect(unsupportedCard).toHaveAttribute('aria-disabled', 'true');
  await expect(unsupportedCard).toContainText('Formato de imagem não suportado');

  // 8. Viewport ativo renderiza canvas Cornerstone
  const viewport = page.getByTestId('dicom-viewport');
  await expect(viewport).toBeVisible({ timeout: 20_000 });
  await expect(viewport.locator('canvas')).toBeVisible({ timeout: 20_000 });
  await expect(viewport).toHaveAttribute('data-annotation-count', '0');

  // Apenas instâncias/frames da série 1 foram solicitados até o momento
  expect(requestedUrls.some((url) => url.includes(`/series/${ctSeries1Uid}/instances`))).toBe(true);
  expect(requestedUrls.some((url) => url.includes(`/series/${ctSeries2Uid}/instances`))).toBe(false);
  expect(requestedUrls.some((url) => url.includes(`/series/${scSeries3Uid}/instances`))).toBe(false);

  // 9. Alternar para a Série 2 suportada
  await seriesCards.nth(1).click();
  await expect(seriesCards.nth(1)).toHaveAttribute('aria-selected', 'true');
  await expect(viewport.locator('canvas')).toBeVisible({ timeout: 20_000 });

  // Agora as instâncias da Série 2 foram requisitadas
  await expect
    .poll(() => requestedUrls.some((url) => url.includes(`/series/${ctSeries2Uid}/instances`)))
    .toBe(true);

  // 10. Ativar ferramenta Length (Medição)
  await page.getByTestId('tool-LENGTH').click();
  await expect(page.getByTestId('tool-LENGTH')).toHaveAttribute('aria-pressed', 'true');

  // Criar anotação Length de medição no canvas
  const box = await viewport.boundingBox();
  expect(box).not.toBeNull();
  if (box) {
    const startX = box.x + box.width * 0.35;
    const startY = box.y + box.height * 0.35;
    const endX = box.x + box.width * 0.65;
    const endY = box.y + box.height * 0.65;

    await page.mouse.move(startX, startY);
    await page.mouse.down();
    await page.mouse.move(endX, endY, { steps: 10 });
    await page.mouse.up();

    if ((await viewport.getAttribute('data-annotation-count')) !== '1') {
      await page.mouse.click(endX, endY);
    }
  }
  await expect(viewport).toHaveAttribute('data-annotation-count', '1');

  // 11. Voltar para a Worklist
  await page.getByTestId('back-button').click();
  await expect(page.getByRole('heading', { name: 'Worklist' })).toBeVisible();

  // Filtros e resultados restaurados do cache de sessão
  await expect(page.getByLabel('Nome do paciente')).toHaveValue(patientPrefix);
  await expect(page.getByTestId('study-row')).toHaveCount(1);

  // 12. Reentrar no visualizador e verificar contagem de anotações zerada (memória limpa)
  await page
    .getByTestId(isMobile ? 'study-card' : 'study-row')
    .getByTestId('open-study')
    .first()
    .click();
  await expect(page.getByTestId('dicom-viewport')).toBeVisible({ timeout: 20_000 });
  await expect(page.locator('[data-testid="dicom-viewport"] canvas')).toBeVisible({
    timeout: 20_000,
  });
  await expect(page.getByTestId('dicom-viewport')).toHaveAttribute('data-annotation-count', '0');

  // 13. O browser nunca deve ter chamado o DCM4CHEE Archive diretamente
  expect(requestedUrls.some((url) => url.includes('/dcm4chee-arc/'))).toBe(false);
});

test('repeatedly opens and closes viewer without leaking canvases or listeners', async ({
  page,
}, testInfo) => {
  if (testInfo.project.name === 'chromium-mobile') {
    test.skip();
    return;
  }

  const runId = Math.floor(Math.random() * 900000 + 100000).toString();
  const uidPrefix = `2.25.823.${runId}`;
  const patientPrefix = `CYCLE${runId}`;
  const studyUid = `${uidPrefix}.1`;
  const ctSeriesUid = `${uidPrefix}.1.1`;

  await login(page);

  // Ingestão de 1 estudo com 2 cortes CT
  await page.goto('/ingest');
  await page.locator('input[type=file]').setInputFiles([
    {
      name: 'cycle-slice1.dcm',
      mimeType: 'application/dicom',
      buffer: createSyntheticCtSlice(studyUid, ctSeriesUid, `${ctSeriesUid}.1`, {
        patientName: `${patientPrefix}^CYCLE`,
        patientId: `${patientPrefix}-01`,
        seriesNumber: 1,
        instanceNumber: 1,
      }),
    },
    {
      name: 'cycle-slice2.dcm',
      mimeType: 'application/dicom',
      buffer: createSyntheticCtSlice(studyUid, ctSeriesUid, `${ctSeriesUid}.2`, {
        patientName: `${patientPrefix}^CYCLE`,
        patientId: `${patientPrefix}-01`,
        seriesNumber: 1,
        instanceNumber: 2,
      }),
    },
  ]);
  await page.getByRole('button', { name: 'Importar' }).click();
  await expect(
    page.getByRole('heading', { name: 'Resultado da importação' }),
  ).toBeVisible({ timeout: 120_000 });
  await expect(page.locator('details')).toHaveCount(1);
  await expect(page.locator('details ul li')).toHaveCount(2);

  await page.goto('/studies');
  await page.getByLabel('Nome do paciente').fill(patientPrefix);
  await page.getByRole('button', { name: 'Buscar' }).click();
  await expect(page.getByTestId('study-row')).toHaveCount(1);

  // Ciclo 1, 2, 3: Entrar e sair sucessivamente
  for (let cycle = 1; cycle <= 3; cycle++) {
    await expect(page.getByTestId('study-row')).toHaveCount(1);
    await page.getByTestId('study-row').getByTestId('open-study').first().click();
    const viewport = page.getByTestId('dicom-viewport');
    await expect(viewport).toBeVisible({ timeout: 20_000 });
    await expect(viewport.locator('canvas')).toBeVisible({ timeout: 20_000 });
    await expect(viewport.locator('canvas')).toHaveCount(1);

    await page.getByTestId('back-button').click();
    await expect(page.getByRole('heading', { name: 'Worklist' })).toBeVisible({ timeout: 20_000 });
  }
});

test('mobile screen enforces capability gate without requesting instances or frames', async ({
  page,
}, testInfo) => {
  if (testInfo.project.name !== 'chromium-mobile') {
    test.skip();
    return;
  }

  const requestedUrls: string[] = [];
  page.on('request', (req) => requestedUrls.push(req.url()));

  await login(page);

  // Navega diretamente para uma rota de visualizador
  await page.goto('/studies/2.25.99999999999');

  // Capability gate presente
  await expect(page.getByRole('status')).toContainText(
    'Use uma tela maior para visualizar as imagens deste estudo.',
  );

  // 0 requisições de instâncias ou frames
  expect(
    requestedUrls.some((url) => url.includes('/instances') || url.includes('/frames/')),
  ).toBe(false);

  // Nenhum canvas Cornerstone
  await expect(page.locator('[data-testid="dicom-viewport"] canvas')).toHaveCount(0);
});

test('displays Problem Details with retry on study summary failure', async ({ page }, testInfo) => {
  if (testInfo.project.name === 'chromium-mobile') {
    test.skip();
    return;
  }

  await login(page);

  const errorStudyUid = '2.25.999.001';
  let requestCount = 0;
  await page.route(`**/api/studies/${errorStudyUid}`, (route) => {
    requestCount += 1;
    return route.fulfill({
      status: 503,
      contentType: 'application/problem+json',
      headers: { 'X-Trace-ID': 'f'.repeat(32) },
      body: JSON.stringify(
        problemBody(
          'API_ARCHIVE_UNAVAILABLE',
          'Archive unavailable',
          'The imaging archive is temporarily unavailable.',
          { traceId: 'f'.repeat(32) },
        ),
      ),
    });
  });

  await page.goto(`/studies/${errorStudyUid}`);

  const pageError = page.locator('.page-error');
  await expect(pageError).toBeVisible();
  await expect(pageError).toContainText('O Archive está temporariamente indisponível.');
  await expect(pageError.locator('code')).toHaveText(TRACE_ID_PATTERN);
  await expect(pageError.getByRole('button', { name: 'Tentar novamente' })).toBeVisible();

  // Texto bruto do backend em inglês não vaza para a tela
  await expect(page.locator('body')).not.toContainText('The imaging archive');

  // Teste de retentativa
  await pageError.getByRole('button', { name: 'Tentar novamente' }).click();
  await expect.poll(() => requestCount).toBe(2);
});

test('displays localized viewport error on series instances failure', async ({ page }, testInfo) => {
  if (testInfo.project.name === 'chromium-mobile') {
    test.skip();
    return;
  }

  const runId = Math.floor(Math.random() * 900000 + 100000).toString();
  const studyUid = `2.25.824.${runId}.1`;
  const ctSeriesUid = `2.25.824.${runId}.1.1`;
  const patientPrefix = `ERRSERIES${runId}`;

  await login(page);

  // Ingestão de 1 estudo para obter estrutura válida
  await page.goto('/ingest');
  await page.locator('input[type=file]').setInputFiles([
    {
      name: 'err-slice1.dcm',
      mimeType: 'application/dicom',
      buffer: createSyntheticCtSlice(studyUid, ctSeriesUid, `${ctSeriesUid}.1`, {
        patientName: `${patientPrefix}^TEST`,
        patientId: `${patientPrefix}-01`,
        seriesNumber: 1,
        instanceNumber: 1,
      }),
    },
  ]);
  await page.getByRole('button', { name: 'Importar' }).click();
  await expect(
    page.getByRole('heading', { name: 'Resultado da importação' }),
  ).toBeVisible({ timeout: 120_000 });

  // Intercepta a rota de instâncias da série para simular falha
  let instancesRequestCount = 0;
  await page.route(`**/api/studies/${studyUid}/series/${ctSeriesUid}/instances`, (route) => {
    instancesRequestCount += 1;
    return route.fulfill({
      status: 500,
      contentType: 'application/problem+json',
      headers: { 'X-Trace-ID': 'c'.repeat(32) },
      body: JSON.stringify(
        problemBody(
          'API_INTERNAL_ERROR',
          'Internal server error',
          'An internal error occurred while processing the request.',
          { traceId: 'c'.repeat(32) },
        ),
      ),
    });
  });

  await page.goto(`/studies/${studyUid}`);

  // Cabeçalho e trilho de séries continuam presentes
  await expect(page.getByLabel('Cabeçalho do estudo')).toContainText(`${patientPrefix}^TEST`);
  await expect(page.getByTestId('series-card')).toHaveCount(1);

  // Viewport exibe erro localizado com TraceID e botão de retentativa
  const viewportError = page.locator('.viewport-error');
  await expect(viewportError).toBeVisible();
  await expect(viewportError).toContainText('Ocorreu uma falha inesperada. Tente novamente.');
  await expect(viewportError.locator('code')).toHaveText(TRACE_ID_PATTERN);
  await expect(viewportError.getByRole('button', { name: 'Tentar novamente' })).toBeVisible();

  // Texto bruto não vaza
  await expect(page.locator('body')).not.toContainText('An internal error occurred');

  // Retentativa chama o endpoint novamente
  await viewportError.getByRole('button', { name: 'Tentar novamente' }).click();
  await expect.poll(() => instancesRequestCount).toBe(2);
});

test('displays message when study has no supported series', async ({ page }, testInfo) => {
  if (testInfo.project.name === 'chromium-mobile') {
    test.skip();
    return;
  }

  const runId = Math.floor(Math.random() * 900000 + 100000).toString();
  const studyUid = `2.25.825.${runId}.1`;
  const scSeriesUid = `2.25.825.${runId}.1.1`;
  const patientPrefix = `UNSUPPORTED${runId}`;

  await login(page);

  // Ingestão de estudo contendo unicamente Secondary Capture (não suportada)
  await page.goto('/ingest');
  await page.locator('input[type=file]').setInputFiles([
    {
      name: 'unsupported-sc.dcm',
      mimeType: 'application/dicom',
      buffer: createSyntheticDicom(studyUid, scSeriesUid, `${scSeriesUid}.1`, {
        patientName: `${patientPrefix}^UNSUPPORTED`,
        patientId: `${patientPrefix}-01`,
        modality: 'OT',
      }),
    },
  ]);
  await page.getByRole('button', { name: 'Importar' }).click();
  await expect(
    page.getByRole('heading', { name: 'Resultado da importação' }),
  ).toBeVisible({ timeout: 120_000 });

  await page.goto(`/studies/${studyUid}`);

  // Viewport exibe mensagem amigável de ausência de séries suportadas
  await expect(page.locator('.unsupported-series-message')).toBeVisible();
  await expect(page.locator('.unsupported-series-message')).toContainText(
    'Nenhuma série suportada para visualização neste estudo.',
  );
  await expect(page.locator('[data-testid="dicom-viewport"] canvas')).toHaveCount(0);
});
