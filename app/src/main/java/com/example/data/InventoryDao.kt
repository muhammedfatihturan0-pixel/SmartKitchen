package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface InventoryDao {
    // Inventory Items
    @Query("SELECT * FROM inventory_items WHERE isConsumed = 0 ORDER BY expiryDate ASC")
    fun getActiveInventoryItems(): Flow<List<InventoryItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: InventoryItem): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<InventoryItem>)

    @Update
    suspend fun updateItem(item: InventoryItem)

    @Delete
    suspend fun deleteItem(item: InventoryItem)

    @Query("DELETE FROM inventory_items WHERE id = :id")
    suspend fun deleteItemById(id: Int)

    // Action Logs
    @Query("SELECT * FROM waste_action_logs ORDER BY timestamp DESC")
    fun getAllActionLogs(): Flow<List<WasteActionLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertActionLog(log: WasteActionLog)

    @Query("DELETE FROM waste_action_logs")
    suspend fun clearLogs()

    // Shopping List
    @Query("SELECT * FROM shopping_items ORDER BY isChecked ASC, addedDate DESC")
    fun getAllShoppingItems(): Flow<List<ShoppingItem>>

    @Query("SELECT * FROM shopping_items WHERE LOWER(name) = LOWER(:name) LIMIT 1")
    suspend fun findShoppingItemByName(name: String): ShoppingItem?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertShoppingItem(item: ShoppingItem): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertShoppingItems(items: List<ShoppingItem>)

    @Update
    suspend fun updateShoppingItem(item: ShoppingItem)

    @Delete
    suspend fun deleteShoppingItem(item: ShoppingItem)

    @Query("DELETE FROM shopping_items WHERE id = :id")
    suspend fun deleteShoppingItemById(id: Int)

    @Query("DELETE FROM shopping_items WHERE isChecked = 1")
    suspend fun clearCheckedShoppingItems()

    @Query("DELETE FROM shopping_items")
    suspend fun clearAllShoppingItems()

    // Meal Plans
    @Query("SELECT * FROM meal_plans ORDER BY id ASC")
    fun getAllMealPlans(): Flow<List<MealPlanItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMealPlan(item: MealPlanItem): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMealPlans(items: List<MealPlanItem>)

    @Update
    suspend fun updateMealPlan(item: MealPlanItem)

    @Delete
    suspend fun deleteMealPlan(item: MealPlanItem)

    @Query("DELETE FROM meal_plans")
    suspend fun clearMealPlans()
}
