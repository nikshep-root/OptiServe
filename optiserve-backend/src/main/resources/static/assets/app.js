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

// Isolated demonstration fixture: fields follow ServiceRequestStageResponse,
// ResourceResponse, WaitTimeResponse and AssignmentSummaryResponse. Never sent to an API.
function serviceWorkOrderPreview() {
  const example = {
    vehicle: { make: "Maruti Suzuki", model: "Swift", registrationNumber: "DEMO 014" },
    request: { priority: "NORMAL", workflowStatus: "ACTIVE", stages: [
      { sequenceNumber: 1, serviceTypeName: "Inspection", status: "COMPLETED", predictedServiceDuration: "PT20M", predictionSource: "DEFAULT_FALLBACK" },
      { sequenceNumber: 2, serviceTypeName: "Brake service", status: "QUEUED", predictedServiceDuration: "PT35M", predictionSource: "DEFAULT_FALLBACK" },
      { sequenceNumber: 3, serviceTypeName: "Final check", status: "PENDING", predictedServiceDuration: "PT10M", predictionSource: "DEFAULT_FALLBACK" }
    ] },
    resource: { name: "Brake bay", status: "BUSY", compatibleServiceTypes: [{ name: "Brake service" }] },
    wait: { estimatedWaitMinutes: 12 },
    completedAssignment: { assignmentStatus: "COMPLETED", actualServiceDuration: "PT18M" }
  };
  const current = example.request.stages[1];
  return `<section class="work-order-story" id="story-work-order" aria-labelledby="work-order-heading">
    <div class="work-order-introduction">
      <p class="work-order-overline">From intake to handover</p>
      <h2 id="work-order-heading">One vehicle.<br>A shared<br><em>plan of work.</em></h2>
      <p>A service request becomes a sequence the whole workshop can follow. See what is done, what is waiting, and what can happen next.</p>
      <div class="work-order-reading"><span aria-hidden="true">↳</span><p>Follow the second stage.<br><strong>The work is ready.<br>The compatible bay isn’t.</strong></p></div>
      <p class="work-order-demo-note">Illustrative product view.<br>Demonstration data, not a live workshop.</p>
    </div>
    <article class="work-order-sheet" aria-label="Demonstration service work order">
      <header class="work-order-masthead"><span>OPTISERVE / Service work order</span><span class="work-order-demo-tag">Demonstration</span></header>
      <div class="work-order-vehicle"><div><p>Vehicle · ${escapeHtml(example.vehicle.registrationNumber)}</p><h3>${escapeHtml(example.vehicle.make)}<br><strong>${escapeHtml(example.vehicle.model)}</strong></h3></div><div class="work-order-request"><span>Service request</span><strong>Example 014</strong><span class="work-order-priority">${escapeHtml(example.request.priority)} priority</span></div></div>
      <div class="work-order-progress"><span>Ordered service stages</span><span>1 of 3 completed · Workflow active</span></div>
      <ol class="work-order-stages">${example.request.stages.map((stage, index) => `<li class="work-order-stage work-order-stage-${stage.status.toLowerCase()}" ${stage.status === "QUEUED" ? 'aria-current="step"' : ""}><span class="work-order-sequence">${String(stage.sequenceNumber).padStart(2, "0")}</span><div><strong>${escapeHtml(stage.serviceTypeName)}</strong><span>${index === 0 ? "Actual " + serviceDurationLabel(example.completedAssignment.actualServiceDuration) + " · estimate " + serviceDurationLabel(stage.predictedServiceDuration) : index === 1 ? "Current stage · waiting for a compatible resource" : "Becomes eligible after brake service"}</span></div><span class="work-order-state">${stage.status === "COMPLETED" ? "✓ Completed" : stage.status === "QUEUED" ? "Queued" : "Pending"}</span></li>`).join("")}</ol>
      <div class="work-order-decision"><div class="work-order-resource"><span>Compatible resource</span><strong>${escapeHtml(example.resource.name)} <small>Busy</small></strong><p>Configured for ${escapeHtml(example.resource.compatibleServiceTypes[0].name.toLowerCase())}. No resource assigned to this stage yet.</p></div><dl class="work-order-times"><div><dt>Estimated service</dt><dd>${serviceDurationLabel(current.predictedServiceDuration)}</dd><dd class="work-order-time-source">Service-type default</dd></div><div><dt>Estimated wait</dt><dd>${example.wait.estimatedWaitMinutes} min</dd><dd class="work-order-time-source">Illustrative queue estimate</dd></div></dl></div>
      <footer class="work-order-foot"><span class="work-order-foot-mark" aria-hidden="true">↳</span><p><strong>Next: assign a compatible available resource.</strong> Brake service remains queued. The final check stays pending until brake service is completed.</p></footer>
    </article>
  </section>`;
}

