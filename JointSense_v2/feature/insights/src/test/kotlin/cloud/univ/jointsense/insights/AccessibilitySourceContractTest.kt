package cloud.univ.jointsense.insights

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessibilitySourceContractTest {
    @Test
    fun recentTrendPublishesItsSummaryExactlyOnce() {
        val source = File(
            "src/main/kotlin/cloud/univ/jointsense/insights/HomeScreen.kt",
        ).readText()

        assertEquals(1, Regex("contentDescription\\s*=\\s*recentSummary").findAll(source).count())
    }

    @Test
    fun trendLegendReplacesDescendantSemanticsWithOneCoherentDescription() {
        val source = File(
            "src/main/kotlin/cloud/univ/jointsense/insights/TrendsScreen.kt",
        ).readText()

        assertTrue(
            Regex("clearAndSetSemantics\\s*\\{\\s*contentDescription\\s*=\\s*styleDescription")
                .containsMatchIn(source),
        )
    }
}
