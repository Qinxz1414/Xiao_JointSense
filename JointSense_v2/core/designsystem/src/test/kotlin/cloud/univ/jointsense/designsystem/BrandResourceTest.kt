package cloud.univ.jointsense.designsystem

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

class BrandResourceTest {

    @Test
    fun vectorsShareStrictFivePartGeometryWithApprovedPerPathAttributes() {
        val full = parseVector(resource("jointsense_logo.xml"))
        val mono = parseVector(resource("jointsense_logo_monochrome.xml"))
        val foreground = parseVector(appResource("drawable/ic_launcher_joint_signal_foreground.xml"))

        listOf(full, mono, foreground).forEach { vector ->
            assertEquals(100f, vector.viewportWidth)
            assertEquals(100f, vector.viewportHeight)
            assertEquals("exactly five semantic paths", 5, vector.paths.size)
            vector.paths.forEach { path ->
                assertTrue("Path must use only absolute M/C/Z grammar: ${path.pathData}", STRICT_PATH.matches(path.pathData))
                assertTrue("Every semantic path must be stroked", path.strokeWidth > 0f)
                assertEquals("round", path.strokeLineCap)
                assertEquals("round", path.strokeLineJoin)
            }
        }
        assertEquals(full.paths.map { it.pathData }, mono.paths.map { it.pathData })
        assertEquals(full.paths.map { it.pathData }, foreground.paths.map { it.pathData })

        assertSemanticPath(full.paths[0], TRANSPARENT, INK, 4f)
        assertSemanticPath(full.paths[1], TRANSPARENT, INK, 4f)
        assertSemanticPath(full.paths[2], TRANSPARENT, PRIMARY, 8f)
        assertSemanticPath(full.paths[3], TRANSPARENT, CYAN, 8f)
        assertSemanticPath(full.paths[4], BIO_GREEN, INK, 2f)
        assertEquals(APPROVED_BRAND_COLORS, full.pathColors)

        assertSemanticPath(mono.paths[0], TRANSPARENT, MONOCHROME, 4f)
        assertSemanticPath(mono.paths[1], TRANSPARENT, MONOCHROME, 4f)
        assertSemanticPath(mono.paths[2], TRANSPARENT, MONOCHROME, 8f)
        assertSemanticPath(mono.paths[3], TRANSPARENT, MONOCHROME, 8f)
        assertSemanticPath(mono.paths[4], MONOCHROME, MONOCHROME, 2f)
        assertTrue(mono.pathColors.isEmpty())

        assertSemanticPath(foreground.paths[0], TRANSPARENT, WHITE, 4f)
        assertSemanticPath(foreground.paths[1], TRANSPARENT, WHITE, 4f)
        assertSemanticPath(foreground.paths[2], TRANSPARENT, PRIMARY, 8f)
        assertSemanticPath(foreground.paths[3], TRANSPARENT, CYAN, 8f)
        assertSemanticPath(foreground.paths[4], BIO_GREEN, WHITE, 2f)
        assertEquals(APPROVED_LAUNCHER_FOREGROUND_COLORS, foreground.pathColors)
    }

