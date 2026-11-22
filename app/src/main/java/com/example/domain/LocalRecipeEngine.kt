package com.example.domain

import com.example.api.RecipeSuggestion
import com.example.api.SmartKitchenResult
import com.example.data.InventoryItem
import com.example.data.MealPlanItem
import java.util.Locale

object LocalRecipeEngine {

    private val trLocale: Locale = Locale.forLanguageTag("tr")

    private data class OfflineRecipeTemplate(
        val title: String,
        val keyKeywords: List<String>,
        val ingredientsSummary: String,
        val prepTime: String,
        val instructions: String,
        val calories: Int,
        val protein: String,
        val carbs: String,
        val fat: String,
        val calorieAnalysis: String,
        val isVegan: Boolean,
        val isVegetarian: Boolean,
        val isGlutenFree: Boolean,
        val allergens: Set<String>
    )

    private val recipeTemplates = listOf(
        OfflineRecipeTemplate(
            title = "Sebzeli Tavuk Sote & Pilav",
            keyKeywords = listOf("tavuk", "piliç", "biber", "domates"),
            ingredientsSummary = "Tavuk Göğsü, Biber, Domates, Soğan, Zeytinyağı",
            prepTime = "20 dk",
            instructions = "1. Tavuk göğsünü ve sebzeleri küp doğrayın.\n2. Tavada zeytinyağı ile tavukları soteleyin.\n3. Sebzeleri ve baharatları ekleyip 10 dakika pişirin.",
            calories = 420,
            protein = "34g",
            carbs = "36g",
            fat = "13g",
            calorieAnalysis = "Yüksek proteinli, tok tutan dengeli ana öğün.",
            isVegan = false,
            isVegetarian = false,
            isGlutenFree = true,
            allergens = emptySet()
        ),
        OfflineRecipeTemplate(
            title = "Peynirli & Sebzeli Mutfak Omleti",
            keyKeywords = listOf("yumurta", "peynir", "kaşar", "domates", "biber"),
            ingredientsSummary = "Yumurta, Peynir, Domates, Yeşil Biber",
            prepTime = "12 dk",
            instructions = "1. Yumurtaları bir kasede çırpın, ince doğranmış sebzeleri ve peyniri ekleyin.\n2. Az yağlı tavada kapağı kapalı şekilde 6-7 dakika pişirin.\n3. Sıcak servis yapın.",
            calories = 310,
            protein = "22g",
            carbs = "8g",
            fat = "18g",
            calorieAnalysis = "Düşük karbonhidratlı, zengin proteinli hızlı öğün.",
            isVegan = false,
            isVegetarian = true,
            isGlutenFree = true,
            allergens = setOf("Yumurta", "Laktoz")
        ),
        OfflineRecipeTemplate(
            title = "Fırında Kremalı Kaşarlı Makarna",
            keyKeywords = listOf("makarna", "süt", "krema", "peynir", "kaşar"),
            ingredientsSummary = "Makarna, Süt, Kaşar Peyniri, Tereyağı",
            prepTime = "25 dk",
            instructions = "1. Makarnayı haşlayıp süzün.\n2. Süt ve rendelenmiş peynirle harmanlayıp fırın kabına alın.\n3. 180°C fırında üzeri kızarana kadar 15 dakika pişirin.",
            calories = 480,
            protein = "19g",
            carbs = "60g",
            fat = "16g",
            calorieAnalysis = "Kalsiyum ve kompleks karbonhidrat kaynağı.",
            isVegan = false,
            isVegetarian = true,
            isGlutenFree = false,
            allergens = setOf("Gluten", "Laktoz")
        ),
        OfflineRecipeTemplate(
            title = "Kırmızı Mercimek & Sebze Çorbası",
            keyKeywords = listOf("mercimek", "havuç", "patates", "soğan", "salça"),
            ingredientsSummary = "Kırmızı Mercimek, Havuç, Patates, Soğan, Zeytinyağı",
            prepTime = "20 dk",
            instructions = "1. Soğan ve havucu zeytinyağında 3 dakika kavurun.\n2. Yıkanmış mercimek, doğranmış patates ve sıcak suyu ekleyip 15 dakika haşlayın.\n3. Blenderdan geçirip nane ile servis edin.",
            calories = 240,
            protein = "12g",
            carbs = "38g",
            fat = "4g",
            calorieAnalysis = "Yüksek bitkisel protein ve lif içeren, tamamen alerjen dostu şifalı çorba.",
            isVegan = true,
            isVegetarian = true,
            isGlutenFree = true,
            allergens = emptySet()
        ),
        OfflineRecipeTemplate(
            title = "Zeytinyağlı Karışık Sebze Güveci",
            keyKeywords = listOf("patates", "domates", "biber", "kabak", "patlıcan", "havuç", "soğan"),
            ingredientsSummary = "Patates, Kabak, Biber, Domates, Soğan, Zeytinyağı",
            prepTime = "25 dk",
            instructions = "1. Dolaptaki tüm taze sebzeleri iri küpler halinde doğrayın.\n2. Zeytinyağı, sarımsak ve kekik ile harmanlayın.\n3. Tencerede kısık ateşte 20 dakika kendi suyunda pişirin.",
            calories = 260,
            protein = "6g",
            carbs = "34g",
            fat = "10g",
            calorieAnalysis = "Antioksidan ve lif zengini, %100 bitkisel ve glütensiz sıfır atık öğünü.",
            isVegan = true,
            isVegetarian = true,
            isGlutenFree = true,
            allergens = emptySet()
        ),
        OfflineRecipeTemplate(
            title = "Nohutlu & Domatesli Pirinç Pilavı",
            keyKeywords = listOf("pirinç", "nohut", "domates", "soğan", "bulgur"),
            ingredientsSummary = "Pirinç veya Bulgur, Haşlanmış Nohut, Domates, Zeytinyağı",
            prepTime = "20 dk",
            instructions = "1. Rendelenmiş domatesi zeytinyağında 2 dakika çevirin.\n2. Yıkanmış pirinci ve haşlanmış nohutu ekleyip sıcak su ilave edin.\n3. Kısık ateşte suyunu çekene kadar 15 dakika pişirin.",
            calories = 370,
            protein = "11g",
            carbs = "62g",
            fat = "8g",
            calorieAnalysis = "Bitkisel protein ve demir yönünden zengin, glütensiz ve laktozsuz doyurucu öğün.",
            isVegan = true,
            isVegetarian = true,
            isGlutenFree = true,
            allergens = emptySet()
        ),
        OfflineRecipeTemplate(
            title = "Zeytinyağlı Ton Balıklı Mutfak Salatası",
            keyKeywords = listOf("ton", "balık", "marul", "domates", "salatalık", "mısır"),
            ingredientsSummary = "Ton Balığı, Marul, Domates, Salatalık, Limon, Zeytinyağı",
            prepTime = "8 dk",
            instructions = "1. Yeşillikleri ve domatesleri doğrayıp kaseye alın.\n2. Ton balığını süzüp üzerine ekleyin.\n3. Limon suyu ve zeytinyağı gezdirip servis yapın.",
            calories = 290,
            protein = "26g",
            carbs = "12g",
            fat = "15g",
            calorieAnalysis = "Omega-3 ve yüksek protein kaynağı, hızlı ve hafif öğün.",
            isVegan = false,
            isVegetarian = false,
            isGlutenFree = true,
            allergens = setOf("Deniz Ürünleri")
        ),
        OfflineRecipeTemplate(
            title = "Fırında Baharatlı Elma Dilim Patates",
            keyKeywords = listOf("patates", "sarımsak", "kekik", "zeytinyağı"),
            ingredientsSummary = "Patates, Zeytinyağı, Sarımsak, Kekik, Biberiye",
            prepTime = "25 dk",
            instructions = "1. Patatesleri elma dilim şeklinde doğrayın.\n2. Zeytinyağı ve baharatlarla harmanlayıp tepsiye dizin.\n3. 200°C fırında 22 dakika çıtır olana kadar pişirin.",
            calories = 280,
            protein = "5g",
            carbs = "46g",
            fat = "8g",
            calorieAnalysis = "Kızartmadan hazırlanan pratik, vegan ve glütensiz atıştırmalık/yan yemek.",
            isVegan = true,
            isVegetarian = true,
            isGlutenFree = true,
            allergens = emptySet()
        )
    )

