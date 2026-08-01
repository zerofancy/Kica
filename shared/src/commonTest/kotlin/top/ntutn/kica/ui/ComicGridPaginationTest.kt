package top.ntutn.kica.ui

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ComicGridPaginationTest {
    @Test
    fun requestsNextPageWhenViewportApproachesGridEnd() {
        assertFalse(shouldLoadMore(totalItemsCount = 20, lastVisibleItemIndex = 14))
        assertTrue(shouldLoadMore(totalItemsCount = 20, lastVisibleItemIndex = 16))
        assertTrue(shouldLoadMore(totalItemsCount = 3, lastVisibleItemIndex = 2))
    }

    @Test
    fun ignoresEmptyOrNotYetLaidOutGrid() {
        assertFalse(shouldLoadMore(totalItemsCount = 0, lastVisibleItemIndex = -1))
        assertFalse(shouldLoadMore(totalItemsCount = 20, lastVisibleItemIndex = -1))
    }

    @Test
    fun waitsUntilAnotherPageCanBeRequested() {
        assertFalse(
            shouldLoadMore(
                totalItemsCount = 20,
                lastVisibleItemIndex = 19,
                canLoadMore = false,
            ),
        )
        assertFalse(
            shouldLoadMore(
                totalItemsCount = 20,
                lastVisibleItemIndex = 19,
                loadingMore = true,
            ),
        )
        assertFalse(
            shouldLoadMore(
                totalItemsCount = 20,
                lastVisibleItemIndex = 19,
                hasLoadMoreError = true,
            ),
        )
    }
}
