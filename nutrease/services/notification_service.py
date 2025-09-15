from __future__ import annotations

"""Very light notification scheduler based on :class:`AlarmConfig`.

This implementation uses :pymod:`threading` to avoid blocking the main UI
thread. In a production Streamlit setup we might switch to *asyncio* tasks or
an external background worker.

The service currently **logs to console** (\U0001F514), but each notification
is returned via the *callback* hook so that UI layers (e.g. Streamlit) can
convert it to ``st.toast`` or any other visual.
"""

import logging
import threading
import time
from datetime import datetime
from typing import Callable, List

from nutrease.models.user import Patient

logger = logging.getLogger(__name__)

__all__ = ["NotificationService"]

# ---------------------------------------------------------------------------
# NotificationService
# ---------------------------------------------------------------------------


class NotificationService:  # noqa: D101 – documented in module docstring
    POLL_SECONDS = 30  # how often to check alarms

    def __init__(self, *, callback: Callable[[Patient, datetime], None] | None = None):
        self._patients: List[Patient] = []
        self._callback = callback or self._default_callback
        self._stop_evt = threading.Event()
        self._thread: threading.Thread | None = None

    # .....................................................................
    # Public API
    # .....................................................................

    def register_patient(self, patient: Patient) -> None:  # noqa: D401 – imperative
        if patient not in self._patients:
            self._patients.append(patient)

    def start(self) -> None:  # noqa: D401 – imperative
        if self._thread and self._thread.is_alive():
            return  # already running
        self._stop_evt.clear()
        self._thread = threading.Thread(target=self._run, name="AlarmScheduler", daemon=True)
        self._thread.start()

    def stop(self) -> None:  # noqa: D401 – imperative
        self._stop_evt.set()
        if self._thread:
            self._thread.join(timeout=self.POLL_SECONDS)

    # .....................................................................
    # Internal loop
    # .....................................................................

    def _run(self) -> None:  # noqa: D401 – imperative
        logger.info("Alarm scheduler avviato (%d pazienti in watch)", len(self._patients))
        while not self._stop_evt.is_set():
            now = datetime.now()
            for patient in self._patients:
                alarms = getattr(patient, "alarms", [])
                for alarm in alarms:
                    if not alarm.enabled:
                        continue
                    next_time = alarm.next_activation(now=now)
                    if (
                        next_time
                        and 0 <= (next_time - now).total_seconds() < self.POLL_SECONDS
                    ):
                        self._notify(patient, next_time)
            time.sleep(self.POLL_SECONDS)
        logger.info("Alarm scheduler terminato.")

    # .....................................................................
    # Helpers
    # .....................................................................

    def _notify(self, patient: Patient, when: datetime) -> None:  # noqa: D401 – imperative
        logger.info("\U0001F514  Promemoria per %s (%s)", patient.email, when.time())
        try:
            self._callback(patient, when)
        except Exception as exc:  # pragma: no cover – defensive log
            logger.exception("Callback notification fallita: %s", exc)

    @staticmethod
    def _default_callback(
        patient: Patient, when: datetime
    ) -> None:  # noqa: D401 – imperative
        # For now simply print:
        print(
            f"\U0001F514  {when:%H:%M} – Ricorda di compilare il diario, "
            f"{patient.name}!"
        )

    # ------------------------------------------------------------------
    # Pickle support (required by Streamlit session state)
    # ------------------------------------------------------------------

    def __getstate__(self) -> dict:  # noqa: D401 - internal helper
        """Return state excluding thread primitives for :mod:`pickle`.

        The running thread and synchronisation primitives cannot be pickled.
        We keep the registered patients and callback so controllers containing
        this service can be safely stored inside ``st.session_state``.
        """

        return {"patients": self._patients, "callback": self._callback}

    def __setstate__(self, state: dict) -> None:  # noqa: D401
        self._patients = state.get("patients", [])
        self._callback = state.get("callback", self._default_callback)
        self._stop_evt = threading.Event()
        self._thread = None