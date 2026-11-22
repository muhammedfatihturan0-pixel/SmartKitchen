package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "meal_plans")
data class MealPlanItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val dayOfWeek: String, // "Pazartesi", "Salı", "Çarşamba", "Perşembe", "Cuma", "Cumartesi", "Pazar"
    val mealType: String, // "Kahvaltı", "Öğle Yemeği", "Akşam Yemeği", "Ara Öğün"
    val recipeTitle: String,
    val ingredients: String, // e.g. "Yumurta, Domates, Biber"
    val prepTime: String = "20 dk",
    val calories: Int = 350,
    val isCooked: Boolean = false,
    val instructions: String = ""
)
