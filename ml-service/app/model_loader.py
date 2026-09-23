from functools import lru_cache
from pathlib import Path

import joblib

MODEL_PATH = Path(__file__).resolve().parent.parent / "model" / "service_duration_model.joblib"


class ModelNotFoundError(RuntimeError):
    pass


@lru_cache(maxsize=1)
def get_model():
    """
    Loads the persisted sklearn pipeline once per process and caches it.
    Raises ModelNotFoundError with a clear message if training hasn't been
    run yet, instead of a raw FileNotFoundError.
    """
    if not MODEL_PATH.exists():
        raise ModelNotFoundError(
            f"No trained model found at {MODEL_PATH}. "
            "Run `python training/train.py` first to generate it."
        )
    return joblib.load(MODEL_PATH)
