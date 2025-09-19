from datetime import date, datetime, time

import pytest

"""Test end-to-end dei controller di Nutrease: verificano registrazione dei pasti,
persistenza dei diari, gestione delle richieste di collegamento e messaggistica
tra pazienti e specialisti."""


from nutrease.controllers.messaging_controller import MessagingController
from nutrease.controllers.patient_controller import PatientController
from nutrease.controllers.specialist_controller import SpecialistController
from nutrease.models.communication import LinkRequest
from nutrease.models.enums import (
    LinkRequestState,
    Nutrient,
    Severity,
    SpecialistCategory,
    Unit,
)
from nutrease.models.record import FoodPortion, MealRecord, RecordType, SymptomRecord
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


def test_add_symptom_creates_record_and_persists(tmp_path):
    db = Database(tmp_path / "symptoms.json")
    patient = Patient(
        email="sym@test.com",
        password="Password1",
        name="Sym",
        surname="Tom",
    )
    pc = PatientController(patient, db=db)
    day = date(2023, 1, 2)
    when = time(14, 30)

    pc.add_symptom(day, "Nausea", Severity.MODERATE, when, note="after lunch")

    diary = pc.get_diary(day)
    assert diary is not None and len(diary.records) == 1
    record = diary.records[0]
    assert record.symptom == "Nausea"
    assert record.severity is Severity.MODERATE
    assert record.note == "after lunch"

    rows = db.search(SymptomRecord, patient_email=patient.email)
    assert rows
    stored = rows[0]
    assert stored["symptom"] == "Nausea"
    assert stored["severity"] == Severity.MODERATE.value
    assert stored["note"] == "after lunch"


def test_modify_meal_updates_diary_and_db(tmp_path):
    db = Database(tmp_path / "meals.json")
    patient = Patient(
        email="meal@test.com",
        password="Password1",
        name="Meal",
        surname="Case",
    )
    pc = PatientController(patient, db=db)
    day = date(2023, 3, 5)

    pc.add_meal(
        day=day,
        when=time(8, 0),
        food_names=["Mela"],
        quantities=[1],
        units=[Unit.ITEM],
        note="old",
    )
    meal_id = pc.get_diary(day).records[0].id

    pc.modify_meal(
        day=day,
        record_id=meal_id,
        food_names=["Pera"],
        quantities=[2],
        units=[Unit.ITEM],
        note="new",
    )

    diary = pc.get_diary(day)
    assert diary is not None
    meal = diary.records[0]
    assert meal.note == "new"
    assert meal.portions[0].food_name == "Pera"
    assert meal.portions[0].quantity == 2

    rows = db.search(MealRecord, id=meal_id)
    assert rows
    stored = rows[0]
    assert stored["note"] == "new"
    assert stored["portions"][0]["food_name"] == "Pera"
    assert stored["portions"][0]["quantity"] == 2


def test_modify_meal_raises_keyerror_when_record_missing(tmp_path):
    db = Database(tmp_path / "missing_meal.json")
    patient = Patient(
        email="meal-missing@test.com",
        password="Password1",
        name="Meal",
        surname="Missing",
    )
    pc = PatientController(patient, db=db)
    day = date(2023, 4, 6)

    pc.add_meal(
        day=day,
        when=time(7, 30),
        food_names=["Pane"],
        quantities=[1],
        units=[Unit.SLICE],
    )

    with pytest.raises(KeyError):
        pc.modify_meal(
            day=day,
            record_id=999,
            food_names=["Pane"],
            quantities=[2],
            units=[Unit.SLICE],
        )


def test_modify_symptom_updates_diary_and_db(tmp_path):
    db = Database(tmp_path / "symptom-update.json")
    patient = Patient(
        email="symptom@test.com",
        password="Password1",
        name="Sym",
        surname="Update",
    )
    pc = PatientController(patient, db=db)
    day = date(2023, 5, 7)

    pc.add_symptom(day, "Mal di testa", Severity.MILD, time(9, 0), note="old")
    record_id = pc.get_diary(day).records[0].id

    pc.modify_symptom(
        day=day,
        record_id=record_id,
        description="Emicrania",
        severity=Severity.SEVERE,
        when=time(10, 30),
        note="new",
    )

    diary = pc.get_diary(day)
    assert diary is not None
    record = diary.records[0]
    assert record.symptom == "Emicrania"
    assert record.severity is Severity.SEVERE
    assert record.note == "new"
    assert record.created_at.hour == 10
    assert record.created_at.minute == 30

    rows = db.search(SymptomRecord, id=record_id)
    assert rows
    stored = rows[0]
    assert stored["symptom"] == "Emicrania"
    assert stored["severity"] == Severity.SEVERE.value
    assert stored["note"] == "new"
    assert datetime.fromisoformat(stored["created_at"]).hour == 10
    assert datetime.fromisoformat(stored["created_at"]).minute == 30


