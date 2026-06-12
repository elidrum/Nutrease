# Backlog — Nutrease Mobile

> Piano per sprint (8 settimane, uno sprint a settimana). Ogni voce è un RF di
> `docs/requirements.md`, ordinato per dipendenze e carico fra i 3 dev.
> Si legge a inizio sprint: si apre lo sprint corrente, si crea il branch feature, si
> implementa, si spunta la voce e si committa, e a fine sprint si ribilancia.
> A progetto concluso resta come storico della pianificazione e lista dei debiti post-MVP.

---

## Sprint 0 — Setup (Sett. 1)
Obiettivo: repo pulito, build verde, Supabase connesso.
- [x] Dev A: setup Android (AGP 8.2+, JDK 17, Compose BOM 2024.02+)
- [x] Dev A: `local.properties` con `SUPABASE_URL` + `SUPABASE_ANON_KEY`
- [x] Dev A: Hilt + `SupabaseModule` (smoke test `SELECT 1`)
- [x] Dev A: `NavGraph.kt` skeleton (3 route placeholder)
- [x] Dev A: tema Material3 con palette Nutrease
- [x] Dev B: verifica `alimento_seed.sql` caricato (1.388 righe, sanity-check nel seed)
- [x] Tutti: clone, build, run su emulatore Pixel 4 API 34

DoD: ogni dev sa (a) buildare, (b) vedere Toast "Supabase OK" al launch, (c) login con account test.

## Sprint 1 — Auth (Sett. 2)
Obiettivo: paziente e specialista si registrano e loggano.
- [x] Dev A: **RF1** Registrazione paziente
- [x] Dev A: **RF2** Registrazione specialista
- [x] Dev A: **RF3** Login con routing per ruolo
- [x] Dev A: **RF4** Logout
- [x] Dev B: setup `AlimentoRepository` + `CercaAlimentoUseCase` (no UI)
- [x] Dev C: skeleton pagine "Richieste" e "Chat" (UI stub)

DoD: demo registrazione paziente → login → logout → registrazione specialista → login → logout.

## Sprint 2 — Diary base (Sett. 3)
Obiettivo: il paziente compila il diario.
- [x] Dev B: **RF8** Ricerca alimenti con conversione unità
- [x] Dev B: **RF9** Inserimento pasto multi-alimento
- [x] Dev B: **RF10** Registrazione sintomi con severità
- [x] Dev A: **RF5** Modifica profilo (paziente)
- [x] Dev A: **RF6** Cambio password
- [x] Dev C: **RF13** Discovery specialisti (lista + filtri specializzazione/città, infinite scroll, esclusione già collegati)

DoD: paziente registra 3 pasti + 2 sintomi; specialista vede l'elenco nella discovery.

## Sprint 3 — Diary advanced + Collaboration base (Sett. 4)
Obiettivo: diario completo, primo collegamento.
- [x] Dev B: **RF11** Consultazione diario (timeline mista per ora, navigazione per data)
- [x] Dev B: **RF12** Modifica/eliminazione pasti e sintomi
- [x] Dev C: **RF14** Invio richiesta (dialog messaggio opz. max 500)
- [x] Dev C: **RF15** Richieste ricevute (badge count su home specialista)
- [x] Dev C: **RF16** Accettazione (trigger `crea_fascicolo_da_richiesta` crea fascicolo Attivo + chat)
- [x] Dev C: **RF17** Rifiuto con motivazione obbligatoria
- [x] Dev A: **RF7** Eliminazione account

DoD: paziente crea richiesta, specialista accetta, appare nei pazienti; paziente modifica un pasto di ieri.

## Sprint 4 — Specialist view (Sett. 5)
Obiettivo: lo specialista consulta i diari.
- [x] Dev C: **RF18** Lista pazienti collegati (card nome/età/email, sort cognome; embed PostgREST `fascicoloclinico`+`paziente`)
- [x] Dev C: **RF19** Diario paziente read-only (reuse `DiaryRepository.getMealsForDate(fascicoloId,…)`/`SymptomRepository`; RLS pre-esistenti)
- [x] Dev C: **RF20** Filtri periodo (Oggi/7g/30g/Personalizzato `DateRangePicker`, cap 92gg) + nutriente (Tutti/Lattosio/Sorbitolo/Glutine/Calorie). Il chip cambia evidenziazione+aggregato in header, **senza filtrare la lista**.
- [x] Dev B: `GetPatientDiaryRangeUseCase` (fetch parallelo/giorno `coroutineScope{async}.awaitAll()`, somma `NutrientTotals`, sort desc)
- [x] Dev A: polishing auth (reset password "Password dimenticata?" + validazione client min 8/maiuscola/cifra — ADR-0021)

DoD: specialista filtra diario per "ultimi 7gg" + chip "Lattosio" → totale in header + valore per pasto evidenziato.

> **Nota RF20**: filtro nutriente implementato come "evidenziazione + aggregato" anziché
> soglia hard (`≥Xg`). Motivo: molti alimenti hanno valori spesso nulli (la maggior parte
> dei 1.388 ha 0 in due delle tre colonne lattosio/sorbitolo); un filtro hard nasconderebbe
> troppe voci e renderebbe illeggibile la timeline. Soglia in backlog Sprint 7 (bonus).

