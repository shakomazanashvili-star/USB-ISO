package com.example.calculation

import com.example.model.*
import kotlin.math.*

/**
 * NEC Standards Compliance Status for Voltage Drop (NEC 210.19(A) & NEC 215.2(A)(1))
 */
enum class VoltageDropStatus(val label: String, val isCompliant: Boolean, val description: String) {
    COMPLIANT("Compliant", true, "Within NEC 3% branch circuit guideline"),
    WARNING("Warning (>3%)", false, "Exceeds 3% branch guideline, within 5% total system limit"),
    CRITICAL("Critical (>5%)", false, "Violates NEC 5% overall efficiency limit (causes equipment stress/heat)")
}

/**
 * NEC / NEMA / IEEE Phase Imbalance Classification (NEC 220.61)
 */
enum class PhaseImbalanceStatus(val label: String, val description: String, val hexColor: Long) {
    EXCELLENT("Excellent (<5%)", "Optimal phase distribution across all phases", 0xFF10B981),
    ACCEPTABLE("Acceptable (5-10%)", "Standard operating range under normal load variations", 0xFF3B82F6),
    MODERATE("Moderate (10-15%)", "Phase rebalancing recommended to reduce neutral conductor heating", 0xFFF59E0B),
    SEVERE("Severe (>15%)", "Violates balance guidelines; high neutral current risk (NEC 220.61)", 0xFFEF4444)
}

/**
 * Detailed voltage drop calculation for an individual branch circuit under NEC.
 */
data class NecCircuitCalculation(
    val circuitId: String,
    val circuitNumber: Int,
    val circuitName: String,
    val nominalCurrentAmps: Double,
    val continuousDutyCurrentAmps: Double,
    val apparentPowerVa: Double,
    val voltageDropVolts: Double,
    val voltageDropPercent: Double,
    val voltageDropStatus: VoltageDropStatus,
    val minNecConductorMm2: Double,
    val minNecBreakerAmps: Int,
    val isOverloaded: Boolean
)

/**
 * Comprehensive load calculation summary based on NEC Standards (Articles 210, 215, 220, 240, 310).
 */
data class NecLoadCalculationSummary(
    // Connected Loads
    val totalConnectedWatts: Double,
    val totalConnectedVa: Double,
    val totalActiveCircuitsCount: Int,
    val totalCircuitsCount: Int,

    // NEC Demand & Continuous Load Calculations (NEC 210.19(A)(1), 220)
    val continuousLoadVa: Double,
    val nonContinuousLoadVa: Double,
    val totalDemandWatts: Double,
    val totalDemandVa: Double,
    val necDesignVa: Double, // (1.25 * Continuous + 1.0 * NonContinuous) * diversity

    // Amperage
    val totalAmperageAmps: Double, // NEC Total Design Current
    val phaseL1Amps: Double,
    val phaseL2Amps: Double,
    val phaseL3Amps: Double,
    val maxPhaseAmps: Double,
    val averagePhaseAmps: Double,
    val neutralCurrentAmps: Double, // Calculated per NEC 220.61

    // Voltage Drop (NEC 210.19(A) Note 4 & 215.2(A)(1) Note 2)
    val maxVoltageDropPercent: Double,
    val worstCircuitVoltageDrop: NecCircuitCalculation?,
    val isOverallVoltageDropCompliant: Boolean, // All circuits <= 3%

    // Phase Imbalance (NEMA / NEC 220.61)
    val phaseImbalancePercent: Double,
    val phaseImbalanceStatus: PhaseImbalanceStatus,

    // Overcurrent Protection (NEC 240.6)
    val recommendedMainBreakerAmps: Int,
    val mainBreakerLoadingPercent: Double,
    val isMainBreakerOverloaded: Boolean,

    // Individual Circuit Breakdown
    val circuitCalculations: List<NecCircuitCalculation>,

    // NEC Compliance Notes
    val necComplianceNotes: List<String>
)

/**
 * Utility calculating connected loads, total amperage, voltage drop, and phase imbalance
 * strictly aligned with the National Electrical Code (NEC).
 */
object NecStandardsCalculator {

    // Standard NEC Overcurrent Protective Device (OCPD) ratings per NEC 240.6(A)
    val STANDARD_NEC_BREAKER_RATINGS = listOf(
        15, 20, 25, 30, 35, 40, 45, 50, 60, 70, 80, 90, 100, 110, 125,
        150, 175, 200, 225, 250, 300, 350, 400, 450, 500, 600
    )

