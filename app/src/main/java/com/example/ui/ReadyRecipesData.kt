package com.example.ui

import com.example.api.RecipeSuggestion
import com.example.data.InventoryItem
import java.util.Locale

data class ReadyRecipe(
    val id: String,
    val title: String,
    val category: String, // "Hızlı & Pratik", "Fit & Diyet", "Çorbalar", "Kahvaltılık", "Ana Yemek"
    val emoji: String,
    val prepTime: String,
    val calories: Int,
    val protein: String,
    val carbs: String,
    val fat: String,
    val usedItems: List<String>,
    val ingredients: List<String>,
    val instructions: List<String>,
    val calorieAnalysis: String,
    val isVegan: Boolean = false,
    val isVegetarian: Boolean = false,
    val isGlutenFree: Boolean = false,
    val allergens: Set<String> = emptySet()
) {
    fun matchesPreferences(diet: String, userAllergies: Set<String>, disliked: String): Boolean {
        val tr = Locale.forLanguageTag("tr")
        val dietOk = when (diet.lowercase(tr)) {
            "vegan" -> isVegan
            "vejetaryen" -> isVegetarian
            "glütensiz", "glutensiz" -> isGlutenFree
            else -> true
        }
        if (!dietOk) return false

        val normalizedAllergies = userAllergies.map { it.lowercase(tr) }.toSet()
        if (allergens.any { it.lowercase(tr) in normalizedAllergies }) return false

        val dislikedTokens = disliked.split(",", ";", "\n")
            .map { it.trim().lowercase(tr) }
            .filter { it.length >= 2 }
        if (dislikedTokens.isNotEmpty()) {
            val corpus = "$title ${ingredients.joinToString(" ")}".lowercase(tr)
            if (dislikedTokens.any { corpus.contains(it) }) return false
        }
        return true
    }

    fun countInventoryMatches(activeItems: List<InventoryItem>): Int {
        if (activeItems.isEmpty()) return 0
        val tr = Locale.forLanguageTag("tr")
        val invNames = activeItems.map { it.name.lowercase(tr) }
        return usedItems.count { needed ->
            val tokens = needed.lowercase(tr).split("/", " ").filter { it.length >= 3 }
            tokens.any { t -> invNames.any { inv -> inv.contains(t) } }
        }
    }

    fun toRecipeSuggestion(): RecipeSuggestion = RecipeSuggestion(
        title = title,
        usedExpiringItems = usedItems,
        prepTime = prepTime,
        instructions = instructions.mapIndexed { idx, s -> "${idx + 1}. $s" }.joinToString("\n"),
        calories = calories,
        protein = protein,
        carbs = carbs,
        fat = fat,
        calorieAnalysis = calorieAnalysis
    )
}

