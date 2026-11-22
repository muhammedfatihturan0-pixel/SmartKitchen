package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.google.android.gms.ads.MobileAds
import com.example.data.AppDatabase
import com.example.data.InventoryRepository
import com.example.ui.InventoryViewModel
import com.example.ui.InventoryViewModelFactory
import com.example.ui.SmartKitchenApp
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private val database by lazy { AppDatabase.getDatabase(this) }
    private val repository by lazy { InventoryRepository(database.inventoryDao()) }
    
    private val viewModel: InventoryViewModel by viewModels {
        InventoryViewModelFactory(application, repository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            MobileAds.initialize(this) {}
        } catch (_: Exception) {}
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                SmartKitchenApp(viewModel)
            }
        }
    }
}
