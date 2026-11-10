package com.example.ui.tabs

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.calculation.ElectricalCalculator
import com.example.model.*
import com.example.ui.theme.*
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CircuitScheduleTab(
    panel: PanelDesign,
    summary: PanelCalculationSummary,
    onEditCircuit: (CircuitBreaker) -> Unit,
    onDeleteCircuit: (String) -> Unit,
    onToggleCircuit: (String) -> Unit,
    onAddCircuit: () -> Unit,
    onAutoBalancePhases: () -> Unit = {},
    onDuplicateCircuit: (CircuitBreaker) -> Unit = {},
    onAddPresetCircuit: (LoadType, Double, Int, Int, Phase) -> Unit = { _, _, _, _, _ -> }
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedPhaseFilter by remember { mutableStateOf<Phase?>(null) }
    var selectedStateFilter by remember { mutableStateOf<Boolean?>(null) } // null = all, true = ON, false = OFF
    var selectedSortOption by remember { mutableStateOf(CircuitSortOption.NUMBER_ASC) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showBalancingDetails by remember { mutableStateOf(false) }
    var circuitToDelete by remember { mutableStateOf<CircuitBreaker?>(null) }
    var balanceToastMessage by remember { mutableStateOf<String?>(null) }

    // Filter & sort circuits
    val filteredCircuits = remember(panel.circuits, searchQuery, selectedPhaseFilter, selectedStateFilter, selectedSortOption) {
        var list = panel.circuits.filter { c ->
            val matchesQuery = searchQuery.isBlank() ||
                    c.name.contains(searchQuery, ignoreCase = true) ||
                    c.circuitNumber.toString() == searchQuery ||
                    c.loadType.displayName.contains(searchQuery, ignoreCase = true) ||
                    "${c.ratingAmps}A".contains(searchQuery, ignoreCase = true)
            val matchesPhase = selectedPhaseFilter == null || c.assignedPhase == selectedPhaseFilter
            val matchesState = selectedStateFilter == null || c.isOn == selectedStateFilter
            matchesQuery && matchesPhase && matchesState
        }

        list = when (selectedSortOption) {
            CircuitSortOption.NUMBER_ASC -> list.sortedBy { it.circuitNumber }
            CircuitSortOption.NUMBER_DESC -> list.sortedByDescending { it.circuitNumber }
            CircuitSortOption.POWER_DESC -> list.sortedByDescending { it.powerWatts }
            CircuitSortOption.POWER_ASC -> list.sortedBy { it.powerWatts }
            CircuitSortOption.CURRENT_DESC -> list.sortedByDescending {
                val calc = summary.circuitsSummary[it.id] ?: ElectricalCalculator.calculateCircuit(it, panel.systemVoltage)
                calc.nominalCurrentAmps
            }
            CircuitSortOption.VOLTAGE_DROP_DESC -> list.sortedByDescending {
                val calc = summary.circuitsSummary[it.id] ?: ElectricalCalculator.calculateCircuit(it, panel.systemVoltage)
                calc.voltageDropPercent
            }
        }
        list
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 80.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Real-time Total Load & Main Breaker HUD Card
        item(key = "total_load_summary") {
            RealTimeTotalLoadHud(
                panel = panel,
                summary = summary
            )
        }

        // 2. Real-time 3-Phase Balancing Status Card
        if (panel.systemVoltage.isThreePhase) {
            item(key = "phase_balancing_hud") {
                RealTimePhaseBalancingHud(
                    panel = panel,
                    summary = summary,
                    isExpanded = showBalancingDetails,
                    onToggleExpand = { showBalancingDetails = !showBalancingDetails },
                    onAutoBalance = {
                        onAutoBalancePhases()
                        balanceToastMessage = "Phases successfully auto-balanced!"
                    }
                )
            }
        }

        // Auto-balance Toast feedback
        if (balanceToastMessage != null) {
            item(key = "toast_message") {
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = EmeraldSuccess.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, EmeraldSuccess.copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = EmeraldSuccess, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = balanceToastMessage ?: "",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            IconButton(
                                onClick = { balanceToastMessage = null },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Dismiss", modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }

        // 3. Quick-Add Preset Chips Strip
        item(key = "quick_preset_strip") {
            QuickPresetsStrip(
                isThreePhase = panel.systemVoltage.isThreePhase,
                onAddPreset = { type, w, amps, poles, ph ->
                    onAddPresetCircuit(type, w, amps, poles, ph)
                }
            )
        }

        // 4. Circuit Filters & Search Toolbar
        item(key = "circuits_filter_toolbar") {
            CircuitFilterToolbar(
                searchQuery = searchQuery,
                onSearchChange = { searchQuery = it },
                selectedPhase = selectedPhaseFilter,
                onSelectPhase = { selectedPhaseFilter = it },
                selectedState = selectedStateFilter,
                onSelectState = { selectedStateFilter = it },
                selectedSort = selectedSortOption,
                onSelectSort = { selectedSortOption = it },
                showSortMenu = showSortMenu,
                onToggleSortMenu = { showSortMenu = it },
                isThreePhase = panel.systemVoltage.isThreePhase,
                totalCircuitsCount = panel.circuits.size,
                filteredCircuitsCount = filteredCircuits.size,
                onAddCircuit = onAddCircuit
            )
        }

        // 5. Circuit List Items
        if (filteredCircuits.isEmpty()) {
            item(key = "empty_circuits_state") {
                EmptyCircuitsCard(
                    hasCircuits = panel.circuits.isNotEmpty(),
                    searchQuery = searchQuery,
                    onClearFilter = {
                        searchQuery = ""
                        selectedPhaseFilter = null
                        selectedStateFilter = null
                    },
                    onAddCircuit = onAddCircuit
                )
            }
        } else {
            items(filteredCircuits, key = { it.id }) { circuit ->
                val calculation = summary.circuitsSummary[circuit.id]
                    ?: ElectricalCalculator.calculateCircuit(circuit, panel.systemVoltage)

                CircuitCard(
                    circuit = circuit,
                    calculation = calculation,
                    systemVoltage = panel.systemVoltage,
                    onToggle = { onToggleCircuit(circuit.id) },
                    onEdit = { onEditCircuit(circuit) },
                    onDuplicate = { onDuplicateCircuit(circuit) },
                    onDelete = { circuitToDelete = circuit }
                )
            }
        }
    }

    // Confirmation Delete Dialog
    circuitToDelete?.let { cct ->
        AlertDialog(
            onDismissRequest = { circuitToDelete = null },
            icon = { Icon(Icons.Default.Delete, contentDescription = null, tint = RoseDanger) },
            title = { Text("Delete Circuit #${cct.circuitNumber}?") },
            text = { Text("Are you sure you want to remove '${cct.name}' (${cct.powerWatts.toInt()}W) from the distribution panel?") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteCircuit(cct.id)
                        circuitToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RoseDanger)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { circuitToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

/**
 * Real-time HUD displaying Total Connected Load, Demand Load, and Main Breaker Capacity.
 */
@Composable
private fun RealTimeTotalLoadHud(
    panel: PanelDesign,
    summary: PanelCalculationSummary
) {
    val loadingPercent = summary.mainBreakerLoadingPercent
    val isOverloaded = summary.isPanelOverloaded
    val animatedLoading by animateFloatAsState(
        targetValue = (loadingPercent / 100.0).toFloat().coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 400),
        label = "main_breaker_loading"
    )

    val loadStatusColor = when {
        isOverloaded -> RoseDanger
        loadingPercent >= 80.0 -> AmberAccent
        else -> ElectricBlue
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("real_time_load_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header Row: Title & Voltage Specs
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(ElectricBlue.copy(alpha = 0.12f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.ElectricBolt,
                            contentDescription = null,
                            tint = ElectricBlue,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "TOTAL ELECTRICAL LOAD",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = 0.8.sp
                        )
                        Text(
                            text = panel.name,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Voltage & Main Breaker Rating Tag
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${panel.systemVoltage.phaseToNeutralV.toInt()}V",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = ElectricBlue
                        )
                        if (panel.systemVoltage.isThreePhase) {
                            Text(
                                text = " / ${panel.systemVoltage.phaseToPhaseV.toInt()}V 3P",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Power Metrics Row: Demand kW vs Connected kW vs Apparent kVA
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Demand kW (Primary Active Load)
                Surface(
                    modifier = Modifier.weight(1f),
                    color = ElectricBlue.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "OPERATING DEMAND",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = ElectricBlue
                            )
                            Surface(
                                color = ElectricBlue.copy(alpha = 0.18f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = "df: ${String.format(Locale.US, "%.2f", panel.diversityFactor)}",
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ElectricBlue,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = String.format(Locale.US, "%.1f kW", summary.totalDemandWatts / 1000.0),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = ElectricBlue
                        )
                        Text(
                            text = "${summary.totalConnectedWatts.toInt()} W Connected",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Apparent Power (kVA)
                Surface(
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "APPARENT POWER",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = String.format(Locale.US, "%.1f kVA", summary.totalApparentVa / 1000.0),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        val activeCount = panel.circuits.count { it.isOn }
                        Text(
                            text = "$activeCount / ${panel.circuits.size} Circuits Active",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Main Incomer Breaker Gauge Bar
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "MAIN INCOMER BREAKER: ",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${panel.mainBreakerAmps}A (${panel.mainBreakerPoles}P)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Text(
                        text = "${String.format(Locale.US, "%.1f", summary.maxPhaseAmps)}A / ${panel.mainBreakerAmps}A (${String.format(Locale.US, "%.1f", loadingPercent)}%)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = loadStatusColor
                    )
                }

                // Progress Bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(animatedLoading)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(ElectricBlue, loadStatusColor)
                                )
                            )
                    )
                }

                if (isOverloaded) {
                    Surface(
                        color = RoseDanger.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = RoseDanger, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Panel Incomer Overload: Max phase current exceeds main breaker rating!",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = RoseDanger
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Real-time 3-Phase Balancing Status HUD Widget:
 * Shows live L1, L2, L3 meters, vector neutral current, balance status chip,
 * and 1-tap "⚡ Auto-Balance" action.
 */
@Composable
private fun RealTimePhaseBalancingHud(
    panel: PanelDesign,
    summary: PanelCalculationSummary,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onAutoBalance: () -> Unit
) {
    val unbalance = summary.unbalancePercent
    val (statusLabel, statusColor, statusIcon) = when {
        unbalance <= 10.0 -> Triple("BALANCED", EmeraldSuccess, Icons.Default.CheckCircle)
        unbalance <= 20.0 -> Triple("MODERATE", AmberAccent, Icons.Default.Warning)
        else -> Triple("CRITICAL IMBALANCE", RoseDanger, Icons.Default.Error)
    }

    val maxAmps = maxOf(summary.maxPhaseAmps, 1.0)
    val total3PWatts = maxOf(summary.phaseL1Watts + summary.phaseL2Watts + summary.phaseL3Watts, 1.0)

    val cctCountL1 = panel.circuits.count { it.isOn && (it.assignedPhase == Phase.L1 || it.assignedPhase == Phase.THREE_PHASE) }
    val cctCountL2 = panel.circuits.count { it.isOn && (it.assignedPhase == Phase.L2 || it.assignedPhase == Phase.THREE_PHASE) }
    val cctCountL3 = panel.circuits.count { it.isOn && (it.assignedPhase == Phase.L3 || it.assignedPhase == Phase.THREE_PHASE) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("phase_balancing_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header Row: Status Chip & Auto-Balance Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Balance,
                            contentDescription = null,
                            tint = ElectricBlue,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "3-PHASE BALANCE STATUS",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            letterSpacing = 0.5.sp
                        )
                    }
                    Text(
                        text = "Distribution symmetry & neutral conductor load",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Balance Status Badge
                Surface(
                    color = statusColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, statusColor.copy(alpha = 0.35f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(statusIcon, contentDescription = null, tint = statusColor, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${String.format(Locale.US, "%.1f", unbalance)}% $statusLabel",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = statusColor
                        )
                    }
                }
            }

            // Phase Meters (L1, L2, L3)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PhaseLoadMeterRow(
                    phaseName = "Phase 1 (L1)",
                    shortName = "L1",
                    color = PhaseL1Color,
                    amps = summary.phaseL1Amps,
                    watts = summary.phaseL1Watts,
                    fraction = (summary.phaseL1Amps / maxAmps).toFloat(),
                    percentOfTotal = (summary.phaseL1Watts / total3PWatts * 100.0),
                    circuitCount = cctCountL1
                )

                PhaseLoadMeterRow(
                    phaseName = "Phase 2 (L2)",
                    shortName = "L2",
                    color = PhaseL2Color,
                    amps = summary.phaseL2Amps,
                    watts = summary.phaseL2Watts,
                    fraction = (summary.phaseL2Amps / maxAmps).toFloat(),
                    percentOfTotal = (summary.phaseL2Watts / total3PWatts * 100.0),
                    circuitCount = cctCountL2
                )

                PhaseLoadMeterRow(
                    phaseName = "Phase 3 (L3)",
                    shortName = "L3",
                    color = PhaseL3Color,
                    amps = summary.phaseL3Amps,
                    watts = summary.phaseL3Watts,
                    fraction = (summary.phaseL3Amps / maxAmps).toFloat(),
                    percentOfTotal = (summary.phaseL3Watts / total3PWatts * 100.0),
                    circuitCount = cctCountL3
                )
            }

            // Neutral Current Summary Bar & Auto-Balance Action
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Vector Neutral Current
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Neutral Current (IN): ",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${String.format(Locale.US, "%.1f", summary.neutralCurrentAmps)} A",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (summary.neutralCurrentAmps > summary.maxPhaseAmps * 0.5) AmberAccent else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = if (unbalance <= 10.0) "Optimal: minimal neutral return current" else "Return current caused by 1P phase unbalance",
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // 1-Click Auto-Balance Action Button
                FilledTonalButton(
                    onClick = onAutoBalance,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = ElectricBlue.copy(alpha = 0.18f),
                        contentColor = ElectricBlue
                    ),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.testTag("auto_balance_phases_btn")
                ) {
                    Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Auto-Balance",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun PhaseLoadMeterRow(
    phaseName: String,
    shortName: String,
    color: Color,
    amps: Double,
    watts: Double,
    fraction: Float,
    percentOfTotal: Double,
    circuitCount: Int
) {
    val animatedFraction by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 350),
        label = "phase_bar_$shortName"
    )

    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Phase Tag
                Surface(
                    color = color.copy(alpha = 0.18f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = shortName,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = color,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = String.format(Locale.US, "%.1f kW (%.0f%%)", watts / 1000.0, percentOfTotal),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "• $circuitCount ccts",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = "${String.format(Locale.US, "%.1f", amps)} A",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }

        // Meter Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedFraction)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(3.dp))
                    .background(color)
            )
        }
    }
}

/**
 * Horizontal quick preset strip to add circuits in 1-tap.
 */
@Composable
private fun QuickPresetsStrip(
    isThreePhase: Boolean,
    onAddPreset: (LoadType, Double, Int, Int, Phase) -> Unit
) {
    val scrollState = rememberScrollState()

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "QUICK CIRCUIT PRESETS",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 0.6.sp
            )
            Text(
                text = "Tap to add with 1 click",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PresetChip(
                icon = Icons.Default.Power,
                label = "+ 16A Sockets (2.5kW)",
                color = ElectricBlue,
                onClick = { onAddPreset(LoadType.SOCKETS, 2500.0, 16, 1, Phase.L1) }
            )

            PresetChip(
                icon = Icons.Default.Lightbulb,
                label = "+ 10A Lighting (1.2kW)",
                color = AmberAccent,
                onClick = { onAddPreset(LoadType.LIGHTING, 1200.0, 10, 1, Phase.L2) }
            )

            if (isThreePhase) {
                PresetChip(
                    icon = Icons.Default.EvStation,
                    label = "+ 32A 3P EV (7.4kW)",
                    color = PhaseThreePhaseColor,
                    onClick = { onAddPreset(LoadType.EV_CHARGER, 7400.0, 32, 3, Phase.THREE_PHASE) }
                )
            }

            PresetChip(
                icon = Icons.Default.AcUnit,
                label = "+ 20A AC / HVAC (3.5kW)",
                color = CyanAccent,
                onClick = { onAddPreset(LoadType.HVAC, 3500.0, 20, 1, Phase.L3) }
            )

            PresetChip(
                icon = Icons.Default.Kitchen,
                label = "+ 25A Kitchen (4.0kW)",
                color = EmeraldSuccess,
                onClick = { onAddPreset(LoadType.KITCHEN, 4000.0, 25, 1, Phase.L1) }
            )
        }
    }
}

@Composable
private fun PresetChip(
    icon: ImageVector,
    label: String,
    color: Color,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = color.copy(alpha = 0.1f),
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/**
 * Filter and search toolbar for the circuit list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CircuitFilterToolbar(
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    selectedPhase: Phase?,
    onSelectPhase: (Phase?) -> Unit,
    selectedState: Boolean?,
    onSelectState: (Boolean?) -> Unit,
    selectedSort: CircuitSortOption,
    onSelectSort: (CircuitSortOption) -> Unit,
    showSortMenu: Boolean,
    onToggleSortMenu: (Boolean) -> Unit,
    isThreePhase: Boolean,
    totalCircuitsCount: Int,
    filteredCircuitsCount: Int,
    onAddCircuit: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Section Title & Counter
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "ELECTRICAL CIRCUITS",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    letterSpacing = 0.6.sp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = if (filteredCircuitsCount == totalCircuitsCount) "$totalCircuitsCount circuits" else "$filteredCircuitsCount of $totalCircuitsCount",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            // Add Circuit CTA
            Button(
                onClick = onAddCircuit,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier.testTag("add_circuit_button")
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Add Circuit", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        // Search Bar and Sort Menu
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                placeholder = { Text("Search by name, #, or rating...", fontSize = 12.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchChange("") }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                        }
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .testTag("search_circuits_input"),
                singleLine = true,
                shape = RoundedCornerShape(10.dp)
            )

            // Sort Dropdown Button
            Box {
                OutlinedIconButton(
                    onClick = { onToggleSortMenu(true) },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort circuits")
                }

                DropdownMenu(
                    expanded = showSortMenu,
                    onDismissRequest = { onToggleSortMenu(false) }
                ) {
                    CircuitSortOption.entries.forEach { opt ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    opt.label,
                                    fontWeight = if (selectedSort == opt) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selectedSort == opt) ElectricBlue else MaterialTheme.colorScheme.onSurface
                                )
                            },
                            trailingIcon = {
                                if (selectedSort == opt) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = ElectricBlue, modifier = Modifier.size(16.dp))
                                }
                            },
                            onClick = {
                                onSelectSort(opt)
                                onToggleSortMenu(false)
                            }
                        )
                    }
                }
            }
        }

        // Phase and State Filter Chips Row
        val chipScroll = rememberScrollState()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(chipScroll),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // All phases
            FilterChip(
                selected = selectedPhase == null,
                onClick = { onSelectPhase(null) },
                label = { Text("All Phases", fontSize = 11.sp) }
            )

            if (isThreePhase) {
                listOf(
                    Phase.L1 to PhaseL1Color,
                    Phase.L2 to PhaseL2Color,
                    Phase.L3 to PhaseL3Color,
                    Phase.THREE_PHASE to PhaseThreePhaseColor
                ).forEach { (ph, col) ->
                    FilterChip(
                        selected = selectedPhase == ph,
                        onClick = { onSelectPhase(if (selectedPhase == ph) null else ph) },
                        label = { Text(ph.shortName, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                        leadingIcon = {
                            Box(modifier = Modifier.size(8.dp).background(col, CircleShape))
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.width(4.dp))
            VerticalDivider(modifier = Modifier.height(20.dp))
            Spacer(modifier = Modifier.width(4.dp))

            // State filters
            FilterChip(
                selected = selectedState == true,
                onClick = { onSelectState(if (selectedState == true) null else true) },
                label = { Text("⚡ ON Only", fontSize = 11.sp) }
            )
            FilterChip(
                selected = selectedState == false,
                onClick = { onSelectState(if (selectedState == false) null else false) },
                label = { Text("⚪ OFF Only", fontSize = 11.sp) }
            )
        }
    }
}

/**
 * Individual High-Fidelity Circuit Card displaying real-time electrical metrics,
 * phase assignment, interactive ON/OFF switch, cable sizing, voltage drop, and breaker rating.
 */
@Composable
private fun CircuitCard(
    circuit: CircuitBreaker,
    calculation: CircuitCalculation,
    systemVoltage: SystemVoltage,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit
) {
    val phaseColor = when (circuit.assignedPhase) {
        Phase.L1 -> PhaseL1Color
        Phase.L2 -> PhaseL2Color
        Phase.L3 -> PhaseL3Color
        Phase.THREE_PHASE -> PhaseThreePhaseColor
    }

    val isOverloaded = calculation.isOverloaded
    val isVoltDropWarning = calculation.isVoltageDropWarning

    val borderStroke = when {
        isOverloaded -> androidx.compose.foundation.BorderStroke(1.5.dp, RoseDanger)
        isVoltDropWarning -> androidx.compose.foundation.BorderStroke(1.dp, AmberAccent)
        !circuit.isOn -> androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        else -> androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEdit() }
            .testTag("circuit_item_${circuit.id}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (circuit.isOn) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        ),
        border = borderStroke,
        elevation = CardDefaults.cardElevation(if (circuit.isOn) 1.5.dp else 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Top Row: Circuit # + Phase Chip + Icon + Name + Real-time Power Toggle Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    // Circuit Number & Phase badge
                    Surface(
                        color = phaseColor.copy(alpha = 0.18f),
                        shape = RoundedCornerShape(6.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, phaseColor.copy(alpha = 0.35f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "C${circuit.circuitNumber}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = phaseColor
                            )
                            Text(
                                text = " • ${circuit.assignedPhase.shortName}",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = phaseColor
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Load Type Icon
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = getLoadTypeIcon(circuit.loadType),
                            contentDescription = null,
                            tint = if (circuit.isOn) ElectricBlue else Color.Gray,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Circuit Name and Load Type label
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = circuit.name,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (circuit.isOn) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = circuit.loadType.displayName,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Real-time ON/OFF Switch
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = circuit.isOn,
                        onCheckedChange = { onToggle() },
                        modifier = Modifier.testTag("circuit_toggle_${circuit.id}")
                    )
                }
            }

            // Key Electrical Metrics Grid
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(10.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Power
                    Column(horizontalAlignment = Alignment.Start) {
                        Text("POWER", fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = if (circuit.powerWatts >= 1000.0) String.format(Locale.US, "%.2f kW", circuit.powerWatts / 1000.0) else "${circuit.powerWatts.toInt()} W",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Nominal Current
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("CURRENT (In)", fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = "${calculation.nominalCurrentAmps} A",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (isOverloaded) RoseDanger else ElectricBlue
                        )
                    }

                    // Breaker Protection
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("MCB / RCD", fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${circuit.breakerCurve}${circuit.ratingAmps}A",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isOverloaded) RoseDanger else MaterialTheme.colorScheme.onSurface
                            )
                            if (circuit.hasRcd) {
                                Spacer(modifier = Modifier.width(3.dp))
                                Surface(
                                    color = EmeraldSuccess.copy(alpha = 0.18f),
                                    shape = RoundedCornerShape(3.dp)
                                ) {
                                    Text(
                                        text = "${circuit.rcdRatingMa}mA",
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = EmeraldSuccess,
                                        modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Voltage Drop
                    Column(horizontalAlignment = Alignment.End) {
                        Text("VOLT DROP (ΔV)", fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = "${calculation.voltageDropPercent}% (${String.format(Locale.US, "%.1f", calculation.voltageDropVolts)}V)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isVoltDropWarning) RoseDanger else EmeraldSuccess
                        )
                    }
                }
            }

            // Cable Specification & Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Cable spec tag
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Cable, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(13.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${circuit.cableCrossSectionMm2} mm² ${circuit.cableMaterial.label.take(2)} • ${circuit.cableLengthMeters.toInt()}m",
                        fontSize = 10.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Card actions: Edit, Duplicate, Delete
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit Circuit", tint = ElectricBlue, modifier = Modifier.size(16.dp))
                    }

                    IconButton(
                        onClick = onDuplicate,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Duplicate Circuit", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                    }

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete Circuit", tint = RoseDanger.copy(alpha = 0.8f), modifier = Modifier.size(16.dp))
                    }
                }
            }

            // Overload or Voltage Drop Warnings
            if (isOverloaded) {
                Surface(
                    color = RoseDanger.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = RoseDanger, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Overloaded: Current ${calculation.nominalCurrentAmps}A exceeds ${circuit.ratingAmps}A MCB rating!",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = RoseDanger
                        )
                    }
                }
            } else if (isVoltDropWarning) {
                Surface(
                    color = AmberAccent.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = AmberAccent, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Excessive Voltage Drop: ${calculation.voltageDropPercent}% exceeds recommended limits. Consider larger conductor.",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = AmberAccent
                        )
                    }
                }
            }
        }
    }
}

/**
 * Empty state card shown when no circuits match the search/filter or when panel has no circuits.
 */
@Composable
private fun EmptyCircuitsCard(
    hasCircuits: Boolean,
    searchQuery: String,
    onClearFilter: () -> Unit,
    onAddCircuit: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .background(ElectricBlue.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (hasCircuits) Icons.Default.FilterListOff else Icons.Default.ElectricBolt,
                    contentDescription = null,
                    tint = ElectricBlue,
                    modifier = Modifier.size(28.dp)
                )
            }

            Text(
                text = if (hasCircuits) "No Circuits Match Filter" else "No Electrical Circuits Added",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = if (hasCircuits) "Try adjusting your search query ('$searchQuery') or phase filter." else "Begin by adding branch circuits or use the quick presets above to calculate live loads.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            if (hasCircuits) {
                OutlinedButton(onClick = onClearFilter) {
                    Text("Clear All Filters")
                }
            } else {
                Button(
                    onClick = onAddCircuit,
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add First Circuit")
                }
            }
        }
    }
}

private fun getLoadTypeIcon(loadType: LoadType): ImageVector = when (loadType) {
    LoadType.LIGHTING -> Icons.Default.Lightbulb
    LoadType.SOCKETS -> Icons.Default.Power
    LoadType.KITCHEN -> Icons.Default.Kitchen
    LoadType.HVAC -> Icons.Default.AcUnit
    LoadType.WATER_HEATER -> Icons.Default.Waves
    LoadType.EV_CHARGER -> Icons.Default.EvStation
    LoadType.SOLAR -> Icons.Default.WbSunny
    LoadType.MOTOR_PUMP -> Icons.Default.PrecisionManufacturing
    LoadType.SUB_FEEDER -> Icons.Default.ElectricMeter
    LoadType.SPARE -> Icons.Default.Shield
}

enum class CircuitSortOption(val label: String) {
    NUMBER_ASC("Circuit # (1 → 99)"),
    NUMBER_DESC("Circuit # (99 → 1)"),
    POWER_DESC("Power (High → Low)"),
    POWER_ASC("Power (Low → High)"),
    CURRENT_DESC("Current In (High → Low)"),
    VOLTAGE_DROP_DESC("Voltage Drop % (High → Low)")
}
