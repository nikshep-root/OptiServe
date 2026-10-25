const { test, expect } = require("@playwright/test");

const request = {
  id: "request-1",
  vehicleRegistrationNumber: "KA01AB1234",
  priority: "NORMAL",
  appointmentTime: null,
  status: "CREATED",
  stages: [
    {
      id: "stage-1",
      serviceTypeId: "type-1",
      serviceTypeName: "Oil change",
      status: "QUEUED",
      predictedServiceDuration: "PT74M",
      predictionSource: "ML_MODEL",
    },
  ],
};

const assignment = {
  assignmentId: "assignment-1",
  serviceRequestId: request.id,
  stageId: "stage-1",
  vehicleRegistrationNumber: request.vehicleRegistrationNumber,
  serviceTypeName: "Oil change",
  priority: "NORMAL",
  resourceName: "Bay 1",
  assignmentStatus: "COMPLETED",
  assignedAt: "2026-06-01T10:00:00Z",
  startedAt: "2026-06-01T10:00:00Z",
  completedAt: "2026-06-01T11:22:00Z",
  predictedServiceDuration: "PT74M",
  actualServiceDuration: "PT82M",
  predictionSource: "DEFAULT_FALLBACK",
};

async function openDashboard(page, role, path) {
  await page.addInitScript((userRole) => {
    sessionStorage.setItem("optiserve.accessToken", "browser-test-token");
    sessionStorage.setItem("optiserve.role", userRole);
  }, role);
  await page.route("**/api/**", async (route) => {
    const url = new URL(route.request().url());
    let body = [];
    if (url.pathname === "/api/auth/me") body = { role };
    else if (url.pathname === "/api/customers/me") body = { id: "customer-1", name: "Test customer" };
    else if (url.pathname === "/api/vehicles" || url.pathname === "/api/resources"
      || url.pathname === "/api/service-types") body = [];
    else if (url.pathname === "/api/service-requests") body = [request];
    else if (url.pathname === "/api/queue") body = [{
      id: "queue-1", stageId: "stage-1", serviceRequestId: request.id,
      vehicleRegistrationNumber: request.vehicleRegistrationNumber,
      serviceTypeName: "Oil change", priority: "NORMAL", stageStatus: "QUEUED",
      predictedServiceDuration: "PT74M", predictionSource: "ML_MODEL",
    }];
    else if (url.pathname === "/api/assignments") body = role === "ADMIN" ? [assignment] : [];
    else if (url.pathname === "/api/queue/stages/stage-1/wait-time") body = { estimatedWaitMinutes: 19 };
    await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(body) });
  });
  await page.goto("/#/" + path);
}

test("customer request stages show persisted estimates without exposing provenance detail", async ({ page }) => {
  await openDashboard(page, "USER", "app/requests");

  await expect(page.getByText("Estimate · 1h 14m")).toBeVisible();
  await expect(page.getByText("ML model", { exact: true })).toHaveCount(0);
});

test("admin queue and assignment history show source and predicted-versus-actual comparison", async ({ page }) => {
  await openDashboard(page, "ADMIN", "app/queue");

  await expect(page.getByText("Expected service · 1h 14m · ML model")).toBeVisible();
  await expect(page.getByText("Estimate · 1h 14m")).toBeVisible();
  await expect(page.getByText("Actual · 1h 22m")).toBeVisible();
  await expect(page.getByText("8 min over estimate")).toBeVisible();
  await expect(page.getByText("service type default")).toBeVisible();
});
