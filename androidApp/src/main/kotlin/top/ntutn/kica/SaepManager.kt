package top.ntutn.kica

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.util.Log
import org.json.JSONObject
import androidx.core.net.toUri

/**
 * SAEP (Screen Automation Execution Protocol) utility.
 *
 * Provides reflection-based detection of SAEP availability and dynamic policy updates.
 * All reflection calls are wrapped in safe fallbacks so the app continues to work on
 * devices without the Obric UI framework.
 */
internal object SaepManager {
    private const val TAG = "SaepManager"
    private const val POLICY_PROVIDER_URI = "content://com.obric.agentrobots.provider/policy"

    /**
     * Returns true when SAEP is available and enabled on the current system.
     * Any reflection failure or missing class is treated as "not available".
     */
    @SuppressLint("PrivateApi")
    fun isRobotsEnabled(context: Context): Boolean {
        return runCatching {
            val clazz = Class.forName("android.security.obric.robots.RobotsHelperStub")
            val getInstance = clazz.getDeclaredMethod("getInstance")
            getInstance.isAccessible = true
            val instance = getInstance.invoke(null)
            val method = clazz.getDeclaredMethod("isRobotsEnabled", Context::class.java)
            method.isAccessible = true
            (method.invoke(instance, context) as? Boolean) == true
        }.getOrElse {
            Log.d(TAG, "SAEP not available: ${it.message}")
            false
        }
    }

    /**
     * Build the policy JSON string and push it to the system Policy Provider.
     *
     * @param enabled when true, app-level global_disable is set to false; when false it stays true.
     * @param version the monotonically increasing policy version.
     * @return rows affected by the provider update, or -1 on failure.
     */
    fun updatePolicy(context: Context, enabled: Boolean, version: Int): Int {
        val policy = buildPolicyJson(enabled, version)
        return runCatching {
            val uri = POLICY_PROVIDER_URI.toUri()
            val values = ContentValues().apply {
                put("policy", policy)
                put("version", version)
            }
            context.contentResolver.update(uri, values, null, null)
        }.getOrElse {
            Log.e(TAG, "updatePolicy failed: ${it.message}")
            -1
        }
    }

    private fun buildPolicyJson(enabled: Boolean, version: Int): String {
        val now = System.currentTimeMillis().toString()
        return JSONObject().apply {
            put("schema", "AGRP-Policy/1.0")
            put("policy_version", version)
            put("package", "top.ntutn.kica")
            put("updated_at", now)
            put("default_policy", JSONObject().apply {
                put("app", JSONObject().apply {
                    put("global_disable", true)
                    put("screenshot_disable", false)
                    put("input_disable", false)
                })
            })
            put("scope", JSONObject().apply {
                put("app", JSONObject().apply {
                    put("global_disable", !enabled)
                    put("screenshot_disable", false)
                    put("input_disable", false)
                })
                put("activities", JSONObject().apply {
                    put("top.ntutn.kica.AiOperationOptionsActivity", JSONObject().apply {
                        put("name", "AI操作选项")
                        put("page_scope", JSONObject().apply {
                            put("global_disable", true)
                            put("screenshot_disable", true)
                            put("input_disable", true)
                        })
                    })
                })
            })
        }.toString()
    }
}
