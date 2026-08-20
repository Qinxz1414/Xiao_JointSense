package cloud.univ.jointsense.designsystem

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import org.w3c.dom.Node

class BrandResourceTest {

    @Test
    fun semanticVectorsHaveExactRootPathStructureGeometryAndColors() {
        validateSemanticVectorMatrix(
            resource("jointsense_logo.xml"),
            nightResource("jointsense_logo.xml"),
            resource("jointsense_logo_monochrome.xml"),
            appResource("drawable/ic_launcher_joint_signal_foreground.xml"),
        )
    }

    @Test
    fun coherentSameBoundsAndColorsSemanticGeometryDriftIsRejected() {
        val approvedRing = "M21,30 C21,25.029 25.029,21 30,21 C34.971,21 39,25.029 39,30 C39,34.971 34.971,39 30,39 C25.029,39 21,34.971 21,30 Z"
        val fakeSameBoundsRing = "M21,30 C21,21 21,21 30,21 C39,21 39,21 39,30 C39,39 39,39 30,39 C21,39 21,39 21,30 Z"
        val fakeFull = resource("jointsense_logo.xml").readText().replace(approvedRing, fakeSameBoundsRing)
        val fakeNight = nightResource("jointsense_logo.xml").readText().replace(approvedRing, fakeSameBoundsRing)
        val fakeMono = resource("jointsense_logo_monochrome.xml").readText().replace(approvedRing, fakeSameBoundsRing)
        val fakeForeground = appResource("drawable/ic_launcher_joint_signal_foreground.xml")
            .readText().replace(approvedRing, fakeSameBoundsRing)

        assertThrows(AssertionError::class.java) {
            validateSemanticVectorMatrix(
                tempXml(fakeFull),
                tempXml(fakeNight),
                tempXml(fakeMono),
                tempXml(fakeForeground),
            )
        }
    }

    @Test
    fun semanticAndBackgroundPathNodesRejectNestedRenderingElements() {
        listOf(
            resource("jointsense_logo.xml"),
            nightResource("jointsense_logo.xml"),
            resource("jointsense_logo_monochrome.xml"),
            appResource("drawable/ic_launcher_joint_signal_foreground.xml"),
        ).forEach { semantic ->
            nestedRenderingElements().forEach { nested ->
                assertThrows(AssertionError::class.java) {
                    parseSemanticVector(tempXml(nestInsideFirstPath(semantic.readText(), nested)))
                }
            }
        }

        listOf(
            appResource("drawable/ic_launcher_background.xml"),
            appResource("drawable/ic_launcher_round_background.xml"),
        ).forEach { background ->
            nestedRenderingElements().forEach { nested ->
                assertThrows(AssertionError::class.java) {
                    parseExactBackground(tempXml(nestInsideFirstPath(background.readText(), nested)))
                }
            }
        }

        val fakeSquare = appResource("drawable/ic_launcher_background.xml").readText()
            .replace(NORMAL_BACKGROUND_PATH, "M0,0 H108 V54 H54 V108 H0 Z")
        assertThrows(AssertionError::class.java) { validateNormalBackground(tempXml(fakeSquare)) }
    }

