"""
Model training pipeline for OptiServe service duration prediction.

Trains Linear Regression and Random Forest Regressor models using a scikit-learn
Pipeline with OneHotEncoder and ColumnTransformer. Evaluates both models,
selects the best model based on validation metrics, and persists the pipeline.
"""

from pathlib import Path
from typing import Any, Dict, Tuple
import joblib
import numpy as np
import pandas as pd
from sklearn.compose import ColumnTransformer
from sklearn.ensemble import RandomForestRegressor
from sklearn.linear_model import LinearRegression
from sklearn.metrics import mean_absolute_error, mean_squared_error, r2_score
from sklearn.model_selection import train_test_split
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import OneHotEncoder

CATEGORICAL_FEATURES = ["serviceType", "vehicleMake", "vehicleModel", "dayOfWeek"]
NUMERICAL_FEATURES = ["vehicleYear"]
TARGET_COLUMN = "durationMinutes"
RANDOM_STATE = 42


def get_project_root() -> Path:
    """Returns the ml-service root directory."""
    return Path(__file__).resolve().parent.parent


def load_dataset(csv_path: Path | None = None) -> pd.DataFrame:
    """Loads the historical service dataset."""
    if csv_path is None:
        csv_path = get_project_root() / "data" / "service_history.csv"
    if not csv_path.exists():
        raise FileNotFoundError(
            f"Dataset not found at {csv_path}. Please generate the dataset first."
        )
    return pd.read_csv(csv_path)


def build_preprocessor() -> ColumnTransformer:
    """Constructs the feature ColumnTransformer."""
    return ColumnTransformer(
        transformers=[
            (
                "cat",
                OneHotEncoder(handle_unknown="ignore", sparse_output=False),
                CATEGORICAL_FEATURES,
            ),
            (
                "num",
                "passthrough",
                NUMERICAL_FEATURES,
            ),
        ],
        remainder="drop",
    )


def compute_metrics(y_true: np.ndarray, y_pred: np.ndarray) -> Dict[str, float]:
    """
    Computes MAE, RMSE, and R2 metrics.
    Calculates RMSE using sqrt of MSE for scikit-learn version compatibility.
    """
    mae = float(mean_absolute_error(y_true, y_pred))
    mse = float(mean_squared_error(y_true, y_pred))
    rmse = float(np.sqrt(mse))
    r2 = float(r2_score(y_true, y_pred))
    return {
        "MAE": mae,
        "RMSE": rmse,
        "R2": r2,
    }


def train_and_evaluate(
    df: pd.DataFrame,
    test_size: float = 0.2,
    random_state: int = RANDOM_STATE,
) -> Tuple[Pipeline, str, Dict[str, Dict[str, float]]]:
    """
    Trains Linear Regression and Random Forest models and selects the best model.
    """
    features = CATEGORICAL_FEATURES + NUMERICAL_FEATURES
    X = df[features]
    y = df[TARGET_COLUMN].values

    X_train, X_test, y_train, y_test = train_test_split(
        X, y, test_size=test_size, random_state=random_state
    )

    models = {
        "Linear Regression": LinearRegression(),
        "Random Forest": RandomForestRegressor(
            n_estimators=100,
            max_depth=12,
            min_samples_split=4,
            min_samples_leaf=2,
            random_state=random_state,
        ),
    }

    results: Dict[str, Dict[str, float]] = {}
    fitted_pipelines: Dict[str, Pipeline] = {}

    for name, regressor in models.items():
        pipeline = Pipeline(
            steps=[
                ("preprocessor", build_preprocessor()),
                ("regressor", regressor),
            ]
        )
        pipeline.fit(X_train, y_train)
        y_pred = pipeline.predict(X_test)
        metrics = compute_metrics(y_test, y_pred)
        results[name] = metrics
        fitted_pipelines[name] = pipeline

    # Print evaluation table
    header = f"{'Model':<22} | {'MAE':<10} | {'RMSE':<10} | {'R2':<10}"
    separator = "-" * len(header)
    print("\n" + separator)
    print("Model Evaluation Summary (Test Set):")
    print(separator)
    print(header)
    print(separator)
    for name, metrics in results.items():
        print(
            f"{name:<22} | {metrics['MAE']:<10.4f} | {metrics['RMSE']:<10.4f} | {metrics['R2']:<10.4f}"
        )
    print(separator + "\n")

    # Selection logic: Deterministically prefer lowest RMSE, then lowest MAE, then highest R2
    def selection_key(item: Tuple[str, Dict[str, float]]) -> Tuple[float, float, float]:
        m = item[1]
        return (m["RMSE"], m["MAE"], -m["R2"])

    best_model_name = min(results.items(), key=selection_key)[0]
    best_pipeline = fitted_pipelines[best_model_name]

    print(
        f"Selected Best Model: '{best_model_name}' "
        f"(Validation RMSE: {results[best_model_name]['RMSE']:.4f}, "
        f"MAE: {results[best_model_name]['MAE']:.4f}, "
        f"R2: {results[best_model_name]['R2']:.4f})"
    )

    return best_pipeline, best_model_name, results


def save_model(pipeline: Pipeline, output_path: Path | None = None) -> Path:
    """Saves the fitted pipeline artifact using joblib."""
    if output_path is None:
        model_dir = get_project_root() / "model"
        model_dir.mkdir(parents=True, exist_ok=True)
        output_path = model_dir / "service_duration_model.joblib"
    else:
        output_path.parent.mkdir(parents=True, exist_ok=True)

    joblib.dump(pipeline, output_path)
    print(f"Persisted trained model pipeline to: {output_path}")
    return output_path


def main() -> None:
    print("Starting training pipeline...")
    df = load_dataset()
    best_pipeline, best_model_name, _ = train_and_evaluate(df)
    save_model(best_pipeline)
    print("Training pipeline completed successfully.")


if __name__ == "__main__":
    main()
