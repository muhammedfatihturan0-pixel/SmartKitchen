package com.example.domain

import com.example.api.DetectedItem
import java.util.Locale

object ReceiptParser {

    private val trLocale: Locale = Locale.forLanguageTag("tr")

    private fun containsWord(text: String, word: String): Boolean {
        val pattern = Regex("(?:^|[^\\p{L}\\p{N}])${Regex.escape(word)}(?:$|[^\\p{L}\\p{N}])")
        return pattern.containsMatchIn(text)
    }

    private fun containsAnyStem(text: String, stems: List<String>): Boolean {
        return stems.any { stem ->
            val pattern = Regex("(?:^|[^\\p{L}\\p{N}])${Regex.escape(stem)}\\p{L}*")
            pattern.containsMatchIn(text)
        }
    }

    fun guessCategory(name: String): String {
        val n = name.lowercase(trLocale)
        return when {
            containsAnyStem(
                n,
                listOf("süt", "peynir", "yoğurt", "tereyağ", "krema", "ayran", "kefir", "labne", "kaşar", "lor", "yumurta")
            ) -> "Süt Ürünleri"

            containsAnyStem(
                n,
                listOf("tavuk", "piliç", "kıyma", "bonfile", "köfte", "sucuk", "sosis", "salam", "pastırma", "balık", "ton", "biftek", "pirzola", "kuşbaşı", "hindi")
            ) || containsWord(n, "et") || containsWord(n, "eti") -> "Et & Tavuk"

            containsAnyStem(
                n,
                listOf("domates", "biber", "salatalık", "marul", "soğan", "patates", "elma", "muz", "limon", "çilek", "sebze", "meyve", "havuç", "kabak", "patlıcan", "mantar", "ıspanak", "maydanoz", "dereotu", "kavun", "karpuz", "portakal", "mandalina", "armut", "üzüm", "pırasa", "karnabahar", "brokoli", "sarımsak")
            ) -> "Sebze & Meyve"

            containsAnyStem(
                n,
                listOf("dondurulmuş", "dondurma", "milföy", "nugget", "pizza")
            ) -> "Dondurulmuş"

            containsAnyStem(
                n,
                listOf("pirinç", "makarna", "şeker", "mercimek", "bulgur", "nohut", "fasulye", "bakliyat", "zeytinyağ", "ayçiçek", "salça", "konserve", "ekmek", "yulaf", "kahve", "baharat", "bisküvi", "kraker", "sirke", "bal", "reçel", "tahin", "pekmez")
            ) || containsWord(n, "un") || containsWord(n, "unu") || containsWord(n, "yağ") || containsWord(n, "yağı") || containsWord(n, "çay") || containsWord(n, "tuz") -> "Kiler / Kuru Gıda"

            else -> "Diğer"
        }
    }

