package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Login
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun RegistrationScreen(
    viewModel: InventoryViewModel,
    onRegister: (String, String) -> Unit
) {
    var username by remember { mutableStateOf("") }
    var selectedAvatar by remember { mutableStateOf("🥦") }
    val avatarList = listOf("🥦", "🥑", "🍎", "🍋", "👨‍🍳", "🥬", "🥕", "🧀")
    val validationError = remember(username) { viewModel.validateUsername(username) }
    val isValid = username.trim().isNotEmpty() && validationError == null

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(AccentGreenBg, SoftSageBg)
                )
            )
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("registration_card"),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    modifier = Modifier.size(64.dp),
                    shape = CircleShape,
                    color = KitchenSage.copy(alpha = 0.12f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(text = selectedAvatar, fontSize = 34.sp)
                    }
                }

                Text(
                    text = "Aramıza Hoş Geldiniz! 🌱",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = DarkCharcoal,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "Sürdürülebilir akıllı mutfak topluluğuna katılmak için profil avatarınızı ve benzersiz kullanıcı adınızı belirleyin.",
                    fontSize = 11.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center,
                    lineHeight = 16.sp
                )

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Profil Simgesi / Avatar Seçin:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = KitchenSage
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(avatarList) { avatar ->
                            val isSelected = avatar == selectedAvatar
                            Surface(
                                onClick = { selectedAvatar = avatar },
                                shape = CircleShape,
                                color = if (isSelected) KitchenSage.copy(alpha = 0.2f) else Color.Transparent,
                                border = BorderStroke(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) KitchenSage else Color.LightGray.copy(alpha = 0.5f)
                                ),
                                modifier = Modifier.size(42.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(avatar, fontSize = 22.sp)
                                }
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Kullanıcı Adı Seçin") },
                    placeholder = { Text("Örn: SifirAtikSefi") },
                    singleLine = true,
                    textStyle = TextStyle(color = DarkCharcoal, fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("username_register_input"),
                    shape = RoundedCornerShape(12.dp),
                    colors = smartKitchenFieldColors(
                        focusedBorderColor = if (isValid) SafeGreen else KitchenSage
                    ),
                    isError = validationError != null && username.isNotEmpty()
                )

                if (username.isNotEmpty()) {
                    if (validationError != null) {
                        Surface(
                            color = Color(0xFFFDE8E8),
                            border = BorderStroke(1.dp, Color(0xFFF8B4B4)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            androidx.compose.foundation.layout.Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = Color.Red, modifier = Modifier.size(18.dp))
                                Text(
                                    text = validationError,
                                    color = Color(0xFF9B1C1C),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    lineHeight = 15.sp
                                )
                            }
                        }
                    } else {
                        Surface(
                            color = Color(0xFFEDFDF5),
                            border = BorderStroke(1.dp, SafeGreen.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            androidx.compose.foundation.layout.Row(
                                modifier = Modifier.padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SafeGreen, modifier = Modifier.size(18.dp))
                                Text(
                                    text = "✓ Mükemmel! Bu kullanıcı adı kullanılabilir.",
                                    color = SafeGreen,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                Surface(
                    color = SoftSageBg.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("📋 Kayıt & Kullanıcı Adı Kuralları:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = KitchenSage)
                        Text("• Uzunluk: En az 3, en fazla 20 karakter olmalıdır.", fontSize = 10.sp, color = DarkCharcoal)
                        Text("• Karakterler: Harfler, rakamlar, alt çizgi (_) ve tire (-) kullanılabilir.", fontSize = 10.sp, color = DarkCharcoal)
                        Text("• Admin, Sistem vb. korunan adlar kullanılamaz.", fontSize = 10.sp, color = DarkCharcoal)
                    }
                }

                Button(
                    onClick = {
                        if (isValid) {
                            onRegister(username.trim(), selectedAvatar)
                        }
                    },
                    enabled = isValid,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("register_submit_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = KitchenSage,
                        disabledContainerColor = Color.LightGray
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    androidx.compose.foundation.layout.Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Login, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(
                            "Kaydol & Başla",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}
