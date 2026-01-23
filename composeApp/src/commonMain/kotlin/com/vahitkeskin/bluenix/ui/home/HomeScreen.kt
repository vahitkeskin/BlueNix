package com.vahitkeskin.bluenix.ui.home

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vahitkeskin.bluenix.ui.theme.HologramBlue
import com.vahitkeskin.bluenix.ui.theme.NeonBlue
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToChat: () -> Unit,
    onNavigateToFiles: () -> Unit
) {

    val viewModel = koinViewModel<HomeViewModel>()
    val locationData by viewModel.locationState.collectAsState()

    // Gerçek cihaz listesini dinle
    val nearbyDevices by viewModel.scannedDevices.collectAsState()

    Scaffold(
        bottomBar = { BlueNixBottomBar(onNavigateToChat, onNavigateToFiles) },
        containerColor = MaterialTheme.colorScheme.background // Arka plan rengini garantiye aldık
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "BLUENIX",
                    color = NeonBlue,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
                StatusChip(isOnline = true)
            }

            Spacer(modifier = Modifier.height(40.dp))

            // RADAR (Visual Core)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(300.dp)
            ) {
                RadarAnimation()

                // Ortadaki bilgi
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.BluetoothSearching,
                        contentDescription = null,
                        tint = NeonBlue,
                        modifier = Modifier.size(48.dp)
                    )

                    // CANLI VERİ GÖSTERİMİ
                    if (locationData != null) {
                        Text(
                            text = "LAT: ${locationData!!.latitude.toString().take(7)}",
                            color = NeonBlue,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "LNG: ${locationData!!.longitude.toString().take(7)}",
                            color = NeonBlue,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )

                        println("This is very good: ${locationData?.latitude}, ${locationData?.longitude}")

                        // Hassasiyet Göstergesi
                        val accuracy = locationData!!.accuracy
                        val accuracyColor = if (accuracy < 5) Color.Green else if (accuracy < 10) Color.Yellow else Color.Red

                        Text(
                            text = "Precision: ±${accuracy.toInt()}m",
                            color = accuracyColor,
                            style = MaterialTheme.typography.labelSmall
                        )
                    } else {
                        Text(
                            "Acquiring Satellites...", // Uydu aranıyor...
                            color = NeonBlue.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(40.dp))

            // DİNAMİK LİSTE BAŞLIĞI
            Text(
                "NEARBY DEVICES (${nearbyDevices.size})",
                style = MaterialTheme.typography.labelMedium,
                color = Color.Gray,
                modifier = Modifier.align(Alignment.Start)
            )
            Spacer(modifier = Modifier.height(10.dp))

            // LAZY COLUMN KULLAN (Çok cihaz olabilir)
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f) // Kalan alanı kaplasın
            ) {
                items(nearbyDevices) { device ->
                    DeviceItem(
                        name = device.name ?: "Unknown [${device.address.takeLast(4)}]",
                        status = device.getEstimatedDistance() // Hesaplanan mesafe
                    )
                }
            }
        }
    }
}

@Composable
fun RadarAnimation() {
    val infiniteTransition = rememberInfiniteTransition()
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing)
        )
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        // Sabit Halkalar
        drawCircle(
            color = HologramBlue,
            radius = size.minDimension / 2,
            style = Stroke(width = 2f)
        )
        drawCircle(
            color = HologramBlue.copy(alpha = 0.5f),
            radius = size.minDimension / 3,
            style = Stroke(width = 2f)
        )
        drawCircle(
            color = HologramBlue.copy(alpha = 0.2f),
            radius = size.minDimension / 6,
            style = Stroke(width = 2f)
        )

        // Dönen Tarayıcı
        rotate(rotation) {
            drawCircle(
                brush = Brush.sweepGradient(
                    colors = listOf(Color.Transparent, NeonBlue.copy(alpha = 0.5f)),
                    center = center
                ),
                radius = size.minDimension / 2
            )
            drawLine(
                color = NeonBlue,
                start = center,
                end = Offset(center.x + size.minDimension / 2, center.y),
                strokeWidth = 4f
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceItem(name: String, status: String) {
    // DÜZELTME BURADA YAPILDI:
    // Modifier.clickable yerine Card'ın onClick parametresi kullanılıyor.
    Card(
        onClick = { /* Tıklama aksiyonu */ },
        colors = CardDefaults.cardColors(containerColor = Color(0xFF111B2E)),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(10.dp).clip(CircleShape).background(NeonBlue)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(text = name, color = Color.White, fontWeight = FontWeight.Bold)
                Text(text = status, color = Color.Gray, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun StatusChip(isOnline: Boolean) {
    Surface(
        color = if (isOnline) NeonBlue.copy(alpha = 0.1f) else Color.Red.copy(alpha = 0.1f),
        shape = RoundedCornerShape(50),
        border = androidx.compose.foundation.BorderStroke(1.dp, if(isOnline) NeonBlue else Color.Red)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (isOnline) NeonBlue else Color.Red)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isOnline) "ONLINE" else "OFFLINE",
                color = if (isOnline) NeonBlue else Color.Red,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
fun BlueNixBottomBar(onChat: () -> Unit, onFiles: () -> Unit) {
    NavigationBar(
        containerColor = Color(0xFF050B14),
        contentColor = NeonBlue
    ) {
        NavigationBarItem(
            icon = { Icon(Icons.Default.BluetoothSearching, contentDescription = null) },
            label = { Text("Radar") },
            selected = true,
            onClick = { /* Zaten buradayız */ },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = NeonBlue,
                selectedTextColor = NeonBlue,
                indicatorColor = Color(0xFF112240),
                unselectedIconColor = Color.Gray,
                unselectedTextColor = Color.Gray
            )
        )
        NavigationBarItem(
            icon = { Icon(Icons.Default.Chat, contentDescription = null) },
            label = { Text("Chat") },
            selected = false,
            onClick = onChat,
            colors = NavigationBarItemDefaults.colors(
                unselectedIconColor = Color.Gray,
                unselectedTextColor = Color.Gray
            )
        )
        NavigationBarItem(
            icon = { Icon(Icons.Default.Folder, contentDescription = null) },
            label = { Text("Files") },
            selected = false,
            onClick = onFiles,
            colors = NavigationBarItemDefaults.colors(
                unselectedIconColor = Color.Gray,
                unselectedTextColor = Color.Gray
            )
        )
        NavigationBarItem(
            icon = { Icon(Icons.Default.Settings, contentDescription = null) },
            label = { Text("Config") },
            selected = false,
            onClick = { },
            colors = NavigationBarItemDefaults.colors(
                unselectedIconColor = Color.Gray,
                unselectedTextColor = Color.Gray
            )
        )
    }
}