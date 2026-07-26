package top.ntutn.kica

import android.app.Application
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import top.ntutn.kica.data.LibraryRepository
import top.ntutn.kica.data.SqlLibraryRepository
import top.ntutn.kica.db.KicaDatabase
import top.ntutn.kica.network.RealPicaRepository

class KicaApplication : Application() {
    internal lateinit var container: AndroidContainer
        private set

    override fun onCreate() {
        super.onCreate()
        val documentTreePicker = AndroidDocumentTreePicker()
        val platform = AndroidPlatformServices(this, documentTreePicker)
        val driver = AndroidSqliteDriver(KicaDatabase.Schema, this, "kica.db")
        val library = SqlLibraryRepository(KicaDatabase(driver))
        val pica = RealPicaRepository(platform.credentialStore)
        container = AndroidContainer(
            platform = platform,
            library = library,
            pica = pica,
            downloads = AndroidDownloadCoordinator(this, library),
            documentTreePicker = documentTreePicker,
        )
    }
}

internal data class AndroidContainer(
    val platform: AndroidPlatformServices,
    val library: LibraryRepository,
    val pica: RealPicaRepository,
    val downloads: AndroidDownloadCoordinator,
    val documentTreePicker: AndroidDocumentTreePicker,
)
