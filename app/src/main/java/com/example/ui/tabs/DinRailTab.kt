package com.example.ui.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.calculation.ElectricalCalculator
import com.example.model.*
import com.example.ui.theme.*

@Composable
fun DinRailTab(
    panel: PanelDesign,
    summary: PanelCalculationSummary,
    onEditCircuit: (CircuitBreaker) -> Unit,
    onToggleCircuit: (String) -> Unit,
    onAddCircuit: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Enclosure Capacity Header Bar
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.ViewWeek, contentDescription = null, tint = ElectricBlue)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "DIN Rail Enclosure (${panel.enclosureRows} Rows × ${panel.modulesPerRow} Modules)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                Surface(
                    color = if (panel.usedModules > panel.totalEnclosureCapacity) RoseDanger.copy(alpha = 0.2f) else EmeraldSuccess.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "${panel.usedModules} / ${panel.totalEnclosureCapacity} Modules Used",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (panel.usedModules > panel.totalEnclosureCapacity) RoseDanger else EmeraldSuccess
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Render DIN Rails (Row 1: Main Incomer + Surge Protector + First circuits; Rows 2+: Branch circuits)
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Main Row: Incomer Rail
            item {
                DinRailSection(
                    title = "ROW 1: MAIN INCOMER & PROTECTION RAIL",
                    panel = panel
                ) {
                    // Main Incomer Module
                    MainIncomerModule(panel, summary)
                    Spacer(modifier = Modifier.width(8.dp))

                    // SPD (Surge Protective Device)
                    SpdModule()
                    Spacer(modifier = Modifier.width(8.dp))

                    // First few branch circuits
                    panel.circuits.take(panel.modulesPerRow - 6).forEach { c ->
                        val calc = summary.circuitsSummary[c.id] ?: ElectricalCalculator.calculateCircuit(c, panel.systemVoltage)
                        McbModule(
                            circuit = c,
                            calculation = calc,
                            onEdit = { onEditCircuit(c) },
                            onToggle = { onToggleCircuit(c.id) }
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                }
            }

            // Subsequent rows
            val remainingCircuits = panel.circuits.drop((panel.modulesPerRow - 6).coerceAtLeast(0))
            val chunks = remainingCircuits.chunked(panel.modulesPerRow.coerceAtLeast(1))

            items(chunks.withIndex().toList()) { (idx, cctGroup) ->
                val rowNum = idx + 2
                DinRailSection(
                    title = "ROW $rowNum: DISTRIBUTION BRANCH RAIL",
                    panel = panel
                ) {
                    cctGroup.forEach { c ->
                        val calc = summary.circuitsSummary[c.id] ?: ElectricalCalculator.calculateCircuit(c, panel.systemVoltage)
                        McbModule(
                            circuit = c,
                            calculation = calc,
                            onEdit = { onEditCircuit(c) },
                            onToggle = { onToggleCircuit(c.id) }
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }

                    // Empty slots placeholder
                    val usedInRow = cctGroup.sumOf { it.poles }
                    val spareSlots = (panel.modulesPerRow - usedInRow).coerceAtLeast(0)
                    if (spareSlots > 0) {
                        SpareSlotModule(spareSlots, onAddCircuit)
                    }
                }
            }
        }
    }
}

@Composable
private fun DinRailSection(
    title: String,
    panel: PanelDesign,
    content: @Composable RowScope.() -> Unit
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))

        // Physical DIN Rail container (metallic style with steel track behind modules)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1E293B), RoundedCornerShape(8.dp))
                .border(1.dp, Color(0xFF334155), RoundedCornerShape(8.dp))
                .padding(vertical = 12.dp, horizontal = 10.dp)
        ) {
            // The Steel DIN 35mm Rail behind the breakers
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp)
                    .align(Alignment.Center)
                    .background(Color(0xFF475569).copy(alpha = 0.6f), RoundedCornerShape(2.dp))
                    .border(1.dp, Color(0xFF64748B), RoundedCornerShape(2.dp))
            )

            // Breaker modules placed on rail
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        content()
                    }
                }
            }
        }
    }
}

