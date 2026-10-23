const { defineConfig } = require("@playwright/test");
const testPort = Number(process.env.PLAYWRIGHT_TEST_PORT || 4174);
const testUrl = `http://127.0.0.1:${testPort}`;

module.exports = defineConfig({
  testDir: __dirname,
  testMatch: "*.spec.cjs",
  fullyParallel: true,
  reporter: "list",
  use: {
    baseURL: testUrl,
    browserName: "chromium",
    viewport: { width: 390, height: 844 },
  },
  webServer: {
    command: `node static-server.cjs`,
    url: `${testUrl}/`,
    reuseExistingServer: !process.env.CI,
    timeout: 10_000,
  },
});
