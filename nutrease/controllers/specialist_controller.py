from __future__ import annotations

"""Specialist‑side application controller (manage patients & link requests)."""

import logging
from datetime import date
from typing import List

from nutrease.models.communication import LinkRequest, LinkRequestState
from nutrease.models.diary import DailyDiary
from nutrease.models.enums import Nutrient
from nutrease.models.user import Patient, Specialist
from nutrease.utils.database import Database

logger = logging.getLogger(__name__)

__all__ = ["SpecialistController"]

# Re‑use shared list defined in patient_controller to keep state in‑sync.
# If patient_controller hasn't been imported yet (e.g. specialist logs in first),
# load existing link requests from DB so connections persist across sessions.
try:  # defensive import
    from nutrease.controllers.patient_controller import _LINK_REQUESTS as _GLOBAL_LR
    from nutrease.controllers.patient_controller import _load_link_requests_from_db
except ModuleNotFoundError:
    _GLOBAL_LR: List[LinkRequest] = []

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
                _GLOBAL_LR.append(lr)
            except Exception:
                logger.debug("LinkRequest non valida nel DB", exc_info=True)


if not _GLOBAL_LR:
    _load_link_requests_from_db()

class SpecialistController:  # noqa: D101 – documented above
    def __init__(
        self,
        specialist: Specialist,
        *,
        db: Database | None = None,
        link_store: List[LinkRequest] | None = None,
    ) -> None:
        self.specialist = specialist
        self._db = db if db is not None else Database.default()    

        self._link_store = link_store if link_store is not None else _GLOBAL_LR

    # .....................................................................
    # Link‑request workflow
    # .....................................................................
    def _iter_link_requests(self):
        """Generator su tutte le ``LinkRequest`` verso questo specialista."""
        yield from self.link_requests()

    def link_requests(self) -> List[LinkRequest]:  # noqa: D401 – imperative
        """Restituisce tutte le ``LinkRequest`` per questo specialista."""
        return [lr for lr in self._link_store if lr.specialist == self.specialist]

    def pending_requests(self) -> List[LinkRequest]:  # noqa: D401 – imperative
        return [lr for lr in self._link_store if lr.specialist == self.specialist and lr.state == LinkRequestState.PENDING]

    def accept_request(self, lr: LinkRequest) -> None:  # noqa: D401 – imperative
        if lr not in self._link_store or lr.specialist != self.specialist:
            raise ValueError("Richiesta non gestita da questo specialista.")
        lr.accept()
        try:
            self._db.save(lr)
        except Exception:  # pragma: no cover - best effort
            logger.debug("Persistenza LinkRequest non riuscita", exc_info=True)
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

    # ------------------------------------------------------------------
    def remove_link(self, patient: Patient) -> None:  # noqa: D401 – imperative
        """Remove an accepted link with *patient* (both memory and DB)."""
        lr = next(
            (
                lr
                for lr in self._link_store
                if lr.specialist == self.specialist
                and lr.patient == patient
                and lr.state == LinkRequestState.ACCEPTED
            ),
            None,
        )
        if not lr:
            raise ValueError("Paziente non collegato")
        self._link_store.remove(lr)
        try:
            self._db.delete(lr)
        except Exception:  # pragma: no cover - best effort
            logger.debug("Rimozione LinkRequest non riuscita", exc_info=True)
