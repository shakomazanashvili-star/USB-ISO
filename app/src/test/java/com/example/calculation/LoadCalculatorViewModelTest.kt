package com.example.calculation

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.PanelRepository
import com.example.model.*
import com.example.ui.viewmodel.LoadCalculatorViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LoadCalculatorViewModelTest {

    private lateinit var context: Context
    private lateinit var repository: PanelRepository
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("panelcraft_cache", Context.MODE_PRIVATE).edit().clear().commit()
        repository = PanelRepository(firestore = null, context = context, ioDispatcher = testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `test NecStandardsCalculator calculates continuous load 125 percent and total amperage`() {
        val circuits = listOf(
            // Commercial Lighting: continuous (NEC 210.19(A)(1)) -> 2400W / 1.0 PF = 2400 VA
            CircuitBreaker(circuitNumber = 1, name = "LED Lighting", loadType = LoadType.LIGHTING, powerWatts = 2400.0, powerFactor = 1.0, assignedPhase = Phase.L1),
            // Receptacles: non-continuous -> 2000W / 1.0 PF = 2000 VA
            CircuitBreaker(circuitNumber = 2, name = "General Outlets", loadType = LoadType.SOCKETS, powerWatts = 2000.0, powerFactor = 1.0, assignedPhase = Phase.L2)
        )

        val summary = NecStandardsCalculator.calculateNecPanel(
            circuits = circuits,
            systemVoltage = SystemVoltage.SINGLE_PHASE_230,
            mainBreakerAmps = 63,
            diversityFactor = 1.0
        )

        assertEquals(4400.0, summary.totalConnectedWatts, 0.1)
        assertEquals(2400.0, summary.continuousLoadVa, 0.1)
        assertEquals(2000.0, summary.nonContinuousLoadVa, 0.1)

        // NEC Design VA = (2400 * 1.25) + 2000 = 3000 + 2000 = 5000 VA
        assertEquals(5000.0, summary.necDesignVa, 0.1)

        // Total Amperage in 230V 1-phase: 5000 / 230 = 21.74 A
        assertEquals(21.74, summary.totalAmperageAmps, 0.2)
    }

    @Test
    fun `test NecStandardsCalculator branch circuit voltage drop classification under NEC`() {
        // Short low-resistance circuit (2.5mm², 10m, 10A) -> low drop <= 3.0%
        val shortCircuit = CircuitBreaker(
            name = "Short Run",
            powerWatts = 2300.0,
            ratingAmps = 16,
            cableLengthMeters = 10.0,
            cableCrossSectionMm2 = 2.5
        )
        val shortCalc = NecStandardsCalculator.calculateNecCircuit(shortCircuit, SystemVoltage.SINGLE_PHASE_230)
        assertTrue(shortCalc.voltageDropPercent <= 3.0)
        assertEquals(VoltageDropStatus.COMPLIANT, shortCalc.voltageDropStatus)

        // Long circuit (1.5mm², 80m, 10A) -> exceeds 3% recommendation
        val longCircuit = CircuitBreaker(
            name = "Long Run",
            powerWatts = 2300.0,
            ratingAmps = 16,
            cableLengthMeters = 80.0,
            cableCrossSectionMm2 = 1.5
        )
        val longCalc = NecStandardsCalculator.calculateNecCircuit(longCircuit, SystemVoltage.SINGLE_PHASE_230)
        assertTrue(longCalc.voltageDropPercent > 3.0)
        assertFalse(longCalc.voltageDropStatus.isCompliant)
    }

    @Test
    fun `test NecStandardsCalculator 3-phase imbalance and neutral current calculation per NEC 220_61`() {
        val unbalancedCircuits = listOf(
            CircuitBreaker(circuitNumber = 1, name = "Heavy Load L1", powerWatts = 6900.0, powerFactor = 1.0, assignedPhase = Phase.L1), // 6900W / 230V = 30A
            CircuitBreaker(circuitNumber = 2, name = "Medium Load L2", powerWatts = 4600.0, powerFactor = 1.0, assignedPhase = Phase.L2), // 4600W / 230V = 20A
            CircuitBreaker(circuitNumber = 3, name = "Light Load L3", powerWatts = 2300.0, powerFactor = 1.0, assignedPhase = Phase.L3)   // 2300W / 230V = 10A
        )

        val summary = NecStandardsCalculator.calculateNecPanel(
            circuits = unbalancedCircuits,
            systemVoltage = SystemVoltage.THREE_PHASE_400_230,
            mainBreakerAmps = 100,
            diversityFactor = 1.0
        )

        // Average phase current = (30 + 20 + 10) / 3 = 20A
        assertEquals(20.0, summary.averagePhaseAmps, 0.5)
        // Max deviation = |30 - 20| = 10A -> (10 / 20) * 100% = 50% imbalance
        assertTrue("Imbalance should exceed 40%", summary.phaseImbalancePercent > 40.0)
        assertEquals(PhaseImbalanceStatus.SEVERE, summary.phaseImbalanceStatus)

        // Neutral current: sqrt(30^2 + 20^2 + 10^2 - (30*20 + 20*10 + 10*30))
        // sqrt(900 + 400 + 100 - (600 + 200 + 300)) = sqrt(1400 - 1100) = sqrt(300) = 17.32 A
        assertEquals(17.32, summary.neutralCurrentAmps, 0.5)
    }

    @Test
    fun `test LoadCalculatorViewModel tracks connected loads and toggling updates amperage`() = runTest {
        val viewModel = LoadCalculatorViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        testScheduler.advanceUntilIdle()

        val initialState = viewModel.uiState.value
        assertTrue("Should have initial tracked loads", initialState.loads.isNotEmpty())
        val initialAmperage = initialState.necSummary.totalAmperageAmps
        assertTrue("Initial amperage should be positive", initialAmperage > 0.0)

        // Turn off first load
        val firstLoad = initialState.loads.first()
        viewModel.toggleLoad(firstLoad.id)
        testScheduler.advanceUntilIdle()

        val updatedState = viewModel.uiState.value
        assertTrue("Total amperage should decrease when a load is turned off", updatedState.necSummary.totalAmperageAmps < initialAmperage)

        // Turn back on
        viewModel.toggleLoad(firstLoad.id)
        testScheduler.advanceUntilIdle()
        assertEquals(initialAmperage, viewModel.uiState.value.necSummary.totalAmperageAmps, 0.2)
    }

    @Test
    fun `test LoadCalculatorViewModel auto balance phases optimizes phase imbalance`() = runTest {
        val viewModel = LoadCalculatorViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        testScheduler.advanceUntilIdle()

        // Set unbalanced loads
        val l1HeavyLoads = listOf(
            CircuitBreaker(id = "1", circuitNumber = 1, powerWatts = 5000.0, assignedPhase = Phase.L1),
            CircuitBreaker(id = "2", circuitNumber = 2, powerWatts = 5000.0, assignedPhase = Phase.L1),
            CircuitBreaker(id = "3", circuitNumber = 3, powerWatts = 5000.0, assignedPhase = Phase.L1)
        )
        l1HeavyLoads.forEach { viewModel.addLoad(it) }
        testScheduler.advanceUntilIdle()

        val preBalanceImbalance = viewModel.uiState.value.necSummary.phaseImbalancePercent
        assertTrue("Unbalanced loads should cause high imbalance", preBalanceImbalance > 30.0)

        // Trigger Auto Balance
        viewModel.autoBalancePhases()
        testScheduler.advanceUntilIdle()

        val postBalanceImbalance = viewModel.uiState.value.necSummary.phaseImbalancePercent
        assertTrue("Phase balance should significantly improve post balance", postBalanceImbalance < preBalanceImbalance)
    }

    @Test
    fun `test LoadCalculatorViewModel loads panel from repository and synchronizes updates`() = runTest {
        val panelId = UUID.randomUUID().toString()
        val testPanel = PanelDesign(
            id = panelId,
            name = "Commercial Distribution Center",
            tag = "CDC-01",
            systemVoltage = SystemVoltage.THREE_PHASE_400_230,
            mainBreakerAmps = 150,
            circuits = listOf(
                CircuitBreaker(id = "cct-101", circuitNumber = 1, name = "Data Center Feeder", powerWatts = 8000.0, assignedPhase = Phase.L1),
                CircuitBreaker(id = "cct-102", circuitNumber = 2, name = "Chiller Pump", powerWatts = 6000.0, assignedPhase = Phase.L2)
            )
        )
        repository.createPanel(testPanel)

        val viewModel = LoadCalculatorViewModel(repository = repository, initialPanelId = panelId)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Commercial Distribution Center", state.panelName)
        assertEquals(2, state.loads.size)
        assertEquals(150, state.mainBreakerAmps)

        // Add a new load via viewModel
        val newLoad = CircuitBreaker(id = "cct-103", circuitNumber = 3, name = "Atrium Lighting", powerWatts = 4000.0, assignedPhase = Phase.L3)
        viewModel.addLoad(newLoad)
        testScheduler.advanceUntilIdle()

        // Confirm repo synchronized
        val syncedPanel = repository.getPanel(panelId)
        assertNotNull(syncedPanel)
        assertEquals(3, syncedPanel?.circuits?.size)
        assertTrue(syncedPanel?.circuits?.any { it.name == "Atrium Lighting" } == true)
    }

    @Test
    fun `test ElectricalLoad calculations apparentPowerVa powerWatts and continuous necDesignVa`() {
        // 1-Phase load: 230V, 16A, PF 0.8, continuous = true
        val singlePhaseLoad = ElectricalLoad(
            name = "Commercial Freezer",
            phase = Phase.L1,
            voltage = 230.0,
            amperage = 16.0,
            powerFactor = 0.8,
            isContinuous = true
        )
        // S = V * I = 230 * 16 = 3680 VA
        assertEquals(3680.0, singlePhaseLoad.apparentPowerVa, 0.1)
        // P = S * PF = 3680 * 0.8 = 2944 W
        assertEquals(2944.0, singlePhaseLoad.powerWatts, 0.1)
        // Continuous duty 125%: 3680 * 1.25 = 4600 VA
        assertEquals(4600.0, singlePhaseLoad.necDesignVa, 0.1)

        // 3-Phase load: 400V, 20A, PF 0.9, continuous = false
        val threePhaseLoad = ElectricalLoad(
            name = "Induction Motor",
            phase = Phase.THREE_PHASE,
            voltage = 400.0,
            amperage = 20.0,
            powerFactor = 0.9,
            poles = 3,
            isContinuous = false
        )
        // S = sqrt(3) * 400 * 20 = 13856.4 VA
        assertEquals(13856.4, threePhaseLoad.apparentPowerVa, 0.5)
        // P = 13856.4 * 0.9 = 12470.76 W
        assertEquals(12470.8, threePhaseLoad.powerWatts, 0.5)
        // Non-continuous: necDesignVa equals apparentPowerVa
        assertEquals(threePhaseLoad.apparentPowerVa, threePhaseLoad.necDesignVa, 0.1)
    }

    @Test
    fun `test LoadCalculatorViewModel manages ElectricalLoad collection CRUD operations`() = runTest {
        val viewModel = LoadCalculatorViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        testScheduler.advanceUntilIdle()

        // 1. Clear initial default loads
        viewModel.clearLoads()
        testScheduler.advanceUntilIdle()
        assertTrue(viewModel.electricalLoads.value.isEmpty())
        assertTrue(viewModel.uiState.value.electricalLoads.isEmpty())

        // 2. Add an electrical load using property overload (name, phase, voltage, amperage)
        viewModel.addLoad(
            name = "Workshop Lathe",
            phase = Phase.L1,
            voltage = 230.0,
            amperage = 15.0,
            isContinuous = false
        )
        testScheduler.advanceUntilIdle()

        assertEquals(1, viewModel.electricalLoads.value.size)
        val addedLoad = viewModel.electricalLoads.value.first()
        assertEquals("Workshop Lathe", addedLoad.name)
        assertEquals(Phase.L1, addedLoad.phase)
        assertEquals(230.0, addedLoad.voltage, 0.1)
        assertEquals(15.0, addedLoad.amperage, 0.1)
        assertFalse(addedLoad.isContinuous)

        // 3. Update load properties
        val updatedLoad = addedLoad.copy(name = "CNC Milling Machine", amperage = 22.0, isContinuous = true)
        viewModel.updateLoad(updatedLoad)
        testScheduler.advanceUntilIdle()

        val retrievedLoad = viewModel.getLoad(addedLoad.id)
        assertNotNull(retrievedLoad)
        assertEquals("CNC Milling Machine", retrievedLoad?.name)
        assertEquals(22.0, retrievedLoad?.amperage ?: 0.0, 0.1)
        assertTrue(retrievedLoad?.isContinuous == true)

        // 4. Duplicate load
        viewModel.duplicateLoad(addedLoad.id)
        testScheduler.advanceUntilIdle()
        assertEquals(2, viewModel.electricalLoads.value.size)
        assertTrue(viewModel.electricalLoads.value.any { it.name.contains("Copy") })

        // 5. Toggle operational state
        val secondLoadId = viewModel.electricalLoads.value.last().id
        assertTrue(viewModel.getLoad(secondLoadId)?.isOn == true)
        viewModel.toggleLoad(secondLoadId)
        testScheduler.advanceUntilIdle()
        assertFalse(viewModel.getLoad(secondLoadId)?.isOn == true)

        // 6. Set continuous status
        viewModel.setLoadContinuous(addedLoad.id, false)
        testScheduler.advanceUntilIdle()
        assertFalse(viewModel.getLoad(addedLoad.id)?.isContinuous == true)

        // 7. Remove load
        viewModel.removeLoad(secondLoadId)
        testScheduler.advanceUntilIdle()
        assertEquals(1, viewModel.electricalLoads.value.size)
        assertEquals(addedLoad.id, viewModel.electricalLoads.value.first().id)
    }

    @Test
    fun `test ElectricalLoad two-way conversion with CircuitBreaker preserves electrical parameters`() {
        val load = ElectricalLoad(
            id = "load-1",
            name = "Air Compressor",
            phase = Phase.L2,
            voltage = 230.0,
            amperage = 12.0,
            powerFactor = 0.85,
            loadType = LoadType.HVAC,
            isContinuous = true,
            cableLengthMeters = 30.0,
            cableCrossSectionMm2 = 4.0,
            poles = 1,
            ratingBreakerAmps = 20,
            isOn = true
        )

        // Convert to CircuitBreaker
        val breaker = load.toCircuitBreaker(circuitNumber = 5)
        assertEquals("load-1", breaker.id)
        assertEquals(5, breaker.circuitNumber)
        assertEquals("Air Compressor", breaker.name)
        assertEquals(Phase.L2, breaker.assignedPhase)
        assertEquals(LoadType.HVAC, breaker.loadType)
        assertEquals(20, breaker.ratingAmps)
        // PowerWatts: 230 * 12 * 0.85 = 2346.0 W
        assertEquals(2346.0, breaker.powerWatts, 0.5)

        // Convert back from CircuitBreaker
        val convertedLoad = ElectricalLoad.fromCircuitBreaker(
            circuit = breaker,
            systemVoltage = SystemVoltage.SINGLE_PHASE_230
        )
        assertEquals(load.id, convertedLoad.id)
        assertEquals(load.name, convertedLoad.name)
        assertEquals(load.phase, convertedLoad.phase)
        assertEquals(230.0, convertedLoad.voltage, 0.1)
        assertEquals(12.0, convertedLoad.amperage, 0.1)
        assertTrue(convertedLoad.isContinuous)
    }
}
