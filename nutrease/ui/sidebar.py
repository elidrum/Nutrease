from __future__ import annotations

"""Shared sidebar components for the patient UI."""

from datetime import time

import streamlit as st

from nutrease.controllers.patient_controller import PatientController


DAY_NAMES = ["Lun", "Mar", "Mer", "Gio", "Ven", "Sab", "Dom"]


def render_notifications(pc: PatientController) -> None:
    """Render the notifications manager for the patient sidebar."""
    st.sidebar.header("⏰ Promemoria Diario")

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