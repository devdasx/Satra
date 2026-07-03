package dev.satra.wallet

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.satra.wallet.ui.onboarding.SatraOnboardingScreen
import dev.satra.wallet.ui.theme.SatraTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SatraTheme {
                SatraOnboardingScreen()
            }
        }
    }
}

