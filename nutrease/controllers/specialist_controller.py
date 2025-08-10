from __future__ import annotations

"""Specialist‑side application controller (manage patients & link requests)."""

import logging
from datetime import date, datetime
from typing import List

from nutrease.models.communication import Connection, LinkRequest, LinkRequestState
from nutrease.models.diary import DailyDiary, Day
from nutrease.models.enums import Nutrient, Severity, Unit
from nutrease.models.record import FoodPortion, MealRecord, Record, SymptomRecord
from nutrease.models.user import Patient, Specialist
from nutrease.utils.database import Database

logger = logging.getLogger(__name__)

__all__ = ["SpecialistController"]

# Re‑use shared list defined in patient_controller to keep state in‑sync.
# If patient_controller hasn't been imported yet (e.g. specialist logs in first),
# load existing link requests from DB so connections persist across sessions.
try:  # defensive import
    from nutrease.controllers.patient_controller import _CONNECTIONS as _GLOBAL_CONN
    from nutrease.controllers.patient_controller import _LINK_REQUESTS as _GLOBAL_LR
    from nutrease.controllers.patient_controller import (
        _load_connections_from_db,
        _load_link_requests_from_db,
    )
except ModuleNotFoundError:
    _GLOBAL_LR: List[LinkRequest] = []
    _GLOBAL_CONN: List[Connection] = []
    def _load_link_requests_from_db() -> None:
        db = Database.default()
        try:
            rows = db.all(LinkRequest)
        except Exception:
            logger.debug("Caricamento LinkRequest non riuscito", exc_info=True)
            return
        for row in rows:
            try:
                p_data = row["patient"]
                if isinstance(p_data, str):
                    p_rows = db.search(Patient, email=p_data)
                    if not p_rows:
                        continue
                    p_data = p_rows[0]
                s_data = row["specialist"]
                if isinstance(s_data, str):
                    s_rows = db.search(Specialist, email=s_data)
                    if not s_rows:
                        continue
                    s_data = s_rows[0]
                patient = Patient(
                    **{k: v for k, v in p_data.items() if not k.startswith("__")}
                )
                specialist = Specialist(
                    **{k: v for k, v in s_data.items() if not k.startswith("__")}
                )
                state = LinkRequestState(row.get("state", LinkRequestState.PENDING))
                lr = LinkRequest(
                    patient=patient,
                    specialist=specialist,
                    state=state,
                    comment=row.get("comment", ""),
                )
                lr.id = row.get("id", 0)
                _GLOBAL_LR.append(lr)
            except Exception:
                logger.debug("LinkRequest non valida nel DB", exc_info=True)

    def _load_connections_from_db() -> None:
        db = Database.default()
        try:
            rows = db.all(Connection)
        except Exception:
            logger.debug("Caricamento Connection non riuscito", exc_info=True)
            return
        for row in rows:
            try:
                p_rows = db.search(Patient, email=row["patient"])
                s_rows = db.search(Specialist, email=row["specialist"])
                if not p_rows or not s_rows:
                    continue
                p_data = p_rows[0]
                s_data = s_rows[0]
                patient = Patient(
                    **{k: v for k, v in p_data.items() if not k.startswith("__")}
                )
                specialist = Specialist(
                    **{k: v for k, v in s_data.items() if not k.startswith("__")}
                )
                conn = Connection(patient=patient, specialist=specialist)
                _GLOBAL_CONN.append(conn)
            except Exception:
                logger.debug("Connection non valida nel DB", exc_info=True)


if not _GLOBAL_LR:
    _load_link_requests_from_db()
if "_GLOBAL_CONN" not in globals() or not _GLOBAL_CONN:
    _load_connections_from_db()


