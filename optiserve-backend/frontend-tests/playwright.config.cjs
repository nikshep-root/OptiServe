const { defineConfig } = require("@playwright/test");

module.exports = defineConfig({
  testDir: __dirname,
  testMatch: "*.spec.cjs",
  fullyParallel: true,
  reporter: "list",
  use: {
    baseURL: "http://127.0.0.1:4173",
    browserName: "chromium",
    viewport: { width: 390, height: 844 },
  },
  webServer: {
    command: "node static-server.cjs",
    url: "http://127.0.0.1:4173/",
    reuseExistingServer: !process.env.CI,
    timeout: 10_000,
  },
});