    private fun validateSemanticVectorMatrix(fullFile: File, nightFile: File, monoFile: File, foregroundFile: File) {
        val full = parseSemanticVector(fullFile)
        val night = parseSemanticVector(nightFile)
        val mono = parseSemanticVector(monoFile)
        val foreground = parseSemanticVector(foregroundFile)

        assertEquals(full.paths.map { it.pathData }, night.paths.map { it.pathData })
        assertEquals(full.paths.map { it.pathData }, mono.paths.map { it.pathData })
        assertEquals(full.paths.map { it.pathData }, foreground.paths.map { it.pathData })

        assertSemanticPath(full.paths[0], TRANSPARENT, INK, 4f)
        assertSemanticPath(full.paths[1], TRANSPARENT, INK, 4f)
        assertSemanticPath(full.paths[2], TRANSPARENT, PRIMARY, 7f)
        assertSemanticPath(full.paths[3], TRANSPARENT, CYAN, 7f)
        assertSemanticPath(full.paths[4], WELL_NEUTRAL, TRANSPARENT, 0f, fillAlpha = 0.24f)
        assertSemanticPath(full.paths[5], BIO_GREEN, INK, 2f)
        assertEquals(APPROVED_BRAND_COLORS, full.pathColors)

        assertSemanticPath(night.paths[0], TRANSPARENT, WHITE, 4f)
        assertSemanticPath(night.paths[1], TRANSPARENT, WHITE, 4f)
        assertSemanticPath(night.paths[2], TRANSPARENT, NIGHT_CYAN, 7f)
        assertSemanticPath(night.paths[3], TRANSPARENT, WHITE, 7f)
        assertSemanticPath(night.paths[4], WHITE, TRANSPARENT, 0f, fillAlpha = 0.20f)
        assertSemanticPath(night.paths[5], BIO_GREEN, WHITE, 2f)
        assertEquals(APPROVED_NIGHT_COLORS, night.pathColors)

        assertSemanticPath(mono.paths[0], TRANSPARENT, MONOCHROME, 4f)
        assertSemanticPath(mono.paths[1], TRANSPARENT, MONOCHROME, 4f)
        assertSemanticPath(mono.paths[2], TRANSPARENT, MONOCHROME, 7f)
        assertSemanticPath(mono.paths[3], TRANSPARENT, MONOCHROME, 7f)
        assertSemanticPath(mono.paths[4], TRANSPARENT, MONOCHROME, 3f)
        assertSemanticPath(mono.paths[5], MONOCHROME, MONOCHROME, 2f)
        assertTrue(mono.pathColors.isEmpty())

        assertSemanticPath(foreground.paths[0], TRANSPARENT, WHITE, 4f)
        assertSemanticPath(foreground.paths[1], TRANSPARENT, WHITE, 4f)
        assertSemanticPath(foreground.paths[2], TRANSPARENT, NIGHT_CYAN, 7f)
        assertSemanticPath(foreground.paths[3], TRANSPARENT, WHITE, 7f)
        assertSemanticPath(foreground.paths[4], WHITE, TRANSPARENT, 0f, fillAlpha = 0.20f)
        assertSemanticPath(foreground.paths[5], BIO_GREEN, WHITE, 2f)
        assertEquals(APPROVED_LAUNCHER_FOREGROUND_COLORS, foreground.pathColors)
    }

    @Test
    fun strictPathGrammarRejectsEveryNonAbsoluteOrMalformedAlternative() {
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
        listOf(
            resource("jointsense_logo.xml"),
            nightResource("jointsense_logo.xml"),
            resource("jointsense_logo_monochrome.xml"),
            appResource("drawable/ic_launcher_joint_signal_foreground.xml"),
        ).forEach { file ->
            parseSemanticVector(file).paths.forEachIndexed { index, path ->
                val x = path.coordinates.filterIndexed { coordinateIndex, _ -> coordinateIndex % 2 == 0 }
                val y = path.coordinates.filterIndexed { coordinateIndex, _ -> coordinateIndex % 2 == 1 }
                val radius = path.strokeWidth / 2f
                assertTrue("${file.name} path ${index + 1} visible left edge", x.min() - radius >= SAFE_MIN)
                assertTrue("${file.name} path ${index + 1} visible top edge", y.min() - radius >= SAFE_MIN)
                assertTrue("${file.name} path ${index + 1} visible right edge", x.max() + radius <= SAFE_MAX)
                assertTrue("${file.name} path ${index + 1} visible bottom edge", y.max() + radius <= SAFE_MAX)
            }
        }
    }

    @Test
    fun darkAndLauncherKeyOutlinesMeetNonTextContrast() {
        listOf(
            nightResource("jointsense_logo.xml") to DARK_PRIMARY_CONTAINER,
            appResource("drawable/ic_launcher_joint_signal_foreground.xml") to INK,
        ).forEach { (file, background) ->
            val paths = parseSemanticVector(file).paths
            KEY_SEMANTIC_PATH_INDICES.forEach { index ->
                val path = paths[index]
                val visibleColors = listOf(path.fillColor, path.strokeColor)
                    .filter { HEX_COLOR.matches(it) }
                val bestContrast = visibleColors.maxOf { contrastRatio(it, background) }
                assertTrue(
                    "${file.path} path ${index + 1} needs a visible outline with at least 3:1 contrast",
                    bestContrast >= MIN_GRAPHIC_CONTRAST,
                )
            }
        }
    }

