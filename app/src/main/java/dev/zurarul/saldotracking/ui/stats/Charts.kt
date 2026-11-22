package dev.zurarul.saldotracking.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.zurarul.saldotracking.stats.Bucket
import dev.zurarul.saldotracking.ui.theme.LocalCashflowColors
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun RatioDonut(
    incomeShare: Float?,
    modifier: Modifier = Modifier
) {
    val cashflow = LocalCashflowColors.current
    val emptyColor = MaterialTheme.colorScheme.surfaceVariant
    val labelText = if (incomeShare == null) {
        "0%"
    } else {
        "${(incomeShare * 100f).roundToInt().coerceIn(0, 100)}%"
    }

    Box(
        modifier = modifier.size(84.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokePx = 11.dp.toPx()
            val inset = strokePx / 2f
            val arcSize = Size(size.width - strokePx, size.height - strokePx)
            val topLeft = Offset(inset, inset)

            if (incomeShare == null) {
                drawArc(
                    color = emptyColor,
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokePx)
                )
            } else {
                val clamped = incomeShare.coerceIn(0f, 1f)
                val incomeSweep = clamped * 360f
                drawArc(
                    color = cashflow.expense,
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokePx, cap = StrokeCap.Butt)
                )
                drawArc(
                    color = cashflow.income,
                    startAngle = -90f,
                    sweepAngle = incomeSweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokePx, cap = StrokeCap.Butt)
                )
            }
        }
        Text(
            text = labelText,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.ExtraBold
        )
    }
}

@Composable
fun RatioBar(
    incomeShare: Float?,
    modifier: Modifier = Modifier
) {
    val cashflow = LocalCashflowColors.current
    val emptyColor = MaterialTheme.colorScheme.surfaceVariant

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(RoundedCornerShape(50))
    ) {
        if (incomeShare == null) {
            drawRect(color = emptyColor, size = size)
        } else {
            val clamped = incomeShare.coerceIn(0f, 1f)
            val inWidth = size.width * clamped
            drawRect(color = cashflow.expense, size = size)
            drawRect(color = cashflow.income, size = Size(inWidth, size.height))
        }
    }
}

@Composable
fun CashflowBarChart(
    buckets: List<Bucket>,
    selectedIndex: Int?,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (buckets.isEmpty()) return
    val cashflow = LocalCashflowColors.current
    val highlightColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.14f)
    val maxVal = buckets.maxOf { max(it.incomeIdr, it.expenseIdr) }.coerceAtLeast(1L)
    val isScrollable = buckets.size > 12
    val scrollState = rememberScrollState()

    val rowModifier = if (isScrollable) {
        modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
    } else {
        modifier.fillMaxWidth()
    }

    Row(
        modifier = rowModifier.height(160.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        buckets.forEachIndexed { index, bucket ->
            val isSelected = selectedIndex == index
            val colModifier = if (isScrollable) {
                Modifier.width(30.dp)
            } else {
                Modifier.weight(1f)
            }

            Column(
                modifier = colModifier
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSelected) highlightColor else androidx.compose.ui.graphics.Color.Transparent)
                    .pointerInput(index) {
                        detectTapGestures { onSelect(index) }
                    }
                    .padding(horizontal = 2.dp, vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    val gap = 2.dp.toPx()
                    val barW = ((size.width - gap) / 2f).coerceAtLeast(3.dp.toPx())
                    val minBarH = 2.dp.toPx()

                    val inRatio = (bucket.incomeIdr.toDouble() / maxVal.toDouble()).toFloat().coerceIn(0f, 1f)
                    val outRatio = (bucket.expenseIdr.toDouble() / maxVal.toDouble()).toFloat().coerceIn(0f, 1f)

                    val inH = if (bucket.incomeIdr > 0L) (size.height * inRatio).coerceAtLeast(minBarH) else 0f
                    val outH = if (bucket.expenseIdr > 0L) (size.height * outRatio).coerceAtLeast(minBarH) else 0f

                    val startX = (size.width - (barW * 2f + gap)) / 2f
                    if (inH > 0f) {
                        drawRoundRect(
                            color = cashflow.income,
                            topLeft = Offset(startX, size.height - inH),
                            size = Size(barW, inH),
                            cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
                        )
                    }
                    if (outH > 0f) {
                        drawRoundRect(
                            color = cashflow.expense,
                            topLeft = Offset(startX + barW + gap, size.height - outH),
                            size = Size(barW, outH),
                            cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
                        )
                    }
                }
                Text(
                    text = bucket.label,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            }
        }
    }
}
