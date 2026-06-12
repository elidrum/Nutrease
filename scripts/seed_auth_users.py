#!/usr/bin/env python3
"""
NOTA: script utilizzato per collegare gli utenti demo presenti con la tabella auth.users di Supabase,
in modo da poter usare l'autenticazione email/password



seed_auth_users.py


Bridging automatico fra le tabelle ruolo (paziente / specialista / segretaria)
e il sistema auth di Supabase.

Per ogni riga di `demo_accounts.csv` esegue 3 operazioni:
  1. Crea l'utente in auth.users (POST /auth/v1/admin/users).
  2. UPDATE <tabella_ruolo> SET auth_uid = <uuid> WHERE "CodiceFiscale" = <cf>.
  3. UPSERT profilo_utente(auth_uid, ruolo, codice_fiscale).

Lo script è IDEMPOTENTE: se un utente è già registrato, recupera il suo
auth_uid e procede con le altre 2 operazioni. Si può rieseguire tutte le
volte che serve senza duplicati.

USO
----
  export SUPABASE_URL='https://xxxxxxxxxxxxx.supabase.co'
  export SUPABASE_SERVICE_ROLE_KEY='eyJhbGci...'   # service_role, NON anon!
  # facoltativo:
  export SEED_PASSWORD='Nutrease2026!'             # default password per tutti
  export SEED_DRY_RUN=1                            # mostra cosa farebbe ma non tocca nulla

  python3 seed_auth_users.py

AVVERTENZA
----------
La SERVICE_ROLE_KEY bypassa la Row Level Security. Trattala come una
password admin: non committarla, non incollarla in chat, non metterla
nell'app mobile.

Al termine delle prove demo puoi REVOCARLA da Supabase Dashboard
> Project Settings > API > Roll service_role secret.

"""

import csv
import json
import os
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

# ============================================================
# Config
# ============================================================

SUPABASE_URL = os.environ.get("SUPABASE_URL", "").rstrip("/")
SERVICE_KEY = os.environ.get("SUPABASE_SERVICE_ROLE_KEY", "")
DEFAULT_PASSWORD = os.environ.get("SEED_PASSWORD", "Nutrease2026!")
DRY_RUN = os.environ.get("SEED_DRY_RUN", "0") == "1"

CSV_PATH = Path(__file__).parent / "demo_accounts.csv"

# Mapping ruolo -> tabella DB. I nomi PascalCase quotati devono matchare
# esattamente lo schema attuale su Supabase (vedi nutreaseDatabase.sql).
ROLE_TABLE = {
    "paziente": "paziente",
    "specialista": "specialista",
    "segretaria": "segretaria",
}


# ============================================================
# HTTP helper (solo stdlib — niente pip install)
# ============================================================

def api(method: str, path: str, body=None, extra_headers=None):
    """Chiamata HTTP alla Supabase API (auth admin o Postgrest)."""
    if not SUPABASE_URL or not SERVICE_KEY:
        raise SystemExit(
            "❌ Imposta SUPABASE_URL e SUPABASE_SERVICE_ROLE_KEY nell'ambiente."
        )

    url = f"{SUPABASE_URL}{path}"
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(url, method=method, data=data)
    req.add_header("apikey", SERVICE_KEY)
    req.add_header("Authorization", f"Bearer {SERVICE_KEY}")
    req.add_header("Content-Type", "application/json")
    req.add_header("Accept", "application/json")
    for k, v in (extra_headers or {}).items():
        req.add_header(k, v)

    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            raw = resp.read().decode()
            return resp.status, (json.loads(raw) if raw else None)
    except urllib.error.HTTPError as e:
        raw = e.read().decode()
        try:
            return e.code, json.loads(raw)
        except Exception:
            return e.code, {"_raw": raw}


# ============================================================
# Step 1 — auth.users
# ============================================================

def create_or_get_auth_user(email: str, password: str) -> tuple[str, str]:
    """Ritorna (user_id, status) dove status è 'created' o 'exists'."""
    if DRY_RUN:
        return ("00000000-0000-0000-0000-000000000000", "dry-run")

    status, body = api(
        "POST", "/auth/v1/admin/users",
        {"email": email, "password": password, "email_confirm": True},
    )
    if status in (200, 201) and isinstance(body, dict) and body.get("id"):
        return body["id"], "created"

    # Già registrato? Cerca per email.
    # GoTrue accetta ?email=<email> su /admin/users (oppure filtro server-side).
    q = urllib.parse.urlencode({"email": email})
    status2, body2 = api("GET", f"/auth/v1/admin/users?{q}")

    users = []
    if isinstance(body2, dict) and "users" in body2:
        users = body2["users"]
    elif isinstance(body2, list):
        users = body2

    matches = [u for u in users if (u.get("email") or "").lower() == email.lower()]
    if matches:
        return matches[0]["id"], "exists"

    raise RuntimeError(f"create auth user fallito [{status}]: {body}")


# ============================================================
# Step 2 — UPDATE paziente|specialista|segretaria SET auth_uid
# ============================================================

