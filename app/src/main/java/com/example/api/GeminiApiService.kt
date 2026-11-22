package com.example.api

import android.graphics.Bitmap
import android.util.Base64
import com.example.BuildConfig
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.roundToInt

// --- Structured Output DTOs ---

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
    @Json(name = "price") val price: Double? = 0.0
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

// --- API Key Status & Result Models ---

enum class ApiKeySource {
    IN_APP,
    ENV_BUILD_CONFIG,
    NONE
}

data class ApiKeyStatus(
    val isConfigured: Boolean,
    val isValidFormat: Boolean,
    val source: ApiKeySource,
    val maskedKey: String
)

enum class AiErrorType {
    NO_API_KEY,
    INVALID_API_KEY,
    QUOTA_EXCEEDED,
    MODEL_NOT_FOUND,
    NETWORK_ERROR,
    PARSE_ERROR,
    UNKNOWN
}

sealed interface AiCallResult<out T> {
    data class Success<T>(val data: T, val modelUsed: String) : AiCallResult<T>
    data class Error<T>(val errorType: AiErrorType, val message: String) : AiCallResult<T>
}

/**
 * Direct Gemini REST API Client (generativelanguage.googleapis.com/v1beta).
 * Supports both build-time `.env` (BuildConfig.GEMINI_API_KEY) and user-provided in-app key.
 */
object GeminiApiClient {

    private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta"
    private val MODEL_FALLBACK_CHAIN = listOf(
        "gemini-2.5-flash",
        "gemini-2.0-flash",
        "gemini-2.5-flash-lite"
    )
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    val resultAdapter = moshi.adapter(SmartKitchenResult::class.java)
    val mealPlanAdapter = moshi.adapter(MealPlanResponse::class.java)

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    fun isPlaceholderKey(key: String?): Boolean {
        val trimmed = key?.trim().orEmpty()
        return trimmed.isEmpty() ||
            trimmed.equals("YOUR_API_KEY_HERE", ignoreCase = true) ||
            trimmed.startsWith("YOUR_", ignoreCase = true) ||
            trimmed.equals("null", ignoreCase = true)
    }

    fun isValidGeminiKeyFormat(key: String?): Boolean {
        val trimmed = key?.trim().orEmpty()
        if (isPlaceholderKey(trimmed)) return false
        return trimmed.startsWith("AIza") &&
            trimmed.length in 30..60 &&
            trimmed.none { it.isWhitespace() }
    }

    fun maskKey(key: String): String {
        val trimmed = key.trim()
        return if (trimmed.length >= 10) {
            "${trimmed.take(6)}••••${trimmed.takeLast(4)}"
        } else if (trimmed.isNotEmpty()) {
            "••••"
        } else {
            ""
        }
    }

    fun getBuildConfigKey(): String {
        return try {
            BuildConfig.GEMINI_API_KEY.trim()
        } catch (_: Throwable) {
            ""
        }
    }

    fun resolveActiveKey(inAppKey: String?, buildConfigKey: String = getBuildConfigKey()): String {
        val custom = inAppKey?.trim().orEmpty()
        if (custom.isNotEmpty() && !isPlaceholderKey(custom)) {
            return custom
        }
        val env = buildConfigKey.trim()
        if (env.isNotEmpty() && !isPlaceholderKey(env)) {
            return env
        }
        return ""
    }

    fun resolveApiKeyStatus(inAppKey: String?, buildConfigKey: String = getBuildConfigKey()): ApiKeyStatus {
        val custom = inAppKey?.trim().orEmpty()
        val env = buildConfigKey.trim()

        val (activeKey, source) = when {
            custom.isNotEmpty() && !isPlaceholderKey(custom) -> custom to ApiKeySource.IN_APP
            env.isNotEmpty() && !isPlaceholderKey(env) -> env to ApiKeySource.ENV_BUILD_CONFIG
            else -> "" to ApiKeySource.NONE
        }

        val configured = activeKey.isNotEmpty() && activeKey.length >= 20
        val validFormat = isValidGeminiKeyFormat(activeKey)
        return ApiKeyStatus(
            isConfigured = configured,
            isValidFormat = validFormat,
            source = source,
            maskedKey = maskKey(activeKey)
        )
    }

