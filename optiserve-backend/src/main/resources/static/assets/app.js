const API = "";
const TOKEN_KEY = "optiserve.accessToken";
const ROLE_KEY = "optiserve.role";
const app = document.querySelector("#app");

const icon = (name, size = 18) => {
  const paths = {
    arrow: '<path d="M5 12h14M13 6l6 6-6 6"/>',
    grid: '<rect x="3" y="3" width="7" height="7" rx="1"/><rect x="14" y="3" width="7" height="7" rx="1"/><rect x="3" y="14" width="7" height="7" rx="1"/><rect x="14" y="14" width="7" height="7" rx="1"/>',
    layers: '<path d="m12 3 9 5-9 5-9-5 9-5Z"/><path d="m3 12 9 5 9-5M3 16l9 5 9-5"/>',
    clock: '<circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/>',
    wrench: '<path d="M14.7 6.3a5 5 0 0 0-6.4 6.4L3 18l3 3 5.3-5.3a5 5 0 0 0 6.4-6.4L14 12l-2-2 2.7-3.7Z"/>',
    search: '<circle cx="11" cy="11" r="7"/><path d="m20 20-4-4"/>',
    logout: '<path d="M10 17l5-5-5-5M15 12H3"/><path d="M12 3h6a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2h-6"/>',
    check: '<path d="m5 12 4 4L19 6"/>',
    plus: '<path d="M12 5v14M5 12h14"/>',
    menu: '<path d="M4 7h16M4 12h16M4 17h16"/>',
  };
  return `<svg width="${size}" height="${size}" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${paths[name] || paths.grid}</svg>`;
};

