package app.ledgerpop.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.ledgerpop.ui.theme.isAppDarkTheme
import dev.chrisbanes.haze.HazeState

/**
 * A Card container styled with the Blurred Liquid Glass effect.
 *
 * Can be used as a drop-in card replacement for transaction items, account balances, stats, etc.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    hazeState: HazeState? = LocalHazeState.current,
    shape: Shape = RoundedCornerShape(24.dp),
    elevation: Dp = 8.dp,
    blurRadius: Dp = 15.dp,
    tintColor: Color = MaterialTheme.colorScheme.primary,
    isDark: Boolean = isAppDarkTheme(),
    borderWidth: Dp = 1.dp,
    content: @Composable () -> Unit
) {
    LiquidGlassSurface(
        modifier = modifier,
        hazeState = hazeState,
        shape = shape,
        elevation = elevation,
        blurRadius = blurRadius,
        tintColor = tintColor,
        isDark = isDark,
        borderWidth = borderWidth,
        content = content
    )
}
