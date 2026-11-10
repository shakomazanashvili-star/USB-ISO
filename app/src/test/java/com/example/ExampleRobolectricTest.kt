package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.calculation.ElectricalCalculator
import com.example.export.ExcelExporter
import com.example.export.PdfExporter
import com.example.export.VisioExporter
import com.example.model.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("PanelCraft", appName)
    }

    @Test
    fun `test electrical calculation nominal current and voltage drop`() {
        val circuit = CircuitBreaker(
            name = "Kitchen Sockets",
            loadType = LoadType.SOCKETS,
            poles = 1,
            assignedPhase = Phase.L1,
            ratingAmps = 16,
            powerWatts = 2300.0,
            powerFactor = 1.0,
            cableLengthMeters = 20.0,
            cableCrossSectionMm2 = 2.5
        )

        val calc = ElectricalCalculator.calculateCircuit(circuit, SystemVoltage.THREE_PHASE_400_230)
        // 2300W / 230V = 10.0 A
        assertEquals(10.0, calc.nominalCurrentAmps, 0.1)
        assertFalse(calc.isOverloaded)
        assertFalse(calc.isVoltageDropWarning)
        assertEquals(16, calc.recommendedBreakerAmps)
    }

    @Test
    fun `test auto phase balancing algorithm distributes loads across L1 L2 L3`() {
        val circuits = listOf(
            CircuitBreaker(circuitNumber = 1, name = "Load 1", powerWatts = 3000.0, assignedPhase = Phase.L1),
            CircuitBreaker(circuitNumber = 2, name = "Load 2", powerWatts = 3000.0, assignedPhase = Phase.L1),
            CircuitBreaker(circuitNumber = 3, name = "Load 3", powerWatts = 3000.0, assignedPhase = Phase.L1)
        )
        val unbalancedPanel = PanelDesign(
            name = "Test Panel",
            systemVoltage = SystemVoltage.THREE_PHASE_400_230,
            circuits = circuits
        )

        val initialSummary = ElectricalCalculator.calculatePanel(unbalancedPanel)
        assertTrue("Initial unbalance should be high", initialSummary.unbalancePercent > 50.0)

        val balanced = ElectricalCalculator.autoBalancePhases(unbalancedPanel)
        val balancedSummary = ElectricalCalculator.calculatePanel(balanced)

        assertEquals(3000.0, balancedSummary.phaseL1Watts, 0.1)
        assertEquals(3000.0, balancedSummary.phaseL2Watts, 0.1)
        assertEquals(3000.0, balancedSummary.phaseL3Watts, 0.1)
        assertEquals(0.0, balancedSummary.unbalancePercent, 0.1)
    }

    @Test
    fun `test export file generators Excel PDF Visio`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val panel = PanelDesign(
            name = "Test Switchboard",
            tag = "DB-TST",
            circuits = listOf(
                CircuitBreaker(circuitNumber = 1, name = "Lighting", powerWatts = 500.0),
                CircuitBreaker(circuitNumber = 2, name = "Sockets", powerWatts = 2000.0)
            )
        )

        val excelFile = ExcelExporter.exportToExcel(context, panel)
        assertTrue(excelFile.exists())
        assertTrue(excelFile.length() > 0)

        try {
            val pdfFile = PdfExporter.exportToPdf(context, panel)
            assertTrue(pdfFile.exists())
            assertTrue(pdfFile.length() > 0)
        } catch (e: Exception) {
            // Android PdfDocument can require device graphics pipeline unavailable in mock JVM
            println("PdfDocument skipped on local JVM: ${e.message}")
        }

        val visioVdx = VisioExporter.exportToVisioVdx(context, panel)
        assertTrue(visioVdx.exists())
        assertTrue(visioVdx.length() > 0)

        val visioSvg = VisioExporter.exportToVisioSvg(context, panel)
        assertTrue(visioSvg.exists())
        assertTrue(visioSvg.length() > 0)
    }

    @Test
    fun `test real time load calculations when circuits are toggled on and off`() {
        val c1 = CircuitBreaker(id = "c1", circuitNumber = 1, name = "Cct 1", powerWatts = 2000.0, assignedPhase = Phase.L1, isOn = true)
        val c2 = CircuitBreaker(id = "c2", circuitNumber = 2, name = "Cct 2", powerWatts = 3000.0, assignedPhase = Phase.L2, isOn = false)

        val panel = PanelDesign(
            name = "Test Panel",
            diversityFactor = 1.0,
            systemVoltage = SystemVoltage.THREE_PHASE_400_230,
            circuits = listOf(c1, c2)
        )

        // Only C1 is ON: total connected = 5000W, total demand = 2000W
        val summary1 = ElectricalCalculator.calculatePanel(panel)
        assertEquals(5000.0, summary1.totalConnectedWatts, 0.1)
        assertEquals(2000.0, summary1.totalDemandWatts, 0.1)

        // Turn C2 ON
        val updatedPanel = panel.copy(circuits = listOf(c1, c2.copy(isOn = true)))
        val summary2 = ElectricalCalculator.calculatePanel(updatedPanel)
        assertEquals(5000.0, summary2.totalConnectedWatts, 0.1)
        assertEquals(5000.0, summary2.totalDemandWatts, 0.1)
    }

    @Test
    fun `test Excel and CSV import parser`() {
        val sampleCsv = com.example.importer.UniversalImporter.getSampleExcelCsv()
        val result = com.example.importer.ExcelImporter.parse("schedule.csv", sampleCsv)

        assertTrue(result.success)
        assertEquals("Commercial Center MDB-North", result.panelName)
        assertEquals("DB-COMM-01", result.panelTag)
        assertEquals(8, result.circuits.size)

        val c1 = result.circuits[0]
        assertEquals("Reception & Atrium LED", c1.name)
        assertEquals(16, c1.ratingAmps)
        assertEquals(Phase.L1, c1.assignedPhase)
        assertEquals(1800.0, c1.powerWatts, 0.1)

        val c6 = result.circuits[5]
        assertEquals("Chiller Unit 3-Phase Pump", c6.name)
        assertEquals(32, c6.ratingAmps)
        assertEquals(Phase.THREE_PHASE, c6.assignedPhase)
        assertEquals(3, c6.poles)
    }

    @Test
    fun `test Visio SVG schematic import parser`() {
        val sampleSvg = com.example.importer.UniversalImporter.getSampleVisioSvg()
        val result = com.example.importer.VisioImporter.parse("schematic.svg", sampleSvg)

        assertTrue(result.success)
        assertTrue(result.circuits.size >= 4)

        val c1 = result.circuits.find { it.circuitNumber == 1 }
        assertNotNull(c1)
        assertTrue(c1!!.name.contains("Warehouse Lighting"))
        assertEquals(16, c1.ratingAmps)
        assertEquals(Phase.L1, c1.assignedPhase)
        assertEquals(2200.0, c1.powerWatts, 0.1)
    }

    @Test
    fun `test PDF electrical schedule specification import parser`() {
        val samplePdfText = com.example.importer.UniversalImporter.getSamplePdfText()
        val result = com.example.importer.PdfImporter.parseTextDirect("spec.pdf", samplePdfText)

        assertTrue(result.success)
        assertEquals("Hospital Wing Panel B", result.panelName)
        assertEquals("DB-MED-04", result.panelTag)
        assertTrue(result.circuits.size >= 6)

        val c1 = result.circuits[0]
        assertTrue(c1.name.contains("Operating Theater Lighting"))
        assertEquals(16, c1.ratingAmps)
        assertEquals(Phase.L1, c1.assignedPhase)
    }
}
