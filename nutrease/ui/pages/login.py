from __future__ import annotations

"""nutrease.ui.pages.login  –  Pagina di **Login / Signup**.

La UI è racchiusa in `main()`, così il router di *streamlit_app.py*
può richiamarla a **ogni rerun** (evitando che il modulo venga eseguito
solo al primo import).

Flusso:

1.  Se l’utente preme **Login** → `AuthService.login()`  
    → `_init_controllers()` → redirect/rerun.
2.  Se l’utente preme **Signup** (paziente o specialista)  
    → `AuthService.signup()` → `_init_controllers()` → rerun.
3.  Dopo il login il router mostrerà le pagine “Diario” o “Dashboard”.
"""

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
    """Restituisce (o crea) l'istanza di AuthService memorizzata in sessione."""
    if "_auth" not in st.session_state:
        st.session_state._auth = AuthService()  # type: ignore[attr-defined]
    return st.session_state._auth  # type: ignore[return-value]


def _init_controllers(user: Patient | Specialist) -> None:
    """Crea i controller necessari e li salva in session_state.

    Effettua inoltre l'avvio del NotificationService (una sola volta).
    Dopo la configurazione esegue `st.experimental_rerun()` per rinfrescare
    il router principale con il nuovo ruolo.
    """
    db = Database.default()

    # Scheduler notifiche – avviato una sola volta
    notif: NotificationService | None = st.session_state.get("_notif")  # type: ignore[annotation-unchecked]
    if notif is None:
        notif = NotificationService()
        notif.start()
        st.session_state._notif = notif  # type: ignore[attr-defined]

    controllers: Dict[str, object] = {"messaging": MessagingController(db=db)}
    if isinstance(user, Patient):
        controllers["patient"] = PatientController(
            user, db=db, notification_service=notif
        )
    else:  # Specialist
        controllers["specialist"] = SpecialistController(user, db=db)

    st.session_state.controllers = controllers
    st.session_state.current_user = user

    # Feedback e rerun per far comparire il menu corretto
    st.success("Login effettuato! Ricarico la pagina…")
    st.experimental_rerun()


# ---------------------------------------------------------------------------
# MAIN PAGE
# ---------------------------------------------------------------------------


def main() -> None:  # noqa: D401 – imperative name by design
    """Renderizza la pagina di Login/Signup."""
    st.title("Nutrease – Accesso")

    main_tabs = st.tabs(["🔑 Login", "🆕 Signup"])

    # ---------------------------------------------------------------------
    # LOGIN TAB
    # ---------------------------------------------------------------------
    with main_tabs[0]:
        st.subheader("Accedi")
        email = st.text_input("E-mail", key="login_email")
        password = st.text_input("Password", type="password", key="login_pwd")

        if st.button("Login", type="primary", use_container_width=True):
            auth = _get_auth()
            try:
                user = auth.login(email, password)
                _init_controllers(user)
            except Exception as exc:
                st.error(str(exc))

    # ---------------------------------------------------------------------
    # SIGNUP TAB
    # ---------------------------------------------------------------------
    with main_tabs[1]:
        sub_tabs = st.tabs(["Paziente", "Specialista"])

        # ----------------------- paziente --------------------------------
        with sub_tabs[0]:
            st.subheader("Registrazione Paziente")
            p_name = st.text_input("Nome", key="p_name")
            p_surname = st.text_input("Cognome", key="p_surname")
            p_email = st.text_input("E-mail", key="p_email")
            p_pwd1 = st.text_input(
                "Password (≥8 alfanumerici)", type="password", key="p_pwd1"
            )
            p_pwd2 = st.text_input(
                "Conferma Password", type="password", key="p_pwd2"
            )

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

        # ----------------------- specialista -----------------------------
        with sub_tabs[1]:
            st.subheader("Registrazione Specialista")
            s_name = st.text_input("Nome", key="s_name")
            s_surname = st.text_input("Cognome", key="s_surname")
            s_email = st.text_input("E-mail", key="s_email")
            s_category = st.selectbox(
                "Categoria", [c.value for c in SpecialistCategory], key="s_cat"
            )
            s_pwd1 = st.text_input(
                "Password (≥8 alfanumerici)", type="password", key="s_pwd1"
            )
            s_pwd2 = st.text_input(
                "Conferma Password", type="password", key="s_pwd2"
            )

            if st.button(
                "Registrati come Specialista", use_container_width=True
            ):
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

    # ---------------------------------------------------------------------
    # Footer – utente già loggato
    # ---------------------------------------------------------------------
    if "current_user" in st.session_state:
        st.info(
            f"Sei loggato come **{st.session_state.current_user.email}**."
        )


# ---------------------------------------------------------------------------
# Debug standalone (facoltativo)
# ---------------------------------------------------------------------------
if __name__ == "__main__":
    # Permette di lanciare il file singolarmente: `streamlit run login.py`
    import os

    # Se lo script è eseguito direttamente, simuliamo il contesto:
    os.environ.setdefault("PYTHONHASHSEED", "0")
    main()