package com.example

import com.example.api.ApiKeySource
import com.example.api.BarcodeScannerHelper
import com.example.api.GeminiApiClient
import com.example.data.InventoryItem
import com.example.data.WasteActionLog
import com.example.domain.EcoMetricsCalculator
import com.example.domain.LocalRecipeEngine
import com.example.domain.ReceiptParser
import com.example.domain.UsernameValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class SmartKitchenDomainTest {

    @Test
    fun `inventory item remaining days distinguishes yesterday today and tomorrow`() {
        val baseCal = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 22, 14, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val now = baseCal.timeInMillis

        val yesterdayItem = InventoryItem(
            name = "Süt",
            quantity = "1L",
            category = "Süt Ürünleri",
            expiryDate = now - (14L * 3600L * 1000L), // 00:00 of Nov 22 minus 0 -> let's use 18h ago (Nov 21)
            storage = "Buzdolabı"
        )
        val expiredYesterday = yesterdayItem.copy(expiryDate = now - (20L * 3600L * 1000L))
        val expiresToday = yesterdayItem.copy(expiryDate = now - (2L * 3600L * 1000L)) // 12:00 today
        val expiresTomorrow = yesterdayItem.copy(expiryDate = now + (24L * 3600L * 1000L))

        assertEquals(-1, expiredYesterday.getRemainingDays(now))
        assertTrue(expiredYesterday.isExpired(now))
        assertEquals(0, expiresToday.getRemainingDays(now))
        assertFalse(expiresToday.isExpired(now))
        assertEquals(1, expiresTomorrow.getRemainingDays(now))
    }

    @Test
    fun `receipt parser correctly distinguishes ml from litre and does not misclassify substrings`() {
        val rawReceipt = """
            MIGROS TICARET A.S.
            500 ml süt 28,50
            1 paket makarna 24.00
            1 adet kavun 45.00
            2 kg domates 60.00 TL
            KDV TOPLAM
        """.trimIndent()

        val items = ReceiptParser.parseTextToDetectedItems(rawReceipt, "RECEIPT")
        assertEquals(4, items.size)

        val milk = items.first { it.name.contains("Süt", ignoreCase = true) }
        assertEquals("500 ml", milk.quantity)
        assertEquals(28.50, milk.price ?: 0.0, 0.01)
        assertEquals("Buzdolabı", milk.storage)

        val pasta = items.first { it.name.contains("Makarna", ignoreCase = true) }
        assertEquals("1 Paket", pasta.quantity)
        assertEquals("Kiler", pasta.storage)

        val melon = items.first { it.name.contains("Kavun", ignoreCase = true) }
        assertEquals("Sebze & Meyve", ReceiptParser.guessCategory(melon.name))
        assertEquals("Kiler / Kuru Gıda", ReceiptParser.guessCategory("1 Paket Makarna"))
        assertEquals("Et & Tavuk", ReceiptParser.guessCategory("Dana Kuşbaşı Et"))

        val commaSeparated = ReceiptParser.parseTextToDetectedItems("2 kg domates 60,50, 500 ml süt 28,50", "RECEIPT")
        assertEquals(2, commaSeparated.size)
        assertEquals(60.50, commaSeparated[0].price ?: 0.0, 0.01)
        assertEquals(28.50, commaSeparated[1].price ?: 0.0, 0.01)
    }

    @Test
    fun `username validator rejects empty allows turkish names containing mal and allows keeping own name`() {
        assertNotNull(UsernameValidator.validateForSubmit(""))
        assertNull(UsernameValidator.validateForDisplay(""))
        assertNull(UsernameValidator.validateForSubmit("Kemal_06"))
        assertNull(UsernameValidator.validateForSubmit("İsmail Şef"))
        assertNotNull(UsernameValidator.validateForSubmit("admin"))
        assertNotNull(UsernameValidator.validateForSubmit("mal şef"))

        val taken = UsernameValidator.defaultTakenUsernames + "fatihturan"
        assertNotNull(UsernameValidator.validateForSubmit("FatihTuran", taken, currentUsername = null))
        assertNull(UsernameValidator.validateForSubmit("FatihTuran", taken, currentUsername = "fatihturan"))
    }

    @Test
    fun `local recipe engine respects vegan gluten free and allergy constraints`() {
        val result = LocalRecipeEngine.generateLocalSmartResult(
            textInput = "",
            scanMode = "FRIDGE",
            isInventoryBased = true,
            activeItemNames = listOf("Tavuk Göğsü", "Kaşar Peyniri", "Makarna", "Kırmızı Mercimek", "Patates"),
            diet = "Vegan",
            allergies = setOf("Gluten", "Laktoz", "Yumurta"),
            dislikedIngredients = ""
        )

        val recipes = result.recipeSuggestions.orEmpty()
        assertTrue(recipes.isNotEmpty())
        recipes.forEach { recipe ->
            val lower = recipe.title.lowercase()
            assertFalse("Vegan recipe should not contain Tavuk: ${recipe.title}", lower.contains("tavuk"))
            assertFalse("Vegan recipe should not contain Peynir/Kaşar: ${recipe.title}", lower.contains("kaşar") || lower.contains("peynir"))
            assertFalse("Gluten-free recipe should not contain Makarna: ${recipe.title}", lower.contains("makarna"))
        }

        val mealPlan = LocalRecipeEngine.generateLocalFallbackMealPlan(
            activeItems = emptyList(),
            diet = "Vegan",
            allergies = setOf("Gluten", "Laktoz"),
            dislikedIngredients = "nohut"
        )
        assertEquals(7, mealPlan.size)
        mealPlan.forEach { meal ->
            assertFalse(meal.ingredients.lowercase().contains("nohut"))
        }
    }

    @Test
    fun `eco metrics calculator reports zero for empty logs and separates meals vs items`() {
        val emptyMetrics = EcoMetricsCalculator.calculate(emptyList())
        assertEquals(0, emptyMetrics.sustainabilityScore)
        assertEquals(0.0, emptyMetrics.equivalentTrees, 0.001)
        assertEquals(0, emptyMetrics.totalMealsCooked)
        assertEquals(0, emptyMetrics.totalItemsRescued)

        val logs = listOf(
            WasteActionLog(itemName = "Domates", category = "Sebze & Meyve", actionType = "CONSUMED", pointsGained = 50, co2SavedKg = 0.9, cost = 30.0),
            WasteActionLog(itemName = "Yemek Tarifi: Mercimek Çorbası", category = "Yemek", actionType = "CONSUMED", pointsGained = 60, co2SavedKg = 1.5, cost = 45.0),
            WasteActionLog(itemName = "Süt", category = "Süt Ürünleri", actionType = "WASTED", pointsGained = -20, co2SavedKg = 0.0, cost = 25.0)
        )
        val metrics = EcoMetricsCalculator.calculate(logs)
        assertEquals(1, metrics.totalItemsRescued)
        assertEquals(1, metrics.totalMealsCooked)
        assertEquals(75.0, metrics.totalMoneySavedTl, 0.01)
        assertEquals(25.0, metrics.totalMoneyLostTl, 0.01)
        assertEquals(66, metrics.sustainabilityScore)
        assertTrue(metrics.totalWaterSavedLiters > 0.0)
    }

    @Test
    fun `barcode scanner does not falsely match short codes`() {
        val exact = BarcodeScannerHelper.lookupBarcode("8690504012345")
        assertTrue(exact.name.contains("Sütaş", ignoreCase = true))

        val shortCode = BarcodeScannerHelper.lookupBarcode("86")
        assertFalse("Short code '86' must not falsely match Sütaş!", shortCode.name.contains("Sütaş", ignoreCase = true))
        assertTrue(shortCode.name.contains("Barkodlu Ürün"))
    }

    @Test
    fun `gemini api client validates keys and extracts fenced json`() {
        val noKey = GeminiApiClient.resolveApiKeyStatus("", "YOUR_API_KEY_HERE")
        assertFalse(noKey.isConfigured)
        assertFalse(noKey.isValidFormat)
        assertEquals(ApiKeySource.NONE, noKey.source)

        val sampleValidKey = "AIzaSyD-ExampleValidFormatKey1234567890"
        val inAppKey = GeminiApiClient.resolveApiKeyStatus(sampleValidKey, "YOUR_API_KEY_HERE")
        assertTrue(inAppKey.isConfigured)
        assertTrue(inAppKey.isValidFormat)
        assertEquals(ApiKeySource.IN_APP, inAppKey.source)

        val fenced = """
            ```json
            {"detected_items": [], "sustainability_impact": "OK"}
            ```
        """.trimIndent()
        assertEquals("{\"detected_items\": [], \"sustainability_impact\": \"OK\"}", GeminiApiClient.extractJsonObject(fenced))
    }
}
