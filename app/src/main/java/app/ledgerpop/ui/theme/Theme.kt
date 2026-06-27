package app.ledgerpop.ui.theme

import android.app.Activity
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import app.ledgerpop.ui.state.AppTheme

@Composable
fun animateColorScheme(targetScheme: ColorScheme): ColorScheme {
    val duration = 600
    return ColorScheme(
        primary = animateColorAsState(targetScheme.primary, tween(duration)).value,
        onPrimary = animateColorAsState(targetScheme.onPrimary, tween(duration)).value,
        primaryContainer = animateColorAsState(targetScheme.primaryContainer, tween(duration)).value,
        onPrimaryContainer = animateColorAsState(targetScheme.onPrimaryContainer, tween(duration)).value,
        inversePrimary = animateColorAsState(targetScheme.inversePrimary, tween(duration)).value,
        secondary = animateColorAsState(targetScheme.secondary, tween(duration)).value,
        onSecondary = animateColorAsState(targetScheme.onSecondary, tween(duration)).value,
        secondaryContainer = animateColorAsState(targetScheme.secondaryContainer, tween(duration)).value,
        onSecondaryContainer = animateColorAsState(targetScheme.onSecondaryContainer, tween(duration)).value,
        tertiary = animateColorAsState(targetScheme.tertiary, tween(duration)).value,
        onTertiary = animateColorAsState(targetScheme.onTertiary, tween(duration)).value,
        tertiaryContainer = animateColorAsState(targetScheme.tertiaryContainer, tween(duration)).value,
        onTertiaryContainer = animateColorAsState(targetScheme.onTertiaryContainer, tween(duration)).value,
        background = animateColorAsState(targetScheme.background, tween(duration)).value,
        onBackground = animateColorAsState(targetScheme.onBackground, tween(duration)).value,
        surface = animateColorAsState(targetScheme.surface, tween(duration)).value,
        onSurface = animateColorAsState(targetScheme.onSurface, tween(duration)).value,
        surfaceVariant = animateColorAsState(targetScheme.surfaceVariant, tween(duration)).value,
        onSurfaceVariant = animateColorAsState(targetScheme.onSurfaceVariant, tween(duration)).value,
        surfaceTint = animateColorAsState(targetScheme.surfaceTint, tween(duration)).value,
        inverseSurface = animateColorAsState(targetScheme.inverseSurface, tween(duration)).value,
        inverseOnSurface = animateColorAsState(targetScheme.inverseOnSurface, tween(duration)).value,
        error = animateColorAsState(targetScheme.error, tween(duration)).value,
        onError = animateColorAsState(targetScheme.onError, tween(duration)).value,
        errorContainer = animateColorAsState(targetScheme.errorContainer, tween(duration)).value,
        onErrorContainer = animateColorAsState(targetScheme.onErrorContainer, tween(duration)).value,
        outline = animateColorAsState(targetScheme.outline, tween(duration)).value,
        outlineVariant = animateColorAsState(targetScheme.outlineVariant, tween(duration)).value,
        scrim = animateColorAsState(targetScheme.scrim, tween(duration)).value,
        surfaceBright = animateColorAsState(targetScheme.surfaceBright, tween(duration)).value,
        surfaceDim = animateColorAsState(targetScheme.surfaceDim, tween(duration)).value,
        surfaceContainer = animateColorAsState(targetScheme.surfaceContainer, tween(duration)).value,
        surfaceContainerLow = animateColorAsState(targetScheme.surfaceContainerLow, tween(duration)).value,
        surfaceContainerLowest = animateColorAsState(targetScheme.surfaceContainerLowest, tween(duration)).value,
        surfaceContainerHigh = animateColorAsState(targetScheme.surfaceContainerHigh, tween(duration)).value,
        surfaceContainerHighest = animateColorAsState(targetScheme.surfaceContainerHighest, tween(duration)).value,
        primaryFixed = animateColorAsState(targetScheme.primaryFixed, tween(duration)).value,
        onPrimaryFixed = animateColorAsState(targetScheme.onPrimaryFixed, tween(duration)).value,
        primaryFixedDim = animateColorAsState(targetScheme.primaryFixedDim, tween(duration)).value,
        onPrimaryFixedVariant = animateColorAsState(targetScheme.onPrimaryFixedVariant, tween(duration)).value,
        secondaryFixed = animateColorAsState(targetScheme.secondaryFixed, tween(duration)).value,
        onSecondaryFixed = animateColorAsState(targetScheme.onSecondaryFixed, tween(duration)).value,
        secondaryFixedDim = animateColorAsState(targetScheme.secondaryFixedDim, tween(duration)).value,
        onSecondaryFixedVariant = animateColorAsState(targetScheme.onSecondaryFixedVariant, tween(duration)).value,
        tertiaryFixed = animateColorAsState(targetScheme.tertiaryFixed, tween(duration)).value,
        onTertiaryFixed = animateColorAsState(targetScheme.onTertiaryFixed, tween(duration)).value,
        tertiaryFixedDim = animateColorAsState(targetScheme.tertiaryFixedDim, tween(duration)).value,
        onTertiaryFixedVariant = animateColorAsState(targetScheme.onTertiaryFixedVariant, tween(duration)).value,
    )
}

