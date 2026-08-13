package cloud.univ.jointsense.designsystem.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = JointSenseColors.Primary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD9EFF8),
    onPrimaryContainer = JointSenseColors.Ink,
    secondary = JointSenseColors.Cyan,
    onSecondary = Color.White,
    tertiary = JointSenseColors.BioGreen,
    onTertiary = Color.White,
    background = JointSenseColors.Canvas,
    onBackground = JointSenseColors.Ink,
    surface = JointSenseColors.Surface,
    onSurface = JointSenseColors.Ink,
    surfaceVariant = Color(0xFFE8EFF3),
    onSurfaceVariant = JointSenseColors.SecondaryText,
    outline = Color(0xFF718594),
    outlineVariant = JointSenseColors.Structure,
    error = JointSenseColors.TnfAlpha,
    onError = Color.White,
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF67C7E8),
    onPrimary = Color(0xFF002F40),
    primaryContainer = Color(0xFF114B63),
    onPrimaryContainer = Color(0xFFD3F1FB),
    secondary = Color(0xFF67C7E8),
    onSecondary = Color(0xFF002F40),
    tertiary = Color(0xFF78D484),
    onTertiary = Color(0xFF083D12),
    background = Color(0xFF0D151B),
    onBackground = Color(0xFFE4EDF2),
    surface = Color(0xFF17242D),
    onSurface = Color(0xFFE4EDF2),
    surfaceVariant = Color(0xFF263640),
    onSurfaceVariant = Color(0xFFBCCBD3),
    outline = Color(0xFF8EA2AD),
    outlineVariant = Color(0xFF334955),
    error = JointSenseColors.TnfAlpha,
    onError = Color.White,
)

val MaterialTheme.jointSenseColors: JointSenseSemanticColors
    @Composable
    @ReadOnlyComposable
    get() = LocalJointSenseSemanticColors.current

@Composable
fun JointSenseTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val view = LocalView.current
    val activity = LocalContext.current.findActivity()
    if (!view.isInEditMode && activity != null) {
        SideEffect {
            WindowCompat.getInsetsController(activity.window, view).apply {
                // Top app bars paint behind the transparent status bar.
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(
        LocalJointSenseSemanticColors provides if (darkTheme) DarkSemanticColors else LightSemanticColors,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
            typography = JointSenseTypography,
            content = content,
        )
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
