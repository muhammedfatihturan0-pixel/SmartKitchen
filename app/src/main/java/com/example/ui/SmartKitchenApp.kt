package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import android.app.Application
import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.provider.MediaStore
import android.graphics.ImageDecoder
import android.os.Build
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.api.DetectedItem
import com.example.data.InventoryItem
import com.example.data.WasteActionLog
import java.text.SimpleDateFormat
import java.util.*

// Geometric Balance Theme Palette Custom Colors
val KitchenSage = Color(0xFF386B01)      // Green Balance primary
val SoftSageBg = Color(0xFFF3F4EE)       // Soft earthy background
val KitchenClay = Color(0xFFD07050)      // Clay/Terracotta secondary accent
val DarkCharcoal = Color(0xFF1B1C17)     // Deep high-contrast text
val SafeGreen = Color(0xFF386B01)        // Sustainable badge text
val WarningYellow = Color(0xFFE65100)    // High-contrast warning
val AlertRed = Color(0xFFB71C1C)         // High-contrast alert
val BorderColor = Color(0xFFDDE4D1)      // Geometric balanced border
val AccentGreenBg = Color(0xFFE8F3D6)    // Light green container
val AccentGreenBorder = Color(0xFFB7CC9D) // Light green border
val HighlightGreen = Color(0xFFD2E8B1)   // Soft green button/accent highlight
val HighlightDark = Color(0xFF141E00)    // Dark green label/text inside highlight

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
                    Toast.makeText(context, "Mutfak topluluğuna katıldınız! Hoş geldiniz, $nickname!", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(context, "Kullanıcı adı geçersiz veya zaten alınmış!", Toast.LENGTH_SHORT).show()
                }
            }
        )
    } else {
        var showAdOverlay by remember { mutableStateOf(!viewModel.hasShownStartupAd) }

        if (showAdOverlay) {
            StartupAdOverlay(onDismiss = {
                viewModel.hasShownStartupAd = true
                showAdOverlay = false
            })
        }

        var currentTab by remember { mutableStateOf("stok") } // "stok", "expiring", "shopping", "scan", "tarif", "sustainability"

        val activeItems by viewModel.activeItems.collectAsStateWithLifecycle()
        val actionLogs by viewModel.actionLogs.collectAsStateWithLifecycle()
        val scanUiState by viewModel.scanUiState.collectAsStateWithLifecycle()
        val currentMicroTip by viewModel.currentMicroTip.collectAsStateWithLifecycle()
        val shoppingList by viewModel.shoppingList.collectAsStateWithLifecycle()

        val expiringCount = remember(activeItems) {
            activeItems.count { it.getRemainingDays() in 0..3 || it.remainingPercent <= 25 }
        }

        // Calculate scores from action logs
        val totalScore = actionLogs.sumOf { it.pointsGained }
        val totalCo2Saved = actionLogs.sumOf { it.co2SavedKg }

        var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
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
                            modifier = Modifier.size(30.dp)
                        )
                        Column {
                            Text(
                                "Smart Kitchen AI",
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp,
                                color = DarkCharcoal
                            )
                            Text(
                                "Yapay Zeka Mutfak Asistanı",
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
                    val criticalCount = unreadWarnings.count { it.level == WarningLevel.CRITICAL || it.level == WarningLevel.EXPIRED }
                    var showNotificationSheet by remember { mutableStateOf(false) }

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
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }

                    if (showNotificationSheet) {
                        KitchenNotificationsSheet(
                            warnings = expirationWarnings,
                            readWarningIds = readWarningIds,
                            onMarkAllAsRead = { viewModel.markAllWarningsAsRead() },
                            onMarkAsRead = { viewModel.markWarningAsRead(it) },
                            onDismiss = { showNotificationSheet = false }
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    // Yeşil Mutfak Puanı badge displayed prominently in top right
                    Surface(
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .clickable { currentTab = "sustainability" }
                            .testTag("top_score_badge"),
                        color = KitchenSage.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, KitchenSage)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Eco,
                                contentDescription = "Eco Points",
                                tint = SafeGreen,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                "$totalScore Puan",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = SafeGreen
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SoftSageBg
                )
            )
        },
        bottomBar = {
            // Styled Bottom Bar conforming to M3 spec with dedicated tabs
            NavigationBar(
                containerColor = SoftSageBg,
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    selected = currentTab == "stok",
                    onClick = { currentTab = "stok" },
                    icon = { Icon(Icons.Default.Kitchen, contentDescription = "Dolabım") },
                    label = { Text("Dolabım", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
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
                    label = { Text("Tüketilecekler", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
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
                                if (shoppingList.isNotEmpty()) {
                                    Badge(containerColor = KitchenSage) {
                                        Text("${shoppingList.size}", color = Color.White, fontSize = 10.sp)
                                    }
                                }
                            }
                        ) {
                            Icon(Icons.Default.ShoppingCart, contentDescription = "Alışveriş")
                        }
                    },
                    label = { Text("Alışveriş", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
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
                    icon = { Icon(Icons.Default.ReceiptLong, contentDescription = "Fiş Tara") },
                    label = { Text("Fiş Tara", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
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
                    label = { Text("Tarifler", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
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
            // Main tab switching container with defensive height reservation for the micro-tip banner
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 76.dp)
            ) {
                when (currentTab) {
                    "stok" -> StockTab(
                        activeItems = activeItems,
                        totalScore = totalScore,
                        onAddClick = { showAddDialog = true },
                        onConsume = { viewModel.consumeItem(it) },
                        onWaste = { viewModel.wasteItem(it) },
                        onUpdateItem = { viewModel.updateItem(it) },
                        viewModel = viewModel,
                        onNavigateToTab = { tab -> currentTab = tab },
                        onAddToInventoryFromShopping = { itemName ->
                            viewModel.addItemFromShoppingList(itemName)
                            Toast.makeText(context, "$itemName dolaba eklendi!", Toast.LENGTH_SHORT).show()
                        }
                    )
                    "expiring" -> ExpiringSectionTab(
                        activeItems = activeItems,
                        onConsume = { viewModel.consumeItem(it) },
                        onWaste = { viewModel.wasteItem(it) },
                        onUpdateItem = { viewModel.updateItem(it) },
                        onNavigateToRecipes = { currentTab = "tarif" }
                    )
                    "shopping" -> ShoppingSectionTab(
                        viewModel = viewModel,
                        onAddToInventoryFromShopping = { itemName ->
                            viewModel.addItemFromShoppingList(itemName)
                            Toast.makeText(context, "$itemName dolaba eklendi!", Toast.LENGTH_SHORT).show()
                        }
                    )
                    "scan" -> ScanTab(
                        scanUiState = scanUiState,
                        onScanClick = { text, bitmap, mode -> viewModel.scanReceiptTextOrImage(text, bitmap, mode) },
                        onAddToInventory = { items ->
                            viewModel.addScannedItemsToInventory(items)
                            viewModel.setScanUiStateToIdle()
                            Toast.makeText(context, "${items.size} ürün başarıyla envantere eklendi!", Toast.LENGTH_LONG).show()
                        },
                        onReset = { viewModel.setScanUiStateToIdle() },
                        viewModel = viewModel
                    )
                    "tarif" -> RecipeTab(
                        activeItems = activeItems,
                        scanUiState = scanUiState,
                        onNavigateToScan = { currentTab = "scan" },
                        viewModel = viewModel
                    )
                    "sustainability" -> SustainabilityTab(
                        totalScore = totalScore,
                        totalCo2Saved = totalCo2Saved,
                        actionLogs = actionLogs,
                        onClearLogs = { viewModel.clearHistory() },
                        viewModel = viewModel
                    )
                }
            }

            // MANDATORY Micro-Tips panel anchored at the bottom-left / bottom
            // Styled like a beautiful, premium, organic helper box with a lightbulb emblem
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { viewModel.rotateMicroTip() }
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                KitchenSage.copy(alpha = 0.12f),
                                SoftSageBg
                            )
                        )
                    )
                    .border(1.dp, KitchenSage.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                    .padding(12.dp)
                    .testTag("micro_tip_banner")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        color = KitchenSage,
                        shape = CircleShape,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lightbulb,
                            contentDescription = "Micro Tip Icon",
                            tint = Color.White,
                            modifier = Modifier
                                .padding(6.dp)
                                .size(16.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                "Pratik Mutfak İpucu",
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = KitchenSage
                            )
                            Text(
                                "• Dokun ve Değiştir ↻",
                                fontSize = 9.sp,
                                color = Color.Gray,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Text(
                            currentMicroTip,
                            fontSize = 12.sp,
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

    // Manual Add Dialog
    if (showAddDialog) {
        AddManualItemDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { name, qty, cat, shelfLife, storage, price ->
                viewModel.addManualItem(name, qty, cat, shelfLife, storage, 100, price)
                showAddDialog = false
                Toast.makeText(context, "$name envantere eklendi!", Toast.LENGTH_SHORT).show()
            }
        )
    }
    }
}

// ==================== TABS IMPLEMENTATIONS ====================

@Composable
fun StockTab(
    activeItems: List<InventoryItem>,
    totalScore: Int,
    onAddClick: () -> Unit,
    onConsume: (InventoryItem) -> Unit,
    onWaste: (InventoryItem) -> Unit,
    onUpdateItem: (InventoryItem) -> Unit,
    viewModel: InventoryViewModel,
    onNavigateToTab: (String) -> Unit = {},
    onAddToInventoryFromShopping: (String) -> Unit
) {
    var currentSubTab by remember { mutableStateOf("Dolabım") } // "Dolabım", "Alışveriş Listem"
    var filterStorage by remember { mutableStateOf("Tümü") } // "Tümü", "Buzdolabı", "Kiler", "Dondurucu"
    var filterStatus by remember { mutableStateOf("Tümü") } // "Tümü", "Az Kalanlar", "Tüketilebilir", "Süresi Geçenler"
    var editingItem by remember { mutableStateOf<InventoryItem?>(null) }

    val filteredItems = remember(activeItems, filterStorage, filterStatus) {
        var items = if (filterStorage == "Tümü") activeItems
        else activeItems.filter { it.storage == filterStorage }
        
        items = when (filterStatus) {
            "Az Kalanlar" -> items.filter { it.getRemainingDays() in 0..2 }
            "Tüketilebilir" -> items.filter { it.getRemainingDays() >= 3 }
            "Süresi Geçenler" -> items.filter { it.getRemainingDays() < 0 }
            else -> items
        }
        items
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            Spacer(modifier = Modifier.height(12.dp))
            
            // Header Section
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = when (currentSubTab) {
                            "Dolabım" -> "Dolap & Kiler Envanterim"
                            "Tüketilmesi Gerekenler" -> "Acil Tüketilecekler Bölmesi"
                            else -> "Alışveriş Listem"
                        },
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = DarkCharcoal
                    )
                    Text(
                        text = when (currentSubTab) {
                            "Dolabım" -> "${activeItems.size} aktif ürün saklanıyor"
                            "Tüketilmesi Gerekenler" -> "SKT yaklaşan ve az kalan gıdalar 🌱"
                            else -> "İhtiyaçlarınızı önceden planlayın 🛒"
                        },
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }

                if (currentSubTab == "Dolabım") {
                    Button(
                        onClick = onAddClick,
                        colors = ButtonDefaults.buttonColors(containerColor = KitchenSage),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("add_item_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Ekle", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Ürün Ekle", fontSize = 12.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Sub Tab Switch Row (Material 3 Segmented Pill Style with Dedicated Tüketilecekler & Lower Alışveriş)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SoftSageBg, RoundedCornerShape(14.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf("Dolabım", "Tüketilmesi Gerekenler", "Alışveriş Listem").forEach { tab ->
                    val isSelected = currentSubTab == tab
                    val tabLabel = when (tab) {
                        "Dolabım" -> "Dolabım"
                        "Tüketilmesi Gerekenler" -> "Tüketilecekler"
                        else -> "Alışveriş"
                    }
                    val icon = when (tab) {
                        "Dolabım" -> Icons.Default.Kitchen
                        "Tüketilmesi Gerekenler" -> Icons.Default.Warning
                        else -> Icons.Default.ShoppingCart
                    }
                    val iconTint = if (isSelected) {
                        if (tab == "Tüketilmesi Gerekenler") KitchenClay else KitchenSage
                    } else Color.Gray

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) Color.White else Color.Transparent)
                            .clickable {
                                when (tab) {
                                    "Tüketilmesi Gerekenler" -> onNavigateToTab("expiring")
                                    "Alışveriş Listem" -> onNavigateToTab("shopping")
                                    else -> currentSubTab = "Dolabım"
                                }
                            }
                            .padding(vertical = 8.dp)
                            .testTag("subtab_$tab"),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = iconTint,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = tabLabel,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) DarkCharcoal else Color.Gray,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (currentSubTab == "Dolabım") {
                val expiringOrLowCount = remember(activeItems) {
                    activeItems.count { it.getRemainingDays() in 0..3 || it.remainingPercent <= 25 }
                }

                if (expiringOrLowCount > 0) {
                    Surface(
                        onClick = { onNavigateToTab("expiring") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        color = KitchenClay.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, KitchenClay.copy(alpha = 0.25f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = KitchenClay, modifier = Modifier.size(18.dp))
                                Text(
                                    text = "⚠️ $expiringOrLowCount ürün acil tüketilmeli veya az kalmış!",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = DarkCharcoal
                                )
                            }
                            Text(
                                text = "Ayrı Bölmede Gör ->",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = KitchenClay
                            )
                        }
                    }
                }

                // Impact Tracker (Geometric Balance specs)
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    color = AccentGreenBg,
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, AccentGreenBorder)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "YEŞİL MUTFAK PUANI",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = KitchenSage,
                                letterSpacing = 0.5.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    text = "$totalScore",
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Black,
                                    color = DarkCharcoal
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "+45",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = KitchenSage,
                                    modifier = Modifier.padding(bottom = 2.dp)
                                )
                            }
                        }
                        Surface(
                            color = Color.White.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.padding(2.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text("🌿", fontSize = 14.sp)
                                Text(
                                    text = "Sürdürülebilir",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = KitchenSage
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Storage Filters Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("Tümü", "Buzdolabı", "Kiler", "Dondurucu").forEach { storageOption ->
                        val isSelected = filterStorage == storageOption
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { filterStorage = storageOption }
                                .testTag("filter_btn_$storageOption"),
                            color = if (isSelected) KitchenSage else Color.White,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, if (isSelected) KitchenSage else Color.LightGray.copy(alpha = 0.5f)),
                            shadowElevation = if (isSelected) 2.dp else 0.dp
                        ) {
                            Text(
                                text = storageOption,
                                modifier = Modifier.padding(vertical = 8.dp),
                                textAlign = TextAlign.Center,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) Color.White else DarkCharcoal
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Section Header with Compact Status Filter Dropdown
                var showStatusMenu by remember { mutableStateOf(false) }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (filterStorage == "Tümü") "Tüm Ürünler" else "$filterStorage Ürünleri",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = DarkCharcoal
                    )

                    Box {
                        Surface(
                            modifier = Modifier
                                .clickable { showStatusMenu = true }
                                .testTag("status_filter_dropdown_btn"),
                            color = if (filterStatus != "Tümü") KitchenClay.copy(alpha = 0.12f) else SoftSageBg,
                            shape = RoundedCornerShape(20.dp),
                            border = BorderStroke(1.dp, if (filterStatus != "Tümü") KitchenClay else Color.LightGray.copy(alpha = 0.4f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FilterList,
                                    contentDescription = "Filter",
                                    tint = if (filterStatus != "Tümü") KitchenClay else DarkCharcoal,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = when (filterStatus) {
                                        "Az Kalanlar" -> "Az Kalanlar"
                                        "Tüketilebilir" -> "Tüketilebilir"
                                        "Süresi Geçenler" -> "Süresi Geçenler"
                                        else -> "Tüm Durumlar"
                                    },
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (filterStatus != "Tümü") KitchenClay else DarkCharcoal
                                )
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = "Expand",
                                    tint = if (filterStatus != "Tümü") KitchenClay else DarkCharcoal,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = showStatusMenu,
                            onDismissRequest = { showStatusMenu = false },
                            modifier = Modifier.background(Color.White)
                        ) {
                            listOf(
                                Pair("Tümü", "Tüm Durumlar"),
                                Pair("Az Kalanlar", "⚠️ Az Kalanlar (0-2 Gün)"),
                                Pair("Tüketilebilir", "✅ Tüketilebilir (3+ Gün)"),
                                Pair("Süresi Geçenler", "🚨 Süresi Geçenler")
                            ).forEach { (statusKey, label) ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = label,
                                            fontSize = 12.sp,
                                            fontWeight = if (filterStatus == statusKey) FontWeight.Bold else FontWeight.Normal,
                                            color = if (filterStatus == statusKey) KitchenClay else DarkCharcoal
                                        )
                                    },
                                    onClick = {
                                        filterStatus = statusKey
                                        showStatusMenu = false
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Inventory List
                if (filteredItems.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Kitchen,
                                contentDescription = "Empty stock",
                                tint = Color.LightGray,
                                modifier = Modifier.size(72.dp)
                            )
                            Text(
                                "Bu bölümde ürün bulunamadı.",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = Color.Gray
                            )
                            Text(
                                "Mutfak fişinizi taratarak veya yukarıdaki 'Ürün Ekle' butonundan el ile girerek dolabınızı doldurun!",
                                textAlign = TextAlign.Center,
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        items(filteredItems, key = { it.id }) { item ->
                            InventoryItemCard(
                                item = item,
                                onConsume = { onConsume(item) },
                                onWaste = { onWaste(item) },
                                onEditClick = { editingItem = item }
                            )
                        }
                    }
                }
            } else if (currentSubTab == "Tüketilmesi Gerekenler") {
                // ==================== DEDICATED TÜKETİLMESİ GEREKENLER SECTION ====================
                Spacer(modifier = Modifier.height(6.dp))
                
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("expiring_section_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = KitchenClay.copy(alpha = 0.08f)),
                    border = BorderStroke(1.dp, KitchenClay.copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = KitchenClay, modifier = Modifier.size(20.dp))
                            Text(
                                "🚨 Acil Tüketilmesi Gerekenler & Az Kalanlar",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = DarkCharcoal
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Bu özel bölmede son kullanma tarihi 3 gün veya daha az kalan ya da dolapta %25'in altına düşen gıdalarınız listelenir. İsrafı önlemek için öncelikli tüketebilirsiniz.",
                            fontSize = 11.sp,
                            color = Color.Gray
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                val expiringOrLowItems = remember(activeItems) {
                    activeItems.filter { it.getRemainingDays() in 0..3 || it.remainingPercent <= 25 }
                }

                if (expiringOrLowItems.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Text("🎉", fontSize = 42.sp)
                            Text(
                                "Tebrikler! Tüketilmesi gereken ürün kalmadı.",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = DarkCharcoal
                            )
                            Text(
                                "Tüm gıdalarınız taze ve ideal stok miktarına sahip.",
                                textAlign = TextAlign.Center,
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        items(expiringOrLowItems, key = { it.id }) { item ->
                            InventoryItemCard(
                                item = item,
                                onConsume = { onConsume(item) },
                                onWaste = { onWaste(item) },
                                onEditClick = { editingItem = item }
                            )
                        }
                    }
                }
            } else {
                // ==================== ALİŞVERİŞ LİSTEM SUBTAB ====================
                Spacer(modifier = Modifier.height(10.dp))
                val shoppingList by viewModel.shoppingList.collectAsStateWithLifecycle()
                var newItemText by remember { mutableStateOf("") }
                
                // Add Section
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newItemText,
                        onValueChange = { newItemText = it },
                        placeholder = { Text("Yeni ürün yazın... (Örn: Süt, Yumurta)", fontSize = 12.sp) },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .testTag("shopping_input"),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = KitchenSage,
                            unfocusedBorderColor = Color.LightGray
                        )
                    )

                    Button(
                        onClick = {
                            if (newItemText.trim().isNotEmpty()) {
                                viewModel.addToShoppingList(newItemText.trim())
                                newItemText = ""
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = KitchenSage),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .height(52.dp)
                            .testTag("shopping_add_btn")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Ekle")
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Suggestions Section
                Text(
                    "Sık Alınan Temel Gıdalar 🌱",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = DarkCharcoal
                )
                Spacer(modifier = Modifier.height(6.dp))
                
                val suggestions = listOf("Yoğurt", "Yumurta", "Domates", "Marul", "Ekmek", "Süt")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    suggestions.forEach { suggestion ->
                        Surface(
                            modifier = Modifier
                                .clickable { viewModel.addToShoppingList(suggestion) }
                                .testTag("suggested_chip_$suggestion"),
                            color = SoftSageBg,
                            border = BorderStroke(1.dp, KitchenSage.copy(alpha = 0.15f)),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Text(
                                text = "+ $suggestion",
                                fontSize = 10.sp,
                                color = KitchenSage,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Shopping list items
                if (shoppingList.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("🥗", fontSize = 36.sp)
                            Text(
                                "Alışveriş listeniz boş!",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = DarkCharcoal
                            )
                            Text(
                                "Şimdiden planlama yaparak gıda israfının önüne geçebilirsiniz.",
                                fontSize = 11.sp,
                                color = Color.Gray,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 32.dp)
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        items(shoppingList) { item ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("shopping_item_$item"),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                border = BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.3f))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ShoppingCart,
                                            contentDescription = null,
                                            tint = KitchenSage.copy(alpha = 0.6f),
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = item,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = DarkCharcoal,
                                            overflow = TextOverflow.Ellipsis,
                                            maxLines = 1
                                        )
                                    }

                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Add to Fridge/Pantry button (Very Smart UX!)
                                        IconButton(
                                            onClick = {
                                                viewModel.removeFromShoppingList(item)
                                                onAddToInventoryFromShopping(item)
                                            },
                                            modifier = Modifier
                                                .size(34.dp)
                                                .background(AccentGreenBg, RoundedCornerShape(8.dp))
                                                .testTag("add_to_fridge_$item")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Kitchen,
                                                contentDescription = "Dolaba Ekle",
                                                tint = KitchenSage,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }

                                        // Remove/Delete button
                                        IconButton(
                                            onClick = { viewModel.removeFromShoppingList(item) },
                                            modifier = Modifier
                                                .size(34.dp)
                                                .background(Color.Red.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                                                .testTag("delete_shopping_item_$item")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.DeleteOutline,
                                                contentDescription = "Sil",
                                                tint = Color.Red,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
        }

        // Edit Inventory Dialog Overlay
        editingItem?.let { itemToEdit ->
            EditInventoryItemDialog(
                item = itemToEdit,
                onDismiss = { editingItem = null },
                onSave = onUpdateItem
            )
        }
    }
}

@Composable
fun InventoryItemCard(
    item: InventoryItem,
    onConsume: () -> Unit,
    onWaste: () -> Unit,
    onEditClick: () -> Unit
) {
    val remainingDays = item.getRemainingDays()
    
    // Dynamic badge coloring to match Geometric Balance specifications
    val (badgeBgColor, badgeTextColor, badgeBorderColor) = when {
        remainingDays < 0 -> Triple(Color(0xFFEAEDF0), Color(0xFF44483D), Color(0xFFDDE4D1))
        remainingDays <= 2 -> Triple(Color(0xFFFFDAD6), Color(0xFF410002), Color(0xFFFFB4AB))
        remainingDays <= 5 -> Triple(Color(0xFFFFDBC1), Color(0xFF2B1700), Color(0xFFFFCC80))
        else -> Triple(Color(0xFFE8F3D6), Color(0xFF386B01), Color(0xFFB7CC9D))
    }

    val badgeText = when {
        remainingDays < 0 -> "Süresi Geçti!"
        remainingDays == 0 -> "Bugün Tüketilmeli!"
        remainingDays == 1 -> "1 Gün Kaldı - Acil!"
        remainingDays == 2 -> "2 Gün Kaldı - Acil Tüketilmeli"
        else -> "$remainingDays Gün Kaldı"
    }

    val storageIcon = when (item.storage) {
        "Buzdolabı" -> Icons.Default.Kitchen
        "Dondurucu" -> Icons.Default.AcUnit
        else -> Icons.Outlined.Store
    }

    val indicatorColor = when {
        remainingDays < 0 -> Color.Gray
        remainingDays <= 2 -> Color(0xFFD32F2F) // Kırmızı (Az Kaldı)
        remainingDays <= 5 -> Color(0xFFFBC02D) // Sarı/Turuncu (Orta)
        else -> Color(0xFF388E3C) // Yeşil (Tüketilebilir)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("item_card_${item.name}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, BorderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            // Left color-coded vertical indicator bar
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .fillMaxHeight()
                    .background(indicatorColor)
            )

            Column(modifier = Modifier.weight(1f).padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Item details
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                item.name,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = DarkCharcoal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            // Category pill
                            Surface(
                                color = KitchenSage.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    item.category,
                                    fontSize = 9.sp,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    color = KitchenSage,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                "Miktar: ${item.quantity}",
                                fontSize = 12.sp,
                                color = Color.Gray,
                                fontWeight = FontWeight.Medium
                            )
                            
                            Text("•", color = Color.LightGray)

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = storageIcon,
                                    contentDescription = item.storage,
                                    tint = Color.Gray,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    item.storage,
                                    fontSize = 11.sp,
                                    color = Color.Gray
                                )
                            }

                            if (item.price > 0.0) {
                                Text("•", color = Color.LightGray)
                                Text(
                                    "Değer: ₺${String.format(Locale.US, "%.1f", item.price)}",
                                    fontSize = 11.sp,
                                    color = Color(0xFFC15C3D),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Color Coded Days Remaining Badge
                    Surface(
                        color = badgeBgColor,
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, badgeBorderColor)
                    ) {
                        Text(
                            text = badgeText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = badgeTextColor,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                // Beautiful Kalan Miktar (Remaining Amount) Progress Bar
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Kalan Miktar: %${item.remainingPercent}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = KitchenSage
                    )
                    LinearProgressIndicator(
                        progress = item.remainingPercent / 100f,
                        modifier = Modifier
                            .weight(1f)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = if (item.remainingPercent <= 25) AlertRed else if (item.remainingPercent <= 50) KitchenClay else KitchenSage,
                        trackColor = SoftSageBg
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                Divider(color = Color.LightGray.copy(alpha = 0.3f))
                Spacer(modifier = Modifier.height(10.dp))

                // Action Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Manual Adjust / Edit Button
                    OutlinedButton(
                        onClick = onEditClick,
                        modifier = Modifier
                            .weight(1.1f)
                            .height(38.dp)
                            .testTag("edit_btn_${item.name}"),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = KitchenSage),
                        border = BorderStroke(1.dp, KitchenSage),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = "Düzenle", modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Düzenle", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    // Mark Wasted Button
                    OutlinedButton(
                        onClick = onWaste,
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                            .testTag("waste_btn_${item.name}"),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Gray),
                        border = BorderStroke(1.dp, Color.LightGray),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Boşalt", modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("İsraf Oldu", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    // Mark Consumed Button (Positive action)
                    Button(
                        onClick = onConsume,
                        modifier = Modifier
                            .weight(1.4f)
                            .height(38.dp)
                            .testTag("consume_btn_${item.name}"),
                        colors = ButtonDefaults.buttonColors(containerColor = KitchenSage),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = "Tüket", modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Tüketildi", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun ScanTab(
    scanUiState: ScanUiState,
    onScanClick: (String, Bitmap?, String) -> Unit,
    onAddToInventory: (List<DetectedItem>) -> Unit,
    onReset: () -> Unit,
    viewModel: InventoryViewModel
) {
    var rawText by remember { mutableStateOf("") }
    var selectedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var showCameraViewfinder by remember { mutableStateOf(false) }
    var mockReceiptStore by remember { mutableStateOf("MİGROS") }
    var activeScanMode by remember { mutableStateOf("RECEIPT") }
    var editableDetectedItems by remember { mutableStateOf<List<DetectedItem>?>(null) }
    var editingIndex by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(scanUiState) {
        if (scanUiState is ScanUiState.Success) {
            editableDetectedItems = scanUiState.result.detectedItems
        } else {
            editableDetectedItems = null
        }
    }

    val context = LocalContext.current

    // Helper function to generate high-fidelity paper receipts as in-memory Bitmap
    fun generateReceiptMockBitmap(storeName: String, itemsText: String): Bitmap {
        val width = 600
        val height = 800
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        val paint = android.graphics.Paint()
        
        // Creamy receipt paper background
        paint.color = android.graphics.Color.parseColor("#FFFEF5")
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        
        // Dashed borders
        paint.color = android.graphics.Color.parseColor("#CCCCCC")
        paint.strokeWidth = 3f
        paint.style = android.graphics.Paint.Style.STROKE
        paint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(12f, 12f), 0f)
        canvas.drawLine(15f, 30f, width - 15f, 30f, paint)
        canvas.drawLine(15f, height - 30f, width - 15f, height - 30f, paint)
        
        // Store Name Title
        paint.style = android.graphics.Paint.Style.FILL
        paint.pathEffect = null
        paint.color = android.graphics.Color.parseColor("#22251D")
        paint.textSize = 34f
        paint.isFakeBoldText = true
        canvas.drawText(storeName + " MULTIPORT", 60f, 100f, paint)
        
        // Subtitle
        paint.textSize = 20f
        paint.isFakeBoldText = false
        paint.color = android.graphics.Color.GRAY
        canvas.drawText("SMART KITCHEN AI - MULTIMODAL CAPTURE", 60f, 140f, paint)
        val formattedDate = SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.getDefault()).format(Date())
        canvas.drawText("TARIH: $formattedDate", 60f, 175f, paint)
        
        // Line divider
        paint.color = android.graphics.Color.parseColor("#DDE4D1")
        paint.strokeWidth = 2f
        canvas.drawLine(60f, 205f, width - 60f, 205f, paint)
        
        // List items
        paint.color = android.graphics.Color.parseColor("#1B1C17")
        paint.textSize = 19f
        var y = 250f
        itemsText.split("\n").forEach { line ->
            if (line.trim().isNotEmpty()) {
                canvas.drawText(line.trim(), 60f, y, paint)
                y += 38f
            }
        }
        
        // Eco signature seal
        paint.color = android.graphics.Color.parseColor("#386B01") // KitchenSage
        paint.textSize = 22f
        paint.isFakeBoldText = true
        canvas.drawText("★ SMART KITCHEN AI MULTIMODAL READ ★", 80f, height - 90f, paint)
        
        return bitmap
    }

    // Gallery Picker launcher
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            try {
                val bitmap = if (Build.VERSION.SDK_INT < 28) {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                } else {
                    val source = ImageDecoder.createSource(context.contentResolver, uri)
                    ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    }
                }
                selectedBitmap = bitmap
                rawText = when (activeScanMode) {
                    "PANTRY" -> "Seçilen kiler odası görseli galeri üzerinden okundu."
                    "FREEZER" -> "Seçilen derin dondurucu görseli galeri üzerinden okundu."
                    else -> "Seçilen market fişi görseli galeri üzerinden okundu."
                }
                val scanPrompt = when (activeScanMode) {
                    "PANTRY" -> "Lütfen bu kiler odası fotoğrafını okuyup gıda ürünlerini ve saklama günlerini analiz et."
                    "FREEZER" -> "Lütfen bu derin dondurucu fotoğrafını okuyup gıda ürünlerini ve saklama günlerini analiz et."
                    else -> "Lütfen bu market fişi fotoğrafını okuyup gıda ürünlerini ve saklama günlerini analiz et."
                }
                // Automatically execute multimodal read
                onScanClick(scanPrompt, bitmap, activeScanMode)
            } catch (e: Exception) {
                Toast.makeText(context, "Görsel yüklenirken hata oluştu: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Real Device Camera Launcher
    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            selectedBitmap = bitmap
            rawText = when (activeScanMode) {
                "PANTRY" -> "Kamera ile çekilen kiler odası fotoğrafı okundu."
                "FREEZER" -> "Kamera ile çekilen dondurucu fotoğrafı okundu."
                else -> "Kamera ile çekilen market fişi fotoğrafı okundu."
            }
            val scanPrompt = when (activeScanMode) {
                "PANTRY" -> "Lütfen bu kiler odası fotoğrafını okuyup gıda ürünlerini ve saklama günlerini analiz et."
                "FREEZER" -> "Lütfen bu dondurucu fotoğrafını okuyup gıda ürünlerini ve saklama günlerini analiz et."
                else -> "Lütfen bu market fişi fotoğrafını okuyup gıda ürünlerini ve saklama günlerini analiz et."
            }
            Toast.makeText(context, "Kamera fotoğrafı çekildi, yapay zeka analiz ediyor...", Toast.LENGTH_SHORT).show()
            onScanClick(scanPrompt, bitmap, activeScanMode)
        } else {
            Toast.makeText(context, "Kamera fotoğrafı çekilmedi.", Toast.LENGTH_SHORT).show()
        }
    }

    // Camera Permission Launcher
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            try {
                takePictureLauncher.launch(null)
            } catch (e: Exception) {
                Toast.makeText(context, "Kamera açılırken hata: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "Fiş ve gıda taraması için kamera izni verilmelidir.", Toast.LENGTH_LONG).show()
        }
    }

    fun triggerCameraScan() {
        val permissionCheck = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
        if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
            try {
                takePictureLauncher.launch(null)
            } catch (e: Exception) {
                Toast.makeText(context, "Kamera başlatılamadı: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // Preloaded Market Receipts for simulation and camera generation
    val preloadedReceipts = listOf(
        Pair(
            "MİGROS - Sebze & Tavuk Fişi",
            """
            MIGROS TICARET A.S.
            TARİH: 20-07-2026
            1 ADET PILIC BONFILE 800G -> 1 adet, Et & Tavuk
            2 KG DOMATES -> 2 kg, Sebze & Meyve
            1 ADET MARUL -> 1 adet, Sebze & Meyve
            500G SÜZME PEYNİR -> 500g, Süt Ürünleri
            TEŞEKKÜRLER.
            """.trimIndent()
        ),
        Pair(
            "BİM - Kahvaltılık Süt Fişi",
            """
            BIM BIRLESİK MAGAZALAR A.S.
            FIS NO: 0231
            1L YARIM YAGLI SUT -> 1 Litre, Süt Ürünleri
            500G TEREYAGI -> 500g, Süt Ürünleri
            1 ADET YUMURTA 15LI -> 15 adet, Diğer
            2 ADET HAZIR PIZZA -> 2 adet, Dondurulmuş
            KDV TOPLAM
            """.trimIndent()
        ),
        Pair(
            "CARREFOUR - Kuru Gıda Kiler Fişi",
            """
            CARREFOURSA MARKET
            1KG BALDO PIRINC -> 1 kg, Kiler / Kuru Gıda
            1KG OSMANCIK MERCIMEK -> 1 kg, Kiler / Kuru Gıda
            2 ADET MAKARNA SPAGETTI -> 2 adet, Kiler / Kuru Gıda
            1KG LİMON -> 1 kg, Sebze & Meyve
            """.trimIndent()
        )
    )

    Box(modifier = Modifier.fillMaxSize()) {
        // --- Regular Scan Tab Dashboard View ---
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp)
        ) {
            item {
                Column {
                    Text(
                        "Market Fişi Fotoğrafı Okuyucu",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = DarkCharcoal
                    )
                    Text(
                        "Market alışverişinizin fiş fotoğrafını çekerek veya galeriden yükleyerek yapay zekanın doğrudan okumasını sağlayın.",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
            }

            // Display Selector Dashboard when state is Idle
            if (scanUiState is ScanUiState.Idle) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, BorderColor)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Text(
                                "Tarama Modu Seçin:",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = DarkCharcoal,
                                modifier = Modifier.align(Alignment.Start)
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val modes = listOf(
                                    Triple("RECEIPT", "Market Fişi", Icons.Default.Receipt),
                                    Triple("PANTRY", "Kiler Odası", Icons.Default.Store),
                                    Triple("FREEZER", "Derin Dondurucu", Icons.Default.AcUnit)
                                )
                                modes.forEach { (modeCode, modeLabel, modeIcon) ->
                                    val isSelected = activeScanMode == modeCode
                                    Surface(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { activeScanMode = modeCode },
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (isSelected) KitchenSage else SoftSageBg,
                                        border = if (isSelected) null else BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.5f))
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(vertical = 8.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Icon(
                                                imageVector = modeIcon,
                                                contentDescription = modeLabel,
                                                tint = if (isSelected) Color.White else DarkCharcoal,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = modeLabel,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isSelected) Color.White else DarkCharcoal
                                            )
                                        }
                                    }
                                }
                            }

                            Divider(color = Color.LightGray.copy(alpha = 0.3f))

                            Text(
                                text = when (activeScanMode) {
                                    "PANTRY" -> "Kiler Odası Görseli Yükleyin"
                                    "FREEZER" -> "Dondurucu Görseli Yükleyin"
                                    else -> "Fiş Fotoğrafı Yükleyin"
                                },
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = DarkCharcoal
                            )
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Camera Button
                                Button(
                                    onClick = { triggerCameraScan() },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(90.dp)
                                        .testTag("camera_shutter_launcher"),
                                    colors = ButtonDefaults.buttonColors(containerColor = AccentGreenBg),
                                    shape = RoundedCornerShape(14.dp),
                                    border = BorderStroke(1.dp, AccentGreenBorder)
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(Icons.Default.CameraAlt, contentDescription = "Camera", tint = KitchenSage, modifier = Modifier.size(28.dp))
                                        Text("Kamera ile Çek", color = KitchenSage, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                // Gallery Button
                                Button(
                                    onClick = { galleryLauncher.launch("image/*") },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(90.dp)
                                        .testTag("gallery_picker_launcher"),
                                    colors = ButtonDefaults.buttonColors(containerColor = SoftSageBg),
                                    shape = RoundedCornerShape(14.dp),
                                    border = BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.5f))
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(Icons.Default.PhotoLibrary, contentDescription = "Gallery", tint = DarkCharcoal, modifier = Modifier.size(28.dp))
                                        Text("Galeriden Seç", color = DarkCharcoal, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }



                // Text Fallback Input block (still present if manual input is desired)
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.4f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                "Alternatif: Alışveriş Listesi Girişi",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = DarkCharcoal
                            )
                            Spacer(modifier = Modifier.height(6.dp))

                            OutlinedTextField(
                                value = rawText,
                                onValueChange = { rawText = it },
                                placeholder = { Text("Örn: 2 kg domates, 1 paket kaşar peyniri, 1 kg tavuk göğsü...", fontSize = 12.sp) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(90.dp)
                                    .testTag("receipt_input_field"),
                                textStyle = TextStyle(fontSize = 12.sp),
                                shape = RoundedCornerShape(10.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = KitchenSage,
                                    unfocusedBorderColor = Color.LightGray
                                )
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Button(
                                onClick = {
                                    if (rawText.trim().isEmpty()) {
                                        Toast.makeText(context, "Lütfen fiş içeriği girin!", Toast.LENGTH_SHORT).show()
                                    } else {
                                        onScanClick(rawText, null, activeScanMode)
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(40.dp)
                                    .testTag("run_ocr_button"),
                                colors = ButtonDefaults.buttonColors(containerColor = KitchenSage),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = "Scan", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Metni Analiz Et", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }

            // Visual Photo/Bitmap thumbnail preview being parsed
            selectedBitmap?.let { bmp ->
                if (scanUiState !is ScanUiState.Idle) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = BorderStroke(1.dp, BorderColor)
                        ) {
                            Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Doğrudan Okunan Fiş Görseli", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = DarkCharcoal)
                                Spacer(modifier = Modifier.height(6.dp))
                                androidx.compose.foundation.Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = "Receipt Image Preview",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(180.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.White)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Yapay Zeka bu görseli doğrudan çoklu-modlu (multimodal) analiz ediyor.", fontSize = 9.sp, color = Color.Gray)
                            }
                        }
                    }
                }
            }

            // Handle states
            when (scanUiState) {
                is ScanUiState.Loading -> {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(36.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                CircularProgressIndicator(
                                    color = KitchenSage,
                                    strokeWidth = 4.dp,
                                modifier = Modifier.size(54.dp)
                            )
                            Text(
                                "Yapay Zeka Fişi Çözümlüyor...",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = KitchenSage
                            )
                            Text(
                                "Gıdalar tanımlanıyor, standart raf ömürlerine göre SKT'ler hesaplanıyor ve israfı önleyecek yeşil mutfak puanları atanıyor...",
                                fontSize = 11.sp,
                                color = Color.Gray,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            is ScanUiState.Success -> {
                val result = scanUiState.result
                val currentItemsList = editableDetectedItems ?: emptyList()

                // Detected Items list
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, KitchenSage.copy(alpha = 0.3f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "Tespit Edilen Gıdalar & Otomatik SKT",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = KitchenSage
                                    )
                                    Text(
                                        "${currentItemsList.size} yeni gıda tanımlandı (Düzenlemek için ✎ simgesine dokunun)",
                                        fontSize = 10.sp,
                                        color = Color.Gray
                                    )
                                }

                                Button(
                                    onClick = { onAddToInventory(currentItemsList) },
                                    colors = ButtonDefaults.buttonColors(containerColor = SafeGreen),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    modifier = Modifier.testTag("add_to_inventory_submit")
                                ) {
                                    Text("Hepsini Envantere Ekle", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            currentItemsList.forEachIndexed { index, item ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 5.dp)
                                        .background(SoftSageBg, RoundedCornerShape(8.dp))
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Detected",
                                        tint = KitchenSage,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(item.name, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = DarkCharcoal)
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text("Miktar: ${item.quantity} | Saklama: ${item.storage}", fontSize = 10.sp, color = Color.Gray)
                                            if (item.price != null && item.price > 0.0) {
                                                Text("•", fontSize = 10.sp, color = Color.LightGray)
                                                Text("Değer: ₺${String.format(Locale.US, "%.1f", item.price)}", fontSize = 10.sp, color = KitchenClay, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                    
                                    Surface(
                                        color = KitchenClay.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            "${item.estimatedShelfLifeDays} Gün Taze Kalır",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = KitchenClay,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(4.dp))

                                    IconButton(
                                        onClick = { editingIndex = index },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "Düzenle",
                                            tint = KitchenSage,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // AI Recipe suggestions
                if (!result.recipeSuggestions.isNullOrEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.5f))
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.RestaurantMenu,
                                        contentDescription = null,
                                        tint = KitchenSage,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        "İsrafı Önleyici Tarif Önerisi",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = DarkCharcoal
                                    )
                                }

                                result.recipeSuggestions.forEach { recipe ->
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        recipe.title,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = KitchenSage
                                    )
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        modifier = Modifier.padding(vertical = 2.dp)
                                    ) {
                                        Text(
                                            "⏱️ Hazırlama: ${recipe.prepTime}",
                                            fontSize = 11.sp,
                                            color = Color.Gray,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            "🔥 Kurtarılan: ${recipe.usedExpiringItems.joinToString(", ")}",
                                            fontSize = 11.sp,
                                            color = KitchenClay,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    recipe.instructions?.let {
                                        if (it.isNotEmpty()) {
                                            Text(
                                                it,
                                                fontSize = 11.sp,
                                                color = DarkCharcoal,
                                                modifier = Modifier
                                                    .padding(top = 4.dp)
                                                    .background(SoftSageBg, RoundedCornerShape(6.dp))
                                                    .padding(8.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Sustainability Impact
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = SafeGreen.copy(alpha = 0.08f)),
                        border = BorderStroke(1.dp, SafeGreen.copy(alpha = 0.3f))
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Eco, contentDescription = "CO2 Impact", tint = SafeGreen, modifier = Modifier.size(24.dp))
                            Column {
                                Text("Yeşil Sürdürülebilirlik Etkisi", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = SafeGreen)
                                Text(
                                    result.sustainabilityImpact ?: "Gıdaları bozulmadan kurtararak çevreye katkıda bulundunuz!",
                                    fontSize = 11.sp,
                                    color = DarkCharcoal,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                // Reset button
                item {
                    OutlinedButton(
                        onClick = onReset,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("reset_ocr_button"),
                        border = BorderStroke(1.dp, KitchenSage),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Yeni Fiş Okut", color = KitchenSage, fontWeight = FontWeight.Bold)
                    }
                }
            }

            is ScanUiState.Error -> {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = AlertRed.copy(alpha = 0.08f)),
                        border = BorderStroke(1.dp, AlertRed.copy(alpha = 0.3f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.Error, contentDescription = "Error", tint = AlertRed)
                                Text("Bir Sorun Oluştu!", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = AlertRed)
                            }
                            Text(
                                (scanUiState as ScanUiState.Error).message,
                                fontSize = 12.sp,
                                color = DarkCharcoal
                            )

                            // Helper diagnostic block
                            Divider(color = AlertRed.copy(alpha = 0.2f))
                            Text(
                                "💡 Çözüm: Google AI Studio Secrets sekmesinden 'GEMINI_API_KEY' değerini tanımlayın (örn: AIzaSy... veya AQ...).",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.DarkGray
                            )

                            Button(
                                onClick = onReset,
                                colors = ButtonDefaults.buttonColors(containerColor = AlertRed),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.align(Alignment.End).testTag("dismiss_error_btn")
                            ) {
                                Text("Tamam", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
            is ScanUiState.Idle -> {
                // Handled above the when-block
            }
        }
    }

    // --- Live High-Fidelity Viewfinder Camera Simulator Overlay ---
    if (showCameraViewfinder) {
        val infiniteTransition = rememberInfiniteTransition(label = "laser")
        val laserOffset by infiniteTransition.animateFloat(
            initialValue = 0.05f,
            targetValue = 0.95f,
            animationSpec = infiniteRepeatable(
                animation = tween(2000, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "laser_y"
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(20.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Camera top HUD
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { showCameraViewfinder = false }) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Text("FIŞ KAMERASI (LIVE)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Icon(Icons.Default.FlashOn, contentDescription = "Flash", tint = Color.Yellow)
            }

            // Viewfinder aspect ratio box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.75f)
                    .border(2.dp, Color.White.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF1E1E1E))
            ) {
                // Viewfinder corner guidelines
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    val lineLen = 40.dp.toPx()
                    val strokeW = 4.dp.toPx()
                    val greenColor = KitchenSage // #386B01

                    // Top Left
                    drawLine(greenColor, androidx.compose.ui.geometry.Offset(20f, 20f), androidx.compose.ui.geometry.Offset(20f + lineLen, 20f), strokeW)
                    drawLine(greenColor, androidx.compose.ui.geometry.Offset(20f, 20f), androidx.compose.ui.geometry.Offset(20f, 20f + lineLen), strokeW)

                    // Top Right
                    drawLine(greenColor, androidx.compose.ui.geometry.Offset(w - 20f, 20f), androidx.compose.ui.geometry.Offset(w - 20f - lineLen, 20f), strokeW)
                    drawLine(greenColor, androidx.compose.ui.geometry.Offset(w - 20f, 20f), androidx.compose.ui.geometry.Offset(w - 20f, 20f + lineLen), strokeW)

                    // Bottom Left
                    drawLine(greenColor, androidx.compose.ui.geometry.Offset(20f, h - 20f), androidx.compose.ui.geometry.Offset(20f + lineLen, h - 20f), strokeW)
                    drawLine(greenColor, androidx.compose.ui.geometry.Offset(20f, h - 20f), androidx.compose.ui.geometry.Offset(20f, h - 20f - lineLen), strokeW)

                    // Bottom Right
                    drawLine(greenColor, androidx.compose.ui.geometry.Offset(w - 20f, h - 20f), androidx.compose.ui.geometry.Offset(w - 20f - lineLen, h - 20f), strokeW)
                    drawLine(greenColor, androidx.compose.ui.geometry.Offset(w - 20f, h - 20f), androidx.compose.ui.geometry.Offset(w - 20f, h - 20f - lineLen), strokeW)
                }

                // Inner Simulated Receipt Sheet preview
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(40.dp)
                        .background(Color(0xFFFFFDF2), RoundedCornerShape(4.dp))
                        .border(1.dp, Color.LightGray.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(mockReceiptStore + " MARKET", fontWeight = FontWeight.Bold, color = Color.Black, fontSize = 16.sp)
                    Text("TARIH: 20-07-2026 15:45", color = Color.DarkGray, fontSize = 10.sp)
                    Divider(color = Color.LightGray)
                    Text("• PILIC BONFILE 800G", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Text("• DOMATES 2 KG", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Text("• MARUL 1 ADET", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Text("• SUZME PEYNIR 500G", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.weight(1f))
                    Text("FIS BEDELI ODENMISTIR", color = Color.Gray, fontSize = 9.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }

                // Glowing green horizontal laser scan line
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val h = size.height
                    val laserY = h * laserOffset
                    drawLine(
                        color = Color(0xFF66BB6A),
                        start = androidx.compose.ui.geometry.Offset(0f, laserY),
                        end = androidx.compose.ui.geometry.Offset(size.width, laserY),
                        strokeWidth = 4.dp.toPx()
                    )
                }

                // Overlay diagnostics logs
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(12.dp)
                        .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(6.dp))
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text("ODAK: %99 (AYARLANDI)", color = Color.Green, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                    Text("MOD: MULTIMODAL OCR", color = Color.Green, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                    Text("CIHAZ SEVIYESI: DÜZ", color = Color.Green, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Quick store selector inside camera HUD to show versatility
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Fiş Şablonu:", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                listOf("MİGROS", "BİM", "CARREFOUR").forEach { name ->
                    val isSelected = mockReceiptStore == name
                    Surface(
                        modifier = Modifier.clickable { mockReceiptStore = name },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) KitchenSage else Color.DarkGray
                    ) {
                        Text(
                            name,
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // Capture Controls
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(bottom = 10.dp)
            ) {
                Text(
                    text = when (activeScanMode) {
                        "PANTRY" -> "Kiler odasını kadraja sığdırın ve kamerayı başlatın"
                        "FREEZER" -> "Dondurucuyu kadraja sığdırın ve kamerayı başlatın"
                        else -> "Fişi kadraja sığdırın ve kamerayı başlatın"
                    },
                    color = Color.LightGray,
                    fontSize = 11.sp
                )
                
                // Primary Real Camera shutter button
                Button(
                    onClick = {
                        showCameraViewfinder = false
                        triggerCameraScan()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = KitchenSage),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Gerçek Kamerayı Başlat & Çek", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }

                // Shutter circle
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(Color.White, CircleShape)
                        .border(4.dp, Color.White.copy(alpha = 0.5f), CircleShape)
                        .clickable {
                            showCameraViewfinder = false
                            triggerCameraScan()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(Color.White, CircleShape)
                            .border(3.dp, Color.Black, CircleShape)
                    )
                }
            }
        }
    }
}

    editingIndex?.let { index ->
        val itemsList = editableDetectedItems
        if (itemsList != null && index >= 0 && index < itemsList.size) {
            EditDetectedItemDialog(
                item = itemsList[index],
                onDismiss = { editingIndex = null },
                onSave = { updatedItem ->
                    val newList = itemsList.toMutableList()
                    newList[index] = updatedItem
                    editableDetectedItems = newList
                }
            )
        }
    }
}

@Composable
fun EditDetectedItemDialog(
    item: DetectedItem,
    onDismiss: () -> Unit,
    onSave: (DetectedItem) -> Unit
) {
    var name by remember { mutableStateOf(item.name) }
    var quantity by remember { mutableStateOf(item.quantity) }
    var storage by remember { mutableStateOf(item.storage) }
    var shelfLifeString by remember { mutableStateOf(item.estimatedShelfLifeDays.toString()) }
    var priceString by remember { mutableStateOf(item.price?.toString() ?: "") }

    val storages = listOf("Buzdolabı", "Kiler", "Dondurucu")

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color.White,
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Taranan Ürünü Düzenle",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = DarkCharcoal
                )
                Divider(color = Color.LightGray.copy(alpha = 0.3f))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Ürün Adı", fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = TextStyle(fontSize = 13.sp),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = KitchenSage)
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = quantity,
                        onValueChange = { quantity = it },
                        label = { Text("Miktar", fontSize = 11.sp) },
                        modifier = Modifier.weight(1f),
                        textStyle = TextStyle(fontSize = 13.sp),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = KitchenSage)
                    )

                    OutlinedTextField(
                        value = priceString,
                        onValueChange = { priceString = it },
                        label = { Text("Fiyat (₺)", fontSize = 11.sp) },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                        ),
                        textStyle = TextStyle(fontSize = 13.sp),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = KitchenSage)
                    )
                }

                OutlinedTextField(
                    value = shelfLifeString,
                    onValueChange = { shelfLifeString = it },
                    label = { Text("Raf Ömrü (Gün)", fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                    ),
                    textStyle = TextStyle(fontSize = 13.sp),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = KitchenSage)
                )

                // Storage Area Selection
                Text("Saklama Alanı:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = DarkCharcoal)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    storages.forEach { s ->
                        val isSelected = storage == s
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { storage = s },
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) KitchenSage else SoftSageBg,
                            border = if (isSelected) null else BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = s,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) Color.White else DarkCharcoal,
                                modifier = Modifier.padding(vertical = 8.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Vazgeç", fontSize = 12.sp)
                    }

                    Button(
                        onClick = {
                            val days = shelfLifeString.toIntOrNull() ?: 7
                            val price = priceString.toDoubleOrNull() ?: 0.0
                            onSave(DetectedItem(name, quantity, days, storage, price))
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = KitchenSage),
                        modifier = Modifier.weight(1.3f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Uygula", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

data class ReadyRecipe(
    val id: String,
    val title: String,
    val category: String, // "Hızlı & Pratik", "Fit & Diyet", "Çorbalar", "Kahvaltılık", "Ana Yemek"
    val emoji: String,
    val prepTime: String,
    val calories: Int,
    val protein: String,
    val carbs: String,
    val fat: String,
    val usedItems: List<String>,
    val ingredients: List<String>,
    val instructions: List<String>,
    val calorieAnalysis: String
)

val defaultReadyRecipes = listOf(
    ReadyRecipe(
        id = "ready_1",
        title = "Kremalı Tavuklu Makarna",
        category = "Hızlı & Pratik",
        emoji = "🍝",
        prepTime = "15 dk",
        calories = 480,
        protein = "32g",
        carbs = "54g",
        fat = "14g",
        usedItems = listOf("Tavuk Göğsü", "Süt / Krema", "Makarna"),
        ingredients = listOf(
            "250g Tavuk Göğsü (Küp doğranmış)",
            "1/2 paket Makarna (Penne veya Burgu)",
            "1 su bardağı Süt veya Sıvı Krema",
            "2 diş Sarımsak (İnce kıyılmış)",
            "1 yemek kaşığı Zeytinyağı veya Tereyağı",
            "1 çay kaşığı Kekik & Karabiber & Tuz",
            "50g Rendelenmiş Kaşar Peyniri"
        ),
        instructions = listOf(
            "Makarnayı bol tuzlu kaynar suda 8-10 dakika al dente kıvamda haşlayıp süzün.",
            "Geniş tavada zeytinyağını ısıtın, tavuk küplerini suyunu çekip hafif kızarana kadar soteleyin.",
            "Sarımsakları ekleyip kokusu çıkana dek 1 dakika çevirin, süt/krema ve baharatları ekleyip kısık ateşte 2-3 dakika kıvam aldırın.",
            "Haşlanan makarnayı sosa ekleyip karıştırın, üzerine rendelenmiş kaşar serperek sıcak servis yapın."
        ),
        calorieAnalysis = "Yüksek protein içeriği kas gelişimini ve tokluk hissini destekler. Tam buğday makarna kullanılarak lif oranı artırılabilir."
    ),
    ReadyRecipe(
        id = "ready_2",
        title = "Fırında Sebzeli Kaşarlı Mücver",
        category = "Fit & Diyet",
        emoji = "🥒",
        prepTime = "25 dk",
        calories = 220,
        protein = "14g",
        carbs = "18g",
        fat = "8g",
        usedItems = listOf("Kabak", "Havuç", "Yumurta", "Kaşar"),
        ingredients = listOf(
            "2 adet Kabak (Rendelenip suyu sıkılmış)",
            "1 adet Havuç (Rendelenmiş)",
            "2 adet Yumurta",
            "3 yemek kaşığı Un veya Yulaf Unu",
            "60g Rendelenmiş Kaşar veya Beyaz Peynir",
            "1/2 demet Dereotu ve Maydanoz",
            "1 yemek kaşığı Zeytinyağı, Pul Biber & Tuz"
        ),
        instructions = listOf(
            "Kabakları rendeleyip avucunuzda sıkarak suyunu tamamen çıkarın (çıtır olması için önemlidir).",
            "Karıştırma kabına yumurta, kabak, havuç, un, peynir, kıyılmış yeşillikler ve baharatları alıp homojen karıştırın.",
            "Yağlı kağıt serili fırın tepsisine birer kaşık harç koyup şekil verin.",
            "Önceden 190°C ısıtılmış fırında yaklaşık 20-25 dakika altın sarısı olana kadar pişirin."
        ),
        calorieAnalysis = "Kızartılmadan fırında pişirildiği için son derece düşük kalorilidir. Sebze lifleriyle sindirimi kolaylaştırır."
    ),
    ReadyRecipe(
        id = "ready_3",
        title = "Şifalı Karışık Sebze Çorbası",
        category = "Çorbalar",
        emoji = "🥣",
        prepTime = "20 dk",
        calories = 160,
        protein = "6g",
        carbs = "26g",
        fat = "4g",
        usedItems = listOf("Domates", "Biber", "Patates", "Soğan", "Havuç"),
        ingredients = listOf(
            "2 adet yumuşamış Domates",
            "1 adet Yeşil / Kapya Biber",
            "1 adet orta boy Patates",
            "1 adet Kuru Soğan",
            "1 adet Havuç",
            "1 yemek kaşığı Zeytinyağı",
            "4 su bardağı Sıcak Su veya Tavuk Suyu",
            "1 tatlı kaşığı Kuru Nane, Tuz & Karabiber"
        ),
        instructions = listOf(
            "Tencereye zeytinyağını dökün, yemeklik doğranmış soğan ve havuçları 3 dakika kavurun.",
            "Patates, biber ve domatesleri iri parçalar halinde ekleyip 2 dakika daha soteleyin.",
            "Sıcak suyu ve tuzu ekleyin, tencerenin kapağını kapatıp sebzeler yumuşayana dek (15 dk) kaynatın.",
            "Pürüzsüz kıvama gelene kadar el blenderından geçirin, üzerine nane serpip sıcak servis yapın."
        ),
        calorieAnalysis = "Dolapta kalan sebzelerin tamamını değerlendiren antioksidan deposu, düşük kalorili bir lif kaynağıdır."
    ),
    ReadyRecipe(
        id = "ready_4",
        title = "Fırında Çıtır Kaşarlı Ekmek Dilimleri",
        category = "Kahvaltılık",
        emoji = "🍞",
        prepTime = "10 dk",
        calories = 290,
        protein = "13g",
        carbs = "34g",
        fat = "11g",
        usedItems = listOf("Bayat Ekmek", "Kaşar Peyniri", "Domates", "Tereyağı"),
        ingredients = listOf(
            "4-5 dilim Bayat Ekmek",
            "100g Kaşar Peyniri veya Beyaz Peynir",
            "1 adet Domates (Halka dilimlenmiş)",
            "1 adet Yeşil Biber (İnce kıyılmış)",
            "1 tatlı kaşığı Tereyağı veya Zeytinyağı",
            "1 çay kaşığı Kekik & Pul Biber"
        ),
        instructions = listOf(
            "Bayat ekmek dilimlerini fırın tepsisine dizin.",
            "Üzerlerine çok hafif tereyağı sürün veya zeytinyağı damlatın.",
            "Domates dilimleri, biber ve bol kaşar peyniri yerleştirin, kekik serpin.",
            "200°C fırında kaşarlar eriyip nar gibi kızarana dek (7-8 dakika) fırınlayın."
        ),
        calorieAnalysis = "Bayat ekmek israfını sıfırlayan, kalsiyum ve enerji açısından dengeli bir pratik kahvaltılıktır."
    ),
    ReadyRecipe(
        id = "ready_5",
        title = "Geleneksel Bol Domatesli Menemen",
        category = "Kahvaltılık",
        emoji = "🍳",
        prepTime = "12 dk",
        calories = 260,
        protein = "16g",
        carbs = "12g",
        fat = "16g",
        usedItems = listOf("Domates", "Yeşil Biber", "Yumurta"),
        ingredients = listOf(
            "3 adet olgun sulu Domates (Küp doğranmış)",
            "2 adet Yeşil Köy Biberi",
            "3 adet Yumurta",
            "1 yemek kaşığı Zeytinyağı",
            "1 tatlı kaşığı Tereyağı",
            "Tuz, Karabiber, Pul Biber"
        ),
        instructions = listOf(
            "Tavada zeytinyağı ve tereyağını eritin, ince doğranmış biberleri hafif yumuşayana kadar soteleyin.",
            "Küp doğranmış domatesleri ekleyin, tavanın kapağını kapatıp domatesler suyunu çekene kadar pişirin.",
            "Yumurtaları kırın, sarılarını hafifçe dağıtarak kısık ateşte istediğiniz kıvama gelene kadar pişirin."
        ),
        calorieAnalysis = "Yumurtadan gelen kaliteli protein ve domatesteki likopen ile metabolizmayı canlandırır."
    ),
    ReadyRecipe(
        id = "ready_6",
        title = "Fırında Kıymalı Patates Oturtma",
        category = "Ana Yemek",
        emoji = "🍲",
        prepTime = "35 dk",
        calories = 440,
        protein = "28g",
        carbs = "38g",
        fat = "18g",
        usedItems = listOf("Patates", "Kıyma", "Domates", "Biber", "Salça"),
        ingredients = listOf(
            "3 adet orta boy Patates (Halka dilimlenmiş)",
            "200g Dana Kıyma",
            "1 adet Kuru Soğan & 2 diş Sarımsak",
            "1 yemek kaşığı Domates Salçası",
            "2 adet Yeşil Biber & 1 adet Domates",
            "2 yemek kaşığı Sıvı Yağ",
            "1 su bardağı Sıcak Su, Baharatlar"
        ),
        instructions = listOf(
            "Patates dilimlerini yağlı kağıt serili tepside 10 dakika 200°C fırınlayın.",
            "Tavada sıvı yağda soğan, kıyma, sarımsak, biber ve salçayı kavurarak lezzetli bir harç hazırlayın.",
            "Fırın tepsisine patatesleri dizin, kıymalı harcı üzerine yayın, domates dilimleri yerleştirin.",
            "Sıcak suyu gezdirip 190°C fırında 20 dakika patatesler yumuşayana dek pişirin."
        ),
        calorieAnalysis = "Kızartma yerine fırında yapılarak yağı azaltılmış, doyurucu ve dengeli bir ana yemektir."
    ),
    ReadyRecipe(
        id = "ready_7",
        title = "Fit Yoğurtlu & Yulaflı Meyve Kasesi",
        category = "Fit & Diyet",
        emoji = "🥣",
        prepTime = "5 dk",
        calories = 240,
        protein = "12g",
        carbs = "36g",
        fat = "4g",
        usedItems = listOf("Yoğurt / Süt", "Muz / Elma", "Yulaf"),
        ingredients = listOf(
            "4 yemek kaşığı Süzme Yoğurt",
            "3 yemek kaşığı Yulaf Ezmesi",
            "1 adet Doğranmış Muz veya Elma",
            "1 tatlı kaşığı Bal",
            "1 çay kaşığı Tarçın",
            "1 yemek kaşığı Çiğ Badem veya Fındık"
        ),
        instructions = listOf(
            "Geniş bir kaseye yoğurdu ekleyip kaşıkla pürüzsüzleştirin.",
            "Yulaf ezmesini ve doğranmış meyveleri üzerine ilave edin.",
            "Bal gezdirin, tarçın ve badem parçaları serpip anında servis edin."
        ),
        calorieAnalysis = "Probiyotik bakteriler bağırsak florasını güçlendirir, yulaf lifleri ise kan şekerini dengeler."
    ),
    ReadyRecipe(
        id = "ready_8",
        title = "Pratik Kırmızı Mercimek Köftesi",
        category = "Hızlı & Pratik",
        emoji = "🥗",
        prepTime = "30 dk",
        calories = 310,
        protein = "15g",
        carbs = "48g",
        fat = "6g",
        usedItems = listOf("Kırmızı Mercimek", "İnce Bulgur", "Soğan", "Salça"),
        ingredients = listOf(
            "1 su bardağı Kırmızı Mercimek",
            "1 su bardağı İnce Köftelik Bulgur",
            "1 adet Kuru Soğan (İnce kıyılmış)",
            "2 yemek kaşığı Domates & Biber Salçası",
            "1/2 çay bardağı Zeytinyağı",
            "1/2 demet Maydanoz ve Taze Soğan",
            "Kimyon, Pul Biber, Tuz, Limon Suyu"
        ),
        instructions = listOf(
            "Mercimeği 3 su bardağı suda iyice haşlayın, suyunu çekince bulguru ekleyip kapağını kapatıp 15 dakika demlendirin.",
            "Tavada zeytinyağında soğanı ve salçayı güzelce kavurun.",
            "Demlenen mercimekli bulgura salçalı sosu, baharatları ve ince kıyılmış yeşillikleri ekleyip yoğurun.",
            "Avucunuzda sıkarak şekil verin, marul ve limon eşliğinde servis yapın."
        ),
        calorieAnalysis = "Tamamen bitkisel protein ve yüksek lif kaynağıdır, sıfır kolesterol içerir."
    )
)

@Composable
fun AIRecipeSuggestionHero(onStartRecipe: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "AI Önerisi: Bugün Ne Pişirmeli? ✨",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = DarkCharcoal,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("ai_recipe_hero_card"),
            color = KitchenSage,
            shape = RoundedCornerShape(24.dp),
            shadowElevation = 3.dp
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = Color.White.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "🍝 Hızlı & Pratik",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                        Surface(
                            color = Color.White.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "⏱️ 15 Dakika",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                        Surface(
                            color = Color.White.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "⚡ 480 kcal",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Column {
                        Text(
                            text = "Kremalı Tavuklu Makarna",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "\"Dolaptaki süt ve tavuk göğsünü kurtararak 0.4kg CO2 tasarrufu sağlayabilirsiniz.\"",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.9f),
                            style = TextStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                        )
                    }

                    Button(
                        onClick = onStartRecipe,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = HighlightGreen,
                            contentColor = HighlightDark
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth().testTag("hero_start_recipe_btn"),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        Icon(Icons.Default.MenuBook, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Tarife Başla & Pişir",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RecipeDetailDialog(
    recipe: ReadyRecipe,
    onConsume: (ReadyRecipe) -> Unit,
    onDismiss: () -> Unit
) {
    val checkedIngredients = remember { mutableStateListOf<Int>() }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp)
                .testTag("recipe_detail_dialog_card"),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            LazyColumn(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(recipe.emoji, fontSize = 32.sp)
                            Column {
                                Text(
                                    text = recipe.title,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp,
                                    color = DarkCharcoal
                                )
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(top = 2.dp)
                                ) {
                                    Surface(
                                        color = SoftSageBg,
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = recipe.category,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = KitchenSage,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                    Text("⏱️ ${recipe.prepTime}", fontSize = 11.sp, color = Color.Gray)
                                }
                            }
                        }

                        IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Kapat", tint = Color.Gray)
                        }
                    }
                }

                // Macro & Calorie Badges
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        SuggestionNutritionChip("⚡ ${recipe.calories} kcal", HighlightGreen, HighlightDark)
                        SuggestionNutritionChip("🥩 P: ${recipe.protein}", SoftSageBg, DarkCharcoal)
                        SuggestionNutritionChip("🌾 K: ${recipe.carbs}", SoftSageBg, DarkCharcoal)
                        SuggestionNutritionChip("🧈 Y: ${recipe.fat}", SoftSageBg, DarkCharcoal)
                    }
                }

                // AI Nutrition & Health Note
                item {
                    Surface(
                        color = AccentGreenBg,
                        border = BorderStroke(1.dp, AccentGreenBorder),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = KitchenSage, modifier = Modifier.size(16.dp))
                            Column {
                                Text("Beslenme & Sağlık Analizi", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = KitchenSage)
                                Text(recipe.calorieAnalysis, fontSize = 11.sp, color = DarkCharcoal, lineHeight = 15.sp)
                            }
                        }
                    }
                }

                // Saved / Used items
                item {
                    Text("🌱 Kurtarılan Malzemeler:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = KitchenClay)
                    FlowRow(
                        mainAxisSpacing = 6.dp,
                        crossAxisSpacing = 6.dp,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                    ) {
                        recipe.usedItems.forEach { item ->
                            Surface(
                                color = KitchenClay.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = item,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = KitchenClay,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                }

                // Ingredients Checklist
                item {
                    Text("🛒 Gerekli Malzemeler (Varsa İşaretleyin):", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = DarkCharcoal)
                }

                items(recipe.ingredients.size) { index ->
                    val ingredient = recipe.ingredients[index]
                    val isChecked = checkedIngredients.contains(index)

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (isChecked) checkedIngredients.remove(index) else checkedIngredients.add(index)
                            }
                            .padding(vertical = 2.dp)
                    ) {
                        Checkbox(
                            checked = isChecked,
                            onCheckedChange = {
                                if (it) checkedIngredients.add(index) else checkedIngredients.remove(index)
                            },
                            colors = CheckboxDefaults.colors(checkedColor = KitchenSage)
                        )
                        Text(
                            text = ingredient,
                            fontSize = 12.sp,
                            color = if (isChecked) Color.Gray else DarkCharcoal,
                            style = TextStyle(
                                textDecoration = if (isChecked) androidx.compose.ui.text.style.TextDecoration.LineThrough else null
                            )
                        )
                    }
                }

                // Step-by-Step Instructions
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("👩‍🍳 Adım Adım Hazırlanış & Pişirme:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = DarkCharcoal)
                }

                items(recipe.instructions.size) { index ->
                    val step = recipe.instructions[index]
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = KitchenSage,
                            modifier = Modifier.size(20.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("${index + 1}", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Text(
                            text = step,
                            fontSize = 12.sp,
                            color = DarkCharcoal,
                            lineHeight = 16.sp,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Action Buttons
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            onConsume(recipe)
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = KitchenSage),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().testTag("dialog_consume_recipe_btn")
                    ) {
                        Icon(Icons.Default.Restaurant, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Bu Yemeği Pişirdim & Tükettim", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun RecipeTab(
    activeItems: List<InventoryItem>,
    scanUiState: ScanUiState,
    onNavigateToScan: () -> Unit,
    viewModel: InventoryViewModel
) {
    val context = LocalContext.current
    val expiringItems = remember(activeItems) {
        activeItems.filter { it.getRemainingDays() <= 3 }
    }

    val userDiet by viewModel.userDiet.collectAsState()
    val userAllergies by viewModel.userAllergies.collectAsState()
    val dislikedIngredients by viewModel.dislikedIngredients.collectAsState()

    var showFilters by remember { mutableStateOf(false) }
    var selectedCategoryFilter by remember { mutableStateOf("Tümü") }
    var searchQuery by remember { mutableStateOf("") }
    var selectedRecipeForDetail by remember { mutableStateOf<ReadyRecipe?>(null) }

    val categories = listOf("Tümü", "Hızlı & Pratik", "Fit & Diyet", "Çorbalar", "Kahvaltılık", "Ana Yemek")
    val diets = listOf("Hepsi", "Vegan", "Vejetaryen", "Glütensiz")
    val allergenList = listOf("Gluten", "Laktoz", "Yer Fıstığı", "Yumurta", "Deniz Ürünleri")

    val filteredReadyRecipes = remember(selectedCategoryFilter, searchQuery) {
        defaultReadyRecipes.filter { recipe ->
            val matchCategory = selectedCategoryFilter == "Tümü" || recipe.category == selectedCategoryFilter
            val matchSearch = searchQuery.isBlank() || 
                recipe.title.contains(searchQuery, ignoreCase = true) ||
                recipe.ingredients.any { it.contains(searchQuery, ignoreCase = true) } ||
                recipe.usedItems.any { it.contains(searchQuery, ignoreCase = true) }
            matchCategory && matchSearch
        }
    }

    // Detail dialog popup
    if (selectedRecipeForDetail != null) {
        RecipeDetailDialog(
            recipe = selectedRecipeForDetail!!,
            onConsume = { recipe ->
                viewModel.consumeRecipe(
                    recipe.title,
                    recipe.calories,
                    recipe.protein,
                    recipe.carbs,
                    recipe.fat
                )
                Toast.makeText(context, "${recipe.title} afiyetle tüketildi! Günlük kalori ve makrolarınıza kaydedildi. 🌱", Toast.LENGTH_LONG).show()
            },
            onDismiss = { selectedRecipeForDetail = null }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp)
    ) {
        item {
            Column {
                Text(
                    "SKT Yaklaşan Ürünler & Tarifler",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = DarkCharcoal
                )
                Text(
                    "Dolabınızdaki son tüketim tarihi yaklaşan gıdaları kurtarmak için özel AI ve hazır pratik tarifler.",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
        }

        item {
            DailyCalorieWidget(viewModel = viewModel)
        }

        // Quick AI Generator Trigger & Preferences Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, BorderColor)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = KitchenSage, modifier = Modifier.size(18.dp))
                            Text("Kişisel Mutfak Asistanı", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = DarkCharcoal)
                        }

                        TextButton(
                            onClick = { showFilters = !showFilters },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(if (showFilters) "Filtreleri Gizle" else "Diyet & Alerji", fontSize = 11.sp, color = KitchenSage, fontWeight = FontWeight.Bold)
                            Icon(
                                imageVector = if (showFilters) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                tint = KitchenSage,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    // Quick AI Generate Button always visible!
                    Button(
                        onClick = {
                            viewModel.generateRecipesFromInventory()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = KitchenSage),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().testTag("quick_ai_recipe_generate_btn")
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = "Sihir", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Dolaptaki Gıdalara Göre AI Tarifi Üret", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    if (showFilters) {
                        Divider(color = Color.LightGray.copy(alpha = 0.3f))

                        // Dietary preference
                        Text("Beslenme Tercihiniz:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            diets.forEach { diet ->
                                val isSelected = userDiet == diet
                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable {
                                            viewModel.updatePreferences(diet = diet, allergies = userAllergies, disliked = dislikedIngredients)
                                        },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) KitchenSage else SoftSageBg,
                                    border = if (isSelected) null else BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.3f))
                                ) {
                                    Text(
                                        text = diet,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) Color.White else DarkCharcoal,
                                        modifier = Modifier.padding(vertical = 6.dp),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // Allergies
                        Text("Alerjileriniz (Hariç tutulur):", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                        FlowRow(
                            mainAxisSpacing = 6.dp,
                            crossAxisSpacing = 6.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            allergenList.forEach { allergen ->
                                val isSelected = userAllergies.contains(allergen)
                                Surface(
                                    modifier = Modifier.clickable {
                                        val newList = if (isSelected) {
                                            userAllergies - allergen
                                        } else {
                                            userAllergies + allergen
                                        }
                                        viewModel.updatePreferences(diet = userDiet, allergies = newList, disliked = dislikedIngredients)
                                    },
                                    shape = RoundedCornerShape(16.dp),
                                    color = if (isSelected) KitchenClay else SoftSageBg,
                                    border = if (isSelected) null else BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.3f))
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                    ) {
                                        if (isSelected) {
                                            Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(10.dp))
                                            Spacer(modifier = Modifier.width(3.dp))
                                        }
                                        Text(
                                            text = allergen,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) Color.White else DarkCharcoal
                                        )
                                    }
                                }
                            }
                        }

                        // Disliked Ingredients
                        Text("İstemediğiniz Malzemeler:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                        OutlinedTextField(
                            value = dislikedIngredients,
                            onValueChange = {
                                viewModel.updatePreferences(diet = userDiet, allergies = userAllergies, disliked = it)
                            },
                            placeholder = { Text("Örn: kereviz, mantar, acı biber...", fontSize = 11.sp) },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = TextStyle(fontSize = 12.sp),
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = KitchenSage,
                                unfocusedBorderColor = Color.LightGray
                            ),
                            singleLine = true
                        )
                    }
                }
            }
        }

        // Display AI generation loading or success cards
        when (scanUiState) {
            is ScanUiState.Loading -> {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = AccentGreenBg),
                        border = BorderStroke(1.dp, AccentGreenBorder)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CircularProgressIndicator(color = KitchenSage, modifier = Modifier.size(36.dp))
                            Text(
                                "AI Mutfak Şefi Dolabınızı Analiz Ediyor...",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = KitchenSage,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                "Dolabınızdaki gıdalar, seçtiğiniz diyet ve alerji tercihlerinize göre harmanlanıyor.",
                                fontSize = 11.sp,
                                color = Color.DarkGray,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
            is ScanUiState.Success -> {
                val result = scanUiState.result
                if (!result.recipeSuggestions.isNullOrEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = BorderStroke(1.dp, KitchenSage.copy(alpha = 0.3f))
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(
                                        Icons.Default.RestaurantMenu,
                                        contentDescription = null,
                                        tint = KitchenSage,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        "Size Özel Üretilen AI Tarifleri (${result.recipeSuggestions.size})",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = DarkCharcoal
                                    )
                                }

                                result.recipeSuggestions.forEach { recipe ->
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        recipe.title,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = KitchenSage
                                    )
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        modifier = Modifier.padding(vertical = 2.dp)
                                    ) {
                                        Text(
                                            "⏱️ ${recipe.prepTime}",
                                            fontSize = 11.sp,
                                            color = Color.Gray,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            "🔥 Kurtarılan: ${recipe.usedExpiringItems.joinToString(", ")}",
                                            fontSize = 11.sp,
                                            color = KitchenClay,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    // Macro row
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        val cal = recipe.calories ?: 350
                                        val prot = recipe.protein ?: "18g"
                                        val carb = recipe.carbs ?: "42g"
                                        val f = recipe.fat ?: "12g"
                                        
                                        SuggestionNutritionChip("⚡ $cal kcal", HighlightGreen, HighlightDark)
                                        SuggestionNutritionChip("🥩 P: $prot", SoftSageBg, DarkCharcoal)
                                        SuggestionNutritionChip("🌾 K: $carb", SoftSageBg, DarkCharcoal)
                                        SuggestionNutritionChip("🧈 Y: $f", SoftSageBg, DarkCharcoal)
                                    }

                                    if (!recipe.instructions.isNullOrEmpty()) {
                                        Text(
                                            recipe.instructions,
                                            fontSize = 11.sp,
                                            color = DarkCharcoal,
                                            modifier = Modifier
                                                .padding(top = 4.dp)
                                                .fillMaxWidth()
                                                .background(SoftSageBg, RoundedCornerShape(6.dp))
                                                .padding(8.dp),
                                            lineHeight = 15.sp
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))
                                    Button(
                                        onClick = {
                                            viewModel.consumeRecipe(
                                                recipe.title,
                                                recipe.calories,
                                                recipe.protein,
                                                recipe.carbs,
                                                recipe.fat
                                            )
                                            Toast.makeText(context, "${recipe.title} tüketildi! Kalori ve makro değerleri günlüğünüze işlendi.", Toast.LENGTH_LONG).show()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = KitchenSage),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth().height(36.dp),
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Restaurant,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Bu Yemeği Pişirdim & Tükettim", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Divider(color = Color.LightGray.copy(alpha = 0.2f))
                                }
                            }
                        }
                    }
                }
            }
            is ScanUiState.Error -> {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = AlertRed.copy(alpha = 0.08f)),
                        border = BorderStroke(1.dp, AlertRed.copy(alpha = 0.3f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = AlertRed)
                                Text("Tarif Bilgisi", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = AlertRed)
                            }
                            Text(scanUiState.message, fontSize = 11.sp, color = DarkCharcoal)
                            Button(
                                onClick = { viewModel.generateRecipesFromInventory() },
                                colors = ButtonDefaults.buttonColors(containerColor = KitchenSage),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Yeniden AI Tarifi Üret", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
            else -> {}
        }

        // Hero Banner: 1-click opening to full recipe instructions & ingredients
        item {
            AIRecipeSuggestionHero(onStartRecipe = {
                val heroRecipe = defaultReadyRecipes.firstOrNull { it.title == "Kremalı Tavuklu Makarna" } ?: defaultReadyRecipes.first()
                selectedRecipeForDetail = heroRecipe
            })
        }

        // Critical expiring items banner
        if (expiringItems.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = KitchenClay.copy(alpha = 0.08f)),
                    border = BorderStroke(1.dp, KitchenClay.copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.Warning, contentDescription = "Alarm", tint = KitchenClay, modifier = Modifier.size(16.dp))
                            Text(
                                "Kritik Durumdaki Ürünler (${expiringItems.size})",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = KitchenClay
                            )
                        }
                        Text(
                            "Aşağıdaki gıdalar bozulmadan önce tariflerle değerlendirilmelidir:",
                            fontSize = 10.sp,
                            color = Color.Gray,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        expiringItems.forEach { item ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("• ${item.name} (${item.quantity})", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = DarkCharcoal)
                                Text(
                                    if (item.getRemainingDays() == 0) "Bugün Son!" else "${item.getRemainingDays()} Gün Kaldı!",
                                    fontSize = 11.sp,
                                    color = AlertRed,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        // READY RECIPES CATALOG SECTION
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Hazır Kurtarma & Mutfak Tarifleri 📖",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = DarkCharcoal
                    )
                    Text(
                        "${filteredReadyRecipes.size} Tarif",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = KitchenSage
                    )
                }

                // Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Tarif veya malzeme ara (örn: tavuk, makarna, çorba)...", fontSize = 11.sp) },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = "Ara", tint = Color.Gray, modifier = Modifier.size(18.dp))
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Temizle", tint = Color.Gray, modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("recipe_search_input"),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = KitchenSage,
                        unfocusedBorderColor = Color.LightGray.copy(alpha = 0.5f)
                    ),
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 12.sp)
                )

                // Category Filter Tabs
                ScrollableTabRow(
                    selectedTabIndex = categories.indexOf(selectedCategoryFilter).coerceAtLeast(0),
                    edgePadding = 0.dp,
                    containerColor = Color.Transparent,
                    indicator = {},
                    divider = {}
                ) {
                    categories.forEach { cat ->
                        val isSelected = selectedCategoryFilter == cat
                        Surface(
                            modifier = Modifier
                                .padding(end = 6.dp)
                                .clickable { selectedCategoryFilter = cat },
                            shape = RoundedCornerShape(16.dp),
                            color = if (isSelected) KitchenSage else SoftSageBg,
                            border = if (isSelected) null else BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.3f))
                        ) {
                            Text(
                                text = cat,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) Color.White else DarkCharcoal,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        }

        // Ready Recipe Cards
        if (filteredReadyRecipes.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.3f))
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("🔍", fontSize = 28.sp)
                        Text("Eşleşen Tarif Bulunamadı", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = DarkCharcoal)
                        Text("Farklı bir kategori veya arama terimi deneyebilirsiniz.", fontSize = 11.sp, color = Color.Gray)
                    }
                }
            }
        } else {
            items(filteredReadyRecipes) { recipe ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedRecipeForDetail = recipe }
                        .testTag("recipe_card_${recipe.id}"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, BorderColor),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(recipe.emoji, fontSize = 24.sp)
                                Column {
                                    Text(
                                        text = recipe.title,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = DarkCharcoal
                                    )
                                    Text(
                                        text = "⏱️ ${recipe.prepTime} • ${recipe.category}",
                                        fontSize = 10.sp,
                                        color = Color.Gray,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }

                            Surface(
                                color = HighlightGreen.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "⚡ ${recipe.calories} kcal",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = HighlightDark,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                )
                            }
                        }

                        // Used / Saved items
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Kurtarılan:", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                            recipe.usedItems.take(3).forEach { item ->
                                Surface(
                                    color = KitchenClay.copy(alpha = 0.08f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = item,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = KitchenClay,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        // Macro chips
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SuggestionNutritionChip("🥩 P: ${recipe.protein}", SoftSageBg, DarkCharcoal)
                            SuggestionNutritionChip("🌾 K: ${recipe.carbs}", SoftSageBg, DarkCharcoal)
                            SuggestionNutritionChip("🧈 Y: ${recipe.fat}", SoftSageBg, DarkCharcoal)
                        }

                        // Action button
                        Button(
                            onClick = { selectedRecipeForDetail = recipe },
                            colors = ButtonDefaults.buttonColors(containerColor = KitchenSage),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().height(36.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Icon(Icons.Default.MenuBook, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Tarifi & Malzemeleri Gör", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

data class BadgeInfo(val name: String, val emoji: String, val desc: String, val isUnlocked: Boolean)

@Composable
fun SustainabilityTab(
    totalScore: Int,
    totalCo2Saved: Double,
    actionLogs: List<WasteActionLog>,
    onClearLogs: () -> Unit,
    viewModel: InventoryViewModel
) {
    val context = LocalContext.current
    var showLogoutDialog by remember { mutableStateOf(false) }
    
    val totalSavedCount = remember(actionLogs) { actionLogs.count { it.actionType == "CONSUMED" } }
    val totalWastedCount = remember(actionLogs) { actionLogs.count { it.actionType == "WASTED" } }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Logout, contentDescription = null, tint = AlertRed)
                    Text("Oturumu Kapat", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Text("Hesabınızdan çıkış yapmak istediğinize emin misiniz? Kayıtlı profil bilgileriniz temizlenecektir.", fontSize = 12.sp)
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.logoutOrResetProfile()
                        showLogoutDialog = false
                        Toast.makeText(context, "Hesabınızdan başarıyla çıkış yapıldı.", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AlertRed)
                ) {
                    Text("Çıkış Yap", fontWeight = FontWeight.Bold, color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("İptal", color = DarkCharcoal)
                }
            },
            shape = RoundedCornerShape(16.dp),
            containerColor = Color.White
        )
    }

    val totalWaterSavedLiters = remember(actionLogs) {
        actionLogs.sumOf { log ->
            if (log.actionType == "CONSUMED") {
                val multiplier = when (log.category.lowercase()) {
                    "et & tavuk", "meat", "chicken" -> 1200.0
                    "süt ürünleri", "dairy" -> 600.0
                    "sebze & meyve", "vegetables", "fruits" -> 150.0
                    "kiler / kuru gıda", "bakery", "grains" -> 400.0
                    else -> 300.0
                }
                log.co2SavedKg * multiplier
            } else 0.0
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp)
    ) {
        item {
            Column {
                Text(
                    "Sürdürülebilirlik & Karbon Etkim",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = DarkCharcoal
                )
                Text(
                    "Gıdaları israf etmeden tüketerek engellediğiniz karbon ayak izi ve Yeşil Mutfak Puanınız:",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
        }

        // Global Carbon & Water Impact Panel
        item {
            val globalCo2Saved by viewModel.globalCo2Saved.collectAsStateWithLifecycle()
            val globalWaterSaved by viewModel.globalWaterSaved.collectAsStateWithLifecycle()
            
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("global_sustainability_panel"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = AccentGreenBg),
                border = BorderStroke(1.dp, AccentGreenBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(
                                imageVector = Icons.Default.Public,
                                contentDescription = null,
                                tint = KitchenSage,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Küresel Sıfır Atık Topluluğu",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = DarkCharcoal
                            )
                        }
                        
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color.White
                        ) {
                            Text(
                                text = "CANLI 🌱",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = KitchenSage,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Toplam Engellenen CO2",
                                fontSize = 10.sp,
                                color = Color.Gray
                            )
                            Text(
                                text = String.format(Locale.US, "%,.1f kg", globalCo2Saved),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = KitchenSage
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = String.format(Locale.US, "Katkınız: %.1f kg", totalCo2Saved),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = DarkCharcoal
                            )
                        }

                        // Vertical divider
                        Box(
                            modifier = Modifier
                                .height(55.dp)
                                .width(1.dp)
                                .background(AccentGreenBorder.copy(alpha = 0.5f))
                        )

                        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(
                                text = "Kazanılan Sanal Su",
                                fontSize = 10.sp,
                                color = Color.Gray
                            )
                            Text(
                                text = String.format(Locale.US, "%,.0f Litre", globalWaterSaved),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF006699)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = String.format(Locale.US, "Katkınız: %.0f L", totalWaterSavedLiters),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = DarkCharcoal
                            )
                        }
                    }
                }
            }
        }

        // User Profile & Nickname Editing Section
        item {
            val nickname by viewModel.userNickname.collectAsStateWithLifecycle()
            val avatar by viewModel.userAvatar.collectAsStateWithLifecycle()
            val userTitle = viewModel.getUserTitle(totalScore)
            var isEditingNick by remember { mutableStateOf(false) }
            var nickInput by remember { mutableStateOf(nickname) }
            var showLogoutDialog by remember { mutableStateOf(false) }
            var showAvatarPicker by remember { mutableStateOf(false) }

            val avatarList = listOf("🥦", "🥑", "🍎", "🍋", "👨‍🍳", "🥬", "🥕", "🧀")

            if (showLogoutDialog) {
                AlertDialog(
                    onDismissRequest = { showLogoutDialog = false },
                    title = { Text("Profilden Çıkış Yapılsın mı?", fontWeight = FontWeight.Bold) },
                    text = { Text("Çıkış yaptığınızda profil kimliğiniz sıfırlanacak ve tekrar kullanıcı adı seçimi ekranına yönlendirileceksiniz. Devam etmek istiyor musunuz?") },
                    confirmButton = {
                        Button(
                            onClick = {
                                showLogoutDialog = false
                                viewModel.logoutOrResetProfile()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                        ) {
                            Text("Çıkış Yap & Sıfırla")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showLogoutDialog = false }) {
                            Text("İptal")
                        }
                    }
                )
            }

            Card(
                modifier = Modifier.fillMaxWidth().testTag("user_profile_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, KitchenSage.copy(alpha = 0.2f))
            ) {
                Column(modifier = Modifier.padding(14.dp).fillMaxWidth()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Interactive Avatar Badge
                        Surface(
                            onClick = { showAvatarPicker = !showAvatarPicker },
                            modifier = Modifier.size(52.dp),
                            shape = CircleShape,
                            color = KitchenSage.copy(alpha = 0.15f),
                            border = BorderStroke(2.dp, KitchenSage)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = avatar,
                                    fontSize = 26.sp
                                )
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            if (isEditingNick) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    OutlinedTextField(
                                        value = nickInput,
                                        onValueChange = { nickInput = it },
                                        modifier = Modifier.weight(1f),
                                        textStyle = TextStyle(fontSize = 13.sp),
                                        shape = RoundedCornerShape(8.dp),
                                        singleLine = true,
                                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = KitchenSage)
                                    )
                                    IconButton(
                                        onClick = {
                                            val err = viewModel.validateUsername(nickInput)
                                            if (err != null) {
                                                Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                                            } else {
                                                if (viewModel.updateNickname(nickInput, avatar)) {
                                                    Toast.makeText(context, "Kullanıcı adı güncellendi!", Toast.LENGTH_SHORT).show()
                                                    isEditingNick = false
                                                }
                                            }
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.Check, contentDescription = "Kaydet", tint = SafeGreen)
                                    }
                                }
                            } else {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = nickname,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = DarkCharcoal
                                    )
                                    IconButton(
                                        onClick = {
                                            nickInput = nickname
                                            isEditingNick = true
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = "Düzenle", tint = Color.Gray, modifier = Modifier.size(13.dp))
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(2.dp))

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Surface(
                                    color = KitchenClay.copy(alpha = 0.12f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = userTitle,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = KitchenClay,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }

                                Text(
                                    text = "• Avatarı değiştirmek için dokunun",
                                    fontSize = 10.sp,
                                    color = Color.Gray
                                )
                            }
                        }
                    }

                    // Expandable Avatar Picker Drawer
                    if (showAvatarPicker) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Divider(color = Color.LightGray.copy(alpha = 0.2f))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Profil Avatarı Değiştir:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = KitchenSage
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            avatarList.forEach { itemAvatar ->
                                val isSelected = itemAvatar == avatar
                                Surface(
                                    onClick = {
                                        viewModel.updateAvatar(itemAvatar)
                                        showAvatarPicker = false
                                        Toast.makeText(context, "Profil simgesi güncellendi!", Toast.LENGTH_SHORT).show()
                                    },
                                    shape = CircleShape,
                                    color = if (isSelected) KitchenSage.copy(alpha = 0.2f) else Color.Transparent,
                                    border = BorderStroke(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) KitchenSage else Color.LightGray.copy(alpha = 0.4f)
                                    ),
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(itemAvatar, fontSize = 18.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Zero Waste Achievement Badges Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("zero_waste_badges_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, KitchenSage.copy(alpha = 0.15f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(bottom = 12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stars,
                            contentDescription = null,
                            tint = KitchenClay,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Atıksız Mutfak Başarı Rozetlerim",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = DarkCharcoal
                        )
                    }

                    val badges = listOf(
                        BadgeInfo("Atıksız Başlangıç", "🍳", "İlk gıdanı israf olmaktan kurtardın!", totalSavedCount >= 1),
                        BadgeInfo("Karbon Avcısı", "🍃", "5 kg CO2 salınımını önledin!", totalCo2Saved >= 5.0),
                        BadgeInfo("Mutfak Koruyucusu", "🛡️", "300 yeşil puan barajını aştın!", totalScore >= 300),
                        BadgeInfo("Mavi Gezegen", "💧", "1000 litre sanal su tasarrufu!", totalWaterSavedLiters >= 1000.0),
                        BadgeInfo("Gıda Kahramanı", "👑", "10 yiyeceği başarıyla tükettin!", totalSavedCount >= 10)
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        badges.forEach { (name, emoji, desc, isUnlocked) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        if (isUnlocked) SoftSageBg.copy(alpha = 0.6f) else Color.LightGray.copy(alpha = 0.1f),
                                        RoundedCornerShape(10.dp)
                                    )
                                    .border(
                                        1.dp,
                                        if (isUnlocked) KitchenSage.copy(alpha = 0.15f) else Color.Transparent,
                                        RoundedCornerShape(10.dp)
                                    )
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(
                                            if (isUnlocked) HighlightGreen else Color.LightGray.copy(alpha = 0.3f),
                                            CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(emoji, fontSize = 18.sp)
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = name,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isUnlocked) DarkCharcoal else Color.Gray
                                    )
                                    Text(
                                        text = desc,
                                        fontSize = 10.sp,
                                        color = if (isUnlocked) Color.Gray else Color.Gray.copy(alpha = 0.7f)
                                    )
                                }

                                if (isUnlocked) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Kazanıldı",
                                        tint = SafeGreen,
                                        modifier = Modifier.size(16.dp)
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = "Kilitli",
                                        tint = Color.LightGray,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Green points card
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Score Card
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .testTag("score_summary_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = KitchenSage),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.Eco, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Yeşil Mutfak Puanı", fontSize = 11.sp, color = Color.White.copy(alpha = 0.8f))
                        Text(
                            "$totalScore XP",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                // CO2 Saved Card
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .testTag("co2_summary_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, KitchenSage.copy(alpha = 0.3f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.CloudQueue, contentDescription = null, tint = KitchenSage, modifier = Modifier.size(28.dp))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Önlenen CO2", fontSize = 11.sp, color = Color.Gray)
                        Text(
                            String.format(Locale.US, "%.1f kg", totalCo2Saved),
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = KitchenSage
                        )
                    }
                }
            }
        }

        // Stats card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Kurtarılan", fontSize = 11.sp, color = Color.Gray)
                        Text("$totalSavedCount Gıda", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = SafeGreen)
                    }
                    Divider(modifier = Modifier.height(30.dp).width(1.dp), color = Color.LightGray)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("İsraf Edilen", fontSize = 11.sp, color = Color.Gray)
                        Text("$totalWastedCount Gıda", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = AlertRed)
                    }
                }
            }
        }

        // Live Sustainability Feed
        item {
            val globalFeedEvents by viewModel.globalFeedEvents.collectAsStateWithLifecycle()
            
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("global_feed_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, KitchenSage.copy(alpha = 0.2f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(bottom = 10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChatBubbleOutline,
                            contentDescription = null,
                            tint = KitchenSage,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Canlı Sürdürülebilirlik Akışı",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = DarkCharcoal
                        )
                    }

                    if (globalFeedEvents.isEmpty()) {
                        Text(
                            text = "Akış yükleniyor...",
                            fontSize = 11.sp,
                            color = Color.Gray,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            globalFeedEvents.take(5).forEach { event ->
                                Row(
                                    verticalAlignment = Alignment.Top,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(SoftSageBg.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                        .padding(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Eco,
                                        contentDescription = null,
                                        tint = SafeGreen,
                                        modifier = Modifier.size(14.dp).padding(top = 2.dp)
                                    )
                                    Text(
                                        text = event,
                                        fontSize = 11.sp,
                                        color = DarkCharcoal,
                                        lineHeight = 15.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Spending & Budget Dashboard Card
        item {
            val activeItems by viewModel.activeItems.collectAsStateWithLifecycle(initialValue = emptyList())
            val activeStockValue = remember(activeItems) { activeItems.sumOf { it.price } }
            val savedValue = remember(actionLogs) { actionLogs.filter { it.actionType == "CONSUMED" }.sumOf { it.cost } }
            val wastedValue = remember(actionLogs) { actionLogs.filter { it.actionType == "WASTED" }.sumOf { it.cost } }
            val totalSpent = activeStockValue + savedValue + wastedValue

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.4f))
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "Mutfak Harcamalarım & Tasarruf Analizi 💰",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = DarkCharcoal
                    )
                    
                    Divider(color = Color.LightGray.copy(alpha = 0.2f))

                    // Total kitchen expense
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Toplam Alışveriş Tutarı", fontSize = 12.sp, color = Color.Gray)
                        Text(
                            "₺${String.format(Locale.US, "%.1f", totalSpent)}",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = DarkCharcoal
                        )
                    }

                    // Breakdown indicator bar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(Color.LightGray.copy(alpha = 0.3f))
                    ) {
                        Row(modifier = Modifier.fillMaxSize()) {
                            val activeRatio = if (totalSpent > 0.0) (activeStockValue / totalSpent).toFloat() else 0f
                            val savedRatio = if (totalSpent > 0.0) (savedValue / totalSpent).toFloat() else 0f
                            val wastedRatio = if (totalSpent > 0.0) (wastedValue / totalSpent).toFloat() else 0f

                            if (activeRatio > 0f) {
                                Box(
                                    modifier = Modifier
                                        .weight(activeRatio)
                                        .fillMaxHeight()
                                        .background(KitchenSage)
                                )
                            }
                            if (savedRatio > 0f) {
                                Box(
                                    modifier = Modifier
                                        .weight(savedRatio)
                                        .fillMaxHeight()
                                        .background(SafeGreen)
                                )
                            }
                            if (wastedRatio > 0f) {
                                Box(
                                    modifier = Modifier
                                        .weight(wastedRatio)
                                        .fillMaxHeight()
                                        .background(AlertRed)
                                )
                            }
                        }
                    }

                    // Mini Legends & Detailed Breakdown
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Active stock (KitchenSage)
                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(KitchenSage))
                                Text("Mevcut Stok", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                            }
                            Text("₺${String.format(Locale.US, "%.1f", activeStockValue)}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = DarkCharcoal)
                        }

                        // Saved/Consumed value (SafeGreen)
                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(SafeGreen))
                                Text("Tüketilen (Kurtarılan)", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                            }
                            Text("₺${String.format(Locale.US, "%.1f", savedValue)}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SafeGreen)
                        }

                        // Wasted value (AlertRed)
                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(AlertRed))
                                Text("İsraf Edilen (Zarar)", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                            }
                            Text("₺${String.format(Locale.US, "%.1f", wastedValue)}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AlertRed)
                        }
                    }
                }
            }
        }

        // Community Leaderboard (Takma Adlar ve Dereceler)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Atık Savaşçıları Liderlik Tablosu 🏆",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = DarkCharcoal
                        )
                        Text(
                            "Haftalık",
                            fontSize = 11.sp,
                            color = Color.Gray,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Divider(color = Color.LightGray.copy(alpha = 0.2f))

                    // Registered user profile score & rank
                    val nickname by viewModel.userNickname.collectAsStateWithLifecycle()
                    val avatar by viewModel.userAvatar.collectAsStateWithLifecycle()
                    val userTitle = viewModel.getUserTitle(totalScore)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(KitchenSage.copy(alpha = 0.08f), RoundedCornerShape(10.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            modifier = Modifier.size(38.dp),
                            shape = CircleShape,
                            color = KitchenSage.copy(alpha = 0.2f)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(avatar, fontSize = 22.sp)
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "$nickname (Kayıtlı Profil)",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = KitchenSage
                            )
                            Text(
                                text = "Ünvan: $userTitle",
                                fontSize = 11.sp,
                                color = DarkCharcoal,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "$totalScore XP",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = KitchenSage
                            )
                            Text(
                                text = "1. Sıra",
                                fontSize = 10.sp,
                                color = SafeGreen,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // API Configuration Checker (CRITICAL SECURITY AND CONFIGURATION REQUIREMENT)
        item {
            val isConfigured = viewModel.isApiKeyConfigured()
            val isValidPrefix = viewModel.isApiKeyValidPrefix()
            
            val statusColor = if (isConfigured && isValidPrefix) SafeGreen else AlertRed
            val statusBg = if (isConfigured && isValidPrefix) SafeGreen.copy(alpha = 0.08f) else AlertRed.copy(alpha = 0.08f)
            val statusText = if (!isConfigured) "Bağlantı Yok / Eksik" else if (!isValidPrefix) "Geçersiz Anahtar Formatı" else "Aktif / Doğrulandı"

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("api_status_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = statusBg),
                border = BorderStroke(1.dp, statusColor.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("AI Studio Bağlantı Durumu", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = DarkCharcoal)
                        Surface(
                            color = statusColor.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, statusColor)
                        ) {
                            Text(
                                statusText,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = statusColor,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    if (!isConfigured || !isValidPrefix) {
                        Text(
                            "Önemli Uyarı: Yapay zekanın market fişlerini ve görsellerini analiz edebilmesi için geçerli bir API anahtarı gereklidir. Yapılandırmayı AI Studio Secrets panelinden 'GEMINI_API_KEY' anahtarıyla ekleyebilirsiniz (örn. AIzaSy... veya AQ...).",
                            fontSize = 11.sp,
                            color = DarkCharcoal,
                            fontWeight = FontWeight.Medium
                        )
                    } else {
                        Text(
                            "Tebrikler! Google AI Studio Gemini API anahtarınız başarıyla yapılandırıldı. Fiş tarama, gıda analizi ve akıllı tarif motoru aktif.",
                            fontSize = 11.sp,
                            color = DarkCharcoal
                        )
                    }
                }
            }
        }

        // Account & Session Management Section
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("account_management_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.4f))
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ManageAccounts,
                            contentDescription = null,
                            tint = DarkCharcoal,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Hesap & Oturum Yönetimi",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = DarkCharcoal
                        )
                    }
                    Text(
                        text = "Profil adınızı veya avatarınızı sıfırlayarak yeni bir kullanıcı ile başlamak veya oturumu sonlandırmak için güvenli çıkış yapabilirsiniz.",
                        fontSize = 11.sp,
                        color = Color.Gray,
                        lineHeight = 15.sp
                    )
                    OutlinedButton(
                        onClick = { showLogoutDialog = true },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AlertRed),
                        border = BorderStroke(1.dp, AlertRed.copy(alpha = 0.6f)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().testTag("profile_logout_bottom_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Logout,
                            contentDescription = "Çıkış",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Hesaptan Güvenli Çıkış Yap",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }


    }
}

// ==================== DIALOGS ====================

@Composable
fun AddManualItemDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, quantity: String, category: String, shelfLifeDays: Int, storage: String, price: Double) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var quantity by remember { mutableStateOf("1 Adet") }
    var category by remember { mutableStateOf("Sebze & Meyve") }
    var shelfLifeString by remember { mutableStateOf("7") }
    var storage by remember { mutableStateOf("Buzdolabı") }
    var priceString by remember { mutableStateOf("") }

    val categories = listOf("Sebze & Meyve", "Süt Ürünleri", "Et & Tavuk", "Kiler / Kuru Gıda", "Dondurulmuş", "Diğer")
    val storages = listOf("Buzdolabı", "Kiler", "Dondurucu")

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .testTag("add_item_dialog_card"),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            LazyColumn(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        "Yeni Gıda Ekle",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = KitchenSage
                    )
                }

                item {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Ürün Adı") },
                        modifier = Modifier.fillMaxWidth().testTag("add_item_name_input"),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = KitchenSage)
                    )
                }

                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = quantity,
                            onValueChange = { quantity = it },
                            label = { Text("Miktar") },
                            modifier = Modifier.weight(1.3f).testTag("add_item_qty_input"),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = KitchenSage)
                        )

                        OutlinedTextField(
                            value = priceString,
                            onValueChange = { priceString = it },
                            label = { Text("Fiyat / Değer (₺)") },
                            modifier = Modifier.weight(1.5f).testTag("add_item_price_input"),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = KitchenSage)
                        )

                        OutlinedTextField(
                            value = shelfLifeString,
                            onValueChange = { shelfLifeString = it },
                            label = { Text("SKT (Gün)") },
                            modifier = Modifier.weight(1.1f).testTag("add_item_shelf_input"),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = KitchenSage)
                        )
                    }
                }

                item {
                    Text("Saklama Alanı:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        storages.forEach { item ->
                            val isSelected = storage == item
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        storage = item
                                        // Set smart default shelf life based on storage
                                        if (item == "Kiler") shelfLifeString = "30"
                                        else if (item == "Dondurucu") shelfLifeString = "90"
                                        else shelfLifeString = "7"
                                    }
                                    .testTag("storage_sel_$item"),
                                color = if (isSelected) KitchenSage else SoftSageBg,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    item,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) Color.White else DarkCharcoal,
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }

                item {
                    Text("Kategori Seçimi:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                }

                // List categories neatly
                item {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        mainAxisSpacing = 6.dp,
                        crossAxisSpacing = 6.dp
                    ) {
                        categories.forEach { item ->
                            val isSelected = category == item
                            Surface(
                                modifier = Modifier
                                    .clickable { category = item }
                                    .testTag("category_sel_$item"),
                                color = if (isSelected) KitchenClay else SoftSageBg,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    item,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) Color.White else DarkCharcoal,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("İptal")
                        }

                        Button(
                            onClick = {
                                if (name.trim().isEmpty()) {
                                    // simple validation
                                } else {
                                    val days = shelfLifeString.toIntOrNull() ?: 7
                                    val price = priceString.toDoubleOrNull() ?: 0.0
                                    onConfirm(name, quantity, category, days, storage, price)
                                }
                            },
                            modifier = Modifier.weight(1.5f).testTag("add_item_dialog_submit"),
                            colors = ButtonDefaults.buttonColors(containerColor = KitchenSage),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Envantere Kaydet")
                        }
                    }
                }
            }
        }
    }
}

