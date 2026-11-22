package com.example.ui

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.util.Locale

@Composable
fun MonthlyReportDialog(
    viewModel: InventoryViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val metrics = remember { viewModel.getDetailedEcoFinancialMetrics() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .testTag("monthly_report_dialog"),
            shape = RoundedCornerShape(24.dp),
            color = Color.White
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            color = SafeGreen.copy(alpha = 0.15f),
                            shape = CircleShape,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Assessment,
                                contentDescription = null,
                                tint = SafeGreen,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                        Column {
                            Text("📊 Aylık Mutfak Karnesi", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = DarkCharcoal)
                            Text("Finansal & Ekolojik Tasarruf Özeti", fontSize = 11.sp, color = Color.Gray)
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Kapat", tint = Color.Gray)
                    }
                }

                // Scrollable Content / Report Card
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 12.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Eco Score Hero
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = AccentGreenBg),
                        border = BorderStroke(1.dp, AccentGreenBorder)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                "🏆 Sürdürülebilirlik Başarı Skoru",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = KitchenSage
                            )
                            Text(
                                "%${metrics.sustainabilityScore}",
                                fontSize = 42.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = SafeGreen
                            )
                            Text(
                                when {
                                    metrics.sustainabilityScore >= 80 -> "🌟 Mükemmel! Sıfır Atık Şampiyonu!"
                                    metrics.sustainabilityScore >= 50 -> "🌱 Harika Gidiyorsunuz! İsraf kontrol altında."
                                    else -> "⚠️ Dikkat! Dolaptaki SKT uyarılarına öncelik verin."
                                },
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = DarkCharcoal,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    // 2x2 Metric Grid
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Kurtarılan Bütçe
                        Card(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = SoftSageBg),
                            border = BorderStroke(1.dp, KitchenSage.copy(alpha = 0.2f))
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(Icons.Default.Savings, contentDescription = null, tint = SafeGreen, modifier = Modifier.size(16.dp))
                                    Text("Kurtarılan Bütçe", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                                }
                                Text(
                                    "₺${String.format(Locale.US, "%.1f", metrics.totalMoneySavedTl)}",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = SafeGreen
                                )
                                Text("İsraftan kurtarılan gıda", fontSize = 9.sp, color = Color.Gray)
                            }
                        }

                        // İsraf Kaybı
                        Card(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = KitchenClay.copy(alpha = 0.08f)),
                            border = BorderStroke(1.dp, KitchenClay.copy(alpha = 0.2f))
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(Icons.Default.TrendingDown, contentDescription = null, tint = KitchenClay, modifier = Modifier.size(16.dp))
                                    Text("İsraf Kaybı", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                                }
                                Text(
                                    "₺${String.format(Locale.US, "%.1f", metrics.totalMoneyLostTl)}",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = KitchenClay
                                )
                                Text("Bozulan gıda maliyeti", fontSize = 9.sp, color = Color.Gray)
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // CO2 Salınımı
                        Card(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = SoftSageBg),
                            border = BorderStroke(1.dp, KitchenSage.copy(alpha = 0.2f))
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(Icons.Default.Eco, contentDescription = null, tint = KitchenSage, modifier = Modifier.size(16.dp))
                                    Text("Önlenen CO₂", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                                }
                                Text(
                                    "${String.format(Locale.US, "%.1f", metrics.totalCo2SavedKg)} kg",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = KitchenSage
                                )
                                Text("Engellenen sera gazı", fontSize = 9.sp, color = Color.Gray)
                            }
                        }

                        // Su Ayak İzi
                        Card(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = SoftSageBg),
                            border = BorderStroke(1.dp, KitchenSage.copy(alpha = 0.2f))
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(Icons.Default.WaterDrop, contentDescription = null, tint = Color(0xFF1976D2), modifier = Modifier.size(16.dp))
                                    Text("Kurtarılan Su", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                                }
                                Text(
                                    "${String.format(Locale.US, "%.0f", metrics.totalWaterSavedLiters)} L",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF1976D2)
                                )
                                Text("Sanal su ayak izi", fontSize = 9.sp, color = Color.Gray)
                            }
                        }
                    }

                    // Key Highlights
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.4f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("📌 Ayın Öne Çıkan Başarıları", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = DarkCharcoal)
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("🍳", fontSize = 16.sp)
                                Text("Toplam ${metrics.totalMealsCooked} adet sıfır atık yemek pişirildi", fontSize = 11.sp, color = DarkCharcoal)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("🥦", fontSize = 16.sp)
                                Text("${metrics.totalItemsRescued} gıda ürünü son kullanma öncesi kurtarıldı", fontSize = 11.sp, color = DarkCharcoal)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("🌳", fontSize = 16.sp)
                                Text("Kurtarılan CO2 yaklaşık ${(metrics.totalCo2SavedKg / 2.5).toInt().coerceAtLeast(1)} ağacın yıllık temizliğine eşdeğer", fontSize = 11.sp, color = DarkCharcoal)
                            }
                        }
                    }
                }

                // Share Button & Close
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f).height(46.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color.LightGray)
                    ) {
                        Text("Kapat", fontWeight = FontWeight.Bold, color = Color.Gray)
                    }

                    Button(
                        onClick = {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "Smart Kitchen AI - Aylık Mutfak Karnem")
                                putExtra(Intent.EXTRA_TEXT, metrics.monthlySummaryText)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Karneni Paylaş"))
                        },
                        modifier = Modifier.weight(1.5f).height(46.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = KitchenSage)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Karnemi Paylaş", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
