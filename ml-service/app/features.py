"""
Feature engineering shared by training/train.py and app/predictor.py.
Keeping this in one place guarantees train-time and inference-time features
are computed identically.
"""

from datetime import datetime

import pandas as pd

CATEGORICAL_FEATURES = ["serviceType", "vehicleMake", "vehicleModel", "dayOfWeek"]
NUMERIC_FEATURES = ["vehicleAge"]
FEATURE_COLUMNS = CATEGORICAL_FEATURES + NUMERIC_FEATURES

VALID_DAYS_OF_WEEK = {
    "MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY",
    "FRIDAY", "SATURDAY", "SUNDAY",
}


def _current_year() -> int:
    return datetime.now().year


def build_feature_frame(raw: pd.DataFrame) -> pd.DataFrame:
    """
    Takes a DataFrame with raw columns
    [serviceType, vehicleMake, vehicleModel, vehicleYear, dayOfWeek]
    and returns a DataFrame with the exact columns/order the model pipeline
    was trained on: categoricals + engineered vehicleAge.
    """
    df = raw.copy()
    df["vehicleAge"] = _current_year() - df["vehicleYear"]
    # Guard against future-dated years producing a negative age.
    df["vehicleAge"] = df["vehicleAge"].clip(lower=0)
    return df[FEATURE_COLUMNS]
