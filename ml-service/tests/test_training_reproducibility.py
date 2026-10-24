import pandas as pd

from training.generate_synthetic_data import generate_rows
from training.train import split_dataset


def test_synthetic_rows_are_reproducible_for_a_fixed_seed():
    assert generate_rows(n_rows=20, seed=42) == generate_rows(n_rows=20, seed=42)
    assert generate_rows(n_rows=20, seed=42) != generate_rows(n_rows=20, seed=43)


def test_train_validation_and_test_splits_are_disjoint_and_reproducible():
    X = pd.DataFrame({"feature": range(100)})
    y = pd.Series(range(100))

    first = split_dataset(X, y)
    second = split_dataset(X, y)

    X_development, X_test, X_train, X_validation = first[:4]
    assert len(X_train) == 70
    assert len(X_validation) == 15
    assert len(X_test) == 15
    assert set(X_development.index) == set(X_train.index) | set(X_validation.index)
    assert not set(X_train.index).intersection(X_validation.index)
    assert not set(X_development.index).intersection(X_test.index)
    for actual, repeat in zip(first, second, strict=True):
        assert actual.equals(repeat)
