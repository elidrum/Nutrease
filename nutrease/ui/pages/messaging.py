from __future__ import annotations

"""nutrease.ui.pages.messaging – Pagina **Chat Paziente-Specialista**.

* Solo lo **Specialista** può inviare nuovi messaggi.
* Il **Paziente** vede la conversazione in sola lettura.
* Usa :class:`MessagingController` già presente in ``st.session_state.controllers``.
"""

from datetime import datetime
from typing import List

import streamlit as st

from nutrease.controllers.messaging_controller import MessagingController
from nutrease.controllers.specialist_controller import SpecialistController
from nutrease.models.communication import Message
from nutrease.models.user import Patient, Specialist


# ---------------------------------------------------------------------------
# MAIN ENTRY
# ---------------------------------------------------------------------------


def main() -> None:  # noqa: D401 – imperative name by design
    """Renderizza la pagina di chat."""
    # Recupera controller e utente
    controllers = st.session_state.get("controllers", {})
    mc: MessagingController | None = controllers.get("messaging")  # type: ignore[assignment]
    user = st.session_state.get("current_user")

    if mc is None or user is None:
        st.error("Effettua prima il login.")
        st.stop()

    st.title("💬 Chat Paziente-Specialista")

    # ------------------ helper interno ----------------------------------
    def _get_peer() -> Specialist | Patient | None:
        """Restituisce la controparte collegata all'utente corrente."""
        if isinstance(user, Specialist):
            # Specialista: seleziona un paziente collegato
            sc: SpecialistController | None = controllers.get("specialist")  # type: ignore[assignment]
            if sc is None:
                return None
            accepted = [
                lr.patient
                for lr in sc._iter_link_requests()  # type: ignore[attr-defined]
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
            linked = [
                lr.specialist
                for lr in pc._iter_link_requests()  # type: ignore[attr-defined]
                if lr.state.value == "ACCEPTED"
            ]
            return linked[0] if linked else None

    # -------------------------------------------------------------------
    peer = _get_peer()
    if peer is None:
        st.info("Nessuna connessione disponibile.")
        st.stop()

    # ------------------ conversazione ----------------------------------
    conv: List[Message] = mc.conversation(user, peer)

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

    # ------------------ invio (solo specialista) -----------------------
    if isinstance(user, Specialist):
        st.text_area("Nuovo messaggio", key="msg_text")
        if st.button("Invia", use_container_width=True):
            text = st.session_state.msg_text.strip()
            if text:
                mc.send(sender=user, receiver=peer, text=text)
                st.session_state.msg_text = ""  # clear
                st.rerun()
    else:
        st.info("Solo lo specialista può inviare messaggi. Attendi una risposta.")


# ---------------------------------------------------------------------------
# Permette di lanciare la pagina standalone per debug
# ---------------------------------------------------------------------------
if __name__ == "__main__":
    main()