package com.chatapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.chatapp.ui.screens.ChatScreen

/**
 * PawPal entry point. The whole UI is Compose; the engine lives in
 * `com.chatapp.aiengine` and is completely UI-agnostic.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ChatScreen()
        }
    }
}
