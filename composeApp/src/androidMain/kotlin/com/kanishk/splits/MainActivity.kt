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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.kanishk.splits.ui.SplitsSplash

class MainActivity : ComponentActivity() {

    // Registered unconditionally: the contract has to be created before the Activity starts.
    private val requestNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* silence is fine */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Android's launch screen shows the whole ring (Theme.Splits.Starting); SplitsSplash
        // takes over from that exact frame and plays the split over the first screen.
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // A cold start from a tapped invite link arrives here.
        DeepLinks.offer(intent?.dataString)
        askForNotificationsIfNeeded()
        // Only on a fresh start; not when the Activity is recreated with state.
        val playSplash = savedInstanceState == null
        setContent {
            var showSplash by rememberSaveable { mutableStateOf(playSplash) }
            Box(Modifier.fillMaxSize()) {
                App()
                if (showSplash) SplitsSplash(onFinished = { showSplash = false })
            }
        }
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
