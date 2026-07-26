package top.ntutn.kica

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import top.ntutn.kica.ui.KicaApp

class MainActivity : ComponentActivity() {
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
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
        if (Build.VERSION.SDK_INT >= 33) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        val container = (application as KicaApplication).container
        container.documentTreePicker.bind { documentTree.launch(null) }
        setContent {
            KicaApp(
                picaRepository = container.pica,
                libraryRepository = container.library,
                downloadCoordinator = container.downloads,
                platformServices = container.platform,
            )
        }
    }

    override fun onDestroy() {
        (application as KicaApplication).container.documentTreePicker.unbind()
        super.onDestroy()
    }
}
