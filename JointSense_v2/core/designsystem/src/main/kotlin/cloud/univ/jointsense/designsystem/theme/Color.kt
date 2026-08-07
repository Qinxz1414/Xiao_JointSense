package cloud.univ.jointsense.designsystem.theme

import androidx.compose.ui.graphics.Color
import cloud.univ.jointsense.domain.model.InflammationFactor

// =====================================================================
// Rule theme — presentation theme tokens (see Rule/SKILL.md)
// Used for all non-data UI structure: text, bars, accents, dividers.
// =====================================================================
val InkText = Color(0xFF0E2841)          // text / dark anchor (top bars, titles)
val StructureGray = Color(0xFFE8E8E8)    // table lines, separators, chips
val PrimaryAccent = Color(0xFF156082)    // main non-experimental accent
val WarmContrast = Color(0xFFE97132)     // warnings / comparison highlights
val BioGreen = Color(0xFF196B24)         // positive biological interpretation
val CyanAccent = Color(0xFF0F9ED5)       // secondary emphasis
val BgWhite = Color(0xFFFFFFFF)          // clean background
val BgLight = Color(0xFFF7F9FA)          // very light gray canvas

// =====================================================================
// Factor identification colors (match the design mockup legend)
// =====================================================================
val TnfRed = Color(0xFFD64545)           // TNF-α
val Il6Orange = WarmContrast             // IL-6
val Il1Green = BioGreen                  // IL-1β

fun factorColor(factor: InflammationFactor): Color = when (factor) {
    InflammationFactor.TNF_ALPHA -> TnfRed
    InflammationFactor.IL6 -> Il6Orange
    InflammationFactor.IL1_BETA -> Il1Green
}

// AI index trend line (purple is forbidden by the Rule visual language)
val AiLine = PrimaryAccent

// =====================================================================
// OA inflammation grade ramp 0..4 (design mockup: green -> red)
// =====================================================================
val GradeColors = listOf(
    Color(0xFF4CAF50), // 0 no risk
    Color(0xFFA5C94B), // 1 mild
    Color(0xFFF2C230), // 2 moderate
    WarmContrast,      // 3 severe
    TnfRed             // 4 very severe
)

// =====================================================================
// ELISA well palette — transparent-to-blue-green gradient extracted
// from well interiors (Rule/SKILL.md). Use ONLY for experimental signal
// intensity (well swatches, concentration ladders), never for chrome.
// =====================================================================
val WellPalette = listOf(
    Color(0xFF9B9790), // blank / near transparent
    Color(0xFF9C9A93), // clear warm gray
    Color(0xFF7B9694), // very pale cyan-gray
    Color(0xFF7F9594), // pale cyan
    Color(0xFF709392), // light teal
    Color(0xFF7C9191), // muted teal
    Color(0xFF778C8D), // blue-gray teal
    Color(0xFF6F8685)  // darker teal
)

// =====================================================================
// Semantic text / state colors
// =====================================================================
val TextSecondary = Color(0xFF5F6B7A)
val TextPrimary = InkText
val ErrorRed = TnfRed
val SuccessGreen = BioGreen

// =====================================================================
// Legacy aliases — old names kept so existing screens compile while
// being re-styled; they now point at the Rule palette.
// =====================================================================
val MedicalBlue = PrimaryAccent
val MedicalBlueDark = InkText
val MedicalBlueLight = CyanAccent
val MedicalTeal = CyanAccent
val MedicalGreen = BioGreen
val AccentOrange = WarmContrast
val BackgroundLight = BgLight
val SurfaceWhite = BgWhite
val ChartBlue = PrimaryAccent
val ChartOrange = WarmContrast

// Purple is banned by the Rule visual language; alias kept only for
// compile compatibility and redirected to the primary accent.
val ChartPurple = PrimaryAccent
