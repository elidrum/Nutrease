import pytest

from nutrease.models.enums import SpecialistCategory
from nutrease.models.user import Patient
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
        ("Valid", ""),
        ("Valid", "   "),
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