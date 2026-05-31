package app.ledgerpop.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.ledgerpop.ui.state.TrendSummary

@Composable
fun ScrollableBarChart(
    summaries: List<TrendSummary>,
    selectedMonth: String? = null,
    onBarClick: (String) -> Unit
) {
    val density = LocalDensity.current
    val surfaceColor = MaterialTheme.colorScheme.surface
    val creditColor = Color(0xFF00B894)
    val debitColor = Color(0xFF9C27B0) // Purple

    val itemWidth = 52.dp
    val spacing = 16.dp
    val chartHeight = 120.dp

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = surfaceColor)
    ) {
        val scrollState = rememberScrollState()

        // Auto-scroll to latest (rightmost) data on first load
        var hasAutoScrolled by remember(summaries.isEmpty()) { mutableStateOf(false) }
        LaunchedEffect(summaries, scrollState.maxValue) {
            if (summaries.isNotEmpty() && scrollState.maxValue > 0 && !hasAutoScrolled) {
                scrollState.scrollTo(scrollState.maxValue)
                hasAutoScrolled = true
            }
        }
        
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp)
        ) {
            val viewportWidth = with(density) { maxWidth.toPx() }
            val itemWidthPx = with(density) { itemWidth.toPx() }
            val spacingPx = with(density) { spacing.toPx() }
            val totalItemStepPx = itemWidthPx + spacingPx

            // Dynamically calculate visible range and its max value for scaling
            val visibleMax by remember(summaries) {
                derivedStateOf {
                    val scrollX = scrollState.value.toFloat()
                    val startIndex = (scrollX / totalItemStepPx).toInt().coerceAtLeast(0)
                    val endIndex = ((scrollX + viewportWidth) / totalItemStepPx).toInt().coerceAtMost(summaries.size - 1)

                    if (summaries.isEmpty()) 1.0 else {
                        var maxV = 0.0
                        for (i in startIndex..endIndex) {
                            maxV = maxOf(maxV, summaries[i].income, summaries[i].expense)
                        }
                        if (maxV <= 0) 1.0 else maxV
                    }
                }
            }

            // Scale so visibleMax is at 85% of chart height
            val chartMaxAmount by animateFloatAsState(
                targetValue = (visibleMax / 0.85f).toFloat(),
                label = "ChartScale"
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(scrollState)
                    .padding(horizontal = 16.dp)
            ) {
                val totalWidth = if (summaries.isEmpty()) 0.dp else (itemWidth + spacing) * summaries.size - spacing
                
                Canvas(
                    modifier = Modifier
                        .width(totalWidth)
                        .height(chartHeight)
                ) {
                    val canvasHeight = size.height
                    val points = mutableListOf<Offset>()

                    summaries.forEachIndexed { index, summary ->
                        val isSelected = summary.label == selectedMonth
                        val alpha = if (selectedMonth == null || isSelected) 1f else 0.4f
                        val xCenter = index * (itemWidthPx + spacingPx) + itemWidthPx / 2
                        
                        // Debit Bar (Purple)
                        val expenseHeight = (summary.expense.toFloat() / chartMaxAmount) * canvasHeight
                        val barWidth = 18.dp.toPx()
                        drawRoundRect(
                            color = debitColor,
                            topLeft = Offset(x = xCenter - barWidth / 2, y = canvasHeight - expenseHeight),
                            size = Size(width = barWidth, height = expenseHeight),
                            cornerRadius = CornerRadius(x = 4.dp.toPx(), y = 4.dp.toPx()),
                            alpha = alpha
                        )

                        // Credit Point (for Line Chart)
                        val incomeHeight = (summary.income.toFloat() / chartMaxAmount) * canvasHeight
                        points.add(Offset(x = xCenter, y = canvasHeight - incomeHeight))
                    }

                    // Draw Credit Line
                    if (points.size > 1) {
                        for (i in 0 until points.size - 1) {
                            val isSelected = selectedMonth == null ||
                                           summaries[i].label == selectedMonth ||
                                           summaries[i+1].label == selectedMonth
                            val lineAlpha = if (isSelected) 1f else 0.3f

                            drawLine(
                                color = creditColor,
                                start = points[i],
                                end = points[i+1],
                                strokeWidth = 2.5.dp.toPx(),
                                cap = StrokeCap.Round,
                                alpha = lineAlpha
                            )
                        }
                    }

                    // Draw Credit Points
                    points.forEachIndexed { index, point ->
                        val monthSelected = selectedMonth == null || summaries[index].label == selectedMonth
                        val alpha = if (monthSelected) 1f else 0.4f

                        drawCircle(
                            color = creditColor,
                            radius = 5.dp.toPx(),
                            center = point,
                            alpha = alpha
                        )
                        drawCircle(
                            color = surfaceColor,
                            radius = 2.5.dp.toPx(),
                            center = point,
                            alpha = alpha
                        )
                    }
                }

                // Interactive layer and Labels
                Row {
                    summaries.forEach { summary ->
                        val isSelected = summary.label == selectedMonth
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .width(itemWidth)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else Color.Transparent)
                                .clickable { onBarClick(summary.label) }
                                .padding(vertical = 4.dp)
                        ) {
                            Spacer(Modifier.height(chartHeight + 8.dp))
                            Text(
                                text = summary.label,
                                style = if (isSelected) MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                                       else MaterialTheme.typography.labelSmall,
                                color = if (isSelected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                        if (summary != summaries.last()) {
                            Spacer(Modifier.width(spacing))
                        }
                    }
                }
            }
        }
    }
}
