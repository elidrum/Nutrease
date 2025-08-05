from datetime import datetime

import pytest

from nutrease.controllers.patient_controller import PatientController
from nutrease.utils.database import Database
from nutrease.models.user import Patient
from nutrease.models.record import FoodPortion, MealRecord, RecordType
from nutrease.models.enums import Unit, Nutrient


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