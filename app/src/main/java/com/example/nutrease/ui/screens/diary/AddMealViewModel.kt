package com.example.nutrease.ui.screens.diary

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nutrease.domain.model.Food
import com.example.nutrease.domain.model.Meal
import com.example.nutrease.domain.model.MealFoodItem
import com.example.nutrease.domain.model.MealType
import com.example.nutrease.domain.model.NutrientTotals
import com.example.nutrease.domain.usecase.AddMealUseCase
import com.example.nutrease.domain.usecase.GetMealUseCase
import com.example.nutrease.domain.usecase.GetPatientFascicoloUseCase
import com.example.nutrease.domain.usecase.SearchFoodsUseCase
import com.example.nutrease.domain.usecase.UpdateMealUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import javax.inject.Inject
import kotlin.time.Clock

/**
 * Riga alimento "in bozza" nel form: tiene unità e quantità come digitate dall'utente
 * e calcola i grammi in tempo reale con [Food.gramsFor]. Diventa [MealFoodItem] solo al salvataggio.
 */
data class PendingMealItem(
    val food: Food,
    val originalUnit: String,
    val originalQuantity: Double
) {
    val quantityGrams: Double get() = food.gramsFor(originalUnit, originalQuantity)
}

data class AddMealUiState(
    val isEditing: Boolean = false,
    val isLoadingExisting: Boolean = false,
    val date: LocalDate = today(),
    val time: LocalTime = nowTime(),
    // Nessun tipo pre-selezionato all'apertura: l'utente deve sceglierne uno (vedi save()).
    val type: MealType? = null,
    val description: String = "",
    val searchQuery: String = "",
    val searchResults: List<Food> = emptyList(),
    val isSearching: Boolean = false,
    val items: List<PendingMealItem> = emptyList(),
    val isSaving: Boolean = false,
    val error: String? = null,
    val saved: Boolean = false
)

const val ARG_MEAL_ID = "meal_id"

/**
 * ViewModel del form pasto (RF8/RF9/RF12), doppia modalità insert/edit come
 * [AddSymptomViewModel] (`meal_id` via [SavedStateHandle]). In più gestisce la ricerca
 * alimenti reattiva: la query passa in un Flow con `debounce` (niente ricerca a ogni
 * tasto) e `distinctUntilChanged` prima di interrogare lo UseCase.
 */
