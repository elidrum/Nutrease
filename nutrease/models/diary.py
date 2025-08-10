from __future__ import annotations

"""Diary‑related domain objects (UML §§ UC8‑12).

* ``Day`` – wrapper around :class:`datetime.date` to allow future metadata.
* ``DailyDiary`` – collection of :class:`nutrease.models.record.Record` for a day.
* ``AlarmConfig`` – simple daily alarm with ``next_activation()`` helper.
"""

import datetime as dt
from dataclasses import field
from typing import List

from pydantic.dataclasses import dataclass

from nutrease.utils.tz import LOCAL_TZ, local_now

from .enums import Nutrient
from .record import MealRecord, Record

__all__ = ["Day", "DailyDiary", "AlarmConfig"]


# ---------------------------------------------------------------------------
# Core – Day
# ---------------------------------------------------------------------------


@dataclass
class Day:
    """A calendar day wrapper (easier future extensions)."""

    date: dt.date

    def __str__(self) -> str:  # noqa: D401 – imperative
        return self.date.isoformat()


# ---------------------------------------------------------------------------
# AlarmConfig
# ---------------------------------------------------------------------------


@dataclass
class AlarmConfig:
    """Configurazione di un promemoria ricorrente.

    ``days`` usa la convenzione :pyfunc:`datetime.date.weekday` (0=Lunedì).
    """

    hour: int = 8
    minute: int = 0
    enabled: bool = True
    days: List[int] = field(default_factory=lambda: list(range(7)))

    # .....................................................................
    # Public helpers
    # .....................................................................

    def next_activation(
        self, *, now: dt.datetime | None = None
    ) -> dt.datetime | None:  # noqa: D401 – imperative
        """Return the next datetime when the alarm should trigger.

        Parameters
        ----------
        now
            *Optional* reference time (defaults to :pyfunc:`datetime.now`).

        Returns
        -------
        datetime | None
            ``None`` if the alarm is disabled; otherwise the next occurrence
            **strictly after** *now*.
        """
        if not self.enabled or not self.days:
            return None

        now = now or local_now()
        if now.tzinfo is None:
            now = now.replace(tzinfo=LOCAL_TZ)
        for offset in range(8):  # massimo una settimana di lookahead
            candidate_date = now.date() + dt.timedelta(days=offset)
            if candidate_date.weekday() not in self.days:
                continue
            candidate_dt = dt.datetime.combine(
                candidate_date, dt.time(self.hour, self.minute), tzinfo=LOCAL_TZ
            )
            if candidate_dt > now:
                return candidate_dt
        return None


# ---------------------------------------------------------------------------
# DailyDiary
# ---------------------------------------------------------------------------


@dataclass(config={"validate_assignment": True})
class DailyDiary:
    """Diary of records for a single *Day* and *Patient*."""

    day: Day
    patient: "Patient"
    records: List[Record] = None  # type: ignore[assignment]

    def __post_init__(self):  # noqa: D401 – imperative
        if self.records is None:
            self.records = []

    # .....................................................................
    # CRUD helpers on *records*
    # .....................................................................

    def add_record(self, record: Record) -> None:  # noqa: D401 – imperative
        """Append *record* after validating its date matches this diary."""
        if record.created_at.date() != self.day.date:
            raise ValueError(
                "La data di creazione del record non corrisponde al giorno del diario."
            )
        self.records.append(record)

    def remove_record(self, record: Record) -> None:  # noqa: D401 – imperative
        try:
            self.records.remove(record)
        except ValueError as err:
            raise KeyError("Record non trovato nel diario.") from err

    def modify_record(
        self, old: Record, new: Record
    ) -> None:  # noqa: D401 – imperative
        """Replace *old* with *new* preserving position (raises if *old* absent)."""
        idx = self.records.index(old)  # raises ValueError if absent
        if new.created_at.date() != self.day.date:
            raise ValueError(
                "Il nuovo record appartiene a un giorno diverso da questo diario."
            )
        self.records[idx] = new

    # .....................................................................
    # Analytics helpers
    # .....................................................................

    def get_totals(self, nutrient: Nutrient) -> float:  # noqa: D401 – imperative
        """Compute total *nutrient* grams across all *MealRecord* entries."""
        total = 0.0
        for rec in self.records:
            if isinstance(rec, MealRecord):
                total += rec.get_nutrient_total(nutrient)
        return total


# Resolve forward references for dataclasses defined across modules ---------
from pydantic.dataclasses import rebuild_dataclass  # noqa: E402

from .user import Patient  # noqa: E402

rebuild_dataclass(Patient)