def test_modify_symptom_raises_keyerror_when_record_missing(tmp_path):
    db = Database(tmp_path / "symptom-missing.json")
    patient = Patient(
        email="symptom-missing@test.com",
        password="Password1",
        name="Sym",
        surname="Missing",
    )
    pc = PatientController(patient, db=db)
    day = date(2023, 6, 8)

    pc.add_symptom(day, "Nausea", Severity.MILD, time(8, 15))

    with pytest.raises(KeyError):
        pc.modify_symptom(
            day=day,
            record_id=123,
            description="Altro",
            severity=Severity.SEVERE,
            when=time(9, 45),
        )


def test_add_alarm_persists_in_db(tmp_path):
    db = Database(tmp_path / "alarms.json")
    patient = Patient(
        email="alarm@test.com",
        password="Password1",
        name="Al",
        surname="Arm",
    )
    pc = PatientController(patient, db=db)

    pc.add_alarm(7, 45, [0, 2], enabled=False)

    assert len(patient.alarms) == 1
    alarm = patient.alarms[0]
    assert alarm.hour == 7
    assert alarm.minute == 45
    assert alarm.days == [0, 2]
    assert alarm.enabled is False

    rows = db.search(Patient, email=patient.email)
    assert rows
    stored_alarm = rows[0]["alarms"][0]
    assert stored_alarm["hour"] == 7
    assert stored_alarm["minute"] == 45
    assert stored_alarm["days"] == [0, 2]
    assert stored_alarm["enabled"] is False


def test_update_alarm_updates_db(tmp_path):
    db = Database(tmp_path / "alarms-update.json")
    patient = Patient(
        email="alarm-update@test.com",
        password="Password1",
        name="Al",
        surname="Update",
    )
    pc = PatientController(patient, db=db)

    pc.add_alarm(6, 0, [1, 3])
    pc.update_alarm(0, 8, 30, [3, 5], enabled=False)

    assert len(patient.alarms) == 1
    alarm = patient.alarms[0]
    assert alarm.hour == 8
    assert alarm.minute == 30
    assert alarm.days == [3, 5]
    assert alarm.enabled is False

    rows = db.search(Patient, email=patient.email)
    assert rows
    stored_alarm = rows[-1]["alarms"][0]
    assert stored_alarm["hour"] == 8
    assert stored_alarm["minute"] == 30
    assert stored_alarm["days"] == [3, 5]
    assert stored_alarm["enabled"] is False


def test_update_alarm_invalid_index_keeps_state(tmp_path):
    db = Database(tmp_path / "alarms-invalid-update.json")
    patient = Patient(
        email="alarm-invalid@test.com",
        password="Password1",
        name="Al",
        surname="Invalid",
    )
    pc = PatientController(patient, db=db)

    pc.add_alarm(6, 15, [0, 1])
    rows_before = db.search(Patient, email=patient.email)

    pc.update_alarm(5, 9, 0, [2], enabled=False)

    assert patient.alarms[0].hour == 6
    assert patient.alarms[0].minute == 15
    rows_after = db.search(Patient, email=patient.email)
    assert rows_after == rows_before


def test_remove_alarm_updates_db(tmp_path):
    db = Database(tmp_path / "alarms-remove.json")
    patient = Patient(
        email="alarm-remove@test.com",
        password="Password1",
        name="Al",
        surname="Remove",
    )
    pc = PatientController(patient, db=db)

    pc.add_alarm(5, 0, [1, 2, 3])
    pc.remove_alarm(0)

    assert patient.alarms == []
    rows = db.search(Patient, email=patient.email)
    assert rows
    assert rows[-1]["alarms"] == []


def test_remove_alarm_invalid_index_keeps_state(tmp_path):
    db = Database(tmp_path / "alarms-invalid-remove.json")
    patient = Patient(
        email="alarm-invalid-remove@test.com",
        password="Password1",
        name="Al",
        surname="Keep",
    )
    pc = PatientController(patient, db=db)

    pc.add_alarm(5, 45, [4, 5])
    rows_before = db.search(Patient, email=patient.email)

    pc.remove_alarm(3)

    assert len(patient.alarms) == 1
    rows_after = db.search(Patient, email=patient.email)
    assert rows_after == rows_before


