import json
from pathlib import Path

import pytest

from app.features import MAX_VEHICLE_YEAR, MIN_VEHICLE_YEAR
from app.main import app
from fastapi.testclient import TestClient


CONTRACT_DIR = Path(__file__).resolve().parents[2] / "contracts" / "ml-duration-prediction"
REQUEST_CONTRACT = json.loads((CONTRACT_DIR / "request.json").read_text())
RESPONSE_CONTRACT = json.loads((CONTRACT_DIR / "response.json").read_text())
client = TestClient(app)


def test_shared_contract_fixtures_match_fastapi_openapi_and_endpoint(monkeypatch):
    schemas = app.openapi()["components"]["schemas"]
    request_schema = schemas["PredictionRequest"]
    response_schema = schemas["PredictionResponse"]

    assert list(request_schema["properties"]) == list(REQUEST_CONTRACT)
    assert request_schema["required"] == list(REQUEST_CONTRACT)
    assert {name: field["type"] for name, field in request_schema["properties"].items()} == {
        "serviceType": "string",
        "vehicleMake": "string",
        "vehicleModel": "string",
        "vehicleYear": "integer",
        "dayOfWeek": "string",
    }
    assert request_schema["properties"]["dayOfWeek"]["enum"] == [
        "MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"
    ]
    assert list(response_schema["properties"]) == list(RESPONSE_CONTRACT)
    assert response_schema["required"] == list(RESPONSE_CONTRACT)
    assert response_schema["properties"]["predictedDurationMinutes"]["type"] == "integer"

    monkeypatch.setattr("app.main.predict_duration", lambda _request: RESPONSE_CONTRACT["predictedDurationMinutes"])
    response = client.post("/predict-duration", json=REQUEST_CONTRACT)

    assert response.status_code == 200
    assert response.json() == RESPONSE_CONTRACT


@pytest.mark.parametrize("year", [MIN_VEHICLE_YEAR, MAX_VEHICLE_YEAR])
def test_contract_accepts_supported_vehicle_year_boundaries(monkeypatch, year):
    monkeypatch.setattr("app.main.predict_duration", lambda _request: RESPONSE_CONTRACT["predictedDurationMinutes"])

    response = client.post("/predict-duration", json={**REQUEST_CONTRACT, "vehicleYear": year})

    assert response.status_code == 200
    assert response.json() == RESPONSE_CONTRACT


@pytest.mark.parametrize("year", [MIN_VEHICLE_YEAR - 1, MAX_VEHICLE_YEAR + 1])
def test_contract_rejects_vehicle_years_outside_supported_boundaries(year):
    response = client.post("/predict-duration", json={**REQUEST_CONTRACT, "vehicleYear": year})

    assert response.status_code == 422