    @Test
    fun legacyLayerListsHaveExactBackgroundAndCentered88DpForeground() {
        validateNormalBackground(appResource("drawable/ic_launcher_background.xml"))
        validateRoundBackground(appResource("drawable/ic_launcher_round_background.xml"))

        val normal = parseLegacyLayer(appResource("mipmap-anydpi/ic_launcher.xml"), BACKGROUND)
        val round = parseLegacyLayer(appResource("mipmap-anydpi/ic_launcher_round.xml"), ROUND_BACKGROUND)
        listOf(normal, round).forEach { layer ->
            assertEquals(LEGACY_FOREGROUND_SIZE_DP, layer.foregroundWidthDp)
            assertEquals(LEGACY_FOREGROUND_SIZE_DP, layer.foregroundHeightDp)
            assertEquals("center", layer.foregroundGravity)
            assertEquals(FOREGROUND, layer.foregroundDrawable)
        }
    }

    @Test
    fun adaptiveMatrixAndManifestUseOnlyApprovedLauncherResources() {
        val adaptive = parseAdaptiveIcon(appResource("mipmap-anydpi-v26/ic_launcher.xml"))
        val adaptiveRound = parseAdaptiveIcon(appResource("mipmap-anydpi-v26/ic_launcher_round.xml"))
        val themed = parseAdaptiveIcon(appResource("mipmap-anydpi-v33/ic_launcher.xml"))
        val themedRound = parseAdaptiveIcon(appResource("mipmap-anydpi-v33/ic_launcher_round.xml"))
        val manifestFile = File("../../app/src/main/AndroidManifest.xml")

        val expectedAdaptive = AdaptiveIcon(BACKGROUND, FOREGROUND, MONOCHROME_DRAWABLE)
        assertEquals(expectedAdaptive, adaptive)
        assertEquals(expectedAdaptive, adaptiveRound)
        val expectedThemed = AdaptiveIcon(BACKGROUND, FOREGROUND, MONOCHROME_DRAWABLE)
        assertEquals(expectedThemed, themed)
        assertEquals(expectedThemed, themedRound)
        assertEquals(
            ManifestLaunchers("@mipmap/ic_launcher", "@mipmap/ic_launcher_round"),
            parseManifestLaunchers(manifestFile),
        )
    }

    @Test
    fun manifestTextDecoysCannotSatisfyLauncherContract() {
        val fakeManifest = File("../../app/src/main/AndroidManifest.xml").readText()
            .replace("android:icon=\"@mipmap/ic_launcher\"", "android:icon=\"@drawable/not_launcher\"")
            .replace(
                "<application",
                "<!-- android:icon=\"@mipmap/ic_launcher\" android:roundIcon=\"@mipmap/ic_launcher_round\" -->\n    <application",
            )
        assertFalse(
            parseManifestLaunchers(tempXml(fakeManifest)) ==
                ManifestLaunchers("@mipmap/ic_launcher", "@mipmap/ic_launcher_round"),
        )
    }

    @Test
    fun transformedClippedTintedOrTrimmedSemanticVectorsAreRejected() {
        val valid = resource("jointsense_logo.xml").readText()
        val transformed = valid
            .replaceFirst("<path", "<group android:scaleX=\"0.5\">\n    <path")
            .replace("</vector>", "</group>\n</vector>")
        val clipped = valid.replaceFirst(
            "<path",
            "<clip-path android:pathData=\"M20,20 C20,20 80,80 80,80 Z\" />\n    <path",
        )
        val tinted = valid.replaceFirst(
            "android:width=\"100dp\"",
            "android:tint=\"#FFFFFF\"\n    android:width=\"100dp\"",
        )
        val hidden = valid.replaceFirst(
            "android:height=\"100dp\"",
            "android:alpha=\"0\"\n    android:height=\"100dp\"",
        )
        val mirrored = valid.replaceFirst(
            "android:height=\"100dp\"",
            "android:autoMirrored=\"true\"\n    android:height=\"100dp\"",
        )
        val changedViewport = valid.replace("android:viewportWidth=\"100\"", "android:viewportWidth=\"101\"")
        val trimmed = valid.replaceFirst(
            "android:strokeWidth=\"4\"",
            "android:strokeWidth=\"4\"\n        android:trimPathStart=\"0.5\"",
        )
        val translucentFill = valid.replaceFirst(
            "android:fillAlpha=\"1\"",
            "android:fillAlpha=\"0\"",
        )
        val faded = valid.replaceFirst(
            "android:strokeWidth=\"4\"",
            "android:strokeWidth=\"4\"\n        android:strokeAlpha=\"0\"",
        )
        listOf(
            transformed,
            clipped,
            tinted,
            hidden,
            mirrored,
            changedViewport,
            trimmed,
            translucentFill,
            faded,
        ).forEach { xml ->
            assertThrows(AssertionError::class.java) { parseSemanticVector(tempXml(xml)) }
        }
    }

