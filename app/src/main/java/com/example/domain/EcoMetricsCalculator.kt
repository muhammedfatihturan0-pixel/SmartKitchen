package com.example.domain

import com.example.data.WasteActionLog
import java.util.Locale

data class DetailedEcoFinancialMetrics(
    val totalMoneySavedTl: Double,
    val totalMoneyLostTl: Double,
    val totalCo2SavedKg: Double,
    val totalWaterSavedLiters: Double,
    val totalMealsCooked: Int,
    val totalItemsRescued: Int,
    val equivalentTrees: Double,
    val sustainabilityScore: Int,
    val monthlySummaryText: String
)

object EcoMetricsCalculator {

    private val trLocale: Locale = Locale.forLanguageTag("tr")

    fun waterSavedForLog(log: WasteActionLog): Double {
        if (log.actionType != "CONSUMED") return 0.0
        val multiplier = when (log.category.lowercase(trLocale)) {
            "et & tavuk", "meat", "chicken" -> 1200.0
            "süt ürünleri", "dairy" -> 600.0
            "sebze & meyve", "vegetables", "fruits" -> 150.0
            "kiler / kuru gıda", "bakery", "grains" -> 400.0
            "yemek", "yemek planı" -> 250.0
            else -> 300.0
        }
        return log.co2SavedKg * multiplier
    }

    fun calculate(logs: List<WasteActionLog>): DetailedEcoFinancialMetrics {
        val consumedLogs = logs.filter { it.actionType == "CONSUMED" }
        val wastedLogs = logs.filter { it.actionType == "WASTED" }

        val mealLogs = consumedLogs.filter {
            it.category.equals("Yemek", ignoreCase = true) ||
                it.category.equals("Yemek Planı", ignoreCase = true)
        }
        val rescuedItemLogs = consumedLogs.filter {
            !it.category.equals("Yemek", ignoreCase = true) &&
                !it.category.equals("Yemek Planı", ignoreCase = true)
        }

        val moneySaved = consumedLogs.sumOf { log ->
            when {
                log.cost > 0.0 -> log.cost
                log.category.equals("Yemek", ignoreCase = true) ||
                    log.category.equals("Yemek Planı", ignoreCase = true) -> 45.0
                else -> 35.0
            }
        }
        val moneyLost = wastedLogs.sumOf { log ->
            if (log.cost > 0.0) log.cost else 30.0
        }
        val co2Saved = consumedLogs.sumOf { it.co2SavedKg }
        val waterSaved = consumedLogs.sumOf { waterSavedForLog(it) }

        val totalInteractions = consumedLogs.size + wastedLogs.size
        val score = if (totalInteractions == 0) {
            0
        } else {
            ((consumedLogs.size.toDouble() / totalInteractions) * 100).toInt().coerceIn(0, 100)
        }
        val equivalentTrees = if (co2Saved > 0.0) co2Saved / 21.0 else 0.0

        val summary = """
            SMART KITCHEN AI - AYLIK MUTFAK & TASARRUF RAPORU
            
            Kurtarılan Bütçe: ₺${String.format(Locale.US, "%.1f", moneySaved)}
            İsraf Kaybı: ₺${String.format(Locale.US, "%.1f", moneyLost)}
            Önlenen Karbon Salınımı: ${String.format(Locale.US, "%.1f", co2Saved)} kg CO₂
            Kurtarılan Su Ayak İzi: ${String.format(Locale.US, "%.0f", waterSaved)} Litre
            Kurtarılan Ürün: ${rescuedItemLogs.size} Adet | Sıfır Atık Yemek: ${mealLogs.size} Porsiyon
            Sürdürülebilirlik Başarı Skoru: %$score
            
            Smart Kitchen AI ile mutfakta israfı azaltın!
        """.trimIndent()

        return DetailedEcoFinancialMetrics(
            totalMoneySavedTl = moneySaved,
            totalMoneyLostTl = moneyLost,
            totalCo2SavedKg = co2Saved,
            totalWaterSavedLiters = waterSaved,
            totalMealsCooked = mealLogs.size,
            totalItemsRescued = rescuedItemLogs.size,
            equivalentTrees = equivalentTrees,
            sustainabilityScore = score,
            monthlySummaryText = summary
        )
    }
}
