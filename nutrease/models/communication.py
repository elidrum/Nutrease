from __future__ import annotations

"""Patient–Specialist communication & linking domain objects.

UML reference: *Communication* package – models Message, Connection, LinkRequest.

Implemented behaviours
----------------------
* ``LinkRequest.accept()`` → changes state to **ACCEPTED**, returns a
  :class:`Connection` instance.
* ``LinkRequest.reject()`` → changes state to **REJECTED**, returns ``None``.
* ``Connection.send_message()`` → creates a :class:`Message`, appends to the
  connection log and returns it.
"""

from datetime import datetime
from typing import List

from pydantic.dataclasses import dataclass

from .enums import LinkRequestState
from .user import Patient, Specialist, User

__all__ = ["Message", "Connection", "LinkRequest"]


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
# Connection (established link)
# ---------------------------------------------------------------------------

@dataclass
class Connection:
    """Established link between a *patient* and a *specialist*."""

    patient: Patient
    specialist: Specialist
    created_at: datetime = datetime.now()
    messages: List[Message] = None  # type: ignore[assignment]

    def __post_init__(self):  # noqa: D401 – imperative
        if self.messages is None:
            self.messages = []

    # .....................................................................
    # Messaging
    # .....................................................................

    def send_message(self, sender: User, text: str) -> Message:  # noqa: D401 – imperative
        """Send a chat *text* if *sender* belongs to this connection."""
        if sender not in {self.patient, self.specialist}:
            raise PermissionError("Mittente non autorizzato in questa connessione.")
        receiver: User = self.specialist if sender is self.patient else self.patient
        msg = Message(sender=sender, receiver=receiver, text=text, sent_at=datetime.now())
        self.messages.append(msg)
        return msg


# ---------------------------------------------------------------------------
# LinkRequest
# ---------------------------------------------------------------------------

@dataclass
class LinkRequest:
    """Link workflow between patient and specialist before *Connection*."""

    patient: Patient
    specialist: Specialist
    id: int = 0
    state: LinkRequestState = LinkRequestState.PENDING
    comment: str = ""
    requested_at: datetime = datetime.now()
    responded_at: datetime | None = None

    # .....................................................................
    # Workflow actions
    # .....................................................................

    def accept(self) -> Connection:  # noqa: D401 – imperative
        if self.state != LinkRequestState.PENDING:
            raise RuntimeError("La richiesta è già stata processata.")
        self.state = LinkRequestState.ACCEPTED
        self.responded_at = datetime.now()
        return Connection(patient=self.patient, specialist=self.specialist)

    def reject(self) -> None:  # noqa: D401 – imperative
        if self.state != LinkRequestState.PENDING:
            raise RuntimeError("La richiesta è già stata processata.")
        self.state = LinkRequestState.REJECTED
        self.responded_at = datetime.now()
