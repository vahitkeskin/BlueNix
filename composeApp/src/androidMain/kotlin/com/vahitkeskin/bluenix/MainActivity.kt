package com.vahitkeskin.bluenix

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat
import com.vahitkeskin.bluenix.core.service.BlueNixBackgroundService

class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            Log.i("BlueNixDebug", "✅ Tüm izinler verildi. Servis başlatılıyor...")
            startAppService()
        } else {
            Log.e("BlueNixDebug", "❌ Bazı izinler REDDEDİLDİ! Chat çalışmayabilir.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        checkPermissionsAndStart()

        setContent { App() }
    }

    private fun checkPermissionsAndStart() {
        val permissions = mutableListOf<String>()

        // Konum (Eski ve Yeni)
        permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION)

        // Android 12+ (API 31) İzinleri
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            // --- KRİTİK EKSİK BU OLABİLİR ---
            permissions.add(Manifest.permission.BLUETOOTH_ADVERTISE)
        }

        // Android 13+ (API 33) Bildirim
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        // Eksik izinleri kontrol et
        val missingPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            Log.w("BlueNixDebug", "⚠️ İzinler eksik, kullanıcıdan isteniyor: $missingPermissions")
            permissionLauncher.launch(missingPermissions.toTypedArray())
        } else {
            Log.i("BlueNixDebug", "✅ İzinler tam. Servis başlatılıyor.")
            startAppService()
        }
    }

    private fun startAppService() {
        val intent = Intent(this, BlueNixBackgroundService::class.java)
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