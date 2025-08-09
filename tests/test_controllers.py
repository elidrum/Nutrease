from datetime import date, datetime, time

import pytest

from nutrease.controllers.patient_controller import PatientController
from nutrease.models.enums import Nutrient, Unit
from nutrease.models.record import FoodPortion, MealRecord, RecordType
from nutrease.models.user import Patient
from nutrease.services.auth_service import AuthService
from nutrease.utils.database import Database


@pytest.fixture(autouse=True)
def clear_db():
    Database.default().clear()
    yield
    Database.default().clear()


@pytest.fixture
def pc() -> PatientController:
    pat = Patient(email="pc@test.com", password="Password1", name="P", surname="C")
    return PatientController(pat, db=Database.default())


def test_add_record_and_total(pc):
    portion = FoodPortion(food_name="Mela", quantity=2, unit=Unit.ITEM)
    meal = MealRecord(record_type=RecordType.MEAL, portions=[portion], note=None)
    pc.add_record(meal)

    today = datetime.today().date()
    assert pc.get_diary(today) is not None
    total = pc.nutrient_total(today, Nutrient.SORBITOL)
    assert total >= 0


def test_record_persistence(tmp_path):
    db = Database(tmp_path / "db.json")
    auth = AuthService(db=db)
    pat = auth.signup(
        email="persist@test.com",
        password="Password1",
        name="P",
        surname="T",
    )
    pc = PatientController(pat, db=db)
    pc.add_meal(
        day=date.today(),
        when=time(8, 0),
        food_names=["Mela"],
        quantities=[1],
        units=[Unit.ITEM],
    )

    loaded = auth.login("persist@test.com", "Password1")
    assert isinstance(loaded, Patient)
    assert loaded.diaries and loaded.diaries[0].records