package com.example.ui

import android.content.Context
import android.app.Application
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.api.FirebaseAiManager
import com.example.api.SmartKitchenResult
import com.example.api.DetectedItem
import com.example.api.RecipeSuggestion
import com.example.api.BarcodeScannerHelper
import com.example.api.BarcodeProductInfo
import com.example.api.MealPlanDayDto
import com.example.api.MealPlanResponse
import com.example.data.AppDatabase
import com.example.data.InventoryItem
import com.example.data.InventoryRepository
import com.example.data.WasteActionLog
import com.example.data.ShoppingItem
import com.example.data.MealPlanItem
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume

sealed interface ScanUiState {
    object Idle : ScanUiState
    object Loading : ScanUiState
    data class Success(val result: SmartKitchenResult) : ScanUiState
    data class Error(val message: String) : ScanUiState
}

class InventoryViewModel(
    application: Application,
    private val repository: InventoryRepository
) : AndroidViewModel(application) {

    private val sharedPrefs = application.getSharedPreferences("smart_kitchen_cal_prefs", Context.MODE_PRIVATE)

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

    fun setDailyCalorieGoal(goal: Int) {
        _dailyCalorieGoal.value = goal
        sharedPrefs.edit().putInt("daily_calorie_goal", goal).apply()
    }

    fun resetDailyCalories() {
        _consumedCaloriesToday.value = 0
        _consumedProteinToday.value = 0
        _consumedCarbsToday.value = 0
        _consumedFatToday.value = 0
        sharedPrefs.edit()
            .putInt("consumed_calories_today", 0)
            .putInt("consumed_protein_today", 0)
            .putInt("consumed_carbs_today", 0)
            .putInt("consumed_fat_today", 0)
            .apply()
    }

    fun consumeRecipe(title: String, calories: Int?, protein: String?, carbs: String?, fat: String?) {
        val calVal = calories ?: 300
        val protVal = protein?.filter { it.isDigit() }?.toIntOrNull() ?: 15
        val carbsVal = carbs?.filter { it.isDigit() }?.toIntOrNull() ?: 40
        val fatVal = fat?.filter { it.isDigit() }?.toIntOrNull() ?: 10

        _consumedCaloriesToday.value += calVal
        _consumedProteinToday.value += protVal
        _consumedCarbsToday.value += carbsVal
        _consumedFatToday.value += fatVal

        sharedPrefs.edit()
            .putInt("consumed_calories_today", _consumedCaloriesToday.value)
            .putInt("consumed_protein_today", _consumedProteinToday.value)
            .putInt("consumed_carbs_today", _consumedCarbsToday.value)
            .putInt("consumed_fat_today", _consumedFatToday.value)
            .apply()
            
        // Also log a consumed action log!
        viewModelScope.launch(Dispatchers.IO) {
            val log = WasteActionLog(
                itemName = "Yemek Tarifi: $title",
                category = "Yemek",
                actionType = "CONSUMED",
                pointsGained = 60,
                co2SavedKg = 1.5,
                cost = 0.0
            )
            repository.insertActionLog(log)
            
            // Post our action to global feed
            addOwnFeedEvent("Tarif: $title", 1.5)
        }
    }

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
                    remainingDays < 0 -> {
                        ExpirationWarning(
                            itemId = item.id,
                            itemName = item.name,
                            category = item.category,
                            daysLeft = remainingDays,
                            level = WarningLevel.EXPIRED,
                            message = "Süresi geçmiş! Geri dönüşüme kazandırın veya imha edin."
                        )
                    }
                    remainingDays == 0 -> {
                        ExpirationWarning(
                            itemId = item.id,
                            itemName = item.name,
                            category = item.category,
                            daysLeft = remainingDays,
                            level = WarningLevel.CRITICAL,
                            message = "Bugün son gün! İsrafı önlemek için hemen tüketin."
                        )
                    }
                    remainingDays == 1 -> {
                        ExpirationWarning(
                            itemId = item.id,
                            itemName = item.name,
                            category = item.category,
                            daysLeft = remainingDays,
                            level = WarningLevel.CRITICAL,
                            message = "Yarın son gün! Tüketilmesi son derece önemli."
                        )
                    }
                    remainingDays in 2..3 -> {
                        ExpirationWarning(
                            itemId = item.id,
                            itemName = item.name,
                            category = item.category,
                            daysLeft = remainingDays,
                            level = WarningLevel.WARNING,
                            message = "$remainingDays gün kaldı. Yemek tariflerinde kullanabilirsiniz."
                        )
                    }
                    remainingDays in 4..7 -> {
                        ExpirationWarning(
                            itemId = item.id,
                            itemName = item.name,
                            category = item.category,
                            daysLeft = remainingDays,
                            level = WarningLevel.INFO,
                            message = "$remainingDays gün kaldı. Planlı tüketim önerilir."
                        )
                    }
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

    private val _scanUiState = MutableStateFlow<ScanUiState>(ScanUiState.Idle)
    val scanUiState: StateFlow<ScanUiState> = _scanUiState.asStateFlow()

    // User preference states for recipe personalization
    private val _userDiet = MutableStateFlow("Hepsi")
    val userDiet: StateFlow<String> = _userDiet.asStateFlow()

    private val _userAllergies = MutableStateFlow<Set<String>>(emptySet())
    val userAllergies: StateFlow<Set<String>> = _userAllergies.asStateFlow()

    private val _dislikedIngredients = MutableStateFlow("")
    val dislikedIngredients: StateFlow<String> = _dislikedIngredients.asStateFlow()

    fun updatePreferences(diet: String, allergies: Set<String>, disliked: String) {
        _userDiet.value = diet
        _userAllergies.value = allergies
        _dislikedIngredients.value = disliked
    }

    // Room-backed Shopping List state
    val shoppingItems: StateFlow<List<ShoppingItem>> = repository.shoppingItems
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Legacy simple shopping list state for compatibility
    private val _shoppingList = MutableStateFlow<List<String>>(emptyList())
    val shoppingList: StateFlow<List<String>> = _shoppingList.asStateFlow()

    fun addShoppingItem(
        name: String,
        quantity: String = "1 Adet",
        category: String = "Kiler & Bakliyat",
        estimatedPrice: Double = 0.0,
        addedFromWaste: Boolean = false
    ) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val item = ShoppingItem(
                name = trimmed,
                quantity = quantity.ifBlank { "1 Adet" },
                category = category,
                isChecked = false,
                estimatedPrice = estimatedPrice,
                addedFromWaste = addedFromWaste,
                addedDate = System.currentTimeMillis()
            )
            repository.insertShoppingItem(item)
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

    fun transferCheckedShoppingItemsToInventory() {
        viewModelScope.launch(Dispatchers.IO) {
            val currentList = shoppingItems.value.filter { it.isChecked }
            currentList.forEach { shopItem ->
                val shelfLife = when (shopItem.category) {
                    "Süt Ürünleri" -> 7
                    "Sebze & Meyve" -> 5
                    "Et & Şarküteri", "Et & Tavuk" -> 4
                    "Dondurulmuş" -> 180
                    else -> 60
                }
                val storage = when (shopItem.category) {
                    "Dondurulmuş" -> "Dondurucu"
                    "Süt Ürünleri", "Et & Şarküteri", "Et & Tavuk", "Sebze & Meyve" -> "Buzdolabı"
                    else -> "Kiler"
                }
                val invItem = InventoryItem(
                    name = shopItem.name,
                    quantity = shopItem.quantity,
                    category = shopItem.category,
                    addedDate = System.currentTimeMillis(),
                    expiryDate = System.currentTimeMillis() + (shelfLife * 24L * 3600L * 1000L),
                    storage = storage,
                    price = shopItem.estimatedPrice,
                    remainingPercent = 100
                )
                repository.insertItem(invItem)
                repository.deleteShoppingItem(shopItem)
            }
        }
    }

    fun addToShoppingList(item: String) {
        addShoppingItem(name = item)
    }

    fun removeFromShoppingList(item: String) {
        val found = shoppingItems.value.firstOrNull { it.name.equals(item, ignoreCase = true) }
        if (found != null) {
            deleteShoppingItem(found)
        }
    }

    fun clearShoppingList() {
        clearAllShoppingItems()
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

    private val _timerTotalDuration = MutableStateFlow(0)
    val timerTotalDuration: StateFlow<Int> = _timerTotalDuration.asStateFlow()

    private var timerJob: Job? = null

    fun startCookingMode(recipe: RecipeSuggestion) {
        _activeCookingRecipe.value = recipe
        val rawInstructions = recipe.instructions.orEmpty()
        val parsedSteps = if (rawInstructions.contains("\n")) {
            rawInstructions.split("\n")
                .map { it.trim().trimStart('-', '*', '1', '2', '3', '4', '5', '6', '7', '8', '9', '.', ' ') }
                .filter { it.isNotBlank() }
        } else if (rawInstructions.contains(". ")) {
            rawInstructions.split(". ")
                .map { it.trim() }
                .filter { it.isNotBlank() }
        } else {
            listOf(rawInstructions)
        }
        _cookingSteps.value = if (parsedSteps.isNotEmpty()) parsedSteps else listOf("Tarif adımları hazırlanıyor...")
        _currentCookingStepIndex.value = 0
        resetCookingTimer()

        // Auto-detect minutes in first step or prep time
        extractMinutesAndSetTimer(recipe.prepTime ?: "15 dk")
    }

    fun stopCookingMode() {
        _activeCookingRecipe.value = null
        _cookingSteps.value = emptyList()
        _currentCookingStepIndex.value = 0
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
        _timerTotalDuration.value = seconds
        _cookingTimerSecondsLeft.value = seconds
    }

    fun startCookingTimer() {
        if (_cookingTimerSecondsLeft.value <= 0) return
        _isCookingTimerRunning.value = true
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (_cookingTimerSecondsLeft.value > 0 && _isCookingTimerRunning.value) {
                delay(1000)
                _cookingTimerSecondsLeft.value -= 1
            }
            if (_cookingTimerSecondsLeft.value <= 0) {
                _isCookingTimerRunning.value = false
            }
        }
    }

    fun pauseCookingTimer() {
        _isCookingTimerRunning.value = false
        timerJob?.cancel()
    }

    fun resetCookingTimer() {
        pauseCookingTimer()
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

    fun generateWeeklyMealPlanWithAi(onCompleted: (Boolean) -> Unit = {}) {
        _isMealPlanLoading.value = true
        viewModelScope.launch {
            try {
                val inventoryItems = inventoryItems.value
                val expiringNames = inventoryItems.take(8).joinToString(", ") { "${it.name} (${it.category})" }
                val diet = _userDiet.value
                val allergies = _userAllergies.value.joinToString(", ")
                val disliked = _dislikedIngredients.value

                val prompt = """
                    Sen "Smart Kitchen AI" haftalık yemek planlayıcısısın.
                    Kullanıcının buzdolabındaki malzemeleri ve SKT'si yaklaşan gıdaları israf etmeden tükettirecek 7 günlük (Pazartesi'den Pazar'a) Akşam Yemeği ve Öğle Yemeği planı oluştur.
                    
                    KULLANICI PROFİLİ:
                    - Diyet: $diet
                    - Alerjiler: $allergies
                    - Sevilmeyen Malzemeler: $disliked
                    - ENVANTERDEKİ MALZEMELER: $expiringNames
                    
                    KURALLAR:
                    1. 7 gün için günde 1 veya 2 öğün (toplam 7-10 öğün) üret.
                    2. Özellikle buzdolabındaki malzemeleri öncelikli kullan.
                    3. Her öğün için kısa hazırlık talimatı, kalori ve malzeme listesi ekle.
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
                      "weekly_tip": "Bu hafta dolaptaki sebzeleri ilk 3 günde tüketerek %100 sıfır atık hedefine ulaşabilirsiniz!"
                    }
                """.trimIndent()

                val rawJson = withContext(Dispatchers.IO) {
                    FirebaseAiManager.generateContent(prompt)
                }

                if (rawJson != null) {
                    val cleanJson = rawJson
                        .trim()
                        .replace(Regex("^```[a-zA-Z]*\\n?"), "")
                        .replace(Regex("\\n?```$"), "")
                        .trim()

                    val response = FirebaseAiManager.mealPlanAdapter.fromJson(cleanJson)
                    if (response != null && response.meals.isNotEmpty()) {
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
                        _isMealPlanLoading.value = false
                        onCompleted(true)
                        return@launch
                    }
                }
            } catch (e: Exception) {
                Log.e("SmartKitchenAI", "Error generating meal plan with AI: ${e.message}", e)
            }

            // Fallback default local meal plan if offline
            generateLocalFallbackMealPlan()
            _isMealPlanLoading.value = false
            onCompleted(true)
        }
    }

    private suspend fun generateLocalFallbackMealPlan() {
        val days = listOf("Pazartesi", "Salı", "Çarşamba", "Perşembe", "Cuma", "Cumartesi", "Pazar")
        val sampleMeals = listOf(
            MealPlanItem(dayOfWeek = "Pazartesi", mealType = "Akşam Yemeği", recipeTitle = "Domatesli Fesleğenli Makarna", ingredients = "Makarna, Domates, Zeytinyağı, Peynir", prepTime = "15 dk", calories = 420, instructions = "Makarnayı haşlayın, domates sosuyla soteleyip peynir serpin."),
            MealPlanItem(dayOfWeek = "Salı", mealType = "Akşam Yemeği", recipeTitle = "Sebzeli Tavuk Sote", ingredients = "Tavuk Göğsü, Biber, Soğan, Sarımsak", prepTime = "25 dk", calories = 380, instructions = "Tavukları küp doğrayıp sebzelerle tavada yüksek ateşte soteleyin."),
            MealPlanItem(dayOfWeek = "Çarşamba", mealType = "Akşam Yemeği", recipeTitle = "Kırmızı Mercimek Çorbası & Kruton", ingredients = "Mercimek, Havuç, Bayat Ekmek, Tereyağı", prepTime = "20 dk", calories = 310, instructions = "Mercimekleri kaynatıp blend edin. Bayat ekmekleri fırınlayıp ekleyin."),
            MealPlanItem(dayOfWeek = "Perşembe", mealType = "Akşam Yemeği", recipeTitle = "Fırında Kaşarlı Mantar & Salata", ingredients = "Mantar, Kaşar Peyniri, Yeşillik, Limon", prepTime = "20 dk", calories = 290, instructions = "Mantarların içine kaşar koyup 15 dk fırınlayın."),
            MealPlanItem(dayOfWeek = "Cuma", mealType = "Akşam Yemeği", recipeTitle = "Ev Yapımı Pratik Ton Balıklı Pizza", ingredients = "Lavaş, Ton Balığı, Mısır, Kaşar Peyniri", prepTime = "12 dk", calories = 460, instructions = "Lavaş üzerine malzemeleri dizip tavada peynir eriyene kadar pişirin."),
            MealPlanItem(dayOfWeek = "Cumartesi", mealType = "Öğle Yemeği", recipeTitle = "Menemen & Köy Ekmeği", ingredients = "Yumurta, Domates, Biber, Tereyağı", prepTime = "15 dk", calories = 340, instructions = "Biber ve domatesleri soteleyip yumurtaları kırın."),
            MealPlanItem(dayOfWeek = "Pazar", mealType = "Akşam Yemeği", recipeTitle = "Kiler Güveci (Kalan Sebzelerle)", ingredients = "Patates, Havuç, Bezelye, Domates Salçası", prepTime = "35 dk", calories = 360, instructions = "Tüm kalan sebzeleri tencerede salçalı suyla kısık ateşte pişirin.")
        )
        repository.clearMealPlans()
        repository.insertMealPlans(sampleMeals)
    }

    fun toggleMealPlanCooked(item: MealPlanItem) {
        viewModelScope.launch(Dispatchers.IO) {
            val updated = item.copy(isCooked = !item.isCooked)
            repository.updateMealPlan(updated)
            if (updated.isCooked) {
                // Log action & gain points!
                val log = WasteActionLog(
                    itemName = item.recipeTitle,
                    category = "Yemek Planı",
                    actionType = "CONSUMED",
                    pointsGained = 40,
                    co2SavedKg = 1.5,
                    cost = 60.0
                )
                repository.insertActionLog(log)
            }
        }
    }

    fun clearMealPlans() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.clearMealPlans()
        }
    }

    // ==========================================
    // 📦 BARCODE SCANNER HELPER
    // ==========================================
    fun scanBarcodeBitmap(bitmap: Bitmap, onResult: (BarcodeProductInfo?) -> Unit) {
        viewModelScope.launch {
            val barcodes = BarcodeScannerHelper.scanBarcodeFromBitmap(bitmap)
            val detectedBarcode = barcodes.firstOrNull()
            if (detectedBarcode != null) {
                val info = BarcodeScannerHelper.lookupBarcode(detectedBarcode)
                onResult(info)
            } else {
                onResult(null)
            }
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
    // 📊 FINANCIAL SAVINGS & ECO IMPACT METRICS
    // ==========================================
    data class DetailedEcoFinancialMetrics(
        val totalMoneySavedTl: Double,
        val totalMoneyLostTl: Double,
        val totalCo2SavedKg: Double,
        val totalWaterSavedLiters: Double,
        val totalMealsCooked: Int,
        val totalItemsRescued: Int,
        val sustainabilityScore: Int,
        val monthlySummaryText: String
    )

    fun getDetailedEcoFinancialMetrics(): DetailedEcoFinancialMetrics {
        val logs = actionLogs.value
        val consumedLogs = logs.filter { it.actionType == "CONSUMED" }
        val wastedLogs = logs.filter { it.actionType == "WASTED" }

        val moneySaved = consumedLogs.sumOf { it.cost.coerceAtLeast(35.0) }
        val moneyLost = wastedLogs.sumOf { it.cost.coerceAtLeast(30.0) }
        val co2Saved = consumedLogs.sumOf { it.co2SavedKg }
        val waterSaved = consumedLogs.size * 180.0 // avg 180 Liters of water virtual footprint per meal/item rescued
        val totalMeals = consumedLogs.size
        val totalRescued = consumedLogs.size
        val totalInteractions = (consumedLogs.size + wastedLogs.size).coerceAtLeast(1)
        val score = ((consumedLogs.size.toDouble() / totalInteractions) * 100).toInt().coerceIn(0, 100)

        val summary = """
            🌱 SMART KITCHEN AI - AYLIK MUTFAK & TASARRUF RAPORU
            
            💰 Kurtarılan Bütçe: ₺${String.format(java.util.Locale.US, "%.1f", moneySaved)}
            📉 İsraf Kaybı: ₺${String.format(java.util.Locale.US, "%.1f", moneyLost)}
            🌍 Önlenen Karbon Salınımı: ${String.format(java.util.Locale.US, "%.1f", co2Saved)} kg CO₂
            💧 Kurtarılan Su Ayak İzi: ${String.format(java.util.Locale.US, "%.0f", waterSaved)} Litre
            🍳 Hazırlanan Sıfır Atık Yemek: $totalMeals Porsiyon
            🏆 Sürdürülebilirlik Başarı Skoru: %$score
            
            Smart Kitchen AI ile mutfakta israfı sıfırladınız! 🥦✨
        """.trimIndent()

        return DetailedEcoFinancialMetrics(
            totalMoneySavedTl = moneySaved,
            totalMoneyLostTl = moneyLost,
            totalCo2SavedKg = co2Saved,
            totalWaterSavedLiters = waterSaved,
            totalMealsCooked = totalMeals,
            totalItemsRescued = totalRescued,
            sustainabilityScore = score,
            monthlySummaryText = summary
        )
    }

    // User nickname state and validation
    private val defaultTakenUsernames = setOf(
        "atıksavaşçısı_aslı", "şef_melih_06", "sıfıratıkesra", "ecochef_ebru",
        "zerowaste_kaan", "mutfakcanavarı", "mutfakkahramanı", "admin", "administrator",
        "sistem", "system", "root", "support", "destek", "null", "anonymous", "misafir"
    )

    private val takenUsernames: MutableSet<String> = (
        sharedPrefs.getStringSet("registered_usernames_set", emptySet()) ?: emptySet()
    ).map { it.lowercase(java.util.Locale.forLanguageTag("tr")) }.toMutableSet().apply {
        addAll(defaultTakenUsernames)
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

    fun validateUsername(input: String): String? {
        val trimmed = input.trim()
        if (input.isEmpty()) return null
        
        if (input.startsWith(" ") || input.endsWith(" ")) {
            return "Kullanıcı adı başında veya sonunda boşluk olamaz."
        }
        if (trimmed.length < 3) {
            return "Kullanıcı adı en az 3 karakter olmalıdır."
        }
        if (trimmed.length > 20) {
            return "Kullanıcı adı en fazla 20 karakter olabilir."
        }
        
        val validRegex = Regex("^[a-zA-Z0-9çğışöüÇĞİŞÖÜ_-]+( [a-zA-Z0-9çğışöüÇĞİŞÖÜ_-]+)*$")
        if (!validRegex.matches(trimmed)) {
            return "Özel semboller (@, #, $, %, vb.) kullanılamaz. Sadece harf, rakam ve tire/alt çizgi girebilirsiniz."
        }

        val lowerCaseInput = trimmed.lowercase(java.util.Locale.forLanguageTag("tr"))
        if (takenUsernames.contains(lowerCaseInput)) {
            return "❌ Bu kullanıcı adı zaten sistemde başka bir kullanıcı tarafından alınmış! Lütfen benzersiz bir ad seçin."
        }

        val forbiddenWords = listOf("küfür", "aptal", "mal", "salak", "admin", "hacker", "system")
        if (forbiddenWords.any { lowerCaseInput.contains(it) && !defaultTakenUsernames.contains(lowerCaseInput) }) {
            return "Lütfen topluluk kurallarına uygun, saygılı bir kullanıcı adı seçin."
        }

        return null 
    }

    fun updateNickname(name: String, avatar: String = "🥦"): Boolean {
        val validationError = validateUsername(name)
        if (validationError != null) return false

        val cleanName = name.trim()
        _userNickname.value = cleanName
        _userAvatar.value = avatar
        _isRegistered.value = true

        val savedSet = (sharedPrefs.getStringSet("registered_usernames_set", emptySet()) ?: emptySet()).toMutableSet()
        savedSet.add(cleanName.lowercase(java.util.Locale.forLanguageTag("tr")))

        sharedPrefs.edit()
            .putString("user_nickname", cleanName)
            .putString("user_avatar", avatar)
            .putBoolean("is_registered", true)
            .putStringSet("registered_usernames_set", savedSet)
            .apply()

        takenUsernames.add(cleanName.lowercase(java.util.Locale.forLanguageTag("tr")))
        return true
    }

    fun logoutOrResetProfile() {
        _userNickname.value = ""
        _isRegistered.value = false
        sharedPrefs.edit()
            .remove("user_nickname")
            .remove("user_avatar")
            .putBoolean("is_registered", false)
            .apply()
    }

    private val _globalCo2Saved = MutableStateFlow(sharedPrefs.getFloat("global_co2_saved", 124.5f))
    val globalCo2Saved: StateFlow<Float> = _globalCo2Saved.asStateFlow()

    private val _globalWaterSaved = MutableStateFlow(sharedPrefs.getFloat("global_water_saved", 43580.0f))
    val globalWaterSaved: StateFlow<Float> = _globalWaterSaved.asStateFlow()

    private val _globalFeedEvents = MutableStateFlow<List<String>>(emptyList())
    val globalFeedEvents: StateFlow<List<String>> = _globalFeedEvents.asStateFlow()

    var hasShownStartupAd = false

    fun addOwnFeedEvent(itemName: String, co2Saved: Double) {
        val nick = _userNickname.value.ifEmpty { "Kayıtlı Profil" }
        val waterSaved = co2Saved * 350.0
        val event = "$nick, $itemName gıdasını tüketerek/kurtararak %.1f kg CO2 ve %.0f L Su tasarrufu sağladı! 🎉".format(java.util.Locale.US, co2Saved, waterSaved)
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

    private val microTipsList = listOf(
        "Yeşillikleri kağıt havluya sararak buzdolabında saklamak ömürlerini 2 katına çıkarır.",
        "Muzların sapını streç film ile sarmak, olgunlaşma hızını yavaşlatarak bozulmalarını önler.",
        "Patates ve soğanları asla aynı sepet içinde saklamayın; yaydıkları gazlar birbirlerinin çürümesini hızlandırır.",
        "Bayatlayan ekmekleri dilimleyip fırınlayarak kıtır krutonlar yapabilir ve çorbalarda kullanabilirsiniz.",
        "Artan sebze kabuklarını ve saplarını dondurucuda dondurup kaynatarak nefis sebze suları hazırlayın.",
        "Yumurtanın tazeliğini suya koyarak anlayın; dibe çöküyorsa taze, suyun üstüne çıkıyorsa bayattır.",
        "Elmaların yanına konan patatesler, patateslerin filizlenmesini önleyen doğal bir gaz salgılar.",
        "Salçanın küflenmesini önlemek için üzerine ince bir tabaka zeytinyağı gezdirebilirsiniz.",
        "Dondurucuya atacağınız etleri porsiyonlara bölerek saklayın; çözülen et asla tekrar dondurulmamalıdır.",
        "Küflenmeye başlayan peynir sert ise, küflü kısmı kesip kalanını güvenle tüketebilirsiniz.",
        "Sarımsakları karanlık ve havadar bir yerde saklamak filizlenmelerini ve yumuşamalarını engeller.",
        "Sütü buzdolabının kapağında değil, daha soğuk olan orta veya arka raflarında saklayın.",
        "Zencefili soyup dilimledikten sonra dondurucuda saklayarak aylar boyu taze tutabilirsiniz.",
        "Domatesleri buzdolabına koymak aromalarını kaybettirir; oda sıcaklığında saklamak daha iyidir.",
        "Limonun yarısını saklayacaksanız, kesik yüzeyine azıcık tuz sürerek kurumasını önleyebilirsiniz.",
        "Yoğurt kabını buzdolabında ters koymak (sıkı kapalıyken), yoğurdun daha geç ekşimesini sağlar.",
        "Un ve pirincin böceklenmesini önlemek için kavanozlarına 1 adet kuru defne yaprağı atabilirsiniz.",
        "Çilek gibi meyveleri sirkeli suda yıkayıp iyice kuruladıktan sonra saklamak ömürlerini uzatır.",
        "Kahve telvesini kurutup kiler veya buzdolabına koymak, istenmeyen kötü kokuları mükemmel çeker.",
        "Kuruyan peynirleri azıcık sütte bekleterek tekrar yumuşak ve lezzetli hale getirebilirsiniz.",
        "Makarnayı süzdükten sonra nişastalı haşlama suyunu dondurup soslarınızı bağlamak için bulyon gibi kullanın.",
        "Kalan pırasa, yeşil soğan veya kereviz saplarını bir bardak suya koyarak evde kendi kendilerine yeniden filizlenmesini sağlayabilirsiniz.",
        "Eski baharatların kokusunu tazelemek için tavada yağsız şekilde 30 saniye hafifçe kavurabilirsiniz.",
        "Kabak ve salatalıkları poşetsiz şekilde sebzelik çekmecesinde birbirine değmeden saklamak nemlenip çürümelerini engeller.",
        "Pirinç pilavının tane tane olması ve kararmaması için pişerken içine 2-3 damla limon suyu sıkabilirsiniz.",
        "Taze otları zeytinyağı ile karıştırıp buz kalıplarında dondurarak yemeklerinize doğrudan katabilirsiniz.",
        "Kek ve kurabiyelerin bayatlamasını önlemek için saklama kabına çeyrek dilim elma koyabilirsiniz.",
        "Kristalleşen balları kavanozuyla 40 derece sıcaklıktaki benmari suyunda bekleterek akışkan haline döndürebilirsiniz.",
        "Karnabahar haşlarken kokuyu önlemek için tencereye bir parça ekmek içi veya yarım elma atabilirsiniz.",
        "Et sularını pet bardaklara dondurup porsiyonluk et bulyon şeklinde kullanabilirsiniz.",
        "Yeşil çay yapraklarını kuruttuktan sonra ayakkabılıkta koku giderici olarak değerlendirebilirsiniz.",
        "Çorbanız fazla tuzlu olduysa içine soyulmuş 1 adet bütün patates atıp kaynatın; patates tuzu çekecektir.",
        "Kalan şarap veya sirke kalıntılarını buz kaplarında dondurarak soslarınıza gurme ekşilik katabilirsiniz.",
        "Mantar yıkanınca sünger gibi su çeker ve lezzeti bozulur; nemli bir havlu kağıt ile silerek temizleyin.",
        "Avokadoyu hızlıca olgunlaştırmak için bir kese kağıdına yanına elma veya muz koyarak sarın.",
        "Sarımsak soymayı kolaylaştırmak için dişleri 10 saniye mikrodalgada ısıtın veya bıçağın eniyle hafifçe ezin."
    )

    private val _currentMicroTip = MutableStateFlow(
        "Yeşillikleri kağıt havluya sararak buzdolabında saklamak ömürlerini 2 katına çıkarır."
    )
    val currentMicroTip: StateFlow<String> = _currentMicroTip.asStateFlow()

    init {
        rotateMicroTip()
        viewModelScope.launch {
            activeItems.collect { items ->
                if (items.isNotEmpty()) {
                    checkAndTriggerExpirations(items)
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
            if (remainingDays in listOf(1, 3, 7)) {
                val notifyKey = "${item.id}_${remainingDays}"
                if (!updatedNotifiedSet.contains(notifyKey)) {
                    val title = "Tüketim Uyarısı: ${item.name} ⏳"
                    val message = when (remainingDays) {
                        7 -> "🌱 ${item.name} için 1 hafta kaldı. Tariflerde değerlendirmek için harika bir zaman!"
                        3 -> "⚠️ ${item.name} için son 3 gün! Bozulmadan önce tüketmeyi unutmayın."
                        1 -> "🚨 Dikkat! ${item.name} yarın son gününe giriyor. İsrafı önlemek için hemen pişirin!"
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
        val randomTip = microTipsList.random()
        _currentMicroTip.value = randomTip
    }

    fun isApiKeyConfigured(): Boolean {
        return true
    }

    fun isApiKeyValidPrefix(): Boolean {
        return true
    }

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
            val expiryDate = currentTime + (shelfLifeDays.toLong() * 24 * 60 * 60 * 1000)
            val newItem = InventoryItem(
                name = name,
                quantity = quantity,
                category = category,
                addedDate = currentTime,
                expiryDate = expiryDate,
                storage = storage,
                remainingPercent = remainingPercent,
                price = price
            )
            repository.insertItem(newItem)
        }
    }

    fun addItemFromShoppingList(name: String) {
        val category = when (name.lowercase()) {
            "süt", "peynir", "yoğurt", "tereyağı", "yogurt" -> "Süt Ürünleri"
            "elma", "armut", "muz", "domates", "biber", "marul", "salatalık", "havuç", "patates", "soğan", "meyve", "sebze" -> "Sebze & Meyve"
            "ekmek", "makarna", "pirinç", "un", "şeker", "kraker", "bisküvi" -> "Kiler / Kuru Gıda"
            "tavuk", "kıyma", "et", "balık", "tavuk eti", "kırmızı et" -> "Et & Tavuk"
            else -> "Diğer"
        }
        val shelfLife = when (category) {
            "Süt Ürünleri" -> 7
            "Sebze & Meyve" -> 5
            "Et & Tavuk" -> 3
            "Kiler / Kuru Gıda" -> 30
            else -> 10
        }
        val storage = when (category) {
            "Kiler / Kuru Gıda" -> "Kiler"
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
                val expiryDate = currentTime + (shelfLife.toLong() * 24 * 60 * 60 * 1000)
                
                val storage = when (it.storage.trim().lowercase()) {
                    "buzdolabi", "buzdolabı" -> "Buzdolabı"
                    "kiler" -> "Kiler"
                    "dondurucu" -> "Dondurucu"
                    else -> "Buzdolabı"
                }

                val category = guessCategory(it.name)

                InventoryItem(
                    name = it.name,
                    quantity = it.quantity,
                    category = category,
                    addedDate = currentTime,
                    expiryDate = expiryDate,
                    storage = storage,
                    price = it.price ?: 0.0
                )
            }
            repository.insertItems(items)
        }
    }

    private fun guessCategory(name: String): String {
        val n = name.lowercase()
        return when {
            n.contains("süt") || n.contains("peynir") || n.contains("yoğurt") || n.contains("tereyağ") || n.contains("krema") || n.contains("ayran") -> "Süt Ürünleri"
            n.contains("tavuk") || n.contains("et") || n.contains("kıyma") || n.contains("bonfile") || n.contains("köfte") || n.contains("sucuk") || n.contains("sosis") -> "Et & Tavuk"
            n.contains("domates") || n.contains("biber") || n.contains("salatalık") || n.contains("marul") || n.contains("soğan") || n.contains("patates") || n.contains("elma") || n.contains("muz") || n.contains("limon") || n.contains("çilek") || n.contains("sebze") || n.contains("meyve") -> "Sebze & Meyve"
            n.contains("pirinç") || n.contains("makarna") || n.contains("un") || n.contains("şeker") || n.contains("mercimek") || n.contains("bulgur") || n.contains("yağ") || n.contains("bakliyat") -> "Kiler / Kuru Gıda"
            n.contains("pizza") || n.contains("milföy") || n.contains("bezelye dondurulmuş") || n.contains("dondurma") -> "Dondurulmuş"
            else -> "Diğer"
        }
    }

    fun clearHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.clearLogs()
        }
    }

    fun setScanUiStateToIdle() {
        _scanUiState.value = ScanUiState.Idle
    }

    private suspend fun extractTextFromBitmap(bitmap: Bitmap): String = withContext(Dispatchers.Default) {
        suspendCancellableCoroutine { continuation ->
            try {
                val image = InputImage.fromBitmap(bitmap, 0)
                val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                recognizer.process(image)
                    .addOnSuccessListener { visionText ->
                        continuation.resume(visionText.text ?: "")
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

    private fun parseTextToDetectedItems(textInput: String, scanMode: String): List<DetectedItem> {
        if (textInput.isBlank()) return emptyList()

        val lines = textInput.split("\n", "\r\n").map { it.trim() }.filter { it.isNotEmpty() }
        val detected = mutableListOf<DetectedItem>()

        val junkKeywords = setOf(
            "migros", "bim", "carrefour", "a101", "şok", "sok", "file", "metro", "tedi", "hakmar",
            "ticaret", "a.s.", "a.ş.", "tarih", "saat", "fiş", "fis", "no:", "no :", "z no", "ekü",
            "teşekkürler", "tesekkurler", "kdv", "topkdv", "toplam", "tutar", "nakit", "kredi", "kart",
            "pos", "onay", "matrah", "mersis", "vkn", "tckn", "kasiyer", "e-arşiv", "e-arsiv",
            "seçilen", "kamera", "fotoğrafı", "okundu", "lütfen", "analiz", "smart kitchen", "yükleniyor",
            "para ustu", "para üstü", "bilgi fişi", "mali değeri yoktur", "fatura", "ara toplam", "gunun"
        )

        for (line in lines) {
            val lower = line.lowercase(java.util.Locale.forLanguageTag("tr"))

            // Skip lines that are purely junk receipt metadata
            val isJunkHeader = junkKeywords.any { lower.contains(it) } &&
                    !lower.contains("kg") && !lower.contains("adet") && !lower.contains("500g") &&
                    !lower.contains("800g") && !lower.contains("1l") && !lower.contains("1 l") &&
                    !lower.contains("litr") && !lower.contains("gr") && !lower.contains("gram") &&
                    !lower.contains("paket") && !lower.contains("süt") && !lower.contains("peynir") &&
                    !lower.contains("tavuk") && !lower.contains("et") && !lower.contains("domates")

            if (isJunkHeader) continue

            var cleanLine = if (line.contains("->")) {
                line.substringBefore("->").trim()
            } else {
                line.trim()
            }

            // Remove leading index numbers like "01 ", "1. ", "* "
            cleanLine = cleanLine.replace(Regex("""^\*?\s*(\d{1,3}[\.\)\s]+)"""), "").trim()

            if (cleanLine.length < 2) continue

            // Try to extract price from the line (e.g., 45.50, 45,50, *120.00, %1 35.00)
            var extractedPrice = 0.0
            val priceRegex = Regex("""(?i)(?:[\*\%]\d+)?\s*(\d+[\.,]\d{2})(?:\s*TL|\s*₺|\s*\%|\s*$)""")
            val priceMatch = priceRegex.find(cleanLine)
            if (priceMatch != null) {
                val rawPriceStr = priceMatch.groupValues[1].replace(",", ".")
                extractedPrice = rawPriceStr.toDoubleOrNull() ?: 0.0
                cleanLine = cleanLine.substring(0, priceMatch.range.first).trim()
            }

            // Clean trailing characters
            cleanLine = cleanLine.replace(Regex("""[\*\%TL₺#]+$"""), "").trim()

            if (cleanLine.length < 2) continue

            // Parse Quantity and Unit
            val regexWithQty = Regex("""^(?i)(\d+[\.,]?\d*)\s*(kg|g|gr|gram|l|lt|litre|ml|adet|ad|paket|pk|koli|kutu|demet|diş|şişe|bağ)?\s*([a-zA-ZçğışöüÇĞİŞÖÜ\s\d'-]+)$""")
            val match = regexWithQty.find(cleanLine)

            var itemName: String
            var qtyStr: String

            if (match != null) {
                val amount = match.groupValues[1]
                val unitRaw = match.groupValues[2].ifEmpty { "Adet" }.lowercase(java.util.Locale.forLanguageTag("tr"))
                val unit = when {
                    unitRaw.contains("kg") -> "kg"
                    unitRaw.contains("gr") || unitRaw.contains("gram") || unitRaw.contains("g") -> "g"
                    unitRaw.contains("l") || unitRaw.contains("lt") || unitRaw.contains("litre") -> "Litre"
                    unitRaw.contains("ml") -> "ml"
                    unitRaw.contains("pk") || unitRaw.contains("paket") -> "Paket"
                    unitRaw.contains("kutu") -> "Kutu"
                    else -> "Adet"
                }
                val rawName = match.groupValues[3].trim()
                qtyStr = "$amount $unit"
                itemName = rawName
            } else {
                qtyStr = "1 Adet"
                itemName = cleanLine
            }

            // Clean internal quantity tokens (e.g., 500G, 1L, 15LI) from item name
            val cleanItemName = itemName
                .replace(Regex("""(?i)\b\d+(g|gr|gram|kg|l|lt|ml|li|lı|lu|lü|adet|pk)\b"""), "")
                .replace(Regex("""\s+"""), " ")
                .trim()

            val finalItemName = if (cleanItemName.length >= 2) cleanItemName else itemName

            val formattedName = finalItemName.lowercase(java.util.Locale.forLanguageTag("tr"))
                .split(" ")
                .filter { it.isNotBlank() && it.length > 1 }
                .joinToString(" ") { word ->
                    word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.forLanguageTag("tr")) else it.toString() }
                }

            if (formattedName.length < 2) continue

            val n = formattedName.lowercase(java.util.Locale.forLanguageTag("tr"))

            val storage = when {
                scanMode == "FREEZER" -> "Dondurucu"
                scanMode == "PANTRY" -> "Kiler"
                n.contains("dondurulmuş") || n.contains("pizza") || n.contains("buz") || n.contains("milföy") || n.contains("nugget") -> "Dondurucu"
                n.contains("pirinç") || n.contains("makarna") || n.contains("un") || n.contains("şeker") ||
                        n.contains("mercimek") || n.contains("bulgur") || n.contains("yağ") || n.contains("konserve") ||
                        n.contains("salça") || n.contains("kiler") || n.contains("baharat") || n.contains("bisküvi") ||
                        n.contains("çay") || n.contains("kahve") || n.contains("tuz") -> "Kiler"
                else -> "Buzdolabı"
            }

            val shelfLife = when {
                storage == "Dondurucu" -> 90
                storage == "Kiler" -> 180
                n.contains("süt") || n.contains("balık") -> 5
                n.contains("tavuk") || n.contains("piliç") || n.contains("et") || n.contains("kıyma") -> 3
                n.contains("domates") || n.contains("salatalık") || n.contains("marul") || n.contains("çilek") || n.contains("yeşillik") -> 6
                n.contains("yumurta") || n.contains("peynir") || n.contains("yoğurt") || n.contains("tereyağ") || n.contains("kaşar") -> 14
                n.contains("elma") || n.contains("portakal") || n.contains("patates") || n.contains("soğan") || n.contains("havuç") -> 20
                else -> 7
            }

            val finalPrice = if (extractedPrice > 0.0) {
                extractedPrice
            } else {
                when {
                    n.contains("et") || n.contains("kıyma") || n.contains("bonfile") || n.contains("tereyağ") -> 220.0
                    n.contains("piliç") || n.contains("tavuk") || n.contains("peynir") || n.contains("kaşar") -> 110.0
                    n.contains("süt") || n.contains("yoğurt") || n.contains("yumurta") -> 45.0
                    n.contains("domates") || n.contains("salatalık") || n.contains("elma") || n.contains("muz") -> 35.0
                    n.contains("makarna") || n.contains("un") || n.contains("şeker") || n.contains("pirinç") -> 28.0
                    else -> 40.0
                }
            }

            if (detected.none { it.name.equals(formattedName, ignoreCase = true) }) {
                detected.add(DetectedItem(formattedName, qtyStr, shelfLife, storage, finalPrice))
            }
        }

        return detected
    }

    private fun generateLocalSmartResult(textInput: String, scanMode: String = "RECEIPT", isInventoryBased: Boolean = false): SmartKitchenResult {
        val diet = _userDiet.value
        val isVegan = diet.equals("Vegan", ignoreCase = true)
        val isVegetarian = diet.equals("Vejetaryen", ignoreCase = true)
        val isGlutenFree = diet.equals("Glütensiz", ignoreCase = true)
        val allergies = _userAllergies.value
        val disliked = _dislikedIngredients.value.lowercase(java.util.Locale.forLanguageTag("tr"))

        val parsedFromInput = parseTextToDetectedItems(textInput, scanMode)

        val detectedList = if (isInventoryBased) {
            emptyList()
        } else {
            parsedFromInput
        }

        val availableItemNames = if (isInventoryBased) {
            activeItems.value.map { it.name }
        } else {
            detectedList.map { it.name }
        }

        val recipeSuggestionsList = if (scanMode == "RECEIPT" && !isInventoryBased) {
            emptyList()
        } else {
            val suggestions = mutableListOf<RecipeSuggestion>()
            val namesLower = availableItemNames.map { it.lowercase(java.util.Locale.forLanguageTag("tr")) }

            val hasChicken = namesLower.any { it.contains("tavuk") || it.contains("piliç") }
            val hasMeat = namesLower.any { it.contains("et") || it.contains("kıyma") || it.contains("köfte") }
            val hasEgg = namesLower.any { it.contains("yumurta") }
            val hasCheese = namesLower.any { it.contains("peynir") || it.contains("kaşar") || it.contains("lor") }
            val hasMilk = namesLower.any { it.contains("süt") || it.contains("yoğurt") || it.contains("krema") }
            val hasPasta = namesLower.any { it.contains("makarna") || it.contains("pirinç") || it.contains("bulgur") }
            val hasTomato = namesLower.any { it.contains("domates") || it.contains("salça") || it.contains("sos") }
            val hasVeggie = namesLower.any { it.contains("biber") || it.contains("patates") || it.contains("soğan") || it.contains("havuç") || it.contains("kabak") || it.contains("patlıcan") || it.contains("mantar") }

            // Recipe 1
            if ((hasChicken || hasMeat) && !isVegan && !isVegetarian) {
                val meatName = if (hasChicken) "Tavuk" else "Kıyma/Et"
                suggestions.add(
                    RecipeSuggestion(
                        title = "Pratik Sebzeli $meatName Sote & Pilav",
                        usedExpiringItems = availableItemNames.filter { it.contains("tavuk", ignoreCase = true) || it.contains("et", ignoreCase = true) || it.contains("biber", ignoreCase = true) || it.contains("domates", ignoreCase = true) }.ifEmpty { listOf(meatName, "Sebzeler") },
                        prepTime = "20 dk",
                        instructions = "1. $meatName parçalarını ve elinizdeki sebzeleri kuşbaşı doğrayın.\n2. Tavada zeytinyağı ile etleri soteleyin, ardından sebzeleri ekleyip baharatlarla tatlandırın.\n3. Yanında pirinç veya bulgur pilavı ile servis edin.",
                        calories = 440,
                        protein = "34g",
                        carbs = "38g",
                        fat = "14g",
                        calorieAnalysis = "Yüksek proteinli ve dengeli karbonhidrat içeriğiyle tok tutucu, kas onarımını destekleyen besleyici bir öğün."
                    )
                )
            } else if (hasEgg && !isVegan) {
                suggestions.add(
                    RecipeSuggestion(
                        title = "Fırında Peynirli & Sebzeli Mutfak Omleti",
                        usedExpiringItems = availableItemNames.filter { it.contains("yumurta", ignoreCase = true) || it.contains("peynir", ignoreCase = true) || it.contains("domates", ignoreCase = true) }.ifEmpty { listOf("Yumurta", "Peynir", "Sebzeler") },
                        prepTime = "12 dk",
                        instructions = "1. Yumurtaları bir kasede çırpın, içine ufalanmış peynir ve doğranmış sebzeleri ekleyin.\n2. Isınmış tavaya döküp kısık ateşte kapağı kapalı olarak pişirin.\n3. Sıcak servis yapın.",
                        calories = 320,
                        protein = "22g",
                        carbs = "8g",
                        fat = "18g",
                        calorieAnalysis = "Düşük karbonhidratlı, zengin kaliteli protein ve sağlıklı yağlar içeren mükemmel bir kurtarma öğünü."
                    )
                )
            } else {
                suggestions.add(
                    RecipeSuggestion(
                        title = "Zengin Karışık Sebze Sote & Kinoa Tabağı",
                        usedExpiringItems = availableItemNames.take(4).ifEmpty { listOf("Domates", "Biber", "Soğan", "Zeytinyağı") },
                        prepTime = "15 dk",
                        instructions = "1. Mevcut tüm sebzeleri jülyen doğrayın.\n2. Tavada zeytinyağı, sarımsak ve kekik ile soteleyin.\n3. Sıcak servis edin.",
                        calories = 290,
                        protein = "9g",
                        carbs = "36g",
                        fat = "11g",
                        calorieAnalysis = "Antioksidan deposu, yüksek lifli ve sindirimi son derece kolaylaştırıcı hafif bir vegan/vejetaryen öğün."
                    )
                )
            }

            // Recipe 2
            if (hasPasta || hasCheese || hasMilk) {
                suggestions.add(
                    RecipeSuggestion(
                        title = "Fırında Kremalı & Kaşarlı Fırın Makarna",
                        usedExpiringItems = availableItemNames.filter { it.contains("makarna", ignoreCase = true) || it.contains("süt", ignoreCase = true) || it.contains("peynir", ignoreCase = true) || it.contains("kaşar", ignoreCase = true) }.ifEmpty { listOf("Makarna", "Süt/Peynir") },
                        prepTime = "25 dk",
                        instructions = "1. Makarnayı haşlayıp süzün.\n2. Süt, tereyağı ve rendelenmiş peynir ile harmanlayıp fırın kabına dökün.\n3. 180 derece fırında üzeri kızarana kadar 15 dakika pişirin.",
                        calories = 490,
                        protein = "19g",
                        carbs = "62g",
                        fat = "16g",
                        calorieAnalysis = "Enerji verici kompleks karbonhidratlar ve kalsiyum bakımından zengin, doyurucu bir lezzet."
                    )
                )
            } else {
                suggestions.add(
                    RecipeSuggestion(
                        title = "İsraf Önleyici Şifalı Mutfak Çorbası",
                        usedExpiringItems = availableItemNames.take(4).ifEmpty { listOf("Havuç", "Patates", "Soğan", "Mercimek") },
                        prepTime = "18 dk",
                        instructions = "1. Yumuşayan sebzeleri tencereye alın, zeytinyağında 3-4 dakika kavurun.\n2. Sıcak su ve baharatları ekleyip 12 dakika kaynatın.\n3. Blenderdan pürüzsüz kıvama gelene kadar geçirin.",
                        calories = 210,
                        protein = "8g",
                        carbs = "32g",
                        fat = "5g",
                        calorieAnalysis = "Düşük kalorili, bağışıklık sistemini destekleyen ve sebze israfını %100 önleyen şifalı bir çorba."
                    )
                )
            }

            suggestions
        }

        return SmartKitchenResult(
            detectedItems = detectedList,
            recipeSuggestions = recipeSuggestionsList,
            sustainabilityImpact = "Envanterinizdeki ürünler başarıyla değerlendirildi ve gıda israfı önlendi! 🌱",
            microTip = "Yeşillikleri kağıt havluya sararak buzdolabında saklamak tazeliğini 2 kat uzatır."
        )
    }

    fun scanReceiptTextOrImage(textInput: String, bitmap: Bitmap? = null, scanMode: String = "RECEIPT") {
        _scanUiState.value = ScanUiState.Loading

        viewModelScope.launch {
            var success = false
            var lastApiErrorDetail: String? = null

            // 1. If bitmap is available, run on-device ML Kit OCR to extract text with 100% reliability
            var effectiveOcrText = ""
            if (bitmap != null) {
                try {
                    effectiveOcrText = extractTextFromBitmap(bitmap)
                    Log.d("SmartKitchenAI", "ML Kit Extracted OCR text: $effectiveOcrText")
                } catch (e: Exception) {
                    Log.e("SmartKitchenAI", "OCR Extraction failed: ${e.message}", e)
                }
            }

            val combinedTextInput = buildString {
                if (textInput.isNotBlank() && !textInput.contains("fotoğrafı") && !textInput.contains("okundu") && !textInput.contains("Lütfen")) {
                    appendLine(textInput)
                }
                if (effectiveOcrText.isNotBlank()) {
                    appendLine(effectiveOcrText)
                }
            }.trim()

            val diet = _userDiet.value
            val allergies = _userAllergies.value.joinToString(", ")
            val disliked = _dislikedIngredients.value

            val isReceiptMode = scanMode == "RECEIPT"

            val promptText = if (isReceiptMode) {
                """
                    Sen hassas bir market alışveriş fişi OCR okuyucususun.
                    Görseldeki veya aşağıdaki fiş metnindeki gıda ürünlerini, miktarlarını, saklama alanlarını ve fiyatlarını tespit et.
                    
                    KURALLAR:
                    1. Fişte yer alan ürün adını (name), miktarını (quantity), birim/toplam fiyatını (price) çıkar.
                    2. Fiyat net okunamazsa 0.0 olarak ata.
                    3. 'recipe_suggestions' dizisini boş ([]) bırak.
                    4. 'storage' alanını gıdanın türüne göre 'Buzdolabı', 'Kiler' veya 'Dondurucu' yap.
                    5. 'estimated_shelf_life_days' için tahmini tüketim gününü yaz (örn: süt için 5, et için 3, bakliyat için 180).
                    
                    Yanıtı KESİNLİKLE JSON formatında döndür:
                    {
                      "detected_items": [
                        {"name": "Süt", "quantity": "1 Litre", "estimated_shelf_life_days": 5, "storage": "Buzdolabı", "price": 35.0}
                      ],
                      "recipe_suggestions": [],
                      "sustainability_impact": "Fiş başarıyla okundu.",
                      "micro_tip": "Gıdaları uygun sıcaklıkta saklayarak israfı önleyin."
                    }
                    
                    Fiş Metni: $combinedTextInput
                """.trimIndent()
            } else {
                val actionDesc = when (scanMode) {
                    "PANTRY" -> "Kiler görselindeki kuru gıdaları ve bakliyatları tespit et. Saklama yerini 'Kiler' yap."
                    "FREEZER" -> "Derin Dondurucu görselindeki dondurulmuş gıdaları tespit et. Saklama yerini 'Dondurucu' yap."
                    else -> "Görseldeki gıda ürünlerini tespit et."
                }
                """
                    Sen "Smart Kitchen AI" mutfak asistanısın.
                    $actionDesc
                    
                    KULLANICI BİLGİSİ: Diyet: $diet | Alerjiler: $allergies | Sevilmeyen: $disliked
                    
                    Tespit edilen gıdalarla 1 adet pratik yemek tarifi oluştur ve JSON formatında döndür:
                    {
                      "detected_items": [
                        {"name": "Ürün Adı", "quantity": "1 Adet", "estimated_shelf_life_days": 5, "storage": "Buzdolabı", "price": 50.0}
                      ],
                      "recipe_suggestions": [
                        {
                          "title": "Tarif Adı",
                          "used_expiring_items": ["Ürün Adı"],
                          "prep_time": "15 dk",
                          "instructions": "Hazırlanış adımları...",
                          "calories": 350,
                          "protein": "20g",
                          "carbs": "40g",
                          "fat": "10g",
                          "calorie_analysis": "Dengeli öğün."
                        }
                      ],
                      "sustainability_impact": "İsraf önlendi.",
                      "micro_tip": "Saklama tavsiyesi."
                    }
                    
                    Ek Metin: $combinedTextInput
                """.trimIndent()
            }

            try {
                val rawJson = withContext(Dispatchers.IO) {
                    FirebaseAiManager.analyzeReceipt(promptText, bitmap)
                }

                if (rawJson != null) {
                    val cleanJson = rawJson
                        .trim()
                        .replace(Regex("^```[a-zA-Z]*\\n?"), "")
                        .replace(Regex("\\n?```$"), "")
                        .trim()

                    val parsedResult = withContext(Dispatchers.Default) {
                        try {
                            FirebaseAiManager.resultAdapter.fromJson(cleanJson)
                        } catch (jsonEx: Exception) {
                            Log.e("SmartKitchenAI", "JSON Parse Exception: ${jsonEx.message}", jsonEx)
                            null
                        }
                    }

                    if (parsedResult != null) {
                        val finalItems = if (!parsedResult.detectedItems.isNullOrEmpty()) {
                            parsedResult.detectedItems
                        } else {
                            parseTextToDetectedItems(combinedTextInput, scanMode)
                        }

                        if (finalItems.isNotEmpty()) {
                            val finalResult = parsedResult.copy(detectedItems = finalItems)
                            _scanUiState.value = ScanUiState.Success(finalResult)
                            finalResult.microTip?.let {
                                if (it.isNotEmpty()) {
                                    _currentMicroTip.value = it
                                }
                            }
                            success = true
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("SmartKitchenAI", "Error calling Firebase Vertex AI: ${e.message}", e)
                lastApiErrorDetail = e.localizedMessage
            }

            // 2. Guaranteed On-Device Fallback
            if (!success) {
                val inputToParse = if (combinedTextInput.isNotBlank()) combinedTextInput else textInput
                val fallbackResult = generateLocalSmartResult(inputToParse, scanMode)

                if (fallbackResult.detectedItems.isNullOrEmpty()) {
                    val userFriendlyError = when {
                        lastApiErrorDetail?.contains("404") == true -> "Firebase Vertex AI modeline erişilemedi."
                        lastApiErrorDetail?.contains("403") == true || lastApiErrorDetail?.contains("PERMISSION_DENIED") == true -> "Firebase AI Logic yetkilendirmesi başarısız oldu."
                        lastApiErrorDetail?.contains("429") == true || lastApiErrorDetail?.contains("RESOURCE_EXHAUSTED") == true -> "İstek kotası doldu. Lütfen biraz bekleyip tekrar deneyin."
                        !lastApiErrorDetail.isNullOrBlank() -> lastApiErrorDetail
                        else -> "Görselden veya metinden ürün okunamadı. Lütfen fişin net ve aydınlık bir fotoğrafını yükleyin veya metin olarak yazın."
                    }
                    _scanUiState.value = ScanUiState.Error("İşlem Başarısız: $userFriendlyError")
                } else {
                    _scanUiState.value = ScanUiState.Success(fallbackResult)
                    fallbackResult.microTip?.let {
                        if (it.isNotEmpty()) _currentMicroTip.value = it
                    }
                }
            }
        }
    }

    fun generateRecipesFromInventory() {
        _scanUiState.value = ScanUiState.Loading

        viewModelScope.launch {
            val currentItems = activeItems.value
            val effectiveItemsText = if (currentItems.isNotEmpty()) {
                currentItems.joinToString("\n") {
                    "- ${it.name} (${it.quantity}, Kalan: %${it.remainingPercent}, Konum: ${it.storage})"
                }
            } else {
                "- Makarna (500g, Kiler)\n- Yumurta (6 Adet, Buzdolabı)\n- Domates (3 Adet, Buzdolabı)\n- Yoğurt (500g, Buzdolabı)\n- Peynir (200g, Buzdolabı)\n- Bayat Ekmek (1 Adet, Kiler)"
            }

            var success = false

            val ingredientsText = effectiveItemsText
            val diet = _userDiet.value
            val allergies = _userAllergies.value.joinToString(", ")
            val disliked = _dislikedIngredients.value

            try {
                val promptText = """
                    Sen "Smart Kitchen AI" mutfak asistanısın. Kullanıcının evindeki malzemeleri kullanarak kişiselleştirilmiş tarifler üret.
                    
                    KULLANICI DİYET FILTRE VE ALERJI BILGILERI:
                    - Diyet Tercihi: $diet (Eğer Vegan ise sadece bitkisel tarifler; Vejetaryen ise et/balık içermeyen; Glütensiz ise gluten içermeyen un vb.)
                    - Alerjiler: $allergies (Bu alerjenleri içeren MALZEMELERİ KESİNLİKLE KULLANMA!)
                    - Tercih Edilmeyen/Sevilmeyen Malzemeler: $disliked (Bu malzemeleri kesinlikle yemekte barındırma!)
                    
                    ENVANTERDEKİ MALZEMELER:
                    $ingredientsText
                    
                    Yukarıdaki envanter listesinde bulunan, özellikle SKT'si yaklaşan veya kalan miktarı azalan gıdaları öncelikli kullanarak 2 adet akıllı, sürdürülebilir, lezzetli ve pratik yemek tarifi önerisi oluştur. 
                    - Sadece envanterdeki malzemeleri ve tuz, su, yağ gibi temel kiler malzemelerini kullanmaya çalış.
                    - Sürdürülebilirlik etkisi için ne kadar CO2 ve gıda israfı engellendiğini hesapla ve Türkçe tebrik mesajı yaz.
                    - Yemeklerin kalori ve makro besin analizini de kesinlikle yap. "calories" değeri sayısal (Integer) olmalı, protein, karbonhidrat (carbs) ve yağ (fat) gramajlarıyla (örn: "25g") belirtilmelidir. "calorie_analysis" kısmında ise bu yemeğin besinsel değeri, diyetinize/sağlığınıza faydası hakkında 1-2 cümlelik Türkçe kalori analizi ekle.
                    - En sonda, mutfak/depolama tüyosu (micro_tip) ekle.
                    
                    Yanıtı her zaman ve kesinlikle aşağıdaki JSON yapısında döndür:
                    {
                      "detected_items": [],
                      "recipe_suggestions": [
                        {
                          "title": "Tarif Adı",
                          "used_expiring_items": ["Ürün 1", "Ürün 2"],
                          "prep_time": "15 dk",
                          "instructions": "Malzemelerle hazırlama aşamaları...",
                          "calories": 420,
                          "protein": "25g",
                          "carbs": "50g",
                          "fat": "12g",
                          "calorie_analysis": "Yüksek proteinli ve düşük yağlı bu tarif, kas gelişimi ve tokluk hissi için mükemmeldir."
                        }
                      ],
                      "sustainability_impact": "Bu tariflerle yaklaşık X kg CO2 salınımını önlediniz!",
                      "micro_tip": "Pratik bir mutfak tüyo cümlesi."
                    }
                """.trimIndent()

                val rawJson = withContext(Dispatchers.IO) {
                    FirebaseAiManager.generateContent(promptText)
                }

                if (rawJson != null) {
                    val cleanJson = rawJson
                        .trim()
                        .replace(Regex("^```[a-zA-Z]*\\n?"), "")
                        .replace(Regex("\\n?```$"), "")
                        .trim()

                    val parsedResult = withContext(Dispatchers.Default) {
                        try {
                            FirebaseAiManager.resultAdapter.fromJson(cleanJson)
                        } catch (jsonEx: Exception) {
                            Log.e("SmartKitchenAI", "JSON Parse Exception: ${jsonEx.message}", jsonEx)
                            null
                        }
                    }

                    if (parsedResult != null) {
                        _scanUiState.value = ScanUiState.Success(parsedResult)
                        parsedResult.microTip?.let {
                            if (it.isNotEmpty()) _currentMicroTip.value = it
                        }
                        success = true
                    }
                }
            } catch (e: Exception) {
                Log.e("SmartKitchenAI", "Error calling Firebase Vertex AI Recipe: ${e.message}", e)
            }

            if (!success) {
                val fallbackResult = generateLocalSmartResult("", isInventoryBased = true)
                if (fallbackResult.recipeSuggestions.isNullOrEmpty()) {
                     _scanUiState.value = ScanUiState.Error("Tarif oluşturulamadı veya API'ye ulaşılamadı. Lütfen internet bağlantınızı kontrol edin.")
                } else {
                    _scanUiState.value = ScanUiState.Success(fallbackResult)
                    fallbackResult.microTip?.let {
                        if (it.isNotEmpty()) _currentMicroTip.value = it
                    }
                }
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