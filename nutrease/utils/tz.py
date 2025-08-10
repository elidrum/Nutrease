from __future__ import annotations

"""Timezone helpers for the Nutrease app."""

from datetime import datetime
from zoneinfo import ZoneInfo

# Central timezone for all datetimes in the app
LOCAL_TZ = ZoneInfo("Europe/Rome")


def local_now() -> datetime:
    """Return current local time with timezone information."""
    return datetime.now(tz=LOCAL_TZ)