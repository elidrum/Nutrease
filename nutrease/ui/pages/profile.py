from __future__ import annotations

"""nutrease.ui.pages.profile – Modifica profilo utente."""

import streamlit as st

from nutrease.models.user import Patient, Specialist
from nutrease.services.auth_service import AuthService
from nutrease.utils.database import Database


def main() -> None:  # noqa: D401
    user = st.session_state.get("current_user")
    if user is None:
        st.error("Effettua prima il login.")
        st.stop()

    st.title("👤 Profilo")
    name = st.text_input("Nome", user.name)
    surname = st.text_input("Cognome", user.surname)

    extra = None
    if isinstance(user, Patient):
        extra = st.text_area("Note personali", user.profile_note)
    elif isinstance(user, Specialist):
        extra = st.text_area("Informazioni professionali", user.bio)

    if st.button("Salva", use_container_width=True):
        user.name = name
        user.surname = surname
        if extra is not None:
            if isinstance(user, Patient):
                user.profile_note = extra
            else:
                user.bio = extra
        db = Database.default()
        try:
            db.save(user)
            st.success("Profilo aggiornato")
        except Exception as exc:
            st.error(str(exc))

    st.markdown("---")
    st.subheader("Cambia password")
    with st.form("pwd_form"):
        old_pw = st.text_input("Password attuale", type="password")
        new_pw = st.text_input("Nuova password", type="password")
        new_pw2 = st.text_input("Conferma nuova password", type="password")
        if st.form_submit_button("Aggiorna password"):
            if new_pw != new_pw2:
                st.error("Le nuove password non coincidono.")
            else:
                auth = AuthService(db=Database.default())
                try:
                    auth.change_password(user.email, old_pw, new_pw)
                    st.success("Password aggiornata")
                except Exception as exc:
                    st.error(str(exc))

    st.markdown("---")
    st.subheader("Elimina account")
    if st.button("Elimina account", type="primary"):
        st.session_state.delete_confirm = True  # type: ignore[attr-defined]
    if st.session_state.get("delete_confirm"):
        st.warning("Questa azione eliminerà definitivamente l'account e tutti i dati.")
        col_yes, col_no = st.columns(2)
        if col_yes.button("Si, elimina"):
            auth = AuthService(db=Database.default())
            try:
                auth.delete_account(user.email)
                st.session_state.clear()
                st.success("Account eliminato")
                st.rerun()
            except Exception as exc:
                st.error(str(exc))
        if col_no.button("Annulla"):
            del st.session_state["delete_confirm"]


if __name__ == "__main__":
    main()