package com.example.ui

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.OutdoorGrill
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.api.RecipeSuggestion
import com.example.data.MealPlanItem

@Composable
fun MealPlannerSection(
    viewModel: InventoryViewModel,
    onStartCooking: (RecipeSuggestion) -> Unit
) {
    val mealPlans by viewModel.mealPlans.collectAsStateWithLifecycle()
    val isLoading by viewModel.isMealPlanLoading.collectAsStateWithLifecycle()
    val weeklyTip by viewModel.weeklyMealPlanTip.collectAsStateWithLifecycle()
    val usedFallback by viewModel.mealPlanUsedFallback.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val daysOfWeek = listOf("Tümü", "Pazartesi", "Salı", "Çarşamba", "Perşembe", "Cuma", "Cumartesi", "Pazar")
    var selectedDay by remember { mutableStateOf("Tümü") }

    val filteredMeals = if (selectedDay == "Tümü") {
        mealPlans
    } else {
        mealPlans.filter { it.dayOfWeek.equals(selectedDay, ignoreCase = true) }
    }

    val totalCooked = mealPlans.count { it.isCooked }
    val totalPlanned = mealPlans.size

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("meal_planner_section"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = AccentGreenBg),
            border = BorderStroke(1.dp, KitchenSage.copy(alpha = 0.3f))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
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
                        Surface(
                            color = KitchenSage,
                            shape = CircleShape,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CalendarMonth,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                        Column {
                            Text(
                                "📅 Haftalık Akıllı Yemek Planı",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = DarkCharcoal
                            )
                            Text(
                                "Dolaptaki malzemeleri israf etmeden tüketin",
                                fontSize = 10.sp,
                                color = Color.Gray
                            )
                        }
                    }

                    if (totalPlanned > 0) {
                        Surface(
                            color = KitchenSage.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                "$totalCooked / $totalPlanned Pişirildi",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = KitchenSage,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                Text(
                    "Buzdolabınızdaki gıdaları ve diyet tercihlerinizi dikkate alarak 7 günlük sıfır atık menüsü hazırlar.",
                    fontSize = 11.sp,
                    color = DarkCharcoal,
                    lineHeight = 16.sp
                )

                Button(
                    onClick = {
                        viewModel.generateWeeklyMealPlanWithAi { isAi ->
                            val msg = if (isAi) {
                                "Gemini AI ile haftalık menünüz oluşturuldu! 🥗"
                            } else {
                                "Çevrimdışı akıllı menü diyetinize göre hazırlandı!"
                            }
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = KitchenSage),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !isLoading
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Menü Hazırlanıyor...", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    } else {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            if (mealPlans.isEmpty()) "Haftalık Planı Oluştur" else "Menüyü Yeniden Planla",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        if (weeklyTip.isNotBlank()) {
            Surface(
                color = if (usedFallback) KitchenClay.copy(alpha = 0.08f) else SoftSageBg,
                border = BorderStroke(1.dp, if (usedFallback) KitchenClay.copy(alpha = 0.3f) else BorderColor),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Lightbulb,
                        contentDescription = null,
                        tint = if (usedFallback) KitchenClay else KitchenSage,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(weeklyTip, fontSize = 11.sp, color = DarkCharcoal, lineHeight = 15.sp)
                }
            }
        }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(daysOfWeek) { day ->
                val isSelected = selectedDay == day
                Surface(
                    modifier = Modifier.clickable { selectedDay = day },
                    shape = RoundedCornerShape(16.dp),
                    color = if (isSelected) KitchenSage else Color.White,
                    border = BorderStroke(1.dp, if (isSelected) KitchenSage else Color.LightGray.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = day,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) Color.White else DarkCharcoal,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }

        if (filteredMeals.isEmpty()) {
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
                    Text("🍽️", fontSize = 28.sp)
                    Text("Henüz yemek planı oluşturulmadı", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = DarkCharcoal)
                    Text("Yukarıdaki butona dokunarak 7 günlük sıfır atık menünüzü oluşturun.", fontSize = 11.sp, color = Color.Gray)
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                filteredMeals.forEach { meal ->
                    MealPlanCard(
                        meal = meal,
                        onToggleCooked = { viewModel.toggleMealPlanCooked(meal) },
                        onStartCooking = {
                            val recipe = RecipeSuggestion(
                                title = meal.recipeTitle,
                                prepTime = meal.prepTime,
                                calories = meal.calories,
                                instructions = meal.instructions.ifBlank {
                                    "Malzemeler: ${meal.ingredients}. Tüm malzemeleri hazırlayıp pişirin."
                                },
                                usedExpiringItems = meal.ingredients.split(",").map { it.trim() }
                            )
                            onStartCooking(recipe)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun MealPlanCard(
    meal: MealPlanItem,
    onToggleCooked: () -> Unit,
    onStartCooking: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (meal.isCooked) SoftSageBg.copy(alpha = 0.4f) else Color.White
        ),
        border = BorderStroke(
            1.dp,
            if (meal.isCooked) SafeGreen.copy(alpha = 0.5f) else Color.LightGray.copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(color = KitchenClay.copy(alpha = 0.12f), shape = RoundedCornerShape(8.dp)) {
                        Text(
                            text = meal.dayOfWeek,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = KitchenClay,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                    Text(text = meal.mealType, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                }

                Surface(color = HighlightGreen.copy(alpha = 0.5f), shape = RoundedCornerShape(8.dp)) {
                    Text(
                        text = "⚡ ${meal.calories} kcal • ⏱️ ${meal.prepTime}",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = HighlightDark,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
            }

            Text(
                text = meal.recipeTitle,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 15.sp,
                color = if (meal.isCooked) Color.Gray else DarkCharcoal
            )

            Text(text = "🥗 Malzemeler: ${meal.ingredients}", fontSize = 11.sp, color = Color.DarkGray)

            if (meal.instructions.isNotBlank()) {
                Text(text = meal.instructions, fontSize = 10.sp, color = Color.Gray, lineHeight = 14.sp)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onToggleCooked,
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = if (meal.isCooked) SafeGreen else Color.DarkGray
                    ),
                    border = BorderStroke(1.dp, if (meal.isCooked) SafeGreen else Color.LightGray)
                ) {
                    Icon(
                        imageVector = if (meal.isCooked) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (meal.isCooked) "Pişirildi (+40 Puan)" else "Pişirildi İşaretle",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Button(
                    onClick = onStartCooking,
                    modifier = Modifier.height(36.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = KitchenSage),
                    contentPadding = PaddingValues(horizontal = 12.dp)
                ) {
                    Icon(Icons.Default.OutdoorGrill, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Pişir", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
