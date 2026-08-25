import { expect, test, type Page } from '@playwright/test';
import { createSyntheticCtSlice } from './fixtures/synthetic-dicom';

async function login(
  page: Page,
  username = 'dr.teste',
  password = 'teste123',
): Promise<void> {
  await page.goto('/');
  await page.getByLabel('Usuário', { exact: true }).fill(username);
  await page.getByLabel('Senha', { exact: true }).fill(password);
  await page.getByRole('button', { name: 'Entrar' }).click();
  await page.waitForURL((url) => !url.pathname.startsWith('/realms/'));
}

async function assertZeroStorageLeaks(
  page: Page,
  sensitiveTokens: string[],
): Promise<void> {
  const result = await page.evaluate(async (tokens: string[]) => {
    const local = { ...localStorage };
    const session = { ...sessionStorage };
    const localKeys = Object.keys(local);
    const localValues = Object.values(local);
    const sessionKeys = Object.keys(session);
    const sessionValues = Object.values(session);

    const idbData: string[] = [];
    if (
      typeof window.indexedDB !== 'undefined' &&
      typeof window.indexedDB.databases === 'function'
    ) {
      try {
        const dbs = await window.indexedDB.databases();
        for (const dbInfo of dbs) {
          if (!dbInfo.name) continue;
          await new Promise<void>((resolve) => {
            const req = window.indexedDB.open(dbInfo.name!);
            req.onsuccess = () => {
              const db = req.result;
              const storeNames = Array.from(db.objectStoreNames);
              if (storeNames.length === 0) {
                db.close();
                resolve();
                return;
              }
              let remaining = storeNames.length;
              const tx = db.transaction(storeNames, 'readonly');
              for (const name of storeNames) {
                const store = tx.objectStore(name);
                const getReq = store.getAll();
                getReq.onsuccess = () => {
                  idbData.push(JSON.stringify(getReq.result));
                  remaining--;
                  if (remaining === 0) {
                    db.close();
                    resolve();
                  }
                };
                getReq.onerror = () => {
                  remaining--;
                  if (remaining === 0) {
                    db.close();
                    resolve();
                  }
                };
              }
            };
            req.onerror = () => resolve();
          });
        }
      } catch {
        // IDB databases listing may not be supported or error out
      }
    }

    const aggregated = [
      ...localKeys,
      ...localValues,
      ...sessionKeys,
      ...sessionValues,
      ...idbData,
    ].join(' ');

    const leaks = tokens.filter(
      (t) => t && t.length > 3 && aggregated.includes(t),
    );
    const hasAuthToken =
      aggregated.includes('access_token') ||
      aggregated.includes('refresh_token') ||
      aggregated.includes('id_token') ||
      aggregated.includes('Bearer ');

    return {
      leaks,
      hasAuthToken,
    };
  }, sensitiveTokens);

  expect(result.leaks).toEqual([]);
  expect(result.hasAuthToken).toBe(false);
}

