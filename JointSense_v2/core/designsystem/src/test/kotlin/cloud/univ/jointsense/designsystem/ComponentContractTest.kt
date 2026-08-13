package cloud.univ.jointsense.designsystem

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ComponentContractTest {
    @Test
    fun sharedComponentsExposeTextThroughCallersAndNoEmbeddedCopy() {
        val componentDirectory = File("src/main/kotlin/cloud/univ/jointsense/designsystem/component")
        val required = listOf(
            "JointSenseTopBar.kt",
            "ClinicalCard.kt",
            "FactorValue.kt",
            "GradeBadge.kt",
            "GradeScale.kt",
            "LoadingErrorState.kt",
        )

        required.forEach { name ->
            val source = File(componentDirectory, name)
            assertTrue("Missing shared component $name", source.isFile)
            assertFalse("$name must not own Android string resources", source.readText().contains("R.string."))
        }

        val topBar = File(componentDirectory, "JointSenseTopBar.kt").readText()
        assertTrue(topBar.contains("title: String"))
        assertTrue(topBar.contains("contentDescription: String"))

        val loading = File(componentDirectory, "LoadingErrorState.kt").readText()
        assertTrue(loading.contains("message: String?"))
        assertTrue(loading.contains("actionLabel: String?"))

        val gradeBadge = File(componentDirectory, "GradeBadge.kt").readText()
        assertTrue(
            Regex("color\\s*=\\s*MaterialTheme\\.colorScheme\\.surface").containsMatchIn(gradeBadge),
        )
        assertTrue(
            Regex("contentColor\\s*=\\s*MaterialTheme\\.colorScheme\\.onSurface").containsMatchIn(gradeBadge),
        )
        assertTrue(
            Regex("\\.background\\(\\s*gradeColor\\(grade\\)\\s*,\\s*CircleShape\\s*\\)")
                .containsMatchIn(gradeBadge),
        )
        assertEquals(
            "Grade color must be limited to the separate swatch",
            1,
            Regex(Regex.escape("gradeColor(grade)")).findAll(gradeBadge).count(),
        )
    }

    @Test
    fun themeOwnsLightAndDarkSchemesWithoutDeprecatedSystemBarColorWrites() {
        val source = File(
            "src/main/kotlin/cloud/univ/jointsense/designsystem/theme/JointSenseTheme.kt",
        ).readText()

        assertTrue(source.contains("LightColorScheme"))
        assertTrue(source.contains("DarkColorScheme"))
        assertTrue(source.contains("isAppearanceLightNavigationBars"))
        assertFalse(source.contains("statusBarColor ="))
        assertFalse(source.contains("dynamicLightColorScheme"))
        assertFalse(source.contains("dynamicDarkColorScheme"))
    }

    @Test
    fun chartsRenderCallerOwnedLabelsAndFormatAllNumericTicks() {
        val source = File(
            "src/main/kotlin/cloud/univ/jointsense/designsystem/chart/ChartComponents.kt",
        ).readText()

        assertTrue(source.contains("yAxisLabel: String"))
        assertTrue(source.contains("drawText(\n                yAxisLabel,"))
        assertTrue(source.contains("formatValue: (Float) -> String"))
        assertTrue(source.contains("AI_SCALE_TICKS.forEachIndexed"))
        assertTrue(source.contains("text = formatValue(tick)"))
        assertFalse(source.contains("listOf(\"0\", \"0.25\", \"0.50\", \"0.75\", \"1.00\")"))
        assertFalse(source.contains("text = \"▼\""))
    }
}
