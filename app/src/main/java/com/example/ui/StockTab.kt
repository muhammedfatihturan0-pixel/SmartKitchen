package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Kitchen
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Kitchen
import androidx.compose.material.icons.outlined.Store
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.InventoryItem
import java.util.Locale

@Composable
fun StockTab(
    activeItems: List<InventoryItem>,
    totalScore: Int,
    onAddClick: () -> Unit,
    onOpenBarcodeScanner: () -> Unit,
    onOpenMonthlyReport: () -> Unit,
    onConsume: (InventoryItem) -> Unit,
    onWaste: (InventoryItem) -> Unit,
    onUpdateItem: (InventoryItem) -> Unit,
    viewModel: InventoryViewModel,
    onNavigateToTab: (String) -> Unit = {}
) {
    var searchQuery by remember { mutableStateOf("") }
    var filterStorage by remember { mutableStateOf("Tümü") }
    var filterStatus by remember { mutableStateOf("Tümü") }
    var sortOption by remember { mutableStateOf("SKT") } // "SKT", "NAME", "PERCENT"
    var editingItem by remember { mutableStateOf<InventoryItem?>(null) }

    val storageCounts = remember(activeItems) {
        mapOf(
            "Tümü" to activeItems.size,
            "Buzdolabı" to activeItems.count { it.storage == "Buzdolabı" },
            "Kiler" to activeItems.count { it.storage == "Kiler" },
            "Dondurucu" to activeItems.count { it.storage == "Dondurucu" }
        )
    }

    val filteredItems = remember(activeItems, searchQuery, filterStorage, filterStatus, sortOption) {
        var items = if (filterStorage == "Tümü") activeItems else activeItems.filter { it.storage == filterStorage }

        if (searchQuery.isNotBlank()) {
            items = items.filter {
                it.name.contains(searchQuery, ignoreCase = true) ||
                    it.category.contains(searchQuery, ignoreCase = true)
            }
        }

        items = when (filterStatus) {
            "Az Kalanlar" -> items.filter { it.getRemainingDays() in 0..3 || it.remainingPercent <= 25 }
            "Tüketilebilir" -> items.filter { it.getRemainingDays() >= 4 }
            "Süresi Geçenler" -> items.filter { it.getRemainingDays() < 0 }
            else -> items
        }

        when (sortOption) {
            "NAME" -> items.sortedBy { it.name.lowercase(Locale.forLanguageTag("tr")) }
            "PERCENT" -> items.sortedBy { it.remainingPercent }
            else -> items.sortedBy { it.getRemainingDays() }
        }
    }

    val urgentCount = remember(activeItems) {
        activeItems.count { it.isUrgent() }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(10.dp))

            // Header with Add & Barcode Quick Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Dolap & Kiler Envanterim",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = DarkCharcoal
                    )
                    Text(
                        text = "${activeItems.size} aktif ürün saklanıyor",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick = onOpenBarcodeScanner,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, KitchenSage),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("open_barcode_btn")
                    ) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = "Barkod", tint = KitchenSage, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Barkod", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = KitchenSage)
                    }

                    Button(
                        onClick = onAddClick,
                        colors = ButtonDefaults.buttonColors(containerColor = KitchenSage),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("add_item_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Ekle", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Ekle", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Urgent Banner
            if (urgentCount > 0) {
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
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = KitchenClay, modifier = Modifier.size(18.dp))
                            Text(
                                text = "$urgentCount ürün acil tüketilmeli veya az kalmış!",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = DarkCharcoal
                            )
                        }
                        Text(
                            text = "Gör ->",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = KitchenClay
                        )
                    }
                }
            }

            // Eco Impact Tracker Card + Monthly Report Shortcut
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                color = AccentGreenBg,
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, AccentGreenBorder)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "YEŞİL MUTFAK PUANI",
                            fontSize = 10.sp,
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
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = viewModel.getUserTitle(totalScore),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = KitchenSage,
                                modifier = Modifier.padding(bottom = 3.dp)
                            )
                        }
                    }

                    Surface(
                        onClick = onOpenMonthlyReport,
                        color = Color.White,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, KitchenSage.copy(alpha = 0.3f)),
                        modifier = Modifier.testTag("stock_monthly_report_btn")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(Icons.Default.Assessment, contentDescription = "Aylık Karne", tint = KitchenSage, modifier = Modifier.size(16.dp))
                            Text(
                                text = "Aylık Karne",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = KitchenSage
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Search Field
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Dolapta ara (örn: süt, domates)...", fontSize = 12.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Ara", tint = Color.Gray, modifier = Modifier.size(18.dp)) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Temizle", tint = Color.Gray, modifier = Modifier.size(16.dp))
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("stock_search_input"),
                shape = RoundedCornerShape(12.dp),
                textStyle = TextStyle(fontSize = 12.sp, color = DarkCharcoal),
                colors = smartKitchenFieldColors()
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Storage Filter Row with Live Counts
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf("Tümü", "Buzdolabı", "Kiler", "Dondurucu").forEach { storageOption ->
                    val isSelected = filterStorage == storageOption
                    val count = storageCounts[storageOption] ?: 0
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
                            text = "$storageOption ($count)",
                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 2.dp),
                            textAlign = TextAlign.Center,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) Color.White else DarkCharcoal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Section Header with Status Filter & Sort Dropdown
            var showStatusMenu by remember { mutableStateOf(false) }
            var showSortMenu by remember { mutableStateOf(false) }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (filterStorage == "Tümü") "Ürünler (${filteredItems.size})" else "$filterStorage (${filteredItems.size})",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = DarkCharcoal
                )

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Sort selector
                    Box {
                        Surface(
                            modifier = Modifier
                                .clickable { showSortMenu = true }
                                .testTag("sort_dropdown_btn"),
                            color = Color.White,
                            shape = RoundedCornerShape(20.dp),
                            border = BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.5f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(Icons.Default.Sort, contentDescription = "Sırala", tint = DarkCharcoal, modifier = Modifier.size(13.dp))
                                Text(
                                    text = when (sortOption) {
                                        "NAME" -> "A-Z"
                                        "PERCENT" -> "Miktar"
                                        else -> "SKT"
                                    },
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = DarkCharcoal
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false },
                            modifier = Modifier.background(Color.White)
                        ) {
                            listOf(
                                "SKT" to "SKT Yakın Olan Önce",
                                "NAME" to "İsme Göre (A-Z)",
                                "PERCENT" to "Kalan Yüzdeye Göre"
                            ).forEach { (key, label) ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            label,
                                            fontSize = 12.sp,
                                            fontWeight = if (sortOption == key) FontWeight.Bold else FontWeight.Normal,
                                            color = if (sortOption == key) KitchenSage else DarkCharcoal
                                        )
                                    },
                                    onClick = {
                                        sortOption = key
                                        showSortMenu = false
                                    }
                                )
                            }
                        }
                    }

                    // Status filter
                    Box {
                        Surface(
                            modifier = Modifier
                                .clickable { showStatusMenu = true }
                                .testTag("status_filter_dropdown_btn"),
                            color = if (filterStatus != "Tümü") KitchenClay.copy(alpha = 0.12f) else Color.White,
                            shape = RoundedCornerShape(20.dp),
                            border = BorderStroke(1.dp, if (filterStatus != "Tümü") KitchenClay else Color.LightGray.copy(alpha = 0.5f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FilterList,
                                    contentDescription = "Filtre",
                                    tint = if (filterStatus != "Tümü") KitchenClay else DarkCharcoal,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = if (filterStatus == "Tümü") "Durum" else filterStatus,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (filterStatus != "Tümü") KitchenClay else DarkCharcoal
                                )
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = "Aç",
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
                                "Tümü" to "Tüm Durumlar",
                                "Az Kalanlar" to "⚠️ Az Kalanlar / Acil",
                                "Tüketilebilir" to "✅ Tüketilebilir (4+ Gün)",
                                "Süresi Geçenler" to "🚨 Süresi Geçenler"
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
            }

            Spacer(modifier = Modifier.height(8.dp))

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
                            contentDescription = "Boş dolap",
                            tint = Color.LightGray,
                            modifier = Modifier.size(64.dp)
                        )
                        Text(
                            if (activeItems.isEmpty()) "Dolabınız henüz boş" else "Bu filtrede ürün bulunamadı.",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = Color.Gray
                        )
                        Text(
                            "Fiş veya barkod taratarak ya da 'Ekle' butonundan ürün girerek dolabınızı kolayca doldurun.",
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
        }

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

    val (badgeBgColor, badgeTextColor, badgeBorderColor) = when {
        remainingDays < 0 -> Triple(Color(0xFFFFDAD6), Color(0xFF410002), Color(0xFFFFB4AB))
        remainingDays <= 2 -> Triple(Color(0xFFFFDAD6), Color(0xFF410002), Color(0xFFFFB4AB))
        remainingDays <= 5 -> Triple(Color(0xFFFFDBC1), Color(0xFF2B1700), Color(0xFFFFCC80))
        else -> Triple(Color(0xFFE8F3D6), Color(0xFF386B01), Color(0xFFB7CC9D))
    }

    val badgeText = when {
        remainingDays < 0 -> "Süresi Geçti!"
        remainingDays == 0 -> "Bugün Son Gün!"
        remainingDays == 1 -> "1 Gün Kaldı - Acil!"
        remainingDays == 2 -> "2 Gün Kaldı - Acil!"
        else -> "$remainingDays Gün Kaldı"
    }

    val storageIcon = when (item.storage) {
        "Buzdolabı" -> Icons.Default.Kitchen
        "Dondurucu" -> Icons.Default.AcUnit
        else -> Icons.Outlined.Store
    }

    val indicatorColor = when {
        remainingDays < 0 -> AlertRed
        remainingDays <= 2 -> Color(0xFFD32F2F)
        remainingDays <= 5 -> Color(0xFFFBC02D)
        else -> Color(0xFF388E3C)
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
        ) {
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .fillMaxHeight()
                    .background(indicatorColor)
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                item.name,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = DarkCharcoal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
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
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                "Miktar: ${item.quantity}",
                                fontSize = 11.sp,
                                color = Color.Gray,
                                fontWeight = FontWeight.Medium
                            )
                            Text("•", color = Color.LightGray)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = storageIcon,
                                    contentDescription = item.storage,
                                    tint = Color.Gray,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(item.storage, fontSize = 11.sp, color = Color.Gray)
                            }
                            if (item.price > 0.0) {
                                Text("•", color = Color.LightGray)
                                Text(
                                    "₺${String.format(Locale.US, "%.1f", item.price)}",
                                    fontSize = 11.sp,
                                    color = Color(0xFFC15C3D),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    Surface(
                        color = badgeBgColor,
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, badgeBorderColor)
                    ) {
                        Text(
                            text = badgeText,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = badgeTextColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Kalan: %${item.remainingPercent}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = KitchenSage
                    )
                    LinearProgressIndicator(
                        progress = { (item.remainingPercent / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .weight(1f)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = if (item.remainingPercent <= 25) AlertRed else if (item.remainingPercent <= 50) KitchenClay else KitchenSage,
                        trackColor = SoftSageBg
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = Color.LightGray.copy(alpha = 0.3f))
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
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
                        Icon(Icons.Default.Delete, contentDescription = "İsraf", modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("İsraf Oldu", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = onConsume,
                        modifier = Modifier
                            .weight(1.3f)
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
