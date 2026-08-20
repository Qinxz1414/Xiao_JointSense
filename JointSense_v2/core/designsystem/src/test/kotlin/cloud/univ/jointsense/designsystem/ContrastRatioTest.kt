package cloud.univ.jointsense.designsystem

import cloud.univ.jointsense.designsystem.theme.contrastRatio
import cloud.univ.jointsense.designsystem.theme.materialColorRoles
import org.junit.Assert.assertTrue
import org.junit.Test

class ContrastRatioTest {
    @Test
    fun lightMaterialTextRolesMeetNormalTextContrast() {
        assertRoleContrast(darkTheme = false)
    }

    @Test
    fun darkMaterialTextRolesMeetNormalTextContrast() {
        assertRoleContrast(darkTheme = true)
    }

    @Test
    fun neutralGradeBadgeTextMeetsNormalTextContrastInBothThemes() {
        listOf(false, true).forEach { darkTheme ->
            val roles = materialColorRoles(darkTheme)
            val ratio = contrastRatio(roles.onSurface, roles.surface)
            assertTrue("Grade badge contrast was $ratio in darkTheme=$darkTheme", ratio >= 4.5)
        }
    }

    private fun assertRoleContrast(darkTheme: Boolean) {
        val roles = materialColorRoles(darkTheme)
        val pairs = mapOf(
            "onPrimary/primary" to (roles.onPrimary to roles.primary),
            "onSecondary/secondary" to (roles.onSecondary to roles.secondary),
            "onError/error" to (roles.onError to roles.error),
            "error/surface" to (roles.error to roles.surface),
            "onSurface/surface" to (roles.onSurface to roles.surface),
            "onSurfaceVariant/surface" to (roles.onSurfaceVariant to roles.surface),
            "onBackground/background" to (roles.onBackground to roles.background),
            "statusForeground/statusBackground" to
                (roles.statusBarForeground to roles.statusBarBackground),
        )
        pairs.forEach { (name, pair) ->
            val ratio = contrastRatio(pair.first, pair.second)
            assertTrue("$name was $ratio in darkTheme=$darkTheme", ratio >= 4.5)
        }
    }
}