test('executes end-to-end multi-actor report lifecycle with concurrency and storage checks', async ({
  page,
}, testInfo) => {
  const requestedUrls: string[] = [];
  page.on('request', (req) => requestedUrls.push(req.url()));

  const projectName = testInfo.project.name;
  const isMobile = projectName === 'chromium-mobile';
  const isDrawer = projectName === 'chromium-1366' || projectName === 'chromium-1024';
  const isSplit = projectName === 'chromium-desktop';

  const runId = Math.floor(Math.random() * 900000 + 100000).toString();
  const uidPrefix = `2.25.850.${runId}`;
  const patientPrefix = `REPORT${runId}`;
  const patientName = `${patientPrefix}^PATIENT`;
  const patientId = `${patientPrefix}-01`;

  const studyUid = `${uidPrefix}.1`;
  const ctSeriesUid = `${uidPrefix}.1.1`;
  const sopUid = `${uidPrefix}.1.1.1`;

  const initialDraftText = 'Achados iniciais: parênquima pulmonar com atenuação e transparência normais.';
  const concurrentUpdateText = 'Atualização concorrente por outra aba: pequeno nódulo residual estável.';
  const localStaleText = 'Tentativa de edição local com versão defasada: alteração adicional.';

  // 1. Login as dr.teste
  await login(page, 'dr.teste', 'teste123');

  // 2. STOW synthetic study
  await page.goto('/ingest');
  await page.locator('input[type=file]').setInputFiles([
    {
      name: 'synthetic-ct-chest.dcm',
      mimeType: 'application/dicom',
      buffer: createSyntheticCtSlice(studyUid, ctSeriesUid, sopUid, {
        patientName,
        patientId,
        studyDate: '2026-08-25',
        studyTime: '120000',
        studyDescription: 'E2E MULTI-ACTOR REPORT STUDY',
        seriesNumber: 1,
        seriesDescription: 'CT CHEST AXIAL',
        instanceNumber: 1,
        sliceLocation: 0,
        windowCenter: 40,
        windowWidth: 400,
      }),
    },
  ]);
  await page.getByRole('button', { name: 'Importar' }).click();
  await expect(
    page.getByRole('heading', { name: 'Resultado da importação' }),
  ).toBeVisible({ timeout: 120_000 });

  // 3. Find study in Worklist through QIDO
  await page.goto('/studies');
  await page.getByLabel('Nome do paciente').fill(patientPrefix);
  await page.getByRole('button', { name: 'Buscar' }).click();

  const resultItem = isMobile ? page.getByTestId('study-card') : page.getByTestId('study-row');
  await expect(resultItem).toHaveCount(1);

  // 4. Open study and observe rendered viewer / capability gate
  await resultItem.getByTestId('open-study').first().click();
  await page.waitForURL((url) => url.pathname.includes(`/studies/${studyUid}`));

  if (isMobile) {
    // Capability gate active on narrow screens
    await expect(page.locator('.capability-gate-message')).toContainText(
      'Use uma tela maior para visualizar as imagens deste estudo.',
    );
    // 0 instance or frame requests
    expect(
      requestedUrls.some((url) => url.includes('/instances') || url.includes('/frames/')),
    ).toBe(false);
    await expect(page.locator('[data-testid="dicom-viewport"] canvas')).toHaveCount(0);

    // Report-only layout is visible
    await expect(page.getByTestId('report-panel')).toBeVisible();
    await expect(page.getByTestId('report-panel')).toHaveClass(/layout-report-only/);
  } else if (isDrawer) {
    // Drawer layout on medium/tablet screens
    const viewport = page.getByTestId('dicom-viewport');
    await expect(viewport).toBeVisible({ timeout: 20_000 });
    await expect(viewport.locator('canvas')).toBeVisible({ timeout: 20_000 });

    // Drawer is closed initially
    await expect(page.getByTestId('report-panel')).toBeHidden();
    const boxBefore = await viewport.boundingBox();
    expect(boxBefore).not.toBeNull();

    // Open drawer via toolbar button
    await page.getByTestId('toggle-report-btn').click();
    await expect(page.getByTestId('report-panel')).toBeVisible();
    await expect(page.getByTestId('report-panel')).toHaveClass(/layout-drawer/);

    // Close drawer via close button and assert viewport geometry unchanged
    await page.getByTestId('panel-close-button').click();
    await expect(page.getByTestId('report-panel')).toBeHidden();
    const boxAfter = await viewport.boundingBox();
    expect(boxAfter).not.toBeNull();
    expect(boxAfter!.width).toBe(boxBefore!.width);
    expect(boxAfter!.height).toBe(boxBefore!.height);

    // Reopen drawer to proceed with report editing
    await page.getByTestId('toggle-report-btn').click();
    await expect(page.getByTestId('report-panel')).toBeVisible();
  } else if (isSplit) {
    // Split layout on desktop screens (>= 1440px)
    const viewport = page.getByTestId('dicom-viewport');
    await expect(viewport).toBeVisible({ timeout: 20_000 });
    await expect(viewport.locator('canvas')).toBeVisible({ timeout: 20_000 });

    const box = await viewport.boundingBox();
    expect(box).not.toBeNull();
    expect(box!.width).toBeGreaterThanOrEqual(720);

    await expect(page.getByTestId('report-panel')).toBeVisible();
    await expect(page.getByTestId('report-panel')).toHaveClass(/layout-split/);
  }

  // 5. Observe 204 report state (empty editor, status Novo)
  await expect(page.getByTestId('report-status-badge')).toHaveText('Novo');
  await expect(page.getByTestId('report-textarea')).toHaveValue('');
  await expect(page.getByTestId('save-draft-button')).toBeDisabled();
  await expect(page.getByTestId('finalize-button')).toBeDisabled();

  // 6. Save DRAFT and reload
  await page.getByTestId('report-textarea').fill(initialDraftText);
  await expect(page.getByTestId('save-draft-button')).toBeEnabled();
  await expect(page.getByTestId('finalize-button')).toBeEnabled();

  await page.getByTestId('save-draft-button').click();
  await expect(page.getByTestId('report-status-badge')).toHaveText('Rascunho');
  await expect(page.getByTestId('live-announcer')).toHaveText('Rascunho salvo com sucesso.');

  // Reload page to verify saved draft persistence
  await page.reload();
  if (isDrawer) {
    await page.getByTestId('toggle-report-btn').click();
    await expect(page.getByTestId('report-panel')).toBeVisible();
  }
  await expect(page.getByTestId('report-status-badge')).toHaveText('Rascunho');
  await expect(page.getByTestId('report-textarea')).toHaveValue(initialDraftText);

  // 7. Open second same-author page, update there, then prove first page gets 412 without losing local text
  const secondPage = await page.context().newPage();
  await secondPage.goto(`/studies/${studyUid}`);
  if (isDrawer) {
    await secondPage.getByTestId('toggle-report-btn').click();
    await expect(secondPage.getByTestId('report-panel')).toBeVisible();
  }
  await expect(secondPage.getByTestId('report-textarea')).toHaveValue(initialDraftText);

  // Update on second page -> bump version/ETag on server
  await secondPage.getByTestId('report-textarea').fill(concurrentUpdateText);
  await secondPage.getByTestId('save-draft-button').click();
  await expect(secondPage.getByTestId('report-status-badge')).toHaveText('Rascunho');
  await expect(secondPage.getByTestId('live-announcer')).toHaveText('Rascunho salvo com sucesso.');
  await secondPage.close();

  // Attempt to save stale draft on first page
  await page.getByTestId('report-textarea').fill(localStaleText);
  await page.getByTestId('save-draft-button').click();

  // Assert 412 version conflict banner without losing local text
  await expect(page.getByTestId('report-error')).toBeVisible();
  await expect(page.getByTestId('reload-server-button')).toBeVisible();
  await expect(page.getByTestId('report-textarea')).toHaveValue(localStaleText);

  // 8. Reload server version with confirmation modal, then finalize report
  await page.getByTestId('reload-server-button').click();
  await expect(page.getByTestId('reload-confirm-modal')).toBeVisible();
  await page.getByTestId('reload-confirm-submit-button').click();

  await expect(page.getByTestId('report-error')).toBeHidden();
  await expect(page.getByTestId('report-textarea')).toHaveValue(concurrentUpdateText);

  // Finalize report
  await page.getByTestId('finalize-button').click();
  await expect(page.getByRole('dialog')).toBeVisible();
  await page.getByTestId('modal-confirm-button').click();

  await expect(page.getByTestId('report-status-badge')).toHaveText('Finalizado');
  await expect(page.getByTestId('report-content-view')).toBeVisible();
  await expect(page.getByTestId('report-content-view')).toHaveText(concurrentUpdateText);
  await expect(page.getByTestId('report-textarea')).toBeHidden();
  await expect(page.getByTestId('save-draft-button')).toBeHidden();
  await expect(page.getByTestId('finalize-button')).toBeHidden();

  // Verify reload maintains immutable finalized view
  await page.reload();
  if (isDrawer) {
    await page.getByTestId('toggle-report-btn').click();
    await expect(page.getByTestId('report-panel')).toBeVisible();
  }
  await expect(page.getByTestId('report-status-badge')).toHaveText('Finalizado');
  await expect(page.getByTestId('report-content-view')).toBeVisible();
  await expect(page.getByTestId('report-content-view')).toHaveText(concurrentUpdateText);
  await expect(page.getByTestId('report-textarea')).toBeHidden();

  // 9. Fresh browser context as dr.leitor: read report, assert mutation controls absent, and direct PUT returns 403
  const browser = page.context().browser();
  expect(browser).not.toBeNull();
  const leitorContext = await browser!.newContext({
    viewport: page.viewportSize() ?? undefined,
  });
  const leitorPage = await leitorContext.newPage();
  const leitorRequests: string[] = [];
  leitorPage.on('request', (req) => leitorRequests.push(req.url()));

  await login(leitorPage, 'dr.leitor', 'teste123');
  await leitorPage.goto(`/studies/${studyUid}`);
  if (isDrawer) {
    await leitorPage.getByTestId('toggle-report-btn').click();
    await expect(leitorPage.getByTestId('report-panel')).toBeVisible();
  }

  // Reader sees finalized report without mutation buttons
  await expect(leitorPage.getByTestId('report-status-badge')).toHaveText('Finalizado');
  await expect(leitorPage.getByTestId('report-content-view')).toBeVisible();
  await expect(leitorPage.getByTestId('report-content-view')).toHaveText(concurrentUpdateText);
  await expect(leitorPage.getByTestId('report-textarea')).toBeHidden();
  await expect(leitorPage.getByTestId('save-draft-button')).toBeHidden();
  await expect(leitorPage.getByTestId('finalize-button')).toBeHidden();

  // Direct malicious PUT by dr.leitor returns 403 Forbidden
  const maliciousPutResult = await leitorPage.evaluate(async (uid) => {
    await fetch('/api/csrf', { credentials: 'include' });
    const match = document.cookie.match(/(?:^|;\s*)csrf-token=([^;]*)/);
    const csrfToken = match ? decodeURIComponent(match[1]) : '';

    const getRes = await fetch(`/api/studies/${uid}/report`, {
      credentials: 'include',
    });
    const etag = getRes.headers.get('ETag') ?? '';

    const response = await fetch(`/api/studies/${uid}/report`, {
      method: 'PUT',
      credentials: 'include',
      headers: {
        'Content-Type': 'application/json',
        'X-CSRF-TOKEN': csrfToken,
        'If-Match': etag,
      },
      body: JSON.stringify({
        content: 'Tentativa de alteração não autorizada por dr.leitor',
        status: 'DRAFT',
      }),
    });

    const status = response.status;
    let body: Record<string, unknown> | null = null;
    try {
      body = await response.json();
    } catch {
      // ignore
    }
    return { status, body };
  }, studyUid);

  expect(maliciousPutResult.status).toBe(403);
  expect(maliciousPutResult.body?.code).toBe('API_ACCESS_DENIED');

  // 10. Verify no Archive URL was called by browser and zero sensitive data in storage
  expect(requestedUrls.some((url) => url.includes('/dcm4chee-arc/'))).toBe(false);
  expect(leitorRequests.some((url) => url.includes('/dcm4chee-arc/'))).toBe(false);

  const sensitiveDataList = [
    studyUid,
    ctSeriesUid,
    sopUid,
    initialDraftText,
    concurrentUpdateText,
    localStaleText,
  ];

  await assertZeroStorageLeaks(page, sensitiveDataList);
  await assertZeroStorageLeaks(leitorPage, sensitiveDataList);

  await leitorContext.close();
});
