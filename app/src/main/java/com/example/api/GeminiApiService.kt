package com.example.api

import android.graphics.Bitmap
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

// --- Custom Structural Output Data Classes ---

@JsonClass(generateAdapter = true)
data class SmartKitchenResult(
    @Json(name = "detected_items") val detectedItems: List<DetectedItem>? = emptyList(),
    @Json(name = "recipe_suggestions") val recipeSuggestions: List<RecipeSuggestion>? = emptyList(),
    @Json(name = "sustainability_impact") val sustainabilityImpact: String? = "",
    @Json(name = "micro_tip") val microTip: String? = ""
)

@JsonClass(generateAdapter = true)
data class DetectedItem(
    @Json(name = "name") val name: String,
    @Json(name = "quantity") val quantity: String,
    @Json(name = "estimated_shelf_life_days") val estimatedShelfLifeDays: Int = 7,
    @Json(name = "storage") val storage: String = "Kiler", // "Buzdolabı", "Kiler", "Dondurucu"
    @Json(name = "price") val price: Double? = 0.0 // Fişteki net fiyat
)

@JsonClass(generateAdapter = true)
data class RecipeSuggestion(
    @Json(name = "title") val title: String,
    @Json(name = "used_expiring_items") val usedExpiringItems: List<String>,
    @Json(name = "prep_time") val prepTime: String,
    @Json(name = "instructions") val instructions: String? = "",
    @Json(name = "calories") val calories: Int? = 300,
    @Json(name = "protein") val protein: String? = "15g",
    @Json(name = "carbs") val carbs: String? = "40g",
    @Json(name = "fat") val fat: String? = "10g",
    @Json(name = "calorie_analysis") val calorieAnalysis: String? = ""
)

@JsonClass(generateAdapter = true)
data class MealPlanDayDto(
    @Json(name = "day_of_week") val dayOfWeek: String,
    @Json(name = "meal_type") val mealType: String,
    @Json(name = "recipe_title") val recipeTitle: String,
    @Json(name = "ingredients") val ingredients: String,
    @Json(name = "prep_time") val prepTime: String = "20 dk",
    @Json(name = "calories") val calories: Int = 350,
    @Json(name = "instructions") val instructions: String = ""
)

@JsonClass(generateAdapter = true)
data class MealPlanResponse(
    @Json(name = "meals") val meals: List<MealPlanDayDto> = emptyList(),
    @Json(name = "weekly_tip") val weeklyTip: String = ""
)

/**
 * Firebase AI Logic Manager
 * Uses Firebase AI SDK (com.google.firebase.ai) configured via google-services.json.
 */
object FirebaseAiManager {
    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    val resultAdapter = moshi.adapter(SmartKitchenResult::class.java)
    val mealPlanAdapter = moshi.adapter(MealPlanResponse::class.java)

    fun getReceiptModel() = Firebase.ai.generativeModel(
        modelName = "gemini-3.1-flash-lite-preview",
        generationConfig = generationConfig {
            responseMimeType = "application/json"
            temperature = 0.0f
        },
        systemInstruction = content {
            text(
                """
                Sen strict bir OCR fiş tarayıcısısın.
                Görseldeki alışveriş fişini incele ve 'detected_items' dizisine SADECE fişte AÇIKÇA YAZAN ürün adını (name), miktarını (quantity) ve birim/toplam fiyatını (price) aktar.
                
                KESİN VE KATI KURALLAR:
                1. Fiş üzerinde metin veya rakam olarak görünmeyen HİÇBİR ÜRÜNÜ, HİÇBİR FİYATI LİSTEYE EKLEME.
                2. Fişte olmayan ürün uydurmak KESİNLİKLE YASAKTIR.
                3. Fiyat okunamıyorsa 'price' değerini 0.0 yap, uydurma rakam yazma.
                4. 'recipe_suggestions' dizisini BOŞ BIRAK ([]) çünkü bu bir fiş okuma işlemidir, tarif üretme işlemi değildir.
                5. 'estimated_shelf_life_days' için varsayılan olarak 7, 'storage' için varsayılan olarak 'Kiler' ver; bunlar için ek uydurma analiz yapma.
                """.trimIndent()
            )
        }
    )

    fun getGeneralModel() = Firebase.ai.generativeModel(
        modelName = "gemini-3.1-flash-lite-preview",
        generationConfig = generationConfig {
            responseMimeType = "application/json"
            temperature = 0.2f
        }
    )

    suspend fun analyzeReceipt(prompt: String, bitmap: Bitmap?): String? {
        val model = getReceiptModel()
        val response = if (bitmap != null) {
            model.generateContent(
                content {
                    image(bitmap)
                    text(prompt)
                }
            )
        } else {
            model.generateContent(
                content {
                    text(prompt)
                }
            )
        }
        return response.text
    }

    suspend fun generateContent(prompt: String, bitmap: Bitmap? = null): String? {
        val model = getGeneralModel()
        val response = if (bitmap != null) {
            model.generateContent(
                content {
                    image(bitmap)
                    text(prompt)
                }
            )
        } else {
            model.generateContent(
                content {
                    text(prompt)
                }
            )
        }
        return response.text
    }
}
