package app.ledgerpop.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SlidingToggle(
    isSelected: Boolean,
    onToggle: (Boolean) -> Unit,
    leftLabel: String,
    rightLabel: String,
    selectedColor: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier
) {
    val themeColor by animateColorAsState(selectedColor, label = "ThemeColor")
    val switchBgColor by animateColorAsState(themeColor.copy(alpha = 0.08f), label = "SwitchBg")
    val thumbOffset by animateDpAsState(if (isSelected) 52.dp else 0.dp, label = "ThumbOffset")

    Box(
        modifier = modifier
            .width(112.dp)
            .height(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(switchBgColor)
            .border(1.dp, themeColor.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
            .clickable { onToggle(!isSelected) }
            .padding(4.dp)
    ) {
        // Thumb (behind text)
        Box(
            modifier = Modifier
                .offset { IntOffset(x = thumbOffset.roundToPx(), y = 0) }
                .fillMaxHeight()
                .width(52.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(themeColor)
        )

        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text(
                    text = leftLabel,
                    color = if (!isSelected) Color.White else themeColor,
                    style = MaterialTheme.typography.titleSmall,
                    fontSize = 12.sp,
                    letterSpacing = 0.5.sp
                )
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text(
                    text = rightLabel,
                    color = if (isSelected) Color.White else themeColor,
                    style = MaterialTheme.typography.titleSmall,
                    fontSize = 12.sp,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}
