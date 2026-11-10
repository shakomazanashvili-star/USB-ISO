package com.example.importer

import com.example.model.CircuitBreaker
import com.example.model.PanelDesign
import com.example.model.SystemVoltage

enum class ImportFormat(val displayName: String, val extensions: List<String>, val mimeTypes: List<String>) {
    EXCEL("Excel / CSV (.xls, .csv)", listOf("xls", "csv", "xml"), listOf("application/vnd.ms-excel", "text/csv", "application/csv", "text/comma-separated-values")),
    VISIO("Microsoft Visio (.vdx, .svg)", listOf("vdx", "svg", "xml"), listOf("application/vnd.visio", "image/svg+xml", "text/xml")),
    PDF("Adobe PDF (.pdf)", listOf("pdf"), listOf("application/pdf")),
    AUTO_DETECT("Auto Detect", listOf("xls", "csv", "vdx", "svg", "pdf", "xml"), listOf("*/*"))
}

data class ImportResult(
    val success: Boolean,
    val panelName: String = "Imported Panel",
    val panelTag: String = "IMP-01",
    val location: String = "Electrical Switchroom",
    val systemVoltage: SystemVoltage = SystemVoltage.THREE_PHASE_400_230,
    val mainBreakerAmps: Int = 63,
    val mainBreakerPoles: Int = 3,
    val diversityFactor: Double = 0.85,
    val circuits: List<CircuitBreaker> = emptyList(),
    val sourceFormat: String = "Unknown",
    val fileName: String = "",
    val warnings: List<String> = emptyList(),
    val errorMessage: String? = null
) {
    fun toPanelDesign(): PanelDesign {
        return PanelDesign(
            id = java.util.UUID.randomUUID().toString(),
            name = panelName,
            tag = panelTag,
            location = location,
            systemVoltage = systemVoltage,
            mainBreakerAmps = mainBreakerAmps,
            mainBreakerPoles = mainBreakerPoles,
            diversityFactor = diversityFactor,
            circuits = circuits,
            notes = "Imported from $sourceFormat on ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US).format(java.util.Date())}"
        )
    }
}
