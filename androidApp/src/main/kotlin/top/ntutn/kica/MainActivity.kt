package top.ntutn.kica

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import top.ntutn.kica.model.AppSettings
import top.ntutn.kica.ui.KicaApp
import com.arkivanov.decompose.defaultComponentContext
import top.ntutn.kica.ui.navigation.AppServices
import top.ntutn.kica.ui.navigation.RootComponent

class MainActivity : ComponentActivity() {
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    private val legacyStoragePermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            (application as KicaApplication).container.legacyStoragePermission.deliver(granted)
        }
    private val documentTree =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            uri?.let {
                runCatching {
                    contentResolver.takePersistableUriPermission(
                        it,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                    )
                }
            }
            (application as KicaApplication).container.documentTreePicker.deliver(uri?.toString())
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        if (Build.VERSION.SDK_INT >= 33) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        val container = (application as KicaApplication).container
        container.documentTreePicker.bind { documentTree.launch(null) }
        container.legacyStoragePermission.bind {
            legacyStoragePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        val root = RootComponent(
            defaultComponentContext(),
            AppServices(container.pica, container.library, container.downloads, container.platform, container.titleTranslation),
        )
        setContent {
            val settings by container.library.settings().collectAsState(initial = AppSettings())
            LaunchedEffect(settings.preventScreenshots) {
                if (settings.preventScreenshots) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }
            KicaApp(root)
        }
    }

    override fun onDestroy() {
        (application as KicaApplication).container.documentTreePicker.unbind()
        (application as KicaApplication).container.legacyStoragePermission.unbind()
        super.onDestroy()
    }
}
