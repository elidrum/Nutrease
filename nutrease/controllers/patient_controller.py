from __future__ import annotations

"""Patient-side application controller (UML UC 8-12).

Orchestra le operazioni di diario, allarmi e richieste di collegamento verso
uno specialista, delegando la logica di dominio ai *model* e ai *service*.

Novità 2025-08:
* Wrapper di alto livello **add_meal**, **add_symptom**, **remove_record**,
  **nutrient_total** con firma coerente alla nuova UI Streamlit.
* Campo autoincrementale ``_next_rec_id`` per assegnare ``Record.id``.
* ``configure_alarm()`` ora accetta anche lo stato *enabled*.
"""

import logging
from datetime import date, datetime, time
from typing import List

from nutrease.models.communication import LinkRequest, LinkRequestState
from nutrease.models.diary import DailyDiary
from nutrease.models.enums import Nutrient, RecordType, Severity, Unit
from nutrease.models.record import FoodPortion, MealRecord, Record, SymptomRecord
from nutrease.models.user import Patient, Specialist
from nutrease.services.notification_service import NotificationService
from nutrease.utils.database import Database

logger = logging.getLogger(__name__)

__all__ = ["PatientController"]

# In-memory shared storage ---------------------------------------------------
_LINK_REQUESTS: List[LinkRequest] = []  # naive placeholder until DB layer

def _load_link_requests_from_db() -> None:
    """Populate in-memory link requests from the JSON DB (best effort)."""
    db = Database.default()
    try:
        rows = db.all(LinkRequest)
    except Exception:  # pragma: no cover - defensive
        logger.debug("Caricamento LinkRequest non riuscito", exc_info=True)
        return
    for row in rows:
        try:
            p_data = row["patient"]
            s_data = row["specialist"]
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
            _LINK_REQUESTS.append(lr)
        except Exception:  # pragma: no cover - skip malformed rows
            logger.debug("LinkRequest non valida nel DB", exc_info=True)


_load_link_requests_from_db()


