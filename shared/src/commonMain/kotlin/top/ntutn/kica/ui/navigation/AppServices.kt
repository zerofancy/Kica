package top.ntutn.kica.ui.navigation

import top.ntutn.kica.data.DownloadCoordinator
import top.ntutn.kica.data.LibraryRepository
import top.ntutn.kica.data.PicaRepository
import top.ntutn.kica.data.PlatformServices
import top.ntutn.kica.data.TitleTranslationService

/** Application-owned dependencies; never put an Activity or Window in this container. */
class AppServices(
    val pica: PicaRepository,
    val library: LibraryRepository,
    val downloads: DownloadCoordinator,
    val platform: PlatformServices,
    val translation: TitleTranslationService,
    val updates: top.ntutn.kica.update.UpdateRuntime? = null,
)
