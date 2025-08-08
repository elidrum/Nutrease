from __future__ import annotations

"""Authentication service con persistenza TinyDB.

Caratteristiche
---------------
* **Signup**: crea Patient o Specialist, assicura unicità e-mail,
  salva su TinyDB (fallback in-memory per i test).
* **Login**: carica credenziali dal DB (o repo in-memory) e verifica hash.
* **Password reset (mock)**: stampa un token in console.
"""

from datetime import datetime
from hashlib import sha256
from secrets import token_urlsafe
from typing import Dict, Literal, Protocol, Type

from pydantic import EmailStr, TypeAdapter

from nutrease.models.enums import SpecialistCategory
from nutrease.models.user import Patient, Specialist, User
from nutrease.utils.database import Database

# ---------------------------------------------------------------------------
# Typing & helper
# ---------------------------------------------------------------------------

Role = Literal["PATIENT", "SPECIALIST"]

# Riutilizza un `TypeAdapter` per validare le e-mail senza dover istanziare
# direttamente ``EmailStr`` (in Pydantic v2 non è più chiamabile come funzione).
_email_adapter = TypeAdapter(EmailStr)


def _hash(raw_pw: str) -> str:
    """Hash elementare SHA-256; da sostituire con bcrypt in prod."""
    return sha256(raw_pw.encode()).hexdigest()

# ---------------------------------------------------------------------------
# Repository Protocol
# ---------------------------------------------------------------------------


class UserRepository(Protocol):  # pragma: no cover – interfaccia minima
    def add(self, user: User) -> None: ...  # noqa: D401 – stub

    def get(self, email: str) -> User | None: ...  # noqa: D401 – stub


# ---------------------------------------------------------------------------
# Repo TinyDB
# ---------------------------------------------------------------------------


class _DBUserRepo:  # noqa: D101 – interno
    def __init__(self, db: Database):
        self._db = db

    # ------------- helpers ----------------------------------------------
    def _key(self, email: str) -> str:
        return email.lower()

    def add(self, user: User) -> None:
        # ``EmailStr`` in Pydantic v2 non è più invocabile direttamente.
        # Usiamo il TypeAdapter condiviso per normalizzare e
        # validare l'indirizzo e-mail, forzando anche il lowercase
        # per garantire unicità.
        user.email = _email_adapter.validate_python(self._key(str(user.email)))
        self._db.save(user)

    def get(self, email: str) -> User | None:  # noqa: D401
        key = self._key(email)
        for cls in (Patient, Specialist):
            rows = self._db.search(cls, email=key)
            if rows:
                data = rows[0]
                filtered = dict(
                    (k, v) for k, v in data.items() if not k.startswith("__")
                )
                return cls(**filtered)
        return None


# ---------------------------------------------------------------------------
# Repo in-memory (fallback)
# ---------------------------------------------------------------------------


class _InMemoryUserRepo:  # noqa: D101
    def __init__(self):
        self._store: Dict[str, User] = {}

    def add(self, user: User) -> None:  # noqa: D401
        self._store[user.email.lower()] = user

    def get(self, email: str) -> User | None:  # noqa: D401
        return self._store.get(email.lower())


# ---------------------------------------------------------------------------
# AuthService
# ---------------------------------------------------------------------------


class AuthService:
    """Gestisce signup / login; default persistente su TinyDB."""

    def __init__(self, *, db: Database | None = None, repo: UserRepository | None = None):
        if repo:  # test injection esplicita
            self._repo = repo
        else:
            self._repo = _DBUserRepo(db or Database.default())

    # ---------------------- signup --------------------------------------
    def signup(
        self,
        email: str,
        password: str,
        *,
        role: Role = "PATIENT",
        name: str = "",
        surname: str = "",
        category: SpecialistCategory | None = None,
    ) -> User:
        """Registra un nuovo utente e lo restituisce."""
        email = _email_adapter.validate_python(email)
        if self._repo.get(email):
            raise ValueError("E-mail già registrata.")

        if role.upper() == "PATIENT":
            user = Patient(
                email=email,
                password=_hash(password),
                name=name,
                surname=surname,
            )  # type: ignore[arg-type]
        elif role.upper() == "SPECIALIST":
            if category is None:
                raise ValueError("category richiesto per SPECIALIST.")
            user = Specialist(
                email=email,
                password=_hash(password),
                name=name,
                surname=surname,
                category=category,
            )  # type: ignore[arg-type]
        else:
            raise ValueError("Ruolo non valido.")

        self._repo.add(user)
        return user

    # ---------------------- login ---------------------------------------
    def login(self, email: str, password: str) -> User:  # noqa: D401
        email = _email_adapter.validate_python(email)
        user = self._repo.get(email)
        if user is None or user.password != _hash(password):
            raise PermissionError("Credenziali non valide.")
        return user

    # ---------------------- password reset (mock) -----------------------
    def password_reset(self, email: str) -> str:  # noqa: D401
        email = _email_adapter.validate_python(email)
        user = self._repo.get(email)
        if user is None:
            raise KeyError("Utente non trovato.")
        token = token_urlsafe(16)
        print(
            f"[MOCK-EMAIL] {datetime.now():%Y-%m-%d %H:%M:%S} → {email}: "
            f"Per reimpostare la password usa il token: {token}"
        )
        return token