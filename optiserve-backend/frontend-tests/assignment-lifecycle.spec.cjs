const { test, expect } = require("@playwright/test");

const assignmentId = "assignment-persisted-1";

function assignment(status) {
  return {
    assignmentId,
    serviceRequestId: "request-1",
    stageId: "stage-1",
    vehicleRegistrationNumber: "KA01AB1234",
    serviceTypeName: "Oil change",
    priority: "NORMAL",
    resourceName: "Bay 1",
    assignmentStatus: status,
    assignedAt: "2026-03-01T10:00:00Z",
    startedAt: status === "IN_PROGRESS" ? "2026-03-01T10:10:00Z" : null,
    completedAt: status === "COMPLETED" ? "2026-03-01T10:40:00Z" : null,
  };
}

async function openAdminQueue(page, initialStatus = "ASSIGNED", role = "ADMIN") {
  let currentStatus = initialStatus;
  const calls = { start: 0, complete: 0, completionBody: null };
  await page.addInitScript(() => sessionStorage.setItem("optiserve.accessToken", "browser-test-token"));
  await page.route("**/api/**", async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    const method = request.method();
    let body = [];
    let status = 200;

    if (url.pathname === "/api/auth/me") body = { role };
    else if (url.pathname === "/api/customers/me") body = { id: "customer-1", name: "Test customer" };
    else if (url.pathname === "/api/vehicles") body = [];
    else if (url.pathname === "/api/service-requests" || url.pathname === "/api/queue"
      || url.pathname === "/api/resources" || url.pathname === "/api/service-types") body = [];
    else if (url.pathname === "/api/assignments" && method === "GET") body = currentStatus ? [assignment(currentStatus)] : [];
    else if (url.pathname === `/api/assignments/${assignmentId}/start` && method === "POST") {
      calls.start += 1;
      currentStatus = "IN_PROGRESS";
      body = { assignmentId, assignmentStatus: currentStatus };
    } else if (url.pathname === `/api/assignments/${assignmentId}/complete` && method === "POST") {
      calls.complete += 1;
      calls.completionBody = request.postDataJSON();
      currentStatus = "COMPLETED";
      body = { assignmentId, assignmentStatus: currentStatus };
    }

    await route.fulfill({ status, contentType: "application/json", body: JSON.stringify(body) });
  });
  await page.goto("/#/app/queue");
  await expect(page.getByRole("heading", { name: role === "ADMIN" ? "Queue sequence" : "Your waiting stages" })).toBeVisible();
  await expect(page.getByRole("heading", { name: "Assignment history" })).toBeVisible();
  return { calls };
}

test("restores Start service from the persisted assignment after refresh", async ({ page }) => {
  await openAdminQueue(page, "ASSIGNED");
  await page.reload();
  await expect(page.locator(`[data-action="start-assignment"][data-id="${assignmentId}"]`)).toBeVisible();
  await expect(page.getByText("Oil change")).toBeVisible();
});

test("does not render lifecycle controls for USER accounts", async ({ page }) => {
  await openAdminQueue(page, "ASSIGNED", "USER");
  await expect(page.getByRole("heading", { name: "My service queue" })).toBeVisible();
  await expect(page.locator('[data-action="start-assignment"], [data-action="complete-assignment"]')).toHaveCount(0);
});

test("starts an assignment, restores duration entry, completes it, and refreshes persisted status", async ({ page }) => {
  const { calls } = await openAdminQueue(page, "ASSIGNED");
  await page.getByRole("button", { name: "Start service" }).click();
  const duration = page.getByRole("spinbutton", { name: "Actual duration in minutes" });
  await expect(duration).toBeVisible();
  await expect(page.locator('[data-action="complete-assignment"]')).toBeEnabled();
  expect(calls.start).toBe(1);

  await page.reload();
  await expect(page.getByRole("spinbutton", { name: "Actual duration in minutes" })).toBeVisible();
  await page.getByRole("spinbutton", { name: "Actual duration in minutes" }).fill("32");
  await page.getByRole("button", { name: /Complete service/ }).click();
  await expect(page.getByText("COMPLETED", { exact: true })).toBeVisible();
  expect(calls.complete).toBe(1);
  expect(calls.completionBody).toEqual({ actualDurationMinutes: 32 });
  await expect(page.locator('[data-action="start-assignment"], [data-action="complete-assignment"]')).toHaveCount(0);
  await page.reload();
  await expect(page.getByText("COMPLETED", { exact: true })).toBeVisible();
  await expect(page.locator('[data-action="start-assignment"], [data-action="complete-assignment"]')).toHaveCount(0);
});

test("prevents duplicate start requests while in flight and shows API errors inline", async ({ page }) => {
  const { calls } = await openAdminQueue(page, "ASSIGNED");
  await page.unrouteAll({ behavior: "wait" });
  await page.route("**/api/**", async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    if (url.pathname === `/api/assignments/${assignmentId}/start`) {
      calls.start += 1;
      await new Promise((resolve) => setTimeout(resolve, 100));
      await route.fulfill({ status: 409, contentType: "application/json", body: JSON.stringify({ message: "Assignment cannot be started in its current state." }) });
      return;
    }
    if (url.pathname === "/api/auth/me") return route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({ role: "ADMIN" }) });
    if (url.pathname === "/api/assignments") return route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify([assignment("ASSIGNED")]) });
    return route.fulfill({ status: 200, contentType: "application/json", body: "[]" });
  });
  await page.reload();
  const start = page.getByRole("button", { name: "Start service" });
  await start.click();
  await expect(start).toBeDisabled();
  await expect(page.getByRole("alert")).toHaveText("Assignment cannot be started in its current state.");
  expect(calls.start).toBe(1);
});

test("prevents duplicate completion submissions and keeps the entered duration after an API error", async ({ page }) => {
  const { calls } = await openAdminQueue(page, "IN_PROGRESS");
  await page.unrouteAll({ behavior: "wait" });
  await page.route("**/api/**", async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    if (url.pathname === `/api/assignments/${assignmentId}/complete`) {
      calls.complete += 1;
      calls.completionBody = request.postDataJSON();
      await new Promise((resolve) => setTimeout(resolve, 100));
      await route.fulfill({ status: 409, contentType: "application/json", body: JSON.stringify({ message: "Assignment cannot be completed in its current state." }) });
      return;
    }
    if (url.pathname === "/api/auth/me") return route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({ role: "ADMIN" }) });
    if (url.pathname === "/api/assignments") return route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify([assignment("IN_PROGRESS")]) });
    return route.fulfill({ status: 200, contentType: "application/json", body: "[]" });
  });
  await page.reload();
  const duration = page.getByRole("spinbutton", { name: "Actual duration in minutes" });
  await duration.fill("41");
  const complete = page.getByRole("button", { name: /Complete service/ });
  await complete.click();
  await expect(complete).toBeDisabled();
  await expect(page.getByRole("alert")).toHaveText("Assignment cannot be completed in its current state.");
  expect(calls.complete).toBe(1);
  expect(calls.completionBody).toEqual({ actualDurationMinutes: 41 });
  await expect(page.getByRole("spinbutton", { name: "Actual duration in minutes" })).toHaveValue("41");
});
