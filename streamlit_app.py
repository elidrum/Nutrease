from __future__ import annotations

import importlib
from types import ModuleType
from typing import Dict

import streamlit as st
from streamlit.runtime.scriptrunner import StopExecution

st.set_page_config(page_title="Nutrease", layout="wide", page_icon="🥑")

# ----------------------------------------------------------------------------
# Helpers
# ----------------------------------------------------------------------------

_PAGE_CACHE: Dict[str, ModuleType] = {}


def _lazy_import(module_path: str) -> ModuleType:
    if module_path not in _PAGE_CACHE:
        _PAGE_CACHE[module_path] = importlib.import_module(module_path)
    return _PAGE_CACHE[module_path]


def _render_page(module_path: str) -> None:
    try:
        page = _lazy_import(module_path)
        if callable(getattr(page, "main", None)):
            page.main()  # type: ignore[arg-type]
    except StopExecution:
        return

# ----------------------------------------------------------------------------
# Determine user / role from session_state
# ----------------------------------------------------------------------------
user = st.session_state.get("current_user")
role = None
if user:
    from nutrease.models.user import Patient # local import

    role = "patient" if isinstance(user, Patient) else "specialist"

# ----------------------------------------------------------------------------
# Sidebar – navigation menu
# ----------------------------------------------------------------------------

st.sidebar.image("assets/logo.png", width=200)

if role is None:
    # Only login available
    choice = st.sidebar.radio("Menu", ["Login"])
    if choice == "Login":
        _render_page("nutrease.ui.pages.login")
else:
    if role == "patient":
        items = {
            "Diario": "nutrease.ui.pages.patient_diary",
            "Specialisti": "nutrease.ui.pages.patient_specialists",
            "Chat": "nutrease.ui.pages.messaging",
            "Profilo": "nutrease.ui.pages.profile",
        }
    else:  # specialist
        items = {
            "Dashboard": "nutrease.ui.pages.specialist_dashboard",
            "Chat": "nutrease.ui.pages.messaging",
            "Profilo": "nutrease.ui.pages.profile",
        }

    choice = st.sidebar.radio("Menu", list(items.keys()))

    _render_page(items[choice])

    st.sidebar.markdown("---")
    if st.sidebar.button("Logout", type="primary", key="logout_btn"):
        st.session_state.logout_confirm = True  # type: ignore[attr-defined]
    if st.session_state.get("logout_confirm"):
        st.sidebar.warning("Sicuro di voler effettuare il logout?")
        col_yes, col_no = st.sidebar.columns(2)
        if col_yes.button("Si", key="logout_yes"):
            st.session_state.clear()
            st.rerun()
        if col_no.button("No", key="logout_no"):
            del st.session_state["logout_confirm"]

    st.sidebar.markdown(
        """
        <style>
        div[data-testid="stSidebar"] button[data-testid="baseButton-primary"] {
            background-color: #ff4b4b;
            color: white;
        }
        </style>
        """,
        unsafe_allow_html=True,
    )