@OptIn(FlowPreview::class)
@HiltViewModel
class AddMealViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getPatientFascicoloUseCase: GetPatientFascicoloUseCase,
    private val getMealUseCase: GetMealUseCase,
    private val searchFoodsUseCase: SearchFoodsUseCase,
    private val addMealUseCase: AddMealUseCase,
    private val updateMealUseCase: UpdateMealUseCase
) : ViewModel() {

    private val editingMealId: Long? = savedStateHandle.get<Long>(ARG_MEAL_ID)?.takeIf { it > 0L }
    private var fascicoloIdForEdit: Int? = null

    private val _uiState = MutableStateFlow(
        AddMealUiState(
            isEditing = editingMealId != null,
            isLoadingExisting = editingMealId != null
        )
    )
    val uiState: StateFlow<AddMealUiState> = _uiState.asStateFlow()

    private val queryFlow = MutableStateFlow("")
    private var searchJob: Job? = null

    init {
        observeQuery()
        editingMealId?.let { loadExistingMeal(it) }
    }

    fun setInitialDate(date: LocalDate) {
        if (_uiState.value.isEditing) return
        _uiState.update { it.copy(date = date) }
    }

    fun updateDate(date: LocalDate) = _uiState.update { it.copy(date = date) }
    fun updateTime(time: LocalTime) = _uiState.update { it.copy(time = time) }
    fun updateType(type: MealType) = _uiState.update { it.copy(type = type) }
    fun updateDescription(text: String) = _uiState.update { it.copy(description = text) }

    fun updateQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        queryFlow.value = query
    }

    fun addItem(food: Food, unit: String, quantity: Double) {
        if (quantity <= 0.0) return
        _uiState.update {
            it.copy(
                items = it.items + PendingMealItem(food, unit, quantity),
                searchQuery = "",
                searchResults = emptyList()
            )
        }
        queryFlow.value = ""
    }

    fun removeItem(index: Int) {
        _uiState.update {
            val newItems = it.items.toMutableList().also { list -> if (index in list.indices) list.removeAt(index) }
            it.copy(items = newItems)
        }
    }

    fun save() {
        val state = _uiState.value
        // Guard di validazione: nessun tipo selezionato → non si salva. Il Button è già
        // disabilitato in questo caso, ma il controllo qui è la difesa definitiva.
        val type = state.type ?: run {
            _uiState.update { it.copy(error = "Seleziona il tipo di pasto") }
            return
        }
        // Non si registra un pasto in un momento futuro (RF12): data+ora oltre "adesso".
        if (isFutureDateTime(state.date, state.time)) {
            _uiState.update { it.copy(error = "Non puoi registrare un pasto in una data o un'ora futura") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            val fascicoloId = fascicoloIdForEdit ?: getPatientFascicoloUseCase().getOrElse {
                _uiState.update { s -> s.copy(isSaving = false, error = it.message) }
                return@launch
            }
            val meal = Meal(
                id = editingMealId,
                fascicoloId = fascicoloId,
                date = state.date,
                time = state.time,
                type = type,
                description = state.description.ifBlank { null },
                items = state.items.map { it.toDomain() }
            )
            val result = if (editingMealId != null) {
                updateMealUseCase(meal).map { Unit }
            } else {
                addMealUseCase(meal).map { Unit }
            }
            result
                .onSuccess {
                    _uiState.update { s -> s.copy(isSaving = false, saved = true) }
                }
                .onFailure { e ->
                    _uiState.update { s -> s.copy(isSaving = false, error = e.message) }
                }
        }
    }

    fun clearError() = _uiState.update { it.copy(error = null) }

    private fun loadExistingMeal(mealId: Long) {
        viewModelScope.launch {
            getMealUseCase(mealId)
                .onSuccess { meal ->
                    fascicoloIdForEdit = meal.fascicoloId
                    _uiState.update {
                        it.copy(
                            isLoadingExisting = false,
                            date = meal.date,
                            time = meal.time,
                            type = meal.type,
                            description = meal.description.orEmpty(),
                            items = meal.items.map { item -> item.toPending() }
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoadingExisting = false, error = e.message) }
                }
        }
    }

    private fun observeQuery() {
        searchJob = viewModelScope.launch {
            queryFlow
                .debounce(300)
                .distinctUntilChanged()
                .collect { query ->
                    if (query.isBlank()) {
                        _uiState.update { it.copy(searchResults = emptyList(), isSearching = false) }
                        return@collect
                    }
                    _uiState.update { it.copy(isSearching = true) }
                    searchFoodsUseCase(query)
                        .onSuccess { results ->
                            _uiState.update { it.copy(searchResults = results, isSearching = false) }
                        }
                        .onFailure { e ->
                            _uiState.update { it.copy(searchResults = emptyList(), isSearching = false, error = e.message) }
                        }
                }
        }
    }
}

private fun PendingMealItem.toDomain(): MealFoodItem = MealFoodItem(
    id = null,
    foodId = food.id,
    foodName = food.name,
    quantityGrams = quantityGrams,
    originalUnit = originalUnit,
    originalQuantity = originalQuantity,
    nutrients = NutrientTotals.Zero
)

private fun MealFoodItem.toPending(): PendingMealItem = PendingMealItem(
    food = Food(
        id = foodId,
        name = foodName,
        category = null,
        lactosePer100g = 0.0,
        sorbitolPer100g = 0.0,
        glutenPer100g = 0.0,
        caloriesPer100g = 0.0,
        unitConversions = if (originalUnit == Food.UNIT_GRAMS || originalQuantity <= 0.0) emptyMap()
        else mapOf(originalUnit to quantityGrams / originalQuantity)
    ),
    originalUnit = originalUnit,
    originalQuantity = originalQuantity
)

/** True se la coppia data+ora scelta cade oltre l'istante attuale (fuso locale). */
private fun isFutureDateTime(date: LocalDate, time: LocalTime): Boolean {
    val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    return LocalDateTime(date, time) > now
}

private fun today(): LocalDate =
    Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

private fun nowTime(): LocalTime {
    val dt = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    return LocalTime(dt.hour, dt.minute)
}