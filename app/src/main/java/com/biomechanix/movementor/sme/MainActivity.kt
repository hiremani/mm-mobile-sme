package com.biomechanix.movementor.sme

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.biomechanix.movementor.sme.navigation.AppNavigation
import com.biomechanix.movementor.sme.ui.theme.MoveMentorSmeTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Main entry point for the MoveMentor SME app.
 *
 * Sets up Compose with Material 3 theming and edge-to-edge display.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MoveMentorSmeTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation()
                }
            }
        }
    }
}
