# Requisiti funzionali — Nutrease Mobile

> Catalogo dei 24 RF dell'MVP, raggruppati per macro-area con un owner ciascuno.
> È il riferimento per implementare una feature: scope, acceptance criteria (AC),
> tabelle DB, dipendenze. Da leggere prima di scrivere codice per un RF.
> Per ogni RF: l'attore (chi), la descrizione, gli AC (le condizioni di "fatto"),
> le tabelle DB coinvolte (vedi `sql/nutreaseDatabase.sql`), le dipendenze da altri
> RF e l'owner.

---

## Macro-area 1 — Identity & Account (Dev A)

### RF1 — Registrazione paziente
Attore: utente non autenticato. Si registra fornendo email, password, nome, cognome, data nascita, sesso, altezza, peso.
- AC: email validata (regex RFC 5322 semplice) e unica · password ≥8 char, ≥1 maiuscola, ≥1 numero · data nascita nel passato, età ≥13 · altezza cm (30-250), peso kg (2-400) · al submit 3 INSERT atomici (`auth.users` via `signUpWith`, `paziente`, `profilo_utente` ruolo `paziente`) · errore su qualsiasi INSERT → rollback + messaggio italiano · su successo → navigazione al Diario.
- DB: `auth.users`, `paziente`, `profilo_utente`. Dip.: nessuna. Owner: Dev A.

### RF2 — Registrazione specialista
Attore: utente non autenticato. Registrazione come specialista (dietista/nutrizionista/gastroenterologo) con dati professionali.
- AC: campi obbligatori email, password, nome, cognome, specializzazione (ENUM), numero albo, città studio · numero albo 4-20 char · 3 INSERT atomici (`auth.users`, `specialista`, `profilo_utente` ruolo `specialista`) · specialista NON può autenticarsi come paziente con la stessa email · su successo → Dashboard specialista (vuota, RF18).
- DB: `auth.users`, `specialista`, `profilo_utente`. Dip.: nessuna. Owner: Dev A.

### RF3 — Login
Attore: utente non autenticato. Auth email+password; il sistema determina il ruolo e instrada alla home.
- AC: `signInWith(Email)` · query `profilo_utente` per il ruolo · paziente → Diario, specialista → Dashboard · errore credenziali senza rivelare quale campo è errato · sessione persistita (token in storage criptato).
- DB: `auth.users`, `profilo_utente`. Dip.: RF1 o RF2. Owner: Dev A.

### RF4 — Logout
Attore: paziente/specialista. Termina la sessione.
- AC: `signOut()` · cancellazione cache locale (Room: sintomi e pasti) · navigazione a login, back stack ripulito.
- DB: nessuna (solo auth). Dip.: RF3. Owner: Dev A.

### RF5 — Modifica profilo
Attore: paziente/specialista (solo i propri dati). Aggiorna dati anagrafici/antropometrici (paziente) o professionali (specialista).
- AC: paziente può modificare altezza, peso, data nascita, sesso (NON email) · specialista specializzazione, città, numero albo · validazioni come RF1/RF2 · update parziale (PATCH) sulla tabella ruolo · RLS permette update solo della propria riga.
- DB: `paziente`/`specialista`, `profilo_utente`. Dip.: RF3. Owner: Dev A (condiviso con Dev B per peso storico).

### RF6 — Cambio password
Attore: utente autenticato.
- AC: richiede password corrente (re-auth) · nuova password con regole RF1 · `updateUser(password=...)` · logout forzato su altri device (opzionale MVP).
- DB: `auth.users`. Dip.: RF3. Owner: Dev A.

### RF7 — Eliminazione account
Attore: utente autenticato. Cancella account e dati associati.
- AC: conferma con modale + password · elimina pasti, sintomi, promemoria, richieste, messaggi, fascicolo, riga ruolo, profilo_utente, utente auth · cancellazione a cascata (DELETE `auth.users` → trigger cascata, o Edge Function) · logout automatico.
- DB: tutte le tabelle ruolo-specifiche. Dip.: RF3. Owner: Dev A.

---

## Macro-area 2 — Diary & Food (Dev B)

### RF8 — Ricerca alimenti con conversione unità
Attore: paziente. Ricerca incrementale con suggerimenti; preview quantità in grammi per l'unità selezionata.
- AC: text field debounce 300ms · query `alimento` `ILIKE '%termine%'` su `Nome`, ordinata per rilevanza (esatto prima, poi parziale) · risultati con nome, categoria, unità da `ConversioniUnitaMisura` (jsonb) · unità ≠ grammo → conversione automatica in grammi (es. 2 cucchiai = 30g) · empty state "Nessun alimento trovato...".
- DB: `alimento`. Dip.: RF3. Owner: Dev B.

