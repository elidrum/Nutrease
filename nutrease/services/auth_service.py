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

from nutrease.models.communication import LinkRequest, Message
from nutrease.models.enums import SpecialistCategory
from nutrease.models.record import MealRecord, SymptomRecord
from nutrease.models.user import Patient, Specialist, User, _validate_password
from nutrease.utils.database import Database

# ---------------------------------------------------------------------------
# Typing & helper
# ---------------------------------------------------------------------------

Role = Literal["PATIENT", "SPECIALIST"]

# Usa un `TypeAdapter` per validare le e-mail senza dover istanziare
# direttamente ``EmailStr`` (in Pydantic v2 non è più chiamabile).
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
        # Usiamo il TypeAdapter condiviso per normalizzare e validare
        # l'indirizzo e-mail, forzando anche il lowercase
        # per garantire unicità.
        user.email = _email_adapter.validate_python(self._key(str(user.email)))
        # Se esiste già un utente con la stessa e-mail, solleva errore
        # senza modificare il database.
        if self._db.search(Patient, email=user.email) or self._db.search(
            Specialist, email=user.email
        ):
            raise ValueError("E-mail già registrata.")
        self._db.save(user)

    def get(self, email: str) -> User | None:  # noqa: D401
        rows = self._db.search(Patient, email=self._key(email))
        is_patient = True
        if not rows:
            rows = self._db.search(Specialist, email=self._key(email))
            is_patient = False
        if not rows:
            return None
        data = rows[0]
        filtered = {k: v for k, v in data.items() if not k.startswith("__")}
        cls: Type[User] = Patient if is_patient else Specialist
        if is_patient:
            patient: Patient = cls(**filtered)  # type: ignore[call-arg]
            self._populate_diaries(patient)
            return patient
        return cls(**filtered)

    def _populate_diaries(self, patient: Patient) -> None:
        from collections import defaultdict
        from datetime import datetime

        from nutrease.models.diary import DailyDiary, Day
        from nutrease.models.enums import Nutrient, Severity, Unit


        from nutrease.models.record import (  # isort: skip
            FoodPortion,
            MealRecord,
            SymptomRecord,
            NutrientIntake,
        )

        rows = self._db.search(MealRecord, patient_email=patient.email)
        rows += self._db.search(SymptomRecord, patient_email=patient.email)
        records_by_day = defaultdict(list)
        for row in rows:
            created_at = datetime.fromisoformat(row["created_at"])
            if row["__type__"] == "MealRecord":
                portions = []
                for p in row.get("portions", []):
                    nutrients = [
                        NutrientIntake(
                            nutrient=Nutrient(np["nutrient"]),
                            grams=np["grams"],
                        )
                        for np in p.get("nutrients", [])
                    ]
                    portions.append(
                        FoodPortion(
                            food_name=p["food_name"],
                            quantity=p["quantity"],
                            unit=Unit(p["unit"]),
                            nutrients=nutrients,
                        )
                    )
                rec = MealRecord(
                    id=row["id"],
                    created_at=created_at,
                    portions=portions,
                    note=row.get("note"),
                )
            else:
                rec = SymptomRecord(
                    id=row["id"],
                    created_at=created_at,
                    symptom=row.get("symptom", ""),
                    severity=Severity(row.get("severity", "NONE")),
                    note=row.get("note"),
                )
            records_by_day[created_at.date()].append(rec)

        for d, recs in records_by_day.items():
            diary = DailyDiary(day=Day(date=d), patient=patient, records=recs)
            patient.diaries.append(diary)

# ---------------------------------------------------------------------------
# Repo in-memory (fallback)
# ---------------------------------------------------------------------------


class _InMemoryUserRepo:  # noqa: D101
    def __init__(self):
        self._store: Dict[str, User] = {}

    def add(self, user: User) -> None:  # noqa: D401
        key = user.email.lower()
        if key in self._store:
            raise ValueError("E-mail già registrata.")
        self._store[key] = user

    def get(self, email: str) -> User | None:  # noqa: D401
        return self._store.get(email.lower())


# ---------------------------------------------------------------------------
# AuthService
# ---------------------------------------------------------------------------


class AuthService:
    """Gestisce signup / login; default persistente su TinyDB."""

    def __init__(
        self, *, db: Database | None = None, repo: UserRepository | None = None
    ):
        if repo:  # test injection esplicita
            self._repo = repo
            self._db = db or getattr(repo, "_db", None)
        else:
            self._db = db or Database.default()
            self._repo = _DBUserRepo(self._db)

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

    # ---------------------- change password ----------------------------
    def change_password(
        self, email: str, old_password: str, new_password: str
    ) -> None:  # noqa: D401
        email = _email_adapter.validate_python(email)
        user = self._repo.get(email)
        if user is None or user.password != _hash(old_password):
            raise PermissionError("Credenziali non valide.")
        _validate_password(new_password)
        user.password = _hash(new_password)
        db = getattr(self, "_db", None)
        if db is not None:
            db.delete(type(user), email=email)
            db.save(user)

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

    # ---------------------- delete account ----------------------------
    def delete_account(self, email: str) -> None:  # noqa: D401
        email = _email_adapter.validate_python(email)
        user = self._repo.get(email)
        if user is None:
            raise KeyError("Utente non trovato.")
        db = getattr(self, "_db", None)
        if db is not None:
            db.delete(type(user), email=email)
            db.delete(Message, sender=email)
            db.delete(Message, receiver=email)
            db.delete(MealRecord, patient_email=email)
            db.delete(SymptomRecord, patient_email=email)
            for row in db.all(LinkRequest):
                patient = row.get("patient")
                specialist = row.get("specialist")
                p_email = patient.get("email") if isinstance(patient, dict) else None
                s_email = (
                    specialist.get("email") if isinstance(specialist, dict) else None
                )
                if p_email == email or s_email == email:
                    db.delete(LinkRequest, id=row.get("id", 0))
        if hasattr(self._repo, "_store"):
            self._repo._store.pop(email.lower(), None)
        try:
            from nutrease.controllers.patient_controller import _LINK_REQUESTS

            _LINK_REQUESTS[:] = [
                lr
                for lr in _LINK_REQUESTS
                if lr.patient.email != email and lr.specialist.email != email
            ]
        except Exception:  # pragma: no cover - best effort
            pass