## Sprint 5 — Dashboard + Notifiche (Sett. 6)
Obiettivo: statistiche specialista + promemoria paziente.
- [x] Dev C: **RF21** Dashboard statistiche (line chart Compose Canvas dep-free, top 3 sintomi, % giorni sintomatici). **BMI placeholder**: tabella `misurazione` fuori scope MVP, `computeBmi` già nel dominio, abilitazione post-MVP (ADR-0018).
- [x] Dev B: **RF22** Promemoria (notifiche locali, niente push server — ADR-0019); UI in `ReminderScreen`.
- [x] Dev A: re-auth operazioni sensibili (cambio password + eliminazione richiedono la password corrente)
- [x] Tutti: mid-project code review incrociata (via PR con review, come da workflow)

DoD: paziente configura "cena ore 20:00 lun-ven", riceve notifica; specialista vede BMI + grafico lattosio 30gg.

## Sprint 6 — Chat (Sett. 7)
Obiettivo: chat real-time.
- [x] Dev C: **RF23** Invio messaggi chat
- [x] Dev C: **RF24** Ricezione real-time via Supabase Realtime
- [ ] Dev A: deep link notifica → chat (dipende da push server-driven, post-MVP)
- [x] Dev B: rifiniture diario — pull-to-refresh su diario e liste (LinkedPatients, LinkRequests, ChatList)
- [x] Tutti: bug fix sprint 1-5

### Consolidamento pre-Sprint 7 (2026-05-30)
- [x] Persistenza sessione + auto-login: gate `splash`/`RootViewModel` (RF3, ADR-0021)
- [x] Validazione password RF1/RF2 (`PasswordPolicy`, pura)
- [x] Re-auth cambio password / eliminazione (RF6/RF7)
- [x] Redesign home paziente/specialista (saluto col nome + card azioni)
- [x] Pull-to-refresh su diario e liste principali
- [x] "Password dimenticata?" su login (invio email reset)
- [x] migrazione stringhe hardcoded → `strings.xml` (ADR-0011) per le schermate sprint 1–3 *(fatta in Sprint 7 Fase 2)*

DoD: 2 emulatori (paziente + specialista) si scambiano 10 messaggi senza refresh, aggiornamento <1s.

## Sprint 7 — Polishing + Demo (Sett. 8)
Obiettivo: app pronta per la discussione tesi.
- [x] Tutti: bug fix residui (giro finale: badge chat, AddMeal senza default, promemoria esatti, reset password OTP)
- [ ] Dev A: screenshot e banner store-ready (bonus, se tempo)
- [ ] Dev B: export CSV diario (bonus)
- [ ] Dev C: typing indicator chat (bonus)
- [ ] Tutti: test manuale dei 24 RF su device fisico
- [x] Tutti: revisione finale `docs/` + `README.md`
- [x] Dev A: build APK release firmato *(Fase 6, ADR-0022: signingConfig legge il keystore da `local.properties` gitignored — non condiviso nel repo; procedura `docs/setup.md §9`)*
- [ ] Tutti: script di demo

DoD: APK installabile, demo end-to-end 5 min, tesina consegnata.

---

## Debiti noti / Post-MVP
- [ ] Reset password — completamento via deep-link: oggi `resetPasswordForEmail` invia
      l'email (anti-enumeration ok), ma non si passa `redirectUrl` e non c'è handler di
      deep-link in-app per impostare la nuova password. Serve: configurare *Redirect URLs*
      sul dashboard Supabase + schermata/deep-link di reset (ADR-0024).
- [ ] Eliminazione account specialista con pazienti collegati: oggi bloccata
      (`has_linked_patients`). Serve una funzione di scollegamento paziente↔specialista
      prima di poter eliminare (ADR-0024).
- [ ] Deep link dalla notifica alla chat (dipende dal push server-driven, vedi Sprint 6).
- [ ] Verifica specialisti — UI di approvazione (ADR-0028): il gate `specialista."Verificato"`
      + RPC `verifica_specialista` esiste lato DB, ma manca (a) un account/ruolo `segretaria`
      operativo e (b) una schermata admin per approvare/sospendere gli specialisti in attesa.
      Oggi l'approvazione si fa dal Dashboard Supabase (`UPDATE specialista SET "Verificato"=true`).
      Estensioni: raccolta n° iscrizione albo in registrazione + (eventuale) verifica esterna FNO/ONB.

## Cerimonie
- Lun 09:30 — Sprint planning (30 min): revisione backlog, claim task.
- Mer 18:00 — Mid-sprint sync (15 min): blocker check.
- Ven 17:00 — Sprint review + retro (45 min): demo, check-box, lessons learned.

## Buffer
I task `(bonus, se tempo)` sono deprioritizzabili. Se in ritardo a fine sprint 5, Sprint 6 +1 settimana e Sprint 7 collassa in 3 giorni.

## Workflow per task
```bash
git checkout dev-<x> && git pull
git checkout -b feature/rfN-titolo-breve
# leggi requirements.md §RFN, implementa
git add -A && git commit -m "feat(rfN): implementazione RFN - <titolo>"
git push -u origin feature/rfN-titolo-breve
gh pr create --base dev-<x> --title "RFN: <titolo>" --body "..."
```