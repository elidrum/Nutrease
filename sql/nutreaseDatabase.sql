-- ============================================================
-- NUTREASE — Schema Supabase definitivo (nutreaseDatabase.sql)
-- Schema unico per l'app Android/Flutter
-- ============================================================
-- INDICE:
--   SEZIONE 0  — Cleanup (esegui solo se stai ripartendo da zero)
--   SEZIONE 1  — Tipi ENUM
--   SEZIONE 2  — Tabelle esistenti (adattate)
--   SEZIONE 3  — Tabelle NUOVE richieste dall'app
--   SEZIONE 4  — Dati di esempio (tabelle originali)
--   SEZIONE 5  — Row Level Security (RLS) + Policy
--   SEZIONE 6  — Helper Functions, Triggers & Views
--              (6.1 updated_at, 6.2 calcolo nutrienti,
--               6.3 stato diario, 6.4 auto-creazione diario,
--               6.5 accettazione richiesta, 6.6 ultimo messaggio,
--               6.7 dashboard_specialista, 6.8 diario_con_nutrienti,
--               6.9 registrazione atomica via Auth Hook,
--               6.10 eliminazione account via RPC SECURITY DEFINER)
--   SEZIONE 7  — Realtime (publication supabase_realtime)
-- ============================================================
-- STORICO CONSOLIDAZIONI (già incorporate in questo file):
--   - 001  Filtri specialista: enum tipo_specializ_enum,
--          colonne Specializzazione/Citta, indici discovery
--   - 002  Views security_invoker = true
--          (dashboard_specialista, diario_con_nutrienti)
--   - 003  Hardening RLS:
--          * search_path fissato sulle 9 funzioni
--          * helper RLS get_*() in SECURITY INVOKER
--          * policy *_insert_signup ristrette a auth.uid()
--   - 004  Registrazione atomica via trigger AFTER INSERT su
--          auth.users (Sezione 6.9 — crea_profilo_da_auth):
--          profilo_utente + paziente/specialista creati nella
--          stessa transazione del signUp. Se una insert fallisce,
--          anche l'utente auth viene rollbackato. Risolve lo
--          stato "limbo" in cui auth.users esiste senza profilo.
--   - 005  Chat real-time (RF23/RF24): tabella messaggio aggiunta
--          alla publication supabase_realtime (Sezione 7) così che
--          il client riceva i nuovi messaggi via Supabase Realtime.
--   - 006  Hardening trigger RLS (Prevenzione ADR-0023): tutti i
--          trigger che scrivono su tabelle con RLS sono ora
--          SECURITY DEFINER + SET search_path TO public, come
--          crea_fascicolo_da_richiesta. Convertiti:
--          * aggiorna_ultimo_messaggio() — FIX: chat non ha policy
--            UPDATE, quindi sotto INVOKER l'UPDATE veniva filtrato a
--            0 righe e ultimo_messaggio_at non si aggiornava mai.
--          * crea_diario_se_mancante() e aggiorna_stato_diario() —
--            allineamento al pattern (non rotti prima, ma robusti
--            a future modifiche di policy/ruoli).
--   - 007  Eliminazione account (RF7, ADR-0024): nuova funzione
--          delete_own_account() (sez. 6.10), SECURITY DEFINER,
--          esposta come RPC agli utenti authenticated. FIX: la
--          cancellazione lato client eliminava solo profilo_utente
--          (paziente/specialista privi di policy DELETE → DELETE a
--          0 righe; auth.users mai toccata) lasciando l'account in
--          limbo. Ora la rimozione è server-side, ordinata e
--          completa (incl. auth.users); lo specialista con pazienti
--          collegati è bloccato (ritorna 'has_linked_patients').
--   - 008  Verifica/approvazione specialisti (ADR-0028): nuova
--          colonna specialista."Verificato" (default false). Uno
--          specialista NON è discoverable dai pazienti né può
--          accettare/rifiutare richieste finché non è approvato da
--          un admin (segretaria). Le registrazioni reali partono
--          Verificato=false; gli specialisti del seed (auth_uid IS
--          NULL) sono backfillati a true. Componenti:
--          * helper is_specialista_verificato() (RLS);
--          * policy specialista_select: i pazienti vedono SOLO i
--            verificati (lo specialista vede sé stesso, la segretaria
--            vede tutti per approvare);
--          * policy rc_specialista_update gated su verificato;
--          * trigger trg_blocca_self_verifica: impedisce allo
--            specialista di auto-promuoversi (cambiare "Verificato"
--            via specialista_self_update); consentito solo a
--            segretaria o service_role/Dashboard (auth.uid() IS NULL);
--          * RPC verifica_specialista(cf,bool) SECURITY DEFINER per
--            l'approvazione lato app da parte di una segretaria.
-- ============================================================
-- NOTA: la Leaked Password Protection di Supabase Auth
-- (HaveIBeenPwned) NON è una feature Postgres: si abilita
-- solo dal Dashboard Supabase (vedi sotto).
-- ============================================================

-- ============================================================
-- SEZIONE 0 — CLEANUP (decommentare solo se si riparte da zero)
-- ============================================================
-- DROP TABLE IF EXISTS messaggio          CASCADE;
-- DROP TABLE IF EXISTS chat               CASCADE;
-- DROP TABLE IF EXISTS notifica_config    CASCADE;
-- DROP TABLE IF EXISTS alimento           CASCADE;
-- DROP TABLE IF EXISTS alimento_pasto     CASCADE;
-- DROP TABLE IF EXISTS richiesta_collegamento CASCADE;
-- DROP TABLE IF EXISTS pagamento          CASCADE;
-- DROP TABLE IF EXISTS fattura            CASCADE;
-- DROP TABLE IF EXISTS visita             CASCADE;
-- DROP TABLE IF EXISTS tipologiaprestazione CASCADE;
-- DROP TABLE IF EXISTS pianoclinico       CASCADE;
-- DROP TABLE IF EXISTS sintomo            CASCADE;
-- DROP TABLE IF EXISTS pasto              CASCADE;
-- DROP TABLE IF EXISTS diariogiornaliero  CASCADE;
-- DROP TABLE IF EXISTS misurazione        CASCADE;
-- DROP TABLE IF EXISTS fascicoloclinico   CASCADE;
-- DROP TABLE IF EXISTS paziente           CASCADE;
-- DROP TABLE IF EXISTS specialista        CASCADE;
-- DROP TABLE IF EXISTS segretaria         CASCADE;
-- DROP TYPE  IF EXISTS stato_compilazione;
-- DROP TYPE  IF EXISTS stato_fascicolo;
-- DROP TYPE  IF EXISTS sesso_paziente;
-- DROP TYPE  IF EXISTS stato_piano;
-- DROP TYPE  IF EXISTS stato_visita;
-- DROP TYPE  IF EXISTS modalita_visita;
-- DROP TYPE  IF EXISTS tipologia_pasto;
-- DROP TYPE  IF EXISTS stato_richiesta;
-- DROP TYPE  IF EXISTS stato_messaggio;
-- DROP TYPE  IF EXISTS ruolo_utente;
-- DROP TYPE  IF EXISTS tipo_specializ_enum;

-- ============================================================
-- SEZIONE 1 — TIPI ENUM
-- ============================================================
CREATE TYPE stato_compilazione  AS ENUM ('COMPLETO', 'INCOMPLETO');
CREATE TYPE stato_fascicolo     AS ENUM ('Attivo', 'Archiviato');
CREATE TYPE sesso_paziente      AS ENUM ('M', 'F', 'Altro');
CREATE TYPE stato_piano         AS ENUM ('Bozza', 'Attivo', 'Sospeso', 'Archiviato');
CREATE TYPE stato_visita        AS ENUM ('Prenotata', 'Completata', 'Annullata');
CREATE TYPE modalita_visita     AS ENUM ('In Sede', 'Online');
CREATE TYPE tipologia_pasto     AS ENUM ('Colazione', 'Pranzo', 'Merenda', 'Cena');
CREATE TYPE stato_richiesta     AS ENUM ('In Attesa', 'Accettata', 'Rifiutata');
CREATE TYPE stato_messaggio     AS ENUM ('Inviato', 'Consegnato', 'Letto');
CREATE TYPE ruolo_utente        AS ENUM ('paziente', 'specialista', 'segretaria');
CREATE TYPE tipo_specializ_enum AS ENUM ('Nutrizionista', 'Dietista', 'Gastroenterologo');

-- ============================================================
-- SEZIONE 2 — TABELLE ESISTENTI (adattate per Supabase Auth)
-- ============================================================
-- ----------------------------------------------------------------
-- NOTA ARCHITETTURALE:
-- Supabase Auth gestisce email/password nella tabella auth.users
-- (schema auth, non modificabile direttamente).
-- Le nostre tabelle paziente, specialista, segretaria estendono
-- auth.users tramite la colonna "auth_uid" (uuid, FK → auth.users.id).
-- Il collegamento avviene tramite trigger at-signup (Sezione 6).
-- La colonna "Password" originale viene RIMOSSA: la gestione
-- password è interamente delegata a Supabase Auth.
-- ----------------------------------------------------------------

CREATE TABLE paziente (
  "CodiceFiscale"  char(16)        NOT NULL,
  "auth_uid"       uuid            UNIQUE,
  "Nome"           varchar(50)     NOT NULL,
  "Cognome"        varchar(50)     NOT NULL,
  "Telefono"       varchar(20)     DEFAULT NULL,
  "Email"          varchar(100)    DEFAULT NULL,
  "Sesso"          sesso_paziente  NOT NULL,
  "DataNascita"    date            NOT NULL,
  "Via"            varchar(100)    DEFAULT NULL,
  "Citta"          varchar(50)     DEFAULT NULL,
  "CAP"            char(5)         DEFAULT NULL,
  "created_at"     timestamptz     NOT NULL DEFAULT now(),
  "updated_at"     timestamptz     NOT NULL DEFAULT now(),
  PRIMARY KEY ("CodiceFiscale")
);
COMMENT ON COLUMN paziente.auth_uid   IS 'Collegamento a auth.users di Supabase';

CREATE TABLE specialista (
  "CodiceFiscale"     char(16)              NOT NULL,
  "auth_uid"          uuid                  UNIQUE,
  "Nome"              varchar(50)           NOT NULL,
  "Cognome"           varchar(50)           NOT NULL,
  "Telefono"          varchar(20)           DEFAULT NULL,
  "Email"             varchar(100)          DEFAULT NULL,
  "PartitaIVA"        varchar(20)           NOT NULL,
  "Info"              varchar(255)          DEFAULT NULL,
  "Specializzazione"  tipo_specializ_enum   DEFAULT NULL,
  "Citta"             varchar(50)           DEFAULT NULL,
  "Verificato"        boolean               NOT NULL DEFAULT false,
  "created_at"        timestamptz           NOT NULL DEFAULT now(),
  "updated_at"        timestamptz           NOT NULL DEFAULT now(),
  PRIMARY KEY ("CodiceFiscale")
);
COMMENT ON COLUMN specialista."Specializzazione" IS 'Specializzazione professionale (filtro RF13)';
COMMENT ON COLUMN specialista."Citta"            IS 'Città dello studio (filtro RF13)';
COMMENT ON COLUMN specialista."Verificato"       IS 'Approvazione admin (segretaria): lo specialista è discoverable/operativo solo se true. Default false; vedi ADR-0028. Su DB già esistenti: ALTER TABLE specialista ADD COLUMN IF NOT EXISTS "Verificato" boolean NOT NULL DEFAULT false; poi UPDATE specialista SET "Verificato"=true WHERE "auth_uid" IS NULL;';
CREATE INDEX idx_specialista_specializzazione
  ON specialista ("Specializzazione")
  WHERE "Specializzazione" IS NOT NULL;
