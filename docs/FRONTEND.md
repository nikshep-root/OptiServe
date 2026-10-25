# OPTISERVE frontend

The dependency-free frontend is served by Spring Boot from `optiserve-backend/src/main/resources/static/`. Browser pages and the REST API share an origin. Start the backend with PostgreSQL and JWT configuration, then open `http://localhost:8080/`.

## Public product page

The public page is a cinematic product story for workshop operations, built from locally bundled artwork, CSS, and the existing JavaScript application. Its sections explain request intake, scheduling, compatible resources, service stages, and the distinction between service duration and waiting time. The five-step workflow panel follows the selected step while scrolling and can also be operated by keyboard and pointer. A second pinned scene scrubs a four-panel workshop feature rail horizontally with vertical page scroll; on narrow screens this becomes a native touch-scroll carousel. The reduced-motion preference disables smooth movement and parallax. The layout adapts for mobile, tablet, and desktop.

The queue, resource map, and interactive workshop-console preview are clearly marked illustrative. They contain no live or invented operational state. Actual customer and admin workspaces remain available after sign-in through the existing routes and APIs.

### Visual reference audit

The reference was inspected in the browser at 1280 × 720 px at the hero and feature gallery. It uses a compact mark and top navigation, full-bleed scenic imagery, large software previews, generous black negative space, and a small number of clearly separated story sections. Its feature gallery gives the product imagery most of the viewport and keeps the active position visible. The page closes with a compact comparison table, accordion-style FAQs, and a roomy footer.

The reference browser review found the product interface featured close to the opening story, while OPTISERVE kept its workshop preview after six operational sections. The showcase now follows the opening operation map, so a visitor sees a concrete workshop interface before moving into the detailed workflow story. The compact header, hero art direction, and operational explanations remain unchanged. At 1280 × 720 px, the revised product section presents its headline and the upper portion of the sample workshop console together. The next five-stage workflow and four-panel scroll gallery retain their existing interactions. Reverse scrolling through the pinned gallery returned to the prior panel without a jump; the pin entered and released without a layout shift in the observed pass. Scroll reveals and the pinned feature rail continue to use CSS transitions and native scroll position; exact reference animation timing was not measured. Desktop motion was checked in both directions. At a 390 × 844 px mobile viewport, the page document measured 375 px wide and the main story sections matched that content width, with no horizontal overflow. The browser had no authenticated session or supplied test account, so the workshop dashboard was not reviewed in an authenticated state.

## Customer flow and API contracts

Authentication remains on the existing contracts:

- `POST /api/auth/register` and `POST /api/auth/login` accept `{ "email", "password" }` and return `{ "accessToken", "tokenType", "expiresAt" }`.
- Public registration always creates a `USER`. `GET /api/auth/me` returns the authenticated account's email and role and routes `ADMIN` accounts to the workshop console.
- For local development, initial admin provisioning remains available through the opt-in `local` profile. Production provisioning requires the separate, explicitly activated `admin-bootstrap` profile, `OPTISERVE_ADMIN_BOOTSTRAP_ENABLED=true`, and valid `OPTISERVE_ADMIN_EMAIL` and `OPTISERVE_ADMIN_PASSWORD` settings. The password is encoded and never logged; an existing customer account is never promoted, and an existing administrator is left unchanged. After a Render bootstrap, verify administrator login, then remove the bootstrap flag, password, email, and temporary profile and redeploy. See the [Render administrator provisioning instructions](../README.md#one-time-render-administrator-provisioning). There is no public admin-registration endpoint.
- After registration, the frontend saves the customer's profile with `PUT /api/customers/me` using `{ "name", "phone" }`. If profile setup fails, the account remains created; retry profile setup or sign in again and complete the profile.
- `GET /api/customers/me` returns only the signed-in customer's profile. `DELETE /api/customers/me` returns `204` only when the customer has no registered vehicles; otherwise it returns a conflict to preserve service history.
- The Profile screen edits the signed-in customer's name and phone with `PUT /api/customers/me`.
- `GET /api/vehicles` lists the signed-in customer's vehicles. `POST /api/vehicles` accepts `{ "registrationNumber", "make", "model", "year" }`. `GET/PUT/DELETE /api/vehicles/{id}` operate only on that customer's vehicle. Delete returns `409` if a service request references the vehicle.
- The My Vehicles screen creates, updates, and deletes through these APIs. It confirms before deletion and displays conflicts without removing the vehicle from the UI.
- `POST /api/service-requests` keeps its existing request shape. A customer can create a request only for an owned vehicle. Customer reads return only that customer's requests; administrators retain the all-workshop view.

Vehicle and customer responses use DTOs and omit account email, password hash, and auth-user identifiers. A foreign vehicle or request ID returns not found so the API does not disclose whether another customer's record exists.

## Workshop operations

The console reads service requests, queue entries, resources, service types, and assignments from the backend. Customer queue responses contain only stages for vehicles owned by that customer; administrators see the workshop-wide queue. Wait estimates come from the backend's dynamic calculation. Customer queue actions are ownership checked. `GET /api/assignments` returns the caller's assignments for `USER` accounts and all workshop assignments for `ADMIN`; assignment mutations remain restricted to `ROLE_ADMIN`.

Admins can create, edit, deactivate, and delete service types through `/api/service-types`. Default duration is entered in minutes and sent to the API in seconds. Admins can create, rename, remove, change resource status, and manage compatible service types through `/api/resources`. Customers can read the active service catalog and select active types when creating requests. If no active type exists, the request form explains that an administrator must configure one.

Each service stage persists its estimated service duration and prediction source. ML_MODEL means the FastAPI service returned a valid prediction; DEFAULT_FALLBACK means the configured service-type duration was used. Existing rows migrated from before provenance tracking use LEGACY_UNKNOWN because their source cannot be established. Service-request and queue responses include the ISO-8601 duration and source; assignment summaries include the assigned estimate, actual duration when completed, and its estimate source. Customer screens show a simple estimate and actual duration; workshop administrators also see the recorded source. Waiting estimates remain a separate backend calculation from current queue and resource state.

The dashboard uses API responses and shows loading, empty, unavailable, validation, unauthorized, and server-error states. It does not invent operational records; missing estimate or actual-duration values remain absent. Assignment history displays persisted estimates and completed actual durations, and administrators can resume assignment actions from the assignments API after reloading. The role model currently distinguishes USER and ADMIN; separate advisor, technician, and manager experiences remain a backend/product gap.

## Local checks

No separate frontend build or package installation is required. JavaScript syntax can be checked with `node --check optiserve-backend/src/main/resources/static/assets/app.js`. Run Maven checks from `optiserve-backend/`; database-backed tests require a PostgreSQL instance and the test database environment variables described in the backend configuration.
