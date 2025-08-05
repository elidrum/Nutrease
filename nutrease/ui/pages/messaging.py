from __future__ import annotations

"""Streamlit page **4 – Messaging** (chat paziente‑specialista).

Requisiti:
* Solo **lo specialista** può inviare messaggi.
* Il paziente visualizza la conversazione (read‑only).
* Usa :class:`MessagingController` già inizializzato in ``st.session_state``.
"""

from datetime import datetime
from typing import List

import streamlit as st

from nutrease.controllers.messaging_controller import MessagingController
from nutrease.controllers.specialist_controller import SpecialistController
from nutrease.models.communication import Message
from nutrease.models.user import Patient, Specialist

# ---------------------------------------------------------------------------
# Recupera Controller & Utente corrente
# ---------------------------------------------------------------------------
controllers = st.session_state.get("controllers", {})
mc: MessagingController | None = controllers.get("messaging")  # type: ignore[assignment]
user = st.session_state.get("current_user")

if mc is None or user is None:
    st.error("Effettua prima il login.")
    st.stop()

st.title("💬 Chat Paziente‑Specialista")

# ---------------------------------------------------------------------------
# Determina controparte & controlli di invio
# ---------------------------------------------------------------------------

def _get_peer() -> Specialist | Patient | None:
    """Restituisce la controparte collegata all'utente corrente."""
    if isinstance(user, Specialist):
        # Specialista → seleziona un paziente collegato
        sc: SpecialistController | None = controllers.get("specialist")  # type: ignore[assignment]
        if sc is None:
            return None
        # pazienti da link request accepted
        accepted = [
            lr.patient for lr in sc._iter_link_requests()  # type: ignore[attr-defined]
            if lr.state.value == "ACCEPTED"
        ]
        if not accepted:
            return None
        labels = [f"{p.name} {p.surname} ({p.email})" for p in accepted]
        sel = st.selectbox("Seleziona paziente", labels)
        return accepted[labels.index(sel)]
    else:  # Patient
        pc = controllers.get("patient")
        if pc is None:
            return None
        # ipotesi: un singolo specialista collegato (primo accepted)
        linked = [
            lr.specialist for lr in pc._iter_link_requests()  # type: ignore[attr-defined]
            if lr.state.value == "ACCEPTED"
        ]
        return linked[0] if linked else None


peer = _get_peer()
if peer is None:
    st.info("Nessuna connessione disponibile.")
    st.stop()

# ---------------------------------------------------------------------------
# Mostra conversazione
# ---------------------------------------------------------------------------
conv: List[Message] = mc.conversation(user, peer)

st.subheader("Conversazione")
chat_holder = st.container(height=400)
with chat_holder:
    if not conv:
        st.markdown("_Nessun messaggio_ …")
    else:
        for msg in conv:
            align = "💬" if msg.sender == user else "📨"
            sender_tag = "<b>Io</b>" if msg.sender == user else msg.sender.email
            st.markdown(
                f"{align} {sender_tag} – {msg.sent_at.strftime('%d/%m %H:%M')}  <br>{msg.text}",
                unsafe_allow_html=True,
            )

# ---------------------------------------------------------------------------
# Input invio (solo per Specialist)
# ---------------------------------------------------------------------------
if isinstance(user, Specialist):
    st.text_area("Nuovo messaggio", key="msg_text")
    if st.button("Invia", use_container_width=True):
        text = st.session_state.msg_text.strip()
        if text:
            mc.send(sender=user, receiver=peer, text=text)
            st.session_state.msg_text = ""  # clear
            st.experimental_rerun()
else:
    st.info("Solo lo specialista può inviare messaggi. Attendi una risposta.")
