from __future__ import annotations

"""Meal & Symptom record models (UML UC 8-12).

Definisce:

* ``Record``            – base astratta con metadata comuni.
* ``MealRecord``        – insieme di ``FoodPortion`` consumate.
* ``SymptomRecord``     – sintomo riportato dal paziente.
* ``FoodPortion``       – alimento + quantità + unità.
  Conversione in grammi tramite dataset.

`FoodPortion.to_grams()` sfrutta il servizio
``nutrease.services.dataset_service.AlimentazioneDataset`` ottenuto lazily
tramite ``_dataset()``.
"""

from abc import ABC
from dataclasses import field
from datetime import datetime
from typing import List, TYPE_CHECKING

from pydantic.dataclasses import dataclass

from .enums import Nutrient, RecordType, Severity, Unit

if TYPE_CHECKING:  # pragma: no cover – forward refs only
    from nutrease.services.dataset_service import AlimentazioneDataset


# ---------------------------------------------------------------------------
# Helper – singleton dataset accessor
# ---------------------------------------------------------------------------


def _dataset() -> "AlimentazioneDataset":  # noqa: D401
    """Ritorna (e cache) il dataset alimenti."""
    from nutrease.services.dataset_service import AlimentazioneDataset  # local import

    if not hasattr(_dataset, "_instance"):
        _dataset._instance = AlimentazioneDataset.default()
    return _dataset._instance  # type: ignore[attr-defined]


# ---------------------------------------------------------------------------
# Record base
# ---------------------------------------------------------------------------


@dataclass(config={"validate_assignment": True, "repr": True}, kw_only=True)
class Record(ABC):
    """Base astratta per ogni voce del diario."""

    id: int = 0
    created_at: datetime = field(default_factory=datetime.now)
    note: str | None = None

    # impostato nei sottotipi con object.__setattr__ in __post_init__
    record_type: RecordType | None = field(init=False, default=None)

    # ---------------------------------------------------------------------
    def as_dict(self) -> dict:  # noqa: D401
        """Serializza in dict semplice (per JSON/DB)."""
        data = {
            "id": self.id,
            "created_at": self.created_at.isoformat(),
            "note": self.note,
            "record_type": (self.record_type.value if self.record_type else None),
        }
        if "patient_email" in self.__dict__:
            data["patient_email"] = self.__dict__["patient_email"]
        return data



# ---------------------------------------------------------------------------
# FoodPortion
# ---------------------------------------------------------------------------


@dataclass(kw_only=True)
class FoodPortion:
    """Quantità di un alimento espressa in una certa unità."""

    food_name: str
    quantity: float
    unit: Unit

    def to_grams(self) -> float:  # noqa: D401
        """Converte la porzione in grammi usando il dataset."""
        grams = _dataset().get_grams_per_unit(self.food_name, self.unit)
        return grams * self.quantity

    def as_dict(self) -> dict:  # noqa: D401
        """Serializza in dict JSON-friendly."""
        return {
            "food_name": self.food_name,
            "quantity": self.quantity,
            "unit": self.unit.value,
        }

# ---------------------------------------------------------------------------
# MealRecord
# ---------------------------------------------------------------------------


@dataclass(kw_only=True)
class MealRecord(Record):
    """Pasto composto da una o più porzioni."""

    portions: List[FoodPortion] = field(default_factory=list)

    def __post_init__(self) -> None:  # noqa: D401
        # Evita validate_assignment di Pydantic
        object.__setattr__(self, "record_type", RecordType.MEAL)

    # ---------------------------------------------------------------------
    def get_nutrient_total(self, nutrient: Nutrient) -> float:
        """Totale grammi di *nutrient* in questo pasto."""
        total = 0.0
        for p in self.portions:
            try:
                nutrient_per_gram = (
                    _dataset().lookup(p.food_name).get(nutrient.name.lower(), 0.0)
                )
                total += nutrient_per_gram * p.to_grams()
            except KeyError:
                # Alimento non presente nel dataset → assume 0 g di nutrienti
                continue
        return total

    def as_dict(self) -> dict:  # noqa: D401
        data = super().as_dict()
        data["portions"] = [p.as_dict() for p in self.portions]
        return data



# ---------------------------------------------------------------------------
# SymptomRecord
# ---------------------------------------------------------------------------


@dataclass(kw_only=True)
class SymptomRecord(Record):
    """Sintomo riportato dal paziente con severità."""

    symptom: str = ""
    severity: Severity = Severity.NONE

    def __post_init__(self) -> None:  # noqa: D401
        object.__setattr__(self, "record_type", RecordType.SYMPTOM)

    def as_dict(self) -> dict:  # noqa: D401
        data = super().as_dict()
        data.update({"symptom": self.symptom, "severity": self.severity.value})
        return data