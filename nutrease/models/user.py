from __future__ import annotations

"""User domain models (abstract & concrete) and an in‑memory e‑mail registry.

This module fulfils RNF4 (password validation ≥ 8 caratteri alfanumerici) and
RNF6 (unicità e‑mail) by means of a temporary :class:`EmailRegistry` that will
later be replaced by a proper DB constraint.
"""

from abc import ABC
from dataclasses import field
from datetime import date
from typing import ClassVar, List, Set, TYPE_CHECKING

from pydantic import EmailStr
from pydantic.dataclasses import dataclass

from .enums import SpecialistCategory

if TYPE_CHECKING:  # pragma: no cover – forward refs
    from .diary import AlarmConfig, DailyDiary
    from .record import Record


# ---------------------------------------------------------------------------
# Helper – in‑memory uniqueness check for e‑mail addresses
# ---------------------------------------------------------------------------

class EmailRegistry:
    """Very small helper to enforce e‑mail uniqueness in memory.

    *Nota*: verrà sostituito da una unique‑constraint a livello di DB.
    """

    _emails: ClassVar[Set[str]] = set()

    @classmethod
    def register(cls, email: str) -> None:
        key = email.lower()
        if key in cls._emails:
            raise ValueError(f"E‑mail '{email}' già in uso (RNF6).")
        cls._emails.add(key)

    @classmethod
    def unregister(cls, email: str) -> None:  # pragma: no cover
        cls._emails.discard(email.lower())

    @classmethod
    def clear(cls) -> None:  # pragma: no cover – handy for tests
        cls._emails.clear()


# ---------------------------------------------------------------------------
# Shared validation helpers
# ---------------------------------------------------------------------------

def _validate_password(password: str) -> None:
    """Enforce RNF4: ≥ 8 *alphanumeric* characters."""
    if len(password) < 8 or not password.isalnum():
        raise ValueError("La password deve contenere almeno 8 caratteri alfanumerici (RNF4).")


# ---------------------------------------------------------------------------
# Abstract base class – *User*
# ---------------------------------------------------------------------------

@dataclass(config={"validate_assignment": True, "repr": True})
class User(ABC):
    """Common attributes and validation shared by all user roles."""

    email: EmailStr
    name: str
    surname: str
    password: str = field(repr=False)

    # --- runtime hooks ------------------------------------------------------

    def __post_init__(self) -> None:  # noqa: D401 – imperative
        """Validate & register e‑mail uniqueness right after instantiation."""
        _validate_password(self.password)
        EmailRegistry.register(self.email)

    def __del__(self):  # pragma: no cover – defensive cleanup
        try:
            EmailRegistry.unregister(self.email)
        except Exception:  # noqa: BLE001 – best‑effort on interpreter shutdown
            pass


# ---------------------------------------------------------------------------
# Concrete subclasses – *Patient* & *Specialist*
# ---------------------------------------------------------------------------

@dataclass
class Patient(User):
    """A system user who records meals/symptoms and is linked to specialists."""

    alarm: "AlarmConfig | None" = None
    diaries: List["DailyDiary"] = field(default_factory=list, repr=False)

    # API façade -------------------------------------------------------------
    def register_record(self, work_date: date, record: "Record") -> None:
        """Append *record* to the diary for *work_date*, creating it if absent."""
        for diary in self.diaries:
            if diary.day.date == work_date:  # type: ignore[attr-defined]
                diary.add_record(record)  # type: ignore[attr-defined]
                return
        # Deferred import avoids circular dependencies during module load.
        from .diary import Day, DailyDiary  # local import

        new_diary = DailyDiary(day=Day(date=work_date), patient=self, records=[record])
        self.diaries.append(new_diary)


@dataclass
class Specialist(User):
    """Professional user who analyses and communicates with patients."""

    category: SpecialistCategory

    # Convenience getter (follows UML signature) -----------------------------
    def get_category(self) -> SpecialistCategory:  # noqa: D401 – imperative
        return self.category
