package com.example.model

import java.util.UUID
import kotlin.math.round
import kotlin.math.sqrt

/**
 * Data model class representing an Electrical Load for load tracking,
 * demand sizing, voltage drop, and phase balance analysis.
 *
 * @param id Unique identifier
 * @param name Descriptive label of the load (e.g., "Air Compressor", "Kitchen Lighting")
 * @param phase Phase connection (Phase.L1, Phase.L2, Phase.L3, or Phase.THREE_PHASE)
 * @param voltage Operating line or phase voltage in Volts (e.g., 120.0, 208.0, 230.0, 400.0)
 * @param amperage Rated or operating current in Amperes
 * @param powerFactor Power factor (cos φ, between 0.1 and 1.0)
 * @param loadType Category of electrical load (LIGHTING, SOCKETS, HVAC, EV_CHARGER, etc.)
 * @param isContinuous Indicates continuous duty (3+ hours), requiring 125% sizing per NEC 210.19(A)(1)
 * @param cableLengthMeters Run distance in meters
 * @param cableCrossSectionMm2 Conductor cross section in mm²
 * @param cableMaterial Copper or Aluminum conductor
 * @param poles Number of poles (1, 2, 3, or 4)
 * @param ratingBreakerAmps Standard breaker protective rating in Amps
 * @param isOn Operational state for live simulation & load shedding
 */
data class ElectricalLoad(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Electrical Load",
    val phase: Phase = Phase.L1,
    val voltage: Double = 230.0,
    val amperage: Double = 10.0,
    val powerFactor: Double = 0.9,
    val loadType: LoadType = LoadType.SOCKETS,
    val isContinuous: Boolean = false,
    val cableLengthMeters: Double = 20.0,
    val cableCrossSectionMm2: Double = 2.5,
    val cableMaterial: CableMaterial = CableMaterial.COPPER,
    val poles: Int = 1,
    val ratingBreakerAmps: Int = 16,
    val isOn: Boolean = true
) {
    /**
     * Apparent Power in Volt-Amperes (VA).
     * For 3-Phase balanced loads: S = sqrt(3) * V_LL * I
     * For 1-Phase loads: S = V_LN * I
     */
    val apparentPowerVa: Double
        get() {
            return if (poles >= 3 || phase == Phase.THREE_PHASE) {
                sqrt(3.0) * voltage * amperage
            } else {
                voltage * amperage
            }
        }

    /**
     * Real Active Power in Watts (W): P = S * PF
     */
    val powerWatts: Double
        get() {
            return apparentPowerVa * powerFactor.coerceIn(0.1, 1.0)
        }

    /**
     * NEC Sizing VA incorporating the 125% continuous duty rule per NEC 215.2(A)(1).
     */
    val necDesignVa: Double
        get() {
            return if (isContinuous) apparentPowerVa * 1.25 else apparentPowerVa
        }

    /**
     * Converts this ElectricalLoad into a CircuitBreaker for compatibility with PanelDesign.
     */
    fun toCircuitBreaker(circuitNumber: Int = 1): CircuitBreaker {
        return CircuitBreaker(
            id = id,
            circuitNumber = circuitNumber,
            name = name,
            loadType = loadType,
            poles = poles,
            assignedPhase = phase,
            ratingAmps = ratingBreakerAmps,
            powerWatts = round(powerWatts * 10.0) / 10.0,
            powerFactor = powerFactor,
            cableLengthMeters = cableLengthMeters,
            cableCrossSectionMm2 = cableCrossSectionMm2,
            cableMaterial = cableMaterial,
            isOn = isOn
        )
    }

    companion object {
        /**
         * Builds an ElectricalLoad from an existing CircuitBreaker model.
         */
        fun fromCircuitBreaker(circuit: CircuitBreaker, systemVoltage: SystemVoltage): ElectricalLoad {
            val isThreePhase = circuit.poles >= 3 && systemVoltage.isThreePhase
            val nomVoltage = if (isThreePhase) systemVoltage.phaseToPhaseV else systemVoltage.phaseToNeutralV
            val pf = circuit.powerFactor.coerceIn(0.1, 1.0)
            val nominalAmps = if (isThreePhase) {
                if (nomVoltage > 0 && pf > 0) circuit.powerWatts / (sqrt(3.0) * nomVoltage * pf) else 0.0
            } else {
                if (nomVoltage > 0 && pf > 0) circuit.powerWatts / (nomVoltage * pf) else 0.0
            }

            val isCont = circuit.loadType in listOf(
                LoadType.LIGHTING,
                LoadType.HVAC,
                LoadType.EV_CHARGER,
                LoadType.WATER_HEATER
            )

            return ElectricalLoad(
                id = circuit.id,
                name = circuit.name,
                phase = circuit.assignedPhase,
                voltage = nomVoltage,
                amperage = round(nominalAmps * 100.0) / 100.0,
                powerFactor = circuit.powerFactor,
                loadType = circuit.loadType,
                isContinuous = isCont,
                cableLengthMeters = circuit.cableLengthMeters,
                cableCrossSectionMm2 = circuit.cableCrossSectionMm2,
                cableMaterial = circuit.cableMaterial,
                poles = circuit.poles,
                ratingBreakerAmps = circuit.ratingAmps,
                isOn = circuit.isOn
            )
        }
    }
}
