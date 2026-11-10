package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.calculation.ElectricalCalculator
import com.example.data.IPanelRepository
import com.example.ui.viewmodel.LoadCalculatorViewModel
import com.example.ui.viewmodel.PanelEditorViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.model.*
import com.example.ui.components.PanelCraftTopBar
import com.example.ui.dialogs.AddEditCircuitDialog
import com.example.ui.dialogs.ExportDialog
import com.example.ui.dialogs.ImportDialog
import com.example.ui.tabs.*
import com.example.ui.theme.*
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PanelEditorScreen(
    panelId: String,
    repository: IPanelRepository,
    viewModel: PanelEditorViewModel = viewModel(
        key = panelId,
        factory = PanelEditorViewModel.Factory(panelId, repository)
    ),
    loadCalculatorViewModel: LoadCalculatorViewModel = viewModel(
        key = "load_calc_$panelId",
        factory = LoadCalculatorViewModel.Factory(repository, panelId)
    ),
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val panel = uiState.panel

    if (panel == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Panel not found.")
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = onNavigateBack) {
                    Text("Go Back")
                }
            }
        }
        return
    }

    val summary: PanelCalculationSummary = uiState.calculationSummary ?: remember(panel) { ElectricalCalculator.calculatePanel(panel) }

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var circuitToEdit by remember { mutableStateOf<CircuitBreaker?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            PanelCraftTopBar(
                title = panel.name,
                subtitle = "${panel.tag} • ${panel.systemVoltage.label} • ${panel.mainBreakerAmps}A Main",
                showBackButton = true,
                onBackClick = onNavigateBack,
                onExportClick = { showExportDialog = true },
                onImportClick = { showImportDialog = true }
            )
        },
        floatingActionButton = {
            if (selectedTabIndex in listOf(0, 1)) {
                FloatingActionButton(
                    onClick = { showAddDialog = true },
                    containerColor = ElectricBlue,
                    contentColor = Color.White,
                    modifier = Modifier.testTag("add_circuit_fab")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Circuit")
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Live Engineering Ticker Strip
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                tonalElevation = 1.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Connected Load & Demand
                    Column {
                        Text("LOAD TOTALS", fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = String.format(Locale.US, "%.1f kW (df: %.2f)", summary.totalDemandWatts / 1000.0, panel.diversityFactor),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = ElectricBlue
                        )
                    }

                    // Phase currents
                    if (panel.systemVoltage.isThreePhase) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            TickerPhaseTag("L1", "${summary.phaseL1Amps}A", PhaseL1Color)
                            TickerPhaseTag("L2", "${summary.phaseL2Amps}A", PhaseL2Color)
                            TickerPhaseTag("L3", "${summary.phaseL3Amps}A", PhaseL3Color)
                        }
                    }

                    // Balance status badge
                    val balanceColor = if (summary.unbalancePercent <= 10.0) EmeraldSuccess else if (summary.unbalancePercent <= 20.0) AmberAccent else RoseDanger
                    Surface(
                        color = balanceColor.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "${summary.unbalancePercent}% Imbalance",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = balanceColor
                        )
                    }
                }
            }

            // Editor Navigation Tabs
            val tabs = listOf(
                "Circuits & Load" to Icons.Default.ElectricBolt,
                "DIN Board" to Icons.Default.ViewWeek,
                "Balancing" to Icons.Default.Balance,
                "NEC Standards" to Icons.Default.Calculate,
                "SLD" to Icons.Default.Schema,
                "AI Copilot" to Icons.Default.AutoAwesome
            )

            ScrollableTabRow(
                selectedTabIndex = selectedTabIndex,
                edgePadding = 12.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                tabs.forEachIndexed { index, (label, icon) ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        icon = { Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        text = { Text(label, fontSize = 11.sp, fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Normal) }
                    )
                }
            }

            // Tab Content
            Box(modifier = Modifier.weight(1f)) {
                when (selectedTabIndex) {
                    0 -> CircuitScheduleTab(
                        panel = panel,
                        summary = summary,
                        onEditCircuit = { circuitToEdit = it },
                        onDeleteCircuit = { circuitId ->
                            viewModel.deleteCircuit(circuitId)
                        },
                        onToggleCircuit = { circuitId ->
                            viewModel.toggleCircuit(circuitId)
                        },
                        onAddCircuit = { showAddDialog = true },
                        onAutoBalancePhases = {
                            viewModel.balancePhases()
                        },
                        onDuplicateCircuit = { circuit ->
                            viewModel.duplicateCircuit(circuit.id)
                        },
                        onAddPresetCircuit = { type, w, amps, poles, ph ->
                            val nextNum = (panel.circuits.maxOfOrNull { it.circuitNumber } ?: 0) + 1
                            val newCct = CircuitBreaker(
                                id = java.util.UUID.randomUUID().toString(),
                                circuitNumber = nextNum,
                                name = "${type.displayName} $nextNum",
                                loadType = type,
                                poles = poles,
                                assignedPhase = ph,
                                ratingAmps = amps,
                                powerWatts = w,
                                powerFactor = type.defaultPf,
                                cableCrossSectionMm2 = if (amps <= 10) 1.5 else if (amps <= 16) 2.5 else if (amps <= 25) 4.0 else if (amps <= 32) 6.0 else 10.0,
                                cableLengthMeters = 20.0,
                                breakerCurve = if (type == LoadType.MOTOR_PUMP || type == LoadType.HVAC) BreakerCurve.D else BreakerCurve.C,
                                hasRcd = true,
                                rcdRatingMa = 30,
                                isOn = true
                            )
                            viewModel.addCircuit(newCct)
                        }
                    )
                    1 -> DinRailTab(
                        panel = panel,
                        summary = summary,
                        onEditCircuit = { circuitToEdit = it },
                        onToggleCircuit = { circuitId ->
                            viewModel.toggleCircuit(circuitId)
                        },
                        onAddCircuit = { showAddDialog = true }
                    )
                    2 -> PhaseBalancingTab(
                        panel = panel,
                        summary = summary,
                        onApplyBalancedPanel = { newPanel ->
                            viewModel.savePanel(newPanel)
                        }
                    )
                    3 -> NecLoadCalculatorTab(
                        viewModel = loadCalculatorViewModel
                    )
                    4 -> SingleLineDiagramTab(
                        panel = panel,
                        summary = summary
                    )
                    5 -> AiCopilotTab(
                        panel = panel,
                        summary = summary
                    )
                }
            }
        }
    }

    // Add or Edit Circuit Dialog
    if (showAddDialog || circuitToEdit != null) {
        val nextCctNum = (panel.circuits.maxOfOrNull { it.circuitNumber } ?: 0) + 1
        AddEditCircuitDialog(
            initialCircuit = circuitToEdit,
            systemVoltage = panel.systemVoltage,
            nextCircuitNumber = nextCctNum,
            onDismiss = {
                showAddDialog = false
                circuitToEdit = null
            },
            onSave = { savedCircuit ->
                if (circuitToEdit != null) {
                    viewModel.updateCircuit(savedCircuit)
                } else {
                    viewModel.addCircuit(savedCircuit)
                }
                showAddDialog = false
                circuitToEdit = null
            }
        )
    }

    // Export Dialog
    if (showExportDialog) {
        ExportDialog(
            panel = panel,
            onDismiss = { showExportDialog = false }
        )
    }

    // Import Dialog
    if (showImportDialog) {
        ImportDialog(
            currentPanel = panel,
            onDismiss = { showImportDialog = false },
            onImportComplete = { updatedPanel ->
                viewModel.savePanel(updatedPanel)
                showImportDialog = false
            }
        )
    }
}

@Composable
private fun TickerPhaseTag(label: String, amps: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(6.dp).background(color, RoundedCornerShape(3.dp)))
        Spacer(modifier = Modifier.width(3.dp))
        Text(
            text = "$label: $amps",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}
