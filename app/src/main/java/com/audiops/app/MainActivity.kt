package com.audiops.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.audiops.app.ui.AudioOpsScreen
import com.audiops.app.ui.AudioOpsTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            AudioOpsTheme {
                AudioOpsScreen()
            }
        }
    }
}
