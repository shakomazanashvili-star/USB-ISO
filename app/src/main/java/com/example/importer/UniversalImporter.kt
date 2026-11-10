package com.example.importer

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

object UniversalImporter {

    fun importFromUri(context: Context, uri: Uri): ImportResult {
        return try {
            val fileName = queryFileName(context, uri) ?: "imported_file"
            val contentResolver = context.contentResolver
            val inputStream: InputStream? = contentResolver.openInputStream(uri)
            if (inputStream == null) {
                return ImportResult(
                    success = false,
                    fileName = fileName,
                    errorMessage = "Could not open stream for selected file."
                )
            }

            val bytes = inputStream.use { it.readBytes() }
            importFromBytes(fileName, bytes)
        } catch (e: Exception) {
            ImportResult(
                success = false,
                fileName = "selected_file",
                errorMessage = "Import error: ${e.localizedMessage ?: e.message}"
            )
        }
    }

    fun importFromBytes(fileName: String, bytes: ByteArray): ImportResult {
        val lower = fileName.lowercase()
        val text = try { String(bytes, Charsets.UTF_8) } catch (e: Exception) { "" }

        return when {
            // PDF Document
            lower.endsWith(".pdf") || (bytes.size >= 4 && bytes[0] == '%'.code.toByte() && bytes[1] == 'P'.code.toByte() && bytes[2] == 'D'.code.toByte() && bytes[3] == 'F'.code.toByte()) -> {
                PdfImporter.parse(fileName, bytes)
            }

            // Visio XML Drawing (.vdx) or Visio SVG (.svg)
            lower.endsWith(".vdx") || (lower.endsWith(".xml") && text.contains("VisioDocument", ignoreCase = true)) -> {
                VisioImporter.parse(fileName, text)
            }
            lower.endsWith(".svg") || (text.contains("<svg", ignoreCase = true)) -> {
                VisioImporter.parse(fileName, text)
            }

            // Microsoft Excel (.xls XML / .csv)
            lower.endsWith(".xls") || lower.endsWith(".xlsx") || lower.endsWith(".csv") || text.contains("Workbook", ignoreCase = true) || text.contains("Panel Schedule", ignoreCase = true) -> {
                ExcelImporter.parse(fileName, text)
            }

            // Fallback content-based heuristic
            text.contains("Visio", ignoreCase = true) -> {
                VisioImporter.parse(fileName, text)
            }
            text.contains("%PDF") -> {
                PdfImporter.parse(fileName, bytes)
            }
            else -> {
                // Try Excel/CSV first, then text parser
                val excelRes = ExcelImporter.parse(fileName, text)
                if (excelRes.success) excelRes else PdfImporter.parseTextDirect(fileName, text)
            }
        }
    }