CREATE INDEX idx_specialista_citta
  ON specialista ("Citta")
  WHERE "Citta" IS NOT NULL;

CREATE TABLE segretaria (
  "CodiceFiscale"  char(16)     NOT NULL,
  "auth_uid"       uuid         UNIQUE,
  "Nome"           varchar(50)  NOT NULL,
  "Cognome"        varchar(50)  NOT NULL,
  "Telefono"       varchar(20)  DEFAULT NULL,
  "Email"          varchar(100) DEFAULT NULL,
  "OrarioLavoro"   varchar(100) DEFAULT NULL,
  "created_at"     timestamptz  NOT NULL DEFAULT now(),
  PRIMARY KEY ("CodiceFiscale")
);

CREATE TABLE profilo_utente (
  "auth_uid"         uuid          PRIMARY KEY,
  "ruolo"            ruolo_utente  NOT NULL,
  "codice_fiscale"   char(16)      NOT NULL,
  "created_at"       timestamptz   NOT NULL DEFAULT now()
);
COMMENT ON TABLE profilo_utente IS
  'Registro centrale: mappa auth.users → ruolo (paziente/specialista/segretaria)';

CREATE TABLE tipologiaprestazione (
  "Nome"    varchar(100)   NOT NULL,
  "Costo"   numeric(8,2)   NOT NULL,
  "Durata"  integer        NOT NULL,
  PRIMARY KEY ("Nome"),
  CONSTRAINT chk_tipologia_costo_pos   CHECK ("Costo" > 0),
  CONSTRAINT chk_tipologia_durata_pos  CHECK ("Durata" > 0)
);

CREATE TABLE fascicoloclinico (
  "IdFascicolo"           serial          NOT NULL,
  "Stato"                 stato_fascicolo NOT NULL DEFAULT 'Attivo',
  "Note"                  varchar(255)    DEFAULT NULL,
  "CodFiscalePaziente"    char(16)        NOT NULL,
  "CodFiscaleSpecialista" char(16)        NOT NULL,
  "created_at"            timestamptz     NOT NULL DEFAULT now(),
  "updated_at"            timestamptz     NOT NULL DEFAULT now(),
  PRIMARY KEY ("IdFascicolo"),
  UNIQUE ("CodFiscalePaziente"),
  CONSTRAINT fascicolo_fk_paziente    FOREIGN KEY ("CodFiscalePaziente")
    REFERENCES paziente ("CodiceFiscale") ON UPDATE CASCADE,
  CONSTRAINT fascicolo_fk_specialista FOREIGN KEY ("CodFiscaleSpecialista")
    REFERENCES specialista ("CodiceFiscale") ON UPDATE CASCADE
);

CREATE TABLE pianoclinico (
  "IdPiano"       serial       NOT NULL,
  "Dieta"         varchar(255) NOT NULL,
  "Obiettivi"     varchar(100) NOT NULL,
  "Note"          varchar(255) DEFAULT NULL,
  "Stato"         stato_piano  NOT NULL    DEFAULT 'Bozza',
  "DataCreazione" date         NOT NULL    DEFAULT CURRENT_DATE,
  "IdFascicolo"   integer      NOT NULL,
  "created_at"    timestamptz  NOT NULL    DEFAULT now(),
  "updated_at"    timestamptz  NOT NULL    DEFAULT now(),
  PRIMARY KEY ("IdPiano"),
  CONSTRAINT piano_fk_fascicolo FOREIGN KEY ("IdFascicolo")
    REFERENCES fascicoloclinico ("IdFascicolo") ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT uq_piano_attivo UNIQUE NULLS NOT DISTINCT ("IdFascicolo", "Stato")
    DEFERRABLE INITIALLY DEFERRED
);

CREATE TABLE misurazione (
  "IdFascicolo" integer      NOT NULL,
  "Data"        date         NOT NULL,
  "Peso"        numeric(5,2) NOT NULL,
  "Altezza"     numeric(3,2) NOT NULL,
  PRIMARY KEY ("IdFascicolo", "Data"),
  CONSTRAINT misurazione_fk_fascicolo FOREIGN KEY ("IdFascicolo")
    REFERENCES fascicoloclinico ("IdFascicolo") ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT chk_mis_alt_pos  CHECK ("Altezza" > 0),
  CONSTRAINT chk_mis_peso_pos CHECK ("Peso" > 0)
);

CREATE TABLE diariogiornaliero (
  "IdFascicolo"       integer            NOT NULL,
  "Data"              date               NOT NULL,
  "NoteSpecialista"   varchar(255)       DEFAULT NULL,
  "StatoCompilazione" stato_compilazione NOT NULL DEFAULT 'INCOMPLETO',
  "created_at"        timestamptz        NOT NULL DEFAULT now(),
  "updated_at"        timestamptz        NOT NULL DEFAULT now(),
  PRIMARY KEY ("IdFascicolo", "Data"),
  CONSTRAINT diario_fk_fascicolo FOREIGN KEY ("IdFascicolo")
    REFERENCES fascicoloclinico ("IdFascicolo") ON DELETE CASCADE ON UPDATE CASCADE
);

CREATE TABLE pasto (
  "IdPasto"     bigserial       NOT NULL,
  "IdFascicolo" integer         NOT NULL,
  "Data"        date            NOT NULL,
  "Ora"         time            NOT NULL,
  "Tipologia"   tipologia_pasto NOT NULL,
  "Descrizione" varchar(255)    DEFAULT NULL,
  "created_at"  timestamptz     NOT NULL DEFAULT now(),
  PRIMARY KEY ("IdPasto"),
  UNIQUE ("IdFascicolo", "Data", "Ora"),
  CONSTRAINT pasto_fk_diario FOREIGN KEY ("IdFascicolo", "Data")
    REFERENCES diariogiornaliero ("IdFascicolo", "Data") ON DELETE CASCADE ON UPDATE CASCADE
);
COMMENT ON COLUMN pasto."Descrizione" IS
  'Campo testo libero; il dettaglio nutrizionale è in alimento_pasto';

CREATE TABLE sintomo (
  "IdSintomo"   bigserial    NOT NULL,
  "IdFascicolo" integer      NOT NULL,
  "Data"        date         NOT NULL,
  "Ora"         time         NOT NULL,
  "Descrizione" varchar(100) NOT NULL,
  "Intensita"   smallint     NOT NULL,
  "created_at"  timestamptz  NOT NULL DEFAULT now(),
  PRIMARY KEY ("IdSintomo"),
  UNIQUE ("IdFascicolo", "Data", "Ora"),
  CONSTRAINT sintomo_fk_diario FOREIGN KEY ("IdFascicolo", "Data")
    REFERENCES diariogiornaliero ("IdFascicolo", "Data") ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT chk_intensita CHECK ("Intensita" BETWEEN 1 AND 10)
);

CREATE TABLE visita (
  "IdVisita"              serial          NOT NULL,
  "Data"                  date            NOT NULL,
  "Ora"                   time            NOT NULL,
  "Stato"                 stato_visita    NOT NULL DEFAULT 'Prenotata',
  "NoteSvolgimento"       varchar(255)    DEFAULT NULL,
  "Modalita"              modalita_visita NOT NULL DEFAULT 'In Sede',
  "PrezzoReale"           numeric(8,2)    NOT NULL,
  "CodFiscalePaziente"    char(16)        NOT NULL,
  "CodFiscaleSpecialista" char(16)        NOT NULL,
  "NomeTipologia"         varchar(100)    NOT NULL,
  "IdPiano"               integer         DEFAULT NULL,
  "CodFiscaleSegretaria"  char(16)        DEFAULT NULL,
  "created_at"            timestamptz     NOT NULL DEFAULT now(),
  "updated_at"            timestamptz     NOT NULL DEFAULT now(),
  PRIMARY KEY ("IdVisita"),
  UNIQUE ("CodFiscaleSpecialista", "Data", "Ora"),
  CONSTRAINT visita_fk_paziente    FOREIGN KEY ("CodFiscalePaziente")
    REFERENCES paziente ("CodiceFiscale") ON UPDATE CASCADE,
  CONSTRAINT visita_fk_specialista FOREIGN KEY ("CodFiscaleSpecialista")
    REFERENCES specialista ("CodiceFiscale") ON UPDATE CASCADE,
  CONSTRAINT visita_fk_tipologia   FOREIGN KEY ("NomeTipologia")
    REFERENCES tipologiaprestazione ("Nome") ON UPDATE CASCADE,
  CONSTRAINT visita_fk_piano       FOREIGN KEY ("IdPiano")
    REFERENCES pianoclinico ("IdPiano") ON DELETE SET NULL ON UPDATE CASCADE,
  CONSTRAINT visita_fk_segretaria  FOREIGN KEY ("CodFiscaleSegretaria")
    REFERENCES segretaria ("CodiceFiscale") ON UPDATE CASCADE,
  CONSTRAINT chk_visita_prezzo_nonneg CHECK ("PrezzoReale" >= 0)
);

CREATE TABLE fattura (
  "Numero"               integer      NOT NULL,
  "Data"                 date         NOT NULL,
  "Importo"              numeric(8,2) NOT NULL,
  "ImportoResiduo"       numeric(8,2) NOT NULL,
  "IdVisita"             integer      NOT NULL,
  "CodFiscaleSegretaria" char(16)     DEFAULT NULL,
  PRIMARY KEY ("Numero"),
  UNIQUE ("IdVisita"),
  CONSTRAINT fattura_fk_visita     FOREIGN KEY ("IdVisita")
    REFERENCES visita ("IdVisita") ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT fattura_fk_segretaria FOREIGN KEY ("CodFiscaleSegretaria")
    REFERENCES segretaria ("CodiceFiscale") ON UPDATE CASCADE,
  CONSTRAINT chk_fattura_importo_pos    CHECK ("Importo" > 0),
  CONSTRAINT chk_fattura_residuo_range  CHECK ("ImportoResiduo" >= 0 AND "ImportoResiduo" <= "Importo")
);

CREATE TABLE pagamento (
  "NumeroFattura" integer      NOT NULL,
  "NumeroRata"    integer      NOT NULL,
  "Data"          date         NOT NULL,
  "Importo"       numeric(8,2) NOT NULL,
  PRIMARY KEY ("NumeroFattura", "NumeroRata"),
  CONSTRAINT pagamento_fk_fattura FOREIGN KEY ("NumeroFattura")
    REFERENCES fattura ("Numero") ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT chk_pagamento_importo_pos CHECK ("Importo" > 0)
);

