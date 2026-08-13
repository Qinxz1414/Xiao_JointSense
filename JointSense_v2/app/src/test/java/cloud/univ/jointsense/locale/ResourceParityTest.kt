package cloud.univ.jointsense.locale

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
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
        val settingsEn = resources("feature/settings", "values")
        val settingsZh = resources("feature/settings", "values-zh-rCN")

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
        listOf(
            "report_oa_week_change",
            "insights_ai_trend",
            "insights_ai_grade_summary",
            "insights_event_ai_value",
            "insights_event_ai_up",
            "insights_event_ai_down",
        ).forEach { key ->
            assertTrue("feature/insights/values:$key must use the approved OA term", insightsEn.text(key).contains(ENGLISH_OA_TERM))
            assertTrue("feature/insights/values-zh-rCN:$key must use the approved OA term", insightsZh.text(key).contains(CHINESE_OA_TERM))
        }
        assertEquals(ENGLISH_OA_TERM, settingsEn.text("settings_about_index_heading"))
        assertEquals(CHINESE_OA_TERM, settingsZh.text("settings_about_index_heading"))
        localizedModules.forEach { module ->
            listOf("values", "values-zh-rCN").forEach { directory ->
                resources(module, directory).forEach { (key, entry) ->
                    entry.items.forEach { (item, value) ->
                        DEPRECATED_OA_TERMS.forEach { deprecated ->
                            assertTrue(
                                "$module/$directory:$key[$item] contains deprecated OA terminology: ${deprecated.pattern}",
                                !deprecated.containsMatchIn(value),
                            )
                        }
                    }
                }
            }
        }
        assertEquals(ENGLISH_DISCLAIMER, insightsEn.text("report_disclaimer"))
        assertEquals(CHINESE_DISCLAIMER, insightsZh.text("report_disclaimer"))
        assertEquals(ENGLISH_DISCLAIMER, settingsEn.text("research_disclaimer"))
        assertEquals(CHINESE_DISCLAIMER, settingsZh.text("research_disclaimer"))
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
        assertTrue(navigation.contains("request(topLevelDestination, sessionNamePrefix)"))
        assertTrue(!navigation.contains("updateSessionNamePrefix"))
        assertTrue(!navigation.contains("context.getString(R.string.session_name_prefix)"))
        listOf(
            "feature/measurement/src/main/kotlin/cloud/univ/jointsense/measurement/MeasurementViewModel.kt",
            "feature/measurement/src/main/kotlin/cloud/univ/jointsense/measurement/MeasurementViewModelFactory.kt",
        ).forEach { path ->
            val source = File(projectRoot, path).readText()
            assertTrue("$path must not retain a localized prefix", !source.contains("currentSessionNamePrefix"))
            assertTrue("$path must not define a hardcoded Test prefix", !source.contains("sessionNamePrefix: String = \"Test\""))
        }
    }

    @Test
    fun `formatter parser models Java argument selection and conversion families`() {
        assertNotEquals(placeholders("%1\$tY"), placeholders("%1\$s"))
        assertEquals(
            listOf(FormatFamily.STRING, FormatFamily.STRING),
            placeholders("%1\$s %<S").arguments.getValue(1),
        )
        assertEquals(
            mapOf(
                1 to listOf(FormatFamily.STRING),
                2 to listOf(FormatFamily.INTEGER),
                3 to listOf(FormatFamily.FLOAT),
            ),
            placeholders("%s %d %.2f").arguments,
        )
        assertEquals(
            mapOf(
                1 to listOf(FormatFamily.STRING, FormatFamily.STRING),
                2 to listOf(FormatFamily.STRING),
            ),
            placeholders("%2\$s %s %<S").arguments,
        )
        assertEquals(FormatContract(emptyMap()), placeholders("100%% | %5% | %-5% | %n"))
        listOf(
            "%D", "%O", "%F", "%N",
            "%1\$n", "%5n", "%.2n",
            "%1\$%", "%.2%", "%+5%", "%0%", "%--5%",
        ).forEach { invalid ->
            try {
                placeholders(invalid)
                fail("Expected invalid Java Formatter token to be rejected: $invalid")
            } catch (_: IllegalArgumentException) {
                Unit
            }
        }
        assertEquals(
            FormatContract(mapOf(1 to listOf(FormatFamily.FLOAT))),
            placeholders("%1\$-08.2f"),
        )
        assertEquals(
            placeholders("%1\$s scored %2\$.2f"),
            placeholders("%2\$.2f / %1\$s"),
        )
        assertEquals(
            listOf(FormatFamily.STRING, FormatFamily.INTEGER),
            placeholders("%1\$d %1\$s").arguments.getValue(1),
        )
        assertNotEquals(placeholders("%1\$d %1\$s"), placeholders("%1\$d %1\$d"))
        assertEquals(
            listOf(FormatFamily.DATE_TIME, FormatFamily.DATE_TIME),
            placeholders("%1\$tY %1\$TY").arguments.getValue(1),
        )
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

    private fun placeholders(value: String): FormatContract {
        var nextImplicitIndex = 1
        var previousIndex: Int? = null
        var cursor = 0
        val arguments = mutableMapOf<Int, MutableList<FormatFamily>>()
        while (true) {
            val start = value.indexOf('%', cursor)
            if (start < 0) break
            val match = FORMAT.matchAt(value, start)
                ?: throw IllegalArgumentException("Unsupported Java Formatter token at offset $start in: $value")
            cursor = match.range.last + 1
            val explicitIndex = match.groupValues[1].takeIf(String::isNotEmpty)?.toInt()
            val flags = match.groupValues[2]
            val width = match.groupValues[3]
            val precision = match.groupValues[4]
            val dateTimePrefix = match.groupValues[5]
            val conversion = match.groupValues[6].single()
            if (dateTimePrefix.isEmpty() && conversion == 'n') {
                require(explicitIndex == null && flags.isEmpty() && width.isEmpty() && precision.isEmpty()) {
                    "Line-separator token cannot declare an index, flags, width, or precision: ${match.value}"
                }
                continue
            }
            if (dateTimePrefix.isEmpty() && conversion == '%') {
                require(explicitIndex == null && precision.isEmpty() && flags in setOf("", "-")) {
                    "Percent token only accepts an optional '-' flag and width: ${match.value}"
                }
                continue
            }

            val relative = '<' in flags
            require(!relative || explicitIndex == null) {
                "Formatter token cannot combine an explicit argument index with '<': ${match.value}"
            }
            val argumentIndex = when {
                relative -> requireNotNull(previousIndex) {
                    "Formatter token cannot use '<' before an argument: ${match.value}"
                }
                explicitIndex != null -> explicitIndex
                else -> nextImplicitIndex++
            }
            require(argumentIndex > 0) { "Formatter argument indices are one-based: ${match.value}" }
            previousIndex = argumentIndex
            val family = if (dateTimePrefix.isNotEmpty()) {
                require(conversion in DATE_TIME_CONVERSIONS) {
                    "Unsupported date/time conversion: ${match.value}"
                }
                FormatFamily.DATE_TIME
            } else {
                placeholderType(conversion)
            }
            arguments.getOrPut(argumentIndex, ::mutableListOf).add(family)
        }
        return FormatContract(
            arguments.toSortedMap().mapValues { (_, families) -> families.sortedBy(FormatFamily::ordinal) },
        )
    }

    private fun placeholderType(type: Char): FormatFamily = when (type) {
        's', 'S' -> FormatFamily.STRING
        'd', 'o', 'x', 'X' -> FormatFamily.INTEGER
        'e', 'E', 'f', 'g', 'G', 'a', 'A' -> FormatFamily.FLOAT
        'c', 'C' -> FormatFamily.CHARACTER
        'b', 'B' -> FormatFamily.BOOLEAN
        'h', 'H' -> FormatFamily.HASH
        else -> throw IllegalArgumentException("Unsupported Java Formatter conversion: $type")
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

    private data class FormatContract(val arguments: Map<Int, List<FormatFamily>>)

    private enum class FormatFamily { STRING, INTEGER, FLOAT, DATE_TIME, CHARACTER, BOOLEAN, HASH }

    private fun invariantScientificResources() = mapOf(
        "core/designsystem/src/main/res/values/strings.xml" to "I18N invariant scientific unit",
        "feature/measurement/src/main/res/values/strings.xml" to "I18N invariant scientific unit",
        "feature/insights/src/main/res/values/strings.xml" to "I18N invariant scientific notation",
    )

    private companion object {
        val FORMAT = Regex("""%(?:(\d+)\$)?([-#+ 0,(<]*)(\d*)(?:\.(\d+))?([tT]?)([a-zA-Z%])""")
        const val DATE_TIME_CONVERSIONS = "HIklMSLNpzZsQBbhAaCYyjmdeRTrDFc"
        val SIGNAL_TERMS = listOf("raw signal", "net signal", "fitted signal", "原始信号", "净信号", "拟合信号")
        val DEPRECATED_OA_TERMS = listOf(
            Regex("""\bOA index\b""", RegexOption.IGNORE_CASE),
            Regex("""\bAI index\b""", RegexOption.IGNORE_CASE),
            Regex("""OA inflammation index(?! \(AI\))""", RegexOption.IGNORE_CASE),
            Regex("""OA 炎症综合指数(?!（AI）)"""),
            Regex("""骨关节炎(?:炎症)?指数"""),
            Regex("""AI 指数"""),
        )
        const val ENGLISH_OA_TERM = "OA inflammation index (AI)"
        const val CHINESE_OA_TERM = "OA 炎症综合指数（AI）"
        const val ENGLISH_DISCLAIMER =
            "Results in this report are estimates derived from smartphone-photo colorimetry for research and longitudinal trend observation only. They are not intended for clinical diagnosis, treatment decisions, or as a substitute for validated laboratory testing."
        const val CHINESE_DISCLAIMER =
            "本报告结果基于手机照片色度代理估算，仅供科研与纵向趋势观察，不作为临床诊断、治疗决策或替代经验证实验室检测的依据。"
    }
}
