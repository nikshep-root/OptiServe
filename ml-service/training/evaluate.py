"""
Evaluation helper shared by train.py. Can also be run standalone against an
already-persisted model + dataset.

Run standalone: python training/evaluate.py
"""

from pathlib import Path

import joblib
import numpy as np
import pandas as pd
from sklearn.metrics import mean_absolute_error, mean_squared_error, r2_score

REPO_ROOT = Path(__file__).resolve().parent.parent
MODEL_PATH = REPO_ROOT / "model" / "service_duration_model.joblib"
DATA_PATH = REPO_ROOT / "data" / "service_history.csv"


def evaluate_predictions(y_true, y_pred) -> dict:
    """Compute MAE, RMSE, R2 for a set of predictions."""
    mae = mean_absolute_error(y_true, y_pred)
    rmse = float(np.sqrt(mean_squared_error(y_true, y_pred)))
    r2 = r2_score(y_true, y_pred)
    return {"mae": round(float(mae), 3), "rmse": round(rmse, 3), "r2": round(float(r2), 4)}


def evaluate_model(model, X_val: pd.DataFrame, y_val: pd.Series) -> dict:
    preds = model.predict(X_val)
    return evaluate_predictions(y_val, preds)


if __name__ == "__main__":
    if not MODEL_PATH.exists():
        raise SystemExit("No persisted model found. Run training/train.py first.")

    model = joblib.load(MODEL_PATH)
    df = pd.read_csv(DATA_PATH)
    X = df.drop(columns=["durationMinutes"])
    y = df["durationMinutes"]

    # NOTE: this re-evaluates on the full dataset (not a held-out split),
    # so it's a sanity check, not the real validation score. The real
    # validation scores from the train/val split are logged by train.py.
    metrics = evaluate_model(model, X, y)
    print("Full-dataset sanity check metrics:", metrics)
