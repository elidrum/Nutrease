from __future__ import annotations

"""Very lightweight persistence layer over TinyDB (JSON).

Provides a singleton :class:`Database` with generic CRUD helpers. Controllers
can receive an instance to persist entities; if omitted they fall back to
in‑memory behaviour useful during unit tests.
"""

from dataclasses import asdict, is_dataclass
from pathlib import Path
from threading import Lock
from typing import Any, Dict, List, Type, TypeVar

from tinydb import Query, TinyDB

__all__ = ["Database"]

T = TypeVar("T")


class Database:  # noqa: D101 – documented above
    _default: "Database | None" = None

    def __init__(self, path: str | Path = "nutrease_db.json") -> None:
        self.path = Path(path).expanduser()
        self._db = TinyDB(self.path)
        self._lock = Lock()

    # Singleton -----------------------------------------------------------
    @classmethod
    def default(cls) -> "Database":
        if cls._default is None:
            cls._default = cls()
        return cls._default

    # Internal ------------------------------------------------------------
    def _table(self, model_or_name: str | Type[Any]):
        name = model_or_name if isinstance(model_or_name, str) else model_or_name.__name__
        return self._db.table(name)

    # CRUD ----------------------------------------------------------------
    def save(self, obj: Any) -> int:
        data = asdict(obj) if is_dataclass(obj) else obj.__dict__.copy()
        data["__type__"] = obj.__class__.__name__
        with self._lock:
            return self._table(obj.__class__).insert(data)

    def all(self, model: Type[T]) -> List[Dict[str, Any]]:
        return self._table(model).all()

    def search(self, model: Type[T], **filters: Any) -> List[Dict[str, Any]]:
        tbl = self._table(model)
        q = Query()
        cond = None
        for k, v in filters.items():
            clause = q[k] == v
            cond = clause if cond is None else (cond & clause)
        return tbl.search(cond) if cond else []

    def delete(self, model: Type[T], **filters: Any) -> int:
        tbl = self._table(model)
        q = Query()
        cond = None
        for k, v in filters.items():
            clause = q[k] == v
            cond = clause if cond is None else (cond & clause)
        with self._lock:
            return tbl.remove(cond)

    def clear(self) -> None:
        with self._lock:
            self._db.truncate()

    def __repr__(self) -> str:
        return f"<Database path='{self.path.name}'>"
