from __future__ import annotations

"""Specialist‑side application controller (manage patients & link requests)."""

import logging
from datetime import date
from typing import List

from nutrease.models.communication import LinkRequest, LinkRequestState
from nutrease.models.diary import DailyDiary
from nutrease.models.enums import Nutrient
from nutrease.models.user import Patient, Specialist

logger = logging.getLogger(__name__)

__all__ = ["SpecialistController"]

# Re‑use shared list defined in patient_controller to keep state in‑sync
try:  # defensive import in case patient_controller not yet evaluated
    from nutrease.controllers.patient_controller import _LINK_REQUESTS as _GLOBAL_LR
except ModuleNotFoundError:
    _GLOBAL_LR: List[LinkRequest] = []


class SpecialistController:  # noqa: D101 – documented above
    def __init__(self, specialist: Specialist, *, link_store: List[LinkRequest] | None = None):
        self.specialist = specialist
        self._link_store = link_store if link_store is not None else _GLOBAL_LR

    # .....................................................................
    # Link‑request workflow
    # .....................................................................

    def pending_requests(self) -> List[LinkRequest]:  # noqa: D401 – imperative
        return [lr for lr in self._link_store if lr.specialist == self.specialist and lr.state == LinkRequestState.PENDING]

    def accept_request(self, lr: LinkRequest) -> None:  # noqa: D401 – imperative
        if lr not in self._link_store or lr.specialist != self.specialist:
            raise ValueError("Richiesta non gestita da questo specialista.")
        lr.accept()
        logger.info("LinkRequest %s accettata da %s", id(lr), self.specialist.email)

    def reject_request(self, lr: LinkRequest) -> None:  # noqa: D401 – imperative
        if lr not in self._link_store or lr.specialist != self.specialist:
            raise ValueError("Richiesta non gestita da questo specialista.")
        lr.reject()
        logger.info("LinkRequest %s rifiutata da %s", id(lr), self.specialist.email)

    # .....................................................................
    # Diary & analytics helpers
    # .....................................................................

    def get_patient_diary(self, patient: Patient, day: date) -> DailyDiary | None:  # noqa: D401 – imperative
        if not self._is_linked(patient):
            raise PermissionError("Specialist non collegato a questo paziente.")
        return next((d for d in patient.diaries if d.day.date == day), None)

    def nutrient_total(self, patient: Patient, day: date, nutrient: Nutrient) -> float:  # noqa: D401 – imperative
        diary = self.get_patient_diary(patient, day)
        return diary.get_totals(nutrient) if diary else 0.0

    # .....................................................................
    # Internal utils
    # .....................................................................

    def _is_linked(self, patient: Patient) -> bool:  # noqa: D401 – imperative
        return any(
            lr.patient == patient and lr.specialist == self.specialist and lr.state == LinkRequestState.ACCEPTED
            for lr in self._link_store
        )