    @Test
    fun shiftedLegacyLayerAndFakeRoundBackgroundAreRejected() {
        val validLayer = appResource("mipmap-anydpi/ic_launcher_round.xml").readText()
        val shiftedGravity = validLayer.replace("android:gravity=\"center\"", "android:gravity=\"top\"")
        val offset = validLayer.replace(
            "android:gravity=\"center\"",
            "android:gravity=\"center\"\n        android:left=\"1dp\"",
        )
        val wrongSize = validLayer.replace(Regex("android:width=\"\\d+dp\""), "android:width=\"87dp\"")
        listOf(shiftedGravity, offset, wrongSize).forEach { xml ->
            assertThrows(AssertionError::class.java) { parseLegacyLayer(tempXml(xml), ROUND_BACKGROUND) }
        }

        val fakeCircle = appResource("drawable/ic_launcher_round_background.xml").readText()
            .replace("C82.719,2", "C80,2")
        assertThrows(AssertionError::class.java) { validateRoundBackground(tempXml(fakeCircle)) }
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
        val malicious = """<!DOCTYPE vector [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
            |<vector xmlns:android="$ANDROID_NAMESPACE"
            | android:width="100dp" android:height="100dp"
            | android:viewportWidth="100" android:viewportHeight="100">&xxe;</vector>
        """.trimMargin()
        assertThrows(Exception::class.java) { parseSemanticVector(tempXml(malicious)) }
    }

    private fun parseSemanticVector(file: File): VectorFixture {
        val root = parseRoot(file)
        assertEquals("vector", root.tagName)
        assertExactAndroidAttributes(root, VECTOR_ROOT_ATTRIBUTES, allowAndroidNamespace = true)
        assertEquals("100dp", root.androidAttribute("width"))
        assertEquals("100dp", root.androidAttribute("height"))
        assertEquals("100", root.androidAttribute("viewportWidth"))
        assertEquals("100", root.androidAttribute("viewportHeight"))
        val children = root.directElementChildren()
        assertEquals("semantic vector must have six direct paths", 6, children.size)
        children.forEach { child ->
            assertEquals("path", child.tagName)
            assertExactAndroidAttributes(child, SEMANTIC_PATH_ATTRIBUTES)
            assertTrue("semantic paths must not contain element descendants", child.directElementChildren().isEmpty())
            assertTrue(
                "semantic paths must remain visibly filled when a fill is present",
                child.androidAttribute("fillAlpha").toFloat() in 0f..1f &&
                    child.androidAttribute("fillAlpha").toFloat() > 0f,
            )
            assertTrue("Path must use absolute M/C/Z grammar", STRICT_PATH.matches(child.androidAttribute("pathData")))
        }
        assertEquals(
            "semantic paths must preserve the approved geometry and order",
            APPROVED_SEMANTIC_PATHS,
            children.map { it.androidAttribute("pathData") },
        )
        return root.toVectorFixture(children)
    }

    private fun validateNormalBackground(file: File) {
        val vector = parseExactBackground(file)
        assertEquals(NORMAL_BACKGROUND_PATH, vector.paths.single().pathData)
    }

    private fun validateRoundBackground(file: File) {
        val vector = parseExactBackground(file)
        assertEquals(ROUND_BACKGROUND_PATH, vector.paths.single().pathData)
    }

    private fun parseExactBackground(file: File): VectorFixture {
        val root = parseRoot(file)
        assertEquals("vector", root.tagName)
        assertExactAndroidAttributes(root, VECTOR_ROOT_ATTRIBUTES, allowAndroidNamespace = true)
        assertEquals("108dp", root.androidAttribute("width"))
        assertEquals("108dp", root.androidAttribute("height"))
        assertEquals("108", root.androidAttribute("viewportWidth"))
        assertEquals("108", root.androidAttribute("viewportHeight"))
        val children = root.directElementChildren()
        assertEquals("background vector must have one direct path", 1, children.size)
        val path = children.single()
        assertEquals("path", path.tagName)
        assertExactAndroidAttributes(path, BACKGROUND_PATH_ATTRIBUTES)
        assertTrue("background path must not contain element descendants", path.directElementChildren().isEmpty())
        assertEquals(INK, path.androidAttribute("fillColor"))
        return root.toVectorFixture(children)
    }

