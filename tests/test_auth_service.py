import pytest

from nutrease.models.communication import LinkRequest, Message
from nutrease.models.enums import SpecialistCategory
from nutrease.models.record import MealRecord, SymptomRecord
from nutrease.models.user import Patient, Specialist
from nutrease.services.auth_service import AuthService
from nutrease.utils.database import Database


def test_signup_and_login_patient(tmp_path):
    db = Database(tmp_path / "db.json")
    auth = AuthService(db=db)
    auth.signup(
        email="p@example.com",
        password="Password1",
        name="Pat",
        surname="Ient",
    )
    user = auth.login("p@example.com", "Password1")
    assert isinstance(user, Patient)


def test_signup_duplicate_email(tmp_path):
    db = Database(tmp_path / "db.json")
    auth = AuthService(db=db)
    auth.signup(
        email="s@example.com",
        password="Password1",
        role="SPECIALIST",
        name="Spec",
        surname="Ialist",
        category=SpecialistCategory.DIETICIAN,
    )
    with pytest.raises(ValueError):
        auth.signup(
            email="s@example.com",
            password="Password1",
            role="SPECIALIST",
            name="Spec",
            surname="Ialist",
            category=SpecialistCategory.DIETICIAN,
        )


def test_login_wrong_password(tmp_path):
    db = Database(tmp_path / "db.json")
    auth = AuthService(db=db)
    auth.signup(
        email="p2@example.com",
        password="Password1",
        name="Pa",
        surname="Tie",
    )
    with pytest.raises(PermissionError):
        auth.login("p2@example.com", "WrongPass1")


def test_signup_rejects_weak_password(tmp_path):
    db = Database(tmp_path / "db.json")
    auth = AuthService(db=db)
    with pytest.raises(ValueError):
        auth.signup(
            email="weak@example.com",
            password="password",  # manca maiuscola e numero
            name="Weak",
            surname="User",
        )


@pytest.mark.parametrize(
    "name,surname",
    [
        ("", "Valid"),
        ("   ", "Valid"),
        ("\u200b", "Valid"),
        ("Valid", ""),
        ("Valid", "   "),
        ("Valid", "\u200b"),
    ],
)
def test_signup_requires_name_and_surname(tmp_path, name, surname):
    db = Database(tmp_path / "db.json")
    auth = AuthService(db=db)
    with pytest.raises(ValueError):
        auth.signup(
            email="missing@example.com",
            password="Password1",
            name=name,
            surname=surname,
        )


def test_signup_strips_name_and_surname(tmp_path):
    db = Database(tmp_path / "db.json")
    auth = AuthService(db=db)
    user = auth.signup(
        email="trim@example.com",
        password="Password1",
        name="  Mario  ",
        surname="  Rossi  ",
    )
    assert user.name == "Mario"
    assert user.surname == "Rossi"


def test_change_password_allows_new_login_and_blocks_old(tmp_path):
    db = Database(tmp_path / "db.json")
    auth = AuthService(db=db)
    auth.signup(
        email="changeme@example.com",
        password="Password1",
        name="Change",
        surname="Me",
    )

    auth.change_password("changeme@example.com", "Password1", "NewPass1")

    user = auth.login("changeme@example.com", "NewPass1")
    assert isinstance(user, Patient)

    with pytest.raises(PermissionError):
        auth.login("changeme@example.com", "Password1")


def test_password_reset_returns_token_and_missing_email(tmp_path):
    db = Database(tmp_path / "db.json")
    auth = AuthService(db=db)
    auth.signup(
        email="reset@example.com",
        password="Password1",
        name="Re",
        surname="Set",
    )

    token = auth.password_reset("reset@example.com")
    assert isinstance(token, str)
    assert token

    with pytest.raises(KeyError):
        auth.password_reset("absent@example.com")


def test_delete_account_removes_related_records(tmp_path):
    db = Database(tmp_path / "db.json")
    auth = AuthService(db=db)
    patient = auth.signup(
        email="patient@example.com",
        password="Password1",
        name="Pat",
        surname="Ient",
    )
    specialist = auth.signup(
        email="spec@example.com",
        password="Password1",
        role="SPECIALIST",
        name="Spec",
        surname="Ialist",
        category=SpecialistCategory.DIETICIAN,
    )
    assert isinstance(patient, Patient)
    assert isinstance(specialist, Specialist)

    link = LinkRequest(patient=patient, specialist=specialist, comment="hi")
    db.save(link)

    msg_to_specialist = Message(
        sender=patient,
        receiver=specialist,
        text="hello",
    )
    msg_to_patient = Message(
        sender=specialist,
        receiver=patient,
        text="hi",
    )
    db.save(msg_to_specialist)
    db.save(msg_to_patient)

    meal = MealRecord()
    symptom = SymptomRecord(symptom="Nausea")
    object.__setattr__(meal, "patient_email", patient.email)
    object.__setattr__(symptom, "patient_email", patient.email)
    db.save(meal)
    db.save(symptom)

    auth.delete_account("patient@example.com")

    assert db.search(Patient, email="patient@example.com") == []
    assert db.search(Message, sender="patient@example.com") == []
    assert db.search(Message, receiver="patient@example.com") == []
    assert db.search(MealRecord, patient_email="patient@example.com") == []
    assert db.search(SymptomRecord, patient_email="patient@example.com") == []
    assert all(
        row.get("patient", {}).get("email") != "patient@example.com"
        and row.get("specialist", {}).get("email") != "patient@example.com"
        for row in db.all(LinkRequest)
    )
    assert db.search(Specialist, email="spec@example.com")