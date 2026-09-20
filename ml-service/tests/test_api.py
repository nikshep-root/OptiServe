"""
API integration tests for OptiServe ML Service FastAPI application.
"""

from fastapi.testclient import TestClient
from app.main import app

client = TestClient(app)


def test_health_endpoint() -> None:
    """Test GET /health returns 200 and healthy status."""
    response = client.get("/health")
    assert response.status_code == 200
    data = response.json()
    assert data["status"] == "healthy"
    assert data["model_loaded"] is True
    assert "model_path" in data


def test_predict_duration_valid_input() -> None:
    """Test POST /predict-duration succeeds with valid payload."""
    payload = {
        "serviceType": "Engine Service",
        "vehicleMake": "Hyundai",
        "vehicleModel": "i20",
        "vehicleYear": 2022,
        "dayOfWeek": "MONDAY",
    }
    response = client.post("/predict-duration", json=payload)
    assert response.status_code == 200
    data = response.json()
    assert "predictedDurationMinutes" in data
    assert isinstance(data["predictedDurationMinutes"], int)
    assert data["predictedDurationMinutes"] > 0


def test_predict_duration_rejects_invalid_day_of_week() -> None:
    """Test POST /predict-duration rejects invalid dayOfWeek values."""
    payload = {
        "serviceType": "Oil Change",
        "vehicleMake": "Tata",
        "vehicleModel": "Nexon",
        "vehicleYear": 2021,
        "dayOfWeek": "FUNDAY",  # Invalid day
    }
    response = client.post("/predict-duration", json=payload)
    assert response.status_code == 422


def test_predict_duration_rejects_invalid_vehicle_year() -> None:
    """Test POST /predict-duration rejects years outside sensible boundaries."""
    payload_too_old = {
        "serviceType": "Oil Change",
        "vehicleMake": "Toyota",
        "vehicleModel": "Innova",
        "vehicleYear": 1800,
        "dayOfWeek": "TUESDAY",
    }
    response = client.post("/predict-duration", json=payload_too_old)
    assert response.status_code == 422

    payload_future = {
        "serviceType": "Oil Change",
        "vehicleMake": "Toyota",
        "vehicleModel": "Innova",
        "vehicleYear": 2099,
        "dayOfWeek": "TUESDAY",
    }
    response = client.post("/predict-duration", json=payload_future)
    assert response.status_code == 422


def test_predict_duration_rejects_empty_or_whitespace_strings() -> None:
    """Test POST /predict-duration rejects empty or whitespace-only strings."""
    payload_empty = {
        "serviceType": "   ",
        "vehicleMake": "Hyundai",
        "vehicleModel": "i20",
        "vehicleYear": 2022,
        "dayOfWeek": "MONDAY",
    }
    response = client.post("/predict-duration", json=payload_empty)
    assert response.status_code == 422


def test_predict_duration_rejects_missing_fields() -> None:
    """Test POST /predict-duration rejects requests missing required fields."""
    payload_incomplete = {
        "serviceType": "Oil Change",
        "vehicleMake": "Maruti",
    }
    response = client.post("/predict-duration", json=payload_incomplete)
    assert response.status_code == 422


def test_openapi_docs_accessible() -> None:
    """Test OpenAPI schema and documentation endpoints are accessible."""
    response = client.get("/openapi.json")
    assert response.status_code == 200
    schema = response.json()
    assert "paths" in schema
    assert "/predict-duration" in schema["paths"]
    assert "/health" in schema["paths"]

    docs_response = client.get("/docs")
    assert docs_response.status_code == 200
