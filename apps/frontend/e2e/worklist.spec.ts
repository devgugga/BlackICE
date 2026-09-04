import { expect, test } from '@playwright/test';
import { randomUUID } from 'node:crypto';
import { createSyntheticDicom } from './fixtures/synthetic-dicom';

function createDicomUid(): string {
  const uuidAsInteger = BigInt(`0x${randomUUID().replaceAll('-', '')}`);
  return `2.25.${uuidAsInteger}`;
}

test('ingests 21 studies and verifies worklist search, pagination, and layout', async ({
  page,
}, testInfo) => {
  const requested: string[] = [];
  page.on('request', (request) => requested.push(request.url()));

  const isMobile = testInfo.project.name === 'chromium-mobile';
  const uidPrefix = isMobile ? '2.25.802' : '2.25.801';
  const patientPrefix = isMobile ? 'WORKLISTMOBILE' : 'WORKLISTDESKTOP';

  // 1. Autenticação via Keycloak
  await page.goto('/');
  await page.getByLabel('Usuário', { exact: true }).fill('dr.teste');
  await page.getByLabel('Senha', { exact: true }).fill('teste123');
  await page.getByRole('button', { name: 'Entrar' }).click();

  // 2. Ingestão de 21 estudos sintéticos com prefixo específico por projeto
  await page.goto('/ingest');
  const files = Array.from({ length: 21 }, (_, index) => {
    const num = index + 1;
    const caseNum = String(num).padStart(2, '0');
    const studyUid = `${uidPrefix}.${num}`;
    const seriesUid = `${uidPrefix}.${num}.1`;
    const sopUid = `${uidPrefix}.${num}.1.1`;
    const isSpecialCase = num === 1;

    return {
      name: `study-${caseNum}.dcm`,
      mimeType: 'application/dicom',
      buffer: createSyntheticDicom(studyUid, seriesUid, sopUid, {
        patientName: `${patientPrefix}^CASE${caseNum}`,
        patientId: `${patientPrefix}-${caseNum}`,
        patientIdIssuer: isSpecialCase ? 'HOSPITAL-TEST' : undefined,
        studyDate: '2026-08-22',
        studyTime: '120000',
        modality: 'OT',
        studyDescription: isSpecialCase
          ? 'WORKLIST E2E TEST STUDY'
          : `STUDY ${caseNum}`,
      }),
    };
  });

  await page.locator('input[type=file]').setInputFiles(files);
  await expect(page.getByText('21 arquivos')).toBeVisible();
  await page.getByRole('button', { name: 'Importar' }).click();
  await expect(
    page.getByRole('heading', { name: 'Resultado da importação' }),
  ).toBeVisible();
  await expect(page.locator('details')).toHaveCount(21);

  // 3. Navegação para a worklist e busca pelo prefixo do paciente
  await page.goto('/studies');
  await page.getByLabel('Nome do paciente').fill(patientPrefix);
  await page.getByRole('button', { name: 'Buscar' }).click();

  // 4. Asserção da paginação 20/1
  const resultItem =
    testInfo.project.name === 'chromium-mobile' ? 'study-card' : 'study-row';
  await expect(page.getByTestId(resultItem)).toHaveCount(20);
  await expect(page.getByRole('button', { name: 'Próxima' })).toBeEnabled();
  await page.getByRole('button', { name: 'Próxima' }).click();
  await expect(page.getByTestId(resultItem)).toHaveCount(1);
  await expect(page.getByRole('button', { name: 'Anterior' })).toBeEnabled();

  // 5. Filtro específico por ID do paciente, modalidade OT e intervalo de datas
  await page.getByLabel('Nome do paciente').fill('');
  await page.getByLabel('ID do paciente').fill(`${patientPrefix}-01`);
  await page.getByLabel('Modalidade').selectOption('OT');
  await page.getByLabel('Data inicial').fill('2026-08-22');
  await page.getByLabel('Data final').fill('2026-08-22');
  await page.getByRole('button', { name: 'Buscar' }).click();

  // 6. Asserção dos metadados do estudo filtrado
  await expect(page.getByTestId(resultItem)).toHaveCount(1);
  const singleResult = page.getByTestId(resultItem).first();
  await expect(singleResult).toContainText('HOSPITAL-TEST');
  await expect(singleResult).toContainText('WORKLIST E2E TEST STUDY');
  await expect(singleResult).toContainText('1 séries · 1 instâncias');

  // 7. Asserção de responsividade desktop vs mobile
  if (isMobile) {
    await expect(page.getByTestId('study-cards')).toBeVisible();
    await expect(page.getByTestId('study-table')).toBeHidden();
  } else {
    await expect(page.getByTestId('study-table')).toBeVisible();
    await expect(page.getByTestId('study-cards')).toBeHidden();
  }

  // 8. O browser nunca deve chamar o DCM4CHEE diretamente
  expect(requested.some((url) => url.includes('/dcm4chee-arc/'))).toBe(false);
});

