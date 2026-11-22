package com.example.ui

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.InventoryItem

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
        activeItems
            .filter { it.isUrgent() }
            .sortedWith(compareBy<InventoryItem> { it.getRemainingDays() }.thenBy { it.remainingPercent })
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
                    "🚨 Acil Tüketilecekler",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = DarkCharcoal
                )
                Text(
                    "SKT'si geçmiş, 3 günden az kalan veya %25'in altına inen ürünler",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
            if (expiringOrLowItems.isNotEmpty()) {
                Spacer(modifier = Modifier.width(8.dp))
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
                        "${expiringOrLowItems.size} Ürün Öncelikli Değerlendirilmeli",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = DarkCharcoal
                    )
                    Text(
                        "Bu ürünleri tüketerek bütçenizi koruyabilir ve Yeşil Mutfak Puanı kazanabilirsiniz.",
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
                        "Tebrikler! Acil tüketilecek ürün yok.",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = DarkCharcoal
                    )
                    Text(
                        "Tüm gıdalarınız taze ve ideal stok miktarında.",
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
