import { expect, test } from '@playwright/test';
import { createSyntheticDicom } from './fixtures/synthetic-dicom';

test('imports two synthetic studies through the BFF', async ({ page }) => {
  const requested: string[] = [];
  page.on('request', request => requested.push(request.url()));
  await page.goto('/');
  await page.getByLabel('Usuário', { exact: true }).fill('dr.teste');
  await page.getByLabel('Senha', { exact: true }).fill('teste123');
  await page.getByRole('button', { name: 'Entrar' }).click();
  await page.goto('/ingest');
  await page.locator('input[type=file]').setInputFiles([
    {
      name: 'study-a.dcm',
      mimeType: 'application/dicom',
      buffer: createSyntheticDicom('2.25.101', '2.25.1011', '2.25.10111'),
    },
    {
      name: 'study-b.dcm',
      mimeType: 'application/dicom',
      buffer: createSyntheticDicom('2.25.102', '2.25.1021', '2.25.10211'),
    },
  ]);
  await expect(page.getByText('2 arquivos')).toBeVisible();
  await page.getByRole('button', { name: 'Importar' }).click();
  await expect(page.getByRole('heading', { name: 'Resultado da importação' })).toBeVisible();
  await expect(page.locator('details')).toHaveCount(2);
  expect(requested.some(url => url.includes('/dcm4chee-arc/'))).toBe(false);
});
