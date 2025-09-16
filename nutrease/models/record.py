from __future__ import annotations

""" Modelli di registrazione di pasti e sintomi

Definisce:

* ``Record``            – base astratta con metadata comuni.
* ``MealRecord``        – insieme di ``FoodPortion`` consumate.
* ``SymptomRecord``     – sintomo riportato dal paziente.
* ``FoodPortion``       – alimento + quantità + unità.
  Conversione in grammi tramite dataset.

``FoodPortion`` ora implementa l'interfaccia ``AlimentazioneDataset``
offrendo i metodi ``get_grams_per_unit`` e ``lookup`` (operazioni di sola
lettura) delegati al servizio
``nutrease.services.dataset_service.AlimentazioneDataset`` ottenuto lazily
tramite ``_dataset()``.
"""

from abc import ABC, abstractmethod
from dataclasses import dataclass, field
from datetime import datetime
from typing import TYPE_CHECKING, List, Mapping

from nutrease.utils.tz import local_now

from .enums import Nutrient, RecordType, Severity, Unit

if TYPE_CHECKING:  # pragma: no cover – forward refs only
    from nutrease.services.dataset_service import AlimentazioneDataset as DatasetService


class AlimentazioneDataset(ABC):
    """Interfaccia per operazioni di lookup sul dataset alimentare."""

    @abstractmethod
    def get_grams_per_unit(self) -> float:
        """Quantità di grammi contenuta in un'unità dell'alimento."""

    @abstractmethod
    def lookup(self, name: str) -> Mapping[str, float]:
        """Restituisce i nutrienti per grammo associati a *name*."""


# ---------------------------------------------------------------------------
# Helper – singleton dataset accessor
# ---------------------------------------------------------------------------


def _dataset() -> "DatasetService":  # noqa: D401
    """Ritorna (e cache) il dataset alimenti."""
    from nutrease.services.dataset_service import (
        AlimentazioneDataset as DatasetService,
    )  # local import

    if not hasattr(_dataset, "_instance"):
        _dataset._instance = DatasetService.default()
    return _dataset._instance  # type: ignore[attr-defined]


# ---------------------------------------------------------------------------
# Record base
# ---------------------------------------------------------------------------

@dataclass(kw_only=True)
class Record:
    """Base astratta per ogni voce del diario."""

    id: int = 0
    created_at: datetime = field(default_factory=local_now)
    note: str | None = None

    # impostato nei sottotipi con object.__setattr__ in __post_init__
    record_type: RecordType | None = None


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
class NutrientIntake:
    """Quantitativo di un singolo nutriente per una porzione."""

    nutrient: Nutrient
    grams: float

    def as_dict(self) -> dict:  # noqa: D401
        return {"nutrient": self.nutrient.value, "grams": self.grams}


@dataclass(kw_only=True)
class FoodPortion(AlimentazioneDataset):
    """Quantità di un alimento espressa in una certa unità."""

    food_name: str
    quantity: float
    unit: Unit
    nutrients: List[NutrientIntake] = field(default_factory=list)

    def __post_init__(self) -> None:  # noqa: D401
        if not self.nutrients:
            try:
                lookup = _dataset().lookup(self.food_name)
            except KeyError:
                lookup = {}
            grams = self.to_grams()
            self.nutrients = [
                NutrientIntake(
                    nutrient=n, grams=grams * lookup.get(n.name.lower(), 0.0)
                )
                for n in Nutrient
                if lookup.get(n.name.lower(), 0.0) > 0
            ]

    # ------------------------------------------------------------------
    # Implementazione dell'interfaccia AlimentazioneDataset
    # ------------------------------------------------------------------
    def get_grams_per_unit(self) -> float:  # noqa: D401
        """Restituisce i grammi contenuti in un'unità dell'alimento."""
        return _dataset().get_grams_per_unit(self.food_name, self.unit)

    def lookup(self, name: str) -> Mapping[str, float]:  # noqa: D401
        """Ricerca nutrienti per un alimento nel dataset."""
        return _dataset().lookup(name)

    # ------------------------------------------------------------------
    def to_grams(self) -> float:  # noqa: D401
        """Converte la porzione in grammi usando il dataset.

        Se l'alimento o l'unità non sono presenti nel dataset, assume che la
        quantità sia già espressa in grammi e restituisce ``self.quantity``
        senza sollevare eccezioni.
        """
        try:
            grams = self.get_grams_per_unit()
            return grams * self.quantity
        except KeyError:
            return self.quantity

    def as_dict(self) -> dict:  # noqa: D401
        """Serializza in dict JSON-friendly."""
        return {
            "food_name": self.food_name,
            "quantity": self.quantity,
            "unit": self.unit.value,
            "nutrients": [n.as_dict() for n in self.nutrients],
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
        return sum(
            ni.grams
            for portion in self.portions
            for ni in portion.nutrients
            if ni.nutrient == nutrient
        )

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