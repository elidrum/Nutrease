from __future__ import annotations

"""Very lightweight persistence layer over TinyDB (JSON)."""

from dataclasses import asdict, is_dataclass
from datetime import date, datetime
from enum import Enum
from pathlib import Path
from threading import Lock
from typing import Any, Dict, List, Type, TypeVar, overload

from tinydb import Query, TinyDB

T = TypeVar("T")


class Database:
    _default: "Database | None" = None

    # ------------------------- init / singleton -------------------------
    def __init__(self, path: str | Path = "nutrease_db.json") -> None:
        self.path = Path(path).expanduser()
        # Pretty-print JSON with indentation
        # so data isn't stored on a single line
        self._db = TinyDB(self.path, indent=2)
        self._lock = Lock()

    @classmethod
    def default(cls) -> "Database":
        if cls._default is None:
            cls._default = cls()
        return cls._default

    # ------------------------- internals --------------------------------
    def _table(self, model_or_name: str | Type[Any]):
        if isinstance(model_or_name, str):
            name = model_or_name
        else:
            name = model_or_name.__name__
        return self._db.table(name)

    # ------------------------- CRUD -------------------------------------
    def _obj_to_dict(self, obj: Any) -> Dict[str, Any]:
        """Serialise dataclass *obj* into a JSON-friendly ``dict``.

        ``dataclasses.asdict`` already converts nested dataclasses to dictionaries,
        but values like :class:`datetime.datetime`, :class:`datetime.date` or
        :class:`enum.Enum` still need to be transformed in order to be serialised
        by :func:`json.dumps`.  This helper walks the data structure recursively
        and sanitises any unsupported types so that TinyDB's JSON storage can
        persist them without errors.
        """

        def _sanitise(value: Any) -> Any:
            if isinstance(value, (datetime, date)):
                return value.isoformat()
            if isinstance(value, Enum):
                return value.value
            if is_dataclass(value):
                return _sanitise(asdict(value))
            if isinstance(value, dict):
                return {k: _sanitise(v) for k, v in value.items()}
            if isinstance(value, (list, tuple, set)):
                return [_sanitise(v) for v in value]
            return value

        raw = asdict(obj) if is_dataclass(obj) else obj.__dict__.copy()
        data = _sanitise(raw)
        data["__type__"] = obj.__class__.__name__
        return data

    # -------- save / upsert --------
    def save(self, obj: Any) -> int:
        """Insert **or update** by primary key ``id`` (if present)."""
        data = self._obj_to_dict(obj)
        tbl = self._table(obj.__class__)

        with self._lock:
            if "id" in data and data["id"] != 0:
                q = Query()
                existing = tbl.get(q.id == data["id"])
                if existing:
                    tbl.update(data, q.id == data["id"])
                    return existing.doc_id
            # fallback: insert new doc
            return tbl.insert(data)

    # -------- read helpers ----------
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

    # -------- delete --------
    @overload
    def delete(self, model: Type[T], **filters: Any) -> int: ...  # noqa: D401

    @overload
    def delete(self, obj: T) -> int: ...  # noqa: D401

    def delete(self, *args, **kwargs):  # type: ignore[override]
        """Rimuove per filtri oppure passando direttamente l’oggetto."""
        if len(args) == 1 and not kwargs:
            obj = args[0]
            model = type(obj)
            if hasattr(obj, "id"):
                kwargs = {"id": obj.id}
            else:  # fallback: rimuovi per doc_id se presente
                return self._table(model).remove(doc_ids=[obj.doc_id])
        else:
            model = args[0]
        tbl = self._table(model)
        q = Query()
        cond = None
        for k, v in kwargs.items():
            clause = q[k] == v
            cond = clause if cond is None else (cond & clause)
        with self._lock:
            return tbl.remove(cond)

    # -------- misc ----------
    def clear(self) -> None:
        with self._lock:
            self._db.truncate()

    def __repr__(self) -> str:
        return f"<Database path='{self.path.name}'>"