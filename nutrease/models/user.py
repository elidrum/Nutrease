from __future__ import annotations

"""User domain models (abstract & concrete).

* Gestisce la **validazione password** (RNF4).
* L’unicità dell’e-mail (RNF6) è ora demandata a :class:`nutrease.services.auth_service.AuthService`,
  che effettua il check sul database; non serve più un registry in-memory.
"""

from abc import ABC
from dataclasses import field
from datetime import date
from typing import List, TYPE_CHECKING

from pydantic import EmailStr
from pydantic.dataclasses import dataclass

from .enums import SpecialistCategory

if TYPE_CHECKING:  # pragma: no cover – forward refs
    from .diary import AlarmConfig, DailyDiary
    from .record import Record


# ---------------------------------------------------------------------------
# Helper – password validation
# ---------------------------------------------------------------------------


def _validate_password(pwd: str) -> None:
    """RNF4: almeno 8 **caratteri alfanumerici**."""
    if len(pwd) < 8 or not pwd.isalnum():
        raise ValueError(
            "La password deve contenere almeno 8 caratteri alfanumerici (RNF4)."
        )


# ---------------------------------------------------------------------------
# Abstract base class – User
# ---------------------------------------------------------------------------


@dataclass(config={"validate_assignment": True, "repr": True})
class User(ABC):
    """Attributi comuni ai vari ruoli utente."""

    email: EmailStr
    name: str
    surname: str
    password: str = field(repr=False)

    # ---------------------------------------------------------------------
    def __post_init__(self) -> None:  # noqa: D401
        _validate_password(self.password)


# ---------------------------------------------------------------------------
# Concrete subclasses
# ---------------------------------------------------------------------------


@dataclass
class Patient(User):
    """Utente che registra pasti / sintomi e si collega agli specialisti."""

    alarms: List["AlarmConfig"] = field(default_factory=list, repr=False)
    diaries: List["DailyDiary"] = field(default_factory=list, repr=False)

    # API façade -----------------------------------------------------------
    def register_record(self, work_date: date, record: "Record") -> None:
        """Aggiunge *record* al diario di *work_date* (creandolo se assente)."""
        for diary in self.diaries:
            if diary.day.date == work_date:  # type: ignore[attr-defined]
                diary.add_record(record)  # type: ignore[attr-defined]
                return

        # Deferred import per evitare cicli
        from .diary import Day, DailyDiary  # local

        new_diary = DailyDiary(day=Day(date=work_date), patient=self, records=[record])
        self.diaries.append(new_diary)


@dataclass
class Specialist(User):
    """Professionista che analizza i dati del paziente."""

    category: SpecialistCategory

    def get_category(self) -> SpecialistCategory:  # noqa: D401
        return self.category