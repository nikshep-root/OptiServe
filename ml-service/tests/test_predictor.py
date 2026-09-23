from fastapi.testclient import TestClient

from app.main import app

client = TestClient(app)

VALID_PAYLOAD = {
    "serviceType": "Engine Service",
    "vehicleMake": "Hyundai",
    "vehicleModel": "i20",
    "vehicleYear": 2022,
    "dayOfWeek": "MONDAY",
}


def test_health_check():
    response = client.get("/health")
    assert response.status_code == 200
    assert response.json() == {"status": "ok"}


def test_predict_duration_returns_positive_integer():
    response = client.post("/predict-duration", json=VALID_PAYLOAD)
    assert response.status_code == 200

    body = response.json()
    assert "predictedDurationMinutes" in body
    assert isinstance(body["predictedDurationMinutes"], int)
    assert body["predictedDurationMinutes"] > 0


def test_predict_duration_is_deterministic():
    first = client.post("/predict-duration", json=VALID_PAYLOAD).json()
    second = client.post("/predict-duration", json=VALID_PAYLOAD).json()
    assert first == second


def test_predict_duration_rejects_invalid_day_of_week():
    payload = {**VALID_PAYLOAD, "dayOfWeek": "FUNDAY"}
    response = client.post("/predict-duration", json=payload)
    assert response.status_code == 422


def test_predict_duration_rejects_missing_field():
    payload = {k: v for k, v in VALID_PAYLOAD.items() if k != "vehicleMake"}
    response = client.post("/predict-duration", json=payload)
    assert response.status_code == 422


def test_predict_duration_rejects_unreasonable_year():
    payload = {**VALID_PAYLOAD, "vehicleYear": 1800}
    response = client.post("/predict-duration", json=payload)
    assert response.status_code == 422


def test_predict_duration_handles_unseen_categorical_gracefully():
    # A make/model the model never saw during training — OneHotEncoder
    # with handle_unknown="ignore" should still produce a valid prediction
    # rather than raising.
    payload = {**VALID_PAYLOAD, "vehicleMake": "Skoda", "vehicleModel": "Kushaq"}
    response = client.post("/predict-duration", json=payload)
    assert response.status_code == 200
    assert response.json()["predictedDurationMinutes"] > 0
