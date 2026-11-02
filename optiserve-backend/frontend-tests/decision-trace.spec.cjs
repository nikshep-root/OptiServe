const { test, expect } = require("@playwright/test");

const decisionId = "1d51f163-6080-4b4e-91a4-32d8e1698e01";
const summary = {
  id: decisionId, evaluatedAt: "2026-10-21T09:30:00Z", trigger: "MANUAL_ASSIGN_NEXT", strategyName: "hybrid-priority",
  strategyVersion: "2", result: "ASSIGNED", reason: "ASSIGNED", selectedStageId: "e1764403-6106-4b68-8d15-c649ab4a2201",
  selectedResourceId: "ffb30ec1-4732-4915-89d3-fc608de47f01", candidateCount: 2, evaluatedCandidateCount: 2,
};
const candidate = (overrides = {}) => ({
  queueEntryId: "02c6c8e6-6f9d-4574-8bbb-9b4ce8e5e11a", serviceStageId: "e1764403-6106-4b68-8d15-c649ab4a2201",
  serviceRequestId: "54609ea6-f058-4160-b1b8-37c508f6b361", workflowId: "f5c9a005-5b73-4170-95a5-8e0ac971c713",
  evaluationOrder: 1, queueStatus: "WAITING", stageStatus: "QUEUED", priorityClass: "NORMAL", queuedAt: "2026-10-21T09:20:00Z",
  waitingAgeMinutes: 10, appointmentAt: null, appointmentDue: false, predictedServiceDuration: "PT45M",
  predictionSource: "ML_MODEL", critical: false, basePriorityPoints: 100, agingPoints: 10, appointmentBonusPoints: 0,
  durationBonusPoints: 15, finalScore: 125, feasibilityStatus: "FEASIBLE", observedResourceStatus: "AVAILABLE",
  matchedResourceId: "ffb30ec1-4732-4915-89d3-fc608de47f01", outcome: "SELECTED", reason: "SELECTED", ...overrides,
});
const detailFixture = { summary, candidates: [
  candidate({ evaluationOrder: 1, serviceStageId: "750ae241-1160-4b1a-a8a2-fde51db5b39e", priorityClass: "CRITICAL", critical: true,
    queueEntryId: "69a9fe9a-6ecc-4ec8-bfad-8553bc5f76c4", serviceRequestId: "970c9211-336f-4e7b-a9cf-8d6196209e84",
    queuedAt: "2026-10-21T09:20:00Z", waitingAgeMinutes: 10, appointmentAt: null, appointmentDue: false,
    basePriorityPoints: 0, agingPoints: 0, appointmentBonusPoints: 0, durationBonusPoints: 0, finalScore: 0,
    feasibilityStatus: "INFEASIBLE", observedResourceStatus: null, matchedResourceId: null, outcome: "SKIPPED", reason: "RESOURCE_UNAVAILABLE",
    predictedServiceDuration: null, predictionSource: null, appointmentAt: null, appointmentDue: false }),
  candidate({ evaluationOrder: 2 }),
] };
const historyFixture = (page = 0) => ({
  content: Array.from({ length: 25 }, (_, index) => {
    const position = page * 25 + index;
    const result = position === 0 || position % 3 === 1 ? "ASSIGNED" : position % 3 === 0 ? "NO_COMPATIBLE_RESOURCE" : "NO_QUEUED_STAGE";
    return {
      ...summary,
      id: position === 0 ? decisionId : `${String(position).padStart(8, "0")}-0000-4000-8000-${String(position + 1).padStart(12, "0")}`,
      evaluatedAt: new Date(Date.parse(summary.evaluatedAt) - position * 60_000).toISOString(),
      trigger: position % 2 ? "AUTO_RECONCILIATION" : "MANUAL_ASSIGN_NEXT",
      result,
      reason: result,
      selectedStageId: result === "ASSIGNED" ? summary.selectedStageId : null,
      selectedResourceId: result === "ASSIGNED" ? summary.selectedResourceId : null,
      candidateCount: result === "NO_QUEUED_STAGE" ? 0 : result === "NO_COMPATIBLE_RESOURCE" ? 1 : 2,
      evaluatedCandidateCount: result === "NO_QUEUED_STAGE" ? 0 : result === "NO_COMPATIBLE_RESOURCE" ? 1 : 2,
    };
  }),
  page, size: 25, totalElements: 51, totalPages: 3,
});

