from __future__ import annotations

"""Diary‑related domain objects (UML §§ UC8‑12).

* ``Day`` – wrapper around :class:`datetime.date` to allow future metadata.
* ``DailyDiary`` – collection of :class:`nutrease.models.record.Record` for a day.
* ``AlarmConfig`` – simple daily alarm with ``next_activation()`` helper.
"""

from datetime import date, datetime, time, timedelta
from typing import List, TYPE_CHECKING

from pydantic.dataclasses import dataclass

from .enums import Nutrient
from .record import MealRecord, Record

if TYPE_CHECKING:  # pragma: no cover – forward refs only
    from .user import Patient

__all__ = ["Day", "DailyDiary", "AlarmConfig"]


# ---------------------------------------------------------------------------
# Core – Day
# ---------------------------------------------------------------------------

@dataclass
class Day:
    """A calendar day wrapper (easier future extensions)."""

    date: date

    def __str__(self) -> str:  # noqa: D401 – imperative
        return self.date.isoformat()


# ---------------------------------------------------------------------------
# AlarmConfig
# ---------------------------------------------------------------------------

@dataclass
class AlarmConfig:
    """Daily alarm time & enable switch.

    *For now* the alarm recurs **every day** at the configured local hour/minute.
    In future we might enrich this with custom RRULEs.
    """

    hour: int = 8
    minute: int = 0
    enabled: bool = True

    # .....................................................................
    # Public helpers
    # .....................................................................

    def next_activation(self, *, now: datetime | None = None) -> datetime | None:  # noqa: D401 – imperative
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
        if not self.enabled:
            return None

        now = now or datetime.now()
        today_target = datetime.combine(now.date(), time(self.hour, self.minute))
        if today_target > now:
            return today_target
        # Otherwise schedule for tomorrow
        tomorrow = today_target + timedelta(days=1)
        return tomorrow


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

    def modify_record(self, old: Record, new: Record) -> None:  # noqa: D401 – imperative
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
from pydantic.dataclasses import rebuild_dataclass
from .user import Patient

rebuild_dataclass(Patient)