package cloud.univ.jointsense.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import cloud.univ.jointsense.domain.model.InflammationFactor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** Stable brand and scientific semantic colors. */
object JointSenseColors {
    val Ink = Color(0xFF0E2841)
    val Cyan = Color(0xFF0F9ED5)
    val BioGreen = Color(0xFF196B24)
    val Structure = Color(0xFFE2E8EC)
    val Canvas = Color(0xFFF5F8FA)
    val Surface = Color(0xFFFFFFFF)
    val SecondaryText = Color(0xFF52697D)

    val TnfAlpha = Color(0xFFD64545)
    val Il6 = Color(0xFFE97132)
    val Il1Beta = Color(0xFF196B24)

    val Grades = listOf(
        Color(0xFF2E7D32),
        Color(0xFF7A9B22),
        Color(0xFFD6A900),
        Color(0xFFE97132),
        Color(0xFFD64545),
    )

    /** Experimental signal intensity only; never use for application chrome. */
    val WellPalette = listOf(
        Color(0xFF9B9790),
        Color(0xFF9C9A93),
        Color(0xFF7B9694),
        Color(0xFF7F9594),
        Color(0xFF709392),
        Color(0xFF7C9191),
        Color(0xFF778C8D),
        Color(0xFF6F8685),
    )
}

private val FactorArgb = mapOf(
    InflammationFactor.TNF_ALPHA to 0xFFD64545,
    InflammationFactor.IL6 to 0xFFE97132,
    InflammationFactor.IL1_BETA to 0xFF196B24,
)

private val GradeArgb = listOf(
    0xFF2E7D32,
    0xFF7A9B22,
    0xFFD6A900,
    0xFFE97132,
    0xFFD64545,
)

fun factorArgb(factor: InflammationFactor): Long = FactorArgb.getValue(factor)

fun gradeArgb(grade: Int): Long {
    require(grade in GradeArgb.indices) { "Grade must be between 0 and 4" }
    return GradeArgb[grade]
}

fun factorColor(factor: InflammationFactor): Color = Color(factorArgb(factor))

fun gradeColor(grade: Int): Color = Color(gradeArgb(grade))

@Immutable
data class JointSenseSemanticColors(
    val topBarContainer: Color,
    val onTopBar: Color,
    val cardContainer: Color,
    val cardOutline: Color,
    val statusBarContainer: Color,
    val statusBarContent: Color,
)

data class MaterialColorRoles(
    val primary: Long,
    val onPrimary: Long,
    val secondary: Long,
    val onSecondary: Long,
    val error: Long,
    val onError: Long,
    val background: Long,
    val onBackground: Long,
    val surface: Long,
    val onSurface: Long,
    val onSurfaceVariant: Long,
    val statusBarBackground: Long,
    val statusBarForeground: Long,
)

private val LightMaterialColorRoles = MaterialColorRoles(
    primary = 0xFF156082,
    onPrimary = 0xFFFFFFFF,
    secondary = 0xFF00677F,
    onSecondary = 0xFFFFFFFF,
    error = 0xFFB3261E,
    onError = 0xFFFFFFFF,
    background = 0xFFF5F8FA,
    onBackground = 0xFF0E2841,
    surface = 0xFFFFFFFF,
    onSurface = 0xFF0E2841,
    onSurfaceVariant = 0xFF52697D,
    statusBarBackground = 0xFF0E2841,
    statusBarForeground = 0xFFFFFFFF,
)

private val DarkMaterialColorRoles = MaterialColorRoles(
    primary = 0xFF67C7E8,
    onPrimary = 0xFF002F40,
    secondary = 0xFF83D1EA,
    onSecondary = 0xFF003543,
    error = 0xFFFFB4AB,
    onError = 0xFF690005,
    background = 0xFF0D151B,
    onBackground = 0xFFE4EDF2,
    surface = 0xFF17242D,
    onSurface = 0xFFE4EDF2,
    onSurfaceVariant = 0xFFBCCBD3,
    statusBarBackground = 0xFF111A22,
    statusBarForeground = 0xFFEAF5FA,
)

fun materialColorRoles(darkTheme: Boolean): MaterialColorRoles =
    if (darkTheme) DarkMaterialColorRoles else LightMaterialColorRoles

fun contrastRatio(firstArgb: Long, secondArgb: Long): Double {
    val first = relativeLuminance(firstArgb)
    val second = relativeLuminance(secondArgb)
    return (max(first, second) + 0.05) / (min(first, second) + 0.05)
}

private fun relativeLuminance(argb: Long): Double {
    fun linear(channel: Int): Double {
        val normalized = channel / 255.0
        return if (normalized <= 0.04045) normalized / 12.92
        else ((normalized + 0.055) / 1.055).pow(2.4)
    }
    val red = linear(((argb shr 16) and 0xFF).toInt())
    val green = linear(((argb shr 8) and 0xFF).toInt())
    val blue = linear((argb and 0xFF).toInt())
    return 0.2126 * red + 0.7152 * green + 0.0722 * blue
}

internal val LightSemanticColors = JointSenseSemanticColors(
    topBarContainer = JointSenseColors.Ink,
    onTopBar = Color(LightMaterialColorRoles.statusBarForeground),
    cardContainer = JointSenseColors.Surface,
    cardOutline = JointSenseColors.Structure,
    statusBarContainer = Color(LightMaterialColorRoles.statusBarBackground),
    statusBarContent = Color(LightMaterialColorRoles.statusBarForeground),
)

internal val DarkSemanticColors = JointSenseSemanticColors(
    topBarContainer = Color(0xFF111A22),
    onTopBar = Color(0xFFEAF5FA),
    cardContainer = Color(0xFF17242D),
    cardOutline = Color(0xFF334955),
    statusBarContainer = Color(DarkMaterialColorRoles.statusBarBackground),
    statusBarContent = Color(DarkMaterialColorRoles.statusBarForeground),
)

internal val LocalJointSenseSemanticColors = staticCompositionLocalOf { LightSemanticColors }

// Canonical short names kept for chart and feature code.
val GradeColors = JointSenseColors.Grades
val WellPalette = JointSenseColors.WellPalette
