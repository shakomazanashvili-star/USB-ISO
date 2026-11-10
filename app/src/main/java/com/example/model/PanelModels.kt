package com.example.model

enum class Phase(val label: String, val shortName: String, val hexColor: Long) {
    L1("Phase 1 (L1 / R)", "L1", 0xFFEF4444),
    L2("Phase 2 (L2 / S)", "L2", 0xFFF59E0B),
    L3("Phase 3 (L3 / T)", "L3", 0xFF3B82F6),
    THREE_PHASE("3-Phase (L1-L2-L3)", "3P", 0xFF8B5CF6)
}

enum class LoadType(val displayName: String, val defaultPowerW: Double, val defaultPf: Double) {
    LIGHTING("Lighting Circuit", 1200.0, 0.95),
    SOCKETS("General Power Sockets", 2500.0, 0.9),
    KITCHEN("Kitchen Heavy Appliances", 4000.0, 0.95),
    HVAC("Air Conditioning / Heat Pump", 3500.0, 0.85),
    WATER_HEATER("Electric Water Heater", 3000.0, 1.0),
    EV_CHARGER("EV Vehicle Charger", 7400.0, 0.98),
    SOLAR("Solar PV Inverter Feed", 5000.0, 1.0),
    MOTOR_PUMP("Motor / Water Pump", 2200.0, 0.8),
    SUB_FEEDER("Sub-distribution Feeder", 10000.0, 0.9),
    SPARE("Spare / Provision", 0.0, 1.0)
}

enum class BreakerCurve(val label: String, val tripFactor: String) {
    B("Type B", "3 - 5x In (Resistive / Domestic)"),
    C("Type C", "5 - 10x In (General / Inductive)"),
    D("Type D", "10 - 20x In (High Inrush Motors)")
}

enum class CableMaterial(val label: String, val resistivity: Double) {
    COPPER("Copper (Cu)", 0.0175), // ohm*mm2/m
    ALUMINUM("Aluminum (Al)", 0.028)
}

enum class SystemVoltage(val label: String, val phaseToNeutralV: Double, val phaseToPhaseV: Double, val isThreePhase: Boolean) {
    THREE_PHASE_400_230("3-Phase 400V / 230V (EU / IEC)", 230.0, 400.0, true),
    THREE_PHASE_415_240("3-Phase 415V / 240V (UK / AUS)", 240.0, 415.0, true),
    THREE_PHASE_208_120("3-Phase 208V / 120V (US / NEC)", 120.0, 208.0, true),
    SINGLE_PHASE_230("1-Phase 230V (EU / IEC)", 230.0, 230.0, false),
    SINGLE_PHASE_120("1-Phase 120V (US / NEC)", 120.0, 120.0, false)
}

data class CircuitBreaker(
    val id: String = java.util.UUID.randomUUID().toString(),
    val circuitNumber: Int = 1,
    val name: String = "Circuit",
    val loadType: LoadType = LoadType.SOCKETS,
    val poles: Int = 1, // 1P, 2P, 3P, 4P
    val assignedPhase: Phase = Phase.L1,
    val ratingAmps: Int = 16,
    val powerWatts: Double = 2300.0,
    val powerFactor: Double = 0.9,
    val cableLengthMeters: Double = 20.0,
    val cableCrossSectionMm2: Double = 2.5,
    val cableMaterial: CableMaterial = CableMaterial.COPPER,
    val breakerCurve: BreakerCurve = BreakerCurve.C,
    val hasRcd: Boolean = true,
    val rcdRatingMa: Int = 30,
    val isOn: Boolean = true
) {
    val moduleWidth: Int get() = when {
        poles >= 3 -> 3
        poles == 2 -> 2
        else -> 1
    }
}

data class PanelDesign(
    val id: String = java.util.UUID.randomUUID().toString(),
    val userId: String = "",
    val name: String = "Main Distribution Board MDB-1",
    val tag: String = "MDB-01",
    val location: String = "Electrical Room B1",
    val systemVoltage: SystemVoltage = SystemVoltage.THREE_PHASE_400_230,
    val mainBreakerAmps: Int = 100,
    val mainBreakerPoles: Int = 3,
    val enclosureRows: Int = 3,
    val modulesPerRow: Int = 18,
    val diversityFactor: Double = 0.85,
    val circuits: List<CircuitBreaker> = emptyList(),
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    val totalEnclosureCapacity: Int get() = enclosureRows * modulesPerRow
    val usedModules: Int get() = circuits.sumOf { it.moduleWidth }
    val freeModules: Int get() = (totalEnclosureCapacity - usedModules).coerceAtLeast(0)
}

data class CircuitCalculation(
    val circuitId: String,
    val nominalCurrentAmps: Double,
    val apparentPowerVa: Double,
    val voltageDropVolts: Double,
    val voltageDropPercent: Double,
    val recommendedBreakerAmps: Int,
    val recommendedCableMm2: Double,
    val isVoltageDropWarning: Boolean,
    val isOverloaded: Boolean
)

data class PanelCalculationSummary(
    val totalConnectedWatts: Double,
    val totalDemandWatts: Double,
    val totalApparentVa: Double,
    val phaseL1Watts: Double,
    val phaseL2Watts: Double,
    val phaseL3Watts: Double,
    val phaseL1Amps: Double,
    val phaseL2Amps: Double,
    val phaseL3Amps: Double,
    val neutralCurrentAmps: Double,
    val maxPhaseAmps: Double,
    val averagePhaseAmps: Double,
    val unbalancePercent: Double,
    val isPanelOverloaded: Boolean,
    val mainBreakerLoadingPercent: Double,
    val circuitsSummary: Map<String, CircuitCalculation>
)
