package cloud.univ.jointsense.designsystem

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

class BrandResourceTest {

    @Test
    fun logoResourcesContainApprovedStructureAndOnlyBrandColors() {
        val vector = parseVector(resource("jointsense_logo.xml"))

        assertEquals(100f, vector.viewportWidth)
        assertEquals(100f, vector.viewportHeight)
        assertEquals(APPROVED_BRAND_COLORS, vector.hexColors)
        assertEquals(
            "two Ink endpoint rings",
            2,
            vector.paths.count {
                it.fillColor == TRANSPARENT && it.strokeColor == INK && it.strokeWidth == 4f
            },
        )
        assertEquals(
            "one Primary and one Cyan analysis arc",
            setOf(PRIMARY, CYAN),
            vector.paths
                .filter { it.fillColor == TRANSPARENT && it.strokeWidth == 8f }
                .map { it.strokeColor }
                .toSet(),
        )
        assertEquals(
            "one Bio Green synovial test well",
            1,
            vector.paths.count {
                it.fillColor == BIO_GREEN && it.strokeColor == INK && it.strokeWidth == 2f
            },
        )
        assertEquals("exactly five semantic paths", 5, vector.paths.size)
    }

    @Test
    fun monochromeLogoUsesTheSameFivePartStructureAndNoHexColors() {
        val vector = parseVector(resource("jointsense_logo_monochrome.xml"))

        assertTrue(vector.hexColors.isEmpty())
        assertEquals(
            "two monochrome endpoint rings",
            2,
            vector.paths.count {
                it.fillColor == TRANSPARENT && it.strokeColor == MONOCHROME && it.strokeWidth == 4f
            },
        )
        assertEquals(
            "two monochrome analysis arcs",
            2,
            vector.paths.count {
                it.fillColor == TRANSPARENT && it.strokeColor == MONOCHROME && it.strokeWidth == 8f
            },
        )
        assertEquals(
            "one monochrome synovial test well",
            1,
            vector.paths.count {
                it.fillColor == MONOCHROME && it.strokeColor == MONOCHROME && it.strokeWidth == 2f
            },
        )
        assertEquals("exactly five semantic paths", 5, vector.paths.size)
    }

    @Test
    fun visibleStrokeAndRoundCapExtentsStayInsideLauncherSafeMask() {
        val vectors = listOf(
            resource("jointsense_logo.xml"),
            resource("jointsense_logo_monochrome.xml"),
            appResource("drawable/ic_launcher_joint_signal_foreground.xml"),
        )

        vectors.forEach { file ->
            parseVector(file).paths.forEachIndexed { index, path ->
                assertFalse(
                    "${file.name} path ${index + 1} must use coordinate-only M/C/Z geometry",
                    UNSUPPORTED_PATH_COMMAND.containsMatchIn(path.pathData),
                )
                val coordinates = NUMBER.findAll(path.pathData).map { it.value.toFloat() }.toList()
                assertTrue("${file.name} path ${index + 1} must contain x/y pairs", coordinates.size % 2 == 0)
                val x = coordinates.filterIndexed { coordinateIndex, _ -> coordinateIndex % 2 == 0 }
                val y = coordinates.filterIndexed { coordinateIndex, _ -> coordinateIndex % 2 == 1 }
                val radius = path.strokeWidth / 2f

                // Cubic Beziers stay inside the convex hull of their control points. Expanding that
                // hull by half the stroke width also conservatively contains round joins and caps.
                assertTrue(
                    "${file.name} path ${index + 1} visible left edge is outside 18",
                    x.min() - radius >= SAFE_MIN,
                )
                assertTrue(
                    "${file.name} path ${index + 1} visible top edge is outside 18",
                    y.min() - radius >= SAFE_MIN,
                )
                assertTrue(
                    "${file.name} path ${index + 1} visible right edge is outside 82",
                    x.max() + radius <= SAFE_MAX,
                )
                assertTrue(
                    "${file.name} path ${index + 1} visible bottom edge is outside 82",
                    y.max() + radius <= SAFE_MAX,
                )
            }
        }
    }

