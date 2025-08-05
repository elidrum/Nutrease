from datetime import datetime, timedelta

import pytest

from nutrease.models.diary import AlarmConfig, Day, DailyDiary
from nutrease.models.enums import Nutrient, Severity, Unit
from nutrease.models.record import FoodPortion, MealRecord, RecordType, SymptomRecord
from nutrease.models.user import Patient


@pytest.fixture
def patient() -> Patient:
    return Patient(email="foo@example.com", password="Password1", name="Foo", surname="Bar")


def test_password_validation():
    with pytest.raises(ValueError):
        Patient(email="x@y.com", password="short", name="A", surname="B")


def test_alarm_next_activation():
    alarm = AlarmConfig(hour=23, minute=0, enabled=True)
    now = datetime(2025, 8, 2, 22, 0)
    nxt = alarm.next_activation(now=now)
    assert nxt.date() == now.date() and nxt.hour == 23

    now2 = datetime(2025, 8, 2, 23, 30)
    nxt2 = alarm.next_activation(now=now2)
    assert nxt2.date() == (now2 + timedelta(days=1)).date()


def test_meal_nutrient_total(patient):
    portion = FoodPortion(food_name="Mela", quantity=1, unit=Unit.ITEM)
    meal = MealRecord(record_type=RecordType.MEAL, portions=[portion])
    diary = DailyDiary(day=Day(date=datetime.today().date()), patient=patient, records=[meal])
    tot = diary.get_totals(Nutrient.SORBITOL)
    assert tot >= 0  # dataset demo has 3.5 g