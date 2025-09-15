from __future__ import annotations
"""Utility per la localizzazione delle etichette nell'interfaccia Streamlit."""

from typing import Mapping

from nutrease.models.enums import Nutrient, Severity, SpecialistCategory, Unit

UnitLike = Unit | str
SeverityLike = Severity | str
NutrientLike = Nutrient | str
SpecialistCategoryLike = SpecialistCategory | str

_UNIT_LABELS: Mapping[Unit, str] = {
    Unit.GLASS: "Bicchiere",
    Unit.GRAMS: "Grammi",
    Unit.ITEM: "Pezzo",
    Unit.CLOVE: "Spicchio",
    Unit.LITERS: "Litri",
    Unit.SLICE: "Fetta",
    Unit.SPOON: "Cucchiaio",
    Unit.CUP: "Tazza",
}

_SEVERITY_LABELS: Mapping[Severity, str] = {
    Severity.NONE: "Assente",
    Severity.MILD: "Lieve",
    Severity.MODERATE: "Moderata",
    Severity.SEVERE: "Grave",
}

_NUTRIENT_LABELS: Mapping[Nutrient, str] = {
    Nutrient.LACTOSE: "Lattosio",
    Nutrient.SORBITOL: "Sorbitolo",
    Nutrient.GLUTEN: "Glutine",
}

_SPECIALIST_CATEGORY_LABELS: Mapping[SpecialistCategory, str] = {
    SpecialistCategory.DIETICIAN: "Dietista",
    SpecialistCategory.NUTRITIONIST: "Nutrizionista",
    SpecialistCategory.GASTROENTEROLOGIST: "Gastroenterologo",
}


def _ensure_unit(value: UnitLike) -> Unit:
    return value if isinstance(value, Unit) else Unit.from_str(value)


def _ensure_severity(value: SeverityLike) -> Severity:
    return value if isinstance(value, Severity) else Severity.from_str(value)


def _ensure_nutrient(value: NutrientLike) -> Nutrient:
    return value if isinstance(value, Nutrient) else Nutrient.from_str(value)


def _ensure_category(value: SpecialistCategoryLike) -> SpecialistCategory:
    if isinstance(value, SpecialistCategory):
        return value
    return SpecialistCategory.from_str(value)


def format_unit(value: UnitLike) -> str:
    """Restituisce l'etichetta italiana per una ``Unit``."""

    unit = _ensure_unit(value)
    return _UNIT_LABELS[unit]


def format_severity(value: SeverityLike) -> str:
    """Restituisce l'etichetta italiana per una ``Severity``."""

    severity = _ensure_severity(value)
    return _SEVERITY_LABELS[severity]


def format_nutrient(value: NutrientLike) -> str:
    """Restituisce l'etichetta italiana per un ``Nutrient``."""

    nutrient = _ensure_nutrient(value)
    return _NUTRIENT_LABELS[nutrient]


def format_specialist_category(value: SpecialistCategoryLike) -> str:
    """Restituisce l'etichetta italiana per una ``SpecialistCategory``."""

    category = _ensure_category(value)
    return _SPECIALIST_CATEGORY_LABELS[category]


__all__ = [
    "format_unit",
    "format_severity",
    "format_nutrient",
    "format_specialist_category",
]