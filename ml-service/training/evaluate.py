"""Evaluation helpers used to report validation and untouched test metrics."""

import numpy as np
import pandas as pd
from sklearn.metrics import mean_absolute_error, mean_squared_error, r2_score


def evaluate_predictions(y_true, y_pred) -> dict:
    """Compute MAE, RMSE, R2 for a set of predictions."""
    mae = mean_absolute_error(y_true, y_pred)
    rmse = float(np.sqrt(mean_squared_error(y_true, y_pred)))
    r2 = r2_score(y_true, y_pred)
    return {"mae": round(float(mae), 3), "rmse": round(rmse, 3), "r2": round(float(r2), 4)}


def evaluate_model(model, X_val: pd.DataFrame, y_val: pd.Series) -> dict:
    preds = model.predict(X_val)
    return evaluate_predictions(y_val, preds)
