const { test, expect } = require("@playwright/test");

test("public page presents the editorial workshop story without implying sample data is live", async ({ page }) => {
  await page.goto("/");

  await expect(page.locator(".editorial-hero h1")).toContainText("Every service.");
  await expect(page.locator(".editorial-hero-image img")).toHaveAttribute("src", "/assets/workshop-documentary.jpg");
  await expect(page.locator(".work-order-stage")).toHaveCount(3);
  await expect(page.locator(".planning-example")).toContainText("Demonstration");
  await expect(page.locator(".operations-preview")).toContainText("Illustrative interface");
  await expect(page.locator(".benefit-story")).toContainText("The ML service predicts service duration only.");
  await expect(page.locator(".operations-preview")).toBeVisible();
  await expect(page.locator('.editorial-button[href="#story-work-order"]')).toBeVisible();
  await expect(page.locator('.editorial-button[href="#/login"]')).toBeVisible();
  await expect(page.locator('.editorial-link[href="#/register"]')).toBeVisible();
  await expect(page.locator('a[href="#/login"]').first()).toBeAttached();
});

test("FAQ disclosures retain native keyboard controls", async ({ page }) => {
  await page.goto("/");
  const item = page.locator(".editorial-faq details").first();
  const summary = item.locator("summary");

  await summary.click();
  await expect(item).toHaveAttribute("open", "");
  await summary.press("Enter");
  await expect(item).not.toHaveAttribute("open", "");
});

test("mobile navigation closes after selecting a section without horizontal overflow", async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto("/");

  await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth)).toBe(390);
  const menu = page.locator(".mobile-menu");
  await menu.click();
  await expect(menu).toHaveAttribute("aria-expanded", "true");
  await page.locator('.story-nav a[href="#story-workflow"]').click();
  await expect(menu).toHaveAttribute("aria-expanded", "false");
  await expect(page).toHaveURL(/#story-workflow$/);
});

test("public layout fits target viewport widths", async ({ page }) => {
  for (const [width, height] of [[1440, 900], [1280, 800], [768, 1024], [390, 844]]) {
    await page.setViewportSize({ width, height });
    await page.goto("/");
    await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth)).toBe(width);
    await expect(page.locator(".editorial-hero")).toBeVisible();
    await expect(page.locator(".work-order-story")).toBeVisible();
    await expect(page.locator(".planning-example")).toBeAttached();
  }
});



test("reduced-motion mode keeps public content visible without reveal animation", async ({ page }) => {
  await page.emulateMedia({ reducedMotion: "reduce" });
  await page.goto("/");
  await expect(page.locator(".planning-example")).toBeVisible();
  await expect(page.locator(".operations-preview")).toBeVisible();
  await expect(page.locator(".editorial-faq")).toBeVisible();
});


test("work order demonstrates a consistent workflow without calling operational APIs", async ({ page }) => {
  const apiCalls = [];
  page.on("request", request => { if (new URL(request.url()).pathname.startsWith("/api/")) apiCalls.push(request.url()); });
  await page.goto("/");
  await page.getByRole("link", { name: "Follow a work order" }).click();
  const order = page.getByRole("article", { name: "Demonstration service work order" });
  await expect(order).toBeVisible();
  await expect(order).toContainText("Demonstration");
  await expect(order).toContainText("1 of 3 completed");
  await expect(order.locator('[aria-current="step"]')).toContainText("Brake service");
  await expect(order.locator(".work-order-stage-completed")).toContainText(/Actual 18 min.*estimate 20 min/);
  await expect(order.locator(".work-order-stage-pending")).toContainText("Becomes eligible after brake service");
  await expect(order.locator(".work-order-resource")).toContainText("Busy");
  await expect(order.locator(".work-order-resource")).toContainText("No resource assigned");
  await expect(order.locator(".work-order-times")).toContainText("35 min");
  await expect(order.locator(".work-order-times")).toContainText("12 min");
  await expect(order.locator(".work-order-times")).toContainText("Service-type default");
  await expect(page).toHaveURL(/#story-work-order$/);
  expect(apiCalls).toEqual([]);
});

test("work order stays within the viewport and primary CTA supports keyboard navigation", async ({ page }) => {
  for (const [width, height] of [[1440,900], [1280,800], [390,844]]) {
    await page.setViewportSize({width, height});
    await page.goto("/");
    const link = page.getByRole("link", {name: "Follow a work order"});
    await link.focus();
    await page.keyboard.press("Enter");
    const order = page.locator(".work-order-sheet");
    await expect(order).toBeVisible();
    const bounds = await order.boundingBox();
    expect(bounds.x).toBeGreaterThanOrEqual(0);
    expect(bounds.x + bounds.width).toBeLessThanOrEqual(width);
    await expect(order.locator(".work-order-stage")).toHaveCount(3);
  }
});

test("public operational stories state ordering, priority, compatibility and timing accurately", async ({ page }) => {
  await page.goto("/");
  await expect(page.locator(".planning-story")).toContainText("Critical work gets first consideration");
  await expect(page.locator(".planning-example")).toContainText("Remains queued");
  await expect(page.locator(".planning-example")).toContainText("Can be assigned");
  await expect(page.locator(".history-ledger li")).toHaveCount(4);
  await expect(page.locator(".benefit-caveat")).toContainText("synthetic data");
});
