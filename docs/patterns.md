# Pattern ricorrenti — Nutrease Mobile

> Catalogo dei pattern Kotlin/Compose del progetto con esempi minimi.
> **Quando leggerlo**: prima di scrivere un nuovo ViewModel, Repository o Composable.
> Aggiornare quando si introduce/abbandona un pattern.

---

## 1. StateFlow + Coroutines

Dove: tutti i ViewModel. Niente LiveData: il pattern Compose è `StateFlow` con `viewModelScope.launch`.

Flusso: il Composable raccoglie con `collectAsStateWithLifecycle` il `MutableStateFlow` del ViewModel, che viene aggiornato dentro `viewModelScope.launch`; la coroutine chiama lo UseCase (suspend), che chiama il RepositoryImpl (suspend, con `withContext(IO)`), che chiama supabase-kt. La coroutine si sospende in attesa della rete e il main thread resta libero per Compose.

```kotlin
// ViewModel
private val _uiState = MutableStateFlow(LoginUiState())
val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

fun login(email: String, password: String) = viewModelScope.launch {
    _uiState.update { it.copy(isLoading = true) }
    loginUseCase(email, password)
        .onSuccess { user -> _uiState.update { it.copy(isLoading = false, navigateTo = user.role) } }
        .onFailure { e    -> _uiState.update { it.copy(isLoading = false, error = e.message) } }
}
// Composable
val uiState by viewModel.uiState.collectAsStateWithLifecycle()
```

Perché non LiveData: StateFlow è nativamente adatto a Compose, richiede un valore iniziale (così lo stato è sempre valido), è Kotlin puro (niente AndroidX) e ha tutti gli operatori Flow.

## 2. UiState come data class immutabile

Dove: ogni schermata ha il proprio `XxxUiState` con tutti i campi per il render (loading, errore, dati, eventi di navigazione).

```kotlin
data class LoginUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val navigateTo: UserRole? = null   // null = nessuna navigazione pendente
)
```
Le modifiche passano sempre per `_uiState.update { it.copy(...) }`, mai per assegnazione diretta: così gli aggiornamenti sono atomici e non si creano stati incoerenti.

## 3. Composable stateless + state hoisting

Dove: ogni schermata è divisa in una parte stateful (prende il ViewModel, raccoglie lo StateFlow, delega) e una stateless (riceve `UiState` e callback, senza logica, e ha la `@Preview`).

```kotlin
@Composable
fun LoginScreen(viewModel: AuthViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LoginContent(uiState = uiState, onLogin = viewModel::login)
}
@Composable
private fun LoginContent(uiState: LoginUiState, onLogin: (String, String) -> Unit) { /* UI */ }
```

## 4. LaunchedEffect per side effect one-shot

Dove: navigazione dopo login o registrazione, snackbar. Esegue un blocco sospendibile una volta sola quando la chiave cambia.

```kotlin
LaunchedEffect(uiState.navigateTo) {
    when (uiState.navigateTo) {
        UserRole.PATIENT -> onNavigateToPatientHome()
        UserRole.SPECIALIST, UserRole.SECRETARY -> onNavigateToSpecialistHome()
        null -> Unit
    }
}
```
Regola: dopo aver consumato l'evento, il ViewModel rimette il campo a `null`, così l'effetto non si ripete a ogni recomposition.

## 5. `withContext(Dispatchers.IO)` nei Repository

Dove: tutti i metodi Repository. Sposta la serializzazione JSON e la costruzione degli oggetti su un thread IO (anche se Ktor è già asincrono).

```kotlin
override suspend fun login(email: String, password: String): Result<AuthUser> =
    withContext(Dispatchers.IO) {
        runCatching { supabase.auth.signInWith(Email) { /* … */ } /* + sessione/profilo */ }
    }
```
In `UserRepositoryImpl` è centralizzato in un helper `ioResult { ... }`. Regola: il dispatcher sta nel layer `data`, mai nel ViewModel o nello UseCase.

## 6. `runCatching` + `Result<T>` per error handling

Dove: metodi Repository che ritornano `Result<T>`.

```kotlin
runCatching {
    supabase.from("profilo_utente").select { /* filtri */ }.decodeSingle<UserProfileDto>()
} // restituisce Result.success(value) oppure Result.failure(exception)
```
Il ViewModel consuma con `.onSuccess { }.onFailure { }`. Per lo stato continuo (caricamento progressivo) si usa `Flow<Resource<T>>` (ADR-0012).

## 7. Dependency Injection con Hilt

Dove: `di/AppModule.kt`, `@HiltViewModel`, `@Inject constructor`.

Grafo: `AppModule` fornisce il `SupabaseClient` come singleton, che arriva alle implementazioni dei Repository; i ViewModel ricevono gli UseCase via `@Inject`, e gli UseCase ricevono le interfacce Repository via `@Inject`. I ViewModel sono `@HiltViewModel` e si ottengono con `hiltViewModel()`. Regola: non istanziare mai Repository o UseCase fuori dai moduli Hilt.