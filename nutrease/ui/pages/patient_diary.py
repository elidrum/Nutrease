from __future__ import annotations

"""nutrease.ui.pages.patient_diary – Pagina **Diario Paziente** (UC 8-12).

Funzionalità:
* Selettore data (default oggi) e lista record per quel giorno
* Form per aggiungere/eliminare pasto o sintomo
* Configurazione promemoria diario (AlarmConfig)
* Riepilogo nutrienti assunti nella giornata
"""

from datetime import date, datetime
from typing import List

import streamlit as st

from nutrease.models.diary import DailyDiary, Day
from nutrease.models.enums import Nutrient, RecordType, Severity, Unit
from nutrease.models.record import FoodPortion, MealRecord, SymptomRecord
from nutrease.models.user import Patient
from nutrease.utils.database import Database


# ---------------------------------------------------------------------------
# MAIN ENTRY
# ---------------------------------------------------------------------------


def main() -> None:  # noqa: D401 – imperative name by design
    """Renderizza la pagina del diario del paziente."""
    # ------------------ recupera controller -----------------------------
    controllers = st.session_state.get("controllers", {})
    pc = controllers.get("patient")
    if pc is None:
        st.error("Devi prima effettuare il login come paziente.")
        st.stop()

    # ------------------ sidebar: allarme diario -------------------------
    st.sidebar.header("⏰ Promemoria Diario")
    if pc.patient.alarm:
        alarm = pc.patient.alarm
        enabled = st.sidebar.checkbox(
            "Abilita allarme", value=alarm.enabled, key="alarm_enabled"
        )
        hour = st.sidebar.slider(
            "Ora", 0, 23, alarm.hour, key="alarm_hour"
        )
        minute = st.sidebar.slider(
            "Minuti", 0, 59, alarm.minute, key="alarm_min"
        )
        if st.sidebar.button("Salva allarme"):
            pc.configure_alarm(hour, minute, enabled)
            st.sidebar.success("Allarme aggiornato!")
    else:
        if st.sidebar.button("Imposta allarme"):
            pc.configure_alarm(20, 0)
            st.sidebar.success("Allarme impostato alle 20:00")

    # ------------------ selezione giornata ------------------------------
    st.title("📒 Diario alimentare")
    selected_day: date = st.date_input(
        "Giorno", value=date.today(), key="diary_date"
    )

    diary = pc.get_diary(selected_day)
    if diary is None:
        diary = DailyDiary(day=Day(date=selected_day), patient=pc.patient)

    # ------------------ lista record ------------------------------------
    st.subheader("Record del giorno")
    if not diary.records:
        st.info("Nessun record per questo giorno.")
    else:
        for i, rec in enumerate(diary.records):
            with st.expander(
                f"{i+1}. {rec.record_type.value.title()} – {rec.created_at:%H:%M}"
            ):
                if rec.record_type == RecordType.MEAL:
                    meal: MealRecord = rec  # type: ignore[assignment]
                    for p in meal.portions:
                        st.markdown(
                            f"- {p.quantity} {p.unit.value.title()} di **{p.food_name}**"
                        )
                else:
                    sym: SymptomRecord = rec  # type: ignore[assignment]
                    st.markdown(
                        f"Sintomo: **{sym.symptom}** – Intensità: **{sym.severity.value}**"
                    )

                if st.button("Elimina", key=f"del_{i}"):
                    diary.remove_record(rec)
                    st.experimental_rerun()

    # ------------------ form aggiunta record ----------------------------
    st.subheader("Aggiungi nuovo record")
    rec_type = st.radio("Tipo", ["Pasto", "Sintomo"], horizontal=True)

    if rec_type == "Pasto":
        food_name = st.text_input("Alimento")
        qty = st.number_input("Quantità", min_value=0.0, step=0.1)
        unit = st.selectbox("Unità", [u.value for u in Unit])
        if st.button("Aggiungi Pasto", use_container_width=True):
            if food_name and qty > 0:
                portion = FoodPortion(
                    food_name=food_name,
                    quantity=qty,
                    unit=Unit.from_str(unit),
                )
                meal = MealRecord(
                    record_type=RecordType.MEAL,
                    portions=[portion],
                    created_at=datetime.now(),
                    note=None,
                )
                pc.add_record(meal)
                st.success("Record pasto aggiunto!")
                st.experimental_rerun()
    else:
        symptom = st.text_input("Sintomo")
        severity = st.selectbox("Intensità", [s.value for s in Severity])
        if st.button("Aggiungi Sintomo", use_container_width=True):
            if symptom:
                sym_rec = SymptomRecord(
                    record_type=RecordType.SYMPTOM,
                    symptom=symptom,
                    severity=Severity.from_str(severity),
                    created_at=datetime.now(),
                    note=None,
                )
                pc.add_record(sym_rec)
                st.success("Record sintomo aggiunto!")
                st.experimental_rerun()

    # ------------------ riepilogo nutrienti -----------------------------
    st.subheader("Totali nutrienti")
    cols = st.columns(len(Nutrient))
    for col, n in zip(cols, Nutrient):
        col.metric(
            label=n.value.title(),
            value=f"{pc.nutrient_total(selected_day, n):.1f} g",
        )


# ---------------------------------------------------------------------------
# Debug standalone (facoltativo)
# ---------------------------------------------------------------------------
if __name__ == "__main__":
    main()