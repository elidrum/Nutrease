from __future__ import annotations

"""Meal/Symptom records and food portions (UML §§ UC8‑12).

The module defines:
* :class:`Record` – abstract base with shared metadata.
* :class:`MealRecord` – collection of :class:`FoodPortion` consumed.
* :class:`SymptomRecord` – patient‑reported symptom & severity.
* :class:`FoodPortion` – food name, quantity & unit, with :py:meth:`to_grams`.

`to_grams()` relies on :class:`nutrease.services.dataset_service.AlimentazioneDataset`.
The service will be injected at runtime via a *class‑level* singleton accessor to
avoid tight coupling (dataset lazily loaded on first use).
"""

from abc import ABC
from datetime import datetime
from typing import ClassVar, List, TYPE_CHECKING

from pydantic.dataclasses import dataclass
from dataclasses import field


from .enums import Nutrient, RecordType, Severity, Unit

if TYPE_CHECKING:  # pragma: no cover – forward‑refs only
    from nutrease.services.dataset_service import AlimentazioneDataset


# ---------------------------------------------------------------------------
# Shared helpers / lazy dataset accessor
# ---------------------------------------------------------------------------

def _dataset() -> "AlimentazioneDataset":  # noqa: D401 – imperative
    """Return (and cache) the singleton dataset service instance."""
    # Local import avoids circular dependency until services package is ready.
    from nutrease.services.dataset_service import AlimentazioneDataset  # local import

    if not hasattr(_dataset, "_instance"):
        setattr(_dataset, "_instance", AlimentazioneDataset.default())  # type: ignore[attr-defined]
    return getattr(_dataset, "_instance")  # type: ignore[return-value]


# ---------------------------------------------------------------------------
# Core domain objects
# ---------------------------------------------------------------------------

@dataclass(config={"validate_assignment": True, "repr": True}, kw_only=True)
class Record(ABC):
    """Abstract base for any diary entry (meal, symptom, …)."""

    record_type: RecordType
    created_at: datetime = datetime.now()
    note: str | None = None

    def as_dict(self) -> dict:  # noqa: D401 – imperative
        """Serialise to plain dict (useful for JSON/DB)."""
        return self.__dict__.copy()


# .........................................................................
# Concrete – MealRecord
# .........................................................................

@dataclass(kw_only=True)
class FoodPortion:
    """A quantity of a given *food* expressed in a *unit*."""

    food_name: str
    quantity: float
    unit: Unit

    # ⇢  to_grams  -----------------------------------------------------------
    def to_grams(self) -> float:  # noqa: D401 – imperative
        """Convert this portion to grams using the nutrient dataset.

        Raises
        ------
        ValueError
            If the dataset lacks a conversion for the given (*food_name*, *unit*).
        """
        grams = _dataset().get_grams_per_unit(self.food_name, self.unit)
        return grams * self.quantity


@dataclass(kw_only=True)
class MealRecord(Record):
    """A patient meal composed of multiple food portions."""

    portions: List[FoodPortion]

    def __post_init__(self):  # noqa: D401 – imperative
        self.record_type = RecordType.MEAL  # always enforced

    # Convenience nutrient total -------------------------------------------
    def get_nutrient_total(self, nutrient: Nutrient) -> float:
        """Return the total amount of *nutrient* in the meal (grams)."""
        dataset = _dataset()
        total = 0.0
        for p in self.portions:
            food_info = dataset.lookup(p.food_name)
            total += food_info.get(nutrient.name.lower(), 0.0) * p.quantity
        return total


# .........................................................................
# Concrete – SymptomRecord
# .........................................................................

@dataclass(kw_only=True)
class SymptomRecord(Record):
    """Patient‑reported symptom with a severity level."""

    symptom: str
    severity: Severity

    def __post_init__(self):  # noqa: D401 – imperative
        self.record_type = RecordType.SYMPTOM