async function setup(page, { history = historyFixture, detail = detailFixture, decisionStatus = 200, delayDetail, role = "ADMIN" } = {}) {
  const requests = [];
  await page.addInitScript(() => {
    sessionStorage.setItem("optiserve.accessToken", "decision-test-token");
    sessionStorage.setItem("optiserve.role", "ADMIN");
  });
  await page.route("**/api/**", async (route) => {
    const url = new URL(route.request().url());
    const { pathname } = url;
    if (pathname === "/api/auth/me") return route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({ role }) });
    if (pathname.startsWith("/api/admin/scheduling/decisions")) {
      requests.push({ pathname, params: url.searchParams.toString() });
      if (delayDetail && pathname.endsWith(decisionId)) await delayDetail;
      const status = typeof decisionStatus === "function" ? decisionStatus(pathname) : decisionStatus;
      const body = status >= 400 ? { message: status === 403 ? "Administrator access is required." : status === 401 ? "Session expired." : "Decision service unavailable." }
        : pathname.endsWith(decisionId) ? detail : typeof history === "function" ? history(Number(url.searchParams.get("page") || 0)) : { ...history, page: Number(url.searchParams.get("page") ?? history.page ?? 0) };
      return route.fulfill({ status, contentType: "application/json", body: JSON.stringify(body) });
    }
    const body = pathname === "/api/admin/workshop-automation" ? { mode: "MANUAL" } : [];
    return route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(body) });
  });
  await page.goto("/#/app/decisions");
  return requests;
}

test("history renders persisted decision data and calls server pagination", async ({ page }, testInfo) => {
  await page.setViewportSize({ width: 1440, height: 1000 });
  const requests = await setup(page);
  await expect(page.locator(".page-heading h1")).toHaveText("Decision Trace");
  await expect(page.getByText("Assignment successful").first()).toBeVisible();
  await expect(page.getByText("2 / 2").first()).toBeVisible();
  await page.screenshot({ path: testInfo.outputPath("decision-trace-history.png"), fullPage: true });
  expect(requests[0].pathname).toBe("/api/admin/scheduling/decisions");
  expect(new URLSearchParams(requests[0].params).get("page")).toBe("0");
  expect(new URLSearchParams(requests[0].params).get("size")).toBe("25");
  await page.getByRole("button", { name: "Next" }).click();
  await expect.poll(() => requests.length).toBe(2);
  expect(new URLSearchParams(requests[1].params).get("page")).toBe("1");
  await expect(page.getByText("Page 2 of 3")).toBeVisible();
});

test("date, trigger and outcome filters use the supported API parameters", async ({ page }) => {
  const requests = await setup(page);
  await page.locator('input[name="from"]').fill("2026-10-01");
  await page.locator('input[name="to"]').fill("2026-10-21");
  await page.locator('select[name="trigger"]').selectOption("AUTO_RECONCILIATION");
  await page.locator('select[name="outcome"]').selectOption("NO_COMPATIBLE_RESOURCE");
  await page.getByRole("button", { name: "Apply filters" }).click();
  await expect.poll(() => requests.length).toBe(2);
  const params = new URLSearchParams(requests[1].params);
  expect(params.get("from")).toBe("2026-10-01T00:00:00Z");
  expect(params.get("to")).toBe("2026-10-22T00:00:00.000Z");
  expect(params.get("trigger")).toBe("AUTO_RECONCILIATION");
  expect(params.get("outcome")).toBe("NO_COMPATIBLE_RESOURCE");
  expect(params.get("page")).toBe("0");
});