    private fun isCompatible(
        template: OfflineRecipeTemplate,
        diet: String,
        allergies: Set<String>,
        dislikedTokens: List<String>
    ): Boolean {
        val dietMatch = when (diet.lowercase(trLocale)) {
            "vegan" -> template.isVegan
            "vejetaryen" -> template.isVegetarian
            "glütensiz", "glutensiz" -> template.isGlutenFree
            else -> true
        }
        if (!dietMatch) return false

        val normalizedAllergies = allergies.map { it.lowercase(trLocale) }.toSet()
        val hasAllergen = template.allergens.any { allergen ->
            allergen.lowercase(trLocale) in normalizedAllergies
        }
        if (hasAllergen) return false

        val textCorpus = "${template.title} ${template.ingredientsSummary} ${template.instructions}".lowercase(trLocale)
        if (dislikedTokens.any { token -> token.isNotBlank() && textCorpus.contains(token) }) {
            return false
        }
        return true
    }

    fun generateLocalSmartResult(
        textInput: String,
        scanMode: String = "RECEIPT",
        isInventoryBased: Boolean = false,
        activeItemNames: List<String> = emptyList(),
        diet: String = "Hepsi",
        allergies: Set<String> = emptySet(),
        dislikedIngredients: String = ""
    ): SmartKitchenResult {
        val parsedFromInput = ReceiptParser.parseTextToDetectedItems(textInput, scanMode)
        val detectedList = if (isInventoryBased) emptyList() else parsedFromInput

        val availableNames = if (isInventoryBased) activeItemNames else detectedList.map { it.name }
        val namesLower = availableNames.map { it.lowercase(trLocale) }
        val dislikedTokens = dislikedIngredients.split(",", ";", "\n")
            .map { it.trim().lowercase(trLocale) }
            .filter { it.length >= 2 }

        val recipeSuggestionsList = if (scanMode == "RECEIPT" && !isInventoryBased) {
            emptyList()
        } else {
            val compatible = recipeTemplates.filter {
                isCompatible(it, diet, allergies, dislikedTokens)
            }.ifEmpty {
                // Ultimate universally safe fallback if user excluded almost everything
                recipeTemplates.filter { it.isVegan && it.isGlutenFree && it.allergens.isEmpty() }
            }

            val scored = compatible.sortedByDescending { tmpl ->
                tmpl.keyKeywords.count { kw -> namesLower.any { item -> item.contains(kw) } }
            }

            scored.take(2).map { tmpl ->
                val matchedItems = availableNames.filter { item ->
                    val low = item.lowercase(trLocale)
                    tmpl.keyKeywords.any { kw -> low.contains(kw) }
                }.ifEmpty {
                    tmpl.ingredientsSummary.split(",").map { it.trim() }.take(3)
                }

                RecipeSuggestion(
                    title = tmpl.title,
                    usedExpiringItems = matchedItems,
                    prepTime = tmpl.prepTime,
                    instructions = tmpl.instructions,
                    calories = tmpl.calories,
                    protein = tmpl.protein,
                    carbs = tmpl.carbs,
                    fat = tmpl.fat,
                    calorieAnalysis = tmpl.calorieAnalysis
                )
            }
        }

        return SmartKitchenResult(
            detectedItems = detectedList,
            recipeSuggestions = recipeSuggestionsList,
            sustainabilityImpact = "Envanterinizdeki ürünler diyet tercihlerinize uygun şekilde analiz edildi ve gıda israfı önlendi!",
            microTip = "Yeşillikleri kağıt havluya sararak buzdolabında saklamak tazeliğini 2 kat uzatır."
        )
    }

