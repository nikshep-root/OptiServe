"""Train reproducible candidate models and evaluate the selected one once on
an untouched test split. Outputs are written under model/candidates/ so this
script never replaces the service's current model artifact.
"""

import hashlib
import json
import platform
import sys
from pathlib import Path

import cloudpickle
import joblib
import numpy
import pandas as pd
import scipy
import sklearn
from sklearn.compose import ColumnTransformer
from sklearn.ensemble import RandomForestRegressor
from sklearn.linear_model import LinearRegression
from sklearn.model_selection import train_test_split
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import OneHotEncoder

REPO_ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(REPO_ROOT))  # allow `import app.*` when run as a script

from app.features import (  # noqa: E402
    CATEGORICAL_FEATURES,
    DEFAULT_REFERENCE_YEAR,
    NUMERIC_FEATURES,
    build_feature_frame,
)
from training.evaluate import evaluate_model  # noqa: E402

DATA_PATH = REPO_ROOT / "data" / "service_history.csv"
DEFAULT_OUTPUT_DIR = REPO_ROOT / "model" / "candidates"
DEPLOYED_MODEL_PATH = REPO_ROOT / "model" / "service_duration_model.joblib"

RANDOM_SEED = 42
TEST_FRACTION = 0.15
VALIDATION_FRACTION = 0.15


def split_dataset(X: pd.DataFrame, y: pd.Series):
    """Return 70/15/15 train/validation/test partitions using fixed seed 42."""
    X_development, X_test, y_development, y_test = train_test_split(
        X, y, test_size=TEST_FRACTION, random_state=RANDOM_SEED
    )
    validation_rows = round(len(X) * VALIDATION_FRACTION)
    X_train, X_validation, y_train, y_validation = train_test_split(
        X_development,
        y_development,
        test_size=validation_rows,
        random_state=RANDOM_SEED,
    )
    return (
        X_development,
        X_test,
        X_train,
        X_validation,
        y_development,
        y_test,
        y_train,
        y_validation,
    )


def build_preprocessor() -> ColumnTransformer:
    return ColumnTransformer(
        transformers=[
            ("categorical", OneHotEncoder(handle_unknown="ignore"), CATEGORICAL_FEATURES),
            ("numeric", "passthrough", NUMERIC_FEATURES),
        ]
    )


def main(output_dir: Path = DEFAULT_OUTPUT_DIR):
    df = pd.read_csv(DATA_PATH)

    y = df["durationMinutes"]
    X = build_feature_frame(df, reference_year=DEFAULT_REFERENCE_YEAR)

    (
        X_development,
        X_test,
        X_train,
        X_validation,
        y_development,
        y_test,
        y_train,
        y_validation,
    ) = split_dataset(X, y)

    candidates = {
        "linear_regression": Pipeline(
            steps=[
                ("preprocessor", build_preprocessor()),
                ("regressor", LinearRegression()),
            ]
        ),
        "random_forest": Pipeline(
            steps=[
                ("preprocessor", build_preprocessor()),
                (
                    "regressor",
                    RandomForestRegressor(
                        n_estimators=200,
                        max_depth=None,
                        random_state=RANDOM_SEED,
                        n_jobs=1,
                    ),
                ),
            ]
        ),
    }

    results = {}
    for name, pipeline in candidates.items():
        pipeline.fit(X_train, y_train)
        metrics = evaluate_model(pipeline, X_validation, y_validation)
        results[name] = metrics
        print(f"{name}: {metrics}")

    # Lower MAE wins.
    best_name = min(results, key=lambda name: results[name]["mae"])
    best_pipeline = candidates[best_name]
    # Refit on train + validation after model selection. The test partition has
    # not been used for fitting or selection and is evaluated exactly once.
    best_pipeline.fit(X_development, y_development)
    test_metrics = evaluate_model(best_pipeline, X_test, y_test)

    model_path = output_dir / "service_duration_model.joblib"
    metrics_path = output_dir / "metrics.json"
    if model_path.resolve() == DEPLOYED_MODEL_PATH.resolve():
        raise ValueError("Candidate training may not overwrite the deployed model artifact.")

    output_dir.mkdir(parents=True, exist_ok=True)
    joblib.dump(
        {
            "pipeline": best_pipeline,
            "reference_year": DEFAULT_REFERENCE_YEAR,
        },
        model_path,
    )

    report = {
        "dataset": {
            "path": "data/service_history.csv",
            "sha256": hashlib.sha256(DATA_PATH.read_bytes()).hexdigest(),
            "rows": len(df),
            "label": "synthetic; not representative of real workshop outcomes",
        },
        "reproducibility": {
            "random_seed": RANDOM_SEED,
            "split_fractions": {
                "train": 0.70,
                "validation": VALIDATION_FRACTION,
                "test": TEST_FRACTION,
            },
            "python": platform.python_version(),
            "numpy": numpy.__version__,
            "scipy": scipy.__version__,
            "pandas": pd.__version__,
            "scikit_learn": sklearn.__version__,
            "joblib": joblib.__version__,
            "cloudpickle": cloudpickle.__version__,
        },
        "selection_metric": "validation MAE (lower is better)",
        "selected_model": best_name,
        "validation_results": results,
        "held_out_test_results": test_metrics,
        "test_rows": len(X_test),
    }
    metrics_path.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")

    print(f"\nSelected candidate: {best_name}")
    print(f"Held-out test metrics: {test_metrics}")
    print(f"Candidate artifact: {model_path}")
    print(f"Metrics report: {metrics_path}")
    print(f"The deployed artifact at {DEPLOYED_MODEL_PATH} was not changed.")


if __name__ == "__main__":
    from argparse import ArgumentParser

    parser = ArgumentParser(description=__doc__)
    parser.add_argument(
        "--output-dir",
        type=Path,
        default=DEFAULT_OUTPUT_DIR,
        help="Candidate output directory (never use model/).",
    )
    main(output_dir=parser.parse_args().output_dir)
