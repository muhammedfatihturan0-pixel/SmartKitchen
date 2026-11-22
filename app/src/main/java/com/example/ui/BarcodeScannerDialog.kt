package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.example.api.BarcodeProductInfo
import com.example.api.BarcodeScannerHelper
import com.example.util.BitmapUtils

@Composable
fun BarcodeScannerDialog(
    viewModel: InventoryViewModel,
    onDismiss: () -> Unit,
    onProductAdded: (BarcodeProductInfo) -> Unit
) {
    val context = LocalContext.current
    var manualBarcodeInput by remember { mutableStateOf("") }
    var detectedProduct by remember { mutableStateOf<BarcodeProductInfo?>(null) }
    var editedName by remember { mutableStateOf("") }
    var editedQuantity by remember { mutableStateOf("") }
    var isScanning by remember { mutableStateOf(false) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    fun setDetected(info: BarcodeProductInfo) {
        detectedProduct = info
        editedName = info.name
        editedQuantity = info.defaultQuantity
    }

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = pendingCameraUri
        if (success && uri != null) {
            val bitmap = BitmapUtils.decodeSampledSoftwareBitmap(context, uri, maxDimension = 1600)
            if (bitmap != null) {
                isScanning = true
                viewModel.scanBarcodeBitmap(bitmap) { info ->
                    isScanning = false
                    if (info != null) {
                        setDetected(info)
                        Toast.makeText(context, "Barkod Okundu: ${info.name}", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Görselde barkod bulunamadı. Daha net ve yakından çekin.", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val bitmap = BitmapUtils.decodeSampledSoftwareBitmap(context, uri, maxDimension = 1600)
            if (bitmap != null) {
                isScanning = true
                viewModel.scanBarcodeBitmap(bitmap) { info ->
                    isScanning = false
                    if (info != null) {
                        setDetected(info)
                        Toast.makeText(context, "Barkod Okundu: ${info.name}", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Seçilen görselde barkod bulunamadı.", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    fun launchCamera() {
        try {
            val uri = BitmapUtils.createCameraImageUri(context)
            pendingCameraUri = uri
            takePictureLauncher.launch(uri)
        } catch (e: Exception) {
            Toast.makeText(context, "Kamera açılamadı: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            launchCamera()
        } else {
            Toast.makeText(context, "Barkod taraması için kamera izni gerekiyor.", Toast.LENGTH_SHORT).show()
        }
    }

    fun startCamera() {
        val permissionCheck = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
        if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
            launchCamera()
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
                                imageVector = Icons.Default.QrCodeScanner,
                                contentDescription = null,
                                tint = KitchenSage,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                        Column {
                            Text("📦 Barkod Okuyucu", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = DarkCharcoal)
                            Text("Kamera, galeri veya barkod no ile 1 saniyede ekleyin", fontSize = 11.sp, color = Color.Gray)
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Kapat", tint = Color.Gray)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (isScanning) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = SoftSageBg)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(color = KitchenSage, modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp)
                            Text("Barkod taranıyor ve ürün bilgileri sorgulanıyor...", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = DarkCharcoal)
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                detectedProduct?.let { product ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = AccentGreenBg),
                        border = BorderStroke(1.dp, KitchenSage)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("✅ Bulunan Ürün (${product.sourceLabel})", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = KitchenSage)
                                Surface(color = KitchenSage, shape = RoundedCornerShape(6.dp)) {
                                    Text(
                                        text = product.storage,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            OutlinedTextField(
                                value = editedName,
                                onValueChange = { editedName = it },
                                label = { Text("Ürün Adı", fontSize = 10.sp) },
                                singleLine = true,
                                textStyle = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = DarkCharcoal),
                                colors = smartKitchenFieldColors(),
                                modifier = Modifier.fillMaxWidth()
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = editedQuantity,
                                    onValueChange = { editedQuantity = it },
                                    label = { Text("Miktar", fontSize = 10.sp) },
                                    singleLine = true,
                                    textStyle = TextStyle(fontSize = 12.sp, color = DarkCharcoal),
                                    colors = smartKitchenFieldColors(),
                                    modifier = Modifier.weight(1f)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(product.category, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = DarkCharcoal)
                                    Text("Raf Ömrü: ~${product.shelfLifeDays} Gün", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = KitchenClay)
                                }
                            }

                            Button(
                                onClick = {
                                    val finalProduct = product.copy(
                                        name = editedName.trim().ifBlank { product.name },
                                        defaultQuantity = editedQuantity.trim().ifBlank { product.defaultQuantity }
                                    )
                                    viewModel.addBarcodeProductToInventory(finalProduct)
                                    onProductAdded(finalProduct)
                                    Toast.makeText(context, "${finalProduct.name} dolaba eklendi!", Toast.LENGTH_SHORT).show()
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
                    Spacer(modifier = Modifier.height(10.dp))
                }

                // Camera & Gallery Scan Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { startCamera() },
                        modifier = Modifier
                            .weight(1.3f)
                            .height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = KitchenSage),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Kamera ile Tara", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = { galleryLauncher.launch("image/*") },
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp),
                        border = BorderStroke(1.dp, KitchenSage),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = KitchenSage, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Galeri", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = KitchenSage)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Manual Barcode Input (with Open Food Facts fallback)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = manualBarcodeInput,
                        onValueChange = { manualBarcodeInput = it },
                        placeholder = { Text("Barkod no (örn: 8690504012345)", fontSize = 11.sp) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(12.dp),
                        textStyle = TextStyle(fontSize = 13.sp, color = DarkCharcoal),
                        colors = smartKitchenFieldColors()
                    )
                    Button(
                        onClick = {
                            if (manualBarcodeInput.isNotBlank()) {
                                isScanning = true
                                viewModel.lookupBarcodeManual(manualBarcodeInput) { info ->
                                    isScanning = false
                                    setDetected(info)
                                }
                            }
                        },
                        enabled = !isScanning,
                        colors = ButtonDefaults.buttonColors(containerColor = KitchenSage),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.height(52.dp)
                    ) {
                        Text("Sorgula", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    "⚡ Hızlı Seçim & Sık Kullanılan Ürünler:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = DarkCharcoal
                )
                Spacer(modifier = Modifier.height(6.dp))

                val sampleList = remember { BarcodeScannerHelper.getAllSampleBarcodes() }
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(sampleList, key = { it.barcode }) { item ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { setDetected(item) },
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
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.name, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = DarkCharcoal)
                                    Text("Barkod: ${item.barcode} • ${item.category}", fontSize = 10.sp, color = Color.Gray)
                                }
                                Surface(color = SoftSageBg, shape = RoundedCornerShape(6.dp)) {
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

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = KitchenSage)
                ) {
                    Text("Kapat", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
