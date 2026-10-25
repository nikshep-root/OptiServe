# Production health and observability

## Spring Boot backend

The backend exposes only a small health surface publicly. Responses include status only; component names, connection details, and health details are hidden.

| Endpoint | Access | Meaning |
| --- | --- | --- |
| `GET /actuator/health/liveness` | Public | Process is alive. It does not depend on PostgreSQL or the ML service. |
| `GET /actuator/health/readiness` | Public | Backend can serve requests. The readiness group checks Spring readiness state and PostgreSQL. The optional ML service is intentionally excluded because prediction falls back to the configured default duration. |
| `GET /actuator/health` | Public | Aggregate status only. |
| `GET /actuator/metrics` and `GET /actuator/metrics/{name}` | Admin JWT | Read only the explicitly exposed Micrometer metrics. |

Prediction counters have fixed names and no input-derived tags:

- `optiserve.ml.predictions.success` — valid model responses accepted by the backend.
- `optiserve.ml.predictions.timeout` — ML HTTP calls that timed out.
- `optiserve.ml.predictions.error` — other HTTP/client failures and invalid model responses.
- `optiserve.ml.predictions.fallback` — calls that use the service type's default duration, including unsupported model inputs.

For example, inspect a counter with an administrator bearer token:

```powershell
curl.exe -H "Authorization: Bearer <admin-token>" `
  https://<backend-host>/actuator/metrics/optiserve.ml.predictions.fallback
```

Counters are process-local and reset when the backend restarts. The metrics endpoint is protected by the existing ADMIN role. Only `health` and `metrics` are exposed; endpoints such as `/actuator/env` and `/actuator/configprops` are not exposed.

## FastAPI ML service

| Endpoint | Meaning |
| --- | --- |
| `GET /health` | Process liveness only; remains healthy if the model artifact is unavailable. |
| `GET /ready` | Prediction readiness; loads/verifies the model and returns 503 if unavailable. Error responses use a generic message and do not reveal filesystem paths. |
| `POST /predict-duration` | Advisory service-duration prediction. A missing model returns 503; the backend falls back to the service type default. |

ML model loading failures are logged with a bounded exception category, without exception text or request data. The backend predictor logs only the failure class and never logs customer or vehicle identifiers, credentials, or tokens.

## Render deployment verification

There is no Render service manifest in this repository. The backend uses [`optiserve-backend/Dockerfile`](optiserve-backend/Dockerfile), so configure its health check in the Render dashboard:

1. Set the backend service's **Health Check Path** to `/actuator/health/readiness`.
2. Deploy and verify that liveness and readiness return HTTP 200. Readiness will return non-200 if PostgreSQL is unavailable; an ML outage alone will not make it fail.
3. For the ML service, use repository root directory `ml-service`, Python 3.12, and (if configured) health check path `/ready`. `/health` is the process-only alternative when testing liveness.
4. Check backend logs for ML timeout/error warnings, then read the admin-only counters to confirm whether predictions succeed or fall back.

This repository does not confirm live Render dashboard settings or production health. Verify the health-check paths after deployment.

### Administrator bootstrap cleanup

The backend defaults `OPTISERVE_ADMIN_BOOTSTRAP_ENABLED` to `false`, and the provisioner is inactive unless the `local` or explicit `admin-bootstrap` Spring profile is active. After one-time production provisioning, verify the Render backend no longer has `admin-bootstrap` in `SPRING_PROFILES_ACTIVE` and has no `OPTISERVE_ADMIN_BOOTSTRAP_ENABLED=true`, `OPTISERVE_ADMIN_PASSWORD`, or `OPTISERVE_ADMIN_EMAIL` values. Preserve any other required Spring profiles. Never paste or log the values while checking them.

## Troubleshooting

- **Backend readiness returns 503:** Check Render PostgreSQL availability, the backend database service binding, and connection failures in backend logs. Do not put credentials in diagnostic output.
- **Backend liveness is 503:** The Spring process is not healthy; inspect startup and crash logs. Liveness intentionally does not restart the process just because PostgreSQL or ML is unavailable.
- **ML `/ready` returns 503:** Confirm the deployed artifact exists and is loadable, the ML service root directory is `ml-service`, Python is 3.12, and dependencies match `ml-service/requirements.txt`. The response is generic; inspect the ML service logs for the exception category.
- **Predictions fall back while readiness is healthy:** Check the admin-only timeout/error/fallback counters and verify `OPTISERVE_ML_BASE_URL` points to the ML service. Fallbacks are expected for unsupported model inputs and are safe for request creation.
- **Metrics return 401/403:** Use a valid administrator JWT. Customer tokens cannot access actuator metrics or health component paths.
