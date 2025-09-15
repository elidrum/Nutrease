from __future__ import annotations

from datetime import datetime
from typing import List

from nutrease.models.communication import Message
from nutrease.models.user import User
from nutrease.utils.database import Database


class MessagingController:
    """Gestisce la chat tra paziente e specialista.

    Se viene passato un `Database`, i messaggi sono salvati su TinyDB;
    altrimenti restano in memoria (comodo per i test).
    """

    def __init__(self, *, db: Database | None = None) -> None:
        self._db: Database | None = db
        # fallback in-memory quando db è None
        self._store: list[Message] = []

    # ------------------------------------------------------------------ #
    # Core API                                                           #
    # ------------------------------------------------------------------ #

    def send(self, *, sender: User, receiver: User, text: str) -> Message:
        """Crea e salva un nuovo messaggio."""
        msg = Message(
            sender=sender,
            receiver=receiver,
            text=text,
            sent_at=datetime.now(),
        )
        if self._db:
            self._db.save(msg) # persiste
        else:
            self._store.append(msg) # fallback
        return msg

    def conversation(self, u1: User, u2: User) -> List[Message]:
        """Ritorna tutti i messaggi (ordinati) scambiati tra *u1* e *u2*."""
        if self._db:
            raw = self._db.search(
                Message,
                sender=u1.email,
                receiver=u2.email,
            ) + self._db.search(
                Message,
                sender=u2.email,
                receiver=u1.email,
            )
            conv = [
                Message(
                    sender=u1 if d["sender"] == u1.email else u2,
                    receiver=u2 if d["receiver"] == u2.email else u1,
                    text=d["text"],
                     sent_at=(
                        datetime.fromisoformat(d["sent_at"])
                        if isinstance(d["sent_at"], str)
                        else d["sent_at"]
                    ),
                )
                for d in raw
            ]
        else:
            conv = [m for m in self._store if {m.sender, m.receiver} == {u1, u2}]
        return sorted(conv, key=lambda m: m.sent_at)