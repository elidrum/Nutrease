from __future__ import annotations

"""Patient‑side application controller (UML UC8‑12).

Orchestra le operazioni di diario, allarmi e richieste di collegamento verso
uno specialista, delegando la logica di dominio ai *model* e ai *service*.
"""

import logging
from datetime import date
from typing import List

from nutrease.models.communication import LinkRequest, LinkRequestState
from nutrease.models.diary import DailyDiary
from nutrease.models.enums import Nutrient
from nutrease.models.record import Record
from nutrease.models.user import Patient, Specialist
from nutrease.services.notification_service import NotificationService

logger = logging.getLogger(__name__)

__all__ = ["PatientController"]

# In‑memory shared storage ---------------------------------------------------
_LINK_REQUESTS: List[LinkRequest] = []  # naive placeholder until DB layer


class PatientController:  # noqa: D101 – documented above
    def __init__(
        self,
        patient: Patient,
        *,
        notification_service: NotificationService | None = None,
        link_store: List[LinkRequest] | None = None,
    ) -> None:
        self.patient = patient
        self._notif = notification_service
        self._link_store = link_store if link_store is not None else _LINK_REQUESTS

        if self._notif:
            self._notif.register_patient(patient)

    # .....................................................................
    # Diary API (CRUD wrapper)
    # .....................................................................

    def add_record(self, record: Record) -> None:  # noqa: D401 – imperative
        self.patient.register_record(record.created_at.date(), record)
        logger.info("Record %s aggiunto al diario di %s", record.record_type, self.patient.email)

    def get_diary(self, day: date) -> DailyDiary | None:  # noqa: D401 – imperative
        return next((d for d in self.patient.diaries if d.day.date == day), None)

    def nutrient_total(self, day: date, nutrient: Nutrient) -> float:  # noqa: D401 – imperative
        diary = self.get_diary(day)
        return diary.get_totals(nutrient) if diary else 0.0

    # .....................................................................
    # Alarm helpers
    # .....................................................................

    def configure_alarm(self, hour: int, minute: int) -> None:  # noqa: D401 – imperative
        from nutrease.models.diary import AlarmConfig  # local to avoid import cycle

        self.patient.alarm = AlarmConfig(hour=hour, minute=minute, enabled=True)
        logger.info("Alarm impostato a %02d:%02d per %s", hour, minute, self.patient.email)

    # .....................................................................
    # Link‑request helpers (Patient → Specialist)
    # .....................................................................

    def send_link_request(self, specialist: Specialist, comment: str = "") -> LinkRequest:  # noqa: D401 – imperative
        existing = next(
            (
                lr
                for lr in self._link_store
                if lr.patient == self.patient and lr.specialist == specialist and lr.state == LinkRequestState.PENDING
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
        logger.info("LinkRequest creata da %s a %s", self.patient.email, specialist.email)
        return req
