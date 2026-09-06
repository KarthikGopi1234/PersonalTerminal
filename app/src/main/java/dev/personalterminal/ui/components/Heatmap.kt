package dev.personalterminal.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import dev.personalterminal.ui.theme.Term
import java.time.LocalDate

/**
 * GitHub-style contribution heatmap. Columns = weeks (Mon→Sun rows), newest week on the right.
 * [values] maps epochDay → intensity 0..1.
 */
@Composable
fun ContributionHeatmap(
    values: Map<Long, Float>,
    weeks: Int,
    modifier: Modifier = Modifier,
    end: LocalDate = LocalDate.now(),
    color: Color = Term.palette.green,
    onDayClick: ((LocalDate) -> Unit)? = null,
) {
    val p = Term.palette
    val density = LocalDensity.current
    val cell = 12.dp
    val gap = 3.dp
    val cellPx = with(density) { cell.toPx() }
    val gapPx = with(density) { gap.toPx() }
    // Align columns to Monday
    val endWeekStart = end.minusDays((end.dayOfWeek.value - 1).toLong())
    val firstWeekStart = endWeekStart.minusWeeks((weeks - 1).toLong())
    val width = cell * weeks + gap * (weeks - 1)
    val height = cell * 7 + gap * 6

    Column(modifier) {
        // month labels
        Row(Modifier.width(width), horizontalArrangement = Arrangement.SpaceBetween) {
            val labels = (0 until weeks step (weeks / 4).coerceAtLeast(1)).map { firstWeekStart.plusWeeks(it.toLong()) }
            labels.forEach { d -> Text(d.month.name.take(3).lowercase(), color = p.fgDim, style = MaterialTheme.typography.labelSmall) }
        }
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.height(height), verticalArrangement = Arrangement.SpaceBetween) {
                listOf("mon", "", "wed", "", "fri", "", "sun").forEach {
                    Text(it, color = p.fgDim, style = MaterialTheme.typography.labelSmall, modifier = Modifier.height(cell))
                }
            }
            Spacer(Modifier.width(6.dp))
            Canvas(
                Modifier
                    .width(width)
                    .height(height)
                    .pointerInput(onDayClick, firstWeekStart) {
                        if (onDayClick == null) return@pointerInput
                        detectTapGestures { pos ->
                            val col = (pos.x / (cellPx + gapPx)).toInt()
                            val row = (pos.y / (cellPx + gapPx)).toInt()
                            if (col in 0 until weeks && row in 0..6) {
                                val d = firstWeekStart.plusWeeks(col.toLong()).plusDays(row.toLong())
                                if (!d.isAfter(end)) onDayClick(d)
                            }
                        }
                    },
            ) {
                for (w in 0 until weeks) {
                    for (d in 0..6) {
                        val date = firstWeekStart.plusWeeks(w.toLong()).plusDays(d.toLong())
                        if (date.isAfter(end)) continue
                        val v = values[date.toEpochDay()] ?: 0f
                        val c = when {
                            v <= 0f -> p.bgHighlight.copy(alpha = if (p.dark) 0.6f else 1f)
                            v < 0.25f -> color.copy(alpha = 0.3f)
                            v < 0.5f -> color.copy(alpha = 0.5f)
                            v < 0.75f -> color.copy(alpha = 0.75f)
                            else -> color
                        }
                        drawRoundRect(
                            color = c,
                            topLeft = Offset(w * (cellPx + gapPx), d * (cellPx + gapPx)),
                            size = Size(cellPx, cellPx),
                            cornerRadius = CornerRadius(2.dp.toPx()),
                        )
                        if (date == end) {
                            drawRoundRect(
                                color = p.fg, topLeft = Offset(w * (cellPx + gapPx), d * (cellPx + gapPx)),
                                size = Size(cellPx, cellPx), cornerRadius = CornerRadius(2.dp.toPx()),
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx()),
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.width(width + 30.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            Text("less ", color = p.fgDim, style = MaterialTheme.typography.labelSmall)
            listOf(0f, 0.2f, 0.4f, 0.7f, 1f).forEach { v ->
                val c = when {
                    v <= 0f -> p.bgHighlight
                    v < 0.25f -> color.copy(alpha = 0.3f)
                    v < 0.5f -> color.copy(alpha = 0.5f)
                    v < 0.75f -> color.copy(alpha = 0.75f)
                    else -> color
                }
                Canvas(Modifier.width(cell).height(cell)) { drawRoundRect(c, cornerRadius = CornerRadius(2.dp.toPx())) }
                Spacer(Modifier.width(gap))
            }
            Text(" more", color = p.fgDim, style = MaterialTheme.typography.labelSmall)
        }
    }
}

/** Compact one-row sparkline of the last N days: `▁▃▅█▇▂▁` */
fun sparkline(values: List<Float>): String {
    val blocks = "▁▂▃▄▅▆▇█"
    return values.joinToString("") { v ->
        val idx = (v.coerceIn(0f, 1f) * (blocks.length - 1)).toInt()
        blocks[idx].toString()
    }
}