// Simple FlowRow helper to layout items responsively
@Composable
fun FlowRow(
    modifier: Modifier = Modifier,
    mainAxisSpacing: androidx.compose.ui.unit.Dp = 0.dp,
    crossAxisSpacing: androidx.compose.ui.unit.Dp = 0.dp,
    content: @Composable () -> Unit
) {
    androidx.compose.ui.layout.Layout(
        content = content,
        modifier = modifier
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints) }
        val layoutWidth = constraints.maxWidth
        val lines = mutableListOf<List<androidx.compose.ui.layout.Placeable>>()
        var currentLine = mutableListOf<androidx.compose.ui.layout.Placeable>()
        var currentLineWidth = 0
        val mainAxisSpacingPx = mainAxisSpacing.roundToPx()
        val crossAxisSpacingPx = crossAxisSpacing.roundToPx()

        for (placeable in placeables) {
            val spaceNeeded = if (currentLineWidth == 0) placeable.width else placeable.width + mainAxisSpacingPx
            if (currentLineWidth + spaceNeeded <= layoutWidth) {
                currentLine.add(placeable)
                currentLineWidth += spaceNeeded
            } else {
                lines.add(currentLine)
                currentLine = mutableListOf(placeable)
                currentLineWidth = placeable.width
            }
        }
        if (currentLine.isNotEmpty()) {
            lines.add(currentLine)
        }

        var layoutHeight = 0
        for ((index, line) in lines.withIndex()) {
            val maxLineHeight = line.maxOf { it.height }
            layoutHeight += maxLineHeight
            if (index < lines.size - 1) {
                layoutHeight += crossAxisSpacingPx
            }
        }

        layout(layoutWidth, layoutHeight) {
            var y = 0
            for (line in lines) {
                var x = 0
                val maxLineHeight = line.maxOf { it.height }
                for (placeable in line) {
                    placeable.placeRelative(x, y + (maxLineHeight - placeable.height) / 2)
                    x += placeable.width + mainAxisSpacingPx
                }
                y += maxLineHeight + crossAxisSpacingPx
            }
        }
    }
}

