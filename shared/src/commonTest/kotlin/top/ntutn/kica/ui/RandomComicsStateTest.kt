package top.ntutn.kica.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import top.ntutn.kica.model.ComicSummary
import top.ntutn.kica.ui.state.RandomComicsUiState

class RandomComicsStateTest {
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun loaderOnlyAutomaticallyRefreshesOnce() = runTest {
        val cached = ComicSummary(id = "cached", title = "Cached")
        val fresh = ComicSummary(id = "fresh", title = "Fresh")
        val networkResponse = CompletableDeferred<List<ComicSummary>>()
        var fetchCount = 0
        var writtenCache: List<ComicSummary>? = null
        val loader = top.ntutn.kica.ui.store.RandomComicsStore(
            fetch = {
                fetchCount++
                networkResponse.await()
            },
            readCache = { listOf(cached) },
            writeCache = { writtenCache = it },
            scope = this,
        )

        loader.ensureLoaded()
        loader.ensureLoaded()
        runCurrent()

        assertEquals(1, fetchCount)
        assertEquals(listOf(cached), loader.state.value.items)
        assertTrue(loader.state.value.isLoading)

        networkResponse.complete(listOf(fresh))
        testScheduler.advanceUntilIdle()

        assertEquals(listOf(fresh), loader.state.value.items)
        assertEquals(listOf(fresh), writtenCache)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun returningAfterFailureDoesNotSilentlyRetry() = runTest {
        var calls = 0
        val store = top.ntutn.kica.ui.store.RandomComicsStore(
            fetch = { calls++; error("offline") }, readCache = { null }, writeCache = {}, scope = this,
        )
        store.ensureLoaded()
        runCurrent()
        store.ensureLoaded()
        runCurrent()
        assertEquals(1, calls)
        assertEquals("offline", store.state.value.errorMessage)
        store.refresh()
        runCurrent()
        assertEquals(2, calls)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun destroyedStoreCancelsRequestWithoutCachingOrUserError() = runTest {
        val response = CompletableDeferred<List<ComicSummary>>()
        var written = false
        var cancelled = false
        val store = top.ntutn.kica.ui.store.RandomComicsStore(
            fetch = { try { response.await() } finally { cancelled = !response.isCompleted } },
            readCache = { null }, writeCache = { written = true }, scope = backgroundScope,
        )
        store.ensureLoaded()
        runCurrent()
        store.onDestroy()
        runCurrent()
        assertTrue(cancelled)
        assertFalse(written)
        assertNull(store.state.value.errorMessage)
    }

    @Test
    fun successfulRefreshReplacesExistingItems() {
        val oldItem = ComicSummary(id = "old", title = "Old")
        val newItem = ComicSummary(id = "new", title = "New")
        val state = RandomComicsUiState()
            .loadSuccess(listOf(oldItem))
            .startLoading()
            .loadSuccess(listOf(newItem))

        assertEquals(listOf(newItem), state.items)
        assertFalse(state.isLoading)
        assertNull(state.errorMessage)
    }

    @Test
    fun failedRefreshPreservesExistingItems() {
        val oldItem = ComicSummary(id = "old", title = "Old")
        val loading = RandomComicsUiState()
            .loadSuccess(listOf(oldItem))
            .startLoading()
        val failed = loading.loadFailure("offline")

        assertEquals(listOf(oldItem), failed.items)
        assertFalse(failed.isLoading)
        assertEquals("offline", failed.errorMessage)
    }

    @Test
    fun initialLoadingAndEmptySuccessRemainDistinct() {
        val loading = RandomComicsUiState().startLoading()
        assertNull(loading.items)
        assertTrue(loading.isLoading)

        val empty = loading.loadSuccess(emptyList())
        assertEquals(emptyList(), empty.items)
        assertFalse(empty.isLoading)
    }
}
