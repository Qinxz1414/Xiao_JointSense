package cloud.univ.jointsense.measurement

import cloud.univ.jointsense.domain.model.DataSource
import cloud.univ.jointsense.domain.model.TestSession
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BuiltInSampleDisplayNameTest {
    @Test
    fun stableBuiltInIdsResolveWithoutDependingOnPersistedEnglishName() {
        assertEquals(
            BuiltInSampleDisplayName(BuiltInSampleKind.TEST, 7),
            builtInSampleDisplayName(session("builtin-tc7", "renamed")),
        )
        assertEquals(
            BuiltInSampleDisplayName(BuiltInSampleKind.CLIPBOARD, 3),
            builtInSampleDisplayName(session("builtin-clip-3", "renamed")),
        )
    }

    @Test
    fun legacyPersistedEnglishNamesRemainRecognized() {
        assertEquals(
            BuiltInSampleDisplayName(BuiltInSampleKind.TEST, 4),
            builtInSampleDisplayName(session("legacy-row", "Test Plate 4 · 2026-07-31")),
        )
        assertEquals(
            BuiltInSampleDisplayName(BuiltInSampleKind.CLIPBOARD, 2),
            builtInSampleDisplayName(session("legacy-row", "Clipboard Plate 2 · 2026-08-06 19:31")),
        )
    }

    @Test
    fun userSessionsAreNeverReinterpretedAsBuiltInSamples() {
        assertNull(
            builtInSampleDisplayName(
                session("user", "Test Plate 1", source = DataSource.USER),
            ),
        )
    }

    @Test
    fun historyVisibleNameAndDeleteDescriptionUseTheSameLocalizedResolution() {
        val source = File(
            "src/main/kotlin/cloud/univ/jointsense/measurement/HistoryScreen.kt",
        ).readText()

        assertTrue(source.contains("val displayName = session.localizedDisplayName()"))
        assertTrue(Regex("text\\s*=\\s*displayName").containsMatchIn(source))
        assertTrue(Regex("measurement_history_delete_session,\\s*displayName", RegexOption.DOT_MATCHES_ALL).containsMatchIn(source))
        assertFalse(source.contains("text = session.name"))
        assertFalse(Regex("measurement_history_delete_session,\\s*session\\.name", RegexOption.DOT_MATCHES_ALL).containsMatchIn(source))
    }

    private fun session(
        id: String,
        name: String,
        source: DataSource = DataSource.BUILT_IN,
    ) = TestSession(id, name, 0L, source, emptyList())
}
