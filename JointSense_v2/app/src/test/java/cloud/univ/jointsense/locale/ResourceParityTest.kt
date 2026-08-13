package cloud.univ.jointsense.locale

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

class ResourceParityTest {
    private val projectRoot = generateSequence(File(requireNotNull(System.getProperty("user.dir")))) { it.parentFile }
        .first { File(it, "settings.gradle.kts").isFile }

    private val localizedModules = listOf(
        "app",
        "core/designsystem",
        "feature/insights",
        "feature/measurement",
        "feature/calibration",
        "feature/settings",
    )

    @Test
    fun `english and simplified Chinese resources preserve keys items plurals and format contracts`() {
        localizedModules.forEach { module ->
            val english = resources(module, "values")
            val chinese = resources(module, "values-zh-rCN")
            assertEquals(
                "$module must expose exactly the same translatable keys in English and zh-rCN",
                english.keys,
                chinese.keys,
            )
            english.forEach { (key, englishEntry) ->
                val chineseEntry = chinese.getValue(key)
                assertEquals("$module:$key resource types must match", englishEntry.kind, chineseEntry.kind)
                when (englishEntry.kind) {
                    "string" -> compareItem(module, key, "value", englishEntry, chineseEntry)
                    "string-array" -> {
                        assertEquals("$module:$key array size must match", englishEntry.items.keys, chineseEntry.items.keys)
                        englishEntry.items.keys.forEach { compareItem(module, key, it, englishEntry, chineseEntry) }
                    }
                    "plurals" -> {
                        assertTrue("$module/values:$key requires plural other", "other" in englishEntry.items)
                        assertTrue("$module/values-zh-rCN:$key requires plural other", "other" in chineseEntry.items)
                        (englishEntry.items.keys intersect chineseEntry.items.keys).forEach {
                            compareItem(module, key, it, englishEntry, chineseEntry)
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `every localized string plural and array item is nonempty`() {
        localizedModules.forEach { module ->
            listOf("values", "values-zh-rCN").forEach { directory ->
                resources(module, directory).forEach { (key, entry) ->
                    assertTrue("$module/$directory:$key must contain items", entry.items.isNotEmpty())
                    entry.items.forEach { (item, value) ->
                        assertTrue("$module/$directory:$key[$item] must not be empty", value.isNotBlank())
                    }
                }
            }
        }
    }

    @Test
    fun `all production Kotlin contains no direct or documented indirect visible literals`() {
        val directSinkPatterns = listOf(
            Regex("""\bText\s*\(\s*(?:text\s*=\s*)?\""""),
            Regex("""\b(?:contentDescription|title|subtitle|label|supportingText|stateDescription|actionLabel|chooserTitle)\s*=\s*\""""),
            Regex("""\b(?:ErrorText|JointSenseTopBar)\s*\(\s*\""""),
            Regex("""\bToast\.makeText\([^,]+,\s*\""""),
        )
        val violations = productionKotlinFiles().flatMap { file ->
            val source = file.readText()
            buildList {
                directSinkPatterns.forEach { pattern ->
                    pattern.findAll(source).forEach { match -> add(violation(file, source, match.range.first)) }
                }
                val relative = file.relativeTo(projectRoot).invariantSeparatorsPath
                if (relative.endsWith("core/data/src/main/kotlin/cloud/univ/jointsense/data/BuiltInSampleProvider.kt")) {
                    Regex("""\bname\s*=\s*\"(?:Clipboard|Test)\s+Plate""").findAll(source).forEach {
                        add(violation(file, source, it.range.first))
                    }
                }
            }
        }.distinct()
        assertTrue(
            "Replace direct and indirect production display literals with resource-backed presentation:\n${violations.joinToString("\n")}",
            violations.isEmpty(),
        )
    }

    @Test
    fun `approved terminology and both disclaimers stay exact`() {
        val calibrationEn = resources("feature/calibration", "values")
        val calibrationZh = resources("feature/calibration", "values-zh-rCN")
        val measurementEn = resources("feature/measurement", "values")
        val measurementZh = resources("feature/measurement", "values-zh-rCN")
        val insightsEn = resources("feature/insights", "values")
        val insightsZh = resources("feature/insights", "values-zh-rCN")

        assertEquals("Standard curve calibration", calibrationEn.text("calibration_title"))
        assertEquals("标准曲线校准", calibrationZh.text("calibration_title"))
        assertTrue(calibrationZh.text("calibration_error_missing_blank").contains("空白孔"))
        assertEquals(setOf("raw signal", "net signal", "fitted signal"), signalTerms(calibrationEn.text("calibration_knot_summary")))
        assertEquals(setOf("原始信号", "净信号", "拟合信号"), signalTerms(calibrationZh.text("calibration_knot_summary")))
        assertEquals("Interleukin-6", measurementEn.text("factor_il6_name"))
        assertEquals("白细胞介素-6", measurementZh.text("factor_il6_name"))
        assertEquals("Tumor necrosis factor α", measurementEn.text("factor_tnf_alpha_name"))
        assertEquals("肿瘤坏死因子 α", measurementZh.text("factor_tnf_alpha_name"))
        assertEquals(ENGLISH_OA_TERM, measurementEn.text("measurement_oa_index"))
        assertEquals(CHINESE_OA_TERM, measurementZh.text("measurement_oa_index"))
        assertEquals("Test plate %1\$d", measurementEn.text("measurement_builtin_test_plate"))
        assertEquals("检测板 %1\$d", measurementZh.text("measurement_builtin_test_plate"))
        assertEquals("Clipboard sample %1\$d", measurementEn.text("measurement_builtin_clipboard_plate"))
        assertEquals("剪贴板样例 %1\$d", measurementZh.text("measurement_builtin_clipboard_plate"))
        assertEquals(ENGLISH_OA_TERM, insightsEn.text("insights_oa_index"))
        assertEquals(CHINESE_OA_TERM, insightsZh.text("insights_oa_index"))
        assertEquals(ENGLISH_OA_TERM, insightsEn.text("report_oa_index"))
        assertEquals(CHINESE_OA_TERM, insightsZh.text("report_oa_index"))
        assertEquals(ENGLISH_DISCLAIMER, insightsEn.text("report_disclaimer"))
        assertEquals(CHINESE_DISCLAIMER, insightsZh.text("report_disclaimer"))
        invariantScientificResources().forEach { (path, declaration) ->
            assertTrue("$path must document invariant scientific notation", File(projectRoot, path).readText().contains(declaration))
        }
        val calibrationScreen = File(
            projectRoot,
            "feature/calibration/src/main/kotlin/cloud/univ/jointsense/calibration/CalibrationScreens.kt",
        ).readText()
        assertTrue(calibrationScreen.contains("knot.netSignal"))
        assertTrue(calibrationScreen.contains("stringResource(\n                        R.string.calibration_knot_summary"))
        val navigation = File(
            projectRoot,
            "app/src/main/java/cloud/univ/jointsense/navigation/JointSenseNavHost.kt",
        ).readText()
        assertTrue(navigation.contains("updateSessionNamePrefix(sessionNamePrefix)"))
        assertTrue(!navigation.contains("context.getString(R.string.session_name_prefix)"))
    }

    private fun compareItem(
        module: String,
        key: String,
        item: String,
        english: ResourceEntry,
        chinese: ResourceEntry,
    ) {
        assertEquals(
            "$module:$key[$item] placeholder indices and types must match",
            placeholders(english.items.getValue(item)),
            placeholders(chinese.items.getValue(item)),
        )
    }

    private fun resources(module: String, directory: String): Map<String, ResourceEntry> {
        val resourceDirectory = File(projectRoot, "$module/src/main/res/$directory")
        assertTrue("Missing localized resource directory: ${resourceDirectory.path}", resourceDirectory.isDirectory)
        val xmlFiles = resourceDirectory.listFiles { file -> file.isFile && file.extension == "xml" }
            ?.sortedBy(File::getName)
            .orEmpty()
        assertTrue("No XML resources in ${resourceDirectory.path}", xmlFiles.isNotEmpty())

        return buildMap {
            xmlFiles.forEach { file ->
                val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
                val children = document.documentElement.childNodes
                for (index in 0 until children.length) {
                    val element = children.item(index) as? Element ?: continue
                    if (element.getAttribute("translatable") == "false") continue
                    if (element.tagName !in setOf("string", "plurals", "string-array")) continue
                    val name = element.getAttribute("name")
                    assertTrue("Unnamed resource in ${file.path}", name.isNotBlank())
                    val key = "${element.tagName}:$name"
                    val entry = when (element.tagName) {
                        "string" -> ResourceEntry(element.tagName, mapOf("value" to element.textContent.trim()))
                        "plurals" -> ResourceEntry(element.tagName, childItems(element, "quantity"))
                        else -> ResourceEntry(element.tagName, childItems(element, null))
                    }
                    check(put(key, entry) == null) { "Duplicate resource $key in $module/$directory" }
                }
            }
        }
    }

    private fun childItems(element: Element, attribute: String?): Map<String, String> = buildMap {
        val children = element.childNodes
        var itemIndex = 0
        for (index in 0 until children.length) {
            val item = children.item(index) as? Element ?: continue
            if (item.tagName != "item") continue
            val key = attribute?.let(item::getAttribute) ?: (itemIndex++).toString()
            check(put(key, item.textContent.trim()) == null) { "Duplicate item $key in ${element.getAttribute("name")}" }
        }
    }

    private fun placeholders(value: String): Map<Int, String> {
        var implicitIndex = 1
        return FORMAT.findAll(value).filter { it.groupValues[2] != "%" }.associate { match ->
            val index = match.groupValues[1].takeIf(String::isNotEmpty)?.toInt() ?: implicitIndex++
            index to placeholderType(match.groupValues[2].single())
        }
    }

    private fun placeholderType(type: Char): String = when (type.lowercaseChar()) {
        'd', 'o', 'x' -> "integer"
        'e', 'f', 'g', 'a' -> "float"
        'c' -> "character"
        'b' -> "boolean"
        'h' -> "hash"
        else -> "string"
    }

    private fun productionKotlinFiles(): List<File> = listOf("app", "core", "feature")
        .flatMap { root ->
            File(projectRoot, root).walkTopDown()
                .onEnter { it.name !in setOf("build", "generated", "test", "androidTest") }
                .filter { it.isFile && it.extension == "kt" && "src${File.separator}main" in it.path }
                .toList()
        }

    private fun violation(file: File, source: String, offset: Int): String {
        val line = source.take(offset).count { it == '\n' } + 1
        return "${file.relativeTo(projectRoot).invariantSeparatorsPath}:$line"
    }

    private fun Map<String, ResourceEntry>.text(name: String): String = getValue("string:$name").items.getValue("value")

    private fun signalTerms(value: String): Set<String> = SIGNAL_TERMS.filter(value::contains).toSet()

    private data class ResourceEntry(val kind: String, val items: Map<String, String>)

    private fun invariantScientificResources() = mapOf(
        "core/designsystem/src/main/res/values/strings.xml" to "I18N invariant scientific unit",
        "feature/measurement/src/main/res/values/strings.xml" to "I18N invariant scientific unit",
        "feature/insights/src/main/res/values/strings.xml" to "I18N invariant scientific notation",
    )

    private companion object {
        val FORMAT = Regex("""%(?:(\d+)\$)?[-#+ 0,(<]*\d*(?:\.\d+)?([a-zA-Z%])""")
        val SIGNAL_TERMS = listOf("raw signal", "net signal", "fitted signal", "原始信号", "净信号", "拟合信号")
        const val ENGLISH_OA_TERM = "OA inflammation index (AI)"
        const val CHINESE_OA_TERM = "OA 炎症综合指数（AI）"
        const val ENGLISH_DISCLAIMER =
            "Results in this report are estimates derived from smartphone-photo colorimetry for research and longitudinal trend observation only. They are not intended for clinical diagnosis, treatment decisions, or as a substitute for validated laboratory testing."
        const val CHINESE_DISCLAIMER =
            "本报告结果基于手机照片色度代理估算，仅供科研与纵向趋势观察，不作为临床诊断、治疗决策或替代经验证实验室检测的依据。"
    }
}