@Composable
fun EditInventoryItemDialog(
    item: InventoryItem,
    onDismiss: () -> Unit,
    onSave: (InventoryItem) -> Unit
) {
    var name by remember { mutableStateOf(item.name) }
    var quantity by remember { mutableStateOf(item.quantity) }
    var category by remember { mutableStateOf(item.category) }
    var storage by remember { mutableStateOf(item.storage) }
    var remainingPercent by remember { mutableStateOf(item.remainingPercent) }
    var priceStr by remember { mutableStateOf(if (item.price > 0.0) item.price.toString() else "") }
    
    val initialRemainingDays = remember {
        val days = item.getRemainingDays()
        if (days < 0) 0 else days
    }
    var remainingDays by remember { mutableStateOf(initialRemainingDays) }

    val categories = listOf("Sebze & Meyve", "Süt Ürünleri", "Et & Tavuk", "Kiler / Kuru Gıda", "Dondurulmuş", "Diğer")
    val storages = listOf("Buzdolabı", "Kiler", "Dondurucu")

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Ürün Bilgilerini Güncelle",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = DarkCharcoal
                )
                Divider(color = Color.LightGray.copy(alpha = 0.3f))

                // Name Input
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Ürün Adı", fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = TextStyle(fontSize = 13.sp),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = KitchenSage,
                        unfocusedBorderColor = Color.LightGray
                    )
                )

                // Quantity Input
                OutlinedTextField(
                    value = quantity,
                    onValueChange = { quantity = it },
                    label = { Text("Miktar", fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = TextStyle(fontSize = 13.sp),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = KitchenSage,
                        unfocusedBorderColor = Color.LightGray
                    )
                )

                // Price Input
                OutlinedTextField(
                    value = priceStr,
                    onValueChange = { priceStr = it },
                    label = { Text("Fiyat / Değer (₺)", fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = TextStyle(fontSize = 13.sp),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = KitchenSage,
                        unfocusedBorderColor = Color.LightGray
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )

                // Kalan Miktar (Remaining Amount) Percentage Slider
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Kalan Miktar:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = DarkCharcoal)
                        Text("%$remainingPercent", fontSize = 13.sp, fontWeight = FontWeight.Black, color = KitchenSage)
                    }
                    Slider(
                        value = remainingPercent.toFloat(),
                        onValueChange = { remainingPercent = it.toInt() },
                        valueRange = 0f..100f,
                        colors = SliderDefaults.colors(
                            thumbColor = KitchenSage,
                            activeTrackColor = KitchenSage,
                            inactiveTrackColor = SoftSageBg
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Son Kullanma Tarihi (Precise expiry date selection via days slider / steppers)
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Tazelik Ömrü (Kalan Gün):", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = DarkCharcoal)
                        Text("$remainingDays Gün", fontSize = 13.sp, fontWeight = FontWeight.Black, color = KitchenClay)
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        IconButton(
                            onClick = { if (remainingDays > 0) remainingDays-- },
                            modifier = Modifier
                                .size(32.dp)
                                .background(SoftSageBg, CircleShape)
                        ) {
                            Text("-", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = DarkCharcoal, textAlign = TextAlign.Center)
                        }
                        Slider(
                            value = remainingDays.toFloat(),
                            onValueChange = { remainingDays = it.toInt() },
                            valueRange = 0f..60f,
                            colors = SliderDefaults.colors(
                                thumbColor = KitchenClay,
                                activeTrackColor = KitchenClay,
                                inactiveTrackColor = SoftSageBg
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { if (remainingDays < 60) remainingDays++ },
                            modifier = Modifier
                                .size(32.dp)
                                .background(SoftSageBg, CircleShape)
                        ) {
                            Text("+", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = DarkCharcoal, textAlign = TextAlign.Center)
                        }
                    }
                    // Show calculated date
                    val calendar = Calendar.getInstance()
                    calendar.add(Calendar.DAY_OF_YEAR, remainingDays)
                    val formattedDate = SimpleDateFormat("dd MMMM yyyy", Locale("tr")).format(calendar.time)
                    Text(
                        "Yeni SKT: $formattedDate",
                        fontSize = 11.sp,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                        color = Color.Gray,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                // Storage Select
                Column {
                    Text("Saklama Alanı:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = DarkCharcoal)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        storages.forEach { s ->
                            val isSelected = storage == s
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { storage = s },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) KitchenSage else SoftSageBg,
                                border = if (isSelected) null else BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.5f))
                            ) {
                                Text(
                                    text = s,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) Color.White else DarkCharcoal,
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1.5f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Vazgeç", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            val newExpiry = System.currentTimeMillis() + (remainingDays.toLong() * 24 * 60 * 60 * 1000)
                            val updatedItem = item.copy(
                                name = name,
                                quantity = quantity,
                                category = category,
                                storage = storage,
                                remainingPercent = remainingPercent,
                                expiryDate = newExpiry,
                                price = priceStr.toDoubleOrNull() ?: 0.0
                            )
                            onSave(updatedItem)
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = KitchenSage),
                        modifier = Modifier.weight(2f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Kaydet", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun SuggestionNutritionChip(text: String, bgColor: Color, textColor: Color) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = bgColor,
        modifier = Modifier.padding(2.dp)
    ) {
        Text(
            text = text,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = textColor,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
        )
    }
}

@Composable
fun DailyCalorieWidget(viewModel: InventoryViewModel) {
    val dailyGoal by viewModel.dailyCalorieGoal.collectAsState()
    val consumedCal by viewModel.consumedCaloriesToday.collectAsState()
    val protein by viewModel.consumedProteinToday.collectAsState()
    val carbs by viewModel.consumedCarbsToday.collectAsState()
    val fat by viewModel.consumedFatToday.collectAsState()

    var showGoalDialog by remember { mutableStateOf(false) }
    var showManualAddDialog by remember { mutableStateOf(false) }

    val progress = if (dailyGoal > 0) (consumedCal.toFloat() / dailyGoal).coerceIn(0f, 1f) else 0f
    val remaining = (dailyGoal - consumedCal).coerceAtLeast(0)

    Card(
        modifier = Modifier.fillMaxWidth().testTag("calorie_tracker_widget"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = AccentGreenBg),
        border = BorderStroke(1.dp, AccentGreenBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Widget Title & Header Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(
                        imageVector = Icons.Default.LocalFireDepartment,
                        contentDescription = "Kalori Takip",
                        tint = KitchenSage,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        "Günlük Kalori & Besin Değerleri",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = DarkCharcoal
                    )
                }
                
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    // Manual Add Button
                    IconButton(
                        onClick = { showManualAddDialog = true },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Manuel Kalori Ekle",
                            tint = KitchenSage,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    
                    // Edit Goal Button
                    IconButton(
                        onClick = { showGoalDialog = true },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Hedef Değiştir",
                            tint = KitchenSage,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    // Reset Button
                    IconButton(
                        onClick = { viewModel.resetDailyCalories() },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Sıfırla",
                            tint = KitchenClay,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Calorie progress row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "Tüketilen: $consumedCal kcal",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = DarkCharcoal
                    )
                    Text(
                        "Hedef: $dailyGoal kcal",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = HighlightGreen),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "Kalan: $remaining kcal",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = HighlightDark,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Progress bar
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)),
                color = KitchenSage,
                trackColor = Color.White.copy(alpha = 0.5f)
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Macros row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Protein Chip
                Column(
                    modifier = Modifier.weight(1f).background(Color.White.copy(alpha = 0.6f), RoundedCornerShape(8.dp)).padding(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("🥩 Protein", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                    Text("$protein g", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = DarkCharcoal)
                }
                Spacer(modifier = Modifier.width(6.dp))
                // Carbs Chip
                Column(
                    modifier = Modifier.weight(1f).background(Color.White.copy(alpha = 0.6f), RoundedCornerShape(8.dp)).padding(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("🌾 Karb.", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                    Text("$carbs g", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = DarkCharcoal)
                }
                Spacer(modifier = Modifier.width(6.dp))
                // Fat Chip
                Column(
                    modifier = Modifier.weight(1f).background(Color.White.copy(alpha = 0.6f), RoundedCornerShape(8.dp)).padding(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("🧈 Yağ", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                    Text("$fat g", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = DarkCharcoal)
                }
            }
        }
    }

    // Dialogs
    if (showGoalDialog) {
        var tempGoal by remember { mutableStateOf(dailyGoal.toString()) }
        AlertDialog(
            onDismissRequest = { showGoalDialog = false },
            title = { Text("Hedef Kalori Belirle", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = tempGoal,
                    onValueChange = { tempGoal = it },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                    label = { Text("Günlük Hedef (kcal)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val g = tempGoal.toIntOrNull() ?: 2000
                        viewModel.setDailyCalorieGoal(g)
                        showGoalDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = KitchenSage)
                ) {
                    Text("Kaydet")
                }
            },
            dismissButton = {
                TextButton(onClick = { showGoalDialog = false }) {
                    Text("İptal")
                }
            }
        )
    }

    if (showManualAddDialog) {
        var manualCal by remember { mutableStateOf("") }
        var manualProt by remember { mutableStateOf("") }
        var manualCarb by remember { mutableStateOf("") }
        var manualFat by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showManualAddDialog = false },
            title = { Text("Manuel Besin Değeri Ekle", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = manualCal,
                        onValueChange = { manualCal = it },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                        label = { Text("Kalori (kcal)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = manualProt,
                        onValueChange = { manualProt = it },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                        label = { Text("Protein (g)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = manualCarb,
                        onValueChange = { manualCarb = it },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                        label = { Text("Karbonhidrat (g)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = manualFat,
                        onValueChange = { manualFat = it },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                        label = { Text("Yağ (g)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.consumeRecipe(
                            title = "Manuel Giriş",
                            calories = manualCal.toIntOrNull() ?: 0,
                            protein = "${manualProt.toIntOrNull() ?: 0}g",
                            carbs = "${manualCarb.toIntOrNull() ?: 0}g",
                            fat = "${manualFat.toIntOrNull() ?: 0}g"
                        )
                        showManualAddDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = KitchenSage)
                ) {
                    Text("Ekle")
                }
            },
            dismissButton = {
                TextButton(onClick = { showManualAddDialog = false }) {
                    Text("İptal")
                }
            }
        )
    }
}

@Composable
fun RegistrationScreen(
    viewModel: InventoryViewModel,
    onRegister: (String, String) -> Unit
) {
    var username by remember { mutableStateOf("") }
    var selectedAvatar by remember { mutableStateOf("🥦") }
    val avatarList = listOf("🥦", "🥑", "🍎", "🍋", "👨‍🍳", "🥬", "🥕", "🧀")
    val validationError = remember(username) { viewModel.validateUsername(username) }
    val isValid = username.trim().isNotEmpty() && validationError == null

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    colors = listOf(AccentGreenBg, SoftSageBg)
                )
            )
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("registration_card"),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Selected Avatar Preview
                Surface(
                    modifier = Modifier.size(64.dp),
                    shape = CircleShape,
                    color = KitchenSage.copy(alpha = 0.12f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(text = selectedAvatar, fontSize = 34.sp)
                    }
                }

                Text(
                    text = "Aramıza Hoş Geldiniz! 🌱",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = DarkCharcoal,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "Sürdürülebilir akıllı mutfak topluluğuna katılmak için profil avatarınızı ve benzersiz kullanıcı adınızı belirleyin.",
                    fontSize = 11.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center,
                    lineHeight = 16.sp
                )

                // Avatar Selection Row
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Profil Simgesi / Avatar Seçin:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = KitchenSage
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(avatarList) { avatar ->
                            val isSelected = avatar == selectedAvatar
                            Surface(
                                onClick = { selectedAvatar = avatar },
                                shape = CircleShape,
                                color = if (isSelected) KitchenSage.copy(alpha = 0.2f) else Color.Transparent,
                                border = BorderStroke(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) KitchenSage else Color.LightGray.copy(alpha = 0.5f)
                                ),
                                modifier = Modifier.size(42.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(avatar, fontSize = 22.sp)
                                }
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Kullanıcı Adı Seçin") },
                    placeholder = { Text("Örn: SifirAtikŞefi") },
                    singleLine = true,
                    textStyle = TextStyle(color = DarkCharcoal, fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                    modifier = Modifier.fillMaxWidth().testTag("username_register_input"),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = DarkCharcoal,
                        unfocusedTextColor = DarkCharcoal,
                        focusedBorderColor = if (isValid) SafeGreen else KitchenSage,
                        unfocusedBorderColor = Color.LightGray,
                        focusedLabelColor = KitchenSage,
                        unfocusedLabelColor = Color.Gray
                    ),
                    isError = validationError != null && username.isNotEmpty()
                )

                // Real-time Validation Status Indicator
                if (username.isNotEmpty()) {
                    if (validationError != null) {
                        Surface(
                            color = Color(0xFFFDE8E8),
                            border = BorderStroke(1.dp, Color(0xFFF8B4B4)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = Color.Red, modifier = Modifier.size(18.dp))
                                Text(
                                    text = validationError,
                                    color = Color(0xFF9B1C1C),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    lineHeight = 15.sp
                                )
                            }
                        }
                    } else {
                        Surface(
                            color = Color(0xFFEDFDF5),
                            border = BorderStroke(1.dp, SafeGreen.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SafeGreen, modifier = Modifier.size(18.dp))
                                Text(
                                    text = "✓ Mükemmel! Bu kullanıcı adı benzersiz ve kullanılabilir.",
                                    color = SafeGreen,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // Registration Rules Info Box
                Surface(
                    color = SoftSageBg.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("📋 Kayıt & Kullanıcı Adı Kuralları:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = KitchenSage)
                        Text("• 🚫 Mükerrer İsim Yasaktır: Sistemdeki veya başka kullanıcıların adları alınamaz.", fontSize = 10.sp, color = DarkCharcoal)
                        Text("• 📏 Uzunluk: En az 3, en fazla 20 karakter olmalıdır.", fontSize = 10.sp, color = DarkCharcoal)
                        Text("• 🔤 Karakterler: Harfler, rakamlar, alt çizgi (_) ve tire (-) kullanılabilir.", fontSize = 10.sp, color = DarkCharcoal)
                        Text("• 🛡️ Korumalı İsimler: Admin, Sistem vb. unvanlar kullanılamaz.", fontSize = 10.sp, color = DarkCharcoal)
                    }
                }

                Button(
                    onClick = {
                        if (isValid) {
                            onRegister(username.trim(), selectedAvatar)
                        }
                    },
                    enabled = isValid,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("register_submit_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = KitchenSage,
                        disabledContainerColor = Color.LightGray
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Login, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(
                            "Kaydol & Topluluğa Katıl",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StartupAdOverlay(onDismiss: () -> Unit) {
    var countdown by remember { mutableStateOf(4) }
    val context = LocalContext.current
    
    val openAdUrl = {
        try {
            val intent = android.content.Intent(
                android.content.Intent.ACTION_VIEW,
                android.net.Uri.parse("https://www.google.com/search?q=organik+ve+doga+dostu+mutfak+urunleri")
            )
            context.startActivity(intent)
            onDismiss()
        } catch (e: Exception) {
            Toast.makeText(context, "Reklam bağlantısı açılamadı.", Toast.LENGTH_SHORT).show()
        }
    }
    
    // Countdown timer effect
    LaunchedEffect(Unit) {
        while (countdown > 0) {
            kotlinx.coroutines.delay(1000)
            countdown--
        }
    }

    Dialog(onDismissRequest = { if (countdown == 0) onDismiss() }) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .testTag("startup_ad_dialog"),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, KitchenSage.copy(alpha = 0.3f))
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header badge
                Surface(
                    color = Color(0xFF4285F4),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.align(Alignment.Start)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "Ad",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "• Google Ads / AdMob",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White
                        )
                    }
                }

                // Sponsor Logo/Visual
                Surface(
                    modifier = Modifier
                        .size(64.dp)
                        .clickable { openAdUrl() },
                    shape = CircleShape,
                    color = Color(0xFFE8F0FE)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.AdsClick,
                            contentDescription = null,
                            tint = Color(0xFF1A73E8),
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                Text(
                    text = "Google Reklamı: Organik & Doğa Dostu Mutfak 🛒",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = DarkCharcoal,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "Google Ads sponsorluğunda akıllı mutfak ve gıda saklama çözümleri. Sürdürülebilir mutfak ürünlerini ve çevre dostu saklama kaplarını hemen inceleyin.",
                    fontSize = 12.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                )

                // Google Ad Banner box inside Interstitial
                Surface(
                    color = Color(0xFFF8F9FA),
                    border = BorderStroke(1.dp, Color(0xFFDADCE0)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { openAdUrl() }
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Sponsorlu Bağlantı", fontSize = 10.sp, color = Color(0xFF5F6368))
                            Text("google.com/ads/organic-kitchen", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1A73E8))
                        }
                        
                        Surface(
                            color = Color(0xFF1A73E8),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.clickable { openAdUrl() }
                        ) {
                            Text(
                                text = "İncele",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Close/Skip Button
                Button(
                    onClick = onDismiss,
                    enabled = countdown == 0,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag("ad_close_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (countdown == 0) KitchenSage else Color.LightGray,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = if (countdown > 0) "Reklamı Geç ($countdown)" else "Reklamı Geç & Devam Et",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

@Composable
fun KitchenNotificationsSheet(
    warnings: List<ExpirationWarning>,
    readWarningIds: Set<Int>,
    onMarkAllAsRead: () -> Unit,
    onMarkAsRead: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (warnings.any { it.itemId !in readWarningIds }) {
                    TextButton(
                        onClick = {
                            onMarkAllAsRead()
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = KitchenSage)
                    ) {
                        Icon(Icons.Default.DoneAll, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Tümünü Okundu İşaretle", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

                Button(
                    onClick = {
                        onMarkAllAsRead()
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = KitchenSage),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Tamam & Kapat", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
            }
        },
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.NotificationsActive,
                    contentDescription = null,
                    tint = KitchenSage,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "Mutfak Bildirimleri",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = DarkCharcoal
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Info text
                Text(
                    text = "Gıda israfını önlemek ve bütçenizi korumak için son tüketim tarihi yaklaşan ürünlerinizi listeledik. Sistem bildirimleriyle 1 hafta, 3 gün ve 1 gün kala sizi uyarırız. 🌱",
                    fontSize = 11.sp,
                    color = Color.Gray,
                    lineHeight = 16.sp
                )

                // Request Notification Permission Button on Android 13+
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val context = LocalContext.current
                    var hasPermission by remember {
                        mutableStateOf(
                            androidx.core.content.ContextCompat.checkSelfPermission(
                                context,
                                android.Manifest.permission.POST_NOTIFICATIONS
                            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                        )
                    }

                    val permissionLauncher = rememberLauncherForActivityResult(
                        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
                    ) { isGranted ->
                        hasPermission = isGranted
                    }

                    if (!hasPermission) {
                        Surface(
                            color = KitchenSage.copy(alpha = 0.08f),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, KitchenSage.copy(alpha = 0.2f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "Native Bildirimleri Alın 🔔",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = DarkCharcoal
                                )
                                Text(
                                    text = "Son tüketim tarihi yaklaştığında telefonunuza anlık bildirim gönderebilmemiz için izin vermelisiniz.",
                                    fontSize = 10.sp,
                                    color = Color.Gray,
                                    textAlign = TextAlign.Center
                                )
                                Button(
                                    onClick = {
                                        permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = KitchenSage),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                ) {
                                    Text("Bildirim İzni Ver", fontSize = 11.sp)
                                }
                            }
                        }
                    } else {
                        Surface(
                            color = SafeGreen.copy(alpha = 0.08f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = SafeGreen,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Anlık sistem bildirimleri etkin ve aktif durumda! 🌱",
                                    fontSize = 10.sp,
                                    color = SafeGreen,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                Divider(color = Color.LightGray.copy(alpha = 0.3f))

                if (warnings.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("🎉", fontSize = 32.sp)
                            Text(
                                "Her Şey Güvende!",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = DarkCharcoal
                            )
                            Text(
                                "Yakın zamanda süresi dolacak hiçbir ürününüz bulunmuyor.",
                                fontSize = 10.sp,
                                color = Color.Gray,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(warnings) { warning ->
                            val isRead = warning.itemId in readWarningIds
                            val (bgColor, borderColor, textColor, icon) = when (warning.level) {
                                WarningLevel.EXPIRED -> Quad(Color(0xFFFBEBEB), Color(0xFFF3C0C0), Color(0xFFC0392B), Icons.Default.ErrorOutline)
                                WarningLevel.CRITICAL -> Quad(Color(0xFFFEF5E7), Color(0xFFFDE3A7), Color(0xFFD35400), Icons.Default.PriorityHigh)
                                WarningLevel.WARNING -> Quad(Color(0xFFFEF9E7), Color(0xFFF9E79F), Color(0xFFB7950B), Icons.Default.Warning)
                                WarningLevel.INFO -> Quad(SoftSageBg, KitchenSage.copy(alpha = 0.2f), KitchenSage, Icons.Default.Info)
                            }

                            Surface(
                                color = if (isRead) Color.White else bgColor,
                                border = BorderStroke(1.dp, if (isRead) Color.LightGray.copy(alpha = 0.4f) else borderColor),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onMarkAsRead(warning.itemId) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (isRead) Icons.Default.CheckCircleOutline else icon,
                                        contentDescription = null,
                                        tint = if (isRead) Color.Gray else textColor,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = warning.itemName,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp,
                                                color = if (isRead) Color.Gray else DarkCharcoal
                                            )
                                            Text(
                                                text = when {
                                                    warning.daysLeft < 0 -> "Süresi Geçti"
                                                    warning.daysLeft == 0 -> "Son Gün!"
                                                    else -> "${warning.daysLeft} gün kaldı"
                                                },
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 10.sp,
                                                color = if (isRead) Color.Gray else textColor
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = warning.message,
                                            fontSize = 9.sp,
                                            color = Color.Gray
                                        )
                                    }

                                    if (!isRead) {
                                        IconButton(
                                            onClick = { onMarkAsRead(warning.itemId) },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Okundu",
                                                tint = SafeGreen,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        shape = RoundedCornerShape(20.dp),
        containerColor = Color.White
    )
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

@Composable
fun GoogleAdBanner(
    modifier: Modifier = Modifier,
    adTitle: String = "Organik Mutfak Ürünleri & Sürdürülebilir Saklama Kapları",
    adDescription: String = "Sürdürülebilir mutfak için %100 doğa dostu cam kavanoz ve vakumlu saklama seti.",
    advertiser: String = "google.com/ads • Google AdMob"
) {
    var isAdVisible by remember { mutableStateOf(true) }
    var showAdInfo by remember { mutableStateOf(false) }
    var isAdMobLoaded by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val adUnitId = "ca-app-pub-7805317320781330/1926664021"

    val openAdUrl = {
        try {
            val intent = android.content.Intent(
                android.content.Intent.ACTION_VIEW,
                android.net.Uri.parse("https://www.google.com/search?q=organik+ve+doga+dostu+mutfak+urunleri")
            )
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Reklam bağlantısı açılamadı.", Toast.LENGTH_SHORT).show()
        }
    }

    if (isAdVisible) {
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .clickable { openAdUrl() },
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFFF8F9FA),
            border = BorderStroke(1.dp, Color(0xFFDADCE0))
        ) {
            Column(
                modifier = Modifier.padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Real Google AdMob SDK AdView
                AndroidView(
                    factory = { ctx ->
                        AdView(ctx).apply {
                            setAdSize(AdSize.BANNER)
                            this.adUnitId = adUnitId
                            adListener = object : com.google.android.gms.ads.AdListener() {
                                override fun onAdLoaded() {
                                    isAdMobLoaded = true
                                }
                            }
                            try {
                                loadAd(AdRequest.Builder().build())
                            } catch (_: Exception) {}
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                if (!isAdMobLoaded) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Surface(
                                color = Color(0xFF4285F4),
                                shape = RoundedCornerShape(3.dp)
                            ) {
                                Text(
                                    text = "Ad",
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                            Text(
                                text = advertiser,
                                fontSize = 10.sp,
                                color = Color(0xFF5F6368),
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            IconButton(
                                onClick = { showAdInfo = true },
                                modifier = Modifier.size(20.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = "Reklam Bilgisi",
                                    tint = Color(0xFF5F6368),
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            IconButton(
                                onClick = { isAdVisible = false },
                                modifier = Modifier.size(20.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Reklamı Kapat",
                                    tint = Color(0xFF5F6368),
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { openAdUrl() }
                        ) {
                            Text(
                                text = adTitle,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = Color(0xFF202124)
                            )
                            Text(
                                text = adDescription,
                                fontSize = 10.sp,
                                color = Color(0xFF5F6368),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Button(
                            onClick = { openAdUrl() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A73E8)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text("İncele", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    if (showAdInfo) {
        AlertDialog(
            onDismissRequest = { showAdInfo = false },
            confirmButton = {
                TextButton(onClick = { showAdInfo = false }) {
                    Text("Tamam", color = Color(0xFF1A73E8), fontWeight = FontWeight.Bold)
                }
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.AdsClick, contentDescription = null, tint = Color(0xFF1A73E8))
                    Text("Google AdMob Reklamı", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Bu alan Google AdMob reklam ağı tarafından dinamik olarak yayınlanmaktadır.", fontSize = 12.sp)
                    Text("Google Reklam Politikaları'na uygundur. Reklamlar uygulamanın ücretsiz kalmasını ve sürdürülebilirlik algoritmalarının geliştirilmesini destekler.", fontSize = 11.sp, color = Color.Gray)
                }
            }
        )
    }
}

// ==================== DEDICATED SECTIONS ====================

@Composable
fun ExpiringSectionTab(
    activeItems: List<InventoryItem>,
    onConsume: (InventoryItem) -> Unit,
    onWaste: (InventoryItem) -> Unit,
    onUpdateItem: (InventoryItem) -> Unit,
    onNavigateToRecipes: () -> Unit
) {
    var editingItem by remember { mutableStateOf<InventoryItem?>(null) }
    val expiringOrLowItems = remember(activeItems) {
        activeItems.filter { it.getRemainingDays() in 0..3 || it.remainingPercent <= 25 }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "🚨 Acil Tüketilecekler Bölmesi",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = DarkCharcoal
                )
                Text(
                    "SKT'si 3 günden az kalan veya miktarı %25'in altına inen gıdalar",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
            if (expiringOrLowItems.isNotEmpty()) {
                Button(
                    onClick = onNavigateToRecipes,
                    colors = ButtonDefaults.buttonColors(containerColor = KitchenClay),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.RestaurantMenu, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Tarif Öner", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("expiring_info_card"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = KitchenClay.copy(alpha = 0.08f)),
            border = BorderStroke(1.dp, KitchenClay.copy(alpha = 0.3f))
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = KitchenClay, modifier = Modifier.size(24.dp))
                Column {
                    Text(
                        "${expiringOrLowItems.size} Ürün Öncelikli Tüketilmeli",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = DarkCharcoal
                    )
                    Text(
                        "Bu bölmedeki gıdaları tüketerek hem israfı önleyebilir hem de +Yeşil Puan kazanabilirsiniz.",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (expiringOrLowItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(24.dp)
                ) {
                    Text("🎉", fontSize = 48.sp)
                    Text(
                        "Tebrikler! Tüketilmesi gereken ürün kalmadı.",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = DarkCharcoal
                    )
                    Text(
                        "Tüm gıdalarınız taze ve ideal stok miktarına sahip. Mutfak israfını başarıyla önlüyorsunuz! 🌱",
                        textAlign = TextAlign.Center,
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(expiringOrLowItems, key = { it.id }) { item ->
                    InventoryItemCard(
                        item = item,
                        onConsume = { onConsume(item) },
                        onWaste = { onWaste(item) },
                        onEditClick = { editingItem = item }
                    )
                }
            }
        }
    }

    editingItem?.let { itemToEdit ->
        EditInventoryItemDialog(
            item = itemToEdit,
            onDismiss = { editingItem = null },
            onSave = onUpdateItem
        )
    }
}

@Composable
fun ShoppingSectionTab(
    viewModel: InventoryViewModel,
    onAddToInventoryFromShopping: (String) -> Unit
) {
    val context = LocalContext.current
    val shoppingList by viewModel.shoppingList.collectAsStateWithLifecycle()
    var newShoppingText by remember { mutableStateOf("") }

    val quickAddSuggestions = listOf("Süt", "Yumurta", "Yoğurt", "Peynir", "Ekmek", "Domates", "Patates", "Zeytinyağı")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "🛒 Alışveriş Listem Bölmesi",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = DarkCharcoal
                )
                Text(
                    "İhtiyaçlarınızı planlayın, satın aldıklarınızı tek tıkla dolabınıza ekleyin.",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
            if (shoppingList.isNotEmpty()) {
                TextButton(
                    onClick = { viewModel.clearShoppingList() },
                    colors = ButtonDefaults.textButtonColors(contentColor = AlertRed)
                ) {
                    Text("Temizle", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Add Item Input Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = newShoppingText,
                onValueChange = { newShoppingText = it },
                placeholder = { Text("Örn: 2 kg Domates, 1 Paket Kaşar...", fontSize = 12.sp) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("shopping_tab_input"),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = KitchenSage,
                    unfocusedBorderColor = Color.LightGray
                ),
                singleLine = true
            )

            Button(
                onClick = {
                    if (newShoppingText.isNotBlank()) {
                        viewModel.addToShoppingList(newShoppingText)
                        newShoppingText = ""
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = KitchenSage),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .height(52.dp)
                    .testTag("shopping_tab_add_btn")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Ekle", modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Ekle", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Quick add chips
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(vertical = 4.dp)
        ) {
            items(quickAddSuggestions) { item ->
                AssistChip(
                    onClick = { viewModel.addToShoppingList(item) },
                    label = { Text("+ $item", fontSize = 11.sp, color = DarkCharcoal) },
                    shape = RoundedCornerShape(16.dp),
                    colors = AssistChipDefaults.assistChipColors(containerColor = SoftSageBg)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (shoppingList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(24.dp)
                ) {
                    Text("🥗", fontSize = 42.sp)
                    Text(
                        "Alışveriş listeniz henüz boş!",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = DarkCharcoal
                    )
                    Text(
                        "Yukarıdaki alandan ihtiyacınız olan gıdaları ekleyerek eksiklerinizi planlayabilirsiniz.",
                        fontSize = 12.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(shoppingList, key = { it }) { item ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, BorderColor)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.ShoppingCart, contentDescription = null, tint = KitchenSage, modifier = Modifier.size(20.dp))
                                Text(
                                    text = item,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = DarkCharcoal
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Button(
                                    onClick = {
                                        onAddToInventoryFromShopping(item)
                                        viewModel.removeFromShoppingList(item)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = KitchenSage),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Icon(Icons.Default.Kitchen, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Dolaba Ekle", fontSize = 11.sp)
                                }

                                IconButton(
                                    onClick = { viewModel.removeFromShoppingList(item) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Sil", tint = AlertRed, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

