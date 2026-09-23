from datetime import datetime
from typing import Literal

from pydantic import BaseModel, Field, field_validator

DayOfWeek = Literal[
    "MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"
]


class PredictionRequest(BaseModel):
    serviceType: str = Field(..., min_length=1, examples=["Engine Service"])
    vehicleMake: str = Field(..., min_length=1, examples=["Hyundai"])
    vehicleModel: str = Field(..., min_length=1, examples=["i20"])
    vehicleYear: int = Field(..., examples=[2022])
    dayOfWeek: DayOfWeek = Field(..., examples=["MONDAY"])

    @field_validator("vehicleYear")
    @classmethod
    def year_in_sane_range(cls, value: int) -> int:
        current_year = datetime.now().year
        if value < 1980 or value > current_year + 1:
            raise ValueError(
                f"vehicleYear must be between 1980 and {current_year + 1}"
            )
        return value

    @field_validator("serviceType", "vehicleMake", "vehicleModel")
    @classmethod
    def not_blank(cls, value: str) -> str:
        if not value.strip():
            raise ValueError("must not be blank")
        return value


class PredictionResponse(BaseModel):
    predictedDurationMinutes: int
