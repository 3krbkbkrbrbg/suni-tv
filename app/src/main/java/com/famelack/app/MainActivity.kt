package com.famelack.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.famelack.app.ui.FamelackApp
import com.famelack.app.ui.theme.FamelackTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FamelackTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    FamelackApp()
                }
            }
        }
    }
}
