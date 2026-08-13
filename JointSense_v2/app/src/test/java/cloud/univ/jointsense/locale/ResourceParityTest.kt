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
    fun `english and simplified Chinese resource keys match in every UI module`() {
        localizedModules.forEach { module ->
            val english = resources(module, "values")
            val chinese = resources(module, "values-zh-rCN")

            assertEquals(
                "$module must expose exactly the same translatable keys in English and zh-rCN",
                english.keys,
                chinese.keys,
            )
        }
    }

    @Test
    fun `localized resources never contain empty values`() {
        localizedModules.forEach { module ->
            listOf("values", "values-zh-rCN").forEach { directory ->
                resources(module, directory).forEach { (key, value) ->
                    assertTrue("$module/$directory:$key must not be empty", value.isNotBlank())
                }
            }
        }
    }

    @Test
    fun `production compose code contains no direct user visible string literals`() {
        val directUiLiteralPatterns = listOf(
            Regex("""\bText\s*\(\s*(?:text\s*=\s*)?\""""),
            Regex("""\b(?:contentDescription|title|subtitle)\s*=\s*\""""),
            Regex("""\b(?:ErrorText|JointSenseTopBar)\s*\(\s*\""""),
            Regex("""\bToast\.makeText\([^,]+,\s*\""""),
        )
        val violations = productionKotlinFiles().flatMap { file ->
            file.readLines().mapIndexedNotNull { index, line ->
                if (directUiLiteralPatterns.any { it.containsMatchIn(line) }) {
                    "${file.relativeTo(projectRoot).invariantSeparatorsPath}:${index + 1}: ${line.trim()}"
                } else {
                    null
                }
            }
        }

        assertTrue(
            "Replace direct production UI literals with module-owned resources:\n${violations.joinToString("\n")}",
            violations.isEmpty(),
        )
    }

    @Test
    fun `professional Chinese terminology and approved disclaimer stay exact`() {
        val calibration = resources("feature/calibration", "values-zh-rCN")
        val measurement = resources("feature/measurement", "values-zh-rCN")
        val insights = resources("feature/insights", "values-zh-rCN")

        assertEquals("标准曲线校准", calibration.getValue("string:calibration_title"))
        assertTrue(calibration.getValue("string:calibration_error_missing_blank").contains("空白孔"))
        assertTrue(calibration.getValue("string:calibration_knot_summary").contains("原始信号"))
        assertTrue(calibration.getValue("string:calibration_knot_summary").contains("拟合信号"))
        assertEquals("白细胞介素-6", measurement.getValue("string:factor_il6_name"))
        assertEquals("肿瘤坏死因子 α", measurement.getValue("string:factor_tnf_alpha_name"))
        assertEquals("OA 炎症综合指数（AI）", insights.getValue("string:insights_oa_index"))
        assertEquals(CHINESE_DISCLAIMER, insights.getValue("string:report_disclaimer"))
    }

    private fun resources(module: String, directory: String): Map<String, String> {
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
                    check(put(key, element.textContent.trim()) == null) {
                        "Duplicate resource $key in $module/$directory"
                    }
                }
            }
        }
    }

    private fun productionKotlinFiles(): List<File> = localizedModules
        .flatMap { module ->
            File(projectRoot, "$module/src/main").walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .toList()
        }

    private companion object {
        const val CHINESE_DISCLAIMER =
            "本报告结果基于手机照片色度代理估算，仅供科研与纵向趋势观察，不作为临床诊断、治疗决策或替代经验证实验室检测的依据。"
    }
}
