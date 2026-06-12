# `scripts/` — utility operative

> Script di supporto allo sviluppo, **non** parte del binary: dati demo, seeding
> account auth Supabase, check sulla purezza di `domain/`. Leggere prima di
> rieseguire un seed (prerequisiti / chiavi richieste).

## `seed_auth_users.py`

Crea 33 account auth (31 pazienti + 1 specialista + 1 segretaria) e li collega alle
righe anagrafiche in `paziente`/`specialista`/`segretaria`, popolando `profilo_utente`.

**Prerequisiti**:
1. Schema deployato (`sql/nutreaseDatabase.sql`), tabelle ruolo piene dei CF in `demo_accounts.csv`.
2. **SERVICE_ROLE_KEY** (*Supabase → Settings → API → Project API keys → service_role*). Trattala come password admin: non committarla, non incollarla in chat.
3. Python 3.10+ (solo stdlib).

**Lancio**:
```bash
export SUPABASE_URL='https://xxxxxxxxxxxxx.supabase.co'
export SUPABASE_SERVICE_ROLE_KEY='eyJhbGci...'   # service_role, non anon!
SEED_DRY_RUN=1 python3 scripts/seed_auth_users.py   # prova a vuoto
python3 scripts/seed_auth_users.py                  # esecuzione reale
```
Password default: `Nutrease2026!` (override con `export SEED_PASSWORD='...'`). Output
riga per utente con stato auth / link ruolo / profilo + riepilogo (`auth_created`, `linked`, `profilo_inserted` = 33).

**Idempotenza**: rilanciabile — se un utente esiste già, recupera l'`auth_uid` (`♻️ exists`/`already_linked`/`updated`) invece di crearne uno nuovo.

**Warning attesi**: `⚠️ cf_not_found` (il CF del CSV non esiste nella tabella ruolo → verifica gli INSERT iniziali); `⏭ profilo (skipped)` (conseguenza di `cf_not_found`).

**Dopo il seed**:
1. Verifica: *Authentication → Users* = 33 confermati; `SELECT COUNT(*) FROM profilo_utente` = 33; `... FROM paziente WHERE auth_uid IS NOT NULL` = 31.
2. Login di prova `marco@nutrease.it` / `mario@email.it` con `Nutrease2026!`.
3. **Revoca la service_role key** a fine prove (*Settings → API → Roll service_role secret*), poi rigenera `local.properties` con la sola **anon key**.

Check SQL post-seed:
```sql
SELECT ruolo, COUNT(*) AS n FROM profilo_utente GROUP BY ruolo ORDER BY ruolo;
SELECT "CodiceFiscale","Nome","Cognome" FROM paziente WHERE auth_uid IS NULL;  -- non collegati
```

## `check-domain-purity.sh`

Verifica che `domain/` non importi nulla di framework-specific (Android, Hilt, Supabase-kt, Room). Da lanciare in locale prima di una PR che tocca `domain/`.
```bash
./scripts/check-domain-purity.sh   # 0 = pulito, 1 = violazioni
```