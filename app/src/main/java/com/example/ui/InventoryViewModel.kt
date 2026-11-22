package com.example.ui

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.api.AiCallResult
import com.example.api.AiErrorType
import com.example.api.ApiKeyStatus
import com.example.api.BarcodeProductInfo
import com.example.api.BarcodeScannerHelper
import com.example.api.DetectedItem
import com.example.api.GeminiApiClient
import com.example.api.RecipeSuggestion
import com.example.api.SmartKitchenResult
import com.example.data.InventoryItem
import com.example.data.InventoryRepository
import com.example.data.MealPlanItem
import com.example.data.ShoppingItem
import com.example.data.WasteActionLog
import com.example.domain.DetailedEcoFinancialMetrics
import com.example.domain.EcoMetricsCalculator
import com.example.domain.LocalRecipeEngine
import com.example.domain.ReceiptParser
import com.example.domain.UsernameValidator
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume

sealed interface ScanUiState {
    object Idle : ScanUiState
    object Loading : ScanUiState
    data class Success(
        val result: SmartKitchenResult,
        val isOfflineFallback: Boolean = false,
        val modelUsed: String? = null,
        val fallbackReason: String? = null
    ) : ScanUiState

    data class Error(
        val message: String,
        val isApiKeyIssue: Boolean = false
    ) : ScanUiState
}

enum class WarningLevel {
    EXPIRED, CRITICAL, WARNING, INFO
}

data class ExpirationWarning(
    val itemId: Int,
    val itemName: String,
    val category: String,
    val daysLeft: Int,
    val level: WarningLevel,
    val message: String
)

