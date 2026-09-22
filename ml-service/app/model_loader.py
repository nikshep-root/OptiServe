"""
Model loading utility for OptiServe ML Service.

Provides robust, project-relative model artifact loading and in-memory caching
to ensure the model is loaded only once and never retrained on API startup.
"""

from pathlib import Path
from typing import Any
import joblib


def get_model_path() -> Path:
    """Returns the default path to the persisted joblib model artifact."""
    project_root = Path(__file__).resolve().parent.parent
    return project_root / "model" / "service_duration_model.joblib"


_model_cache: Any = None


def load_model(model_path: Path | None = None) -> Any:
    """
    Loads and caches the persisted scikit-learn pipeline from disk.

    Raises:
        FileNotFoundError: If the model file is not found at the expected location.
    """
    global _model_cache

    if _model_cache is not None and model_path is None:
        return _model_cache

    target_path = model_path if model_path is not None else get_model_path()

    if not target_path.exists():
        raise FileNotFoundError(
            f"Model artifact not found at '{target_path}'. "
            "Please train the model first by running: 'python training/train.py' from ml-service root."
        )

    pipeline = joblib.load(target_path)

    if model_path is None:
        _model_cache = pipeline

    return pipeline


def is_model_loaded() -> bool:
    """Checks whether the model pipeline is loaded in memory."""
    return _model_cache is not None
