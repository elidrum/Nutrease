from __future__ import annotations

"""nutrease.ui.pages.patient_specialists – Gestione collegamenti con specialisti."""

import streamlit as st

from nutrease.controllers.patient_controller import PatientController
from nutrease.models.communication import LinkRequestState
from nutrease.models.user import Specialist
from nutrease.utils.database import Database


def main() -> None:  # noqa: D401 – imperative
    controllers = st.session_state.get("controllers", {})
    pc: PatientController | None = controllers.get("patient")
    if pc is None:
        st.error("Devi prima effettuare il login come paziente.")
        st.stop()

    st.title("🩺 Specialisti")
    tabs = st.tabs(["Tutti", "I miei specialisti"])

    db = Database.default()
    rows = db.all(Specialist)
    specialists = [
        Specialist(**{k: v for k, v in r.items() if not k.startswith("__")})
        for r in rows
    ]

    # --------------------- elenco completo ------------------------------
    with tabs[0]:
        for spec in specialists:
            st.markdown(f"### {spec.name} {spec.surname} – {spec.category.value}")
            existing = next(
                (
                    lr
                    for lr in pc._iter_link_requests()  # type: ignore[attr-defined]
                    if lr.specialist.email == spec.email
                ),
                None,
            )
            if existing:
                if existing.state == LinkRequestState.ACCEPTED:
                    st.success("Collegato")
                elif existing.state == LinkRequestState.PENDING:
                    st.info("Richiesta in attesa")
                else:
                    st.error("Richiesta rifiutata")
            else:
                if st.button("Richiedi collegamento", key=f"req_{spec.email}"):
                    try:
                        pc.send_link_request(spec)
                        st.success("Richiesta inviata")
                        st.rerun()
                    except Exception as exc:
                        st.error(str(exc))

    # --------------------- specialisti collegati ------------------------
    with tabs[1]:
        linked = [
            lr.specialist
            for lr in pc._iter_link_requests()  # type: ignore[attr-defined]
            if lr.state == LinkRequestState.ACCEPTED
        ]
        if not linked:
            st.info("Nessuno specialista collegato.")
        for spec in linked:
            with st.container(border=True):
                st.markdown(f"**{spec.name} {spec.surname}** – {spec.category.value}")
                if st.button("Scollega", key=f"un_{spec.email}"):
                    try:
                        pc.remove_link(spec)
                        st.warning("Specialista scollegato")
                        st.rerun()
                    except Exception as exc:
                        st.error(str(exc))


if __name__ == "__main__":
    main()