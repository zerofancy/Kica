package top.ntutn.kica.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import top.ntutn.kica.model.ComicSummary

class RandomComicsStateTest {
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