test("decision detail preserves candidate order, exact score inputs, critical precedence and recorded skip reasons", async ({ page }, testInfo) => {
  await page.setViewportSize({ width: 1440, height: 1000 });
  const requests = await setup(page);
  await page.locator(`a[href="#/app/decisions/${decisionId}"]`).click();
  await expect(page.getByRole("heading", { name: "Assignment successful" })).toBeVisible();
  await expect(page.getByText("100 base + 10 aging + 0 appointment + 15 predicted-duration bonus = 125 points")).toBeVisible();
  await page.locator(".candidate-trace").nth(0).locator("summary").click();
  await expect(page.getByText("CRITICAL precedence is evaluated separately from the numeric score.")).toBeVisible();
  const order = await page.locator(".candidate-order").allTextContents();
  expect(order).toEqual(["01", "02"]);
  await expect(page.locator(".candidate-heading").nth(0)).toContainText("Critical");
  await expect(page.locator(".candidate-heading").nth(1)).toContainText("Normal");
  await expect(page.getByText("Resource unavailable").first()).toBeVisible();
  await expect(page.getByText("Evaluation continued to a later candidate, which was selected.")).toBeVisible();
  await page.addStyleTag({ content: ".app-sidebar,.app-topbar{position:relative!important;top:auto!important}.app-sidebar{height:auto!important;align-self:stretch}.skip-link{display:none!important}" });
  await page.evaluate(() => document.activeElement?.blur?.());
  await page.screenshot({ path: testInfo.outputPath("decision-trace-assigned-detail.png"), fullPage: true });
  expect(requests.at(-1).pathname).toBe(`/api/admin/scheduling/decisions/${decisionId}`);
});

test("no-compatible-resource decision displays its outcome without a selected candidate", async ({ page }, testInfo) => {
  await page.setViewportSize({ width: 1440, height: 1000 });
  const unsuccessful = { summary: { ...summary, result: "NO_COMPATIBLE_RESOURCE", reason: "NO_COMPATIBLE_RESOURCE", selectedStageId: null, selectedResourceId: null }, candidates: [
    candidate({ outcome: "SKIPPED", reason: "RESOURCE_UNAVAILABLE", feasibilityStatus: "INFEASIBLE", observedResourceStatus: null, matchedResourceId: null }),
    candidate({ evaluationOrder: 2, queueEntryId: "7f017d65-f335-4849-8807-84699a1c34ab", serviceStageId: "750ae241-1160-4b1a-a8a2-fde51db5b39e", serviceRequestId: "5364c394-d743-42a5-9180-ac3346e6f981", queuedAt: "2026-10-21T09:25:00Z", waitingAgeMinutes: 5, agingPoints: 5, finalScore: 120, outcome: "SKIPPED", reason: "RESOURCE_UNAVAILABLE", feasibilityStatus: "INFEASIBLE", observedResourceStatus: null, matchedResourceId: null }),
  ] };
  await setup(page, { detail: unsuccessful });
  await page.locator(`a[href="#/app/decisions/${decisionId}"]`).click();
  await expect(page.getByRole("heading", { name: "No compatible resource" })).toBeVisible();
  await expect(page.getByRole("heading", { name: "No candidate was selected" })).toBeVisible();
  await expect(page.getByRole("heading", { name: "Why couldn't OPTISERVE assign this work?" })).toBeVisible();
  await expect(page.getByText("All 2 candidates were evaluated and skipped.")).toBeVisible();
  await expect(page.locator(".skip-reasons li")).toHaveCount(2);
  await expect(page.locator(".skip-reasons")).toContainText("Skipped · Resource unavailable");
  await page.addStyleTag({ content: ".app-sidebar,.app-topbar{position:relative!important;top:auto!important}.app-sidebar{height:auto!important;align-self:stretch}.skip-link{display:none!important}" });
  await page.evaluate(() => document.activeElement?.blur?.());
  await page.screenshot({ path: testInfo.outputPath("decision-trace-no-compatible-resource.png"), fullPage: true });
});

