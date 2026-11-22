package com.example.ui

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.api.ApiKeySource
import com.example.data.WasteActionLog
import com.example.domain.EcoMetricsCalculator
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import java.util.Locale

data class BadgeInfo(val name: String, val emoji: String, val desc: String, val isUnlocked: Boolean)

@Composable
fun SustainabilityTab(
    totalScore: Int,
    totalCo2Saved: Double,
    actionLogs: List<WasteActionLog>,
    onClearLogs: () -> Unit,
    onOpenMonthlyReport: () -> Unit,
    viewModel: InventoryViewModel
) {
    val context = LocalContext.current
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showClearLogsDialog by remember { mutableStateOf(false) }

    val totalSavedCount = remember(actionLogs) { actionLogs.count { it.actionType == "CONSUMED" } }
    val totalWastedCount = remember(actionLogs) { actionLogs.count { it.actionType == "WASTED" } }
    val totalWaterSavedLiters = remember(actionLogs) {
        actionLogs.sumOf { EcoMetricsCalculator.waterSavedForLog(it) }
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            containerColor = Color.White,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Logout, contentDescription = null, tint = AlertRed)
                    Text("Oturumu Kapat", fontWeight = FontWeight.Bold, color = DarkCharcoal)
                }
            },
            text = {
                Text(
                    "Hesabınızdan çıkış yapmak istediğinize emin misiniz? Profil adınız ve avatarınız sıfırlanacaktır.",
                    fontSize = 12.sp,
                    color = DarkCharcoal
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.logoutOrResetProfile()
                        showLogoutDialog = false
                        Toast.makeText(context, "Hesabınızdan çıkış yapıldı.", Toast.LENGTH_SHORT).show()
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
            }
        )
    }

    if (showClearLogsDialog) {
        AlertDialog(
            onDismissRequest = { showClearLogsDialog = false },
            containerColor = Color.White,
            title = { Text("Geçmişi Sıfırla", fontWeight = FontWeight.Bold, color = DarkCharcoal) },
            text = { Text("Tüm tüketim ve israf puanı geçmişiniz silinecektir. Onaylıyor musunuz?", fontSize = 12.sp, color = DarkCharcoal) },
            confirmButton = {
                Button(
                    onClick = {
                        onClearLogs()
                        showClearLogsDialog = false
                        Toast.makeText(context, "Geçmiş kayıtlar temizlendi.", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AlertRed)
                ) {
                    Text("Temizle", fontWeight = FontWeight.Bold, color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearLogsDialog = false }) {
                    Text("Vazgeç", color = DarkCharcoal)
                }
            }
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
                    "Sürdürülebilirlik & Ayarlar",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = DarkCharcoal
                )
                Text(
                    "Karbon ve su tasarrufunuz, aylık mutfak karneniz ve Gemini API yapılandırmanız.",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
        }

        // Monthly Report CTA Card (Wires MonthlyReportDialog!)
        item {
            Card(
                onClick = onOpenMonthlyReport,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("open_monthly_report_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = KitchenSage)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "📊 Aylık Mutfak Karnesi",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp,
                            color = Color.White
                        )
                        Text(
                            "Kurtarılan bütçe, CO₂, su tasarrufu ve paylaşılabilir özet rapor",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }
                    Surface(
                        color = HighlightGreen,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.Assessment, contentDescription = null, tint = HighlightDark, modifier = Modifier.size(16.dp))
                            Text("Karnemi Gör", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = HighlightDark)
                        }
                    }
                }
            }
        }

        // Interactive Gemini API Key & Connection Checker Card
        item {
            GeminiApiConfigurationCard(viewModel = viewModel)
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
                            Icon(Icons.Default.Public, contentDescription = null, tint = KitchenSage, modifier = Modifier.size(20.dp))
                            Text("Küresel Sıfır Atık Etkisi", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = DarkCharcoal)
                        }
                        Surface(shape = RoundedCornerShape(6.dp), color = Color.White) {
                            Text(
                                text = "TOPLULUK 🌱",
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
                            Text("Engellenen CO₂", fontSize = 10.sp, color = Color.Gray)
                            Text(
                                String.format(Locale.US, "%,.1f kg", globalCo2Saved),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = KitchenSage
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                String.format(Locale.US, "Katkınız: %.1f kg", totalCo2Saved),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = DarkCharcoal
                            )
                        }

                        Box(
                            modifier = Modifier
                                .height(55.dp)
                                .width(1.dp)
                                .background(AccentGreenBorder.copy(alpha = 0.5f))
                        )

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 12.dp)
                        ) {
                            Text("Kazanılan Sanal Su", fontSize = 10.sp, color = Color.Gray)
                            Text(
                                String.format(Locale.US, "%,.0f L", globalWaterSaved),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF006699)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                String.format(Locale.US, "Katkınız: %.0f L", totalWaterSavedLiters),
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
            var showAvatarPicker by remember { mutableStateOf(false) }
            val avatarList = listOf("🥦", "🥑", "🍎", "🍋", "👨‍🍳", "🥬", "🥕", "🧀")

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("user_profile_card"),
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
                        Surface(
                            onClick = { showAvatarPicker = !showAvatarPicker },
                            modifier = Modifier.size(52.dp),
                            shape = CircleShape,
                            color = KitchenSage.copy(alpha = 0.15f),
                            border = BorderStroke(2.dp, KitchenSage)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(text = avatar, fontSize = 26.sp)
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
                                        textStyle = TextStyle(fontSize = 13.sp, color = DarkCharcoal),
                                        shape = RoundedCornerShape(8.dp),
                                        singleLine = true,
                                        colors = smartKitchenFieldColors()
                                    )
                                    IconButton(
                                        onClick = {
                                            val err = viewModel.validateUsername(nickInput, currentUsername = nickname)
                                            if (err != null) {
                                                Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                                            } else if (viewModel.updateNickname(nickInput, avatar)) {
                                                Toast.makeText(context, "Kullanıcı adı güncellendi!", Toast.LENGTH_SHORT).show()
                                                isEditingNick = false
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
                                        text = nickname.ifBlank { "Mutfak Şefi" },
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
                                Surface(color = KitchenClay.copy(alpha = 0.12f), shape = RoundedCornerShape(6.dp)) {
                                    Text(
                                        text = userTitle,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = KitchenClay,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                                Text("• Avatar için dokunun", fontSize = 10.sp, color = Color.Gray)
                            }
                        }
                    }

                    if (showAvatarPicker) {
                        Spacer(modifier = Modifier.height(10.dp))
                        HorizontalDivider(color = Color.LightGray.copy(alpha = 0.2f))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Profil Avatarı Değiştir:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = KitchenSage)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            avatarList.forEach { itemAvatar ->
                                val isSelected = itemAvatar == avatar
                                Surface(
                                    onClick = {
                                        viewModel.updateAvatar(itemAvatar)
                                        showAvatarPicker = false
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

        // Green points & CO2 Summary
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .testTag("score_summary_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = KitchenSage)
                ) {
                    Column(modifier = Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Eco, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Yeşil Mutfak Puanı", fontSize = 11.sp, color = Color.White.copy(alpha = 0.85f))
                        Text("$totalScore XP", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }

                Card(
                    modifier = Modifier
                        .weight(1f)
                        .testTag("co2_summary_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, KitchenSage.copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.CloudQueue, contentDescription = null, tint = KitchenSage, modifier = Modifier.size(28.dp))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Önlenen CO₂", fontSize = 11.sp, color = Color.Gray)
                        Text(String.format(Locale.US, "%.1f kg", totalCo2Saved), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = KitchenSage)
                    }
                }
            }
        }

        // Stats Card
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
                        Text("$totalSavedCount İşlem", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = SafeGreen)
                    }
                    HorizontalDivider(modifier = Modifier.height(30.dp).width(1.dp), color = Color.LightGray)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("İsraf Edilen", fontSize = 11.sp, color = Color.Gray)
                        Text("$totalWastedCount Ürün", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = AlertRed)
                    }
                }
            }
        }

        // Achievement Badges
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
                        modifier = Modifier.padding(bottom = 10.dp)
                    ) {
                        Icon(Icons.Default.Stars, contentDescription = null, tint = KitchenClay, modifier = Modifier.size(18.dp))
                        Text("Atıksız Mutfak Başarı Rozetlerim", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = DarkCharcoal)
                    }

                    val badges = listOf(
                        BadgeInfo("Atıksız Başlangıç", "🍳", "İlk gıdanı israf olmaktan kurtardın!", totalSavedCount >= 1),
                        BadgeInfo("Karbon Avcısı", "🍃", "5 kg CO₂ salınımını önledin!", totalCo2Saved >= 5.0),
                        BadgeInfo("Mutfak Koruyucusu", "🛡️", "300 yeşil puan barajını aştın!", totalScore >= 300),
                        BadgeInfo("Mavi Gezegen", "💧", "1000 litre sanal su tasarrufu!", totalWaterSavedLiters >= 1000.0),
                        BadgeInfo("Gıda Kahramanı", "👑", "10 gıda veya tarif başarıyla tüketildi!", totalSavedCount >= 10)
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                                        .background(if (isUnlocked) HighlightGreen else Color.LightGray.copy(alpha = 0.3f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(emoji, fontSize = 18.sp)
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(name, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (isUnlocked) DarkCharcoal else Color.Gray)
                                    Text(desc, fontSize = 10.sp, color = if (isUnlocked) Color.Gray else Color.Gray.copy(alpha = 0.7f))
                                }
                                Icon(
                                    imageVector = if (isUnlocked) Icons.Default.CheckCircle else Icons.Default.Lock,
                                    contentDescription = if (isUnlocked) "Kazanıldı" else "Kilitli",
                                    tint = if (isUnlocked) SafeGreen else Color.LightGray,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Spending & Budget Dashboard Card
        item {
            val activeItems by viewModel.activeItems.collectAsStateWithLifecycle()
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
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Mutfak Harcamalarım & Tasarruf Analizi 💰", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = DarkCharcoal)
                    HorizontalDivider(color = Color.LightGray.copy(alpha = 0.2f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Takip Edilen Toplam Değer", fontSize = 12.sp, color = Color.Gray)
                        Text("₺${String.format(Locale.US, "%.1f", totalSpent)}", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = DarkCharcoal)
                    }

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

                            if (activeRatio > 0f) Box(modifier = Modifier.weight(activeRatio).fillMaxHeight().background(KitchenSage))
                            if (savedRatio > 0f) Box(modifier = Modifier.weight(savedRatio).fillMaxHeight().background(SafeGreen))
                            if (wastedRatio > 0f) Box(modifier = Modifier.weight(wastedRatio).fillMaxHeight().background(AlertRed))
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Mevcut Stok", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                            Text("₺${String.format(Locale.US, "%.1f", activeStockValue)}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = DarkCharcoal)
                        }
                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Kurtarılan", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                            Text("₺${String.format(Locale.US, "%.1f", savedValue)}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SafeGreen)
                        }
                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("İsraf Kaybı", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                            Text("₺${String.format(Locale.US, "%.1f", wastedValue)}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AlertRed)
                        }
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
                        Icon(Icons.Default.ChatBubbleOutline, contentDescription = null, tint = KitchenSage, modifier = Modifier.size(16.dp))
                        Text("Canlı Tasarruf Günlüğü", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = DarkCharcoal)
                    }

                    if (globalFeedEvents.isEmpty()) {
                        Text(
                            text = "Henüz aktivite kaydedilmedi. Dolaptan bir ürün tükettiğinizde veya tarif pişirdiğinizde burada görünecektir.",
                            fontSize = 11.sp,
                            color = Color.Gray,
                            modifier = Modifier.padding(vertical = 8.dp)
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
                                    Icon(Icons.Default.Eco, contentDescription = null, tint = SafeGreen, modifier = Modifier.size(14.dp).padding(top = 2.dp))
                                    Text(text = event, fontSize = 11.sp, color = DarkCharcoal, lineHeight = 15.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Account & History Management
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("account_management_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.ManageAccounts, contentDescription = null, tint = DarkCharcoal, modifier = Modifier.size(18.dp))
                        Text("Hesap & Geçmiş Yönetimi", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = DarkCharcoal)
                    }

                    if (actionLogs.isNotEmpty()) {
                        OutlinedButton(
                            onClick = { showClearLogsDialog = true },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = KitchenClay),
                            border = BorderStroke(1.dp, KitchenClay.copy(alpha = 0.6f)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Puan & Eylem Geçmişini Sıfırla", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }

                    OutlinedButton(
                        onClick = { showLogoutDialog = true },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AlertRed),
                        border = BorderStroke(1.dp, AlertRed.copy(alpha = 0.6f)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("profile_logout_bottom_btn")
                    ) {
                        Icon(Icons.Default.Logout, contentDescription = "Çıkış", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Hesaptan Güvenli Çıkış Yap", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }

        // Lifecycle-safe AdMob Banner
        item {
            GoogleAdBanner()
        }
    }
}

@Composable
fun GeminiApiConfigurationCard(viewModel: InventoryViewModel) {
    val context = LocalContext.current
    val apiKeyStatus by viewModel.apiKeyStatus.collectAsStateWithLifecycle()
    val isTesting by viewModel.isTestingApi.collectAsStateWithLifecycle()
    val testResult by viewModel.apiTestResult.collectAsStateWithLifecycle()

    var apiKeyInput by remember { mutableStateOf("") }
    var showKeyText by remember { mutableStateOf(false) }

    val statusColor = when {
        apiKeyStatus.isConfigured && apiKeyStatus.isValidFormat -> SafeGreen
        apiKeyStatus.isConfigured -> WarningYellow
        else -> AlertRed
    }
    val statusBg = statusColor.copy(alpha = 0.08f)
    val statusLabel = when {
        !apiKeyStatus.isConfigured -> "Anahtar Eksik"
        !apiKeyStatus.isValidFormat -> "Format Kontrolü Gerekli"
        apiKeyStatus.source == ApiKeySource.IN_APP -> "Aktif (Uygulama İçi)"
        else -> "Aktif (.env)"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("api_status_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = statusBg),
        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.4f))
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.Key, contentDescription = null, tint = statusColor, modifier = Modifier.size(18.dp))
                    Text("Gemini API Anahtarı", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = DarkCharcoal)
                }

                Surface(
                    color = statusColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, statusColor)
                ) {
                    Text(
                        statusLabel,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = statusColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            if (apiKeyStatus.isConfigured) {
                Text(
                    "Aktif anahtar: ${apiKeyStatus.maskedKey} (${if (apiKeyStatus.source == ApiKeySource.IN_APP) "Cihazda kayıtlı" else ".env dosyasından"})",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = DarkCharcoal
                )
            } else {
                Text(
                    "Görsel fiş okuma, dolap analizi ve kişiselleştirilmiş Gemini tarifleri için AIza... ile başlayan Gemini API anahtarınızı aşağıdan ekleyin veya proje kökündeki .env dosyasına GEMINI_API_KEY=... şeklinde tanımlayın.",
                    fontSize = 11.sp,
                    color = DarkCharcoal
                )
            }

            OutlinedTextField(
                value = apiKeyInput,
                onValueChange = { apiKeyInput = it },
                label = { Text("Yeni Gemini API Anahtarı (AIza...)", fontSize = 11.sp) },
                placeholder = { Text("AIzaSy...", fontSize = 11.sp) },
                singleLine = true,
                visualTransformation = if (showKeyText) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showKeyText = !showKeyText }) {
                        Icon(
                            imageVector = if (showKeyText) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = "Göster/Gizle",
                            tint = Color.Gray
                        )
                    }
                },
                textStyle = TextStyle(fontSize = 12.sp, color = DarkCharcoal),
                colors = smartKitchenFieldColors(),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("api_key_input")
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        val trimmed = apiKeyInput.trim()
                        if (trimmed.isEmpty()) {
                            Toast.makeText(context, "Lütfen bir API anahtarı yapıştırın.", Toast.LENGTH_SHORT).show()
                        } else {
                            viewModel.saveCustomApiKey(trimmed)
                            apiKeyInput = ""
                            Toast.makeText(context, "API anahtarı cihaza kaydedildi!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = KitchenSage),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                ) {
                    Text("Kaydet", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = {
                        viewModel.testApiKeyConnection(apiKeyInput.trim().ifEmpty { null })
                    },
                    enabled = !isTesting,
                    border = BorderStroke(1.dp, KitchenSage),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1.2f),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                ) {
                    if (isTesting) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = KitchenSage)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Test...", fontSize = 11.sp, color = KitchenSage, fontWeight = FontWeight.Bold)
                    } else {
                        Icon(Icons.Default.NetworkCheck, contentDescription = null, tint = KitchenSage, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Bağlantıyı Test Et", fontSize = 11.sp, color = KitchenSage, fontWeight = FontWeight.Bold)
                    }
                }

                if (apiKeyStatus.source == ApiKeySource.IN_APP) {
                    OutlinedButton(
                        onClick = {
                            viewModel.clearCustomApiKey()
                            Toast.makeText(context, "Cihazdaki anahtar temizlendi.", Toast.LENGTH_SHORT).show()
                        },
                        border = BorderStroke(1.dp, AlertRed.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Text("Sil", fontSize = 11.sp, color = AlertRed, fontWeight = FontWeight.Bold)
                    }
                }
            }

            testResult?.let { (isOk, msg) ->
                Surface(
                    color = if (isOk) SafeGreen.copy(alpha = 0.12f) else AlertRed.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, if (isOk) SafeGreen.copy(alpha = 0.4f) else AlertRed.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = msg,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isOk) SafeGreen else AlertRed,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun GoogleAdBanner(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var isAdLoaded by remember { mutableStateOf(false) }

    val adView = remember(context) {
        AdView(context).apply {
            setAdSize(AdSize.BANNER)
            adUnitId = "ca-app-pub-7805317320781330/1926664021"
            adListener = object : AdListener() {
                override fun onAdLoaded() {
                    isAdLoaded = true
                }
            }
            try {
                loadAd(AdRequest.Builder().build())
            } catch (_: Exception) {
                // Ignore AdMob load issues on test/offline environments
            }
        }
    }

    DisposableEffect(adView) {
        onDispose {
            try {
                adView.destroy()
            } catch (_: Exception) {
            }
        }
    }

    if (isAdLoaded) {
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = Color.White,
            border = BorderStroke(1.dp, BorderColor)
        ) {
            AndroidView(
                factory = { adView },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp)
            )
        }
    }
}
