package cloud.univ.jointsense.designsystem

import java.io.File
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
}
