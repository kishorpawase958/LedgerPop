package app.ledgerpop.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColors = darkColorScheme(
    primary             = Accent500,
    onPrimary           = SurfaceLight,
    primaryContainer    = Accent600,
    onPrimaryContainer  = Neutral100,
    secondary           = Success500,
    onSecondary         = SurfaceLight,
    tertiary            = Warning400,
    background          = SurfaceDark,
    onBackground        = Neutral100,
    surface             = SurfaceDark2,
    onSurface           = Neutral200,
    surfaceVariant      = SurfaceDark3,
    onSurfaceVariant    = Neutral300,
    outline             = Neutral600,
    error               = Warning500,
)

private val LightColors = lightColorScheme(
    primary             = Accent500,
    onPrimary           = SurfaceLight,
    primaryContainer    = Color(0xFFEEEBFF),
    onPrimaryContainer  = Accent600,
    secondary           = Success500,
    onSecondary         = SurfaceLight,
    tertiary            = Warning500,
    background          = Neutral50,
    onBackground        = Neutral900,
    surface             = SurfaceLight,
    onSurface           = Neutral800,
    surfaceVariant      = Neutral100,
    onSurfaceVariant    = Neutral600,
    outline             = Neutral300,
    error               = Warning500,
)

@Composable
fun LedgerPopTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
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