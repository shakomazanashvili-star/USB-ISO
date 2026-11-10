package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.calculation.ElectricalCalculator
import com.example.calculation.NecLoadCalculationSummary
import com.example.calculation.NecStandardsCalculator
import com.example.data.IPanelRepository
import com.example.model.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.round

/**
 * UI State for LoadCalculatorViewModel containing tracked electrical loads collection,
 * system configuration, and complete NEC standards calculation results.
 */
data class LoadCalculatorUiState(
    val panelId: String? = null,
    val panelName: String = "Electrical Load Center",
    val systemVoltage: SystemVoltage = SystemVoltage.THREE_PHASE_400_230,
    val mainBreakerAmps: Int = 100,
    val diversityFactor: Double = 0.85,
    val electricalLoads: List<ElectricalLoad> = emptyList(),
    val filteredLoads: List<ElectricalLoad> = emptyList(),
    val necSummary: NecLoadCalculationSummary = NecStandardsCalculator.calculateNecLoads(
        loads = emptyList(),
        systemVoltage = SystemVoltage.THREE_PHASE_400_230,
        mainBreakerAmps = 100,
        diversityFactor = 0.85
    ),
    val filterPhase: Phase? = null,
    val filterLoadType: LoadType? = null,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val statusMessage: String? = null
) {
    /**
     * Convenience property providing CircuitBreaker representations for backward compatibility.
     */
    val loads: List<CircuitBreaker>
        get() = electricalLoads.mapIndexed { i, l -> l.toCircuitBreaker(i + 1) }
}

/**
 * ViewModel that tracks connected loads and calculates total amperage,
 * voltage drop, and phase imbalance based strictly on NEC standards.
 * Manages the collection of ElectricalLoad items with full CRUD and reactive state.
 */
