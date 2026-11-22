package com.example.api

import android.graphics.Bitmap
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

data class BarcodeProductInfo(
    val barcode: String,
    val name: String,
    val category: String, // "Süt Ürünleri", "Sebze & Meyve", "Et & Tavuk", "Kiler / Kuru Gıda", "Dondurulmuş", "Diğer"
    val shelfLifeDays: Int,
    val storage: String, // "Buzdolabı", "Kiler", "Dondurucu"
    val defaultQuantity: String = "1 Adet",
    val estimatedPrice: Double = 0.0,
    val sourceLabel: String = "Yerel Katalog"
)

object BarcodeScannerHelper {

    private val trLocale: Locale = Locale.forLanguageTag("tr")

    private val openFoodFactsClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()
    }

    // Comprehensive Turkish & Global Food Barcode Database
    private val barcodeCatalog = mapOf(
        "8690504012345" to BarcodeProductInfo("8690504012345", "Sütaş Tam Yağlı Süt 1L", "Süt Ürünleri", 7, "Buzdolabı", "1 Litre", 38.0),
        "8690504112233" to BarcodeProductInfo("8690504112233", "Sütaş Kaymaklı Yoğurt 1kg", "Süt Ürünleri", 14, "Buzdolabı", "1 kg", 65.0),
        "8690504556677" to BarcodeProductInfo("8690504556677", "Sütaş Süzme Peynir 500g", "Süt Ürünleri", 20, "Buzdolabı", "500g", 95.0),
        "8690637123456" to BarcodeProductInfo("8690637123456", "Pınar Beyaz Peynir 500g", "Süt Ürünleri", 15, "Buzdolabı", "500g", 110.0),
        "8690637998877" to BarcodeProductInfo("8690637998877", "Pınar Tereyağı 250g", "Süt Ürünleri", 30, "Buzdolabı", "250g", 125.0),
        "8690637000011" to BarcodeProductInfo("8690637000011", "Pınar Labne 200g", "Süt Ürünleri", 18, "Buzdolabı", "200g", 58.0),
        "8690504099887" to BarcodeProductInfo("8690504099887", "Sütaş Kaşar Peyniri 400g", "Süt Ürünleri", 25, "Buzdolabı", "400g", 145.0),
        "8690504055443" to BarcodeProductInfo("8690504055443", "Sütaş Ayran 1L", "Süt Ürünleri", 12, "Buzdolabı", "1 Litre", 34.0),
        "8690526001122" to BarcodeProductInfo("8690526001122", "Filiz Burgu Makarna 500g", "Kiler / Kuru Gıda", 180, "Kiler", "500g", 24.0),
        "8690526002233" to BarcodeProductInfo("8690526002233", "Barilla Spagetti No:5 500g", "Kiler / Kuru Gıda", 180, "Kiler", "500g", 42.0),
        "8690555001122" to BarcodeProductInfo("8690555001122", "Tat Domates Salçası 830g", "Kiler / Kuru Gıda", 90, "Kiler", "830g", 55.0),
        "8690555003344" to BarcodeProductInfo("8690555003344", "Tat Biber Salçası 550g", "Kiler / Kuru Gıda", 90, "Kiler", "550g", 68.0),
        "8690750001122" to BarcodeProductInfo("8690750001122", "Duru Baldo Pirinç 1kg", "Kiler / Kuru Gıda", 180, "Kiler", "1 kg", 85.0),
        "8690750002233" to BarcodeProductInfo("8690750002233", "Duru Kırmızı Mercimek 1kg", "Kiler / Kuru Gıda", 180, "Kiler", "1 kg", 58.0),
        "8690750003344" to BarcodeProductInfo("8690750003344", "Duru Pilavlık Bulgur 1kg", "Kiler / Kuru Gıda", 180, "Kiler", "1 kg", 46.0),
        "8690750004455" to BarcodeProductInfo("8690750004455", "Duru Nohut 1kg", "Kiler / Kuru Gıda", 365, "Kiler", "1 kg", 72.0),
        "8690888001122" to BarcodeProductInfo("8690888001122", "Banvit Piliç Göğüs 500g", "Et & Tavuk", 4, "Buzdolabı", "500g", 115.0),
        "8690888002233" to BarcodeProductInfo("8690888002233", "Namet Dana Kıyma 400g", "Et & Tavuk", 3, "Buzdolabı", "400g", 195.0),
        "8690888003344" to BarcodeProductInfo("8690888003344", "Namet Kangal Sucuk 250g", "Et & Tavuk", 21, "Buzdolabı", "250g", 185.0),
        "8690999001122" to BarcodeProductInfo("8690999001122", "SuperFresh Dondurulmuş Bezelye 450g", "Dondurulmuş", 180, "Dondurucu", "450g", 48.0),
        "8690999002233" to BarcodeProductInfo("8690999002233", "SuperFresh Pizza King 4'lü", "Dondurulmuş", 180, "Dondurucu", "4 Adet", 145.0),
        "8690999003344" to BarcodeProductInfo("8690999003344", "SuperFresh Milföy Hamuru 1kg", "Dondurulmuş", 180, "Dondurucu", "1 kg", 89.0),
        "8690624001122" to BarcodeProductInfo("8690624001122", "Erikli Doğal Kaynak Suyu 5L", "Kiler / Kuru Gıda", 365, "Kiler", "5 Litre", 32.0),
        "8690111001122" to BarcodeProductInfo("8690111001122", "Kızılay Doğal Maden Suyu 6x200ml", "Kiler / Kuru Gıda", 180, "Kiler", "6'lı Paket", 45.0),
        "8690511001122" to BarcodeProductInfo("8690511001122", "Tariş Sızma Zeytinyağı 1L", "Kiler / Kuru Gıda", 365, "Kiler", "1 Litre", 340.0),
        "8690511002233" to BarcodeProductInfo("8690511002233", "Komili Ayçiçek Yağı 2L", "Kiler / Kuru Gıda", 365, "Kiler", "2 Litre", 185.0),
        "8690533001122" to BarcodeProductInfo("8690533001122", "Keskinoğlu Gezen Tavuk Yumurtası 10'lu", "Süt Ürünleri", 21, "Buzdolabı", "10 Adet", 68.0),
        "8690566001122" to BarcodeProductInfo("8690566001122", "Dardanel Zeytinyağlı Ton Balığı 2x160g", "Kiler / Kuru Gıda", 365, "Kiler", "2x160g", 135.0),
        "8690577001122" to BarcodeProductInfo("8690577001122", "Çaykur Rize Turist Çayı 1kg", "Kiler / Kuru Gıda", 365, "Kiler", "1 kg", 175.0),
        "8690500001111" to BarcodeProductInfo("8690500001111", "Söke Buğday Unu 2kg", "Kiler / Kuru Gıda", 180, "Kiler", "2 kg", 62.0),
        "8690500002222" to BarcodeProductInfo("8690500002222", "Torku Toz Şeker 1kg", "Kiler / Kuru Gıda", 365, "Kiler", "1 kg", 48.0),
        "8690500003333" to BarcodeProductInfo("8690500003333", "Eti Lifalif Yulaf Ezmesi 500g", "Kiler / Kuru Gıda", 180, "Kiler", "500g", 54.0),
        "8690500004444" to BarcodeProductInfo("8690500004444", "Uno Tam Buğday Tost Ekmeği", "Kiler / Kuru Gıda", 7, "Kiler", "1 Paket", 45.0),
        "8690500005555" to BarcodeProductInfo("8690500005555", "İçim Kefir Sade 1L", "Süt Ürünleri", 14, "Buzdolabı", "1 Litre", 64.0)
    )

    suspend fun scanBarcodeFromBitmap(bitmap: Bitmap): List<String> = suspendCancellableCoroutine { continuation ->
        try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val scanner = BarcodeScanning.getClient()

            scanner.process(image)
                .addOnSuccessListener { barcodes ->
                    val results = barcodes.mapNotNull { it.rawValue }
                    if (continuation.isActive) {
                        continuation.resume(results)
                    }
                }
                .addOnFailureListener {
                    if (continuation.isActive) {
                        continuation.resume(emptyList())
                    }
                }
        } catch (_: Exception) {
            if (continuation.isActive) {
                continuation.resume(emptyList())
            }
        }
    }

    fun lookupLocalBarcode(barcodeRaw: String): BarcodeProductInfo? {
        val cleanBarcode = barcodeRaw.trim()
        if (cleanBarcode.isEmpty()) return null

        // Exact match
        barcodeCatalog[cleanBarcode]?.let { return it }

        // Partial match only when input is a realistic barcode (>= 8 digits)
        if (cleanBarcode.length >= 8) {
            barcodeCatalog.entries.firstOrNull {
                cleanBarcode.contains(it.key) || it.key.contains(cleanBarcode)
            }?.let { return it.value }
        }
        return null
    }

    fun lookupBarcode(barcodeRaw: String): BarcodeProductInfo {
        val cleanBarcode = barcodeRaw.trim()
        lookupLocalBarcode(cleanBarcode)?.let { return it }
        return buildFallbackProduct(cleanBarcode)
    }

    /**
     * Checks local catalog first, then queries Open Food Facts free API for real product data,
     * falling back gracefully to intelligent prefix inference when offline or not found.
     */
    suspend fun lookupBarcodeWithOnlineFallback(barcodeRaw: String): BarcodeProductInfo {
        val cleanBarcode = barcodeRaw.trim()
        lookupLocalBarcode(cleanBarcode)?.let { return it }

        if (cleanBarcode.length >= 8 && cleanBarcode.all { it.isDigit() }) {
            val onlineProduct = withContext(Dispatchers.IO) {
                fetchFromOpenFoodFacts(cleanBarcode)
            }
            if (onlineProduct != null) {
                return onlineProduct
            }
        }

        return buildFallbackProduct(cleanBarcode)
    }

    private fun fetchFromOpenFoodFacts(barcode: String): BarcodeProductInfo? {
        return try {
            val url = "https://world.openfoodfacts.org/api/v2/product/$barcode.json?fields=product_name,product_name_tr,brands,quantity,categories_tags"
            val request = Request.Builder()
                .url(url)
                .addHeader("User-Agent", "SmartKitchenAI-Android/1.0")
                .get()
                .build()

            openFoodFactsClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string().orEmpty()
                if (body.isBlank()) return null

                val root = JSONObject(body)
                if (root.optInt("status", 0) != 1) return null

                val product = root.optJSONObject("product") ?: return null
                val rawName = product.optString("product_name_tr", "").ifBlank {
                    product.optString("product_name", "")
                }.trim()
                val brand = product.optString("brands", "").split(",").firstOrNull()?.trim().orEmpty()
                val displayName = when {
                    rawName.isBlank() && brand.isBlank() -> return null
                    rawName.isBlank() -> "$brand Ürün"
                    brand.isNotBlank() && !rawName.contains(brand, ignoreCase = true) -> "$brand $rawName"
                    else -> rawName
                }

                val qty = product.optString("quantity", "").trim().ifBlank { "1 Adet" }
                val tagsArray = product.optJSONArray("categories_tags")
                val tagsText = buildString {
                    if (tagsArray != null) {
                        for (i in 0 until tagsArray.length()) {
                            append(tagsArray.optString(i, "")).append(" ")
                        }
                    }
                }.lowercase(Locale.ROOT)

                val lowerName = displayName.lowercase(trLocale)
                val (category, shelfLife, storage) = inferMetadataFromTagsAndName(tagsText, lowerName)

                BarcodeProductInfo(
                    barcode = barcode,
                    name = displayName,
                    category = category,
                    shelfLifeDays = shelfLife,
                    storage = storage,
                    defaultQuantity = qty,
                    estimatedPrice = 0.0,
                    sourceLabel = "Open Food Facts"
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun inferMetadataFromTagsAndName(tags: String, lowerName: String): Triple<String, Int, String> {
        return when {
            tags.contains("frozen") || lowerName.contains("dondurul") || lowerName.contains("dondurma") ->
                Triple("Dondurulmuş", 180, "Dondurucu")
            tags.contains("dairy") || tags.contains("milk") || tags.contains("cheese") || tags.contains("yogurt") ||
                lowerName.contains("süt") || lowerName.contains("peynir") || lowerName.contains("yoğurt") || lowerName.contains("ayran") ->
                Triple("Süt Ürünleri", 10, "Buzdolabı")
            tags.contains("meat") || tags.contains("poultry") || tags.contains("fish") ||
                lowerName.contains("tavuk") || lowerName.contains("kıyma") || lowerName.contains("sucuk") ->
                Triple("Et & Tavuk", 4, "Buzdolabı")
            tags.contains("vegetable") || tags.contains("fruit") || tags.contains("produce") ->
                Triple("Sebze & Meyve", 6, "Buzdolabı")
            else -> Triple("Kiler / Kuru Gıda", 90, "Kiler")
        }
    }

    private fun buildFallbackProduct(cleanBarcode: String): BarcodeProductInfo {
        val category = when {
            cleanBarcode.startsWith("8690504") || cleanBarcode.startsWith("8690637") -> "Süt Ürünleri"
            cleanBarcode.startsWith("8690888") -> "Et & Tavuk"
            cleanBarcode.startsWith("8690999") -> "Dondurulmuş"
            cleanBarcode.startsWith("8690526") || cleanBarcode.startsWith("8690750") -> "Kiler / Kuru Gıda"
            else -> "Kiler / Kuru Gıda"
        }

        val shelfLife = when (category) {
            "Süt Ürünleri" -> 10
            "Et & Tavuk" -> 4
            "Dondurulmuş" -> 180
            else -> 60
        }

        val storage = when (category) {
            "Dondurulmuş" -> "Dondurucu"
            "Süt Ürünleri", "Et & Tavuk" -> "Buzdolabı"
            else -> "Kiler"
        }

        return BarcodeProductInfo(
            barcode = cleanBarcode,
            name = "Barkodlu Ürün (#${cleanBarcode.takeLast(6)})",
            category = category,
            shelfLifeDays = shelfLife,
            storage = storage,
            defaultQuantity = "1 Adet",
            estimatedPrice = 0.0,
            sourceLabel = "Akıllı Tahmin"
        )
    }

    fun getAllSampleBarcodes(): List<BarcodeProductInfo> = barcodeCatalog.values.toList()
}
