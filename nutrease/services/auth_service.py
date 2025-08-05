from __future__ import annotations

"""Simple in‑memory authentication service.

This is a *placeholder* implementation that satisfies basic requirements until
a real DB‑backed repository is wired.

Functions / methods implemented
-------------------------------
* :meth:`AuthService.signup` – create user account (Patient/Specialist) ensuring
  e‑mail uniqueness via :class:`nutrease.models.user.EmailRegistry`.
* :meth:`AuthService.login` – return user instance after credential check.
* :meth:`AuthService.password_reset` – mock reset that prints a reset token to
  console (later to be replaced with an e‑mail workflow via AWS SES or similar).
"""

from datetime import datetime
from secrets import token_urlsafe
from typing import Dict, Literal, Protocol

from pydantic import EmailStr

from nutrease.models.enums import SpecialistCategory
from nutrease.models.user import EmailRegistry, Patient, Specialist, User

# Typing --------------------------------------------------------------------
Role = Literal["PATIENT", "SPECIALIST"]


class UserRepository(Protocol):  # pragma: no cover – minimal protocol
    """Dependency inversion boundary for persistence (DB vs in‑memory)."""

    def add(self, user: User) -> None: ...  # noqa: D401 – ellipsis stub

    def get(self, email: str) -> User | None: ...  # noqa: D401 – stub


# ---------------------------------------------------------------------------
# In‑memory fallback repository (until DB layer is ready)
# ---------------------------------------------------------------------------

class _InMemoryUserRepo:  # noqa: D101 – internal helper
    def __init__(self):
        self._store: Dict[str, User] = {}

    # Protocol compliance ---------------------------------------------------
    def add(self, user: User) -> None:  # noqa: D401 – imperative
        self._store[user.email.lower()] = user

    def get(self, email: str) -> User | None:  # noqa: D401 – imperative
        return self._store.get(email.lower())


# ---------------------------------------------------------------------------
# AuthService
# ---------------------------------------------------------------------------

class AuthService:  # noqa: D101 – documented in module docstring
    def __init__(self, *, repo: UserRepository | None = None):
        self._repo: UserRepository = repo or _InMemoryUserRepo()

    # .....................................................................
    # API – signup/login/password‑reset
    # .....................................................................

    def signup(
        self,
        email: EmailStr,
        password: str,
        *,
        role: Role = "PATIENT",
        name: str = "",
        surname: str = "",
        category: SpecialistCategory | None = None,
    ) -> User:
        """Register a new user and return the created instance.

        Parameters
        ----------
        role
            ``"PATIENT"`` (default) or ``"SPECIALIST"``.
        category
            Required when *role* is ``SPECIALIST``.
        """
        if self._repo.get(str(email)) is not None:
            raise ValueError("E‑mail già registrata.")

        if role.upper() == "PATIENT":
            user = Patient(email=email, password=password, name=name, surname=surname)  # type: ignore[arg-type]
        elif role.upper() == "SPECIALIST":
            if category is None:
                raise ValueError("category richiesto per SPECIALIST.")
            user = Specialist(
                email=email,
                password=password,
                name=name,
                surname=surname,
                category=category,
            )  # type: ignore[arg-type]
        else:
            raise ValueError("Ruolo non valido: scegliere PATIENT o SPECIALIST.")

        # EmailRegistry.register is implicitly called inside model.__post_init__
        self._repo.add(user)
        return user

    # .....................................................................

    def login(self, email: EmailStr, password: str) -> User:  # noqa: D401 – imperative
        user = self._repo.get(str(email))
        if user is None or user.password != password:
            raise PermissionError("Credenziali non valide.")
        return user

    # .....................................................................

    def password_reset(self, email: EmailStr) -> str:  # noqa: D401 – imperative
        user = self._repo.get(str(email))
        if user is None:
            raise KeyError("Utente non trovato.")
        token = token_urlsafe(16)
        print(
            f"[MOCK‑EMAIL] {datetime.now():%Y‑%m‑%d %H:%M:%S} → {email}: "
            f"Per reimpostare la password usa il token: {token}"
        )
        return token
