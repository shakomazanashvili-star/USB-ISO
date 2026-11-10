package com.example.calculation

import com.example.model.*
import kotlin.math.*

object ElectricalCalculator {

    private val STANDARD_BREAKER_RATINGS = listOf(6, 10, 16, 20, 25, 32, 40, 50, 63, 80, 100, 125, 160, 200, 250)

    // Standard copper cable ampacities (IEC 60364-5-52 reference method C - in conduit/enclosed)
    private val COPPER_AMPACITY_TABLE = listOf(
        1.5 to 18.0,
        2.5 to 25.0,
        4.0 to 34.0,
        6.0 to 44.0,
        10.0 to 61.0,
        16.0 to 82.0,
        25.0 to 108.0,
        35.0 to 135.0,
        50.0 to 168.0,
        70.0 to 207.0,
        95.0 to 250.0,
        120.0 to 292.0
    )

    fun calculateCircuit(circuit: CircuitBreaker, systemVoltage: SystemVoltage): CircuitCalculation {
        val pf = circuit.powerFactor.coerceIn(0.1, 1.0)
        val powerW = circuit.powerWatts

        // Determine nominal voltage and nominal current
        val nominalVoltage = if (circuit.poles >= 3 && systemVoltage.isThreePhase) {
            systemVoltage.phaseToPhaseV
        } else {
            systemVoltage.phaseToNeutralV
        }

        val nominalCurrent = if (circuit.poles >= 3 && systemVoltage.isThreePhase) {
            if (nominalVoltage > 0 && pf > 0) powerW / (sqrt(3.0) * nominalVoltage * pf) else 0.0
        } else {
            if (nominalVoltage > 0 && pf > 0) powerW / (nominalVoltage * pf) else 0.0
        }

        val apparentPowerVa = if (pf > 0) powerW / pf else powerW

        // Voltage drop calculation
        // Operating conductor resistivity: Cu approx 0.0225 ohm*mm2/m at 70°C; Al approx 0.036
        val rho = if (circuit.cableMaterial == CableMaterial.COPPER) 0.0225 else 0.036
        val crossSection = circuit.cableCrossSectionMm2.coerceAtLeast(1.0)
        val length = circuit.cableLengthMeters.coerceAtLeast(0.1)

        val resistancePerPhase = (rho * length) / crossSection
        val sinPhi = sqrt((1.0 - pf * pf).coerceAtLeast(0.0))
        val reactancePerPhase = 0.00008 * length // standard cable reactance ~0.08 mOhm/m

        val impedanceDrop = nominalCurrent * (resistancePerPhase * pf + reactancePerPhase * sinPhi)
        val voltageDropVolts = if (circuit.poles >= 3 && systemVoltage.isThreePhase) {
            sqrt(3.0) * impedanceDrop
        } else {
            2.0 * impedanceDrop
        }

        val voltageDropPercent = if (nominalVoltage > 0) (voltageDropVolts / nominalVoltage) * 100.0 else 0.0
        val maxAllowedDropPercent = if (circuit.loadType == LoadType.LIGHTING) 3.0 else 5.0
        val isVoltageDropWarning = voltageDropPercent > maxAllowedDropPercent

        val isOverloaded = nominalCurrent > circuit.ratingAmps.toDouble()

        // Sizing recommendations
        val recommendedBreakerAmps = recommendBreaker(nominalCurrent)
        val recommendedCableMm2 = recommendCable(
            currentAmps = max(nominalCurrent, recommendedBreakerAmps.toDouble()),
            lengthMeters = length,
            voltage = nominalVoltage,
            isThreePhase = circuit.poles >= 3 && systemVoltage.isThreePhase,
            isLighting = circuit.loadType == LoadType.LIGHTING,
            material = circuit.cableMaterial
        )

        return CircuitCalculation(
            circuitId = circuit.id,
            nominalCurrentAmps = round(nominalCurrent * 100.0) / 100.0,
            apparentPowerVa = round(apparentPowerVa * 10.0) / 10.0,
            voltageDropVolts = round(voltageDropVolts * 100.0) / 100.0,
            voltageDropPercent = round(voltageDropPercent * 100.0) / 100.0,
            recommendedBreakerAmps = recommendedBreakerAmps,
            recommendedCableMm2 = recommendedCableMm2,
            isVoltageDropWarning = isVoltageDropWarning,
            isOverloaded = isOverloaded
        )
    }

