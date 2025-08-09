from __future__ import annotations

"""nutrease.ui.pages.profile – Modifica profilo utente."""

import streamlit as st

from nutrease.models.user import Patient
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

    if st.button("Salva", use_container_width=True):
        user.name = name
        user.surname = surname
        if extra is not None:
            user.profile_note = extra
        db = Database.default()
        try:
            db.save(user)
            st.success("Profilo aggiornato")
        except Exception as exc:
            st.error(str(exc))


if __name__ == "__main__":
    main()