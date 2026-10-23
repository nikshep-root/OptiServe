const { test, expect } = require("@playwright/test");

const serviceTypes = [
  { id: "type-oil", name: "Oil change", active: true, defaultServiceDurationSeconds: 1800 },
  { id: "type-inspection", name: "Inspection", active: true, defaultServiceDurationSeconds: 2400 },
  { id: "type-repair", name: "Repair", active: true, defaultServiceDurationSeconds: 3600 },
];

async function openCustomerDashboard(page, route = "/#/app/requests") {
  await page.addInitScript(() => sessionStorage.setItem("optiserve.accessToken", "browser-test-token"));
  await page.route("**/api/**", async (requestRoute) => {
    const request = requestRoute.request();
    const url = new URL(request.url());
    const method = request.method();
    let body = [];

    if (url.pathname === "/api/auth/me") body = { role: "USER" };
    else if (url.pathname === "/api/customers/me") body = { id: "customer-test", name: "Test Customer" };
    else if (url.pathname === "/api/vehicles") body = [{ id: "vehicle-test", registrationNumber: "KA01AB1234", make: "Toyota", model: "Camry", year: 2022 }];
    else if (url.pathname === "/api/service-types") body = serviceTypes;
    else if (url.pathname === "/api/service-requests" && method === "POST") body = { id: "request-test", status: "CREATED" };

    await requestRoute.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(body) });
  });
  await page.goto(route);
  await expect(page.locator(".page-heading h1")).toBeVisible();
}

test.beforeEach(async ({ page }) => openCustomerDashboard(page));

test("service-stage checkboxes can be selected and deselected", async ({ page }) => {
  await page.getByRole("button", { name: /New request/ }).click();
  const oilChange = page.locator('input[name="serviceTypeIds"][value="type-oil"]');
  await expect(oilChange).not.toBeChecked();
  await oilChange.check();
  await expect(oilChange).toBeChecked();
  await oilChange.uncheck();
  await expect(oilChange).not.toBeChecked();
});

test("submits selected service IDs in the displayed stage order", async ({ page }) => {
  let submittedRequest;
  await page.route("**/api/service-requests", async (requestRoute) => {
    if (requestRoute.request().method() === "POST") submittedRequest = requestRoute.request().postDataJSON();
    await requestRoute.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({ id: "request-test", status: "CREATED" }) });
  });

  await page.getByRole("button", { name: /New request/ }).click();
  await page.locator('input[name="serviceTypeIds"][value="type-repair"]').check();
  await page.locator('input[name="serviceTypeIds"][value="type-oil"]').check();
  await page.getByRole("button", { name: /Create service request/ }).click();

  await expect.poll(() => submittedRequest?.serviceTypeIds).toEqual(["type-oil", "type-repair"]);
  expect(submittedRequest.vehicleId).toBe("vehicle-test");
});

test("requires a service stage and does not call request creation when none is selected", async ({ page }) => {
  let createRequestCalls = 0;
  await page.route("**/api/service-requests", async (requestRoute) => {
    if (requestRoute.request().method() === "POST") createRequestCalls += 1;
    await requestRoute.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify([]) });
  });

  await page.getByRole("button", { name: /New request/ }).click();
  await page.getByRole("button", { name: /Create service request/ }).click();

  await expect(page.getByRole("alert")).toHaveText("Select at least one service stage to continue.");
  expect(createRequestCalls).toBe(0);
});

test("mobile menu opens and closes repeatedly, including after a dashboard rerender", async ({ page }) => {
  await page.goto("/#/app/overview");
  await expect(page.locator(".page-heading h1")).toBeVisible();
  const menu = page.getByRole("button", { name: "Toggle navigation" });
  const sidebar = page.locator("#sidebar");

  await expect(menu).toHaveAttribute("aria-controls", "sidebar");
  await expect(menu).toHaveAttribute("aria-expanded", "false");
  await menu.click();
  await expect(sidebar).toHaveClass(/sidebar-open/);
  await expect(menu).toHaveAttribute("aria-expanded", "true");

  await page.getByRole("button", { name: /Refresh data/ }).click();
  await expect(page.locator("#connection-label")).toHaveText("CONNECTED");
  await expect(menu).toHaveAttribute("aria-expanded", "false");
  await menu.click();
  await expect(sidebar).toHaveClass(/sidebar-open/);
  await expect(menu).toHaveAttribute("aria-expanded", "true");
  await menu.click();
  await expect(menu).toHaveAttribute("aria-expanded", "false");

  for (let interaction = 0; interaction < 3; interaction += 1) {
    await menu.click();
    await expect(menu).toHaveAttribute("aria-expanded", "true");
    await menu.click();
    await expect(menu).toHaveAttribute("aria-expanded", "false");
  }
});

test("closes mobile navigation when a navigation link is selected", async ({ page }) => {
  await page.goto("/#/app/overview");
  await expect(page.locator(".page-heading h1")).toBeVisible();
  const menu = page.getByRole("button", { name: "Toggle navigation" });
  await menu.click();
  await page.locator('#sidebar a[href="#/app/queue"]').click();

  await expect(page.locator(".page-heading h1")).toHaveText("My service queue");
  await expect(page.getByRole("button", { name: "Toggle navigation" })).toHaveAttribute("aria-expanded", "false");
});

test("closes mobile navigation after an outside tap", async ({ page }) => {
  await page.goto("/#/app/overview");
  await expect(page.locator(".page-heading h1")).toBeVisible();
  const menu = page.getByRole("button", { name: "Toggle navigation" });
  await menu.click();
  await expect(page.locator("#sidebar")).toHaveClass(/sidebar-open/);
  await page.mouse.click(350, 120);
  await expect(menu).toHaveAttribute("aria-expanded", "false");
});

test("closes mobile navigation on Escape", async ({ page }) => {
  await page.goto("/#/app/overview");
  await expect(page.locator(".page-heading h1")).toBeVisible();
  const menu = page.getByRole("button", { name: "Toggle navigation" });
  await menu.click();
  await page.keyboard.press("Escape");
  await expect(page.locator("#sidebar")).not.toHaveClass(/sidebar-open/);
  await expect(menu).toHaveAttribute("aria-expanded", "false");
  await expect(menu).toBeFocused();
});
