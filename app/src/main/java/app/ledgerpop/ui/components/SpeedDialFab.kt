package app.ledgerpop.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import app.ledgerpop.ui.theme.MidnightPrimary
import app.ledgerpop.ui.theme.Purple700

data class SpeedDialAction(
    val icon: ImageVector,
    val label: String,
    val onClick: () -> Unit,
    val contentDescription: String? = null
)

@Composable
fun SpeedDialFab(
    actions: List<SpeedDialAction>,
    modifier: Modifier = Modifier,
    mainIcon: ImageVector = Icons.Rounded.Menu,
    isExpanded: Boolean,
    onExpandedChange: (Boolean) -> Unit
) {
    val isMidnight = MaterialTheme.colorScheme.primary == MidnightPrimary
    val accentColor = if (isMidnight) MaterialTheme.colorScheme.primaryContainer else Purple700
    // No rotation for Menu icon switch
    val rotation = 0f

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AnimatedVisibility(
            visible = isExpanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                actions.forEach { action ->
                    SmallFloatingActionButton(
                        onClick = {
                            action.onClick()
                            onExpandedChange(false)
                        },
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(text = action.label, style = MaterialTheme.typography.labelLarge)
                            Icon(
                                imageVector = action.icon,
                                contentDescription = action.contentDescription ?: action.label,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { onExpandedChange(!isExpanded) },
            containerColor = if (isExpanded) MaterialTheme.colorScheme.surfaceVariant else accentColor,
            contentColor = if (isExpanded) MaterialTheme.colorScheme.onSurfaceVariant else Color.White,
            shape = RoundedCornerShape(18.dp)
        ) {
            Icon(
                imageVector = if (isExpanded) Icons.Rounded.Close else mainIcon,
                contentDescription = if (isExpanded) "Close Menu" else "Open Menu",
                modifier = Modifier
                    .size(28.dp)
                    .rotate(rotation)
            )
        }
    }
}
