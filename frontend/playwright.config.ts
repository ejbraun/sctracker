import { defineConfig, devices } from '@playwright/test';

/**
 * specs/frontend/*.md's app, exercised end to end against a real backend. Playwright only manages
 * the frontend dev server here (webServer below) — the Spring Boot backend + MySQL are a documented
 * prerequisite (see frontend/README or IMPLEMENTATION_PROGRESS.md), started separately, since
 * bringing up Docker MySQL + a Liquibase-migrated Spring Boot app is out of scope for a test runner
 * that only knows how to start one Node process and poll a URL.
 */
export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  workers: 1,
  reporter: [['list']],
  use: {
    baseURL: 'http://localhost:5173',
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
  webServer: {
    command: 'npm run dev',
    url: 'http://localhost:5173',
    reuseExistingServer: !process.env.CI,
    timeout: 30_000,
  },
});
