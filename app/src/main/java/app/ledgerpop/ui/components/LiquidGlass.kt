package app.ledgerpop.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.ledgerpop.ui.theme.isAppDarkTheme
import dev.chrisbanes.haze.HazeDefaults
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect

/**
 * CompositionLocal providing ambient [HazeState] down the Compose hierarchy.
 */
val LocalHazeState = compositionLocalOf<HazeState?> { null }

/**
 * Applies the polished blurred liquid glass effect to any composable.
 *
 * Visual layers:
 * 1. Crisp ambient depth shadow
 * 2. Crystal-clear minimal haze blur with subtle tint
 * 3. Curved glossy lens glare (specular horizon gradient)
 * 4. Razor-sharp prismatic glass rim border
 *
 * @param hazeState Optional [HazeState] used to blur content behind the glass. Defaults to [LocalHazeState].
 * @param shape The shape of the glass element (e.g. [CircleShape], [RoundedCornerShape]).
 * @param elevation Elevation for the ambient depth shadow. Pass 0.dp to disable shadow.
 * @param blurRadius The blur radius for the haze effect.
 * @param tintColor Accent color used in the specular glare and bottom refraction highlight.
 * @param isDark Whether to render dark mode glass parameters. Defaults to [isAppDarkTheme].
 * @param borderWidth Width of the prismatic glass rim border.
 */
@Composable
fun Modifier.liquidGlass(
    hazeState: HazeState? = LocalHazeState.current,
    shape: Shape = CircleShape,
    elevation: Dp = 12.dp,
    blurRadius: Dp = 15.dp,
    tintColor: Color = MaterialTheme.colorScheme.primary,
    isDark: Boolean = isAppDarkTheme(),
    borderWidth: Dp = 1.dp
): Modifier {
    val ambientAlpha = if (isDark) 0.35f else 0.12f
    val spotAlpha = if (isDark) 0.45f else 0.18f
    val hazeTint = if (isDark) Color.Black.copy(alpha = 0.20f) else Color.White.copy(alpha = 0.08f)
    val glareTop = tintColor.copy(alpha = if (isDark) 0.35f else 0.15f)
    val glareFalloff = Color.White.copy(alpha = if (isDark) 0.10f else 0.15f)
    val glareBottom = tintColor.copy(alpha = if (isDark) 0.10f else 0.10f)
    val rimTop = MaterialTheme.colorScheme.primary.copy(alpha = if (isDark) 0.35f else 0.15f)
    val rimFalloff = MaterialTheme.colorScheme.primary.copy(alpha = if (isDark) 0.15f else 0.15f)
    val rimBottom = tintColor.copy(alpha = if (isDark) 0.15f else 0.15f)

    return this
        // 1. Crisp ambient depth shadow
        .then(
            if (elevation > 0.dp) {
                Modifier.shadow(
                    elevation = elevation,
                    shape = shape,
                    ambientColor = Color.Black.copy(alpha = ambientAlpha),
                    spotColor = Color.Black.copy(alpha = spotAlpha)
                )
            } else Modifier
        )
        .clip(shape)
        // 2. Crystal-clear minimal haze (low blur, zero grain for pure polished glass)
        .then(
            if (hazeState != null) {
                Modifier.hazeEffect(
                    state = hazeState,
                    style = HazeDefaults.style(
                        backgroundColor = Color.Transparent,
                        blurRadius = blurRadius,
                        noiseFactor = 0f,
                        tint = HazeDefaults.tint(hazeTint)
                    )
                )
            } else {
                Modifier.background(
                    if (isDark) Color.Black.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.35f)
                )
            }
        )
        // 3. Curved Glossy Lens Glare (Specular Horizon)
        .background(
            Brush.verticalGradient(
                0.0f to glareTop,
                0.05f to glareFalloff,
                0.48f to Color.Transparent,
                0.85f to Color.Transparent,
                1f to glareBottom
            )
        )
        // 4. Razor-sharp Prismatic Glass Rim
        .border(
            width = borderWidth,
            brush = Brush.verticalGradient(
                0.0f to rimTop,
                0.35f to rimFalloff,
                0.55f to MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                0.5f to rimBottom
            ),
            shape = shape
        )
}

/**
 * Secondary glossy liquid lens modifier (such as for selected pill highlights or badges).
 */
@Composable
fun Modifier.liquidGlassLens(
    hazeState: HazeState? = LocalHazeState.current,
    shape: Shape = CircleShape,
    tintColor: Color = MaterialTheme.colorScheme.primary,
    blurRadius: Dp = 20.dp,
    borderWidth: Dp = 1.dp
): Modifier {
    return this
        .clip(shape)
        .then(
            if (hazeState != null) {
                Modifier.hazeEffect(
                    state = hazeState,
                    style = HazeDefaults.style(
                        backgroundColor = Color.Transparent,
                        blurRadius = blurRadius,
                        noiseFactor = 0f,
                        tint = HazeDefaults.tint(tintColor.copy(alpha = 0.12f))
                    )
                )
            } else {
                Modifier.background(tintColor.copy(alpha = 0.12f))
            }
        )
        .background(
            Brush.verticalGradient(
                0.0f to tintColor.copy(alpha = 0.22f),
                0.5f to tintColor.copy(alpha = 0.08f),
                1.0f to Color.Transparent
            )
        )
        .border(
            width = borderWidth,
            brush = Brush.verticalGradient(
                0.0f to tintColor.copy(alpha = 0.60f),
                0.5f to tintColor.copy(alpha = 0.20f),
                1.0f to Color.Transparent
            ),
            shape = shape
        )
}

/**
 * Reusable container composable providing the blurred liquid glass effect over a [Surface].
 */
@Composable
fun LiquidGlassSurface(
    modifier: Modifier = Modifier,
    hazeState: HazeState? = LocalHazeState.current,
    shape: Shape = CircleShape,
    elevation: Dp = 12.dp,
    blurRadius: Dp = 15.dp,
    tintColor: Color = MaterialTheme.colorScheme.primary,
    isDark: Boolean = isAppDarkTheme(),
    borderWidth: Dp = 1.dp,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier.liquidGlass(
            hazeState = hazeState,
            shape = shape,
            elevation = elevation,
            blurRadius = blurRadius,
            tintColor = tintColor,
            isDark = isDark,
            borderWidth = borderWidth
        ),
        shape = shape,
        color = Color.Transparent,
        tonalElevation = 0.dp,
        content = content
    )
}

/**
 * Box layout variant with blurred liquid glass effect applied.
 */
@Composable
fun LiquidGlassBox(
    modifier: Modifier = Modifier,
    hazeState: HazeState? = LocalHazeState.current,
    shape: Shape = CircleShape,
    elevation: Dp = 12.dp,
    blurRadius: Dp = 15.dp,
    tintColor: Color = MaterialTheme.colorScheme.primary,
    isDark: Boolean = isAppDarkTheme(),
    borderWidth: Dp = 1.dp,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier.liquidGlass(
            hazeState = hazeState,
            shape = shape,
            elevation = elevation,
            blurRadius = blurRadius,
            tintColor = tintColor,
            isDark = isDark,
            borderWidth = borderWidth
        ),
        content = content
    )
}