    private fun parseLegacyLayer(file: File, expectedBackground: String): LegacyLayerList {
        val root = parseRoot(file)
        assertEquals("layer-list", root.tagName)
        assertExactAndroidAttributes(root, emptySet(), allowAndroidNamespace = true)
        val items = root.directElementChildren()
        assertEquals("legacy icon must have two direct items", 2, items.size)
        items.forEach { item ->
            assertEquals("item", item.tagName)
            assertTrue("layer item must not contain nested elements", item.directElementChildren().isEmpty())
        }
        val background = items[0]
        assertExactAndroidAttributes(background, setOf("drawable"))
        assertEquals(expectedBackground, background.androidAttribute("drawable"))
        val foreground = items[1]
        assertExactAndroidAttributes(foreground, LEGACY_FOREGROUND_ATTRIBUTES)
        val width = foreground.androidAttribute("width").removeSuffix("dp").toInt()
        val height = foreground.androidAttribute("height").removeSuffix("dp").toInt()
        assertEquals(LEGACY_FOREGROUND_SIZE_DP, width)
        assertEquals(LEGACY_FOREGROUND_SIZE_DP, height)
        assertEquals("center", foreground.androidAttribute("gravity"))
        assertEquals(FOREGROUND, foreground.androidAttribute("drawable"))
        return LegacyLayerList(width, height, foreground.androidAttribute("gravity"), foreground.androidAttribute("drawable"))
    }

    private fun parseAdaptiveIcon(file: File): AdaptiveIcon {
        val root = parseRoot(file)
        assertEquals("adaptive-icon", root.tagName)
        assertExactAndroidAttributes(root, emptySet(), allowAndroidNamespace = true)
        val children = root.directElementChildren()
        val tags = children.map { it.tagName }
        assertTrue(tags == listOf("background", "foreground") || tags == listOf("background", "foreground", "monochrome"))
        children.forEach { child ->
            assertExactAndroidAttributes(child, setOf("drawable"))
            assertTrue(child.directElementChildren().isEmpty())
        }
        fun drawable(tag: String): String? = children.singleOrNull { it.tagName == tag }?.androidAttribute("drawable")
        return AdaptiveIcon(
            background = requireNotNull(drawable("background")),
            foreground = requireNotNull(drawable("foreground")),
            monochrome = drawable("monochrome"),
        )
    }

    private fun parseManifestLaunchers(file: File): ManifestLaunchers {
        val root = parseRoot(file)
        assertEquals("manifest", root.tagName)
        val applications = root.directElementChildren().filter { it.tagName == "application" }
        assertEquals("manifest must have exactly one direct application", 1, applications.size)
        val application = applications.single()
        return ManifestLaunchers(
            icon = application.androidAttribute("icon"),
            roundIcon = application.androidAttribute("roundIcon"),
        )
    }

    private fun nestedRenderingElements(): List<String> = listOf(
        "<group />",
        "<clip-path android:pathData=\"M20,20 C20,20 80,80 80,80 Z\" />",
        "<path android:fillColor=\"#FFFFFF\" android:pathData=\"M20,20 C20,20 80,80 80,80 Z\" />",
    )

    private fun nestInsideFirstPath(xml: String, nested: String): String =
        xml.replaceFirst(" />", ">\n        $nested\n    </path>")

    private fun Element.toVectorFixture(pathElements: List<Element>): VectorFixture = VectorFixture(
        viewportWidth = androidAttribute("viewportWidth").toFloat(),
        viewportHeight = androidAttribute("viewportHeight").toFloat(),
        paths = pathElements.map { element ->
            VectorPath(
                pathData = element.androidAttribute("pathData"),
                fillColor = element.androidAttribute("fillColor"),
                fillAlpha = element.androidAttribute("fillAlpha").ifBlank { "1" }.toFloat(),
                strokeColor = element.androidAttribute("strokeColor"),
                strokeWidth = element.androidAttribute("strokeWidth").toFloatOrNull() ?: 0f,
                strokeLineCap = element.androidAttribute("strokeLineCap"),
                strokeLineJoin = element.androidAttribute("strokeLineJoin"),
            )
        },
    )

