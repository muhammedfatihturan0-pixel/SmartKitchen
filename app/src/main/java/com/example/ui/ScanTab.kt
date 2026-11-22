package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Kitchen
import androidx.compose.material.icons.filled.OutdoorGrill
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Store
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.api.DetectedItem
import com.example.api.RecipeSuggestion
import com.example.util.BitmapUtils
import java.util.Locale

@Composable
fun ScanTab(
    scanUiState: ScanUiState,
    onScanClick: (String, Bitmap?, String) -> Unit,
    onAddToInventory: (List<DetectedItem>) -> Unit,
    onReset: () -> Unit,
    onOpenBarcodeScanner: () -> Unit,
    onStartCooking: (RecipeSuggestion) -> Unit,
    onNavigateToApiSettings: () -> Unit
) {
    val context = LocalContext.current
    var rawText by remember { mutableStateOf("") }
    var selectedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var activeScanMode by remember { mutableStateOf("RECEIPT") } // RECEIPT, FRIDGE, PANTRY, FREEZER
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    var editableDetectedItems by remember { mutableStateOf<List<DetectedItem>?>(null) }
    var editingIndex by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(scanUiState) {
        editableDetectedItems = if (scanUiState is ScanUiState.Success) {
            scanUiState.result.detectedItems
        } else {
            null
        }
    }

    fun modePrompt(mode: String): String = when (mode) {
        "PANTRY" -> "Lütfen bu kiler fotoğrafındaki kuru gıdaları ve raf ömürlerini analiz et."
        "FREEZER" -> "Lütfen bu dondurucu fotoğrafındaki dondurulmuş ürünleri ve raf ömürlerini analiz et."
        "FRIDGE" -> "Lütfen bu buzdolabı fotoğrafındaki gıdaları, raf ömürlerini ve tarif önerisini analiz et."
        else -> "Lütfen bu market fişi fotoğrafındaki gıda ürünlerini ve fiyatlarını oku."
    }

    // Gallery Picker
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val bitmap = BitmapUtils.decodeSampledSoftwareBitmap(context, uri, maxDimension = 1600)
            if (bitmap != null) {
                selectedBitmap = bitmap
                onScanClick(modePrompt(activeScanMode), bitmap, activeScanMode)
            } else {
                Toast.makeText(context, "Görsel okunamadı. Lütfen başka bir fotoğraf seçin.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // High-resolution Device Camera Launcher via FileProvider
    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = pendingCameraUri
        if (success && uri != null) {
            val bitmap = BitmapUtils.decodeSampledSoftwareBitmap(context, uri, maxDimension = 1600)
            if (bitmap != null) {
                selectedBitmap = bitmap
                Toast.makeText(context, "Fotoğraf çekildi, analiz ediliyor...", Toast.LENGTH_SHORT).show()
                onScanClick(modePrompt(activeScanMode), bitmap, activeScanMode)
            } else {
                Toast.makeText(context, "Çekilen görsel işlenemedi.", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "Kamera çekimi iptal edildi.", Toast.LENGTH_SHORT).show()
        }
    }

    fun launchHighResCamera() {
        try {
            val uri = BitmapUtils.createCameraImageUri(context)
            pendingCameraUri = uri
            takePictureLauncher.launch(uri)
        } catch (e: Exception) {
            Toast.makeText(context, "Kamera başlatılamadı: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            launchHighResCamera()
        } else {
            Toast.makeText(context, "Fiş ve mutfak taraması için kamera izni gereklidir.", Toast.LENGTH_LONG).show()
        }
    }

    fun triggerCameraScan() {
        val permissionCheck = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
        if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
            launchHighResCamera()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
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
                    "Akıllı Fiş, Mutfak & Barkod Tarayıcı",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = DarkCharcoal
                )
                Text(
                    "Market fişi veya dolap fotoğrafı çekin, yapay zeka ürünleri ve SKT'lerini otomatik çıkarsın.",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
        }

        // Quick Barcode Scanner Card
        item {
            Card(
                onClick = onOpenBarcodeScanner,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("scan_tab_barcode_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = AccentGreenBg),
                border = BorderStroke(1.dp, AccentGreenBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        color = KitchenSage,
                        shape = CircleShape,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            Icons.Default.QrCodeScanner,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.padding(9.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "📦 Barkod ile Hızlı Ürün Ekle",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = DarkCharcoal
                        )
                        Text(
                            "Paket barkodunu okutun veya barkod no girin (Open Food Facts destekli)",
                            fontSize = 11.sp,
                            color = Color.DarkGray
                        )
                    }
                }
            }
        }

        if (scanUiState is ScanUiState.Idle) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, BorderColor)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
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
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val modes = listOf(
                                Triple("RECEIPT", "Fiş", Icons.Default.Receipt),
                                Triple("FRIDGE", "Dolap", Icons.Default.Kitchen),
                                Triple("PANTRY", "Kiler", Icons.Default.Store),
                                Triple("FREEZER", "Dondurucu", Icons.Default.AcUnit)
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
                                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 2.dp),
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
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) Color.White else DarkCharcoal,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }

                        HorizontalDivider(color = Color.LightGray.copy(alpha = 0.3f))

                        Text(
                            text = when (activeScanMode) {
                                "PANTRY" -> "Kiler Fotoğrafı Yükleyin"
                                "FREEZER" -> "Dondurucu Fotoğrafı Yükleyin"
                                "FRIDGE" -> "Buzdolabı İçi Fotoğrafı Yükleyin"
                                else -> "Market Fişi Fotoğrafı Yükleyin"
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = DarkCharcoal
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = { triggerCameraScan() },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(86.dp)
                                    .testTag("camera_shutter_launcher"),
                                colors = ButtonDefaults.buttonColors(containerColor = AccentGreenBg),
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, AccentGreenBorder)
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Default.CameraAlt, contentDescription = "Kamera", tint = KitchenSage, modifier = Modifier.size(28.dp))
                                    Text("Kamera ile Çek", color = KitchenSage, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            Button(
                                onClick = { galleryLauncher.launch("image/*") },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(86.dp)
                                    .testTag("gallery_picker_launcher"),
                                colors = ButtonDefaults.buttonColors(containerColor = SoftSageBg),
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.5f))
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Default.PhotoLibrary, contentDescription = "Galeri", tint = DarkCharcoal, modifier = Modifier.size(28.dp))
                                    Text("Galeriden Seç", color = DarkCharcoal, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            // Text Fallback Input
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.4f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            "Alternatif: Metin veya Liste Yapıştır",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = DarkCharcoal
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        OutlinedTextField(
                            value = rawText,
                            onValueChange = { rawText = it },
                            placeholder = { Text("Örn:\n2 kg domates 65.00\n500 ml süt 28.50\n1 paket makarna", fontSize = 12.sp) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(100.dp)
                                .testTag("receipt_input_field"),
                            textStyle = TextStyle(fontSize = 12.sp, color = DarkCharcoal),
                            shape = RoundedCornerShape(10.dp),
                            colors = smartKitchenFieldColors()
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Button(
                            onClick = {
                                if (rawText.trim().isEmpty()) {
                                    Toast.makeText(context, "Lütfen fiş veya ürün metni girin!", Toast.LENGTH_SHORT).show()
                                } else {
                                    selectedBitmap = null
                                    onScanClick(rawText, null, activeScanMode)
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(42.dp)
                                .testTag("run_ocr_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = KitchenSage),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = "Analiz", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Metni Analiz Et", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // Photo preview when non-Idle
        selectedBitmap?.let { bmp ->
            if (scanUiState !is ScanUiState.Idle) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, BorderColor)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Okunan Görsel", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = DarkCharcoal)
                            Spacer(modifier = Modifier.height(6.dp))
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "Taranan Görsel",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.White)
                            )
                        }
                    }
                }
            }
        }

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
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            CircularProgressIndicator(color = KitchenSage, strokeWidth = 4.dp, modifier = Modifier.size(48.dp))
                            Text(
                                "Akıllı Analiz Yapılıyor...",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = KitchenSage
                            )
                            Text(
                                "Ürünler ayrıştırılıyor, raf ömürleri ve saklama alanları hesaplanıyor.",
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

                // Source Banner (AI vs Offline Fallback)
                item {
                    Surface(
                        color = if (scanUiState.isOfflineFallback) KitchenClay.copy(alpha = 0.1f) else AccentGreenBg,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(
                            1.dp,
                            if (scanUiState.isOfflineFallback) KitchenClay.copy(alpha = 0.35f) else AccentGreenBorder
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(
                                text = if (scanUiState.isOfflineFallback) {
                                    "📱 Çevrimdışı Akıllı Ayrıştırıcı"
                                } else {
                                    "✨ Gemini AI Analizi (${scanUiState.modelUsed ?: "gemini"})"
                                },
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (scanUiState.isOfflineFallback) KitchenClay else KitchenSage
                            )
                            if (!scanUiState.fallbackReason.isNullOrBlank()) {
                                Text(
                                    text = scanUiState.fallbackReason,
                                    fontSize = 10.sp,
                                    color = DarkCharcoal
                                )
                            }
                        }
                    }
                }

                // Detected Items Card
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
                                        "Tespit Edilen Gıdalar (${currentItemsList.size})",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = KitchenSage
                                    )
                                    Text(
                                        "Kaydetmeden önce düzenleyebilir veya silebilirsiniz",
                                        fontSize = 10.sp,
                                        color = Color.Gray
                                    )
                                }

                                Button(
                                    onClick = {
                                        if (currentItemsList.isNotEmpty()) {
                                            onAddToInventory(currentItemsList)
                                        }
                                    },
                                    enabled = currentItemsList.isNotEmpty(),
                                    colors = ButtonDefaults.buttonColors(containerColor = SafeGreen),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    modifier = Modifier.testTag("add_to_inventory_submit")
                                ) {
                                    Text("Hepsini Ekle", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            if (currentItemsList.isEmpty()) {
                                Text(
                                    "Listede ürün kalmadı. Yeniden tarayabilirsiniz.",
                                    fontSize = 12.sp,
                                    color = Color.Gray,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            } else {
                                currentItemsList.forEachIndexed { index, item ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp)
                                            .background(SoftSageBg, RoundedCornerShape(10.dp))
                                            .padding(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = KitchenSage,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(item.name, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = DarkCharcoal)
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Text("${item.quantity} • ${item.storage} • ${item.estimatedShelfLifeDays}g", fontSize = 10.sp, color = Color.Gray)
                                                if (item.price != null && item.price > 0.0) {
                                                    Text("• ₺${String.format(Locale.US, "%.1f", item.price)}", fontSize = 10.sp, color = KitchenClay, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }

                                        IconButton(
                                            onClick = { editingIndex = index },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(Icons.Default.Edit, contentDescription = "Düzenle", tint = KitchenSage, modifier = Modifier.size(15.dp))
                                        }

                                        IconButton(
                                            onClick = {
                                                editableDetectedItems = currentItemsList.toMutableList().apply { removeAt(index) }
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(Icons.Default.DeleteOutline, contentDescription = "Kaldır", tint = AlertRed, modifier = Modifier.size(15.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Optional AI Recipe suggestions (Kitchen Vision modes)
                if (!result.recipeSuggestions.isNullOrEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.5f))
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(Icons.Default.RestaurantMenu, contentDescription = null, tint = KitchenSage, modifier = Modifier.size(18.dp))
                                    Text("Bu Ürünlerle Önerilen Tarifler", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = DarkCharcoal)
                                }

                                result.recipeSuggestions.forEach { recipe ->
                                    Text(recipe.title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = KitchenSage)
                                    Text("⏱️ ${recipe.prepTime} • Kurtarılan: ${recipe.usedExpiringItems.joinToString(", ")}", fontSize = 11.sp, color = Color.Gray)
                                    if (!recipe.instructions.isNullOrBlank()) {
                                        Text(
                                            recipe.instructions,
                                            fontSize = 11.sp,
                                            color = DarkCharcoal,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(SoftSageBg, RoundedCornerShape(8.dp))
                                                .padding(8.dp)
                                        )
                                    }
                                    Button(
                                        onClick = { onStartCooking(recipe) },
                                        colors = ButtonDefaults.buttonColors(containerColor = KitchenSage),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.Default.OutdoorGrill, contentDescription = null, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Adım Adım Pişir", fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
                            Icon(Icons.Default.Eco, contentDescription = null, tint = SafeGreen, modifier = Modifier.size(24.dp))
                            Column {
                                Text("Sürdürülebilirlik Etkisi", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = SafeGreen)
                                Text(
                                    result.sustainabilityImpact ?: "Gıdaları kayıt altına alarak israfın önüne geçtiniz.",
                                    fontSize = 11.sp,
                                    color = DarkCharcoal
                                )
                            }
                        }
                    }
                }

                item {
                    OutlinedButton(
                        onClick = {
                            selectedBitmap = null
                            onReset()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("reset_ocr_button"),
                        border = BorderStroke(1.dp, KitchenSage),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Yeni Tarama Başlat", color = KitchenSage, fontWeight = FontWeight.Bold)
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
                                Icon(Icons.Default.Error, contentDescription = "Hata", tint = AlertRed)
                                Text("Tarama Tamamlanamadı", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = AlertRed)
                            }
                            Text(scanUiState.message, fontSize = 12.sp, color = DarkCharcoal)

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                            ) {
                                if (scanUiState.isApiKeyIssue) {
                                    OutlinedButton(
                                        onClick = {
                                            onReset()
                                            onNavigateToApiSettings()
                                        },
                                        border = BorderStroke(1.dp, KitchenSage),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Icon(Icons.Default.Settings, contentDescription = null, tint = KitchenSage, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("API Ayarları", fontSize = 11.sp, color = KitchenSage, fontWeight = FontWeight.Bold)
                                    }
                                }
                                Button(
                                    onClick = {
                                        selectedBitmap = null
                                        onReset()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = AlertRed),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.testTag("dismiss_error_btn")
                                ) {
                                    Text("Tamam", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }

            is ScanUiState.Idle -> {}
        }
    }

    editingIndex?.let { index ->
        val itemsList = editableDetectedItems
        if (itemsList != null && index in itemsList.indices) {
            EditDetectedItemDialog(
                item = itemsList[index],
                onDismiss = { editingIndex = null },
                onSave = { updatedItem ->
                    editableDetectedItems = itemsList.toMutableList().apply { set(index, updatedItem) }
                }
            )
        }
    }
}
