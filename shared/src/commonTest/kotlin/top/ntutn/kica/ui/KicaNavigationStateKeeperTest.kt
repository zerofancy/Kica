package top.ntutn.kica.ui

import com.arkivanov.essenty.statekeeper.StateKeeperDispatcher
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlin.random.Random
import kotlin.test.Test
import com.arkivanov.essenty.statekeeper.SerializableContainer
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import top.ntutn.kica.model.AppRoute
import top.ntutn.kica.ui.navigation.NavigationSnapshotV1
import top.ntutn.kica.ui.navigation.NavigationStateKeeper
import top.ntutn.kica.ui.navigation.SectionId
import top.ntutn.kica.ui.navigation.SnapshotPolicy

class KicaNavigationStateKeeperTest {
    @Test
    fun boundedEnvelopeRoundTripsSectionSelectionAndDeepEntryIdentity() = navigationTest {
        val source = host()
        val wrapped = NavigationStateKeeper(source.keeper)
        val original = main(source, wrapped.dispatcher)
        original.navigate(AppRoute.Detail("comic"))
        original.selectSection(SectionId.DOWNLOADS)
        original.navigate(AppRoute.Reader("comic", "episode"))
        val readerId = original.active.stack.value.active.instance.entryId
        val restoredHost = host(source.save())
        val restored = main(restoredHost, NavigationStateKeeper(restoredHost.keeper).dispatcher)
        assertEquals(SectionId.DOWNLOADS, restored.activeSection)
        assertEquals(readerId, restored.active.stack.value.active.instance.entryId)
        restored.selectSection(SectionId.HOME)
        assertEquals(AppRoute.Detail("comic"), restored.active.stack.value.active.instance.route)
    }

    @Test
    fun unknownSchemaMalformedJsonAndOversizedDataAllSafelyStartAtHome() = navigationTest {
        val badSnapshots = listOf(
            NavigationSnapshotV1(schema = 99, data = "{}"),
            NavigationSnapshotV1(data = "not-json"),
            NavigationSnapshotV1(data = "x".repeat(NavigationStateKeeper.MAX_BYTES + 1)),
            NavigationSnapshotV1(data = null),
        )
        for (snapshot in badSnapshots) {
            val source = host()
            source.keeper.register(KEY, NavigationSnapshotV1.serializer()) { snapshot }
            val restoredHost = host(source.save())
            val main = main(restoredHost, NavigationStateKeeper(restoredHost.keeper).dispatcher)
            assertEquals(SectionId.HOME, main.activeSection)
            assertEquals(AppRoute.Home, main.active.stack.value.active.instance.route)
            assertEquals(1, main.sections.value.items.size)
            assertEquals(emptyList(), pica.pageCalls)
        }
    }

    @Test
    fun savingOverBudgetPayloadDropsTheEnvelopeInsteadOfEmittingAnOversizedBundle() = navigationTest {
        val source = host()
        val wrapped = NavigationStateKeeper(source.keeper)
        val payload = largePayload()
        wrapped.dispatcher.register("oversized-page-draft", String.serializer()) { payload }
        val actualEncoded = Json.encodeToString(SerializableContainer.serializer(), wrapped.dispatcher.save())
        assertTrue(actualEncoded.encodeToByteArray().size > NavigationStateKeeper.MAX_BYTES,
            "Fixture must exceed the real compressed/serialized budget")
        val saved = StateKeeperDispatcher(source.save())
        val envelope = saved.consume(KEY, NavigationSnapshotV1.serializer())!!
        assertEquals(1, envelope.schema)
        assertNull(envelope.data)
        val encoded = Json.encodeToString(NavigationSnapshotV1.serializer(), envelope)
        assertTrue(encoded.encodeToByteArray().size < NavigationStateKeeper.MAX_BYTES)
    }

    @Test
    fun optionalDraftIsTrimmedOnSecondPassWhileEveryNavigationIdentitySurvives() = navigationTest {
        val source = host()
        val wrapped = NavigationStateKeeper(source.keeper)
        val original = main(source, wrapped.dispatcher)
        original.navigate(AppRoute.Detail("retained-home-comic"))
        val homeId = original.active.stack.value.active.instance.entryId
        original.selectSection(SectionId.FAVORITES)
        original.navigate(AppRoute.Search("submitted-query", fromTag = true))
        val page = original.active.stack.value.active.instance
        val payload = largePayload()
        val passes = mutableListOf<Boolean>()
        page.stateKeeper.register("optional-draft", String.serializer()) {
            passes += SnapshotPolicy.includeOptional
            if (SnapshotPolicy.includeOptional) payload else ""
        }
        val saved = source.save()
        assertTrue(true in passes && false in passes, "Budget retry must evaluate nested page suppliers without optional data")
        assertTrue(SnapshotPolicy.includeOptional, "Budget retry must restore the policy even after omitting drafts")
        val inspectionCopy = Json.decodeFromString(SerializableContainer.serializer(),
            Json.encodeToString(SerializableContainer.serializer(), saved))
        val envelope = StateKeeperDispatcher(inspectionCopy).consume(KEY, NavigationSnapshotV1.serializer())!!
        assertTrue(envelope.data != null)
        assertTrue(envelope.data!!.encodeToByteArray().size <= NavigationStateKeeper.MAX_BYTES)
        val restoredHost = host(saved)
        val recovered = main(restoredHost, NavigationStateKeeper(restoredHost.keeper).dispatcher)
        assertEquals(SectionId.FAVORITES, recovered.activeSection)
        val recoveredPage = recovered.active.stack.value.active.instance
        assertEquals(page.entryId, recoveredPage.entryId)
        assertEquals(AppRoute.Search("submitted-query", fromTag = true), recoveredPage.route)
        assertEquals("", recoveredPage.stateKeeper.consume("optional-draft", String.serializer()))
        recovered.selectSection(SectionId.HOME)
        assertEquals(homeId, recovered.active.stack.value.active.instance.entryId)
        assertEquals(AppRoute.Detail("retained-home-comic"), recovered.active.stack.value.active.instance.route)
    }

    @Test
    fun optionalPolicyIsRestoredWhenASnapshotSupplierFails() {
        kotlin.test.assertFailsWith<IllegalStateException> {
            SnapshotPolicy.withoutOptional {
                assertTrue(!SnapshotPolicy.includeOptional)
                error("Fixture supplier failure")
            }
        }
        assertTrue(SnapshotPolicy.includeOptional)
    }

    @Test
    fun validSmallBusinessSnapshotUsesExactlyOneRecoverableSource() = navigationTest {
        val source = host()
        val wrapped = NavigationStateKeeper(source.keeper)
        wrapped.dispatcher.register("page-anchor", String.serializer()) { "stable-item:12:24" }
        val restored = host(source.save())
        val recovered = NavigationStateKeeper(restored.keeper)
        assertEquals("stable-item:12:24", recovered.dispatcher.consume("page-anchor", String.serializer()))
        assertNull(recovered.dispatcher.consume("page-anchor", String.serializer()), "A consumed snapshot cannot replay twice")
    }

    companion object {
        private const val KEY = "kica-navigation-v1"

        private fun largePayload(): String {
            val random = Random(0x4b696361)
            // SerializableContainer compresses its payload: repeated strings do not exercise
            // the encoded budget. Seeded ASCII is stable, non-sensitive and poorly compressible.
            return buildString {
                repeat(NavigationStateKeeper.MAX_BYTES * 6) { append(random.nextInt(33, 127).toChar()) }
            }
        }
    }
}