function landing() {
  app.innerHTML = `
    <div class="marketing-shell editorial-site">
      <div class="story-scroll-meter" aria-hidden="true"><span></span></div>
      <header class="story-header wrap">
        ${brand()}
        <button class="mobile-menu" aria-label="Open navigation" aria-controls="story-nav" aria-expanded="false">${icon("menu")}</button>
        <nav class="story-nav" id="story-nav" aria-label="Main navigation" inert>
          <a href="#story-workflow">How it works</a>
          <a href="#story-queue">Queue planning</a>
          <a href="#story-resources">Workshop fit</a>
          <a href="#story-platform">Platform</a>
          <a href="#/login">Sign in</a>
          <a class="story-menu-cta" href="#/register">Customer access ${icon("arrow", 15)}</a>
        </nav>
      </header>

      <main id="main" class="story-main">
        <section class="editorial-hero">
          <div class="editorial-hero-copy">
            <p class="editorial-eyebrow"><span></span> Service coordination for workshops</p>
            <h1>Every service.<br>Every stage.<br><em>In good order.</em></h1>
            <p class="editorial-lead">Coordinate service requests, ordered stages and waiting work. Match each stage to a compatible workshop resource, from intake through completion.</p>
            <div class="editorial-actions">
              <a class="editorial-button" href="#story-work-order">Follow a work order ${icon("arrow", 16)}</a>
              <a class="editorial-link" href="#/login">Workshop sign in <span aria-hidden="true">↗</span></a>
            </div>
            <p class="editorial-note">For workshop owners, managers and the people keeping work moving.</p>
          </div>
          <figure class="editorial-hero-image">
            <picture class="story-hero-media">
              <img src="/assets/workshop-documentary.jpg" alt="A mechanic working at an open car engine in a garage" fetchpriority="high" decoding="async">
            </picture>
            <figcaption><span>01</span><span>The craft behind every service</span><small class="editorial-photo-credit">Photo by <a href="https://commons.wikimedia.org/wiki/File:Car_repair_takes_place_inside_a_garage_with_a_person_working_on_the_engine_in_a_rural_setting.jpg" target="_blank" rel="noreferrer">Nenad Stojković</a> · <a href="https://creativecommons.org/licenses/by/2.0/" target="_blank" rel="noreferrer">CC BY 2.0</a></small></figcaption>
          </figure>
          <div class="editorial-hero-index" aria-hidden="true">OPTISERVE / 2026</div>
        </section>

        ${serviceWorkOrderPreview()}

        <section class="coordination-story" id="story-platform">
          <p class="section-note">The work between the work</p><h2>A busy workshop.<br><em>One shared picture.</em></h2>
          <div class="coordination-columns"><p>A request at the desk. A vehicle waiting outside. A bay finishing another job. Good service depends on the handoffs between them.</p><p>OPTISERVE connects the request, its ordered stages and the workshop’s resources. Customers follow progress. The team sees the next action.</p></div>
          <div class="handoff-strip"><span>Customer request</span><span>Ordered stages</span><span>Compatible resource</span><span>Recorded completion</span></div>
        </section>
        <section class="planning-story" id="story-queue">
          <div class="planning-heading"><p class="section-note">Intelligent queue planning</p><h2>Priority matters.<br><em>So does the right bay.</em></h2><p>Critical work gets first consideration. Compatibility and availability determine what can actually start. Work already in progress is never interrupted.</p></div>
          <div class="planning-example" aria-label="Illustrative scheduling example"><div class="example-caption"><strong>A workshop decision</strong><span>Demonstration · not a live queue</span></div>
            <div class="planning-line"><span class="priority-critical">CRITICAL</span><div><strong>Brake repair</strong><p>Needs the brake bay</p></div><div><strong>Bay busy</strong><p>Remains queued</p></div></div>
            <div class="planning-line planning-selected"><span>NORMAL</span><div><strong>Oil change</strong><p>Compatible with the service bay</p></div><div><strong>Bay available</strong><p>Can be assigned</p></div></div>
            <p class="planning-explanation">The oil change can proceed on a different compatible resource. The critical request keeps its queue history and takes precedence when a compatible resource becomes available.</p>
          </div>
          <div class="planning-principles"><p><strong>Four priority classes</strong>Critical, urgent, appointment and normal.</p><p><strong>Waiting has weight</strong>Aging, due appointments and a bounded duration bonus contribute to ranking.</p><p><strong>Two separate estimates</strong>Service duration describes the work. Waiting time reflects current queue and resource state.</p></div>
        </section>
        <section class="visibility-story" id="story-resources"><div class="visibility-heading"><div><p class="section-note">The workshop, in view</p><h2>Know what needs<br><em>your attention.</em></h2></div><p>Start assigned work. Record its completion. Keep resource compatibility and availability visible alongside the work that depends on them.</p></div>
          <div class="operations-preview" role="region" aria-label="Demonstration workshop interface"><aside><strong>OPTISERVE</strong><span>Overview</span><b>Active work</b><span>Requests</span><span>Resources</span></aside><div class="preview-main"><div class="example-caption"><strong>Active work</strong><span>Illustrative interface · demonstration data</span></div><div class="preview-job"><div><small>DEMO 014 / Inspection</small><strong>Recorded and ready for the next stage.</strong><p>Inspection completed · actual 18 min · estimated 20 min</p></div><span>Completed</span></div><div class="preview-job"><div><small>DEMO 022 / Oil change</small><strong>Service bay · work underway</strong><p>Actual duration will be recorded at completion.</p></div><span>In progress</span></div><div class="preview-resource"><span>Brake bay <b>Busy</b></span><span>Service bay <b>Busy</b></span><span>Inspection bay <b>Available</b></span></div><p class="preview-footnote">A product illustration, not an interactive control panel. Sign in to operate your configured workshop.</p></div></div>
        </section>
        <section class="history-story" id="story-workflow"><div><p class="section-note">A service record that carries forward</p><h2>The next stage<br>starts with<br><em>a clear handoff.</em></h2><p>A completed assignment records its actual duration. The immediate next stage becomes eligible. The request finishes when the final stage is complete.</p></div><ol class="history-ledger"><li><span>01</span><div><strong>Inspection completed</strong><p>18 min actual / 20 min estimated</p></div><small>Recorded</small></li><li><span>02</span><div><strong>Brake service ready</strong><p>Eligible to queue, then match to a compatible resource</p></div><small>Next</small></li><li><span>03</span><div><strong>Final check pending</strong><p>Waits for the preceding stage to finish</p></div><small>Later</small></li><li class="history-ledger-note">Illustrative handoff · stages remain ordered</li></ol></section>
        <section class="benefit-story editorial-proof"><p class="section-note">Built for the day-to-day</p><div><article><h3>Fewer blind handoffs.</h3><p>See each request’s stages, assignment and recorded completion in one connected service record.</p></article><article><h3>Resources with a purpose.</h3><p>Configure service compatibility and see available, busy and offline workshop resources.</p></article><article><h3>Estimates with context.</h3><p>See the recorded estimate source and actual duration. The ML service predicts service duration only. Waiting time is calculated separately.</p></article></div><p class="benefit-caveat">Duration prediction currently uses a model trained on synthetic data. No real-world accuracy or time-saving claim is implied.</p></section>

        <section class="editorial-faq" id="story-faq">
          <div class="editorial-faq-intro" data-reveal><p class="editorial-eyebrow"><span></span> Good to know</p><h2>Clear answers<br><em>for the workshop.</em></h2></div>
          <div class="faq-list">
            <details><summary>How is waiting time calculated?<span aria-hidden="true">+</span></summary><p>The backend estimates waiting time from the current queue and resource state. The ML service predicts service duration only.</p></details>
            <details><summary>What does the service duration show?<span aria-hidden="true">+</span></summary><p>Each stage keeps its estimate and source. Administrators can distinguish a valid ML prediction from the configured default fallback; actual duration appears after completion.</p></details>
            <details><summary>Who manages service types and resources?<span aria-hidden="true">+</span></summary><p>Workshop administrators configure service types, resource compatibility, and operating mode. Public registration creates customer accounts only.</p></details>
            <details><summary>Can workshop operations run automatically?<span aria-hidden="true">+</span></summary><p>Administrators can choose MANUAL or AUTO. New deployments default to MANUAL, and the existing scheduler still applies priority and compatibility rules.</p></details>
          </div>
        </section>

        <section class="editorial-end" data-reveal>
          <div class="editorial-end-top"><p class="editorial-eyebrow"><span></span> OPTISERVE / Workshop operations</p><span>From request to ready</span></div>
          <h2>Your next<br><em>clear handoff.</em></h2>
          <div class="editorial-end-bottom"><p>Already have workshop access? Sign in to your operations workspace. Vehicle owners can create a customer account. Workshop administrator access is provisioned separately.</p><div class="editorial-actions"><a class="editorial-button" href="#/login">Sign in to OPTISERVE ${icon("arrow", 16)}</a><a class="editorial-link" href="#/register">Create a customer account <span aria-hidden="true">↗</span></a></div></div>
        </section>
      </main>

      <footer class="editorial-footer wrap">
        <div>${brand()}<p>Clearer workshop operations,<br>from intake through handoff.</p></div>
        <nav aria-label="Footer"><a href="#story-workflow">Service journey</a><a href="#story-queue">Queue planning</a><a href="#story-faq">Questions</a><a href="#/login">Sign in</a></nav>
        <span>© ${new Date().getFullYear()} OPTISERVE</span>
      </footer>
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
  app.innerHTML = `<main id="main" class="auth-shell"><a class="auth-back" href="/">← Back to OPTISERVE</a><section class="auth-panel"><div class="auth-brand">${brand()}</div><div class="eyebrow"><span class="eyebrow-dot"></span> WORKSHOP OPERATIONS</div><h1>${registering ? "Start with a" : "Welcome"}<br>${registering ? "clearer view." : "back."}</h1><p class="auth-intro">${registering ? "Create a customer account to register vehicles and follow your service requests." : "Sign in to your workshop or customer account."}</p>
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
    ? [["overview", "Workshop overview", "grid"], ["work", "Active work", "wrench"], ["history", "Service history", "clock"], ["analytics", "Analytics", "grid"], ["automation", "Automation", "check"], ["requests", "Service requests", "layers"], ["queue", "Live queue", "clock"], ["resources", "Resources", "wrench"], ["service-types", "Service types", "layers"]]
    : [["overview", "Overview", "grid"], ["requests", "Service requests", "layers"], ["queue", "My service queue", "clock"], ["vehicles", "My vehicles", "wrench"], ["profile", "Profile", "grid"]];
  return `<div class="app-shell ${admin ? "workshop-app" : "customer-app"}"><aside class="app-sidebar" id="sidebar"><div class="sidebar-brand">${brand(true)}<span>${admin ? "WORKSHOP" : "CUSTOMER"}</span></div><div class="sidebar-caption">${admin ? "Workshop operations" : "Your service account"}</div><nav class="app-nav" aria-label="Operations navigation">${nav.map(([id, label, glyph]) => `<a href="#/app/${id}" class="${active === id ? "selected" : ""}" ${active === id ? 'aria-current="page"' : ""}>${icon(glyph, 17)}<span>${label}</span>${id === "queue" ? '<i class="nav-dot"></i>' : ""}</a>`).join("")}</nav><div class="sidebar-spacer"></div><div class="sidebar-foot"><div class="system-state"><i></i><span>API CONNECTION</span><b id="connection-label">CHECKING</b></div><button id="sign-out" class="sign-out">${icon("logout", 16)} Sign out</button></div></aside><div class="app-main"><header class="app-topbar"><button class="topbar-menu" id="menu-toggle" type="button" aria-label="Toggle navigation" aria-controls="sidebar" aria-expanded="false">${icon("menu")}</button><div class="breadcrumbs"><span>OPTISERVE</span><i>/</i><strong>${nav.find(([id]) => id === active)?.[1] || "Overview"}</strong></div><div class="topbar-right"><span class="topbar-date">${new Intl.DateTimeFormat(undefined, { weekday: "long", month: "short", day: "numeric" }).format(new Date())}</span><span class="avatar">OP</span></div></header><main class="dashboard-content" id="dashboard-content"><div class="loading-state"><span class="spinner"></span><p>Connecting to your workshop…</p></div></main></div></div>`;
}

