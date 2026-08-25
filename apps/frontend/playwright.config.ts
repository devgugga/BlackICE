import { defineConfig } from '@playwright/test';

const isCi = Boolean(process.env.CI);

export default defineConfig({
  testDir: './e2e',
  testMatch: [
    'keycloak-login.spec.ts',
    'manual-dicom-import.spec.ts',
    'worklist.spec.ts',
    'problem-details.spec.ts',
    'viewer.spec.ts',
  ],
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
  updateSnapshots: 'none',
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