-- ============================================================
-- SEZIONE 3 — TABELLE NUOVE (requisiti app)
-- ============================================================

-- 3.1 DATASET NUTRIZIONALE
CREATE TABLE alimento (
  "IdAlimento"             serial        NOT NULL,
  "Nome"                   varchar(150)  NOT NULL,
  "Alias"                  text[]        DEFAULT '{}',
  "Categoria"              varchar(80)   DEFAULT NULL,
  "LattosioP100g"          numeric(6,3)  NOT NULL DEFAULT 0,
  "SorbitoloP100g"         numeric(6,3)  NOT NULL DEFAULT 0,
  "GlutineP100g"           numeric(6,3)  NOT NULL DEFAULT 0,
  "CaloriePer100g"         numeric(6,2)  NOT NULL DEFAULT 0,
  "ProteinePer100g"        numeric(6,2)  DEFAULT NULL,
  "CarboPer100g"           numeric(6,2)  DEFAULT NULL,
  "GrassiPer100g"          numeric(6,2)  DEFAULT NULL,
  "FibrePer100g"           numeric(6,2)  DEFAULT NULL,
  "ConversioniUnitaMisura" jsonb         DEFAULT '{}',
  "FonteDati"              varchar(100)  DEFAULT 'BDA CREA',
  "created_at"             timestamptz   NOT NULL DEFAULT now(),
  "updated_at"             timestamptz   NOT NULL DEFAULT now(),
  PRIMARY KEY ("IdAlimento")
);
COMMENT ON TABLE  alimento IS 'Dataset nutrizionale (fonte: BDA CREA o simile)';
COMMENT ON COLUMN alimento."ConversioniUnitaMisura" IS
  'JSON: {"cucchiaio": 15, "tazza": 240} — grammi per unità di misura comune';
CREATE INDEX idx_alimento_nome  ON alimento USING gin(to_tsvector('italian', "Nome"));
CREATE INDEX idx_alimento_alias ON alimento USING gin("Alias");

-- 3.2 DETTAGLIO NUTRIZIONALE DEL PASTO
-- NOTA: le colonne *Calc sono colonne normali popolate dal trigger
-- calcola_nutrienti_pasto (Sezione 6.2). Non si può usare GENERATED
-- ALWAYS AS con subquery in PostgreSQL.
CREATE TABLE alimento_pasto (
  "IdAlimentoPasto"  bigserial    NOT NULL,
  "IdPasto"          bigint       NOT NULL,
  "IdAlimento"       integer      NOT NULL,
  "QuantitaGrammi"   numeric(7,2) NOT NULL,
  "UnitaMisuraOrig"  varchar(50)  DEFAULT 'g',
  "QuantitaOrig"     numeric(7,2) DEFAULT NULL,
  "LattosioCalc"     numeric(7,3) NOT NULL DEFAULT 0,
  "SorbitoloCalc"    numeric(7,3) NOT NULL DEFAULT 0,
  "GlutineCalc"      numeric(7,3) NOT NULL DEFAULT 0,
  "CalorieCalc"      numeric(7,2) NOT NULL DEFAULT 0,
  PRIMARY KEY ("IdAlimentoPasto"),
  CONSTRAINT ap_fk_pasto    FOREIGN KEY ("IdPasto")    REFERENCES pasto ("IdPasto")       ON DELETE CASCADE,
  CONSTRAINT ap_fk_alimento FOREIGN KEY ("IdAlimento") REFERENCES alimento ("IdAlimento") ON UPDATE CASCADE
);
COMMENT ON TABLE alimento_pasto IS
  'Righe di dettaglio di un pasto: un alimento con quantità e nutrienti calcolati (trigger calcola_nutrienti_pasto)';

-- 3.3 SISTEMA DI COLLEGAMENTO PAZIENTE ↔ SPECIALISTA
CREATE TABLE richiesta_collegamento (
  "IdRichiesta"           bigserial       NOT NULL,
  "CodFiscalePaziente"    char(16)        NOT NULL,
  "CodFiscaleSpecialista" char(16)        NOT NULL,
  "Stato"                 stato_richiesta NOT NULL DEFAULT 'In Attesa',
  "MessaggioRichiesta"    varchar(500)    DEFAULT NULL,
  "DataRichiesta"         timestamptz     NOT NULL DEFAULT now(),
  "DataRisposta"          timestamptz     DEFAULT NULL,
  "MotivazioneRifiuto"    varchar(500)    DEFAULT NULL,
  PRIMARY KEY ("IdRichiesta"),
  UNIQUE ("CodFiscalePaziente", "CodFiscaleSpecialista"),
  CONSTRAINT rc_fk_paziente    FOREIGN KEY ("CodFiscalePaziente")
    REFERENCES paziente ("CodiceFiscale") ON UPDATE CASCADE ON DELETE CASCADE,
  CONSTRAINT rc_fk_specialista FOREIGN KEY ("CodFiscaleSpecialista")
    REFERENCES specialista ("CodiceFiscale") ON UPDATE CASCADE ON DELETE CASCADE,
  CONSTRAINT chk_risposta_coerente CHECK (
    ("Stato" = 'In Attesa' AND "DataRisposta" IS NULL) OR
    ("Stato" IN ('Accettata', 'Rifiutata') AND "DataRisposta" IS NOT NULL)
  )
);
COMMENT ON TABLE richiesta_collegamento IS
  'Richieste di collegamento paziente→specialista; l''accettazione crea il fascicoloclinico';
CREATE INDEX idx_richiesta_specialista_stato
  ON richiesta_collegamento ("CodFiscaleSpecialista", "Stato");
CREATE INDEX idx_richiesta_paziente_stato
  ON richiesta_collegamento ("CodFiscalePaziente", "Stato");

-- 3.4 SISTEMA DI NOTIFICHE/PROMEMORIA
CREATE TABLE notifica_config (
  "IdConfig"           bigserial    NOT NULL,
  "CodFiscalePaziente" char(16)     NOT NULL,
  "Attiva"             boolean      NOT NULL DEFAULT true,
  "GiorniSettimana"    integer[]    NOT NULL DEFAULT '{1,2,3,4,5,6,7}',
  "Orario"             time         NOT NULL DEFAULT '20:00:00',
  "Messaggio"          varchar(200) DEFAULT 'Ricordati di compilare il tuo diario alimentare!',
  "created_at"         timestamptz  NOT NULL DEFAULT now(),
  "updated_at"         timestamptz  NOT NULL DEFAULT now(),
  PRIMARY KEY ("IdConfig"),
  CONSTRAINT nc_fk_paziente FOREIGN KEY ("CodFiscalePaziente")
    REFERENCES paziente ("CodiceFiscale") ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT chk_giorni_range CHECK (array_length("GiorniSettimana", 1) > 0)
);
COMMENT ON TABLE notifica_config IS
  'Configurazione promemoria compilazione diario per paziente';

-- 3.5 CHAT BIDIREZIONALE PAZIENTE ↔ SPECIALISTA
CREATE TABLE chat (
  "IdChat"                bigserial    NOT NULL,
  "CodFiscalePaziente"    char(16)     NOT NULL,
  "CodFiscaleSpecialista" char(16)     NOT NULL,
  "created_at"            timestamptz  NOT NULL DEFAULT now(),
  "ultimo_messaggio_at"   timestamptz  DEFAULT NULL,
  PRIMARY KEY ("IdChat"),
  UNIQUE ("CodFiscalePaziente", "CodFiscaleSpecialista"),
  CONSTRAINT chat_fk_paziente    FOREIGN KEY ("CodFiscalePaziente")
    REFERENCES paziente ("CodiceFiscale") ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT chat_fk_specialista FOREIGN KEY ("CodFiscaleSpecialista")
    REFERENCES specialista ("CodiceFiscale") ON DELETE CASCADE ON UPDATE CASCADE
);
COMMENT ON TABLE chat IS
  'Canale di chat 1-a-1 tra paziente e specialista collegato';

CREATE TABLE messaggio (
  "IdMessaggio"    bigserial       NOT NULL,
  "IdChat"         bigint          NOT NULL,
  "MittenteUid"    uuid            NOT NULL,
  "Testo"          text            NOT NULL,
  "Stato"          stato_messaggio NOT NULL DEFAULT 'Inviato',
  "created_at"     timestamptz     NOT NULL DEFAULT now(),
  "letto_at"       timestamptz     DEFAULT NULL,
  PRIMARY KEY ("IdMessaggio"),
  CONSTRAINT msg_fk_chat FOREIGN KEY ("IdChat")
    REFERENCES chat ("IdChat") ON DELETE CASCADE
);
CREATE INDEX idx_messaggio_chat     ON messaggio ("IdChat", "created_at");
CREATE INDEX idx_messaggio_mittente ON messaggio ("MittenteUid");
COMMENT ON TABLE messaggio IS 'Messaggi della chat real-time (lettura via Supabase Realtime)';

-- ============================================================
-- SEZIONE 4 — DATI DI ESEMPIO
-- ============================================================
INSERT INTO paziente
  ("CodiceFiscale","Nome","Cognome","Telefono","Email","Sesso","DataNascita","Via","Citta","CAP")
