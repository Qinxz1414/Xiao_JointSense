package cloud.univ.jointsense.designsystem

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductionColorRoleAuditTest {
    @Test
    fun genericProductionCallersUseMaterialRoles() {
        val projectRoot = File("../..").canonicalFile
        val sourceFiles = sequenceOf(
            File(projectRoot, "app/src/main"),
            File(projectRoot, "feature"),
        ).flatMap { root ->
            root.walkTopDown().filter {
                it.isFile &&
                    it.extension == "kt" &&
                    "/build/" !in it.invariantSeparatorsPath &&
                    "/src/main/" in it.invariantSeparatorsPath
            }
        }

        val forbiddenImports = Regex(
            "designsystem\\.theme\\.(PrimaryAccent|BioGreen|TnfRed|StructureGray)",
        )
        val offenders = sourceFiles.mapNotNull { file ->
            if (forbiddenImports.containsMatchIn(file.readText())) file.relativeTo(projectRoot).path else null
        }.toList()

        assertTrue("Fixed generic color imports: $offenders", offenders.isEmpty())
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
            sequenceOf(
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
                .any { it.readText().contains("GradeBar(") },
        )
    }
}
