# Nutrease Mobile

> Vetrina pubblica del progetto. Contesto tecnico esteso in `docs/`.

[![Status](https://img.shields.io/badge/status-MVP%20completo-brightgreen)]()
[![Android](https://img.shields.io/badge/platform-Android%208.0%2B-green)]()
[![Kotlin](https://img.shields.io/badge/kotlin-2.3-blue)]()

---

App Android nativa per il monitoraggio alimentare e sintomatologico, pensata per due figure che lavorano insieme:

- **Paziente** — registra pasti e sintomi nel diario, si collega a uno specialista e chatta con lui.
- **Specialista** (nutrizionista, dietista, gastroenterologo) — consulta i diari dei pazienti collegati, con filtri per nutriente e periodo.

L'obiettivo clinico è stimare automaticamente lattosio, sorbitolo e glutine ingeriti, per individuare correlazioni con i sintomi e supportare la diagnosi delle intolleranze alimentari.

## Stack tecnologico

| Livello | Tecnologia |
|---|---|
| Linguaggio | Kotlin 2.3, JDK 17 |
| UI | Jetpack Compose + Material3 |
| Navigazione | Navigation Compose |
| Backend | Supabase (Postgres + Auth + Realtime) |
| Notifiche | Locali esatte via AlarmManager (`setAlarmClock`) |
| Cache | In-memory del dataset alimenti (app online-only) |
| DI | Hilt |
| Date/orari | kotlinx-datetime |

Architettura: MVVM + Clean Architecture su 3 layer (`data`/`domain`/`ui`). Dettagli e ADR in [`docs/architecture.md`](docs/architecture.md).

## Funzionalità (MVP)

Elenco completo dei 24 RF in [`docs/requirements.md`](docs/requirements.md). In sintesi:
- Autenticazione e gestione profilo (paziente / specialista)
- Diario alimentare multi-alimento con stima nutrienti
- Registrazione sintomi con severità
- Ricerca alimenti con conversione automatica delle unità in grammi
- Discovery specialisti, richieste di collegamento, fascicolo clinico
- Consultazione diario paziente lato specialista, con filtri e statistiche
- Promemoria configurabili (AlarmManager con notifiche locali esatte)
- Chat real-time tra paziente e specialista

Stato attuale: tutte le 13 macro-feature MVP (RF1–RF24) sono implementate. Restano voci post-MVP (push dei messaggi, sblocco BMI). Decisioni e dettagli negli ADR di [`docs/architecture.md`](docs/architecture.md); piano in `docs/backlog.md`.

## Setup rapido

Procedura completa in [`docs/setup.md`](docs/setup.md). In breve:
1. Clona e apri in Android Studio.
2. Copia `local.properties.template` in `local.properties` e riempi:
   ```properties
   sdk.dir=/Users/<tu>/Library/Android/sdk
   SUPABASE_URL=https://<tuo-progetto>.supabase.co
   SUPABASE_ANON_KEY=<chiave-anonima>
   ```
3. Esegui `sql/nutreaseDatabase.sql` poi `sql/alimento_seed.sql` sul progetto Supabase.
4. Sync Gradle e run su emulatore Pixel 4 API 34.

## Struttura del progetto

```
app/src/main/java/com/example/nutrease/
├── data/      # dto/ (@Serializable Supabase), repository/, mapper/, scheduler/, notification/
├── domain/    # model/ (data class pure), usecase/ (1 = 1 azione), repository/ (interfacce)
├── ui/        # screens/, navigation/, theme/
└── di/        # Moduli Hilt
```

## Git workflow

- Branch `main`: solo PR con ≥1 review. Branch dev per sviluppatore (`dev-a/b/c`); feature branch `dev-x/feat/nome`.
- Prima della PR: `git pull --rebase origin main`. Vietato `push --force` su `main` e committare credenziali (`local.properties`, `*.keystore`).
- Messaggi di commit descrittivi in italiano, uno per area funzionale (es. `login e registrazione`, `chat e promemoria del diario`).

## Licenza

Progetto ad uso interno. Non distribuire senza autorizzazione.