    // Copper conductor ampacities at 75°C (NEC Table 310.16)
    private val COPPER_AMPACITY_TABLE_75C = listOf(
        1.5 to 18.0,   // ~14 AWG (15A max OCPD per 240.4(D))
        2.5 to 25.0,   // ~12 AWG (20A max OCPD per 240.4(D))
        4.0 to 35.0,   // ~10 AWG (30A max OCPD per 240.4(D))
        6.0 to 50.0,   // ~8 AWG
        10.0 to 65.0,  // ~6 AWG
        16.0 to 85.0,  // ~4 AWG
        25.0 to 115.0, // ~2 AWG
        35.0 to 130.0, // ~1 AWG
        50.0 to 175.0, // ~1/0 AWG
        70.0 to 230.0, // ~2/0 AWG
        95.0 to 255.0, // ~3/0 AWG
        120.0 to 285.0 // ~4/0 AWG
    )

    /**
     * Calculates single circuit voltage drop and NEC sizing.
     */
    fun calculateNecCircuit(circuit: CircuitBreaker, systemVoltage: SystemVoltage): NecCircuitCalculation {
        val pf = circuit.powerFactor.coerceIn(0.1, 1.0)
        val powerW = circuit.powerWatts
        val apparentPowerVa = if (pf > 0) powerW / pf else powerW

        val isThreePhase = circuit.poles >= 3 && systemVoltage.isThreePhase
        val nominalVoltage = if (isThreePhase) systemVoltage.phaseToPhaseV else systemVoltage.phaseToNeutralV

        val nominalCurrent = if (isThreePhase) {
            if (nominalVoltage > 0 && pf > 0) powerW / (sqrt(3.0) * nominalVoltage * pf) else 0.0
        } else {
            if (nominalVoltage > 0 && pf > 0) powerW / (nominalVoltage * pf) else 0.0
        }

        // NEC 210.19(A)(1): Continuous load factor = 125%
        // Lighting, EV, HVAC, and Water Heaters are classified continuous by default unless overridden
        val isContinuous = isContinuousLoad(circuit)
        val designFactor = if (isContinuous) 1.25 else 1.0
        val continuousDutyCurrent = nominalCurrent * designFactor

        // Voltage Drop Calculation per NEC Chapter 9 Tables 8 & 9
        // rho at 75°C: Cu = 0.0225 ohm*mm2/m, Al = 0.036 ohm*mm2/m
        val rho = if (circuit.cableMaterial == CableMaterial.COPPER) 0.0225 else 0.036
        val crossSection = circuit.cableCrossSectionMm2.coerceAtLeast(1.0)
        val length = circuit.cableLengthMeters.coerceAtLeast(0.1)

        val resistance = (rho * length) / crossSection
        val sinPhi = sqrt((1.0 - pf * pf).coerceAtLeast(0.0))
        val reactance = 0.00008 * length // standard AC conduit reactance

        val impedanceDrop = nominalCurrent * (resistance * pf + reactance * sinPhi)
        val voltageDropVolts = if (isThreePhase) {
            sqrt(3.0) * impedanceDrop
        } else {
            2.0 * impedanceDrop
        }

        val voltageDropPercent = if (nominalVoltage > 0) (voltageDropVolts / nominalVoltage) * 100.0 else 0.0

        val vDropStatus = when {
            voltageDropPercent <= 3.0 -> VoltageDropStatus.COMPLIANT
            voltageDropPercent <= 5.0 -> VoltageDropStatus.WARNING
            else -> VoltageDropStatus.CRITICAL
        }

        val isOverloaded = nominalCurrent > circuit.ratingAmps.toDouble()

        // Minimum recommended OCPD per NEC 240.4
        val minBreaker = recommendNecBreaker(continuousDutyCurrent)
        val minCable = recommendNecCable(
            currentAmps = max(continuousDutyCurrent, minBreaker.toDouble()),
            lengthMeters = length,
            voltage = nominalVoltage,
            isThreePhase = isThreePhase,
            material = circuit.cableMaterial
        )

        return NecCircuitCalculation(
            circuitId = circuit.id,
            circuitNumber = circuit.circuitNumber,
            circuitName = circuit.name,
            nominalCurrentAmps = round(nominalCurrent * 100.0) / 100.0,
            continuousDutyCurrentAmps = round(continuousDutyCurrent * 100.0) / 100.0,
            apparentPowerVa = round(apparentPowerVa * 10.0) / 10.0,
            voltageDropVolts = round(voltageDropVolts * 100.0) / 100.0,
            voltageDropPercent = round(voltageDropPercent * 100.0) / 100.0,
            voltageDropStatus = vDropStatus,
            minNecConductorMm2 = minCable,
            minNecBreakerAmps = minBreaker,
            isOverloaded = isOverloaded
        )
    }

