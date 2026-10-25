const { test, expect } = require("@playwright/test");

async function openAutomation(page) {
  const state = { mode: "MANUAL", writes: [] };
  await page.addInitScript(() => sessionStorage.setItem("optiserve.accessToken", "browser-test-token"));
  await page.route("**/api/**", async (route) => {
    const url = new URL(route.request().url());
    let body = [];
    if (url.pathname === "/api/auth/me") body = { role: "ADMIN" };
    else if (url.pathname === "/api/admin/workshop-automation") {
      if (route.request().method() === "PUT") {
        const payload = route.request().postDataJSON();
        state.writes.push(payload.mode);
        state.mode = payload.mode;
      }
      body = { mode: state.mode, updatedAt: "2026-10-01T10:00:00Z" };
    }
    await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(body) });
  });
  await page.goto("/#/app/automation");
  return state;
}

test("AUTO requires confirmation and canceled confirmation does not update the mode", async ({ page }) => {
  const state = await openAutomation(page);
  await expect(page.getByTestId("automation-mode")).toHaveText("MANUAL");
  page.on("dialog", (dialog) => dialog.dismiss());
  await page.getByRole("button", { name: "Enable automatic operations" }).click();
  await expect(page.getByTestId("automation-mode")).toHaveText("MANUAL");
  expect(state.writes).toEqual([]);
});

test("confirmed mode change persists and reloads the workshop setting", async ({ page }) => {
  const state = await openAutomation(page);
  page.on("dialog", (dialog) => dialog.accept());
  await page.getByRole("button", { name: "Enable automatic operations" }).click();
  await expect(page.getByTestId("automation-mode")).toHaveText("AUTO");
  await expect(page.getByText(/work already waiting/)).toBeVisible();
  expect(state.writes).toEqual(["AUTO"]);

  await page.getByRole("button", { name: "Return to manual mode" }).click();
  await expect(page.getByTestId("automation-mode")).toHaveText("MANUAL");
  expect(state.writes).toEqual(["AUTO", "MANUAL"]);
});

test("customers cannot open automation controls or request the admin setting", async ({ page }) => {
  const requests = [];
  await page.addInitScript(() => sessionStorage.setItem("optiserve.accessToken", "browser-test-token"));
  await page.route("**/api/**", async (route) => {
    const url = new URL(route.request().url());
    requests.push(url.pathname);
    const body = url.pathname === "/api/auth/me" ? { role: "USER" }
      : url.pathname === "/api/customers/me" ? { id: "customer-1", name: "Test" } : [];
    await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(body) });
  });
  await page.goto("/#/app/automation");
  await expect(page.getByRole("heading", { name: "My service overview" })).toBeVisible();
  await expect(page.getByRole("link", { name: "Automation" })).toHaveCount(0);
  expect(requests).not.toContain("/api/admin/workshop-automation");
});
