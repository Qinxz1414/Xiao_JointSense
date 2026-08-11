package cloud.univ.jointsense.insights.report

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextLayoutTest {
    @Test
    fun wrapsLongChineseAndEnglishWithoutDroppingCharacters() {
        val input = "炎症标志物纵向趋势JointSense report keeps every character"

        val lines = layoutLines(input, fakeMeasurer(maxChars = 12), maxWidth = 100f)

        assertEquals(input, lines.joinToString(""))
        assertTrue(lines.all { it.length <= 12 })
    }

    @Test
    fun preservesExplicitParagraphsIncludingEmptyAndTrailingParagraphs() {
        val input = "first paragraph\n\n第三段\n"

        val lines = layoutLines(input, fakeMeasurer(maxChars = 80), maxWidth = 100f)

        assertEquals(listOf("first paragraph", "", "第三段", ""), lines)
    }

    @Test
    fun makesProgressWhenOneCodePointIsWiderThanTheAvailableWidth() {
        val input = "🧪A"
        val neverFits = TextMeasurer { _, _ -> 0 }

        val lines = layoutLines(input, neverFits, maxWidth = 1f)

        assertEquals(listOf("🧪", "A"), lines)
    }

    private fun fakeMeasurer(maxChars: Int) = TextMeasurer { text, _ ->
        minOf(text.length, maxChars)
    }
}