    fun recommendBreaker(designCurrent: Double): Int {
        val minRequired = designCurrent * 1.25 // standard continuous load safety margin
        for (rating in STANDARD_BREAKER_RATINGS) {
            if (rating >= minRequired) return rating
        }
        return STANDARD_BREAKER_RATINGS.last()
    }

    fun recommendCable(
        currentAmps: Double,
        lengthMeters: Double,
        voltage: Double,
        isThreePhase: Boolean,
        isLighting: Boolean,
        material: CableMaterial
    ): Double {
        val maxDropPct = if (isLighting) 3.0 else 4.0
        val rho = if (material == CableMaterial.COPPER) 0.0225 else 0.036

        for ((size, ampacity) in COPPER_AMPACITY_TABLE) {
            val adjustedAmpacity = if (material == CableMaterial.ALUMINUM) ampacity * 0.78 else ampacity
            if (adjustedAmpacity < currentAmps) continue

            // Check voltage drop constraint
            val vDrop = if (isThreePhase) {
                sqrt(3.0) * currentAmps * (rho * lengthMeters / size)
            } else {
                2.0 * currentAmps * (rho * lengthMeters / size)
            }
            val dropPct = if (voltage > 0) (vDrop / voltage) * 100.0 else 0.0
            if (dropPct <= maxDropPct) {
                return size
            }
        }
        return COPPER_AMPACITY_TABLE.last().first
    }