    fun generateLocalFallbackMealPlan(
        activeItems: List<InventoryItem>,
        diet: String = "Hepsi",
        allergies: Set<String> = emptySet(),
        dislikedIngredients: String = ""
    ): List<MealPlanItem> {
        val days = listOf("Pazartesi", "Salı", "Çarşamba", "Perşembe", "Cuma", "Cumartesi", "Pazar")
        val itemNamesLower = activeItems.map { it.name.lowercase(trLocale) }
        val dislikedTokens = dislikedIngredients.split(",", ";", "\n")
            .map { it.trim().lowercase(trLocale) }
            .filter { it.length >= 2 }

        val compatible = recipeTemplates.filter {
            isCompatible(it, diet, allergies, dislikedTokens)
        }.ifEmpty {
            recipeTemplates.filter { it.isVegan && it.isGlutenFree && it.allergens.isEmpty() }
        }

        val prioritized = compatible.sortedByDescending { tmpl ->
            tmpl.keyKeywords.count { kw -> itemNamesLower.any { name -> name.contains(kw) } }
        }

        return days.mapIndexed { idx, day ->
            val template = prioritized[idx % prioritized.size]
            MealPlanItem(
                dayOfWeek = day,
                mealType = if (day == "Cumartesi") "Öğle Yemeği" else "Akşam Yemeği",
                recipeTitle = template.title,
                ingredients = template.ingredientsSummary,
                prepTime = template.prepTime,
                calories = template.calories,
                isCooked = false,
                instructions = template.instructions
            )
        }
    }
}