    fun parseTextToDetectedItems(textInput: String, scanMode: String): List<DetectedItem> {
        if (textInput.isBlank()) return emptyList()

        val lines = textInput.split(Regex("""\r?\n|,(?!\d)"""))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        val detected = mutableListOf<DetectedItem>()

        val junkKeywords = setOf(
            "migros", "bim", "carrefour", "a101", "şok", "sok", "file", "metro", "tedi", "hakmar",
            "ticaret", "a.s.", "a.ş.", "tarih", "saat", "fiş", "fis", "no:", "no :", "z no", "ekü",
            "teşekkürler", "tesekkurler", "kdv", "topkdv", "toplam", "tutar", "nakit", "kredi", "kart",
            "pos", "onay", "matrah", "mersis", "vkn", "tckn", "kasiyer", "e-arşiv", "e-arsiv",
            "seçilen", "kamera", "fotoğrafı", "okundu", "lütfen", "analiz", "smart kitchen", "yükleniyor",
            "para ustu", "para üstü", "bilgi fişi", "mali değeri yoktur", "fatura", "ara toplam", "gunun",
            "alışveriş poşeti", "poset", "poşet"
        )

        val unitRegex = Regex(
            """^(?i)(\d+(?:[\.,]\d+)?)\s*(kg|gr|gram|g|ml|lt|litre|l|adet|ad|paket|pk|koli|kutu|demet|diş|şişe|bağ)?(?=\s|$)\s*(.+)$"""
        )

        for (line in lines) {
            val lower = line.lowercase(trLocale)

            val hasFoodUnitOrItem = containsAnyStem(lower, listOf("kg", "adet", "500g", "800g", "1l", "litr", "gram", "paket", "süt", "peynir", "tavuk", "domates", "ekmek", "yumurta")) ||
                containsWord(lower, "et")
            val isJunkHeader = junkKeywords.any { lower.contains(it) } && !hasFoodUnitOrItem
            if (isJunkHeader) continue

            var cleanLine = if (line.contains("->")) {
                line.substringBefore("->").trim()
            } else {
                line.trim()
            }

            // Remove leading list index numbers like "01 ", "1. ", "- ", "* "
            cleanLine = cleanLine.replace(Regex("""^[\-\*•]?\s*(\d{1,3}[\.\)]\s+|\d{2}\s+)"""), "").trim()
            if (cleanLine.length < 2) continue

            // Extract price from end of line (e.g., 45.50, 45,50, *120.00, %1 35.00 TL)
            var extractedPrice = 0.0
            val priceRegex = Regex("""(?i)(?:[\*%]\d+)?\s*(\d+[\.,]\d{2})(?:\s*TL|\s*₺|\s*%|\s*$)""")
            val priceMatch = priceRegex.find(cleanLine)
            if (priceMatch != null) {
                val rawPriceStr = priceMatch.groupValues[1].replace(",", ".")
                extractedPrice = rawPriceStr.toDoubleOrNull() ?: 0.0
                cleanLine = cleanLine.substring(0, priceMatch.range.first).trim()
            }

            cleanLine = cleanLine.replace(Regex("""[\*%TL₺#]+$"""), "").trim()
            if (cleanLine.length < 2 || cleanLine.none { it.isLetter() }) continue

            val match = unitRegex.find(cleanLine)
            val itemName: String
            val qtyStr: String

            if (match != null) {
                val amount = match.groupValues[1]
                val unitRaw = match.groupValues[2].ifEmpty { "Adet" }.lowercase(trLocale)
                // Order matters: check "ml" before "l", "kg" before "g", "koli" before "l"!
                val unit = when {
                    unitRaw == "kg" -> "kg"
                    unitRaw == "ml" -> "ml"
                    unitRaw == "g" || unitRaw == "gr" || unitRaw == "gram" -> "g"
                    unitRaw == "l" || unitRaw == "lt" || unitRaw == "litre" -> "Litre"
                    unitRaw == "pk" || unitRaw == "paket" -> "Paket"
                    unitRaw == "koli" -> "Koli"
                    unitRaw == "kutu" -> "Kutu"
                    unitRaw == "demet" -> "Demet"
                    unitRaw == "diş" -> "Diş"
                    unitRaw == "şişe" -> "Şişe"
                    unitRaw == "bağ" -> "Bağ"
                    else -> "Adet"
                }
                qtyStr = "$amount $unit"
                itemName = match.groupValues[3].trim()
            } else {
                qtyStr = "1 Adet"
                itemName = cleanLine
            }

            val cleanItemName = itemName
                .replace(Regex("""(?i)\b\d+(g|gr|gram|kg|l|lt|ml|li|lı|lu|lü|adet|pk)\b"""), "")
                .replace(Regex("""\s+"""), " ")
                .trim()

            val finalItemName = if (cleanItemName.length >= 2) cleanItemName else itemName

            val formattedName = finalItemName.lowercase(trLocale)
                .split(" ")
                .filter { it.isNotBlank() && it.length > 1 }
                .joinToString(" ") { word ->
                    word.replaceFirstChar { ch ->
                        if (ch.isLowerCase()) ch.titlecase(trLocale) else ch.toString()
                    }
                }

            if (formattedName.length < 2) continue

            val n = formattedName.lowercase(trLocale)
            val storage = when {
                scanMode == "FREEZER" -> "Dondurucu"
                scanMode == "PANTRY" -> "Kiler"
                containsAnyStem(n, listOf("dondurulmuş", "pizza", "buz", "milföy", "nugget", "dondurma")) -> "Dondurucu"
                containsAnyStem(
                    n,
                    listOf("pirinç", "makarna", "şeker", "mercimek", "bulgur", "nohut", "fasulye", "konserve", "salça", "baharat", "bisküvi", "kahve", "yulaf", "ekmek")
                ) || containsWord(n, "un") || containsWord(n, "yağ") || containsWord(n, "zeytinyağı") || containsWord(n, "çay") || containsWord(n, "tuz") -> "Kiler"
                else -> "Buzdolabı"
            }

            val shelfLife = when {
                storage == "Dondurucu" -> 90
                storage == "Kiler" -> 180
                containsAnyStem(n, listOf("süt", "balık")) -> 5
                containsAnyStem(n, listOf("tavuk", "piliç", "kıyma", "bonfile")) || containsWord(n, "et") -> 3
                containsAnyStem(n, listOf("domates", "salatalık", "marul", "çilek", "yeşillik", "maydanoz", "biber")) -> 6
                containsAnyStem(n, listOf("yumurta", "peynir", "yoğurt", "tereyağ", "kaşar", "labne")) -> 14
                containsAnyStem(n, listOf("elma", "portakal", "patates", "soğan", "havuç")) -> 20
                else -> 7
            }

            if (detected.none { it.name.equals(formattedName, ignoreCase = true) }) {
                detected.add(DetectedItem(formattedName, qtyStr, shelfLife, storage, extractedPrice))
            }
        }

        return detected
    }
}
