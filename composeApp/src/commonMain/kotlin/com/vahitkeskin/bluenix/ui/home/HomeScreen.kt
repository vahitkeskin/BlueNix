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
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
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
import com.vahitkeskin.bluenix.core.model.BluetoothDeviceDomain
import com.vahitkeskin.bluenix.ui.theme.HologramBlue
import com.vahitkeskin.bluenix.ui.theme.NeonBlue
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToChat: () -> Unit,
    onNavigateToFiles: () -> Unit,
    onDeviceClick: (BluetoothDeviceDomain) -> Unit
) {

    val viewModel = koinViewModel<HomeViewModel>()
    val locationData by viewModel.locationState.collectAsState()
    val isBluetoothOn by viewModel.isBluetoothOn.collectAsState()

    // Okunmamış mesaj sayısı (Badge için)
    val unreadCount by viewModel.unreadMessageCount.collectAsState()

    val allDevices by viewModel.scannedDevices.collectAsState()

    // Listeyi Ayrıştır
    val pairedDevices = remember(allDevices) { allDevices.filter { it.isPaired } }
    val availableDevices = remember(allDevices) { allDevices.filter { !it.isPaired } }

    Scaffold(
        bottomBar = { BlueNixBottomBar(onNavigateToChat, onNavigateToFiles, unreadCount) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // --- HEADER ---
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
                StatusChip(isOnline = isBluetoothOn)
            }

            Spacer(modifier = Modifier.height(30.dp))

            // --- RADAR ANIMASYONU ---
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(280.dp)
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

                        val accuracy = locationData!!.accuracy
                        val accuracyColor =
                            if (accuracy < 5) Color.Green else if (accuracy < 10) Color.Yellow else Color.Red

                        Text(
                            text = "Precision: ±${accuracy.toInt()}m",
                            color = accuracyColor,
                            style = MaterialTheme.typography.labelSmall
                        )
                    } else {
                        Text(
                            "Acquiring Satellites...",
                            color = NeonBlue.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // --- CİHAZ LİSTESİ ---
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                // 1. Eşleşmiş Cihazlar
                if (pairedDevices.isNotEmpty()) {
                    item {
                        SectionHeader("PAIRED DEVICES (${pairedDevices.size})")
                    }
                    items(pairedDevices) { device ->
                        DeviceItem(
                            device = device,
                            onClick = { onDeviceClick(device) }
                        )
                    }
                }

                // 2. Yeni Cihazlar
                if (availableDevices.isNotEmpty()) {
                    item {
                        SectionHeader("AVAILABLE DEVICES (${availableDevices.size})")
                    }
                    items(availableDevices) { device ->
                        DeviceItem(
                            device = device,
                            onClick = { onDeviceClick(device) }
                        )
                    }
                } else if (isBluetoothOn) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "Scanning Frequency...",
                                color = Color.Gray,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }
}

// --- YARDIMCI BİLEŞENLER ---

@Composable
fun SectionHeader(title: String) {
    Text(
        text = title,
        color = NeonBlue.copy(alpha = 0.8f),
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF050B14))
            .padding(vertical = 12.dp, horizontal = 4.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceItem(
    device: BluetoothDeviceDomain,
    onClick: () -> Unit
) {
    val isPaired = device.isPaired
    val itemColor = if (isPaired) Color(0xFF1A2C42) else Color(0xFF111B2E)
    val iconColor = if (isPaired) Color(0xFF00FF9D) else NeonBlue

    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = itemColor),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isPaired) Icons.Default.BluetoothConnected else Icons.Default.Bluetooth,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(32.dp)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = device.name,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    maxLines = 1
                )
                Text(
                    text = device.address,
                    color = Color.Gray,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                // Sinyal Göstergesi
                SignalStrengthIndicator(level = device.getSignalLevel())
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = device.getEstimatedDistance(),
                    color = NeonBlue,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun SignalStrengthIndicator(level: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        for (i in 1..4) {
            val isActive = i <= level
            val barHeight = (i * 4).dp
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(barHeight)
                    .background(
                        color = if (isActive) NeonBlue else Color.Gray.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(2.dp)
                    )
            )
        }
    }
}

@Composable
fun StatusChip(isOnline: Boolean) {
    Surface(
        color = if (isOnline) NeonBlue.copy(alpha = 0.1f) else Color.Red.copy(alpha = 0.1f),
        shape = RoundedCornerShape(50),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isOnline) NeonBlue else Color.Red
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(8.dp).clip(CircleShape)
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
fun RadarAnimation() {
    val infiniteTransition = rememberInfiniteTransition()
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(animation = tween(3000, easing = LinearEasing))
    )
    Canvas(modifier = Modifier.fillMaxSize()) {
        drawCircle(color = HologramBlue, radius = size.minDimension / 2, style = Stroke(width = 2f))
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
        rotate(rotation) {
            drawCircle(
                brush = Brush.sweepGradient(
                    colors = listOf(
                        Color.Transparent,
                        NeonBlue.copy(alpha = 0.5f)
                    ), center = center
                ), radius = size.minDimension / 2
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
fun BlueNixBottomBar(onChat: () -> Unit, onFiles: () -> Unit, unreadCount: Int = 0) {
    NavigationBar(containerColor = Color(0xFF050B14), contentColor = NeonBlue) {
        NavigationBarItem(
            icon = { Icon(Icons.Default.BluetoothSearching, null) },
            label = { Text("Radar") }, selected = true, onClick = { },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = NeonBlue,
                selectedTextColor = NeonBlue,
                indicatorColor = Color(0xFF112240),
                unselectedIconColor = Color.Gray,
                unselectedTextColor = Color.Gray
            )
        )
        NavigationBarItem(
            icon = {
                if (unreadCount > 0) {
                    BadgedBox(badge = { Badge { Text(text = unreadCount.toString()) } }) {
                        Icon(Icons.Default.Chat, null)
                    }
                } else {
                    Icon(Icons.Default.Chat, null)
                }
            },
            label = { Text("Chat") }, selected = false, onClick = onChat,
            colors = NavigationBarItemDefaults.colors(
                unselectedIconColor = Color.Gray,
                unselectedTextColor = Color.Gray
            )
        )
        NavigationBarItem(
            icon = { Icon(Icons.Default.Folder, null) },
            label = { Text("Files") }, selected = false, onClick = onFiles,
            colors = NavigationBarItemDefaults.colors(
                unselectedIconColor = Color.Gray,
                unselectedTextColor = Color.Gray
            )
        )
        NavigationBarItem(
            icon = { Icon(Icons.Default.Settings, null) },
            label = { Text("Config") }, selected = false, onClick = { },
            colors = NavigationBarItemDefaults.colors(
                unselectedIconColor = Color.Gray,
                unselectedTextColor = Color.Gray
            )
        )
    }
}