    @Test
    fun launcherMatrixManifestAndColorWhitelistUseOnlyJointSignal() {
        val appResources = File("../../app/src/main/res")
        val foregroundFile = appResource("drawable/ic_launcher_joint_signal_foreground.xml")
        val backgroundFile = appResource("drawable/ic_launcher_background.xml")
        val legacy = appResource("mipmap-anydpi/ic_launcher.xml").readText()
        val legacyRound = appResource("mipmap-anydpi/ic_launcher_round.xml").readText()
        val adaptive = appResource("mipmap-anydpi-v26/ic_launcher.xml").readText()
        val adaptiveRound = appResource("mipmap-anydpi-v26/ic_launcher_round.xml").readText()
        val themed = appResource("mipmap-anydpi-v33/ic_launcher.xml").readText()
        val themedRound = appResource("mipmap-anydpi-v33/ic_launcher_round.xml").readText()
        val manifest = File("../../app/src/main/AndroidManifest.xml").readText()

        assertEquals(APPROVED_LAUNCHER_FOREGROUND_COLORS, parseVector(foregroundFile).hexColors)
        assertEquals(setOf(INK), parseVector(backgroundFile).hexColors)
        assertEquals(legacy, legacyRound)
        assertTrue(legacy.contains("@drawable/ic_launcher_joint_signal_foreground"))
        assertEquals(adaptive, adaptiveRound)
        assertTrue(adaptive.contains("@drawable/ic_launcher_joint_signal_foreground"))
        assertFalse(adaptive.contains("<monochrome"))
        assertEquals(themed, themedRound)
        assertTrue(themed.contains("@drawable/ic_launcher_joint_signal_foreground"))
        assertTrue(themed.contains("@drawable/jointsense_logo_monochrome"))
        assertTrue(manifest.contains("android:icon=\"@mipmap/ic_launcher\""))
        assertTrue(manifest.contains("android:roundIcon=\"@mipmap/ic_launcher_round\""))
        assertFalse(File(appResources, "drawable/ic_launcher_foreground.xml").exists())
        assertFalse(File(appResources, "drawable/logo.png").exists())
        assertFalse(File("src/main/res/drawable/logo.png").exists())
        assertTrue(appResources.walkTopDown().none { it.extension == "webp" && it.name.startsWith("ic_launcher") })
    }

    private fun resource(name: String): File =
        File("src/main/res/drawable/$name").requireFile()

    private fun appResource(path: String): File =
        File("../../app/src/main/res/$path").requireFile()

    private fun File.requireFile(): File = also { file ->
        assertTrue("Missing brand resource: ${file.path}", file.isFile)
    }

    private fun parseVector(file: File): VectorFixture {
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        val root = factory.newDocumentBuilder().parse(file).documentElement
        val nodes = root.getElementsByTagName("path")
        val paths = (0 until nodes.length).map { index ->
            val element = nodes.item(index) as Element
            VectorPath(
                pathData = element.androidAttribute("pathData"),
                fillColor = element.androidAttribute("fillColor"),
                strokeColor = element.androidAttribute("strokeColor"),
                strokeWidth = element.androidAttribute("strokeWidth").toFloatOrNull() ?: 0f,
            )
        }
        return VectorFixture(
            viewportWidth = root.androidAttribute("viewportWidth").toFloat(),
            viewportHeight = root.androidAttribute("viewportHeight").toFloat(),
            paths = paths,
            hexColors = HEX_COLOR.findAll(file.readText()).map { it.value.uppercase() }.toSet(),
        )
    }

    private fun Element.androidAttribute(name: String): String = getAttributeNS(ANDROID_NAMESPACE, name)

    private data class VectorFixture(
        val viewportWidth: Float,
        val viewportHeight: Float,
        val paths: List<VectorPath>,
        val hexColors: Set<String>,
    )

    private data class VectorPath(
        val pathData: String,
        val fillColor: String,
        val strokeColor: String,
        val strokeWidth: Float,
    )

    private companion object {
        const val ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"
        const val TRANSPARENT = "@android:color/transparent"
        const val MONOCHROME = "?android:attr/colorControlNormal"
        const val INK = "#0E2841"
        const val PRIMARY = "#156082"
        const val CYAN = "#0F9ED5"
        const val BIO_GREEN = "#196B24"
        const val SAFE_MIN = 18f
        const val SAFE_MAX = 82f
        val APPROVED_BRAND_COLORS = setOf(INK, PRIMARY, CYAN, BIO_GREEN)
        val APPROVED_LAUNCHER_FOREGROUND_COLORS = setOf("#FFFFFF", PRIMARY, CYAN, BIO_GREEN)
        val HEX_COLOR = Regex("#[0-9A-Fa-f]{6,8}")
        val NUMBER = Regex("-?\\d+(?:\\.\\d+)?")
        val UNSUPPORTED_PATH_COMMAND = Regex("[AaHhLlQqSsTtVv]")
    }
}
