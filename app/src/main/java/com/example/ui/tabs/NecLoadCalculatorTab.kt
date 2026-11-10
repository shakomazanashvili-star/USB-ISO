package com.example.ui.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Rule
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.calculation.NecCircuitCalculation
import com.example.calculation.PhaseImbalanceStatus
import com.example.calculation.VoltageDropStatus
import com.example.model.ElectricalLoad
import com.example.model.LoadType
import com.example.model.Phase
import com.example.ui.theme.*
import com.example.ui.viewmodel.LoadCalculatorViewModel
import java.util.Locale

@Composable
fun NecLoadCalculatorTab(
    viewModel: LoadCalculatorViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    val summary = uiState.necSummary
    var showAddLoadDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("nec_load_calculator_tab"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Hero Header: NEC Standards Compliance Banner
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(ElectricBlue.copy(alpha = 0.15f), RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Verified, contentDescription = null, tint = ElectricBlue, modifier = Modifier.size(20.dp))
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("NEC Load Calculator", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("NFPA 70 • Articles 210, 215, 220 & 240", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        // Auto-balance phases button
                        if (uiState.systemVoltage.isThreePhase) {
                            FilledTonalButton(
                                onClick = { viewModel.autoBalancePhases() },
                                modifier = Modifier.testTag("nec_auto_balance_button"),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.Balance, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Balance Phases", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Diversity factor & System voltage chips
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Text(
                                text = "System: ${uiState.systemVoltage.label}",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Text(
                                text = "Demand Factor: ${(uiState.diversityFactor * 100).toInt()}%",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }

        // 3 Key Pillar KPI Cards (Amperage, Voltage Drop, Phase Imbalance)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Total Amperage Card
                KpiMetricCard(
                    title = "TOTAL AMPERAGE",
                    primaryValue = String.format(Locale.US, "%.1f A", summary.totalAmperageAmps),
                    subtext = "NEC Design (125% Cont.)",
                    badgeText = "${summary.mainBreakerLoadingPercent}% Main",
                    badgeColor = if (summary.isMainBreakerOverloaded) RoseDanger else ElectricBlue,
                    icon = Icons.Default.ElectricBolt,
                    modifier = Modifier.weight(1f)
                )

                // Voltage Drop Card
                val vdColor = if (summary.isOverallVoltageDropCompliant) EmeraldSuccess else AmberAccent
                KpiMetricCard(
                    title = "MAX VOLTAGE DROP",
                    primaryValue = String.format(Locale.US, "%.2f%%", summary.maxVoltageDropPercent),
                    subtext = if (summary.isOverallVoltageDropCompliant) "<= 3.0% NEC 210.19" else "Exceeds 3.0%",
                    badgeText = if (summary.isOverallVoltageDropCompliant) "COMPLIANT" else "WARNING",
                    badgeColor = vdColor,
                    icon = Icons.AutoMirrored.Filled.TrendingDown,
                    modifier = Modifier.weight(1f)
                )

                // Phase Imbalance Card
                val imbColor = Color(summary.phaseImbalanceStatus.hexColor)
                KpiMetricCard(
                    title = "PHASE IMBALANCE",
                    primaryValue = String.format(Locale.US, "%.1f%%", summary.phaseImbalancePercent),
                    subtext = "NEMA / IEEE Std",
                    badgeText = summary.phaseImbalanceStatus.label.split(" ").first(),
                    badgeColor = imbColor,
                    icon = Icons.Default.Balance,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Phase Distribution & Neutral Current Meter
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Phase Loading & Neutral Vector (NEC 220.61)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(12.dp))

                    if (uiState.systemVoltage.isThreePhase) {
                        PhaseMeterBar("Phase A (L1)", summary.phaseL1Amps, summary.maxPhaseAmps, PhaseL1Color)
                        Spacer(modifier = Modifier.height(8.dp))
                        PhaseMeterBar("Phase B (L2)", summary.phaseL2Amps, summary.maxPhaseAmps, PhaseL2Color)
                        Spacer(modifier = Modifier.height(8.dp))
                        PhaseMeterBar("Phase C (L3)", summary.phaseL3Amps, summary.maxPhaseAmps, PhaseL3Color)
                        Spacer(modifier = Modifier.height(10.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(10.dp))
                        PhaseMeterBar("Neutral Return", summary.neutralCurrentAmps, summary.maxPhaseAmps, PhaseNeutralColor, isNeutral = true)
                    } else {
                        PhaseMeterBar("Line Current", summary.phaseL1Amps, summary.maxPhaseAmps, ElectricBlue)
                        Spacer(modifier = Modifier.height(8.dp))
                        PhaseMeterBar("Neutral Current", summary.neutralCurrentAmps, summary.maxPhaseAmps, PhaseNeutralColor, isNeutral = true)
                    }
                }
            }
        }

        // NEC Diagnostic Notes & Code Citations
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.AutoMirrored.Filled.Rule, contentDescription = null, tint = ElectricBlue, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("NEC Code Compliance Citations", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    summary.necComplianceNotes.forEach { note ->
                        Row(modifier = Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.Top) {
                            Text("•", color = ElectricBlue, fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 6.dp))
                            Text(note, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        // Connected Electrical Loads Header with Add Load button
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Tracked Electrical Loads (${uiState.electricalLoads.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${summary.totalActiveCircuitsCount} Active • ${String.format(Locale.US, "%.1f kW Connected", summary.totalConnectedWatts / 1000.0)}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Button(
                    onClick = { showAddLoadDialog = true },
                    modifier = Modifier.testTag("add_electrical_load_button"),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add Load", fontSize = 12.sp)
                }
            }
        }

        // Connected Electrical Loads Items
        items(uiState.electricalLoads, key = { it.id }) { load ->
            val calc = summary.circuitCalculations.find { it.circuitId == load.id }
            ElectricalLoadItemCard(
                load = load,
                calc = calc,
                onToggle = { viewModel.toggleLoad(load.id) },
                onDelete = { viewModel.removeLoad(load.id) }
            )
        }
    }

    // Add Electrical Load Dialog
    if (showAddLoadDialog) {
        AddElectricalLoadDialog(
            systemVoltage = uiState.systemVoltage,
            onDismiss = { showAddLoadDialog = false },
            onAddLoad = { name, phase, voltage, amperage, isContinuous, loadType ->
                viewModel.addLoad(
                    name = name,
                    phase = phase,
                    voltage = voltage,
                    amperage = amperage,
                    isContinuous = isContinuous,
                    loadType = loadType
                )
                showAddLoadDialog = false
            }
        )
    }
}

@Composable
private fun KpiMetricCard(
    title: String,
    primaryValue: String,
    subtext: String,
    badgeText: String,
    badgeColor: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                Surface(
                    color = badgeColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = badgeText,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = badgeColor
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(title, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(primaryValue, fontSize = 16.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            Text(subtext, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

@Composable
private fun PhaseMeterBar(
    label: String,
    amps: Double,
    maxAmps: Double,
    color: Color,
    isNeutral: Boolean = false
) {
    val progress = if (maxAmps > 0) (amps / maxAmps).coerceIn(0.0, 1.0).toFloat() else 0f

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(color)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
            Text(
                text = String.format(Locale.US, "%.1f A", amps),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}

@Composable
private fun ElectricalLoadItemCard(
    load: ElectricalLoad,
    calc: NecCircuitCalculation?,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (load.isOn) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (load.isOn) MaterialTheme.colorScheme.outlineVariant else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Load Info
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = load.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        color = Color(load.phase.hexColor).copy(alpha = 0.15f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = load.phase.shortName,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(load.phase.hexColor)
                        )
                    }

                    if (load.isContinuous) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            color = AmberAccent.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "125% Cont",
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = AmberAccent
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Core Properties Display: Phase, Voltage, and Amperage
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${String.format(Locale.US, "%.1f", load.amperage)} A @ ${load.voltage.toInt()} V",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = ElectricBlue
                    )
                    Text(
                        text = "• ${load.powerWatts.toInt()} W",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "• ${load.loadType.displayName}",
                        fontSize = 10.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                calc?.let { c ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val vdColor = when (c.voltageDropStatus) {
                            VoltageDropStatus.COMPLIANT -> EmeraldSuccess
                            VoltageDropStatus.WARNING -> AmberAccent
                            VoltageDropStatus.CRITICAL -> RoseDanger
                        }
                        Text(
                            text = "VD: ${c.voltageDropPercent}% (${c.voltageDropVolts}V)",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = vdColor
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "• Min Breaker: ${c.minNecBreakerAmps}A",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Controls: Delete and Power Toggle
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "Delete Load", tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
                }
                Spacer(modifier = Modifier.width(4.dp))
                Switch(
                    checked = load.isOn,
                    onCheckedChange = { onToggle() },
                    modifier = Modifier.testTag("load_toggle_${load.id}")
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddElectricalLoadDialog(
    systemVoltage: com.example.model.SystemVoltage,
    onDismiss: () -> Unit,
    onAddLoad: (name: String, phase: Phase, voltage: Double, amperage: Double, isContinuous: Boolean, loadType: LoadType) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var selectedPhase by remember { mutableStateOf(Phase.L1) }
    var voltageInput by remember { mutableStateOf(systemVoltage.phaseToNeutralV.toString()) }
    var amperageInput by remember { mutableStateOf("10.0") }
    var isContinuous by remember { mutableStateOf(false) }
    var selectedLoadType by remember { mutableStateOf(LoadType.SOCKETS) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Electrical Load", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Name
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Load Name") },
                    placeholder = { Text("e.g. Compressor, Lighting") },
                    modifier = Modifier.fillMaxWidth()
                )

                // Phase Selection
                Text("Assigned Phase", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val phases = if (systemVoltage.isThreePhase) {
                        listOf(Phase.L1, Phase.L2, Phase.L3, Phase.THREE_PHASE)
                    } else {
                        listOf(Phase.L1)
                    }
                    phases.forEach { p ->
                        FilterChip(
                            selected = selectedPhase == p,
                            onClick = {
                                selectedPhase = p
                                voltageInput = if (p == Phase.THREE_PHASE && systemVoltage.isThreePhase) {
                                    systemVoltage.phaseToPhaseV.toString()
                                } else {
                                    systemVoltage.phaseToNeutralV.toString()
                                }
                            },
                            label = { Text(p.shortName, fontSize = 11.sp) }
                        )
                    }
                }

                // Voltage & Amperage
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = voltageInput,
                        onValueChange = { voltageInput = it },
                        label = { Text("Voltage (V)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )

                    OutlinedTextField(
                        value = amperageInput,
                        onValueChange = { amperageInput = it },
                        label = { Text("Amperage (A)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                }

                // Continuous Load Switch (NEC 125%)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Continuous Duty Load (3+ hrs)", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        Text("Applies 125% sizing per NEC 210.19(A)", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = isContinuous, onCheckedChange = { isContinuous = it })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val v = voltageInput.toDoubleOrNull() ?: systemVoltage.phaseToNeutralV
                    val a = amperageInput.toDoubleOrNull() ?: 10.0
                    onAddLoad(name, selectedPhase, v, a, isContinuous, selectedLoadType)
                }
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
