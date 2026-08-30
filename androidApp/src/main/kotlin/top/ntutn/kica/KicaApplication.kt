package top.ntutn.kica

import android.app.Application
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import java.io.File
import top.ntutn.kica.data.DefaultTitleTranslationService
import top.ntutn.kica.data.LibraryRepository
import top.ntutn.kica.data.SqlLibraryRepository
import top.ntutn.kica.data.SqlTitleTranslationCache
import top.ntutn.kica.data.TitleTranslationService
import top.ntutn.kica.db.KicaDatabase
import top.ntutn.kica.network.JvmTitleTranslationModelStore
import top.ntutn.kica.network.RealPicaRepository

class KicaApplication : Application() {
    internal lateinit var container: AndroidContainer
        private set

    override fun onCreate() {
        super.onCreate()
        val documentTreePicker = AndroidDocumentTreePicker()
        val legacyStoragePermission = AndroidLegacyStoragePermissionRequester()
        val platform = AndroidPlatformServices(this, documentTreePicker, legacyStoragePermission)
        val driver = AndroidSqliteDriver(KicaDatabase.Schema, this, "kica.db")
        val database = KicaDatabase(driver)
        val library = SqlLibraryRepository(database)
        val pica = RealPicaRepository(platform.credentialStore)
        val titleTranslation = DefaultTitleTranslationService(
            modelStore = JvmTitleTranslationModelStore(File(filesDir, "models")),
            cache = SqlTitleTranslationCache(database),
        )
        container = AndroidContainer(
            platform = platform,
            library = library,
            pica = pica,
            downloads = AndroidDownloadCoordinator(this, library),
            titleTranslation = titleTranslation,
            documentTreePicker = documentTreePicker,
            legacyStoragePermission = legacyStoragePermission,
        )
    }
}

internal data class AndroidContainer(
    val platform: AndroidPlatformServices,
    val library: LibraryRepository,
    val pica: RealPicaRepository,
    val downloads: AndroidDownloadCoordinator,
    val titleTranslation: TitleTranslationService,
    val documentTreePicker: AndroidDocumentTreePicker,
    val legacyStoragePermission: AndroidLegacyStoragePermissionRequester,
)
