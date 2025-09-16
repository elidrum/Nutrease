from __future__ import annotations

"""
Enum di dominio usati in tutta l'applicazione Nutrease.
Ogni enum eredita da :class:`StrEnumMixin`, che offre un comodo
helper :py:meth:`from_str` per l'analisi case-insensitive di input
provenienti da utenti o file.
"""

from enum import Enum
from typing import Type, TypeVar

__all__ = [
    "Unit",
    "Nutrient",
    "Severity",
    "SpecialistCategory",
    "RecordType",
    "LinkRequestState",
]

T = TypeVar("T", bound="StrEnumMixin")


class StrEnumMixin(str, Enum):
    """String‑backed :class:`enum.Enum` with a generic :pymeth:`from_str`.

    The mixin stores enum members as *strings equal to their names* to avoid
    conversions when serialising to JSON/DB.  The :pymeth:`from_str` helper
    turns any case‑insensitive user‐provided string into the corresponding
    enum member, raising :class:`ValueError` otherwise.
    """

    @classmethod
    def from_str(cls: Type[T], value: str) -> T:  # type: ignore[type-var]
        """Parse *value* into the corresponding enum member (case‑insensitive)."""
        try:
            return cls[value.strip().upper()]  # type: ignore[index]
        except KeyError as err:  # pragma: no cover – clarity beats coverage here
            valid = ", ".join(m.name for m in cls)
            raise ValueError(
                f"{value!r} is not a valid {cls.__name__}. Valid values: {valid}"
            ) from err

    # Make printing / f‑strings show the raw value rather than "Enum.X".
    def __str__(self) -> str:  # noqa: D401 – simple str override
        return str(self.value)


class Unit(StrEnumMixin):
    """Unit of measure for a food quantity."""
    GRAMS = "GRAMS"
    GLASS = "GLASS"
    ITEM = "ITEM"
    CLOVE = "CLOVE"
    LITERS = "LITERS"
    SLICE = "SLICE"
    SPOON = "SPOON"
    CUP = "CUP"


class Nutrient(StrEnumMixin):
    """Nutrient categories tracked in the food diary."""

    LACTOSE = "LACTOSE"
    SORBITOL = "SORBITOL"
    GLUTEN = "GLUTEN"


class Severity(StrEnumMixin):
    """Intensity levels for a symptom."""

    NONE = "NONE"
    MILD = "MILD"
    MODERATE = "MODERATE"
    SEVERE = "SEVERE"


class SpecialistCategory(StrEnumMixin):
    """Professional categories a specialist can belong to."""

    DIETICIAN = "DIETICIAN"
    NUTRITIONIST = "NUTRITIONIST"
    GASTROENTEROLOGIST = "GASTROENTEROLOGIST"


class RecordType(StrEnumMixin):
    """Kinds of records a patient can add to the diary."""

    MEAL = "MEAL"
    SYMPTOM = "SYMPTOM"


class LinkRequestState(StrEnumMixin):
    """Lifecycle states of a patient–specialist link request."""

    PENDING = "PENDING"
    CONNECTED = "CONNECTED"
    REJECTED = "REJECTED"