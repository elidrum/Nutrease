from __future__ import annotations

"""nutrease.ui.pages.patient_specialists – Gestione collegamenti con specialisti."""

import streamlit as st

from nutrease.controllers.patient_controller import PatientController
from nutrease.models.enums import LinkRequestState, SpecialistCategory
from nutrease.models.user import Specialist
from nutrease.utils.database import Database
from nutrease.ui.i18n import format_specialist_category



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
    seen: set[str] = set()
    specialists: list[Specialist] = []
    for r in rows:
        email = r.get("email")
        if email in seen:
            continue
        seen.add(email)
        data = {k: v for k, v in r.items() if not k.startswith("__")}
        category = data.get("category")
        if isinstance(category, str):
            try:
                data["category"] = SpecialistCategory.from_str(category)
            except ValueError:
                pass
        specialists.append(Specialist(**data))


    # --------------------- elenco completo ------------------------------
    with tabs[0]:
        for spec in specialists:
            with st.expander(
                f"{spec.name} {spec.surname} – {format_specialist_category(spec.category)}",
                expanded=False,
            ):
                st.markdown(spec.bio or "_Nessuna informazione disponibile._")
                link = next(
                    (lr for lr in pc.connections() if lr.specialist.email == spec.email),
                    None,
                )
                if link:
                    st.success("Collegato")
                else:
                    existing = next(
                        (
                            lr
                            for lr in pc._iter_link_requests()  # type: ignore[attr-defined]
                            if lr.specialist.email == spec.email
                        ),
                        None,
                    )
                    if existing:
                        if existing.state == LinkRequestState.PENDING:
                            st.info("Richiesta in attesa")
                        elif existing.state == LinkRequestState.REJECTED:
                            st.error("Richiesta rifiutata")
                        else:
                            st.success("Collegato")
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
        linked = [lr.specialist for lr in pc.connections()]
        if not linked:
            st.info("Nessuno specialista collegato.")
        for spec in linked:
            with st.expander(
                f"{spec.name} {spec.surname} – {format_specialist_category(spec.category)}",
                expanded=False,
            ):
                st.markdown(spec.bio or "_Nessuna informazione disponibile._")
                if st.button("Scollega", key=f"un_{spec.email}"):
                    try:
                        pc.remove_link(spec)
                        st.warning("Specialista scollegato")
                        st.rerun()
                    except Exception as exc:
                        st.error(str(exc))


if __name__ == "__main__":
    main()