"""Smoke-test the committed model artifact separately from contract stubs."""

import warnings

from sklearn.exceptions import InconsistentVersionWarning

from app.model_loader import get_model_artifact
from app.predictor import predict_duration
from app.schemas import PredictionRequest


def test_committed_model_artifact_loads_and_predicts_without_compatibility_warnings():
    get_model_artifact.cache_clear()
    request = PredictionRequest(
        serviceType="Oil Change",
        vehicleMake="Hyundai",
        vehicleModel="i20",
        vehicleYear=2022,
        dayOfWeek="MONDAY",
    )

    try:
        with warnings.catch_warnings():
            warnings.simplefilter("error", InconsistentVersionWarning)
            warnings.filterwarnings(
                "error",
                message="Setting the shape on a NumPy array has been deprecated",
                category=DeprecationWarning,
            )
            artifact = get_model_artifact()
            first_prediction = predict_duration(request)
            second_prediction = predict_duration(request)

        assert "pipeline" in artifact
        assert first_prediction == second_prediction
        assert isinstance(first_prediction, int)
        assert first_prediction == 28
    finally:
        get_model_artifact.cache_clear()
