package com.example.ui

import android.view.WindowManager
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AlarmOn
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.OutdoorGrill
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.util.Locale

@Composable
fun InteractiveCookingDialog(
    viewModel: InventoryViewModel,
    onDismiss: () -> Unit
) {
    val recipe by viewModel.activeCookingRecipe.collectAsStateWithLifecycle()
    val steps by viewModel.cookingSteps.collectAsStateWithLifecycle()
    val currentStepIndex by viewModel.currentCookingStepIndex.collectAsStateWithLifecycle()
    val timerSeconds by viewModel.cookingTimerSecondsLeft.collectAsStateWithLifecycle()
    val isTimerRunning by viewModel.isCookingTimerRunning.collectAsStateWithLifecycle()
    val isTimerFinished by viewModel.isCookingTimerFinished.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val currentRecipe = recipe ?: return
    val totalSteps = steps.size.coerceAtLeast(1)
    val progress = (currentStepIndex + 1).toFloat() / totalSteps.toFloat()
    val animatedProgress by animateFloatAsState(targetValue = progress, label = "cooking_progress")

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        // Keep screen awake while user cooks hands-free
        val dialogView = LocalView.current
        DisposableEffect(dialogView) {
            val window = (dialogView.parent as? DialogWindowProvider)?.window
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            onDispose {
                window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }

        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .testTag("interactive_cooking_dialog"),
            shape = RoundedCornerShape(24.dp),
            color = Color.White,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Header
                Column {
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
                            Surface(
                                color = KitchenSage.copy(alpha = 0.15f),
                                shape = CircleShape,
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.OutdoorGrill,
                                    contentDescription = null,
                                    tint = KitchenSage,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "👩‍🍳 Pişirme Modu",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = KitchenSage
                                )
                                Text(
                                    text = currentRecipe.title,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = DarkCharcoal,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Kapat", tint = Color.Gray)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Adım ${currentStepIndex + 1} / $totalSteps",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = KitchenSage
                        )
                        Text(
                            text = "%${(progress * 100).toInt()} Tamamlandı",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.Gray
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    LinearProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = KitchenSage,
                        trackColor = SoftSageBg
                    )
                }

                // Step Card + Timer
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 12.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = SoftSageBg.copy(alpha = 0.5f)),
                        border = BorderStroke(1.dp, KitchenSage.copy(alpha = 0.2f))
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                color = KitchenSage,
                                shape = CircleShape,
                                modifier = Modifier.size(48.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "${currentStepIndex + 1}",
                                        color = Color.White,
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            }

                            val stepText = steps.getOrNull(currentStepIndex) ?: "Adım detayı yükleniyor..."
                            Text(
                                text = stepText,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                color = DarkCharcoal,
                                textAlign = TextAlign.Center,
                                lineHeight = 24.sp
                            )
                        }
                    }

                    if (isTimerFinished) {
                        Surface(
                            onClick = { viewModel.dismissCookingTimerFinished() },
                            color = AlertRed.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, AlertRed),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(Icons.Default.AlarmOn, contentDescription = null, tint = AlertRed, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "⏰ Süre Doldu! (Kapatmak için dokunun)",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 13.sp,
                                    color = AlertRed
                                )
                            }
                        }
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = AccentGreenBg),
                        border = BorderStroke(1.dp, AccentGreenBorder)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Default.Timer, contentDescription = null, tint = KitchenSage, modifier = Modifier.size(18.dp))
                                Text("Pişirme & Dinlendirme Sayacı", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = KitchenSage)
                            }

                            val minutes = timerSeconds / 60
                            val seconds = timerSeconds % 60
                            val formattedTime = String.format(Locale.US, "%02d:%02d", minutes, seconds)

                            Text(
                                text = formattedTime,
                                fontSize = 38.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (timerSeconds in 1..10) AlertRed else DarkCharcoal
                            )

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                listOf(1, 3, 5, 10).forEach { addMin ->
                                    OutlinedButton(
                                        onClick = { viewModel.addCookingTimerSeconds(addMin * 60) },
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                        modifier = Modifier.height(32.dp),
                                        border = BorderStroke(1.dp, KitchenSage.copy(alpha = 0.4f))
                                    ) {
                                        Text("+$addMin dk", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = KitchenSage)
                                    }
                                }
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (!isTimerRunning) {
                                    Button(
                                        onClick = {
                                            if (timerSeconds <= 0) {
                                                viewModel.setCookingTimer(5 * 60)
                                            }
                                            viewModel.startCookingTimer()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = KitchenSage),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Başlat", fontWeight = FontWeight.Bold)
                                    }
                                } else {
                                    Button(
                                        onClick = { viewModel.pauseCookingTimer() },
                                        colors = ButtonDefaults.buttonColors(containerColor = KitchenClay),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(Icons.Default.Pause, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Duraklat", fontWeight = FontWeight.Bold)
                                    }
                                }

                                OutlinedButton(
                                    onClick = { viewModel.resetCookingTimer() },
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, Color.LightGray)
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.Gray)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Sıfırla", color = Color.Gray, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // Bottom Navigation
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { viewModel.previousCookingStep() },
                        enabled = currentStepIndex > 0,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, if (currentStepIndex > 0) KitchenSage else Color.LightGray)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Önceki", fontWeight = FontWeight.Bold, color = if (currentStepIndex > 0) KitchenSage else Color.Gray)
                    }

                    if (currentStepIndex < totalSteps - 1) {
                        Button(
                            onClick = { viewModel.nextCookingStep() },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = KitchenSage)
                        ) {
                            Text("Sonraki Adım", fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                    } else {
                        Button(
                            onClick = {
                                viewModel.consumeRecipe(
                                    currentRecipe.title,
                                    currentRecipe.calories,
                                    currentRecipe.protein,
                                    currentRecipe.carbs,
                                    currentRecipe.fat
                                )
                                Toast.makeText(
                                    context,
                                    "Tebrikler! ${currentRecipe.title} başarıyla pişirildi ve günlüğe işlendi! 🎉",
                                    Toast.LENGTH_LONG
                                ).show()
                                onDismiss()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = SafeGreen)
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Bitir! 🎉", fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }
            }
        }
    }
}
