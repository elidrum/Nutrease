# Setup — Nutrease Mobile

> Procedura per portare un clone fresco a una build che parla con Supabase
> (circa 30 minuti con Android Studio e JDK 17 già installati). Se trovi un errore non
> documentato, aggiungilo alla sezione Troubleshooting nella stessa PR della fix.

---

## 1. Prerequisiti

| Tool | Versione | Verifica |
|---|---|---|
| JDK | 17 (Temurin) | `java -version` |
| Android Studio | Iguana 2023.2.1+ | Help → About |
| Android SDK | API 34 | SDK Manager |
| Android Build Tools | 34.0.0+ | SDK Manager → SDK Tools |
| Git | recente | `git --version` |

Opzionali: gh CLI, jq.

## 2. Clone + prima apertura

```bash
git clone <url-repo> nutrease-mobile && cd nutrease-mobile
```
Apri in Android Studio (File, Open) e scegli Trust Project. Il primo sync Gradle fallirà perché i secret non sono ancora configurati: è normale.

## 3. Credenziali Supabase

Dove: su [app.supabase.com](https://app.supabase.com), progetto Nutrease, sezione Project Settings, API. Copia il Project URL (`https://xxx.supabase.co`) e la chiave anon public (oppure `sb_publishable_...`): è la chiave pubblica, sicura da tenere in app.

Non usare mai `service_role` o `sb_secret_...`: sono chiavi admin che bypassano le RLS, solo per il backend.

Crea `local.properties` (gitignored) con `cp local.properties.template local.properties`, poi:
```properties
sdk.dir=/Users/<tu>/Library/Android/sdk
SUPABASE_URL=https://xxxxxxxxxxxxx.supabase.co
SUPABASE_ANON_KEY=eyJhbGci...    # oppure sb_publishable_...
```

BuildConfig in `app/build.gradle.kts` (le proprietà si caricano in cima al file con `java.util.Properties`):
```kotlin
// in cima al file: import java.util.Properties
val localProperties = java.util.Properties()
rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { localProperties.load(it) }

android {
    namespace = "com.example.nutrease"
    compileSdk { version = release(36) { minorApiLevel = 1 } }   // syntax AGP 9
    defaultConfig {
        applicationId = "com.example.nutrease"
        minSdk = 26; targetSdk = 36; versionCode = 1; versionName = "1.0"
        buildConfigField("String", "SUPABASE_URL",      "\"${localProperties.getProperty("SUPABASE_URL", "").trim()}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${localProperties.getProperty("SUPABASE_ANON_KEY", "").trim()}\"")
    }
    buildFeatures { compose = true; buildConfig = true }
}
```

## 4. Dipendenze Gradle

Versioni in `gradle/libs.versions.toml` (AGP 9.2.1, Kotlin 2.3.21, KSP 2.3.2). Librerie configurate:
- Compose BOM 2026.05.01 con Material3, material-icons-extended, Activity e Navigation 2.9.8
- Lifecycle 2.10.0 (viewmodel-compose, runtime-compose, runtime-ktx)
- Hilt 2.59.2 (KSP, non KAPT, ADR-0004) con hilt-navigation-compose
- Supabase-kt BOM 3.5.0 (Auth, Postgrest, Realtime) e ktor-client-okhttp 3.4.3
- kotlinx-datetime 0.7.1 e coroutines 1.11.0
- Test: JUnit, MockK, Turbine, coroutines-test; androidTest: androidx.junit ed espresso

> Predisposto ma non ancora in dipendenze: Room (cache offline futura, ADR-0007 ne vieta
> già l'uso in `domain/`). Va aggiunto solo quando si implementa.

## 5. Hilt — `AppModule`

Modulo unico `di/AppModule.kt` con i singleton fondamentali:
```kotlin
@Module @InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton
    fun provideSupabaseClient(): SupabaseClient = createSupabaseClient(
        supabaseUrl = BuildConfig.SUPABASE_URL, supabaseKey = BuildConfig.SUPABASE_ANON_KEY,
    ) { install(Auth); install(Postgrest) }

    @Provides @Singleton fun provideAuthRepository(s: SupabaseClient): AuthRepository = AuthRepositoryImpl(s)
    @Provides @Singleton fun provideUserRepository(s: SupabaseClient): UserRepository = UserRepositoryImpl(s)
}
```
```kotlin
@HiltAndroidApp class NutreaseApplication : Application()
@AndroidEntryPoint class MainActivity : ComponentActivity()
```
`AndroidManifest.xml`: `<application android:name=".NutreaseApplication" />`.

## 6. Smoke test al primo launch

Screen "Debug" temporaneo che valida la connessione:
```kotlin
supabase.postgrest["alimento"].select { limit(1) }.decodeList<AlimentoDto>()
```
Mostra l'esito (riuscito o fallito). Si elimina dopo lo Sprint 0.

## 7. Troubleshooting

| Sintomo | Causa | Fix |
|---|---|---|
| `BuildConfig.SUPABASE_URL unresolved` | `buildConfig=true` mancante | aggiungilo in `buildFeatures` |
| `SerializationException: Polymorphic serializer not found` | manca `@Serializable` o plugin | verifica `alias(libs.plugins.kotlin.serialization)` |
| `HttpRequestTimeoutException` | rete o URL sbagliato | verifica `SUPABASE_URL` |
| `permission denied for table xxx` | RLS nega | sei loggato? policy corrisponde al ruolo? |
| `duplicate key on profilo_utente` | già registrato con quell'email | fai login, non signup |
| `row-level security policy violation on insert` | insert da client anon pre-login | chiama `signInWith(...)` prima dell'INSERT |
| Hilt `Missing binding` | impl senza `@Inject constructor`/`@Binds` | aggiungi `@Inject constructor()` o `@Provides` |
| Compose Preview vuota | ViewModel istanziato nel Preview | passa dati finti, non creare VM reali |

Warning noto `android.disallowKotlinSourceSets=false`: è atteso, non rimuovere la riga da `gradle.properties`. Il flag permette a KSP di vedere i `kotlin.sourceSets` con AGP 9.x e tiene in piedi i processor Hilt; se lo togli, `:app:kspDebugKotlin` fallisce. Va lasciato finché non si fa un downgrade di AGP o non c'è un'alternativa stabile.

## 8. Pre-flight checklist prima di Sprint 1

- [ ] `./gradlew assembleDebug` passa
- [ ] L'app si installa su emulatore Pixel 4 API 34
- [ ] Lo smoke test mostra "Supabase OK"
- [ ] `git status` non traccia `local.properties`
- [ ] Tutti e 3 i dev hanno buildato con successo

Quando sono tutte spuntate si può partire con lo Sprint 1.

## 9. Build di release firmata (APK)

Per produrre un APK installabile e distribuibile (test su device fisico, demo tesi).

a) Genera un keystore (una volta sola; conservalo con un backup e riusalo per ogni
aggiornamento, vedi nota sotto). Dalla root del progetto:
```bash
keytool -genkeypair -v \
  -keystore nutrease-release.keystore -alias nutrease \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -dname "CN=Nutrease, OU=Tesi, O=UniPM, L=Ancona, ST=Marche, C=IT"
# (ti chiede le password dello store e della chiave)
```
Il file `*.keystore`/`*.jks` è gitignored: non finisce mai nel repo.

b) Configura le credenziali in `local.properties` (gitignored), con path relativo alla root:
```properties
RELEASE_STORE_FILE=nutrease-release.keystore
RELEASE_STORE_PASSWORD=<password-store>
RELEASE_KEY_ALIAS=nutrease
RELEASE_KEY_PASSWORD=<password-chiave>
```
Il `signingConfig` in `app/build.gradle.kts` le legge da qui; se mancano, la release
resta non firmata (build comunque verde).

