"""
FastAPI application for OptiServe Service Duration ML microservice.

Predicts automotive service duration in minutes based on service type,
vehicle details, and scheduling day. Spring Boot remains authoritative for
queue waiting times, scheduling priority, and resource assignment.
"""

from contextlib import asynccontextmanager
from typing import AsyncGenerator
from fastapi import Depends, FastAPI, status
from app.model_loader import get_model_path, is_model_loaded, load_model
from app.predictor import ServiceDurationPredictor, get_predictor
from app.schemas import (
    DurationPredictionRequest,
    DurationPredictionResponse,
    HealthResponse,
)


@asynccontextmanager
async def lifespan(app: FastAPI) -> AsyncGenerator[None, None]:
    """Lifespan context manager ensuring model pipeline is loaded on startup."""
    try:
        load_model()
        print("Model pipeline loaded successfully at startup.")
    except Exception as e:
        print(f"Warning during model loading on startup: {e}")
    yield


app = FastAPI(
    title="OptiServe Service Duration ML Service",
    description=(
        "Independent ML microservice providing machine learning predictions "
        "for automotive service duration in minutes. Spring Boot backend remains "
        "authoritative for queue order, resource assignment, and wait-time calculation."
    ),
    version="1.0.0",
    docs_url="/docs",
    openapi_url="/openapi.json",
    lifespan=lifespan,
)


@app.get(
    "/health",
    response_model=HealthResponse,
    status_code=status.HTTP_200_OK,
    summary="Health check and model status",
    tags=["Health"],
)
def health_check() -> HealthResponse:
    """
    Returns service health status and model load availability.
    """
    model_path = get_model_path()
    loaded = is_model_loaded() or model_path.exists()
    return HealthResponse(
        status="healthy",
        model_loaded=loaded,
        model_path=str(model_path),
    )


@app.post(
    "/predict-duration",
    response_model=DurationPredictionResponse,
    status_code=status.HTTP_200_OK,
    summary="Predict automotive service duration in minutes",
    tags=["Prediction"],
)
def predict_duration(
    request: DurationPredictionRequest,
    predictor: ServiceDurationPredictor = Depends(get_predictor),
) -> DurationPredictionResponse:
    """
    Predicts the duration (in minutes) required for an automotive service.

    - **serviceType**: e.g., 'Oil Change', 'Engine Service', 'Brake Service'
    - **vehicleMake**: e.g., 'Hyundai', 'Tata', 'Maruti', 'Toyota'
    - **vehicleModel**: e.g., 'i20', 'Nexon', 'Swift', 'Innova'
    - **vehicleYear**: integer e.g., 2022
    - **dayOfWeek**: MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY
    """
    predicted_minutes = predictor.predict(
        service_type=request.serviceType,
        vehicle_make=request.vehicleMake,
        vehicle_model=request.vehicleModel,
        vehicle_year=request.vehicleYear,
        day_of_week=request.dayOfWeek.value,
    )
    return DurationPredictionResponse(predictedDurationMinutes=predicted_minutes)
