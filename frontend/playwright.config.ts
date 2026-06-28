import { defineConfig, devices } from '@playwright/test';

const port = Number(process.env.E2E_PORT ?? 5173);
const host = process.env.E2E_HOST ?? '127.0.0.1';
const baseURL = process.env.E2E_BASE_URL ?? `http://${host}:${port}`;
const skipWebServer = process.env.E2E_SKIP_WEB_SERVER === 'true';

export default defineConfig({
  testDir: './e2e',
  fullyParallel: true,
  timeout: 45_000,
  expect: {
    timeout: 10_000
  },
  reporter: process.env.CI ? [['list'], ['html', { open: 'never' }]] : 'list',
  retries: process.env.CI ? 2 : 0,
  use: {
    baseURL,
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure'
  },
  webServer: skipWebServer
    ? undefined
    : {
        command: `npm run dev -- --host ${host} --port ${port}`,
        url: baseURL,
        reuseExistingServer: !process.env.CI,
        timeout: 120_000
      },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] }
    }
  ]
});
