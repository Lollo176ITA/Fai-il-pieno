package it.faiilpieno

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import it.faiilpieno.ui.navigation.AppRoot
import it.faiilpieno.ui.theme.FaiIlPienoTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            FaiIlPienoTheme {
                AppRoot()
            }
        }
    }
}
