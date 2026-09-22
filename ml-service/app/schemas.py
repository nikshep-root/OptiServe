"""
Pydantic schemas for OptiServe service duration ML service.
"""

from enum import Enum
from pydantic import BaseModel, ConfigDict, Field, field_validator


class DayOfWeek(str, Enum):
    MONDAY = "MONDAY"
    TUESDAY = "TUESDAY"
    WEDNESDAY = "WEDNESDAY"
    THURSDAY = "THURSDAY"
    FRIDAY = "FRIDAY"
    SATURDAY = "SATURDAY"
    SUNDAY = "SUNDAY"


class DurationPredictionRequest(BaseModel):
    model_config = ConfigDict(
        json_schema_extra={
            "example": {
                "serviceType": "Engine Service",
                "vehicleMake": "Hyundai",
                "vehicleModel": "i20",
                "vehicleYear": 2022,
                "dayOfWeek": "MONDAY",
            }
        }
    )

    serviceType: str = Field(
        ...,
        description="Type of automotive service (e.g. Oil Change, Engine Service)",
    )
    vehicleMake: str = Field(
        ...,
        description="Vehicle manufacturer (e.g. Hyundai, Tata, Maruti)",
    )
    vehicleModel: str = Field(
        ...,
        description="Vehicle model (e.g. i20, Nexon, Swift)",
    )
    vehicleYear: int = Field(
        ...,
        ge=1990,
        le=2035,
        description="Manufacturing year of the vehicle (between 1990 and 2035)",
    )
    dayOfWeek: DayOfWeek = Field(
        ...,
        description="Day of week on which service is scheduled",
    )

    @field_validator("serviceType", "vehicleMake", "vehicleModel")
    @classmethod
    def validate_non_empty_string(cls, v: str) -> str:
        stripped = v.strip()
        if not stripped:
            raise ValueError("Field cannot be empty or whitespace only")
        return stripped


class DurationPredictionResponse(BaseModel):
    model_config = ConfigDict(
        json_schema_extra={
            "example": {
                "predictedDurationMinutes": 75,
            }
        }
    )

    predictedDurationMinutes: int = Field(
        ...,
        gt=0,
        description="Predicted service duration in minutes (positive integer)",
    )


class HealthResponse(BaseModel):
    status: str = Field(..., description="Service status indicator")
    model_loaded: bool = Field(..., description="Whether ML pipeline is loaded and ready")
    model_path: str = Field(..., description="Path to loaded model artifact")
