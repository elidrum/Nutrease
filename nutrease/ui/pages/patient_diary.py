from __future__ import annotations

"""nutrease.ui.pages.patient_diary – Pagina **Diario Paziente** (UC 8-12).

Funzionalità:
* Calendario (default oggi) + time-picker per i nuovi record
* Lista record (pasti / sintomi) con pulsante «Elimina»
* Form «Aggiungi pasto» / «Aggiungi sintomo»
* Configurazione promemoria diario
* Riepilogo nutrienti giornalieri
"""

from datetime import date, time
from typing import List, Sequence  # noqa: F401 (usati in forward refs)

import streamlit as st

from nutrease.models.diary import DailyDiary, Day
from nutrease.models.enums import Nutrient, RecordType, Severity, Unit
from nutrease.models.record import MealRecord, SymptomRecord
from nutrease.utils.database import (  # noqa: F401 – placeholder per futuri use-cases
    Database,
)
from nutrease.utils.tz import local_now
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
    DAY_NAMES = ["Lun", "Mar", "Mer", "Gio", "Ven", "Sab", "Dom"]
    if st.sidebar.button("Aggiungi notifica", key="add_alarm"):
        st.session_state.show_new_alarm = True
    if st.session_state.get("show_new_alarm"):
        t = st.sidebar.time_input("Orario", time(20, 0), key="new_alarm_time")
        days_sel = st.sidebar.multiselect(
            "Giorni", DAY_NAMES, default=DAY_NAMES, key="new_alarm_days"
        )
        if st.sidebar.button("Salva notifica", key="save_new_alarm"):
            pc.add_alarm(t.hour, t.minute, [DAY_NAMES.index(d) for d in days_sel])
            st.session_state.show_new_alarm = False
            st.sidebar.success("Notifica aggiunta")
            st.rerun()

    for idx, alarm in enumerate(pc.patient.alarms):
        with st.sidebar.expander(f"Notifica {idx + 1}"):
            enabled = st.checkbox("Attiva", alarm.enabled, key=f"al_en_{idx}")
            t = st.time_input(
                "Orario", time(alarm.hour, alarm.minute), key=f"al_time_{idx}"
            )
            days_sel = st.multiselect(
                "Giorni",
                DAY_NAMES,
                default=[DAY_NAMES[d] for d in alarm.days],
                key=f"al_days_{idx}",
            )
            save_col, del_col = st.columns(2)
            if save_col.button("Salva", key=f"al_save_{idx}"):
                pc.update_alarm(
                    idx,
                    t.hour,
                    t.minute,
                    [DAY_NAMES.index(d) for d in days_sel],
                    enabled,
                )
                st.sidebar.success("Notifica aggiornata")
                st.rerun()
            if del_col.button("Elimina", key=f"al_del_{idx}"):
                pc.remove_alarm(idx)
                st.sidebar.warning("Notifica eliminata")
                st.rerun()

    # ---------------- Selettore giorno -----------------------------------
    st.title("📒 Diario alimentare")
    sel_day: date = st.date_input("Giorno", value=date.today())
    diary = pc.get_diary(sel_day) or DailyDiary(
        day=Day(date=sel_day), patient=pc.patient
    )

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
                        st.markdown(
                            f"- {p.quantity} {p.unit.value.title()} "
                            f"di **{p.food_name}**"
                        )
                    cols = st.columns(len(Nutrient))
                    for col, n in zip(cols, Nutrient):
                        col.metric(n.value.title(), f"{meal.get_nutrient_total(n):.1f}")
                    if st.button("Modifica", key=f"edit_{rec.id}"):
                        st.session_state[f"edit_{rec.id}"] = True
                        st.rerun()
                    if st.session_state.get(f"edit_{rec.id}"):
                        foods: List[str] = []
                        qtys: List[float] = []
                        units: List[Unit] = []
                        for i, p in enumerate(meal.portions):
                            foods.append(
                                st.text_input(
                                    "Alimento", p.food_name, key=f"ef_{rec.id}_{i}"
                                )
                            )
                            qtys.append(
                                st.number_input(
                                    "Quantità",
                                    value=p.quantity,
                                    step=0.1,
                                    key=f"eq_{rec.id}_{i}",
                                )
                            )
                            units.append(
                                Unit.from_str(
                                    st.selectbox(
                                        "Unità",
                                        [u.value for u in Unit],
                                        index=list(Unit).index(p.unit),
                                        key=f"eu_{rec.id}_{i}",
                                    )
                                )
                            )
                        if st.button("Salva", key=f"save_{rec.id}"):
                            pc.modify_meal(sel_day, rec.id, foods, qtys, units)
                            st.session_state.pop(f"edit_{rec.id}")
                            st.success("Record aggiornato")
                            st.rerun()
                else:
                    sym: SymptomRecord = rec  # type: ignore[assignment]
                    st.markdown(
                        f"Sintomo: **{sym.symptom}**  \n"
                        f"Intensità: **{sym.severity.value}**",
                    )
                    if st.button("Modifica", key=f"edit_{rec.id}"):
                        st.session_state[f"edit_{rec.id}"] = True
                        st.rerun()
                    if st.session_state.get(f"edit_{rec.id}"):
                        sym_val = st.text_input(
                            "Sintomo", sym.symptom, key=f"sym_{rec.id}"
                        )
                        sev_val = st.selectbox(
                            "Intensità",
                            [s.value for s in Severity],
                            index=list(Severity).index(sym.severity),
                            key=f"sev_{rec.id}",
                        )
                        if st.button("Salva", key=f"save_{rec.id}"):
                            pc.modify_symptom(
                                sel_day,
                                rec.id,
                                sym_val,
                                Severity.from_str(sev_val),
                                rec.created_at.time(),
                            )
                            st.session_state.pop(f"edit_{rec.id}")
                            st.success("Record aggiornato")
                            st.rerun()
                if st.button("Elimina", key=f"del_{rec.id}"):
                    pc.remove_record(sel_day, rec.id)
                    st.rerun()
    # ---------------- Form aggiunta record -------------------------------
    st.subheader("Aggiungi nuovo record")

    # ⏰ time-picker persistente: mantiene l’orario scelto dopo il rerun
    if "new_rec_time" not in st.session_state:
        st.session_state.new_rec_time = local_now().time()

    record_time: time = st.time_input(
        "Orario",
        key="new_rec_time",
    )

    rec_type = st.radio("Tipo di record", ["Pasto", "Sintomo"], horizontal=True)
    if st.session_state.pop("meal_added", False):
        st.success("Pasto aggiunto")
        st.session_state.meal_food = ""
        st.session_state.meal_qty = 0.0
        st.session_state.meal_unit = [u.value for u in Unit][0]
    if st.session_state.pop("symptom_added", False):
        st.success("Sintomo aggiunto")

    if rec_type == "Pasto":
        food_name = st.text_input("Alimento", key="meal_food")
        qty = st.number_input("Quantità", min_value=0.0, step=0.1, key="meal_qty")
        unit_str = st.selectbox("Unità", [u.value for u in Unit], key="meal_unit")
        if st.button("Aggiungi Pasto", use_container_width=True):
            if food_name and qty > 0:
                try:
                    pc.add_meal(
                        sel_day,
                        record_time,
                        [food_name],
                        [qty],
                        [Unit.from_str(unit_str)],
                    )
                    st.session_state.meal_added = True
                    st.rerun()
                except ValueError as exc:
                    st.error(str(exc))
            else:
                st.warning("Compila nome alimento e quantità > 0.")

    else:
        symptom = st.text_input("Sintomo", key="symptom_desc")
        severity_str = st.selectbox(
            "Intensità", [s.value for s in Severity], key="symptom_sev"
        )
        if st.button("Aggiungi Sintomo", use_container_width=True):
            if symptom:
                pc.add_symptom(
                    sel_day,
                    symptom,
                    Severity.from_str(severity_str),
                    record_time,
                )
                st.session_state.symptom_desc = ""
                st.session_state.symptom_sev = [s.value for s in Severity][0]
                st.session_state.symptom_added = True
                st.rerun()
            else:
                st.warning("Inserisci il nome del sintomo.")


# ---------------------------------------------------------------------------
# Debug standalone
# ---------------------------------------------------------------------------
if __name__ == "__main__":
    main()