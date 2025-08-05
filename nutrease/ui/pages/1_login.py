from __future__ import annotations

"""Streamlit page **1 – Login / Signup**.

Visualizza due tab principali:
1. **Login**
2. **Signup** – con sotto‑tab "Paziente" e "Specialista".

Flusso:
* In fase di login crea le istanze dei controller e le mette in
  ``st.session_state.controllers`` insieme all'utente loggato.
* Password validation RNF4 è già nei modelli; qui facciamo solo un feedback
  rapido prima di chiamare :class:`nutrease.services.auth_service.AuthService`.
"""

from pathlib import Path
from typing import Dict

import streamlit as st

from nutrease.controllers.messaging_controller import MessagingController
from nutrease.controllers.patient_controller import PatientController
from nutrease.controllers.specialist_controller import SpecialistController
from nutrease.models.enums import SpecialistCategory
from nutrease.models.user import Patient, Specialist
from nutrease.services.auth_service import AuthService
from nutrease.services.notification_service import NotificationService
from nutrease.utils.database import Database

# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def _get_auth() -> AuthService:
    # In questa demo AuthService usa ancora repo in‑memory; Database servirà ai
    # controller. Se in futuro AuthService avrà un repo DB, basterà passarglielo.
    if "_auth" not in st.session_state:
        st.session_state._auth = AuthService()
    return st.session_state._auth  # type: ignore[attr-defined]


def _init_controllers(user: Patient | Specialist) -> None:
    """Crea controller appropriati e li salva in session_state."""
    db = Database.default()
    notif: NotificationService | None = st.session_state.get("_notif")  # type: ignore[annotation-unchecked]
    if notif is None:
        notif = NotificationService()
        notif.start()
        st.session_state._notif = notif  # type: ignore[attr-defined]

    controllers: Dict[str, object] = {}
    controllers["messaging"] = MessagingController(db=db)
    if isinstance(user, Patient):
        controllers["patient"] = PatientController(user, db=db, notification_service=notif)
    else:
        controllers["specialist"] = SpecialistController(user, db=db)
    st.session_state.controllers = controllers
    st.session_state.current_user = user

    st.success("Login effettuato! Ricarico la pagina…")
    st.experimental_rerun()


# ---------------------------------------------------------------------------
# UI – Tabs
# ---------------------------------------------------------------------------

st.title("Nutrease – Accesso")

main_tabs = st.tabs(["🔑 Login", "🆕 Signup"])

# .........................................................................
# LOGIN TAB
# .........................................................................
with main_tabs[0]:
    st.subheader("Accedi")
    email = st.text_input("E‑mail", key="login_email")
    password = st.text_input("Password", type="password", key="login_pwd")

    if st.button("Login", type="primary", use_container_width=True):
        auth = _get_auth()
        try:
            user = auth.login(email, password)
            _init_controllers(user)
        except Exception as exc:
            st.error(str(exc))

# .........................................................................
# SIGNUP TAB
# .........................................................................
with main_tabs[1]:
    sub_tabs = st.tabs(["Paziente", "Specialista"])

    # ----------------------- paziente ------------------------------------
    with sub_tabs[0]:
        st.subheader("Registrazione Paziente")
        p_name = st.text_input("Nome", key="p_name")
        p_surname = st.text_input("Cognome", key="p_surname")
        p_email = st.text_input("E‑mail", key="p_email")
        p_pwd1 = st.text_input("Password (≥8 alfanumerici)", type="password", key="p_pwd1")
        p_pwd2 = st.text_input("Conferma Password", type="password", key="p_pwd2")

        if st.button("Registrati come Paziente", use_container_width=True):
            if p_pwd1 != p_pwd2:
                st.error("Le password non coincidono.")
            else:
                auth = _get_auth()
                try:
                    user = auth.signup(
                        p_email,
                        p_pwd1,
                        role="PATIENT",
                        name=p_name,
                        surname=p_surname,
                    )
                    st.success("Registrazione riuscita! Eseguo login…")
                    _init_controllers(user)  # type: ignore[arg-type]
                except Exception as exc:
                    st.error(str(exc))

    # ----------------------- specialista ----------------------------------
    with sub_tabs[1]:
        st.subheader("Registrazione Specialista")
        s_name = st.text_input("Nome", key="s_name")
        s_surname = st.text_input("Cognome", key="s_surname")
        s_email = st.text_input("E‑mail", key="s_email")
        s_category = st.selectbox("Categoria", [c.value for c in SpecialistCategory], key="s_cat")
        s_pwd1 = st.text_input("Password (≥8 alfanumerici)", type="password", key="s_pwd1")
        s_pwd2 = st.text_input("Conferma Password", type="password", key="s_pwd2")

        if st.button("Registrati come Specialista", use_container_width=True):
            if s_pwd1 != s_pwd2:
                st.error("Le password non coincidono.")
            else:
                auth = _get_auth()
                try:
                    user = auth.signup(
                        s_email,
                        s_pwd1,
                        role="SPECIALIST",
                        name=s_name,
                        surname=s_surname,
                        category=SpecialistCategory.from_str(s_category),
                    )
                    st.success("Registrazione riuscita! Eseguo login…")
                    _init_controllers(user)  # type: ignore[arg-type]
                except Exception as exc:
                    st.error(str(exc))

# ---------------------------------------------------------------------------
# Footer – link rapido se utente già loggato
# ---------------------------------------------------------------------------
if "current_user" in st.session_state:
    st.info(f"Sei loggato come **{st.session_state.current_user.email}**.")