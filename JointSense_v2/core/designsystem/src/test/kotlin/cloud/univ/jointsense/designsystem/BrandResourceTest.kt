package cloud.univ.jointsense.designsystem

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrandResourceTest {

    @Test
    fun logoResourcesContainApprovedGeometryAndBrandColors() {
        val xml = resource("jointsense_logo.xml").readText()

        assertTrue(xml.contains("android:viewportWidth=\"100\""))
        assertTrue(xml.contains("android:viewportHeight=\"100\""))
        assertTrue(xml.contains("#0E2841"))
        assertTrue(xml.contains("#156082"))
        assertTrue(xml.contains("#0F9ED5"))
        assertTrue(xml.contains("#196B24"))
        assertEquals("two endpoint rings", 2, xml.countOccurrences("JOINT_ENDPOINT_RING"))
        assertEquals("two analysis arcs", 2, xml.countOccurrences("ANALYSIS_ARC"))
        assertEquals("one synovial test well", 1, xml.countOccurrences("SYNOVIAL_TEST_WELL"))
        assertFalse(PURPLE_HEX.containsMatchIn(xml))
    }

    @Test
    fun monochromeLogoUsesTheSameSemanticGeometryWithoutBrandOrPurpleColors() {
        val xml = resource("jointsense_logo_monochrome.xml").readText()

        assertEquals("two endpoint rings", 2, xml.countOccurrences("JOINT_ENDPOINT_RING"))
        assertEquals("two analysis arcs", 2, xml.countOccurrences("ANALYSIS_ARC"))
        assertEquals("one synovial test well", 1, xml.countOccurrences("SYNOVIAL_TEST_WELL"))
        assertTrue(xml.contains("?android:attr/colorControlNormal"))
        assertFalse(BRAND_OR_PURPLE_HEX.containsMatchIn(xml))
    }

    @Test
    fun semanticPathCoordinatesStayInsideLauncherSafeMask() {
        listOf("jointsense_logo.xml", "jointsense_logo_monochrome.xml").forEach { name ->
            val paths = PATH_DATA.findAll(resource(name).readText()).map { it.groupValues[1] }.toList()

            assertEquals("$name should have exactly five semantic paths", 5, paths.size)
            paths.forEach { path ->
                NUMBER.findAll(path).map { it.value.toFloat() }.forEach { coordinate ->
                    assertTrue(
                        "$name coordinate $coordinate falls outside the 18..82 safe mask",
                        coordinate in 18f..82f,
                    )
                }
            }
        }
    }

    @Test
    fun launcherVariantsUseJointSignalAndNoLegacyRasterAssetsRemain() {
        val appResources = File("../../app/src/main/res")
        val foreground = File(appResources, "drawable/ic_launcher_joint_signal_foreground.xml").readText()
        val adaptive = File(appResources, "mipmap-anydpi-v26/ic_launcher.xml").readText()
        val themed = File(appResources, "mipmap-anydpi-v33/ic_launcher.xml").readText()
        val legacy = File(appResources, "mipmap-anydpi/ic_launcher.xml").readText()

        assertTrue(foreground.contains("#156082"))
        assertTrue(foreground.contains("#0F9ED5"))
        assertTrue(foreground.contains("#196B24"))
        assertFalse(PURPLE_HEX.containsMatchIn(foreground))
        assertTrue(adaptive.contains("@drawable/ic_launcher_joint_signal_foreground"))
        assertFalse(adaptive.contains("<monochrome"))
        assertTrue(themed.contains("@drawable/jointsense_logo_monochrome"))
        assertTrue(legacy.contains("@drawable/ic_launcher_joint_signal_foreground"))
        assertFalse(File(appResources, "drawable/logo.png").exists())
        assertFalse(File("src/main/res/drawable/logo.png").exists())
        assertTrue(appResources.walkTopDown().none { it.extension == "webp" && it.name.startsWith("ic_launcher") })
    }

    private fun resource(name: String): File =
        File("src/main/res/drawable/$name").also { file ->
            assertTrue("Missing brand resource: ${file.path}", file.isFile)
        }

    private fun String.countOccurrences(value: String): Int = windowed(value.length).count { it == value }

    private companion object {
        val PURPLE_HEX = Regex(
            "#(?:8A2BE2|7B2CBF|9C27B0|6200EE|7D21DC|BB86FC|3700B3)",
            RegexOption.IGNORE_CASE,
        )
        val BRAND_OR_PURPLE_HEX = Regex(
            "#(?:0E2841|156082|0F9ED5|196B24|8A2BE2|7B2CBF|9C27B0|6200EE|7D21DC|BB86FC|3700B3)",
            RegexOption.IGNORE_CASE,
        )
        val PATH_DATA = Regex("android:pathData=\"([^\"]+)\"")
        val NUMBER = Regex("-?\\d+(?:\\.\\d+)?")
    }
}