    /**
     * Strips markdown code fences and extracts the outermost JSON object `{ ... }`.
     */
    fun extractJsonObject(raw: String): String {
        var cleaned = raw.trim()
        cleaned = cleaned
            .replace(Regex("^```(?:json)?\\s*", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s*```$"), "")
            .trim()

        val firstBrace = cleaned.indexOf('{')
        val lastBrace = cleaned.lastIndexOf('}')
        return if (firstBrace >= 0 && lastBrace > firstBrace) {
            cleaned.substring(firstBrace, lastBrace + 1).trim()
        } else {
            cleaned
        }
    }

    suspend fun testApiConnection(apiKey: String): AiCallResult<String> {
        val cleanKey = apiKey.trim()
        if (isPlaceholderKey(cleanKey) || cleanKey.length < 20) {
            return AiCallResult.Error(
                AiErrorType.NO_API_KEY,
                "Geçerli bir Gemini API anahtarı tanımlanmamış."
            )
        }
        if (!isValidGeminiKeyFormat(cleanKey)) {
            return AiCallResult.Error(
                AiErrorType.INVALID_API_KEY,
                "API anahtarı formatı geçersiz görünüyor ('AIza...' ile başlamalıdır)."
            )
        }

        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("$BASE_URL/models?pageSize=1")
                    .addHeader("x-goog-api-key", cleanKey)
                    .get()
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    val code = response.code
                    val bodyStr = response.body?.string().orEmpty()
                    when {
                        response.isSuccessful -> AiCallResult.Success(
                            data = "Bağlantı başarılı! Gemini API anahtarı doğrulandı.",
                            modelUsed = "models-check"
                        )
                        code == 400 || code == 401 || code == 403 -> AiCallResult.Error(
                            AiErrorType.INVALID_API_KEY,
                            parseErrorMessage(bodyStr, "API anahtarı geçersiz veya yetkisiz (HTTP $code).")
                        )
                        code == 429 -> AiCallResult.Error(
                            AiErrorType.QUOTA_EXCEEDED,
                            "API istek kotası aşıldı (HTTP 429). Lütfen biraz sonra tekrar deneyin."
                        )
                        else -> AiCallResult.Error(
                            AiErrorType.UNKNOWN,
                            parseErrorMessage(bodyStr, "Bağlantı hatası (HTTP $code).")
                        )
                    }
                }
            } catch (_: UnknownHostException) {
                AiCallResult.Error(
                    AiErrorType.NETWORK_ERROR,
                    "İnternet bağlantısı yok veya sunucuya ulaşılamıyor."
                )
            } catch (_: SocketTimeoutException) {
                AiCallResult.Error(
                    AiErrorType.NETWORK_ERROR,
                    "Gemini sunucusu zaman aşımına uğradı. Lütfen tekrar deneyin."
                )
            } catch (e: IOException) {
                AiCallResult.Error(
                    AiErrorType.NETWORK_ERROR,
                    "Ağ hatası: ${e.localizedMessage ?: "Bağlantı kesildi"}"
                )
            } catch (e: Exception) {
                AiCallResult.Error(
                    AiErrorType.UNKNOWN,
                    "Beklenmeyen hata: ${e.localizedMessage ?: "Bilinmiyor"}"
                )
            }
        }
    }

    suspend fun analyzeReceipt(
        prompt: String,
        bitmap: Bitmap?,
        apiKey: String
    ): AiCallResult<SmartKitchenResult> {
        val systemInstruction = """
            Sen strict bir Türkçe market fişi OCR tarayıcısısın.
            Görseldeki veya metindeki alışveriş fişini incele ve 'detected_items' dizisine SADECE fişte açıkça yer alan gıda ürünlerini (name, quantity, estimated_shelf_life_days, storage, price) aktar.
            
            KESİN KURALLAR:
            1. Fişte görünmeyen hiçbir ürünü veya fiyatı uydurma. Poşet, KDV, toplam, kasiyer bilgilerini ekleme.
            2. Fiyat okunamıyorsa 'price' değerini 0.0 yap.
            3. 'recipe_suggestions' dizisini BOŞ BIRAK ([]).
            4. 'storage' değerini gıdaya uygun şekilde 'Buzdolabı', 'Kiler' veya 'Dondurucu' olarak belirle.
            5. 'estimated_shelf_life_days' değerini gıdaya göre gerçekçi gün sayısı (örn: süt 5-7, et 3, sebze 5-7, bakliyat 180) olarak yaz.
            6. Yalnızca geçerli JSON döndür.
        """.trimIndent()

        return generateSmartKitchenResult(
            prompt = prompt,
            bitmap = bitmap,
            systemInstruction = systemInstruction,
            apiKey = apiKey,
            temperature = 0.0f
        )
    }

    suspend fun analyzeKitchenVision(
        prompt: String,
        bitmap: Bitmap?,
        scanMode: String,
        apiKey: String
    ): AiCallResult<SmartKitchenResult> {
        val defaultStorage = when (scanMode) {
            "PANTRY" -> "Kiler"
            "FREEZER" -> "Dondurucu"
            else -> "Buzdolabı"
        }
        val systemInstruction = """
            Sen "Smart Kitchen AI" mutfak görsel analizi ve sıfır atık şefisin.
            Görseldeki veya metindeki gıda ürünlerini tanımla, varsayılan saklama alanı '$defaultStorage' olacak şekilde 'detected_items' listesi çıkar ve bu ürünlerle yapılabilecek 1-2 adet pratik, lezzetli yemek tarifi öner ('recipe_suggestions').
            Tariflerde kalori (Int), protein/carbs/fat (örn: "22g"), calorie_analysis ve adım adım hazırlanış ('instructions') mutlaka yer alsın.
            Yalnızca geçerli JSON döndür.
        """.trimIndent()

        return generateSmartKitchenResult(
            prompt = prompt,
            bitmap = bitmap,
            systemInstruction = systemInstruction,
            apiKey = apiKey,
            temperature = 0.25f
        )
    }

    suspend fun generateSmartKitchenResult(
        prompt: String,
        bitmap: Bitmap? = null,
        systemInstruction: String? = null,
        apiKey: String,
        temperature: Float = 0.35f
    ): AiCallResult<SmartKitchenResult> {
        return when (val jsonCall = generateStructuredJson(prompt, bitmap, systemInstruction, apiKey, temperature)) {
            is AiCallResult.Error -> AiCallResult.Error(jsonCall.errorType, jsonCall.message)
            is AiCallResult.Success -> {
                val parsed = withContext(Dispatchers.Default) {
                    try {
                        resultAdapter.fromJson(jsonCall.data)
                    } catch (_: Exception) {
                        null
                    }
                }
                if (parsed != null) {
                    AiCallResult.Success(parsed, jsonCall.modelUsed)
                } else {
                    AiCallResult.Error(
                        AiErrorType.PARSE_ERROR,
                        "Gemini yanıtı beklenen JSON formatında çözümlenemedi."
                    )
                }
            }
        }
    }

    suspend fun generateMealPlan(
        prompt: String,
        apiKey: String
    ): AiCallResult<MealPlanResponse> {
        val systemInstruction = """
            Sen "Smart Kitchen AI" haftalık sıfır atık menü planlayıcısısın.
            Kullanıcının diyet, alerji ve envanter kısıtlarına %100 uyarak 7 günlük pratik Türkçe yemek planı JSON'ı üret.
            Yalnızca geçerli JSON döndür.
        """.trimIndent()

        return when (val jsonCall = generateStructuredJson(prompt, null, systemInstruction, apiKey, 0.45f)) {
            is AiCallResult.Error -> AiCallResult.Error(jsonCall.errorType, jsonCall.message)
            is AiCallResult.Success -> {
                val parsed = withContext(Dispatchers.Default) {
                    try {
                        mealPlanAdapter.fromJson(jsonCall.data)
                    } catch (_: Exception) {
                        null
                    }
                }
                if (parsed != null && parsed.meals.isNotEmpty()) {
                    AiCallResult.Success(parsed, jsonCall.modelUsed)
                } else {
                    AiCallResult.Error(
                        AiErrorType.PARSE_ERROR,
                        "Haftalık plan yanıtı çözümlenemedi."
                    )
                }
            }
        }
    }

    suspend fun generateStructuredJson(
        prompt: String,
        bitmap: Bitmap? = null,
        systemInstruction: String? = null,
        apiKey: String,
        temperature: Float = 0.4f
    ): AiCallResult<String> {
        val cleanKey = apiKey.trim()
        if (isPlaceholderKey(cleanKey) || cleanKey.length < 20) {
            return AiCallResult.Error(
                AiErrorType.NO_API_KEY,
                "Gemini API anahtarı ayarlanmamış. Sürdürülebilirlik -> API Ayarları veya .env üzerinden ekleyin."
            )
        }

        val base64Image = if (bitmap != null) {
            withContext(Dispatchers.Default) { encodeBitmapToBase64(bitmap) }
        } else {
            null
        }

        val payload = buildGenerateContentPayload(prompt, base64Image, systemInstruction, temperature)

        return withContext(Dispatchers.IO) {
            var lastError: AiCallResult.Error<String>? = null

            for (model in MODEL_FALLBACK_CHAIN) {
                val url = "$BASE_URL/models/$model:generateContent"
                val request = Request.Builder()
                    .url(url)
                    .addHeader("x-goog-api-key", cleanKey)
                    .addHeader("Content-Type", "application/json")
                    .post(payload.toRequestBody(JSON_MEDIA_TYPE))
                    .build()

                try {
                    httpClient.newCall(request).execute().use { response ->
                        val code = response.code
                        val rawBody = response.body?.string().orEmpty()

                        if (response.isSuccessful) {
                            val text = extractCandidateText(rawBody)
                            if (text.isNullOrBlank()) {
                                return@withContext AiCallResult.Error(
                                    AiErrorType.PARSE_ERROR,
                                    "Gemini boş bir yanıt döndürdü."
                                )
                            }
                            return@withContext AiCallResult.Success(
                                data = extractJsonObject(text),
                                modelUsed = model
                            )
                        }

                        when (code) {
                            404 -> {
                                lastError = AiCallResult.Error(
                                    AiErrorType.MODEL_NOT_FOUND,
                                    "Model bulunamadı ($model)."
                                )
                            }
                            400, 401, 403 -> {
                                return@withContext AiCallResult.Error(
                                    AiErrorType.INVALID_API_KEY,
                                    parseErrorMessage(rawBody, "Gemini API anahtarı geçersiz veya yetkisiz (HTTP $code).")
                                )
                            }
                            429 -> {
                                return@withContext AiCallResult.Error(
                                    AiErrorType.QUOTA_EXCEEDED,
                                    "API kullanım kotası doldu (HTTP 429). Kısa süre sonra tekrar deneyin."
                                )
                            }
                            else -> {
                                return@withContext AiCallResult.Error(
                                    AiErrorType.UNKNOWN,
                                    parseErrorMessage(rawBody, "Gemini API hatası (HTTP $code).")
                                )
                            }
                        }
                    }
                } catch (_: UnknownHostException) {
                    return@withContext AiCallResult.Error(
                        AiErrorType.NETWORK_ERROR,
                        "İnternet bağlantısı bulunamadı."
                    )
                } catch (_: SocketTimeoutException) {
                    return@withContext AiCallResult.Error(
                        AiErrorType.NETWORK_ERROR,
                        "İstek zaman aşımına uğradı. Lütfen tekrar deneyin."
                    )
                } catch (e: IOException) {
                    return@withContext AiCallResult.Error(
                        AiErrorType.NETWORK_ERROR,
                        "Ağ hatası: ${e.localizedMessage ?: "Bağlantı sorunu"}"
                    )
                } catch (e: Exception) {
                    return@withContext AiCallResult.Error(
                        AiErrorType.UNKNOWN,
                        "Beklenmeyen hata: ${e.localizedMessage ?: "Bilinmiyor"}"
                    )
                }
            }

            lastError ?: AiCallResult.Error(
                AiErrorType.MODEL_NOT_FOUND,
                "Uygun Gemini modeli bulunamadı."
            )
        }
    }

    private fun buildGenerateContentPayload(
        prompt: String,
        base64Image: String?,
        systemInstruction: String?,
        temperature: Float
    ): String {
        val root = JSONObject()

        if (!systemInstruction.isNullOrBlank()) {
            val sysParts = JSONArray().put(JSONObject().put("text", systemInstruction))
            root.put("systemInstruction", JSONObject().put("parts", sysParts))
        }

        val userParts = JSONArray()
        if (!base64Image.isNullOrBlank()) {
            val inlineData = JSONObject()
                .put("mimeType", "image/jpeg")
                .put("data", base64Image)
            userParts.put(JSONObject().put("inlineData", inlineData))
        }
        userParts.put(JSONObject().put("text", prompt))

        val contents = JSONArray().put(
            JSONObject()
                .put("role", "user")
                .put("parts", userParts)
        )
        root.put("contents", contents)

        val genConfig = JSONObject()
            .put("responseMimeType", "application/json")
            .put("temperature", temperature.toDouble())
        root.put("generationConfig", genConfig)

        return root.toString()
    }

    private fun encodeBitmapToBase64(bitmap: Bitmap): String {
        val maxSide = 1280
        val longest = max(bitmap.width, bitmap.height)
        val scaled = if (longest > maxSide && longest > 0) {
            val ratio = maxSide.toFloat() / longest.toFloat()
            val targetW = (bitmap.width * ratio).roundToInt().coerceAtLeast(1)
            val targetH = (bitmap.height * ratio).roundToInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
        } else {
            bitmap
        }

        val outputStream = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
        val bytes = outputStream.toByteArray()
        if (scaled !== bitmap) {
            scaled.recycle()
        }
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    private fun extractCandidateText(rawResponseJson: String): String? {
        return try {
            val root = JSONObject(rawResponseJson)
            val candidates = root.optJSONArray("candidates") ?: return null
            if (candidates.length() == 0) return null
            val content = candidates.optJSONObject(0)?.optJSONObject("content") ?: return null
            val parts = content.optJSONArray("parts") ?: return null
            buildString {
                for (i in 0 until parts.length()) {
                    val partText = parts.optJSONObject(i)?.optString("text", "") ?: ""
                    append(partText)
                }
            }.trim()
        } catch (_: Exception) {
            null
        }
    }

    private fun parseErrorMessage(rawErrorJson: String, fallback: String): String {
        return try {
            val root = JSONObject(rawErrorJson)
            val errObj = root.optJSONObject("error")
            val msg = errObj?.optString("message", "").orEmpty()
            if (msg.isNotBlank()) "$fallback ($msg)" else fallback
        } catch (_: Exception) {
            fallback
        }
    }
}
