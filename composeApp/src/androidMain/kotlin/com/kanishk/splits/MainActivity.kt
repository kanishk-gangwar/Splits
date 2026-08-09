package com.kanishk.splits

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // A cold start from a tapped invite link arrives here.
        DeepLinks.offer(intent?.dataString)
        setContent { App() }
    }

    // A warm start (app already running) arrives here instead.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        DeepLinks.offer(intent.dataString)
    }
}
