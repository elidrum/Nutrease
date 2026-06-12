# Architettura — Nutrease Mobile

> Diagramma dell'architettura, registro degli ADR (Architecture Decision Records) e
> pattern strutturali ricorrenti (template Repository/ViewModel, layout dei package).
> Da leggere prima di una nuova decisione architetturale o di aggiungere una feature,
> per capire dove vanno i file.
> Regola: un ADR accettato non si ridiscute senza un motivo forte; semmai se ne apre
> uno nuovo che lo sostituisce.

---

## Overview

MVVM + Clean Architecture su 3 layer:
```
ui/      (Compose + ViewModel)        ← Android / Compose
domain/  (UseCase + Model + Repo iface) ← Kotlin puro
data/    (Repo impl + DTO + Mapper)   ← Supabase-kt
            ↓ Supabase (Postgres + Auth + Realtime)
```
Le frecce di dipendenza puntano verso `domain/`, che non importa Android né supabase-kt.
È così un modulo portabile (Flutter/KMP) e testabile con soli JUnit, senza emulatore.

---

## Registro ADR

Formato [Michael Nygard](https://cognitect.com/blog/2011/11/15/documenting-architecture-decisions):
stato, data, contesto, decisione, conseguenze.

### ADR-0001 — MVVM + Clean Architecture a 3 layer
Accettato, 2026-04-01. Contesto: team eterogeneo, progetto potenzialmente portabile, testabilità. Decisione: 3 layer (data/domain/ui), interfaccia Repository in `domain/` e implementazione in `data/`, un ViewModel per schermata, `StateFlow` per lo stato della UI. Conseguenze: più codice di servizio (DTO, Entity, Domain, Mapper per ogni feature) ma testabilità alta, confini chiari e niente Android nella logica di base.

### ADR-0002 — Supabase Auth come unico provider di identità
Accettato, 2026-04-01. Contesto: serve autenticazione email/password con join sulle tabelle profilo. Decisione: Supabase Auth come unico provider di identità, lo stesso backend che ospita i dati. Conseguenze: un solo backend e una sola identità per utente, quindi le RLS usano `auth.uid()` direttamente. Si rinuncia ai provider social, non richiesti dall'MVP.

### ADR-0003 — SDK supabase-kt
Accettato 2026-04-01 (versione aggiornata 2026-05-30). Contesto: accesso a Supabase da Kotlin. Decisione: `io.github.jan-tennert.supabase:*` via BOM 3.5.0, moduli Auth, Postgrest e Realtime; engine `ktor-client-okhttp 3.4.3`. Conseguenze: SDK mantenuto, API coroutine-first. L'aggiornamento 2.x a 3.x ha cambiato l'API Postgrest (per esempio il `filter { }` a lambda) e il codice è stato allineato. È una dipendenza non ufficiale, quindi fissiamo la versione tramite BOM.

### ADR-0004 — DI con Hilt + KSP
Accettato, 2026-04-01. Contesto: serve dependency injection per Repository, UseCase e SupabaseClient. Decisione: Hilt (Dagger 2) con KSP invece di KAPT, moduli per layer. Conseguenze: build più rapida con KSP; servono `@HiltAndroidApp` sull'Application e `@AndroidEntryPoint` sulle Activity.

### ADR-0005 — kotlinx-datetime (non java.time)
Accettato, 2026-04-01. Contesto: `domain/` deve restare puro; `java.time` è disponibile da minSdk 26 ma non è multipiattaforma. Decisione: `kotlinx-datetime` per tutte le date e gli orari in domain e DTO; la conversione a `java.time` avviene solo in `ui/` quando serve (date picker). Conseguenze: API meno ricca (niente ZoneRules) ma sufficiente, e portabile a KMP/iOS/Flutter.

### ADR-0006 — Mapping DB PascalCase italiano e Kotlin inglese
Accettato, 2026-04-01. Contesto: lo schema DB è già in italiano con identificatori PascalCase quotati (`"IdAlimento"`, `"LattosioP100g"`); per il porting Flutter e la leggibilità i domain model sono in inglese. Decisione: DB in PascalCase italiano quotato; DTO in camelCase italiano con `@SerialName("Nome")`; domain model in inglese (`Food`, `lactosePer100g`, `Patient`, `Specialist`); il mapper in `data/mapper/` traduce; la UI usa i domain model con le stringhe in italiano (ADR-0011). Conseguenze: un layer di traduzione in più, ma `domain/` portabile uno a uno in Dart. La tabella di traduzione sta nel commento in testa a ciascun mapper.

### ADR-0007 — Regola "domain/ puro"
Accettato, 2026-04-01. Contesto: evitare che il dominio si accoppi ai framework. Decisione: in `domain/` sono vietati gli import di `android.*`, `androidx.*`, `dagger.*` e `javax.inject.*` (tranne `@Inject` sui UseCase, unico compromesso), `io.github.jan.supabase.*` e `androidx.room.*`. Il controllo avviene in code review (e con Konsist post-MVP). Conseguenze: UseCase testabili con sole dipendenze JVM; le annotazioni dei framework restano in DTO, Entity e implementazioni dei Repository.

### ADR-0008 — Niente SDK di crash reporting nell'MVP
Accettato, 2026-04-15. Contesto: un servizio di crash reporting centralizzato sarebbe utile, ma aggiunge un SDK, un progetto cloud e configurazione per ogni dev. Decisione: nessun SDK di error reporting nell'MVP; i crash si leggono da Android Studio/logcat durante test e demo. Conseguenze: dipendenze minime e zero configurazione extra; in cambio nessun report centralizzato dai device dei tester. Integrabile dopo l'MVP senza impatti architetturali.

### ADR-0009 — RLS con lookup via `profilo_utente`
Accettato, 2026-04-01. Contesto: `auth.users` ha solo email e password, ma le RLS devono sapere se l'utente è paziente o specialista. Decisione: una tabella `profilo_utente (IdUtente uuid PK REF auth.users, Ruolo, IdPaziente, IdSpecialista)` come lookup; le policy la interrogano con `USING (IdPaziente = (SELECT "IdPaziente" FROM profilo_utente WHERE "IdUtente" = auth.uid()))`. Conseguenze: un join logico per policy (veloce, la tabella è piccola e indicizzata su `IdUtente`); in Kotlin, dopo il login, si carica `ProfiloUtenteDto` una volta sola come `SessionInfo`.

### ADR-0010 — Nutrienti via trigger (non colonna GENERATED)
Accettato, 2026-04-10. Contesto: `LattosioCalc` dipende da `QuantitaGrammi * alimento.LattosioP100g / 100`, e Postgres non permette subquery in `GENERATED ALWAYS AS`. Decisione: colonne normali `LattosioCalc/SorbitoloCalc/GlutineCalc/CalorieCalc` su `alimento_pasto`, riempite dal trigger `calcola_nutrienti_pasto` in BEFORE INSERT/UPDATE. Conseguenze: letture semplici (niente join per la somma) e scritture con un lookup in più su `alimento` (trascurabile). Se un alimento cambia i suoi valori per 100g, i pasti già registrati non si ricalcolano: è il comportamento voluto.

### ADR-0011 — Stringhe UI in `res/values/strings.xml`
Accettato, 2026-04-01. Contesto: coerenza con le convenzioni di progetto (codice in inglese, testi UI in italiano) e predisposizione all'i18n (file `.arb` Flutter con mapping uno a uno). Decisione: tutte le stringhe utente in `app/src/main/res/values/strings.xml` (italiano di default), lette con `stringResource(R.string.xxx)`; le stringhe tecniche di log possono restare in inglese nel codice. Conseguenze: un piccolo overhead per feature (qualche chiave) in cambio di piena ricercabilità e i18n abilitata.

### ADR-0012 — Error handling: sealed class `Resource<T>`
Accettato, 2026-04-01. Contesto: `Result<T>` non distingue "loading" da "success" per la UI. Decisione:
```kotlin
sealed class Resource<out T> {
    data object Loading : Resource<Nothing>()
    data class Success<T>(val data: T) : Resource<T>()
    data class Error(val message: String, val cause: Throwable? = null) : Resource<Nothing>()
}
```
I Repository emettono `Flow<Resource<T>>` e i ViewModel li mappano verso lo `UiState`. Conseguenze: gestione uniforme, `when` esaustivo in Compose; gli UseCase senza stato continuo possono usare il `Result<T>` standard.

### ADR-0013 — Edit mode via SavedStateHandle
Accettato, 2026-04-29. Contesto: `AddMealScreen` serve sia per inserire che per modificare (RF12); passare un `Meal` serializzato nell'URL è fragile e aggira Hilt. Decisione: il ViewModel riceve `meal_id: Long` via `SavedStateHandle` (`0L` per insert, valore positivo per edit); in modifica carica con `GetMealUseCase` nell'`init` e precompila lo `UiState`; `save()` sceglie fra `AddMealUseCase` e `UpdateMealUseCase`. Conseguenze: nessun oggetto di dominio nell'URL, nav graph con soli tipi primitivi; il pattern si riusa per ogni form di creazione/modifica.

### ADR-0014 — Refresh DiaryScreen via LifecycleStartEffect
Accettato, 2026-04-29. Contesto: dopo il salvataggio di un pasto la lista restava vecchia (il ViewModel sopravvive alla navigazione). Decisione: `DiaryScreen` usa `LifecycleStartEffect(Unit)` per chiamare `viewModel.refresh()` a ogni ritorno in primo piano, senza navigation result (il refresh serve anche tornando indietro senza aver salvato). Conseguenze: una chiamata di rete in più al ritorno, accettabile per l'MVP e ottimizzabile in futuro con un timer di staleness o un flag dirty.

### ADR-0015 — Registrazione atomica via Auth Hook su `auth.users`
Accettato, 2026-05-11. Contesto: il flusso precedente faceva tre scritture indipendenti (`auth.users`, `profilo_utente`, `paziente`/`specialista`) senza transazione: se il profilo falliva dopo la creazione dell'auth user, l'account restava bloccato a metà (login impossibile con "Profilo non trovato", re-signup impossibile perché l'email esiste già, e correzione solo manuale). Decisione: creazione del profilo interamente server-side. Il client passa i campi come `raw_user_meta_data` nel `signUpWith(Email)`; un trigger AFTER INSERT su `auth.users` (`trg_crea_profilo_da_auth`, `SECURITY DEFINER`) inserisce `profilo_utente` e `paziente`/`specialista` nella stessa transazione. `UserRepository` non espone più `createPatient`/`createSpecialist` e `RegisterUseCase` delega a `AuthRepository.register(data)`. Conseguenze: atomicità sulle tre tabelle garantita dal DB. Gli errori del trigger tornano come 500 generici di Supabase Auth (mitigati da `RegisterViewModel.validate()`). Per retro-compatibilità, senza `"ruolo"` nei metadata il trigger esce subito, così `seed_auth_users.py` continua con il suo UPSERT manuale.

### ADR-0016 — Vista diario specialista: riuso di `DiaryRepository`
Accettato, 2026-05-27. Contesto: per RF18–RF20 si era pensato a un `PatientDiaryRepository` dedicato, ma `DiaryRepository.getMealsForDate(fascicoloId, date)` e `SymptomRepository.getSymptomsForDate(fascicoloId, date)` accettano già un `fascicoloId` parametrico, e le RLS autorizzano già la lettura allo specialista collegato. Decisione: nessun nuovo repository per il diario; si riusano `DiaryRepository` e `SymptomRepository` passando il `fascicoloId` del paziente. L'unico repository nuovo è `LinkedPatientsRepository`. Il fan-out per giorno sta in `GetPatientDiaryRangeUseCase` (fetch parallelo con `coroutineScope{async}.awaitAll()`). Conseguenze: zero duplicazione, e il diritto di lettura è espresso solo nelle policy SQL. `DiaryRepository.addMeal/updateMeal/deleteMeal` resta tecnicamente chiamabile da uno specialista, ma è bloccato dalle RLS (`get_ruolo()='paziente'`) e dalla UI read-only (`PatientDiaryScreen` senza FAB, menu o onClick).

### ADR-0017 — Cap di 92 giorni sul range del diario specialista
Accettato, 2026-05-27. Contesto: RF20 chiedeva "max 6 mesi", ma `GetPatientDiaryRangeUseCase` itera giorno per giorno con 2 query parallele al giorno: circa 180 giorni per 2 fa 360 chiamate Postgrest che saturano il pool OkHttp (max 5 per host) e vanno in timeout su mobile. Decisione: cap lato client a 92 giorni (circa 3 mesi); oltre, la richiesta fallisce subito con `IllegalArgumentException`. Il `DateRangePicker` non vincola (l'utente può provare) ma la richiesta non parte. Conseguenze: query limitate; per intervalli più lunghi servirebbe un RPC server-side aggregato (rimandato nel backlog).

### ADR-0018 — Grafico in Compose invece di librerie esterne
Accettato, 2026-05-27. Contesto: RF21 chiede un grafico a linee per i nutrienti (30 punti per serie); le librerie disponibili (MPAndroidChart, vico, compose-charts) aggiungono dipendenze e peso all'APK e si legano a un'API che non esiste su Flutter/Dart. Decisione: grafico fatto a mano in Compose con `Canvas`, `drawLine` e `drawCircle`, etichette degli assi via `nativeCanvas.drawText` e `android.graphics.Paint`; file `ui/components/NutrientLineChart.kt` (circa 140 righe), asse Y a 4 tacche, etichette sull'asse X ogni 7 giorni più l'ultima, empty state inline. Conseguenze: zero dipendenze, APK invariato, traduzione diretta in `CustomPaint` su Flutter e aspetto coerente con il tema. Niente interattività pronta all'uso (RF21 non la chiede); le etichette introducono `android.graphics.Paint` in `ui/`, il che è ammesso (solo `domain/` deve restare puro, ADR-0007).

### ADR-0019 — Promemoria: notifiche locali, niente push server-side
Accettato, 2026-05-28. Contesto: RF22 chiede promemoria innescati dal calendario dell'utente, non dal server. Una soluzione push server-side richiederebbe token per dispositivo nel DB e una funzione server schedulata: fuori scope per l'MVP. Decisione: scheduling interamente on-device con notifiche locali (`NotificationCompat`) sul canale `diary_reminder`; prima implementazione con `WorkManager` (`PeriodicWorkRequest` settimanale per ogni giorno in `notifica_config.GiorniSettimana`, con ritardo iniziale fino al prossimo slot). Le notifiche guidate dal server (per esempio i push della chat) restano post-MVP. Conseguenze: zero infrastruttura cloud, build riproducibile, funziona offline e dominio puro (`ReminderScheduler` come interfaccia in `domain/`, implementazione in `data/scheduler/`). Limite: tolleranza di circa 15 minuti; per la precisione al minuto servirebbe `AlarmManager`. I giorni sono un `integer[]` ISO 1-7, non una bitmask, e mappano su `DayOfWeek.isoDayNumber`. Questa scelta è poi stata superata da ADR-0030.

### ADR-0020 — Chat real-time: supabase-kt Realtime + query singola con embed
Accettato, 2026-05-29. Contesto: RF23/24 richiedono invio e ricezione in tempo reale; vanno decise come gestire lo stream Realtime nel dominio puro, come mostrare l'anteprima dell'ultimo messaggio e quale tipo usare per i timestamp. Decisione: `ChatRepository.observeNewMessages` ritorna `Flow<ChatMessage>` (implementato con `postgresChangeFlow<PostgresAction.Insert>` su `messaggio` filtrato per `IdChat` dentro un `channelFlow`; la pulizia con `removeChannel` gira su un `cleanupScope` del repo `@Singleton`, senza GlobalScope né runBlocking in `awaitClose`); la lista chat è una sola query su `chat` con embed di `paziente`/`specialista` e di `messaggio` ordinato per `created_at` desc con `limit(1)` per l'anteprima (la controparte si ricava confrontando il codice fiscale dell'utente). I model usano `kotlin.time.Instant`. Fuori scope: read receipt ed empty-state ricco. Conseguenze: niente problema N+1 per la lista, dominio puro (il `Flow` è ammesso), un canale Realtime per chat aperta; gli echo del proprio INSERT sono deduplicati per id nel ViewModel.

### ADR-0021 — Auto-login con gate di sessione + re-auth sulle operazioni sensibili
Accettato, 2026-05-30. Contesto: RF3 chiede una sessione persistente ma l'app partiva sempre dal login; RF6/RF7 chiedono di ri-autenticarsi prima del cambio password o dell'eliminazione, cosa che gli UseCase non facevano. Decisione: gate di sessione con destinazione iniziale `splash` e `RootViewModel` che chiama `AuthRepository.getCurrentUser()` (dopo aver atteso `auth.awaitInitialization()`) e instrada alla home per ruolo o al login; re-auth via `AuthRepository.reauthenticate(email, password)` chiamato da `ChangePasswordUseCase` e `DeleteAccountUseCase`; le regole sulla password sono centralizzate in `domain/model/PasswordPolicy` (min 8 caratteri, almeno una maiuscola e una cifra), usata sia in registrazione che nel cambio password. Conseguenze: un round-trip in più all'avvio verso `profilo_utente`, in cambio di una UX corretta e della logica di sessione in un punto solo; la password corrente va solo verso Supabase Auth e non viene mai salvata.

### ADR-0022 — Build di release firmata: keystore da local.properties, minify off
Accettato, 2026-06-02. Contesto: serve un APK di release firmato, installabile su device fisico e distribuibile (test e demo tesi); il blocco `release` non aveva `signingConfig`, minify era spento e `proguard-rules.pro` vuoto. Decisione: `signingConfigs.release` in `app/build.gradle.kts` legge keystore, password e alias da `local.properties` (gitignored; `*.keystore`/`*.jks` aggiunti al `.gitignore`); se le chiavi mancano la config non viene creata e la release resta non firmata, con la build comunque verde. Minify lasciato spento: R8 romperebbe la serializzazione kotlinx/supabase senza keep-rules dedicate, un rischio inutile per un MVP. Procedura keystore in `docs/setup.md §9`. Conseguenze: `assembleRelease` produce un APK firmato (schema v2, valido da minSdk 26) installabile e aggiornabile a patto di firmarlo sempre con lo stesso keystore; credenziali e keystore restano fuori dal repo. Lo svantaggio è un APK più grande e non offuscato (accettabile per l'MVP); il keystore va conservato, e il `versionCode` va incrementato a ogni release distribuita.

### ADR-0023 — Accettazione e re-invio richiesta di collegamento: trigger SECURITY DEFINER + upsert
Accettato, 2026-06-08. Contesto: due bug emersi in test sul flusso richieste (RF14–RF17). Primo: all'accettazione lo specialista riceveva un errore `42501`, perché il trigger `crea_fascicolo_da_richiesta` (SECURITY INVOKER) scatta sull'UPDATE di `richiesta_collegamento` ma deve scrivere su `fascicoloclinico`/`chat`, e la policy permetteva l'INSERT solo allo specialista già titolare, quindi il primo fascicolo non si poteva creare. Secondo: il re-invio di una richiesta dopo un rifiuto dava `23505`, perché il client faceva un `insert` puro che violava il vincolo `UNIQUE (CodFiscalePaziente, CodFiscaleSpecialista)` (la riga rifiutata esisteva ancora). Decisione, lato DB: `crea_fascicolo_da_richiesta()` diventa `SECURITY DEFINER SET search_path TO public` (gira come owner e bypassa le RLS su fascicolo e chat; il controllo d'accesso resta sull'UPDATE di `richiesta_collegamento`), e il ramo `ON CONFLICT DO UPDATE` aggiorna anche `CodFiscaleSpecialista` per consentire il cambio di specialista; aggiunta la policy `rc_paziente_update`. Lato app: `LinkRequestRepositoryImpl.sendRequest` passa da `insert` a `upsert`, riportando la richiesta a "In Attesa" e azzerando `DataRisposta` e `MotivazioneRifiuto`. Nota: il body dell'upsert è un `JsonObject` esplicito e non un DTO, perché il serializer di supabase-kt 3.5.0 ha `encodeDefaults = false` e i campi pari al default verrebbero omessi e non azzerati; vanno quindi inviati a mano con `JsonNull` esplicito. Conseguenze: accettazione e re-invio robusti a prescindere dallo stato residuo, con upsert atomico. La regola generale che ne deriva: ogni trigger che scrive su una tabella con RLS va reso `SECURITY DEFINER`. La stessa fix è stata applicata agli altri trigger cross-table (`crea_diario_se_mancante`, `aggiorna_stato_diario`, `aggiorna_ultimo_messaggio`); quest'ultimo era davvero rotto sotto INVOKER, perché `chat` non ha policy UPDATE e l'aggiornamento di `ultimo_messaggio_at` veniva filtrato a 0 righe in silenzio, lasciando l'ordinamento della lista chat sempre vecchio.

### ADR-0024 — Eliminazione account: errori generici (DomainError) + RPC server-side
Accettato, 2026-06-08. Contesto: tre problemi nel flusso account (RF1/RF7). Primo, gli errori di login risalivano alla UI con il messaggio grezzo di Supabase, esponendo dettagli tecnici. Secondo, l'eliminazione account cancellava dal client `paziente`/`specialista` e `profilo_utente`, ma quelle tabelle non hanno policy DELETE, quindi la cancellazione veniva filtrata a 0 righe in silenzio: spariva solo `profilo_utente` e `auth.users` restava intatto. Inoltre i fascicoli sono in `ON UPDATE CASCADE` (senza `ON DELETE`), quindi anche con una policy il DELETE su un paziente con fascicolo attivo fallirebbe. Risultato: account bloccato a metà. Decisione: introdotta una `sealed class DomainError` (dominio puro) con messaggi già generici; `AuthRepositoryImpl` mappa le eccezioni Supabase/rete su `DomainError` (login e re-auth danno sempre lo stesso `InvalidCredentials`, per non rivelare se un'email esiste), e i ViewModel mostrano solo `DomainError.message`. L'eliminazione passa a una funzione server-side `delete_own_account()` `SECURITY DEFINER`, esposta come RPC: gira come owner, cancella nell'ordine corretto i dati di dominio e infine `auth.users`, e blocca lo specialista con pazienti collegati restituendo `has_linked_patients` per non distruggere dati altrui. `DeleteAccountUseCase` ora fa re-auth, poi RPC, poi logout. Conseguenze: niente enumerazione utenti né dettagli tecnici in UI, cancellazione completa e atomica (compreso `auth.users`), specialista protetto dai cascade pericolosi. Lo specialista con fascicoli non può ancora auto-eliminarsi finché non esiste uno scollegamento (post-MVP).

### ADR-0025 — Promemoria multipli (uno per pasto) + fix scheduling per-promemoria
Accettato, 2026-06-08. Contesto: tre limiti su RF22. Primo, la domenica non era selezionabile, perché `DaysOfWeekChips` metteva i 7 chip in un `Row` non a capo e su schermi stretti l'ultimo finiva fuori vista (il mapping ISO era invece corretto). Secondo, i giorni non venivano rispettati con più promemoria, perché i nomi unici WorkManager erano per solo giorno e due promemoria sullo stesso giorno si sovrascrivevano; mancava anche una cancellazione mirata. Terzo, era previsto un solo promemoria per paziente, perché model, repo e scheduler erano cablati su una sola riga `notifica_config`. Decisione: `DaysOfWeekChips` passa a `FlowRow` (i chip vanno a capo, tutti e 7 sempre visibili); lo scheduling diventa per-promemoria, con nomi e tag che includono l'id, e `ReminderScheduler` espone `cancel(configId)`; la matematica del prossimo slot è estratta in `ReminderScheduling` (oggetto puro, testato); il modello passa a lista (`getConfigs`/`saveConfig`/`deleteConfig(id)`), senza migrazioni perché `notifica_config` supportava già N righe per paziente. La UI mostra la lista con switch, modifica ed eliminazione per card, un FAB "Aggiungi" e un'azione rapida "un promemoria per ogni pasto". Conseguenze: N promemoria indipendenti, almeno uno per pasto, ciascuno innescato solo nei suoi giorni e cancellato/ripianificato in modo isolato; la domenica torna selezionabile. Restava la tolleranza di WorkManager, poi risolta da ADR-0030.

### ADR-0026 — Età minima alla registrazione + maschera per la data di nascita
Accettato, 2026-06-08. Contesto: la registrazione paziente accettava qualunque data di nascita scritta a mano, senza vincolo di età, e il campo era fragile. Decisione: una regola pura `domain/model/AgePolicy`, sul modello di `PasswordPolicy`, con `MIN_AGE_YEARS = 18` e `validate(birthDate, today)` basato su `LocalDate.yearsUntil` (solo kotlinx-datetime); "oggi" è un parametro esplicito così il dominio non dipende dall'orologio e i test sono deterministici. La regola è applicata sia nella UI (errore inline e bottone disabilitato) sia, in modo autoritativo, in `RegisterViewModel`. La data si digita a sole cifre e le `/` sono inserite in sola visualizzazione da una `VisualTransformation`. Conseguenze: limite d'età centralizzato e testato (casi limite a 18 anni esatti, un giorno prima, e 29 febbraio), niente `java.time`, input più robusto. La regola vale solo per i pazienti, perché gli specialisti non hanno data di nascita.

### ADR-0027 — Badge in home: stato "letto/visto" locale via SharedPreferences
Accettato, 2026-06-08. Contesto: servono badge in home per le chat non lette (paziente e specialista) e per le richieste di collegamento accettate non ancora viste (paziente). Il punto delicato è far sparire il badge al momento giusto: la chat non modella la lettura (ADR-0020, online-only) e una richiesta accettata resta tale per sempre, quindi senza un concetto di "visto" il badge non si azzererebbe mai. Decisione: un piccolo stato locale "visto" dietro l'interfaccia di dominio `BadgeStateRepository` (pura), implementato su SharedPreferences: per ogni chat l'ultimo timestamp letto, e l'insieme degli id di richieste accettate già viste. UseCase dedicati per contare e per marcare come visto; i conteggi sono esposti dai ViewModel delle home e ricalcolati al ritorno con `LifecycleStartEffect`. Conseguenze: badge che compaiono e spariscono correttamente, persistenti oltre il riavvio, con dominio puro e logica testata; nessuna modifica a schema o RLS. È il primo uso di persistenza locale accanto al modello online-only, ma è stato di UI, non dato clinico; il badge conta le chat con messaggi non letti, non i singoli messaggi.

### ADR-0028 — Verifica/approvazione specialisti (gate `Verificato` lato Supabase)
Accettato, 2026-06-09. Contesto: chiunque può registrarsi come specialista e ottenere subito accesso ai dati clinici dei pazienti che lo collegano. Serve un controllo su chi può operare come specialista. Una allowlist di email pre-approvate sarebbe sicura ma poco pratica (niente self-service). Decisione: self-service con stato "da verificare" e approvazione admin, imposto via RLS senza fidarsi del client. Nuova colonna `specialista."Verificato"` (default false); la registrazione parte da false, mentre gli specialisti del seed sono portati a true con un UPDATE che colpisce solo le righe demo. Il gate è fatto di: un helper `is_specialista_verificato()`; la policy `specialista_select` che mostra ai pazienti solo i verificati; la policy `rc_specialista_update` che impedisce a un non-verificato di accettare o rifiutare; un trigger che blocca l'auto-promozione; e un RPC `verifica_specialista(cf, bool)` per l'approvazione da parte di una segretaria. Lato client, `Specialist.isVerified` alimenta un banner "Account in verifica" nella home specialista. Conseguenze: nessuno specialista diventa operativo o visibile senza una decisione umana, il controllo è interamente server-side, e il client non può aggirarlo né auto-approvarsi. Per l'MVP non esiste ancora un account segretaria né una UI di approvazione, quindi l'approvazione si fa dal Dashboard Supabase; la UI admin è rimandata.

### ADR-0029 — Ricerca alimenti: client-side su dataset in cache + ranking fuzzy in Kotlin puro
Accettato, 2026-06-09. Contesto: la ricerca alimenti (RF8) usava `ILIKE '%query%'` su `Nome` lato Supabase. Due limiti: nessuna tolleranza ai refusi (`ILIKE '%spagetti%'` non trova "Spaghetti"), e nessun match multi-parola (`ILIKE '%pasta bolo%'` cerca la stringa contigua e non trova "Pasta al ragù alla bolognese"). Tra le alternative valutate, `pg_trgm` lato DB era scalabile ma richiedeva estensioni e una migrazione, mentre la soluzione tutta client-side era la più semplice da spiegare e teneva la logica nel dominio puro. Decisione: `FoodRepository.getAllFoods()` scarica l'intera tabella `alimento` una volta e la tiene in cache in memoria (con `Mutex` per evitare doppi fetch). Tutto il matching e il ranking stanno in `domain/model/FoodSearch.kt` (oggetto puro, testato): normalizzazione (lowercase e rimozione accenti con una mappa esplicita, non `java.text.Normalizer`), tokenizzazione, distanza di Levenshtein e punteggio. Il punteggio è per token con semantica AND (ogni parola digitata deve trovare un riscontro): parola esatta, prefisso, sottostringa, poi fuzzy con distanza massima crescente sulla lunghezza della parola. L'ordinamento è per rilevanza, poi nome più corto, poi alfabetico. Conseguenze: tolleranza ai refusi e ricerca multi-parola in ordine libero, ranking deterministico e testabile, zero estensioni o migrazioni SQL, e ricerca istantanea anche offline dopo il primo scaricamento. Il primo fetch tiene l'intero dataset in memoria (accettabile a poco più di mille righe). La colonna `Alias`, popolata nel seed, non è ancora usata nel matching e resta un'estensione futura.

### ADR-0030 — Promemoria esatti: da WorkManager ad AlarmManager `setAlarmClock`
Accettato, 2026-06-10. Contesto: RF22 chiede che il promemoria arrivi entro circa 5 minuti dall'orario impostato, ma lo scheduling di ADR-0019/0025 con `PeriodicWorkRequest` aveva una tolleranza di circa 15 minuti (batching e Doze), insufficiente. Decisione: un allarme one-shot `AlarmManager.setAlarmClock()` per ogni coppia (promemoria, giorno), con orario calcolato da `ReminderScheduling.delayUntilNext` (kotlinx-datetime, oggetto puro testato); allo scatto, `ReminderAlarmReceiver` mostra la notifica e ri-arma la settimana successiva (`setAlarmClock` è one-shot). I PendingIntent sono distinti per (id, giorno) tramite una Uri `nutrease://reminder/{id}/{iso}`; `cancel(id)` annulla solo gli allarmi di quel promemoria, e `cancelAll()` usa un registro su SharedPreferences perché AlarmManager non elenca i propri allarmi. Gli allarmi esatti si perdono al reboot, quindi `RescheduleRemindersUseCase` (chiamato da `RootViewModel` dopo l'auth, solo per i pazienti) rilegge `notifica_config` e li ri-arma, in modo idempotente; è stato preferito a un BootReceiver, che girerebbe senza rete né sessione garantite. L'interfaccia `ReminderScheduler` resta invariata; cambia solo l'implementazione, e la dipendenza WorkManager esce dal progetto. Conseguenze: scarto di pochi secondi (esente da Doze, e `setAlarmClock` non richiede il permesso `SCHEDULE_EXACT_ALARM`), quindi RF22 è rispettato; dominio e UseCase intatti, cambia solo il binding Hilt. Il ri-arma post-reboot dipende dalla prima apertura dell'app.

---

## Dataset alimentare

- `alimento`: 1.388 voci (base CSV svizzero, 109 voci integrate con stime da letteratura e
  correzioni mirate di giugno 2026 da CREA-IEO/BDA e USDA FDC, tracciate in `FonteDati`),
  valori nutrizionali per 100 g.
  Popolamento: prima `sql/nutreaseDatabase.sql`, poi `sql/alimento_seed.sql` (sanity-check `count>=1388`).
- Il grammo è l'unità base implicita: il JSON `ConversioniUnitaMisura` contiene solo le
  conversioni non banali (per esempio `{"cucchiaio":15,"fetta":30}`) e mai la chiave `"g"`,
  che sarebbe un'identità ridondante. Lato client `Food.availableUnits()` antepone `"g"` come
  prima opzione.
- Unità presenti oltre a `"g"`: cucchiaio, cucchiaino, pezzo, fetta, tazza, bicchiere, litro,
  spicchio, coppetta, porzione, piatto, piadina, panino, pizza, spiedino. Il client le tratta
  come `Map<String, Double>` e mostra la chiave così com'è.
- La colonna `Alias` (text[], indice GIN) è popolata nel dataset ma non ancora usata dal
  matching (la ricerca lavora sui token del solo `Nome`, ADR-0029): è l'estensione futura
  naturale. `FonteDati` traccia la provenienza di ogni voce e non è esposta in UI.

---

## Pattern ricorrenti

### Layout dei package per feature
```
data/dto/FoodDto.kt · data/mapper/ (estensioni toDomain) · data/repository/FoodRepositoryImpl.kt
domain/model/Food.kt · domain/repository/FoodRepository.kt · domain/usecase/SearchFoodsUseCase.kt
ui/screens/diary/{DiaryScreen,DiaryViewModel}.kt · ui/navigation/AppNavGraph.kt · ui/theme/
di/AppModule.kt (SupabaseClient singleton + binding dei repository)
```

### Template Repository
```kotlin
// domain/repository/FoodRepository.kt
interface FoodRepository {
    suspend fun getAllFoods(): Result<List<Food>>
}
// data/repository/FoodRepositoryImpl.kt
class FoodRepositoryImpl @Inject constructor(
    private val client: SupabaseClient,
) : FoodRepository { /* … */ }
```

### Template ViewModel
```kotlin
@HiltViewModel
class DiaryViewModel @Inject constructor(
    private val getMealsForDate: GetMealsForDateUseCase,
) : ViewModel() {
    private val _state = MutableStateFlow<DiaryUiState>(DiaryUiState.Loading)
    val state: StateFlow<DiaryUiState> = _state.asStateFlow()

    fun load(date: LocalDate) = viewModelScope.launch {
        _state.value = DiaryUiState.Loading
        getMealsForDate(date).fold(
            onSuccess = { _state.value = DiaryUiState.Success(it) },
            onFailure = { _state.value = DiaryUiState.Error(it.message ?: "Errore") },
        )
    }
}
```

---

## Vincoli non negoziabili

1. Niente `java.time` in `domain/`: si usa `kotlinx-datetime`.
2. Niente `android.*` in `domain/`.
3. Niente credenziali committate (`local.properties`, keystore, chiavi API).
4. Niente `GlobalScope`: si usa `viewModelScope` o uno scope dedicato.
5. Niente `!!` in produzione: si usa `?.let { }` o `requireNotNull` con messaggio.
6. Niente query SQL grezze lato client: sempre supabase-kt Postgrest o RPC.
7. Niente logica di business nei Composable: va negli UseCase.
8. Niente modifiche allo schema DB senza aggiornare `docs/architecture.md` e `sql/nutreaseDatabase.sql`.