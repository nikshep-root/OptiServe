# OptiServe — Duration Prediction ML Service

An independent Python microservice that predicts the **expected service
duration (in minutes)** for an OptiServe service stage.

This service is deliberately narrow in scope:

- ✅ Predicts service duration.
- ❌ Does **not** calculate queue waiting time.
- ❌ Does **not** determine scheduling priority.
- ❌ Does **not** access the OptiServe PostgreSQL database.
- ❌ Does **not** modify any queue, resource, or assignment state.

Predictions are **advisory**. The Spring Boot backend (`ServiceDurationPredictor`
abstraction) remains the authoritative system for all operational decisions.

---

## 1. Dataset

`data/service_history.csv` is currently a **synthetic** dataset
(`training/generate_synthetic_data.py`), generated because OptiServe does not
yet have enough real completed-assignment history to train on.

Each row simulates one completed service:

| Column             | Description                              |
| ------------------ | ----------------------------------------- |
| `serviceType`       | e.g. "Engine Service", "Oil Change"       |
| `vehicleMake`        | e.g. "Hyundai"                            |
| `vehicleModel`        | e.g. "i20"                                |
| `vehicleYear`         | Manufacturing year                        |
| `dayOfWeek`           | MONDAY–SUNDAY                             |
| `durationMinutes`     | Target — actual/simulated service duration |

Generation logic: a base duration per `serviceType`, adjusted by vehicle age
(older vehicles take slightly longer) and day-of-week load (Fri/Sat/Sun busier),
plus Gaussian noise so the relationship isn't perfectly linear.

**Limitations:** this data is synthetic and encodes the same assumptions used
to generate its labels. Metrics from this dataset measure how well a model
fits that simulator; they do **not** estimate real-world accuracy or prove
that a deployed prediction is operationally reliable. Replace it with an
approved, representative export of completed assignments before making any
real-world accuracy claim. Check for data quality, leakage, and coverage
before using such an export for training.

## 2. Feature engineering

`app/features.py` is the single source of truth for feature construction,
shared by both training and inference so they can never drift apart:

- `serviceType`, `vehicleMake`, `vehicleModel`, `dayOfWeek` → one-hot encoded
  (`handle_unknown="ignore"`, so an unseen make/model at inference time
  doesn't crash the request).
- `vehicleYear` → converted to `vehicleAge` (current year − `vehicleYear`,
  clipped at 0), which is a more directly useful signal than the raw year.

## 3. Training

Use Python 3.12 and a clean virtual environment. The scikit-learn,
NumPy, SciPy, joblib, threadpoolctl, and pandas versions are pinned in
`requirements.txt`. The scikit-learn pin matches the version embedded in the
committed artifact; joblib is pinned to a newer release to avoid the NumPy
array-shape deprecation emitted by the former joblib 1.4.2 loader with NumPy
2.5.

```powershell
cd ml-service
py -3.12 -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
.\.venv\Scripts\python.exe training\generate_synthetic_data.py --rows 4000 --seed 42
.\.venv\Scripts\python.exe training\train.py
```

### Render Python runtime

The repository has no Render service manifest, so it cannot confirm or change
a dashboard-only runtime override. `ml-service/.python-version` pins the
service to Python 3.12 when the Render service's **Root Directory** is set to
`ml-service`. In the Render dashboard, verify that root directory and check
the Environment page for a `PYTHON_VERSION` value: Render gives that variable
precedence over `.python-version`, so if it is set, change it to a compatible
Python 3.12 release. Render's default runtime varies by service creation date
and may not match CI.

The generator and trainer both use seed `42` by default. Regenerating the CSV
overwrites the checked-in synthetic dataset, so review that data diff before
keeping it. `train.py` creates `model/candidates/service_duration_model.joblib`
and `model/candidates/metrics.json`; it refuses to write to the deployed
artifact path `model/service_duration_model.joblib`.

`train.py`:
1. Loads `data/service_history.csv`.
2. Builds features via `app/features.py`.
3. Deterministic 70/15/15 train/validation/test split (seed `42`).
4. Trains two pipelines (preprocessing + model), each wrapped in a single
   sklearn `Pipeline` so preprocessing and the model are always persisted
   and loaded together:
   - **Linear Regression** (baseline)
   - **Random Forest Regressor** (200 trees, single worker)
5. Evaluates both candidates on validation data and selects the lower MAE.
6. Refits the selected pipeline on train plus validation data.
7. Evaluates it once on the untouched test partition. The report includes
   MAE (average absolute error, in minutes), RMSE (penalizes larger errors,
   in minutes), and R².
8. Writes the candidate artifact and `metrics.json` only under
   `model/candidates/`, including dataset SHA-256, split sizes, seed, and
   Python/NumPy/pandas/scikit-learn versions. Candidate files are ignored by
   Git. Review the candidate and metrics before any separate deployment-artifact
   replacement decision.

The existing `model/metrics.json` contains older validation-only results and
is not a held-out test report. It must not be presented as real-world accuracy.

## 4. Evaluation

`training/evaluate.py` holds the shared metric helpers used by `train.py`.
Run `python training/train.py` to generate the reproducible held-out report at
`model/candidates/metrics.json`. Do not evaluate a model trained on the full
dataset against that same dataset and call it a held-out result.

Metrics reported: **MAE** (mean absolute error, minutes), **RMSE** (root mean
squared error, minutes), **R²** (variance explained).

## 5. Running the API

```powershell
.\.venv\Scripts\python.exe -m uvicorn app.main:app --reload --port 8000
```

### `POST /predict-duration`

Request:

```json
{
  "serviceType": "Engine Service",
  "vehicleMake": "Hyundai",
  "vehicleModel": "i20",
  "vehicleYear": 2022,
  "dayOfWeek": "MONDAY"
}
```

Response:

```json
{
  "predictedDurationMinutes": 97
}
```

Invalid input (bad `dayOfWeek`, missing field, out-of-range `vehicleYear` outside
the supported 2008–2026 range) returns `422`. If the model is missing or cannot be loaded,
the API returns `503` with a descriptive error message.

### `GET /health`

Liveness probe — returns `{"status": "ok"}` when the FastAPI process is running.
It does not check or depend on ML model availability.

### `GET /ready`

Readiness probe — checks whether the trained ML model is available and can be loaded.
Returns `{"status": "ready"}` with HTTP 200 when ready to serve predictions, or HTTP 503
when the model is missing, unavailable, corrupt, or cannot be loaded.

## 6. Tests

```powershell
.\.venv\Scripts\python.exe -m pytest tests\ -v
```

- `tests/test_preprocessing.py` — feature engineering (vehicle age
  computation, negative-age clipping, categorical pass-through).
- `tests/test_predictor.py` — end-to-end API tests via FastAPI's
  `TestClient`: liveness (`/health`), readiness (`/ready`) when model is available
  vs unavailable/corrupt (HTTP 503), prediction availability and 503 handling,
  supported `vehicleYear` range acceptance (2008–2026) and boundary rejections (HTTP 422),
  positive integer predictions, deterministic outputs, invalid field handling,
  and graceful unseen categorical values.
- `tests/test_model_artifact.py` loads the actual committed joblib artifact,
  rejects scikit-learn version and NumPy shape deprecation warnings, and makes
  a representative prediction. It is separate from tests that stub the API
  prediction function.
- `tests/test_training_reproducibility.py` verifies seeded synthetic data and
  deterministic, disjoint 70/15/15 splits.

### Cross-service contract regression

The canonical JSON examples are in `contracts/ml-duration-prediction/` at the
repository root. Both the Spring Boot client tests and FastAPI contract tests
load those same request and response fixtures. The contract tests verify the
request fields and types against FastAPI's generated OpenAPI schema, exercise
the endpoint, and cover the supported vehicle-year boundaries (2008 and 2026).
FastAPI's contract test replaces the prediction function with a fixed value, so
it neither calls an external service nor loads a trained model. The Spring test
uses an in-process HTTP stub and checks fallback for malformed responses, HTTP
errors, timeouts, unavailable service, and invalid duration values.

From the repository root, run the focused contract tests:

```powershell
Push-Location .\ml-service
.\.venv\Scripts\python.exe -m pytest tests\test_contract.py -v
Pop-Location
Push-Location .\optiserve-backend
.\mvnw.cmd -B -Dtest=MlServiceDurationPredictorTests test
Pop-Location
```

Run the complete ML service suite from `ml-service/` with
`.\.venv\Scripts\python.exe -m pytest tests\ -v`. Run the full PostgreSQL-backed
backend verification from the repository root with
`.\optiserve-backend\scripts\verify-with-test-postgres.ps1`, or from
`optiserve-backend/` with `.\scripts\verify-with-test-postgres.ps1`. The
focused Maven test uses only a local stub; the full verification script
provisions its own disposable PostgreSQL test container.

## 7. Project structure

```
ml-service/
├── app/
│   ├── main.py           # FastAPI app + /predict-duration, /health, /ready
│   ├── schemas.py         # Pydantic request/response models + validation
│   ├── features.py         # Shared feature engineering + vehicle year range
│   ├── predictor.py         # Builds features, calls model, clamps output
│   └── model_loader.py       # Loads/caches the persisted joblib model
├── model/
│   ├── service_duration_model.joblib
│   └── metrics.json        # MAE/RMSE/R² for both candidate models
├── training/
│   ├── generate_synthetic_data.py
│   ├── train.py
│   └── evaluate.py
├── data/
│   └── service_history.csv
├── tests/
│   ├── test_preprocessing.py
│   └── test_predictor.py
├── pytest.ini
├── requirements.txt
└── README.md
```


## 8. Service boundary and Spring Boot integration

- No database access of any kind.
- No queue/resource/assignment mutation.
- No authentication.
- No notifications/WhatsApp.
- This service remains independently runnable and does not access the backend database.
- Spring Boot calls `POST /predict-duration` using `OPTISERVE_ML_BASE_URL`.
- The response is advisory service-duration input. Spring Boot validates it and uses the configured service-type duration when the call fails, the model is unavailable, or inputs/predictions are unsupported.
- Queue priority, resource compatibility, assignment, and waiting-time estimates remain backend responsibilities.