VALUES
('BNCLRA95B45H501W','Laura','Bianchi','3330000002','laura@email.it','F','1995-05-20','C.so Italia 5','Ancona','60100'),
('BRBRCC83A05H501T','Riccardo','Barbieri','3330000022','riki@email.it','M','1983-12-05','Via Battisti 7','Pesaro','61121'),
('BRNDVD75B14H501J','Davide','Bruno','3330000012','davide@email.it','M','1975-01-14','Via Gramsci 2','Loreto','60025'),
('CLBALE00M18H501F','Alessandro','Colombo','3330000008','ale@email.it','M','2000-09-18','Via Toti 12','Macerata','62100'),
('CNTFED84D28H501L','Federico','Conti','3330000014','fede@email.it','M','1984-04-28','Piazza Diaz 4','Ancona','60100'),
('CRSEMM95F55H501Y','Emma','Caruso','3330000027','emma@email.it','F','1995-06-15','Via XX Settembre','Tolentino','62029'),
('CSTELN94G54H501O','Elena','Costa','3330000017','elena@email.it','F','1994-07-14','C.so Stamira 18','Ancona','60100'),
('DLCSLV91E42H501M','Silvia','De Luca','3330000015','silvia@email.it','F','1991-05-02','Via Toti 10','Jesi','60035'),
('ESPANT82H25H501D','Anna','Esposito','3330000006','anna@email.it','F','1982-03-25','Via Verdi 9','Falconara','60015'),
('FNTLCE97B63H501U','Alice','Fontana','3330000023','alice@email.it','F','1997-02-23','P.zza del Popolo','Fermo','63900'),
('FRRMRC85E15H501C','Marco','Ferrari','3330000005','marco.f@email.it','M','1985-08-15','Via Dante 3','Osimo','60027'),
('FRRPLA85G07H501Z','Paolo','Ferrara','3330000028','paolo@email.it','M','1985-07-07','Via Dante 2','Camerino','62032'),
('GLLGIA92D50H501B','Giulia','Gallo','3330000004','giulia@email.it','F','1992-04-10','Via Mazzini 22','Senigallia','60019'),
('GLLGRT92H62H501A','Greta','Galli','3330000029','greta@email.it','F','1992-08-22','Via Manzoni 8','Fabriano','60044'),
('GLLMRT99C65H501K','Martina','Galli','3330000013','martina@email.it','F','1999-03-25','Via Veneto 8','Castelfidardo','60022'),
('GRCFRN80T58H501I','Francesca','Greco','3330000011','francesca@email.it','F','1980-12-18','Via Leopardi 6','Recanati','62019'),
('GRDGBR89H21H501P','Gabriele','Giordano','3330000018','gabry@email.it','M','1989-08-21','Via Isonzo 5','Senigallia','60019'),
('LMBLNZ81L11H501R','Lorenzo','Lombardi','3330000020','lorenzo@email.it','M','1981-10-11','Via Flaminia 20','Ancona','60100'),
('MNCMIC86F16H501N','Michele','Mancini','3330000016','michele@email.it','M','1986-06-16','Via Piave 3','Falconara','60015'),
('MRIGRV01D51H501W','Ginevra','Mariani','3330000025','ginevra@email.it','F','2001-04-11','Via Roma 55','Ascoli','63100'),
('MRNMAT93S22H501H','Mattia','Marino','3330000010','mattia@email.it','M','1993-06-22','C.so Umberto 1','Fano','61032'),
('MRTEDO89I03H501B','Edoardo','Martini','3330000030','edo@email.it','M','1989-09-03','Via Petrarca 10','Ancona','60100'),
('MRTGRG90M49H501S','Giorgia','Moretti','3330000021','giorgia@email.it','F','1990-11-09','Via Adriatica 1','Fano','61032'),
('RCCSRA87P48H501G','Sara','Ricci','3330000009','sara@email.it','F','1987-02-28','Via Rossini 5','Pesaro','61100'),
('RMNCHR98L55H501E','Chiara','Romano','3330000007','chiara@email.it','F','1998-11-05','C.so Stamira 8','Ancona','60100'),
('RNLSMN79E19H501X','Simone','Rinaldi','3330000026','simo@email.it','M','1979-05-19','C.so Mazzini 4','S.Benedetto','63074'),
('RSSGNN88C12H501A','Giovanni','Rossi','3330000003','gio@email.it','M','1988-12-10','P.zza Cavour 1','Jesi','60035'),
('RZZBTR96I66H501Q','Beatrice','Rizzo','3330000019','bea@email.it','F','1996-09-26','Via Trieste 9','Osimo','60027'),
('SNTTOM88C29H501V','Tommaso','Santoro','3330000024','tommy@email.it','M','1988-03-29','Via Marche 12','Ancona','60100'),
('TSTPLA00A00H501Z','Test','SenzaPiano','3330000000','test.senzapiano@nutrease.it','M','2000-01-01','Via Test 1','Ancona','60100'),
('VRDMRR90A01H501Q','Mario','Verdi','3330000001','mario@email.it','M','1990-01-15','Via Roma 10','Ancona','60100');

INSERT INTO specialista ("CodiceFiscale","Nome","Cognome","Telefono","Email","PartitaIVA","Info","Specializzazione","Citta") VALUES
('LSEMRC80A01H501U','Marco','Elisei','3331234567','marco@nutrease.it','12345678901','Nutrizionista Sportivo','Nutrizionista','Ancona');

-- ADR-0028: gli specialisti del seed nascono già "verificati". Il filtro auth_uid IS NULL
-- colpisce SOLO le righe demo (gli account registrati dall'app hanno sempre auth_uid
-- valorizzato dal trigger crea_profilo_da_auth) → questa UPDATE non riabilita mai per
-- sbaglio uno specialista reale in attesa di approvazione, anche se lo script viene rieseguito.
UPDATE specialista SET "Verificato" = TRUE WHERE "auth_uid" IS NULL;

INSERT INTO segretaria ("CodiceFiscale","Nome","Cognome","Telefono","Email","OrarioLavoro") VALUES
('RSSGLI85M50H501Z','Giulia','Rossi','3339876543','segreteria@nutrease.it','Lun-Ven 9-18');

INSERT INTO tipologiaprestazione VALUES
('Analisi BIA (Bioimpedenziometria)',40.00,20),
('Analisi Diario Alimentare (Remoto)',20.00,20),
('Educazione Alimentare (Spesa Guidata)',80.00,60),
('Nutrizione Clinica / Patologica',130.00,60),
('Nutrizione Sportiva Agonistica',150.00,60),
('Prima Visita Nutrizionale Completa',120.00,60),
('Videoconsulto / Telemedicina',50.00,30),
('Visita di Controllo Periodica',60.00,30);

INSERT INTO fascicoloclinico ("IdFascicolo","Stato","Note","CodFiscalePaziente","CodFiscaleSpecialista") VALUES
(1,'Attivo','Obiettivi condivisi e tracciati via app.','BNCLRA95B45H501W','LSEMRC80A01H501U'),
(2,'Attivo','Monitoraggio intensivo sintomi.','BRBRCC83A05H501T','LSEMRC80A01H501U'),
(3,'Attivo','Monitoraggio intensivo sintomi.','BRNDVD75B14H501J','LSEMRC80A01H501U'),
(4,'Attivo','In trattamento nutrizionale.','CLBALE00M18H501F','LSEMRC80A01H501U'),
(5,'Attivo','Obiettivi condivisi e tracciati via app.','CNTFED84D28H501L','LSEMRC80A01H501U'),
(6,'Attivo','Paziente in fase di rieducazione alimentare.','CRSEMM95F55H501Y','LSEMRC80A01H501U'),
(7,'Attivo','Obiettivi condivisi e tracciati via app.','CSTELN94G54H501O','LSEMRC80A01H501U'),
(8,'Attivo','Obiettivi condivisi e tracciati via app.','DLCSLV91E42H501M','LSEMRC80A01H501U'),
(9,'Attivo','Follow-up programmato.','ESPANT82H25H501D','LSEMRC80A01H501U'),
(10,'Attivo','Monitoraggio intensivo sintomi.','FNTLCE97B63H501U','LSEMRC80A01H501U'),
(11,'Attivo','Follow-up programmato.','FRRMRC85E15H501C','LSEMRC80A01H501U'),
(12,'Attivo','Monitoraggio intensivo sintomi.','FRRPLA85G07H501Z','LSEMRC80A01H501U'),
(13,'Attivo','Paziente in fase di rieducazione alimentare.','GLLGIA92D50H501B','LSEMRC80A01H501U'),
(14,'Attivo','In trattamento nutrizionale.','GLLGRT92H62H501A','LSEMRC80A01H501U'),
(15,'Attivo','In trattamento nutrizionale.','GLLMRT99C65H501K','LSEMRC80A01H501U'),
(16,'Attivo','Follow-up programmato.','GRCFRN80T58H501I','LSEMRC80A01H501U'),
(17,'Attivo','In trattamento nutrizionale.','GRDGBR89H21H501P','LSEMRC80A01H501U'),
(18,'Attivo','Monitoraggio intensivo sintomi.','LMBLNZ81L11H501R','LSEMRC80A01H501U'),
(19,'Attivo','Obiettivi condivisi e tracciati via app.','MNCMIC86F16H501N','LSEMRC80A01H501U'),
(20,'Attivo','In trattamento nutrizionale.','MRIGRV01D51H501W','LSEMRC80A01H501U'),
(21,'Attivo','Follow-up programmato.','MRNMAT93S22H501H','LSEMRC80A01H501U'),
(22,'Attivo','Follow-up programmato.','MRTEDO89I03H501B','LSEMRC80A01H501U'),
(23,'Attivo','Follow-up programmato.','MRTGRG90M49H501S','LSEMRC80A01H501U'),
(24,'Attivo','Follow-up programmato.','RCCSRA87P48H501G','LSEMRC80A01H501U'),
(25,'Attivo','In trattamento nutrizionale.','RMNCHR98L55H501E','LSEMRC80A01H501U'),
(26,'Attivo','Follow-up programmato.','RNLSMN79E19H501X','LSEMRC80A01H501U'),
(27,'Attivo','Monitoraggio intensivo sintomi.','RSSGNN88C12H501A','LSEMRC80A01H501U'),
(28,'Attivo','Obiettivi condivisi e tracciati via app.','RZZBTR96I66H501Q','LSEMRC80A01H501U'),
(29,'Attivo','Obiettivi condivisi e tracciati via app.','SNTTOM88C29H501V','LSEMRC80A01H501U'),
(30,'Attivo','Follow-up programmato.','VRDMRR90A01H501Q','LSEMRC80A01H501U'),
(31,'Attivo','Fascicolo creato senza piani clinici.','TSTPLA00A00H501Z','LSEMRC80A01H501U');
SELECT setval(pg_get_serial_sequence('fascicoloclinico','IdFascicolo'), 31);

-- Dataset alimentare: caricare ESCLUSIVAMENTE da `sql/alimento_seed.sql`
-- (1.255 voci dal dataset CSV + 109 integrate con stime da letteratura,
-- sanity-check finale nel seed file). Le righe di esempio precedenti
-- ("Pasta di grano duro" etc.) sono state rimosse perché in conflitto
-- con la sanity-check del seed.

-- ============================================================
-- SEZIONE 5 — ROW LEVEL SECURITY (RLS) + POLICY
-- ============================================================

CREATE OR REPLACE FUNCTION get_ruolo()
RETURNS ruolo_utente
LANGUAGE sql STABLE
SET search_path TO public
AS $$
  SELECT ruolo FROM profilo_utente WHERE auth_uid = auth.uid();
$$;

CREATE OR REPLACE FUNCTION get_cf()
RETURNS char(16)
LANGUAGE sql STABLE
SET search_path TO public
AS $$
  SELECT codice_fiscale FROM profilo_utente WHERE auth_uid = auth.uid();
$$;

CREATE OR REPLACE FUNCTION get_pazienti_collegati()
RETURNS SETOF char(16)
LANGUAGE sql STABLE
SET search_path TO public
AS $$
  SELECT "CodFiscalePaziente"
  FROM fascicoloclinico
  WHERE "CodFiscaleSpecialista" = get_cf()
    AND "Stato" = 'Attivo';
$$;

-- ADR-0028: true sse lo specialista corrente è stato approvato (Verificato=true).
-- COALESCE→false per i non-specialisti (nessuna riga). SECURITY INVOKER come gli altri
-- helper RLS: legge la PROPRIA riga specialista, consentita da specialista_select.
CREATE OR REPLACE FUNCTION is_specialista_verificato()
RETURNS boolean
LANGUAGE sql STABLE
SET search_path TO public
AS $$
  SELECT COALESCE(
    (SELECT "Verificato" FROM specialista WHERE auth_uid = auth.uid()),
    false
  );