    fun calculatePanel(panel: PanelDesign): PanelCalculationSummary {
        val circuitsSummary = mutableMapOf<String, CircuitCalculation>()

        var totalConnectedWatts = 0.0
        var totalActiveWatts = 0.0
        var totalApparentVa = 0.0

        var pL1 = 0.0
        var pL2 = 0.0
        var pL3 = 0.0

        var sumPfL1 = 0.0
        var countL1 = 0.0
        var sumPfL2 = 0.0
        var countL2 = 0.0
        var sumPfL3 = 0.0
        var countL3 = 0.0

        for (circuit in panel.circuits) {
            val calc = calculateCircuit(circuit, panel.systemVoltage)
            circuitsSummary[circuit.id] = calc

            val pW = circuit.powerWatts
            totalConnectedWatts += pW

            if (!circuit.isOn) continue

            totalActiveWatts += pW
            totalApparentVa += calc.apparentPowerVa

            if (panel.systemVoltage.isThreePhase) {
                if (circuit.poles >= 3 || circuit.assignedPhase == Phase.THREE_PHASE) {
                    val partW = pW / 3.0
                    pL1 += partW
                    pL2 += partW
                    pL3 += partW
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
                // 1-phase panel
                pL1 += pW
                sumPfL1 += circuit.powerFactor
                countL1++
            }
        }

        val diversity = panel.diversityFactor.coerceIn(0.1, 1.0)
        val totalDemandWatts = totalActiveWatts * diversity

        val vPn = panel.systemVoltage.phaseToNeutralV.coerceAtLeast(1.0)

        val pfL1 = if (countL1 > 0) (sumPfL1 / countL1).coerceIn(0.7, 1.0) else 0.95
        val pfL2 = if (countL2 > 0) (sumPfL2 / countL2).coerceIn(0.7, 1.0) else 0.95
        val pfL3 = if (countL3 > 0) (sumPfL3 / countL3).coerceIn(0.7, 1.0) else 0.95

        val currentL1 = (pL1 * diversity) / (vPn * pfL1)
        val currentL2 = if (panel.systemVoltage.isThreePhase) (pL2 * diversity) / (vPn * pfL2) else 0.0
        val currentL3 = if (panel.systemVoltage.isThreePhase) (pL3 * diversity) / (vPn * pfL3) else 0.0

        // Neutral current calculation for 3-phase systems
        val neutralCurrent = if (panel.systemVoltage.isThreePhase) {
            val i1 = currentL1
            val i2 = currentL2
            val i3 = currentL3
            val term = i1 * i1 + i2 * i2 + i3 * i3 - (i1 * i2 + i2 * i3 + i3 * i1)
            sqrt(max(0.0, term))
        } else {
            currentL1
        }

        val phaseCurrents = if (panel.systemVoltage.isThreePhase) {
            listOf(currentL1, currentL2, currentL3)
        } else {
            listOf(currentL1)
        }

        val maxCurrent = phaseCurrents.maxOrNull() ?: 0.0
        val avgCurrent = phaseCurrents.average()

        val unbalancePercent = if (panel.systemVoltage.isThreePhase && avgCurrent > 0.1) {
            val maxDev = maxOf(
                abs(currentL1 - avgCurrent),
                abs(currentL2 - avgCurrent),
                abs(currentL3 - avgCurrent)
            )
            (maxDev / avgCurrent) * 100.0
        } else {
            0.0
        }

        val isPanelOverloaded = maxCurrent > panel.mainBreakerAmps.toDouble()
        val mainBreakerLoadingPercent = if (panel.mainBreakerAmps > 0) {
            (maxCurrent / panel.mainBreakerAmps.toDouble()) * 100.0
        } else {
            0.0
        }

        return PanelCalculationSummary(
            totalConnectedWatts = round(totalConnectedWatts * 10.0) / 10.0,
            totalDemandWatts = round(totalDemandWatts * 10.0) / 10.0,
            totalApparentVa = round(totalApparentVa * 10.0) / 10.0,
            phaseL1Watts = round(pL1 * 10.0) / 10.0,
            phaseL2Watts = round(pL2 * 10.0) / 10.0,
            phaseL3Watts = round(pL3 * 10.0) / 10.0,
            phaseL1Amps = round(currentL1 * 100.0) / 100.0,
            phaseL2Amps = round(currentL2 * 100.0) / 100.0,
            phaseL3Amps = round(currentL3 * 100.0) / 100.0,
            neutralCurrentAmps = round(neutralCurrent * 100.0) / 100.0,
            maxPhaseAmps = round(maxCurrent * 100.0) / 100.0,
            averagePhaseAmps = round(avgCurrent * 100.0) / 100.0,
            unbalancePercent = round(unbalancePercent * 10.0) / 10.0,
            isPanelOverloaded = isPanelOverloaded,
            mainBreakerLoadingPercent = round(mainBreakerLoadingPercent * 10.0) / 10.0,
            circuitsSummary = circuitsSummary
        )
    }

    /**
     * Automatic Phase Balancing Algorithm:
     * Sorts 1-phase circuits in descending order of power and distributes them
     * onto L1, L2, L3 using greedy multi-way bin balancing to achieve minimum variance.
     */
    fun autoBalancePhases(panel: PanelDesign): PanelDesign {
        if (!panel.systemVoltage.isThreePhase) return panel

        val threePhaseCircuits = panel.circuits.filter { it.poles >= 3 || it.assignedPhase == Phase.THREE_PHASE }
        val singlePhaseCircuits = panel.circuits.filter { it.poles < 3 && it.assignedPhase != Phase.THREE_PHASE }
            .sortedByDescending { it.powerWatts }

        var loadL1 = 0.0
        var loadL2 = 0.0
        var loadL3 = 0.0

        // Base 3-phase load
        val threePhasePart = threePhaseCircuits.sumOf { it.powerWatts } / 3.0
        loadL1 += threePhasePart
        loadL2 += threePhasePart
        loadL3 += threePhasePart

        val reallocatedSingles = mutableListOf<CircuitBreaker>()

        for (circuit in singlePhaseCircuits) {
            // Assign to the phase currently carrying minimum load
            val minPhase = when {
                loadL1 <= loadL2 && loadL1 <= loadL3 -> Phase.L1
                loadL2 <= loadL1 && loadL2 <= loadL3 -> Phase.L2
                else -> Phase.L3
            }

            when (minPhase) {
                Phase.L1 -> loadL1 += circuit.powerWatts
                Phase.L2 -> loadL2 += circuit.powerWatts
                Phase.L3 -> loadL3 += circuit.powerWatts
                else -> {}
            }

            reallocatedSingles.add(circuit.copy(assignedPhase = minPhase))
        }

        // Recombine while preserving original circuit numbers / order
        val allReallocatedMap = reallocatedSingles.associateBy { it.id }
        val newCircuits = panel.circuits.map { circuit ->
            allReallocatedMap[circuit.id] ?: circuit
        }

        return panel.copy(circuits = newCircuits, updatedAt = System.currentTimeMillis())
    }
}
