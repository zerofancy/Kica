package top.ntutn.kica.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import top.ntutn.kica.ui.store.LibrarySnapshot
import top.ntutn.kica.ui.store.SearchSnapshot
import top.ntutn.kica.ui.store.SettingsSnapshot

class BrowseSnapshotsTest {
    @Test
    fun unsubmittedDraftDoesNotReplaceSubmittedSearchAfterRestore() {
        val original = SearchSnapshot(query = "unsubmitted", category = "draft-category",
            fromTag = true, keyword = "submitted", submittedCategory = "committed-category", loadedPageHint = 4)
        val restored = Json.decodeFromString<SearchSnapshot>(Json.encodeToString(original))
        assertEquals("unsubmitted", restored.query)
        assertEquals("submitted", restored.keyword)
        assertEquals("committed-category", restored.submittedCategory)
        assertEquals(4, restored.loadedPageHint)
    }

    @Test
    fun libraryVisibilityOptionsSurviveSmallSnapshot() {
        val original = LibrarySnapshot(visibleLimit = 120, showHidden = true)
        assertEquals(original, Json.decodeFromString<LibrarySnapshot>(Json.encodeToString(original)))
    }

    @Test
    fun settingsDraftSnapshotHasNoLockSecretsOrModelState() {
        val original = SettingsSnapshot("proxy.example", "8080", hostDirty = true, portDirty = true)
        val encoded = Json.encodeToString(original)
        assertEquals(original, Json.decodeFromString<SettingsSnapshot>(encoded))
        assertFalse(encoded.contains("Password"))
        assertFalse(encoded.contains("Pattern"))
        assertFalse(encoded.contains("token"))
    }
}