$$;

ALTER TABLE profilo_utente         ENABLE ROW LEVEL SECURITY;
ALTER TABLE paziente               ENABLE ROW LEVEL SECURITY;
ALTER TABLE specialista            ENABLE ROW LEVEL SECURITY;
ALTER TABLE segretaria             ENABLE ROW LEVEL SECURITY;
ALTER TABLE fascicoloclinico       ENABLE ROW LEVEL SECURITY;
ALTER TABLE pianoclinico           ENABLE ROW LEVEL SECURITY;
ALTER TABLE misurazione            ENABLE ROW LEVEL SECURITY;
ALTER TABLE diariogiornaliero      ENABLE ROW LEVEL SECURITY;
ALTER TABLE pasto                  ENABLE ROW LEVEL SECURITY;
ALTER TABLE sintomo                ENABLE ROW LEVEL SECURITY;
ALTER TABLE alimento_pasto         ENABLE ROW LEVEL SECURITY;
ALTER TABLE visita                 ENABLE ROW LEVEL SECURITY;
ALTER TABLE fattura                ENABLE ROW LEVEL SECURITY;
ALTER TABLE pagamento              ENABLE ROW LEVEL SECURITY;
ALTER TABLE tipologiaprestazione   ENABLE ROW LEVEL SECURITY;
ALTER TABLE alimento               ENABLE ROW LEVEL SECURITY;
ALTER TABLE richiesta_collegamento ENABLE ROW LEVEL SECURITY;
ALTER TABLE notifica_config        ENABLE ROW LEVEL SECURITY;
ALTER TABLE chat                   ENABLE ROW LEVEL SECURITY;
ALTER TABLE messaggio              ENABLE ROW LEVEL SECURITY;

-- profilo_utente
CREATE POLICY "profilo_utente_self"
  ON profilo_utente FOR ALL
  USING (auth_uid = auth.uid())
  WITH CHECK (auth_uid = auth.uid());

-- paziente
CREATE POLICY "paziente_self_select"
  ON paziente FOR SELECT
  USING (
    "auth_uid" = auth.uid()
    OR get_ruolo() = 'specialista'
    OR get_ruolo() = 'segretaria'
  );
CREATE POLICY "paziente_self_update"
  ON paziente FOR UPDATE
  USING ("auth_uid" = auth.uid())
  WITH CHECK ("auth_uid" = auth.uid());
CREATE POLICY "paziente_insert_signup"
  ON paziente FOR INSERT
  WITH CHECK ("auth_uid" = auth.uid());

-- specialista
-- ADR-0028: i pazienti vedono SOLO gli specialisti verificati (discovery RF13). Lo
-- specialista vede sempre la propria riga (qualsiasi stato, per la UI "in verifica");
-- la segretaria vede tutti, anche quelli da approvare.
CREATE POLICY "specialista_select"
  ON specialista FOR SELECT
  USING (
    "auth_uid" = auth.uid()
    OR get_ruolo() = 'segretaria'
    OR (get_ruolo() = 'paziente' AND "Verificato" = TRUE)
  );
CREATE POLICY "specialista_self_update"
  ON specialista FOR UPDATE
  USING ("auth_uid" = auth.uid())
  WITH CHECK ("auth_uid" = auth.uid());
CREATE POLICY "specialista_insert_signup"
  ON specialista FOR INSERT
  WITH CHECK ("auth_uid" = auth.uid());

-- segretaria
CREATE POLICY "segretaria_self"
  ON segretaria FOR ALL
  USING ("auth_uid" = auth.uid() OR get_ruolo() = 'specialista');

-- tipologiaprestazione
CREATE POLICY "tipologia_read_all"
  ON tipologiaprestazione FOR SELECT
  USING (true);
CREATE POLICY "tipologia_write_specialista"
  ON tipologiaprestazione FOR ALL
  USING (get_ruolo() = 'specialista');

-- alimento
CREATE POLICY "alimento_read_all"
  ON alimento FOR SELECT
  USING (true);
CREATE POLICY "alimento_write_specialista"
  ON alimento FOR ALL
  USING (get_ruolo() = 'specialista')
  WITH CHECK (get_ruolo() = 'specialista');

-- richiesta_collegamento
CREATE POLICY "rc_paziente_select"
  ON richiesta_collegamento FOR SELECT
  USING (
    "CodFiscalePaziente" = get_cf()
    OR "CodFiscaleSpecialista" = get_cf()
  );
CREATE POLICY "rc_paziente_insert"
  ON richiesta_collegamento FOR INSERT
  WITH CHECK (
    get_ruolo() = 'paziente'
    AND "CodFiscalePaziente" = get_cf()
  );
-- Re-invio dopo un rifiuto: il paziente fa l'upsert che riporta a 'In Attesa' la
-- propria richiesta (azzerando DataRisposta/MotivazioneRifiuto), invece di inserire
-- una riga duplicata che violerebbe la UNIQUE (CodFiscalePaziente, CodFiscaleSpecialista).
CREATE POLICY "rc_paziente_update"
  ON richiesta_collegamento FOR UPDATE
  USING (
    get_ruolo() = 'paziente'
    AND "CodFiscalePaziente" = get_cf()
  )
  WITH CHECK (
    get_ruolo() = 'paziente'
    AND "CodFiscalePaziente" = get_cf()
  );
-- ADR-0028: solo uno specialista VERIFICATO può accettare/rifiutare (difesa in profondità:
-- un non verificato non è discoverable, quindi non dovrebbe avere richieste, ma il gate
-- garantisce che non possa comunque crearsi un fascicolo via trg_richiesta_accettata).
CREATE POLICY "rc_specialista_update"
  ON richiesta_collegamento FOR UPDATE
  USING (
    get_ruolo() = 'specialista'
    AND "CodFiscaleSpecialista" = get_cf()
    AND is_specialista_verificato()
  )
  WITH CHECK (
    get_ruolo() = 'specialista'
    AND "CodFiscaleSpecialista" = get_cf()
    AND is_specialista_verificato()
  );

-- fascicoloclinico
CREATE POLICY "fascicolo_paziente_select"
  ON fascicoloclinico FOR SELECT
  USING (
    (get_ruolo() = 'paziente' AND "CodFiscalePaziente" = get_cf())
    OR (get_ruolo() = 'specialista' AND "CodFiscaleSpecialista" = get_cf())
  );
CREATE POLICY "fascicolo_specialista_write"
  ON fascicoloclinico FOR INSERT
  WITH CHECK (
    get_ruolo() = 'specialista'
    AND "CodFiscaleSpecialista" = get_cf()
  );
CREATE POLICY "fascicolo_specialista_update"
  ON fascicoloclinico FOR UPDATE
  USING (get_ruolo() = 'specialista' AND "CodFiscaleSpecialista" = get_cf())
  WITH CHECK (get_ruolo() = 'specialista' AND "CodFiscaleSpecialista" = get_cf());

-- pianoclinico
CREATE POLICY "piano_select"
  ON pianoclinico FOR SELECT
  USING (
    EXISTS (
      SELECT 1 FROM fascicoloclinico f
      WHERE f."IdFascicolo" = pianoclinico."IdFascicolo"
        AND (
          (get_ruolo() = 'paziente' AND f."CodFiscalePaziente" = get_cf())
          OR (get_ruolo() = 'specialista' AND f."CodFiscaleSpecialista" = get_cf())
        )
    )
  );
CREATE POLICY "piano_write_specialista"
  ON pianoclinico FOR ALL
  USING (
    get_ruolo() = 'specialista'
    AND EXISTS (
      SELECT 1 FROM fascicoloclinico f
      WHERE f."IdFascicolo" = pianoclinico."IdFascicolo"
        AND f."CodFiscaleSpecialista" = get_cf()
    )
  );

-- misurazione
CREATE POLICY "misurazione_select"
  ON misurazione FOR SELECT
  USING (
    EXISTS (
      SELECT 1 FROM fascicoloclinico f
      WHERE f."IdFascicolo" = misurazione."IdFascicolo"
        AND (
          (get_ruolo() = 'paziente' AND f."CodFiscalePaziente" = get_cf())
          OR (get_ruolo() = 'specialista' AND f."CodFiscaleSpecialista" = get_cf())
        )
    )
  );
CREATE POLICY "misurazione_write"
  ON misurazione FOR ALL
  USING (
    EXISTS (
      SELECT 1 FROM fascicoloclinico f
      WHERE f."IdFascicolo" = misurazione."IdFascicolo"
        AND (
          (get_ruolo() = 'paziente' AND f."CodFiscalePaziente" = get_cf())
          OR (get_ruolo() = 'specialista' AND f."CodFiscaleSpecialista" = get_cf())
        )
    )
  );

-- diariogiornaliero
CREATE POLICY "diario_paziente"
  ON diariogiornaliero FOR ALL
  USING (
    EXISTS (
      SELECT 1 FROM fascicoloclinico f
      WHERE f."IdFascicolo" = diariogiornaliero."IdFascicolo"
        AND (
          (get_ruolo() = 'paziente' AND f."CodFiscalePaziente" = get_cf())
          OR (get_ruolo() = 'specialista' AND f."CodFiscaleSpecialista" = get_cf())
        )
    )
  );

-- pasto
CREATE POLICY "pasto_select"
  ON pasto FOR SELECT
  USING (
    EXISTS (
      SELECT 1 FROM fascicoloclinico f
      WHERE f."IdFascicolo" = pasto."IdFascicolo"
        AND (
          (get_ruolo() = 'paziente' AND f."CodFiscalePaziente" = get_cf())
          OR (get_ruolo() = 'specialista' AND f."CodFiscaleSpecialista" = get_cf())
        )
    )
  );
CREATE POLICY "pasto_paziente_write"
  ON pasto FOR INSERT
  WITH CHECK (
    get_ruolo() = 'paziente'
    AND EXISTS (
      SELECT 1 FROM fascicoloclinico f
      WHERE f."IdFascicolo" = pasto."IdFascicolo"
        AND f."CodFiscalePaziente" = get_cf()
    )
  );
CREATE POLICY "pasto_paziente_update_delete"
  ON pasto FOR UPDATE
  USING (
    get_ruolo() = 'paziente'
    AND EXISTS (
      SELECT 1 FROM fascicoloclinico f
      WHERE f."IdFascicolo" = pasto."IdFascicolo"
        AND f."CodFiscalePaziente" = get_cf()
    )
  );
CREATE POLICY "pasto_paziente_delete"
  ON pasto FOR DELETE
  USING (
    get_ruolo() = 'paziente'
    AND EXISTS (
      SELECT 1 FROM fascicoloclinico f
      WHERE f."IdFascicolo" = pasto."IdFascicolo"
        AND f."CodFiscalePaziente" = get_cf()
    )
  );

