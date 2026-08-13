package cloud.univ.jointsense.designsystem

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductionColorRoleAuditTest {
    @Test
    fun genericProductionCallersUseMaterialRoles() {
        val projectRoot = File("../..").canonicalFile
        val forbiddenFixedTokens = Regex(
            "\\bAiLine\\b|JointSenseColors\\.Primary|" +
                "designsystem\\.theme\\.(PrimaryAccent|BioGreen|TnfRed|StructureGray|AiLine)",
        )
        val offenders = productionKotlinFiles(projectRoot).mapNotNull { file ->
            if (forbiddenFixedTokens.containsMatchIn(file.readText())) file.relativeTo(projectRoot).path else null
        }.toList()

        assertTrue("Fixed generic color tokens: $offenders", offenders.isEmpty())
    }

    @Test
    fun whiteIsLimitedToDocumentedImageCropOverlays() {
        val projectRoot = File("../..").canonicalFile
        // White strokes/grids are required to keep crop controls visible over arbitrary photos.
        val allowed = setOf(
            "feature/calibration/src/main/kotlin/cloud/univ/jointsense/calibration/CalibrationCropView.kt",
            "feature/measurement/src/main/kotlin/cloud/univ/jointsense/measurement/crop/ImageCropView.kt",
        )
        val offenders = productionKotlinFiles(projectRoot).mapNotNull { file ->
            val relative = file.relativeTo(projectRoot).invariantSeparatorsPath
            if (file.readText().contains("Color.White") && relative !in allowed) relative else null
        }.toList()

        assertTrue("Undocumented Color.White production uses: $offenders", offenders.isEmpty())
    }

    @Test
    fun directArgbIsLimitedToCentralThemeDefinitions() {
        val projectRoot = File("../..").canonicalFile
        // Raw role/palette values are owned only by the two central theme definition files.
        val allowed = setOf(
            "core/designsystem/src/main/kotlin/cloud/univ/jointsense/designsystem/theme/JointSenseColors.kt",
            "core/designsystem/src/main/kotlin/cloud/univ/jointsense/designsystem/theme/JointSenseTheme.kt",
        )
        val directArgb = Regex("Color\\(0x[0-9A-Fa-f]+")
        val offenders = productionKotlinFiles(projectRoot).mapNotNull { file ->
            val relative = file.relativeTo(projectRoot).invariantSeparatorsPath
            if (directArgb.containsMatchIn(file.readText()) && relative !in allowed) relative else null
        }.toList()

        assertTrue("Direct ARGB outside theme definitions: $offenders", offenders.isEmpty())
    }

    @Test
    fun productionDoesNotCoerceGradeBoundaries() {
        val projectRoot = File("../..").canonicalFile
        val gradeCoercion = Regex(
            "grade[^\\n]{0,100}coerce(?:In|AtLeast|AtMost)|" +
                "coerce(?:In|AtLeast|AtMost)[^\\n]{0,100}grade",
            RegexOption.IGNORE_CASE,
        )
        val offenders = productionKotlinFiles(projectRoot).mapNotNull { file ->
            if (gradeCoercion.containsMatchIn(file.readText())) file.relativeTo(projectRoot).path else null
        }.toList()

        assertTrue("Grade coercion remains: $offenders", offenders.isEmpty())
    }

    @Test
    fun duplicateGradeBarIsRemovedFromProduction() {
        val projectRoot = File("../..").canonicalFile
        val chartSource = File(
            projectRoot,
            "core/designsystem/src/main/kotlin/cloud/univ/jointsense/designsystem/chart/ChartComponents.kt",
        ).readText()
        assertFalse(chartSource.contains("fun GradeBar("))
        assertFalse(
            productionKotlinFiles(projectRoot)
                .any { it.readText().contains("GradeBar(") },
        )
    }

    private fun productionKotlinFiles(projectRoot: File): Sequence<File> = sequenceOf(
        File(projectRoot, "app/src/main"),
        File(projectRoot, "core/designsystem/src/main"),
        File(projectRoot, "feature"),
    ).flatMap { root -> root.walkTopDown() }
        .filter {
            it.isFile &&
                it.extension == "kt" &&
                "/build/" !in it.invariantSeparatorsPath &&
                "/src/main/" in it.invariantSeparatorsPath
        }
}