@Composable
private fun MainIncomerModule(panel: PanelDesign, summary: PanelCalculationSummary) {
    val modWidth = (panel.mainBreakerPoles * 26).coerceAtLeast(54).dp
    Card(
        modifier = Modifier
            .width(modWidth)
            .height(130.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
        shape = RoundedCornerShape(6.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(ElectricBlue))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header
            Surface(
                color = ElectricBlue,
                shape = RoundedCornerShape(2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "MAIN INCOMER",
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            }

            Text(
                text = "${panel.mainBreakerAmps}A",
                color = Color.White,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 14.sp
            )

            Text(
                text = "${panel.mainBreakerPoles}P 35kA",
                color = Color(0xFF94A3B8),
                fontSize = 8.sp
            )

            // Switch toggle handle (Main toggle)
            Box(
                modifier = Modifier
                    .width(modWidth - 16.dp)
                    .height(26.dp)
                    .background(Color(0xFFDC2626), RoundedCornerShape(3.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("ON", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 10.sp)
            }

            Text(
                text = String.format(java.util.Locale.US, "%.1f kW", summary.totalDemandWatts / 1000.0),
                color = ElectricBlueLight,
                fontSize = 8.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun SpdModule() {
    Card(
        modifier = Modifier
            .width(36.dp)
            .height(130.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        shape = RoundedCornerShape(6.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(AmberAccent))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(3.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Surface(
                color = AmberAccent,
                shape = RoundedCornerShape(2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "SPD",
                    fontSize = 7.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                    textAlign = TextAlign.Center
                )
            }

            Icon(Icons.Default.Bolt, contentDescription = null, tint = AmberAccent, modifier = Modifier.size(16.dp))

            Text("Type 2\n40kA", fontSize = 7.sp, color = Color.White, textAlign = TextAlign.Center)

            Box(
                modifier = Modifier
                    .size(14.dp)
                    .background(EmeraldSuccess, RoundedCornerShape(2.dp))
            )
        }
    }
}

@Composable
private fun McbModule(
    circuit: CircuitBreaker,
    calculation: CircuitCalculation,
    onEdit: () -> Unit,
    onToggle: () -> Unit
) {
    val modWidth = (circuit.poles * 34).coerceAtLeast(36).dp
    val phaseColor = when (circuit.assignedPhase) {
        Phase.L1 -> PhaseL1Color
        Phase.L2 -> PhaseL2Color
        Phase.L3 -> PhaseL3Color
        Phase.THREE_PHASE -> PhaseThreePhaseColor
    }

    Card(
        modifier = Modifier
            .width(modWidth)
            .height(130.dp)
            .clickable { onEdit() }
            .testTag("mcb_${circuit.circuitNumber}"),
        colors = CardDefaults.cardColors(
            containerColor = if (circuit.isOn) Color(0xFF0F172A) else Color(0xFF334155)
        ),
        shape = RoundedCornerShape(6.dp),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(
                if (calculation.isOverloaded) RoseDanger else phaseColor
            )
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(3.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Phase Color Band with Circuit Number
            Surface(
                color = phaseColor,
                shape = RoundedCornerShape(2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "C${circuit.circuitNumber} ${circuit.assignedPhase.shortName}",
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            }

            // Name
            Text(
                text = circuit.name,
                color = Color.White,
                fontSize = 7.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )

            // Rating & Curve (e.g. C16)
            Text(
                text = "${circuit.breakerCurve.name}${circuit.ratingAmps}",
                color = if (calculation.isOverloaded) RoseDanger else Color.White,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 12.sp
            )

            // Operating current & load
            Text(
                text = "${calculation.nominalCurrentAmps}A",
                color = if (calculation.isOverloaded) RoseDanger else ElectricBlueLight,
                fontWeight = FontWeight.Bold,
                fontSize = 9.sp
            )

            // Toggle switch handle
            Box(
                modifier = Modifier
                    .width(modWidth - 10.dp)
                    .height(24.dp)
                    .background(
                        if (circuit.isOn) Color(0xFF16A34A) else Color(0xFF64748B),
                        RoundedCornerShape(3.dp)
                    )
                    .clickable { onToggle() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (circuit.isOn) "ON" else "OFF",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 8.5.sp
                )
            }

            // Wire tag
            Text(
                text = "${circuit.cableCrossSectionMm2}mm²",
                color = Color(0xFF94A3B8),
                fontSize = 7.5.sp
            )
        }
    }
}

@Composable
private fun SpareSlotModule(spareCount: Int, onAdd: () -> Unit) {
    Card(
        modifier = Modifier
            .width((spareCount * 28).coerceAtMost(140).dp)
            .height(130.dp)
            .clickable { onAdd() },
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B).copy(alpha = 0.5f)),
        shape = RoundedCornerShape(6.dp),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(Color(0xFF475569))
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Default.Add, contentDescription = null, tint = ElectricBlue, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "+ Add ($spareCount Spare)",
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = ElectricBlue,
                textAlign = TextAlign.Center
            )
        }
    }
}
