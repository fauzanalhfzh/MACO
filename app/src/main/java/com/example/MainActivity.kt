package com.example

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import com.example.ui.MainScreen
import com.example.ui.FinanceViewModel
import com.example.ui.NotificationHelper
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    // Retrieve active FinanceViewModel
    private val viewModel: FinanceViewModel by viewModels()

    // Activity launcher for POST_NOTIFICATIONS runtime permission on Android 13+
    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Toast.makeText(this, "Notifikasi anggaran akhir pekan aktif!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Izin notifikasi ditolak. Anda tidak akan menerima pengingat akhir pekan kecuali jika diaktifkan di setelan sistem.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Support modern immersive visual edge-to-edge content bleed
        enableEdgeToEdge()

        // Initialize system budget warnings notification channel
        NotificationHelper.createNotificationChannel(this)

        // Automatically request notification permissions if running on Android 13+ (Tiramisu)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(
                        this,
                        android.Manifest.permission.POST_NOTIFICATIONS
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    requestNotificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to launch notification permission request", e)
        }

        // Auto schedule initial weekly alarm trigger if not already scheduled
        NotificationHelper.scheduleWeeklyReminder(this)

        setContent {
            MyApplicationTheme {
                MainScreen(viewModel = viewModel)
            }
        }
    }
}
