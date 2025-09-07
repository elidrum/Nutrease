from __future__ import annotations

"""nutrease.ui.pages.messaging – Pagina **Chat Paziente-Specialista**.

* Paziente e specialista possono inviare messaggi.
* La conversazione è salvata dentro la relativa :class:`LinkRequest`.
"""

from typing import List

import streamlit as st

from nutrease.controllers.patient_controller import PatientController
from nutrease.controllers.specialist_controller import SpecialistController
from nutrease.models.communication import Message
from nutrease.models.user import Specialist

# MAIN ENTRY
# ---------------------------------------------------------------------------


def main() -> None:  # noqa: D401 – imperative name by design
    """Renderizza la pagina di chat."""
    controllers = st.session_state.get("controllers", {})
    user = st.session_state.get("current_user")

    if user is None:
        st.error("Effettua prima il login.")
        st.stop()

    st.title("💬 Chat Paziente-Specialista")

    if isinstance(user, Specialist):
        sc: SpecialistController | None = controllers.get("specialist")  # type: ignore[assignment]
        if sc is None:
            st.error("Controller non disponibile.")
            st.stop()
        conns = sc.connections()
        if not conns:
            st.info("Nessuna connessione disponibile.")
            st.stop()
        labels = [
            f"{lr.patient.name} {lr.patient.surname} ({lr.patient.email})"
            for lr in conns
        ]
        sel = st.selectbox("Seleziona paziente", labels)
        link = conns[labels.index(sel)]
    else:
        pc: PatientController | None = controllers.get("patient")  # type: ignore[assignment]
        if pc is None:
            st.error("Controller non disponibile.")
            st.stop()
        conns = pc.connections()
        if not conns:
            st.info("Nessuna connessione disponibile.")
            st.stop()
        labels = [
            f"{lr.specialist.name} {lr.specialist.surname} ({lr.specialist.email})"
            for lr in conns
        ]
        sel = (
            st.selectbox("Seleziona specialista", labels)
            if len(conns) > 1
            else labels[0]
        )
        link = conns[labels.index(sel)] if len(conns) > 1 else conns[0]

    # ------------------ conversazione ----------------------------------
    conv: List[Message] = sorted(link.messages, key=lambda m: m.sent_at)

    st.subheader("Conversazione")
    chat_holder = st.container(height=400)
    with chat_holder:
        if not conv:
            st.markdown("_Nessun messaggio …_")
        else:
            for msg in conv:
                align = "💬" if msg.sender == user else "📨"
                sender_tag = "<b>Io</b>" if msg.sender == user else msg.sender.email
                ts = msg.sent_at.strftime("%d/%m %H:%M")
                st.markdown(
                    f"{align} {sender_tag} – {ts}<br>{msg.text}",
                    unsafe_allow_html=True,
                )

    # ------------------ invio ------------------------------------------
    st.text_area("Nuovo messaggio", key="msg_text")
    if st.button("Invia", use_container_width=True):
        text = st.session_state.msg_text.strip()
        if text:
            try:
                if isinstance(user, Specialist):
                    sc.send_message(link.patient, text)
                else:
                    pc.send_message(link.specialist, text)
                st.session_state.pop("msg_text", None)
                st.rerun()
            except Exception:  # pragma: no cover - best effort
                st.error(
                    "Errore interno durante l'invio del messaggio. "
                    "Riprova più tardi."
                )


# ---------------------------------------------------------------------------
# Permette di lanciare la pagina standalone per debug
# ---------------------------------------------------------------------------
if __name__ == "__main__":
    main()