val defaultReadyRecipes = listOf(
    ReadyRecipe(
        id = "ready_1",
        title = "Kremalı Tavuklu Makarna",
        category = "Hızlı & Pratik",
        emoji = "🍝",
        prepTime = "15 dk",
        calories = 480,
        protein = "32g",
        carbs = "54g",
        fat = "14g",
        usedItems = listOf("Tavuk", "Süt", "Makarna", "Kaşar"),
        ingredients = listOf(
            "250g Tavuk Göğsü (Küp doğranmış)",
            "1/2 paket Makarna (Penne veya Burgu)",
            "1 su bardağı Süt veya Sıvı Krema",
            "2 diş Sarımsak (İnce kıyılmış)",
            "1 yemek kaşığı Zeytinyağı veya Tereyağı",
            "Kekik, Karabiber & Tuz",
            "50g Rendelenmiş Kaşar Peyniri"
        ),
        instructions = listOf(
            "Makarnayı bol tuzlu kaynar suda 8-10 dakika haşlayıp süzün.",
            "Geniş tavada zeytinyağını ısıtın, tavuk küplerini hafif kızarana kadar soteleyin.",
            "Sarımsakları ekleyip 1 dakika çevirin, süt ve baharatları ekleyip 3 dakika kıvam aldırın.",
            "Makarnayı sosa ekleyip karıştırın, üzerine kaşar serperek sıcak servis yapın."
        ),
        calorieAnalysis = "Yüksek protein içeriği kas gelişimini ve tokluk hissini destekler.",
        isVegan = false,
        isVegetarian = false,
        isGlutenFree = false,
        allergens = setOf("Gluten", "Laktoz")
    ),
    ReadyRecipe(
        id = "ready_2",
        title = "Fırında Sebzeli Kaşarlı Mücver",
        category = "Fit & Diyet",
        emoji = "🥒",
        prepTime = "25 dk",
        calories = 220,
        protein = "14g",
        carbs = "18g",
        fat = "8g",
        usedItems = listOf("Kabak", "Havuç", "Yumurta", "Kaşar"),
        ingredients = listOf(
            "2 adet Kabak (Rendelenip suyu sıkılmış)",
            "1 adet Havuç (Rendelenmiş)",
            "2 adet Yumurta",
            "3 yemek kaşığı Un",
            "60g Rendelenmiş Kaşar veya Beyaz Peynir",
            "1/2 demet Dereotu ve Maydanoz",
            "1 yemek kaşığı Zeytinyağı, Pul Biber & Tuz"
        ),
        instructions = listOf(
            "Kabakları rendeleyip avucunuzda sıkarak suyunu tamamen çıkarın.",
            "Karıştırma kabına yumurta, kabak, havuç, un, peynir ve yeşillikleri alıp karıştırın.",
            "Yağlı kağıt serili tepsiye birer kaşık harç koyup şekil verin.",
            "190°C fırında 22-25 dakika altın sarısı olana kadar pişirin."
        ),
        calorieAnalysis = "Kızartılmadan fırında pişirildiği için düşük kalorili ve lif zengini bir öğündür.",
        isVegan = false,
        isVegetarian = true,
        isGlutenFree = false,
        allergens = setOf("Yumurta", "Gluten", "Laktoz")
    ),
    ReadyRecipe(
        id = "ready_3",
        title = "Şifalı Karışık Sebze Çorbası",
        category = "Çorbalar",
        emoji = "🥣",
        prepTime = "20 dk",
        calories = 160,
        protein = "6g",
        carbs = "26g",
        fat = "4g",
        usedItems = listOf("Domates", "Biber", "Patates", "Soğan", "Havuç"),
        ingredients = listOf(
            "2 adet yumuşamış Domates",
            "1 adet Yeşil / Kapya Biber",
            "1 adet orta boy Patates",
            "1 adet Kuru Soğan",
            "1 adet Havuç",
            "1 yemek kaşığı Zeytinyağı",
            "4 su bardağı Sıcak Su, Kuru Nane, Tuz & Karabiber"
        ),
        instructions = listOf(
            "Zeytinyağında yemeklik doğranmış soğan ve havuçları 3 dakika kavurun.",
            "Patates, biber ve domatesleri ekleyip 2 dakika daha soteleyin.",
            "Sıcak suyu ve tuzu ekleyip sebzeler yumuşayana dek 15 dakika kaynatın.",
            "Blenderdan pürüzsüz kıvama gelene kadar geçirin ve nane serpip servis yapın."
        ),
        calorieAnalysis = "Dolapta kalan sebzeleri değerlendiren antioksidan deposu, tamamen bitkisel ve glütensiz çorba.",
        isVegan = true,
        isVegetarian = true,
        isGlutenFree = true,
        allergens = emptySet()
    ),
    ReadyRecipe(
        id = "ready_4",
        title = "Fırında Çıtır Kaşarlı Ekmek Dilimleri",
        category = "Kahvaltılık",
        emoji = "🍞",
        prepTime = "10 dk",
        calories = 290,
        protein = "13g",
        carbs = "34g",
        fat = "11g",
        usedItems = listOf("Ekmek", "Kaşar", "Domates", "Tereyağı"),
        ingredients = listOf(
            "4-5 dilim Bayat Ekmek",
            "100g Kaşar Peyniri veya Beyaz Peynir",
            "1 adet Domates (Dilimlenmiş)",
            "1 adet Yeşil Biber",
            "1 tatlı kaşığı Tereyağı veya Zeytinyağı",
            "Kekik & Pul Biber"
        ),
        instructions = listOf(
            "Bayat ekmek dilimlerini fırın tepsisine dizin.",
            "Üzerlerine hafifçe tereyağı veya zeytinyağı sürün.",
            "Domates, biber ve rendelenmiş peyniri yerleştirip kekik serpin.",
            "200°C fırında 7-8 dakika peynirler kızarana dek fırınlayın."
        ),
        calorieAnalysis = "Bayat ekmek israfını sıfırlayan pratik kahvaltılık.",
        isVegan = false,
        isVegetarian = true,
        isGlutenFree = false,
        allergens = setOf("Gluten", "Laktoz")
    ),
    ReadyRecipe(
        id = "ready_5",
        title = "Geleneksel Bol Domatesli Menemen",
        category = "Kahvaltılık",
        emoji = "🍳",
        prepTime = "12 dk",
        calories = 260,
        protein = "16g",
        carbs = "12g",
        fat = "16g",
        usedItems = listOf("Domates", "Biber", "Yumurta"),
        ingredients = listOf(
            "3 adet olgun Domates (Küp doğranmış)",
            "2 adet Yeşil Köy Biberi",
            "3 adet Yumurta",
            "1 yemek kaşığı Zeytinyağı",
            "Tuz, Karabiber, Pul Biber"
        ),
        instructions = listOf(
            "Tavada yağı ısıtıp ince doğranmış biberleri 2-3 dakika soteleyin.",
            "Domatesleri ekleyin, kapağı kapatıp domatesler suyunu çekene kadar pişirin.",
            "Yumurtaları kırın, kısık ateşte istediğiniz kıvama gelene dek 3-4 dakika pişirin."
        ),
        calorieAnalysis = "Yüksek biyoyararlanımlı protein ve likopen kaynağı, glütensiz kahvaltı.",
        isVegan = false,
        isVegetarian = true,
        isGlutenFree = true,
        allergens = setOf("Yumurta")
    ),
    ReadyRecipe(
        id = "ready_6",
        title = "Fırında Kıymalı Patates Oturtma",
        category = "Ana Yemek",
        emoji = "🍲",
        prepTime = "35 dk",
        calories = 440,
        protein = "28g",
        carbs = "38g",
        fat = "18g",
        usedItems = listOf("Patates", "Kıyma", "Domates", "Biber", "Salça"),
        ingredients = listOf(
            "3 adet orta boy Patates (Halka dilimlenmiş)",
            "200g Dana Kıyma",
            "1 adet Kuru Soğan & 2 diş Sarımsak",
            "1 yemek kaşığı Domates Salçası",
            "2 adet Yeşil Biber & 1 adet Domates",
            "1 su bardağı Sıcak Su, Baharatlar"
        ),
        instructions = listOf(
            "Patates dilimlerini yağlı kağıtlı tepside 10 dakika 200°C fırında ön pişirin.",
            "Tavada soğan, kıyma, sarımsak, biber ve salçayı kavurun.",
            "Tepsiye patatesleri ve kıymalı harcı yerleştirin, domates dilimleri ekleyin.",
            "Sıcak suyu gezdirip 190°C fırında 20 dakika pişirin."
        ),
        calorieAnalysis = "Kızartma yerine fırında hazırlanarak yağı azaltılmış, glütensiz doyurucu ana yemek.",
        isVegan = false,
        isVegetarian = false,
        isGlutenFree = true,
        allergens = emptySet()
    ),
    ReadyRecipe(
        id = "ready_7",
        title = "Fit Yoğurtlu & Yulaflı Meyve Kasesi",
        category = "Fit & Diyet",
        emoji = "🥣",
        prepTime = "5 dk",
        calories = 240,
        protein = "12g",
        carbs = "36g",
        fat = "4g",
        usedItems = listOf("Yoğurt", "Muz", "Elma", "Yulaf"),
        ingredients = listOf(
            "4 yemek kaşığı Yoğurt",
            "3 yemek kaşığı Yulaf Ezmesi",
            "1 adet Doğranmış Muz veya Elma",
            "1 tatlı kaşığı Bal",
            "1 çay kaşığı Tarçın"
        ),
        instructions = listOf(
            "Geniş bir kaseye yoğurdu ekleyip pürüzsüzleştirin.",
            "Yulaf ezmesini ve doğranmış meyveleri üzerine ekleyin.",
            "Bal ve tarçın serpip hemen servis edin."
        ),
        calorieAnalysis = "Probiyotik ve çözünür lif dengesi yüksek, 5 dakikalık fit öğün.",
        isVegan = false,
        isVegetarian = true,
        isGlutenFree = false,
        allergens = setOf("Laktoz", "Gluten")
    ),
    ReadyRecipe(
        id = "ready_8",
        title = "Pratik Kırmızı Mercimek Köftesi",
        category = "Hızlı & Pratik",
        emoji = "🥗",
        prepTime = "30 dk",
        calories = 310,
        protein = "15g",
        carbs = "48g",
        fat = "6g",
        usedItems = listOf("Mercimek", "Bulgur", "Soğan", "Salça"),
        ingredients = listOf(
            "1 su bardağı Kırmızı Mercimek",
            "1 su bardağı İnce Köftelik Bulgur",
            "1 adet Kuru Soğan",
            "2 yemek kaşığı Domates & Biber Salçası",
            "1/2 çay bardağı Zeytinyağı",
            "1/2 demet Maydanoz, Kimyon, Limon Suyu"
        ),
        instructions = listOf(
            "Mercimeği 3 su bardağı suda haşlayın, bulguru ekleyip 15 dakika demlendirin.",
            "Tavada zeytinyağında soğanı ve salçayı kavurun.",
            "Demlenen bulgurlu mercimeğe salçalı sos, baharatlar ve yeşillikleri ekleyip yoğurun.",
            "Şekil verip marul ve limon eşliğinde servis yapın."
        ),
        calorieAnalysis = "Tamamen bitkisel protein ve yüksek lif kaynağı, kolesterol içermez.",
        isVegan = true,
        isVegetarian = true,
        isGlutenFree = false,
        allergens = setOf("Gluten")
    ),
    ReadyRecipe(
        id = "ready_9",
        title = "Zeytinyağlı Nohutlu Sebze Güveci",
        category = "Ana Yemek",
        emoji = "🥘",
        prepTime = "25 dk",
        calories = 330,
        protein = "13g",
        carbs = "44g",
        fat = "11g",
        usedItems = listOf("Nohut", "Kabak", "Patates", "Domates", "Biber"),
        ingredients = listOf(
            "1 su bardağı Haşlanmış Nohut",
            "1 adet Kabak, 1 adet Patates",
            "2 adet Domates & 1 adet Kapya Biber",
            "2 yemek kaşığı Zeytinyağı & Sarımsak",
            "Kekik, Kimyon, Tuz"
        ),
        instructions = listOf(
            "Tüm sebzeleri küp şeklinde doğrayın.",
            "Tencerede zeytinyağı ve sarımsağı 1 dakika çevirin.",
            "Sebzeleri, nohutu ve baharatları ekleyip 20 dakika kısık ateşte pişirin."
        ),
        calorieAnalysis = "Vegan, glütensiz ve tüm yaygın alerjenlerden arındırılmış tam protein kaynağı.",
        isVegan = true,
        isVegetarian = true,
        isGlutenFree = true,
        allergens = emptySet()
    ),
    ReadyRecipe(
        id = "ready_10",
        title = "Sütlü & Tarçınlı Sıcak Yulaf Lapası",
        category = "Fit & Diyet",
        emoji = "🌾",
        prepTime = "8 dk",
        calories = 270,
        protein = "9g",
        carbs = "46g",
        fat = "5g",
        usedItems = listOf("Yulaf", "Süt", "Muz", "Elma"),
        ingredients = listOf(
            "4 yemek kaşığı Yulaf Ezmesi",
            "1 su bardağı Süt (veya Bitkisel Süt / Su)",
            "1/2 adet Ezilmiş Muz veya Rendelenmiş Elma",
            "1 çay kaşığı Tarçın"
        ),
        instructions = listOf(
            "Küçük tencerede süt ve yulafı orta ateşte 5 dakika karıştırarak pişirin.",
            "Kaseye alıp meyve ve tarçınla süsleyerek sıcak servis edin."
        ),
        calorieAnalysis = "Bitkisel sütle vegan hazırlanabilen, uzun süre tok tutan düşük glisemik kahvaltı.",
        isVegan = false,
        isVegetarian = true,
        isGlutenFree = false,
        allergens = setOf("Laktoz", "Gluten")
    )
)