test('handles concurrent STOW import and QIDO worklist search in parallel pages', async ({
  page,
}, testInfo) => {
  test.setTimeout(180_000);
  const pixelDataBytesPerInstance = 1024 * 1024;
  const requested: string[] = [];
  page.on('request', (request) => requested.push(request.url()));

  const isMobile = testInfo.project.name === 'chromium-mobile';
  const runSuffix = randomUUID().slice(0, 8).toUpperCase();
  const patientPrefix = `${
    isMobile ? 'CONCURRENTMOBILE' : 'CONCURRENTDESKTOP'
  }${runSuffix}`;
  const storedMarkerPatientId = `${patientPrefix}-01`;
  const pendingMarkerPatientId = `${patientPrefix}-30`;

  // 1. Autenticação via Keycloak
  await page.goto('/');
  await page.getByLabel('Usuário', { exact: true }).fill('dr.teste');
  await page.getByLabel('Senha', { exact: true }).fill('teste123');
  await page.getByRole('button', { name: 'Entrar' }).click();

  const worklistPage = await page.context().newPage();
  worklistPage.on('request', (request) => requested.push(request.url()));

  const timings: {
    ingestPostRequestAt?: number;
    ingestPostResponseAt?: number;
    qidoRequestAt?: number;
    qidoResponseAt?: number;
    archiveMarkerVisibleAt?: number;
    partialBatchConfirmedAt?: number;
    markerQidoAttempts?: number;
  } = {};
  const isStudiesRequest = (url: string) =>
    new URL(url).pathname === '/api/studies';
  const isPatientQidoRequest = (url: string, patientId: string) => {
    const parsed = new URL(url);
    return (
      parsed.pathname === '/api/studies' &&
      parsed.searchParams.get('patientId') === patientId
    );
  };

  page.on('request', (request) => {
    if (request.method() === 'POST' && isStudiesRequest(request.url())) {
      timings.ingestPostRequestAt = performance.now();
    }
  });
  page.on('response', (response) => {
    if (
      response.request().method() === 'POST' &&
      isStudiesRequest(response.url())
    ) {
      timings.ingestPostResponseAt = performance.now();
    }
  });
  worklistPage.on('request', (request) => {
    if (
      request.method() === 'GET' &&
      isPatientQidoRequest(request.url(), storedMarkerPatientId)
    ) {
      timings.qidoRequestAt = performance.now();
    }
  });

  await worklistPage.goto('/studies');
  await expect(
    worklistPage.getByRole('heading', { name: 'Worklist' }),
  ).toBeVisible();
  await expect(worklistPage.getByText('Carregando estudos…')).toBeHidden();

  // 2. Ingestão de 30 estudos sintéticos na Page A
  await page.goto('/ingest');
  const files = Array.from({ length: 30 }, (_, index) => {
    const num = index + 1;
    const caseNum = String(num).padStart(2, '0');
    const studyUid = createDicomUid();
    const seriesUid = createDicomUid();
    const sopUid = createDicomUid();

    const buffer = createSyntheticDicom(studyUid, seriesUid, sopUid, {
      patientName: `${patientPrefix}^CASE${caseNum}`,
      patientId: `${patientPrefix}-${caseNum}`,
      studyDate: '2026-08-22',
      studyTime: '120000',
      modality: 'OT',
      studyDescription: `CONCURRENT STUDY ${caseNum}`,
      pixelDataBytes: pixelDataBytesPerInstance,
    });

    return {
      name: `concurrent-study-${caseNum}.dcm`,
      mimeType: 'application/dicom',
      buffer,
    };
  });
  expect(files.every(({ buffer }) => buffer.length > pixelDataBytesPerInstance))
    .toBe(true);

  await page.locator('input[type=file]').setInputFiles(files);
  await expect(page.getByText('30 arquivos')).toBeVisible();

  const ingestPostRequest = page.waitForRequest(
    (request) =>
      request.method() === 'POST' && isStudiesRequest(request.url()),
  );
  const ingestPostResponse = page.waitForResponse(
    (response) =>
      response.request().method() === 'POST' &&
      isStudiesRequest(response.url()),
    { timeout: 120_000 },
  );
  const importClick = page.getByRole('button', { name: 'Importar' }).click();
  await ingestPostRequest;
  await expect(page.getByRole('status')).toContainText(
    'Processando no Archive',
    { timeout: 30_000 },
  );

  // 3. Confirma via QIDO que o Archive já tornou um estudo do lote consultável
  expect(timings.ingestPostResponseAt).toBeUndefined();
  await worklistPage.getByLabel('ID do paciente').fill(storedMarkerPatientId);
  const resultItem = isMobile ? 'study-card' : 'study-row';

  const beforeIngestPostResponse = async <T>(
    operation: Promise<T>,
    stage: string,
  ): Promise<T> => {
    const outcome = await Promise.race([
      operation.then((value) => ({ kind: 'operation' as const, value })),
      ingestPostResponse.then(() => ({ kind: 'ingest-response' as const })),
    ]);
    if (outcome.kind === 'ingest-response') {
      throw new Error(
        `POST de ingestão respondeu antes de ${stage}; sobreposição não comprovada`,
      );
    }
    return outcome.value;
  };

  const markerDeadline = performance.now() + 30_000;
  let markerVisible = false;
  let markerQidoAttempts = 0;
  while (!markerVisible && performance.now() < markerDeadline) {
    markerQidoAttempts += 1;
    const remainingMs = Math.max(1, markerDeadline - performance.now());
    const qidoResponse = worklistPage.waitForResponse(
      (response) =>
        response.request().method() === 'GET' &&
        isPatientQidoRequest(response.url(), storedMarkerPatientId),
      { timeout: Math.min(15_000, remainingMs) },
    );
    await worklistPage.getByRole('button', { name: 'Buscar' }).click();
    const completedQidoResponse = await beforeIngestPostResponse(
      qidoResponse,
      'o QIDO do marcador responder',
    );
    expect(completedQidoResponse.ok()).toBe(true);
    expect(
      await beforeIngestPostResponse(
        completedQidoResponse.finished(),
        'o body QIDO do marcador terminar',
      ),
    ).toBeNull();
    timings.qidoResponseAt = performance.now();
    await beforeIngestPostResponse(
      worklistPage
        .getByText('Carregando estudos…')
        .waitFor({ state: 'hidden' }),
      'a Worklist concluir o QIDO do marcador',
    );
    await expect(worklistPage.getByRole('alert')).toHaveCount(0);

    const itemCount = await worklistPage.getByTestId(resultItem).count();
    expect(itemCount).toBeLessThanOrEqual(1);
    markerVisible = itemCount === 1;
    if (!markerVisible) {
      await expect(
        worklistPage.getByText(
          'Nenhum estudo encontrado para os filtros informados.',
        ),
      ).toBeVisible();
    }
  }
  timings.markerQidoAttempts = markerQidoAttempts;
  expect(markerVisible, 'CASE01 não ficou consultável antes do fim do POST')
    .toBe(true);
  await expect(worklistPage.getByTestId(resultItem).first()).toContainText(
    storedMarkerPatientId,
  );
  timings.archiveMarkerVisibleAt = performance.now();
  expect(timings.ingestPostResponseAt).toBeUndefined();

  // O processamento sequencial ainda não tornou o último estudo consultável.
  await worklistPage.getByLabel('ID do paciente').fill(pendingMarkerPatientId);
  const pendingMarkerResponse = worklistPage.waitForResponse(
    (response) =>
      response.request().method() === 'GET' &&
      isPatientQidoRequest(response.url(), pendingMarkerPatientId),
  );
  await worklistPage.getByRole('button', { name: 'Buscar' }).click();
  const completedPendingMarkerResponse = await pendingMarkerResponse;
  expect(completedPendingMarkerResponse.ok()).toBe(true);
  expect(await completedPendingMarkerResponse.finished()).toBeNull();
  await expect(worklistPage.getByText('Carregando estudos…')).toBeHidden();
  await expect(worklistPage.getByRole('alert')).toHaveCount(0);
  await expect(worklistPage.getByTestId(resultItem)).toHaveCount(0);
  await expect(
    worklistPage.getByText(
      'Nenhum estudo encontrado para os filtros informados.',
    ),
  ).toBeVisible();
  timings.partialBatchConfirmedAt = performance.now();
  expect(timings.ingestPostResponseAt).toBeUndefined();

  // 4. Aguarda conclusão do STOW na Page A
  await importClick;
  const completedIngestPostResponse = await ingestPostResponse;
  expect(completedIngestPostResponse.ok()).toBe(true);
  expect(await completedIngestPostResponse.finished()).toBeNull();
  await expect(
    page.getByRole('heading', { name: 'Resultado da importação' }),
  ).toBeVisible({ timeout: 120_000 });
  await expect(page.getByText('30 armazenados', { exact: true })).toBeVisible();
  await expect(
    page.getByText('0 rejeitados ou sem confirmação', { exact: true }),
  ).toBeVisible();
  await expect(page.locator('details')).toHaveCount(30);

  // 5. Registra e comprova a ordem real das operações HTTP
  await testInfo.attach('concurrency-timings', {
    body: JSON.stringify(
      {
        project: testInfo.project.name,
        totalPayloadBytes: files.reduce(
          (total, { buffer }) => total + buffer.length,
          0,
        ),
        ...timings,
        ingestPostDurationMs:
          timings.ingestPostResponseAt! - timings.ingestPostRequestAt!,
        qidoDurationMs:
          timings.qidoResponseAt! - timings.qidoRequestAt!,
      },
      null,
      2,
    ),
    contentType: 'application/json',
  });
  expect(timings.qidoRequestAt).toBeGreaterThan(timings.ingestPostRequestAt!);
  expect(timings.archiveMarkerVisibleAt).toBeLessThan(
    timings.ingestPostResponseAt!,
  );
  expect(timings.partialBatchConfirmedAt).toBeLessThan(
    timings.ingestPostResponseAt!,
  );

  // 6. O browser nunca deve chamar o DCM4CHEE diretamente em nenhuma das páginas
  expect(requested.some((url) => url.includes('/dcm4chee-arc/'))).toBe(false);

  await worklistPage.close();
});