### RF9 — Inserimento pasto multi-alimento
Attore: paziente. Pasto con uno o più alimenti, ciascuno con quantità e unità.
- AC: tipo pasto (ENUM `tipo_pasto_enum`: colazione/spuntino/pranzo/cena) · data/ora selezionabili (default adesso) · lista alimenti aggiungibile/rimuovibile prima del submit · per alimento quantità >0, unità fra quelle disponibili · al submit INSERT `pasto` poi INSERT multipli `alimento_pasto` (trigger `calcola_nutrienti_pasto` popola i `*Calc`) · fallimento parziale → rollback · toast "Pasto registrato" + refresh.
- DB: `pasto`, `alimento_pasto`, `alimento`. Dip.: RF8. Owner: Dev B.

### RF10 — Registrazione sintomi con severità
Attore: paziente. Sintomo con tipo, severità e timestamp.
- AC: tipo da ENUM `tipo_sintomo_enum` (gonfiore/crampi/diarrea/stitichezza/nausea/reflusso/altro) · severità slider con label (Lieve…Grave) · note opzionali (max 500) · data/ora (default adesso) · INSERT `sintomo`.
- DB: `sintomo`. Dip.: RF3. Owner: Dev B.

### RF11 — Consultazione diario giornaliero (paziente)
Attore: paziente. Vista del proprio diario per una data.
- AC: date picker default oggi · timeline cronologica mista pasti+sintomi · per pasto tipo, ora, alimenti, totali nutrienti aggregati (somma `*Calc`) · per sintomo tipo, ora, severità colorata · swipe-down refresh · cache offline (Room) della giornata.
- DB: `pasto`, `alimento_pasto`, `alimento`, `sintomo`. Dip.: RF9, RF10. Owner: Dev B.

### RF12 — Modifica/eliminazione voci diario
Attore: paziente. CRUD sulle voci registrate.
- AC: tap lungo o menu "…" → Modifica/Elimina · modifica apre la form RF9/RF10 precompilata · elimina con modale + DELETE (cascata FK `ON DELETE CASCADE` per `alimento_pasto`) · update ottimistico · RLS limita alle proprie voci.
- DB: `pasto`, `alimento_pasto`, `sintomo`. Dip.: RF9, RF10, RF11. Owner: Dev B.

---

## Macro-area 3 — Collaboration & Chat (Dev C)

### RF13 — Discovery specialisti
Attore: paziente. Cerca specialisti per città, specializzazione o nome.
- AC: filtri combinabili città (text), specializzazione (dropdown) · lista paginata 20/pagina con nome, specializzazione, città · card apribile con dettaglio + CTA "Richiedi collegamento" (→RF14) · esclude specialisti già collegati o con richiesta pending.
- DB: `specialista`, `profilo_utente`, `richiesta_collegamento`. Dip.: RF3 (come paziente). Owner: Dev C.

### RF14 — Invio richiesta di collegamento
Attore: paziente.
- AC: messaggio opzionale (max 300) · INSERT `richiesta_collegamento` stato `inviata` · UNIQUE (IdPaziente, IdSpecialista) su richieste pending · notifica push allo specialista (post-MVP).
- Nota impl.: il re-invio dopo un rifiuto è un **upsert** sulla UNIQUE (riporta la riga a `In Attesa`, azzera `DataRisposta`/`MotivazioneRifiuto`), non un secondo INSERT — evita il `23505`. Richiede la policy `rc_paziente_update`. Vedi ADR-0023.
- DB: `richiesta_collegamento`. Dip.: RF13. Owner: Dev C.

### RF15 — Visualizzazione richieste ricevute
Attore: specialista. Lista richieste pending.
- AC: filtra stato `inviata` · mostra nome paziente, età, messaggio, data · badge contatore · pull-to-refresh.
- DB: `richiesta_collegamento`, `paziente`, `profilo_utente`. Dip.: RF14. Owner: Dev C.

### RF16 — Accettazione richiesta
Attore: specialista. Accetta → crea fascicolo + canale chat.
- AC: transazione UPDATE `richiesta_collegamento` stato='accettata' + INSERT `fascicoloclinico` + INSERT `canale_chat` · notifica push al paziente · nuova entry in "Pazienti" (RF18) · accesso al tab Chat per entrambi (RF23).
- Nota impl.: il client fa solo l'UPDATE; fascicolo + chat li crea il trigger `crea_fascicolo_da_richiesta`, che è `SECURITY DEFINER` per poter scrivere su `fascicoloclinico`/`chat` (lo specialista non è ancora titolare del fascicolo → senza DEFINER sarebbe `42501`). Vedi ADR-0023.
- DB: `richiesta_collegamento`, `fascicoloclinico`, `canale_chat`. Dip.: RF15. Owner: Dev C.

