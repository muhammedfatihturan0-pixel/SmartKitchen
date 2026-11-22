package com.example.ui

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Kitchen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.ShoppingItem
import java.util.Locale

@Composable
fun ShoppingSectionTab(
    viewModel: InventoryViewModel
) {
    val context = LocalContext.current
    val shoppingItems by viewModel.shoppingItems.collectAsStateWithLifecycle()
    var newShoppingText by remember { mutableStateOf("") }
    var showClearAllDialog by remember { mutableStateOf(false) }

    val quickAddSuggestions = listOf("Süt", "Yumurta", "Yoğurt", "Peynir", "Ekmek", "Domates", "Patates", "Zeytinyağı")
    val checkedCount = remember(shoppingItems) { shoppingItems.count { it.isChecked } }
    val uncheckedCount = shoppingItems.size - checkedCount
    val estimatedTotal = remember(shoppingItems) { shoppingItems.sumOf { it.estimatedPrice } }

    if (showClearAllDialog) {
        AlertDialog(
            onDismissRequest = { showClearAllDialog = false },
            containerColor = Color.White,
            title = { Text("Alışveriş Listesini Temizle", fontWeight = FontWeight.Bold, color = DarkCharcoal) },
            text = { Text("Listedeki tüm ${shoppingItems.size} ürünü silmek istediğinize emin misiniz?", color = DarkCharcoal) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearAllShoppingItems()
                        showClearAllDialog = false
                        Toast.makeText(context, "Alışveriş listesi temizlendi.", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AlertRed)
                ) {
                    Text("Tümünü Sil", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllDialog = false }) {
                    Text("İptal", color = DarkCharcoal)
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "🛒 Alışveriş Listem",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = DarkCharcoal
                )
                Text(
                    "Alınanları işaretleyin, tek tıkla dolabınıza aktarın.",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
            if (shoppingItems.isNotEmpty()) {
                TextButton(
                    onClick = { showClearAllDialog = true },
                    colors = ButtonDefaults.textButtonColors(contentColor = AlertRed)
                ) {
                    Text("Temizle", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Summary & Batch Transfer Card
        if (shoppingItems.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = AccentGreenBg),
                border = BorderStroke(1.dp, AccentGreenBorder)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "$uncheckedCount alınacak • $checkedCount sepette",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = DarkCharcoal
                        )
                        if (estimatedTotal > 0.0) {
                            Text(
                                "Tahmini: ₺${String.format(Locale.US, "%.1f", estimatedTotal)}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = KitchenSage
                            )
                        }
                    }

                    if (checkedCount > 0) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    viewModel.transferCheckedShoppingItemsToInventory { count ->
                                        Toast.makeText(context, "$count ürün dolabınıza aktarıldı!", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = KitchenSage),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .weight(1.5f)
                                    .height(38.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Icon(Icons.Default.Kitchen, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Seçilenleri Dolaba Aktar ($checkedCount)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = { viewModel.clearCheckedShoppingItems() },
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, AlertRed.copy(alpha = 0.5f)),
                                modifier = Modifier.height(38.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp)
                            ) {
                                Icon(Icons.Default.DeleteSweep, contentDescription = "Seçilenleri Sil", tint = AlertRed, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
        }

        // Add Item Input Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = newShoppingText,
                onValueChange = { newShoppingText = it },
                placeholder = { Text("Örn: Süt, Domates, Yumurta...", fontSize = 12.sp) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("shopping_tab_input"),
                shape = RoundedCornerShape(12.dp),
                textStyle = TextStyle(fontSize = 13.sp, color = DarkCharcoal),
                colors = smartKitchenFieldColors(),
                singleLine = true
            )

            Button(
                onClick = {
                    val trimmed = newShoppingText.trim()
                    if (trimmed.isNotEmpty()) {
                        viewModel.addShoppingItem(name = trimmed) { addedNew ->
                            if (!addedNew) {
                                Toast.makeText(context, "$trimmed zaten listenizde var.", Toast.LENGTH_SHORT).show()
                            }
                        }
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
            items(quickAddSuggestions) { suggestion ->
                AssistChip(
                    onClick = {
                        viewModel.addShoppingItem(name = suggestion) { addedNew ->
                            if (!addedNew) {
                                Toast.makeText(context, "$suggestion zaten listede.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    label = { Text("+ $suggestion", fontSize = 11.sp, color = DarkCharcoal) },
                    shape = RoundedCornerShape(16.dp),
                    colors = AssistChipDefaults.assistChipColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, BorderColor)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (shoppingItems.isEmpty()) {
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
                        "Yukarıdan eksiklerinizi ekleyin; aldıklarınızı tek dokunuşla dolabınıza aktarın.",
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
                items(shoppingItems, key = { it.id }) { item ->
                    ShoppingListItemRow(
                        item = item,
                        onToggleChecked = { viewModel.toggleShoppingItemChecked(item) },
                        onMoveToInventory = {
                            viewModel.moveSingleShoppingItemToInventory(item)
                            Toast.makeText(context, "${item.name} dolaba eklendi!", Toast.LENGTH_SHORT).show()
                        },
                        onDelete = { viewModel.deleteShoppingItem(item) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ShoppingListItemRow(
    item: ShoppingItem,
    onToggleChecked: () -> Unit,
    onMoveToInventory: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggleChecked() }
            .testTag("shopping_item_${item.name}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (item.isChecked) SoftSageBg.copy(alpha = 0.6f) else Color.White
        ),
        border = BorderStroke(1.dp, if (item.isChecked) SafeGreen.copy(alpha = 0.35f) else BorderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Checkbox(
                    checked = item.isChecked,
                    onCheckedChange = { onToggleChecked() },
                    colors = CheckboxDefaults.colors(checkedColor = KitchenSage)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = if (item.isChecked) Color.Gray else DarkCharcoal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = TextStyle(
                            textDecoration = if (item.isChecked) TextDecoration.LineThrough else null
                        )
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(item.quantity, fontSize = 10.sp, color = Color.Gray)
                        Surface(
                            color = KitchenSage.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                item.category,
                                fontSize = 9.sp,
                                color = KitchenSage,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                        if (item.addedFromWaste) {
                            Surface(
                                color = KitchenClay.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    "İsraf Yenileme",
                                    fontSize = 9.sp,
                                    color = KitchenClay,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                )
                            }
                        }
                        if (item.estimatedPrice > 0.0) {
                            Text(
                                "₺${String.format(Locale.US, "%.1f", item.estimatedPrice)}",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = KitchenClay
                            )
                        }
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(
                    onClick = onMoveToInventory,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(Icons.Default.Kitchen, contentDescription = "Dolaba Ekle", tint = KitchenSage, modifier = Modifier.size(18.dp))
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Sil", tint = AlertRed, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}
