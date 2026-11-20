package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.configureAppCheck
import com.example.domain.rules.CounterpartyConfirmationRules
import com.example.ui.navigation.PassportAppRoot
import com.example.ui.theme.PaperCanvas
import com.example.ui.theme.WorkPassportTheme
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {

    private val incomingConfirmationToken = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Configure Firebase App Check with launch intent BEFORE Compose UI or Firebase calls
        configureAppCheck(this, intent)

        extractConfirmationTokenFromIntent(intent)?.let { token ->
            incomingConfirmationToken.value = token
        }

        // STEP 2: Enable edge-to-edge and render UI
        enableEdgeToEdge()
        setContent {
            val deepLinkToken by incomingConfirmationToken.collectAsStateWithLifecycle()
            WorkPassportTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = PaperCanvas
                ) {
                    PassportAppRoot(
                        incomingConfirmationToken = deepLinkToken,
                        onConsumeIncomingToken = { incomingConfirmationToken.value = null }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        extractConfirmationTokenFromIntent(intent)?.let { token ->
            incomingConfirmationToken.value = token
        }
    }

    private fun extractConfirmationTokenFromIntent(intent: Intent?): String? {
        val dataString = intent?.dataString ?: return null
        val token = CounterpartyConfirmationRules.extractTokenFromInput(dataString)
        return token.takeIf { it.length >= CounterpartyConfirmationRules.MIN_TOKEN_LENGTH }
    }
}
