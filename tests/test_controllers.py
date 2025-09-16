from datetime import date, datetime, time

import pytest

"""Test end-to-end dei controller di Nutrease: verificano registrazione dei pasti,
persistenza dei diari, gestione delle richieste di collegamento e messaggistica
tra pazienti e specialisti."""


from nutrease.controllers.messaging_controller import MessagingController
from nutrease.controllers.patient_controller import PatientController
from nutrease.controllers.specialist_controller import SpecialistController
from nutrease.models.enums import LinkRequestState, Nutrient, SpecialistCategory, Unit
from nutrease.models.record import FoodPortion, MealRecord, RecordType
from nutrease.models.user import Patient, Specialist
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


def test_add_meal_duplicate_time(pc):
    today = date.today()
    pc.add_meal(
        day=today,
        when=time(8, 0),
        food_names=["Mela"],
        quantities=[1],
        units=[Unit.ITEM],
    )
    with pytest.raises(ValueError):
        pc.add_meal(
            day=today,
            when=time(8, 0),
            food_names=["Mela"],
            quantities=[1],
            units=[Unit.ITEM],
        )


def test_link_request_workflow_and_messaging(tmp_path):
    db = Database(tmp_path / "db.json")
    link_store = []

    patient = Patient(
        email="pat@test.com",
        password="Password1",
        name="Pa",
        surname="T",
    )
    specialist = Specialist(
        email="spec@test.com",
        password="Password1",
        name="Sp",
        surname="Ec",
        category=SpecialistCategory.DIETICIAN,
    )
    db.save(specialist)

    pc = PatientController(patient, db=db, link_store=link_store)
    sc = SpecialistController(specialist, db=db, link_store=link_store)

    lr = pc.send_link_request_by_email("spec@test.com", comment="hi")
    assert lr in sc.pending_requests()

    with pytest.raises(PermissionError):
        sc.get_patient_diary(patient, date.today())

    sc.accept_request(lr)
    assert lr.state is LinkRequestState.CONNECTED
    assert pc.connections() and sc.connections()

    msg1 = pc.send_message(specialist, "ciao")
    msg2 = sc.send_message(patient, "salve")
    assert pc.conversation(specialist) == [msg1, msg2]
    assert sc.conversation(patient) == [msg1, msg2]

    pc.add_meal(
        day=date.today(),
        when=time(9, 0),
        food_names=["Mela"],
        quantities=[1],
        units=[Unit.ITEM],
    )
    diary = sc.get_patient_diary(patient, date.today())
    assert diary is not None and diary.records
    assert sc.nutrient_total(patient, date.today(), Nutrient.SORBITOL) >= 0


def test_messaging_controller(tmp_path):
    db = Database(tmp_path / "msg.json")
    pat = Patient(email="p@test.com", password="Password1", name="P", surname="T")
    spec = Specialist(
        email="s@test.com",
        password="Password1",
        name="S",
        surname="T",
        category=SpecialistCategory.DIETICIAN,
    )

    mc_mem = MessagingController()
    mc_mem.send(sender=pat, receiver=spec, text="hey")
    mc_mem.send(sender=spec, receiver=pat, text="hi")
    assert [m.text for m in mc_mem.conversation(pat, spec)] == ["hey", "hi"]

    mc_db = MessagingController(db=db)
    mc_db.send(sender=pat, receiver=spec, text="db1")
    mc_db.send(sender=spec, receiver=pat, text="db2")
    mc_db2 = MessagingController(db=db)
    assert [m.text for m in mc_db2.conversation(pat, spec)] == ["db1", "db2"]