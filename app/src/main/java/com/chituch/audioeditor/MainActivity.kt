package com.chituch.audioeditor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.chituch.audioeditor.ui.screens.AudioEditorScreen
import com.chituch.audioeditor.ui.theme.ChiTuchTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ChiTuchTheme {
                AudioEditorScreen()
            }
        }
    }
}
