package com.vahitkeskin.bluenix

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.vahitkeskin.bluenix.ui.chat.ChatHistoryScreen
import com.vahitkeskin.bluenix.ui.chat.ChatScreen
import com.vahitkeskin.bluenix.ui.home.HomeScreen
import com.vahitkeskin.bluenix.ui.theme.BlueNixBlack
import com.vahitkeskin.bluenix.ui.theme.BlueNixDarkSurface
import com.vahitkeskin.bluenix.ui.theme.NeonBlue
import com.vahitkeskin.bluenix.ui.theme.TextWhite

@Composable
fun App() {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = NeonBlue,
            background = BlueNixBlack,
            surface = BlueNixDarkSurface,
            onPrimary = BlueNixBlack,
            onBackground = TextWhite,
            onSurface = TextWhite
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            val navController = rememberNavController()

            NavHost(
                navController = navController,
                startDestination = "home"
            ) {
                composable("home") {
                    HomeScreen(
                        // Bottom Bar -> Chat History'ye git
                        onNavigateToChat = { navController.navigate("chat_history") },
                        onNavigateToFiles = { navController.navigate("files") },
                        // Cihaz Listesi -> Direkt Chat'e git
                        onDeviceClick = { device ->
                            val safeName = device.name ?: "Unknown"
                            val safeAddress = device.address
                            navController.navigate("chat/$safeName/$safeAddress")
                        }
                    )
                }

                // 2. Chat History Ekranı (YENİ)
                composable("chat_history") {
                    ChatHistoryScreen(
                        onChatClick = { name, address ->
                            // Listeden birine tıklayınca detay sayfasına git
                            navController.navigate("chat/$name/$address")
                        }
                    )
                }

                // 3. Chat Detay Ekranı (Mevcut)
                composable("chat/{name}/{address}") { backStackEntry ->
                    val name = backStackEntry.arguments?.getString("name") ?: "Unknown"
                    val address = backStackEntry.arguments?.getString("address") ?: ""

                    ChatScreen(
                        targetDeviceName = name,
                        targetDeviceAddress = address,
                        onBackClick = { navController.popBackStack() }
                    )
                }

                // --- FILES ROUTE ---
                composable("files") {
                    PlaceholderScreen("Dosya Transferi", navController)
                }
            }
        }
    }
}

@Composable
fun PlaceholderScreen(title: String, navController: androidx.navigation.NavController) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = MaterialTheme.typography.headlineMedium, color = NeonBlue)
            Button(onClick = { navController.popBackStack() }) {
                Text("Geri Dön")
            }
        }
    }
}