-- sintomo
CREATE POLICY "sintomo_select"
  ON sintomo FOR SELECT
  USING (
    EXISTS (
      SELECT 1 FROM fascicoloclinico f
      WHERE f."IdFascicolo" = sintomo."IdFascicolo"
        AND (
          (get_ruolo() = 'paziente' AND f."CodFiscalePaziente" = get_cf())
          OR (get_ruolo() = 'specialista' AND f."CodFiscaleSpecialista" = get_cf())
        )
    )
  );
CREATE POLICY "sintomo_paziente_write"
  ON sintomo FOR ALL
  USING (
    get_ruolo() = 'paziente'
    AND EXISTS (
      SELECT 1 FROM fascicoloclinico f
      WHERE f."IdFascicolo" = sintomo."IdFascicolo"
        AND f."CodFiscalePaziente" = get_cf()
    )
  );

-- alimento_pasto
CREATE POLICY "ap_select"
  ON alimento_pasto FOR SELECT
  USING (
    EXISTS (
      SELECT 1 FROM pasto p
      JOIN fascicoloclinico f ON f."IdFascicolo" = p."IdFascicolo"
      WHERE p."IdPasto" = alimento_pasto."IdPasto"
        AND (
          (get_ruolo() = 'paziente' AND f."CodFiscalePaziente" = get_cf())
          OR (get_ruolo() = 'specialista' AND f."CodFiscaleSpecialista" = get_cf())
        )
    )
  );
CREATE POLICY "ap_paziente_write"
  ON alimento_pasto FOR ALL
  USING (
    get_ruolo() = 'paziente'
    AND EXISTS (
      SELECT 1 FROM pasto p
      JOIN fascicoloclinico f ON f."IdFascicolo" = p."IdFascicolo"
      WHERE p."IdPasto" = alimento_pasto."IdPasto"
        AND f."CodFiscalePaziente" = get_cf()
    )
  );

-- visita
CREATE POLICY "visita_select"
  ON visita FOR SELECT
  USING (
    "CodFiscalePaziente" = get_cf()
    OR "CodFiscaleSpecialista" = get_cf()
    OR get_ruolo() = 'segretaria'
  );
CREATE POLICY "visita_write_segretaria_specialista"
  ON visita FOR ALL
  USING (
    get_ruolo() IN ('segretaria', 'specialista')
    AND (
      "CodFiscaleSpecialista" = get_cf()
      OR get_ruolo() = 'segretaria'
    )
  );

-- fattura / pagamento
CREATE POLICY "fattura_select"
  ON fattura FOR SELECT
  USING (
    get_ruolo() = 'segretaria'
    OR EXISTS (
      SELECT 1 FROM visita v
      WHERE v."IdVisita" = fattura."IdVisita"
        AND (
          v."CodFiscalePaziente" = get_cf()
          OR v."CodFiscaleSpecialista" = get_cf()
        )
    )
  );
CREATE POLICY "fattura_write_segretaria"
  ON fattura FOR ALL
  USING (get_ruolo() = 'segretaria')
  WITH CHECK (get_ruolo() = 'segretaria');

CREATE POLICY "pagamento_select"
  ON pagamento FOR SELECT
  USING (
    get_ruolo() = 'segretaria'
    OR EXISTS (
      SELECT 1 FROM fattura f
      JOIN visita v ON v."IdVisita" = f."IdVisita"
      WHERE f."Numero" = pagamento."NumeroFattura"
        AND (
          v."CodFiscalePaziente" = get_cf()
          OR v."CodFiscaleSpecialista" = get_cf()
        )
    )
  );
CREATE POLICY "pagamento_write_segretaria"
  ON pagamento FOR ALL
  USING (get_ruolo() = 'segretaria')
  WITH CHECK (get_ruolo() = 'segretaria');

-- notifica_config
CREATE POLICY "notifica_self"
  ON notifica_config FOR ALL
  USING ("CodFiscalePaziente" = get_cf())
  WITH CHECK ("CodFiscalePaziente" = get_cf());

-- chat
CREATE POLICY "chat_select"
  ON chat FOR SELECT
  USING (
    "CodFiscalePaziente" = get_cf()
    OR "CodFiscaleSpecialista" = get_cf()
  );
CREATE POLICY "chat_insert"
  ON chat FOR INSERT
  WITH CHECK (
    (get_ruolo() = 'paziente' AND "CodFiscalePaziente" = get_cf())
    OR (get_ruolo() = 'specialista' AND "CodFiscaleSpecialista" = get_cf())
  );

-- messaggio
CREATE POLICY "messaggio_select"
  ON messaggio FOR SELECT
  USING (
    EXISTS (
      SELECT 1 FROM chat c
      WHERE c."IdChat" = messaggio."IdChat"
        AND (c."CodFiscalePaziente" = get_cf() OR c."CodFiscaleSpecialista" = get_cf())
    )
  );
CREATE POLICY "messaggio_insert"
  ON messaggio FOR INSERT
  WITH CHECK (
    "MittenteUid" = auth.uid()
    AND EXISTS (
      SELECT 1 FROM chat c
      WHERE c."IdChat" = messaggio."IdChat"
        AND (c."CodFiscalePaziente" = get_cf() OR c."CodFiscaleSpecialista" = get_cf())
    )
  );
CREATE POLICY "messaggio_update_stato"
  ON messaggio FOR UPDATE
  USING ("MittenteUid" = auth.uid())
  WITH CHECK ("MittenteUid" = auth.uid());

-- ============================================================
-- SEZIONE 6 — HELPER FUNCTIONS, TRIGGERS & VIEWS
-- ============================================================

-- 6.1 updated_at
CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS trigger LANGUAGE plpgsql
SET search_path TO public
AS $$
BEGIN
  NEW.updated_at = now();
  RETURN NEW;
END;
$$;

CREATE TRIGGER trg_paziente_updated_at
  BEFORE UPDATE ON paziente
  FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_specialista_updated_at
  BEFORE UPDATE ON specialista
  FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_fascicolo_updated_at
  BEFORE UPDATE ON fascicoloclinico
  FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_piano_updated_at
  BEFORE UPDATE ON pianoclinico
  FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_diario_updated_at
  BEFORE UPDATE ON diariogiornaliero
  FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_notifica_updated_at
  BEFORE UPDATE ON notifica_config
  FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- 6.1-bis ANTI AUTO-PROMOZIONE SPECIALISTA (ADR-0028)
-- La policy specialista_self_update consente allo specialista di aggiornare la PROPRIA
-- riga (modifica profilo). Senza questo guard potrebbe però impostarsi "Verificato"=true
-- e auto-approvarsi. Il guard blocca ogni cambio di "Verificato" che non arrivi da:
--   * una segretaria (get_ruolo()='segretaria') — es. via RPC verifica_specialista();
--   * il service_role / Dashboard (auth.uid() IS NULL, nessun JWT app).
-- Gli UPDATE di profilo (Nome/Cognome/...) non toccano "Verificato" → NEW=OLD → passano.
-- L'INSERT in fase di registrazione non attiva questo trigger (è BEFORE UPDATE).
CREATE OR REPLACE FUNCTION blocca_self_verifica_specialista()
RETURNS trigger
LANGUAGE plpgsql
SECURITY INVOKER
SET search_path TO public
AS $$
BEGIN
  IF NEW."Verificato" IS DISTINCT FROM OLD."Verificato"
     AND auth.uid() IS NOT NULL
     AND get_ruolo() IS DISTINCT FROM 'segretaria' THEN
    RAISE EXCEPTION 'Solo un amministratore puo modificare lo stato di verifica dello specialista';
  END IF;
  RETURN NEW;
END;
$$;
DROP TRIGGER IF EXISTS trg_blocca_self_verifica ON specialista;
CREATE TRIGGER trg_blocca_self_verifica
  BEFORE UPDATE ON specialista
  FOR EACH ROW EXECUTE FUNCTION blocca_self_verifica_specialista();

-- 6.2 calcolo nutrienti in alimento_pasto
CREATE OR REPLACE FUNCTION calcola_nutrienti_pasto()
RETURNS trigger LANGUAGE plpgsql
SET search_path TO public
AS $$
DECLARE
  v_alimento alimento%ROWTYPE;
  v_factor   numeric;
BEGIN
  SELECT * INTO v_alimento FROM alimento WHERE "IdAlimento" = NEW."IdAlimento";
  v_factor := NEW."QuantitaGrammi" / 100.0;
  NEW."LattosioCalc"  := ROUND(v_alimento."LattosioP100g"  * v_factor, 3);
  NEW."SorbitoloCalc" := ROUND(v_alimento."SorbitoloP100g" * v_factor, 3);
  NEW."GlutineCalc"   := ROUND(v_alimento."GlutineP100g"   * v_factor, 3);
  NEW."CalorieCalc"   := ROUND(v_alimento."CaloriePer100g" * v_factor, 2);
  RETURN NEW;
END;
$$;
CREATE TRIGGER trg_calcola_nutrienti
  BEFORE INSERT OR UPDATE ON alimento_pasto
  FOR EACH ROW EXECUTE FUNCTION calcola_nutrienti_pasto();

-- 6.3 StatoCompilazione diario
-- SECURITY DEFINER: il trigger scatta su INSERT/UPDATE/DELETE di pasto (fatti dal
-- paziente) ma riscrive su diariogiornaliero (tabella con RLS). Il controllo d'accesso
-- resta sull'operazione su pasto (policy pasto_*); il trigger tocca solo il diario dello
-- stesso fascicolo. Pattern standard Supabase (ADR-0023).
CREATE OR REPLACE FUNCTION aggiorna_stato_diario()
RETURNS trigger LANGUAGE plpgsql
SECURITY DEFINER                 -- gira come owner: bypassa le RLS su diariogiornaliero
SET search_path TO public        -- obbligatorio con SECURITY DEFINER (hardening)
AS $$
DECLARE
  v_idf integer;
  v_data date;
  v_colazione int;
  v_pranzo    int;
  v_cena      int;
  v_nuovo_stato stato_compilazione;
BEGIN
  IF TG_OP = 'DELETE' THEN
    v_idf  := OLD."IdFascicolo";
    v_data := OLD."Data";
  ELSE
    v_idf  := NEW."IdFascicolo";
    v_data := NEW."Data";
  END IF;
  SELECT
    COUNT(*) FILTER (WHERE "Tipologia" = 'Colazione'),
    COUNT(*) FILTER (WHERE "Tipologia" = 'Pranzo'),
    COUNT(*) FILTER (WHERE "Tipologia" = 'Cena')
  INTO v_colazione, v_pranzo, v_cena
  FROM pasto
  WHERE "IdFascicolo" = v_idf AND "Data" = v_data;
  IF v_colazione > 0 AND v_pranzo > 0 AND v_cena > 0 THEN
    v_nuovo_stato := 'COMPLETO';
  ELSE
    v_nuovo_stato := 'INCOMPLETO';
  END IF;
  UPDATE diariogiornaliero
  SET "StatoCompilazione" = v_nuovo_stato, "updated_at" = now()
  WHERE "IdFascicolo" = v_idf AND "Data" = v_data;
  RETURN NULL;