    private fun queryFileName(context: Context, uri: Uri): String? {
        if (uri.scheme == "content") {
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1) {
                            return cursor.getString(nameIndex)
                        }
                    }
                }
            } catch (e: Exception) {
                // Ignore fallback to path
            }
        }
        return uri.path?.substringAfterLast('/')
    }

    // ==========================================
    // Built-in High-Fidelity Engineering Samples
    // ==========================================

    fun getSampleExcelCsv(): String {
        return """
PANEL SCHEDULE & LOAD CALCULATION REPORT
Panel Name,Commercial Center MDB-North
Panel Tag,DB-COMM-01
Location,Substation Room 102
System Voltage,3-Phase 400V / 230V (EU / IEC)
Main Breaker,125 A (3P)
Diversity Factor,0.85

Circuit #,Description,Load Type,Poles,Phase,Rating (A),Curve,Power (W),Power Factor,Current (A),Cable Size (mm²),Length (m),Volt Drop (V),Volt Drop (%),RCD Protection,Status
1,Reception & Atrium LED,Lighting Circuit,1P,L1,16A,Type B,1800,0.95,8.2,2.5 mm²,28 m,1.8,0.78%,Yes (30mA),ACTIVE
2,Floor 1 Workstation Sockets,General Power Sockets,1P,L2,20A,Type C,3200,0.90,15.5,4.0 mm²,35 m,3.2,1.39%,Yes (30mA),ACTIVE
3,Server Room Dedicated Rack,General Power Sockets,1P,L3,25A,Type C,4200,0.95,19.2,6.0 mm²,20 m,1.5,0.65%,Yes (30mA),ACTIVE
4,Conference Room HVAC,Air Conditioning / Heat Pump,1P,L1,20A,Type C,3500,0.85,17.9,4.0 mm²,25 m,2.6,1.13%,Yes (30mA),ACTIVE
5,Kitchen Coffee & Microwaves,Kitchen Heavy Appliances,1P,L2,16A,Type C,2800,0.95,12.8,2.5 mm²,18 m,2.1,0.91%,Yes (30mA),ACTIVE
6,Chiller Unit 3-Phase Pump,Motor / Water Pump,3P,3P,32A,Type D,9000,0.80,16.2,10.0 mm²,45 m,3.8,0.95%,Yes (100mA),ACTIVE
7,Dual EV Charging Station,EV Vehicle Charger,3P,3P,32A,Type C,11000,0.98,16.2,10.0 mm²,30 m,2.2,0.55%,Yes (30mA),ACTIVE
8,Emergency Exit Lighting,Lighting Circuit,1P,L3,10A,Type B,800,0.95,3.7,1.5 mm²,40 m,2.3,1.00%,Yes (30mA),ACTIVE
""".trimIndent()
    }

    fun getSampleVisioSvg(): String {
        return """
<svg xmlns="http://www.w3.org/2000/svg" width="900" height="600" viewBox="0 0 900 600">
  <rect width="100%" height="100%" fill="#FFFFFF"/>
  <text x="40" y="50" font-family="Segoe UI, sans-serif" font-size="22" font-weight="bold" fill="#0F172A">Distribution Board DB-PLANT-02</text>
  <text x="40" y="80" font-family="Segoe UI, sans-serif" font-size="14" fill="#64748B">Main Incomer: 100A 3P | Voltage: 400V/230V | Tag: DB-PLANT-02</text>

  <g id="circuit_1">
    <rect x="50" y="120" width="160" height="90" rx="8" fill="#F1F5F9" stroke="#CBD5E1"/>
    <text x="65" y="145" font-family="Segoe UI" font-size="14" font-weight="bold" fill="#0F172A">C1: Warehouse Lighting</text>
    <text x="65" y="170" font-family="Segoe UI" font-size="12" fill="#EF4444">16A L1 • 2200W</text>
    <text x="65" y="192" font-family="Segoe UI" font-size="11" fill="#64748B">2.5 mm² • Type B</text>
  </g>

  <g id="circuit_2">
    <rect x="230" y="120" width="160" height="90" rx="8" fill="#F1F5F9" stroke="#CBD5E1"/>
    <text x="245" y="145" font-family="Segoe UI" font-size="14" font-weight="bold" fill="#0F172A">C2: High-Bay Sockets</text>
    <text x="245" y="170" font-family="Segoe UI" font-size="12" fill="#F59E0B">20A L2 • 3500W</text>
    <text x="245" y="192" font-family="Segoe UI" font-size="11" fill="#64748B">4.0 mm² • Type C</text>
  </g>

  <g id="circuit_3">
    <rect x="410" y="120" width="160" height="90" rx="8" fill="#F1F5F9" stroke="#CBD5E1"/>
    <text x="425" y="145" font-family="Segoe UI" font-size="14" font-weight="bold" fill="#0F172A">C3: HVAC Ventilation</text>
    <text x="425" y="170" font-family="Segoe UI" font-size="12" fill="#3B82F6">25A L3 • 4600W</text>
    <text x="425" y="192" font-family="Segoe UI" font-size="11" fill="#64748B">6.0 mm² • Type C</text>
  </g>

  <g id="circuit_4">
    <rect x="590" y="120" width="160" height="90" rx="8" fill="#F1F5F9" stroke="#CBD5E1"/>
    <text x="605" y="145" font-family="Segoe UI" font-size="14" font-weight="bold" fill="#0F172A">C4: Main Booster Pump</text>
    <text x="605" y="170" font-family="Segoe UI" font-size="12" fill="#8B5CF6">32A 3P • 7500W</text>
    <text x="605" y="192" font-family="Segoe UI" font-size="11" fill="#64748B">10.0 mm² • Type D</text>
  </g>
</svg>
""".trimIndent()
    }

    fun getSamplePdfText(): String {
        return """
ELECTRICAL DISTRIBUTION BOARD SPECIFICATION
Panel Name: Hospital Wing Panel B
Tag: DB-MED-04
Location: 2nd Floor Clinical Distribution
System Voltage: 3-Phase 400V / 230V
Main Breaker: 80A (3P)
Diversity Factor: 0.80

BRANCH CIRCUIT SCHEDULE:
Circuit 1: Operating Theater Lighting | 16A | L1 | 1600W | 2.5mm² | Type B
Circuit 2: Medical Diagnostic Equipment | 25A | L2 | 4500W | 6.0mm² | Type C
Circuit 3: Sterilization Autoclave Unit | 32A | L3 | 6000W | 10.0mm² | Type C
Circuit 4: Nurse Station Outlets | 16A | L1 | 2400W | 2.5mm² | Type C
Circuit 5: Patient Ward Lighting | 10A | L2 | 1100W | 1.5mm² | Type B
Circuit 6: Clean Room Air Handling | 20A | L3 | 3800W | 4.0mm² | Type C
Circuit 7: Emergency Backup Generator Feed | 40A | 3P | 15000W | 16.0mm² | Type D
""".trimIndent()
    }

    suspend fun importFromUrl(urlString: String): ImportResult = withContext(Dispatchers.IO) {
        try {
            val cleanUrl = urlString.trim()
            val url = URL(cleanUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            connection.instanceFollowRedirects = true
            connection.connect()

            val fileName = cleanUrl.substringAfterLast("/").substringBefore("?").ifBlank { "downloaded_schedule" }
            val bytes = connection.inputStream.use { it.readBytes() }
            importFromBytes(fileName, bytes)
        } catch (e: Exception) {
            ImportResult(
                success = false,
                fileName = "url_resource",
                errorMessage = "Failed to download from link: ${e.localizedMessage ?: e.message}"
            )
        }
    }

    fun saveSampleFilesToDownloads(context: Context): Int {
        var count = 0
        val samples = listOf(
            "Commercial_Center_MDB_North.csv" to getSampleExcelCsv().toByteArray(Charsets.UTF_8),
            "Plant_Distribution_Schematic.svg" to getSampleVisioSvg().toByteArray(Charsets.UTF_8),
            "Hospital_Wing_Panel_Spec.txt" to getSamplePdfText().toByteArray(Charsets.UTF_8)
        )

        for ((name, data) in samples) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val values = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                        put(MediaStore.MediaColumns.MIME_TYPE, when {
                            name.endsWith(".csv") -> "text/csv"
                            name.endsWith(".svg") -> "image/svg+xml"
                            else -> "text/plain"
                        })
                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    }
                    val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    uri?.let {
                        context.contentResolver.openOutputStream(it)?.use { out -> out.write(data) }
                        count++
                    }
                } else {
                    val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    if (!dir.exists()) dir.mkdirs()
                    val file = File(dir, name)
                    file.writeBytes(data)
                    count++
                }
            } catch (e: Exception) {
                // Fallback to internal storage downloads directory
                try {
                    val fallbackDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
                    val f = File(fallbackDir, name)
                    f.writeBytes(data)
                    count++
                } catch (_: Exception) {}
            }
        }
        return count
    }

    fun getDownloadedFiles(context: Context): List<Pair<String, Uri>> {
        val list = mutableListOf<Pair<String, Uri>>()
        // Scan MediaStore.Downloads
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val projection = arrayOf(
                    MediaStore.MediaColumns._ID,
                    MediaStore.MediaColumns.DISPLAY_NAME
                )
                val cursor = context.contentResolver.query(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    projection,
                    null,
                    null,
                    "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"
                )
                cursor?.use {
                    val idCol = it.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                    val nameCol = it.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                    while (it.moveToNext()) {
                        val id = it.getLong(idCol)
                        val name = it.getString(nameCol)
                        val lower = name.lowercase()
                        if (lower.endsWith(".csv") || lower.endsWith(".xls") || lower.endsWith(".xlsx") ||
                            lower.endsWith(".pdf") || lower.endsWith(".vdx") || lower.endsWith(".svg") || lower.endsWith(".txt")) {
                            val contentUri = Uri.withAppendedPath(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id.toString())
                            list.add(name to contentUri)
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        // Scan fallback app download directory
        try {
            val appDownloadDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            appDownloadDir?.listFiles()?.forEach { file ->
                val lower = file.name.lowercase()
                if (lower.endsWith(".csv") || lower.endsWith(".xls") || lower.endsWith(".xlsx") ||
                    lower.endsWith(".pdf") || lower.endsWith(".vdx") || lower.endsWith(".svg") || lower.endsWith(".txt")) {
                    if (!list.any { it.first == file.name }) {
                        list.add(file.name to Uri.fromFile(file))
                    }
                }
            }
        } catch (_: Exception) {}

        return list
    }
}
