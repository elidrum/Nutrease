from __future__ import annotations

"""Streamlit page **3 – Specialist Dashboard**.

Funzioni principali (UC ManageRequests + ReadPatientDiary):
* Elenco richieste di collegamento in attesa → pulsanti **Accetta / Rifiuta**
* Elenco pazienti già collegati → selezione paziente + giorno
* Visualizzazione diario paziente con filtro per nutriente
"""

from datetime import date
from typing import List, Optional

import streamlit as st

from nutrease.models.communication import LinkRequest, LinkRequestState
from nutrease.models.enums import Nutrient, RecordType, Severity
from nutrease.models.record import MealRecord, SymptomRecord

# Recupera controller specialist -------------------------------------------
controllers = st.session_state.get("controllers", {})
sc = controllers.get("specialist")
if sc is None:
    st.error("È necessario effettuare il login come specialista.")
    st.stop()

st.title("🩺 Dashboard Specialista")

# ---------------------------------------------------------------------------
# Colonne: ManageRequests (sx)  |  Pazienti collegati (dx)
# ---------------------------------------------------------------------------
col_req, col_pat = st.columns(2)

# ------------------- colonna sinistra – richieste -------------------------
with col_req:
    st.subheader("Richieste in attesa")
    pending: List[LinkRequest] = sc.pending_requests()
    if not pending:
        st.info("Nessuna richiesta da gestire.")
    else:
        for lr in pending:
            with st.container(border=True):
                st.markdown(
                    f"**{lr.patient.email}** – _{lr.comment or 'no comment'}_  ".replace("  ", "&nbsp;&nbsp;"),
                    unsafe_allow_html=True,
                )
                acc, rej = st.columns(2)
                if acc.button("✅ Accetta", key=f"acc_{id(lr)}"):
                    sc.accept_request(lr)
                    st.success("Richiesta accettata")
                    st.experimental_rerun()
                if rej.button("❌ Rifiuta", key=f"rej_{id(lr)}"):
                    sc.reject_request(lr)
                    st.warning("Richiesta rifiutata")
                    st.experimental_rerun()

# ------------------- colonna destra – pazienti collegati ------------------
with col_pat:
    st.subheader("Pazienti collegati")
    # Recupera tutte le richieste accettate
    accepted = [
        lr for lr in sc._iter_link_requests()  # type: ignore[attr-defined]
        if lr.state == LinkRequestState.ACCEPTED
    ]
    if not accepted:
        st.info("Nessun paziente collegato.")
        st.stop()

    patient_options = {f"{lr.patient.name} {lr.patient.surname} ({lr.patient.email})": lr.patient for lr in accepted}
    sel_label = st.selectbox("Seleziona paziente", list(patient_options.keys()))
    selected_patient = patient_options[sel_label]

    # -------------------------------------------------------------------
    # Seleziona giorno + filtro nutriente
    # -------------------------------------------------------------------
    st.divider()
    st.subheader("Diario paziente")
    day: date = st.date_input("Giorno", value=date.today(), key="view_date")
    nutrient_filter = st.selectbox("Filtra nutriente (Totale)", ["Tutti"] + [n.value for n in Nutrient])

    diary = sc.get_patient_diary(selected_patient, day)
    if diary is None or not diary.records:
        st.info("Nessun record per questo giorno.")
    else:
        for rec in diary.records:
            if rec.record_type == RecordType.MEAL:
                meal: MealRecord = rec  # type: ignore[assignment]
                with st.expander(f"🍽️ Pasto – {rec.created_at:%H:%M}"):
                    for p in meal.portions:
                        st.markdown(f"- {p.quantity} {p.unit.value.title()} di **{p.food_name}**")
            else:
                sym: SymptomRecord = rec  # type: ignore[assignment]
                with st.expander(f"🤒 Sintomo – {rec.created_at:%H:%M}"):
                    st.write(sym.symptom)
                    st.write(f"Intensità: {sym.severity.value}")

    # -------------------------------------------------------------------
    # Totali nutrienti
    # -------------------------------------------------------------------
    st.subheader("Totali nutrienti (g)")
    if nutrient_filter == "Tutti":
        cols = st.columns(len(Nutrient))
        for col, n in zip(cols, Nutrient):
            col.metric(n.value.title(), f"{sc.nutrient_total(selected_patient, day, n):.1f}")
    else:
        n_sel = Nutrient.from_str(nutrient_filter)
        tot = sc.nutrient_total(selected_patient, day, n_sel)
        st.metric(n_sel.value.title(), f"{tot:.1f} g")