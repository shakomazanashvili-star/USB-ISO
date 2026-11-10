package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
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
import com.example.ui.viewmodel.PanelListViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.model.PanelDesign
import com.example.model.SystemVoltage
import com.example.ui.components.PanelCraftTopBar
import com.example.ui.dialogs.ExportDialog
import com.example.ui.dialogs.ImportDialog
import com.example.ui.theme.*
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PanelListScreen(
    repository: IPanelRepository,
    viewModel: PanelListViewModel = viewModel(factory = PanelListViewModel.Factory(repository)),
    onSelectPanel: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val panels = uiState.filteredPanels
    val isSyncing = uiState.isSyncing

    var showCreateDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var panelToExport by remember { mutableStateOf<PanelDesign?>(null) }

    Scaffold(
        topBar = {
            PanelCraftTopBar(
                title = "PanelCraft",
                subtitle = "Electrical Distribution Boards & Sizing",
                onImportClick = { showImportDialog = true },
                isSyncing = isSyncing
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateDialog = true },
                containerColor = ElectricBlue,
                contentColor = Color.White,
                modifier = Modifier.testTag("add_panel_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Create Electrical Panel")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // High-Tech Dashboard Header Banner
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Bolt, contentDescription = null, tint = AmberAccent)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Switchboard Engineering Hub",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Phase Balancing, DIN rail layout & multi-format export",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 2.dp
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "${panels.size}",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = ElectricBlue
                            )
                            Text("Panels", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            // Quick Actions: New Panel & Import
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = { showImportDialog = true },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.FileUpload, contentDescription = null, tint = ElectricBlue, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Import Schedule", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }

                Button(
                    onClick = { showCreateDialog = true },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("New Board", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            // List of Panels
            if (panels.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.ElectricMeter,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No Distribution Panels Found",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Tap the + button to design your first electrical switchboard.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(panels, key = { it.id }) { panel ->
                        PanelCard(
                            panel = panel,
                            onClick = { onSelectPanel(panel.id) },
                            onExport = { panelToExport = panel },
                            onDelete = { viewModel.deletePanel(panel.id) }
                        )
                    }
                }
            }
        }
    }

    // Create New Panel Dialog
    if (showCreateDialog) {
        CreatePanelDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { newPanel ->
                viewModel.savePanel(newPanel)
                showCreateDialog = false
                onSelectPanel(newPanel.id)
            }
        )
    }

    // Export Dialog
    panelToExport?.let { p ->
        ExportDialog(
            panel = p,
            onDismiss = { panelToExport = null }
        )
    }

    // Import Dialog
    if (showImportDialog) {
        ImportDialog(
            currentPanel = null,
            onDismiss = { showImportDialog = false },
            onImportComplete = { newPanel ->
                viewModel.savePanel(newPanel)
                showImportDialog = false
                onSelectPanel(newPanel.id)
            }
        )
    }
}

@Composable
private fun PanelCard(
    panel: PanelDesign,
    onClick: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit
) {
    val summary = remember(panel) { ElectricalCalculator.calculatePanel(panel) }
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("panel_card_${panel.tag}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Card Top Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Surface(
                        color = ElectricBlue.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = panel.tag,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = ElectricBlue
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = panel.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        Text(
                            text = panel.location,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }

                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Options")
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Export (Excel/PDF/Visio)") },
                            leadingIcon = { Icon(Icons.Default.FileDownload, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                onExport()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete Panel", color = RoseDanger) },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = RoseDanger) },
                            onClick = {
                                showMenu = false
                                onDelete()
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Metrics Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricChip(
                    label = "CONNECTED",
                    value = String.format(Locale.US, "%.1f kW", summary.totalConnectedWatts / 1000.0),
                    modifier = Modifier.weight(1f)
                )
                MetricChip(
                    label = "MAIN INCOMER",
                    value = "${panel.mainBreakerAmps}A",
                    modifier = Modifier.weight(0.9f)
                )
                MetricChip(
                    label = "CIRCUITS",
                    value = "${panel.circuits.size} (${panel.usedModules}m)",
                    modifier = Modifier.weight(0.9f)
                )
                val unbalanceColor = if (summary.unbalancePercent <= 10.0) EmeraldSuccess else if (summary.unbalancePercent <= 20.0) AmberAccent else RoseDanger
                MetricChip(
                    label = "BALANCE",
                    value = "${summary.unbalancePercent}%",
                    valueColor = unbalanceColor,
                    modifier = Modifier.weight(0.8f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Quick Phase Mini-Bars
            if (panel.systemVoltage.isThreePhase) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Phases:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    PhaseBadge("L1: ${summary.phaseL1Amps}A", PhaseL1Color)
                    PhaseBadge("L2: ${summary.phaseL2Amps}A", PhaseL2Color)
                    PhaseBadge("L3: ${summary.phaseL3Amps}A", PhaseL3Color)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Quick Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                OutlinedButton(
                    onClick = onExport,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Export", fontSize = 12.sp)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = onClick,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)
                ) {
                    Text("Open Panel", fontSize = 12.sp)
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}

@Composable
private fun MetricChip(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = valueColor, maxLines = 1)
        }
    }
}

@Composable
private fun PhaseBadge(text: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(4.dp)
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreatePanelDialog(
    onDismiss: () -> Unit,
    onCreate: (PanelDesign) -> Unit
) {
    var name by remember { mutableStateOf("New Distribution Board") }
    var tag by remember { mutableStateOf("DB-01") }
    var location by remember { mutableStateOf("Main Electrical Room") }
    var selectedVoltage by remember { mutableStateOf(SystemVoltage.THREE_PHASE_400_230) }
    var mainBreakerAmps by remember { mutableIntStateOf(63) }
    var rows by remember { mutableIntStateOf(3) }
    var modulesPerRow by remember { mutableIntStateOf(18) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Create Electrical Panel", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Panel Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = tag,
                        onValueChange = { tag = it },
                        label = { Text("Tag / Code") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = location,
                        onValueChange = { location = it },
                        label = { Text("Location") },
                        singleLine = true,
                        modifier = Modifier.weight(1.5f)
                    )
                }

                Text("System Voltage", style = MaterialTheme.typography.labelMedium)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SystemVoltage.values().forEach { v ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedVoltage = v }
                        ) {
                            RadioButton(
                                selected = selectedVoltage == v,
                                onClick = { selectedVoltage = v }
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(v.label, fontSize = 12.sp)
                        }
                    }
                }

                Text("Main Incomer Breaker", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf(40, 63, 100, 125, 160).forEach { rating ->
                        OutlinedButton(
                            onClick = { mainBreakerAmps = rating },
                            colors = if (mainBreakerAmps == rating) ButtonDefaults.outlinedButtonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            ) else ButtonDefaults.outlinedButtonColors(),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("${rating}A", fontSize = 11.sp, fontWeight = if (mainBreakerAmps == rating) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val p = PanelDesign(
                        name = name.ifBlank { "Distribution Board" },
                        tag = tag.ifBlank { "DB-01" },
                        location = location.ifBlank { "Building" },
                        systemVoltage = selectedVoltage,
                        mainBreakerAmps = mainBreakerAmps,
                        mainBreakerPoles = if (selectedVoltage.isThreePhase) 4 else 2,
                        enclosureRows = rows,
                        modulesPerRow = modulesPerRow
                    )
                    onCreate(p)
                }
            ) {
                Text("Create Panel")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
