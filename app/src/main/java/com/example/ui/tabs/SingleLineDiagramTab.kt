package com.example.ui.tabs

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.calculation.ElectricalCalculator
import com.example.model.*
import com.example.ui.theme.*
import java.util.Locale

@Composable
fun SingleLineDiagramTab(
    panel: PanelDesign,
    summary: PanelCalculationSummary
) {
    val hScroll = rememberScrollState()
    val vScroll = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // SLD Legend Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = ElectricBlue, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("SLD Schematic: Busbars & Branch Protections", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("L1", color = PhaseL1Color, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    Text("L2", color = PhaseL2Color, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    Text("L3", color = PhaseL3Color, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    Text("N", color = PhaseNeutralColor, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    Text("PE", color = PhaseEarthColor, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Canvas container with dual scrolling for large schematics
        Card(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A))
        ) {
            val totalCircuits = panel.circuits.size
            val canvasWidthDp = maxOf(700, 160 + totalCircuits * 95).dp
            val canvasHeightDp = 620.dp

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .horizontalScroll(hScroll)
                    .verticalScroll(vScroll)
            ) {
                Canvas(
                    modifier = Modifier.size(width = canvasWidthDp, height = canvasHeightDp)
                ) {
                    val w = size.width
                    val midX = w / 2f

                    // Draw Incomer Supply Line
                    drawLine(
                        color = Color.White,
                        start = Offset(midX, 20f),
                        end = Offset(midX, 60f),
                        strokeWidth = 4f
                    )

                    // Incomer MCCB Box
                    drawRoundRect(
                        color = ElectricBlueDark,
                        topLeft = Offset(midX - 55f, 60f),
                        size = Size(110f, 65f),
                        cornerRadius = CornerRadius(8f, 8f)
                    )
                    drawRoundRect(
                        color = ElectricBlueLight,
                        topLeft = Offset(midX - 55f, 60f),
                        size = Size(110f, 65f),
                        cornerRadius = CornerRadius(8f, 8f),
                        style = Stroke(width = 2.5f)
                    )

                    // Text inside Incomer MCCB
                    drawContext.canvas.nativeCanvas.apply {
                        val paint = android.graphics.Paint().apply {
                            color = android.graphics.Color.WHITE
                            textSize = 28f
                            textAlign = android.graphics.Paint.Align.CENTER
                            isFakeBoldText = true
                            isAntiAlias = true
                        }
                        drawText("MCCB", midX, 90f, paint)
                        paint.textSize = 24f
                        paint.color = android.graphics.Color.rgb(56, 189, 248)
                        drawText("${panel.mainBreakerAmps}A", midX, 114f, paint)
                    }

                    // Feed from Incomer to Busbars
                    drawLine(
                        color = Color.White,
                        start = Offset(midX, 125f),
                        end = Offset(midX, 170f),
                        strokeWidth = 4f
                    )

                    // Busbars coordinates
                    val busLeft = 40f
                    val busRight = w - 40f
                    val busYStart = 170f
                    val busSpacing = 28f

                    // L1 Busbar
                    drawLine(PhaseL1Color, Offset(busLeft, busYStart), Offset(busRight, busYStart), strokeWidth = 6f)
                    // L2 Busbar
                    drawLine(PhaseL2Color, Offset(busLeft, busYStart + busSpacing), Offset(busRight, busYStart + busSpacing), strokeWidth = 6f)
                    // L3 Busbar
                    drawLine(PhaseL3Color, Offset(busLeft, busYStart + busSpacing * 2), Offset(busRight, busYStart + busSpacing * 2), strokeWidth = 6f)
                    // Neutral Busbar
                    drawLine(PhaseNeutralColor, Offset(busLeft, busYStart + busSpacing * 3), Offset(busRight, busYStart + busSpacing * 3), strokeWidth = 5f)
                    // Earth Busbar (dashed)
                    val dashedEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
                    drawLine(
                        color = PhaseEarthColor,
                        start = Offset(busLeft, busYStart + busSpacing * 4),
                        end = Offset(busRight, busYStart + busSpacing * 4),
                        strokeWidth = 4f,
                        pathEffect = dashedEffect
                    )

                    // Busbar Labels
                    drawContext.canvas.nativeCanvas.apply {
                        val textP = android.graphics.Paint().apply {
                            textSize = 22f
                            isFakeBoldText = true
                            isAntiAlias = true
                        }
                        textP.color = android.graphics.Color.rgb(239, 68, 68)
                        drawText("L1 (400V)", 45f, busYStart - 8f, textP)

                        textP.color = android.graphics.Color.rgb(245, 158, 11)
                        drawText("L2 (400V)", 45f, busYStart + busSpacing - 8f, textP)

                        textP.color = android.graphics.Color.rgb(59, 130, 246)
                        drawText("L3 (400V)", 45f, busYStart + busSpacing * 2 - 8f, textP)

                        textP.color = android.graphics.Color.rgb(6, 182, 212)
                        drawText("N Neutral", 45f, busYStart + busSpacing * 3 - 8f, textP)

                        textP.color = android.graphics.Color.rgb(34, 197, 94)
                        drawText("PE Earth", 45f, busYStart + busSpacing * 4 - 8f, textP)
                    }

                    // Draw Circuits Feeders
                    val feederStartX = 90f
                    val feederSpacing = 95f
                    val feederTopY = busYStart + busSpacing * 4 + 20f

                    for ((idx, circuit) in panel.circuits.withIndex()) {
                        val cx = feederStartX + idx * feederSpacing
                        val busTapY = when (circuit.assignedPhase) {
                            Phase.L1 -> busYStart
                            Phase.L2 -> busYStart + busSpacing
                            Phase.L3 -> busYStart + busSpacing * 2
                            else -> busYStart
                        }

                        val phaseColor = when (circuit.assignedPhase) {
                            Phase.L1 -> PhaseL1Color
                            Phase.L2 -> PhaseL2Color
                            Phase.L3 -> PhaseL3Color
                            Phase.THREE_PHASE -> PhaseThreePhaseColor
                        }

                        // Connection Dot on busbar
                        drawCircle(color = phaseColor, radius = 6f, center = Offset(cx, busTapY))

                        // Tap line down
                        drawLine(
                            color = phaseColor,
                            start = Offset(cx, busTapY),
                            end = Offset(cx, feederTopY + 20f),
                            strokeWidth = 3f
                        )

                        // Branch MCB Symbol
                        val mcbBoxTop = feederTopY + 20f
                        drawRoundRect(
                            color = Color(0xFF1E293B),
                            topLeft = Offset(cx - 32f, mcbBoxTop),
                            size = Size(64f, 75f),
                            cornerRadius = CornerRadius(6f, 6f)
                        )
                        drawRoundRect(
                            color = phaseColor,
                            topLeft = Offset(cx - 32f, mcbBoxTop),
                            size = Size(64f, 75f),
                            cornerRadius = CornerRadius(6f, 6f),
                            style = Stroke(width = 2f)
                        )

                        // MCB Text
                        drawContext.canvas.nativeCanvas.apply {
                            val tp = android.graphics.Paint().apply {
                                color = android.graphics.Color.WHITE
                                textSize = 20f
                                textAlign = android.graphics.Paint.Align.CENTER
                                isFakeBoldText = true
                                isAntiAlias = true
                            }
                            drawText("C${circuit.circuitNumber}", cx, mcbBoxTop + 24f, tp)
                            tp.textSize = 22f
                            tp.color = android.graphics.Color.rgb(241, 245, 249)
                            drawText("${circuit.ratingAmps}A", cx, mcbBoxTop + 48f, tp)
                            tp.textSize = 17f
                            tp.color = android.graphics.Color.rgb(148, 163, 184)
                            drawText(circuit.assignedPhase.shortName, cx, mcbBoxTop + 68f, tp)
                        }

                        // Line to Load Terminal
                        val mcbBottom = mcbBoxTop + 75f
                        drawLine(
                            color = Color(0xFF64748B),
                            start = Offset(cx, mcbBottom),
                            end = Offset(cx, mcbBottom + 45f),
                            strokeWidth = 2.5f
                        )

                        // Load Symbol circle
                        val loadY = mcbBottom + 65f
                        drawCircle(
                            color = Color(0xFF334155),
                            radius = 20f,
                            center = Offset(cx, loadY)
                        )
                        drawCircle(
                            color = ElectricBlue,
                            radius = 20f,
                            center = Offset(cx, loadY),
                            style = Stroke(width = 2f)
                        )

                        // Load details text
                        drawContext.canvas.nativeCanvas.apply {
                            val lp = android.graphics.Paint().apply {
                                color = android.graphics.Color.rgb(224, 242, 254)
                                textSize = 17f
                                textAlign = android.graphics.Paint.Align.CENTER
                                isFakeBoldText = true
                                isAntiAlias = true
                            }
                            drawText("M", cx, loadY + 6f, lp)

                            lp.textSize = 17f
                            lp.color = android.graphics.Color.rgb(203, 213, 225)
                            val shortName = if (circuit.name.length > 11) circuit.name.substring(0, 9) + ".." else circuit.name
                            drawText(shortName, cx, loadY + 36f, lp)

                            lp.textSize = 18f
                            lp.color = android.graphics.Color.rgb(56, 189, 248)
                            drawText("${circuit.powerWatts.toInt()}W", cx, loadY + 56f, lp)
                        }
                    }
                }
            }
        }
    }
}
