from __future__ import annotations

"""nutrease.ui.pages.patient_diary – Pagina **Diario Paziente** (UC 8-12).

Funzionalità:
* Calendario (default oggi) + time-picker per i nuovi record
* Lista record (pasti / sintomi) con pulsante «Elimina»
* Form «Aggiungi pasto» / «Aggiungi sintomo»
* Configurazione promemoria diario
"""

from datetime import date, time
from typing import List, Sequence  # noqa: F401 (usati in forward refs)

import streamlit as st

from nutrease.models.diary import DailyDiary, Day
from nutrease.models.enums import RecordType, Severity, Unit
from nutrease.models.record import MealRecord, SymptomRecord
from nutrease.utils.database import (  # noqa: F401 – placeholder per futuri use-cases
    Database,
)
from nutrease.utils.tz import local_now
from nutrease.ui.i18n import format_severity, format_unit
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
                            f"- {p.quantity} {format_unit(p.unit)} "
                            f"di **{p.food_name}**"
                        )
                    if st.button("Modifica", key=f"edit_btn_{rec.id}"):
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
                                st.selectbox(
                                    "Unità",
                                    list(Unit),
                                    index=list(Unit).index(p.unit),
                                    key=f"eu_{rec.id}_{i}",
                                    format_func=format_unit,
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
                        f"Intensità: **{format_severity(sym.severity)}**",
                    )
                    if st.button("Modifica", key=f"edit_btn_{rec.id}"):
                        st.session_state[f"edit_{rec.id}"] = True
                        st.rerun()
                    if st.session_state.get(f"edit_{rec.id}"):
                        sym_val = st.text_input(
                            "Sintomo", sym.symptom, key=f"sym_{rec.id}"
                        )
                        sev_val = st.selectbox(
                            "Intensità",
                            list(Severity),
                            index=list(Severity).index(sym.severity),
                            key=f"sev_{rec.id}",
                            format_func=format_severity,
                        )
                        if st.button("Salva", key=f"save_{rec.id}"):
                            pc.modify_symptom(
                                sel_day,
                                rec.id,
                                sym_val,
                                sev_val,
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
    unit_options = list(Unit)
    severity_options = list(Severity)
    if st.session_state.pop("meal_added", False):
        st.success("Pasto aggiunto")
        st.session_state.meal_items = [
            {"food": "", "qty": 0.0, "unit": unit_options[0]}
        ]
    if "meal_items" not in st.session_state:
        st.session_state.meal_items = [
            {"food": "", "qty": 0.0, "unit": unit_options[0]}
        ]
    else:
        for item in st.session_state.meal_items:
            if isinstance(item.get("unit"), str):
                item["unit"] = Unit.from_str(item["unit"])
    if st.session_state.pop("symptom_added", False):
        st.success("Sintomo aggiunto")
        st.session_state.symptom_desc = ""
        st.session_state.symptom_sev = severity_options[0]
    if "symptom_sev" not in st.session_state:
        st.session_state.symptom_sev = severity_options[0]
    elif isinstance(st.session_state.symptom_sev, str):
        st.session_state.symptom_sev = Severity.from_str(st.session_state.symptom_sev)

    if rec_type == "Pasto":
        for idx, item in enumerate(st.session_state.meal_items):
            cols = st.columns(3)
            item["food"] = cols[0].text_input(
                "Alimento", key=f"meal_food_{idx}", value=item["food"]
            )
            item["qty"] = cols[1].number_input(
                "Quantità", min_value=0.0, step=0.1, key=f"meal_qty_{idx}", value=item["qty"]
            )
            current_unit = item.get("unit", unit_options[0])
            if isinstance(current_unit, str):
                current_unit = Unit.from_str(current_unit)
            item["unit"] = cols[2].selectbox(
                "Unità",
                unit_options,
                index=unit_options.index(current_unit),
                key=f"meal_unit_{idx}",
                format_func=format_unit,
            )

        if st.button("Aggiungi alimento", key="add_food"):
            st.session_state.meal_items.append(
                {"food": "", "qty": 0.0, "unit": unit_options[0]}
            )
            st.rerun()

        if st.button("Aggiungi Pasto", use_container_width=True):
            valid_items = [
                it for it in st.session_state.meal_items if it["food"] and it["qty"] > 0
            ]
            foods = [it["food"] for it in valid_items]
            qtys = [it["qty"] for it in valid_items]
            units: List[Unit] = []
            for it in valid_items:
                unit_val = it["unit"]
                if isinstance(unit_val, str):
                    unit_val = Unit.from_str(unit_val)
                units.append(unit_val)
            if foods:
                try:
                    pc.add_meal(sel_day, record_time, foods, qtys, units)
                    st.session_state.meal_added = True
                    st.rerun()
                except ValueError as exc:
                    st.error(str(exc))
            else:
                st.warning("Compila almeno un alimento e quantità > 0.")

    else:
        symptom = st.text_input("Sintomo", key="symptom_desc")
        severity = st.selectbox(
            "Intensità", severity_options, key="symptom_sev", format_func=format_severity
        )
        if st.button("Aggiungi Sintomo", use_container_width=True):
            if symptom:
                pc.add_symptom(
                    sel_day,
                    symptom,
                    severity,
                    record_time,
                )
                st.session_state.symptom_added = True
                st.rerun()
            else:
                st.warning("Inserisci il nome del sintomo.")


# ---------------------------------------------------------------------------
# Debug standalone
# ---------------------------------------------------------------------------
if __name__ == "__main__":
    main()