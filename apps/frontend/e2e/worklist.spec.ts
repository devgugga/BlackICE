import { expect, test } from '@playwright/test';
import { createSyntheticDicom } from './fixtures/synthetic-dicom';

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
  const requested: string[] = [];
  page.on('request', (request) => requested.push(request.url()));

  const isMobile = testInfo.project.name === 'chromium-mobile';
  const uidPrefix = isMobile ? '2.25.812' : '2.25.811';
  const patientPrefix = isMobile ? 'CONCURRENTMOBILE' : 'CONCURRENTDESKTOP';

  // 1. Autenticação via Keycloak
  await page.goto('/');
  await page.getByLabel('Usuário', { exact: true }).fill('dr.teste');
  await page.getByLabel('Senha', { exact: true }).fill('teste123');
  await page.getByRole('button', { name: 'Entrar' }).click();

  // 2. Ingestão de 30 estudos sintéticos na Page A
  await page.goto('/ingest');
  const files = Array.from({ length: 30 }, (_, index) => {
    const num = index + 1;
    const caseNum = String(num).padStart(2, '0');
    const studyUid = `${uidPrefix}.${num}`;
    const seriesUid = `${uidPrefix}.${num}.1`;
    const sopUid = `${uidPrefix}.${num}.1.1`;

    return {
      name: `concurrent-study-${caseNum}.dcm`,
      mimeType: 'application/dicom',
      buffer: createSyntheticDicom(studyUid, seriesUid, sopUid, {
        patientName: `${patientPrefix}^CASE${caseNum}`,
        patientId: `${patientPrefix}-${caseNum}`,
        studyDate: '2026-08-22',
        studyTime: '120000',
        modality: 'OT',
        studyDescription: `CONCURRENT STUDY ${caseNum}`,
      }),
    };
  });

  await page.locator('input[type=file]').setInputFiles(files);
  await expect(page.getByText('30 arquivos')).toBeVisible();

  const stowStartTime = Date.now();
  await page.getByRole('button', { name: 'Importar' }).click();

  // 3. Aguarda fase de processamento no Archive na Page A e abre Page B para QIDO concorrente
  await expect(page.getByRole('status')).toContainText('Processando no Archive');

  const qidoStartTime = Date.now();
  const worklistPage = await page.context().newPage();
  worklistPage.on('request', (request) => requested.push(request.url()));

  await worklistPage.goto('/studies');
  await expect(
    worklistPage.getByRole('heading', { name: 'Worklist' }),
  ).toBeVisible();
  await expect(worklistPage.getByText('Carregando estudos…')).toBeHidden();
  const qidoDurationMs = Date.now() - qidoStartTime;

  // 4. Aguarda conclusão do STOW na Page A
  await expect(
    page.getByRole('heading', { name: 'Resultado da importação' }),
  ).toBeVisible({ timeout: 120_000 });
  const stowDurationMs = Date.now() - stowStartTime;
  await expect(page.locator('details')).toHaveCount(30);

  // 5. Registra tempos de execução nos anexos do teste
  await testInfo.attach('concurrency-timings', {
    body: JSON.stringify(
      {
        project: testInfo.project.name,
        stowDurationMs,
        qidoDurationMs,
      },
      null,
      2,
    ),
    contentType: 'application/json',
  });

  // 6. O browser nunca deve chamar o DCM4CHEE diretamente em nenhuma das páginas
  expect(requested.some((url) => url.includes('/dcm4chee-arc/'))).toBe(false);

  await worklistPage.close();
});
