from __future__ import annotations

"""Nutrient & unit conversion dataset service (RNF7).

`AlimentazioneDataset` lazily loads a CSV file *once* and keeps an in‑memory
(cache) mapping so successive look‑ups are O(1) and filesystem‑free.

Expected CSV schema (case‑insensitive columns)::

    food_name,unit,grams,lactose,sorbitol,gluten

* Multiple rows per ``food_name`` are allowed, one per **unit**.
* Nutrient columns *must* match :class:`nutrease.models.enums.Nutrient` names.
* Extra columns are ignored.

The default singleton path is read from env‐var ``NUTREASE_DATASET_PATH`` or
falls back to ``data/alimentazione_demo.csv`` (created by *scripts.bootstrap*).
"""

from functools import lru_cache
from pathlib import Path
from typing import ClassVar, Dict, Mapping
import os

import pandas as pd

from nutrease.models.enums import Nutrient, Unit

__all__ = ["AlimentazioneDataset"]


class AlimentazioneDataset:  # noqa: D101 – documented in module docstring
    # ---------------------------------------------------------------------
    # Construction helpers
    # ---------------------------------------------------------------------

    _instance: ClassVar["AlimentazioneDataset | None"] = None

    def __init__(self, csv_path: str | Path):
        self.csv_path = Path(csv_path).expanduser().resolve()
        if not self.csv_path.exists():
            raise FileNotFoundError(f"Dataset CSV non trovato: {self.csv_path}")
        self._load()

    # .....................................................................
    # Singleton accessor – used by Record helper
    # .....................................................................

    @classmethod
    def default(cls) -> "AlimentazioneDataset":  # noqa: D401 – imperative
        if cls._instance is None:
            path = os.getenv("NUTREASE_DATASET_PATH", "data/alimentazione_demo.csv")
            cls._instance = cls(path)
        return cls._instance

    # ---------------------------------------------------------------------
    # Public API
    # ---------------------------------------------------------------------

    @lru_cache(maxsize=None)
    def lookup(self, food_name: str) -> Mapping[str, float]:  # noqa: D401 – imperative
        """Return a *copy* of nutrient dict for *food_name* (case‑insensitive)."""
        key = food_name.lower()
        try:
            return dict(self._nutrients[key])  # shallow copy to avoid mutation
        except KeyError as err:
            raise KeyError(f"Alimento '{food_name}' non presente nel dataset.") from err

    @lru_cache(maxsize=None)
    def get_grams_per_unit(self, food_name: str, unit: Unit) -> float:  # noqa: D401 – imperative
        """Return conversion factor grams per *unit* for *food_name*.

        Raises
        ------
        KeyError
            If the food or the specific unit is missing in the dataset.
        """
        key = food_name.lower()
        try:
            unit_map = self._unit_grams[key]
            return unit_map[unit]
        except KeyError as err:
            raise KeyError(
                f"Conversione per unità '{unit}' dell'alimento '{food_name}' mancante."
            ) from err

    # ---------------------------------------------------------------------
    # Internal – CSV loading
    # ---------------------------------------------------------------------

    def _load(self) -> None:  # noqa: D401 – imperative
        df = pd.read_csv(self.csv_path)
        # Normalise columns
        df.columns = [c.strip().lower() for c in df.columns]

        nutrients: Dict[str, Dict[str, float]] = {}
        unit_grams: Dict[str, Dict[Unit, float]] = {}

        for _, row in df.iterrows():
            food = str(row.get("food_name", "")).strip().lower()
            if not food:
                continue  # skip malformed row

            # 1) nutrient amounts -------------------------------------------------
            nutrients.setdefault(food, {})
            for n in Nutrient:
                if n.name.lower() in row and row[n.name.lower()] != "":
                    nutrients[food][n.name] = float(row[n.name.lower()])

            # 2) per‑unit grams ---------------------------------------------------
            unit_val = str(row.get("unit", "GRAMS")).strip().upper() or "GRAMS"
            try:
                unit = Unit.from_str(unit_val)
            except ValueError:
                unit = Unit.GRAMS  # fallback safe default
            grams = float(row.get("grams", 1.0))
            unit_grams.setdefault(food, {})[unit] = grams

        self._nutrients = nutrients  # type: ignore[attr-defined]
        self._unit_grams = unit_grams  # type: ignore[attr-defined]

    # ---------------------------------------------------------------------
    # Dunder helpers – nice repr & len
    # ---------------------------------------------------------------------

    def __len__(self) -> int:  # noqa: D401 – imperative
        return len(self._nutrients)

    def __repr__(self) -> str:  # noqa: D401 – imperative
        return f"<AlimentazioneDataset foods={len(self)} path='{self.csv_path.name}'>"
    