"""
Standalone evaluation script for OptiServe service duration ML model.

Loads the dataset and persisted joblib model pipeline, reproduces the evaluation
process on the held-out test split, and prints MAE, RMSE, and R2 metrics.
"""

import sys
from pathlib import Path
import joblib
import pandas as pd
from sklearn.model_selection import train_test_split

# Ensure project root is in sys.path
PROJECT_ROOT = Path(__file__).resolve().parent.parent
if str(PROJECT_ROOT) not in sys.path:
    sys.path.insert(0, str(PROJECT_ROOT))

from training.train import (
    CATEGORICAL_FEATURES,
    NUMERICAL_FEATURES,
    RANDOM_STATE,
    TARGET_COLUMN,
    compute_metrics,
    get_project_root,
    load_dataset,
)


def evaluate_persisted_model(
    model_path: Path | None = None,
    dataset_path: Path | None = None,
    test_size: float = 0.2,
    random_state: int = RANDOM_STATE,
) -> dict[str, float]:
    """Loads persisted model pipeline and reproduces test set evaluation."""
    if model_path is None:
        model_path = get_project_root() / "model" / "service_duration_model.joblib"

    if not model_path.exists():
        raise FileNotFoundError(
            f"Persisted model not found at {model_path}. Please run train.py first."
        )

    df = load_dataset(dataset_path)
    features = CATEGORICAL_FEATURES + NUMERICAL_FEATURES
    X = df[features]
    y = df[TARGET_COLUMN].values

    _, X_test, _, y_test = train_test_split(
        X, y, test_size=test_size, random_state=random_state
    )

    model = joblib.load(model_path)
    y_pred = model.predict(X_test)
    metrics = compute_metrics(y_test, y_pred)

    header = f"{'Metric':<10} | {'Value':<12}"
    separator = "-" * len(header)
    print("\n" + separator)
    print("Persisted Model Evaluation (Test Split):")
    print(separator)
    print(header)
    print(separator)
    for k, v in metrics.items():
        print(f"{k:<10} | {v:<12.4f}")
    print(separator + "\n")

    return metrics


def main() -> None:
    print("Evaluating persisted model...")
    evaluate_persisted_model()


if __name__ == "__main__":
    main()
