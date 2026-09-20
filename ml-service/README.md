# OptiServe Service Duration ML Service

## Overview
The **OptiServe Service Duration ML Service** is a lightweight, production-ready machine learning microservice built with FastAPI and scikit-learn. It is designed to provide data-driven estimates of how long a given automotive service will take (in minutes) based on historical service patterns, vehicle characteristics, and scheduling conditions.

## Objective
The primary objective of this microservice is to implement GitHub Issue #12: *"Implement service-duration prediction ML service"*. It replaces static or arbitrary time estimates with an empirical machine learning model trained on historical service records, providing a specialized prediction endpoint for the OptiServe platform.

---

## Architectural Boundaries

> [!IMPORTANT]
> **Strict Operational Boundary**:
> The ML service predicts **SERVICE DURATION ONLY** in minutes.
> It does **NOT** calculate:
> - Queue waiting time
> - Scheduling priority
> - Resource availability / bay allocation
> - Queue ordering
> - Mechanic or technician assignment
> 
> The **Spring Boot backend remains authoritative** for all queue management, bay resource allocations, scheduling policies, customer notifications, and operational decisions. The Spring Boot backend codebase contains a `ServiceDurationPredictor` abstraction interface designed to interact with this microservice boundary.

Do note that Spring Boot is not yet modified or coupled to this service; this repository maintains clear separation of concerns as an independent microservice.

---

## Architecture
The microservice is architected into clean, decoupled layers:

```
ml-service/
├── app/
│   ├── __init__.py           # Package marker
│   ├── main.py               # FastAPI application, OpenAPI documentation, and routing
│   ├── schemas.py            # Pydantic v2 schemas and validation logic
│   ├── predictor.py          # Prediction abstraction and inference safety guards
│   └── model_loader.py       # Model artifact resolution and memory caching
├── model/
│   └── service_duration_model.joblib  # Serialized scikit-learn pipeline (preprocessor + estimator)
├── training/
│   ├── generate_data.py      # Reproducible synthetic dataset generator
│   ├── train.py              # Model training, cross-model comparison, and artifact serialization
│   └── evaluate.py           # Independent validation script reproducing test metrics
├── data/
│   └── service_history.csv   # Historical automotive service dataset
├── tests/
│   ├── __init__.py           # Test package marker
│   ├── test_predictor.py     # Predictor logic and model loader unit tests
│   └── test_api.py           # FastAPI endpoint and HTTP schema integration tests
├── requirements.txt          # Python dependencies
└── README.md                 # Complete service documentation
```

---

## Dataset
- **File**: `data/service_history.csv`
- **Volume**: 1,000 historical service records.
- **Context**: The dataset represents historical automotive repair and maintenance visits across popular automotive brands and models. For academic and demonstration purposes where production service telematics may not yet be available, this dataset is synthetically generated with realistic service duration distributions, vehicle complexity weights, age decay, and day-of-week shop dynamics.

### Features
The dataset contains the following 6 fields:
1. `serviceType` (*categorical*): Type of automotive service performed (e.g. `Oil Change`, `Brake Service`, `AC Service`, `Engine Service`, `Clutch Service`, `Transmission Service`, `Battery Replacement`, `Wheel Alignment`, `Tire Replacement`, `General Inspection`).
2. `vehicleMake` (*categorical*): Vehicle manufacturer (e.g. `Hyundai`, `Tata`, `Maruti`, `Honda`, `Toyota`, `Kia`).
3. `vehicleModel` (*categorical*): Specific vehicle model (e.g. `i20`, `Creta`, `Nexon`, `Swift`, `Innova`, `Seltos`).
4. `vehicleYear` (*numerical*): Vehicle year of manufacture (2015–2025).
5. `dayOfWeek` (*categorical*): Day of the week when the service occurred (`MONDAY`, `TUESDAY`, `WEDNESDAY`, `THURSDAY`, `FRIDAY`, `SATURDAY`, `SUNDAY`).
6. `durationMinutes` (*numerical target*): Total service duration in minutes (positive integer).

---

## Data Preprocessing
Data preprocessing is managed using a single scikit-learn `ColumnTransformer`:
- **Categorical Columns** (`serviceType`, `vehicleMake`, `vehicleModel`, `dayOfWeek`):
  - Encoded with `OneHotEncoder(handle_unknown='ignore', sparse_output=False)`.
  - Setting `handle_unknown='ignore'` guarantees that novel or unseen vehicle models or service tags received at inference time do not cause unhandled exceptions.
- **Numerical Columns** (`vehicleYear`):
  - Passed through directly (`passthrough`).