END;
$$;
CREATE TRIGGER trg_stato_diario_on_pasto
  AFTER INSERT OR UPDATE OR DELETE ON pasto
  FOR EACH ROW EXECUTE FUNCTION aggiorna_stato_diario();

-- 6.4 auto-creazione diariogiornaliero
-- SECURITY DEFINER: il trigger scatta su INSERT di pasto/sintomo (fatti dal paziente) ma
-- inserisce su diariogiornaliero (tabella con RLS). Sotto INVOKER passa già (la policy
-- diario_paziente lascia inserire il paziente titolare del fascicolo), ma allineiamo al
-- pattern standard Supabase per robustezza futura (ADR-0023, Prevenzione §1).
CREATE OR REPLACE FUNCTION crea_diario_se_mancante()
RETURNS trigger LANGUAGE plpgsql
SECURITY DEFINER                 -- gira come owner: bypassa le RLS su diariogiornaliero
SET search_path TO public        -- obbligatorio con SECURITY DEFINER (hardening)
AS $$
BEGIN
  INSERT INTO diariogiornaliero ("IdFascicolo", "Data")
  VALUES (NEW."IdFascicolo", NEW."Data")
  ON CONFLICT ("IdFascicolo", "Data") DO NOTHING;
  RETURN NEW;
END;
$$;
CREATE TRIGGER trg_crea_diario_auto
  BEFORE INSERT ON pasto
  FOR EACH ROW EXECUTE FUNCTION crea_diario_se_mancante();
CREATE TRIGGER trg_crea_diario_auto_sintomo
  BEFORE INSERT ON sintomo
  FOR EACH ROW EXECUTE FUNCTION crea_diario_se_mancante();

-- 6.5 accettazione richiesta → fascicolo + chat
-- SECURITY DEFINER: il trigger scatta sull'UPDATE fatto dallo specialista, ma deve
-- scrivere su fascicoloclinico/chat. Le policy su quelle tabelle ammettono solo lo
-- specialista già titolare del fascicolo: senza DEFINER l'INSERT iniziale verrebbe
-- bloccato (RLS 42501). Girando come owner il trigger bypassa le policy; il controllo
-- d'accesso resta sull'UPDATE di richiesta_collegamento (policy rc_specialista_update).
CREATE OR REPLACE FUNCTION crea_fascicolo_da_richiesta()
RETURNS trigger LANGUAGE plpgsql
SECURITY DEFINER                 -- gira come owner: bypassa le RLS su fascicoloclinico/chat
SET search_path TO public        -- obbligatorio con SECURITY DEFINER (hardening)
AS $$
BEGIN
  IF NEW."Stato" = 'Accettata' AND OLD."Stato" = 'In Attesa' THEN
    INSERT INTO fascicoloclinico ("Stato", "CodFiscalePaziente", "CodFiscaleSpecialista")
    VALUES ('Attivo', NEW."CodFiscalePaziente", NEW."CodFiscaleSpecialista")
    ON CONFLICT ("CodFiscalePaziente") DO UPDATE
      SET "Stato" = 'Attivo',
          "CodFiscaleSpecialista" = EXCLUDED."CodFiscaleSpecialista",  -- consente cambio specialista
          "updated_at" = now();
    INSERT INTO chat ("CodFiscalePaziente", "CodFiscaleSpecialista")
    VALUES (NEW."CodFiscalePaziente", NEW."CodFiscaleSpecialista")
    ON CONFLICT ("CodFiscalePaziente", "CodFiscaleSpecialista") DO NOTHING;
  END IF;
  RETURN NEW;
END;
$$;
CREATE TRIGGER trg_richiesta_accettata
  AFTER UPDATE ON richiesta_collegamento
  FOR EACH ROW EXECUTE FUNCTION crea_fascicolo_da_richiesta();

-- 6.6 ultimo_messaggio_at
-- SECURITY DEFINER (obbligatorio, non solo hardening): il trigger scatta su INSERT di
-- messaggio e fa UPDATE su chat, ma chat NON ha policy UPDATE (solo chat_select/chat_insert).
-- Sotto INVOKER l'UPDATE sarebbe filtrato dalle RLS a 0 righe (silenzioso, nessun errore)
-- e ultimo_messaggio_at non si aggiornerebbe mai → la chat list ordinata per quel campo
-- resterebbe ferma. Come owner il trigger bypassa le RLS; il controllo d'accesso resta
-- sull'INSERT di messaggio (policy messaggio_insert). Pattern standard Supabase (ADR-0023).
CREATE OR REPLACE FUNCTION aggiorna_ultimo_messaggio()
RETURNS trigger LANGUAGE plpgsql
SECURITY DEFINER                 -- gira come owner: bypassa le RLS su chat
SET search_path TO public        -- obbligatorio con SECURITY DEFINER (hardening)
AS $$
BEGIN
  UPDATE chat SET "ultimo_messaggio_at" = now()
  WHERE "IdChat" = NEW."IdChat";
  RETURN NEW;
END;
$$;
CREATE TRIGGER trg_ultimo_messaggio
  AFTER INSERT ON messaggio
  FOR EACH ROW EXECUTE FUNCTION aggiorna_ultimo_messaggio();

-- 6.7 dashboard_specialista
CREATE VIEW dashboard_specialista
  WITH (security_invoker = true) AS
SELECT
  f."CodFiscaleSpecialista",
  p."CodiceFiscale",
  p."Nome",
  p."Cognome",
  f."Stato" AS stato_fascicolo,
  m_last."Peso"    AS ultimo_peso,
  m_last."Altezza" AS ultima_altezza,
  CASE WHEN m_last."Altezza" > 0
    THEN ROUND(m_last."Peso" / (m_last."Altezza" * m_last."Altezza"), 2)
  END AS bmi,
  (SELECT ROUND(100.0 * COUNT(*) FILTER (WHERE d."StatoCompilazione" = 'COMPLETO') / NULLIF(COUNT(*),0), 1)
   FROM diariogiornaliero d
   WHERE d."IdFascicolo" = f."IdFascicolo"
     AND d."Data" >= CURRENT_DATE - 7) AS pct_diario_7gg,
  (SELECT COUNT(*) FROM sintomo s
   WHERE s."IdFascicolo" = f."IdFascicolo"
     AND s."Data" >= CURRENT_DATE - 7) AS sintomi_7gg,
  pc."Obiettivi" AS obiettivo_piano_attivo,
  pc."Stato"     AS stato_piano_attivo,
  v_last."Data"  AS ultima_visita
FROM fascicoloclinico f
JOIN paziente p ON p."CodiceFiscale" = f."CodFiscalePaziente"
LEFT JOIN LATERAL (
  SELECT "Peso", "Altezza" FROM misurazione
  WHERE "IdFascicolo" = f."IdFascicolo"
  ORDER BY "Data" DESC LIMIT 1
) m_last ON true
LEFT JOIN pianoclinico pc ON pc."IdFascicolo" = f."IdFascicolo" AND pc."Stato" = 'Attivo'
LEFT JOIN LATERAL (
  SELECT "Data" FROM visita
  WHERE "CodFiscalePaziente" = f."CodFiscalePaziente"
    AND "Stato" = 'Completata'
  ORDER BY "Data" DESC, "Ora" DESC LIMIT 1
) v_last ON true;
COMMENT ON VIEW dashboard_specialista IS
  'Statistiche sintetiche per il dashboard dello specialista (una riga per paziente)';

-- 6.8 diario_con_nutrienti
CREATE VIEW diario_con_nutrienti
  WITH (security_invoker = true) AS
SELECT
  dg."IdFascicolo",
  dg."Data",
  dg."StatoCompilazione",
  dg."NoteSpecialista",
  p."IdPasto",
  p."Ora"          AS ora_pasto,
  p."Tipologia"    AS tipo_pasto,
  a."Nome"         AS alimento_nome,
  a."Categoria",
  ap."QuantitaGrammi",
  ap."UnitaMisuraOrig",
  ap."LattosioCalc",
  ap."SorbitoloCalc",
  ap."GlutineCalc",
  ap."CalorieCalc",
  SUM(ap."LattosioCalc")  OVER (PARTITION BY p."IdPasto") AS tot_lattosio_pasto,
  SUM(ap."SorbitoloCalc") OVER (PARTITION BY p."IdPasto") AS tot_sorbitolo_pasto,
  SUM(ap."GlutineCalc")   OVER (PARTITION BY p."IdPasto") AS tot_glutine_pasto,
  SUM(ap."CalorieCalc")   OVER (PARTITION BY p."IdPasto") AS tot_calorie_pasto,
  SUM(ap."LattosioCalc")  OVER (PARTITION BY dg."IdFascicolo", dg."Data") AS tot_lattosio_giorno,
  SUM(ap."SorbitoloCalc") OVER (PARTITION BY dg."IdFascicolo", dg."Data") AS tot_sorbitolo_giorno,
  SUM(ap."GlutineCalc")   OVER (PARTITION BY dg."IdFascicolo", dg."Data") AS tot_glutine_giorno,
  SUM(ap."CalorieCalc")   OVER (PARTITION BY dg."IdFascicolo", dg."Data") AS tot_calorie_giorno
FROM diariogiornaliero dg
JOIN pasto p           ON p."IdFascicolo" = dg."IdFascicolo" AND p."Data" = dg."Data"
JOIN alimento_pasto ap ON ap."IdPasto" = p."IdPasto"
JOIN alimento a        ON a."IdAlimento" = ap."IdAlimento";
COMMENT ON VIEW diario_con_nutrienti IS
  'Vista denormalizzata del diario con dettaglio nutrienti e totali (pasto e giornata)';

-- ============================================================
-- 6.9 REGISTRAZIONE ATOMICA via Auth Hook su auth.users
-- ============================================================
-- Trigger AFTER INSERT su auth.users che, nella STESSA transazione
-- del signUp di Supabase Auth, popola profilo_utente +
-- paziente|specialista leggendo i dati da raw_user_meta_data.
-- Se una qualsiasi insert fallisce, l'eccezione propaga e Supabase
-- rolla indietro anche l'utente in auth.users: non resta nessun
-- account "orfano" in limbo.
--
-- Il client (Kotlin) deve passare TUTTI i dati come metadata:
--   supabase.auth.signUpWith(Email) {
--       this.email = email
--       this.password = password
--       data = buildJsonObject {
--           put("ruolo", "paziente")            // o "specialista"
--           put("codice_fiscale", taxCode)
--           put("nome", firstName)
--           put("cognome", lastName)
--           // SOLO se ruolo = paziente
--           put("sesso", "M")                   // M | F | Altro
--           put("data_nascita", birthDate.toString())  // yyyy-MM-dd
--           // SOLO se ruolo = specialista
--           put("partita_iva", vatNumber)
--           put("specializzazione", "Nutrizionista")   // opzionale
--           put("citta", "Milano")                      // opzionale
--       }
--   }
--
-- IMPORTANTE — Compatibilità retro:
-- se raw_user_meta_data non contiene "ruolo" (es. signUp da admin
-- console, password recovery, magic link) la funzione esce subito
-- senza fare nulla. Anche il vecchio flusso applicativo che inseriva
-- manualmente in profilo_utente + paziente continua quindi a
-- funzionare finché non viene aggiornato.

