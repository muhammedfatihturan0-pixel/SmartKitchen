package com.example.ui

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.OutdoorGrill
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.api.RecipeSuggestion
import com.example.data.InventoryItem

@Composable
fun RecipeTab(
    activeItems: List<InventoryItem>,
    scanUiState: ScanUiState,
    viewModel: InventoryViewModel,
    onStartCooking: (RecipeSuggestion) -> Unit
) {
    val context = LocalContext.current
    val expiringItems = remember(activeItems) {
        activeItems.filter { it.getRemainingDays() <= 3 }.sortedBy { it.getRemainingDays() }
    }

    val userDiet by viewModel.userDiet.collectAsStateWithLifecycle()
    val userAllergies by viewModel.userAllergies.collectAsStateWithLifecycle()
    val dislikedIngredients by viewModel.dislikedIngredients.collectAsStateWithLifecycle()

    var activeSubMode by remember { mutableStateOf("RECIPES") } // "RECIPES" or "MEAL_PLAN"
    var showFilters by remember { mutableStateOf(false) }
    var selectedCategoryFilter by remember { mutableStateOf("Tümü") }
    var searchQuery by remember { mutableStateOf("") }
    var selectedRecipeForDetail by remember { mutableStateOf<ReadyRecipe?>(null) }

    val categories = listOf("Tümü", "Hızlı & Pratik", "Fit & Diyet", "Çorbalar", "Kahvaltılık", "Ana Yemek")
    val diets = listOf("Hepsi", "Vegan", "Vejetaryen", "Glütensiz")
    val allergenList = listOf("Gluten", "Laktoz", "Yer Fıstığı", "Yumurta", "Deniz Ürünleri")

    val heroRecipe = remember(activeItems, userDiet, userAllergies, dislikedIngredients) {
        val compatible = defaultReadyRecipes.filter {
            it.matchesPreferences(userDiet, userAllergies, dislikedIngredients)
        }.ifEmpty { defaultReadyRecipes }

        compatible.maxByOrNull { it.countInventoryMatches(activeItems) } ?: defaultReadyRecipes.first()
    }

    val filteredReadyRecipes = remember(
        selectedCategoryFilter,
        searchQuery,
        activeItems,
        userDiet,
        userAllergies,
        dislikedIngredients
    ) {
        defaultReadyRecipes.filter { recipe ->
            val matchCategory = selectedCategoryFilter == "Tümü" || recipe.category == selectedCategoryFilter
            val matchSearch = searchQuery.isBlank() ||
                recipe.title.contains(searchQuery, ignoreCase = true) ||
                recipe.ingredients.any { it.contains(searchQuery, ignoreCase = true) } ||
                recipe.usedItems.any { it.contains(searchQuery, ignoreCase = true) }
            val matchPref = recipe.matchesPreferences(userDiet, userAllergies, dislikedIngredients)
            matchCategory && matchSearch && matchPref
        }.sortedByDescending { it.countInventoryMatches(activeItems) }
    }

    if (selectedRecipeForDetail != null) {
        RecipeDetailDialog(
            recipe = selectedRecipeForDetail!!,
            onConsume = { recipe ->
                viewModel.consumeRecipe(
                    recipe.title,
                    recipe.calories,
                    recipe.protein,
                    recipe.carbs,
                    recipe.fat
                )
                Toast.makeText(
                    context,
                    "${recipe.title} tüketildi! Kalori ve makrolarınıza kaydedildi.",
                    Toast.LENGTH_LONG
                ).show()
            },
            onStartCooking = { recipe ->
                selectedRecipeForDetail = null
                onStartCooking(recipe.toRecipeSuggestion())
            },
            onDismiss = { selectedRecipeForDetail = null }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp)
    ) {
        item {
            Column {
                Text(
                    "Akıllı Tarifler & Haftalık Plan",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = DarkCharcoal
                )
                Text(
                    "Dolabınızdaki gıdaları değerlendiren kişisel tarifler ve 7 günlük menü.",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
        }

        // Sub-mode switcher (Tarifler & AI | Haftalık Plan)
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White, RoundedCornerShape(14.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf(
                    "RECIPES" to Pair("🍳 Tarifler & AI", Icons.Default.RestaurantMenu),
                    "MEAL_PLAN" to Pair("📅 Haftalık Plan", Icons.Default.CalendarMonth)
                ).forEach { (key, labelIcon) ->
                    val isSelected = activeSubMode == key
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { activeSubMode = key }
                            .testTag("recipe_submode_$key"),
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) KitchenSage else Color.Transparent
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 9.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = labelIcon.second,
                                contentDescription = null,
                                tint = if (isSelected) Color.White else Color.Gray,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = labelIcon.first,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) Color.White else DarkCharcoal
                            )
                        }
                    }
                }
            }
        }

        if (activeSubMode == "MEAL_PLAN") {
            item {
                MealPlannerSection(
                    viewModel = viewModel,
                    onStartCooking = onStartCooking
                )
            }
        } else {
            item {
                DailyCalorieWidget(viewModel = viewModel)
            }

            // AI Generator & Diet/Allergy Filter Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, BorderColor)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = KitchenSage, modifier = Modifier.size(18.dp))
                                Text("Kişisel Mutfak Asistanı", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = DarkCharcoal)
                            }

                            TextButton(
                                onClick = { showFilters = !showFilters },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    if (showFilters) "Filtreleri Gizle" else "Diyet & Alerji",
                                    fontSize = 11.sp,
                                    color = KitchenSage,
                                    fontWeight = FontWeight.Bold
                                )
                                Icon(
                                    imageVector = if (showFilters) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = null,
                                    tint = KitchenSage,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        Button(
                            onClick = { viewModel.generateRecipesFromInventory() },
                            colors = ButtonDefaults.buttonColors(containerColor = KitchenSage),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("quick_ai_recipe_generate_btn")
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Dolaptaki Gıdalara Göre AI Tarifi Üret", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        if (showFilters) {
                            HorizontalDivider(color = Color.LightGray.copy(alpha = 0.3f))

                            Text("Beslenme Tercihiniz:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                diets.forEach { diet ->
                                    val isSelected = userDiet == diet
                                    Surface(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable {
                                                viewModel.updatePreferences(
                                                    diet = diet,
                                                    allergies = userAllergies,
                                                    disliked = dislikedIngredients
                                                )
                                            },
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isSelected) KitchenSage else SoftSageBg,
                                        border = if (isSelected) null else BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.3f))
                                    ) {
                                        Text(
                                            text = diet,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) Color.White else DarkCharcoal,
                                            modifier = Modifier.padding(vertical = 6.dp),
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }

                            Text("Alerjileriniz (Hariç tutulur):", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                            FlowRow(
                                mainAxisSpacing = 6.dp,
                                crossAxisSpacing = 6.dp,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                allergenList.forEach { allergen ->
                                    val isSelected = userAllergies.contains(allergen)
                                    Surface(
                                        modifier = Modifier.clickable {
                                            val newList = if (isSelected) userAllergies - allergen else userAllergies + allergen
                                            viewModel.updatePreferences(
                                                diet = userDiet,
                                                allergies = newList,
                                                disliked = dislikedIngredients
                                            )
                                        },
                                        shape = RoundedCornerShape(16.dp),
                                        color = if (isSelected) KitchenClay else SoftSageBg,
                                        border = if (isSelected) null else BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.3f))
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                        ) {
                                            if (isSelected) {
                                                Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(10.dp))
                                                Spacer(modifier = Modifier.width(3.dp))
                                            }
                                            Text(
                                                text = allergen,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isSelected) Color.White else DarkCharcoal
                                            )
                                        }
                                    }
                                }
                            }

                            Text("İstemediğiniz Malzemeler:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                            OutlinedTextField(
                                value = dislikedIngredients,
                                onValueChange = {
                                    viewModel.updatePreferences(
                                        diet = userDiet,
                                        allergies = userAllergies,
                                        disliked = it
                                    )
                                },
                                placeholder = { Text("Örn: kereviz, mantar, acı biber...", fontSize = 11.sp) },
                                modifier = Modifier.fillMaxWidth(),
                                textStyle = TextStyle(fontSize = 12.sp, color = DarkCharcoal),
                                shape = RoundedCornerShape(10.dp),
                                colors = smartKitchenFieldColors(),
                                singleLine = true
                            )
                        }
                    }
                }
            }

            // AI Generation Results
            when (scanUiState) {
                is ScanUiState.Loading -> {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = AccentGreenBg),
                            border = BorderStroke(1.dp, AccentGreenBorder)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                CircularProgressIndicator(color = KitchenSage, modifier = Modifier.size(36.dp))
                                Text(
                                    "AI Mutfak Şefi Dolabınızı Analiz Ediyor...",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = KitchenSage,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    "Dolabınızdaki gıdalar, seçtiğiniz diyet ve alerji tercihlerinize göre harmanlanıyor.",
                                    fontSize = 11.sp,
                                    color = Color.DarkGray,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }

                is ScanUiState.Success -> {
                    val result = scanUiState.result
                    if (!result.recipeSuggestions.isNullOrEmpty()) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                border = BorderStroke(1.dp, KitchenSage.copy(alpha = 0.3f))
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Icon(Icons.Default.RestaurantMenu, contentDescription = null, tint = KitchenSage, modifier = Modifier.size(18.dp))
                                            Text(
                                                "Size Özel Tarifler (${result.recipeSuggestions.size})",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                color = DarkCharcoal
                                            )
                                        }
                                        Surface(
                                            color = if (scanUiState.isOfflineFallback) KitchenClay.copy(alpha = 0.12f) else AccentGreenBg,
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text(
                                                text = if (scanUiState.isOfflineFallback) "Çevrimdışı Motor" else "Gemini AI",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (scanUiState.isOfflineFallback) KitchenClay else KitchenSage,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                            )
                                        }
                                    }

                                    if (!scanUiState.fallbackReason.isNullOrBlank()) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(scanUiState.fallbackReason, fontSize = 10.sp, color = Color.Gray)
                                    }

                                    result.recipeSuggestions.forEach { recipe ->
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text(recipe.title, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = KitchenSage)
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                            modifier = Modifier.padding(vertical = 2.dp)
                                        ) {
                                            Text("⏱️ ${recipe.prepTime}", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Medium)
                                            Text(
                                                "🔥 Kurtarılan: ${recipe.usedExpiringItems.joinToString(", ")}",
                                                fontSize = 11.sp,
                                                color = KitchenClay,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }

                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            modifier = Modifier.padding(vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            val cal = recipe.calories ?: 350
                                            val prot = recipe.protein ?: "18g"
                                            val carb = recipe.carbs ?: "42g"
                                            val f = recipe.fat ?: "12g"

                                            SuggestionNutritionChip("⚡ $cal kcal", HighlightGreen, HighlightDark)
                                            SuggestionNutritionChip("🥩 P: $prot", SoftSageBg, DarkCharcoal)
                                            SuggestionNutritionChip("🌾 K: $carb", SoftSageBg, DarkCharcoal)
                                            SuggestionNutritionChip("🧈 Y: $f", SoftSageBg, DarkCharcoal)
                                        }

                                        if (!recipe.instructions.isNullOrEmpty()) {
                                            Text(
                                                recipe.instructions,
                                                fontSize = 11.sp,
                                                color = DarkCharcoal,
                                                modifier = Modifier
                                                    .padding(top = 4.dp)
                                                    .fillMaxWidth()
                                                    .background(SoftSageBg, RoundedCornerShape(6.dp))
                                                    .padding(8.dp),
                                                lineHeight = 15.sp
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(8.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            OutlinedButton(
                                                onClick = { onStartCooking(recipe) },
                                                shape = RoundedCornerShape(8.dp),
                                                border = BorderStroke(1.dp, KitchenSage),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(38.dp),
                                                contentPadding = PaddingValues(horizontal = 6.dp)
                                            ) {
                                                Icon(Icons.Default.OutdoorGrill, contentDescription = null, tint = KitchenSage, modifier = Modifier.size(14.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Adım Adım Pişir", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = KitchenSage)
                                            }

                                            Button(
                                                onClick = {
                                                    viewModel.consumeRecipe(
                                                        recipe.title,
                                                        recipe.calories,
                                                        recipe.protein,
                                                        recipe.carbs,
                                                        recipe.fat
                                                    )
                                                    Toast.makeText(
                                                        context,
                                                        "${recipe.title} tüketildi! Kalori ve makrolarınıza işlendi.",
                                                        Toast.LENGTH_LONG
                                                    ).show()
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = KitchenSage),
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(38.dp),
                                                contentPadding = PaddingValues(horizontal = 6.dp)
                                            ) {
                                                Icon(Icons.Default.Restaurant, contentDescription = null, modifier = Modifier.size(14.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Pişirdim", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        HorizontalDivider(color = Color.LightGray.copy(alpha = 0.2f))
                                    }
                                }
                            }
                        }
                    }
                }

                is ScanUiState.Error -> {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = AlertRed.copy(alpha = 0.08f)),
                            border = BorderStroke(1.dp, AlertRed.copy(alpha = 0.3f))
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = AlertRed)
                                    Text("Tarif Bilgisi", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = AlertRed)
                                }
                                Text(scanUiState.message, fontSize = 11.sp, color = DarkCharcoal)
                                Button(
                                    onClick = { viewModel.generateRecipesFromInventory() },
                                    colors = ButtonDefaults.buttonColors(containerColor = KitchenSage),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Yeniden Tarif Üret", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                else -> {}
            }

            // Dynamic Hero Banner based on user's actual inventory + diet!
            item {
                AIRecipeSuggestionHero(
                    recipe = heroRecipe,
                    matchCount = heroRecipe.countInventoryMatches(activeItems),
                    onStartRecipe = { selectedRecipeForDetail = heroRecipe }
                )
            }

            // Critical expiring items banner
            if (expiringItems.isNotEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = KitchenClay.copy(alpha = 0.08f)),
                        border = BorderStroke(1.dp, KitchenClay.copy(alpha = 0.3f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.Warning, contentDescription = "Alarm", tint = KitchenClay, modifier = Modifier.size(16.dp))
                                Text(
                                    "Kritik Durumdaki Ürünler (${expiringItems.size})",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = KitchenClay
                                )
                            }
                            Text(
                                "Bu gıdalar bozulmadan önce tariflerle değerlendirilmelidir:",
                                fontSize = 10.sp,
                                color = Color.Gray,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            expiringItems.forEach { item ->
                                val rem = item.getRemainingDays()
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("• ${item.name} (${item.quantity})", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = DarkCharcoal)
                                    Text(
                                        when {
                                            rem < 0 -> "Süresi Geçti!"
                                            rem == 0 -> "Bugün Son!"
                                            else -> "$rem Gün Kaldı!"
                                        },
                                        fontSize = 11.sp,
                                        color = AlertRed,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Ready Recipes Catalog Header + Search + Category Tabs
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Hazır Kurtarma Tarifleri 📖",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = DarkCharcoal
                        )
                        Text(
                            "${filteredReadyRecipes.size} Tarif",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = KitchenSage
                        )
                    }

                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Tarif veya malzeme ara (örn: mercimek, makarna)...", fontSize = 11.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Ara", tint = Color.Gray, modifier = Modifier.size(18.dp)) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "Temizle", tint = Color.Gray, modifier = Modifier.size(16.dp))
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("recipe_search_input"),
                        shape = RoundedCornerShape(12.dp),
                        colors = smartKitchenFieldColors(),
                        singleLine = true,
                        textStyle = TextStyle(fontSize = 12.sp, color = DarkCharcoal)
                    )

                    ScrollableTabRow(
                        selectedTabIndex = categories.indexOf(selectedCategoryFilter).coerceAtLeast(0),
                        edgePadding = 0.dp,
                        containerColor = Color.Transparent,
                        indicator = {},
                        divider = {}
                    ) {
                        categories.forEach { cat ->
                            val isSelected = selectedCategoryFilter == cat
                            Surface(
                                modifier = Modifier
                                    .padding(end = 6.dp)
                                    .clickable { selectedCategoryFilter = cat },
                                shape = RoundedCornerShape(16.dp),
                                color = if (isSelected) KitchenSage else Color.White,
                                border = if (isSelected) null else BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.4f))
                            ) {
                                Text(
                                    text = cat,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) Color.White else DarkCharcoal,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            }

            if (filteredReadyRecipes.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.3f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("🔍", fontSize = 28.sp)
                            Text("Eşleşen Tarif Bulunamadı", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = DarkCharcoal)
                            Text("Farklı bir kategori, arama terimi veya diyet filtresi deneyebilirsiniz.", fontSize = 11.sp, color = Color.Gray, textAlign = TextAlign.Center)
                        }
                    }
                }
            } else {
                items(filteredReadyRecipes, key = { it.id }) { recipe ->
                    val matches = recipe.countInventoryMatches(activeItems)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedRecipeForDetail = recipe }
                            .testTag("recipe_card_${recipe.id}"),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, BorderColor),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(recipe.emoji, fontSize = 24.sp)
                                    Column {
                                        Text(recipe.title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = DarkCharcoal)
                                        Text(
                                            text = "⏱️ ${recipe.prepTime} • ${recipe.category}",
                                            fontSize = 10.sp,
                                            color = Color.Gray,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }

                                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Surface(
                                        color = HighlightGreen.copy(alpha = 0.5f),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = "⚡ ${recipe.calories} kcal",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = HighlightDark,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                        )
                                    }
                                    if (matches > 0) {
                                        Text(
                                            "Dolapta $matches/${recipe.usedItems.size} malzeme",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = KitchenSage
                                        )
                                    }
                                }
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Malzemeler:", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                                recipe.usedItems.take(3).forEach { item ->
                                    Surface(
                                        color = KitchenClay.copy(alpha = 0.08f),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = item,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = KitchenClay,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                SuggestionNutritionChip("🥩 P: ${recipe.protein}", SoftSageBg, DarkCharcoal)
                                SuggestionNutritionChip("🌾 K: ${recipe.carbs}", SoftSageBg, DarkCharcoal)
                                SuggestionNutritionChip("🧈 Y: ${recipe.fat}", SoftSageBg, DarkCharcoal)
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { onStartCooking(recipe.toRecipeSuggestion()) },
                                    border = BorderStroke(1.dp, KitchenSage),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(36.dp),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Icon(Icons.Default.OutdoorGrill, contentDescription = null, tint = KitchenSage, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Pişir", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = KitchenSage)
                                }

                                Button(
                                    onClick = { selectedRecipeForDetail = recipe },
                                    colors = ButtonDefaults.buttonColors(containerColor = KitchenSage),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .weight(1.5f)
                                        .height(36.dp),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Icon(Icons.Default.MenuBook, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Tarifi Gör", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AIRecipeSuggestionHero(
    recipe: ReadyRecipe,
    matchCount: Int,
    onStartRecipe: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Günün Önerisi: Bugün Ne Pişirmeli? ✨",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = DarkCharcoal,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("ai_recipe_hero_card"),
            color = KitchenSage,
            shape = RoundedCornerShape(24.dp),
            shadowElevation = 3.dp
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(color = Color.White.copy(alpha = 0.2f), shape = RoundedCornerShape(8.dp)) {
                            Text(
                                text = "${recipe.emoji} ${recipe.category}",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                        Surface(color = Color.White.copy(alpha = 0.2f), shape = RoundedCornerShape(8.dp)) {
                            Text(
                                text = "⏱️ ${recipe.prepTime}",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                        Surface(color = Color.White.copy(alpha = 0.2f), shape = RoundedCornerShape(8.dp)) {
                            Text(
                                text = "⚡ ${recipe.calories} kcal",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Column {
                        Text(
                            text = recipe.title,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        val subtitle = if (matchCount > 0) {
                            "Dolabınızdaki $matchCount malzeme ile uyumlu — israfı önleyerek CO₂ tasarrufu sağlayabilirsiniz."
                        } else {
                            recipe.calorieAnalysis
                        }
                        Text(
                            text = subtitle,
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.9f),
                            style = TextStyle(fontStyle = FontStyle.Italic)
                        )
                    }

                    Button(
                        onClick = onStartRecipe,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = HighlightGreen,
                            contentColor = HighlightDark
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("hero_start_recipe_btn"),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        Icon(Icons.Default.MenuBook, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Tarife Başla & Pişir", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun RecipeDetailDialog(
    recipe: ReadyRecipe,
    onConsume: (ReadyRecipe) -> Unit,
    onStartCooking: (ReadyRecipe) -> Unit,
    onDismiss: () -> Unit
) {
    val checkedIngredients = remember { mutableStateListOf<Int>() }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp)
                .testTag("recipe_detail_dialog_card"),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            LazyColumn(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(recipe.emoji, fontSize = 30.sp)
                            Column {
                                Text(recipe.title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = DarkCharcoal)
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(top = 2.dp)
                                ) {
                                    Surface(color = SoftSageBg, shape = RoundedCornerShape(6.dp)) {
                                        Text(
                                            text = recipe.category,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = KitchenSage,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                    Text("⏱️ ${recipe.prepTime}", fontSize = 11.sp, color = Color.Gray)
                                }
                            }
                        }

                        IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Kapat", tint = Color.Gray)
                        }
                    }
                }

                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SuggestionNutritionChip("⚡ ${recipe.calories} kcal", HighlightGreen, HighlightDark)
                        SuggestionNutritionChip("🥩 P: ${recipe.protein}", SoftSageBg, DarkCharcoal)
                        SuggestionNutritionChip("🌾 K: ${recipe.carbs}", SoftSageBg, DarkCharcoal)
                        SuggestionNutritionChip("🧈 Y: ${recipe.fat}", SoftSageBg, DarkCharcoal)
                    }
                }

                item {
                    Surface(
                        color = AccentGreenBg,
                        border = BorderStroke(1.dp, AccentGreenBorder),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = KitchenSage, modifier = Modifier.size(16.dp))
                            Column {
                                Text("Beslenme & Sağlık Analizi", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = KitchenSage)
                                Text(recipe.calorieAnalysis, fontSize = 11.sp, color = DarkCharcoal, lineHeight = 15.sp)
                            }
                        }
                    }
                }

                item {
                    Text("🛒 Gerekli Malzemeler (Varsa İşaretleyin):", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = DarkCharcoal)
                }

                items(recipe.ingredients.size) { index ->
                    val ingredient = recipe.ingredients[index]
                    val isChecked = checkedIngredients.contains(index)

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (isChecked) checkedIngredients.remove(index) else checkedIngredients.add(index)
                            }
                            .padding(vertical = 2.dp)
                    ) {
                        Checkbox(
                            checked = isChecked,
                            onCheckedChange = {
                                if (it) checkedIngredients.add(index) else checkedIngredients.remove(index)
                            },
                            colors = CheckboxDefaults.colors(checkedColor = KitchenSage)
                        )
                        Text(
                            text = ingredient,
                            fontSize = 12.sp,
                            color = if (isChecked) Color.Gray else DarkCharcoal,
                            style = TextStyle(
                                textDecoration = if (isChecked) TextDecoration.LineThrough else null
                            )
                        )
                    }
                }

                item {
                    Text("👩‍🍳 Adım Adım Hazırlanış:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = DarkCharcoal)
                }

                items(recipe.instructions.size) { index ->
                    val step = recipe.instructions[index]
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = KitchenSage,
                            modifier = Modifier.size(20.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("${index + 1}", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Text(
                            text = step,
                            fontSize = 12.sp,
                            color = DarkCharcoal,
                            lineHeight = 16.sp,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { onStartCooking(recipe) },
                            border = BorderStroke(1.dp, KitchenSage),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.OutdoorGrill, contentDescription = null, tint = KitchenSage, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Sayaçlı Pişirme Modunu Başlat", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = KitchenSage)
                        }

                        Button(
                            onClick = {
                                onConsume(recipe)
                                onDismiss()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = KitchenSage),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("dialog_consume_recipe_btn")
                        ) {
                            Icon(Icons.Default.Restaurant, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Bu Yemeği Pişirdim & Tükettim", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DailyCalorieWidget(viewModel: InventoryViewModel) {
    val dailyGoal by viewModel.dailyCalorieGoal.collectAsStateWithLifecycle()
    val consumedCal by viewModel.consumedCaloriesToday.collectAsStateWithLifecycle()
    val protein by viewModel.consumedProteinToday.collectAsStateWithLifecycle()
    val carbs by viewModel.consumedCarbsToday.collectAsStateWithLifecycle()
    val fat by viewModel.consumedFatToday.collectAsStateWithLifecycle()

    var showGoalDialog by remember { mutableStateOf(false) }
    var showManualAddDialog by remember { mutableStateOf(false) }

    val progress = if (dailyGoal > 0) (consumedCal.toFloat() / dailyGoal).coerceIn(0f, 1f) else 0f
    val remaining = (dailyGoal - consumedCal).coerceAtLeast(0)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("calorie_tracker_widget"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = AccentGreenBg),
        border = BorderStroke(1.dp, AccentGreenBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.LocalFireDepartment, contentDescription = "Kalori Takip", tint = KitchenSage, modifier = Modifier.size(18.dp))
                    Text("Günlük Kalori & Besin Değerleri", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = DarkCharcoal)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = { showManualAddDialog = true }, modifier = Modifier.size(26.dp)) {
                        Icon(Icons.Default.Add, contentDescription = "Manuel Kalori Ekle", tint = KitchenSage, modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = { showGoalDialog = true }, modifier = Modifier.size(26.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "Hedef Değiştir", tint = KitchenSage, modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = { viewModel.resetDailyCalories() }, modifier = Modifier.size(26.dp)) {
                        Icon(Icons.Default.Refresh, contentDescription = "Sıfırla", tint = KitchenClay, modifier = Modifier.size(16.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Tüketilen: $consumedCal kcal", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = DarkCharcoal)
                    Text("Hedef: $dailyGoal kcal", fontSize = 11.sp, color = Color.Gray)
                }

                Card(colors = CardDefaults.cardColors(containerColor = HighlightGreen), shape = RoundedCornerShape(8.dp)) {
                    Text(
                        text = "Kalan: $remaining kcal",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = HighlightDark,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp)),
                color = KitchenSage,
                trackColor = Color.White.copy(alpha = 0.6f)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .background(Color.White.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                        .padding(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("🥩 Protein", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                    Text("$protein g", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = DarkCharcoal)
                }
                Spacer(modifier = Modifier.width(6.dp))
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .background(Color.White.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                        .padding(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("🌾 Karb.", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                    Text("$carbs g", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = DarkCharcoal)
                }
                Spacer(modifier = Modifier.width(6.dp))
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .background(Color.White.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                        .padding(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("🧈 Yağ", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                    Text("$fat g", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = DarkCharcoal)
                }
            }
        }
    }

    if (showGoalDialog) {
        var tempGoal by remember { mutableStateOf(dailyGoal.toString()) }
        AlertDialog(
            onDismissRequest = { showGoalDialog = false },
            containerColor = Color.White,
            title = { Text("Hedef Kalori Belirle", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = DarkCharcoal) },
            text = {
                OutlinedTextField(
                    value = tempGoal,
                    onValueChange = { tempGoal = it },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    label = { Text("Günlük Hedef (kcal)") },
                    singleLine = true,
                    colors = smartKitchenFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.setDailyCalorieGoal(tempGoal.toIntOrNull() ?: 2000)
                        showGoalDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = KitchenSage)
                ) {
                    Text("Kaydet")
                }
            },
            dismissButton = {
                TextButton(onClick = { showGoalDialog = false }) {
                    Text("İptal", color = DarkCharcoal)
                }
            }
        )
    }

    if (showManualAddDialog) {
        var manualCal by remember { mutableStateOf("") }
        var manualProt by remember { mutableStateOf("") }
        var manualCarb by remember { mutableStateOf("") }
        var manualFat by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showManualAddDialog = false },
            containerColor = Color.White,
            title = { Text("Manuel Besin Değeri Ekle", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = DarkCharcoal) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = manualCal,
                        onValueChange = { manualCal = it },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        label = { Text("Kalori (kcal)") },
                        singleLine = true,
                        colors = smartKitchenFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = manualProt,
                        onValueChange = { manualProt = it },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        label = { Text("Protein (g)") },
                        singleLine = true,
                        colors = smartKitchenFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = manualCarb,
                        onValueChange = { manualCarb = it },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        label = { Text("Karbonhidrat (g)") },
                        singleLine = true,
                        colors = smartKitchenFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = manualFat,
                        onValueChange = { manualFat = it },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        label = { Text("Yağ (g)") },
                        singleLine = true,
                        colors = smartKitchenFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.addManualNutrition(
                            calories = manualCal.toIntOrNull() ?: 0,
                            protein = manualProt.toIntOrNull() ?: 0,
                            carbs = manualCarb.toIntOrNull() ?: 0,
                            fat = manualFat.toIntOrNull() ?: 0
                        )
                        showManualAddDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = KitchenSage)
                ) {
                    Text("Ekle")
                }
            },
            dismissButton = {
                TextButton(onClick = { showManualAddDialog = false }) {
                    Text("İptal", color = DarkCharcoal)
                }
            }
        )
    }
}
