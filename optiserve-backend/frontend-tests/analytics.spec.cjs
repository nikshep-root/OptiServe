const { test, expect } = require("@playwright/test");

const analyticsFixture = {
  fromDate: "2026-06-01",
  toDate: "2026-06-02",
  historical: {
    requestsCreated: 12,
    requestsCompleted: 3,
    averageActualServiceDurationMinutes: 42.5,
    meanAbsolutePredictionErrorMinutes: 6.25,
    predictionErrorSampleCount: 4,
  },
  current: {
    waitingQueueSize: 2,
    activeAssignments: 1,
    availableResources: 3,
    busyResources: 1,
    offlineResources: 1,
  },
  completionTrend: [
    { date: "2026-06-01", completedRequests: 1 },
    { date: "2026-06-02", completedRequests: 2 },
  ],
};

async function serveDashboard(page, role, analyticsResponse = analyticsFixture) {
  const analyticsRequests = [];
  await page.addInitScript(() => sessionStorage.setItem("optiserve.accessToken", "browser-test-token"));
  await page.route("**/api/**", async (route) => {
    const url = new URL(route.request().url());
    let body = [];
    let status = 200;
    if (url.pathname === "/api/auth/me") body = { role };
    else if (url.pathname === "/api/admin/analytics") {
      analyticsRequests.push(url.searchParams.toString());
      body = analyticsResponse;
      status = analyticsResponse?.status || 200;
      if (status >= 400) body = { message: "Analytics service is unavailable." };
    } else if (url.pathname === "/api/service-requests" || url.pathname === "/api/queue"
      || url.pathname === "/api/resources" || url.pathname === "/api/service-types"
      || url.pathname === "/api/assignments" || url.pathname === "/api/vehicles") body = [];
    else if (url.pathname === "/api/customers/me") body = { id: "customer-1", name: "Test customer" };
    await route.fulfill({ status, contentType: "application/json", body: JSON.stringify(body) });
  });
  await page.goto("/#/app/analytics");
  return analyticsRequests;
}

test("admin analytics renders historical metrics separately from current workshop state", async ({ page }) => {
  const requests = await serveDashboard(page, "ADMIN");
  await expect(page.getByRole("heading", { name: "Operational analytics" })).toBeVisible();
  await expect(page.getByText("REQUESTS CREATED")).toBeVisible();
  await expect(page.getByText("42.5")).toBeVisible();
  await expect(page.getByText("Mean absolute error · 4 samples")).toBeVisible();
  await expect(page.getByText("Live snapshot · not filtered by date range")).toBeVisible();
  await expect(page.getByText("Requests completed per UTC day")).toBeVisible();
  expect(requests).toHaveLength(1);
  expect(requests[0]).toMatch(/from=\d{4}-\d{2}-\d{2}&to=\d{4}-\d{2}-\d{2}/);

  await page.locator('input[name="from"]').fill("2026-06-01");
  await page.locator('input[name="to"]').fill("2026-06-02");
  await page.getByRole("button", { name: "Update range" }).click();
  await expect.poll(() => requests.length).toBe(2);
  expect(requests[1]).toBe("from=2026-06-01&to=2026-06-02");
});

test("analytics displays a useful empty completion state", async ({ page }) => {
  const empty = {
    ...analyticsFixture,
    historical: { requestsCreated: 0, requestsCompleted: 0, averageActualServiceDurationMinutes: null,
      meanAbsolutePredictionErrorMinutes: null, predictionErrorSampleCount: 0 },
    completionTrend: [{ date: "2026-06-01", completedRequests: 0 }],
  };
  await serveDashboard(page, "ADMIN", empty);
  await expect(page.getByRole("heading", { name: "No completions in this period" })).toBeVisible();
  await expect(page.getByText("0 samples")).toBeVisible();
});

test("customer role cannot open admin analytics or request the analytics endpoint", async ({ page }) => {
  const requests = await serveDashboard(page, "USER");
  await expect(page.getByRole("heading", { name: "My service overview" })).toBeVisible();
  await expect(page.getByRole("link", { name: "Analytics" })).toHaveCount(0);
  expect(requests).toHaveLength(0);
});

test("analytics API failures are shown with a retry action", async ({ page }) => {
  await serveDashboard(page, "ADMIN", { status: 503 });
  await expect(page.getByText("Analytics service is unavailable.")).toBeVisible();
  await expect(page.getByRole("button", { name: "Retry" })).toBeVisible();
});
