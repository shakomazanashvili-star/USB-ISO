package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.calculation.ElectricalCalculator
import com.example.data.IPanelRepository
import com.example.model.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

data class PanelEditorUiState(
    val panel: PanelDesign? = null,
    val calculationSummary: PanelCalculationSummary? = null,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val selectedTabIndex: Int = 0
)

class PanelEditorViewModel(
    val panelId: String,
    private val repository: IPanelRepository
) : ViewModel() {

    private val _selectedTabIndex = MutableStateFlow(0)
    val selectedTabIndex = _selectedTabIndex.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving = _isSaving.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()

    val uiState: StateFlow<PanelEditorUiState> = combine(
        repository.observePanel(panelId),
        _selectedTabIndex,
        _isSaving,
        _errorMessage
    ) { panel, tabIndex, saving, error ->
        val summary = panel?.let { ElectricalCalculator.calculatePanel(it) }
        PanelEditorUiState(
            panel = panel,
            calculationSummary = summary,
            isLoading = panel == null,
            isSaving = saving,
            errorMessage = error,
            selectedTabIndex = tabIndex
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = PanelEditorUiState(isLoading = true)
    )

    fun setSelectedTabIndex(index: Int) {
        _selectedTabIndex.value = index
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun savePanel(panel: PanelDesign) {
        viewModelScope.launch {
            _isSaving.value = true
            val result = repository.updatePanel(panel)
            _isSaving.value = false
            result.onFailure { error ->
                _errorMessage.value = "Failed to update panel: ${error.localizedMessage}"
            }
        }
    }

    fun updatePanelInfo(
        name: String,
        tag: String,
        location: String,
        systemVoltage: SystemVoltage,
        mainBreakerAmps: Int,
        mainBreakerPoles: Int,
        enclosureRows: Int,
        modulesPerRow: Int,
        diversityFactor: Double,
        notes: String
    ) {
        val current = uiState.value.panel ?: return
        val updated = current.copy(
            name = name,
            tag = tag,
            location = location,
            systemVoltage = systemVoltage,
            mainBreakerAmps = mainBreakerAmps,
            mainBreakerPoles = mainBreakerPoles,
            enclosureRows = enclosureRows,
            modulesPerRow = modulesPerRow,
            diversityFactor = diversityFactor,
            notes = notes
        )
        savePanel(updated)
    }

    fun addCircuit(circuit: CircuitBreaker) {
        viewModelScope.launch {
            _isSaving.value = true
            val result = repository.addCircuit(panelId, circuit)
            _isSaving.value = false
            result.onFailure { error ->
                _errorMessage.value = "Failed to add circuit: ${error.localizedMessage}"
            }
        }
    }

    fun updateCircuit(circuit: CircuitBreaker) {
        viewModelScope.launch {
            _isSaving.value = true
            val result = repository.updateCircuit(panelId, circuit)
            _isSaving.value = false
            result.onFailure { error ->
                _errorMessage.value = "Failed to update circuit: ${error.localizedMessage}"
            }
        }
    }

    fun deleteCircuit(circuitId: String) {
        viewModelScope.launch {
            _isSaving.value = true
            val result = repository.deleteCircuit(panelId, circuitId)
            _isSaving.value = false
            result.onFailure { error ->
                _errorMessage.value = "Failed to delete circuit: ${error.localizedMessage}"
            }
        }
    }

    fun toggleCircuit(circuitId: String) {
        val current = uiState.value.panel ?: return
        val updatedCircuits = current.circuits.map { circuit ->
            if (circuit.id == circuitId) circuit.copy(isOn = !circuit.isOn) else circuit
        }
        savePanel(current.copy(circuits = updatedCircuits))
    }

    fun duplicateCircuit(circuitId: String) {
        val current = uiState.value.panel ?: return
        val circuit = current.circuits.find { it.id == circuitId } ?: return
        val newCircuitNumber = (current.circuits.maxOfOrNull { it.circuitNumber } ?: 0) + 1
        val duplicated = circuit.copy(
            id = UUID.randomUUID().toString(),
            circuitNumber = newCircuitNumber,
            name = "${circuit.name} (Copy)"
        )
        addCircuit(duplicated)
    }

    fun reorderCircuits(circuits: List<CircuitBreaker>) {
        viewModelScope.launch {
            val result = repository.reorderCircuits(panelId, circuits)
            result.onFailure { error ->
                _errorMessage.value = "Failed to reorder circuits: ${error.localizedMessage}"
            }
        }
    }

    fun balancePhases() {
        val current = uiState.value.panel ?: return
        val balanced = ElectricalCalculator.autoBalancePhases(current)
        savePanel(balanced)
    }

    class Factory(
        private val panelId: String,
        private val repository: IPanelRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(PanelEditorViewModel::class.java)) {
                return PanelEditorViewModel(panelId, repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