    private fun assertSemanticPath(
        path: VectorPath,
        fill: String,
        stroke: String,
        width: Float,
        fillAlpha: Float = 1f,
    ) {
        assertEquals(fill, path.fillColor)
        assertEquals(fillAlpha, path.fillAlpha)
        assertEquals(stroke, path.strokeColor)
        assertEquals(width, path.strokeWidth)
        assertEquals("round", path.strokeLineCap)
        assertEquals("round", path.strokeLineJoin)
    }

    private fun contrastRatio(foreground: String, background: String): Double {
        val lighter = maxOf(relativeLuminance(foreground), relativeLuminance(background))
        val darker = minOf(relativeLuminance(foreground), relativeLuminance(background))
        return (lighter + 0.05) / (darker + 0.05)
    }

    private fun relativeLuminance(color: String): Double {
        val channels = listOf(1, 3, 5).map { start ->
            val encoded = color.substring(start, start + 2).toInt(16) / 255.0
            if (encoded <= 0.04045) encoded / 12.92 else Math.pow((encoded + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * channels[0] + 0.7152 * channels[1] + 0.0722 * channels[2]
    }

    private fun assertExactAndroidAttributes(
        element: Element,
        expected: Set<String>,
        allowAndroidNamespace: Boolean = false,
    ) {
        val android = mutableMapOf<String, String>()
        val unexpected = mutableListOf<String>()
        val attributes = element.attributes
        for (index in 0 until attributes.length) {
            val attribute = attributes.item(index)
            when (attribute.namespaceURI) {
                ANDROID_NAMESPACE -> android[attribute.localName] = attribute.nodeValue
                XMLNS_NAMESPACE -> {
                    val approved = allowAndroidNamespace &&
                        attribute.localName == "android" && attribute.nodeValue == ANDROID_NAMESPACE
                    if (!approved) unexpected += attribute.nodeName
                }
                else -> unexpected += attribute.nodeName
            }
        }
        assertTrue("${element.tagName} has undeclared attributes: $unexpected", unexpected.isEmpty())
        assertEquals("${element.tagName} Android attribute whitelist", expected, android.keys)
    }

    private fun Element.directElementChildren(): List<Element> =
        (0 until childNodes.length)
            .map { childNodes.item(it) }
            .filter { it.nodeType == Node.ELEMENT_NODE }
            .map { it as Element }

    private fun parseRoot(file: File): Element = secureXmlFactory().newDocumentBuilder().parse(file).documentElement

    private fun secureXmlFactory(): DocumentBuilderFactory = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
        setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        setFeature("http://xml.org/sax/features/external-general-entities", false)
        setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
        setXIncludeAware(false)
        isExpandEntityReferences = false
    }

    private fun resource(name: String): File = File("src/main/res/drawable/$name").requireFile()
    private fun nightResource(name: String): File = File("src/main/res/drawable-night/$name").requireFile()
    private fun appResource(path: String): File = File("../../app/src/main/res/$path").requireFile()
    private fun File.requireFile(): File = also { assertTrue("Missing brand resource: ${it.path}", it.isFile) }
    private fun tempXml(xml: String): File = File.createTempFile("jointsense-brand-contract", ".xml").apply {
        writeText(xml)
        deleteOnExit()
    }
    private fun Element.androidAttribute(name: String): String = getAttributeNS(ANDROID_NAMESPACE, name)

    private data class VectorFixture(
        val viewportWidth: Float,
        val viewportHeight: Float,
        val paths: List<VectorPath>,
    ) {
        val pathColors: Set<String> = paths.flatMap { listOf(it.fillColor, it.strokeColor) }
            .filter { HEX_COLOR.matches(it) }.map { it.uppercase() }.toSet()
    }

    private data class VectorPath(
        val pathData: String,
        val fillColor: String,
        val fillAlpha: Float,
        val strokeColor: String,
        val strokeWidth: Float,
        val strokeLineCap: String,
        val strokeLineJoin: String,
    ) {
        val coordinates: List<Float> = NUMBER.findAll(pathData).map { it.value.toFloat() }.toList()
    }

    private data class LegacyLayerList(
        val foregroundWidthDp: Int,
        val foregroundHeightDp: Int,
        val foregroundGravity: String,
        val foregroundDrawable: String,
    )

    private data class AdaptiveIcon(val background: String, val foreground: String, val monochrome: String?)

    private data class ManifestLaunchers(val icon: String, val roundIcon: String)

    private companion object {
        const val ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"
        const val XMLNS_NAMESPACE = "http://www.w3.org/2000/xmlns/"
        const val TRANSPARENT = "@android:color/transparent"
        const val MONOCHROME = "?android:attr/colorControlNormal"
        const val INK = "#0E2841"
        const val PRIMARY = "#156082"
        const val CYAN = "#0F9ED5"
        const val BIO_GREEN = "#196B24"
        const val WELL_NEUTRAL = "#7B9694"
        const val WHITE = "#FFFFFF"
        const val NIGHT_CYAN = "#8BD8F4"
        const val DARK_PRIMARY_CONTAINER = "#114B63"
        const val MIN_GRAPHIC_CONTRAST = 3.0
        const val SAFE_MIN = 18f
        const val SAFE_MAX = 82f
        const val LEGACY_FOREGROUND_SIZE_DP = 88
        const val BACKGROUND = "@drawable/ic_launcher_background"
        const val ROUND_BACKGROUND = "@drawable/ic_launcher_round_background"
        const val FOREGROUND = "@drawable/ic_launcher_joint_signal_foreground"
        const val MONOCHROME_DRAWABLE = "@drawable/jointsense_logo_monochrome"
        const val NORMAL_BACKGROUND_PATH = "M0,0 H108 V108 H0 Z"
        const val ROUND_BACKGROUND_PATH = "M54,2 C82.719,2 106,25.281 106,54 C106,82.719 82.719,106 54,106 C25.281,106 2,82.719 2,54 C2,25.281 25.281,2 54,2 Z"
        val APPROVED_SEMANTIC_PATHS = listOf(
            "M21,30 C21,25.029 25.029,21 30,21 C34.971,21 39,25.029 39,30 C39,34.971 34.971,39 30,39 C25.029,39 21,34.971 21,30 Z",
            "M61,70 C61,65.029 65.029,61 70,61 C74.971,61 79,65.029 79,70 C79,74.971 74.971,79 70,79 C65.029,79 61,74.971 61,70 Z",
            "M37,29 C44,23 54,22 63,26 C69,28 73,32 75,36",
            "M63,71 C56,77 46,78 37,74 C31,72 27,68 25,64",
            "M36,50 C36,42.268 42.268,36 50,36 C57.732,36 64,42.268 64,50 C64,57.732 57.732,64 50,64 C42.268,64 36,57.732 36,50 Z",
            "M43,50 C43,46.134 46.134,43 50,43 C53.866,43 57,46.134 57,50 C57,53.866 53.866,57 50,57 C46.134,57 43,53.866 43,50 Z",
        )
        const val NUMBER_TOKEN = "-?(?:0|[1-9]\\d*)(?:\\.\\d+)?"
        const val PAIR_TOKEN = "$NUMBER_TOKEN,$NUMBER_TOKEN"
        val STRICT_PATH = Regex("^M$PAIR_TOKEN(?: C$PAIR_TOKEN $PAIR_TOKEN $PAIR_TOKEN)+(?: Z)?$")
        val VECTOR_ROOT_ATTRIBUTES = setOf("width", "height", "viewportWidth", "viewportHeight")
        val SEMANTIC_PATH_ATTRIBUTES = setOf(
            "fillAlpha", "fillColor", "pathData", "strokeColor", "strokeLineCap", "strokeLineJoin", "strokeWidth",
        )
        val BACKGROUND_PATH_ATTRIBUTES = setOf("fillColor", "pathData")
        val LEGACY_FOREGROUND_ATTRIBUTES = setOf("width", "height", "drawable", "gravity")
        val APPROVED_BRAND_COLORS = setOf(INK, PRIMARY, CYAN, BIO_GREEN, WELL_NEUTRAL)
        val APPROVED_NIGHT_COLORS = setOf(WHITE, NIGHT_CYAN, BIO_GREEN)
        val APPROVED_LAUNCHER_FOREGROUND_COLORS = APPROVED_NIGHT_COLORS
        val KEY_SEMANTIC_PATH_INDICES = listOf(0, 1, 2, 3, 5)
        val HEX_COLOR = Regex("#[0-9A-Fa-f]{6,8}")
        val NUMBER = Regex(NUMBER_TOKEN)
        val RASTER_EXTENSIONS = setOf("png", "jpg", "jpeg", "webp")
    }
}