c) Builda:
```bash
./gradlew assembleRelease
```
Output: `app/build/outputs/apk/release/app-release.apk` (firmato). Minify resta spento,
perché R8 romperebbe la serializzazione kotlinx/supabase senza keep-rules dedicate, e per
la tesi non serve.

d) Aggiornamenti: per ogni nuovo APK incrementa `versionCode` in `app/build.gradle.kts`
(`versionName` è solo l'etichetta). Firma sempre con lo stesso keystore, così l'APK si
installa sopra il precedente senza disinstallare. Se perdi il keystore puoi ancora
buildare, ma con una firma diversa, e i tester dovranno disinstallare e reinstallare
(Nutrease è online-only su Supabase, quindi non si perdono dati locali).

## 10. Approvazione specialisti (ADR-0028)

Uno specialista appena registrato parte non verificato (`specialista."Verificato" = false`):
non è visibile ai pazienti nella discovery e non può accettare richieste di collegamento
finché un admin non lo approva. Finché non esiste una UI admin (vedi `docs/backlog.md`),
l'approvazione si fa dall'SQL editor di Supabase.

Per approvare un nuovo specialista (sostituisci il codice fiscale):
```sql
UPDATE specialista SET "Verificato" = true WHERE "CodiceFiscale" = 'XXXXXXXXXXXXXXXX';
```

Per vedere chi è in attesa (i `Verificato = false`):
```sql
SELECT "CodiceFiscale","Nome","Cognome","Email","Verificato"
FROM specialista WHERE "Verificato" = false ORDER BY "Cognome";
```

Per sospendere uno specialista (revoca, raro): stessa query con `= false`. Nota: il Dashboard
gira senza JWT (`auth.uid()` è NULL), quindi l'UPDATE diretto è consentito dal guard
`trg_blocca_self_verifica`; uno specialista, invece, non può auto-approvarsi dall'app.

## 11. Reset password — codice OTP via email

Il flusso "Password dimenticata?" usa un codice OTP a 6 cifre, senza magic-link né deep-link:
`sendPasswordReset` invia la mail, poi `ResetPasswordScreen` verifica il codice con
`verifyEmailOtp(OtpType.Email.RECOVERY)` e imposta la nuova password. Attenzione: senza la
configurazione qui sotto la mail contiene il link di default (il Site URL, cioè localhost) e
il flusso non funziona.

Sul Dashboard Supabase, in Authentication, Emails, tab Templates, Reset Password,
sostituisci il corpo usando `{{ .Token }}` (il codice) al posto di `{{ .ConfirmationURL }}` (il link):

```html
<h2>Reimposta la tua password</h2>
<p>Usa questo codice per reimpostare la password del tuo account Nutrease:</p>
<p style="font-size:28px; font-weight:bold; letter-spacing:4px;">{{ .Token }}</p>
<p>Il codice è valido per un'ora. Se non hai richiesto tu il reset, ignora questa email.</p>
```

Note:
- OTP: 6 cifre di default; la scadenza si imposta in Authentication, Emails (campo Email OTP Expiration, default 3600s).
- SMTP: lascia Custom SMTP disabilitato, così usa il mittente integrato di Supabase (rate limit basso,
  va bene per test e demo). Per la produzione o volumi alti configura un SMTP proprio (per esempio Resend o Brevo).
- Il Site URL non serve a questo flusso (il link non si usa) e può restare al default.