package com.example.ui.tabs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
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
import com.example.ai.GeminiClient
import com.example.calculation.ElectricalCalculator
import com.example.model.PanelDesign
import com.example.model.PanelCalculationSummary
import com.example.ui.theme.AmberAccent
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.EmeraldSuccess
import kotlinx.coroutines.launch

@Composable
fun AiCopilotTab(
    panel: PanelDesign,
    summary: PanelCalculationSummary
) {
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var customQuestion by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }

    var auditResult by remember { mutableStateOf<String?>(null) }
    var isAuditing by remember { mutableStateOf(false) }
    var auditError by remember { mutableStateOf<String?>(null) }

    var searchResult by remember { mutableStateOf<Pair<String, List<String>>?>(null) }
    var isSearching by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // High Thinking Audit Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Psychology,
                        contentDescription = null,
                        tint = ElectricBlue,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "High-Thinking Electrical Engineering Audit",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Deep analytical reasoning with Gemini 3.1 Pro (Thinking Mode)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = customQuestion,
                    onValueChange = { customQuestion = it },
                    label = { Text("Specific audit inquiry (Optional)") },
                    placeholder = { Text("e.g. Check if the neutral wire is at risk of harmonic overheating") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 2
                )

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = {
                        isAuditing = true
                        auditResult = null
                        auditError = null
                        coroutineScope.launch {
                            val res = GeminiClient.performHighThinkingAudit(panel, customQuestion)
                            isAuditing = false
                            res.onSuccess { text ->
                                auditResult = text
                            }.onFailure { err ->
                                auditError = err.message ?: "Failed to complete engineering audit."
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("run_audit_button"),
                    enabled = !isAuditing,
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue)
                ) {
                    if (isAuditing) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Conducting Deep Engineering Thinking...")
                    } else {
                        Icon(Icons.Default.Verified, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Run Deep IEC/NEC Audit", fontWeight = FontWeight.Bold)
                    }
                }

                auditError?.let { err ->
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(err, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }

                auditResult?.let { result ->
                    Spacer(modifier = Modifier.height(14.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.AutoMirrored.Filled.Assignment, contentDescription = null, tint = EmeraldSuccess, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("AUDIT REPORT:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = EmeraldSuccess)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(result, fontSize = 13.sp, lineHeight = 20.sp)
                        }
                    }
                }
            }
        }

        // Search Grounding Inquiry Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.TravelExplore,
                        contentDescription = null,
                        tint = AmberAccent,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Live Search Grounding with Google",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Real-time electrical standards & manufacturer equipment data",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Ask technical question with Search Grounding") },
                    placeholder = { Text("e.g. What is the minimum breaker size for a 7.4kW EV charger under latest NEC?") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedButton(
                    onClick = {
                        if (searchQuery.isNotBlank()) {
                            isSearching = true
                            searchResult = null
                            searchError = null
                            coroutineScope.launch {
                                val contextStr = "Panel ${panel.tag}: ${panel.name}, ${panel.systemVoltage.label}, ${panel.mainBreakerAmps}A Main Breaker."
                                val res = GeminiClient.searchGroundingQuery(searchQuery, contextStr)
                                isSearching = false
                                res.onSuccess { pair ->
                                    searchResult = pair
                                }.onFailure { err ->
                                    searchError = err.message ?: "Search query failed."
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSearching && searchQuery.isNotBlank()
                ) {
                    if (isSearching) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Grounding with Google Search...")
                    } else {
                        Icon(Icons.Default.Search, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Search Standards & Codes")
                    }
                }

                searchError?.let { err ->
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(err, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }

                searchResult?.let { (text, queries) ->
                    Spacer(modifier = Modifier.height(14.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            if (queries.isNotEmpty()) {
                                Text(
                                    text = "Searched: ${queries.joinToString(", ")}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                            }
                            Text(text, fontSize = 13.sp, lineHeight = 20.sp)
                        }
                    }
                }
            }
        }
    }
}
