package cloud.univ.jointsense.designsystem

import cloud.univ.jointsense.designsystem.theme.factorArgb
import cloud.univ.jointsense.designsystem.theme.gradeArgb
import cloud.univ.jointsense.domain.model.InflammationFactor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BrandPaletteTest {
    @Test
    fun factorAndGradeMappingsAreStable() {
        assertEquals(0xFFD64545, factorArgb(InflammationFactor.TNF_ALPHA))
        assertEquals(0xFFE97132, factorArgb(InflammationFactor.IL6))
        assertEquals(0xFF196B24, factorArgb(InflammationFactor.IL1_BETA))
        assertEquals(5, (0..4).map(::gradeArgb).distinct().size)
    }

    @Test
    fun gradesAreClampedAndNeverUsePurple() {
        assertEquals(gradeArgb(0), gradeArgb(-1))
        assertEquals(gradeArgb(4), gradeArgb(5))

        val forbiddenPurple = setOf(0xFF8A2BE2, 0xFF7B2CBF, 0xFF9C27B0, 0xFF6200EE)
        val semanticColors = (0..4).map(::gradeArgb) + listOf(
            factorArgb(InflammationFactor.TNF_ALPHA),
            factorArgb(InflammationFactor.IL6),
            factorArgb(InflammationFactor.IL1_BETA),
        )
        assertTrue(semanticColors.none(forbiddenPurple::contains))
        assertNotEquals(gradeArgb(0), gradeArgb(4))
    }
}