    @Test
    fun strictPathGrammarRejectsRelativeCommandsAndMalformedCoordinates() {
        val invalid = listOf(
            "m20,31 C20,28 23,25 26,25 Z",
            "M20,31 c20,28 23,25 26,25 Z",
            "M20,31 L32,31 Z",
            "M20,31 A6,6 0,1 0,32,31 Z",
            "M20 31 C20,28 23,25 26,25 Z",
            "M20,31 C20,28 23,25 Z",
            "M20,31 C20,28 23,25 26,25 z",
        )

        invalid.forEach { path -> assertFalse("Invalid grammar was accepted: $path", STRICT_PATH.matches(path)) }
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
                val coordinates = path.coordinates
                val x = coordinates.filterIndexed { coordinateIndex, _ -> coordinateIndex % 2 == 0 }
                val y = coordinates.filterIndexed { coordinateIndex, _ -> coordinateIndex % 2 == 1 }
                val radius = path.strokeWidth / 2f

                // Cubic Beziers stay inside the convex hull of their control points. Expanding that
                // hull by half the stroke width also conservatively contains round joins and caps.
                assertTrue("${file.name} path ${index + 1} visible left edge", x.min() - radius >= SAFE_MIN)
                assertTrue("${file.name} path ${index + 1} visible top edge", y.min() - radius >= SAFE_MIN)
                assertTrue("${file.name} path ${index + 1} visible right edge", x.max() + radius <= SAFE_MAX)
                assertTrue("${file.name} path ${index + 1} visible bottom edge", y.max() + radius <= SAFE_MAX)
            }
        }
    }

    @Test
    fun legacyRoundHasTransparentCornersAndLauncherMatrixUsesJointSignal() {
        val normalLayer = parseLayerList(appResource("mipmap-anydpi/ic_launcher.xml"))
        val roundLayer = parseLayerList(appResource("mipmap-anydpi/ic_launcher_round.xml"))
        val adaptive = parseAdaptiveIcon(appResource("mipmap-anydpi-v26/ic_launcher.xml"))
        val adaptiveRound = parseAdaptiveIcon(appResource("mipmap-anydpi-v26/ic_launcher_round.xml"))
        val themed = parseAdaptiveIcon(appResource("mipmap-anydpi-v33/ic_launcher.xml"))
        val themedRound = parseAdaptiveIcon(appResource("mipmap-anydpi-v33/ic_launcher_round.xml"))
        val manifest = File("../../app/src/main/AndroidManifest.xml").readText()

        assertEquals(listOf(BACKGROUND, FOREGROUND), normalLayer.drawables)
        assertEquals(listOf(ROUND_BACKGROUND, FOREGROUND), roundLayer.drawables)
        assertEquals(72, normalLayer.foregroundWidthDp)
        assertEquals(72, normalLayer.foregroundHeightDp)
        assertEquals(72, roundLayer.foregroundWidthDp)
        assertEquals(72, roundLayer.foregroundHeightDp)

        val normalBackground = parseVector(appResource("drawable/ic_launcher_background.xml"))
        assertEquals(setOf(INK), normalBackground.pathColors)
        val roundBackground = parseVector(appResource("drawable/ic_launcher_round_background.xml"))
        assertEquals(108f, roundBackground.viewportWidth)
        assertEquals(108f, roundBackground.viewportHeight)
        assertEquals(setOf(INK), roundBackground.pathColors)
        assertEquals("one closed circular fill leaves corners transparent", 1, roundBackground.paths.size)
        val roundPath = roundBackground.paths.single()
        assertEquals(INK, roundPath.fillColor)
        assertEquals("", roundPath.strokeColor)
        assertTrue(roundPath.pathData.endsWith(" Z"))
        assertEquals("circle uses four cubic quadrants", 4, roundPath.pathData.count { it == 'C' })
        assertEquals(2f, roundPath.coordinates.min())
        assertEquals(106f, roundPath.coordinates.max())

        val expectedAdaptive = AdaptiveIcon(BACKGROUND, FOREGROUND, null)
        assertEquals(expectedAdaptive, adaptive)
        assertEquals(expectedAdaptive, adaptiveRound)
        val expectedThemed = AdaptiveIcon(BACKGROUND, FOREGROUND, MONOCHROME_DRAWABLE)
        assertEquals(expectedThemed, themed)
        assertEquals(expectedThemed, themedRound)
        assertTrue(manifest.contains("android:icon=\"@mipmap/ic_launcher\""))
        assertTrue(manifest.contains("android:roundIcon=\"@mipmap/ic_launcher_round\""))
    }

    @Test
    fun obsoleteLogoAndEveryLegacyRasterLauncherExtensionAreAbsent() {
        val appResources = File("../../app/src/main/res")

        assertFalse(File(appResources, "drawable/ic_launcher_foreground.xml").exists())
        assertFalse(File(appResources, "drawable/logo.png").exists())
        assertFalse(File("src/main/res/drawable/logo.png").exists())
        val rasterLaunchers = appResources.walkTopDown().filter { file ->
            file.isFile && file.nameWithoutExtension.startsWith("ic_launcher") &&
                file.extension.lowercase() in RASTER_EXTENSIONS
        }.toList()
        assertTrue("Raster launcher assets remain: $rasterLaunchers", rasterLaunchers.isEmpty())
    }

    @Test
    fun vectorParserRejectsDoctypeAndExternalEntityDeclarations() {
        val malicious = File.createTempFile("jointsense-vector-doctype", ".xml").apply {
            writeText(
                """<!DOCTYPE vector [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
                    |<vector xmlns:android="$ANDROID_NAMESPACE"
                    | android:viewportWidth="100" android:viewportHeight="100">&xxe;</vector>
                """.trimMargin(),
            )
            deleteOnExit()
        }

        assertThrows(Exception::class.java) { parseVector(malicious) }
    }

    private fun assertSemanticPath(path: VectorPath, fill: String, stroke: String, width: Float) {
        assertEquals(fill, path.fillColor)
        assertEquals(stroke, path.strokeColor)
        assertEquals(width, path.strokeWidth)
        assertEquals("round", path.strokeLineCap)
        assertEquals("round", path.strokeLineJoin)
    }

    private fun resource(name: String): File = File("src/main/res/drawable/$name").requireFile()

    private fun appResource(path: String): File = File("../../app/src/main/res/$path").requireFile()

    private fun File.requireFile(): File = also { file ->
        assertTrue("Missing brand resource: ${file.path}", file.isFile)
    }

    private fun parseVector(file: File): VectorFixture {
        val root = secureXmlFactory().newDocumentBuilder().parse(file).documentElement
        val nodes = root.getElementsByTagName("path")
        val paths = (0 until nodes.length).map { index ->
            val element = nodes.item(index) as Element
            VectorPath(
                pathData = element.androidAttribute("pathData"),
                fillColor = element.androidAttribute("fillColor"),
                strokeColor = element.androidAttribute("strokeColor"),
                strokeWidth = element.androidAttribute("strokeWidth").toFloatOrNull() ?: 0f,
                strokeLineCap = element.androidAttribute("strokeLineCap"),
                strokeLineJoin = element.androidAttribute("strokeLineJoin"),
            )
        }
        return VectorFixture(
            viewportWidth = root.androidAttribute("viewportWidth").toFloat(),
            viewportHeight = root.androidAttribute("viewportHeight").toFloat(),
            paths = paths,
        )
    }

    private fun parseLayerList(file: File): LegacyLayerList {
        val root = secureXmlFactory().newDocumentBuilder().parse(file).documentElement
        val nodes = root.getElementsByTagName("item")
        val items = (0 until nodes.length).map { nodes.item(it) as Element }
        val foreground = items.last()
        return LegacyLayerList(
            drawables = items.map { it.androidAttribute("drawable") },
            foregroundWidthDp = foreground.androidAttribute("width").removeSuffix("dp").toInt(),
            foregroundHeightDp = foreground.androidAttribute("height").removeSuffix("dp").toInt(),
        )
    }

    private fun parseAdaptiveIcon(file: File): AdaptiveIcon {
        val root = secureXmlFactory().newDocumentBuilder().parse(file).documentElement
        fun drawable(tag: String): String? =
            (root.getElementsByTagName(tag).item(0) as? Element)?.androidAttribute("drawable")
        return AdaptiveIcon(
            background = requireNotNull(drawable("background")),
            foreground = requireNotNull(drawable("foreground")),
            monochrome = drawable("monochrome"),
        )
    }

    private fun secureXmlFactory(): DocumentBuilderFactory = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
        setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        setFeature("http://xml.org/sax/features/external-general-entities", false)
        setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
        setXIncludeAware(false)
        isExpandEntityReferences = false
    }

    private fun Element.androidAttribute(name: String): String = getAttributeNS(ANDROID_NAMESPACE, name)

    private data class VectorFixture(
        val viewportWidth: Float,
        val viewportHeight: Float,
        val paths: List<VectorPath>,
    ) {
        val pathColors: Set<String> = paths
            .flatMap { listOf(it.fillColor, it.strokeColor) }
            .filter { HEX_COLOR.matches(it) }
            .map { it.uppercase() }
            .toSet()
    }

    private data class VectorPath(
        val pathData: String,
        val fillColor: String,
        val strokeColor: String,
        val strokeWidth: Float,
        val strokeLineCap: String,
        val strokeLineJoin: String,
    ) {
        val coordinates: List<Float> = NUMBER.findAll(pathData).map { it.value.toFloat() }.toList()
    }

    private data class LegacyLayerList(
        val drawables: List<String>,
        val foregroundWidthDp: Int,
        val foregroundHeightDp: Int,
    )

    private data class AdaptiveIcon(
        val background: String,
        val foreground: String,
        val monochrome: String?,
    )

    private companion object {
        const val ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"
        const val TRANSPARENT = "@android:color/transparent"
        const val MONOCHROME = "?android:attr/colorControlNormal"
        const val INK = "#0E2841"
        const val PRIMARY = "#156082"
        const val CYAN = "#0F9ED5"
        const val BIO_GREEN = "#196B24"
        const val WHITE = "#FFFFFF"
        const val SAFE_MIN = 18f
        const val SAFE_MAX = 82f
        const val BACKGROUND = "@drawable/ic_launcher_background"
        const val ROUND_BACKGROUND = "@drawable/ic_launcher_round_background"
        const val FOREGROUND = "@drawable/ic_launcher_joint_signal_foreground"
        const val MONOCHROME_DRAWABLE = "@drawable/jointsense_logo_monochrome"
        const val NUMBER_TOKEN = "-?(?:0|[1-9]\\d*)(?:\\.\\d+)?"
        const val PAIR_TOKEN = "$NUMBER_TOKEN,$NUMBER_TOKEN"
        val STRICT_PATH = Regex("^M$PAIR_TOKEN(?: C$PAIR_TOKEN $PAIR_TOKEN $PAIR_TOKEN)+(?: Z)?$")
        val APPROVED_BRAND_COLORS = setOf(INK, PRIMARY, CYAN, BIO_GREEN)
        val APPROVED_LAUNCHER_FOREGROUND_COLORS = setOf(WHITE, PRIMARY, CYAN, BIO_GREEN)
        val HEX_COLOR = Regex("#[0-9A-Fa-f]{6,8}")
        val NUMBER = Regex(NUMBER_TOKEN)
        val RASTER_EXTENSIONS = setOf("png", "jpg", "jpeg", "webp")
    }
}
