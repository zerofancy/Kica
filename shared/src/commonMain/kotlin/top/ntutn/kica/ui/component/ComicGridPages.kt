package top.ntutn.kica.ui.component

import top.ntutn.kica.model.ComicSummary

data class ComicGridPage(val number: Int, val comicIds: List<String>)

internal sealed interface ComicGridEntry {
    val key: String

    data class Comic(val comic: ComicSummary) : ComicGridEntry {
        override val key = "comic:${comic.id}"
    }

    data class Page(val number: Int, val status: PageStatus) : ComicGridEntry {
        override val key = "page:$number"
    }
}

internal enum class PageStatus { FILTERED, UNVERIFIED, EMPTY }

internal fun comicGridEntries(
    visible: List<ComicSummary>,
    pages: List<ComicGridPage>,
    hiddenIds: Set<String> = emptySet(),
    unknownIds: Set<String> = emptySet(),
): List<ComicGridEntry> {
    if (pages.isEmpty()) return visible.distinctBy { it.id }.map(ComicGridEntry::Comic)
    val byId = visible.associateBy { it.id }
    val seen = mutableSetOf<String>()
    return buildList {
        pages.sortedBy { it.number }.forEach { page ->
            val ids = page.comicIds.distinct()
            if (ids.isNotEmpty() && ids.all { it in hiddenIds }) {
                add(ComicGridEntry.Page(page.number, PageStatus.FILTERED))
            } else {
                val items = ids.mapNotNull { id -> byId[id]?.takeIf { seen.add(id) } }
                if (items.isEmpty()) {
                    val status = if (ids.any { it in unknownIds }) PageStatus.UNVERIFIED else PageStatus.EMPTY
                    add(ComicGridEntry.Page(page.number, status))
                } else items.forEach { add(ComicGridEntry.Comic(it)) }
            }
        }
        visible.filter { seen.add(it.id) }.forEach { add(ComicGridEntry.Comic(it)) }
    }
}
