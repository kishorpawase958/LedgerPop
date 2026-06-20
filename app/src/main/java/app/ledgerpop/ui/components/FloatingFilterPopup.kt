package app.ledgerpop.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties

@Composable
fun FloatingFilterPopup(
    title: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
    yOffset: Int = -100, // Offset in pixels from BottomEnd
    emojiProvider: ((String) -> String)? = null
) {
    var isVisible by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    
    // Positioning: FAB is at end=24dp, width=56dp. 
    // Left edge of Main FAB is at 24 + 56 = 80dp from the right edge of the screen.
    // We align the right edge of the popup to the left edge of the FAB.
    val xOffsetPx = remember(density) { with(density) { (-80).dp.roundToPx() } }

    LaunchedEffect(Unit) {
        isVisible = true
    }

    Popup(
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
        offset = IntOffset(x = xOffsetPx, y = yOffset),
        alignment = Alignment.BottomEnd
    ) {
        AnimatedVisibility(
            visible = isVisible,
            enter = fadeIn(tween(200)) + scaleIn(
                initialScale = 0.8f,
                transformOrigin = TransformOrigin(1f, 0.9f),
                animationSpec = tween(250)
            ) + slideInHorizontally(
                initialOffsetX = { it / 8 },
                animationSpec = tween(250)
            ),
            exit = fadeOut(tween(200)) + scaleOut(
                targetScale = 0.8f,
                transformOrigin = TransformOrigin(1f, 0.8f)
            )
        ) {
            Card(
                modifier = Modifier
                    .width(260.dp)
                    .heightIn(max = 400.dp)
                    .padding(8.dp),
                shape = RoundedCornerShape(28.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(options) { option ->
                            val isSelected = option == selected
                            Surface(
                                onClick = { 
                                    onSelect(option)
                                    onDismiss()
                                },
                                shape = RoundedCornerShape(16.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (emojiProvider != null) {
                                        Text(
                                            text = emojiProvider(option), 
                                            modifier = Modifier.padding(end = 12.dp),
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                    }
                                    Text(
                                        text = option,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