class InventoryViewModel(
    application: Application,
    private val repository: InventoryRepository
) : AndroidViewModel(application) {

    private val sharedPrefs = application.getSharedPreferences("smart_kitchen_cal_prefs", Context.MODE_PRIVATE)
    private val trLocale: Locale = Locale.forLanguageTag("tr")

    // ==========================================
    // 🔑 API KEY & CONNECTION MANAGEMENT
    // ==========================================
    private fun loadSavedCustomApiKey(): String =
        sharedPrefs.getString("custom_gemini_api_key", "")?.trim().orEmpty()

    private val _apiKeyStatus = MutableStateFlow(
        GeminiApiClient.resolveApiKeyStatus(loadSavedCustomApiKey())
    )
    val apiKeyStatus: StateFlow<ApiKeyStatus> = _apiKeyStatus.asStateFlow()

    private val _isTestingApi = MutableStateFlow(false)
    val isTestingApi: StateFlow<Boolean> = _isTestingApi.asStateFlow()

    private val _apiTestResult = MutableStateFlow<Pair<Boolean, String>?>(null)
    val apiTestResult: StateFlow<Pair<Boolean, String>?> = _apiTestResult.asStateFlow()

    private fun getActiveApiKey(): String =
        GeminiApiClient.resolveActiveKey(loadSavedCustomApiKey())

    fun saveCustomApiKey(rawKey: String): Boolean {
        val cleaned = rawKey.trim()
        if (cleaned.isEmpty()) return false
        sharedPrefs.edit().putString("custom_gemini_api_key", cleaned).apply()
        _apiKeyStatus.value = GeminiApiClient.resolveApiKeyStatus(cleaned)
        _apiTestResult.value = null
        return true
    }

    fun clearCustomApiKey() {
        sharedPrefs.edit().remove("custom_gemini_api_key").apply()
        _apiKeyStatus.value = GeminiApiClient.resolveApiKeyStatus("")
        _apiTestResult.value = null
    }

    fun testApiKeyConnection(candidateKey: String? = null) {
        val keyToTest = candidateKey?.trim()?.takeIf { it.isNotEmpty() } ?: getActiveApiKey()
        _isTestingApi.value = true
        _apiTestResult.value = null
        viewModelScope.launch {
            val result = GeminiApiClient.testApiConnection(keyToTest)
            _apiTestResult.value = when (result) {
                is AiCallResult.Success -> true to result.data
                is AiCallResult.Error -> false to result.message
            }
            _isTestingApi.value = false
        }
    }

    fun isApiKeyConfigured(): Boolean = _apiKeyStatus.value.isConfigured

    fun isApiKeyValidPrefix(): Boolean = _apiKeyStatus.value.isValidFormat

    // ==========================================
    // 🔥 CALORIE & NUTRITION TRACKER (AUTO DAILY RESET)
    // ==========================================
    private fun todayDateString(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    private val _dailyCalorieGoal = MutableStateFlow(sharedPrefs.getInt("daily_calorie_goal", 2000))
    val dailyCalorieGoal: StateFlow<Int> = _dailyCalorieGoal.asStateFlow()

    private val _consumedCaloriesToday = MutableStateFlow(sharedPrefs.getInt("consumed_calories_today", 0))
    val consumedCaloriesToday: StateFlow<Int> = _consumedCaloriesToday.asStateFlow()

    private val _consumedProteinToday = MutableStateFlow(sharedPrefs.getInt("consumed_protein_today", 0))
    val consumedProteinToday: StateFlow<Int> = _consumedProteinToday.asStateFlow()

    private val _consumedCarbsToday = MutableStateFlow(sharedPrefs.getInt("consumed_carbs_today", 0))
    val consumedCarbsToday: StateFlow<Int> = _consumedCarbsToday.asStateFlow()

    private val _consumedFatToday = MutableStateFlow(sharedPrefs.getInt("consumed_fat_today", 0))
    val consumedFatToday: StateFlow<Int> = _consumedFatToday.asStateFlow()

    private fun ensureCalorieDayIsCurrent() {
        val today = todayDateString()
        val storedDate = sharedPrefs.getString("calorie_tracking_date", null)
        if (storedDate == null) {
            sharedPrefs.edit().putString("calorie_tracking_date", today).apply()
        } else if (storedDate != today) {
            resetDailyCalories()
            sharedPrefs.edit().putString("calorie_tracking_date", today).apply()
        }
    }

    fun setDailyCalorieGoal(goal: Int) {
        val safeGoal = goal.coerceIn(500, 10000)
        _dailyCalorieGoal.value = safeGoal
        sharedPrefs.edit().putInt("daily_calorie_goal", safeGoal).apply()
    }

    fun resetDailyCalories() {
        _consumedCaloriesToday.value = 0
        _consumedProteinToday.value = 0
        _consumedCarbsToday.value = 0
        _consumedFatToday.value = 0
        sharedPrefs.edit()
            .putString("calorie_tracking_date", todayDateString())
            .putInt("consumed_calories_today", 0)
            .putInt("consumed_protein_today", 0)
            .putInt("consumed_carbs_today", 0)
            .putInt("consumed_fat_today", 0)
            .apply()
    }

    /**
     * Logs manual calories/macros without generating synthetic eco points or CO2 savings.
     */
    fun addManualNutrition(calories: Int, protein: Int, carbs: Int, fat: Int) {
        ensureCalorieDayIsCurrent()
        _consumedCaloriesToday.value += calories.coerceAtLeast(0)
        _consumedProteinToday.value += protein.coerceAtLeast(0)
        _consumedCarbsToday.value += carbs.coerceAtLeast(0)
        _consumedFatToday.value += fat.coerceAtLeast(0)

        sharedPrefs.edit()
            .putString("calorie_tracking_date", todayDateString())
            .putInt("consumed_calories_today", _consumedCaloriesToday.value)
            .putInt("consumed_protein_today", _consumedProteinToday.value)
            .putInt("consumed_carbs_today", _consumedCarbsToday.value)
            .putInt("consumed_fat_today", _consumedFatToday.value)
            .apply()
    }

    fun consumeRecipe(title: String, calories: Int?, protein: String?, carbs: String?, fat: String?) {
        val calVal = calories ?: 300
        val protVal = protein?.filter { it.isDigit() }?.toIntOrNull() ?: 15
        val carbsVal = carbs?.filter { it.isDigit() }?.toIntOrNull() ?: 40
        val fatVal = fat?.filter { it.isDigit() }?.toIntOrNull() ?: 10

        addManualNutrition(calVal, protVal, carbsVal, fatVal)

        viewModelScope.launch(Dispatchers.IO) {
            val log = WasteActionLog(
                itemName = "Yemek Tarifi: $title",
                category = "Yemek",
                actionType = "CONSUMED",
                pointsGained = 60,
                co2SavedKg = 1.5,
                cost = 45.0
            )
            repository.insertActionLog(log)
            addOwnFeedEvent("Tarif: $title", 1.5)
        }
    }

    // ==========================================
    // 🧊 INVENTORY & EXPIRATION WARNINGS
    // ==========================================
    val activeItems: StateFlow<List<InventoryItem>> = repository.activeItems
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val expirationWarnings: StateFlow<List<ExpirationWarning>> = activeItems
        .map { items ->
            val currentTime = System.currentTimeMillis()
            items.mapNotNull { item ->
                val remainingDays = item.getRemainingDays(currentTime)
                when {
                    remainingDays < 0 -> ExpirationWarning(
                        itemId = item.id,
                        itemName = item.name,
                        category = item.category,
                        daysLeft = remainingDays,
                        level = WarningLevel.EXPIRED,
                        message = "Süresi geçmiş! Kontrol edip değerlendirin veya imha edin."
                    )
                    remainingDays == 0 -> ExpirationWarning(
                        itemId = item.id,
                        itemName = item.name,
                        category = item.category,
                        daysLeft = remainingDays,
                        level = WarningLevel.CRITICAL,
                        message = "Bugün son gün! İsrafı önlemek için hemen tüketin."
                    )
                    remainingDays == 1 -> ExpirationWarning(
                        itemId = item.id,
                        itemName = item.name,
                        category = item.category,
                        daysLeft = remainingDays,
                        level = WarningLevel.CRITICAL,
                        message = "Yarın son gün! Öncelikli tüketilmesi önerilir."
                    )
                    remainingDays in 2..3 -> ExpirationWarning(
                        itemId = item.id,
                        itemName = item.name,
                        category = item.category,
                        daysLeft = remainingDays,
                        level = WarningLevel.WARNING,
                        message = "$remainingDays gün kaldı. Tariflerde değerlendirebilirsiniz."
                    )
                    remainingDays in 4..7 -> ExpirationWarning(
                        itemId = item.id,
                        itemName = item.name,
                        category = item.category,
                        daysLeft = remainingDays,
                        level = WarningLevel.INFO,
                        message = "$remainingDays gün kaldı. Planlı tüketim önerilir."
                    )
                    else -> null
                }
            }.sortedBy { it.daysLeft }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _readWarningItemIds = MutableStateFlow<Set<Int>>(
        sharedPrefs.getStringSet("read_warning_item_ids", emptySet())
            ?.mapNotNull { it.toIntOrNull() }?.toSet() ?: emptySet()
    )
    val readWarningItemIds: StateFlow<Set<Int>> = _readWarningItemIds.asStateFlow()

    fun markWarningAsRead(itemId: Int) {
        val newSet = _readWarningItemIds.value + itemId
        _readWarningItemIds.value = newSet
        sharedPrefs.edit().putStringSet("read_warning_item_ids", newSet.map { it.toString() }.toSet()).apply()
    }

    fun markAllWarningsAsRead() {
        val allIds = expirationWarnings.value.map { it.itemId }.toSet()
        val newSet = _readWarningItemIds.value + allIds
        _readWarningItemIds.value = newSet
        sharedPrefs.edit().putStringSet("read_warning_item_ids", newSet.map { it.toString() }.toSet()).apply()
    }

    val actionLogs: StateFlow<List<WasteActionLog>> = repository.actionLogs
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val ecoMetrics: StateFlow<DetailedEcoFinancialMetrics> = actionLogs
        .map { logs -> EcoMetricsCalculator.calculate(logs) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = EcoMetricsCalculator.calculate(emptyList())
        )

    fun getDetailedEcoFinancialMetrics(): DetailedEcoFinancialMetrics = ecoMetrics.value

    private val _scanUiState = MutableStateFlow<ScanUiState>(ScanUiState.Idle)
    val scanUiState: StateFlow<ScanUiState> = _scanUiState.asStateFlow()

    // ==========================================
    // 🥗 PERSISTED DIET & ALLERGY PREFERENCES
    // ==========================================
    private val _userDiet = MutableStateFlow(sharedPrefs.getString("user_diet", "Hepsi") ?: "Hepsi")
    val userDiet: StateFlow<String> = _userDiet.asStateFlow()

    private val _userAllergies = MutableStateFlow<Set<String>>(
        sharedPrefs.getStringSet("user_allergies", emptySet())?.toSet() ?: emptySet()
    )
    val userAllergies: StateFlow<Set<String>> = _userAllergies.asStateFlow()

    private val _dislikedIngredients = MutableStateFlow(
        sharedPrefs.getString("disliked_ingredients", "") ?: ""
    )
    val dislikedIngredients: StateFlow<String> = _dislikedIngredients.asStateFlow()

    fun updatePreferences(diet: String, allergies: Set<String>, disliked: String) {
        _userDiet.value = diet
        _userAllergies.value = allergies
        _dislikedIngredients.value = disliked
        sharedPrefs.edit()
            .putString("user_diet", diet)
            .putStringSet("user_allergies", allergies.toSet())
            .putString("disliked_ingredients", disliked)
            .apply()
    }

    // ==========================================
    // 🛒 ROOM-BACKED SHOPPING LIST
    // ==========================================
    val shoppingItems: StateFlow<List<ShoppingItem>> = repository.shoppingItems
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun addShoppingItem(
        name: String,
        quantity: String = "1 Adet",
        category: String = "",
        estimatedPrice: Double = 0.0,
        addedFromWaste: Boolean = false,
        onAdded: ((addedNew: Boolean) -> Unit)? = null
    ) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        val resolvedCategory = category.ifBlank { ReceiptParser.guessCategory(trimmed) }
        viewModelScope.launch(Dispatchers.IO) {
            val existing = repository.findShoppingItemByName(trimmed)
            if (existing != null) {
                withContext(Dispatchers.Main) { onAdded?.invoke(false) }
                return@launch
            }
            val item = ShoppingItem(
                name = trimmed,
                quantity = quantity.ifBlank { "1 Adet" },
                category = resolvedCategory,
                isChecked = false,
                estimatedPrice = estimatedPrice,
                addedFromWaste = addedFromWaste,
                addedDate = System.currentTimeMillis()
            )
            repository.insertShoppingItem(item)
            withContext(Dispatchers.Main) { onAdded?.invoke(true) }
        }
    }

    fun toggleShoppingItemChecked(item: ShoppingItem) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateShoppingItem(item.copy(isChecked = !item.isChecked))
        }
    }

    fun deleteShoppingItem(item: ShoppingItem) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteShoppingItem(item)
        }
    }

    fun clearCheckedShoppingItems() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.clearCheckedShoppingItems()
        }
    }

    fun clearAllShoppingItems() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.clearAllShoppingItems()
        }
    }

    fun transferCheckedShoppingItemsToInventory(onTransferred: ((Int) -> Unit)? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            val checkedList = shoppingItems.value.filter { it.isChecked }
            checkedList.forEach { shopItem ->
                insertShoppingItemIntoInventory(shopItem)
            }
            withContext(Dispatchers.Main) {
                onTransferred?.invoke(checkedList.size)
            }
        }
    }

    fun moveSingleShoppingItemToInventory(shopItem: ShoppingItem) {
        viewModelScope.launch(Dispatchers.IO) {
            insertShoppingItemIntoInventory(shopItem)
        }
    }

    private suspend fun insertShoppingItemIntoInventory(shopItem: ShoppingItem) {
        val resolvedCategory = if (shopItem.category.isBlank() || shopItem.category == "Kiler & Bakliyat") {
            ReceiptParser.guessCategory(shopItem.name)
        } else {
            shopItem.category
        }
        val shelfLife = when (resolvedCategory) {
            "Süt Ürünleri" -> 7
            "Sebze & Meyve" -> 5
            "Et & Şarküteri", "Et & Tavuk" -> 4
            "Dondurulmuş" -> 180
            else -> 60
        }
        val storage = when (resolvedCategory) {
            "Dondurulmuş" -> "Dondurucu"
            "Süt Ürünleri", "Et & Şarküteri", "Et & Tavuk", "Sebze & Meyve" -> "Buzdolabı"
            else -> "Kiler"
        }
        val now = System.currentTimeMillis()
        val invItem = InventoryItem(
            name = shopItem.name,
            quantity = shopItem.quantity,
            category = resolvedCategory,
            addedDate = now,
            expiryDate = now + (shelfLife * 24L * 3600L * 1000L),
            storage = storage,
            price = shopItem.estimatedPrice,
            remainingPercent = 100
        )
        repository.insertItem(invItem)
        repository.deleteShoppingItem(shopItem)
    }

    fun addToShoppingList(item: String) {
        addShoppingItem(name = item, category = ReceiptParser.guessCategory(item))
    }

    // ==========================================
    // 👩‍🍳 INTERACTIVE COOKING MODE & TIMER
    // ==========================================
    private val _activeCookingRecipe = MutableStateFlow<RecipeSuggestion?>(null)
    val activeCookingRecipe: StateFlow<RecipeSuggestion?> = _activeCookingRecipe.asStateFlow()

    private val _cookingSteps = MutableStateFlow<List<String>>(emptyList())
    val cookingSteps: StateFlow<List<String>> = _cookingSteps.asStateFlow()

    private val _currentCookingStepIndex = MutableStateFlow(0)
    val currentCookingStepIndex: StateFlow<Int> = _currentCookingStepIndex.asStateFlow()

    private val _cookingTimerSecondsLeft = MutableStateFlow(0)
    val cookingTimerSecondsLeft: StateFlow<Int> = _cookingTimerSecondsLeft.asStateFlow()

    private val _isCookingTimerRunning = MutableStateFlow(false)
    val isCookingTimerRunning: StateFlow<Boolean> = _isCookingTimerRunning.asStateFlow()

    private val _isCookingTimerFinished = MutableStateFlow(false)
    val isCookingTimerFinished: StateFlow<Boolean> = _isCookingTimerFinished.asStateFlow()

    private val _timerTotalDuration = MutableStateFlow(0)
    val timerTotalDuration: StateFlow<Int> = _timerTotalDuration.asStateFlow()

    private var timerJob: Job? = null

    fun startCookingMode(recipe: RecipeSuggestion) {
        _activeCookingRecipe.value = recipe
        val rawInstructions = recipe.instructions.orEmpty()
        val parsedSteps = if (rawInstructions.contains("\n")) {
            rawInstructions.split("\n")
                .map { it.trim().replace(Regex("""^(?:\d+[\.\)]\s*|[\-\*•]\s*)"""), "").trim() }
                .filter { it.isNotBlank() }
        } else if (rawInstructions.contains(". ")) {
            rawInstructions.split(". ")
                .map { it.trim() }
                .filter { it.isNotBlank() }
        } else {
            listOf(rawInstructions).filter { it.isNotBlank() }
        }
        _cookingSteps.value = if (parsedSteps.isNotEmpty()) parsedSteps else listOf("Tarif adımları hazırlanıyor...")
        _currentCookingStepIndex.value = 0
        _isCookingTimerFinished.value = false
        pauseCookingTimer()
        _timerTotalDuration.value = 0
        _cookingTimerSecondsLeft.value = 0

        extractMinutesAndSetTimer(parsedSteps.firstOrNull().orEmpty().ifBlank { recipe.prepTime })
    }

    fun stopCookingMode() {
        _activeCookingRecipe.value = null
        _cookingSteps.value = emptyList()
        _currentCookingStepIndex.value = 0
        _isCookingTimerFinished.value = false
        pauseCookingTimer()
        _cookingTimerSecondsLeft.value = 0
    }

    fun nextCookingStep() {
        if (_currentCookingStepIndex.value < _cookingSteps.value.size - 1) {
            _currentCookingStepIndex.value += 1
            val stepText = _cookingSteps.value.getOrNull(_currentCookingStepIndex.value).orEmpty()
            extractMinutesAndSetTimer(stepText)
        }
    }

    fun previousCookingStep() {
        if (_currentCookingStepIndex.value > 0) {
            _currentCookingStepIndex.value -= 1
        }
    }

    private fun extractMinutesAndSetTimer(text: String) {
        val regex = Regex("""(\d+)\s*(?:dakika|dk|min|minute)""", RegexOption.IGNORE_CASE)
        val match = regex.find(text)
        if (match != null) {
            val minutes = match.groupValues[1].toIntOrNull() ?: 0
            if (minutes > 0 && !_isCookingTimerRunning.value) {
                setCookingTimer(minutes * 60)
            }
        }
    }

    fun setCookingTimer(seconds: Int) {
        pauseCookingTimer()
        _isCookingTimerFinished.value = false
        val safeSeconds = seconds.coerceAtLeast(0)
        _timerTotalDuration.value = safeSeconds
        _cookingTimerSecondsLeft.value = safeSeconds
    }

    fun addCookingTimerSeconds(deltaSeconds: Int) {
        _isCookingTimerFinished.value = false
        val updated = (_cookingTimerSecondsLeft.value + deltaSeconds).coerceAtLeast(0)
        _cookingTimerSecondsLeft.value = updated
        if (updated > _timerTotalDuration.value) {
            _timerTotalDuration.value = updated
        }
    }

    fun dismissCookingTimerFinished() {
        _isCookingTimerFinished.value = false
    }

    fun startCookingTimer() {
        if (_cookingTimerSecondsLeft.value <= 0) return
        _isCookingTimerFinished.value = false
        _isCookingTimerRunning.value = true
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (_cookingTimerSecondsLeft.value > 0 && _isCookingTimerRunning.value) {
                delay(1000)
                _cookingTimerSecondsLeft.value = (_cookingTimerSecondsLeft.value - 1).coerceAtLeast(0)
            }
            if (_cookingTimerSecondsLeft.value <= 0 && _isCookingTimerRunning.value) {
                _isCookingTimerRunning.value = false
                _isCookingTimerFinished.value = true
                NotificationHelper.sendCookingTimerDoneNotification(
                    getApplication(),
                    _activeCookingRecipe.value?.title ?: "Tarif"
                )
            }
        }
    }

    fun pauseCookingTimer() {
        _isCookingTimerRunning.value = false
        timerJob?.cancel()
    }

    fun resetCookingTimer() {
        pauseCookingTimer()
        _isCookingTimerFinished.value = false
        _cookingTimerSecondsLeft.value = _timerTotalDuration.value
    }

    // ==========================================
    // 📅 WEEKLY AI MEAL PLANNER
    // ==========================================
    val mealPlans: StateFlow<List<MealPlanItem>> = repository.mealPlans
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _isMealPlanLoading = MutableStateFlow(false)
    val isMealPlanLoading: StateFlow<Boolean> = _isMealPlanLoading.asStateFlow()

    private val _weeklyMealPlanTip = MutableStateFlow("")
    val weeklyMealPlanTip: StateFlow<String> = _weeklyMealPlanTip.asStateFlow()

    private val _mealPlanUsedFallback = MutableStateFlow(false)
    val mealPlanUsedFallback: StateFlow<Boolean> = _mealPlanUsedFallback.asStateFlow()

    private val awardedMealPlanIds: MutableSet<String> = (
        sharedPrefs.getStringSet("awarded_meal_plan_ids", emptySet()) ?: emptySet()
    ).toMutableSet()

    fun generateWeeklyMealPlanWithAi(onCompleted: (isAiGenerated: Boolean) -> Unit = {}) {
        _isMealPlanLoading.value = true
        viewModelScope.launch {
            val currentInventory = activeItems.value
            val diet = _userDiet.value
            val allergiesSet = _userAllergies.value
            val allergies = allergiesSet.joinToString(", ").ifBlank { "Yok" }
            val disliked = _dislikedIngredients.value.ifBlank { "Yok" }
            val activeKey = getActiveApiKey()

            var generatedWithAi = false

            if (activeKey.isNotEmpty()) {
                val expiringNames = if (currentInventory.isNotEmpty()) {
                    currentInventory.sortedBy { it.getRemainingDays() }.take(10)
                        .joinToString(", ") { "${it.name} (${it.quantity}, ${it.getRemainingDays()} gün)" }
                } else {
                    "Henüz ürün yok — dengeli temel ev malzemeleriyle planla"
                }

                val prompt = """
                    Sen "Smart Kitchen AI" haftalık yemek planlayıcısısın.
                    Kullanıcının buzdolabındaki malzemeleri ve SKT'si yaklaşan gıdaları israf etmeden tükettirecek 7 günlük (Pazartesi'den Pazar'a) öğün planı oluştur.
                    
                    KULLANICI PROFİLİ:
                    - Diyet: $diet
                    - Alerjiler: $allergies
                    - Sevilmeyen Malzemeler: $disliked
                    - ENVANTERDEKİ MALZEMELER: $expiringNames
                    
                    KURALLAR:
                    1. 7 gün için günde 1 veya 2 öğün (toplam 7-10 öğün) üret.
                    2. Özellikle SKT'si yakın olan buzdolabı malzemelerini ilk günlerde kullan.
                    3. Diyet, alerji ve sevilmeyen malzemelere KESİNLİKLE uy.
                    4. Yanıtı KESİNLİKLE aşağıdaki JSON formatında döndür:
                    {
                      "meals": [
                        {
                          "day_of_week": "Pazartesi",
                          "meal_type": "Akşam Yemeği",
                          "recipe_title": "Fırında Sebzeli Tavuk",
                          "ingredients": "Tavuk, Patates, Biber, Zeytinyağı",
                          "prep_time": "30 dk",
                          "calories": 450,
                          "instructions": "Sebzeleri doğrayıp tavukla harmanlayın, 200 derecede 30 dk fırınlayın."
                        }
                      ],
                      "weekly_tip": "Bu hafta dolaptaki sebzeleri ilk 3 günde tüketerek sıfır atık hedefine ulaşabilirsiniz."
                    }
                """.trimIndent()

                when (val aiResult = GeminiApiClient.generateMealPlan(prompt, activeKey)) {
                    is AiCallResult.Success -> {
                        val response = aiResult.data
                        withContext(Dispatchers.IO) {
                            repository.clearMealPlans()
                            val planEntities = response.meals.map {
                                MealPlanItem(
                                    dayOfWeek = it.dayOfWeek,
                                    mealType = it.mealType,
                                    recipeTitle = it.recipeTitle,
                                    ingredients = it.ingredients,
                                    prepTime = it.prepTime,
                                    calories = it.calories,
                                    isCooked = false,
                                    instructions = it.instructions
                                )
                            }
                            repository.insertMealPlans(planEntities)
                        }
                        awardedMealPlanIds.clear()
                        sharedPrefs.edit().remove("awarded_meal_plan_ids").apply()
                        _weeklyMealPlanTip.value = response.weeklyTip
                        _mealPlanUsedFallback.value = false
                        generatedWithAi = true
                    }
                    is AiCallResult.Error -> {
                        Log.w("SmartKitchenAI", "Meal plan AI fallback: ${aiResult.message}")
                    }
                }
            }

            if (!generatedWithAi) {
                val fallbackMeals = LocalRecipeEngine.generateLocalFallbackMealPlan(
                    activeItems = currentInventory,
                    diet = diet,
                    allergies = allergiesSet,
                    dislikedIngredients = _dislikedIngredients.value
                )
                withContext(Dispatchers.IO) {
                    repository.clearMealPlans()
                    repository.insertMealPlans(fallbackMeals)
                }
                awardedMealPlanIds.clear()
                sharedPrefs.edit().remove("awarded_meal_plan_ids").apply()
                _weeklyMealPlanTip.value = if (activeKey.isEmpty()) {
                    "Çevrimdışı akıllı planlayıcı ile diyetinize uygun 7 günlük menü oluşturuldu. Kişiselleştirilmiş AI planı için API anahtarı ekleyebilirsiniz."
                } else {
                    "Gemini servisine ulaşılamadığı için çevrimdışı akıllı menü oluşturuldu."
                }
                _mealPlanUsedFallback.value = true
            }

            _isMealPlanLoading.value = false
            onCompleted(generatedWithAi)
        }
    }

    fun toggleMealPlanCooked(item: MealPlanItem) {
        viewModelScope.launch(Dispatchers.IO) {
            val updated = item.copy(isCooked = !item.isCooked)
            repository.updateMealPlan(updated)
            val idStr = item.id.toString()
            if (updated.isCooked && idStr !in awardedMealPlanIds) {
                awardedMealPlanIds.add(idStr)
                sharedPrefs.edit().putStringSet("awarded_meal_plan_ids", awardedMealPlanIds.toSet()).apply()

                val log = WasteActionLog(
                    itemName = item.recipeTitle,
                    category = "Yemek Planı",
                    actionType = "CONSUMED",
                    pointsGained = 40,
                    co2SavedKg = 1.5,
                    cost = 60.0
                )
                repository.insertActionLog(log)
                addOwnFeedEvent("Menü: ${item.recipeTitle}", 1.5)
            }
        }
    }

    fun clearMealPlans() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.clearMealPlans()
            awardedMealPlanIds.clear()
            sharedPrefs.edit().remove("awarded_meal_plan_ids").apply()
        }
    }

    // ==========================================
    // 📦 BARCODE SCANNER HELPER (LOCAL + OPEN FOOD FACTS)
    // ==========================================
    fun scanBarcodeBitmap(bitmap: Bitmap, onResult: (BarcodeProductInfo?) -> Unit) {
        viewModelScope.launch {
            val barcodes = BarcodeScannerHelper.scanBarcodeFromBitmap(bitmap)
            val detectedBarcode = barcodes.firstOrNull()
            if (detectedBarcode != null) {
                val info = BarcodeScannerHelper.lookupBarcodeWithOnlineFallback(detectedBarcode)
                onResult(info)
            } else {
                onResult(null)
            }
        }
    }

    fun lookupBarcodeManual(barcodeRaw: String, onResult: (BarcodeProductInfo) -> Unit) {
        viewModelScope.launch {
            val info = BarcodeScannerHelper.lookupBarcodeWithOnlineFallback(barcodeRaw)
            onResult(info)
        }
    }

    fun addBarcodeProductToInventory(info: BarcodeProductInfo, customShelfLife: Int? = null) {
        val days = customShelfLife ?: info.shelfLifeDays
        addManualItem(
            name = info.name,
            quantity = info.defaultQuantity,
            category = info.category,
            shelfLifeDays = days,
            storage = info.storage,
            price = info.estimatedPrice
        )
    }

    // ==========================================
    // 👤 USER PROFILE & VALIDATION
    // ==========================================
    private val takenUsernames: MutableSet<String> = (
        sharedPrefs.getStringSet("registered_usernames_set", emptySet()) ?: emptySet()
    ).map { it.lowercase(trLocale) }.toMutableSet().apply {
        addAll(UsernameValidator.defaultTakenUsernames)
    }

    private val _userNickname = MutableStateFlow(sharedPrefs.getString("user_nickname", "") ?: "")
    val userNickname: StateFlow<String> = _userNickname.asStateFlow()

    private val _userAvatar = MutableStateFlow(sharedPrefs.getString("user_avatar", "🥦") ?: "🥦")
    val userAvatar: StateFlow<String> = _userAvatar.asStateFlow()

    private val _isRegistered = MutableStateFlow(sharedPrefs.getBoolean("is_registered", false))
    val isRegistered: StateFlow<Boolean> = _isRegistered.asStateFlow()

    fun updateAvatar(avatar: String) {
        _userAvatar.value = avatar
        sharedPrefs.edit().putString("user_avatar", avatar).apply()
    }

    fun validateUsername(input: String, currentUsername: String? = null): String? {
        return UsernameValidator.validateForDisplay(
            input = input,
            takenUsernames = takenUsernames,
            currentUsername = currentUsername
        )
    }

    fun updateNickname(name: String, avatar: String = _userAvatar.value): Boolean {
        val currentNick = _userNickname.value.takeIf { _isRegistered.value && it.isNotBlank() }
        val validationError = UsernameValidator.validateForSubmit(
            input = name,
            takenUsernames = takenUsernames,
            currentUsername = currentNick
        )
        if (validationError != null) return false

        val cleanName = name.trim()
        val oldLower = currentNick?.lowercase(trLocale)
        val newLower = cleanName.lowercase(trLocale)

        _userNickname.value = cleanName
        _userAvatar.value = avatar
        _isRegistered.value = true

        val savedSet = (sharedPrefs.getStringSet("registered_usernames_set", emptySet()) ?: emptySet()).toMutableSet()
        if (oldLower != null && oldLower != newLower) {
            savedSet.remove(oldLower)
            takenUsernames.remove(oldLower)
        }
        savedSet.add(newLower)
        takenUsernames.add(newLower)

        sharedPrefs.edit()
            .putString("user_nickname", cleanName)
            .putString("user_avatar", avatar)
            .putBoolean("is_registered", true)
            .putStringSet("registered_usernames_set", savedSet)
            .apply()

        return true
    }

    fun logoutOrResetProfile() {
        val oldLower = _userNickname.value.trim().lowercase(trLocale)
        if (oldLower.isNotEmpty() && oldLower !in UsernameValidator.defaultTakenUsernames) {
            takenUsernames.remove(oldLower)
            val savedSet = (sharedPrefs.getStringSet("registered_usernames_set", emptySet()) ?: emptySet()).toMutableSet()
            savedSet.remove(oldLower)
            sharedPrefs.edit().putStringSet("registered_usernames_set", savedSet).apply()
        }
        _userNickname.value = ""
        _isRegistered.value = false
        sharedPrefs.edit()
            .remove("user_nickname")
            .remove("user_avatar")
            .putBoolean("is_registered", false)
            .apply()
    }

    // ==========================================
    // 🌍 COMMUNITY IMPACT & LIVE FEED
    // ==========================================
    private val _globalCo2Saved = MutableStateFlow(sharedPrefs.getFloat("global_co2_saved", 124.5f))
    val globalCo2Saved: StateFlow<Float> = _globalCo2Saved.asStateFlow()

    private val _globalWaterSaved = MutableStateFlow(sharedPrefs.getFloat("global_water_saved", 43580.0f))
    val globalWaterSaved: StateFlow<Float> = _globalWaterSaved.asStateFlow()

    private val _globalFeedEvents = MutableStateFlow<List<String>>(emptyList())
    val globalFeedEvents: StateFlow<List<String>> = _globalFeedEvents.asStateFlow()

    fun addOwnFeedEvent(itemName: String, co2Saved: Double) {
        val nick = _userNickname.value.ifEmpty { "Mutfak Şefi" }
        val waterSaved = co2Saved * 350.0
        val event = "$nick, $itemName gıdasını kurtararak %.1f kg CO₂ ve %.0f L su tasarrufu sağladı!".format(Locale.US, co2Saved, waterSaved)
        val currentList = _globalFeedEvents.value.toMutableList()
        currentList.add(0, event)
        if (currentList.size > 25) {
            currentList.removeAt(currentList.lastIndex)
        }
        _globalFeedEvents.value = currentList

        val newGlobal = _globalCo2Saved.value + co2Saved.toFloat()
        _globalCo2Saved.value = newGlobal

        val newGlobalWater = _globalWaterSaved.value + waterSaved.toFloat()
        _globalWaterSaved.value = newGlobalWater

        sharedPrefs.edit()
            .putFloat("global_co2_saved", newGlobal)
            .putFloat("global_water_saved", newGlobalWater)
            .apply()
    }

    fun getUserTitle(score: Int): String {
        return when {
            score < 100 -> "Mutfak Çaylağı 🍳"
            score < 300 -> "Yeşil Aşçı 🌿"
            score < 600 -> "Atıksız Şef 🥗"
            score < 1000 -> "Mutfak Elçisi 👑"
            else -> "Atık Savaşçısı Kahramanı 🛡️"
        }
    }

    // ==========================================
    // 💡 MICRO TIPS
    // ==========================================
    private val microTipsList = listOf(
        "Yeşillikleri kağıt havluya sararak buzdolabında saklamak ömürlerini 2 katına çıkarır.",
        "Muzların sapını streç film ile sarmak, olgunlaşma hızını yavaşlatarak bozulmalarını önler.",
        "Patates ve soğanları asla aynı sepet içinde saklamayın; yaydıkları gazlar birbirlerinin çürümesini hızlandırır.",
        "Bayatlayan ekmekleri dilimleyip fırınlayarak kıtır krutonlar yapabilir ve çorbalarda kullanabilirsiniz.",
        "Artan sebze kabuklarını ve saplarını dondurucuda dondurup kaynatarak nefis sebze suları hazırlayın.",
        "Yumurtanın tazeliğini suya koyarak anlayın; dibe çöküyorsa taze, suyun üstüne çıkıyorsa bayattır.",
        "Salçanın küflenmesini önlemek için üzerine ince bir tabaka zeytinyağı gezdirebilirsiniz.",
        "Dondurucuya atacağınız etleri porsiyonlara bölerek saklayın; çözülen et asla tekrar dondurulmamalıdır.",
        "Sarımsakları karanlık ve havadar bir yerde saklamak filizlenmelerini ve yumuşamalarını engeller.",
        "Sütü buzdolabının kapağında değil, daha soğuk olan orta veya arka raflarında saklayın.",
        "Zencefili soyup dilimledikten sonra dondurucuda saklayarak aylar boyu taze tutabilirsiniz.",
        "Domatesleri buzdolabına koymak aromalarını kaybettirir; oda sıcaklığında saklamak daha iyidir.",
        "Limonun yarısını saklayacaksanız, kesik yüzeyine azıcık tuz sürerek kurumasını önleyebilirsiniz.",
        "Un ve pirincin böceklenmesini önlemek için kavanozlarına 1 adet kuru defne yaprağı atabilirsiniz.",
        "Taze otları zeytinyağı ile karıştırıp buz kalıplarında dondurarak yemeklerinize doğrudan katabilirsiniz.",
        "Mantar yıkanınca sünger gibi su çeker ve lezzeti bozulur; nemli bir havlu kağıt ile silerek temizleyin.",
        "Avokadoyu hızlıca olgunlaştırmak için bir kese kağıdına yanına elma veya muz koyarak sarın."
    )

    private val _currentMicroTip = MutableStateFlow(microTipsList.first())
    val currentMicroTip: StateFlow<String> = _currentMicroTip.asStateFlow()

    init {
        ensureCalorieDayIsCurrent()
        rotateMicroTip()
        viewModelScope.launch {
            activeItems.collect { items ->
                if (items.isNotEmpty()) {
                    checkAndTriggerExpirations(items)
                }
            }
        }
        viewModelScope.launch {
            actionLogs.collect { logs ->
                if (_globalFeedEvents.value.isEmpty() && logs.isNotEmpty()) {
                    val nick = _userNickname.value.ifEmpty { "Mutfak Şefi" }
                    _globalFeedEvents.value = logs.filter { it.actionType == "CONSUMED" }
                        .take(10)
                        .map { log ->
                            val water = EcoMetricsCalculator.waterSavedForLog(log)
                            "$nick, ${log.itemName} ile %.1f kg CO₂ ve %.0f L su tasarrufu sağladı!".format(Locale.US, log.co2SavedKg, water)
                        }
                }
            }
        }
    }

    private fun checkAndTriggerExpirations(items: List<InventoryItem>) {
        val currentTime = System.currentTimeMillis()
        val notifiedSet = sharedPrefs.getStringSet("notified_expirations", emptySet()) ?: emptySet()
        val updatedNotifiedSet = notifiedSet.toMutableSet()
        var hasChanges = false

        items.forEach { item ->
            val remainingDays = item.getRemainingDays(currentTime)
            if (remainingDays in listOf(0, 1, 3, 7)) {
                val notifyKey = "${item.id}_${remainingDays}"
                if (!updatedNotifiedSet.contains(notifyKey)) {
                    val title = "Tüketim Uyarısı: ${item.name}"
                    val message = when (remainingDays) {
                        7 -> "${item.name} için 1 hafta kaldı. Tariflerde değerlendirebilirsiniz."
                        3 -> "${item.name} için son 3 gün! Bozulmadan önce tüketmeyi unutmayın."
                        1 -> "Dikkat! ${item.name} yarın son gününe giriyor. Hemen tüketin!"
                        0 -> "Bugün son gün! ${item.name} israf olmadan önce hemen değerlendirin."
                        else -> ""
                    }
                    if (message.isNotEmpty()) {
                        NotificationHelper.sendNotification(getApplication(), item.id, title, message)
                        updatedNotifiedSet.add(notifyKey)
                        hasChanges = true
                    }
                }
            }
        }

        if (hasChanges) {
            sharedPrefs.edit().putStringSet("notified_expirations", updatedNotifiedSet).apply()
        }
    }

    fun rotateMicroTip() {
        val current = _currentMicroTip.value
        val candidates = microTipsList.filter { it != current }.ifEmpty { microTipsList }
        _currentMicroTip.value = candidates.random()
    }

    // ==========================================
    // 🥫 INVENTORY CRUD & UNDO
    // ==========================================
    fun addManualItem(
        name: String,
        quantity: String,
        category: String,
        shelfLifeDays: Int,
        storage: String,
        remainingPercent: Int = 100,
        price: Double = 0.0
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val currentTime = System.currentTimeMillis()
            val expiryDate = currentTime + (shelfLifeDays.coerceAtLeast(0).toLong() * 24L * 60L * 60L * 1000L)
            val newItem = InventoryItem(
                name = name.trim(),
                quantity = quantity.ifBlank { "1 Adet" },
                category = category,
                addedDate = currentTime,
                expiryDate = expiryDate,
                storage = storage,
                remainingPercent = remainingPercent.coerceIn(0, 100),
                price = price.coerceAtLeast(0.0)
            )
            repository.insertItem(newItem)
        }
    }

    fun restoreInventoryItem(item: InventoryItem) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.insertItem(item.copy(id = 0))
        }
    }

    fun addItemFromShoppingList(name: String) {
        val category = ReceiptParser.guessCategory(name)
        val shelfLife = when (category) {
            "Süt Ürünleri" -> 7
            "Sebze & Meyve" -> 5
            "Et & Tavuk" -> 3
            "Kiler / Kuru Gıda" -> 60
            "Dondurulmuş" -> 180
            else -> 10
        }
        val storage = when (category) {
            "Kiler / Kuru Gıda" -> "Kiler"
            "Dondurulmuş" -> "Dondurucu"
            else -> "Buzdolabı"
        }
        addManualItem(
            name = name,
            quantity = "1 Adet",
            category = category,
            shelfLifeDays = shelfLife,
            storage = storage
        )
    }

    fun updateItem(item: InventoryItem) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateItem(item)
        }
    }

    fun consumeItem(item: InventoryItem, addToShopping: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteItem(item)

            val points = 50
            val co2Saved = when (item.category) {
                "Et & Tavuk" -> 4.8
                "Süt Ürünleri" -> 2.2
                "Sebze & Meyve" -> 0.9
                "Kiler / Kuru Gıda" -> 1.2
                "Dondurulmuş" -> 1.5
                else -> 1.0
            }

            val log = WasteActionLog(
                itemName = item.name,
                category = item.category,
                actionType = "CONSUMED",
                pointsGained = points,
                co2SavedKg = co2Saved,
                cost = item.price
            )
            repository.insertActionLog(log)
            addOwnFeedEvent(item.name, co2Saved)

            if (addToShopping) {
                addShoppingItem(
                    name = item.name,
                    quantity = item.quantity,
                    category = item.category,
                    estimatedPrice = item.price,
                    addedFromWaste = false
                )
            }
        }
    }

    fun wasteItem(item: InventoryItem, addToShopping: Boolean = true) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteItem(item)

            val log = WasteActionLog(
                itemName = item.name,
                category = item.category,
                actionType = "WASTED",
                pointsGained = -20,
                co2SavedKg = 0.0,
                cost = item.price
            )
            repository.insertActionLog(log)

            if (addToShopping) {
                addShoppingItem(
                    name = item.name,
                    quantity = item.quantity,
                    category = item.category,
                    estimatedPrice = item.price,
                    addedFromWaste = true
                )
            }
        }
    }

    fun addScannedItemsToInventory(detectedItems: List<DetectedItem>) {
        viewModelScope.launch(Dispatchers.IO) {
            val currentTime = System.currentTimeMillis()
            val items = detectedItems.map {
                val shelfLife = if (it.estimatedShelfLifeDays <= 0) 5 else it.estimatedShelfLifeDays
                val expiryDate = currentTime + (shelfLife.toLong() * 24L * 60L * 60L * 1000L)

                val storage = when (it.storage.trim().lowercase(trLocale)) {
                    "buzdolabi", "buzdolabı" -> "Buzdolabı"
                    "kiler" -> "Kiler"
                    "dondurucu" -> "Dondurucu"
                    else -> "Buzdolabı"
                }

                val category = ReceiptParser.guessCategory(it.name)

                InventoryItem(
                    name = it.name,
                    quantity = it.quantity.ifBlank { "1 Adet" },
                    category = category,
                    addedDate = currentTime,
                    expiryDate = expiryDate,
                    storage = storage,
                    price = (it.price ?: 0.0).coerceAtLeast(0.0)
                )
            }
            repository.insertItems(items)
        }
    }

    fun clearHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.clearLogs()
            _globalFeedEvents.value = emptyList()
        }
    }

    fun setScanUiStateToIdle() {
        _scanUiState.value = ScanUiState.Idle
    }

    // ==========================================
    // 📸 RECEIPT & KITCHEN OCR / VISION SCANNING
    // ==========================================
    private suspend fun extractTextFromBitmap(bitmap: Bitmap): String = withContext(Dispatchers.Default) {
        suspendCancellableCoroutine { continuation ->
            try {
                val image = InputImage.fromBitmap(bitmap, 0)
                val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                recognizer.process(image)
                    .addOnSuccessListener { visionText ->
                        continuation.resume(visionText.text)
                    }
                    .addOnFailureListener { e ->
                        Log.e("SmartKitchenAI", "ML Kit OCR Error: ${e.message}", e)
                        continuation.resume("")
                    }
            } catch (e: Exception) {
                Log.e("SmartKitchenAI", "ML Kit Init Error: ${e.message}", e)
                continuation.resume("")
            }
        }
    }

    fun scanReceiptTextOrImage(textInput: String, bitmap: Bitmap? = null, scanMode: String = "RECEIPT") {
        _scanUiState.value = ScanUiState.Loading

        viewModelScope.launch {
            var effectiveOcrText = ""
            if (bitmap != null) {
                try {
                    effectiveOcrText = extractTextFromBitmap(bitmap)
                } catch (e: Exception) {
                    Log.e("SmartKitchenAI", "OCR Extraction failed: ${e.message}", e)
                }
            }

            val combinedTextInput = buildString {
                if (textInput.isNotBlank() &&
                    !textInput.contains("fotoğrafı") &&
                    !textInput.contains("okundu") &&
                    !textInput.contains("Lütfen")
                ) {
                    appendLine(textInput)
                }
                if (effectiveOcrText.isNotBlank()) {
                    appendLine(effectiveOcrText)
                }
            }.trim()

            val diet = _userDiet.value
            val allergiesSet = _userAllergies.value
            val allergies = allergiesSet.joinToString(", ").ifBlank { "Yok" }
            val disliked = _dislikedIngredients.value.ifBlank { "Yok" }
            val isReceiptMode = scanMode == "RECEIPT"
            val activeKey = getActiveApiKey()

            var lastAiError: AiCallResult.Error<*>? = null

            if (activeKey.isNotEmpty()) {
                val promptText = if (isReceiptMode) {
                    """
                        Görseldeki veya aşağıdaki fiş metnindeki gıda ürünlerini, miktarlarını, saklama alanlarını ve fiyatlarını çıkar.
                        Yanıtı KESİNLİKLE aşağıdaki JSON formatında döndür:
                        {
                          "detected_items": [
                            {"name": "Süt", "quantity": "1 Litre", "estimated_shelf_life_days": 5, "storage": "Buzdolabı", "price": 35.0}
                          ],
                          "recipe_suggestions": [],
                          "sustainability_impact": "Fiş başarıyla analiz edildi.",
                          "micro_tip": "Gıdaları uygun sıcaklıkta saklayarak israfı önleyin."
                        }
                        
                        Fiş Metni:
                        $combinedTextInput
                    """.trimIndent()
                } else {
                    val areaDesc = when (scanMode) {
                        "PANTRY" -> "Kiler dolabındaki kuru gıda ve bakliyatları tespit et (storage='Kiler')."
                        "FREEZER" -> "Derin dondurucudaki dondurulmuş gıdaları tespit et (storage='Dondurucu')."
                        else -> "Buzdolabındaki taze gıda ürünlerini tespit et (storage='Buzdolabı')."
                    }
                    """
                        $areaDesc
                        Kullanıcı Profili -> Diyet: $diet | Alerjiler: $allergies | Sevilmeyen: $disliked
                        
                        Tespit edilen gıdalarla kullanıcının diyetine uygun 1-2 adet pratik yemek tarifi öner ve JSON döndür:
                        {
                          "detected_items": [
                            {"name": "Ürün Adı", "quantity": "1 Adet", "estimated_shelf_life_days": 5, "storage": "Buzdolabı", "price": 0.0}
                          ],
                          "recipe_suggestions": [
                            {
                              "title": "Tarif Adı",
                              "used_expiring_items": ["Ürün Adı"],
                              "prep_time": "15 dk",
                              "instructions": "1. Adım...\n2. Adım...",
                              "calories": 350,
                              "protein": "20g",
                              "carbs": "40g",
                              "fat": "10g",
                              "calorie_analysis": "Dengeli öğün."
                            }
                          ],
                          "sustainability_impact": "Mutfaktaki gıdalar kaydedildi.",
                          "micro_tip": "Kısa saklama tavsiyesi."
                        }
                        
                        Ek Metin:
                        $combinedTextInput
                    """.trimIndent()
                }

                val aiCall = if (isReceiptMode) {
                    GeminiApiClient.analyzeReceipt(promptText, bitmap, activeKey)
                } else {
                    GeminiApiClient.analyzeKitchenVision(promptText, bitmap, scanMode, activeKey)
                }

                when (aiCall) {
                    is AiCallResult.Success -> {
                        val parsedResult = aiCall.data
                        val finalItems = if (!parsedResult.detectedItems.isNullOrEmpty()) {
                            parsedResult.detectedItems
                        } else {
                            ReceiptParser.parseTextToDetectedItems(combinedTextInput, scanMode)
                        }

                        if (finalItems.isNotEmpty()) {
                            val finalResult = parsedResult.copy(detectedItems = finalItems)
                            _scanUiState.value = ScanUiState.Success(
                                result = finalResult,
                                isOfflineFallback = false,
                                modelUsed = aiCall.modelUsed
                            )
                            finalResult.microTip?.takeIf { it.isNotBlank() }?.let { _currentMicroTip.value = it }
                            return@launch
                        }
                    }
                    is AiCallResult.Error -> {
                        lastAiError = aiCall
                        Log.w("SmartKitchenAI", "Gemini scan fallback: ${aiCall.message}")
                    }
                }
            }

            // Guaranteed On-Device Fallback
            val inputToParse = if (combinedTextInput.isNotBlank()) combinedTextInput else textInput
            val fallbackResult = LocalRecipeEngine.generateLocalSmartResult(
                textInput = inputToParse,
                scanMode = scanMode,
                isInventoryBased = false,
                diet = diet,
                allergies = allergiesSet,
                dislikedIngredients = _dislikedIngredients.value
            )

            if (fallbackResult.detectedItems.isNullOrEmpty()) {
                val isKeyIssue = activeKey.isEmpty() ||
                    lastAiError?.errorType == AiErrorType.NO_API_KEY ||
                    lastAiError?.errorType == AiErrorType.INVALID_API_KEY
                val errMessage = lastAiError?.message ?: if (activeKey.isEmpty()) {
                    "Görselden veya metinden ürün okunamadı. Görsel tanıma için Gemini API anahtarı ekleyin veya ürünleri metin olarak yazın."
                } else {
                    "Görselden veya metinden ürün okunamadı. Lütfen daha net bir fotoğraf seçin veya ürünleri metin olarak girin."
                }
                _scanUiState.value = ScanUiState.Error(errMessage, isApiKeyIssue = isKeyIssue)
            } else {
                val reason = lastAiError?.message ?: if (activeKey.isEmpty()) {
                    "Gemini API anahtarı ekli olmadığı için çevrimdışı fiş ayrıştırıcı kullanıldı."
                } else {
                    "Gemini servisine ulaşılamadığı için çevrimdışı ayrıştırıcı kullanıldı."
                }
                _scanUiState.value = ScanUiState.Success(
                    result = fallbackResult,
                    isOfflineFallback = true,
                    fallbackReason = reason
                )
                fallbackResult.microTip?.takeIf { it.isNotBlank() }?.let { _currentMicroTip.value = it }
            }
        }
    }

    // ==========================================
    // 🍳 AI RECIPE GENERATION FROM INVENTORY
    // ==========================================
    fun generateRecipesFromInventory() {
        _scanUiState.value = ScanUiState.Loading

        viewModelScope.launch {
            val currentItems = activeItems.value
            val effectiveItemsText = if (currentItems.isNotEmpty()) {
                currentItems.sortedBy { it.getRemainingDays() }.joinToString("\n") {
                    "- ${it.name} (${it.quantity}, SKT: ${it.getRemainingDays()} gün, Kalan: %${it.remainingPercent}, Konum: ${it.storage})"
                }
            } else {
                "- Makarna (500g, Kiler)\n- Yumurta (6 Adet, Buzdolabı)\n- Domates (3 Adet, Buzdolabı)\n- Yoğurt (500g, Buzdolabı)\n- Peynir (200g, Buzdolabı)"
            }

            val diet = _userDiet.value
            val allergiesSet = _userAllergies.value
            val allergies = allergiesSet.joinToString(", ").ifBlank { "Yok" }
            val disliked = _dislikedIngredients.value.ifBlank { "Yok" }
            val activeKey = getActiveApiKey()

            var lastErrorMsg: String? = null

            if (activeKey.isNotEmpty()) {
                val promptText = """
                    Sen "Smart Kitchen AI" mutfak asistanısın. Kullanıcının evindeki malzemeleri kullanarak kişiselleştirilmiş 2 yemek tarifi üret.
                    
                    KULLANICI DİYET FİLTRE VE ALERJİ BİLGİLERİ:
                    - Diyet Tercihi: $diet
                    - Alerjiler: $allergies (Bu alerjenleri içeren malzemeleri KESİNLİKLE KULLANMA!)
                    - Sevilmeyen Malzemeler: $disliked (Bu malzemeleri KESİNLİKLE KULLANMA!)
                    
                    ENVANTERDEKİ MALZEMELER:
                    $effectiveItemsText
                    
                    Özellikle SKT'si yaklaşan veya kalan miktarı azalan ürünleri öncelikli kullanarak 2 pratik Türkçe yemek tarifi oluştur.
                    Her tarif için kalori (Int), protein/carbs/fat (örn: "25g"), calorie_analysis ve numaralı adım adım hazırlanış ('instructions') ekle.
                    Yanıtı KESİNLİKLE aşağıdaki JSON yapısında döndür:
                    {
                      "detected_items": [],
                      "recipe_suggestions": [
                        {
                          "title": "Tarif Adı",
                          "used_expiring_items": ["Ürün 1", "Ürün 2"],
                          "prep_time": "15 dk",
                          "instructions": "1. Adım...\n2. Adım...",
                          "calories": 420,
                          "protein": "25g",
                          "carbs": "50g",
                          "fat": "12g",
                          "calorie_analysis": "Besin değeri özeti."
                        }
                      ],
                      "sustainability_impact": "Bu tariflerle gıda israfını ve karbon salınımını önlediniz!",
                      "micro_tip": "Pratik saklama ipucu."
                    }
                """.trimIndent()

                when (val aiCall = GeminiApiClient.generateSmartKitchenResult(
                    prompt = promptText,
                    apiKey = activeKey,
                    temperature = 0.45f
                )) {
                    is AiCallResult.Success -> {
                        val parsedResult = aiCall.data
                        if (!parsedResult.recipeSuggestions.isNullOrEmpty()) {
                            _scanUiState.value = ScanUiState.Success(
                                result = parsedResult,
                                isOfflineFallback = false,
                                modelUsed = aiCall.modelUsed
                            )
                            parsedResult.microTip?.takeIf { it.isNotBlank() }?.let { _currentMicroTip.value = it }
                            return@launch
                        }
                    }
                    is AiCallResult.Error -> {
                        lastErrorMsg = aiCall.message
                        Log.w("SmartKitchenAI", "Recipe AI fallback: ${aiCall.message}")
                    }
                }
            }

            val fallbackResult = LocalRecipeEngine.generateLocalSmartResult(
                textInput = "",
                scanMode = "FRIDGE",
                isInventoryBased = true,
                activeItemNames = currentItems.map { it.name },
                diet = diet,
                allergies = allergiesSet,
                dislikedIngredients = _dislikedIngredients.value
            )

            if (fallbackResult.recipeSuggestions.isNullOrEmpty()) {
                _scanUiState.value = ScanUiState.Error(
                    "Tarif oluşturulamadı. Lütfen filtrelerinizi veya internet bağlantınızı kontrol edin.",
                    isApiKeyIssue = activeKey.isEmpty()
                )
            } else {
                val reason = lastErrorMsg ?: if (activeKey.isEmpty()) {
                    "Gemini API anahtarı henüz girilmediği için diyetinize uygun çevrimdışı tarifler önerildi."
                } else {
                    "Gemini API'ye ulaşılamadığı için çevrimdışı akıllı tarifler gösteriliyor."
                }
                _scanUiState.value = ScanUiState.Success(
                    result = fallbackResult,
                    isOfflineFallback = true,
                    fallbackReason = reason
                )
                fallbackResult.microTip?.takeIf { it.isNotBlank() }?.let { _currentMicroTip.value = it }
            }
        }
    }
}

class InventoryViewModelFactory(
    private val application: Application,
    private val repository: InventoryRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(InventoryViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return InventoryViewModel(application, repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
