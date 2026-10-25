from contextlib import asynccontextmanager
import logging

from fastapi import FastAPI, HTTPException

from app.model_loader import ModelNotFoundError, get_model
from app.predictor import predict_duration
from app.schemas import PredictionRequest, PredictionResponse


@asynccontextmanager
async def lifespan(app: FastAPI):
    yield


app = FastAPI(
    title="OptiServe Duration Prediction Service",
    description=(
        "Independent ML service that predicts expected service duration "
        "(minutes) for an OptiServe service stage. Advisory only — does not "
        "calculate queue waiting time or scheduling priority, and never "
        "touches the OptiServe database."
    ),
    version="0.1.0",
    lifespan=lifespan,
)

LOGGER = logging.getLogger(__name__)
MODEL_UNAVAILABLE_DETAIL = "Prediction model is unavailable."


def _safe_failure_category(exception: ModelNotFoundError) -> str:
    cause = exception.__cause__
    return type(cause).__name__ if cause is not None else "ModelUnavailable"


@app.get("/health")
def health():
    return {"status": "ok"}


@app.get("/ready")
def ready():
    try:
        get_model()
    except ModelNotFoundError as exc:
        LOGGER.warning("ML readiness failed (cause=%s)", _safe_failure_category(exc))
        raise HTTPException(status_code=503, detail=MODEL_UNAVAILABLE_DETAIL) from None
    return {"status": "ready"}


@app.post("/predict-duration", response_model=PredictionResponse)
def predict_duration_endpoint(request: PredictionRequest) -> PredictionResponse:
    try:
        minutes = predict_duration(request)
    except ModelNotFoundError as exc:
        LOGGER.warning("ML prediction unavailable (cause=%s)", _safe_failure_category(exc))
        raise HTTPException(status_code=503, detail=MODEL_UNAVAILABLE_DETAIL) from None

    return PredictionResponse(predictedDurationMinutes=minutes)
