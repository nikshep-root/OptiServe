# OPTISERVE frontend

The dependency-free frontend is served by Spring Boot from `optiserve-backend/src/main/resources/static/`. Browser pages and the REST API share an origin. Start the backend with PostgreSQL and JWT configuration, then open `http://localhost:8080/`.

## Public product page and visual system

The public site addresses workshop owners and managers. Its visual idea is “workshop craft meets precise operations,” with the service work order as the recurring product artifact. The hero leads into that work order; later sections explain workshop handoffs, a clearly labeled scheduling scenario, an interface illustration, ordered stage history, product benefits, access, and the footer. The existing documentary photo remains attributed to Nenad Stojković under CC BY 2.0.

The public product and scheduling scenes contain demonstration content, are labeled as illustrative, and make no live API requests. The preview is not an interactive or live dashboard. It does not claim model accuracy; the current training data is synthetic. There is no workshop enquiry or demo booking endpoint, so the marketing CTA leads to workshop sign-in. Customer registration remains a separate labeled action and creates a USER account.

## Authenticated application

The static HTML/CSS/JavaScript app is served by Spring Boot from `optiserve-backend/src/main/resources/static/` and calls the same-origin APIs. The customer workspace focuses on a welcome, vehicles, service requests, ordered progress, and wait estimates. The workshop workspace prioritizes active assignments and waiting stages, followed by requests and resource state. Service history, detailed work orders, service types, resources, analytics, and MANUAL/AUTO settings remain reachable through the role-specific navigation.

An individual work order composes the existing service request, ordered stage, assignment and wait-time responses. It displays recorded actual duration only when present. Assignment actions are restored from persisted assignment summaries and refresh after actions. Queue rows are labeled as arrival order; they do not claim to be the scheduler's assignment rank. The connection badge reflects the required request and queue reads; partial failures appear with unavailable values rather than fabricated zeros.

No WebSocket/EventSource updates are implemented. Data refreshes on navigation, explicit refresh, and after mutations. Admin and customer views share the brand palette and work-order vocabulary while keeping different navigation, density and actions.

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

The dashboard uses API responses and shows loading, empty, unavailable, validation, unauthorized, and server-error states. It does not invent operational records; missing estimate or actual-duration values remain absent. Assignment history displays persisted estimates and completed actual durations, and administrators can resume assignment actions from the assignments API after reloading. On narrow screens, the multi-column service-request table remains a semantic table inside a keyboard-focusable horizontal scroller with a swipe hint. The role model currently distinguishes USER and ADMIN; separate advisor, technician, and manager experiences remain a backend/product gap.

## Local checks

Static frontend tests use Playwright: from `optiserve-backend/`, run `npm test` after installing the checked-in package dependencies. Browser fixtures cover both USER and ADMIN views without production credentials or a live database; they verify API-driven rendering, role navigation, unavailable-data states and request-table scrolling. JavaScript syntax can be checked with `node --check src/main/resources/static/assets/app.js`. Run Maven checks from `optiserve-backend/`; database-backed tests require PostgreSQL and the test database environment variables described in the backend configuration. The landing page can be reviewed without API access; real operational integration still requires a running backend and authorized non-production accounts.

## Visual review artifacts

The `visual-audit/` directory contains desktop and mobile page captures plus mocked ADMIN and USER workspace views. Workspace captures use test fixtures and are not evidence of a live backend session. Public demonstrations are isolated from API traffic, visibly labeled, and follow the existing domain field shapes; they introduce no endpoint or backend behavior. The hero photo retains its license and attribution.
