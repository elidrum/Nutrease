from __future__ import annotations

"""
Comunicazione Paziente–Specialista e collegamento degli oggetti di dominio.

Riferimento UML: pacchetto *Communication* – modella Message e LinkRequest.

Comportamenti implementati
--------------------------
* ``LinkRequest.accept()`` -> cambia lo stato in **CONNECTED**.
* ``LinkRequest.reject()`` -> cambia lo stato in **REJECTED**.
* ``LinkRequest.send_message()`` -> crea un :class:`Message`, lo aggiunge al
  registro della conversazione e lo restituisce quando il collegamento è attivo.
"""

from dataclasses import asdict, dataclass
from datetime import datetime
from typing import List

from nutrease.utils.tz import local_now

from .enums import LinkRequestState
from .user import Patient, Specialist, User

__all__ = ["Message", "LinkRequest"]


@dataclass(init=False)
class Message:
    """Singolo messaggio tra due utenti"""

    _sender: User
    _receiver: User
    _text: str
    _sent_at: datetime

    def __init__(
        self,
        *,
        sender: User,
        receiver: User,
        text: str,
        sent_at: datetime | None = None,
    ) -> None:
        self._sender = sender
        self._receiver = receiver
        self._text = text
        self._sent_at = sent_at if sent_at is not None else local_now()

    @property
    def sender(self) -> User:  # noqa: D401
        """Return the message sender."""
        return self._sender

    @property
    def receiver(self) -> User:  # noqa: D401
        """Return the message receiver."""
        return self._receiver

    @property
    def text(self) -> str:  # noqa: D401
        """Return the message text."""
        return self._text

    @property
    def sent_at(self) -> datetime:  # noqa: D401
        """Return the datetime the message was sent."""
        return self._sent_at

    def __repr__(self) -> str:  # noqa: D401 – imperative
        direction = f"{self.sender.email} → {self.receiver.email}"
        return f"<Message {direction} @ {self.sent_at:%H:%M}>"

    def as_dict(self) -> dict:  # noqa: D401
        """Serialise the message for persistence."""
        return {
            "sender": self.sender.email,
            "receiver": self.receiver.email,
            "text": self.text,
            "sent_at": self.sent_at,
        }



@dataclass(init=False)
class LinkRequest:
    """Link workflow between patient and specialist (also active link)."""

    _patient: Patient
    _specialist: Specialist
    _id: int
    _state: LinkRequestState
    _comment: str
    _requested_at: datetime
    _responded_at: datetime | None
    _messages: List[Message]

    def __init__(
        self,
        *,
        patient: Patient,
        specialist: Specialist,
        id: int = 0,
        state: LinkRequestState = LinkRequestState.PENDING,
        comment: str = "",
        requested_at: datetime | None = None,
        responded_at: datetime | None = None,
        messages: List[Message] | None = None,
    ) -> None:
        self._patient = patient
        self._specialist = specialist
        self._id = id
        self._state = state
        self._comment = comment
        self._requested_at = requested_at if requested_at is not None else local_now()
        self._responded_at = responded_at
        self._messages = messages if messages is not None else []

    @property
    def patient(self) -> Patient:  # noqa: D401
        """Return the patient who initiated the request."""
        return self._patient

    @property
    def specialist(self) -> Specialist:  # noqa: D401
        """Return the specialist involved in the request."""
        return self._specialist

    @property
    def id(self) -> int:  # noqa: D401
        """Return the identifier of this request."""
        return self._id

    @id.setter
    def id(self, value: int) -> None:
        self._id = value

    @property
    def state(self) -> LinkRequestState:  # noqa: D401
        """Current state of the request."""
        return self._state

    @state.setter
    def state(self, value: LinkRequestState) -> None:
        self._state = value

    @property
    def comment(self) -> str:  # noqa: D401
        return self._comment

    @property
    def requested_at(self) -> datetime:  # noqa: D401
        return self._requested_at

    @property
    def responded_at(self) -> datetime | None:  # noqa: D401
        return self._responded_at

    @responded_at.setter
    def responded_at(self, value: datetime | None) -> None:
        self._responded_at = value

    @property
    def messages(self) -> List[Message]:  # noqa: D401
        return self._messages

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
            "specialist": (
                self.specialist.as_dict()
                if hasattr(self.specialist, "as_dict")
                else asdict(self.specialist)
            ),
            "id": self.id,
            "state": self.state,
            "comment": self.comment,
            "requested_at": self.requested_at,
            "responded_at": self.responded_at,
            "messages": [
                m.as_dict() if hasattr(m, "as_dict") else asdict(m)
                for m in self.messages
            ],
        }

    # .....................................................................
    # Workflow actions
    # .....................................................................

    def accept(self) -> None:  # noqa: D401 – imperative
        if self.state != LinkRequestState.PENDING:
            raise RuntimeError("La richiesta è già stata processata.")
        self.state = LinkRequestState.CONNECTED
        self.responded_at = local_now()

    def reject(self) -> None:  # noqa: D401 – imperative
        if self.state != LinkRequestState.PENDING:
            raise RuntimeError("La richiesta è già stata processata.")
        self.state = LinkRequestState.REJECTED
        self.responded_at = local_now()

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
        msg = Message(sender=sender, receiver=receiver, text=text, sent_at=local_now())
        self.messages.append(msg)
        return msg