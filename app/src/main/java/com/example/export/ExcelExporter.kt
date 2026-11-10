package com.example.export

import android.content.Context
import androidx.core.content.FileProvider
import com.example.calculation.ElectricalCalculator
import com.example.model.PanelDesign
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ExcelExporter {

    /**
     * Exports the panel schedule as an XML Spreadsheet (Excel 2003 / 2016 / 365 compatible .xls/.xml),
     * which opens directly in Microsoft Excel with formatted columns, colors, and calculation tables.
     */
    fun exportToExcel(context: Context, panel: PanelDesign): File {
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val cleanName = panel.name.replace(Regex("[^a-zA-Z0-9_]"), "_")
        val file = File(exportDir, "PanelSchedule_${cleanName}_$timeStamp.xls")

        val summary = ElectricalCalculator.calculatePanel(panel)
        val xmlContent = buildExcelXml(panel, summary)

        FileOutputStream(file).use { out ->
            out.write(xmlContent.toByteArray(Charsets.UTF_8))
        }
        return file
    }

    /**
     * Also exports standard CSV for maximum interoperability.
     */
    fun exportToCsv(context: Context, panel: PanelDesign): File {
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val cleanName = panel.name.replace(Regex("[^a-zA-Z0-9_]"), "_")
        val file = File(exportDir, "PanelSchedule_${cleanName}_$timeStamp.csv")

        val summary = ElectricalCalculator.calculatePanel(panel)
        val sb = StringBuilder()

        sb.appendLine("PANEL SCHEDULE & LOAD CALCULATION REPORT")
        sb.appendLine("Panel Name,${escapeCsv(panel.name)}")
        sb.appendLine("Panel Tag,${escapeCsv(panel.tag)}")
        sb.appendLine("Location,${escapeCsv(panel.location)}")
        sb.appendLine("System Voltage,${escapeCsv(panel.systemVoltage.label)}")
        sb.appendLine("Main Breaker,${panel.mainBreakerAmps} A (${panel.mainBreakerPoles}P)")
        sb.appendLine("Diversity Factor,${panel.diversityFactor}")
        sb.appendLine("Total Connected Load,${summary.totalConnectedWatts / 1000.0} kW")
        sb.appendLine("Total Demand Load,${summary.totalDemandWatts / 1000.0} kW")
        sb.appendLine("Total Apparent Power,${summary.totalApparentVa / 1000.0} kVA")
        sb.appendLine("Phase Currents,L1: ${summary.phaseL1Amps} A | L2: ${summary.phaseL2Amps} A | L3: ${summary.phaseL3Amps} A")
        sb.appendLine("Neutral Current,${summary.neutralCurrentAmps} A")
        sb.appendLine("Phase Unbalance,${summary.unbalancePercent} %")
        sb.appendLine()

        // Table headers
        sb.appendLine("Circuit #,Description,Load Type,Poles,Phase,Rating (A),Curve,Power (W),Power Factor,Current (A),Cable Size (mm²),Length (m),Volt Drop (V),Volt Drop (%),RCD Protection,Status")

        for (circuit in panel.circuits) {
            val calc = summary.circuitsSummary[circuit.id] ?: ElectricalCalculator.calculateCircuit(circuit, panel.systemVoltage)
            sb.appendLine(listOf(
                circuit.circuitNumber.toString(),
                escapeCsv(circuit.name),
                circuit.loadType.displayName,
                "${circuit.poles}P",
                circuit.assignedPhase.shortName,
                "${circuit.ratingAmps}A",
                circuit.breakerCurve.label,
                circuit.powerWatts.toString(),
                circuit.powerFactor.toString(),
                calc.nominalCurrentAmps.toString(),
                "${circuit.cableCrossSectionMm2} mm²",
                "${circuit.cableLengthMeters} m",
                calc.voltageDropVolts.toString(),
                "${calc.voltageDropPercent}%",
                if (circuit.hasRcd) "Yes (${circuit.rcdRatingMa}mA)" else "No",
                if (circuit.isOn) "ACTIVE" else "OFF"
            ).joinToString(","))
        }

        FileOutputStream(file).use { out ->
            out.write(sb.toString().toByteArray(Charsets.UTF_8))
        }
        return file
    }

    private fun escapeCsv(value: String): String {
        return if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }
    }

    private fun buildExcelXml(panel: PanelDesign, summary: com.example.model.PanelCalculationSummary): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
        return """<?xml version="1.0"?>
<?mso-application progid="Excel.Sheet"?>
<Workbook xmlns="urn:schemas-microsoft-com:office:spreadsheet"
 xmlns:o="urn:schemas-microsoft-com:office:office"
 xmlns:x="urn:schemas-microsoft-com:office:excel"
 xmlns:ss="urn:schemas-microsoft-com:office:spreadsheet"
 xmlns:html="http://www.w3.org/TR/REC-html40">
 <Styles>
  <Style ss:ID="Default" ss:Name="Normal">
   <Alignment ss:Vertical="Center"/>
   <Borders/>
   <Font ss:FontName="Segoe UI" x:Family="Swiss" ss:Size="10" ss:Color="#000000"/>
  </Style>
  <Style ss:ID="HeaderTitle">
   <Alignment ss:Horizontal="Left" ss:Vertical="Center"/>
   <Font ss:FontName="Segoe UI" ss:Size="16" ss:Bold="1" ss:Color="#0F172A"/>
  </Style>
  <Style ss:ID="TableHeader">
   <Alignment ss:Horizontal="Center" ss:Vertical="Center" ss:WrapText="1"/>
   <Borders>
    <Border ss:Position="Bottom" ss:LineStyle="Continuous" ss:Weight="2" ss:Color="#0284C7"/>
    <Border ss:Position="Top" ss:LineStyle="Continuous" ss:Weight="1" ss:Color="#CBD5E1"/>
   </Borders>
   <Font ss:FontName="Segoe UI" ss:Size="10" ss:Bold="1" ss:Color="#FFFFFF"/>
   <Interior ss:Color="#0284C7" ss:Pattern="Solid"/>
  </Style>
  <Style ss:ID="DataCell">
   <Alignment ss:Horizontal="Left" ss:Vertical="Center"/>
   <Borders>
    <Border ss:Position="Bottom" ss:LineStyle="Continuous" ss:Weight="1" ss:Color="#E2E8F0"/>
   </Borders>
  </Style>
  <Style ss:ID="DataCellCenter">
   <Alignment ss:Horizontal="Center" ss:Vertical="Center"/>
   <Borders>
    <Border ss:Position="Bottom" ss:LineStyle="Continuous" ss:Weight="1" ss:Color="#E2E8F0"/>
   </Borders>
  </Style>
  <Style ss:ID="SummaryLabel">
   <Font ss:FontName="Segoe UI" ss:Bold="1" ss:Color="#334155"/>
  </Style>
  <Style ss:ID="SummaryValue">
   <Font ss:FontName="Segoe UI" ss:Bold="1" ss:Color="#0284C7"/>
  </Style>
 </Styles>
 <Worksheet ss:Name="Panel Schedule">
  <Table ss:DefaultRowHeight="20">
   <Column ss:Width="40"/>
   <Column ss:Width="160"/>
   <Column ss:Width="110"/>
   <Column ss:Width="45"/>
   <Column ss:Width="55"/>
   <Column ss:Width="65"/>
   <Column ss:Width="50"/>
   <Column ss:Width="70"/>
   <Column ss:Width="50"/>
   <Column ss:Width="65"/>
   <Column ss:Width="80"/>
   <Column ss:Width="65"/>
   <Column ss:Width="70"/>
   <Column ss:Width="70"/>
   <Column ss:Width="80"/>
   
   <Row ss:Height="30">
    <Cell ss:MergeAcross="14" ss:StyleID="HeaderTitle">
     <Data ss:Type="String">ELECTRICAL PANEL SCHEDULE &amp; LOAD ANALYSIS - ${panel.name}</Data>
    </Cell>
   </Row>
   <Row>
    <Cell ss:StyleID="SummaryLabel"><Data ss:Type="String">Tag / ID:</Data></Cell>
    <Cell><Data ss:Type="String">${panel.tag}</Data></Cell>
    <Cell ss:StyleID="SummaryLabel"><Data ss:Type="String">System Voltage:</Data></Cell>
    <Cell ss:MergeAcross="2"><Data ss:Type="String">${panel.systemVoltage.label}</Data></Cell>
    <Cell ss:StyleID="SummaryLabel"><Data ss:Type="String">Export Date:</Data></Cell>
    <Cell ss:MergeAcross="2"><Data ss:Type="String">$dateFormat</Data></Cell>
   </Row>
   <Row>
    <Cell ss:StyleID="SummaryLabel"><Data ss:Type="String">Location:</Data></Cell>
    <Cell><Data ss:Type="String">${panel.location}</Data></Cell>
    <Cell ss:StyleID="SummaryLabel"><Data ss:Type="String">Main Incomer:</Data></Cell>
    <Cell ss:MergeAcross="2"><Data ss:Type="String">${panel.mainBreakerAmps}A (${panel.mainBreakerPoles}P)</Data></Cell>
    <Cell ss:StyleID="SummaryLabel"><Data ss:Type="String">Demand Factor:</Data></Cell>
    <Cell ss:MergeAcross="2"><Data ss:Type="String">${panel.diversityFactor}</Data></Cell>
   </Row>
   <Row>
    <Cell ss:StyleID="SummaryLabel"><Data ss:Type="String">Total Connected:</Data></Cell>
    <Cell ss:StyleID="SummaryValue"><Data ss:Type="String">${summary.totalConnectedWatts / 1000.0} kW</Data></Cell>
    <Cell ss:StyleID="SummaryLabel"><Data ss:Type="String">Max Demand:</Data></Cell>
    <Cell ss:StyleID="SummaryValue" ss:MergeAcross="2"><Data ss:Type="String">${summary.totalDemandWatts / 1000.0} kW (${summary.totalApparentVa / 1000.0} kVA)</Data></Cell>
    <Cell ss:StyleID="SummaryLabel"><Data ss:Type="String">Phase Currents:</Data></Cell>
    <Cell ss:StyleID="SummaryValue" ss:MergeAcross="4"><Data ss:Type="String">L1: ${summary.phaseL1Amps}A | L2: ${summary.phaseL2Amps}A | L3: ${summary.phaseL3Amps}A (Unbalance: ${summary.unbalancePercent}%)</Data></Cell>
   </Row>
   <Row ss:Height="10"/>

   <Row ss:Height="24">
    <Cell ss:StyleID="TableHeader"><Data ss:Type="String">Cct #</Data></Cell>
    <Cell ss:StyleID="TableHeader"><Data ss:Type="String">Circuit Description</Data></Cell>
    <Cell ss:StyleID="TableHeader"><Data ss:Type="String">Load Type</Data></Cell>
    <Cell ss:StyleID="TableHeader"><Data ss:Type="String">Poles</Data></Cell>
    <Cell ss:StyleID="TableHeader"><Data ss:Type="String">Phase</Data></Cell>
    <Cell ss:StyleID="TableHeader"><Data ss:Type="String">Rating</Data></Cell>
    <Cell ss:StyleID="TableHeader"><Data ss:Type="String">Curve</Data></Cell>
    <Cell ss:StyleID="TableHeader"><Data ss:Type="String">Load (W)</Data></Cell>
    <Cell ss:StyleID="TableHeader"><Data ss:Type="String">PF</Data></Cell>
    <Cell ss:StyleID="TableHeader"><Data ss:Type="String">Current (A)</Data></Cell>
    <Cell ss:StyleID="TableHeader"><Data ss:Type="String">Cable Size</Data></Cell>
    <Cell ss:StyleID="TableHeader"><Data ss:Type="String">Length (m)</Data></Cell>
    <Cell ss:StyleID="TableHeader"><Data ss:Type="String">Drop (V)</Data></Cell>
    <Cell ss:StyleID="TableHeader"><Data ss:Type="String">Drop (%)</Data></Cell>
    <Cell ss:StyleID="TableHeader"><Data ss:Type="String">RCD (mA)</Data></Cell>
   </Row>
   
   ${panel.circuits.joinToString("\n") { c ->
       val calc = summary.circuitsSummary[c.id] ?: ElectricalCalculator.calculateCircuit(c, panel.systemVoltage)
       """   <Row>
    <Cell ss:StyleID="DataCellCenter"><Data ss:Type="Number">${c.circuitNumber}</Data></Cell>
    <Cell ss:StyleID="DataCell"><Data ss:Type="String">${c.name}</Data></Cell>
    <Cell ss:StyleID="DataCell"><Data ss:Type="String">${c.loadType.displayName}</Data></Cell>
    <Cell ss:StyleID="DataCellCenter"><Data ss:Type="String">${c.poles}P</Data></Cell>
    <Cell ss:StyleID="DataCellCenter"><Data ss:Type="String">${c.assignedPhase.shortName}</Data></Cell>
    <Cell ss:StyleID="DataCellCenter"><Data ss:Type="String">${c.ratingAmps}A</Data></Cell>
    <Cell ss:StyleID="DataCellCenter"><Data ss:Type="String">${c.breakerCurve.name}</Data></Cell>
    <Cell ss:StyleID="DataCellCenter"><Data ss:Type="Number">${c.powerWatts}</Data></Cell>
    <Cell ss:StyleID="DataCellCenter"><Data ss:Type="Number">${c.powerFactor}</Data></Cell>
    <Cell ss:StyleID="DataCellCenter"><Data ss:Type="Number">${calc.nominalCurrentAmps}</Data></Cell>
    <Cell ss:StyleID="DataCellCenter"><Data ss:Type="String">${c.cableCrossSectionMm2} mm²</Data></Cell>
    <Cell ss:StyleID="DataCellCenter"><Data ss:Type="Number">${c.cableLengthMeters}</Data></Cell>
    <Cell ss:StyleID="DataCellCenter"><Data ss:Type="Number">${calc.voltageDropVolts}</Data></Cell>
    <Cell ss:StyleID="DataCellCenter"><Data ss:Type="String">${calc.voltageDropPercent}%</Data></Cell>
    <Cell ss:StyleID="DataCellCenter"><Data ss:Type="String">${if (c.hasRcd) "${c.rcdRatingMa}mA" else "None"}</Data></Cell>
   </Row>"""
   }}
  </Table>
 </Worksheet>
</Workbook>
"""
    }
}
