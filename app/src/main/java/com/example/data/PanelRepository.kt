package com.example.data

import android.content.Context
import android.util.Log
import com.example.R
import com.example.model.*
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.tasks.await
import java.util.UUID

/**
 * Concrete implementation of IPanelRepository.
 * Manages two-way synchronization, real-time Firestore listeners, and CRUD operations
 * between Cloud Firestore and application ViewModels with local offline caching.
 */
class PanelRepository(
    private val firestore: FirebaseFirestore?,
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : IPanelRepository {

    constructor(context: Context) : this(
        resolveFirestore(context),
        context.applicationContext,
        Dispatchers.IO
    )

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val listType = Types.newParameterizedType(List::class.java, PanelDesign::class.java)
    private val jsonAdapter = moshi.adapter<List<PanelDesign>>(listType)

    private val prefs = context.getSharedPreferences("panelcraft_cache", Context.MODE_PRIVATE)

    private val repositoryScope = CoroutineScope(SupervisorJob() + ioDispatcher)

    private val _panels = MutableStateFlow<List<PanelDesign>>(emptyList())
    override val panels: StateFlow<List<PanelDesign>> = _panels.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    override val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _syncError = MutableStateFlow<String?>(null)
    override val syncError: StateFlow<String?> = _syncError.asStateFlow()

    private var snapshotListenerRegistration: ListenerRegistration? = null

    init {
        loadFromLocalCache()
        if (_panels.value.isEmpty()) {
            val defaults = createSamplePanels()
            _panels.value = defaults
            saveToLocalCache(defaults)
        }
        setupFirestoreRealtimeListener()
    }

    /**
     * Attaches a real-time snapshot listener to Firestore collection "panels".
     */
    private fun setupFirestoreRealtimeListener() {
        val fs = firestore ?: return
        try {
            snapshotListenerRegistration?.remove()
            snapshotListenerRegistration = fs.collection(COLLECTION_PANELS)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        handleFirestoreError(error, OperationType.LIST, COLLECTION_PANELS)
                        _syncError.value = error.localizedMessage
                        return@addSnapshotListener
                    }

                    if (snapshot != null && !snapshot.isEmpty) {
                        val cloudPanels = snapshot.documents.mapNotNull { doc ->
                            try {
                                val dto = doc.toObject(PanelDesignDto::class.java)
                                if (dto != null) {
                                    if (dto.id.isBlank()) dto.id = doc.id
                                    dto.toModel()
                                } else null
                            } catch (e: Exception) {
                                Log.w(TAG, "Failed to parse Firestore doc ${doc.id}", e)
                                null
                            }
                        }

                        if (cloudPanels.isNotEmpty()) {
                            // Merge cloud panels with local panels (cloud takes precedence for same IDs)
                            val mergedMap = LinkedHashMap<String, PanelDesign>()
                            cloudPanels.forEach { mergedMap[it.id] = it }
                            _panels.value.forEach { local ->
                                if (!mergedMap.containsKey(local.id)) {
                                    mergedMap[local.id] = local
                                }
                            }
                            val updatedList = mergedMap.values.toList()
                            _panels.value = updatedList
                            saveToLocalCache(updatedList)
                            _syncError.value = null
                        }
                    }
                }
        } catch (e: Exception) {
            Log.w(TAG, "Error attaching Firestore snapshot listener: ${e.message}")
        }
    }

    override fun observePanels(): Flow<List<PanelDesign>> = _panels.asStateFlow()

    override fun observePanel(id: String): Flow<PanelDesign?> {
        return _panels.map { list -> list.find { it.id == id } }
    }

    override fun getPanel(id: String): PanelDesign? {
        return _panels.value.find { it.id == id }
    }

    override suspend fun getPanelById(id: String): Result<PanelDesign?> = withContext(ioDispatcher) {
        // Return from memory first if present
        val cached = getPanel(id)
        if (cached != null) {
            return@withContext Result.success(cached)
        }

        val fs = firestore ?: return@withContext Result.success(null)
        val docPath = "$COLLECTION_PANELS/$id"
        try {
            val doc = fs.collection(COLLECTION_PANELS).document(id).get().await()
            if (doc.exists()) {
                val dto = doc.toObject(PanelDesignDto::class.java)
                if (dto != null) {
                    if (dto.id.isBlank()) dto.id = doc.id
                    val model = dto.toModel()
                    // Update cache
                    val updated = (_panels.value + model).distinctBy { it.id }
                    _panels.value = updated
                    saveToLocalCache(updated)
                    return@withContext Result.success(model)
                }
            }
            Result.success(null)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docPath)
            Result.failure(e)
        }
    }

    override suspend fun createPanel(panel: PanelDesign): Result<PanelDesign> = withContext(ioDispatcher) {
        val currentUserId = getCurrentUserId()
        val timestamp = System.currentTimeMillis()
        val preparedPanel = panel.copy(
            userId = if (panel.userId.isBlank()) currentUserId else panel.userId,
            createdAt = if (panel.createdAt == 0L) timestamp else panel.createdAt,
            updatedAt = timestamp
        )

        // Optimistically update memory & cache
        val current = _panels.value.toMutableList()
        current.add(0, preparedPanel)
        _panels.value = current
        saveToLocalCache(current)

        val fs = firestore ?: return@withContext Result.success(preparedPanel)
        val docPath = "$COLLECTION_PANELS/${preparedPanel.id}"
        try {
            val dto = PanelDesignDto.fromModel(preparedPanel)
            fs.collection(COLLECTION_PANELS).document(preparedPanel.id)
                .set(dto, SetOptions.merge())
                .await()
            Log.d(TAG, "Created panel ${preparedPanel.id} in Firestore")
            Result.success(preparedPanel)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            _syncError.value = e.localizedMessage
            // Retain local copy for offline support
            Result.success(preparedPanel)
        }
    }

    override suspend fun updatePanel(panel: PanelDesign): Result<PanelDesign> = withContext(ioDispatcher) {
        val timestamp = System.currentTimeMillis()
        val currentUserId = getCurrentUserId()
        val updatedPanel = panel.copy(
            userId = if (panel.userId.isBlank()) currentUserId else panel.userId,
            updatedAt = timestamp
        )

        // Update local memory & cache
        val current = _panels.value.toMutableList()
        val index = current.indexOfFirst { it.id == updatedPanel.id }
        if (index >= 0) {
            current[index] = updatedPanel
        } else {
            current.add(0, updatedPanel)
        }
        _panels.value = current
        saveToLocalCache(current)

        val fs = firestore ?: return@withContext Result.success(updatedPanel)
        val docPath = "$COLLECTION_PANELS/${updatedPanel.id}"
        try {
            val dto = PanelDesignDto.fromModel(updatedPanel)
            fs.collection(COLLECTION_PANELS).document(updatedPanel.id)
                .set(dto, SetOptions.merge())
                .await()
            Log.d(TAG, "Updated panel ${updatedPanel.id} in Firestore")
            Result.success(updatedPanel)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            _syncError.value = e.localizedMessage
            Result.success(updatedPanel)
        }
    }

    override fun savePanel(panel: PanelDesign) {
        repositoryScope.launch {
            updatePanel(panel)
        }
    }

    override suspend fun deletePanel(panelId: String): Result<Unit> = withContext(ioDispatcher) {
        // Remove from local memory & cache immediately
        val current = _panels.value.filter { it.id != panelId }
        _panels.value = current
        saveToLocalCache(current)

        val fs = firestore ?: return@withContext Result.success(Unit)
        val docPath = "$COLLECTION_PANELS/$panelId"
        try {
            fs.collection(COLLECTION_PANELS).document(panelId).delete().await()
            Log.d(TAG, "Deleted panel $panelId from Firestore")
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            _syncError.value = e.localizedMessage
            Result.failure(e)
        }
    }

    override fun deletePanelSync(panelId: String) {
        repositoryScope.launch {
            deletePanel(panelId)
        }
    }

    override suspend fun addCircuit(panelId: String, circuit: CircuitBreaker): Result<PanelDesign> {
        val panel = getPanel(panelId)
            ?: return Result.failure(IllegalArgumentException("Panel with ID $panelId not found"))
        val updatedCircuits = panel.circuits + circuit
        val updatedPanel = panel.copy(circuits = updatedCircuits)
        return updatePanel(updatedPanel)
    }

    override suspend fun updateCircuit(panelId: String, circuit: CircuitBreaker): Result<PanelDesign> {
        val panel = getPanel(panelId)
            ?: return Result.failure(IllegalArgumentException("Panel with ID $panelId not found"))
        val updatedCircuits = panel.circuits.map { if (it.id == circuit.id) circuit else it }
        val updatedPanel = panel.copy(circuits = updatedCircuits)
        return updatePanel(updatedPanel)
    }

    override suspend fun deleteCircuit(panelId: String, circuitId: String): Result<PanelDesign> {
        val panel = getPanel(panelId)
            ?: return Result.failure(IllegalArgumentException("Panel with ID $panelId not found"))
        val updatedCircuits = panel.circuits.filter { it.id != circuitId }
        val updatedPanel = panel.copy(circuits = updatedCircuits)
        return updatePanel(updatedPanel)
    }

    override suspend fun reorderCircuits(panelId: String, circuits: List<CircuitBreaker>): Result<PanelDesign> {
        val panel = getPanel(panelId)
            ?: return Result.failure(IllegalArgumentException("Panel with ID $panelId not found"))
        val updatedPanel = panel.copy(circuits = circuits)
        return updatePanel(updatedPanel)
    }

    override suspend fun duplicatePanel(panelId: String): Result<PanelDesign> {
        val source = getPanel(panelId)
            ?: return Result.failure(IllegalArgumentException("Panel with ID $panelId not found"))
        val newId = UUID.randomUUID().toString()
        val duplicated = source.copy(
            id = newId,
            name = "${source.name} (Copy)",
            tag = "${source.tag}-CPY",
            circuits = source.circuits.map { it.copy(id = UUID.randomUUID().toString()) },
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        return createPanel(duplicated)
    }

    override suspend fun syncWithCloud(): Result<List<PanelDesign>> = withContext(ioDispatcher) {
        val fs = firestore ?: return@withContext Result.success(_panels.value)
        _isSyncing.value = true
        _syncError.value = null
        try {
            val snapshot = fs.collection(COLLECTION_PANELS).get().await()
            val cloudPanels = snapshot.documents.mapNotNull { doc ->
                try {
                    val dto = doc.toObject(PanelDesignDto::class.java)
                    if (dto != null) {
                        if (dto.id.isBlank()) dto.id = doc.id
                        dto.toModel()
                    } else null
                } catch (e: Exception) {
                    Log.w(TAG, "Error parsing panel doc ${doc.id}", e)
                    null
                }
            }
            if (cloudPanels.isNotEmpty()) {
                val merged = (cloudPanels + _panels.value).distinctBy { it.id }
                _panels.value = merged
                saveToLocalCache(merged)
            }
            Result.success(_panels.value)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, COLLECTION_PANELS)
            _syncError.value = e.localizedMessage
            Result.failure(e)
        } finally {
            _isSyncing.value = false
        }
    }

    override fun syncFromCloud() {
        repositoryScope.launch {
            syncWithCloud()
        }
    }

    private fun getCurrentUserId(): String {
        return try {
            FirebaseAuth.getInstance().currentUser?.uid ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    private fun loadFromLocalCache() {
        val json = prefs.getString("cached_panels", null) ?: return
        try {
            val list = jsonAdapter.fromJson(json)
            if (!list.isNullOrEmpty()) {
                _panels.value = list
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading cached panels", e)
        }
    }

    private fun saveToLocalCache(list: List<PanelDesign>) {
        try {
            val json = jsonAdapter.toJson(list)
            prefs.edit().putString("cached_panels", json).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving panels cache", e)
        }
    }

    private fun createSamplePanels(): List<PanelDesign> {
        val villaCircuits = listOf(
            CircuitBreaker(circuitNumber = 1, name = "Ground Floor Lighting", loadType = LoadType.LIGHTING, poles = 1, assignedPhase = Phase.L1, ratingAmps = 10, powerWatts = 850.0, cableCrossSectionMm2 = 1.5, cableLengthMeters = 22.0),
            CircuitBreaker(circuitNumber = 2, name = "First Floor Lighting", loadType = LoadType.LIGHTING, poles = 1, assignedPhase = Phase.L2, ratingAmps = 10, powerWatts = 720.0, cableCrossSectionMm2 = 1.5, cableLengthMeters = 26.0),
            CircuitBreaker(circuitNumber = 3, name = "Outdoor Garden Lighting", loadType = LoadType.LIGHTING, poles = 1, assignedPhase = Phase.L3, ratingAmps = 10, powerWatts = 950.0, cableCrossSectionMm2 = 1.5, cableLengthMeters = 35.0),
            CircuitBreaker(circuitNumber = 4, name = "Living Room & Dining Sockets", loadType = LoadType.SOCKETS, poles = 1, assignedPhase = Phase.L1, ratingAmps = 16, powerWatts = 2800.0, cableCrossSectionMm2 = 2.5, cableLengthMeters = 18.0),
            CircuitBreaker(circuitNumber = 5, name = "Bedrooms Power Sockets", loadType = LoadType.SOCKETS, poles = 1, assignedPhase = Phase.L2, ratingAmps = 16, powerWatts = 2400.0, cableCrossSectionMm2 = 2.5, cableLengthMeters = 25.0),
            CircuitBreaker(circuitNumber = 6, name = "Kitchen Small Appliances", loadType = LoadType.SOCKETS, poles = 1, assignedPhase = Phase.L3, ratingAmps = 16, powerWatts = 3000.0, cableCrossSectionMm2 = 2.5, cableLengthMeters = 15.0),
            CircuitBreaker(circuitNumber = 7, name = "Induction Cooktop Hob", loadType = LoadType.KITCHEN, poles = 2, assignedPhase = Phase.L1, ratingAmps = 32, powerWatts = 6500.0, cableCrossSectionMm2 = 6.0, cableLengthMeters = 12.0),
            CircuitBreaker(circuitNumber = 8, name = "Electric Oven & Steamer", loadType = LoadType.KITCHEN, poles = 1, assignedPhase = Phase.L2, ratingAmps = 20, powerWatts = 3500.0, cableCrossSectionMm2 = 4.0, cableLengthMeters = 14.0),
            CircuitBreaker(circuitNumber = 9, name = "HVAC Heat Pump Inverter", loadType = LoadType.HVAC, poles = 3, assignedPhase = Phase.THREE_PHASE, ratingAmps = 25, powerWatts = 5800.0, cableCrossSectionMm2 = 4.0, cableLengthMeters = 20.0),
            CircuitBreaker(circuitNumber = 10, name = "Central Water Heater", loadType = LoadType.WATER_HEATER, poles = 1, assignedPhase = Phase.L3, ratingAmps = 20, powerWatts = 3000.0, cableCrossSectionMm2 = 4.0, cableLengthMeters = 16.0),
            CircuitBreaker(circuitNumber = 11, name = "EV Wallbox Charger", loadType = LoadType.EV_CHARGER, poles = 3, assignedPhase = Phase.THREE_PHASE, ratingAmps = 32, powerWatts = 11000.0, cableCrossSectionMm2 = 6.0, cableLengthMeters = 18.0),
            CircuitBreaker(circuitNumber = 12, name = "Swimming Pool Filter Pump", loadType = LoadType.MOTOR_PUMP, poles = 1, assignedPhase = Phase.L1, ratingAmps = 16, powerWatts = 1800.0, cableCrossSectionMm2 = 2.5, cableLengthMeters = 30.0),
            CircuitBreaker(circuitNumber = 13, name = "Solar PV Micro-Infeed", loadType = LoadType.SOLAR, poles = 3, assignedPhase = Phase.THREE_PHASE, ratingAmps = 20, powerWatts = 6000.0, cableCrossSectionMm2 = 4.0, cableLengthMeters = 22.0),
            CircuitBreaker(circuitNumber = 14, name = "Garage Workshop Feeder", loadType = LoadType.SUB_FEEDER, poles = 1, assignedPhase = Phase.L2, ratingAmps = 25, powerWatts = 4200.0, cableCrossSectionMm2 = 6.0, cableLengthMeters = 28.0)
        )

        val villa = PanelDesign(
            name = "Residential Villa Main Switchboard",
            tag = "MDB-VILLA",
            location = "Main Utility Room B1",
            systemVoltage = SystemVoltage.THREE_PHASE_400_230,
            mainBreakerAmps = 63,
            mainBreakerPoles = 4,
            enclosureRows = 3,
            modulesPerRow = 18,
            diversityFactor = 0.8,
            circuits = villaCircuits,
            notes = "Equipped with Type 2 SPD surge protection and 300mA time-delayed main RCD."
        )

        val officeCircuits = listOf(
            CircuitBreaker(circuitNumber = 1, name = "Open Office Lighting Zone A", loadType = LoadType.LIGHTING, poles = 1, assignedPhase = Phase.L1, ratingAmps = 16, powerWatts = 1600.0, cableCrossSectionMm2 = 2.5, cableLengthMeters = 32.0),
            CircuitBreaker(circuitNumber = 2, name = "Conference Rooms Lighting", loadType = LoadType.LIGHTING, poles = 1, assignedPhase = Phase.L2, ratingAmps = 10, powerWatts = 900.0, cableCrossSectionMm2 = 1.5, cableLengthMeters = 24.0),
            CircuitBreaker(circuitNumber = 3, name = "Workstation Desk Pods Ring 1", loadType = LoadType.SOCKETS, poles = 1, assignedPhase = Phase.L3, ratingAmps = 20, powerWatts = 3600.0, cableCrossSectionMm2 = 4.0, cableLengthMeters = 28.0),
            CircuitBreaker(circuitNumber = 4, name = "Workstation Desk Pods Ring 2", loadType = LoadType.SOCKETS, poles = 1, assignedPhase = Phase.L1, ratingAmps = 20, powerWatts = 3600.0, cableCrossSectionMm2 = 4.0, cableLengthMeters = 30.0),
            CircuitBreaker(circuitNumber = 5, name = "Server Room UPS Feeder", loadType = LoadType.SUB_FEEDER, poles = 3, assignedPhase = Phase.THREE_PHASE, ratingAmps = 40, powerWatts = 12000.0, cableCrossSectionMm2 = 10.0, cableLengthMeters = 15.0),
            CircuitBreaker(circuitNumber = 6, name = "VRF Air Conditioning Chiller", loadType = LoadType.HVAC, poles = 3, assignedPhase = Phase.THREE_PHASE, ratingAmps = 50, powerWatts = 18000.0, cableCrossSectionMm2 = 16.0, cableLengthMeters = 35.0),
            CircuitBreaker(circuitNumber = 7, name = "Pantry Coffee & Microwave", loadType = LoadType.KITCHEN, poles = 1, assignedPhase = Phase.L2, ratingAmps = 20, powerWatts = 3800.0, cableCrossSectionMm2 = 4.0, cableLengthMeters = 20.0),
            CircuitBreaker(circuitNumber = 8, name = "Emergency Exit Signs & Backup", loadType = LoadType.LIGHTING, poles = 1, assignedPhase = Phase.L3, ratingAmps = 10, powerWatts = 450.0, cableCrossSectionMm2 = 1.5, cableLengthMeters = 40.0)
        )

        val office = PanelDesign(
            name = "Commercial Office Floor 2 DB",
            tag = "DB-L2-OFFICE",
            location = "Electrical Riser Shaft 2",
            systemVoltage = SystemVoltage.THREE_PHASE_400_230,
            mainBreakerAmps = 100,
            mainBreakerPoles = 4,
            enclosureRows = 4,
            modulesPerRow = 24,
            diversityFactor = 0.85,
            circuits = officeCircuits,
            notes = "Includes dedicated clean earth busbar for IT racks."
        )

        return listOf(villa, office)
    }

    companion object {
        private const val TAG = "PanelRepository"
        const val COLLECTION_PANELS = "panels"

        fun resolveFirestore(context: Context): FirebaseFirestore? {
            return try {
                val app = FirebaseApp.getInstance()
                val dbId = context.applicationContext.getString(R.string.firestore_database_id)
                if (dbId.isNotBlank()) {
                    FirebaseFirestore.getInstance(app, dbId)
                } else {
                    FirebaseFirestore.getInstance(app)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to initialize named Firestore instance: ${e.message}", e)
                try { FirebaseFirestore.getInstance() } catch (ex: Exception) { null }
            }
        }
    }
}

// DTO for Firestore serialization
data class PanelDesignDto(
    var id: String = "",
    var userId: String = "",
    var name: String = "",
    var tag: String = "",
    var location: String = "",
    var systemVoltage: String = "",
    var mainBreakerAmps: Int = 100,
    var mainBreakerPoles: Int = 3,
    var enclosureRows: Int = 3,
    var modulesPerRow: Int = 18,
    var diversityFactor: Double = 0.85,
    var circuits: List<CircuitBreakerDto> = emptyList(),
    var notes: String = "",
    var createdAt: Long = 0L,
    var updatedAt: Long = 0L
) {
    fun toModel(): PanelDesign {
        val sysVolt = try { SystemVoltage.valueOf(systemVoltage) } catch (e: Exception) { SystemVoltage.THREE_PHASE_400_230 }
        return PanelDesign(
            id = id,
            userId = userId,
            name = name,
            tag = tag,
            location = location,
            systemVoltage = sysVolt,
            mainBreakerAmps = mainBreakerAmps,
            mainBreakerPoles = mainBreakerPoles,
            enclosureRows = enclosureRows,
            modulesPerRow = modulesPerRow,
            diversityFactor = diversityFactor,
            circuits = circuits.map { it.toModel() },
            notes = notes,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    companion object {
        fun fromModel(model: PanelDesign): PanelDesignDto {
            return PanelDesignDto(
                id = model.id,
                userId = model.userId,
                name = model.name,
                tag = model.tag,
                location = model.location,
                systemVoltage = model.systemVoltage.name,
                mainBreakerAmps = model.mainBreakerAmps,
                mainBreakerPoles = model.mainBreakerPoles,
                enclosureRows = model.enclosureRows,
                modulesPerRow = model.modulesPerRow,
                diversityFactor = model.diversityFactor,
                circuits = model.circuits.map { CircuitBreakerDto.fromModel(it) },
                notes = model.notes,
                createdAt = model.createdAt,
                updatedAt = model.updatedAt
            )
        }
    }
}

data class CircuitBreakerDto(
    var id: String = "",
    var circuitNumber: Int = 1,
    var name: String = "",
    var loadType: String = "",
    var poles: Int = 1,
    var assignedPhase: String = "",
    var ratingAmps: Int = 16,
    var powerWatts: Double = 0.0,
    var powerFactor: Double = 0.9,
    var cableLengthMeters: Double = 20.0,
    var cableCrossSectionMm2: Double = 2.5,
    var cableMaterial: String = "",
    var breakerCurve: String = "",
    var hasRcd: Boolean = true,
    var rcdRatingMa: Int = 30,
    var isOn: Boolean = true
) {
    fun toModel(): CircuitBreaker {
        val lt = try { LoadType.valueOf(loadType) } catch (e: Exception) { LoadType.SOCKETS }
        val ph = try { Phase.valueOf(assignedPhase) } catch (e: Exception) { Phase.L1 }
        val cm = try { CableMaterial.valueOf(cableMaterial) } catch (e: Exception) { CableMaterial.COPPER }
        val bc = try { BreakerCurve.valueOf(breakerCurve) } catch (e: Exception) { BreakerCurve.C }
        return CircuitBreaker(
            id = id,
            circuitNumber = circuitNumber,
            name = name,
            loadType = lt,
            poles = poles,
            assignedPhase = ph,
            ratingAmps = ratingAmps,
            powerWatts = powerWatts,
            powerFactor = powerFactor,
            cableLengthMeters = cableLengthMeters,
            cableCrossSectionMm2 = cableCrossSectionMm2,
            cableMaterial = cm,
            breakerCurve = bc,
            hasRcd = hasRcd,
            rcdRatingMa = rcdRatingMa,
            isOn = isOn
        )
    }

    companion object {
        fun fromModel(model: CircuitBreaker): CircuitBreakerDto {
            return CircuitBreakerDto(
                id = model.id,
                circuitNumber = model.circuitNumber,
                name = model.name,
                loadType = model.loadType.name,
                poles = model.poles,
                assignedPhase = model.assignedPhase.name,
                ratingAmps = model.ratingAmps,
                powerWatts = model.powerWatts,
                powerFactor = model.powerFactor,
                cableLengthMeters = model.cableLengthMeters,
                cableCrossSectionMm2 = model.cableCrossSectionMm2,
                cableMaterial = model.cableMaterial.name,
                breakerCurve = model.breakerCurve.name,
                hasRcd = model.hasRcd,
                rcdRatingMa = model.rcdRatingMa,
                isOn = model.isOn
            )
        }
    }
}
