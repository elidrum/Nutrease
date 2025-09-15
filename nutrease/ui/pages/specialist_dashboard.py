from __future__ import annotations

"""nutrease.ui.pages.specialist_dashboard – Pagina **Dashboard Specialista**.

Funzioni principali (UC ManageRequests + ReadPatientDiary):

* Colonna sinistra → richieste di collegamento (Accetta/Rifiuta)
* Colonna destra  → pazienti collegati, selezione data, filtro nutriente,
                    lista record e riepilogo nutrienti
"""

from datetime import date, timedelta
from typing import List

import streamlit as st

from nutrease.models.communication import LinkRequest
from nutrease.models.enums import Nutrient, RecordType
from nutrease.models.record import MealRecord, SymptomRecord
from nutrease.models.user import Patient
from nutrease.ui.i18n import format_nutrient, format_severity, format_unit

# ---------------------------------------------------------------------------
# MAIN ENTRY
# ---------------------------------------------------------------------------


def main() -> None:  # noqa: D401 – imperative name by design
    """Renderizza la dashboard dello specialista."""
    controllers = st.session_state.get("controllers", {})
    sc = controllers.get("specialist")
    if sc is None:
        st.error("È necessario effettuare il login come specialista.")
        st.stop()

    st.title("🩺 Dashboard Specialista")

    # ------ layout: richieste (sx) | pazienti & diario (dx) -------------
    col_req, col_pat = st.columns(2)

  # ------------------- colonna sinistra – richieste -------------------
    with col_req:
        st.subheader("Richieste in attesa")
        pending: List[LinkRequest] = sc.pending_requests()
        if not pending:
            st.info("Nessuna richiesta da gestire.")
        else:
            for lr in pending:
                with st.container(border=True):
                    st.markdown(
                        (
                            f"**{lr.patient.email}** – "
                            f"_{lr.comment or 'nessun commento'}_&nbsp;&nbsp;"
                        ),
                        unsafe_allow_html=True,
                    )
                    acc_col, rej_col = st.columns(2)
                    if acc_col.button("✅ Accetta", key=f"acc_{id(lr)}"):
                        sc.accept_request(lr)
                        st.success("Richiesta accettata")
                        st.rerun()
                    if rej_col.button("❌ Rifiuta", key=f"rej_{id(lr)}"):
                        sc.reject_request(lr)
                        st.warning("Richiesta rifiutata")
                        st.rerun()

    # ------------------- colonna destra – pazienti ----------------------
    with col_pat:
        st.subheader("Pazienti collegati")
        connections = sc.connections()

        if not connections:
            st.info("Nessun paziente collegato.")
            st.stop()

        patient_options = {
            f"{c.patient.name} {c.patient.surname} ({c.patient.email})": c.patient
            for c in connections
        }
        sel_label = st.selectbox("Seleziona paziente", list(patient_options.keys()))
        selected_patient: Patient = patient_options[sel_label]

        # --- azioni su paziente -----------------------------------------
        view_col, unlink_col = st.columns(2)
        if view_col.button("Visualizza paziente", key="view_patient_btn"):
            st.session_state["view_patient"] = sel_label
        if unlink_col.button("Scollega paziente", key="unlink"):
            try:
                sc.remove_link(selected_patient)
                st.warning("Paziente scollegato")
                st.rerun()
            except Exception as exc:
                st.error(str(exc))

        if st.session_state.get("view_patient") == sel_label:
            with st.expander("Scheda paziente", expanded=True):
                st.markdown(f"**Nome:** {selected_patient.name}")
                st.markdown(f"**Cognome:** {selected_patient.surname}")
                st.markdown(f"**Email:** {selected_patient.email}")
                st.text_area(
                    "Note personali del paziente",
                    value=selected_patient.profile_note,
                    key=f"pat_note_{selected_patient.email}",
                    disabled=True,
                )

 # ---------------- intervallo date ------------------------------
        st.divider()
        st.subheader("Diario paziente")
        col_from, col_to = st.columns(2)
        with col_from:
            start_day: date = st.date_input("Da", value=date.today(), key="start_day")
        with col_to:
            end_day: date = st.date_input("A", value=date.today(), key="end_day")

        nutrient_options: list[Nutrient | None] = [None] + list(Nutrient)
        nutrient_filter = st.selectbox(
            "Filtra nutriente (per pasto)",
            nutrient_options,
            key="nut_filter",
            format_func=lambda opt: "Tutti" if opt is None else format_nutrient(opt),
        )

        if start_day > end_day:
            st.error("La data iniziale deve precedere la data finale.")
            st.stop()

        n_sel = nutrient_filter

        for offset in range((end_day - start_day).days + 1):
            day = start_day + timedelta(days=offset)
            st.markdown(f"### {day:%Y-%m-%d}")
            diary = sc.get_patient_diary(selected_patient, day)
            if diary is None or not diary.records:
                st.info("Nessun record per questo giorno.")
                continue
            for rec in diary.records:
                if rec.record_type == RecordType.MEAL:
                    meal: MealRecord = rec  # type: ignore[assignment]
                    if n_sel and meal.get_nutrient_total(n_sel) == 0:
                        continue
                    with st.expander(f"🍽️ Pasto – {rec.created_at:%H:%M}"):
                        for p in meal.portions:
                            st.markdown(
                                (
                                    f"- {p.quantity} {format_unit(p.unit)} "
                                    f"di **{p.food_name}**"
                                )
                            )
                        if n_sel is None:
                            cols = st.columns(len(Nutrient))
                            for col, n in zip(cols, Nutrient):
                                col.metric(
                                    format_nutrient(n),
                                    f"{meal.get_nutrient_total(n):.1f}",
                                )
                        else:
                            st.metric(
                                format_nutrient(n_sel),
                                f"{meal.get_nutrient_total(n_sel):.1f}",
                            )
                else:
                    sym: SymptomRecord = rec  # type: ignore[assignment]
                    with st.expander(f"🤒 Sintomo – {rec.created_at:%H:%M}"):
                        st.write(sym.symptom)
                        st.write(f"Intensità: {format_severity(sym.severity)}")


# ---------------------------------------------------------------------------
# Debug standalone (facoltativo)
# ---------------------------------------------------------------------------
if __name__ == "__main__":
    main()