### RF17 — Rifiuto richiesta con motivazione
Attore: specialista.
- AC: modal con textarea opzionale (max 300) · UPDATE stato='rifiutata', MotivazioneRifiuto · notifica push al paziente · scompare dalla lista pending.
- DB: `richiesta_collegamento`. Dip.: RF15. Owner: Dev C.

### RF18 — Lista pazienti collegati
Attore: specialista. Dashboard con i pazienti a fascicolo attivo.
- AC: query `fascicoloclinico` con `IdSpecialista`=corrente e stato=attivo · card con nome, età, ultima attività · tap → RF19.
- DB: `fascicoloclinico`, `paziente`, `profilo_utente`. Dip.: RF16. Owner: Dev C.

### RF19 — Consultazione diario paziente (specialista)
Attore: specialista. Vista read-only del diario di un paziente collegato.
- AC: UI come RF11 ma NO CRUD · RLS: solo pazienti con fascicolo attivo · banner "Stai visualizzando il diario di <nome>".
- DB: `pasto`, `alimento_pasto`, `alimento`, `sintomo`, `fascicoloclinico`. Dip.: RF18. Owner: Dev C.

### RF20 — Filtri per periodo e nutriente
Attore: specialista (in RF19). Filtra per intervallo date e/o soglia nutriente.
- AC: range date picker (da/a, max 6 mesi) · filtro nutriente checkbox lattosio/sorbitolo/glutine + soglia (`>=` grammi) · filtro sintomo tipo + severità minima · export CSV (post-MVP, skip).
- DB: `pasto`, `alimento_pasto`, `sintomo`. Dip.: RF19. Owner: Dev C.

### RF21 — Dashboard statistiche paziente
Attore: specialista. Riepilogo (BMI, andamento nutrienti, frequenza sintomi).
- AC: BMI da ultimo peso/altezza in `paziente` · grafico a linee media giornaliera lattosio/sorbitolo/glutine ultimi 30gg (MPAndroidChart o chart Compose-native) · top 3 sintomi per frequenza · % giorni con ≥1 sintomo.
- DB: `paziente`, `pasto`, `alimento_pasto`, `sintomo`. Dip.: RF19. Owner: Dev C.

### RF22 — Promemoria personalizzabili
Attore: paziente. Promemoria ricorrenti per la compilazione del diario.
- AC: CRUD su `promemoria` (tipo ENUM pasto/sintomo/pesata, giorni settimana bitmask, ora) · schedulazione locale on-device (ADR-0019/0030) · notifica locale alla scadenza → apre form.
- DB: `promemoria`. Dip.: RF3. Owner: Dev B (schedulazione) + Dev C (config UI).

### RF23 — Invio messaggio in chat
Attore: paziente/specialista. Messaggio testuale nel canale del fascicolo.
- AC: textarea con invio via tasto/bottone · INSERT `messaggio` (IdCanale, IdMittente, Contenuto, Timestamp) · max 2000 char · ordine cronologico, bubble WhatsApp (mittente dx) · notifica push al destinatario (post-MVP).
- DB: `messaggio`, `canale_chat`. Dip.: RF16. Owner: Dev C.

### RF24 — Ricezione real-time messaggi
Attore: paziente/specialista. Nuovi messaggi senza refresh manuale.
- AC: subscription Supabase Realtime su `messaggio` filtrato per `IdCanale` · aggiunta senza flicker · indicatore "nuovo messaggio" se scrollato in alto · riconnessione automatica · typing indicator (opzionale, post-MVP).
- DB: `messaggio` (canale Realtime). Dip.: RF23. Owner: Dev C.

---

## Note trasversali

- Sicurezza: le query sensibili sono protette da RLS e gli UseCase non disabilitano mai le policy.
- Localizzazione: testo UI in italiano via `strings.xml` (ADR-0011).
- Accessibilità: `contentDescription` su ogni icon button; il font-scale dal 100% al 200% deve funzionare.
- Offline: RF9/RF10/RF11 dovevano funzionare in lettura parziale offline (cache); le scritture offline restano post-MVP.

> Nota di implementazione: alcuni RF sono stati realizzati con variazioni rispetto alla
> spec originale, documentate negli ADR di `docs/architecture.md`. Per esempio: RF21 con BMI
> placeholder in attesa del modulo misurazioni; RF22 con giorni `integer[]` invece di
> bitmask e allarmi esatti `AlarmManager` per la precisione al minuto (ADR-0019/0030);
> RF20 con filtro nutriente come evidenziazione anziché soglia rigida; chat su
> `chat`/`messaggio` con `IdChat`; cache offline Room rimandata (l'app è online-only,
> con cache in memoria del solo dataset alimenti).
> Questo file resta la spec dei requisiti originali.