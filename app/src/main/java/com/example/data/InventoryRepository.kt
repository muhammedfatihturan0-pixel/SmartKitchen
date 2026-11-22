package com.example.data

import kotlinx.coroutines.flow.Flow

class InventoryRepository(private val inventoryDao: InventoryDao) {
    // Inventory & Logs
    val activeItems: Flow<List<InventoryItem>> = inventoryDao.getActiveInventoryItems()
    val actionLogs: Flow<List<WasteActionLog>> = inventoryDao.getAllActionLogs()

    suspend fun insertItem(item: InventoryItem) = inventoryDao.insertItem(item)
    suspend fun insertItems(items: List<InventoryItem>) = inventoryDao.insertItems(items)
    suspend fun updateItem(item: InventoryItem) = inventoryDao.updateItem(item)
    suspend fun deleteItem(item: InventoryItem) = inventoryDao.deleteItem(item)
    suspend fun deleteItemById(id: Int) = inventoryDao.deleteItemById(id)

    suspend fun insertActionLog(log: WasteActionLog) = inventoryDao.insertActionLog(log)
    suspend fun clearLogs() = inventoryDao.clearLogs()

    // Shopping List
    val shoppingItems: Flow<List<ShoppingItem>> = inventoryDao.getAllShoppingItems()
    suspend fun findShoppingItemByName(name: String) = inventoryDao.findShoppingItemByName(name)
    suspend fun insertShoppingItem(item: ShoppingItem) = inventoryDao.insertShoppingItem(item)
    suspend fun insertShoppingItems(items: List<ShoppingItem>) = inventoryDao.insertShoppingItems(items)
    suspend fun updateShoppingItem(item: ShoppingItem) = inventoryDao.updateShoppingItem(item)
    suspend fun deleteShoppingItem(item: ShoppingItem) = inventoryDao.deleteShoppingItem(item)
    suspend fun deleteShoppingItemById(id: Int) = inventoryDao.deleteShoppingItemById(id)
    suspend fun clearCheckedShoppingItems() = inventoryDao.clearCheckedShoppingItems()
    suspend fun clearAllShoppingItems() = inventoryDao.clearAllShoppingItems()

    // Meal Plans
    val mealPlans: Flow<List<MealPlanItem>> = inventoryDao.getAllMealPlans()
    suspend fun insertMealPlan(item: MealPlanItem) = inventoryDao.insertMealPlan(item)
    suspend fun insertMealPlans(items: List<MealPlanItem>) = inventoryDao.insertMealPlans(items)
    suspend fun updateMealPlan(item: MealPlanItem) = inventoryDao.updateMealPlan(item)
    suspend fun deleteMealPlan(item: MealPlanItem) = inventoryDao.deleteMealPlan(item)
    suspend fun clearMealPlans() = inventoryDao.clearMealPlans()
}