- **Unified Pipeline**:
  - The preprocessing pipeline is bundled directly with the estimator into a single scikit-learn `Pipeline`. Training and inference share the exact same preprocessing logic without code duplication.

---

## Models
During training, two regression models are trained and benchmarked against an 80/20 train/test split:
1. **Linear Regression**: Baseline multivariate linear model fitting linear coefficients for each encoded feature.
2. **Random Forest Regressor**: Non-linear ensemble model with 100 decision trees (`n_estimators=100`, `max_depth=12`, `min_samples_split=4`, `min_samples_leaf=2`, `random_state=42`).

---

## Model Evaluation
Performance is evaluated on the held-out test split (20% of data) using:
- **MAE** (Mean Absolute Error): Average absolute difference between predicted and actual duration in minutes.
- **RMSE** (Root Mean Squared Error): Penalizes larger errors; calculated universally using `np.sqrt(mean_squared_error(y_true, y_pred))`.
- **R²** (Coefficient of Determination): Proportion of variance explained by the model.

### Measured Evaluation Metrics
| Model | MAE (mins) | RMSE (mins) | R² |
| :--- | :--- | :--- | :--- |
| **Linear Regression** | **9.7302** | **13.0137** | **0.9509** |
| Random Forest Regressor | 10.9655 | 14.4343 | 0.9397 |

---

## Model Selection
The model selection process is fully deterministic:
- Models are evaluated on the identical held-out test split.
- The pipeline selects the model that minimizes validation RMSE, breaking ties with MAE and R².
- **Selected Model**: **Linear Regression** achieved the best test performance (RMSE of 13.01 vs 14.43 for Random Forest, MAE of 9.73 vs 10.97, and R² of 0.9509 vs 0.9397). The relationships in duration across service categories, vehicle age, and models are effectively linear with additive components, making the regularized linear model generalize better without overfitting small categorical splits.

---

## Model Persistence
- The selected complete pipeline (including the `ColumnTransformer` and the fitted estimator) is serialized using `joblib` to:
  `model/service_duration_model.joblib`
- The API loads this single pipeline file upon startup. No preprocessing logic is duplicated inside the API service.

---

## FastAPI API

### Interactive API Documentation
- **Swagger UI**: `http://localhost:8000/docs`
- **OpenAPI Schema**: `http://localhost:8000/openapi.json`

### Endpoints

#### 1. `GET /health`
Verifies that the service is running and the model artifact is loaded and ready.
- **Response**:
  ```json
  {
    "status": "healthy",
    "model_loaded": true,
    "model_path": ".../model/service_duration_model.joblib"
  }
  ```

#### 2. `POST /predict-duration`
Calculates predicted duration in integer minutes for a vehicle service request.

##### Request Example
```bash
curl -X POST "http://localhost:8000/predict-duration" \
  -H "Content-Type: application/json" \
  -d '{
    "serviceType": "Engine Service",
    "vehicleMake": "Hyundai",
    "vehicleModel": "i20",
    "vehicleYear": 2022,
    "dayOfWeek": "MONDAY"
  }'
```

##### Response Example
```json
{
  "predictedDurationMinutes": 204
}
```

---

## How to Run

### 1. Install Dependencies
```bash
cd ml-service
pip install -r requirements.txt
```

### 2. Generate Dataset (Optional - Dataset is pre-generated)
```bash
python training/generate_data.py
```

### 3. Run Training Script
Trains both models, prints comparison table, selects the winner, and saves the pipeline artifact:
```bash
python training/train.py
```

### 4. Run Evaluation Script
Reproduces evaluation metrics against the persisted model:
```bash
python training/evaluate.py
```

### 5. Run Automated Tests
```bash
pytest -v
```

### 6. Run the FastAPI Service
```bash
uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
```

---

## Limitations
1. **Synthetic Training Data**: The current dataset uses realistic synthetic distributions; models should be retrained once production service completion logs are accumulated.
2. **Deterministic Service Focus**: Real-world service duration may experience unexpected technician delays, parts out-of-stock, or unforeseen mechanical damage discovered during disassembly. The ML service predicts standard baseline duration, and operational adjustments must be handled by the backend scheduler.
3. **No Database Dependencies**: The service operates statelessly and does not read from or write to the OptiServe PostgreSQL database.

---

## Future Integration with Spring Boot
- The OptiServe Spring Boot backend defines a `ServiceDurationPredictor` abstraction.
- Future work will configure a Spring `RestClient` or `WebClient` to invoke `POST /predict-duration` during booking creation or queue placement.
- If the ML service is unreachable or encounters network timeout, the backend can safely fall back to static service category duration defaults without interrupting customer bookings.
