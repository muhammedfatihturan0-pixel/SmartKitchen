package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "inventory_items")
data class InventoryItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val quantity: String,
    val category: String, // e.g., "Sebze & Meyve", "Süt Ürünleri", "Et & Tavuk", "Kiler / Kuru Gıda", "Dondurulmuş", "Diğer"
    val addedDate: Long = System.currentTimeMillis(),
    val expiryDate: Long, // timestamp
    val storage: String,  // "Buzdolabı", "Kiler", "Dondurucu"
    val isConsumed: Boolean = false,
    val remainingPercent: Int = 100, // represents "kalan miktar" percentage
    val price: Double = 0.0 // price / cost of the item
) {
    fun getRemainingDays(currentTime: Long = System.currentTimeMillis()): Int {
        val diff = expiryDate - currentTime
        return (diff / (24 * 60 * 60 * 1000)).toInt()
    }
}