class LoadCalculatorViewModel(
    private val repository: IPanelRepository? = null,
    initialPanelId: String? = null
) : ViewModel() {

    private val _panelId = MutableStateFlow(initialPanelId)
    private val _panelName = MutableStateFlow("Distribution Board")
    private val _systemVoltage = MutableStateFlow(SystemVoltage.THREE_PHASE_400_230)
    private val _mainBreakerAmps = MutableStateFlow(100)
    private val _diversityFactor = MutableStateFlow(0.85)
    private val _electricalLoads = MutableStateFlow<List<ElectricalLoad>>(emptyList())
    val electricalLoads: StateFlow<List<ElectricalLoad>> = _electricalLoads.asStateFlow()

    private val _filterPhase = MutableStateFlow<Phase?>(null)
    private val _filterLoadType = MutableStateFlow<LoadType?>(null)
    private val _isLoading = MutableStateFlow(false)
    private val _isSaving = MutableStateFlow(false)
    private val _errorMessage = MutableStateFlow<String?>(null)
    private val _statusMessage = MutableStateFlow<String?>(null)

    init {
        if (!initialPanelId.isNullOrBlank()) {
            loadPanel(initialPanelId)
        } else if (repository != null) {
            // Observe default panel if available
            viewModelScope.launch {
                repository.panels.collect { panelsList ->
                    if (_panelId.value == null && panelsList.isNotEmpty()) {
                        loadFromPanel(panelsList.first())
                    }
                }
            }
        } else {
            // Seed sample loads for immediate interactive calculation
            _electricalLoads.value = createDefaultSampleLoads()
        }
    }

    val uiState: StateFlow<LoadCalculatorUiState> = combine(
        combine(_panelId, _panelName, _systemVoltage, _mainBreakerAmps, _diversityFactor) { pid, name, volt, amps, div ->
            PanelMeta(pid, name, volt, amps, div)
        },
        combine(_electricalLoads, _filterPhase, _filterLoadType) { loads, fPhase, fType ->
            LoadFilter(loads, fPhase, fType)
        },
        combine(_isLoading, _isSaving, _errorMessage, _statusMessage) { loading, saving, err, msg ->
            StatusMeta(loading, saving, err, msg)
        }
    ) { meta, filter, status ->
        val filtered = filter.loads.filter { load ->
            (filter.phase == null || load.phase == filter.phase) &&
            (filter.loadType == null || load.loadType == filter.loadType)
        }

        val necSummary = NecStandardsCalculator.calculateNecLoads(
            loads = filter.loads,
            systemVoltage = meta.voltage,
            mainBreakerAmps = meta.mainAmps,
            diversityFactor = meta.diversity
        )

        LoadCalculatorUiState(
            panelId = meta.id,
            panelName = meta.name,
            systemVoltage = meta.voltage,
            mainBreakerAmps = meta.mainAmps,
            diversityFactor = meta.diversity,
            electricalLoads = filter.loads,
            filteredLoads = filtered,
            necSummary = necSummary,
            filterPhase = filter.phase,
            filterLoadType = filter.loadType,
            isLoading = status.loading,
            isSaving = status.saving,
            errorMessage = status.error,
            statusMessage = status.message
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = LoadCalculatorUiState(isLoading = true)
    )

    fun loadPanel(panelId: String) {
        _panelId.value = panelId
        val repo = repository ?: return
        viewModelScope.launch {
            _isLoading.value = true
            val panel = repo.getPanel(panelId) ?: repo.getPanelById(panelId).getOrNull()
            if (panel != null) {
                loadFromPanel(panel)
            } else {
                _errorMessage.value = "Panel with ID $panelId not found"
            }
            _isLoading.value = false
        }
    }

    private fun loadFromPanel(panel: PanelDesign) {
        _panelId.value = panel.id
        _panelName.value = panel.name
        _systemVoltage.value = panel.systemVoltage
        _mainBreakerAmps.value = panel.mainBreakerAmps
        _diversityFactor.value = panel.diversityFactor
        _electricalLoads.value = panel.circuits.map { circuit ->
            ElectricalLoad.fromCircuitBreaker(circuit, panel.systemVoltage)
        }
    }

    fun setSystemVoltage(voltage: SystemVoltage) {
        _systemVoltage.value = voltage
        // Re-align voltage property on 1-phase / 3-phase loads if desired
        _electricalLoads.value = _electricalLoads.value.map { load ->
            val v = if (load.poles >= 3 || load.phase == Phase.THREE_PHASE) voltage.phaseToPhaseV else voltage.phaseToNeutralV
            load.copy(voltage = v)
        }
    }

    fun setDiversityFactor(factor: Double) {
        _diversityFactor.value = factor.coerceIn(0.1, 1.0)
    }

    fun setMainBreakerAmps(amps: Int) {
        _mainBreakerAmps.value = amps.coerceAtLeast(10)
    }

    fun setPanelName(name: String) {
        _panelName.value = name
    }

    // --- Electrical Load Collection Management Methods ---

    /**
     * Adds an ElectricalLoad to the tracked collection.
     */
    fun addLoad(load: ElectricalLoad) {
        val updated = _electricalLoads.value + load
        _electricalLoads.value = updated
        autoSyncIfConnected()
    }

    /**
     * Creates and adds an ElectricalLoad by its core electrical properties.
     */
    fun addLoad(
        name: String,
        phase: Phase,
        voltage: Double,
        amperage: Double,
        isContinuous: Boolean = false,
        loadType: LoadType = LoadType.SOCKETS,
        cableLengthMeters: Double = 20.0,
        cableCrossSectionMm2: Double = 2.5,
        ratingBreakerAmps: Int = 16,
        powerFactor: Double = 0.9,
        poles: Int = if (phase == Phase.THREE_PHASE) 3 else 1
    ) {
        val newLoad = ElectricalLoad(
            name = name.ifBlank { "Load ${_electricalLoads.value.size + 1}" },
            phase = phase,
            voltage = voltage.coerceAtLeast(1.0),
            amperage = amperage.coerceAtLeast(0.1),
            isContinuous = isContinuous,
            loadType = loadType,
            cableLengthMeters = cableLengthMeters.coerceAtLeast(1.0),
            cableCrossSectionMm2 = cableCrossSectionMm2.coerceAtLeast(1.0),
            ratingBreakerAmps = ratingBreakerAmps.coerceAtLeast(6),
            powerFactor = powerFactor.coerceIn(0.1, 1.0),
            poles = poles
        )
        addLoad(newLoad)
    }

    /**
     * Compatibility overload for adding via CircuitBreaker.
     */
    fun addLoad(circuit: CircuitBreaker) {
        addLoad(ElectricalLoad.fromCircuitBreaker(circuit, _systemVoltage.value))
    }

    /**
     * Updates an existing ElectricalLoad in the collection.
     */
    fun updateLoad(load: ElectricalLoad) {
        val updated = _electricalLoads.value.map { if (it.id == load.id) load else it }
        _electricalLoads.value = updated
        autoSyncIfConnected()
    }

    /**
     * Compatibility overload for updating via CircuitBreaker.
     */
    fun updateLoad(circuit: CircuitBreaker) {
        val existing = _electricalLoads.value.find { it.id == circuit.id }
        if (existing != null) {
            val updated = ElectricalLoad.fromCircuitBreaker(circuit, _systemVoltage.value).copy(
                isContinuous = existing.isContinuous
            )
            updateLoad(updated)
        } else {
            addLoad(circuit)
        }
    }

    /**
     * Removes an ElectricalLoad from the collection by ID.
     */
    fun removeLoad(loadId: String) {
        val updated = _electricalLoads.value.filter { it.id != loadId }
        _electricalLoads.value = updated
        autoSyncIfConnected()
    }

    /**
     * Toggles an ElectricalLoad operational state (ON/OFF) for live load shedding analysis.
     */
    fun toggleLoad(loadId: String) {
        val updated = _electricalLoads.value.map {
            if (it.id == loadId) it.copy(isOn = !it.isOn) else it
        }
        _electricalLoads.value = updated
        autoSyncIfConnected()
    }

    /**
     * Sets continuous duty status on an ElectricalLoad, applying 125% sizing under NEC.
     */
    fun setLoadContinuous(loadId: String, isContinuous: Boolean) {
        val updated = _electricalLoads.value.map {
            if (it.id == loadId) it.copy(isContinuous = isContinuous) else it
        }
        _electricalLoads.value = updated
        autoSyncIfConnected()
    }

    /**
     * Clears all loads from the collection.
     */
    fun clearLoads() {
        _electricalLoads.value = emptyList()
        autoSyncIfConnected()
    }

    /**
     * Duplicates an existing ElectricalLoad in the collection.
     */
    fun duplicateLoad(loadId: String) {
        val load = _electricalLoads.value.find { it.id == loadId } ?: return
        val duplicate = load.copy(
            id = UUID.randomUUID().toString(),
            name = "${load.name} (Copy)"
        )
        addLoad(duplicate)
    }

    /**
     * Reorders the collection of electrical loads.
     */
    fun reorderLoads(loads: List<ElectricalLoad>) {
        _electricalLoads.value = loads
        autoSyncIfConnected()
    }

    /**
     * Gets a single ElectricalLoad by ID.
     */
    fun getLoad(loadId: String): ElectricalLoad? {
        return _electricalLoads.value.find { it.id == loadId }
    }

    /**
     * Auto-balances single-phase loads across L1, L2, L3 per NEC 220.61.
     */
    fun autoBalancePhases() {
        if (!_systemVoltage.value.isThreePhase) return
        val circuits = _electricalLoads.value.mapIndexed { i, l -> l.toCircuitBreaker(i + 1) }
        val dummyPanel = PanelDesign(
            id = _panelId.value ?: "temp",
            name = _panelName.value,
            systemVoltage = _systemVoltage.value,
            circuits = circuits
        )
        val balanced = ElectricalCalculator.autoBalancePhases(dummyPanel)
        _electricalLoads.value = balanced.circuits.map { circuit ->
            val orig = _electricalLoads.value.find { it.id == circuit.id }
            orig?.copy(phase = circuit.assignedPhase) ?: ElectricalLoad.fromCircuitBreaker(circuit, _systemVoltage.value)
        }
        _statusMessage.value = "Phases successfully balanced across L1, L2, L3 per NEC"
        autoSyncIfConnected()
    }

    fun filterByPhase(phase: Phase?) {
        _filterPhase.value = phase
    }

    fun filterByLoadType(type: LoadType?) {
        _filterLoadType.value = type
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun clearStatus() {
        _statusMessage.value = null
    }

    fun saveToRepository() {
        val repo = repository ?: return
        val currentId = _panelId.value ?: UUID.randomUUID().toString()
        val circuits = _electricalLoads.value.mapIndexed { i, l -> l.toCircuitBreaker(i + 1) }
        val panel = PanelDesign(
            id = currentId,
            name = _panelName.value,
            systemVoltage = _systemVoltage.value,
            mainBreakerAmps = _mainBreakerAmps.value,
            diversityFactor = _diversityFactor.value,
            circuits = circuits
        )
        viewModelScope.launch {
            _isSaving.value = true
            val result = repo.updatePanel(panel)
            _isSaving.value = false
            result.onSuccess {
                _statusMessage.value = "Saved panel & calculations to Firestore"
            }.onFailure { err ->
                _errorMessage.value = "Failed to save to Firestore: ${err.localizedMessage}"
            }
        }
    }

    private fun autoSyncIfConnected() {
        val repo = repository ?: return
        val id = _panelId.value ?: return
        val current = repo.getPanel(id) ?: return
        val circuits = _electricalLoads.value.mapIndexed { i, l -> l.toCircuitBreaker(i + 1) }
        val updated = current.copy(
            circuits = circuits,
            systemVoltage = _systemVoltage.value,
            mainBreakerAmps = _mainBreakerAmps.value,
            diversityFactor = _diversityFactor.value
        )
        repo.savePanel(updated)
    }

    private fun createDefaultSampleLoads(): List<ElectricalLoad> {
        return listOf(
            ElectricalLoad(
                name = "Commercial LED Lighting",
                phase = Phase.L1,
                voltage = 230.0,
                amperage = 10.4,
                loadType = LoadType.LIGHTING,
                isContinuous = true,
                cableLengthMeters = 35.0,
                cableCrossSectionMm2 = 2.5
            ),
            ElectricalLoad(
                name = "General Receptacles Ring",
                phase = Phase.L2,
                voltage = 230.0,
                amperage = 13.9,
                loadType = LoadType.SOCKETS,
                isContinuous = false,
                cableLengthMeters = 25.0,
                cableCrossSectionMm2 = 4.0
            ),
            ElectricalLoad(
                name = "Office IT Computer Pods",
                phase = Phase.L3,
                voltage = 230.0,
                amperage = 12.2,
                loadType = LoadType.SOCKETS,
                isContinuous = true,
                cableLengthMeters = 28.0,
                cableCrossSectionMm2 = 4.0
            ),
            ElectricalLoad(
                name = "Electric Water Heater",
                phase = Phase.L1,
                voltage = 230.0,
                amperage = 19.6,
                loadType = LoadType.WATER_HEATER,
                isContinuous = true,
                cableLengthMeters = 15.0,
                cableCrossSectionMm2 = 6.0
            ),
            ElectricalLoad(
                name = "HVAC Heat Pump Inverter",
                phase = Phase.THREE_PHASE,
                voltage = 400.0,
                amperage = 13.7,
                loadType = LoadType.HVAC,
                poles = 3,
                isContinuous = true,
                cableLengthMeters = 30.0,
                cableCrossSectionMm2 = 10.0
            ),
            ElectricalLoad(
                name = "Level 2 EV Wallbox",
                phase = Phase.THREE_PHASE,
                voltage = 400.0,
                amperage = 15.9,
                loadType = LoadType.EV_CHARGER,
                poles = 3,
                isContinuous = true,
                cableLengthMeters = 20.0,
                cableCrossSectionMm2 = 6.0
            )
        )
    }

    // Helper data holders for combine
    private data class PanelMeta(
        val id: String?,
        val name: String,
        val voltage: SystemVoltage,
        val mainAmps: Int,
        val diversity: Double
    )

    private data class LoadFilter(
        val loads: List<ElectricalLoad>,
        val phase: Phase?,
        val loadType: LoadType?
    )

    private data class StatusMeta(
        val loading: Boolean,
        val saving: Boolean,
        val error: String?,
        val message: String?
    )

    class Factory(
        private val repository: IPanelRepository? = null,
        private val initialPanelId: String? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(LoadCalculatorViewModel::class.java)) {
                return LoadCalculatorViewModel(repository, initialPanelId) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
