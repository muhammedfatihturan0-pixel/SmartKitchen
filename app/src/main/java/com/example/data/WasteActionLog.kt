package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "waste_action_logs")
data class WasteActionLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val itemName: String,
    val category: String,
    val actionType: String, // "CONSUMED" or "WASTED"
    val pointsGained: Int,
    val co2SavedKg: Double, // CO2 saved in kilograms
    val cost: Double = 0.0, // logged price/cost of the item
    val timestamp: Long = System.currentTimeMillis()
)
