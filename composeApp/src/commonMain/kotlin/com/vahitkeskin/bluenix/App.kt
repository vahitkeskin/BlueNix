package com.vahitkeskin.bluenix

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
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
import com.vahitkeskin.bluenix.utils.safeStatusBarPadding
import kotlinx.coroutines.launch

// Bottom Bar Öğeleri (Index sırasına göre)
sealed class BottomNavItem(val index: Int, val icon: ImageVector, val label: String) {
    object Home : BottomNavItem(0, Icons.Default.Radar, "Radar")
    object History : BottomNavItem(1, Icons.Default.History, "Sohbetler")
}

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
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            val navController = rememberNavController()

            NavHost(
                navController = navController,
                startDestination = "main_tabs", // Başlangıç noktası artık Tab yapısı
                modifier = Modifier.safeStatusBarPadding()
            ) {
                // 1. ANA EKRAN (İçinde Pager ve BottomBar var)
                composable("main_tabs") {
                    MainTabsScreen(
                        onNavigateToChatDetail = { name, address ->
                            navController.navigate("chat/$name/$address")
                        },
                        onNavigateToFiles = {
                            navController.navigate("files")
                        }
                    )
                }

                // 2. DETAY EKRANLARI (BottomBar GİZLİ)
                composable("chat/{name}/{address}") { backStackEntry ->
                    val name = backStackEntry.arguments?.getString("name") ?: "Unknown"
                    val address = backStackEntry.arguments?.getString("address") ?: ""

                    ChatScreen(
                        targetDeviceName = name,
                        targetDeviceAddress = address,
                        onBackClick = { navController.popBackStack() }
                    )
                }

                composable("files") {
                    PlaceholderScreen("Dosya Transferi", navController)
                }
            }
        }
    }
}

// --- KAYDIRILABİLİR ANA EKRAN YAPISI ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainTabsScreen(
    onNavigateToChatDetail: (String, String) -> Unit,
    onNavigateToFiles: () -> Unit
) {
    // 2 Sayfalı Pager (0: Radar, 1: Geçmiş)
    val pagerState = rememberPagerState(pageCount = { 2 })
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            // Bottom Bar artık Pager State'ini dinliyor
            BlueNixBottomBar(
                selectedIndex = pagerState.currentPage,
                onItemSelected = { index ->
                    // Tıklanınca Pager o sayfaya kaysın
                    coroutineScope.launch {
                        pagerState.animateScrollToPage(index)
                    }
                }
            )
        }
    ) { innerPadding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = innerPadding.calculateBottomPadding()) // BottomBar'ın üstünde kal
        ) { page ->
            when (page) {
                0 -> HomeScreen(
                    // Home ekranındaki "Sohbetler" butonuna basınca sayfayı kaydır
                    onNavigateToChat = {
                        coroutineScope.launch { pagerState.animateScrollToPage(1) }
                    },
                    onNavigateToFiles = onNavigateToFiles,
                    onDeviceClick = { device ->
                        val safeName = device.name ?: "Unknown"
                        val safeAddress = device.address
                        onNavigateToChatDetail(safeName, safeAddress)
                    }
                )

                1 -> ChatHistoryScreen(
                    onChatClick = onNavigateToChatDetail
                )
            }
        }
    }
}

// --- GÜNCELLENMİŞ BOTTOM BAR ---
@Composable
fun BlueNixBottomBar(
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit
) {
    val items = listOf(BottomNavItem.Home, BottomNavItem.History)

    NavigationBar(
        containerColor = BlueNixDarkSurface,
        contentColor = NeonBlue,
        tonalElevation = 8.dp
    ) {
        items.forEach { item ->
            val isSelected = selectedIndex == item.index

            NavigationBarItem(
                icon = { Icon(item.icon, contentDescription = item.label) },
                label = { Text(item.label) },
                selected = isSelected,
                onClick = { onItemSelected(item.index) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = BlueNixBlack,
                    selectedTextColor = NeonBlue,
                    indicatorColor = NeonBlue,
                    unselectedIconColor = androidx.compose.ui.graphics.Color.Gray,
                    unselectedTextColor = androidx.compose.ui.graphics.Color.Gray
                )
            )
        }
    }
}

@Composable
fun PlaceholderScreen(title: String, navController: NavController) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = MaterialTheme.typography.headlineMedium, color = NeonBlue)
            Button(onClick = { navController.popBackStack() }) {
                Text("Geri Dön")
            }
        }
    }
}