def test_patient_remove_link_removes_from_store_and_db(tmp_path):
    db = Database(tmp_path / "links-patient.json")
    link_store = []
    patient = Patient(
        email="link-p@test.com",
        password="Password1",
        name="Link",
        surname="Patient",
    )
    specialist = Specialist(
        email="link-s@test.com",
        password="Password1",
        name="Link",
        surname="Specialist",
        category=SpecialistCategory.DIETICIAN,
    )
    pc = PatientController(patient, db=db, link_store=link_store)
    sc = SpecialistController(specialist, db=db, link_store=link_store)

    lr = pc.send_link_request(specialist)
    sc.accept_request(lr)

    assert lr.state is LinkRequestState.CONNECTED
    assert lr in link_store
    assert db.all(LinkRequest)

    pc.remove_link(specialist)

    assert lr not in link_store
    assert db.all(LinkRequest) == []


def test_patient_remove_link_raises_when_connection_missing(tmp_path):
    db = Database(tmp_path / "links-patient-missing.json")
    link_store = []
    patient = Patient(
        email="link-missing@test.com",
        password="Password1",
        name="Link",
        surname="Missing",
    )
    specialist = Specialist(
        email="link-missing-spec@test.com",
        password="Password1",
        name="Link",
        surname="Spec",
        category=SpecialistCategory.DIETICIAN,
    )
    pc = PatientController(patient, db=db, link_store=link_store)

    pending = pc.send_link_request(specialist)
    assert pending.state is LinkRequestState.PENDING

    with pytest.raises(ValueError):
        pc.remove_link(specialist)

    assert pending in link_store


def test_specialist_reject_request_updates_state_and_db(tmp_path):
    db = Database(tmp_path / "links-reject.json")
    link_store = []
    patient = Patient(
        email="reject-p@test.com",
        password="Password1",
        name="Reject",
        surname="Patient",
    )
    specialist = Specialist(
        email="reject-s@test.com",
        password="Password1",
        name="Reject",
        surname="Specialist",
        category=SpecialistCategory.DIETICIAN,
    )
    pc = PatientController(patient, db=db, link_store=link_store)
    sc = SpecialistController(specialist, db=db, link_store=link_store)

    lr = pc.send_link_request(specialist)
    sc.reject_request(lr)

    assert lr.state is LinkRequestState.REJECTED
    rows = db.search(LinkRequest, id=lr.id)
    assert rows
    assert rows[0]["state"] == LinkRequestState.REJECTED.value


def test_specialist_reject_request_raises_for_unmanaged_request(tmp_path):
    db = Database(tmp_path / "links-reject-invalid.json")
    link_store = []
    patient = Patient(
        email="reject-invalid@test.com",
        password="Password1",
        name="Reject",
        surname="Invalid",
    )
    specialist = Specialist(
        email="reject-invalid-spec@test.com",
        password="Password1",
        name="Reject",
        surname="Spec",
        category=SpecialistCategory.DIETICIAN,
    )
    sc = SpecialistController(specialist, db=db, link_store=link_store)
    lr = LinkRequest(patient=patient, specialist=specialist)

    with pytest.raises(ValueError):
        sc.reject_request(lr)


def test_specialist_remove_link_removes_from_store_and_db(tmp_path):
    db = Database(tmp_path / "links-specialist.json")
    link_store = []
    patient = Patient(
        email="link-specialist@test.com",
        password="Password1",
        name="Link",
        surname="Specialist",
    )
    specialist = Specialist(
        email="link-specialist-spec@test.com",
        password="Password1",
        name="Link",
        surname="Spec",
        category=SpecialistCategory.DIETICIAN,
    )
    pc = PatientController(patient, db=db, link_store=link_store)
    sc = SpecialistController(specialist, db=db, link_store=link_store)

    lr = pc.send_link_request(specialist)
    sc.accept_request(lr)

    assert lr in link_store and lr.state is LinkRequestState.CONNECTED

    sc.remove_link(patient)

    assert lr not in link_store
    assert db.all(LinkRequest) == []


def test_specialist_remove_link_raises_when_connection_missing(tmp_path):
    db = Database(tmp_path / "links-specialist-missing.json")
    link_store = []
    patient = Patient(
        email="link-specialist-missing@test.com",
        password="Password1",
        name="Link",
        surname="Missing",
    )
    specialist = Specialist(
        email="link-specialist-missing-spec@test.com",
        password="Password1",
        name="Link",
        surname="Spec",
        category=SpecialistCategory.DIETICIAN,
    )
    pc = PatientController(patient, db=db, link_store=link_store)
    pc.send_link_request(specialist)
    sc = SpecialistController(specialist, db=db, link_store=link_store)

    with pytest.raises(ValueError):
        sc.remove_link(patient)
        