class SpecialistController:  # noqa: D101 – documented above
    def __init__(
        self,
        specialist: Specialist,
        *,
        db: Database | None = None,
        link_store: List[LinkRequest] | None = None,
        connection_store: List[Connection] | None = None,
    ) -> None:
        self.specialist = specialist
        self._db = db if db is not None else Database.default()

        self._link_store = link_store if link_store is not None else _GLOBAL_LR
        self._connection_store = (
            connection_store if connection_store is not None else _GLOBAL_CONN
        )

    # .....................................................................
    # Link‑request workflow
    # .....................................................................
    def _iter_link_requests(self):
        """Generator su tutte le ``LinkRequest`` verso questo specialista."""
        yield from self.link_requests()

    def link_requests(self) -> List[LinkRequest]:  # noqa: D401 – imperative
        """Restituisce tutte le ``LinkRequest`` per questo specialista."""
        return [lr for lr in self._link_store if lr.specialist == self.specialist]

    def connections(self) -> List[Connection]:  # noqa: D401 – imperative
        """Restituisce tutte le ``Connection`` attive per questo specialista."""
        return [c for c in self._connection_store if c.specialist == self.specialist]

    def pending_requests(self) -> List[LinkRequest]:  # noqa: D401 – imperative
        return [
            lr
            for lr in self._link_store
            if lr.specialist == self.specialist and lr.state == LinkRequestState.PENDING
        ]

    def accept_request(self, lr: LinkRequest) -> None:  # noqa: D401 – imperative
        if lr not in self._link_store or lr.specialist != self.specialist:
            raise ValueError("Richiesta non gestita da questo specialista.")
        conn = lr.accept()
        self._connection_store.append(conn)
        try:
            self._db.save(conn)
        except Exception:  # pragma: no cover - best effort
            logger.debug("Persistenza Connection non riuscita", exc_info=True)
        if lr in self._link_store:
            self._link_store.remove(lr)
            try:
                self._db.delete(lr)
            except Exception:  # pragma: no cover - best effort
                logger.debug("Rimozione LinkRequest non riuscita", exc_info=True)
        logger.info("LinkRequest %s accettata da %s", id(lr), self.specialist.email)

    def reject_request(self, lr: LinkRequest) -> None:  # noqa: D401 – imperative
        if lr not in self._link_store or lr.specialist != self.specialist:
            raise ValueError("Richiesta non gestita da questo specialista.")
        lr.reject()
        try:
            self._db.save(lr)
        except Exception:  # pragma: no cover - best effort
            logger.debug("Persistenza LinkRequest non riuscita", exc_info=True)
        logger.info("LinkRequest %s rifiutata da %s", id(lr), self.specialist.email)

    # .....................................................................
    # Diary & analytics helpers
    # .....................................................................

    def get_patient_diary(
        self, patient: Patient, day: date
    ) -> DailyDiary | None:  # noqa: D401 – imperative
        if not self._is_linked(patient):
            raise PermissionError("Specialist non collegato a questo paziente.")

        diary = next((d for d in patient.diaries if d.day.date == day), None)
        if diary is not None:
            return diary

        records: List[Record] = []
        try:
            meal_rows = self._db.search(MealRecord, patient_email=patient.email)
            for row in meal_rows:
                ts = datetime.fromisoformat(row["created_at"])
                if ts.date() != day:
                    continue
                portions = [
                    FoodPortion(
                        food_name=p["food_name"],
                        quantity=p["quantity"],
                        unit=Unit(p["unit"]),
                    )
                    for p in row.get("portions", [])
                ]
                rec = MealRecord(
                    id=row.get("id", 0),
                    created_at=ts,
                    portions=portions,
                    note=row.get("note"),
                )
                object.__setattr__(rec, "patient_email", patient.email)
                records.append(rec)

            sym_rows = self._db.search(SymptomRecord, patient_email=patient.email)
            for row in sym_rows:
                ts = datetime.fromisoformat(row["created_at"])
                if ts.date() != day:
                    continue
                rec = SymptomRecord(
                    id=row.get("id", 0),
                    created_at=ts,
                    symptom=row.get("symptom", ""),
                    severity=Severity(row.get("severity", Severity.NONE.value)),
                    note=row.get("note"),
                )
                object.__setattr__(rec, "patient_email", patient.email)
                records.append(rec)
        except Exception:  # pragma: no cover - best effort
            logger.debug("Caricamento diario non riuscito", exc_info=True)

        if not records:
            return None
        records.sort(key=lambda r: r.created_at)
        diary = DailyDiary(day=Day(date=day), patient=patient, records=records)
        patient.diaries.append(diary)
        return diary

    def nutrient_total(
        self, patient: Patient, day: date, nutrient: Nutrient
    ) -> float:  # noqa: D401 – imperative
        diary = self.get_patient_diary(patient, day)
        return diary.get_totals(nutrient) if diary else 0.0

    # .....................................................................
    # Internal utils
    # .....................................................................

    def _is_linked(self, patient: Patient) -> bool:  # noqa: D401 – imperative
        return any(
            c.patient == patient and c.specialist == self.specialist
            for c in self._connection_store
        )

    # ------------------------------------------------------------------
    def remove_link(self, patient: Patient) -> None:  # noqa: D401 – imperative
        """Remove an accepted link with *patient* (both memory and DB)."""
        conn = next(
            (
                c
                for c in self._connection_store
                if c.specialist == self.specialist and c.patient == patient
            ),
            None,
        )
        if not conn:
            raise ValueError("Paziente non collegato")
        self._connection_store.remove(conn)
        try:
            self._db.delete(
                Connection,
                patient=patient.email,
                specialist=self.specialist.email,
            )
        except Exception:  # pragma: no cover - best effort
            logger.debug("Rimozione Connection non riuscita", exc_info=True)