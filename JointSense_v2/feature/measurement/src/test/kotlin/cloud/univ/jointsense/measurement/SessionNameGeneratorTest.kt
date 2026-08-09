package cloud.univ.jointsense.measurement

import org.junit.Assert.assertEquals
import org.junit.Test

class SessionNameGeneratorTest {
    @Test
    fun skipsDeletedMiddleSuffixAndUsesHighestExistingSuffix() {
        assertEquals("Test #4", nextSessionName(listOf("Test #1", "Test #3"), "Test"))
    }

    @Test
    fun isolatesNamesByExactLocalePrefix() {
        assertEquals(
            "检测 #10",
            nextSessionName(listOf("Test #40", "检测 #2", "检测 #9"), "检测"),
        )
    }

    @Test
    fun acceptsLargeValidSuffixWithoutIntOverflow() {
        assertEquals(
            "Test #9223372036854775807",
            nextSessionName(listOf("Test #9223372036854775806"), "Test"),
        )
    }

    @Test
    fun ignoresMalformedOrPartiallyMatchingNames() {
        assertEquals(
            "Test #1",
            nextSessionName(
                listOf("Test #abc", "Test #2x", " Test #99", "Test #-1", "Test#8"),
                "Test",
            ),
        )
    }
}
