from __future__ import annotations

"""Messaging controller: simple patient‑specialist chat storage."""

import logging
from datetime import datetime
from typing import List

from nutrease.models.communication import Message
from nutrease.models.user import User

logger = logging.getLogger(__name__)

__all__ = ["MessagingController"]


class MessagingController:  # noqa: D101 – documented above
    def __init__(self, *, store: List[Message] | None = None):
        self._store = store if store is not None else []

    # .....................................................................
    # Public API
    # .....................................................................

    def send(self, sender: User, receiver: User, text: str) -> Message:  # noqa: D401 – imperative
        msg = Message(sender=sender, receiver=receiver, text=text, sent_at=datetime.now())
        self._store.append(msg)
        logger.debug("Messaggio #%d inviato da %s a %s", id(msg), sender.email, receiver.email)
        return msg

    def conversation(self, u1: User, u2: User) -> List[Message]:  # noqa: D401 – imperative
        """Return chronologically ordered conversation between *u1* and *u2*."""
        conv = [m for m in self._store if {m.sender, m.receiver} == {u1, u2}]
        conv.sort(key=lambda m: m.sent_at)
        return conv
