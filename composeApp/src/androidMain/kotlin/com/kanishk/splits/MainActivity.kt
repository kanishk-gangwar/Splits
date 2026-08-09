package com.kanishk.splits

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts

class MainActivity : ComponentActivity() {

    // Registered unconditionally: the contract has to be created before the Activity starts.
    private val requestNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* silence is fine */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // A cold start from a tapped invite link arrives here.
        DeepLinks.offer(intent?.dataString)
        askForNotificationsIfNeeded()
        setContent { App() }
    }

    // A warm start (app already running) arrives here instead.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        DeepLinks.offer(intent.dataString)
    }

    private fun askForNotificationsIfNeeded() {
        if (Build.VERSION.SDK_INT < 33) return
        val granted = checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
