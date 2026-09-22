"""
Service duration prediction abstraction for OptiServe ML Service.
"""

from typing import Any
import numpy as np
import pandas as pd
from app.model_loader import load_model


class ServiceDurationPredictor:
    """
    Encapsulates service duration prediction using the persisted scikit-learn pipeline.
    """

    def __init__(self, model_pipeline: Any | None = None) -> None:
        self.model_pipeline = (
            model_pipeline if model_pipeline is not None else load_model()
        )

    def predict(
        self,
        service_type: str,
        vehicle_make: str,
        vehicle_model: str,
        vehicle_year: int,
        day_of_week: str,
    ) -> int:
        """
        Predicts service duration in minutes.

        Args:
            service_type: Name/type of automotive service.
            vehicle_make: Vehicle manufacturer brand.
            vehicle_model: Vehicle model name.
            vehicle_year: Year of vehicle manufacture.
            day_of_week: Scheduled day of week (e.g. MONDAY).

        Returns:
            Positive integer duration in minutes.
        """
        input_data = pd.DataFrame(
            [
                {
                    "serviceType": service_type,
                    "vehicleMake": vehicle_make,
                    "vehicleModel": vehicle_model,
                    "vehicleYear": int(vehicle_year),
                    "dayOfWeek": str(day_of_week),
                }
            ]
        )

        raw_pred = self.model_pipeline.predict(input_data)
        raw_val = float(raw_pred[0])

        # Round to nearest integer minute
        rounded_val = int(np.round(raw_val))

        # Enforce positive duration safety boundary (at least 1 minute)
        duration_minutes = max(1, rounded_val)

        return duration_minutes


_default_predictor: ServiceDurationPredictor | None = None


def get_predictor() -> ServiceDurationPredictor:
    """Returns the singleton predictor instance."""
    global _default_predictor
    if _default_predictor is None:
        _default_predictor = ServiceDurationPredictor()
    return _default_predictor
