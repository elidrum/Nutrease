from __future__ import annotations

"""nutrease.ui.pages.login – Pagina di **Login / Signup**.

La UI è racchiusa in `main()`, così il router di *streamlit_app.py*
può richiamarla a **ogni rerun** (evitando che il modulo venga eseguito
solo al primo import).

Flusso:

1.  **Login** -> `AuthService.login()` -> `_init_controllers()` -> `st.rerun()`
2.  **Signup** -> `AuthService.signup()` -> idem sopra
3.  Dopo il login il router mostrerà “Diario” (paziente) o “Dashboard”
   (specialista) finché l’utente non clicca «Logout».
"""

import re
from typing import Dict

import streamlit as st

from nutrease.controllers.patient_controller import PatientController
from nutrease.controllers.specialist_controller import SpecialistController
from nutrease.models.enums import SpecialistCategory
from nutrease.models.user import Patient, Specialist, _validate_password
from nutrease.services.auth_service import AuthService
from nutrease.services.notification_service import NotificationService
from nutrease.utils.database import Database
from nutrease.ui.i18n import format_specialist_category

EMAIL_RE = re.compile(r"^[^@\s]+@[^@\s]+\.[^@\s]+$")


# ---------------------------------------------------------------------------
# Helper
# ---------------------------------------------------------------------------
def _clean_email(raw: str) -> str:
    """Rimuove spazi, newline e caratteri invisibili da e-mail incollate."""
    return raw.strip().replace("\u200b", "")


def _get_auth() -> AuthService:
    if "_auth" not in st.session_state:
        st.session_state._auth = AuthService()  # type: ignore[attr-defined]
    return st.session_state._auth  # type: ignore[return-value]


def _init_controllers(user: Patient | Specialist) -> None:
    """Crea controller, salva l’utente e (una sola volta) avvia il notifier."""
    db = Database.default()

    notif: NotificationService | None = st.session_state.get("_notif")  # type: ignore[annotation-unchecked]
    if notif is None:
        notif = NotificationService()
        notif.start()
        st.session_state._notif = notif  # type: ignore[attr-defined]

    # Evita di ricreare i controller se già presenti (es. rerun superfluo)
    if "controllers" not in st.session_state:
        controllers: Dict[str, object] = {}
        if isinstance(user, Patient):
            controllers["patient"] = PatientController(
                user, db=db, notification_service=notif
            )
        else:
            controllers["specialist"] = SpecialistController(user, db=db)
        st.session_state.controllers = controllers

    st.session_state.current_user = user
    st.success("Login effettuato! Ricarico la pagina…")
    st.rerun()


# ---------------------------------------------------------------------------
# Page
# ---------------------------------------------------------------------------


def main() -> None:  # noqa: D401
    st.title("Nutrease – Accesso")
    main_tabs = st.tabs(["🔑 Login", "🆕 Signup"])

    # ------------------------------------------------------------------ #
    # LOGIN TAB                                                          #
    # ------------------------------------------------------------------ #
    with main_tabs[0]:
        st.subheader("Accedi")
        email = _clean_email(st.text_input("E-mail", key="login_email"))
        password = st.text_input("Password", type="password", key="login_pwd")

        if st.button("Login", type="primary", use_container_width=True):
            if not email:
                st.error("Inserisci l’e-mail.")
            elif not EMAIL_RE.match(email):
                st.error("E-mail non valida.")
            elif not password:
                st.error("Inserisci la password.")
            else:
                try:
                    user = _get_auth().login(email, password)
                    _init_controllers(user)
                except Exception as exc:
                    st.error(str(exc))

    # ------------------------------------------------------------------ #
    # SIGNUP TAB                                                         #
    # ------------------------------------------------------------------ #
    with main_tabs[1]:
        sub_tabs = st.tabs(["Paziente", "Specialista"])

        # -------------------- Paziente ---------------------------------
        with sub_tabs[0]:
            st.subheader("Registrazione Paziente")
            p_name_input = st.text_input("Nome", key="p_name")
            p_surname_input = st.text_input("Cognome", key="p_surname")
            p_email = _clean_email(st.text_input("E-mail", key="p_email"))
            p_pwd1 = st.text_input(
                (
                    "Password (≥8 alfanumerici, "
                    "almeno 1 numero e 1 lettera maiuscola)"
                ),
                type="password",
                key="p_pwd1",
            )
            p_pwd2 = st.text_input("Conferma Password", type="password", key="p_pwd2")

            if st.button("Registrati come Paziente", use_container_width=True):
                p_name = p_name_input.strip()
                p_surname = p_surname_input.strip()
                if not p_name:
                    st.error("Inserisci il nome.")
                elif not p_surname:
                    st.error("Inserisci il cognome.")
                elif not EMAIL_RE.match(p_email):
                    st.error("E-mail non valida.")
                elif p_pwd1 != p_pwd2:
                    st.error("Le password non coincidono.")
                else:
                    try:
                        _validate_password(p_pwd1)
                    except ValueError as exc:
                        st.error(str(exc))
                    else:
                        try:
                            user = _get_auth().signup(
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

         # -------------------- Specialista ------------------------------
        with sub_tabs[1]:
            st.subheader("Registrazione Specialista")
            s_name_input = st.text_input("Nome", key="s_name")
            s_surname_input = st.text_input("Cognome", key="s_surname")
            s_email = _clean_email(st.text_input("E-mail", key="s_email"))
            category_options = list(SpecialistCategory)
            if "s_cat" not in st.session_state:
                st.session_state.s_cat = category_options[0]
            elif isinstance(st.session_state.s_cat, str):
                try:
                    st.session_state.s_cat = SpecialistCategory.from_str(
                        st.session_state.s_cat
                    )
                except ValueError:
                    st.session_state.s_cat = category_options[0]
            s_category = st.selectbox(
                "Categoria",
                category_options,
                key="s_cat",
                format_func=format_specialist_category,
            )
            s_pwd1 = st.text_input(
                (
                    "Password (≥8 alfanumerici, "
                    "almeno 1 numero e 1 lettera maiuscola)"
                ),
                type="password",
                key="s_pwd1",
            )
            s_pwd2 = st.text_input("Conferma Password", type="password", key="s_pwd2")

            if st.button("Registrati come Specialista", use_container_width=True):
                s_name = s_name_input.strip()
                s_surname = s_surname_input.strip()
                if not s_name:
                    st.error("Inserisci il nome.")
                elif not s_surname:
                    st.error("Inserisci il cognome.")
                elif not EMAIL_RE.match(s_email):
                    st.error("E-mail non valida.")
                elif s_pwd1 != s_pwd2:
                    st.error("Le password non coincidono.")
                else:
                    try:
                        _validate_password(s_pwd1)
                    except ValueError as exc:
                        st.error(str(exc))
                    else:
                        try:
                            user = _get_auth().signup(
                                s_email,
                                s_pwd1,
                                role="SPECIALIST",
                                name=s_name,
                                surname=s_surname,
                                category=s_category,
                            )
                            st.success("Registrazione riuscita! Eseguo login…")
                            _init_controllers(user)  # type: ignore[arg-type]
                        except Exception as exc:
                            st.error(str(exc))

    # ------------------------------------------------------------------ #
    # Footer – già loggato                                               #
    # ------------------------------------------------------------------ #
    if "current_user" in st.session_state and st.session_state.current_user:
        st.info(f"Sei loggato come **{st.session_state.current_user.email}**.")


# ---------------------------------------------------------------------------
# Debug standalone
# ---------------------------------------------------------------------------
if __name__ == "__main__":
    import os

    os.environ.setdefault("PYTHONHASHSEED", "0")
    main()