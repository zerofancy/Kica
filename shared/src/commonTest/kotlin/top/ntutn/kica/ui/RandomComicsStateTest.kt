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

class RandomComicsStateTest {
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun loaderOnlyAutomaticallyRefreshesOnce() = runTest {
        val cached = ComicSummary(id = "cached", title = "Cached")
        val fresh = ComicSummary(id = "fresh", title = "Fresh")
        val networkResponse = CompletableDeferred<List<ComicSummary>>()
        var fetchCount = 0
        var writtenCache: List<ComicSummary>? = null
        val loader = RandomComicsLoader(
            fetchRandomComics = {
                fetchCount++
                networkResponse.await()
            },
            readCache = { listOf(cached) },
            writeCache = { writtenCache = it },
            scope = this,
            fallbackError = "failed",
        )

        loader.loadOnce()
        loader.loadOnce()
        runCurrent()

        assertEquals(1, fetchCount)
        assertEquals(listOf(cached), loader.state.items)
        assertTrue(loader.state.isLoading)

        networkResponse.complete(listOf(fresh))
        testScheduler.advanceUntilIdle()

        assertEquals(listOf(fresh), loader.state.items)
        assertEquals(listOf(fresh), writtenCache)
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
