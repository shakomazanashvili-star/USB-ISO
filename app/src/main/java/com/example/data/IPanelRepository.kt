package com.example.data

import com.example.model.CircuitBreaker
import com.example.model.PanelDesign
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Repository interface defining CRUD operations and reactive data streams
 * between Cloud Firestore and the application layer / ViewModels.
 */
interface IPanelRepository {
    /**
     * Observable stream of all panels, automatically updated in real-time from Firestore & local cache.
     */
    val panels: StateFlow<List<PanelDesign>>

    /**
     * Realtime synchronization state indicator.
     */
    val isSyncing: StateFlow<Boolean>

    /**
     * Last synchronization error message if any.
     */
    val syncError: StateFlow<String?>

    /**
     * Reactive flow observing all panels.
     */
    fun observePanels(): Flow<List<PanelDesign>>

    /**
     * Reactive flow observing a single panel by ID.
     */
    fun observePanel(id: String): Flow<PanelDesign?>

    /**
     * Retrieves a panel synchronously from current in-memory / cached state.
     */
    fun getPanel(id: String): PanelDesign?

    /**
     * Asynchronously fetches a panel directly from Cloud Firestore or cache.
     */
    suspend fun getPanelById(id: String): Result<PanelDesign?>

    /**
     * Creates a new electrical panel design in Firestore and local state.
     */
    suspend fun createPanel(panel: PanelDesign): Result<PanelDesign>

    /**
     * Updates an existing electrical panel design in Firestore and local state.
     */
    suspend fun updatePanel(panel: PanelDesign): Result<PanelDesign>

    /**
     * Saves a panel (creates if absent, updates if present) - fire-and-forget / coroutine dispatch.
     */
    fun savePanel(panel: PanelDesign)

    /**
     * Deletes a panel design by ID from Firestore and local cache.
     */
    suspend fun deletePanel(panelId: String): Result<Unit>

    /**
     * Convenience synchronous-style delete method.
     */
    fun deletePanelSync(panelId: String)

    /**
     * Adds a new circuit breaker to an existing panel in Firestore and local state.
     */
    suspend fun addCircuit(panelId: String, circuit: CircuitBreaker): Result<PanelDesign>

    /**
     * Updates a specific circuit breaker inside a panel in Firestore and local state.
     */
    suspend fun updateCircuit(panelId: String, circuit: CircuitBreaker): Result<PanelDesign>

    /**
     * Deletes a circuit breaker from a panel.
     */
    suspend fun deleteCircuit(panelId: String, circuitId: String): Result<PanelDesign>

    /**
     * Reorders or updates the entire circuit breaker list of a panel.
     */
    suspend fun reorderCircuits(panelId: String, circuits: List<CircuitBreaker>): Result<PanelDesign>

    /**
     * Duplicates an existing panel design.
     */
    suspend fun duplicatePanel(panelId: String): Result<PanelDesign>

    /**
     * Triggers a cloud synchronization with Firestore.
     */
    suspend fun syncWithCloud(): Result<List<PanelDesign>>

    /**
     * Non-suspending trigger for cloud sync.
     */
    fun syncFromCloud()
}
