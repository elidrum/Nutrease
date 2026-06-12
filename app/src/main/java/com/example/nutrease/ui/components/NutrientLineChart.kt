package com.example.nutrease.ui.components

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nutrease.R
import com.example.nutrease.domain.model.DailyNutrientPoint
import kotlinx.datetime.LocalDate

/**
 * Line chart dell'andamento giornaliero di un nutriente (RF21), disegnato a mano con
 * [Canvas] Compose: nessuna libreria esterna per un grafico così semplice.
 *
 * Idea di base: i dati si mappano in pixel con due proporzioni lineari —
 * x = indice del giorno spalmato sulla larghezza utile, y = valore/maxValue spalmato
 * sull'altezza utile (invertita: in Canvas la y cresce verso il basso). I padding
 * riservano lo spazio per le etichette degli assi, disegnate via `nativeCanvas.drawText`
 * (il testo non è tra le primitive di DrawScope).
 */
@Composable
fun NutrientLineChart(
    points: List<DailyNutrientPoint>,
    lineColor: Color,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val axisColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant

    // Conversioni dp/sp → pixel fatte fuori dal Canvas: dentro DrawScope si lavora solo in px.
    val labelTextSizePx = with(density) { 10.sp.toPx() }
    val dotRadiusPx = with(density) { 3.dp.toPx() }
    val strokeWidthPx = with(density) { 2.dp.toPx() }
    val paddingStartPx = with(density) { 36.dp.toPx() }
    val paddingEndPx = with(density) { 12.dp.toPx() }
    val paddingTopPx = with(density) { 12.dp.toPx() }
    val paddingBottomPx = with(density) { 24.dp.toPx() }

    val maxValue = points.maxOfOrNull { it.value } ?: 0.0
    val hasData = points.isNotEmpty() && maxValue > 0.0

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp),
        contentAlignment = Alignment.Center
    ) {
        if (!hasData) {
            Text(
                text = stringResource(R.string.nutrient_chart_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@Box
        }

        val scaledMax = maxValue.coerceAtLeast(1.0)
        val labelArgb = labelColor.toArgb()

        Canvas(modifier = Modifier.fillMaxWidth().height(200.dp)) {
            val plotLeft = paddingStartPx
            val plotTop = paddingTopPx
            val plotRight = size.width - paddingEndPx
            val plotBottom = size.height - paddingBottomPx
            val plotWidth = plotRight - plotLeft
            val plotHeight = plotBottom - plotTop

            val labelPaint = Paint().apply {
                color = labelArgb
                textSize = labelTextSizePx
                isAntiAlias = true
                typeface = Typeface.DEFAULT
            }

            // Griglia orizzontale: 4 linee a 0%, 33%, 66% e 100% del massimo, con il
            // valore corrispondente come etichetta sull'asse y.
            for (i in 0..3) {
                val fraction = i / 3.0
                val y = plotBottom - (plotHeight * fraction).toFloat()
                drawLine(
                    color = gridColor,
                    start = Offset(plotLeft, y),
                    end = Offset(plotRight, y),
                    strokeWidth = 1f
                )
                val tickValue = scaledMax * fraction
                drawContext.canvas.nativeCanvas.drawText(
                    formatTick(tickValue),
                    4f,
                    y + labelTextSizePx / 3f,
                    labelPaint
                )
            }

            drawLine(
                color = axisColor,
                start = Offset(plotLeft, plotBottom),
                end = Offset(plotRight, plotBottom),
                strokeWidth = 1f
            )

            val stepX = if (points.size > 1) plotWidth / (points.size - 1) else 0f

            // Etichette x diradate (una a settimana + l'ultimo giorno): 30 date non ci stanno.
            val labelIndices = points.indices.filter { it % 7 == 0 || it == points.size - 1 }
            labelIndices.forEach { i ->
                val x = plotLeft + i * stepX
                drawContext.canvas.nativeCanvas.drawText(
                    formatDayShort(points[i].date),
                    x - labelTextSizePx * 1.5f,
                    plotBottom + labelTextSizePx + 6f,
                    labelPaint
                )
            }

            // Dati → pixel: y parte dal fondo e sale in proporzione a valore/massimo.
            val plotPoints = points.mapIndexed { i, p ->
                Offset(
                    x = plotLeft + i * stepX,
                    y = plotBottom - ((p.value / scaledMax) * plotHeight).toFloat()
                )
            }
            for (i in 0 until plotPoints.size - 1) {
                drawLine(
                    color = lineColor,
                    start = plotPoints[i],
                    end = plotPoints[i + 1],
                    strokeWidth = strokeWidthPx
                )
            }
            plotPoints.forEach { p ->
                drawCircle(color = lineColor, radius = dotRadiusPx, center = p)
            }
        }
    }
}

/** Precisione adattiva dell'asse y: interi sopra 100, un decimale sotto 10. */
private fun formatTick(value: Double): String = when {
    value >= 100.0 -> value.toInt().toString()
    value >= 10.0 -> "%.0f".format(value)
    else -> "%.1f".format(value)
}

private fun formatDayShort(date: LocalDate): String {
    val day = date.day.toString().padStart(2, '0')
    val month = (date.month.ordinal + 1).toString().padStart(2, '0')
    return "$day/$month"
}