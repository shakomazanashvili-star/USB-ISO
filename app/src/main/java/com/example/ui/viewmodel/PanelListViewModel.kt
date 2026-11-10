package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.IPanelRepository
import com.example.model.PanelDesign
import com.example.model.SystemVoltage
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class PanelListUiState(
    val panels: List<PanelDesign> = emptyList(),
    val filteredPanels: List<PanelDesign> = emptyList(),
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val isSyncing: Boolean = false,
    val errorMessage: String? = null
)

class PanelListViewModel(
    private val repository: IPanelRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()

    val isSyncing: StateFlow<Boolean> = repository.isSyncing

    val uiState: StateFlow<PanelListUiState> = combine(
        repository.panels,
        _searchQuery,
        repository.isSyncing,
        _errorMessage
    ) { panels, query, syncing, error ->
        val filtered = if (query.isBlank()) {
            panels
        } else {
            panels.filter { panel ->
                panel.name.contains(query, ignoreCase = true) ||
                panel.tag.contains(query, ignoreCase = true) ||
                panel.location.contains(query, ignoreCase = true)
            }
        }
        PanelListUiState(
            panels = panels,
            filteredPanels = filtered,
            searchQuery = query,
            isLoading = false,
            isSyncing = syncing,
            errorMessage = error
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = PanelListUiState(isLoading = true)
    )

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun createPanel(
        name: String,
        tag: String,
        location: String,
        systemVoltage: SystemVoltage = SystemVoltage.THREE_PHASE_400_230,
        mainBreakerAmps: Int = 100,
        mainBreakerPoles: Int = 3,
        enclosureRows: Int = 3,
        modulesPerRow: Int = 18,
        diversityFactor: Double = 0.85,
        notes: String = ""
    ) {
        val newPanel = PanelDesign(
            name = name.ifBlank { "New Panel" },
            tag = tag.ifBlank { "DB-01" },
            location = location.ifBlank { "Main Floor" },
            systemVoltage = systemVoltage,
            mainBreakerAmps = mainBreakerAmps,
            mainBreakerPoles = mainBreakerPoles,
            enclosureRows = enclosureRows,
            modulesPerRow = modulesPerRow,
            diversityFactor = diversityFactor,
            notes = notes
        )
        viewModelScope.launch {
            val result = repository.createPanel(newPanel)
            result.onFailure { error ->
                _errorMessage.value = "Failed to create panel: ${error.localizedMessage}"
            }
        }
    }

    fun savePanel(panel: PanelDesign) {
        viewModelScope.launch {
            val result = repository.updatePanel(panel)
            result.onFailure { error ->
                _errorMessage.value = "Failed to save panel: ${error.localizedMessage}"
            }
        }
    }

    fun deletePanel(panelId: String) {
        viewModelScope.launch {
            val result = repository.deletePanel(panelId)
            result.onFailure { error ->
                _errorMessage.value = "Failed to delete panel: ${error.localizedMessage}"
            }
        }
    }

    fun duplicatePanel(panelId: String) {
        viewModelScope.launch {
            val result = repository.duplicatePanel(panelId)
            result.onFailure { error ->
                _errorMessage.value = "Failed to duplicate panel: ${error.localizedMessage}"
            }
        }
    }

    fun importPanels(imported: List<PanelDesign>) {
        viewModelScope.launch {
            imported.forEach { panel ->
                repository.createPanel(panel)
            }
        }
    }

    fun syncFromCloud() {
        viewModelScope.launch {
            val result = repository.syncWithCloud()
            result.onFailure { error ->
                _errorMessage.value = "Cloud sync issue: ${error.localizedMessage}"
            }
        }
    }

    class Factory(private val repository: IPanelRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(PanelListViewModel::class.java)) {
                return PanelListViewModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
