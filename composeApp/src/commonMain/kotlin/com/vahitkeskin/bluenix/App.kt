package com.vahitkeskin.bluenix

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.vahitkeskin.bluenix.ui.home.HomeScreen
import com.vahitkeskin.bluenix.ui.theme.BlueNixBlack
import com.vahitkeskin.bluenix.ui.theme.BlueNixDarkSurface
import com.vahitkeskin.bluenix.ui.theme.NeonBlue
import com.vahitkeskin.bluenix.ui.theme.TextWhite

@Composable
//@Preview
fun App() {
    // Özel BlueNix Teması
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
                        onNavigateToChat = { navController.navigate("chat") },
                        onNavigateToFiles = { navController.navigate("files") }
                    )
                }

                composable("chat") {
                    // ChatScreen() -> İleride eklenecek
                    PlaceholderScreen("Chat Modülü", navController)
                }

                composable("files") {
                    // FileScreen() -> İleride eklenecek
                    PlaceholderScreen("Dosya Transferi", navController)
                }
            }
        }
    }
}

// Geçici Placeholder Ekranı
@Composable
fun PlaceholderScreen(title: String, navController: androidx.navigation.NavController) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
        Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
            Text(title, style = MaterialTheme.typography.headlineMedium)
            Button(onClick = { navController.popBackStack() }) {
                Text("Geri Dön")
            }
        }
    }
}