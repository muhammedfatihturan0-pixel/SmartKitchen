package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Calendar
import kotlin.math.roundToInt

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
    /**
     * Calendar-day difference between currentTime's local midnight and expiryDate's local midnight.
     * Negative means expired (< 0), 0 means expires today, 1 means expires tomorrow, etc.
     */
    fun getRemainingDays(currentTime: Long = System.currentTimeMillis()): Int {
        val currentMidnight = startOfDayMillis(currentTime)
        val expiryMidnight = startOfDayMillis(expiryDate)
        return ((expiryMidnight - currentMidnight) / MILLIS_PER_DAY.toDouble()).roundToInt()
    }

    fun isExpired(currentTime: Long = System.currentTimeMillis()): Boolean =
        getRemainingDays(currentTime) < 0

    fun isUrgent(currentTime: Long = System.currentTimeMillis()): Boolean =
        getRemainingDays(currentTime) <= 3 || remainingPercent <= 25

    companion object {
        private const val MILLIS_PER_DAY = 24L * 60L * 60L * 1000L

        private fun startOfDayMillis(timestamp: Long): Long {
            val cal = Calendar.getInstance()
            cal.timeInMillis = timestamp
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            return cal.timeInMillis
        }
    }
}
