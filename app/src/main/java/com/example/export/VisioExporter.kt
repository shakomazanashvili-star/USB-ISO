package com.example.export

import android.content.Context
import com.example.calculation.ElectricalCalculator
import com.example.model.PanelDesign
import com.example.model.Phase
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object VisioExporter {

    /**
     * Exports as Microsoft Visio XML Drawing (.vdx)
     * Compatible with Microsoft Visio 2003 through Visio 365, LibreOffice Draw, and CAD tools.
     */
    fun exportToVisioVdx(context: Context, panel: PanelDesign): File {
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val cleanName = panel.name.replace(Regex("[^a-zA-Z0-9_]"), "_")
        val file = File(exportDir, "Electrical_Panel_${cleanName}_$timeStamp.vdx")

        val summary = ElectricalCalculator.calculatePanel(panel)
        val vdxXml = buildVisioVdxXml(panel, summary)

        FileOutputStream(file).use { out ->
            out.write(vdxXml.toByteArray(Charsets.UTF_8))
        }
        return file
    }

    /**
     * Exports as Microsoft Visio-compatible SVG vector schematic
     * Microsoft Visio imports SVG files directly as fully ungroupable, editable shapes.
     */
    fun exportToVisioSvg(context: Context, panel: PanelDesign): File {
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val cleanName = panel.name.replace(Regex("[^a-zA-Z0-9_]"), "_")
        val file = File(exportDir, "Visio_Schematic_${cleanName}_$timeStamp.svg")

        val summary = ElectricalCalculator.calculatePanel(panel)
        val svgContent = buildVisioSvg(panel, summary)

        FileOutputStream(file).use { out ->
            out.write(svgContent.toByteArray(Charsets.UTF_8))
        }
        return file
    }

    private fun buildVisioVdxXml(panel: PanelDesign, summary: com.example.model.PanelCalculationSummary): String {
        val sb = StringBuilder()
        sb.appendLine("""<?xml version="1.0" encoding="utf-8"?>""")
        sb.appendLine("""<VisioDocument xmlns="http://schemas.microsoft.com/visio/2003/core" xmlns:vx="http://schemas.microsoft.com/visio/2006/extension">""")
        sb.appendLine("""  <DocumentProperties>""")
        sb.appendLine("""    <Title>Electrical Panel - ${xmlEscape(panel.name)}</Title>""")
        sb.appendLine("""    <Subject>Single Line Diagram and Switchboard Layout</Subject>""")
        sb.appendLine("""    <Creator>PanelCraft Pro</Creator>""")
        sb.appendLine("""  </DocumentProperties>""")
        sb.appendLine("""  <Pages>""")
        sb.appendLine("""    <Page ID="0" Name="Panel Schematic">""")
        sb.appendLine("""      <Shapes>""")

        var shapeId = 1

        // Title Header Block
        sb.appendLine("""        <Shape ID="${shapeId++}" Type="Shape" Name="TitleBlock">""")
        sb.appendLine("""          <XForm><PinX>5.5</PinX><PinY>8.0</PinY><Width>9.0</Width><Height>0.8</Height></XForm>""")
        sb.appendLine("""          <Text>PANELCRAFT ELECTRICAL PANEL SCHEMATIC&#xA;Panel: ${xmlEscape(panel.name)} | Tag: ${xmlEscape(panel.tag)} | Voltage: ${xmlEscape(panel.systemVoltage.label)}</Text>""")
        sb.appendLine("""        </Shape>""")

        // Incomer MCCB shape
        sb.appendLine("""        <Shape ID="${shapeId++}" Type="Shape" Name="MainIncomer">""")
        sb.appendLine("""          <XForm><PinX>5.5</PinX><PinY>7.0</PinY><Width>2.0</Width><Height>0.8</Height></XForm>""")
        sb.appendLine("""          <Text>MAIN INCOMER&#xA;${panel.mainBreakerAmps}A (${panel.mainBreakerPoles}P)&#xA;Demand: ${String.format(Locale.US, "%.1f", summary.totalDemandWatts / 1000.0)} kW</Text>""")
        sb.appendLine("""        </Shape>""")

        // Busbars
        val busNames = listOf("L1 Busbar (Phase 1)", "L2 Busbar (Phase 2)", "L3 Busbar (Phase 3)", "Neutral Busbar (N)", "Ground Earth Busbar (PE)")
        var busY = 6.2
        for (bName in busNames) {
            sb.appendLine("""        <Shape ID="${shapeId++}" Type="Shape" Name="Busbar">""")
            sb.appendLine("""          <XForm><PinX>5.5</PinX><PinY>$busY</PinY><Width>9.5</Width><Height>0.12</Height></XForm>""")
            sb.appendLine("""          <Text>${xmlEscape(bName)}</Text>""")
            sb.appendLine("""        </Shape>""")
            busY -= 0.25
        }

        // Branch Circuit Breakers (DIN modules)
        val circuitCount = panel.circuits.size.coerceAtMost(24)
        val startX = 1.0
        val stepX = if (circuitCount > 1) 8.5 / circuitCount else 2.0
        var curX = startX

        for (circuit in panel.circuits.take(24)) {
            val calc = summary.circuitsSummary[circuit.id]
            val iAmps = calc?.nominalCurrentAmps ?: 0.0

            sb.appendLine("""        <Shape ID="${shapeId++}" Type="Shape" Name="MCB_C${circuit.circuitNumber}">""")
            sb.appendLine("""          <XForm><PinX>${String.format(Locale.US, "%.2f", curX)}</PinX><PinY>3.8</PinY><Width>0.8</Width><Height>1.6</Height></XForm>""")
            sb.appendLine("""          <Text>C${circuit.circuitNumber}&#xA;${xmlEscape(circuit.name)}&#xA;${circuit.assignedPhase.shortName}&#xA;${circuit.ratingAmps}A ${circuit.breakerCurve}&#xA;${String.format(Locale.US, "%.1f", iAmps)}A&#xA;${circuit.cableCrossSectionMm2}mm²</Text>""")
            sb.appendLine("""        </Shape>""")

            curX += stepX
        }

        // Footer Summary Table shape
        sb.appendLine("""        <Shape ID="${shapeId++}" Type="Shape" Name="SummaryBlock">""")
        sb.appendLine("""          <XForm><PinX>5.5</PinX><PinY>1.5</PinY><Width>9.0</Width><Height>1.2</Height></XForm>""")
        sb.appendLine("""          <Text>CALCULATION METRICS &amp; PHASE BALANCE:&#xA;Connected: ${String.format(Locale.US, "%.1f", summary.totalConnectedWatts / 1000.0)} kW | Demand: ${String.format(Locale.US, "%.1f", summary.totalDemandWatts / 1000.0)} kW&#xA;L1: ${summary.phaseL1Amps}A | L2: ${summary.phaseL2Amps}A | L3: ${summary.phaseL3Amps}A | Unbalance: ${summary.unbalancePercent}% | Neutral: ${summary.neutralCurrentAmps}A</Text>""")
        sb.appendLine("""        </Shape>""")

        sb.appendLine("""      </Shapes>""")
        sb.appendLine("""    </Page>""")
        sb.appendLine("""  </Pages>""")
        sb.appendLine("""</VisioDocument>""")
        return sb.toString()
    }

    private fun buildVisioSvg(panel: PanelDesign, summary: com.example.model.PanelCalculationSummary): String {
        val width = 1200
        val height = 800
        val sb = StringBuilder()

        sb.appendLine("""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 $width $height" width="$width" height="$height">""")
        sb.appendLine("""  <defs>""")
        sb.appendLine("""    <style>""")
        sb.appendLine("""      .title { font-family: Segoe UI, sans-serif; font-size: 20px; font-weight: bold; fill: #0f172a; }""")
        sb.appendLine("""      .subtitle { font-family: Segoe UI, sans-serif; font-size: 13px; fill: #64748b; }""")
        sb.appendLine("""      .mcb-title { font-family: Segoe UI, sans-serif; font-size: 10px; font-weight: bold; fill: #0f172a; }""")
        sb.appendLine("""      .mcb-desc { font-family: Segoe UI, sans-serif; font-size: 9px; fill: #334155; }""")
        sb.appendLine("""      .bus-label { font-family: Segoe UI, sans-serif; font-size: 11px; font-weight: bold; }""")
        sb.appendLine("""    </style>""")
        sb.appendLine("""  </defs>""")

        // Background
        sb.appendLine("""  <rect width="$width" height="$height" fill="#ffffff" stroke="#cbd5e1" stroke-width="2"/>""")

        // Header
        sb.appendLine("""  <rect x="30" y="25" width="1140" height="75" rx="8" fill="#f8fafc" stroke="#e2e8f0"/>""")
        sb.appendLine("""  <text x="50" y="55" class="title">PANELCRAFT ELECTRICAL DISTRIBUTION BOARD - ${xmlEscape(panel.name)}</text>""")
        sb.appendLine("""  <text x="50" y="80" class="subtitle">Tag: ${xmlEscape(panel.tag)} | System: ${xmlEscape(panel.systemVoltage.label)} | Location: ${xmlEscape(panel.location)} | Main Breaker: ${panel.mainBreakerAmps}A</text>""")

        // Incomer Symbol
        val incomerX = width / 2
        sb.appendLine("""  <line x1="$incomerX" y1="100" x2="$incomerX" y2="135" stroke="#0f172a" stroke-width="3"/>""")
        sb.appendLine("""  <rect x="${incomerX - 50}" y="135" width="100" height="50" rx="6" fill="#e0f2fe" stroke="#0284c7" stroke-width="2"/>""")
        sb.appendLine("""  <text x="$incomerX" y="157" text-anchor="middle" font-weight="bold" fill="#0284c7" font-size="12">MAIN MCCB</text>""")
        sb.appendLine("""  <text x="$incomerX" y="174" text-anchor="middle" font-weight="bold" fill="#0284c7" font-size="11">${panel.mainBreakerAmps}A (${panel.mainBreakerPoles}P)</text>""")
        sb.appendLine("""  <line x1="$incomerX" y1="185" x2="$incomerX" y2="220" stroke="#0f172a" stroke-width="3"/>""")

        // Busbars
        val busYStart = 220
        sb.appendLine("""  <!-- Busbars -->""")
        sb.appendLine("""  <line x1="60" y1="$busYStart" x2="1140" y2="$busYStart" stroke="#ef4444" stroke-width="5"/>""")
        sb.appendLine("""  <text x="70" y="${busYStart - 6}" class="bus-label" fill="#ef4444">L1 (Phase 1)</text>""")

        sb.appendLine("""  <line x1="60" y1="${busYStart + 22}" x2="1140" y2="${busYStart + 22}" stroke="#f59e0b" stroke-width="5"/>""")
        sb.appendLine("""  <text x="70" y="${busYStart + 16}" class="bus-label" fill="#f59e0b">L2 (Phase 2)</text>""")

        sb.appendLine("""  <line x1="60" y1="${busYStart + 44}" x2="1140" y2="${busYStart + 44}" stroke="#3b82f6" stroke-width="5"/>""")
        sb.appendLine("""  <text x="70" y="${busYStart + 38}" class="bus-label" fill="#3b82f6">L3 (Phase 3)</text>""")

        sb.appendLine("""  <line x1="60" y1="${busYStart + 66}" x2="1140" y2="${busYStart + 66}" stroke="#06b6d4" stroke-width="4"/>""")
        sb.appendLine("""  <text x="70" y="${busYStart + 60}" class="bus-label" fill="#06b6d4">N (Neutral)</text>""")

        sb.appendLine("""  <line x1="60" y1="${busYStart + 88}" x2="1140" y2="${busYStart + 88}" stroke="#22c55e" stroke-width="3" stroke-dasharray="8,6"/>""")
        sb.appendLine("""  <text x="70" y="${busYStart + 82}" class="bus-label" fill="#22c55e">PE (Protective Earth)</text>""")

        // Circuits
        val circuits = panel.circuits.take(12)
        val colWidth = 1040f / circuits.size.coerceAtLeast(1)
        var curX = 80f

        for (c in circuits) {
            val calc = summary.circuitsSummary[c.id] ?: ElectricalCalculator.calculateCircuit(c, panel.systemVoltage)
            val topY = when (c.assignedPhase) {
                Phase.L1 -> busYStart
                Phase.L2 -> busYStart + 22
                Phase.L3 -> busYStart + 44
                else -> busYStart
            }

            // Connection tap dot
            sb.appendLine("""  <circle cx="$curX" cy="$topY" r="4" fill="#0f172a"/>""")
            sb.appendLine("""  <line x1="$curX" y1="$topY" x2="$curX" y2="350" stroke="#475569" stroke-width="2"/>""")

            // MCB Box
            sb.appendLine("""  <g id="circuit_${c.circuitNumber}">""")
            sb.appendLine("""    <rect x="${curX - 35}" y="350" width="70" height="150" rx="5" fill="#f8fafc" stroke="#94a3b8" stroke-width="1.5"/>""")
            sb.appendLine("""    <rect x="${curX - 35}" y="350" width="70" height="24" rx="5" fill="#e2e8f0"/>""")
            sb.appendLine("""    <text x="$curX" y="366" text-anchor="middle" class="mcb-title">C${c.circuitNumber} (${c.assignedPhase.shortName})</text>""")
            sb.appendLine("""    <text x="$curX" y="392" text-anchor="middle" class="mcb-title">${c.ratingAmps}A ${c.breakerCurve}</text>""")
            sb.appendLine("""    <text x="$curX" y="410" text-anchor="middle" class="mcb-desc">${c.powerWatts.toInt()} W</text>""")
            sb.appendLine("""    <text x="$curX" y="426" text-anchor="middle" class="mcb-desc">${String.format(Locale.US, "%.1f", calc.nominalCurrentAmps)} A</text>""")
            sb.appendLine("""    <text x="$curX" y="442" text-anchor="middle" class="mcb-desc">${c.cableCrossSectionMm2} mm²</text>""")
            sb.appendLine("""    <text x="$curX" y="458" text-anchor="middle" class="mcb-desc">Drop: ${String.format(Locale.US, "%.1f", calc.voltageDropPercent)}%</text>""")
            sb.appendLine("""    <text x="$curX" y="474" text-anchor="middle" class="mcb-desc">${if (c.hasRcd) "RCD 30mA" else "No RCD"}</text>""")
            sb.appendLine("""    <circle cx="$curX" cy="488" r="4" fill="${if (c.isOn) "#22c55e" else "#ef4444"}"/>""")
            sb.appendLine("""  </g>""")

            curX += colWidth
        }

        // Summary Bar at Bottom
        sb.appendLine("""  <rect x="30" y="690" width="1140" height="85" rx="8" fill="#0f172a"/>""")
        sb.appendLine("""  <text x="50" y="722" font-family="Segoe UI, sans-serif" font-size="14" font-weight="bold" fill="#ffffff">ELECTRICAL SUMMARY: Total Connected: ${String.format(Locale.US, "%.1f", summary.totalConnectedWatts / 1000.0)} kW | Max Demand: ${String.format(Locale.US, "%.1f", summary.totalDemandWatts / 1000.0)} kW (${String.format(Locale.US, "%.1f", summary.totalApparentVa / 1000.0)} kVA)</text>""")
        sb.appendLine("""  <text x="50" y="750" font-family="Segoe UI, sans-serif" font-size="13" fill="#38bdf8">Phase Currents: L1: ${summary.phaseL1Amps} A | L2: ${summary.phaseL2Amps} A | L3: ${summary.phaseL3Amps} A | Neutral: ${summary.neutralCurrentAmps} A | Phase Unbalance: ${summary.unbalancePercent}% (${if (summary.unbalancePercent <= 10.0) "Balanced" else "Imbalanced"})</text>""")

        sb.appendLine("""</svg>""")
        return sb.toString()
    }

    private fun xmlEscape(str: String): String {
        return str.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}
