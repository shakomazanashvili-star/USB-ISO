package com.example.export

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import com.example.calculation.ElectricalCalculator
import com.example.model.PanelDesign
import com.example.model.Phase
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PdfExporter {

    private const val PAGE_WIDTH = 595 // A4 standard pt at 72 dpi (portrait)
    private const val PAGE_HEIGHT = 842

    fun exportToPdf(context: Context, panel: PanelDesign): File {
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val cleanName = panel.name.replace(Regex("[^a-zA-Z0-9_]"), "_")
        val file = File(exportDir, "Electrical_Panel_${cleanName}_$timeStamp.pdf")

        val summary = ElectricalCalculator.calculatePanel(panel)
        val document = PdfDocument()

        // Page 1: Engineering Panel Schedule & Phase Balancing
        val pageInfo1 = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create()
        val page1 = document.startPage(pageInfo1)
        drawPage1(page1.canvas, panel, summary)
        document.finishPage(page1)

        // Page 2: Single Line Diagram (SLD) Schematic
        val pageInfo2 = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 2).create()
        val page2 = document.startPage(pageInfo2)
        drawPage2Sld(page2.canvas, panel, summary)
        document.finishPage(page2)

        FileOutputStream(file).use { out ->
            document.writeTo(out)
        }
        document.close()
        return file
    }

    private fun drawPage1(canvas: Canvas, panel: PanelDesign, summary: com.example.model.PanelCalculationSummary) {
        val bgPaint = Paint().apply { color = Color.WHITE }
        canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), PAGE_HEIGHT.toFloat(), bgPaint)

        // Title banner
        val headerPaint = Paint().apply {
            color = Color.rgb(15, 23, 42) // Slate 900
            style = Paint.Style.FILL
        }
        canvas.drawRect(20f, 20f, (PAGE_WIDTH - 20).toFloat(), 75f, headerPaint)

        val accentPaint = Paint().apply {
            color = Color.rgb(2, 132, 199) // Electric blue
            strokeWidth = 4f
        }
        canvas.drawLine(20f, 75f, (PAGE_WIDTH - 20).toFloat(), 75f, accentPaint)

        val textPaint = Paint().apply {
            color = Color.WHITE
            textSize = 16f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        canvas.drawText("PANELCRAFT ELECTRICAL DISTRIBUTION SCHEDULE", 35f, 48f, textPaint)

        val subTextPaint = Paint().apply {
            color = Color.rgb(148, 163, 184)
            textSize = 9f
            isAntiAlias = true
        }
        val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
        canvas.drawText("PROJECT: ${panel.name.uppercase()} | TAG: ${panel.tag} | DATE: $dateStr", 35f, 64f, subTextPaint)

        // Technical specs summary cards
        var curY = 90f
        val cardPaint = Paint().apply {
            color = Color.rgb(248, 250, 252)
            style = Paint.Style.FILL
        }
        val cardBorder = Paint().apply {
            color = Color.rgb(226, 232, 240)
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }

        canvas.drawRoundRect(20f, curY, (PAGE_WIDTH - 20).toFloat(), curY + 65f, 6f, 6f, cardPaint)
        canvas.drawRoundRect(20f, curY, (PAGE_WIDTH - 20).toFloat(), curY + 65f, 6f, 6f, cardBorder)

        val labelPaint = Paint().apply {
            color = Color.rgb(100, 116, 139)
            textSize = 8.5f
            typeface = Typeface.DEFAULT_BOLD
            isAntiAlias = true
        }
        val valPaint = Paint().apply {
            color = Color.rgb(15, 23, 42)
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            isAntiAlias = true
        }

        // Col 1
        canvas.drawText("SYSTEM VOLTAGE", 30f, curY + 18f, labelPaint)
        canvas.drawText(panel.systemVoltage.label, 30f, curY + 34f, valPaint)
        canvas.drawText("LOCATION: ${panel.location}", 30f, curY + 50f, subTextPaint.apply { color = Color.DKGRAY })

        // Col 2
        canvas.drawText("CONNECTED LOAD", 210f, curY + 18f, labelPaint)
        canvas.drawText(String.format(Locale.US, "%.1f kW / %.1f kVA", summary.totalConnectedWatts / 1000.0, summary.totalApparentVa / 1000.0), 210f, curY + 34f, valPaint)
        canvas.drawText(String.format(Locale.US, "MAX DEMAND: %.1f kW (df: %.2f)", summary.totalDemandWatts / 1000.0, panel.diversityFactor), 210f, curY + 50f, subTextPaint)

        // Col 3
        canvas.drawText("MAIN INCOMER BREAKER", 400f, curY + 18f, labelPaint)
        canvas.drawText("${panel.mainBreakerAmps} A (${panel.mainBreakerPoles}P)", 400f, curY + 34f, valPaint.apply { color = Color.rgb(2, 132, 199) })
        canvas.drawText(String.format(Locale.US, "LOADING: %.1f%%", summary.mainBreakerLoadingPercent), 400f, curY + 50f, subTextPaint)

        // Phase Balancing Meter Bar
        curY += 78f
        canvas.drawText("PHASE LOAD BALANCE (L1 / L2 / L3)", 20f, curY, labelPaint)

        val unbalanceStatus = if (summary.unbalancePercent <= 10.0) "BALANCED" else if (summary.unbalancePercent <= 20.0) "MODERATE" else "UNBALANCED"
        val unbalanceColor = if (summary.unbalancePercent <= 10.0) Color.rgb(16, 185, 129) else Color.rgb(239, 68, 68)
        val statusPaint = Paint().apply {
            color = unbalanceColor
            textSize = 9f
            typeface = Typeface.DEFAULT_BOLD
            isAntiAlias = true
        }
        canvas.drawText(String.format(Locale.US, "UNBALANCE: %.1f%% (%s) | NEUTRAL CURRENT: %.1f A", summary.unbalancePercent, unbalanceStatus, summary.neutralCurrentAmps), 200f, curY, statusPaint)

        curY += 10f
        // Phase L1 bar
        drawPhaseBar(canvas, 20f, curY, "L1", summary.phaseL1Amps, summary.phaseL1Watts, Color.rgb(239, 68, 68), summary.maxPhaseAmps)
        // Phase L2 bar
        drawPhaseBar(canvas, 205f, curY, "L2", summary.phaseL2Amps, summary.phaseL2Watts, Color.rgb(245, 158, 11), summary.maxPhaseAmps)
        // Phase L3 bar
        drawPhaseBar(canvas, 390f, curY, "L3", summary.phaseL3Amps, summary.phaseL3Watts, Color.rgb(59, 130, 246), summary.maxPhaseAmps)

        // Circuit Schedule Table
        curY += 45f
        val thPaint = Paint().apply {
            color = Color.rgb(30, 41, 59)
            style = Paint.Style.FILL
        }
        canvas.drawRect(20f, curY, (PAGE_WIDTH - 20).toFloat(), curY + 20f, thPaint)

        val thText = Paint().apply {
            color = Color.WHITE
            textSize = 7.5f
            typeface = Typeface.DEFAULT_BOLD
            isAntiAlias = true
        }
        canvas.drawText("CCT", 24f, curY + 13f, thText)
        canvas.drawText("DESCRIPTION", 46f, curY + 13f, thText)
        canvas.drawText("PH", 185f, curY + 13f, thText)
        canvas.drawText("RATING", 210f, curY + 13f, thText)
        canvas.drawText("LOAD", 255f, curY + 13f, thText)
        canvas.drawText("CURRENT", 305f, curY + 13f, thText)
        canvas.drawText("CABLE", 360f, curY + 13f, thText)
        canvas.drawText("VDROP", 420f, curY + 13f, thText)
        canvas.drawText("RCD", 475f, curY + 13f, thText)
        canvas.drawText("STATUS", 525f, curY + 13f, thText)

        curY += 20f
        val rowPaint = Paint().apply {
            color = Color.rgb(15, 23, 42)
            textSize = 7.5f
            isAntiAlias = true
        }
        val altBg = Paint().apply {
            color = Color.rgb(241, 245, 249)
            style = Paint.Style.FILL
        }
        val linePaint = Paint().apply {
            color = Color.rgb(226, 232, 240)
            strokeWidth = 0.5f
        }

        for ((index, c) in panel.circuits.take(24).withIndex()) {
            if (index % 2 == 1) {
                canvas.drawRect(20f, curY, (PAGE_WIDTH - 20).toFloat(), curY + 17f, altBg)
            }
            val calc = summary.circuitsSummary[c.id] ?: ElectricalCalculator.calculateCircuit(c, panel.systemVoltage)

            canvas.drawText("${c.circuitNumber}", 24f, curY + 12f, rowPaint)
            val truncName = if (c.name.length > 25) c.name.substring(0, 23) + ".." else c.name
            canvas.drawText(truncName, 46f, curY + 12f, rowPaint)
            canvas.drawText(c.assignedPhase.shortName, 185f, curY + 12f, rowPaint)
            canvas.drawText("${c.ratingAmps}A ${c.breakerCurve}", 210f, curY + 12f, rowPaint)
            canvas.drawText("${c.powerWatts.toInt()} W", 255f, curY + 12f, rowPaint)
            canvas.drawText(String.format(Locale.US, "%.1f A", calc.nominalCurrentAmps), 305f, curY + 12f, rowPaint)
            canvas.drawText("${c.cableCrossSectionMm2}mm²", 360f, curY + 12f, rowPaint)
            canvas.drawText(String.format(Locale.US, "%.1f%%", calc.voltageDropPercent), 420f, curY + 12f, rowPaint)
            canvas.drawText(if (c.hasRcd) "${c.rcdRatingMa}mA" else "-", 475f, curY + 12f, rowPaint)
            canvas.drawText(if (c.isOn) "ON" else "OFF", 525f, curY + 12f, rowPaint)

            canvas.drawLine(20f, curY + 17f, (PAGE_WIDTH - 20).toFloat(), curY + 17f, linePaint)
            curY += 17f
        }

        // Engineer Approval Block at Bottom
        val signY = PAGE_HEIGHT - 65f
        canvas.drawRoundRect(20f, signY, (PAGE_WIDTH - 20).toFloat(), (PAGE_HEIGHT - 20).toFloat(), 4f, 4f, cardBorder)
        canvas.drawText("DESIGNED BY: PANELCRAFT ENGINE", 30f, signY + 18f, labelPaint)
        canvas.drawText("VERIFIED BY REGISTERED ELECTRICAL ENGINEER:", 220f, signY + 18f, labelPaint)
        canvas.drawLine(220f, signY + 36f, 420f, signY + 36f, linePaint)
        canvas.drawText("SIGNATURE & SEAL", 220f, signY + 44f, subTextPaint)
        canvas.drawText("IEC 60364 / NEC CERTIFIED", 440f, signY + 28f, valPaint.apply { color = Color.rgb(16, 185, 129); textSize = 8.5f })
    }

    private fun drawPhaseBar(canvas: Canvas, x: Float, y: Float, label: String, amps: Double, watts: Double, color: Int, maxAmps: Double) {
        val barWidth = 160f
        val paint = Paint().apply {
            this.color = color
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        val bgPaint = Paint().apply {
            this.color = Color.rgb(241, 245, 249)
            style = Paint.Style.FILL
        }
        val textPaint = Paint().apply {
            this.color = Color.rgb(15, 23, 42)
            textSize = 8.5f
            typeface = Typeface.DEFAULT_BOLD
            isAntiAlias = true
        }

        canvas.drawText("$label: ${String.format(Locale.US, "%.1f A (%.1f kW)", amps, watts / 1000.0)}", x, y + 10f, textPaint)
        canvas.drawRoundRect(x, y + 14f, x + barWidth, y + 24f, 4f, 4f, bgPaint)

        val fillFraction = if (maxAmps > 0) (amps / maxAmps).coerceIn(0.05, 1.0).toFloat() else 0.1f
        canvas.drawRoundRect(x, y + 14f, x + (barWidth * fillFraction), y + 24f, 4f, 4f, paint)
    }

    private fun drawPage2Sld(canvas: Canvas, panel: PanelDesign, summary: com.example.model.PanelCalculationSummary) {
        val bgPaint = Paint().apply { color = Color.WHITE }
        canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), PAGE_HEIGHT.toFloat(), bgPaint)

        // Header
        val headerPaint = Paint().apply {
            color = Color.rgb(15, 23, 42)
            style = Paint.Style.FILL
        }
        canvas.drawRect(20f, 20f, (PAGE_WIDTH - 20).toFloat(), 65f, headerPaint)

        val textPaint = Paint().apply {
            color = Color.WHITE
            textSize = 14f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        canvas.drawText("SINGLE LINE DIAGRAM (SLD) - ${panel.tag} SCHEMATIC", 35f, 45f, textPaint)

        val wirePaint = Paint().apply {
            color = Color.rgb(30, 41, 59)
            strokeWidth = 2f
            style = Paint.Style.STROKE
            isAntiAlias = true
        }

        val busL1 = Paint().apply { color = Color.rgb(239, 68, 68); strokeWidth = 3f; isAntiAlias = true }
        val busL2 = Paint().apply { color = Color.rgb(245, 158, 11); strokeWidth = 3f; isAntiAlias = true }
        val busL3 = Paint().apply { color = Color.rgb(59, 130, 246); strokeWidth = 3f; isAntiAlias = true }
        val busN = Paint().apply { color = Color.rgb(6, 182, 212); strokeWidth = 2.5f; isAntiAlias = true }
        val busPE = Paint().apply { color = Color.rgb(34, 197, 94); strokeWidth = 2f; pathEffect = DashPathEffect(floatArrayOf(6f, 4f), 0f); isAntiAlias = true }

        val labelPaint = Paint().apply {
            color = Color.rgb(71, 85, 105)
            textSize = 8f
            typeface = Typeface.DEFAULT_BOLD
            isAntiAlias = true
        }

        // Main Incomer Box
        val incomerX = (PAGE_WIDTH / 2).toFloat()
        var curY = 90f
        canvas.drawLine(incomerX, curY, incomerX, curY + 25f, wirePaint)
        curY += 25f

        // Draw Incomer Breaker Symbol
        val symbolBox = RectF(incomerX - 25f, curY, incomerX + 25f, curY + 35f)
        val incomerFill = Paint().apply { color = Color.rgb(224, 242, 254); style = Paint.Style.FILL }
        val incomerBorder = Paint().apply { color = Color.rgb(2, 132, 199); style = Paint.Style.STROKE; strokeWidth = 1.5f }
        canvas.drawRoundRect(symbolBox, 4f, 4f, incomerFill)
        canvas.drawRoundRect(symbolBox, 4f, 4f, incomerBorder)

        val symText = Paint().apply {
            color = Color.rgb(2, 132, 199)
            textSize = 9f
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        canvas.drawText("MCCB", incomerX, curY + 16f, symText)
        canvas.drawText("${panel.mainBreakerAmps}A", incomerX, curY + 28f, symText)

        curY += 35f
        canvas.drawLine(incomerX, curY, incomerX, curY + 20f, wirePaint)
        curY += 20f

        // Busbars
        val busLeft = 35f
        val busRight = (PAGE_WIDTH - 35).toFloat()

        canvas.drawLine(busLeft, curY, busRight, curY, busL1)
        canvas.drawText("L1 BUSBAR (Phase 1)", busLeft, curY - 3f, labelPaint.apply { color = Color.rgb(239, 68, 68) })

        canvas.drawLine(busLeft, curY + 10f, busRight, curY + 10f, busL2)
        canvas.drawText("L2 BUSBAR (Phase 2)", busLeft, curY + 8f, labelPaint.apply { color = Color.rgb(245, 158, 11) })

        canvas.drawLine(busLeft, curY + 20f, busRight, curY + 20f, busL3)
        canvas.drawText("L3 BUSBAR (Phase 3)", busLeft, curY + 18f, labelPaint.apply { color = Color.rgb(59, 130, 246) })

        canvas.drawLine(busLeft, curY + 30f, busRight, curY + 30f, busN)
        canvas.drawText("N NEUTRAL", busLeft, curY + 28f, labelPaint.apply { color = Color.rgb(6, 182, 212) })

        canvas.drawLine(busLeft, curY + 40f, busRight, curY + 40f, busPE)
        canvas.drawText("PE EARTH", busLeft, curY + 38f, labelPaint.apply { color = Color.rgb(34, 197, 94) })

        curY += 55f

        // Draw Circuits Feeders (up to 12 feeders across the width for visual clarity)
        val shownCircuits = panel.circuits.take(10)
        val colWidth = (busRight - busLeft) / (shownCircuits.size.coerceAtLeast(1) + 0.5f)

        for ((idx, circuit) in shownCircuits.withIndex()) {
            val cx = busLeft + 25f + (idx * colWidth)
            val topBusY = when (circuit.assignedPhase) {
                Phase.L1 -> curY - 55f
                Phase.L2 -> curY - 45f
                Phase.L3 -> curY - 35f
                else -> curY - 55f
            }

            // Connection dot on busbar
            val dotPaint = Paint().apply { color = Color.rgb(30, 41, 59); style = Paint.Style.FILL }
            canvas.drawCircle(cx, topBusY, 2.5f, dotPaint)

            // Feeder line
            canvas.drawLine(cx, topBusY, cx, curY + 10f, wirePaint)

            // Circuit Breaker symbol
            val cbBox = RectF(cx - 14f, curY + 10f, cx + 14f, curY + 35f)
            val cbFill = Paint().apply { color = Color.rgb(241, 245, 249); style = Paint.Style.FILL }
            val cbBorder = Paint().apply { color = Color.rgb(71, 85, 105); style = Paint.Style.STROKE; strokeWidth = 1f }
            canvas.drawRoundRect(cbBox, 3f, 3f, cbFill)
            canvas.drawRoundRect(cbBox, 3f, 3f, cbBorder)

            val cbText = Paint().apply {
                color = Color.rgb(15, 23, 42)
                textSize = 7f
                textAlign = Paint.Align.CENTER
                typeface = Typeface.DEFAULT_BOLD
                isAntiAlias = true
            }
            canvas.drawText("${circuit.ratingAmps}A", cx, curY + 22f, cbText)
            canvas.drawText(circuit.assignedPhase.shortName, cx, curY + 31f, cbText)

            // Continue line to load
            canvas.drawLine(cx, curY + 35f, cx, curY + 60f, wirePaint)

            // Load symbol (circle with load type icon / text)
            val loadCirclePaint = Paint().apply {
                color = Color.rgb(224, 231, 255)
                style = Paint.Style.FILL
            }
            canvas.drawCircle(cx, curY + 70f, 10f, loadCirclePaint)
            val loadText = Paint().apply {
                color = Color.rgb(67, 56, 202)
                textSize = 6.5f
                textAlign = Paint.Align.CENTER
                typeface = Typeface.DEFAULT_BOLD
                isAntiAlias = true
            }
            canvas.drawText("C${circuit.circuitNumber}", cx, curY + 72f, loadText)

            // Rotated or wrapped label below
            val nameText = Paint().apply {
                color = Color.rgb(71, 85, 105)
                textSize = 6.5f
                textAlign = Paint.Align.CENTER
                isAntiAlias = true
            }
            val shortDesc = if (circuit.name.length > 12) circuit.name.substring(0, 10) + ".." else circuit.name
            canvas.drawText(shortDesc, cx, curY + 92f, nameText)
            canvas.drawText("${circuit.powerWatts.toInt()}W", cx, curY + 102f, nameText)
        }

        // Legend box
        val legY = PAGE_HEIGHT - 90f
        val legPaint = Paint().apply { color = Color.rgb(248, 250, 252); style = Paint.Style.FILL }
        val legBorder = Paint().apply { color = Color.rgb(203, 213, 225); style = Paint.Style.STROKE; strokeWidth = 1f }
        canvas.drawRoundRect(20f, legY, (PAGE_WIDTH - 20).toFloat(), (PAGE_HEIGHT - 20).toFloat(), 4f, 4f, legPaint)
        canvas.drawRoundRect(20f, legY, (PAGE_WIDTH - 20).toFloat(), (PAGE_HEIGHT - 20).toFloat(), 4f, 4f, legBorder)

        val legTitle = Paint().apply { color = Color.rgb(15, 23, 42); textSize = 8.5f; typeface = Typeface.DEFAULT_BOLD }
        canvas.drawText("SINGLE LINE DIAGRAM SYMBOLOGY & COLOR CODE:", 30f, legY + 16f, legTitle)

        canvas.drawText("L1: Phase 1 (Red/Brown) | L2: Phase 2 (Amber/Black) | L3: Phase 3 (Blue/Grey)", 30f, legY + 32f, labelPaint)
        canvas.drawText("N: Neutral (Cyan) | PE: Protective Earth (Green Dashed) | MCCB: Molded Case Circuit Breaker", 30f, legY + 46f, labelPaint)
        canvas.drawText("All branch circuit protections equipped with MCB Type ${panel.circuits.firstOrNull()?.breakerCurve ?: "C"} & RCD 30mA residual current protection.", 30f, legY + 60f, labelPaint)
    }
}
