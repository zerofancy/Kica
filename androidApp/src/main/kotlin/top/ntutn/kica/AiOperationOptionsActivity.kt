package top.ntutn.kica

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import top.ntutn.kica.model.AppSettings
import top.ntutn.kica.ui.screen.AiOperationOptionsScreen
import top.ntutn.kica.ui.theme.KicaFluentTheme
import androidx.core.content.edit

/**
 * Standalone activity for AI (GUI Agent) operation options.
 *
 * This page itself is declared as fully disabled for Agent operations in the SAEP policy.
 */
class AiOperationOptionsActivity : ComponentActivity() {
    companion object {
        private const val TAG = "AiOpOptions"
        private const val PREFS_NAME = "saep_policy"
        private const val KEY_POLICY_VERSION = "policy_version"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as KicaApplication).container

        setContent {
            val settings by container.library.settings().collectAsState(initial = AppSettings())
            KicaFluentTheme(preference = settings.theme) {
                AiOperationOptionsScreen(
                    library = container.library,
                    onToggle = { enabled -> pushDynamicPolicy(enabled) },
                )
            }
        }
    }

    private fun pushDynamicPolicy(enabled: Boolean) {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val nextVersion = prefs.getInt(KEY_POLICY_VERSION, 1) + 1
        val rows = SaepManager.updatePolicy(this, enabled, nextVersion)
        if (rows >= 0) {
            prefs.edit { putInt(KEY_POLICY_VERSION, nextVersion) }
            Log.d(TAG, "Policy updated to v$nextVersion, enabled=$enabled, rows=$rows")
        } else {
            Log.w(TAG, "Policy update failed, keeping local setting only")
        }
    }
}
