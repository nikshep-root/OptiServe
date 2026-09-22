"""
Dataset generation script for OptiServe ML Service.

Generates a realistic synthetic dataset representing historical automotive service records.
Required columns:
- serviceType
- vehicleMake
- vehicleModel
- vehicleYear
- dayOfWeek
- durationMinutes
"""

from pathlib import Path
import numpy as np
import pandas as pd


def generate_service_dataset(num_records: int = 1000, random_state: int = 42) -> pd.DataFrame:
    rng = np.random.default_rng(random_state)

    service_profiles = {
        "Battery Replacement": {"base": 30.0, "std": 5.0},
        "Wheel Alignment": {"base": 35.0, "std": 6.0},
        "Oil Change": {"base": 40.0, "std": 7.0},
        "General Inspection": {"base": 48.0, "std": 8.0},
        "Tire Replacement": {"base": 55.0, "std": 8.0},
        "Brake Service": {"base": 75.0, "std": 10.0},
        "AC Service": {"base": 80.0, "std": 12.0},
        "Clutch Service": {"base": 150.0, "std": 16.0},
        "Transmission Service": {"base": 170.0, "std": 18.0},
        "Engine Service": {"base": 195.0, "std": 22.0},
    }

    vehicle_catalog = {
        "Hyundai": [("i20", 0.0), ("Creta", 8.0), ("Verna", 4.0)],
        "Tata": [("Punch", -2.0), ("Altroz", 0.0), ("Nexon", 7.0)],
        "Maruti": [("Swift", -3.0), ("Baleno", 0.0), ("Brezza", 6.0)],
        "Honda": [("Amaze", 1.0), ("City", 5.0)],
        "Toyota": [("Glanza", 0.0), ("Innova", 16.0)],
        "Kia": [("Sonet", 6.0), ("Seltos", 9.0)],
    }

    days_of_week = [
        "MONDAY",
        "TUESDAY",
        "WEDNESDAY",
        "THURSDAY",
        "FRIDAY",
        "SATURDAY",
        "SUNDAY",
    ]

    # Slight shop congestion / scheduling variance by day
    day_effects = {
        "MONDAY": 3.0,
        "TUESDAY": 0.0,
        "WEDNESDAY": 0.0,
        "THURSDAY": 1.0,
        "FRIDAY": 2.0,
        "SATURDAY": 5.0,
        "SUNDAY": 4.0,
    }

    makes = list(vehicle_catalog.keys())
    service_types = list(service_profiles.keys())

    records = []
    for _ in range(num_records):
        make = rng.choice(makes)
        models = vehicle_catalog[make]
        model_tuple = models[rng.integers(0, len(models))]
        model_name, model_offset = model_tuple

        service_type = rng.choice(service_types)
        service_info = service_profiles[service_type]

        year = int(rng.integers(2015, 2026))  # 2015 through 2025
        day = rng.choice(days_of_week)

        # Vehicle age effect: older cars require more inspection, bolt loosening, cleaning
        age = 2025 - year
        age_effect = age * 1.5

        day_offset = day_effects[day]

        # Calculate duration with Gaussian noise
        noise = rng.normal(0.0, service_info["std"])
        raw_duration = service_info["base"] + model_offset + age_effect + day_offset + noise

        # Ensure positive duration with sensible lower bound
        duration = int(np.round(max(15.0, raw_duration)))

        records.append({
            "serviceType": service_type,
            "vehicleMake": make,
            "vehicleModel": model_name,
            "vehicleYear": year,
            "dayOfWeek": day,
            "durationMinutes": duration,
        })

    df = pd.DataFrame(records)
    return df


def main() -> None:
    current_dir = Path(__file__).resolve().parent
    data_dir = current_dir.parent / "data"
    data_dir.mkdir(parents=True, exist_ok=True)
    output_path = data_dir / "service_history.csv"

    df = generate_service_dataset(num_records=1000, random_state=42)
    df.to_csv(output_path, index=False)
    print(f"Dataset generated successfully at: {output_path}")
    print(f"Total records: {len(df)}")
    print(df.head())


if __name__ == "__main__":
    main()
