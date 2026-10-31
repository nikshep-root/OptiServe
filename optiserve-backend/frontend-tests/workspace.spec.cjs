const { test, expect } = require("@playwright/test");

const requestId = "request-demo-014";
const stageId = "stage-brake";
const assignmentId = "assignment-demo-014";

async function openWorkspace(page, role = "ADMIN", failQueue = false) {
  await page.addInitScript(() => sessionStorage.setItem("optiserve.accessToken", "workspace-test-token"));
  await page.route("**/api/**", async route => {
    const { pathname } = new URL(route.request().url());
    let status = 200;
    let body = [];
    if (pathname === "/api/auth/me") body = {role};
    else if (pathname === "/api/customers/me") body = {id:"customer-1", name:"Asha"};
    else if (pathname === "/api/vehicles") body = role === "USER" ? [{id:"vehicle-1",registrationNumber:"DEMO014",make:"Maruti Suzuki",model:"Swift",year:2022}] : [];
    else if (pathname === "/api/service-requests") body = [{id:requestId,vehicleId:"vehicle-1",vehicleRegistrationNumber:"DEMO014",priority:"NORMAL",status:"IN_SERVICE",workflowStatus:"ACTIVE",stages:[
      {id:"stage-inspection",sequenceNumber:1,serviceTypeName:"Inspection",status:"COMPLETED",predictedServiceDuration:"PT20M",predictionSource:"DEFAULT_FALLBACK"},
      {id:stageId,sequenceNumber:2,serviceTypeName:"Brake service",status:"IN_PROGRESS",predictedServiceDuration:"PT35M",predictionSource:"ML_MODEL"},
      {id:"stage-final",sequenceNumber:3,serviceTypeName:"Final check",status:"PENDING",predictedServiceDuration:"PT10M",predictionSource:"DEFAULT_FALLBACK"}
    ]}];
    else if (pathname === "/api/queue") { if (failQueue) status = 503; else body = []; }
    else if (pathname === "/api/service-types") body = [];
    else if (pathname === "/api/resources") body = [{id:"resource-1",name:"Brake bay",status:"BUSY",compatibleServiceTypes:[{id:"type-brake",name:"Brake service"}]}];
    else if (pathname === "/api/assignments") body = [{assignmentId,serviceRequestId:requestId,stageId,vehicleRegistrationNumber:"DEMO014",serviceTypeName:"Brake service",priority:"NORMAL",resourceName:"Brake bay",assignmentStatus:"IN_PROGRESS",assignedAt:"2026-10-31T08:00:00Z",startedAt:"2026-10-31T08:10:00Z",predictedServiceDuration:"PT35M",predictionSource:"ML_MODEL"}];
    else if (pathname === "/api/admin/workshop-automation") body = {mode:"MANUAL"};
    await route.fulfill({status,contentType:"application/json",body:JSON.stringify(status >= 400 ? {message:"Queue API unavailable"} : body)});
  });
  await page.goto("/#/app/overview");
}

test("ADMIN overview and active work use persisted assignment and request data", async ({page}) => {
  await openWorkspace(page);
  await expect(page.getByRole("heading", {name:"Workshop overview"})).toBeVisible();
  await expect(page.locator("#connection-label")).toHaveText("CONNECTED");
  await expect(page.locator(".request-table-hint")).toBeVisible();
  expect(await page.locator(".request-table-wrap").evaluate(node => node.scrollWidth > node.clientWidth)).toBeTruthy();
  await expect(page.getByRole("heading", {name:"Active assignments"})).toBeVisible();
  await expect(page.getByText("DEMO014 · Brake service")).toBeVisible();
  await page.getByRole("button", {name:"Toggle navigation"}).click();
  await page.getByRole("link", {name:"Active work"}).click();
  await expect(page.locator('[data-action="complete-assignment"]')).toBeVisible();
  await expect(page.locator(".duration-comparison")).toContainText("Estimate · 35 min");
});

test("work-order detail shows ordered progress and does not fabricate an unavailable wait", async ({page}) => {
  await openWorkspace(page,"USER");
  await page.goto(`/#/app/request/${requestId}`);
  const stages = page.locator(".detail-stage");
  await expect(stages).toHaveCount(3);
  await expect(stages.nth(0)).toContainText("Inspection");
  await expect(stages.nth(1)).toContainText("Brake service");
  await expect(stages.nth(1)).toContainText("Estimate · 35 min");
  await expect(stages.nth(1)).not.toContainText("ML model");
  await expect(stages.nth(2)).toContainText("Final check");
  await expect(page.getByText("Estimated wait:")).toHaveCount(0);
});

test("connection failure is visible and unavailable request counts are not shown as zero", async ({page}) => {
  await openWorkspace(page,"ADMIN",true);
  await expect(page.locator("#connection-label")).toHaveText("PARTIAL");
  await expect(page.locator(".partial-warning")).toContainText("data source");
  await expect(page.locator(".metric-grid")).toContainText("—");
  await expect(page.locator(".queue-panel")).toHaveCount(0);
});
