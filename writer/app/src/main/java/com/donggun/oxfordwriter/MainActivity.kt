package com.donggun.oxfordwriter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = C.Wood, onPrimary = C.Ivory, background = C.Ivory, onBackground = C.Ink,
                    surface = C.Ivory, onSurface = C.Ink, surfaceVariant = C.Paper2, onSurfaceVariant = C.Ink2,
                    outline = C.Line, outlineVariant = C.Line, secondaryContainer = C.Paper2, onSecondaryContainer = C.Ink,
                    surfaceContainerHigh = C.Card, surfaceContainerHighest = C.Card, surfaceContainer = C.Card,
                ),
            ) { WriterApp() }
        }
    }
}
