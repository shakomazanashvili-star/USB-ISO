package com.example.ui.dialogs

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import com.example.export.ExcelExporter
import com.example.export.PdfExporter
import com.example.export.VisioExporter
import com.example.model.PanelDesign
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.EmeraldSuccess
import java.io.File

@Composable
fun ExportDialog(
    panel: PanelDesign,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var exportedFile by remember { mutableStateOf<File?>(null) }
    var exportFormatName by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .testTag("export_dialog"),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Download,
                            contentDescription = null,
                            tint = ElectricBlue,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Export Panel Project",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Excel, PDF, and Microsoft Visio formats",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                if (exportedFile == null) {
                    Text(
                        text = "SELECT EXPORT FORMAT",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    // 1. Excel Export Card
                    ExportFormatCard(
                        icon = Icons.Default.TableChart,
                        iconTint = Color(0xFF107C41), // Excel green
                        title = "Microsoft Excel (.xls / .csv)",
                        subtitle = "Structured Panel Schedule table with load calculations, currents, cable sizes, and phase summary.",
                        onClick = {
                            val f = ExcelExporter.exportToExcel(context, panel)
                            exportedFile = f
                            exportFormatName = "Microsoft Excel"
                        }
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // 2. PDF Export Card
                    ExportFormatCard(
                        icon = Icons.Default.PictureAsPdf,
                        iconTint = Color(0xFFE11D48), // PDF red
                        title = "Adobe PDF Document (.pdf)",
                        subtitle = "Architectural Single-Line Diagram (SLD) drawing & engineering schedule with sign-off block.",
                        onClick = {
                            val f = PdfExporter.exportToPdf(context, panel)
                            exportedFile = f
                            exportFormatName = "Adobe PDF"
                        }
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // 3. Microsoft Visio Export Card (VDX & SVG)
                    ExportFormatCard(
                        icon = Icons.Default.Schema,
                        iconTint = Color(0xFF2B579A), // Visio blue
                        title = "Microsoft Visio (.vdx / .svg)",
                        subtitle = "Visio XML Drawing & vector schematic with DIN-rail busbars, MCBs, and editable shapes.",
                        onClick = {
                            val f = VisioExporter.exportToVisioVdx(context, panel)
                            exportedFile = f
                            exportFormatName = "Microsoft Visio"
                        }
                    )
                } else {
                    // Success View after generation
                    Card(
                        colors = CardDefaults.cardColors(containerColor = EmeraldSuccess.copy(alpha = 0.15f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = EmeraldSuccess, modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Export Completed!",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = EmeraldSuccess
                            )
                            Text(
                                text = "$exportFormatName file successfully generated:",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = exportedFile?.name ?: "",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Actions
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                val file = exportedFile ?: return@Button
                                shareFile(context, file)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("share_file_button")
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Share File")
                        }

                        OutlinedButton(
                            onClick = {
                                val file = exportedFile ?: return@OutlinedButton
                                openFile(context, file)
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Open")
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    TextButton(
                        onClick = { exportedFile = null },
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Text("Export Another Format")
                    }
                }
            }
        }
    }
}

@Composable
private fun ExportFormatCard(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(iconTint.copy(alpha = 0.15f), shape = RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(24.dp))
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun shareFile(context: Context, file: File) {
    try {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val mimeType = when {
            file.name.endsWith(".pdf") -> "application/pdf"
            file.name.endsWith(".xls") -> "application/vnd.ms-excel"
            file.name.endsWith(".csv") -> "text/csv"
            file.name.endsWith(".vdx") -> "application/vnd.ms-visio.drawing"
            file.name.endsWith(".svg") -> "image/svg+xml"
            else -> "*/*"
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share Electrical Panel Document"))
    } catch (e: Exception) {
        Toast.makeText(context, "Error sharing file: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

private fun openFile(context: Context, file: File) {
    try {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val mimeType = when {
            file.name.endsWith(".pdf") -> "application/pdf"
            file.name.endsWith(".xls") -> "application/vnd.ms-excel"
            file.name.endsWith(".csv") -> "text/csv"
            file.name.endsWith(".vdx") -> "application/vnd.ms-visio.drawing"
            file.name.endsWith(".svg") -> "image/svg+xml"
            else -> "*/*"
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "No app available to open this file directly. Please use 'Share File'.", Toast.LENGTH_LONG).show()
    }
}
