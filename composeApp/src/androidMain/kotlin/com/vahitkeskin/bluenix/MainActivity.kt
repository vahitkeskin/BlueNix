package com.vahitkeskin.bluenix

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat
import com.vahitkeskin.bluenix.service.BlueNixLocationService

class MainActivity : ComponentActivity() {

    // İzin sonucunu dinleyen mekanizma
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseLocationGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false

        if (fineLocationGranted || coarseLocationGranted) {
            // ✅ SADECE İZİN VARSA SERVİSİ BAŞLAT
            startLocationService()
        } else {
            println("❌ Konum izni reddedildi, servis başlatılmadı.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // ⚠️ DİKKAT: Burada 'startLocationService()' ASLA doğrudan çağrılmamalıdır!
        // Önce izinleri kontrol et, varsa başlat, yoksa iste.
        checkPermissionsAndStartService()

        setContent {
            App()
        }
    }

    private fun checkPermissionsAndStartService() {
        val permissionsToRequest = mutableListOf<String>()

        // 1. Konum İzni Kontrolü
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
            permissionsToRequest.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }

        // 2. Bildirim İzni (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            // İzinler eksik -> Kullanıcıdan iste
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
            // İzinler tam -> Servisi başlat
            startLocationService()
        }
    }

    private fun startLocationService() {
        val intent = Intent(this, BlueNixLocationService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}