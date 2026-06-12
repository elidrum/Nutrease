# Glossario IT (DB) → EN (domain)

> Tabella di traduzione canonica fra i nomi DB (italiano, PascalCase quotato) e il
> codice Kotlin in `domain/` (inglese puro). Riferimento per mapper consistenti.
> **Quando leggerlo**: ogni volta che aggiungi un DTO o domain model, per non
> inventare nomi diversi. **Origine**: ADR-0006. **Aggiornamento**: la PR che modifica
> `sql/nutreaseDatabase.sql` deve aggiungere qui la riga di traduzione (e i mapper esistenti).

---

## Entità / Tabelle

| DB | Domain | Descrizione |
|---|---|---|
| `paziente` | `Patient` | Profilo paziente |
| `specialista` | `Specialist` | Profilo specialista |
| `profilo_utente` | `UserProfile` | Lookup auth ↔ ruolo |
| `fascicoloclinico` | `ClinicalFile` | Collegamento attivo paziente ↔ specialista |
| `alimento` | `Food` | Anagrafica alimento |
| `alimento_pasto` | `MealFood` | Voce di pasto (alimento + quantità) |
| `pasto` | `Meal` | Pasto composto |
| `sintomo` | `Symptom` | Sintomo registrato |
| `richiesta_collegamento` | `ConnectionRequest` | Richiesta paziente → specialista |
| `canale_chat` | `ChatChannel` | Canale chat |
| `messaggio` | `Message` | Messaggio in chat |
| `promemoria` | `Reminder` | Promemoria compilazione diario |
| `notifica_config` | `NotificationPreference` | Config promemoria (legacy naming nel DB) |

## Campi comuni

| DB | Domain | Tipo Kotlin |
|---|---|---|
| `IdAlimento` | `id` | `Int` |
| `IdPaziente` | `id` | `Int`/`Long` |
| `IdSpecialista` | `id` | `Int`/`Long` |
| `IdUtente` | `userId` | `String` (UUID auth) |
| `Nome` | `name` | `String` |
| `Cognome` | `surname` | `String` |
| `Email` | `email` | `String` |
| `CodiceFiscale` | `taxCode` | `String` |
| `DataNascita` | `birthDate` | `LocalDate` |
| `Sesso` | `sex` | `Sex` (M/F/OTHER) |
| `Altezza` | `heightCm` | `Double` |
| `Peso` | `weightKg` | `Double` |
| `Categoria` | `category` | `String` (o enum) |
| `Timestamp` | `timestamp` | `Instant` |
| `DataOra` | `occurredAt` | `Instant` |
| `StatoFascicolo` | `status` | `ClinicalFileStatus` |

## Campi per feature

**Alimenti / Nutrizione**

| DB | Domain | Unità |
|---|---|---|
| `LattosioP100g` | `lactosePer100g` | g/100g |
| `SorbitoloP100g` | `sorbitolPer100g` | g/100g |
| `GlutineP100g` | `glutenPer100g` | g/100g |
| `CaloriePer100g` | `kcalPer100g` | kcal/100g |
| `ConversioniUnitaMisura` | `unitConversions` | `Map<String,Double>` |
| `QuantitaGrammi` | `amountGrams` | grammi |
| `QuantitaOrig` | `originalAmount` | numero |
| `UnitaMisuraOrig` | `originalUnit` | String (es. "cucchiaio") |
| `LattosioCalc` | `lactoseCalc` | g (da trigger) |
| `SorbitoloCalc` | `sorbitolCalc` | g |
| `GlutineCalc` | `glutenCalc` | g |
| `CalorieCalc` | `kcalCalc` | kcal |
| `TipoPasto` | `mealType` | `MealType` (BREAKFAST/SNACK/LUNCH/DINNER) |

**Sintomi**: `TipoSintomo`→`type` (`SymptomType`) · `Severita`→`severity` (`Int` 1-5) · `Note`→`notes` (`String?`).

**Richieste / Fascicoli**: `StatoRichiesta`→`status` (`RequestStatus` SENT/ACCEPTED/REJECTED) · `MotivazioneRifiuto`→`rejectionReason` (`String?`) · `MessaggioIniziale`→`initialMessage` (`String?`) · `DataCreazione`→`createdAt` (`Instant`) · `DataChiusura`→`closedAt` (`Instant?`).

**Chat**: `IdCanale`→`channelId` (`Long`) · `IdMittente`→`senderId` (`String` UUID) · `Contenuto`→`content` (`String`) · `InviatoIl`→`sentAt` (`Instant`) · `LettoIl`→`readAt` (`Instant?`).

**Profilo / Ruoli**: `Ruolo`→`role` (`Role` PATIENT/SPECIALIST).

## ENUM — mapping valori

- **`Sesso`/`Sex`**: `M`→`MALE`, `F`→`FEMALE`, `A`→`OTHER`.
- **`tipo_pasto_enum`/`MealType`**: `colazione`→`BREAKFAST`, `spuntino`→`SNACK`, `pranzo`→`LUNCH`, `cena`→`DINNER`.
- **`tipo_sintomo_enum`/`SymptomType`**: `gonfiore`→`BLOATING`, `crampi`→`CRAMPS`, `diarrea`→`DIARRHEA`, `stitichezza`→`CONSTIPATION`, `nausea`→`NAUSEA`, `reflusso`→`REFLUX`, `altro`→`OTHER`.
- **`user_role_enum`/`Role`**: `paziente`→`PATIENT`, `specialista`→`SPECIALIST`.
- **`stato_richiesta_enum`/`RequestStatus`**: `inviata`→`SENT`, `accettata`→`ACCEPTED`, `rifiutata`→`REJECTED`.
- **`stato_fascicolo_enum`/`ClinicalFileStatus`**: `attivo`→`ACTIVE`, `chiuso`→`CLOSED`.

## Regole di sicurezza linguistica

1. **Mai** nomi italiani in `domain/` (neanche nei commenti esterni).
2. Nei **DTO**: italiano, mappano 1:1 i campi JSON di Supabase.
3. Nei **Mapper**: ogni `toDomain()`/`toDto()` è il punto di traduzione unico.
4. **Stringhe UI** in `strings.xml`: italiano (ADR-0011).
5. **Log** tecnici: inglese.

> Se rinomini un campo aggiorna anche i mapper; se aggiungi un ENUM aggiungi la tabella valori.