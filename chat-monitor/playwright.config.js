import { defineConfig } from '@playwright/test';

export default defineConfig({
  testDir: './test/browser',
  fullyParallel: false,
  retries: 0,
  workers: 1,
  reporter: 'list',
  use: {
    baseURL: 'http://127.0.0.1:8090',
    browserName: 'chromium'
  },
  webServer: {
    command: 'npm start',
    url: 'http://127.0.0.1:8090/api/v1/health/ready',
    reuseExistingServer: false,
    timeout: 30_000,
    env: {
      CHAT_MONITOR_ENVIRONMENTS_FILE: './test/fixtures/environments.json',
      CHAT_MONITOR_HOST: '127.0.0.1',
      CHAT_MONITOR_PORT: '8090',
      CHAT_MONITOR_LOG_LEVEL: 'silent'
    }
  }
});