def link_role_row(table: str, cf: str, user_id: str) -> str:
    """PATCH Postgrest. Ritorna 'linked' / 'cf_not_found' / 'already_linked'."""
    if DRY_RUN:
        return "dry-run"

    # Nome colonna PascalCase quotata nel DB: Postgrest la richiede senza virgolette
    # ma case-sensitive.
    path = f'/rest/v1/{table}?CodiceFiscale=eq.{urllib.parse.quote(cf)}'
    status, body = api(
        "PATCH", path,
        {"auth_uid": user_id},
        extra_headers={"Prefer": "return=representation"},
    )

    if status in (200, 204):
        rows = body if isinstance(body, list) else []
        if not rows:
            return "cf_not_found"
        prev = rows[0].get("auth_uid")
        if prev and prev == user_id:
            return "already_linked"
        return "linked"

    raise RuntimeError(f"update {table} fallito [{status}]: {body}")


# ============================================================
# Step 3 — UPSERT profilo_utente
# ============================================================

def upsert_profilo(user_id: str, ruolo: str, cf: str) -> str:
    """Insert con ON CONFLICT via header Postgrest. Ritorna 'inserted' | 'updated'."""
    if DRY_RUN:
        return "dry-run"

    # Prima tento INSERT pulito
    status, body = api(
        "POST", "/rest/v1/profilo_utente",
        {"auth_uid": user_id, "ruolo": ruolo, "codice_fiscale": cf},
        extra_headers={
            "Prefer": "return=representation,resolution=merge-duplicates",
        },
    )

    if status in (200, 201):
        return "inserted"
    if status == 409:
        # Conflict → update esplicito
        path = f"/rest/v1/profilo_utente?auth_uid=eq.{user_id}"
        status2, body2 = api(
            "PATCH", path,
            {"ruolo": ruolo, "codice_fiscale": cf},
            extra_headers={"Prefer": "return=representation"},
        )
        if status2 in (200, 204):
            return "updated"
        raise RuntimeError(f"patch profilo_utente fallito [{status2}]: {body2}")

    raise RuntimeError(f"insert profilo_utente fallito [{status}]: {body}")


# ============================================================
# Main loop
# ============================================================

def main():
    if not CSV_PATH.exists():
        sys.exit(f"❌ Non trovo {CSV_PATH}. Esegui lo script dalla dir 'scripts/'.")

    if DRY_RUN:
        print("🧪 DRY RUN — nessuna scrittura su Supabase.\n")

    with CSV_PATH.open(encoding="utf-8") as f:
        rows = list(csv.DictReader(f))

    password = DEFAULT_PASSWORD
    print(f"Processerò {len(rows)} utenti. Password comune: {password!r}\n")

    tally = {
        "auth_created": 0, "auth_exists": 0,
        "linked": 0, "already_linked": 0, "cf_not_found": 0,
        "profilo_inserted": 0, "profilo_updated": 0,
        "errors": 0,
    }

    for i, row in enumerate(rows, 1):
        ruolo = row["ruolo"].strip()
        cf = row["codice_fiscale"].strip()
        email = row["email"].strip()

        if ruolo not in ROLE_TABLE:
            print(f"{i:2d}. ❌ ruolo sconosciuto: {ruolo}")
            tally["errors"] += 1
            continue

        table = ROLE_TABLE[ruolo]

        try:
            # 1
            user_id, auth_status = create_or_get_auth_user(email, password)
            tally[f"auth_{auth_status}"] = tally.get(f"auth_{auth_status}", 0) + 1
            auth_icon = "✅" if auth_status == "created" else "♻️ "

            # 2
            link_status = link_role_row(table, cf, user_id)
            if link_status == "cf_not_found":
                link_icon = "⚠️ "
                tally["cf_not_found"] += 1
            elif link_status == "already_linked":
                link_icon = "♻️ "
                tally["already_linked"] += 1
            else:
                link_icon = "✅"
                tally["linked"] += 1

            # 3 (salta se CF non esiste nella tabella ruolo)
            if link_status == "cf_not_found":
                prof_icon = "⏭ "
                prof_status = "skipped"
            else:
                prof_status = upsert_profilo(user_id, ruolo, cf)
                prof_icon = "✅" if prof_status == "inserted" else "♻️ "
                tally[f"profilo_{prof_status}"] = tally.get(f"profilo_{prof_status}", 0) + 1

            print(
                f"{i:2d}. {email:36s} | "
                f"{auth_icon} auth ({auth_status}, id={user_id[:8]}…) | "
                f"{link_icon} {table} ({link_status}) | "
                f"{prof_icon} profilo ({prof_status})"
            )

        except Exception as e:
            tally["errors"] += 1
            print(f"{i:2d}. ❌ {email:36s} | {e}")

        # Rate limit soft per evitare rate-limit dell'Admin API
        time.sleep(0.15)

    # Riepilogo
    print("\n────── Riepilogo ──────")
    for k, v in tally.items():
        if v:
            print(f"  {k:20s} {v:3d}")

    if tally["errors"]:
        sys.exit(1)
    if tally["cf_not_found"]:
        print("\n⚠️  Alcuni CF non trovati: controlla gli INSERT iniziali delle tabelle ruolo.")


if __name__ == "__main__":
    main()