let dashboardData = { customer: null, vehicles: [], requests: [], queue: [], resources: [], serviceTypes: [], assignments: [], analytics: null, automation: null, waits: {}, errors: {} };
let dashboardNavigationCleanup = null;
let activeDashboardPage = "overview";
let dashboardLoadVersion = 0;
let dashboardUpdatedAt = null;
let analyticsRange = null;

function defaultAnalyticsRange() {
  const to = new Date();
  const from = new Date(to);
  from.setDate(from.getDate() - 29);
  const dateValue = (date) => date.toISOString().slice(0, 10);
  return { from: dateValue(from), to: dateValue(to) };
}

async function loadDashboard() {
  const version = ++dashboardLoadVersion;
  const page = activeDashboardPage;
  const admin = sessionStorage.getItem(ROLE_KEY) === "ADMIN";
  const calls = { requests: "/api/service-requests", queue: "/api/queue", serviceTypes: "/api/service-types", assignments: "/api/assignments" };
  if (admin) { calls.resources = "/api/resources"; calls.automation = "/api/admin/workshop-automation"; }
  else { calls.customer = "/api/customers/me"; calls.vehicles = "/api/vehicles"; }
  if (admin && page === "analytics") calls.analytics = `/api/admin/analytics?from=${encodeURIComponent(analyticsRange.from)}&to=${encodeURIComponent(analyticsRange.to)}`;
  document.querySelectorAll('[data-action="refresh"], [data-action="retry"]').forEach(button => button.disabled = true);
  const names = Object.keys(calls);
  const outcomes = await Promise.allSettled(names.map(name => api(calls[name])));
  if (version !== dashboardLoadVersion || !location.hash.startsWith("#/app")) return;
  dashboardData = { customer: null, vehicles: [], requests: [], queue: [], resources: [], serviceTypes: [], assignments: [], analytics: null, automation: null, waits: {}, errors: {} };
  outcomes.forEach((result, index) => {
    if (result.status === "fulfilled") dashboardData[names[index]] = result.value;
    else dashboardData.errors[names[index]] = result.reason;
  });
  if (outcomes.some(result => result.status === "rejected" && result.reason.status === 401)) {
    sessionStorage.removeItem(TOKEN_KEY); sessionStorage.removeItem(ROLE_KEY); location.hash = "#/login"; return;
  }
  const successes = outcomes.filter(result => result.status === "fulfilled").length;
  const coreUnavailable = ["requests", "queue"].some(name => dashboardData.errors[name]);
  const connection = document.querySelector("#connection-label");
  if (connection) connection.textContent = coreUnavailable ? (successes ? "PARTIAL" : "UNAVAILABLE") : "CONNECTED";
  document.querySelector(".system-state")?.classList.toggle("disconnected", coreUnavailable);
  dashboardUpdatedAt = successes ? new Date() : null;
  if (!admin && dashboardData.errors.customer?.status === 404) { renderCustomerProfileSetup(); return; }
  const waits = await Promise.all(dashboardData.queue.map(async entry => {
    try { return [entry.stageId, await api(`/api/queue/stages/${entry.stageId}/wait-time`)]; }
    catch (error) { return [entry.stageId, { error: error.message, status: error.status }]; }
  }));
  if (version !== dashboardLoadVersion) return;
  if (waits.some(([,wait]) => wait.status === 401)) { sessionStorage.removeItem(TOKEN_KEY); sessionStorage.removeItem(ROLE_KEY); location.hash = "#/login"; return; }
  dashboardData.waits = Object.fromEntries(waits);
  renderDashboard(page);
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

function assignmentsPanel(mode = "all") {
  const entries = dashboardData.assignments.filter(item => mode !== "history" || ["COMPLETED", "CANCELLED"].includes(item.assignmentStatus));
  if (dashboardData.errors.assignments) return errorPanel("Assignments unavailable", dashboardData.errors.assignments.message);
  if (!entries.length) return '<section class="panel"><div class="panel-heading"><div><span class="panel-kicker">SERVICE / ASSIGNMENTS</span><h2>Assignment history</h2></div></div><div class="empty-inline"><span>' + icon("check", 18) + '</span><div><strong>No assignments yet</strong><p>Assignments for your service requests will appear here.</p></div></div></section>';
  const admin = sessionStorage.getItem(ROLE_KEY) === "ADMIN";
  const rows = entries.map((item) => "<tr><td>" + escapeHtml(item.vehicleRegistrationNumber)
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
  const stage = request.stages?.find(item => ["IN_PROGRESS", "ASSIGNED", "QUEUED", "ELIGIBLE"].includes(item.status)) || request.stages?.[0];
  const admin = sessionStorage.getItem(ROLE_KEY) === "ADMIN";
  const progress = request.stages?.map((item) => '<span class="stage-pill stage-' + escapeHtml(String(item.status).toLowerCase()) + '"><span>'
    + escapeHtml(item.serviceTypeName) + ' <i>' + escapeHtml(item.status.replaceAll("_", " ")) + "</i></span>"
    + stageEstimate(item, admin)
    + (item.status === "ELIGIBLE" ? ' <button class="stage-queue-action" data-action="queue-stage" data-stage="' + escapeHtml(item.id) + '">Queue</button>' : "")
    + "</span>").join("") || "—";
  return '<tr data-status="' + escapeHtml(request.status) + '" data-search="'
    + escapeHtml((request.vehicleRegistrationNumber + " " + (request.stages || []).map(item => item.serviceTypeName).join(" ") + " " + request.status + " " + request.priority).toLowerCase())
    + '"><td><span class="table-primary">' + escapeHtml(request.vehicleRegistrationNumber)
    + '</span><a class="table-secondary request-detail-link" href="#/app/request/' + encodeURIComponent(request.id) + '">View work order · ' + escapeHtml(request.id.slice(0, 8).toUpperCase())
    + '</a></td><td><span class="table-primary">' + escapeHtml(stage?.serviceTypeName || "Service request")
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
  return '<p class="request-table-hint">Swipe horizontally to view all service request details.</p><div class="table-wrap request-table-wrap" role="region" aria-label="Service requests" tabindex="0"><table class="request-table"><thead><tr><th>VEHICLE</th><th>SERVICE</th><th>WORKFLOW STAGES</th><th>STATUS</th><th>PRIORITY</th><th>APPOINTMENT</th></tr></thead><tbody>'
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
  const rows = dashboardData.queue.map((entry) => {
    const wait = dashboardData.waits[entry.stageId];
    const expectedDuration = serviceDurationLabel(entry.predictedServiceDuration);
    return '<article class="queue-item"><span class="queue-symbol" aria-hidden="true">' + icon("clock", 16)
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

let automationActionError = null;

function automationPage() {
  if (dashboardData.errors.automation) {
    return errorPanel("Automation setting unavailable", displayError(dashboardData.errors.automation));
  }
  const mode = dashboardData.automation?.mode;
  if (mode !== "MANUAL" && mode !== "AUTO") {
    return errorPanel("Automation setting unavailable", "The workshop automation API returned an invalid mode.");
  }
  const automatic = mode === "AUTO";
  return `<section class="panel automation-panel"><div class="panel-heading"><div><span class="panel-kicker">WORKSHOP / OPERATING MODE</span><h2>Automatic operations</h2><p>Choose whether eligible work is queued and assigned automatically.</p></div><span class="status-tag ${automatic ? "status-available" : "status-offline"}" data-testid="automation-mode">${automatic ? "AUTO" : "MANUAL"}</span></div><div class="automation-explanation"><p>${automatic ? "AUTO is active. New eligible stages are queued, then the existing priority-aware scheduler attempts compatible available resources." : "MANUAL is active. Staff choose when eligible stages enter the queue and when to assign the next job."}</p><p>Enabling AUTO also considers work already waiting: existing queue entries keep their order, and currently eligible stages are added before the existing scheduler attempts assignments. If no compatible resource is available, work remains queued for a later resource or configuration change. In-progress service is never interrupted.</p></div>${automationActionError ? `<p class="form-error" role="alert">${escapeHtml(automationActionError)}</p>` : ""}<div class="automation-actions"><button class="button ${automatic ? "button-outline" : "button-lime"}" data-action="set-automation-mode" data-mode="${automatic ? "MANUAL" : "AUTO"}">${automatic ? "Return to manual mode" : "Enable automatic operations"}</button><span class="field-hint">Changing modes does not remove queue entries or active assignments.</span></div></section>`;
}

function activeWorkPanel() {
  if (dashboardData.errors.assignments) return errorPanel("Active work unavailable", displayError(dashboardData.errors.assignments));
  const active = dashboardData.assignments.filter(item => ["ASSIGNED", "IN_PROGRESS"].includes(item.assignmentStatus));
  return `<section class="panel active-work-panel"><div class="panel-heading"><div><span class="panel-kicker">Workshop / Execution</span><h2>Active assignments</h2><p>Start assigned work. Record actual minutes when service is complete.</p></div><a class="panel-link" href="#/app/history">Service history ${icon("arrow", 14)}</a></div>${active.length ? `<div class="active-work-list">${active.map(item => `<article class="active-work-item"><div><span class="status-tag status-${item.assignmentStatus.toLowerCase()}">${escapeHtml(item.assignmentStatus.replaceAll("_", " "))}</span><h3>${escapeHtml(item.vehicleRegistrationNumber)} · ${escapeHtml(item.serviceTypeName)}</h3><p>${escapeHtml(item.resourceName)} · ${escapeHtml(item.priority)}</p><a href="#/app/request/${encodeURIComponent(item.serviceRequestId)}" class="panel-link">Open work order</a></div><div>${assignmentDurationSummary(item, true)}</div><div>${assignmentControls(item)}</div></article>`).join("")}</div>` : `<div class="empty-state"><h3>No active assignments</h3><p>Assign compatible waiting work from the queue.</p><a class="panel-link" href="#/app/queue">Open queue ${icon("arrow", 14)}</a></div>`}</section>`;
}

function workOrderDetail() {
  if (dashboardData.errors.requests) return errorPanel("Work order unavailable", displayError(dashboardData.errors.requests));
  const id = decodeURIComponent(location.hash.split("/")[3] || "");
  const request = dashboardData.requests.find(item => item.id === id);
  if (!request) return `<section class="panel empty-state"><h2>Work order not found</h2><p>This request is unavailable for your account.</p><a href="#/app/requests" class="panel-link">Back to service requests</a></section>`;
  const admin = sessionStorage.getItem(ROLE_KEY) === "ADMIN";
  const stages = [...request.stages].sort((a,b) => a.sequenceNumber - b.sequenceNumber);
  const completed = stages.filter(stage => stage.status === "COMPLETED").length;
  return `<section class="panel order-detail"><div class="panel-heading"><div><span class="panel-kicker">Service work order · ${escapeHtml(request.id.slice(0,8))}</span><h2>${escapeHtml(request.vehicleRegistrationNumber)}</h2><p>${escapeHtml(request.priority)} priority · ${escapeHtml(request.workflowStatus)} workflow · ${completed} of ${stages.length} stages completed</p>${request.appointmentTime ? `<p>Appointment ${escapeHtml(formatDate(request.appointmentTime))}</p>` : ""}</div><a class="panel-link" href="#/app/requests">All requests ${icon("arrow",14)}</a></div>${dashboardData.errors.assignments ? errorPanel("Assignment details unavailable", displayError(dashboardData.errors.assignments)) : ""}<ol class="detail-stages">${stages.map(stage => {
    const assignment = dashboardData.assignments.find(item => item.stageId === stage.id && item.assignmentStatus !== "CANCELLED");
    const wait = dashboardData.waits[stage.id];
    return `<li class="detail-stage detail-stage-${stage.status.toLowerCase()}"><span class="detail-stage-number">${String(stage.sequenceNumber).padStart(2,"0")}</span><div class="detail-stage-content"><div class="detail-stage-heading"><h3>${escapeHtml(stage.serviceTypeName)}</h3><span class="status-tag">${escapeHtml(stage.status.replaceAll("_"," "))}</span></div>${stageEstimate(stage,admin)}${stage.status === "QUEUED" ? `<p class="detail-wait">Estimated wait: <strong>${wait && !wait.error ? escapeHtml(wait.estimatedWaitMinutes) + " min" : "Unavailable"}</strong>${wait?.calculatedAt ? ` · calculated ${escapeHtml(formatDate(wait.calculatedAt))}` : ""}</p>` : ""}${stage.status === "PENDING" ? '<p>Waiting for the preceding stage to complete.</p>' : ""}${assignment ? `<p>Resource: <strong>${escapeHtml(assignment.resourceName)}</strong></p>${assignmentDurationSummary(assignment,admin)}<p class="detail-timestamps">Assigned ${escapeHtml(formatDate(assignment.assignedAt))}${assignment.startedAt ? ` · Started ${escapeHtml(formatDate(assignment.startedAt))}` : ""}${assignment.completedAt ? ` · Completed ${escapeHtml(formatDate(assignment.completedAt))}` : ""}</p>${admin ? assignmentControls(assignment) : ""}` : ""}${stage.status === "ELIGIBLE" ? `<button class="button button-outline" data-action="queue-stage" data-stage="${escapeHtml(stage.id)}">Queue stage</button>` : ""}</div></li>`;
  }).join("")}</ol></section>`;
}

function overviewMetrics(admin) {
  const metric = (source,label,value,note) => `<article class="metric-card"><span class="metric-label">${label}</span><strong>${dashboardData.errors[source] ? "—" : value}</strong><div class="metric-foot">${dashboardData.errors[source] ? "Data unavailable" : note}</div></article>`;
  const active = dashboardData.requests.filter(item => !["COMPLETED","CANCELLED","NO_SHOW"].includes(item.status)).length;
  return `<section class="metric-grid" aria-label="Current account snapshot">${metric("requests",admin ? "ACTIVE REQUESTS" : "MY ACTIVE REQUESTS",active,"Work still to complete")}${metric("queue",admin ? "WAITING STAGES" : "MY WAITING STAGES",dashboardData.queue.length,"Waiting for assignment")}${admin ? metric("assignments","ACTIVE ASSIGNMENTS",dashboardData.assignments.filter(item=>["ASSIGNED","IN_PROGRESS"].includes(item.assignmentStatus)).length,"Assigned or underway") : metric("vehicles","MY VEHICLES",dashboardData.vehicles.length,"Registered to your profile")}${admin ? metric("resources","AVAILABLE RESOURCES",dashboardData.resources.filter(item=>item.status==="AVAILABLE").length,"Compatibility checked at assignment") : metric("requests","COMPLETED REQUESTS",dashboardData.requests.filter(item=>item.status==="COMPLETED").length,"Final stage completed")}</section>`;
}

function renderDashboard(page = "overview") {
  const admin = sessionStorage.getItem(ROLE_KEY) === "ADMIN";
  const titles = {overview: admin ? "Workshop overview" : "My service overview", work:"Active work", history:"Service history", request:"Service work order", analytics:"Operational analytics", automation:"Workshop automation",requests:"Service requests",queue:admin ? "Live queue" : "My service queue",resources:"Resources","service-types":"Service types",vehicles:"My vehicles",profile:"Customer profile"};
  const queuePanel = `<section class="panel"><div class="panel-heading"><div><span class="panel-kicker">Waiting work</span><h2>${admin ? "Waiting stages" : "Your waiting stages"}</h2><p>Listed by queue arrival, not assignment rank. Priority, aging and resource compatibility affect the next assignment.</p></div>${admin ? `<button class="button button-outline" data-action="assign-next">Assign next ${icon("arrow",15)}</button>` : ""}</div>${queueRows()}</section>`;
  const requestsPanel = `<section class="panel"><div class="panel-heading"><div><span class="panel-kicker">Service work orders</span><h2>Service requests</h2><p>Open a work order to see each stage, resource and recorded duration.</p></div><div class="panel-actions"><label class="search-box">${icon("search",16)}<input id="request-search" type="search" placeholder="Search vehicles or service" aria-label="Search service requests"></label><select class="status-filter" id="request-status-filter" aria-label="Filter by request status"><option value="">All statuses</option>${["CREATED","WAITING","ASSIGNED","IN_SERVICE","COMPLETED","CANCELLED","NO_SHOW"].map(status=>`<option value="${status}">${status.replaceAll("_"," ")}</option>`).join("")}</select>${admin ? "" : `<button class="button button-outline" data-action="new-request">New request ${icon("plus",15)}</button>`}</div></div>${requestTable()}</section>`;
  let content;
  if (page === "request") content = workOrderDetail();
  else if(page === "work" && admin) content = activeWorkPanel();
  else if(page === "history" && admin) content = assignmentsPanel("history");
  else if(page === "analytics" && admin) content = analyticsPage();
  else if(page === "automation" && admin) content = automationPage();
  else if(page === "resources" && admin) content = resourceManagementPage();
  else if(page === "service-types" && admin) content = serviceTypesPage();
  else if(page === "profile" && !admin) content = profilePage();
  else if(page === "vehicles" && !admin) content = vehiclesPage();
  else if(page === "requests") content = requestsPanel;
  else if(page === "queue") content = queuePanel + assignmentsPanel() + '<section id="assignment-result"></section>';
  else if(admin) content = `<div class="operations-brief"><p><strong>${dashboardData.automation?.mode === "AUTO" ? "Automatic operations" : dashboardData.automation?.mode === "MANUAL" ? "Manual operations" : "Operating mode unavailable"}</strong><span> Review waiting work and keep assigned services moving.</span></p><a href="#/app/automation">Workshop settings ${icon("arrow",14)}</a></div>${activeWorkPanel()}${queuePanel}<section id="assignment-result"></section>${requestsPanel}`;
  else content = `<section class="customer-welcome"><div><p>Your service, step by step</p><h2>${dashboardData.customer?.name ? "Welcome, " + escapeHtml(dashboardData.customer.name) + "." : "Your next service starts here."}</h2><p>Register a vehicle, request its services and follow each stage through the workshop.</p></div><button class="button button-lime" data-action="new-request">New service request ${icon("plus",15)}</button></section>${requestsPanel}${queuePanel}`;
  const failed = Object.keys(dashboardData.errors).length;
  document.querySelector("#dashboard-content").innerHTML = `<div class="page-heading"><div><span class="eyebrow">${admin ? "Workshop operations" : "Your service account"}</span><h1>${titles[page] || titles.overview}</h1><p>${page === "analytics" ? "Historical results, with current workshop state shown separately." : "A current snapshot. Refresh or complete an action to update."}</p></div><div class="page-heading-actions"><span class="refresh-time">${dashboardUpdatedAt ? "Fetched " + new Intl.DateTimeFormat(undefined,{timeStyle:"short"}).format(dashboardUpdatedAt) : "Data unavailable"}</span><button class="button button-lime" data-action="refresh">Refresh data ${icon("arrow",15)}</button></div></div>${failed ? `<div class="partial-warning" role="status">${failed} data source${failed===1 ? " is" : "s are"} unavailable. Missing values are not shown as zero.</div>` : ""}${page === "overview" ? overviewMetrics(admin) : ""}${content}<p class="api-footnote">Service duration is an estimate of the work. Waiting time is calculated separately from queue and resource state.</p>`;
  wireDashboardActions();
}

function wireDashboardActions() {
  dashboardNavigationCleanup?.();
  dashboardNavigationCleanup = null;
  const signOut = document.querySelector("#sign-out");
  if (signOut) signOut.onclick = () => { sessionStorage.removeItem(TOKEN_KEY); sessionStorage.removeItem(ROLE_KEY); assignmentDurationDrafts.clear(); assignmentActionErrors.clear(); location.hash = "#/login"; };
  const menuButton = document.querySelector("#menu-toggle");
  const sidebar = document.querySelector("#sidebar");
  if (menuButton && sidebar) {
    const mobileNavigation = window.matchMedia("(max-width: 820px)");
    const setSidebarOpen = (open, returnFocus = false) => {
      const shouldOpen = open && mobileNavigation.matches;
      sidebar.classList.toggle("sidebar-open", shouldOpen);
      sidebar.inert = mobileNavigation.matches && !shouldOpen;
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
    setSidebarOpen(sidebar.classList.contains("sidebar-open"));
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
  document.querySelector('#analytics-range-form input[name="to"]')?.addEventListener("input", event => event.currentTarget.setCustomValidity(""));
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
  document.querySelector('[data-action="set-automation-mode"]')?.addEventListener("click", async (event) => {
    const button = event.currentTarget;
    const mode = button.dataset.mode;
    if (mode === "AUTO" && !window.confirm("Enable automatic operations? Eligible stages, including work already waiting, will be considered by the existing scheduler. In-progress services will not be interrupted.")) return;
    button.disabled = true;
    automationActionError = null;
    try {
      await api("/api/admin/workshop-automation", { method: "PUT", body: JSON.stringify({ mode }) });
      await loadDashboard();
      showToast(`Workshop automation set to ${mode}.`);
    } catch (error) {
      automationActionError = displayError(error);
      renderDashboard("automation");
    }
  });
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

function mountModal(modal) {
  const trigger = document.activeElement;
  document.body.append(modal);
  const focusable = () => [...modal.querySelectorAll('button, input, select, textarea, a[href], [tabindex="0"]')].filter(el => !el.disabled && el.getClientRects().length);
  const onKey = event => {
    if (event.key === "Escape") { event.preventDefault(); modal.remove(); }
    if (event.key === "Tab") {
      const list = focusable(), first = list[0], last = list.at(-1);
      if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last?.focus(); }
      else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first?.focus(); }
    }
  };
  app.inert = true;
  modal.addEventListener("keydown", onKey);
  const observer = new MutationObserver(() => { if (!modal.isConnected) { app.inert = false; observer.disconnect(); if (trigger?.isConnected) trigger.focus(); } });
  observer.observe(document.body, {childList:true});
  requestAnimationFrame(() => focusable().find(el => el.matches('input,select,textarea'))?.focus());
}

function showVehicleForm(vehicle = null) {
  const modal = document.createElement("div");
  modal.className = "modal-backdrop";
  const editing = Boolean(vehicle);
  modal.innerHTML = `<section class="modal" role="dialog" aria-modal="true" aria-labelledby="vehicle-modal-title"><button class="modal-close" type="button" aria-label="Close dialog">×</button><span class="panel-kicker">GARAGE / VEHICLE</span><h2 id="vehicle-modal-title">${editing ? "Update vehicle" : "Add a vehicle"}</h2><form id="vehicle-form" class="auth-form"><label>Registration number<input name="registrationNumber" maxlength="32" required value="${escapeHtml(vehicle?.registrationNumber || "")}"></label><label>Make<input name="make" maxlength="100" required value="${escapeHtml(vehicle?.make || "")}"></label><label>Model<input name="model" maxlength="100" required value="${escapeHtml(vehicle?.model || "")}"></label><label>Year<input type="number" name="year" min="1" required value="${escapeHtml(vehicle?.year || "")}"></label><button class="button button-lime" type="submit">${editing ? "Save vehicle" : "Add vehicle"}</button><p class="form-error" role="alert"></p></form></section>`;
  mountModal(modal);
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
  document.querySelectorAll("tbody tr[data-search]").forEach((row) => {
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
  mountModal(modal);
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
  mountModal(modal);
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
  mountModal(modal);
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
  const routeVersion = dashboardLoadVersion;
  if (!sessionStorage.getItem(TOKEN_KEY)) { location.hash = "#/login"; return; }
  app.innerHTML = '<div class="loading-state"><span class="spinner"></span><p>Checking your account…</p></div>';
  let role;
  try {
    const session = await api("/api/auth/me");
    if (routeVersion !== dashboardLoadVersion) return;
    role = session.role;
    sessionStorage.setItem(ROLE_KEY, role);
  } catch (error) {
    if (routeVersion !== dashboardLoadVersion) return;
    if (error.status !== 401) { app.innerHTML = `<main class="auth-shell"><section class="auth-panel"><h1>Unable to open your account</h1><p>${escapeHtml(displayError(error))}</p><button class="button" id="retry-session">Try again</button><a href="#/login">Back to sign in</a></section></main>`; document.querySelector("#retry-session").onclick = () => dashboard(page); return; }
    sessionStorage.removeItem(TOKEN_KEY);
    sessionStorage.removeItem(ROLE_KEY);
    location.hash = "#/login";
    return;
  }
  const adminPage = ["analytics", "automation", "resources", "service-types", "work", "history"].includes(page);
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
  dashboardLoadVersion++;
  document.querySelectorAll(".modal-backdrop").forEach(modal => modal.remove());
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
