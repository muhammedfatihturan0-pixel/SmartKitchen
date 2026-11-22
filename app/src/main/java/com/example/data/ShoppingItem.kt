package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "shopping_items")
data class ShoppingItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val quantity: String = "1 Adet",
    val category: String = "Kiler & Bakliyat", // "Sebze & Meyve", "Süt Ürünleri", "Et & Şarküteri", "Kiler & Bakliyat", "Atıştırmalık & İçecek", "Diğer"
    val isChecked: Boolean = false,
    val estimatedPrice: Double = 0.0,
    val addedFromWaste: Boolean = false,
    val addedDate: Long = System.currentTimeMillis()
)
