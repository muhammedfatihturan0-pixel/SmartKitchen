package com.example.api

import android.graphics.Bitmap
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

data class BarcodeProductInfo(
    val barcode: String,
    val name: String,
    val category: String, // "Süt Ürünleri", "Sebze & Meyve", "Et & Tavuk", "Kiler / Kuru Gıda", "Dondurulmuş", "İçecek & Atıştırmalık", "Diğer"
    val shelfLifeDays: Int,
    val storage: String, // "Buzdolabı", "Kiler", "Dondurucu"
    val defaultQuantity: String = "1 Adet",
    val estimatedPrice: Double = 0.0
)

object BarcodeScannerHelper {

    // Comprehensive Turkish & Global Food Barcode Database
    private val barcodeCatalog = mapOf(
        "8690504012345" to BarcodeProductInfo("8690504012345", "Sütaş Tam Yağlı Süt 1L", "Süt Ürünleri", 7, "Buzdolabı", "1 Litre", 38.0),
        "8690504112233" to BarcodeProductInfo("8690504112233", "Sütaş Kaymaklı Yoğurt 1kg", "Süt Ürünleri", 14, "Buzdolabı", "1 kg", 65.0),
        "8690504556677" to BarcodeProductInfo("8690504556677", "Sütaş Süzme Peynir 500g", "Süt Ürünleri", 20, "Buzdolabı", "500g", 95.0),
        "8690637123456" to BarcodeProductInfo("8690637123456", "Pınar Beyaz Peynir 500g", "Süt Ürünleri", 15, "Buzdolabı", "500g", 110.0),
        "8690637998877" to BarcodeProductInfo("8690637998877", "Pınar Tereyağı 250g", "Süt Ürünleri", 30, "Buzdolabı", "250g", 125.0),
        "8690526001122" to BarcodeProductInfo("8690526001122", "Filiz Burgu Makarna 500g", "Kiler / Kuru Gıda", 180, "Kiler", "500g", 24.0),
        "8690526002233" to BarcodeProductInfo("8690526002233", "Barilla Spagetti No:5 500g", "Kiler / Kuru Gıda", 180, "Kiler", "500g", 42.0),
        "8690555001122" to BarcodeProductInfo("8690555001122", "Tat Domates Salçası 830g", "Kiler / Kuru Gıda", 90, "Kiler", "830g", 55.0),
        "8690750001122" to BarcodeProductInfo("8690750001122", "Duru Baldo Pirinç 1kg", "Kiler / Kuru Gıda", 180, "Kiler", "1 kg", 85.0),
        "8690750002233" to BarcodeProductInfo("8690750002233", "Duru Kırmızı Mercimek 1kg", "Kiler / Kuru Gıda", 180, "Kiler", "1 kg", 58.0),
        "8690888001122" to BarcodeProductInfo("8690888001122", "Banvit Piliç Göğüs 500g", "Et & Tavuk", 4, "Buzdolabı", "500g", 115.0),
        "8690888002233" to BarcodeProductInfo("8690888002233", "Namet Dana Kıyma 400g", "Et & Tavuk", 3, "Buzdolabı", "400g", 195.0),
        "8690999001122" to BarcodeProductInfo("8690999001122", "SuperFresh Dondurulmuş Bezelye 450g", "Dondurulmuş", 180, "Dondurucu", "450g", 48.0),
        "8690999002233" to BarcodeProductInfo("8690999002233", "SuperFresh Pizza King 4'lü", "Dondurulmuş", 180, "Dondurucu", "4 Adet", 145.0),
        "8690624001122" to BarcodeProductInfo("8690624001122", "Erikli Doğal Kaynak Suyu 5L", "Kiler / Kuru Gıda", 365, "Kiler", "5 Litre", 32.0),
        "8690111001122" to BarcodeProductInfo("8690111001122", "Kızılay Doğal Maden Suyu 6x200ml", "Kiler / Kuru Gıda", 180, "Kiler", "6'lı Paket", 45.0),
        "8690511001122" to BarcodeProductInfo("8690511001122", "Tariş Sızma Zeytinyağı 1L", "Kiler / Kuru Gıda", 365, "Kiler", "1 Litre", 340.0),
        "8690533001122" to BarcodeProductInfo("8690533001122", "Keskinoğlu Gezen Tavuk Yumurtası 10'lu", "Süt Ürünleri", 21, "Buzdolabı", "10 Adet", 68.0),
        "8690566001122" to BarcodeProductInfo("8690566001122", "Dardanel Zeytinyağlı Ton Balığı 2x160g", "Kiler / Kuru Gıda", 365, "Kiler", "2x160g", 135.0),
        "8690577001122" to BarcodeProductInfo("8690577001122", "Çaykur Rize Turist Çayı 1kg", "Kiler / Kuru Gıda", 365, "Kiler", "1 kg", 175.0)
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
        } catch (e: Exception) {
            if (continuation.isActive) {
                continuation.resume(emptyList())
            }
        }
    }

    fun lookupBarcode(barcodeRaw: String): BarcodeProductInfo {
        val cleanBarcode = barcodeRaw.trim()
        
        // Exact match
        barcodeCatalog[cleanBarcode]?.let { return it }

        // Partial match or smart lookup
        barcodeCatalog.entries.firstOrNull { cleanBarcode.contains(it.key) || it.key.contains(cleanBarcode) }?.let {
            return it.value
        }

        // Generic intelligent fallback for unrecognized barcodes
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
            estimatedPrice = 45.0
        )
    }

    fun getAllSampleBarcodes(): List<BarcodeProductInfo> = barcodeCatalog.values.toList()
}
