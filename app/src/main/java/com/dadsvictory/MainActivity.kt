package com.dadsvictory

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dadsvictory.notifications.Notifications
import com.dadsvictory.ui.VictoryViewModel
import com.dadsvictory.ui.nav.VictoryApp
import com.dadsvictory.ui.theme.DadsVictoryTheme

/**
 * A [FragmentActivity] rather than a plain ComponentActivity because
 * `BiometricPrompt` requires one, and the journal offers fingerprint unlock.
 * Compose is hosted from it exactly the same way.
 */
class MainActivity : FragmentActivity() {

    /** Set when the app is opened from the afternoon notification's craving button. */
    private var pendingCravingRequest by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingCravingRequest = intent?.getBooleanExtra(Notifications.EXTRA_OPEN_CRAVING, false) == true

        setContent {
            val viewModel: VictoryViewModel = viewModel(factory = VictoryViewModel.Factory)
            val state by viewModel.uiState.collectAsStateWithLifecycle()

            DadsVictoryTheme(
                themeMode = state.settings.themeMode,
                dynamicColour = state.settings.dynamicColour,
                highContrast = state.settings.highContrast,
                reducedMotion = state.settings.reducedMotion,
            ) {
                VictoryApp(
                    viewModel = viewModel,
                    state = state,
                    openCravingRequest = pendingCravingRequest,
                    onCravingRequestHandled = { pendingCravingRequest = false },
                )
            }
        }
    }

    /** The activity is singleTask, so a second tap on the notification lands here. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(Notifications.EXTRA_OPEN_CRAVING, false)) {
            pendingCravingRequest = true
        }
    }
}
