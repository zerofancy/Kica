package top.ntutn.kica.ui

import kotlin.test.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import top.ntutn.kica.data.PicaRepository
import top.ntutn.kica.model.ComicDetail
import top.ntutn.kica.model.ComicSummary
import top.ntutn.kica.ui.navigation.AppServices
import top.ntutn.kica.ui.navigation.SessionData
import top.ntutn.kica.ui.store.SettingsSnapshot
import top.ntutn.kica.ui.store.SettingsStore

@OptIn(ExperimentalCoroutinesApi::class)
class SessionTagResolverTest {
    @Test
    fun settingsClearsSessionResolverAndClosingSessionDropsItsProjection() = navigationTest { test ->
        val originalMain = main()
        val resolver = originalMain.session.tagResolver
        resolver.rememberTags("cached", listOf("Tag"))
        assertEquals(setOf("cached"), resolver.updates.value.keys)
        val settings = SettingsStore(services, SettingsSnapshot(), resolver)
        try {
            settings.clearCache()
            test.runCurrent()
            assertTrue(resolver.updates.value.isEmpty())
            resolver.rememberTags("cached-again", listOf("Other"))
            originalMain.close()
            assertTrue(resolver.updates.value.isEmpty())
            val nextSession = main().session
            assertNotSame(resolver, nextSession.tagResolver)
            assertTrue(nextSession.tagResolver.updates.value.isEmpty())
        } finally { settings.onDestroy() }
    }

    @Test
    fun sessionCloseCancelsInFlightTagLookupWithoutPublishingItsLateResult() = navigationTest { test ->
        val response = CompletableDeferred<ComicDetail>()
        var started = false
        var cancelled = false
        val repository = object : PicaRepository by pica {
            override suspend fun comic(id: String): ComicDetail {
                started = true
                return try { response.await() } finally { cancelled = !response.isCompleted }
            }
        }
        val dependencies = AppServices(repository, library, downloads, services.platform, translation)
        val session = SessionData(host().context(), dependencies)
        val lookup = test.async { session.tagResolver.resolve(ComicSummary("pending", "Pending")) }
        test.runCurrent()
        assertTrue(started)
        session.close()
        test.runCurrent()
        assertTrue(cancelled)
        assertTrue(lookup.isCancelled)
        response.complete(ComicDetail("pending", "Pending", tags = listOf("late")))
        test.runCurrent()
        assertTrue(session.tagResolver.updates.value.isEmpty())
    }
}
