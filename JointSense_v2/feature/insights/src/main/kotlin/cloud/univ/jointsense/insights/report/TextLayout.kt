package cloud.univ.jointsense.insights.report

import android.graphics.Paint

fun interface TextMeasurer {
    /** Returns the number of UTF-16 code units that fit within [maxWidth]. */
    fun breakText(text: String, maxWidth: Float): Int
}

/**
 * Splits text without trimming or dropping characters. Explicit newlines remain represented by
 * line boundaries, including empty and trailing paragraphs.
 */
fun layoutLines(text: String, measurer: TextMeasurer, maxWidth: Float): List<String> {
    require(maxWidth > 0f) { "maxWidth must be positive" }

    return explicitParagraphs(text).flatMap { paragraph ->
        if (paragraph.isEmpty()) {
            listOf("")
        } else {
            buildList {
                var offset = 0
                while (offset < paragraph.length) {
                    val remaining = paragraph.substring(offset)
                    val measured = measurer.breakText(remaining, maxWidth)
                        .coerceIn(0, remaining.length)
                    val safeCount = measured.safeCodePointBoundary(remaining)
                    add(remaining.substring(0, safeCount))
                    offset += safeCount
                }
            }
        }
    }
}

fun layoutLines(text: String, paint: Paint, maxWidth: Float): List<String> =
    layoutLines(
        text = text,
        measurer = TextMeasurer { remaining, width ->
            paint.breakText(remaining, true, width, null)
        },
        maxWidth = maxWidth,
    )

private fun explicitParagraphs(text: String): List<String> = buildList {
    var paragraphStart = 0
    while (true) {
        val newline = text.indexOf('\n', paragraphStart)
        if (newline < 0) {
            add(text.substring(paragraphStart))
            break
        }
        add(text.substring(paragraphStart, newline))
        paragraphStart = newline + 1
    }
}

private fun Int.safeCodePointBoundary(text: String): Int {
    if (this <= 0) return Character.charCount(text.codePointAt(0))
    if (this < text.length && text[this - 1].isHighSurrogate() && text[this].isLowSurrogate()) {
        return if (this > 1) this - 1 else Character.charCount(text.codePointAt(0))
    }
    return this
}
