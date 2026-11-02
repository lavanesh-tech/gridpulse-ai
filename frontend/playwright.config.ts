import { defineConfig, devices } from '@playwright/test';

// Started by scripts/e2e.sh, which runs a throwaway Postgres + the Spring Boot API and passes their address here.
const api = process.env.E2E_API ?? 'http://localhost:18081';

export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  workers: 1,
  retries: 0,
  reporter: [['list'], ['html', { open: 'never', outputFolder: 'playwright-report' }]],
  globalSetup: './e2e/global-setup.ts',
  use: {
    baseURL: 'http://localhost:5174',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    timezoneId: 'America/New_York',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: {
    // Test the production build (what users get), not the dev server.
    command: 'npx vite build && npx vite preview --port 5174 --strictPort',
    url: 'http://localhost:5174',
    reuseExistingServer: false,
    env: { VITE_API_TARGET: api },
    timeout: 120_000,
  },
});
