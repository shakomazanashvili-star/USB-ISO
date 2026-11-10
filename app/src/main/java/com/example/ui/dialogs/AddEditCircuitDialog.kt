package com.example.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ai.GeminiClient
import com.example.calculation.ElectricalCalculator
import com.example.model.*
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditCircuitDialog(
    initialCircuit: CircuitBreaker?,
    systemVoltage: SystemVoltage,
    nextCircuitNumber: Int,
    onDismiss: () -> Unit,
    onSave: (CircuitBreaker) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()

    var name by remember { mutableStateOf(initialCircuit?.name ?: "Branch Circuit $nextCircuitNumber") }
    var selectedLoadType by remember { mutableStateOf(initialCircuit?.loadType ?: LoadType.SOCKETS) }
    var poles by remember { mutableIntStateOf(initialCircuit?.poles ?: 1) }
    var assignedPhase by remember { mutableStateOf(initialCircuit?.assignedPhase ?: Phase.L1) }
    var powerWattsStr by remember { mutableStateOf(initialCircuit?.powerWatts?.toInt()?.toString() ?: "2300") }
    var powerFactorStr by remember { mutableStateOf(initialCircuit?.powerFactor?.toString() ?: "0.90") }
    var cableLengthStr by remember { mutableStateOf(initialCircuit?.cableLengthMeters?.toInt()?.toString() ?: "20") }
    var cableCrossSectionMm2 by remember { mutableDoubleStateOf(initialCircuit?.cableCrossSectionMm2 ?: 2.5) }
    var cableMaterial by remember { mutableStateOf(initialCircuit?.cableMaterial ?: CableMaterial.COPPER) }
    var breakerRatingAmps by remember { mutableIntStateOf(initialCircuit?.ratingAmps ?: 16) }
    var breakerCurve by remember { mutableStateOf(initialCircuit?.breakerCurve ?: BreakerCurve.C) }
    var hasRcd by remember { mutableStateOf(initialCircuit?.hasRcd ?: true) }
    var rcdRatingMa by remember { mutableIntStateOf(initialCircuit?.rcdRatingMa ?: 30) }

    var isAiSuggesting by remember { mutableStateOf(false) }
    var aiNote by remember { mutableStateOf<String?>(null) }

    // Live calculation for preview
    val currentPowerW = powerWattsStr.toDoubleOrNull() ?: 0.0
    val currentPf = powerFactorStr.toDoubleOrNull() ?: 0.9
    val currentLength = cableLengthStr.toDoubleOrNull() ?: 20.0

    val previewCircuit = CircuitBreaker(
        id = initialCircuit?.id ?: "preview",
        circuitNumber = initialCircuit?.circuitNumber ?: nextCircuitNumber,
        name = name,
        loadType = selectedLoadType,
        poles = poles,
        assignedPhase = if (poles >= 3) Phase.THREE_PHASE else assignedPhase,
        ratingAmps = breakerRatingAmps,
        powerWatts = currentPowerW,
        powerFactor = currentPf,
        cableLengthMeters = currentLength,
        cableCrossSectionMm2 = cableCrossSectionMm2,
        cableMaterial = cableMaterial,
        breakerCurve = breakerCurve,
        hasRcd = hasRcd,
        rcdRatingMa = rcdRatingMa
    )

    val liveCalc = ElectricalCalculator.calculateCircuit(previewCircuit, systemVoltage)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.92f)
                .testTag("circuit_dialog"),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (initialCircuit == null) "Add Branch Circuit" else "Edit Circuit #${initialCircuit.circuitNumber}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Automatic load & wire sizing per IEC / NEC standards",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Quick Load Type Presets
                    Text(
                        text = "Circuit Category Preset",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val presets = listOf(
                            LoadType.LIGHTING to "Lighting",
                            LoadType.SOCKETS to "Sockets",
                            LoadType.KITCHEN to "Kitchen",
                            LoadType.HVAC to "HVAC",
                            LoadType.EV_CHARGER to "EV Charger"
                        )
                        presets.forEach { (type, label) ->
                            FilterChip(
                                selected = selectedLoadType == type,
                                onClick = {
                                    selectedLoadType = type
                                    powerWattsStr = type.defaultPowerW.toInt().toString()
                                    powerFactorStr = String.format(java.util.Locale.US, "%.2f", type.defaultPf)
                                    if (type == LoadType.LIGHTING) {
                                        poles = 1
                                        breakerRatingAmps = 10
                                        cableCrossSectionMm2 = 1.5
                                    } else if (type == LoadType.EV_CHARGER) {
                                        poles = if (systemVoltage.isThreePhase) 3 else 1
                                        breakerRatingAmps = 32
                                        cableCrossSectionMm2 = 6.0
                                    }
                                },
                                label = { Text(label, fontSize = 12.sp) }
                            )
                        }
                    }

                    // Circuit Name with Quick AI assistant
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Circuit Name / Description") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("circuit_name_input"),
                        leadingIcon = { Icon(Icons.Default.ElectricBolt, contentDescription = null, tint = ElectricBlue) },
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    if (name.isNotBlank()) {
                                        isAiSuggesting = true
                                        aiNote = null
                                        coroutineScope.launch {
                                            val res = GeminiClient.quickCircuitSuggestion(name)
                                            isAiSuggesting = false
                                            res.onSuccess { text ->
                                                aiNote = text
                                            }.onFailure { err ->
                                                aiNote = "AI recommendation note: ${err.message}"
                                            }
                                        }
                                    }
                                }
                            ) {
                                if (isAiSuggesting) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Default.AutoAwesome, contentDescription = "AI Sizing Suggestion", tint = AmberAccent)
                                }
                            }
                        },
                        singleLine = true
                    )

                    aiNote?.let { note ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.Top) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = AmberAccent, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                        }
                    }

                    // Poles & Phase Allocation
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Poles selector
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Poles", style = MaterialTheme.typography.labelMedium)
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                listOf(1, 2, 3, 4).forEach { p ->
                                    OutlinedButton(
                                        onClick = {
                                            poles = p
                                            if (p >= 3) assignedPhase = Phase.THREE_PHASE
                                        },
                                        colors = if (poles == p) ButtonDefaults.outlinedButtonColors(
                                            containerColor = MaterialTheme.colorScheme.primaryContainer
                                        ) else ButtonDefaults.outlinedButtonColors(),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("${p}P", fontSize = 12.sp, fontWeight = if (poles == p) FontWeight.Bold else FontWeight.Normal)
                                    }
                                }
                            }
                        }

                        // Phase Selector
                        if (systemVoltage.isThreePhase && poles < 3) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Assigned Phase", style = MaterialTheme.typography.labelMedium)
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    listOf(Phase.L1, Phase.L2, Phase.L3).forEach { ph ->
                                        val phColor = when (ph) {
                                            Phase.L1 -> PhaseL1Color
                                            Phase.L2 -> PhaseL2Color
                                            Phase.L3 -> PhaseL3Color
                                            else -> ElectricBlue
                                        }
                                        OutlinedButton(
                                            onClick = { assignedPhase = ph },
                                            colors = if (assignedPhase == ph) ButtonDefaults.outlinedButtonColors(
                                                containerColor = phColor.copy(alpha = 0.2f)
                                            ) else ButtonDefaults.outlinedButtonColors(),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(
                                                text = ph.shortName,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (assignedPhase == ph) phColor else MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Electrical Power & Power Factor
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = powerWattsStr,
                            onValueChange = { powerWattsStr = it },
                            label = { Text("Power (Watts)") },
                            modifier = Modifier
                                .weight(1.2f)
                                .testTag("power_watts_input"),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = powerFactorStr,
                            onValueChange = { powerFactorStr = it },
                            label = { Text("Power Factor (cos φ)") },
                            modifier = Modifier.weight(0.9f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true
                        )
                    }

                    // Cable Length & Cable Cross-section
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = cableLengthStr,
                            onValueChange = { cableLengthStr = it },
                            label = { Text("Length (m)") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true
                        )

                        Column(modifier = Modifier.weight(1.5f)) {
                            Text("Cable Size: ${cableCrossSectionMm2} mm²", style = MaterialTheme.typography.labelMedium)
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                listOf(1.5, 2.5, 4.0, 6.0, 10.0, 16.0).forEach { size ->
                                    val isRec = liveCalc.recommendedCableMm2 == size
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(36.dp)
                                            .background(
                                                color = if (cableCrossSectionMm2 == size) MaterialTheme.colorScheme.primaryContainer else if (isRec) EmeraldSuccess.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant,
                                                shape = RoundedCornerShape(6.dp)
                                            )
                                            .clickable { cableCrossSectionMm2 = size },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = if (size == size.toInt().toDouble()) "${size.toInt()}" else "$size",
                                            fontSize = 11.sp,
                                            fontWeight = if (cableCrossSectionMm2 == size || isRec) FontWeight.Bold else FontWeight.Normal,
                                            color = if (cableCrossSectionMm2 == size) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Breaker Rating (Amps) & Curve
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(modifier = Modifier.weight(1.5f)) {
                            Text("MCB Rating: ${breakerRatingAmps} A", style = MaterialTheme.typography.labelMedium)
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                listOf(10, 16, 20, 25, 32, 40, 50, 63).forEach { rating ->
                                    val isRec = liveCalc.recommendedBreakerAmps == rating
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(36.dp)
                                            .background(
                                                color = if (breakerRatingAmps == rating) MaterialTheme.colorScheme.primaryContainer else if (isRec) EmeraldSuccess.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant,
                                                shape = RoundedCornerShape(6.dp)
                                            )
                                            .clickable { breakerRatingAmps = rating },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "$rating",
                                            fontSize = 11.sp,
                                            fontWeight = if (breakerRatingAmps == rating || isRec) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }
                                }
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text("Curve", style = MaterialTheme.typography.labelMedium)
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                listOf(BreakerCurve.B, BreakerCurve.C, BreakerCurve.D).forEach { curve ->
                                    OutlinedButton(
                                        onClick = { breakerCurve = curve },
                                        colors = if (breakerCurve == curve) ButtonDefaults.outlinedButtonColors(
                                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                                        ) else ButtonDefaults.outlinedButtonColors(),
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(curve.name, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    // RCD Protection Toggle
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("RCD Residual Current Protection", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                Text("Earth fault trip sensitivity: 30 mA (Life Safety)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = hasRcd,
                                onCheckedChange = { hasRcd = it }
                            )
                        }
                    }

                    // LIVE ENGINEERING CALCULATION AUDIT CARD
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (liveCalc.isOverloaded || liveCalc.isVoltageDropWarning)
                                RoseDanger.copy(alpha = 0.12f)
                            else
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                        ),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "LIVE SIZING AUDIT",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = if (liveCalc.isOverloaded) RoseDanger else ElectricBlue
                                )
                                Text(
                                    text = if (liveCalc.isOverloaded) "OVERLOADED" else if (liveCalc.isVoltageDropWarning) "VOLT DROP ALERT" else "COMPLIANT",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (liveCalc.isOverloaded || liveCalc.isVoltageDropWarning) RoseDanger else EmeraldSuccess
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Operating Current:", fontSize = 12.sp)
                                Text("${liveCalc.nominalCurrentAmps} A", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Calculated Voltage Drop:", fontSize = 12.sp)
                                Text("${liveCalc.voltageDropVolts} V (${liveCalc.voltageDropPercent}%)", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = if (liveCalc.isVoltageDropWarning) RoseDanger else Color.Unspecified)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Standard Recommended Breaker:", fontSize = 12.sp)
                                Text("${liveCalc.recommendedBreakerAmps} A", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = EmeraldSuccess)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Standard Recommended Cable:", fontSize = 12.sp)
                                Text("${liveCalc.recommendedCableMm2} mm² Cu", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = EmeraldSuccess)
                            }

                            // Quick button to apply recommended values
                            if (breakerRatingAmps != liveCalc.recommendedBreakerAmps || cableCrossSectionMm2 != liveCalc.recommendedCableMm2) {
                                Spacer(modifier = Modifier.height(8.dp))
                                TextButton(
                                    onClick = {
                                        breakerRatingAmps = liveCalc.recommendedBreakerAmps
                                        cableCrossSectionMm2 = liveCalc.recommendedCableMm2
                                    },
                                    modifier = Modifier.align(Alignment.End),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Apply Recommended Sizing", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }

                // Actions Footer
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = {
                            val finalCircuit = previewCircuit.copy(
                                name = name.ifBlank { "Branch Circuit $nextCircuitNumber" },
                                ratingAmps = breakerRatingAmps,
                                cableCrossSectionMm2 = cableCrossSectionMm2,
                                powerWatts = currentPowerW,
                                powerFactor = currentPf,
                                cableLengthMeters = currentLength
                            )
                            onSave(finalCircuit)
                        },
                        modifier = Modifier.testTag("save_circuit_button")
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (initialCircuit == null) "Add Circuit" else "Save Changes")
                    }
                }
            }
        }
    }
}