CREATE OR REPLACE FUNCTION crea_profilo_da_auth()
RETURNS trigger
LANGUAGE plpgsql SECURITY DEFINER
SET search_path TO public
AS $$
DECLARE
  v_meta         jsonb := NEW.raw_user_meta_data;
  v_ruolo        text  := v_meta->>'ruolo';
  v_cf           text  := v_meta->>'codice_fiscale';
  v_nome         text  := v_meta->>'nome';
  v_cognome      text  := v_meta->>'cognome';
  v_sesso        text  := v_meta->>'sesso';
  v_data_nascita text  := v_meta->>'data_nascita';
  v_partita_iva  text  := v_meta->>'partita_iva';
  v_specializ    text  := v_meta->>'specializzazione';
  v_citta        text  := v_meta->>'citta';
BEGIN
  -- Nessun metadata "ruolo" → non gestiamo (vedi nota retro-compat sopra).
  IF v_ruolo IS NULL THEN
    RETURN NEW;
  END IF;

  IF v_cf IS NULL OR length(v_cf) <> 16 THEN
    RAISE EXCEPTION 'Codice fiscale mancante o non valido (richiesti 16 caratteri)';
  END IF;
  IF v_nome IS NULL OR v_cognome IS NULL THEN
    RAISE EXCEPTION 'Nome e cognome sono obbligatori per la registrazione';
  END IF;

  INSERT INTO profilo_utente (auth_uid, ruolo, codice_fiscale)
  VALUES (NEW.id, v_ruolo::ruolo_utente, v_cf);

  IF v_ruolo = 'paziente' THEN
    IF v_sesso IS NULL OR v_data_nascita IS NULL THEN
      RAISE EXCEPTION 'Sesso e data_nascita sono obbligatori per il paziente';
    END IF;
    INSERT INTO paziente (
      "CodiceFiscale", "auth_uid", "Nome", "Cognome", "Email",
      "Sesso", "DataNascita"
    ) VALUES (
      v_cf, NEW.id, v_nome, v_cognome, NEW.email,
      v_sesso::sesso_paziente, v_data_nascita::date
    );

  ELSIF v_ruolo = 'specialista' THEN
    IF v_partita_iva IS NULL THEN
      RAISE EXCEPTION 'Partita IVA obbligatoria per lo specialista';
    END IF;
    INSERT INTO specialista (
      "CodiceFiscale", "auth_uid", "Nome", "Cognome", "Email",
      "PartitaIVA", "Specializzazione", "Citta"
    ) VALUES (
      v_cf, NEW.id, v_nome, v_cognome, NEW.email,
      v_partita_iva,
      NULLIF(v_specializ, '')::tipo_specializ_enum,
      NULLIF(v_citta, '')
    );

  ELSE
    RAISE EXCEPTION 'Ruolo non supportato per la registrazione: %', v_ruolo;
  END IF;

  RETURN NEW;
END;
$$;

COMMENT ON FUNCTION crea_profilo_da_auth() IS
  'Auth Hook AFTER INSERT su auth.users: crea profilo_utente + paziente|specialista atomicamente. Vedi sezione 6.9.';

-- DROP idempotente per permettere il re-run dello script.
DROP TRIGGER IF EXISTS trg_crea_profilo_da_auth ON auth.users;
CREATE TRIGGER trg_crea_profilo_da_auth
  AFTER INSERT ON auth.users
  FOR EACH ROW EXECUTE FUNCTION crea_profilo_da_auth();

-- 6.10 eliminazione account dell'utente corrente (RF7)
-- Eseguita lato server come RPC SECURITY DEFINER. Motivi per cui NON si può fare
-- dal client:
--   * paziente/specialista NON hanno policy DELETE (RLS) → un DELETE dal client
--     verrebbe filtrato a 0 righe, silenziosamente;
--   * va rimossa anche la riga in auth.users, impossibile dal client senza la
--     service_role key.
-- Senza questa funzione la "cancellazione" eliminava solo profilo_utente, lasciando
-- l'utente in limbo (auth.users presente + paziente/fascicolo orfani: login KO con
-- "Profilo non trovato", re-signup KO per email già esistente). Girando come owner,
-- la funzione bypassa le RLS e può cancellare da auth.users.
--
-- Ordine delle cancellazioni (le FK ON DELETE CASCADE fanno il resto):
--   paziente: prima il fascicolo (→ diari/pasti/sintomi via cascade), poi la riga
--             paziente (→ chat/messaggi, richieste, notifiche via cascade), poi
--             profilo_utente e infine auth.users.
--   specialista: BLOCCATO se ha ancora un fascicolo — non si cancellano i dati clinici
--             dei suoi pazienti (la FK fascicolo→specialista è ON UPDATE CASCADE, niente
--             ON DELETE: un cascade qui sarebbe anche pericoloso). Senza fascicoli:
--             specialista (→ chat, richieste via cascade) poi profilo_utente e auth.users.
-- Ritorna un esito testuale che il client mappa su DomainError:
--   'deleted' | 'has_linked_patients' | 'not_authenticated'.
CREATE OR REPLACE FUNCTION delete_own_account()
RETURNS text
LANGUAGE plpgsql
SECURITY DEFINER                 -- gira come owner: bypassa le RLS e accede ad auth.users
SET search_path TO public        -- obbligatorio con SECURITY DEFINER (hardening)
AS $$
DECLARE
  v_uid   uuid := auth.uid();
  v_cf    char(16);
  v_ruolo ruolo_utente;
BEGIN
  IF v_uid IS NULL THEN
    RETURN 'not_authenticated';
  END IF;

  SELECT "codice_fiscale", "ruolo"
    INTO v_cf, v_ruolo
    FROM profilo_utente
   WHERE "auth_uid" = v_uid;

  -- Profilo già assente (es. account in limbo da una cancellazione parziale
  -- precedente): rimuovi comunque l'auth user per non lasciare residui.
  IF v_cf IS NULL THEN
    DELETE FROM auth.users WHERE id = v_uid;
    RETURN 'deleted';
  END IF;

  IF v_ruolo = 'specialista' THEN
    IF EXISTS (SELECT 1 FROM fascicoloclinico WHERE "CodFiscaleSpecialista" = v_cf) THEN
      RETURN 'has_linked_patients';
    END IF;
    DELETE FROM specialista WHERE "CodiceFiscale" = v_cf;   -- cascade: chat, richieste
  ELSIF v_ruolo = 'paziente' THEN
    DELETE FROM fascicoloclinico WHERE "CodFiscalePaziente" = v_cf;  -- cascade: diari/pasti/sintomi
    DELETE FROM paziente         WHERE "CodiceFiscale"      = v_cf;  -- cascade: chat, richieste, notifiche
  END IF;

  DELETE FROM profilo_utente WHERE "auth_uid" = v_uid;
  DELETE FROM auth.users     WHERE id = v_uid;
  RETURN 'deleted';
END;
$$;

-- Esposta come RPC solo agli utenti autenticati (usa auth.uid()).
REVOKE ALL ON FUNCTION delete_own_account() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION delete_own_account() TO authenticated;

COMMENT ON FUNCTION delete_own_account() IS
  'RF7: elimina l''account dell''utente corrente (SECURITY DEFINER). Ritorna deleted|has_linked_patients|not_authenticated.';

-- 6.11 approvazione/sospensione specialista da parte di un admin (ADR-0028)
-- SECURITY DEFINER: l'UPDATE su specialista."Verificato" gira come owner (bypassa le RLS,
-- visto che non esiste una policy UPDATE per la segretaria su specialista). Il guard
-- trg_blocca_self_verifica consente comunque l'operazione perché, dentro la funzione,
-- auth.uid() resta quello del chiamante → get_ruolo()='segretaria'. Solo una segretaria ha
-- effetto; chiunque altro ottiene 'not_authorized' senza alcun UPDATE.
-- Ritorna: 'verified' | 'unverified' | 'not_found' | 'not_authorized'.
-- NOTA MVP: finché non esiste un account segretaria + UI di approvazione (vedi backlog),
-- l'approvazione si fa dal Dashboard (UPDATE diretto consentito: auth.uid() IS NULL).
CREATE OR REPLACE FUNCTION verifica_specialista(p_cf char(16), p_verificato boolean DEFAULT true)
RETURNS text
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO public
AS $$
BEGIN
  IF get_ruolo() IS DISTINCT FROM 'segretaria' THEN
    RETURN 'not_authorized';
  END IF;
  UPDATE specialista SET "Verificato" = p_verificato WHERE "CodiceFiscale" = p_cf;
  IF NOT FOUND THEN
    RETURN 'not_found';
  END IF;
  RETURN CASE WHEN p_verificato THEN 'verified' ELSE 'unverified' END;
END;
$$;
REVOKE ALL ON FUNCTION verifica_specialista(char(16), boolean) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION verifica_specialista(char(16), boolean) TO authenticated;
COMMENT ON FUNCTION verifica_specialista(char(16), boolean) IS
  'ADR-0028: approvazione/sospensione specialista da parte di una segretaria (SECURITY DEFINER). Ritorna verified|unverified|not_found|not_authorized.';

-- ============================================================
-- SEZIONE 7 — REALTIME (publication supabase_realtime)
-- ============================================================
-- La chat (RF24) riceve i nuovi messaggi via Supabase Realtime, che
-- trasmette i cambiamenti delle sole tabelle incluse nella publication
-- `supabase_realtime`. Va quindi aggiunta `messaggio` a tale publication.
--
-- Note:
--   * Il client ascolta SOLO gli INSERT (vedi
--     ChatRepositoryImpl.observeNewMessages): la REPLICA IDENTITY di default
--     è sufficiente, perché il record nuovo viaggia completo. Servirebbe
--     REPLICA IDENTITY FULL solo per avere i valori "vecchi" su UPDATE/DELETE.
--   * Le RLS su `messaggio` (policy messaggio_select) restano valide anche
--     sul canale Realtime: ogni utente riceve solo i messaggi delle proprie
--     chat. Nessuna policy aggiuntiva necessaria.
--   * Equivale al toggle Dashboard → Database → Publications →
--     supabase_realtime → tabella `messaggio`.
--
-- Idempotente e difensivo: agisce solo se la publication esiste (su Supabase
-- è creata dalla piattaforma) e solo se la tabella non ne è già membro. Su un
-- Postgres standalone privo di `supabase_realtime` non fa nulla.
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM pg_publication WHERE pubname = 'supabase_realtime') THEN
    IF NOT EXISTS (
      SELECT 1 FROM pg_publication_tables
      WHERE pubname = 'supabase_realtime'
        AND schemaname = 'public'
        AND tablename = 'messaggio'
    ) THEN
      ALTER PUBLICATION supabase_realtime ADD TABLE messaggio;
    END IF;
  END IF;
END $$;