function escapeHtml(value = "") {
  return String(value).replace(/[&<>"']/g, (char) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[char]);
}

function formatDate(value) {
  if (!value) return "—";
  return new Intl.DateTimeFormat(undefined, { dateStyle: "medium", timeStyle: "short" }).format(new Date(value));
}

function displayError(error) {
  const fields = Object.entries(error?.fieldErrors || {}).map(([field, message]) => `${field}: ${message}`);
  return [error?.message || "The request could not be completed.", ...fields].join(" ");
}

function duration(seconds) {
  if (seconds == null || !Number.isFinite(Number(seconds))) return "—";
  const minutes = Math.round(Number(seconds) / 60);
  return minutes < 60 ? `${minutes} min` : `${Math.floor(minutes / 60)}h ${minutes % 60}m`;
}

function durationSeconds(isoDuration) {
  if (typeof isoDuration !== "string") return null;
  const match = isoDuration.match(/^PT(?:(\d+)H)?(?:(\d+)M)?(?:(\d+(?:\.\d+)?)S)?$/);
  if (!match) return null;
  return Number(match[1] || 0) * 3600 + Number(match[2] || 0) * 60 + Number(match[3] || 0);
}

function serviceDurationLabel(isoDuration) {
  const seconds = durationSeconds(isoDuration);
  return seconds === null ? null : duration(seconds);
}

function predictionSourceLabel(source) {
  if (source === "ML_MODEL") return "ML model";
  if (source === "DEFAULT_FALLBACK") return "service type default";
  return "source not recorded";
}

function stageEstimate(item, admin) {
  const estimate = serviceDurationLabel(item.predictedServiceDuration);
  if (!estimate) return "";
  return '<small class="stage-estimate">Estimate · ' + escapeHtml(estimate)
    + (admin ? " · " + escapeHtml(predictionSourceLabel(item.predictionSource)) : "")
    + "</small>";
}

async function api(path, options = {}) {
  const token = sessionStorage.getItem(TOKEN_KEY);
  let response;
  try {
    response = await fetch(`${API}${path}`, {
      ...options,
      headers: {
        ...(options.body ? { "Content-Type": "application/json" } : {}),
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
        ...options.headers,
      },
    });
  } catch {
    const error = new Error("Unable to reach the OPTISERVE API. Check your connection and try again.");
    error.status = 0;
    throw error;
  }
  if (response.status === 401 || response.status === 403) {
    const error = new Error(response.status === 401 ? "Your session has expired. Sign in again." : "Your account is not allowed to perform this action.");
    error.status = response.status;
    throw error;
  }
  if (!response.ok) {
    let payload = {};
    try { payload = await response.json(); } catch { /* The API can return an empty response. */ }
    const error = new Error(payload.message || `Request failed (${response.status}).`);
    error.status = response.status;
    error.fieldErrors = payload.fieldErrors || {};
    throw error;
  }
  if (response.status === 204) return null;
  return response.json();
}

function brand(light = false) {
  return `<a class="brand ${light ? "brand-light" : ""}" href="/" aria-label="OPTISERVE home"><span class="brand-mark" aria-hidden="true"><i></i><i></i><i></i></span><span>OPTI<span>SERVE</span></span></a>`;
}

let landingStoryCleanup = null;
let landingMenuCleanup = null;

function landing() {
  app.innerHTML = `
    <div class="marketing-shell story-shell">
      <div class="story-scroll-meter" aria-hidden="true"><span></span></div>
      <header class="story-header wrap">
        ${brand()}
        <button class="mobile-menu" aria-label="Open navigation" aria-controls="story-nav" aria-expanded="false">${icon("menu")}</button>
        <nav class="story-nav" id="story-nav" aria-label="Main navigation" inert>
          <a href="#story-system">The operation</a><a href="#story-workflow">Service journey</a><a href="#story-queue">Queue intelligence</a><a href="#story-resources">Resources</a><a href="#story-product">Platform</a><a href="#/login">Sign in</a><a class="story-menu-cta" href="#/register">Create account ${icon("arrow", 15)}</a>
        </nav>
      </header>
      <main id="main" class="story-main">
        <section class="story-hero">
          <picture class="story-hero-media" aria-hidden="true"><img src="/assets/workshop-hero.png" alt="" fetchpriority="high" decoding="async"></picture>
          <div class="story-hero-grid" aria-hidden="true"></div>
          <div class="story-hero-copy wrap">
            <div class="story-overline" data-reveal><i></i> INTELLIGENT AUTOMOTIVE OPERATIONS <span>— 01</span></div>
            <h1 data-reveal>Your workshop.<br><em>In sync.</em></h1>
            <p data-reveal>Bring requests, queue decisions, and compatible resources into one clear service operation.</p>
            <div class="story-actions" data-reveal><a class="story-cta" href="#/register">Explore the platform ${icon("arrow", 16)}</a><a class="story-text-link" href="#story-workflow">See how it works <span>↓</span></a></div>
          </div>
          <div class="story-hero-coordinates" aria-hidden="true"><span>WORKSHOP SYSTEMS / 01</span><i></i><span>VEHICLE → SERVICE → RESOURCE</span></div>
          <a class="story-scroll-cue" href="#story-system"><span>SCROLL TO EXPLORE</span><i></i></a>
        </section>
        <section class="story-system" id="story-system">
          <div class="wrap system-intro">
            <div class="story-section-index" data-reveal><span>01 / THE OPERATION</span><i></i></div>
            <div class="system-statement" data-reveal><p class="story-kicker">EVERY BAY IS CONNECTED TO THE NEXT DECISION</p><h2>Good service needs<br>a <em>clear chain of work.</em></h2><p class="story-body">A customer request becomes a set of service stages. Each stage meets a priority-aware queue and configured resource compatibility. The team sees what is waiting, what can move, and what needs a handoff.</p></div>
          </div>
          <div class="system-rail" aria-label="How OPTISERVE connects workshop operations"><div class="wrap system-rail-inner">
            <div class="system-rail-head"><span>FROM CUSTOMER REQUEST</span><i></i><span>TO WORKSHOP ASSIGNMENT</span></div>
            <div class="system-rail-flow"><div><small>01 / INTAKE</small><strong>Vehicle<br>+ services</strong><span>Customer request</span></div><b aria-hidden="true">→</b><div><small>02 / QUEUE</small><strong>Priority<br>+ eligibility</strong><span>Backend scheduler</span></div><b aria-hidden="true">→</b><div><small>03 / MATCH</small><strong>Service<br>+ capability</strong><span>Configured resources</span></div><b aria-hidden="true">→</b><div><small>04 / HANDOFF</small><strong>Assigned<br>+ in service</strong><span>Workflow stage</span></div></div>
          </div></div>
        </section>
        <section class="story-showcase" id="story-product">
          <div class="wrap"><div class="story-showcase-heading" data-reveal><div class="story-section-index"><span>02 / THE PRODUCT</span><i></i></div><p class="story-kicker">A WORKING VIEW, NOT A PRETTY REPORT</p><h2>Operations at<br><em>a glance.</em></h2><p class="story-body">Customer and workshop screens use live API responses after sign-in. Preview the shape of the experience below; the sample entries are illustrative.</p></div>
            <div class="story-product-frame" data-reveal><div class="story-product-top"><div class="story-product-brand">${brand(true)}<span>OPTISERVE / WORKSHOP</span></div><div class="story-product-actions"><span>PRODUCT PREVIEW / SAMPLE RECORDS</span><i></i><i></i><i></i></div></div><div class="story-product-body"><aside class="story-product-sidebar"><span class="sidebar-logo">O</span><button class="product-tab selected" type="button" aria-selected="true" data-product-view="queue">Queue</button><button class="product-tab" type="button" aria-selected="false" data-product-view="requests">Requests</button><button class="product-tab" type="button" aria-selected="false" data-product-view="resources">Resources</button><span class="product-sidebar-rule"></span><small>WORKSHOP ADMIN</small></aside><div class="story-product-main">
              <section class="product-view-panel is-active" data-view="queue"><div class="story-product-title"><div><small>OPERATIONS / PRIORITY QUEUE</small><h3>Workshop queue</h3></div><span class="sample-pill">ILLUSTRATIVE DATA</span></div><div class="product-summary-line"><span>Queue order follows backend scheduling rules.</span><span>Waiting time is calculated from current state.</span></div><div class="story-product-table"><div class="product-table-head"><span>SERVICE / VEHICLE</span><span>PRIORITY</span><span>STAGE</span><span>WAIT</span></div><div><span class="product-service-cell"><i class="product-table-dot"></i><b>Brake inspection</b><small>Vehicle sample 01</small></span><span class="product-status critical">CRITICAL</span><span>ELIGIBLE</span><span>Dynamic</span></div><div><span class="product-service-cell"><i class="product-table-dot dot-muted"></i><b>Oil service</b><small>Vehicle sample 02</small></span><span class="product-status">NORMAL</span><span>WAITING</span><span>Dynamic</span></div></div></section>
              <section class="product-view-panel" data-view="requests" hidden><div class="story-product-title"><div><small>WORKFLOW / SERVICE REQUESTS</small><h3>Requests in progress</h3></div><span class="sample-pill">ILLUSTRATIVE DATA</span></div><div class="product-summary-line"><span>Stage progress and request state from the service workflow.</span></div><div class="story-product-table"><div class="product-table-head"><span>VEHICLE / SERVICE</span><span>STAGE</span><span>REQUEST</span><span>APPOINTMENT</span></div><div><span class="product-service-cell"><i class="product-table-dot"></i><b>Vehicle sample 01</b><small>Oil service</small></span><span>IN PROGRESS</span><span class="product-status">ASSIGNED</span><span>—</span></div><div><span class="product-service-cell"><i class="product-table-dot dot-muted"></i><b>Vehicle sample 02</b><small>Inspection</small></span><span>UP NEXT</span><span class="product-status">WAITING</span><span>—</span></div></div></section>
              <section class="product-view-panel" data-view="resources" hidden><div class="story-product-title"><div><small>CAPACITY / COMPATIBILITY</small><h3>Resources</h3></div><span class="sample-pill">ILLUSTRATIVE DATA</span></div><div class="product-summary-line"><span>Compatibility is configured by an administrator.</span></div><div class="product-resource-preview"><article><span>RESOURCE / SAMPLE</span><strong>Workshop lift</strong><small>Compatible service types · availability checked during assignment</small><i>AVAILABLE</i></article><article><span>RESOURCE / SAMPLE</span><strong>Inspection bay</strong><small>Compatible service types · availability checked during assignment</small><i>BUSY</i></article><article><span>RESOURCE / SAMPLE</span><strong>Alignment station</strong><small>Compatible service types · availability checked during assignment</small><i>AVAILABLE</i></article></div></section>
            </div></div><div class="story-product-bottom"><span>ACTUAL REQUESTS, QUEUE, AND RESOURCES REQUIRE SIGN-IN</span><a href="#/login">Open OPTISERVE ${icon("arrow", 14)}</a></div></div>
          </div>
        </section>
        <section class="story-workflow" id="story-workflow">
          <div class="wrap story-workflow-head"><div class="story-section-index" data-reveal><span>03 / THE ORCHESTRATION</span><i></i></div><div class="workflow-heading-copy" data-reveal><p class="story-kicker">THE QUEUE MOVES. THE VIEW MOVES WITH IT.</p><h2>Five moments.<br><em>One service operation.</em></h2><p class="story-body">Scroll through a workshop request from intake to completion. The active operational view follows your position through the sequence.</p></div></div>
          <div class="story-workflow-layout wrap">
            <div class="story-workflow-visual" aria-live="polite" aria-atomic="true"><div class="story-workflow-visual-inner" id="workflow-visual" data-mode="request">
              <div class="workflow-visual-top"><span>OPTISERVE / WORKFLOW</span><span><b id="workflow-active-number">01</b> — 05</span></div>
              <div class="workflow-route" aria-hidden="true"><svg viewBox="0 0 600 170" preserveAspectRatio="none"><path class="route-base" d="M28 123 C108 123 108 40 190 40 S270 123 350 123 432 40 512 40 552 84 572 84"/><path class="route-progress" d="M28 123 C108 123 108 40 190 40 S270 123 350 123 432 40 512 40 552 84 572 84" pathLength="100"/></svg><div class="route-node node-1"><i></i><span>REQUEST</span></div><div class="route-node node-2"><i></i><span>PRIORITY</span></div><div class="route-node node-3"><i></i><span>RESOURCE</span></div><div class="route-node node-4"><i></i><span>IN SERVICE</span></div><div class="route-node node-5"><i></i><span>COMPLETE</span></div><div class="route-vehicle">${icon("wrench", 22)}</div></div>
              <div class="workflow-active-copy"><small id="workflow-active-label">REQUEST / RECEIVED</small><h3 id="workflow-active-title">Request received</h3><p id="workflow-active-caption">A customer selects a vehicle and the service types needed for the visit.</p></div>
              <div class="workflow-visual-foot"><span>SCROLL POSITION / LIVE</span><span id="workflow-active-step">01 / 05</span></div>
            </div></div>
            <div class="story-stage-list" aria-label="OPTISERVE service operation steps">
              <button type="button" class="story-stage is-active" aria-current="step" data-stage="01" data-mode="request" data-label="REQUEST / RECEIVED" data-title="Request received" data-caption="A customer selects a vehicle and the service types needed for the visit."><span>01</span><span class="stage-copy"><strong>Request received</strong><small>Vehicle, selected services, and appointment details enter one request.</small></span><span class="stage-tag">INTAKE</span></button>
              <button type="button" class="story-stage" data-stage="02" data-mode="priority" data-label="QUEUE / PRIORITIZED" data-title="Priority in context" data-caption="Priority, eligibility, and time in queue inform the next scheduling decision."><span>02</span><span class="stage-copy"><strong>Priority in context</strong><small>The scheduler weighs priority, eligible work, and queue aging.</small></span><span class="stage-tag">SCHEDULING</span></button>
              <button type="button" class="story-stage" data-stage="03" data-mode="resource" data-label="RESOURCE / COMPATIBLE" data-title="Capability matched" data-caption="The assignment flow checks configured compatibility and resource availability."><span>03</span><span class="stage-copy"><strong>Capability matched</strong><small>Configured service compatibility shapes which resources can be assigned.</small></span><span class="stage-tag">ASSIGNMENT</span></button>
              <button type="button" class="story-stage" data-stage="04" data-mode="service" data-label="WORKFLOW / IN SERVICE" data-title="Service in progress" data-caption="The assigned stage advances through the existing workshop workflow."><span>04</span><span class="stage-copy"><strong>Service in progress</strong><small>Assignment and stage state stay visible to the workshop team.</small></span><span class="stage-tag">WORKFLOW</span></button>
              <button type="button" class="story-stage" data-stage="05" data-mode="complete" data-label="WORKFLOW / COMPLETED" data-title="Completed and ready" data-caption="Completion closes the stage and updates the request for the customer."><span>05</span><span class="stage-copy"><strong>Completed and ready</strong><small>The team records completion; the customer can follow the request.</small></span><span class="stage-tag">HANDOFF</span></button>
            </div>
          </div>
        </section>
        <section class="story-queue" id="story-queue">
          <div class="wrap story-queue-layout">
            <div class="story-queue-copy" data-reveal><div class="story-section-index"><span>04 / QUEUE INTELLIGENCE</span><i></i></div><p class="story-kicker">ORDER WITH CONTEXT</p><h2>Priority is a rule.<br><em>Waiting is a process.</em></h2><p class="story-body">OPTISERVE’s hybrid scheduler uses priority classes, stage eligibility, queue aging, and defined tie-break rules. The waiting estimate is recalculated separately from current queue and resource state.</p><a class="story-text-link" href="#story-product">Explore the workshop view <span>↘</span></a></div>
            <div class="story-queue-scene" data-reveal><div class="queue-console"><div class="queue-console-head"><div><span>WORKSHOP / QUEUE</span><strong>Service stages</strong></div><span class="demo-flag">ILLUSTRATIVE DATA</span></div><div class="queue-console-columns"><span>POSITION / SERVICE</span><span>PRIORITY</span><span>STAGE</span></div>
              <article class="story-queue-card queue-card-priority"><span class="story-queue-position">01</span><div><strong>Brake inspection</strong><small>Vehicle sample 01</small></div><span class="priority-chip critical">CRITICAL</span><span class="stage-chip">ELIGIBLE</span></article>
              <article class="story-queue-card"><span class="story-queue-position">02</span><div><strong>Oil service</strong><small>Vehicle sample 02</small></div><span class="priority-chip appointment">APPOINTMENT</span><span class="stage-chip">WAITING</span></article>
              <article class="story-queue-card"><span class="story-queue-position">03</span><div><strong>Vehicle inspection</strong><small>Vehicle sample 03</small></div><span class="priority-chip">NORMAL</span><span class="stage-chip">PENDING</span></article>
              <div class="queue-console-foot"><span><i></i> Sequence shown for illustration</span><span>WAIT ESTIMATE / BACKEND-CALCULATED</span></div>
            </div><div class="queue-console-orbit" aria-hidden="true"></div></div>
          </div>
        </section>
        <section class="story-resources" id="story-resources">
          <div class="wrap story-resource-layout"><div class="story-resource-copy" data-reveal><div class="story-section-index"><span>05 / RESOURCE ORCHESTRATION</span><i></i></div><p class="story-kicker">A MATCH IS CONFIGURED, THEN CHECKED</p><h2>Service type<br>meets <em>capability.</em></h2><p class="story-body">An administrator connects resources to compatible service types. At assignment time, OPTISERVE checks that configuration and current availability; the server owns the assignment decision.</p><p class="resource-honesty">CONCEPTUAL DIAGRAM / NOT LIVE RESOURCE STATUS</p></div>
            <div class="story-resource-map" data-reveal><div class="resource-map-label">COMPATIBILITY PATH / WORKSHOP CONFIGURATION</div><div class="resource-map-service"><span class="map-node-icon">${icon("layers", 19)}</span><div><small>SERVICE TYPE</small><strong>Configured service</strong></div></div><svg class="resource-connectors" viewBox="0 0 500 320" preserveAspectRatio="none" aria-hidden="true"><path class="connector connector-active" d="M130 65 C250 65 230 158 370 158"/><path class="connector" d="M130 65 C250 65 230 255 370 255"/><path class="connector connector-muted" d="M130 65 C250 65 230 62 370 62"/></svg><div class="resource-map-bay bay-compatible"><span class="bay-led"></span><div><small>COMPATIBILITY / SET BY ADMIN</small><strong>Workshop resource</strong></div><span class="bay-status">MATCH</span></div><div class="resource-map-bay"><span class="bay-led"></span><div><small>AVAILABILITY / CHECKED AT ASSIGNMENT</small><strong>Workshop resource</strong></div><span class="bay-status">CHECK</span></div><div class="resource-map-bay bay-unavailable"><span class="bay-led"></span><div><small>INCOMPATIBLE SERVICE TYPE</small><strong>Excluded from match</strong></div><span class="bay-status">SKIP</span></div><div class="resource-map-note">Names and states above are illustrative. Actual resources load from the workshop API.</div></div></div>
        </section>
        <section class="story-intelligence" id="story-intelligence">
          <div class="wrap"><div class="story-intel-heading" data-reveal><div class="story-section-index"><span>06 / DURATION &amp; WAIT</span><i></i></div><p class="story-kicker">TWO CLOCKS. TWO DIFFERENT JOBS.</p><h2>Plan the work.<br><em>Recalculate the wait.</em></h2></div>
            <div class="intelligence-ledger" data-reveal><div class="intelligence-row"><span class="ledger-index">01</span><div><small>SERVICE DURATION / CURRENT BACKEND</small><h3>Service type default</h3><p>A deterministic duration comes from the configured service type. The separate FastAPI model is not connected to this Spring Boot runtime.</p></div><span class="ledger-state">ACTIVE</span></div><div class="intelligence-row ledger-planned"><span class="ledger-index">02</span><div><small>ML SERVICE / SEPARATE PROJECT</small><h3>Duration prediction</h3><p>The FastAPI predictor exists as a separate service; the current backend does not call it. No ML prediction is shown as live here.</p></div><span class="ledger-state">NOT INTEGRATED</span></div><div class="intelligence-row ledger-live"><span class="ledger-index">03</span><div><small>WAITING TIME / BACKEND CALCULATION</small><h3>Dynamic estimate</h3><p>The backend recalculates estimated wait from current queue and resource availability.</p></div><span class="ledger-state">OPERATIONAL</span></div></div>
          </div>
        </section>
        <section class="story-feature-scrub" id="story-features" aria-label="Workshop product features">
          <div class="story-feature-pin">
            <div class="wrap story-feature-heading"><div class="story-section-index"><span>07 / THE WORKSHOP VIEW</span><i></i></div><div><p class="story-kicker">ONE OPERATION, SEEN FROM EVERY ANGLE</p><h2>Built for the<br><em>next decision.</em></h2></div></div>
            <div class="story-feature-viewport"><div class="story-feature-track">
              <article class="story-feature-panel feature-queue-panel"><div class="feature-panel-meta"><span>01 / QUEUE</span><span>ILLUSTRATIVE VIEW</span></div><div class="feature-panel-copy"><small>PRIORITY, ELIGIBILITY, AGING</small><h3>See what<br>can move.</h3><p>Queue order follows the backend scheduler and current workflow state.</p></div><div class="feature-queue-visual"><div class="feature-queue-top"><span>WORKSHOP QUEUE</span><b>03 STAGES</b></div><div class="feature-queue-row active"><i>01</i><span><b>Brake inspection</b><small>CRITICAL · ELIGIBLE</small></span><em>READY</em></div><div class="feature-queue-row"><i>02</i><span><b>Oil service</b><small>NORMAL · WAITING</small></span><em>IN LINE</em></div><div class="feature-queue-row"><i>03</i><span><b>Vehicle inspection</b><small>APPOINTMENT · PENDING</small></span><em>UP NEXT</em></div></div><div class="feature-panel-index">01 <span>—</span> 04</div></article>
              <article class="story-feature-panel feature-resource-panel"><div class="feature-panel-meta"><span>02 / CAPABILITY</span><span>ILLUSTRATIVE VIEW</span></div><div class="feature-panel-copy"><small>COMPATIBILITY BEFORE ASSIGNMENT</small><h3>Match the<br>work to the bay.</h3><p>Configured service compatibility shapes eligible resource assignment.</p></div><div class="feature-resource-visual"><div class="feature-service-node"><small>SERVICE TYPE</small><b>Brake inspection</b></div><div class="feature-links"><i></i><i></i><i></i></div><div class="feature-bays"><span class="match"><i></i><b>Inspection bay</b><small>COMPATIBLE</small></span><span><i></i><b>General lift</b><small>AVAILABLE</small></span><span class="unmatched"><i></i><b>Paint booth</b><small>NOT COMPATIBLE</small></span></div></div><div class="feature-panel-index">02 <span>—</span> 04</div></article>
              <article class="story-feature-panel feature-stage-panel"><div class="feature-panel-meta"><span>03 / SERVICE STAGE</span><span>ILLUSTRATIVE VIEW</span></div><div class="feature-panel-copy"><small>ONE REQUEST, CLEAR HANDOFFS</small><h3>Keep each<br>stage in view.</h3><p>Follow the workflow from a pending stage through assignment and completion.</p></div><div class="feature-stage-visual"><div class="feature-stage-path"><span class="done"><i>${icon("check", 14)}</i><b>Request</b><small>RECEIVED</small></span><span class="done"><i>${icon("check", 14)}</i><b>Queue</b><small>PRIORITIZED</small></span><span class="current"><i>03</i><b>Assignment</b><small>RESOURCE MATCH</small></span><span><i>04</i><b>Service</b><small>IN PROGRESS</small></span><span><i>05</i><b>Complete</b><small>READY</small></span></div></div><div class="feature-panel-index">03 <span>—</span> 04</div></article>
              <article class="story-feature-panel feature-wait-panel"><div class="feature-panel-meta"><span>04 / WAIT ESTIMATE</span><span>ILLUSTRATIVE VIEW</span></div><div class="feature-panel-copy"><small>A LIVE WORKSHOP CALCULATION</small><h3>Keep the wait<br>in context.</h3><p>The backend recalculates waiting estimates from current queue and resource state.</p></div><div class="feature-wait-visual"><div class="wait-estimate-label">WAITING ESTIMATE <span>BACKEND-CALCULATED</span></div><div class="wait-dial"><div><small>EXAMPLE</small><strong>—</strong><span>No live value shown</span></div></div><div class="wait-factors"><span>QUEUE ORDER <i></i></span><span>RESOURCE STATE <i></i></span><span>SERVICE TYPE DEFAULT <i></i></span></div></div><div class="feature-panel-index">04 <span>—</span> 04</div></article>
            </div></div>
            <div class="story-feature-controls wrap"><span>SCROLL TO EXPLORE</span><div role="group" aria-label="Choose workshop feature">${[0, 1, 2, 3].map((index) => `<button type="button" data-feature-index="${index}" aria-label="Show feature ${index + 1}" aria-current="${index === 0 ? "step" : "false"}">0${index + 1}</button>`).join("")}</div></div>
          </div>
        </section>
        <section class="story-final"><div class="story-final-grid" aria-hidden="true"></div><div class="wrap story-final-inner" data-reveal><div class="story-section-index"><span>08 / YOUR WORKSHOP</span><i></i></div><p class="story-kicker">FROM REQUEST TO READY</p><h2>Make every<br>handoff <em>clear.</em></h2><p>Sign in to follow customer requests or coordinate the workshop from one shared operating view.</p><div class="story-actions"><a class="story-cta" href="#/register">Create a customer account ${icon("arrow", 16)}</a><a class="story-text-link" href="#/login">Workshop admin? Sign in <span>↗</span></a></div></div></section>
      </main>
      <footer class="story-footer wrap"><div>${brand()}<p>Clearer workshop operations,<br>from intake through handoff.</p></div><nav class="story-footer-links" aria-label="Footer"><a href="#story-workflow">Orchestration</a><a href="#story-product">Platform</a><a href="#/login">Sign in</a></nav><span>© ${new Date().getFullYear()} OPTISERVE</span></footer>
    </div>`;
  const menuButton = document.querySelector(".mobile-menu");
  const nav = document.querySelector(".story-nav");
  const setMenuOpen = (open, returnFocus = false) => {
    nav?.classList.toggle("nav-open", open);
    if (nav) nav.inert = !open;
    menuButton?.setAttribute("aria-expanded", String(open));
    menuButton?.setAttribute("aria-label", open ? "Close navigation" : "Open navigation");
    if (returnFocus) menuButton?.focus();
  };
  const onMenuClick = () => {
    const open = !nav?.classList.contains("nav-open");
    setMenuOpen(open);
    if (open) nav?.querySelector("a")?.focus();
  };
  const onEscape = (event) => {
    if (event.key === "Escape" && nav?.classList.contains("nav-open")) setMenuOpen(false, true);
  };
  const navLinks = [...(nav?.querySelectorAll("a") || [])];
  const onNavLinkClick = () => setMenuOpen(false, true);
  menuButton?.addEventListener("click", onMenuClick);
  window.addEventListener("keydown", onEscape);
  navLinks.forEach((link) => link.addEventListener("click", onNavLinkClick));
  landingMenuCleanup = () => {
    menuButton?.removeEventListener("click", onMenuClick);
    window.removeEventListener("keydown", onEscape);
    navLinks.forEach((link) => link.removeEventListener("click", onNavLinkClick));
  };
  document.querySelector(".story-hero-media img")?.addEventListener("error", (event) => event.currentTarget.closest("picture")?.classList.add("media-unavailable"), { once: true });
  document.querySelectorAll("[data-product-view]").forEach((button) => button.addEventListener("click", () => {
    const selected = button.dataset.productView;
    document.querySelectorAll("[data-product-view]").forEach((tab) => {
      const isSelected = tab === button;
      tab.classList.toggle("selected", isSelected);
      tab.setAttribute("aria-selected", String(isSelected));
    });
    document.querySelectorAll("[data-view]").forEach((panel) => {
      const isSelected = panel.dataset.view === selected;
      panel.hidden = !isSelected;
      panel.classList.toggle("is-active", isSelected);
    });
  }));
  initLandingStoryMotion();
}

function initLandingStoryMotion() {
  const reducedMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
  const revealElements = [...document.querySelectorAll("[data-reveal]")];
  const stages = [...document.querySelectorAll(".story-stage")];
  const header = document.querySelector(".story-header");
  const featureSection = document.querySelector(".story-feature-scrub");
  const featureViewport = document.querySelector(".story-feature-viewport");
  const featureTrack = document.querySelector(".story-feature-track");
  const featurePanels = [...document.querySelectorAll(".story-feature-panel")];
  const featureButtons = [...document.querySelectorAll("[data-feature-index]")];
  let revealObserver;
  let stageObserver;
  let animationFrame = 0;
  let activeStage = null;
  let activeFeature = -1;
  const setActiveStage = (stage) => {
    if (!stage || stage === activeStage) return;
    activeStage = stage;
    stages.forEach((item, index) => {
      const selected = item === stage;
      item.classList.toggle("is-active", selected);
      if (selected) item.setAttribute("aria-current", "step");
      else item.removeAttribute("aria-current");
      if (selected) document.querySelector("#workflow-visual")?.style.setProperty("--active-step", String(index));
    });
    const visual = document.querySelector("#workflow-visual");
    visual?.classList.add("is-changing");
    if (visual) window.setTimeout(() => visual.classList.remove("is-changing"), reducedMotion ? 0 : 220);
    visual?.setAttribute("data-mode", stage.dataset.mode || "request");
    const title = document.querySelector("#workflow-active-title");
    const caption = document.querySelector("#workflow-active-caption");
    const label = document.querySelector("#workflow-active-label");
    const number = document.querySelector("#workflow-active-number");
    const step = document.querySelector("#workflow-active-step");
    if (title) title.textContent = stage.dataset.title || "";
    if (caption) caption.textContent = stage.dataset.caption || "";
    if (label) label.textContent = stage.dataset.label || "";
    if (number) number.textContent = stage.dataset.stage || "01";
    if (step) step.textContent = `${stage.dataset.stage || "01"} / ${String(stages.length).padStart(2, "0")}`;
  };
  const onStageClick = (event) => {
    const stage = event.currentTarget;
    setActiveStage(stage);
    stage.scrollIntoView({ behavior: reducedMotion ? "auto" : "smooth", block: "center" });
  };
  const setActiveFeature = (index) => {
    const selected = Math.max(0, Math.min(featurePanels.length - 1, index));
    if (selected === activeFeature) return;
    activeFeature = selected;
    featureButtons.forEach((button, buttonIndex) => {
      button.setAttribute("aria-current", buttonIndex === selected ? "step" : "false");
    });
    featurePanels.forEach((panel, panelIndex) => panel.classList.toggle("is-current", panelIndex === selected));
  };
  const onFeatureButtonClick = (event) => {
    const index = Number(event.currentTarget.dataset.featureIndex);
    if (!Number.isFinite(index) || !featurePanels[index]) return;
    if (window.matchMedia("(max-width: 820px)").matches) {
      const panelLeft = featurePanels[index].getBoundingClientRect().left - (featureViewport?.getBoundingClientRect().left || 0) + (featureViewport?.scrollLeft || 0);
      featureViewport?.scrollTo({ left: panelLeft, behavior: reducedMotion ? "auto" : "smooth" });
      setActiveFeature(index);
      return;
    }
    const pin = featureSection?.querySelector(".story-feature-pin");
    const range = Math.max(0, (featureSection?.offsetHeight || 0) - (pin?.offsetHeight || window.innerHeight));
    const progress = featurePanels.length < 2 ? 0 : index / (featurePanels.length - 1);
    if (featureSection) window.scrollTo({ top: window.scrollY + featureSection.getBoundingClientRect().top + range * progress, behavior: reducedMotion ? "auto" : "smooth" });
  };
  const updateProgress = () => {
    animationFrame = 0;
    const documentHeight = document.documentElement.scrollHeight - window.innerHeight;
    document.documentElement.style.setProperty("--page-progress", documentHeight > 0 ? String(window.scrollY / documentHeight) : "0");
    header?.classList.toggle("is-scrolled", window.scrollY > 24);
    if (!reducedMotion) document.documentElement.style.setProperty("--hero-parallax", `${Math.min(window.scrollY * 0.025, 18)}px`);
    if (!featureSection || !featureViewport || !featureTrack || !featurePanels.length) return;
    if (reducedMotion) {
      featureTrack.style.removeProperty("--carousel-shift");
      return;
    }
    if (window.matchMedia("(max-width: 820px)").matches) {
      const maxScroll = featureViewport.scrollWidth - featureViewport.clientWidth;
      const progress = maxScroll > 0 ? featureViewport.scrollLeft / maxScroll : 0;
      setActiveFeature(Math.round(progress * (featurePanels.length - 1)));
      featureTrack.style.removeProperty("--carousel-shift");
    } else {
      const pin = featureSection.querySelector(".story-feature-pin");
      const range = Math.max(1, featureSection.offsetHeight - (pin?.offsetHeight || window.innerHeight));
      const progress = Math.max(0, Math.min(1, -featureSection.getBoundingClientRect().top / range));
      const maxShift = Math.max(0, featureTrack.scrollWidth - featureViewport.clientWidth);
      featureTrack.style.setProperty("--carousel-shift", `${maxShift * progress}px`);
      setActiveFeature(Math.min(featurePanels.length - 1, Math.floor(progress * featurePanels.length)));
    }
  };
  const requestProgressUpdate = () => {
    if (!animationFrame) animationFrame = window.requestAnimationFrame(updateProgress);
  };

  if (!reducedMotion && "IntersectionObserver" in window) {
    document.documentElement.classList.add("story-motion-ready");
    revealObserver = new IntersectionObserver((entries) => entries.forEach((entry) => {
      if (entry.isIntersecting) {
        entry.target.classList.add("is-visible");
        revealObserver.unobserve(entry.target);
      }
    }), { threshold: 0.01, rootMargin: "10% 0px 14% 0px" });
    revealElements.forEach((element) => revealObserver.observe(element));
    stageObserver = new IntersectionObserver((entries) => {
      const active = entries.filter((entry) => entry.isIntersecting).sort((a, b) => b.intersectionRatio - a.intersectionRatio)[0];
      if (active) setActiveStage(active.target);
    }, { threshold: [0.2, 0.45, 0.7], rootMargin: "-24% 0px -24% 0px" });
    stages.forEach((stage) => stageObserver.observe(stage));
  } else {
    revealElements.forEach((element) => element.classList.add("is-visible"));
    if ("IntersectionObserver" in window) {
      stageObserver = new IntersectionObserver((entries) => {
        const active = entries.filter((entry) => entry.isIntersecting).sort((a, b) => b.intersectionRatio - a.intersectionRatio)[0];
        if (active) setActiveStage(active.target);
      }, { threshold: [0.2, 0.45, 0.7], rootMargin: "-24% 0px -24% 0px" });
      stages.forEach((stage) => stageObserver.observe(stage));
    }
  }

  stages.forEach((stage) => stage.addEventListener("click", onStageClick));
  featureButtons.forEach((button) => button.addEventListener("click", onFeatureButtonClick));
  if (stages[0]) setActiveStage(stages[0]);
  setActiveFeature(0);
  window.addEventListener("scroll", requestProgressUpdate, { passive: true });
  window.addEventListener("resize", requestProgressUpdate, { passive: true });
  featureViewport?.addEventListener("scroll", requestProgressUpdate, { passive: true });
  updateProgress();
  landingStoryCleanup = () => {
    revealObserver?.disconnect();
    stageObserver?.disconnect();
    stages.forEach((stage) => stage.removeEventListener("click", onStageClick));
    featureButtons.forEach((button) => button.removeEventListener("click", onFeatureButtonClick));
    window.removeEventListener("scroll", requestProgressUpdate);
    window.removeEventListener("resize", requestProgressUpdate);
    featureViewport?.removeEventListener("scroll", requestProgressUpdate);
    if (animationFrame) window.cancelAnimationFrame(animationFrame);
    document.documentElement.classList.remove("story-motion-ready");
    document.documentElement.style.removeProperty("--hero-parallax");
  };
}

function authPage(mode = "login") {
  const registering = mode === "register";
  let registrationTokenReady = false;
  app.innerHTML = `<main id="main" class="auth-shell"><a class="auth-back" href="/">← Back to OPTISERVE</a><section class="auth-panel"><div class="auth-brand">${brand()}</div><div class="eyebrow"><span class="eyebrow-dot"></span> WORKSHOP OPERATIONS</div><h1>${registering ? "Start with a" : "Welcome"}<br><em>${registering ? "clearer view." : "back."}</em></h1><p class="auth-intro">${registering ? "Create an operations account to explore the OPTISERVE workspace." : "Sign in to your OPTISERVE operations workspace."}</p>
    <form id="auth-form" class="auth-form">${registering ? '<label>Your name<input type="text" name="name" autocomplete="name" required maxlength="200" placeholder="Full name"></label><label>Phone <span class="field-optional">OPTIONAL</span><input type="tel" name="phone" autocomplete="tel" maxlength="32" placeholder="Contact number"></label>' : ""}<label>Email address<input type="email" name="email" autocomplete="email" required maxlength="320" placeholder="you@example.com"></label><label>Password<input type="password" name="password" autocomplete="${registering ? "new-password" : "current-password"}" required minlength="8" maxlength="72" placeholder="At least 8 characters"></label><button class="button button-lime auth-submit" type="submit">${registering ? "Create customer account" : "Sign in"} ${icon("arrow", 16)}</button><p id="auth-error" class="form-error" role="alert"></p></form>
    <p class="auth-switch">${registering ? "Already have an account?" : "New to OPTISERVE?"} <a href="#/${registering ? "login" : "register"}">${registering ? "Sign in" : "Create an account"}</a></p><p class="auth-note">Public registration creates customer accounts only. Workshop administrator access is provisioned separately.</p></section><aside class="auth-aside"><span class="aside-index">OPERATIONS / 01</span><div class="aside-rule"></div><p>Clarity at every<br><em>service stage.</em></p><div class="aside-flow"><span>REQUEST</span><i></i><span>QUEUE</span><i></i><span>ASSIGN</span><i></i><span>COMPLETE</span></div></aside></main>`;
  document.querySelector("#auth-form").addEventListener("submit", async (event) => {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const button = event.currentTarget.querySelector("button");
    const error = document.querySelector("#auth-error");
    button.disabled = true;
    button.innerHTML = "Connecting…";
    error.textContent = "";
    try {
      if (registering && !registrationTokenReady) {
        const token = await api("/api/auth/register", { method: "POST", body: JSON.stringify({ email: form.get("email"), password: form.get("password") }) });
        sessionStorage.setItem(TOKEN_KEY, token.accessToken);
        registrationTokenReady = true;
      } else if (!registering) {
        const token = await api("/api/auth/login", { method: "POST", body: JSON.stringify({ email: form.get("email"), password: form.get("password") }) });
        sessionStorage.setItem(TOKEN_KEY, token.accessToken);
        const session = await api("/api/auth/me");
        sessionStorage.setItem(ROLE_KEY, session.role);
      }
      if (registering) {
        button.innerHTML = "Saving profile…";
        await api("/api/customers/me", { method: "PUT", body: JSON.stringify({ name: form.get("name"), phone: form.get("phone") || null }) });
        sessionStorage.setItem(ROLE_KEY, "USER");
      }
      location.hash = sessionStorage.getItem(ROLE_KEY) === "ADMIN" ? "#/app/overview" : "#/app";
    } catch (cause) {
      error.textContent = cause.message || "Could not connect to OPTISERVE. Please try again.";
      button.disabled = false;
      button.innerHTML = `${registering ? (registrationTokenReady ? "Retry profile setup" : "Create account") : "Sign in"} ${icon("arrow", 16)}`;
    }
  });
}

function dashboardShell(active = "overview") {
  const admin = sessionStorage.getItem(ROLE_KEY) === "ADMIN";
  const nav = admin
    ? [["overview", "Workshop overview", "grid"], ["analytics", "Analytics", "grid"], ["requests", "Service requests", "layers"], ["queue", "Live queue", "clock"], ["resources", "Resources", "wrench"], ["service-types", "Service types", "layers"]]
    : [["overview", "Overview", "grid"], ["requests", "Service requests", "layers"], ["queue", "My service queue", "clock"], ["vehicles", "My vehicles", "wrench"], ["profile", "Profile", "grid"]];
  return `<div class="app-shell"><aside class="app-sidebar" id="sidebar"><div class="sidebar-brand">${brand(true)}<span>OPERATIONS</span></div><div class="sidebar-caption">WORKSPACE</div><nav class="app-nav" aria-label="Operations navigation">${nav.map(([id, label, glyph]) => `<a href="#/app/${id}" class="${active === id ? "selected" : ""}" ${active === id ? 'aria-current="page"' : ""}>${icon(glyph, 17)}<span>${label}</span>${id === "queue" ? '<i class="nav-dot"></i>' : ""}</a>`).join("")}</nav><div class="sidebar-spacer"></div><div class="sidebar-foot"><div class="system-state"><i></i><span>API CONNECTION</span><b id="connection-label">CHECKING</b></div><button id="sign-out" class="sign-out">${icon("logout", 16)} Sign out</button></div></aside><div class="app-main"><header class="app-topbar"><button class="topbar-menu" id="menu-toggle" type="button" aria-label="Toggle navigation" aria-controls="sidebar" aria-expanded="false">${icon("menu")}</button><div class="breadcrumbs"><span>OPTISERVE</span><i>/</i><strong>${nav.find(([id]) => id === active)?.[1] || "Overview"}</strong></div><div class="topbar-right"><span class="topbar-date">${new Intl.DateTimeFormat(undefined, { weekday: "long", month: "short", day: "numeric" }).format(new Date())}</span><span class="avatar">OP</span></div></header><main class="dashboard-content" id="dashboard-content"><div class="loading-state"><span class="spinner"></span><p>Connecting to your workshop…</p></div></main></div></div>`;
}

let dashboardData = { customer: null, vehicles: [], requests: [], queue: [], resources: [], serviceTypes: [], assignments: [], analytics: null, waits: {}, errors: {} };
let dashboardNavigationCleanup = null;
let activeDashboardPage = "overview";
let analyticsRange = null;

function defaultAnalyticsRange() {
  const to = new Date();
  const from = new Date(to);
  from.setDate(from.getDate() - 29);
  const dateValue = (date) => date.toISOString().slice(0, 10);
  return { from: dateValue(from), to: dateValue(to) };
}

async function loadDashboard() {
  dashboardData.errors = {};
  dashboardData.waits = {};
  const admin = sessionStorage.getItem(ROLE_KEY) === "ADMIN";
  const outcomes = await Promise.allSettled([
    admin ? Promise.resolve(null) : api("/api/customers/me"),
    admin ? Promise.resolve([]) : api("/api/vehicles"),
    api("/api/service-requests"), api("/api/queue"), api("/api/resources"), api("/api/service-types"), api("/api/assignments"),
    admin && activeDashboardPage === "analytics"
      ? api(`/api/admin/analytics?from=${encodeURIComponent(analyticsRange.from)}&to=${encodeURIComponent(analyticsRange.to)}`)
      : Promise.resolve(null),
  ]);
  const names = ["customer", "vehicles", "requests", "queue", "resources", "serviceTypes", "assignments", "analytics"];
  outcomes.forEach((result, index) => {
    if (result.status === "fulfilled") dashboardData[names[index]] = result.value;
    else {
      dashboardData.errors[names[index]] = result.reason;
      dashboardData[names[index]] = names[index] === "customer" || names[index] === "analytics" ? null : [];
    }
  });
  if (outcomes.some((result) => result.status === "rejected" && result.reason.status === 401)) {
    sessionStorage.removeItem(TOKEN_KEY);
    sessionStorage.removeItem(ROLE_KEY);
    location.hash = "#/login";
    return;
  }
  if (!admin && dashboardData.errors.customer?.status === 404) {
    renderCustomerProfileSetup();
    return;
  }
  document.querySelector("#connection-label").textContent = outcomes.some((result) => result.status === "fulfilled") ? "CONNECTED" : "UNAVAILABLE";
  document.querySelector(".system-state")?.classList.toggle("disconnected", outcomes.every((result) => result.status === "rejected"));
  const queueEntries = dashboardData.queue;
  await Promise.all(queueEntries.map(async (entry) => {
    try { dashboardData.waits[entry.stageId] = await api(`/api/queue/stages/${entry.stageId}/wait-time`); }
    catch (error) { dashboardData.waits[entry.stageId] = { error: error.message }; }
  }));
  renderDashboard(location.hash.split("/")[2] || "overview");
}

function renderCustomerProfileSetup() {
  document.querySelector("#dashboard-content").innerHTML = `<section class="panel profile-setup"><span class="panel-kicker">ACCOUNT / PROFILE</span><h2>Finish setting up your customer profile</h2><p>Add your name before registering a vehicle or creating a service request.</p><form id="profile-form" class="auth-form"><label>Your name<input name="name" autocomplete="name" required maxlength="200"></label><label>Phone <span class="field-optional">OPTIONAL</span><input type="tel" name="phone" autocomplete="tel" maxlength="32"></label><button class="button button-lime" type="submit">Save profile</button><p class="form-error" id="profile-error" role="alert"></p></form></section>`;
  document.querySelector("#profile-form").addEventListener("submit", async (event) => {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const button = event.currentTarget.querySelector("button");
    const error = document.querySelector("#profile-error");
    button.disabled = true;
    try {
      await api("/api/customers/me", { method: "PUT", body: JSON.stringify({ name: form.get("name"), phone: form.get("phone") || null }) });
      await loadDashboard();
    } catch (cause) {
      error.textContent = displayError(cause);
      button.disabled = false;
    }
  });
}

function profilePage() {
  if (dashboardData.errors.customer) return errorPanel("Profile unavailable", dashboardData.errors.customer.message);
  const customer = dashboardData.customer || {};
  return `<section class="panel profile-setup"><span class="panel-kicker">ACCOUNT / PROFILE</span><h2>Your customer profile</h2><p>Update the contact details used for your OPTISERVE account.</p><form id="profile-form" class="auth-form"><label>Your name<input name="name" autocomplete="name" required maxlength="200" value="${escapeHtml(customer.name || "")}"></label><label>Phone <span class="field-optional">OPTIONAL</span><input type="tel" name="phone" autocomplete="tel" maxlength="32" value="${escapeHtml(customer.phone || "")}"></label><button class="button button-lime" type="submit">Save profile</button><p class="form-error" id="profile-error" role="alert"></p></form></section>`;
}

function vehiclesPage() {
  if (dashboardData.errors.vehicles) return errorPanel("Vehicles unavailable", dashboardData.errors.vehicles.message);
  const rows = dashboardData.vehicles.map((vehicle) => `<tr><td><span class="table-primary">${escapeHtml(vehicle.registrationNumber)}</span></td><td>${escapeHtml(vehicle.make)} ${escapeHtml(vehicle.model)}</td><td>${escapeHtml(vehicle.year)}</td><td><div class="panel-actions"><button class="button button-outline" data-action="edit-vehicle" data-id="${vehicle.id}">Edit</button><button class="button button-outline" data-action="delete-vehicle" data-id="${vehicle.id}">Delete</button></div></td></tr>`).join("");
  return `<section class="panel"><div class="panel-heading"><div><span class="panel-kicker">GARAGE / ACCOUNT</span><h2>My vehicles</h2><p>Vehicles registered to your profile. Vehicles with service history are retained.</p></div><button class="button button-lime" data-action="add-vehicle">Add vehicle ${icon("plus", 15)}</button></div>${rows ? `<div class="table-wrap"><table><thead><tr><th>REGISTRATION</th><th>VEHICLE</th><th>YEAR</th><th>ACTIONS</th></tr></thead><tbody>${rows}</tbody></table></div>` : `<div class="empty-state"><h3>No vehicles registered</h3><p>Add a vehicle to create a service request.</p></div>`}</section>`;
}

function assignmentsPanel() {
  if (dashboardData.errors.assignments) return errorPanel("Assignments unavailable", dashboardData.errors.assignments.message);
  if (!dashboardData.assignments.length) return '<section class="panel"><div class="panel-heading"><div><span class="panel-kicker">SERVICE / ASSIGNMENTS</span><h2>Assignment history</h2></div></div><div class="empty-inline"><span>' + icon("check", 18) + '</span><div><strong>No assignments yet</strong><p>Assignments for your service requests will appear here.</p></div></div></section>';
  const admin = sessionStorage.getItem(ROLE_KEY) === "ADMIN";
  const rows = dashboardData.assignments.map((item) => "<tr><td>" + escapeHtml(item.vehicleRegistrationNumber)
    + "</td><td>" + escapeHtml(item.serviceTypeName) + "</td><td>" + escapeHtml(item.resourceName) + "</td>"
    + '<td><span class="status-tag status-' + escapeHtml(String(item.assignmentStatus).toLowerCase()) + '">'
    + escapeHtml(item.assignmentStatus.replaceAll("_", " ")) + "</span></td><td>"
    + assignmentDurationSummary(item, admin) + "</td><td>" + escapeHtml(formatDate(item.assignedAt)) + "</td>"
    + (admin ? "<td>" + assignmentControls(item) + "</td>" : "") + "</tr>").join("");
  return '<section class="panel"><div class="panel-heading"><div><span class="panel-kicker">SERVICE / ASSIGNMENTS</span><h2>Assignment history</h2>'
    + '<p>Assignment state is refreshed from the backend. Actual duration appears after completion.</p></div></div>'
    + '<div class="table-wrap"><table><thead><tr><th>VEHICLE</th><th>SERVICE</th><th>RESOURCE</th><th>STATUS</th><th>DURATION</th><th>ASSIGNED</th>'
    + (admin ? "<th>ACTIONS</th>" : "") + "</tr></thead><tbody>" + rows + "</tbody></table></div></section>";
}

function assignmentDurationSummary(item, admin) {
  const predicted = serviceDurationLabel(item.predictedServiceDuration);
  const actual = serviceDurationLabel(item.actualServiceDuration);
  const comparison = predicted && actual ? durationDifference(item.predictedServiceDuration, item.actualServiceDuration) : "";
  const source = admin && predicted ? "<small>" + escapeHtml(predictionSourceLabel(item.predictionSource)) + "</small>" : "";
  return '<span class="duration-comparison">'
    + (predicted ? "<span>Estimate · " + escapeHtml(predicted) + "</span>" : "<span>Estimate unavailable</span>")
    + (actual ? "<span>Actual · " + escapeHtml(actual) + "</span>" : "")
    + (comparison ? "<small>" + escapeHtml(comparison) + "</small>" : "") + source + "</span>";
}

function durationDifference(predictedIso, actualIso) {
  const predicted = durationSeconds(predictedIso);
  const actual = durationSeconds(actualIso);
  if (predicted === null || actual === null) return "";
  const difference = Math.round(Math.abs(actual - predicted) / 60);
  return actual > predicted ? difference + " min over estimate"
    : actual < predicted ? difference + " min under estimate" : "On estimate";
}
const assignmentActionsInFlight = new Set();
const assignmentActionErrors = new Map();
const assignmentDurationDrafts = new Map();

function assignmentControls(assignment) {
  const id = escapeHtml(assignment.assignmentId);
  const busy = assignmentActionsInFlight.has(assignment.assignmentId);
  const error = assignmentActionErrors.get(assignment.assignmentId);
  let controls = "—";
  if (assignment.assignmentStatus === "ASSIGNED") {
    controls = `<button class="button button-outline" data-action="start-assignment" data-id="${id}" ${busy ? "disabled" : ""}>Start service</button>`;
  } else if (assignment.assignmentStatus === "IN_PROGRESS") {
    controls = `<div class="assignment-actions"><label class="duration-input">Actual minutes <input type="number" min="1" step="1" required placeholder="Minutes" aria-label="Actual duration in minutes" data-duration-for="${id}" value="${escapeHtml(assignmentDurationDrafts.get(assignment.assignmentId) || "")}" ${busy ? "disabled" : ""}></label><button class="button button-lime" data-action="complete-assignment" data-id="${id}" ${busy ? "disabled" : ""}>Complete service ${icon("check", 15)}</button></div>`;
  }
  return `<div class="assignment-controls">${controls}${error ? `<p class="assignment-action-error" role="alert">${escapeHtml(error)}</p>` : ""}</div>`;
}

function serviceTypesPage() {
  if (dashboardData.errors.serviceTypes) return errorPanel("Service types unavailable", dashboardData.errors.serviceTypes.message);
  const rows = dashboardData.serviceTypes.map((type) => `<tr><td><span class="table-primary">${escapeHtml(type.name)}</span><span class="table-secondary">${escapeHtml(type.description || "No description")}</span></td><td>${duration(type.defaultServiceDurationSeconds)}</td><td><span class="status-tag status-${type.active ? "available" : "offline"}">${type.active ? "ACTIVE" : "INACTIVE"}</span></td><td><div class="panel-actions"><button class="button button-outline" data-action="edit-service-type" data-id="${type.id}">Edit</button><button class="button button-outline" data-action="delete-service-type" data-id="${type.id}">Delete</button></div></td></tr>`).join("");
  return `<section class="panel"><div class="panel-heading"><div><span class="panel-kicker">CONFIGURATION / SERVICES</span><h2>Service types</h2><p>Active service types are available to customers when they create a request.</p></div><button class="button button-lime" data-action="add-service-type">Add service type ${icon("plus", 15)}</button></div>${rows ? `<div class="table-wrap"><table><thead><tr><th>SERVICE</th><th>DEFAULT DURATION</th><th>AVAILABILITY</th><th>ACTIONS</th></tr></thead><tbody>${rows}</tbody></table></div>` : `<div class="empty-state"><h3>No service types configured</h3><p>Add an active service type so customers can select it in their service request.</p></div>`}</section>`;
}

function resourceManagementPage() {
  if (dashboardData.errors.resources) return errorPanel("Resources unavailable", dashboardData.errors.resources.message);
  if (dashboardData.errors.serviceTypes) return errorPanel("Service types unavailable", dashboardData.errors.serviceTypes.message);
  const cards = dashboardData.resources.map((resource) => {
    const compatibleIds = new Set(resource.compatibleServiceTypes.map((type) => type.id));
    const options = dashboardData.serviceTypes.filter((type) => !compatibleIds.has(type.id));
    return `<article class="resource-card"><div class="resource-head"><span class="resource-id">RESOURCE / ${escapeHtml(resource.id.slice(0, 4).toUpperCase())}</span><i class="resource-state ${String(resource.status).toLowerCase()}"></i></div><h3>${escapeHtml(resource.name)}</h3><label class="field-hint">Resource status<select data-action="resource-status" data-id="${resource.id}">${["AVAILABLE", "BUSY", "OFFLINE"].map((status) => `<option value="${status}" ${resource.status === status ? "selected" : ""}>${status.replaceAll("_", " ")}</option>`).join("")}</select></label><div class="panel-actions"><button class="button button-outline" data-action="edit-resource" data-id="${resource.id}">Rename</button><button class="button button-outline" data-action="delete-resource" data-id="${resource.id}">Delete</button></div><div class="resource-compat"><small>COMPATIBLE SERVICES</small><div>${resource.compatibleServiceTypes.length ? resource.compatibleServiceTypes.map((type) => `<span>${escapeHtml(type.name)} <button type="button" data-action="remove-compatibility" data-resource="${resource.id}" data-type="${type.id}" aria-label="Remove ${escapeHtml(type.name)} compatibility">×</button></span>`).join("") : "<span>No compatible service types</span>"}</div>${options.length ? `<div class="panel-actions"><select data-compat-select="${resource.id}" aria-label="Add compatible service type"><option value="">Choose service type</option>${options.map((type) => `<option value="${type.id}">${escapeHtml(type.name)}</option>`).join("")}</select><button class="button button-outline" data-action="add-compatibility" data-resource="${resource.id}">Add compatibility</button></div>` : ""}</div></article>`;
  }).join("");
  return `<section class="panel"><div class="panel-heading"><div><span class="panel-kicker">WORKSHOP / CAPACITY</span><h2>Resource management</h2><p>Set availability and service compatibility for each workshop resource.</p></div><button class="button button-lime" data-action="add-resource">Add resource ${icon("plus", 15)}</button></div>${cards ? `<div class="resource-grid">${cards}</div>` : `<div class="empty-state"><h3>No resources configured</h3><p>Add a workshop resource, then assign compatible service types.</p></div>`}</section>`;
}

function analyticsPage() {
  const analytics = dashboardData.analytics;
  const rangeForm = `<form class="analytics-range" id="analytics-range-form"><label>From<input type="date" name="from" required value="${escapeHtml(analyticsRange.from)}"></label><label>To<input type="date" name="to" required value="${escapeHtml(analyticsRange.to)}"></label><button class="button button-outline" type="submit">Update range</button></form>`;
  const header = `<section class="panel analytics-panel"><div class="panel-heading"><div><span class="panel-kicker">HISTORICAL / SELECTED PERIOD</span><h2>Workshop performance</h2><p>UTC dates · ${escapeHtml(analyticsRange.from)} to ${escapeHtml(analyticsRange.to)}</p></div>${rangeForm}</div>`;
  if (dashboardData.errors.analytics) return `${header}${errorPanel("Analytics unavailable", displayError(dashboardData.errors.analytics))}</section>`;
  if (!analytics) return `${header}<div class="loading-state"><span class="spinner"></span><p>Loading workshop analytics…</p></div></section>`;
  const historical = analytics.historical;
  const current = analytics.current;
  const maxTrend = Math.max(1, ...analytics.completionTrend.map((point) => point.completedRequests));
  const trend = analytics.completionTrend.map((point, index) => {
    const label = index === 0 || index === analytics.completionTrend.length - 1 || new Date(`${point.date}T00:00:00Z`).getUTCDate() === 1;
    const height = point.completedRequests === 0 ? 2 : Math.max(8, Math.round((point.completedRequests / maxTrend) * 100));
    return `<div class="analytics-trend-day" role="listitem" title="${escapeHtml(point.date)}: ${point.completedRequests} completed"><span class="analytics-bar" style="--bar-height:${height}%"></span>${label ? `<small>${escapeHtml(point.date.slice(5))}</small>` : ""}<span class="sr-only">${escapeHtml(point.date)}: ${point.completedRequests} completed requests</span></div>`;
  }).join("");
  return `${header}<div class="analytics-metrics"><article class="analytics-metric"><span>REQUESTS CREATED</span><strong>${historical.requestsCreated}</strong><small>Requested during the selected period</small></article><article class="analytics-metric"><span>REQUESTS COMPLETED</span><strong>${historical.requestsCompleted}</strong><small>Final workflow stage completed in period</small></article><article class="analytics-metric"><span>AVG ACTUAL DURATION</span><strong>${historical.averageActualServiceDurationMinutes == null ? "—" : `${Number(historical.averageActualServiceDurationMinutes).toFixed(1)}<i>min</i>`}</strong><small>Completed assignments with recorded actual duration</small></article><article class="analytics-metric"><span>PREDICTION ERROR · MAE</span><strong>${historical.meanAbsolutePredictionErrorMinutes == null ? "—" : `${Number(historical.meanAbsolutePredictionErrorMinutes).toFixed(1)}<i>min</i>`}</strong><small>Mean absolute error · ${historical.predictionErrorSampleCount} samples</small></article></div><div class="analytics-live" role="region" aria-label="Current workshop state"><div><span>WAITING QUEUE</span><strong>${current.waitingQueueSize}</strong></div><div><span>ACTIVE ASSIGNMENTS</span><strong>${current.activeAssignments}</strong></div><div><span>AVAILABLE RESOURCES</span><strong>${current.availableResources}</strong></div><div><span>BUSY RESOURCES</span><strong>${current.busyResources}</strong></div><div><span>OFFLINE RESOURCES</span><strong>${current.offlineResources}</strong></div><small>Live snapshot · not filtered by date range</small></div><section class="analytics-trend-panel"><div class="analytics-section-heading"><div><span class="panel-kicker">COMPLETION / DAILY</span><h3>Completed service requests</h3></div><span>Requests completed per UTC day</span></div>${analytics.completionTrend.every((point) => point.completedRequests === 0) ? `<div class="empty-state analytics-empty"><h3>No completions in this period</h3><p>Completed requests will appear here when their final workflow stage is completed.</p></div>` : `<div class="analytics-trend-scroll"><div class="analytics-trend" style="--trend-days:${analytics.completionTrend.length}" role="list" aria-label="Daily completed service requests from ${escapeHtml(analyticsRange.from)} through ${escapeHtml(analyticsRange.to)}">${trend}</div></div>`}</section></section>`;
}

function requestRow(request) {
  const stage = request.stages?.[0];
  const admin = sessionStorage.getItem(ROLE_KEY) === "ADMIN";
  const progress = request.stages?.map((item) => '<span class="stage-pill stage-' + escapeHtml(String(item.status).toLowerCase()) + '"><span>'
    + escapeHtml(item.serviceTypeName) + ' <i>' + escapeHtml(item.status.replaceAll("_", " ")) + "</i></span>"
    + stageEstimate(item, admin)
    + (item.status === "ELIGIBLE" ? ' <button class="stage-queue-action" data-action="queue-stage" data-stage="' + escapeHtml(item.id) + '">Queue</button>' : "")
    + "</span>").join("") || "—";
  return '<tr data-status="' + escapeHtml(request.status) + '" data-search="'
    + escapeHtml((request.vehicleRegistrationNumber + " " + (stage?.serviceTypeName || "") + " " + request.status + " " + request.priority).toLowerCase())
    + '"><td><span class="table-primary">' + escapeHtml(request.vehicleRegistrationNumber)
    + '</span><span class="table-secondary">' + escapeHtml(request.id.slice(0, 8).toUpperCase())
    + '</span></td><td><span class="table-primary">' + escapeHtml(stage?.serviceTypeName || "Service request")
    + '</span><span class="table-secondary">' + (request.stages?.length || 0) + " workflow stage"
    + (request.stages?.length === 1 ? "" : "s") + '</span></td><td><div class="stage-list">' + progress
    + '</div></td><td><span class="status-tag status-' + escapeHtml(String(request.status).toLowerCase()) + '">'
    + escapeHtml(request.status.replaceAll("_", " ")) + '</span></td><td><span class="priority-text">'
    + escapeHtml(request.priority) + '</span></td><td>' + escapeHtml(formatDate(request.appointmentTime)) + "</td></tr>";
}

function requestTable() {
  const customerAccount = sessionStorage.getItem(ROLE_KEY) !== "ADMIN";
  if (dashboardData.errors.requests) return errorPanel("Service requests could not be loaded", dashboardData.errors.requests.message);
  if (!dashboardData.requests.length) return '<div class="empty-state"><div class="empty-icon">' + icon("layers", 20)
    + '</div><h3>No service requests yet</h3><p>Requests from the existing OPTISERVE API will appear here once created.</p>'
    + (customerAccount ? '<button class="button button-outline" data-action="new-request">Create a request ' + icon("arrow", 15) + "</button>" : "")
    + "</div>";
  const rows = dashboardData.requests.map((request) => requestRow(request)).join("");
  return '<div class="table-wrap"><table><thead><tr><th>VEHICLE</th><th>SERVICE</th><th>WORKFLOW STAGES</th><th>STATUS</th><th>PRIORITY</th><th>APPOINTMENT</th></tr></thead><tbody>'
    + rows + "</tbody></table></div>";
}
function errorPanel(title, message) {
  return `<div class="inline-error"><strong>${escapeHtml(title)}</strong><p>${escapeHtml(message || "The service is unavailable.")}</p><button class="button button-outline" data-action="retry">Retry</button></div>`;
}

function queueRows() {
  if (dashboardData.errors.queue) return errorPanel("Queue unavailable", dashboardData.errors.queue.message);
  if (!dashboardData.queue.length) return '<div class="empty-inline"><span>' + icon("check", 18)
    + '</span><div><strong>Queue is clear</strong><p>Waiting stages from the backend will appear here.</p></div></div>';
  const admin = sessionStorage.getItem(ROLE_KEY) === "ADMIN";
  const rows = dashboardData.queue.map((entry, index) => {
    const wait = dashboardData.waits[entry.stageId];
    const expectedDuration = serviceDurationLabel(entry.predictedServiceDuration);
    return '<article class="queue-item"><span class="queue-position">' + String(index + 1).padStart(2, "0")
      + '</span><div class="queue-vehicle"><strong>' + escapeHtml(entry.vehicleRegistrationNumber)
      + '</strong><span>' + escapeHtml(entry.serviceTypeName) + " · " + escapeHtml(entry.priority)
      + '</span><small class="queue-duration">' + (expectedDuration
        ? "Expected service · " + escapeHtml(expectedDuration) + (admin ? " · " + escapeHtml(predictionSourceLabel(entry.predictionSource)) : "")
        : "Expected service · unavailable") + '</small></div><div class="queue-status"><span class="status-dot"></span>'
      + escapeHtml(entry.stageStatus.replaceAll("_", " ")) + '</div><div class="queue-wait" title="'
      + escapeHtml(wait?.error || "Backend-calculated waiting estimate") + '"><strong>'
      + (wait?.error ? "Unavailable" : wait ? escapeHtml(wait.estimatedWaitMinutes) + " min" : "—")
      + '</strong><span>EST. WAIT</span></div><button class="icon-button" title="Remove from queue" aria-label="Remove '
      + escapeHtml(entry.vehicleRegistrationNumber) + ' from queue" data-action="dequeue" data-stage="'
      + escapeHtml(entry.stageId) + '">×</button></article>';
  }).join("");
  return '<div class="queue-list">' + rows + "</div>";
}
function resourceCards() {
  if (dashboardData.errors.resources) return errorPanel("Resources unavailable", dashboardData.errors.resources.message);
  if (!dashboardData.resources.length) return `<div class="empty-inline"><span>${icon("wrench", 18)}</span><div><strong>No resources configured</strong><p>Resource status will appear here when the API returns workshop resources.</p></div></div>`;
  return `<div class="resource-grid">${dashboardData.resources.map((resource) => `<article class="resource-card"><div class="resource-head"><span class="resource-id">RESOURCE / ${escapeHtml(resource.id.slice(0, 4).toUpperCase())}</span><i class="resource-state ${String(resource.status).toLowerCase()}"></i></div><h3>${escapeHtml(resource.name)}</h3><span class="status-tag status-${String(resource.status).toLowerCase()}">${escapeHtml(resource.status.replaceAll("_", " "))}</span><div class="resource-compat"><small>COMPATIBLE SERVICES</small><div>${resource.compatibleServiceTypes?.length ? resource.compatibleServiceTypes.map((item) => `<span>${escapeHtml(item.name)}</span>`).join("") : "<span>No compatibility listed</span>"}</div></div></article>`).join("")}</div>`;
}

function renderDashboard(page = "overview") {
  const admin = sessionStorage.getItem(ROLE_KEY) === "ADMIN";
  const title = { overview: admin ? "Workshop overview" : "My service overview", analytics: "Operational analytics", requests: "Service requests", queue: admin ? "Live queue" : "My service queue", resources: "Resources", "service-types": "Service types", vehicles: "My vehicles", profile: "Customer profile" }[page] || "Workshop overview";
  const available = dashboardData.resources.filter((resource) => resource.status === "AVAILABLE").length;
  const failed = Object.values(dashboardData.errors).length;
  const serviceCards = `<section class="metric-grid"><article class="metric-card metric-main"><span class="metric-label">${admin ? "ACTIVE REQUESTS" : "MY ACTIVE REQUESTS"}</span><strong>${dashboardData.requests.filter((request) => request.status !== "COMPLETED" && request.status !== "CANCELLED").length.toString().padStart(2, "0")}</strong><div class="metric-foot"><span class="metric-accent"></span>From service request API</div></article><article class="metric-card"><span class="metric-label">${admin ? "WORKSHOP QUEUE" : "MY QUEUED STAGES"}</span><strong>${dashboardData.queue.length.toString().padStart(2, "0")}</strong><div class="metric-foot">${dashboardData.queue.length ? "Backend-calculated timing" : "No active queue entries"}</div></article>${admin ? `<article class="metric-card"><span class="metric-label">RESOURCES READY</span><strong>${dashboardData.resources.filter((resource) => resource.status === "AVAILABLE").length.toString().padStart(2, "0")}</strong><div class="metric-foot">Compatible workshop resources</div></article>` : `<article class="metric-card"><span class="metric-label">MY VEHICLES</span><strong>${dashboardData.vehicles.length.toString().padStart(2, "0")}</strong><div class="metric-foot">Registered to your profile</div></article>`}<article class="metric-card"><span class="metric-label">SERVICE TYPES</span><strong>${dashboardData.serviceTypes.filter((type) => type.active).length.toString().padStart(2, "0")}</strong><div class="metric-foot">Active for service requests</div></article></section>`;
  const content = page === "analytics" && admin ? analyticsPage()
    : page === "profile" && !admin ? profilePage()
    : page === "vehicles" ? vehiclesPage()
      : page === "queue" ? `<section class="panel"><div class="panel-heading"><div><span class="panel-kicker">OPERATIONS / LIVE</span><h2>${admin ? "Queue sequence" : "Your waiting stages"}</h2><p>Wait estimates are calculated by the backend from current queue and resource state.</p></div>${admin ? `<button class="button button-outline" data-action="assign-next">Assign next ${icon("arrow", 15)}</button>` : ""}</div>${queueRows()}</section>${assignmentsPanel()}<section id="assignment-result"></section>`
    : page === "resources" && admin ? resourceManagementPage()
    : page === "service-types" && admin ? serviceTypesPage()
      : page === "requests" ? `<section class="panel"><div class="panel-heading"><div><span class="panel-kicker">WORKFLOW / ALL</span><h2>Service requests</h2><p>Current requests and workflow stages from the backend.</p></div><div class="panel-actions"><label class="search-box">${icon("search", 16)}<input id="request-search" type="search" placeholder="Search vehicles or service" aria-label="Search service requests"></label><select class="status-filter" id="request-status-filter" aria-label="Filter by request status"><option value="">All statuses</option>${["CREATED", "WAITING", "ASSIGNED", "IN_SERVICE", "COMPLETED", "CANCELLED", "NO_SHOW"].map((status) => `<option value="${status}">${status.replaceAll("_", " ")}</option>`).join("")}</select>${admin ? "" : `<button class="button button-outline" data-action="new-request">New request ${icon("plus", 15)}</button>`}</div></div>${requestTable()}</section>`
        : `<section class="panel queue-panel"><div class="panel-heading"><div><span class="panel-kicker">WORKFLOW / NOW</span><h2>Queue at a glance</h2><p>Wait estimates update from the live workshop state.</p></div><a class="panel-link" href="#/app/queue">View queue ${icon("arrow", 14)}</a></div>${queueRows()}</section><section class="panel request-panel"><div class="panel-heading"><div><span class="panel-kicker">SERVICE / PIPELINE</span><h2>Recent requests</h2><p>Workflow progress for registered service requests.</p></div><a class="panel-link" href="#/app/requests">All requests ${icon("arrow", 14)}</a></div>${requestTable()}</section><section class="dashboard-lower"><div class="panel resource-panel"><div class="panel-heading"><div><span class="panel-kicker">CAPACITY / RESOURCES</span><h2>Workshop resources</h2></div><a class="panel-link" href="#/app/resources">All resources ${icon("arrow", 14)}</a></div>${resourceCards()}</div><aside class="duration-note"><span class="duration-symbol">${icon("clock", 19)}</span><span class="panel-kicker">DURATION &amp; WAITING</span><h3>Two different signals.</h3><p>Duration guidance belongs to each service. Waiting time is calculated dynamically from queue and resource state.</p><div class="duration-note-row"><span>Standard service duration</span><strong>Service type default</strong></div><div class="duration-note-row"><span>Predicted duration</span><strong>Shown beside each stage</strong></div><div class="duration-note-row"><span>Estimated wait</span><strong>Live backend estimate</strong></div></aside></section>`;
  document.querySelector("#dashboard-content").innerHTML = `<div class="page-heading"><div><span class="eyebrow"><span class="eyebrow-dot"></span> WORKSHOP / OPERATIONS</span><h1>${title}</h1><p>${page === "analytics" ? "Historical performance with a separate live workshop snapshot." : "Real-time operational data from the OPTISERVE backend."}</p></div><div class="page-heading-actions"><span class="refresh-time">Updated ${new Intl.DateTimeFormat(undefined, { timeStyle: "short" }).format(new Date())}</span><button class="button button-lime" data-action="refresh">Refresh data ${icon("arrow", 15)}</button></div></div>${failed ? `<div class="partial-warning" role="status">${failed} data source${failed === 1 ? " is" : "s are"} unavailable. Displaying available API data only.</div>` : ""}${serviceCards}${content}<p class="api-footnote">Service estimates show persisted stage durations and their recorded source. Waiting time remains a live backend calculation.</p>`;
  wireDashboardActions();
}

function wireDashboardActions() {
  dashboardNavigationCleanup?.();
  dashboardNavigationCleanup = null;
  document.querySelector("#sign-out")?.addEventListener("click", () => { sessionStorage.removeItem(TOKEN_KEY); sessionStorage.removeItem(ROLE_KEY); location.hash = "#/login"; });
  const menuButton = document.querySelector("#menu-toggle");
  const sidebar = document.querySelector("#sidebar");
  if (menuButton && sidebar) {
    const mobileNavigation = window.matchMedia("(max-width: 820px)");
    const setSidebarOpen = (open, returnFocus = false) => {
      const shouldOpen = open && mobileNavigation.matches;
      sidebar.classList.toggle("sidebar-open", shouldOpen);
      menuButton.setAttribute("aria-expanded", String(shouldOpen));
      if (!shouldOpen && returnFocus && mobileNavigation.matches) menuButton.focus();
    };
    const toggleSidebar = () => setSidebarOpen(!sidebar.classList.contains("sidebar-open"));
    const closeOnOutsideTap = (event) => {
      if (mobileNavigation.matches && sidebar.classList.contains("sidebar-open") && !sidebar.contains(event.target) && !menuButton.contains(event.target)) setSidebarOpen(false);
    };
    const closeOnEscape = (event) => {
      if (event.key === "Escape" && sidebar.classList.contains("sidebar-open")) setSidebarOpen(false, true);
    };
    const closeOnNavigation = () => setSidebarOpen(false, true);
    const handleBreakpointChange = () => setSidebarOpen(false);
    menuButton.addEventListener("click", toggleSidebar);
    document.addEventListener("pointerdown", closeOnOutsideTap);
    window.addEventListener("keydown", closeOnEscape);
    sidebar.querySelectorAll(".app-nav a").forEach((link) => link.addEventListener("click", closeOnNavigation));
    mobileNavigation.addEventListener?.("change", handleBreakpointChange);
    dashboardNavigationCleanup = () => {
      menuButton.removeEventListener("click", toggleSidebar);
      document.removeEventListener("pointerdown", closeOnOutsideTap);
      window.removeEventListener("keydown", closeOnEscape);
      sidebar.querySelectorAll(".app-nav a").forEach((link) => link.removeEventListener("click", closeOnNavigation));
      mobileNavigation.removeEventListener?.("change", handleBreakpointChange);
    };
  }
  document.querySelectorAll('[data-action="refresh"], [data-action="retry"]').forEach((button) => button.addEventListener("click", loadDashboard));
  document.querySelector("#analytics-range-form")?.addEventListener("submit", (event) => {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const from = String(form.get("from"));
    const to = String(form.get("to"));
    if (from > to) {
      const endDate = event.currentTarget.querySelector('input[name="to"]');
      endDate.setCustomValidity("Choose an end date on or after the start date.");
      endDate.reportValidity();
      return;
    }
    event.currentTarget.querySelector('input[name="to"]').setCustomValidity("");
    analyticsRange = { from, to };
    loadDashboard();
  });
  document.querySelector("#request-search")?.addEventListener("input", (event) => {
    filterRequests();
  });
  document.querySelector("#request-status-filter")?.addEventListener("change", filterRequests);
  document.querySelectorAll('[data-action="new-request"]').forEach((button) => button.addEventListener("click", showRequestForm));
  document.querySelector('[data-action="add-service-type"]')?.addEventListener("click", () => showServiceTypeForm());
  document.querySelectorAll('[data-action="edit-service-type"]').forEach((button) => button.addEventListener("click", () => {
    const serviceType = dashboardData.serviceTypes.find((item) => item.id === button.dataset.id);
    if (serviceType) showServiceTypeForm(serviceType);
  }));
  document.querySelectorAll('[data-action="delete-service-type"]').forEach((button) => button.addEventListener("click", async () => {
    const type = dashboardData.serviceTypes.find((item) => item.id === button.dataset.id);
    if (!type || !window.confirm(`Delete service type “${type.name}”?`)) return;
    button.disabled = true;
    try { await api(`/api/service-types/${type.id}`, { method: "DELETE" }); await loadDashboard(); showToast("Service type deleted."); }
    catch (error) { showToast(displayError(error)); button.disabled = false; }
  }));
  document.querySelector('[data-action="add-resource"]')?.addEventListener("click", () => showResourceForm());
  document.querySelectorAll('[data-action="edit-resource"]').forEach((button) => button.addEventListener("click", () => {
    const resource = dashboardData.resources.find((item) => item.id === button.dataset.id);
    if (resource) showResourceForm(resource);
  }));
  document.querySelectorAll('[data-action="delete-resource"]').forEach((button) => button.addEventListener("click", async () => {
    const resource = dashboardData.resources.find((item) => item.id === button.dataset.id);
    if (!resource || !window.confirm(`Delete resource “${resource.name}”?`)) return;
    button.disabled = true;
    try { await api(`/api/resources/${resource.id}`, { method: "DELETE" }); await loadDashboard(); showToast("Resource deleted."); }
    catch (error) { showToast(displayError(error)); button.disabled = false; }
  }));
  document.querySelectorAll('[data-action="resource-status"]').forEach((select) => select.addEventListener("change", async () => {
    select.disabled = true;
    try { await api(`/api/resources/${select.dataset.id}/status`, { method: "PATCH", body: JSON.stringify({ status: select.value }) }); await loadDashboard(); showToast("Resource status updated."); }
    catch (error) { showToast(displayError(error)); await loadDashboard(); }
  }));
  document.querySelectorAll('[data-action="add-compatibility"]').forEach((button) => button.addEventListener("click", async () => {
    const select = document.querySelector(`[data-compat-select="${button.dataset.resource}"]`);
    if (!select?.value) { showToast("Choose a service type first."); return; }
    button.disabled = true;
    try { await api(`/api/resources/${button.dataset.resource}/service-types/${select.value}`, { method: "PUT" }); await loadDashboard(); showToast("Resource compatibility added."); }
    catch (error) { showToast(displayError(error)); button.disabled = false; }
  }));
  document.querySelectorAll('[data-action="remove-compatibility"]').forEach((button) => button.addEventListener("click", async () => {
    button.disabled = true;
    try { await api(`/api/resources/${button.dataset.resource}/service-types/${button.dataset.type}`, { method: "DELETE" }); await loadDashboard(); showToast("Resource compatibility removed."); }
    catch (error) { showToast(displayError(error)); button.disabled = false; }
  }));
  document.querySelector("#profile-form")?.addEventListener("submit", saveProfile);
  document.querySelector('[data-action="add-vehicle"]')?.addEventListener("click", () => showVehicleForm());
  document.querySelectorAll('[data-action="edit-vehicle"]').forEach((button) => button.addEventListener("click", () => {
    const vehicle = dashboardData.vehicles.find((item) => item.id === button.dataset.id);
    if (vehicle) showVehicleForm(vehicle);
  }));
  document.querySelectorAll('[data-action="delete-vehicle"]').forEach((button) => button.addEventListener("click", async () => {
    const vehicle = dashboardData.vehicles.find((item) => item.id === button.dataset.id);
    if (!vehicle || !window.confirm(`Delete ${vehicle.registrationNumber}? Vehicles with service history cannot be deleted.`)) return;
    button.disabled = true;
    try {
      await api(`/api/vehicles/${vehicle.id}`, { method: "DELETE" });
      await loadDashboard();
      showToast("Vehicle deleted.");
    } catch (error) {
      showToast(displayError(error));
      button.disabled = false;
    }
  }));
  document.querySelectorAll('[data-action="dequeue"]').forEach((button) => button.addEventListener("click", async () => {
    button.disabled = true;
    try { await api(`/api/queue/stages/${button.dataset.stage}`, { method: "DELETE" }); await loadDashboard(); }
    catch (error) { showToast(error.message); button.disabled = false; }
  }));
  document.querySelectorAll('[data-action="queue-stage"]').forEach((button) => button.addEventListener("click", async () => {
    button.disabled = true;
    try { await api(`/api/queue/stages/${button.dataset.stage}`, { method: "POST" }); await loadDashboard(); showToast("Eligible stage added to the queue."); }
    catch (error) { showToast(error.message); button.disabled = false; }
  }));
  document.querySelectorAll('[data-action="assign-next"]').forEach((button) => button.addEventListener("click", assignNext));
  document.querySelectorAll('[data-action="start-assignment"]').forEach((button) => button.addEventListener("click", startAssignment));
  document.querySelectorAll('[data-action="complete-assignment"]').forEach((button) => button.addEventListener("click", completeAssignment));
  document.querySelectorAll("[data-duration-for]").forEach((input) => input.addEventListener("input", () => {
    assignmentDurationDrafts.set(input.dataset.durationFor, input.value);
  }));
}

async function saveProfile(event) {
  event.preventDefault();
  const form = event.currentTarget;
  const data = new FormData(form);
  const button = form.querySelector("button[type=submit]");
  const error = form.querySelector(".form-error");
  button.disabled = true;
  error.textContent = "";
  try {
    dashboardData.customer = await api("/api/customers/me", {
      method: "PUT",
      body: JSON.stringify({ name: data.get("name"), phone: data.get("phone") || null }),
    });
    showToast("Profile updated.");
  } catch (cause) {
    error.textContent = displayError(cause);
  } finally {
    button.disabled = false;
  }
}

function showVehicleForm(vehicle = null) {
  const modal = document.createElement("div");
  modal.className = "modal-backdrop";
  const editing = Boolean(vehicle);
  modal.innerHTML = `<section class="modal" role="dialog" aria-modal="true" aria-labelledby="vehicle-modal-title"><button class="modal-close" type="button" aria-label="Close dialog">×</button><span class="panel-kicker">GARAGE / VEHICLE</span><h2 id="vehicle-modal-title">${editing ? "Update vehicle" : "Add a vehicle"}</h2><form id="vehicle-form" class="auth-form"><label>Registration number<input name="registrationNumber" maxlength="32" required value="${escapeHtml(vehicle?.registrationNumber || "")}"></label><label>Make<input name="make" maxlength="100" required value="${escapeHtml(vehicle?.make || "")}"></label><label>Model<input name="model" maxlength="100" required value="${escapeHtml(vehicle?.model || "")}"></label><label>Year<input type="number" name="year" min="1" required value="${escapeHtml(vehicle?.year || "")}"></label><button class="button button-lime" type="submit">${editing ? "Save vehicle" : "Add vehicle"}</button><p class="form-error" role="alert"></p></form></section>`;
  document.body.append(modal);
  modal.querySelector(".modal-close").focus();
  modal.querySelector(".modal-close").addEventListener("click", () => modal.remove());
  modal.addEventListener("click", (event) => { if (event.target === modal) modal.remove(); });
  modal.addEventListener("keydown", (event) => { if (event.key === "Escape") modal.remove(); });
  modal.querySelector("#vehicle-form").addEventListener("submit", async (event) => {
    event.preventDefault();
    const form = event.currentTarget;
    const data = new FormData(form);
    const button = form.querySelector("button[type=submit]");
    const error = form.querySelector(".form-error");
    button.disabled = true;
    error.textContent = "";
    const body = JSON.stringify({ registrationNumber: data.get("registrationNumber"), make: data.get("make"), model: data.get("model"), year: Number(data.get("year")) });
    try {
      await api(editing ? `/api/vehicles/${vehicle.id}` : "/api/vehicles", { method: editing ? "PUT" : "POST", body });
      modal.remove();
      await loadDashboard();
      showToast(editing ? "Vehicle updated." : "Vehicle added.");
    } catch (cause) {
      error.textContent = displayError(cause);
      button.disabled = false;
    }
  });
}

function filterRequests() {
  const query = (document.querySelector("#request-search")?.value || "").trim().toLowerCase();
  const status = document.querySelector("#request-status-filter")?.value || "";
  document.querySelectorAll("tbody tr").forEach((row) => {
    const requestStatus = row.dataset.status || "";
    row.hidden = !row.dataset.search.includes(query) || Boolean(status && status !== requestStatus);
  });
}

function showServiceTypeForm(serviceType = null) {
  const modal = document.createElement("div");
  modal.className = "modal-backdrop";
  const editing = Boolean(serviceType);
  const minutes = serviceType ? (Number(serviceType.defaultServiceDurationSeconds) / 60).toString() : "30";
  modal.innerHTML = `<section class="modal" role="dialog" aria-modal="true" aria-labelledby="service-type-modal-title"><button class="modal-close" type="button" aria-label="Close dialog">×</button><span class="panel-kicker">CONFIGURATION / SERVICE TYPE</span><h2 id="service-type-modal-title">${editing ? "Edit service type" : "Add service type"}</h2><form id="service-type-form" class="auth-form" autocomplete="off"><label>Name<input name="serviceTypeName" autocomplete="off" required maxlength="150" value="${escapeHtml(serviceType?.name || "")}"></label><label>Description<textarea name="description" maxlength="1000">${escapeHtml(serviceType?.description || "")}</textarea></label><label>Default duration in minutes<input name="durationMinutes" type="number" min="0.01" step="0.01" required value="${escapeHtml(minutes)}"></label><label>Availability<select name="active"><option value="true" ${!serviceType || serviceType.active ? "selected" : ""}>Active</option><option value="false" ${serviceType && !serviceType.active ? "selected" : ""}>Inactive</option></select></label><button class="button button-lime" type="submit">${editing ? "Save service type" : "Create service type"}</button><p class="form-error" role="alert"></p></form></section>`;
  document.body.append(modal);
  modal.querySelector(".modal-close").focus();
  modal.querySelector(".modal-close").addEventListener("click", () => modal.remove());
  modal.addEventListener("click", (event) => { if (event.target === modal) modal.remove(); });
  modal.addEventListener("keydown", (event) => { if (event.key === "Escape") modal.remove(); });
  modal.querySelector("#service-type-form").addEventListener("submit", async (event) => {
    event.preventDefault();
    const form = event.currentTarget;
    const data = new FormData(form);
    const button = form.querySelector("button[type=submit]");
    const error = form.querySelector(".form-error");
    button.disabled = true;
    error.textContent = "";
    const body = JSON.stringify({
      name: data.get("serviceTypeName"),
      description: data.get("description") || null,
      defaultServiceDurationSeconds: Math.round(Number(data.get("durationMinutes")) * 60),
      active: data.get("active") === "true",
    });
    try {
      await api(editing ? `/api/service-types/${serviceType.id}` : "/api/service-types", { method: editing ? "PATCH" : "POST", body });
      modal.remove();
      await loadDashboard();
      showToast(editing ? "Service type updated." : "Service type created.");
    } catch (cause) {
      error.textContent = displayError(cause);
      button.disabled = false;
    }
  });
}

function showResourceForm(resource = null) {
  const modal = document.createElement("div");
  modal.className = "modal-backdrop";
  const editing = Boolean(resource);
  modal.innerHTML = `<section class="modal" role="dialog" aria-modal="true" aria-labelledby="resource-modal-title"><button class="modal-close" type="button" aria-label="Close dialog">×</button><span class="panel-kicker">WORKSHOP / RESOURCE</span><h2 id="resource-modal-title">${editing ? "Rename resource" : "Add resource"}</h2><form id="resource-form" class="auth-form"><label>Resource name<input name="name" maxlength="150" required value="${escapeHtml(resource?.name || "")}"></label><button class="button button-lime" type="submit">${editing ? "Save resource" : "Create resource"}</button><p class="form-error" role="alert"></p></form></section>`;
  document.body.append(modal);
  modal.querySelector(".modal-close").focus();
  modal.querySelector(".modal-close").addEventListener("click", () => modal.remove());
  modal.addEventListener("click", (event) => { if (event.target === modal) modal.remove(); });
  modal.addEventListener("keydown", (event) => { if (event.key === "Escape") modal.remove(); });
  modal.querySelector("#resource-form").addEventListener("submit", async (event) => {
    event.preventDefault();
    const form = event.currentTarget;
    const data = new FormData(form);
    const button = form.querySelector("button[type=submit]");
    const error = form.querySelector(".form-error");
    button.disabled = true;
    try {
      await api(editing ? `/api/resources/${resource.id}` : "/api/resources", {
        method: editing ? "PATCH" : "POST",
        body: JSON.stringify({ name: data.get("name") }),
      });
      modal.remove();
      await loadDashboard();
      showToast(editing ? "Resource renamed." : "Resource created.");
    } catch (cause) {
      error.textContent = displayError(cause);
      button.disabled = false;
    }
  });
}

function showRequestForm() {
  const types = dashboardData.serviceTypes.filter((type) => type.active);
  const modal = document.createElement("div");
  modal.className = "modal-backdrop";
  const options = dashboardData.vehicles.map((vehicle) => `<option value="${vehicle.id}">${escapeHtml(vehicle.registrationNumber)} · ${escapeHtml(vehicle.make)} ${escapeHtml(vehicle.model)} (${vehicle.year})</option>`).join("");
  const serviceTypeHint = types.length
    ? '<small class="field-hint" id="service-stage-hint">Choose one or more stages. Their displayed order will be used in your request.</small>'
    : `<p class="field-hint" id="service-stage-hint">${dashboardData.errors.serviceTypes ? "Service types are unavailable right now. Retry after the catalog reconnects." : "No active service types are configured yet. Ask a workshop administrator to add an active service type."}</p>`;
  const serviceStages = types.map((type, index) => `<label class="service-stage-option"><input type="checkbox" name="serviceTypeIds" value="${escapeHtml(type.id)}"><span class="service-stage-order">${String(index + 1).padStart(2, "0")}</span><span class="service-stage-name">${escapeHtml(type.name)}<small>Default ${duration(type.defaultServiceDurationSeconds)}</small></span></label>`).join("");
  modal.innerHTML = `<section class="modal request-modal" role="dialog" aria-modal="true" aria-labelledby="request-modal-title"><button class="modal-close" type="button" aria-label="Close dialog">×</button><span class="panel-kicker">SERVICE / NEW REQUEST</span><h2 id="request-modal-title">Start a request</h2><p class="modal-intro">Register or choose one of your vehicles, then select the ordered service stages.</p><form id="request-form" class="auth-form"><label>Vehicle<select name="vehicleId" required>${options}<option value="new" ${options ? "" : "selected"}>Register a vehicle</option></select></label><div id="new-vehicle-fields" ${options ? "hidden" : ""}><label>Registration number<input name="registrationNumber" maxlength="32" required placeholder="KA01AB1234"></label><label>Make<input name="make" maxlength="100" required placeholder="Toyota"></label><label>Model<input name="model" maxlength="100" required placeholder="Camry"></label><label>Year<input type="number" name="year" min="1" required placeholder="2024"></label></div><label>Priority<select name="priority"><option value="NORMAL">Normal</option><option value="URGENT">Urgent</option><option value="CRITICAL">Critical</option><option value="APPOINTMENT">Appointment</option></select></label><label class="appointment-field" hidden>Appointment time<input type="datetime-local" name="appointmentTime"></label><fieldset class="service-stage-group" tabindex="-1" aria-describedby="service-stage-hint"><legend>Service stages <span>Request order</span></legend><div class="service-stage-options">${serviceStages}</div></fieldset>${serviceTypeHint}<button class="button button-lime auth-submit" type="submit" ${types.length ? "" : "disabled"}>Create service request ${icon("arrow", 15)}</button><p class="form-error" id="request-error" role="alert"></p></form></section>`;
  document.body.append(modal);
  modal.querySelector(".modal-close").focus();
  modal.querySelectorAll('[name="serviceTypeIds"]').forEach((checkbox) => checkbox.addEventListener("change", () => {
    if (modal.querySelector('[name="serviceTypeIds"]:checked')) modal.querySelector("#request-error").textContent = "";
  }));
  modal.querySelector(".modal-close").addEventListener("click", () => modal.remove());
  modal.addEventListener("click", (event) => { if (event.target === modal) modal.remove(); });
  modal.addEventListener("keydown", (event) => { if (event.key === "Escape") modal.remove(); });
  modal.querySelector('[name="priority"]').addEventListener("change", (event) => { const field = modal.querySelector(".appointment-field"); field.hidden = event.target.value !== "APPOINTMENT"; field.querySelector("input").required = !field.hidden; });
  modal.querySelector('[name="vehicleId"]').addEventListener("change", (event) => {
    const fields = modal.querySelector("#new-vehicle-fields");
    fields.hidden = event.target.value !== "new";
    fields.querySelectorAll("input").forEach((input) => { input.required = !fields.hidden; });
  });
  modal.querySelector('[name="vehicleId"]').dispatchEvent(new Event("change"));
  modal.querySelector("#request-form").addEventListener("submit", async (event) => {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    const serviceTypeIds = [...modal.querySelectorAll('[name="serviceTypeIds"]:checked')].map((checkbox) => checkbox.value);
    const errorMessage = modal.querySelector("#request-error");
    if (!serviceTypeIds.length) {
      errorMessage.textContent = "Select at least one service stage to continue.";
      modal.querySelector(".service-stage-group")?.focus();
      return;
    }
    errorMessage.textContent = "";
    const appointmentTime = data.get("appointmentTime");
    const body = { priority: data.get("priority"), serviceTypeIds, ...(appointmentTime ? { appointmentTime: new Date(appointmentTime).toISOString() } : {}) };
    const submit = event.currentTarget.querySelector("button[type=submit]");
    submit.disabled = true;
    try {
      let vehicleId = data.get("vehicleId");
      if (vehicleId === "new") {
        submit.innerHTML = "Registering vehicle…";
        const vehicle = await api("/api/vehicles", { method: "POST", body: JSON.stringify({ registrationNumber: data.get("registrationNumber"), make: data.get("make"), model: data.get("model"), year: Number(data.get("year")) }) });
        vehicleId = vehicle.id;
        dashboardData.vehicles.unshift(vehicle);
        const vehicleSelect = modal.querySelector('[name="vehicleId"]');
        vehicleSelect.add(new Option(`${vehicle.registrationNumber} · ${vehicle.make} ${vehicle.model} (${vehicle.year})`, vehicle.id));
        vehicleSelect.value = vehicle.id;
        vehicleSelect.dispatchEvent(new Event("change"));
      }
      await api("/api/service-requests", { method: "POST", body: JSON.stringify({ ...body, vehicleId }) });
      modal.remove();
      await loadDashboard();
      showToast("Service request created.");
    } catch (error) {
      modal.querySelector("#request-error").textContent = displayError(error);
      submit.disabled = false;
      submit.innerHTML = `Create service request ${icon("arrow", 15)}`;
      if (error.status === 409 && dashboardData.vehicles.length && modal.querySelector('[name="vehicleId"]').value === "new") {
        modal.querySelector("#request-error").textContent = `${displayError(error)} If the vehicle was registered, it is saved to your account.`;
      }
    }
  });
}

async function assignNext(event) {
  const button = event.currentTarget;
  button.disabled = true;
  try {
    const assignment = await api("/api/assignments/next", { method: "POST" });
    await loadDashboard();
    const region = document.querySelector("#assignment-result");
    if (assignment.result !== "ASSIGNED") {
      region.innerHTML = `<div class="assignment-result subdued"><strong>No assignment made</strong><span>${escapeHtml(assignment.result.replaceAll("_", " "))}</span></div>`;
    } else {
      region.innerHTML = `<div class="assignment-result"><div><span class="panel-kicker">ASSIGNED / ${escapeHtml(assignment.assignmentStatus)}</span><strong>${escapeHtml(assignment.vehicleRegistrationNumber)} · ${escapeHtml(assignment.serviceTypeName)}</strong><span>${escapeHtml(assignment.resourceName)} · ${escapeHtml(assignment.priority)}</span></div><span>Continue from Assignment history below.</span></div>`;
      showToast("Next compatible resource assigned.");
    }
  } catch (error) { showToast(displayError(error)); }
  finally { button.disabled = false; }
}

async function startAssignment(event) {
  const button = event.currentTarget;
  const id = button.dataset.id;
  if (sessionStorage.getItem(ROLE_KEY) !== "ADMIN" || assignmentActionsInFlight.has(id)) return;
  assignmentActionsInFlight.add(id);
  assignmentActionErrors.delete(id);
  button.disabled = true;
  try {
    await api(`/api/assignments/${id}/start`, { method: "POST" });
    showToast("Service started.");
  } catch (error) {
    assignmentActionErrors.set(id, displayError(error));
  } finally {
    assignmentActionsInFlight.delete(id);
    await loadDashboard();
  }
}

async function completeAssignment(event) {
  const button = event.currentTarget;
  const id = button.dataset.id;
  if (sessionStorage.getItem(ROLE_KEY) !== "ADMIN" || assignmentActionsInFlight.has(id)) return;
  const durationInput = document.querySelector(`[data-duration-for="${CSS.escape(id)}"]`);
  if (!durationInput?.reportValidity()) return;
  const actualDurationMinutes = Number(durationInput.value);
  if (!Number.isFinite(actualDurationMinutes) || actualDurationMinutes <= 0) return;
  assignmentActionsInFlight.add(id);
  assignmentActionErrors.delete(id);
  button.disabled = true;
  durationInput.disabled = true;
  try {
    await api(`/api/assignments/${id}/complete`, { method: "POST", body: JSON.stringify({ actualDurationMinutes }) });
    assignmentDurationDrafts.delete(id);
    showToast("Service completed and workflow updated.");
  } catch (error) {
    assignmentDurationDrafts.set(id, durationInput.value);
    assignmentActionErrors.set(id, displayError(error));
  } finally {
    assignmentActionsInFlight.delete(id);
    await loadDashboard();
  }
}

function showToast(message) {
  let toast = document.querySelector("#toast");
  if (!toast) { toast = document.createElement("div"); toast.id = "toast"; toast.className = "toast"; toast.setAttribute("role", "status"); document.body.append(toast); }
  toast.textContent = message;
  toast.classList.add("toast-visible");
  window.clearTimeout(showToast.timer);
  showToast.timer = window.setTimeout(() => toast.classList.remove("toast-visible"), 3600);
}

async function dashboard(page = "overview") {
  if (!sessionStorage.getItem(TOKEN_KEY)) { location.hash = "#/login"; return; }
  app.innerHTML = '<div class="loading-state"><span class="spinner"></span><p>Checking your account…</p></div>';
  let role;
  try {
    const session = await api("/api/auth/me");
    role = session.role;
    sessionStorage.setItem(ROLE_KEY, role);
  } catch {
    sessionStorage.removeItem(TOKEN_KEY);
    sessionStorage.removeItem(ROLE_KEY);
    location.hash = "#/login";
    return;
  }
  const adminPage = ["analytics", "resources", "service-types"].includes(page);
  const customerPage = ["vehicles", "profile"].includes(page);
  if ((role === "ADMIN" && customerPage) || (role !== "ADMIN" && adminPage)) {
    page = "overview";
    if (location.hash !== "#/app/overview") location.hash = "#/app/overview";
  }
  activeDashboardPage = page;
  if (page === "analytics" && !analyticsRange) analyticsRange = defaultAnalyticsRange();
  app.innerHTML = dashboardShell(page);
  wireDashboardActions();
  await loadDashboard();
}

function renderRoute() {
  dashboardNavigationCleanup?.();
  dashboardNavigationCleanup = null;
  const anchor = location.hash.replace(/^#/, "");
  if (anchor.startsWith("story-") && document.getElementById(anchor)) return;
  landingMenuCleanup?.();
  landingMenuCleanup = null;
  landingStoryCleanup?.();
  landingStoryCleanup = null;
  window.scrollTo({ top: 0, behavior: "auto" });
  const route = location.hash.replace(/^#\/?/, "").split("/").filter(Boolean);
  if (route[0] === "login" || route[0] === "register") authPage(route[0]);
  else if (route[0] === "app") dashboard(route[1] || "overview");
  else {
    landing();
    if (anchor.startsWith("story-")) window.requestAnimationFrame(() => document.getElementById(anchor)?.scrollIntoView());
  }
}

window.addEventListener("hashchange", renderRoute);
renderRoute();
