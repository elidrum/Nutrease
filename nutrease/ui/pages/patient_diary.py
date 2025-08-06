from __future__ import annotations

"""nutrease.ui.pages.patient_diary – Pagina **Diario Paziente** (UC 8-12).

Funzionalità:
* Calendario (default oggi) + time-picker per i nuovi record
* Lista record (pasti / sintomi) con pulsante «Elimina»
* Form «Aggiungi pasto» / «Aggiungi sintomo»
* Configurazione promemoria diario
* Riepilogo nutrienti giornalieri
"""

from datetime import date, datetime, time
from typing import List, Sequence   # noqa: F401 (usati in forward refs)

import streamlit as st

from nutrease.models.diary import DailyDiary, Day
from nutrease.models.enums import Nutrient, RecordType, Severity, Unit
from nutrease.models.record import FoodPortion, MealRecord, SymptomRecord
from nutrease.utils.database import Database  # noqa: F401 – placeholder per futuri use-cases


# ---------------------------------------------------------------------------
# MAIN
# ---------------------------------------------------------------------------


def main() -> None:  # noqa: D401
    """Renderizza la pagina del diario del paziente."""
    controllers = st.session_state.get("controllers", {})
    pc = controllers.get("patient")
    if pc is None:
        st.error("Devi prima effettuare il login come paziente.")
        st.stop()

    # ---------------- Sidebar – promemoria -------------------------------
    st.sidebar.header("⏰ Promemoria Diario")
    if pc.patient.alarm:
        alarm = pc.patient.alarm
        enabled = st.sidebar.checkbox("Abilita allarme", alarm.enabled)
        hour = st.sidebar.slider("Ora", 0, 23, alarm.hour)
        minute = st.sidebar.slider("Minuti", 0, 59, alarm.minute)
        if st.sidebar.button("Salva allarme"):
            pc.configure_alarm(hour, minute, enabled)
            st.sidebar.success("Allarme aggiornato!")
    else:
        if st.sidebar.button("Imposta allarme"):
            pc.configure_alarm(20, 0, True)
            st.sidebar.success("Allarme impostato (20:00).")

    # ---------------- Selettore giorno -----------------------------------
    st.title("📒 Diario alimentare")
    sel_day: date = st.date_input("Giorno", value=date.today())
    diary = pc.get_diary(sel_day) or DailyDiary(day=Day(date=sel_day), patient=pc.patient)

    # ---------------- Lista record ---------------------------------------
    st.subheader("Record del giorno")
    if not diary.records:
        st.info("Nessun record per questo giorno.")
    else:
        for rec in diary.records:
            header = "🍽️ Pasto" if rec.record_type == RecordType.MEAL else "🤒 Sintomo"
            with st.expander(f"{header} – {rec.created_at:%H:%M}"):
                if rec.record_type == RecordType.MEAL:
                    meal: MealRecord = rec  # type: ignore[assignment]
                    for p in meal.portions:
                        st.markdown(f"- {p.quantity} {p.unit.value.title()} di **{p.food_name}**")
                else:
                    sym: SymptomRecord = rec  # type: ignore[assignment]
                    st.markdown(
                        f"Sintomo: **{sym.symptom}**  \nIntensità: **{sym.severity.value}**"
                    )
                if st.button("Elimina", key=f"del_{rec.id}"):
                    pc.remove_record(sel_day, rec.id)
                    st.rerun()

    # ---------------- Form aggiunta record -------------------------------
    st.subheader("Aggiungi nuovo record")

    # ⏰ time-picker persistente: mantiene l’orario scelto dopo il rerun
    if "new_rec_time" not in st.session_state:
        st.session_state.new_rec_time = datetime.now().time()

    record_time: time = st.time_input(
        "Orario",
        key="new_rec_time",
    )

    rec_type = st.radio("Tipo di record", ["Pasto", "Sintomo"], horizontal=True)

    if rec_type == "Pasto":
        food_name = st.text_input("Alimento")
        qty = st.number_input("Quantità", min_value=0.0, step=0.1)
        unit_str = st.selectbox("Unità", [u.value for u in Unit])
        if st.button("Aggiungi Pasto", use_container_width=True):
            if food_name and qty > 0:
                pc.add_meal(
                    sel_day,
                    record_time,
                    [food_name],
                    [qty],
                    [Unit.from_str(unit_str)],
                )
                st.success("Pasto salvato!")
                st.rerun()
            else:
                st.warning("Compila nome alimento e quantità > 0.")
    else:
        symptom = st.text_input("Sintomo")
        severity_str = st.selectbox("Intensità", [s.value for s in Severity])
        if st.button("Aggiungi Sintomo", use_container_width=True):
            if symptom:
                pc.add_symptom(
                    sel_day,
                    symptom,
                    Severity.from_str(severity_str),
                    record_time,
                )
                st.success("Sintomo salvato!")
                st.rerun()
            else:
                st.warning("Inserisci il nome del sintomo.")

    # ---------------- Riepilogo nutrienti --------------------------------
    st.subheader("Totali nutrienti (g)")
    cols = st.columns(len(Nutrient))
    for col, nutr in zip(cols, Nutrient):
        total = pc.nutrient_total(sel_day, nutr)
        col.metric(nutr.value.title(), f"{total:.1f}")


# ---------------------------------------------------------------------------
# Debug standalone
# ---------------------------------------------------------------------------
if __name__ == "__main__":
    main()