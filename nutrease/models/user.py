from __future__ import annotations

"""Modelli di dominio utente (astratti e concreti).

* Gestisce la **validazione password** 
* L’unicità dell’e-mail (RNF6) è demandata a
  :class:`nutrease.services.auth_service.AuthService`, che effettua il check
  sul database; non serve più un registry in-memory.
"""

import string
from abc import ABC
from dataclasses import asdict, dataclass, field
from datetime import date
from typing import TYPE_CHECKING, List

from pydantic import EmailStr, TypeAdapter

from .enums import SpecialistCategory

if TYPE_CHECKING:  # pragma: no cover – forward refs
    from .diary import AlarmConfig, DailyDiary
    from .record import Record


# ---------------------------------------------------------------------------
# Helper – password validation
# ---------------------------------------------------------------------------


def _validate_password(pwd: str) -> None:
    """Almeno 8 caratteri alfanumerici con un numero e una lettera maiuscola."""
    if len(pwd) == 64 and all(c in string.hexdigits for c in pwd):
        return
    if (
        len(pwd) < 8
        or not pwd.isalnum()
        or not any(c.isdigit() for c in pwd)
        or not any(c.isupper() for c in pwd)
    ):
        raise ValueError(
            "La password deve contenere almeno 8 caratteri alfanumerici "
            "con almeno un numero e una lettera maiuscola."
        )
# ---------------------------------------------------------------------------
# Abstract base class – User
# ---------------------------------------------------------------------------


@dataclass(init=False, eq=False)
class User(ABC):
    """Attributi comuni ai vari ruoli utente."""

    _email: str
    _name: str
    _surname: str
    _password: str = field(repr=False)

    # Riutilizza un ``TypeAdapter`` per convalidare l'e-mail senza istanziare
    # direttamente ``EmailStr`` (in Pydantic v2 non è più chiamabile).
    _email_adapter = TypeAdapter(EmailStr)

    def __init__(self, *, email: str, name: str, surname: str, password: str) -> None:
        _validate_password(password)
        self._email = self._email_adapter.validate_python(email)
        self._name = name
        self._surname = surname
        self._password = password

    @property
    def email(self) -> str:  # noqa: D401
        return self._email

    @email.setter
    def email(self, value: str) -> None:
        self._email = self._email_adapter.validate_python(value)

    @property
    def name(self) -> str:  # noqa: D401
        return self._name

    @name.setter
    def name(self, value: str) -> None:
        self._name = value

    @property
    def surname(self) -> str:  # noqa: D401
        return self._surname

    @surname.setter
    def surname(self, value: str) -> None:
        self._surname = value

    @property
    def password(self) -> str:  # noqa: D401
        return self._password

    @password.setter
    def password(self, value: str) -> None:
        _validate_password(value)
        self._password = value


# Uguaglianza e hashing basati esclusivamente sull'e-mail per evitare ricorsioni
# attraverso strutture annidate come i diari che fanno riferimento al ``Patient``.

    def __eq__(self, other: object) -> bool:  # type: ignore[override]
        if not isinstance(other, User):
            return NotImplemented
        return self.email == other.email

    def __hash__(self) -> int:  # type: ignore[override]
        return hash(self.email)


# ---------------------------------------------------------------------------
# Concrete subclasses
# ---------------------------------------------------------------------------


@dataclass(init=False, eq=False)
class Patient(User):
    """Utente che registra pasti / sintomi e si collega agli specialisti."""

    _alarms: List["AlarmConfig"] = field(default_factory=list, repr=False)
    _diaries: List["DailyDiary"] = field(default_factory=list, repr=False)
    _profile_note: str = ""

    def __init__(
        self,
        *,
        email: str,
        password: str,
        name: str,
        surname: str,
        alarms: List["AlarmConfig"] | None = None,
        diaries: List["DailyDiary"] | None = None,
        profile_note: str = "",
    ) -> None:
        super().__init__(email=email, password=password, name=name, surname=surname)
        self._alarms = alarms if alarms is not None else []
        self._diaries = diaries if diaries is not None else []
        self._profile_note = profile_note

    @property
    def alarms(self) -> List["AlarmConfig"]:  # noqa: D401
        return self._alarms

    @property
    def diaries(self) -> List["DailyDiary"]:  # noqa: D401
        return self._diaries

    @property
    def profile_note(self) -> str:  # noqa: D401
        return self._profile_note

    @profile_note.setter
    def profile_note(self, value: str) -> None:
        self._profile_note = value

    # API façade -----------------------------------------------------------
    def register_record(self, work_date: date, record: "Record") -> None:
        """Aggiunge *record* al diario di *work_date* (creandolo se assente)."""
        for diary in self.diaries:
            if diary.day.date == work_date:  # type: ignore[attr-defined]
                diary.add_record(record)  # type: ignore[attr-defined]
                return

        # Deferred import per evitare cicli
        from .diary import DailyDiary, Day  # local

        new_diary = DailyDiary(day=Day(date=work_date), patient=self, records=[record])
        self.diaries.append(new_diary)

    def as_dict(self) -> dict:  # noqa: D401
        """Serializza il paziente in forma JSON-friendly senza i diari."""
        return {
            "email": self.email,
            "name": self.name,
            "surname": self.surname,
            "password": self.password,
            "alarms": [asdict(a) for a in self.alarms],
            "profile_note": self.profile_note,
        }


@dataclass(init=False, eq=False)
class Specialist(User):
    """Professionista che analizza i dati del paziente."""

    _category: SpecialistCategory
    _bio: str = ""

    def __init__(
        self,
        *,
        email: str,
        password: str,
        name: str,
        surname: str,
        category: SpecialistCategory,
        bio: str = "",
    ) -> None:
        super().__init__(email=email, password=password, name=name, surname=surname)
        self._category = category
        self._bio = bio

    @property
    def category(self) -> SpecialistCategory:  # noqa: D401
        return self._category

    @property
    def bio(self) -> str:  # noqa: D401
        return self._bio

    @bio.setter
    def bio(self, value: str) -> None:
        self._bio = value

    def get_category(self) -> SpecialistCategory:  # noqa: D401
        return self.category

    def as_dict(self) -> dict:  # noqa: D401
        """Serializza lo specialista in forma JSON-friendly."""
        return {
            "email": self.email,
            "name": self.name,
            "surname": self.surname,
            "password": self.password,
            "category": self.category,
            "bio": self.bio,
        }