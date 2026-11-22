package com.example.ui

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.Kitchen
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartKitchenApp(viewModel: InventoryViewModel) {
    val context = LocalContext.current
    val isRegistered by viewModel.isRegistered.collectAsStateWithLifecycle()

    if (!isRegistered) {
        RegistrationScreen(
            viewModel = viewModel,
            onRegister = { nickname, avatar ->
                val success = viewModel.updateNickname(nickname, avatar)
                if (success) {
                    Toast.makeText(context, "Hoş geldiniz, $nickname!", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(context, "Kullanıcı adı geçersiz veya zaten alınmış!", Toast.LENGTH_SHORT).show()
                }
            }
        )
        return
    }

    var currentTab by remember { mutableStateOf("stok") } // "stok", "expiring", "shopping", "scan", "tarif", "sustainability"
    var showAddDialog by remember { mutableStateOf(false) }
    var showBarcodeDialog by remember { mutableStateOf(false) }
    var showMonthlyReportDialog by remember { mutableStateOf(false) }
    var showNotificationSheet by remember { mutableStateOf(false) }

    val activeItems by viewModel.activeItems.collectAsStateWithLifecycle()
    val actionLogs by viewModel.actionLogs.collectAsStateWithLifecycle()
    val scanUiState by viewModel.scanUiState.collectAsStateWithLifecycle()
    val currentMicroTip by viewModel.currentMicroTip.collectAsStateWithLifecycle()
    val shoppingItems by viewModel.shoppingItems.collectAsStateWithLifecycle()
    val activeCookingRecipe by viewModel.activeCookingRecipe.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    val expiringCount = remember(activeItems) {
        activeItems.count { it.isUrgent() }
    }
    val uncheckedShoppingCount = remember(shoppingItems) {
        shoppingItems.count { !it.isChecked }
    }
    val totalScore = remember(actionLogs) { actionLogs.sumOf { it.pointsGained } }
    val totalCo2Saved = remember(actionLogs) { actionLogs.sumOf { it.co2SavedKg } }

    fun handleConsumeWithUndo(item: com.example.data.InventoryItem) {
        viewModel.consumeItem(item)
        coroutineScope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            val res = snackbarHostState.showSnackbar(
                message = "${item.name} tüketildi (+50 Puan)",
                actionLabel = "Geri Al",
                duration = SnackbarDuration.Short
            )
            if (res == SnackbarResult.ActionPerformed) {
                viewModel.restoreInventoryItem(item)
            }
        }
    }

    fun handleWasteWithUndo(item: com.example.data.InventoryItem) {
        viewModel.wasteItem(item)
        coroutineScope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            val res = snackbarHostState.showSnackbar(
                message = "${item.name} israf edildi olarak kaydedildi",
                actionLabel = "Geri Al",
                duration = SnackbarDuration.Short
            )
            if (res == SnackbarResult.ActionPerformed) {
                viewModel.restoreInventoryItem(item)
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Kitchen,
                            contentDescription = "Smart Kitchen Logo",
                            tint = KitchenSage,
                            modifier = Modifier.size(28.dp)
                        )
                        Column {
                            Text(
                                "Smart Kitchen AI",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = DarkCharcoal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                "Akıllı Mutfak Asistanı",
                                fontSize = 11.sp,
                                color = Color.Gray
                            )
                        }
                    }
                },
                actions = {
                    val expirationWarnings by viewModel.expirationWarnings.collectAsStateWithLifecycle()
                    val readWarningIds by viewModel.readWarningItemIds.collectAsStateWithLifecycle()
                    val unreadWarnings = expirationWarnings.filter { it.itemId !in readWarningIds }
                    val criticalCount = unreadWarnings.count {
                        it.level == WarningLevel.CRITICAL || it.level == WarningLevel.EXPIRED
                    }

                    IconButton(
                        onClick = { showBarcodeDialog = true },
                        modifier = Modifier.testTag("top_barcode_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = "Barkod Tara",
                            tint = KitchenSage
                        )
                    }

                    IconButton(
                        onClick = { showNotificationSheet = true },
                        modifier = Modifier.testTag("notification_bell_btn")
                    ) {
                        Box {
                            Icon(
                                imageVector = if (unreadWarnings.isEmpty()) Icons.Default.NotificationsNone else Icons.Default.NotificationsActive,
                                contentDescription = "Bildirimler",
                                tint = if (criticalCount > 0) KitchenClay else if (unreadWarnings.isNotEmpty()) KitchenSage else Color.Gray
                            )
                            if (unreadWarnings.isNotEmpty()) {
                                Surface(
                                    color = AlertRed,
                                    shape = CircleShape,
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .offset(x = 4.dp, y = (-4).dp)
                                ) {
                                    Text(
                                        text = "${unreadWarnings.size}",
                                        color = Color.White,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(2.dp))

                    Surface(
                        modifier = Modifier
                            .padding(end = 10.dp)
                            .clickable { currentTab = "sustainability" }
                            .testTag("top_score_badge"),
                        color = KitchenSage.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, KitchenSage)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Eco,
                                contentDescription = "Eco Points",
                                tint = SafeGreen,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                "$totalScore Puan",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = SafeGreen
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SoftSageBg)
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = SoftSageBg,
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    selected = currentTab == "stok",
                    onClick = { currentTab = "stok" },
                    icon = { Icon(Icons.Default.Kitchen, contentDescription = "Dolabım") },
                    label = { Text("Dolabım", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, maxLines = 1) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = KitchenSage,
                        selectedTextColor = KitchenSage,
                        indicatorColor = KitchenSage.copy(alpha = 0.15f)
                    ),
                    modifier = Modifier.testTag("nav_stok")
                )

                NavigationBarItem(
                    selected = currentTab == "expiring",
                    onClick = { currentTab = "expiring" },
                    icon = {
                        BadgedBox(
                            badge = {
                                if (expiringCount > 0) {
                                    Badge(containerColor = KitchenClay) {
                                        Text("$expiringCount", color = Color.White, fontSize = 10.sp)
                                    }
                                }
                            }
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = "Tüketilecekler")
                        }
                    },
                    label = { Text("Acil", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, maxLines = 1) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = KitchenClay,
                        selectedTextColor = KitchenClay,
                        indicatorColor = KitchenClay.copy(alpha = 0.15f)
                    ),
                    modifier = Modifier.testTag("nav_expiring")
                )

                NavigationBarItem(
                    selected = currentTab == "shopping",
                    onClick = { currentTab = "shopping" },
                    icon = {
                        BadgedBox(
                            badge = {
                                if (uncheckedShoppingCount > 0) {
                                    Badge(containerColor = KitchenSage) {
                                        Text("$uncheckedShoppingCount", color = Color.White, fontSize = 10.sp)
                                    }
                                }
                            }
                        ) {
                            Icon(Icons.Default.ShoppingCart, contentDescription = "Alışveriş")
                        }
                    },
                    label = { Text("Alışveriş", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, maxLines = 1) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = KitchenSage,
                        selectedTextColor = KitchenSage,
                        indicatorColor = KitchenSage.copy(alpha = 0.15f)
                    ),
                    modifier = Modifier.testTag("nav_shopping")
                )

                NavigationBarItem(
                    selected = currentTab == "scan",
                    onClick = { currentTab = "scan" },
                    icon = { Icon(Icons.Default.ReceiptLong, contentDescription = "Tara") },
                    label = { Text("Tara", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, maxLines = 1) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = KitchenSage,
                        selectedTextColor = KitchenSage,
                        indicatorColor = KitchenSage.copy(alpha = 0.15f)
                    ),
                    modifier = Modifier.testTag("nav_scan")
                )

                NavigationBarItem(
                    selected = currentTab == "tarif",
                    onClick = { currentTab = "tarif" },
                    icon = { Icon(Icons.Default.RestaurantMenu, contentDescription = "Tarifler") },
                    label = { Text("Tarifler", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, maxLines = 1) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = KitchenSage,
                        selectedTextColor = KitchenSage,
                        indicatorColor = KitchenSage.copy(alpha = 0.15f)
                    ),
                    modifier = Modifier.testTag("nav_tarif")
                )
            }
        },
        containerColor = SoftSageBg
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 74.dp)
            ) {
                when (currentTab) {
                    "stok" -> StockTab(
                        activeItems = activeItems,
                        totalScore = totalScore,
                        onAddClick = { showAddDialog = true },
                        onOpenBarcodeScanner = { showBarcodeDialog = true },
                        onOpenMonthlyReport = { showMonthlyReportDialog = true },
                        onConsume = { handleConsumeWithUndo(it) },
                        onWaste = { handleWasteWithUndo(it) },
                        onUpdateItem = { viewModel.updateItem(it) },
                        viewModel = viewModel,
                        onNavigateToTab = { tab -> currentTab = tab }
                    )

                    "expiring" -> ExpiringSectionTab(
                        activeItems = activeItems,
                        onConsume = { handleConsumeWithUndo(it) },
                        onWaste = { handleWasteWithUndo(it) },
                        onUpdateItem = { viewModel.updateItem(it) },
                        onNavigateToRecipes = {
                            viewModel.generateRecipesFromInventory()
                            currentTab = "tarif"
                        }
                    )

                    "shopping" -> ShoppingSectionTab(viewModel = viewModel)

                    "scan" -> ScanTab(
                        scanUiState = scanUiState,
                        onScanClick = { text, bitmap, mode -> viewModel.scanReceiptTextOrImage(text, bitmap, mode) },
                        onAddToInventory = { items ->
                            viewModel.addScannedItemsToInventory(items)
                            viewModel.setScanUiStateToIdle()
                            Toast.makeText(context, "${items.size} ürün başarıyla dolaba eklendi!", Toast.LENGTH_LONG).show()
                        },
                        onReset = { viewModel.setScanUiStateToIdle() },
                        onOpenBarcodeScanner = { showBarcodeDialog = true },
                        onStartCooking = { recipe -> viewModel.startCookingMode(recipe) },
                        onNavigateToApiSettings = { currentTab = "sustainability" }
                    )

                    "tarif" -> RecipeTab(
                        activeItems = activeItems,
                        scanUiState = scanUiState,
                        viewModel = viewModel,
                        onStartCooking = { recipe -> viewModel.startCookingMode(recipe) }
                    )

                    "sustainability" -> SustainabilityTab(
                        totalScore = totalScore,
                        totalCo2Saved = totalCo2Saved,
                        actionLogs = actionLogs,
                        onClearLogs = { viewModel.clearHistory() },
                        onOpenMonthlyReport = { showMonthlyReportDialog = true },
                        viewModel = viewModel
                    )
                }
            }

            // Micro-Tip Footer Banner
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { viewModel.rotateMicroTip() }
                    .background(
                        Brush.horizontalGradient(
                            listOf(KitchenSage.copy(alpha = 0.12f), Color.White)
                        )
                    )
                    .border(1.dp, KitchenSage.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                    .padding(10.dp)
                    .testTag("micro_tip_banner")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        color = KitchenSage,
                        shape = CircleShape,
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lightbulb,
                            contentDescription = "İpucu",
                            tint = Color.White,
                            modifier = Modifier
                                .padding(6.dp)
                                .size(16.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("Pratik Mutfak İpucu", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = KitchenSage)
                            Text("• Değiştir ↻", fontSize = 9.sp, color = Color.Gray, fontWeight = FontWeight.SemiBold)
                        }
                        Text(
                            currentMicroTip,
                            fontSize = 11.sp,
                            color = DarkCharcoal,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddManualItemDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { name, qty, cat, shelfLife, storage, price ->
                viewModel.addManualItem(name, qty, cat, shelfLife, storage, 100, price)
                showAddDialog = false
                Toast.makeText(context, "$name dolaba eklendi!", Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (showBarcodeDialog) {
        BarcodeScannerDialog(
            viewModel = viewModel,
            onDismiss = { showBarcodeDialog = false },
            onProductAdded = { }
        )
    }

    if (showMonthlyReportDialog) {
        MonthlyReportDialog(
            viewModel = viewModel,
            onDismiss = { showMonthlyReportDialog = false }
        )
    }

    if (activeCookingRecipe != null) {
        InteractiveCookingDialog(
            viewModel = viewModel,
            onDismiss = { viewModel.stopCookingMode() }
        )
    }

    if (showNotificationSheet) {
        val expirationWarnings by viewModel.expirationWarnings.collectAsStateWithLifecycle()
        val readWarningIds by viewModel.readWarningItemIds.collectAsStateWithLifecycle()
        KitchenNotificationsSheet(
            warnings = expirationWarnings,
            readWarningIds = readWarningIds,
            onMarkAllAsRead = { viewModel.markAllWarningsAsRead() },
            onMarkAsRead = { viewModel.markWarningAsRead(it) },
            onDismiss = { showNotificationSheet = false }
        )
    }
}