    /**
     * Performs complete panel load tracking, total amperage, voltage drop, and phase imbalance calculations.
     */
    fun calculateNecPanel(
        circuits: List<CircuitBreaker>,
        systemVoltage: SystemVoltage,
        mainBreakerAmps: Int,
        diversityFactor: Double = 1.0
    ): NecLoadCalculationSummary {
        val circuitCalcs = mutableListOf<NecCircuitCalculation>()

        var totalConnectedWatts = 0.0
        var totalConnectedVa = 0.0
        var continuousLoadVa = 0.0
        var nonContinuousLoadVa = 0.0

        var pL1 = 0.0
        var pL2 = 0.0
        var pL3 = 0.0

        var sumPfL1 = 0.0; var countL1 = 0
        var sumPfL2 = 0.0; var countL2 = 0
        var sumPfL3 = 0.0; var countL3 = 0

        var activeCircuits = 0

        for (circuit in circuits) {
            val calc = calculateNecCircuit(circuit, systemVoltage)
            circuitCalcs.add(calc)

            val pW = circuit.powerWatts
            val pVa = calc.apparentPowerVa
            totalConnectedWatts += pW
            totalConnectedVa += pVa

            if (!circuit.isOn) continue
            activeCircuits++

            if (isContinuousLoad(circuit)) {
                continuousLoadVa += pVa
            } else {
                nonContinuousLoadVa += pVa
            }

            // Phase allocation
            if (systemVoltage.isThreePhase) {
                if (circuit.poles >= 3 || circuit.assignedPhase == Phase.THREE_PHASE) {
                    val partW = pW / 3.0
                    pL1 += partW; pL2 += partW; pL3 += partW
                    sumPfL1 += circuit.powerFactor; countL1++
                    sumPfL2 += circuit.powerFactor; countL2++
                    sumPfL3 += circuit.powerFactor; countL3++
                } else {
                    when (circuit.assignedPhase) {
                        Phase.L1 -> { pL1 += pW; sumPfL1 += circuit.powerFactor; countL1++ }
                        Phase.L2 -> { pL2 += pW; sumPfL2 += circuit.powerFactor; countL2++ }
                        Phase.L3 -> { pL3 += pW; sumPfL3 += circuit.powerFactor; countL3++ }
                        else -> { pL1 += pW; sumPfL1 += circuit.powerFactor; countL1++ }
                    }
                }
            } else {
                pL1 += pW
                sumPfL1 += circuit.powerFactor
                countL1++
            }
        }

        val clampedDiversity = diversityFactor.coerceIn(0.1, 1.0)
        val totalDemandWatts = (pL1 + pL2 + pL3) * clampedDiversity
        val totalDemandVa = (continuousLoadVa + nonContinuousLoadVa) * clampedDiversity

        // NEC Design VA: 125% of continuous + 100% of non-continuous per NEC 215.2(A)(1)
        val necDesignVa = (continuousLoadVa * 1.25 + nonContinuousLoadVa) * clampedDiversity

        val vPn = systemVoltage.phaseToNeutralV.coerceAtLeast(1.0)
        val vLl = systemVoltage.phaseToPhaseV.coerceAtLeast(1.0)

        // Phase amperages
        val pfL1 = if (countL1 > 0) (sumPfL1 / countL1).coerceIn(0.7, 1.0) else 0.95
        val pfL2 = if (countL2 > 0) (sumPfL2 / countL2).coerceIn(0.7, 1.0) else 0.95
        val pfL3 = if (countL3 > 0) (sumPfL3 / countL3).coerceIn(0.7, 1.0) else 0.95

        val currentL1 = (pL1 * clampedDiversity) / (vPn * pfL1)
        val currentL2 = if (systemVoltage.isThreePhase) (pL2 * clampedDiversity) / (vPn * pfL2) else 0.0
        val currentL3 = if (systemVoltage.isThreePhase) (pL3 * clampedDiversity) / (vPn * pfL3) else 0.0

        val totalAmperageAmps = if (systemVoltage.isThreePhase) {
            necDesignVa / (sqrt(3.0) * vLl)
        } else {
            necDesignVa / vPn
        }

        // Neutral Current calculation per NEC 220.61
        val neutralCurrent = if (systemVoltage.isThreePhase) {
            val i1 = currentL1; val i2 = currentL2; val i3 = currentL3
            val term = i1 * i1 + i2 * i2 + i3 * i3 - (i1 * i2 + i2 * i3 + i3 * i1)
            sqrt(max(0.0, term))
        } else {
            currentL1
        }

        val phaseCurrents = if (systemVoltage.isThreePhase) listOf(currentL1, currentL2, currentL3) else listOf(currentL1)
        val maxPhaseCurrent = phaseCurrents.maxOrNull() ?: 0.0
        val avgPhaseCurrent = phaseCurrents.average()

        // Phase Imbalance calculation (NEMA / IEEE standard)
        val phaseImbalancePercent = if (systemVoltage.isThreePhase && avgPhaseCurrent > 0.1) {
            val maxDev = maxOf(
                abs(currentL1 - avgPhaseCurrent),
                abs(currentL2 - avgPhaseCurrent),
                abs(currentL3 - avgPhaseCurrent)
            )
            (maxDev / avgPhaseCurrent) * 100.0
        } else {
            0.0
        }

        val imbalanceStatus = when {
            !systemVoltage.isThreePhase -> PhaseImbalanceStatus.EXCELLENT
            phaseImbalancePercent < 5.0 -> PhaseImbalanceStatus.EXCELLENT
            phaseImbalancePercent <= 10.0 -> PhaseImbalanceStatus.ACCEPTABLE
            phaseImbalancePercent <= 15.0 -> PhaseImbalanceStatus.MODERATE
            else -> PhaseImbalanceStatus.SEVERE
        }

        // Voltage Drop summary
        val activeCalculations = circuitCalcs.filter { calc -> circuits.any { it.id == calc.circuitId && it.isOn } }
        val worstCircuit = activeCalculations.maxByOrNull { it.voltageDropPercent }
        val maxVoltageDropPct = worstCircuit?.voltageDropPercent ?: 0.0
        val isOverallVdCompliant = maxVoltageDropPct <= 3.0

        // Recommended Main OCPD Breaker per NEC 240.6
        val recommendedMainBreaker = recommendNecBreaker(totalAmperageAmps)
        val mainBreakerLoadingPct = if (mainBreakerAmps > 0) {
            (maxPhaseCurrent / mainBreakerAmps.toDouble()) * 100.0
        } else 0.0
        val isMainOverloaded = maxPhaseCurrent > mainBreakerAmps.toDouble()

        // NEC Compliance Diagnostic Notes
        val notes = mutableListOf<String>()
        if (isOverallVdCompliant) {
            notes.add("NEC 210.19(A) Note 4: Branch circuit voltage drops are fully compliant (<= 3.0%).")
        } else {
            notes.add("NEC 210.19(A) Note 4: One or more branch circuits exceed 3.0% voltage drop. Consider upgrading conductor cross-section.")
        }

        if (phaseImbalancePercent > 10.0 && systemVoltage.isThreePhase) {
            notes.add("NEC 220.61 / NEMA: High phase unbalance (${round(phaseImbalancePercent * 10.0) / 10.0}%). Auto-balance phases to reduce neutral conductor heating.")
        } else if (systemVoltage.isThreePhase) {
            notes.add("NEC 220.61: Phase balance is within standard limits (${round(phaseImbalancePercent * 10.0) / 10.0}% unbalance).")
        }

        if (continuousLoadVa > 0) {
            notes.add("NEC 215.2(A)(1): Continuous loads include 125% factor (${round(continuousLoadVa * 1.25)} VA continuous demand).")
        }

        if (isMainOverloaded) {
            notes.add("NEC 240.4 Warning: Panel is overloaded! Peak phase current (${round(maxPhaseCurrent * 10.0) / 10.0}A) exceeds ${mainBreakerAmps}A main OCPD rating.")
        }

        return NecLoadCalculationSummary(
            totalConnectedWatts = round(totalConnectedWatts * 10.0) / 10.0,
            totalConnectedVa = round(totalConnectedVa * 10.0) / 10.0,
            totalActiveCircuitsCount = activeCircuits,
            totalCircuitsCount = circuits.size,
            continuousLoadVa = round(continuousLoadVa * 10.0) / 10.0,
            nonContinuousLoadVa = round(nonContinuousLoadVa * 10.0) / 10.0,
            totalDemandWatts = round(totalDemandWatts * 10.0) / 10.0,
            totalDemandVa = round(totalDemandVa * 10.0) / 10.0,
            necDesignVa = round(necDesignVa * 10.0) / 10.0,
            totalAmperageAmps = round(totalAmperageAmps * 100.0) / 100.0,
            phaseL1Amps = round(currentL1 * 100.0) / 100.0,
            phaseL2Amps = round(currentL2 * 100.0) / 100.0,
            phaseL3Amps = round(currentL3 * 100.0) / 100.0,
            maxPhaseAmps = round(maxPhaseCurrent * 100.0) / 100.0,
            averagePhaseAmps = round(avgPhaseCurrent * 100.0) / 100.0,
            neutralCurrentAmps = round(neutralCurrent * 100.0) / 100.0,
            maxVoltageDropPercent = round(maxVoltageDropPct * 100.0) / 100.0,
            worstCircuitVoltageDrop = worstCircuit,
            isOverallVoltageDropCompliant = isOverallVdCompliant,
            phaseImbalancePercent = round(phaseImbalancePercent * 10.0) / 10.0,
            phaseImbalanceStatus = imbalanceStatus,
            recommendedMainBreakerAmps = recommendedMainBreaker,
            mainBreakerLoadingPercent = round(mainBreakerLoadingPct * 10.0) / 10.0,
            isMainBreakerOverloaded = isMainOverloaded,
            circuitCalculations = circuitCalcs,
            necComplianceNotes = notes
        )
    }

