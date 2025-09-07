from __future__ import annotations

"""Patient–Specialist communication & linking domain objects.

UML reference: *Communication* package – models Message and LinkRequest.

Implemented behaviours
----------------------
* ``LinkRequest.accept()`` → changes state to **CONNECTED**.
* ``LinkRequest.reject()`` → changes state to **REJECTED**.
* ``LinkRequest.send_message()`` → creates a :class:`Message`, appends to the
  conversation log and returns it when the link is active.
"""

from dataclasses import asdict
from datetime import datetime
from typing import List

from pydantic.dataclasses import dataclass

from .enums import LinkRequestState
from .user import Patient, Specialist, User

__all__ = ["Message", "LinkRequest"]


# ---------------------------------------------------------------------------
# Message
# ---------------------------------------------------------------------------


@dataclass
class Message:
    """A single chat message between two users."""

    sender: User
    receiver: User
    text: str
    sent_at: datetime = datetime.now()

    def __repr__(self) -> str:  # noqa: D401 – imperative
        direction = f"{self.sender.email} → {self.receiver.email}"
        return f"<Message {direction} @ {self.sent_at:%H:%M}>"


# ---------------------------------------------------------------------------
# LinkRequest
# ---------------------------------------------------------------------------


@dataclass
class LinkRequest:
    """Link workflow between patient and specialist (also active link)."""

    patient: Patient
    specialist: Specialist
    id: int = 0
    state: LinkRequestState = LinkRequestState.PENDING
    comment: str = ""
    requested_at: datetime = datetime.now()
    responded_at: datetime | None = None
    messages: List[Message] | None = None

    def __post_init__(self):  # noqa: D401 – imperative
        if self.messages is None:
            self.messages = []

    # ..................................................................
    # Serialisation helpers
    # ..................................................................

    def as_dict(self) -> dict:  # noqa: D401 – JSON-friendly representation
        """Return a JSON-serialisable mapping including nested users."""
        return {
            "patient": (
                self.patient.as_dict()
                if hasattr(self.patient, "as_dict")
                else asdict(self.patient)
            ),
            "specialist": asdict(self.specialist),
            "id": self.id,
            "state": self.state,
            "comment": self.comment,
            "requested_at": self.requested_at,
            "responded_at": self.responded_at,
            "messages": [asdict(m) for m in self.messages],
        }

    # .....................................................................
    # Workflow actions
    # .....................................................................

    def accept(self) -> None:  # noqa: D401 – imperative
        if self.state != LinkRequestState.PENDING:
            raise RuntimeError("La richiesta è già stata processata.")
        self.state = LinkRequestState.CONNECTED
        self.responded_at = datetime.now()

    def reject(self) -> None:  # noqa: D401 – imperative
        if self.state != LinkRequestState.PENDING:
            raise RuntimeError("La richiesta è già stata processata.")
        self.state = LinkRequestState.REJECTED
        self.responded_at = datetime.now()

    # .....................................................................
    # Messaging
    # .....................................................................

    def send_message(self, sender: User, text: str) -> Message:  # noqa: D401
        """Send a chat *text* if link is active and *sender* belongs to it."""
        if self.state != LinkRequestState.CONNECTED:
            raise RuntimeError("Link non attivo.")
        if sender not in {self.patient, self.specialist}:
            raise PermissionError("Mittente non autorizzato in questa connessione.")
        receiver: User = self.specialist if sender is self.patient else self.patient
        msg = Message(sender=sender, receiver=receiver, text=text, sent_at=datetime.now())
        self.messages.append(msg)
        return msg