package com.example.ui.dialogs

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.importer.*
import com.example.model.CircuitBreaker
import com.example.model.PanelDesign
import com.example.model.Phase
import com.example.ui.theme.*
import kotlinx.coroutines.launch

enum class ImportMode {
    CREATE_NEW,
    APPEND,
    REPLACE
}

enum class ImportTab {
    STORAGE,
    CLIPBOARD,
    URL_LINK
}

@Composable
fun ImportDialog(
    currentPanel: PanelDesign? = null,
    onDismiss: () -> Unit,
    onImportComplete: (PanelDesign) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current

    var selectedTab by remember { mutableStateOf(ImportTab.STORAGE) }
    var selectedFormat by remember { mutableStateOf(ImportFormat.EXCEL) }
    var importResult by remember { mutableStateOf<ImportResult?>(null) }
    var isProcessing by remember { mutableStateOf(false) }
    var importMode by remember {
        mutableStateOf(if (currentPanel != null) ImportMode.APPEND else ImportMode.CREATE_NEW)
    }

    var pastedContent by remember { mutableStateOf("") }
    var urlInput by remember { mutableStateOf("") }
    var downloadedFiles by remember { mutableStateOf<List<Pair<String, Uri>>>(emptyList()) }

    // Load initial downloaded files from device storage
    LaunchedEffect(Unit) {
        downloadedFiles = UniversalImporter.getDownloadedFiles(context)
    }

    // System File Picker Launcher using */* so no valid files are hidden by MIME filters
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            isProcessing = true
            try {
                val res = UniversalImporter.importFromUri(context, uri)
                importResult = res
                if (!res.success) {
                    Toast.makeText(context, res.errorMessage ?: "Failed to parse file", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error reading file: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                isProcessing = false
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.98f)
                .fillMaxHeight(0.92f)
                .testTag("import_dialog"),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = ElectricBlue.copy(alpha = 0.12f),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.FileUpload,
                                    contentDescription = null,
                                    tint = ElectricBlue,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Import Schedule / Panel",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Excel (.xls, .csv), Visio (.vdx, .svg) & PDF",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Result Preview or Source Selection
                val result = importResult
                if (result != null && result.success) {
                    ImportPreviewView(
                        result = result,
                        currentPanel = currentPanel,
                        importMode = importMode,
                        onModeChange = { importMode = it },
                        onReset = { importResult = null },
                        onConfirm = {
                            val finalPanel = when (importMode) {
                                ImportMode.CREATE_NEW -> result.toPanelDesign()
                                ImportMode.APPEND -> {
                                    val base = currentPanel ?: result.toPanelDesign()
                                    val maxNum = base.circuits.maxOfOrNull { it.circuitNumber } ?: 0
                                    val renumbered = result.circuits.mapIndexed { idx, c ->
                                        c.copy(circuitNumber = maxNum + idx + 1)
                                    }
                                    base.copy(circuits = base.circuits + renumbered)
                                }
                                ImportMode.REPLACE -> {
                                    val base = currentPanel ?: result.toPanelDesign()
                                    base.copy(
                                        name = if (result.panelName.isNotBlank() && result.panelName != "Imported Panel") result.panelName else base.name,
                                        tag = if (result.panelTag.isNotBlank() && result.panelTag != "DB-01") result.panelTag else base.tag,
                                        circuits = result.circuits
                                    )
                                }
                            }
                            onImportComplete(finalPanel)
                        }
                    )
                } else {
                    // Method Selection Tabs
                    TabRow(
                        selectedTabIndex = selectedTab.ordinal,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        contentColor = ElectricBlue,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Tab(
                            selected = selectedTab == ImportTab.STORAGE,
                            onClick = { selectedTab = ImportTab.STORAGE },
                            text = { Text("📁 File / Storage", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                        )
                        Tab(
                            selected = selectedTab == ImportTab.CLIPBOARD,
                            onClick = { selectedTab = ImportTab.CLIPBOARD },
                            text = { Text("📋 Paste Text", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                        )
                        Tab(
                            selected = selectedTab == ImportTab.URL_LINK,
                            onClick = { selectedTab = ImportTab.URL_LINK },
                            text = { Text("🔗 Link / URL", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Box(modifier = Modifier.weight(1f)) {
                        when (selectedTab) {
                            ImportTab.STORAGE -> {
                                StorageSourceView(
                                    selectedFormat = selectedFormat,
                                    onSelectFormat = { selectedFormat = it },
                                    downloadedFiles = downloadedFiles,
                                    onPickFile = {
                                        // Use */* so Android file picker does not filter out .xls, .vdx, .svg
                                        filePickerLauncher.launch(arrayOf("*/*"))
                                    },
                                    onSelectDownloadedFile = { uri ->
                                        isProcessing = true
                                        try {
                                            val res = UniversalImporter.importFromUri(context, uri)
                                            importResult = res
                                            if (!res.success) {
                                                Toast.makeText(context, res.errorMessage ?: "Failed to parse file", Toast.LENGTH_LONG).show()
                                            }
                                        } finally {
                                            isProcessing = false
                                        }
                                    },
                                    onGenerateSamples = {
                                        val count = UniversalImporter.saveSampleFilesToDownloads(context)
                                        downloadedFiles = UniversalImporter.getDownloadedFiles(context)
                                        Toast.makeText(context, "$count sample files written to Downloads folder!", Toast.LENGTH_SHORT).show()
                                    },
                                    onLoadSample = {
                                        isProcessing = true
                                        try {
                                            val sampleResult = when (selectedFormat) {
                                                ImportFormat.EXCEL -> ExcelImporter.parse("Sample_Commercial_MDB.csv", UniversalImporter.getSampleExcelCsv())
                                                ImportFormat.VISIO -> VisioImporter.parse("Sample_Plant_Schematic.svg", UniversalImporter.getSampleVisioSvg())
                                                ImportFormat.PDF -> PdfImporter.parseTextDirect("Hospital_Wing_Specification.pdf", UniversalImporter.getSamplePdfText())
                                                ImportFormat.AUTO_DETECT -> ExcelImporter.parse("Sample_Commercial_MDB.csv", UniversalImporter.getSampleExcelCsv())
                                            }
                                            importResult = sampleResult
                                        } finally {
                                            isProcessing = false
                                        }
                                    }
                                )
                            }

                            ImportTab.CLIPBOARD -> {
                                ClipboardSourceView(
                                    pastedContent = pastedContent,
                                    onPastedContentChange = { pastedContent = it },
                                    onPasteFromClipboard = {
                                        val clipText = clipboardManager.getText()?.text
                                        if (!clipText.isNullOrBlank()) {
                                            pastedContent = clipText
                                            Toast.makeText(context, "Pasted from clipboard!", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "Clipboard is empty.", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    onParse = {
                                        if (pastedContent.isNotBlank()) {
                                            isProcessing = true
                                            try {
                                                val res = UniversalImporter.importFromBytes("pasted_schedule.txt", pastedContent.toByteArray(Charsets.UTF_8))
                                                importResult = res
                                                if (!res.success) {
                                                    Toast.makeText(context, res.errorMessage ?: "Failed to parse content", Toast.LENGTH_LONG).show()
                                                }
                                            } finally {
                                                isProcessing = false
                                            }
                                        }
                                    }
                                )
                            }

                            ImportTab.URL_LINK -> {
                                UrlSourceView(
                                    urlInput = urlInput,
                                    onUrlChange = { urlInput = it },
                                    onDownloadAndImport = {
                                        if (urlInput.isNotBlank()) {
                                            isProcessing = true
                                            coroutineScope.launch {
                                                try {
                                                    val res = UniversalImporter.importFromUrl(urlInput)
                                                    importResult = res
                                                    if (!res.success) {
                                                        Toast.makeText(context, res.errorMessage ?: "Failed to download", Toast.LENGTH_LONG).show()
                                                    }
                                                } finally {
                                                    isProcessing = false
                                                }
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }

                    if (isProcessing) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = ElectricBlue)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Analyzing and importing electrical schedule...", fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StorageSourceView(
    selectedFormat: ImportFormat,
    onSelectFormat: (ImportFormat) -> Unit,
    downloadedFiles: List<Pair<String, Uri>>,
    onPickFile: () -> Unit,
    onSelectDownloadedFile: (Uri) -> Unit,
    onGenerateSamples: () -> Unit,
    onLoadSample: () -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Explanatory Note for Emulator Storage
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.Top) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    tint = ElectricBlue,
                    modifier = Modifier.size(18.dp).padding(top = 2.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Why is Downloads empty?",
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = ElectricBlue
                    )
                    Text(
                        text = "The emulator runs in the cloud (sdk_gphone64), so files downloaded on your physical phone are not inside it. You can tap 'Save Sample Files to Downloads' below or use the '📋 Paste Text' tab to paste your file!",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 14.sp
                    )
                }
            }
        }

        // Format Selector
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(
                ImportFormat.EXCEL to "Excel (.xls, .csv)",
                ImportFormat.VISIO to "Visio (.vdx, .svg)",
                ImportFormat.PDF to "PDF (.pdf)"
            ).forEach { (fmt, label) ->
                val isSelected = selectedFormat == fmt
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onSelectFormat(fmt) },
                    shape = RoundedCornerShape(10.dp),
                    color = if (isSelected) ElectricBlue.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, ElectricBlue) else null
                ) {
                    Text(
                        text = label,
                        fontSize = 10.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) ElectricBlue else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }

        // Browse Storage Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onPickFile() },
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            ),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = ElectricBlue.copy(alpha = 0.15f),
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.FolderOpen,
                            contentDescription = null,
                            tint = ElectricBlue,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Browse Storage (*.*)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Text(
                        text = "Opens file manager to pick any file from device",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // Generate Samples Button
        OutlinedButton(
            onClick = onGenerateSamples,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp)
        ) {
            Icon(Icons.Default.DownloadForOffline, contentDescription = null, tint = ElectricBlue, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Save Sample Files into Downloads Folder", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }

        // List of files found in Downloads (if any)
        if (downloadedFiles.isNotEmpty()) {
            Text(
                text = "Detected Files in Downloads (${downloadedFiles.size}):",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            downloadedFiles.forEach { (name, uri) ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectDownloadedFile(uri) },
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(
                                when {
                                    name.endsWith(".csv") || name.endsWith(".xls") || name.endsWith(".xlsx") -> Icons.Default.TableChart
                                    name.endsWith(".svg") || name.endsWith(".vdx") -> Icons.Default.Schema
                                    else -> Icons.Default.Description
                                },
                                contentDescription = null,
                                tint = ElectricBlue,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = name, fontSize = 11.sp, maxLines = 1, fontWeight = FontWeight.Medium)
                        }
                        Text("Import", fontSize = 11.sp, color = ElectricBlue, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Instant Load Template Button
        Button(
            onClick = onLoadSample,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue)
        ) {
            Icon(Icons.Default.Bolt, contentDescription = null, tint = AmberAccent, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "⚡ Instant Load Sample (${when (selectedFormat) {
                    ImportFormat.EXCEL -> "Excel MDB"
                    ImportFormat.VISIO -> "Visio SVG"
                    ImportFormat.PDF -> "PDF Spec"
                    else -> "Template"
                }})",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun ClipboardSourceView(
    pastedContent: String,
    onPastedContentChange: (String) -> Unit,
    onPasteFromClipboard: () -> Unit,
    onParse: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Explanatory Banner
        Card(
            colors = CardDefaults.cardColors(containerColor = EmeraldSuccess.copy(alpha = 0.15f)),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.ContentPaste, contentDescription = null, tint = EmeraldSuccess, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Fastest method: Copy the table or text from your phone/PC and tap 'Paste from Clipboard' below!",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Button(
            onClick = onPasteFromClipboard,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(containerColor = EmeraldSuccess)
        ) {
            Icon(Icons.Default.ContentPasteGo, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("📋 Paste from Clipboard", fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }

        OutlinedTextField(
            value = pastedContent,
            onValueChange = onPastedContentChange,
            placeholder = { Text("Or paste CSV table rows, Visio XML / SVG, or PDF text specification here...", fontSize = 11.sp) },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(10.dp),
            textStyle = LocalTextStyle.current.copy(fontSize = 11.sp)
        )

        Button(
            onClick = onParse,
            enabled = pastedContent.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue)
        ) {
            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Parse & Extract Circuits", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun UrlSourceView(
    urlInput: String,
    onUrlChange: (String) -> Unit,
    onDownloadAndImport: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Link, contentDescription = null, tint = ElectricBlue, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Enter a direct URL to a CSV, XLS, SVG, or PDF file to download and import it directly into the app.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        OutlinedTextField(
            value = urlInput,
            onValueChange = onUrlChange,
            label = { Text("File URL (HTTP / HTTPS)") },
            placeholder = { Text("https://example.com/panel_schedule.csv", fontSize = 11.sp) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp)
        )

        Button(
            onClick = onDownloadAndImport,
            enabled = urlInput.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue)
        ) {
            Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Download & Import", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ImportPreviewView(
    result: ImportResult,
    currentPanel: PanelDesign?,
    importMode: ImportMode,
    onModeChange: (ImportMode) -> Unit,
    onReset: () -> Unit,
    onConfirm: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            // Success Banner
            Card(
                colors = CardDefaults.cardColors(containerColor = EmeraldSuccess.copy(alpha = 0.12f)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = EmeraldSuccess, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Extracted ${result.circuits.size} Circuits from ${result.sourceFormat}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = EmeraldSuccess
                        )
                        Text(
                            text = "Panel: ${result.panelName} (${result.panelTag}) • Main: ${result.mainBreakerAmps}A",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // If inside panel editor, give option to Append or Replace or Create New
            if (currentPanel != null) {
                Text("Import Destination:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        ImportMode.APPEND to "Append (+${result.circuits.size})",
                        ImportMode.REPLACE to "Replace",
                        ImportMode.CREATE_NEW to "Create New"
                    ).forEach { (mode, label) ->
                        val isSel = importMode == mode
                        FilterChip(
                            selected = isSel,
                            onClick = { onModeChange(mode) },
                            label = { Text(label, fontSize = 10.sp) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            // Preview List
            Text(
                text = "Preview of Circuits (${result.circuits.size}):",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(result.circuits) { cct ->
                    CircuitPreviewItem(cct)
                }
            }
        }

        // Action Buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = onReset,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Pick Another")
            }

            Button(
                onClick = onConfirm,
                modifier = Modifier.weight(1.3f),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue)
            ) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Confirm Import", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun CircuitPreviewItem(circuit: CircuitBreaker) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        border = androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = when (circuit.assignedPhase) {
                        Phase.L1 -> PhaseL1Color
                        Phase.L2 -> PhaseL2Color
                        Phase.L3 -> PhaseL3Color
                        Phase.THREE_PHASE -> PhaseThreePhaseColor
                    },
                    modifier = Modifier.size(width = 30.dp, height = 22.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = circuit.assignedPhase.shortName,
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "C${circuit.circuitNumber}: ${circuit.name}",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        maxLines = 1
                    )
                    Text(
                        text = "${circuit.ratingAmps}A (${circuit.breakerCurve.label}) • ${circuit.cableCrossSectionMm2} mm²",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text(
                text = "${circuit.powerWatts.toInt()} W",
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = ElectricBlue
            )
        }
    }
}
