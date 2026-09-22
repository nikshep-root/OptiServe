"""
Unit tests for service duration predictor and model loader.
"""

from pathlib import Path
import pytest
from app.model_loader import get_model_path, load_model
from app.predictor import ServiceDurationPredictor, get_predictor


def test_model_loads_successfully() -> None:
    """Test that the persisted model artifact exists and loads correctly."""
    model_path = get_model_path()
    assert model_path.exists(), f"Model artifact does not exist at {model_path}"

    model = load_model()
    assert model is not None
    assert hasattr(model, "predict"), "Loaded artifact must have a predict method"


def test_missing_model_raises_filenotfound() -> None:
    """Test that pointing to a non-existent path raises FileNotFoundError."""
    invalid_path = Path("non_existent_dir") / "fake_model.joblib"
    with pytest.raises(FileNotFoundError):
        load_model(invalid_path)


def test_valid_prediction_returns_positive_int() -> None:
    """Test that a valid input produces a positive integer duration."""
    predictor = get_predictor()
    duration = predictor.predict(
        service_type="Engine Service",
        vehicle_make="Hyundai",
        vehicle_model="i20",
        vehicle_year=2022,
        day_of_week="MONDAY",
    )

    assert isinstance(duration, int)
    assert duration > 0


def test_prediction_is_deterministic() -> None:
    """Test that identical inputs produce identical predictions (no artificial noise)."""
    predictor = get_predictor()
    params = {
        "service_type": "Brake Service",
        "vehicle_make": "Tata",
        "vehicle_model": "Nexon",
        "vehicle_year": 2021,
        "day_of_week": "WEDNESDAY",
    }

    pred1 = predictor.predict(
        service_type=params["service_type"],
        vehicle_make=params["vehicle_make"],
        vehicle_model=params["vehicle_model"],
        vehicle_year=params["vehicle_year"],
        day_of_week="WEDNESDAY",
    )
    pred2 = predictor.predict(
        service_type=params["service_type"],
        vehicle_make=params["vehicle_make"],
        vehicle_model=params["vehicle_model"],
        vehicle_year=params["vehicle_year"],
        day_of_week="WEDNESDAY",
    )
    pred3 = predictor.predict(
        service_type=params["service_type"],
        vehicle_make=params["vehicle_make"],
        vehicle_model=params["vehicle_model"],
        vehicle_year=params["vehicle_year"],
        day_of_week="WEDNESDAY",
    )

    assert pred1 == pred2 == pred3


def test_unseen_category_handling() -> None:
    """Test that unseen makes or models do not crash inference due to OneHotEncoder(handle_unknown='ignore')."""
    predictor = get_predictor()
    duration = predictor.predict(
        service_type="Oil Change",
        vehicle_make="UnknownBrand",
        vehicle_model="MysteryModel",
        vehicle_year=2020,
        day_of_week="FRIDAY",
    )

    assert isinstance(duration, int)
    assert duration > 0


def test_safe_positive_duration_guard() -> None:
    """Test that even if a pipeline predicted a non-positive value, predictor returns at least 1."""
    class MockPipeline:
        def predict(self, _):
            return [-15.0]

    predictor = ServiceDurationPredictor(model_pipeline=MockPipeline())
    duration = predictor.predict(
        service_type="Oil Change",
        vehicle_make="Maruti",
        vehicle_model="Swift",
        vehicle_year=2024,
        day_of_week="MONDAY",
    )

    assert duration == 1
