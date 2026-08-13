package cloud.univ.jointsense.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import cloud.univ.jointsense.domain.model.InflammationFactor

/** Stable brand and scientific semantic colors. */
object JointSenseColors {
    val Ink = Color(0xFF0E2841)
    val Primary = Color(0xFF156082)
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

fun gradeArgb(grade: Int): Long = GradeArgb[grade.coerceIn(0, GradeArgb.lastIndex)]

fun factorColor(factor: InflammationFactor): Color = Color(factorArgb(factor))

fun gradeColor(grade: Int): Color = Color(gradeArgb(grade))

@Immutable
data class JointSenseSemanticColors(
    val topBarContainer: Color,
    val onTopBar: Color,
    val cardContainer: Color,
    val cardOutline: Color,
)

internal val LightSemanticColors = JointSenseSemanticColors(
    topBarContainer = JointSenseColors.Ink,
    onTopBar = Color.White,
    cardContainer = JointSenseColors.Surface,
    cardOutline = JointSenseColors.Structure,
)

internal val DarkSemanticColors = JointSenseSemanticColors(
    topBarContainer = Color(0xFF111A22),
    onTopBar = Color(0xFFEAF5FA),
    cardContainer = Color(0xFF17242D),
    cardOutline = Color(0xFF334955),
)

internal val LocalJointSenseSemanticColors = staticCompositionLocalOf { LightSemanticColors }

// Canonical short names kept for chart and feature code.
val StructureGray = JointSenseColors.Structure
val PrimaryAccent = JointSenseColors.Primary
val BioGreen = JointSenseColors.BioGreen
val TnfRed = JointSenseColors.TnfAlpha
val AiLine = JointSenseColors.Primary
val GradeColors = JointSenseColors.Grades
val WellPalette = JointSenseColors.WellPalette
