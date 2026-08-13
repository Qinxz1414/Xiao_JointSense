package cloud.univ.jointsense.designsystem.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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

private val LightRoles = materialColorRoles(darkTheme = false)
private val DarkRoles = materialColorRoles(darkTheme = true)

private val LightColorScheme = lightColorScheme(
    primary = Color(LightRoles.primary),
    onPrimary = Color(LightRoles.onPrimary),
    primaryContainer = Color(0xFFD9EFF8),
    onPrimaryContainer = JointSenseColors.Ink,
    secondary = Color(LightRoles.secondary),
    onSecondary = Color(LightRoles.onSecondary),
    tertiary = JointSenseColors.BioGreen,
    onTertiary = Color.White,
    background = Color(LightRoles.background),
    onBackground = Color(LightRoles.onBackground),
    surface = Color(LightRoles.surface),
    onSurface = Color(LightRoles.onSurface),
    surfaceVariant = Color(0xFFE8EFF3),
    onSurfaceVariant = Color(LightRoles.onSurfaceVariant),
    outline = Color(0xFF718594),
    outlineVariant = JointSenseColors.Structure,
    error = Color(LightRoles.error),
    onError = Color(LightRoles.onError),
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(DarkRoles.primary),
    onPrimary = Color(DarkRoles.onPrimary),
    primaryContainer = Color(0xFF114B63),
    onPrimaryContainer = Color(0xFFD3F1FB),
    secondary = Color(DarkRoles.secondary),
    onSecondary = Color(DarkRoles.onSecondary),
    tertiary = Color(0xFF78D484),
    onTertiary = Color(0xFF083D12),
    background = Color(DarkRoles.background),
    onBackground = Color(DarkRoles.onBackground),
    surface = Color(DarkRoles.surface),
    onSurface = Color(DarkRoles.onSurface),
    surfaceVariant = Color(0xFF263640),
    onSurfaceVariant = Color(DarkRoles.onSurfaceVariant),
    outline = Color(0xFF8EA2AD),
    outlineVariant = Color(0xFF334955),
    error = Color(DarkRoles.error),
    onError = Color(DarkRoles.onError),
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
    val semanticColors = if (darkTheme) DarkSemanticColors else LightSemanticColors
    val statusBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
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
        LocalJointSenseSemanticColors provides semanticColors,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
            typography = JointSenseTypography,
        ) {
            Box(
                modifier = androidx.compose.ui.Modifier
                    .fillMaxSize()
                    .background(semanticColors.statusBarContainer),
            ) {
                Surface(
                    modifier = androidx.compose.ui.Modifier
                        .fillMaxSize()
                        .padding(top = statusBarPadding),
                    color = MaterialTheme.colorScheme.background,
                    content = content,
                )
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