    fun isContinuousLoad(circuit: CircuitBreaker): Boolean {
        // Continuous if explicitly flagged, or typical continuous NEC categories: EV, HVAC, Lighting, Water Heater
        return when (circuit.loadType) {
            LoadType.EV_CHARGER,
            LoadType.HVAC,
            LoadType.WATER_HEATER,
            LoadType.LIGHTING -> true
            else -> false
        }
    }

    fun recommendNecBreaker(designAmps: Double): Int {
        val minRequired = designAmps * 1.0 // continuous factor already applied where applicable
        for (rating in STANDARD_NEC_BREAKER_RATINGS) {
            if (rating >= minRequired) return rating
        }
        return STANDARD_NEC_BREAKER_RATINGS.last()
    }

    fun recommendNecCable(
        currentAmps: Double,
        lengthMeters: Double,
        voltage: Double,
        isThreePhase: Boolean,
        material: CableMaterial
    ): Double {
        val rho = if (material == CableMaterial.COPPER) 0.0225 else 0.036
        for ((size, ampacity) in COPPER_AMPACITY_TABLE_75C) {
            val adjustedAmpacity = if (material == CableMaterial.ALUMINUM) ampacity * 0.78 else ampacity
            if (adjustedAmpacity < currentAmps) continue

            val vDrop = if (isThreePhase) {
                sqrt(3.0) * currentAmps * (rho * lengthMeters / size)
            } else {
                2.0 * currentAmps * (rho * lengthMeters / size)
            }
            val dropPct = if (voltage > 0) (vDrop / voltage) * 100.0 else 0.0
            if (dropPct <= 3.0) {
                return size
            }
        }
        return COPPER_AMPACITY_TABLE_75C.last().first
    }

    /**
     * Calculates connected loads, total amperage, voltage drop, and phase imbalance directly from a list of ElectricalLoad.
     */
    fun calculateNecLoads(
        loads: List<ElectricalLoad>,
        systemVoltage: SystemVoltage,
        mainBreakerAmps: Int,
        diversityFactor: Double = 1.0
    ): NecLoadCalculationSummary {
        val circuits = loads.mapIndexed { index, load -> load.toCircuitBreaker(index + 1) }
        return calculateNecPanel(circuits, systemVoltage, mainBreakerAmps, diversityFactor)
    }
}