private val DarkColors = darkColorScheme(
    primary             = Purple500,
    onPrimary           = Color.Black,
    primaryContainer    = Purple700,
    onPrimaryContainer  = Color.White,
    secondary           = SuccessGreen,
    onSecondary         = Color.Black,
    tertiary            = WarningOrange,
    background          = BackgroundDark,
    onBackground        = Color.White,
    surface             = SurfaceDark2,
    onSurface           = Color.White,
    surfaceVariant      = SurfaceDark3,
    onSurfaceVariant    = Neutral300,
    outline             = Neutral600,
    error               = ErrorRed,
)

private val LightColors = lightColorScheme(
    primary             = Purple700,
    onPrimary           = Color.White,
    primaryContainer    = Purple200,
    onPrimaryContainer  = Purple700,
    secondary           = SuccessGreen,
    onSecondary         = Color.White,
    tertiary            = WarningOrange,
    background          = Neutral100,
    onBackground        = Neutral900,
    surface             = Color.White,
    onSurface           = Neutral900,
    surfaceVariant      = Neutral100,
    onSurfaceVariant    = Neutral600,
    outline             = Neutral300,
    error               = ErrorRed,
)

private val MidnightColors = darkColorScheme(
    primary             = MidnightPrimary,
    onPrimary           = Color(0xFF002B75),
    primaryContainer    = Color(0xFF003FA4),
    onPrimaryContainer  = Color(0xFFDBE1FF),
    secondary           = MidnightSecondary,
    onSecondary         = Color(0xFF1E2737),
    tertiary            = MidnightTertiary,
    onTertiary          = Color(0xFF00344B),
    background          = BackgroundMidnight,
    onBackground        = Color(0xFFE2E2E6),
    surface             = SurfaceMidnight,
    onSurface           = Color(0xFFE2E2E6),
    surfaceVariant      = SurfaceVariantMidnight,
    onSurfaceVariant    = Color(0xFFC3C7CF),
    outline             = Color(0xFF8D9199),
    error               = ErrorRed,
)

@Composable
fun LedgerPopTheme(
    appTheme: AppTheme = AppTheme.AUTO,
    content: @Composable () -> Unit
) {
    val targetColorScheme = when (appTheme) {
        AppTheme.DARK -> DarkColors
        AppTheme.LIGHT -> LightColors
        AppTheme.MIDNIGHT -> MidnightColors
        AppTheme.AUTO -> if (isSystemInDarkTheme()) DarkColors else LightColors
    }

    val colorScheme = animateColorScheme(targetColorScheme)
    val darkTheme = targetColorScheme != LightColors

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view)
                .isAppearanceLightStatusBars = !darkTheme
        }
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography  = LedgerPopTypography,
        content     = content
    )
}
