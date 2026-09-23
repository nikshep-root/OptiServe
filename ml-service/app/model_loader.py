from functools import lru_cache
from pathlib import Path

import joblib

from app.features import DEFAULT_REFERENCE_YEAR

MODEL_PATH = Path(__file__).resolve().parent.parent / "model" / "service_duration_model.joblib"


class ModelNotFoundError(RuntimeError):
    pass


@lru_cache(maxsize=1)
def get_model_artifact() -> dict:
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
    artifact = joblib.load(MODEL_PATH)
    if isinstance(artifact, dict) and "pipeline" in artifact:
        return artifact
    return {"pipeline": artifact, "reference_year": DEFAULT_REFERENCE_YEAR}


def get_model():
    return get_model_artifact()["pipeline"]


def get_reference_year() -> int:
    return int(get_model_artifact().get("reference_year", DEFAULT_REFERENCE_YEAR))