class PatientController:  # noqa: D101 – documented above
    _next_rec_id: int = 1  # id autoincrement “globale” all’istanza

    # ---------------------------------------------------------------------
    # Init
    # ---------------------------------------------------------------------
    def __init__(
        self,
        patient: Patient,
        *,
        db: Database | None = None,
        notification_service: NotificationService | None = None,
        link_store: List[LinkRequest] | None = None,
    ) -> None:
        self.patient = patient
        self._db = db if db is not None else Database.default()
        self._notif = notification_service
        self._link_store = link_store if link_store is not None else _LINK_REQUESTS

        if self._notif:
            self._notif.register_patient(patient)

    # ---------------------------------------------------------------------
    # Diary – API “high-level” usate dalla UI
    # ---------------------------------------------------------------------
    def _iter_link_requests(self):  # noqa: D401 – internal helper
        """Ritorna un generatore di LinkRequest relative a questo paziente."""
        yield from (
            lr for lr in self._link_store if lr.patient == self.patient
        )
        
    # ---------- ADD MEAL --------------------------------------------------
    def add_meal(  # noqa: D401 – imperative
        self,
        day: date,
        when: time,
        food_names: List[str],
        quantities: List[float],
        units: List[Unit],
        note: str | None = None,
    ) -> None:
        """Crea un `MealRecord` e lo salva."""
        
        portions = [
            FoodPortion(food_name=fn, quantity=q, unit=u)
            for fn, q, u in zip(food_names, quantities, units)
        ]
        record = MealRecord(
            id=self._next_rec_id,
            created_at=datetime.combine(day, when),
            portions=portions,
            note=note,
        )
        record.record_type = RecordType.MEAL
        self._next_rec_id += 1
        self.add_record(record)

    # ---------- ADD SYMPTOM ----------------------------------------------
    def add_symptom(  # noqa: D401 – imperative
        self,
        day: date,
        description: str,
        severity: Severity,
        when: time,
        note: str | None = None,
    ) -> None:
        """Crea un `SymptomRecord` e lo salva."""
        record = SymptomRecord(
            id=self._next_rec_id,
            created_at=datetime.combine(day, when),
            symptom=description,
            severity=severity,
            note=note,
        )
        record.record_type = RecordType.SYMPTOM
        self._next_rec_id += 1
        self.add_record(record)

    # ---------- REMOVE RECORD --------------------------------------------
    def remove_record(self, day: date, record_id: int) -> None:  # noqa: D401
        """Elimina un record dal diario e dal DB (best effort)."""
        diary = self.get_diary(day)
        if diary is None:
            logger.warning("Diario %s non trovato per rimozione record", day)
            return
        target = next((r for r in diary.records if r.id == record_id), None)
        if not target:
            logger.warning("Record id=%s non trovato nel diario %s", record_id, day)
            return

        diary.remove_record(target)
        try:
            self._db.delete(target)  # type: ignore[attr-defined]
        except Exception:  # pragma: no cover
            logger.debug("Delete record non riuscita", exc_info=True)
        logger.info("Record %s eliminato da %s", record_id, self.patient.email)
        
        try:
            self._db.save(self.patient)
        except Exception:  # pragma: no cover
            logger.debug("Persistenza paziente non riuscita", exc_info=True)
    # ---------- NUTRIENT TOTAL -------------------------------------------
    def nutrient_total(  # noqa: D401 – imperative
        self, day: date, nutrient: Nutrient
    ) -> float:
        diary = self.get_diary(day)
        if diary is None:
            return 0.0
        tot = 0.0
        for rec in diary.records:
            if rec.record_type == RecordType.MEAL:
                meal: MealRecord = rec  # type: ignore[assignment]
                tot += meal.get_nutrient_total(nutrient)
        return tot

    # ---------- MODIFY RECORD -------------------------------------------
    def modify_meal(
        self,
        day: date,
        record_id: int,
        food_names: List[str],
        quantities: List[float],
        units: List[Unit],
        note: str | None = None,
    ) -> None:
        diary = self.get_diary(day)
        if diary is None:
            raise KeyError("Diario non trovato")
        old = next(
            (r for r in diary.records if r.id == record_id and r.record_type == RecordType.MEAL),
            None,
        )
        if old is None:
            raise KeyError("Record non trovato")
        meal: MealRecord = old  # type: ignore[assignment]
        portions = [
            FoodPortion(food_name=fn, quantity=q, unit=u)
            for fn, q, u in zip(food_names, quantities, units)
        ]
        new = MealRecord(
            id=record_id,
            created_at=meal.created_at,
            portions=portions,
            note=note,
        )
        diary.modify_record(meal, new)
        try:
            self._db.save(new)
            self._db.save(self.patient)
        except Exception:  # pragma: no cover
            logger.debug("Persistenza update meal non riuscita", exc_info=True)

    def modify_symptom(
        self,
        day: date,
        record_id: int,
        description: str,
        severity: Severity,
        when: time,
        note: str | None = None,
    ) -> None:
        diary = self.get_diary(day)
        if diary is None:
            raise KeyError("Diario non trovato")
        old = next(
            (r for r in diary.records if r.id == record_id and r.record_type == RecordType.SYMPTOM),
            None,
        )
        if old is None:
            raise KeyError("Record non trovato")
        sym: SymptomRecord = old  # type: ignore[assignment]
        new = SymptomRecord(
            id=record_id,
            created_at=datetime.combine(day, when),
            symptom=description,
            severity=severity,
            note=note,
        )
        diary.modify_record(sym, new)
        try:
            self._db.save(new)
            self._db.save(self.patient)
        except Exception:  # pragma: no cover
            logger.debug("Persistenza update symptom non riuscita", exc_info=True)

    # ---------- Metodo legacy add_record (resta invariato) ---------------
    def add_record(self, record: Record) -> None:  # noqa: D401 – imperative
        self.patient.register_record(record.created_at.date(), record)
        try:
            self._db.save(record)
            self._db.save(self.patient)
        except Exception:  # pragma: no cover - best effort
            logger.debug("Persistenza record non riuscita", exc_info=True)
        logger.info(
            "Record %s aggiunto al diario di %s",
            record.record_type,
            self.patient.email,
        )

    def get_diary(self, day: date) -> DailyDiary | None:  # noqa: D401
        return next((d for d in self.patient.diaries if d.day.date == day), None)

    # ---------------------------------------------------------------------
    # Alarm helpers
    # ---------------------------------------------------------------------
    def add_alarm(
        self, hour: int, minute: int, days: List[int], enabled: bool = True
    ) -> None:  # noqa: D401 – imperative
        from nutrease.models.diary import AlarmConfig  # local import

        self.patient.alarms.append(
            AlarmConfig(hour=hour, minute=minute, days=days, enabled=enabled)
        )
        try:
            self._db.save(self.patient)
        except Exception:  # pragma: no cover
            logger.debug("Persistenza paziente non riuscita", exc_info=True)
        logger.info(
            "Alarm aggiunto %02d:%02d per %s",
            hour,
            minute,
            self.patient.email,
        )

    def update_alarm(
        self, idx: int, hour: int, minute: int, days: List[int], enabled: bool
    ) -> None:  # noqa: D401 – imperative
        from nutrease.models.diary import AlarmConfig  # local import

        if 0 <= idx < len(self.patient.alarms):
            self.patient.alarms[idx] = AlarmConfig(
                hour=hour, minute=minute, days=days, enabled=enabled
            )
            try:
                self._db.save(self.patient)
            except Exception:  # pragma: no cover
                logger.debug("Persistenza paziente non riuscita", exc_info=True)
        else:
            logger.warning("Indice alarm %s non valido", idx)

    def remove_alarm(self, idx: int) -> None:  # noqa: D401 – imperative
        if 0 <= idx < len(self.patient.alarms):
            self.patient.alarms.pop(idx)
            try:
                self._db.save(self.patient)
            except Exception:  # pragma: no cover
                logger.debug("Persistenza paziente non riuscita", exc_info=True)
        else:
            logger.warning("Indice alarm %s non valido", idx)

    # ---------------------------------------------------------------------
    # Link-request helpers (Patient → Specialist)
    # ---------------------------------------------------------------------
    def send_link_request(  # noqa: D401 – imperative
        self, specialist: Specialist, comment: str = ""
    ) -> LinkRequest:
        existing = next(
            (
                lr
                for lr in self._link_store
                if lr.patient == self.patient
                and lr.specialist == specialist
                and lr.state == LinkRequestState.PENDING
            ),
            None,
        )
        if existing:
            raise ValueError("Richiesta già inviata e in attesa di risposta.")

        req = LinkRequest(
            patient=self.patient,
            specialist=specialist,
            state=LinkRequestState.PENDING,
            comment=comment,
        )
        self._link_store.append(req)
        try:
            req.id = self._db.save(req)
        except Exception:  # pragma: no cover - best effort
            logger.debug("Persistenza LinkRequest non riuscita", exc_info=True)
        logger.info(
            "LinkRequest creata da %s a %s", self.patient.email, specialist.email
        )
        return req
    
    def send_link_request_by_email(self, email: str, comment: str = "") -> LinkRequest:
        rows = self._db.search(Specialist, email=email.lower())
        if not rows:
            raise ValueError("Specialista non trovato")
        data = rows[0]
        specialist = Specialist(**{k: v for k, v in data.items() if not k.startswith("__")})
        return self.send_link_request(specialist, comment)

    # ------------------------------------------------------------------
    def remove_link(self, specialist: Specialist) -> None:  # noqa: D401 – imperative
        """Remove an accepted link with *specialist* (both memory and DB)."""
        lr = next(
            (
                lr
                for lr in self._link_store
                if lr.patient == self.patient
                and lr.specialist == specialist
                and lr.state == LinkRequestState.ACCEPTED
            ),
            None,
        )
        if not lr:
            raise ValueError("Specialista non collegato")
        self._link_store.remove(lr)
        try:
            self._db.delete(lr)
        except Exception:  # pragma: no cover - best effort
            logger.debug("Rimozione LinkRequest non riuscita", exc_info=True)
  