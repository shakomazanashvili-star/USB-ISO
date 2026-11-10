package com.example.ui.tabs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.calculation.ElectricalCalculator
import com.example.model.*
import com.example.ui.theme.*
import java.util.Locale

@Composable
fun PhaseBalancingTab(
    panel: PanelDesign,
    summary: PanelCalculationSummary,
    onApplyBalancedPanel: (PanelDesign) -> Unit
) {
    val scrollState = rememberScrollState()
    var optimizedPanel by remember { mutableStateOf<PanelDesign?>(null) }
    var optimizedSummary by remember { mutableStateOf<PanelCalculationSummary?>(null) }
    var optimizationAppliedMsg by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Phase Balancing Hero Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "3-Phase Power Distribution & Symmetry",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Prevents neutral overheating & maximizes transformer efficiency",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    val unbalanceStatus = if (summary.unbalancePercent <= 10.0) "BALANCED" else if (summary.unbalancePercent <= 20.0) "MODERATE" else "UNBALANCED"
                    val unbalanceColor = if (summary.unbalancePercent <= 10.0) EmeraldSuccess else if (summary.unbalancePercent <= 20.0) AmberAccent else RoseDanger

                    Surface(
                        color = unbalanceColor.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "${summary.unbalancePercent}% ($unbalanceStatus)",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = unbalanceColor
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Phase Bars
                val maxAmps = maxOf(summary.maxPhaseAmps, 1.0)
                PhaseMeterBar(
                    phaseName = "Phase L1",
                    color = PhaseL1Color,
                    amps = summary.phaseL1Amps,
                    watts = summary.phaseL1Watts,
                    maxAmps = maxAmps,
                    circuitCount = panel.circuits.count { it.isOn && (it.assignedPhase == Phase.L1 || it.assignedPhase == Phase.THREE_PHASE) }
                )
                Spacer(modifier = Modifier.height(10.dp))
                PhaseMeterBar(
                    phaseName = "Phase L2",
                    color = PhaseL2Color,
                    amps = summary.phaseL2Amps,
                    watts = summary.phaseL2Watts,
                    maxAmps = maxAmps,
                    circuitCount = panel.circuits.count { it.isOn && (it.assignedPhase == Phase.L2 || it.assignedPhase == Phase.THREE_PHASE) }
                )
                Spacer(modifier = Modifier.height(10.dp))
                PhaseMeterBar(
                    phaseName = "Phase L3",
                    color = PhaseL3Color,
                    amps = summary.phaseL3Amps,
                    watts = summary.phaseL3Watts,
                    maxAmps = maxAmps,
                    circuitCount = panel.circuits.count { it.isOn && (it.assignedPhase == Phase.L3 || it.assignedPhase == Phase.THREE_PHASE) }
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp))

                // Neutral and Main Incomer Stats
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Neutral Conductor Current (IN):", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = "${summary.neutralCurrentAmps} A",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = PhaseNeutralColor
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text("Main Incomer Capacity Used:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = "${summary.mainBreakerLoadingPercent}% (${summary.maxPhaseAmps}A / ${panel.mainBreakerAmps}A)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (summary.isPanelOverloaded) RoseDanger else ElectricBlue
                        )
                    }
                }
            }
        }

        // ONE-TAP AUTO-BALANCE ALGORITHM CARD
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Balance, contentDescription = null, tint = ElectricBlue, modifier = Modifier.size(28.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Smart Auto-Balancing Engine",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Multi-way optimization redistributes 1-phase loads across L1, L2, L3",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = {
                        val balanced = ElectricalCalculator.autoBalancePhases(panel)
                        val balancedSummary = ElectricalCalculator.calculatePanel(balanced)
                        optimizedPanel = balanced
                        optimizedSummary = balancedSummary
                        optimizationAppliedMsg = null
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("auto_balance_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue)
                ) {
                    Icon(Icons.Default.AutoFixHigh, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Run Auto-Phase Balancing", fontWeight = FontWeight.Bold)
                }

                // Optimization Preview Dialog / Section
                optimizedPanel?.let { optPanel ->
                    val optSum = optimizedSummary ?: return@let
                    Spacer(modifier = Modifier.height(14.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "OPTIMIZATION PROPOSAL:",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = EmeraldSuccess
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Current Imbalance: ${summary.unbalancePercent}% ➔ Optimized: ${optSum.unbalancePercent}%",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Neutral Current: ${summary.neutralCurrentAmps}A ➔ Reduced to: ${optSum.neutralCurrentAmps}A",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(onClick = { optimizedPanel = null }) {
                                    Text("Discard")
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = {
                                        onApplyBalancedPanel(optPanel)
                                        optimizationAppliedMsg = "Optimal phase balance successfully applied!"
                                        optimizedPanel = null
                                    }
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = null)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Apply Optimization")
                                }
                            }
                        }
                    }
                }

                optimizationAppliedMsg?.let { msg ->
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = EmeraldSuccess
                    )
                }
            }
        }

        // Phase Allocations Breakdown
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Circuit Allocation by Phase",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(10.dp))

                listOf(
                    Triple("Phase L1 Circuits", Phase.L1, PhaseL1Color),
                    Triple("Phase L2 Circuits", Phase.L2, PhaseL2Color),
                    Triple("Phase L3 Circuits", Phase.L3, PhaseL3Color),
                    Triple("3-Phase Balanced Loads", Phase.THREE_PHASE, PhaseThreePhaseColor)
                ).forEach { (title, phase, color) ->
                    val circuits = panel.circuits.filter {
                        if (phase == Phase.THREE_PHASE) it.poles >= 3 || it.assignedPhase == Phase.THREE_PHASE
                        else it.assignedPhase == phase && it.poles < 3
                    }
                    if (circuits.isNotEmpty()) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(modifier = Modifier.size(10.dp).background(color, RoundedCornerShape(2.dp)))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "$title (${circuits.size} circuits - ${circuits.sumOf { it.powerWatts }.toInt()} W)",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = circuits.joinToString(", ") { "C${it.circuitNumber}: ${it.name} (${it.powerWatts.toInt()}W)" },
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PhaseMeterBar(
    phaseName: String,
    color: Color,
    amps: Double,
    watts: Double,
    maxAmps: Double,
    circuitCount: Int
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(12.dp).background(color, RoundedCornerShape(3.dp)))
                Spacer(modifier = Modifier.width(6.dp))
                Text(phaseName, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Spacer(modifier = Modifier.width(6.dp))
                Text("($circuitCount circuits)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                text = String.format(Locale.US, "%.1f A (%.1f kW)", amps, watts / 1000.0),
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = color
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Progress Track
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(14.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(7.dp))
        ) {
            val fraction = (amps / maxAmps).coerceIn(0.04, 1.0).toFloat()
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .fillMaxHeight()
                    .background(color, RoundedCornerShape(7.dp))
            )
        }
    }
}
