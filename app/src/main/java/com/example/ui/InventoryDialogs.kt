package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import com.example.api.DetectedItem
import com.example.data.InventoryItem
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private val kitchenCategories = listOf(
    "Sebze & Meyve",
    "Süt Ürünleri",
    "Et & Tavuk",
    "Kiler / Kuru Gıda",
    "Dondurulmuş",
    "Diğer"
)

private val kitchenStorages = listOf("Buzdolabı", "Kiler", "Dondurucu")

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
    var showNameError by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp)
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
                    Column {
                        OutlinedTextField(
                            value = name,
                            onValueChange = {
                                name = it
                                if (it.isNotBlank()) showNameError = false
                            },
                            label = { Text("Ürün Adı") },
                            placeholder = { Text("Örn: Domates, Süt, Peynir", fontSize = 12.sp) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("add_item_name_input"),
                            singleLine = true,
                            isError = showNameError,
                            colors = smartKitchenFieldColors()
                        )
                        if (showNameError) {
                            Text(
                                "Lütfen ürün adını yazın.",
                                fontSize = 11.sp,
                                color = AlertRed,
                                modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                            )
                        }
                    }
                }

                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = quantity,
                            onValueChange = { quantity = it },
                            label = { Text("Miktar") },
                            modifier = Modifier
                                .weight(1.2f)
                                .testTag("add_item_qty_input"),
                            singleLine = true,
                            colors = smartKitchenFieldColors()
                        )

                        OutlinedTextField(
                            value = priceString,
                            onValueChange = { priceString = it },
                            label = { Text("Fiyat (₺)") },
                            modifier = Modifier
                                .weight(1.1f)
                                .testTag("add_item_price_input"),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            colors = smartKitchenFieldColors()
                        )

                        OutlinedTextField(
                            value = shelfLifeString,
                            onValueChange = { shelfLifeString = it },
                            label = { Text("SKT (Gün)") },
                            modifier = Modifier
                                .weight(1.0f)
                                .testTag("add_item_shelf_input"),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            colors = smartKitchenFieldColors()
                        )
                    }
                }

                item {
                    Text("Saklama Alanı:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        kitchenStorages.forEach { item ->
                            val isSelected = storage == item
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        storage = item
                                        shelfLifeString = when (item) {
                                            "Kiler" -> "60"
                                            "Dondurucu" -> "90"
                                            else -> "7"
                                        }
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

                item {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        mainAxisSpacing = 6.dp,
                        crossAxisSpacing = 6.dp
                    ) {
                        kitchenCategories.forEach { item ->
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
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("İptal", color = DarkCharcoal)
                        }

                        Button(
                            onClick = {
                                val trimmedName = name.trim()
                                if (trimmedName.isEmpty()) {
                                    showNameError = true
                                } else {
                                    val days = shelfLifeString.toIntOrNull()?.coerceAtLeast(0) ?: 7
                                    val price = priceString.replace(",", ".").toDoubleOrNull() ?: 0.0
                                    onConfirm(trimmedName, quantity.ifBlank { "1 Adet" }, category, days, storage, price)
                                }
                            },
                            modifier = Modifier
                                .weight(1.5f)
                                .testTag("add_item_dialog_submit"),
                            colors = ButtonDefaults.buttonColors(containerColor = KitchenSage),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Envantere Kaydet", fontWeight = FontWeight.Bold)
                        }
                    }
                }
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
    var remainingDays by remember { mutableStateOf(item.getRemainingDays().coerceIn(0, 60)) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color.White,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 12.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Ürün Bilgilerini Güncelle",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = DarkCharcoal
                )
                HorizontalDivider(color = Color.LightGray.copy(alpha = 0.3f))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Ürün Adı", fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = TextStyle(fontSize = 13.sp, color = DarkCharcoal),
                    shape = RoundedCornerShape(12.dp),
                    colors = smartKitchenFieldColors()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = quantity,
                        onValueChange = { quantity = it },
                        label = { Text("Miktar", fontSize = 11.sp) },
                        modifier = Modifier.weight(1f),
                        textStyle = TextStyle(fontSize = 13.sp, color = DarkCharcoal),
                        shape = RoundedCornerShape(12.dp),
                        colors = smartKitchenFieldColors()
                    )

                    OutlinedTextField(
                        value = priceStr,
                        onValueChange = { priceStr = it },
                        label = { Text("Fiyat (₺)", fontSize = 11.sp) },
                        modifier = Modifier.weight(1f),
                        textStyle = TextStyle(fontSize = 13.sp, color = DarkCharcoal),
                        shape = RoundedCornerShape(12.dp),
                        colors = smartKitchenFieldColors(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                }

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
                            modifier = Modifier.size(32.dp)
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
                            modifier = Modifier.size(32.dp)
                        ) {
                            Text("+", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = DarkCharcoal, textAlign = TextAlign.Center)
                        }
                    }
                    val calendar = Calendar.getInstance()
                    calendar.add(Calendar.DAY_OF_YEAR, remainingDays)
                    val formattedDate = SimpleDateFormat("dd MMMM yyyy", Locale.forLanguageTag("tr")).format(calendar.time)
                    Text(
                        "Yeni SKT: $formattedDate",
                        fontSize = 11.sp,
                        fontStyle = FontStyle.Italic,
                        color = Color.Gray,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                Column {
                    Text("Saklama Alanı:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = DarkCharcoal)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        kitchenStorages.forEach { s ->
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

                Column {
                    Text("Kategori:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = DarkCharcoal)
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        mainAxisSpacing = 6.dp,
                        crossAxisSpacing = 6.dp
                    ) {
                        kitchenCategories.forEach { cat ->
                            val isSelected = category == cat
                            Surface(
                                modifier = Modifier.clickable { category = cat },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) KitchenClay else SoftSageBg,
                                border = if (isSelected) null else BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.4f))
                            ) {
                                Text(
                                    text = cat,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) Color.White else DarkCharcoal,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1.3f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Vazgeç", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = DarkCharcoal)
                    }

                    Button(
                        onClick = {
                            val newExpiry = System.currentTimeMillis() + (remainingDays.toLong() * 24L * 60L * 60L * 1000L)
                            val updatedItem = item.copy(
                                name = name.trim().ifBlank { item.name },
                                quantity = quantity.trim().ifBlank { item.quantity },
                                category = category,
                                storage = storage,
                                remainingPercent = remainingPercent,
                                expiryDate = newExpiry,
                                price = priceStr.replace(",", ".").toDoubleOrNull() ?: 0.0
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
fun EditDetectedItemDialog(
    item: DetectedItem,
    onDismiss: () -> Unit,
    onSave: (DetectedItem) -> Unit
) {
    var name by remember { mutableStateOf(item.name) }
    var quantity by remember { mutableStateOf(item.quantity) }
    var storage by remember { mutableStateOf(item.storage) }
    var shelfLifeString by remember { mutableStateOf(item.estimatedShelfLifeDays.toString()) }
    var priceString by remember { mutableStateOf(if ((item.price ?: 0.0) > 0.0) item.price.toString() else "") }

    Dialog(onDismissRequest = onDismiss) {
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
                HorizontalDivider(color = Color.LightGray.copy(alpha = 0.3f))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Ürün Adı", fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = TextStyle(fontSize = 13.sp, color = DarkCharcoal),
                    shape = RoundedCornerShape(10.dp),
                    colors = smartKitchenFieldColors()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = quantity,
                        onValueChange = { quantity = it },
                        label = { Text("Miktar", fontSize = 11.sp) },
                        modifier = Modifier.weight(1f),
                        textStyle = TextStyle(fontSize = 13.sp, color = DarkCharcoal),
                        shape = RoundedCornerShape(10.dp),
                        colors = smartKitchenFieldColors()
                    )

                    OutlinedTextField(
                        value = priceString,
                        onValueChange = { priceString = it },
                        label = { Text("Fiyat (₺)", fontSize = 11.sp) },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        textStyle = TextStyle(fontSize = 13.sp, color = DarkCharcoal),
                        shape = RoundedCornerShape(10.dp),
                        colors = smartKitchenFieldColors()
                    )
                }

                OutlinedTextField(
                    value = shelfLifeString,
                    onValueChange = { shelfLifeString = it },
                    label = { Text("Raf Ömrü (Gün)", fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    textStyle = TextStyle(fontSize = 13.sp, color = DarkCharcoal),
                    shape = RoundedCornerShape(10.dp),
                    colors = smartKitchenFieldColors()
                )

                Text("Saklama Alanı:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = DarkCharcoal)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    kitchenStorages.forEach { s ->
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
                        Text("Vazgeç", fontSize = 12.sp, color = DarkCharcoal)
                    }

                    Button(
                        onClick = {
                            val days = shelfLifeString.toIntOrNull()?.coerceAtLeast(1) ?: 7
                            val price = priceString.replace(",", ".").toDoubleOrNull() ?: 0.0
                            onSave(
                                DetectedItem(
                                    name = name.trim().ifBlank { item.name },
                                    quantity = quantity.trim().ifBlank { item.quantity },
                                    estimatedShelfLifeDays = days,
                                    storage = storage,
                                    price = price
                                )
                            )
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

private data class NotificationQuad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

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
                        onClick = onMarkAllAsRead,
                        colors = ButtonDefaults.textButtonColors(contentColor = KitchenSage)
                    ) {
                        Icon(Icons.Default.DoneAll, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Tümünü Okundu", fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
                    Text("Tamam", fontWeight = FontWeight.Bold, fontSize = 11.sp)
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
                Text(
                    text = "Gıda israfını önlemek için son tüketim tarihi yaklaşan ürünleriniz burada listelenir. 7 gün, 3 gün, 1 gün kala ve son gün sistem bildirimi gönderilir.",
                    fontSize = 11.sp,
                    color = Color.Gray,
                    lineHeight = 16.sp
                )

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val context = LocalContext.current
                    var hasPermission by remember {
                        mutableStateOf(
                            ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.POST_NOTIFICATIONS
                            ) == PackageManager.PERMISSION_GRANTED
                        )
                    }

                    val permissionLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.RequestPermission()
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
                                    text = "Anlık Bildirim İzni 🔔",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = DarkCharcoal
                                )
                                Text(
                                    text = "SKT uyarıları ve pişirme zamanlayıcısı için bildirim izni verebilirsiniz.",
                                    fontSize = 10.sp,
                                    color = Color.Gray,
                                    textAlign = TextAlign.Center
                                )
                                Button(
                                    onClick = { permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
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
                                    text = "Sistem bildirimleri etkin durumda.",
                                    fontSize = 10.sp,
                                    color = SafeGreen,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = Color.LightGray.copy(alpha = 0.3f))

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
                        items(warnings, key = { it.itemId }) { warning ->
                            val isRead = warning.itemId in readWarningIds
                            val (bgColor, borderColor, textColor, icon) = when (warning.level) {
                                WarningLevel.EXPIRED -> NotificationQuad(Color(0xFFFBEBEB), Color(0xFFF3C0C0), Color(0xFFC0392B), Icons.Default.ErrorOutline)
                                WarningLevel.CRITICAL -> NotificationQuad(Color(0xFFFEF5E7), Color(0xFFFDE3A7), Color(0xFFD35400), Icons.Default.PriorityHigh)
                                WarningLevel.WARNING -> NotificationQuad(Color(0xFFFEF9E7), Color(0xFFF9E79F), Color(0xFFB7950B), Icons.Default.Warning)
                                WarningLevel.INFO -> NotificationQuad(SoftSageBg, KitchenSage.copy(alpha = 0.2f), KitchenSage, Icons.Default.Info)
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