test("no-queued-stage decision explains that no assignment was made", async ({ page }, testInfo) => {
  await page.setViewportSize({ width: 1440, height: 1000 });
  const noQueued = { summary: { ...summary, result: "NO_QUEUED_STAGE", reason: "NO_QUEUED_STAGE", selectedStageId: null,
    selectedResourceId: null, candidateCount: 0, evaluatedCandidateCount: 0 }, candidates: [] };
  await setup(page, { detail: noQueued });
  await page.locator(`a[href="#/app/decisions/${decisionId}"]`).click();
  await expect(page.getByRole("heading", { name: "Why was no assignment made?" })).toBeVisible();
  await expect(page.getByRole("heading", { name: "No candidate was selected" })).toBeVisible();
  await expect(page.getByText("No candidate evaluations were recorded for this decision.")).toBeVisible();
  await page.addStyleTag({ content: ".app-sidebar,.app-topbar{position:relative!important;top:auto!important}.app-sidebar{height:auto!important;align-self:stretch}.skip-link{display:none!important}" });
  await page.evaluate(() => document.activeElement?.blur?.());
  await page.screenshot({ path: testInfo.outputPath("decision-trace-no-queued-stage.png"), fullPage: true });
});

test("missing optional candidate fields render without crashing", async ({ page }) => {
  const sparse = { ...detailFixture, summary: { ...summary, strategyName: null, strategyVersion: null, selectedResourceId: null }, candidates: [candidate({
    appointmentAt: null, predictedServiceDuration: null, predictionSource: null, matchedResourceId: null, observedResourceStatus: null,
  })] };
  await setup(page, { detail: sparse });
  await page.locator(`a[href="#/app/decisions/${decisionId}"]`).click();
  await expect(page.getByText("Not recorded").first()).toBeVisible();
  await expect(page.getByRole("heading", { name: "Candidate evaluation timeline" })).toBeVisible();
});

test("empty history has an honest empty state", async ({ page }) => {
  await setup(page, { history: { content: [], page: 0, size: 25, totalElements: 0, totalPages: 0 } });
  await expect(page.getByRole("heading", { name: "No scheduling decisions in this range" })).toBeVisible();
  await expect(page.getByText("Showing 0–0 of 0")).toBeVisible();
});

test("decision API failure has a retry path and displays forbidden responses", async ({ page }) => {
  let calls = 0;
  const requests = await setup(page, { decisionStatus: () => { calls += 1; return calls === 1 ? 503 : calls === 3 ? 403 : 200; } });
  await expect(page.getByText("Decision service unavailable.")).toBeVisible();
  await page.getByRole("button", { name: "Retry" }).first().click();
  await expect(page.locator(".page-heading h1")).toHaveText("Decision Trace");
  expect(requests.length).toBe(2);

  const forbiddenPage = await page.context().newPage();
  await setup(forbiddenPage, { decisionStatus: 403 });
  await expect(forbiddenPage.getByText("Your account is not allowed to perform this action.")).toBeVisible();
});

test("decision endpoint 401 clears the session and redirects to sign in", async ({ page }) => {
  await setup(page, { decisionStatus: 401 });
  await expect(page).toHaveURL(/#\/login$/);
  await expect(page.getByRole("heading", { name: /welcome back/i })).toBeVisible();
  expect(await page.evaluate(() => sessionStorage.getItem("optiserve.accessToken"))).toBeNull();
});

test("rapid navigation ignores a late detail response and keeps the dashboard shell mounted", async ({ page }) => {
  await page.setViewportSize({ width: 1280, height: 900 });
  let release;
  const gate = new Promise((resolve) => { release = resolve; });
  const requests = await setup(page, { delayDetail: gate });
  await page.evaluate(() => { window.initialShell = document.querySelector(".app-shell"); });
  await page.locator(`a[href="#/app/decisions/${decisionId}"]`).click();
  await page.getByRole("link", { name: "Live queue" }).click();
  await expect(page.locator(".breadcrumbs strong")).toHaveText("Live queue");
  release();
  await expect(page.getByRole("heading", { name: "Assignment successful" })).toHaveCount(0);
  expect(await page.evaluate(() => document.querySelector(".app-shell") === window.initialShell)).toBeTruthy();
  expect(requests.some((request) => request.pathname.endsWith(decisionId))).toBeTruthy();
});

test("decision route is not exposed to customer navigation", async ({ page }) => {
  await setup(page, { role: "USER" });
  await expect(page.getByRole("link", { name: "Decision Trace" })).toHaveCount(0);
  await expect(page.getByRole("heading", { name: "My service overview" })).toBeVisible();
});
