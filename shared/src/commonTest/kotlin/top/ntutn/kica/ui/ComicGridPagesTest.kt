package top.ntutn.kica.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import top.ntutn.kica.model.ComicSummary
import top.ntutn.kica.ui.component.*

class ComicGridPagesTest {
    @Test
    fun completelyFilteredPagesRemainAtTheirOriginalPositions() {
        val visible = listOf(ComicSummary("one", "One"), ComicSummary("last", "Last"))
        val pages = listOf(
            ComicGridPage(1, listOf("one", "hidden")),
            ComicGridPage(2, listOf("hidden-two")),
            ComicGridPage(3, listOf("hidden-three")),
            ComicGridPage(4, listOf("last")),
        )
        assertEquals(listOf(
            ComicGridEntry.Comic(visible[0]),
            ComicGridEntry.Page(2, PageStatus.FILTERED),
            ComicGridEntry.Page(3, PageStatus.FILTERED),
            ComicGridEntry.Comic(visible[1]),
        ), comicGridEntries(visible, pages, setOf("hidden", "hidden-two", "hidden-three")))
    }

    @Test
    fun removingRulesRestoresComicsAndRemovesFilteredPageMarkers() {
        val pages = listOf(ComicGridPage(1, listOf("one")), ComicGridPage(2, listOf("two")))
        val comics = listOf(ComicSummary("one", "One"), ComicSummary("two", "Two"))
        assertEquals(listOf(ComicGridEntry.Page(1, PageStatus.FILTERED), ComicGridEntry.Page(2, PageStatus.FILTERED)),
            comicGridEntries(emptyList(), pages, setOf("one", "two")))
        assertEquals(comics.map { ComicGridEntry.Comic(it) }, comicGridEntries(comics, pages))
    }

    @Test
    fun unknownEmptyAndDuplicateOnlyPagesDoNotClaimTheyWereFiltered() {
        val comic = ComicSummary("one", "One")
        val pages = listOf(ComicGridPage(1, listOf("one")), ComicGridPage(2, listOf("one")),
            ComicGridPage(3, emptyList()), ComicGridPage(4, listOf("hidden", "unknown")))
        assertEquals(listOf(ComicGridEntry.Comic(comic), ComicGridEntry.Page(2, PageStatus.EMPTY),
            ComicGridEntry.Page(3, PageStatus.EMPTY), ComicGridEntry.Page(4, PageStatus.UNVERIFIED)),
            comicGridEntries(listOf(comic), pages, setOf("hidden"), setOf("unknown")))
    }
}
