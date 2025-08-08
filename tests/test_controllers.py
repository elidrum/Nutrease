from datetime import datetime

import pytest

from nutrease.controllers.patient_controller import PatientController
from nutrease.controllers.specialist_controller import SpecialistController
from nutrease.utils.database import Database
from nutrease.models.user import Patient, Specialist
from nutrease.models.record import FoodPortion, MealRecord, RecordType
from nutrease.models.enums import Unit, Nutrient, SpecialistCategory
from nutrease.models.communication import LinkRequest, LinkRequestState


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


def test_link_requests_returns_only_for_specialist():
    spec_a = Specialist(
        email="s1@test.com",
        password="Password1",
        name="S",
        surname="One",
        category=SpecialistCategory.DIETICIAN,
    )
    spec_b = Specialist(
        email="s2@test.com",
        password="Password1",
        name="S",
        surname="Two",
        category=SpecialistCategory.NUTRITIONIST,
    )
    pat1 = Patient(email="p1@test.com", password="Password1", name="P1", surname="A")
    pat2 = Patient(email="p2@test.com", password="Password1", name="P2", surname="B")
    lr1 = LinkRequest(patient=pat1, specialist=spec_a, state=LinkRequestState.ACCEPTED)
    lr2 = LinkRequest(patient=pat2, specialist=spec_a)
    lr3 = LinkRequest(patient=pat1, specialist=spec_b)
    sc = SpecialistController(spec_a, link_store=[lr1, lr2, lr3])

    res = sc.link_requests()
    assert res == [lr1, lr2]
    linked = [lr.patient for lr in res if lr.state == LinkRequestState.ACCEPTED]
    assert linked == [pat1]