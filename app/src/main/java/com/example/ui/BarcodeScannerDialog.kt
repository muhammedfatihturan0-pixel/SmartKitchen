package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.example.api.BarcodeProductInfo
import com.example.api.BarcodeScannerHelper

@Composable
fun BarcodeScannerDialog(
    viewModel: InventoryViewModel,
    onDismiss: () -> Unit,
    onProductAdded: (BarcodeProductInfo) -> Unit
) {
    val context = LocalContext.current
    var manualBarcodeInput by remember { mutableStateOf("") }
    var detectedProduct by remember { mutableStateOf<BarcodeProductInfo?>(null) }
    var isScanning by remember { mutableStateOf(false) }

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            isScanning = true
            viewModel.scanBarcodeBitmap(bitmap) { info ->
                isScanning = false
                if (info != null) {
                    detectedProduct = info
                    Toast.makeText(context, "Barkod Okundu: ${info.name}", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Görselde barkod bulunamadı. Lütfen daha yakından veya net çekin.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            takePictureLauncher.launch(null)
        } else {
            Toast.makeText(context, "Barkod taraması için kamera izni gerekiyor.", Toast.LENGTH_SHORT).show()
        }
    }

    fun startCamera() {
        val permissionCheck = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
        if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
            takePictureLauncher.launch(null)
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .testTag("barcode_scanner_dialog"),
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
                            color = KitchenSage.copy(alpha = 0.15f),
                            shape = CircleShape,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.QrCodeScanner,
                                contentDescription = null,
                                tint = KitchenSage,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                        Column {
                            Text("📦 Barkod Okuyucu", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = DarkCharcoal)
                            Text("Ürünleri 1 saniyede dolabınıza kaydedin", fontSize = 11.sp, color = Color.Gray)
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Kapat", tint = Color.Gray)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // If Product Detected Card
                if (detectedProduct != null) {
                    val product = detectedProduct!!
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = AccentGreenBg),
                        border = BorderStroke(1.dp, KitchenSage)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("✅ Bulunan Ürün", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = KitchenSage)
                                Surface(
                                    color = KitchenSage,
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = product.storage,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Text(
                                text = product.name,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = DarkCharcoal
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Kategori: ${product.category}", fontSize = 11.sp, color = Color.DarkGray)
                                Text("•", color = Color.LightGray)
                                Text("Raf Ömrü: ~${product.shelfLifeDays} Gün", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = KitchenClay)
                            }

                            Button(
                                onClick = {
                                    viewModel.addBarcodeProductToInventory(product)
                                    onProductAdded(product)
                                    Toast.makeText(context, "${product.name} dolaba eklendi!", Toast.LENGTH_SHORT).show()
                                    detectedProduct = null
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = KitchenSage),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Dolaba Ekle", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Camera Scanner Trigger
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { startCamera() },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SoftSageBg),
                    border = BorderStroke(1.dp, KitchenSage.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            color = KitchenSage,
                            shape = CircleShape,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = null, tint = Color.White, modifier = Modifier.padding(11.dp))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Kamera ile Barkod Tara", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = DarkCharcoal)
                            Text("Gıda paketinin arkasındaki barkodu kameraya tutun", fontSize = 11.sp, color = Color.Gray)
                        }
                        Icon(Icons.Default.ArrowForwardIos, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(14.dp))
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Manual Input
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = manualBarcodeInput,
                        onValueChange = { manualBarcodeInput = it },
                        placeholder = { Text("Barkod no girin (örn: 8690504...)", fontSize = 11.sp) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = KitchenSage,
                            unfocusedBorderColor = Color.LightGray
                        )
                    )
                    Button(
                        onClick = {
                            if (manualBarcodeInput.isNotBlank()) {
                                val info = BarcodeScannerHelper.lookupBarcode(manualBarcodeInput)
                                detectedProduct = info
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = KitchenSage),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.height(50.dp)
                    ) {
                        Text("Sorgula", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Fast Catalog Picker (Great for testing in emulator!)
                Text(
                    "⚡ Hızlı Test & Sık Kullanılan Ürün Barkodları:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = DarkCharcoal
                )

                val sampleList = remember { BarcodeScannerHelper.getAllSampleBarcodes() }
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(sampleList) { item ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    detectedProduct = item
                                },
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.3f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(item.name, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = DarkCharcoal)
                                    Text("Barkod: ${item.barcode} • ${item.category}", fontSize = 10.sp, color = Color.Gray)
                                }
                                Surface(
                                    color = SoftSageBg,
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        "Seç",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = KitchenSage,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Close button
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = KitchenSage)
                ) {
                    Text("Tamamla", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
