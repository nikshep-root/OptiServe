const { test, expect } = require("@playwright/test");

async function installApiMocks(page, { role = "ADMIN", sessionStatus = 200, sessionGate, sessionDelayMs = 100, queueStatus = 200 } = {}) {
  await page.addInitScript(({ role }) => {
    sessionStorage.setItem("optiserve.accessToken", "navigation-regression-token");
    sessionStorage.setItem("optiserve.role", role);
  }, { role });

  let sessionCalls = 0;
  await page.route("**/api/**", async (route) => {
    const { pathname } = new URL(route.request().url());
    let status = 200;
    let body = [];
    if (pathname === "/api/auth/me") {
      sessionCalls++;
      if (sessionGate) await sessionGate;
      if (sessionDelayMs) await new Promise((resolve) => setTimeout(resolve, sessionDelayMs));
      status = sessionStatus;
      body = { role };
    } else if (pathname === "/api/service-requests") {
      body = [];
    } else if (pathname === "/api/queue") {
      status = queueStatus;
      body = [];
    } else if (pathname === "/api/service-types" || pathname === "/api/assignments" || pathname === "/api/resources" || pathname === "/api/vehicles") {
      body = [];
    } else if (pathname === "/api/customers/me") {
      body = { id: "customer-1", name: "Asha" };
    } else if (pathname === "/api/admin/workshop-automation") {
      body = { mode: "MANUAL" };
    }
    await route.fulfill({
      status,
      contentType: "application/json",
      body: JSON.stringify(status >= 400 ? { message: status === 401 ? "Session expired" : "Service unavailable" } : body),
    });
  });
  return () => sessionCalls;
}

for (const [route, label] of [["", "Workshop overview"], ["queue", "Live queue"], ["requests", "Service requests"]]) {
  test(`direct navigation to #/app${route ? `/${route}` : ""} keeps the authenticated shell and validates once`, async ({ page }) => {
    const sessionCalls = await installApiMocks(page, { role: "ADMIN" });
    await page.goto(`/#/app${route ? `/${route}` : ""}`);
    const shell = page.locator(".app-shell");
    await expect(shell).toBeVisible();
    await expect(page.locator(".breadcrumbs strong")).toHaveText(label);
    await expect(page.locator(".app-nav a[aria-current='page']")).toHaveAttribute("href", `#/app/${route || "overview"}`);
    await expect(page.getByText("Checking your account…")).toHaveCount(0);
    await expect.poll(() => page.locator(".brand-mark img").first().evaluate((image) => image.naturalWidth)).toBe(1536);
    await expect.poll(sessionCalls).toBe(1);
  });
}

test("internal authenticated navigation changes content without replacing the shell or rechecking the session", async ({ page }) => {
  await page.setViewportSize({ width: 1280, height: 800 });
  const sessionCalls = await installApiMocks(page, { role: "ADMIN" });
  await page.goto("/#/app");
  await expect(page.getByRole("heading", { name: "Workshop overview" })).toBeVisible();
  await page.evaluate(() => { window.__initialDashboardShell = document.querySelector(".app-shell"); });

  await page.getByRole("link", { name: "Live queue" }).click();
  await expect(page.getByRole("heading", { name: "Live queue" })).toBeVisible();
  await page.getByRole("link", { name: "Service requests" }).click();
  await expect(page.locator("#dashboard-content .page-heading h1")).toHaveText("Service requests");

  expect(await page.evaluate(() => document.querySelector(".app-shell") === window.__initialDashboardShell)).toBeTruthy();
  expect(sessionCalls()).toBe(1);
  await expect(page.locator("#app > .loading-state")).toHaveCount(0);
});

test("customer navigation keeps its shell mounted across service pages", async ({ page }) => {
  await page.setViewportSize({ width: 1280, height: 800 });
  const sessionCalls = await installApiMocks(page, { role: "USER" });
  await page.goto("/#/app");
  await expect(page.getByRole("heading", { name: "My service overview" })).toBeVisible();
  await page.evaluate(() => { window.__initialCustomerShell = document.querySelector(".app-shell"); });

  await page.getByRole("link", { name: "Service requests" }).click();
  await expect(page.locator("#dashboard-content .page-heading h1")).toHaveText("Service requests");
  await page.getByRole("link", { name: "My service queue" }).click();
  await expect(page.locator(".breadcrumbs strong")).toHaveText("My service queue");

  expect(await page.evaluate(() => document.querySelector(".app-shell") === window.__initialCustomerShell)).toBeTruthy();
  expect(sessionCalls()).toBe(1);
});

test("expired session clears cached credentials and redirects to login", async ({ page }) => {
  await installApiMocks(page, { role: "ADMIN", sessionStatus: 401 });
  await page.goto("/#/app/queue");
  await expect(page).toHaveURL(/#\/login$/);
  await expect(page.getByRole("heading", { name: /welcome back/i })).toBeVisible();
  expect(await page.evaluate(() => [sessionStorage.getItem("optiserve.accessToken"), sessionStorage.getItem("optiserve.role")])).toEqual([null, null]);
});

test("temporary session API failure preserves the shell and offers retry", async ({ page }) => {
  await page.addInitScript(() => {
    sessionStorage.setItem("optiserve.accessToken", "navigation-regression-token");
    sessionStorage.setItem("optiserve.role", "ADMIN");
  });
  let sessionCalls = 0;
  await page.route("**/api/**", async (route) => {
    if (new URL(route.request().url()).pathname === "/api/auth/me") {
      sessionCalls++;
      const status = sessionCalls === 1 ? 503 : 200;
      await route.fulfill({ status, contentType: "application/json", body: JSON.stringify(status === 200 ? { role: "ADMIN" } : { message: "Temporarily unavailable" }) });
      return;
    }
    const pathname = new URL(route.request().url()).pathname;
    const body = pathname === "/api/admin/workshop-automation" ? { mode: "MANUAL" } : [];
    await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(body) });
  });
  await page.goto("/#/app/requests");
  await expect(page.locator(".app-shell")).toBeVisible();
  await expect(page.locator("#connection-label")).toHaveText("UNAVAILABLE");
  await expect(page.getByRole("heading", { name: "Could not verify your account" })).toBeVisible();
  await page.getByRole("button", { name: "Retry connection" }).click();
  await expect(page.locator("#dashboard-content .page-heading h1")).toHaveText("Service requests");
  await expect(page.locator("#connection-label")).toHaveText("CONNECTED");
});

test("ADMIN routes retain administrator navigation", async ({ page }) => {
  await installApiMocks(page, { role: "ADMIN" });
  await page.goto("/#/app/queue");
  await expect(page.getByRole("link", { name: "Resources" })).toBeVisible();
  await expect(page.getByRole("link", { name: "Profile" })).toHaveCount(0);
});

test("USER routes retain customer navigation", async ({ page }) => {
  await installApiMocks(page, { role: "USER" });
  await page.goto("/#/app/requests");
  await expect(page.getByRole("link", { name: "My vehicles" })).toBeVisible();
  await expect(page.getByRole("link", { name: "Resources" })).toHaveCount(0);
});
