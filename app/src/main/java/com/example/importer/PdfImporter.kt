package com.example.importer

import com.example.model.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.zip.InflaterInputStream

object PdfImporter {

    fun parse(fileName: String, pdfBytes: ByteArray): ImportResult {
        return try {
            val text = extractTextFromPdf(pdfBytes)
            if (text.isBlank()) {
                ImportResult(
                    success = false,
                    fileName = fileName,
                    sourceFormat = "Adobe PDF",
                    errorMessage = "Could not extract text stream from this PDF. It may be a scanned raster image or encrypted."
                )
            } else {
                parseExtractedText(fileName, text)
            }
        } catch (e: Exception) {
            ImportResult(
                success = false,
                fileName = fileName,
                sourceFormat = "Adobe PDF",
                errorMessage = "Failed to parse PDF: ${e.message}"
            )
        }
    }

    fun parseTextDirect(fileName: String, text: String): ImportResult {
        return parseExtractedText(fileName, text)
    }

    /**
     * Extracts text tokens from PDF streams (handling both uncompressed and FlateDecode streams)
     */
    private fun extractTextFromPdf(pdfBytes: ByteArray): String {
        val fullContent = String(pdfBytes, Charsets.ISO_8859_1)
        val sb = StringBuilder()

        // Scan for stream ... endstream blocks
        val streamRegex = Regex("stream\\r?\\n(.*?)\\r?\\nendstream", RegexOption.DOT_MATCHES_ALL)
        val matches = streamRegex.findAll(fullContent)

        for (match in matches) {
            val streamRaw = match.groupValues[1]
            val streamBytes = streamRaw.toByteArray(Charsets.ISO_8859_1)

            // Try decompressing with Inflater
            val decompressed = tryInflate(streamBytes) ?: streamBytes
            val streamText = String(decompressed, Charsets.ISO_8859_1)

            extractTextFromPdfStream(streamText, sb)
        }

        // If streams didn't yield text, scan the full document for text strings
        if (sb.length < 50) {
            extractTextFromPdfStream(fullContent, sb)
        }

        return sb.toString()
    }

    private fun tryInflate(bytes: ByteArray): ByteArray? {
        return try {
            val bis = ByteArrayInputStream(bytes)
            val iis = InflaterInputStream(bis)
            val bos = ByteArrayOutputStream()
            val buffer = ByteArray(4096)
            var read: Int
            while (iis.read(buffer).also { read = it } != -1) {
                bos.write(buffer, 0, read)
            }
            bos.toByteArray()
        } catch (e: Exception) {
            null
        }
    }

    private fun extractTextFromPdfStream(streamText: String, out: StringBuilder) {
        // Match simple Tj: (Hello World) Tj
        val tjRegex = Regex("\\((.*?)\\)\\s*(?:Tj|'|\")")
        for (m in tjRegex.findAll(streamText)) {
            val t = cleanPdfString(m.groupValues[1])
            if (t.isNotBlank()) {
                out.append(t).append(" ")
            }
        }

        // Match array TJ: [ (Hello) 10 (World) ] TJ
        val arrayTjRegex = Regex("\\[(.*?)\\]\\s*TJ")
        for (m in arrayTjRegex.findAll(streamText)) {
            val arrayBody = m.groupValues[1]
            val subTj = Regex("\\((.*?)\\)").findAll(arrayBody)
            for (sub in subTj) {
                val t = cleanPdfString(sub.groupValues[1])
                out.append(t).append(" ")
            }
            out.append("\n")
        }
        out.append("\n")
    }

    private fun cleanPdfString(str: String): String {
        return str
            .replace("\\(", "(")
            .replace("\\)", ")")
            .replace("\\\\", "\\")
            .replace("\\n", " ")
            .replace("\\r", "")
            .replace("\\t", " ")
            .trim()
    }

    /**
     * Parses reconstructed text lines into a PanelDesign and CircuitBreakers
     */
    private fun parseExtractedText(fileName: String, text: String): ImportResult {
        var panelName = "Imported PDF Specification"
        var panelTag = "DB-PDF"
        var location = "Main Electrical Board"
        var systemVoltage = SystemVoltage.THREE_PHASE_400_230
        var mainBreakerAmps = 100
        var diversityFactor = 0.85

        val circuits = mutableListOf<CircuitBreaker>()
        val warnings = mutableListOf<String>()

        val lines = text.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }

        for (line in lines) {
            // Check metadata
            if (line.contains("Panel Name", ignoreCase = true) || line.contains("Distribution Board", ignoreCase = true)) {
                val candidate = line.substringAfter(":").trim()
                if (candidate.isNotBlank() && candidate.length > 2) panelName = candidate
            }
            if (line.contains("Tag:", ignoreCase = true) || line.contains("Panel Tag:", ignoreCase = true)) {
                val candidate = line.substringAfter(":").trim()
                if (candidate.isNotBlank()) panelTag = candidate
            }
            if (line.contains("Location:", ignoreCase = true)) {
                val candidate = line.substringAfter(":").trim()
                if (candidate.isNotBlank()) location = candidate
            }
            if (line.contains("Voltage:", ignoreCase = true) || line.contains("System Voltage", ignoreCase = true)) {
                if (line.contains("415")) systemVoltage = SystemVoltage.THREE_PHASE_415_240
                else if (line.contains("208")) systemVoltage = SystemVoltage.THREE_PHASE_208_120
                else if (line.contains("1-Phase") || line.contains("Single Phase")) systemVoltage = SystemVoltage.SINGLE_PHASE_230
            }
            if (line.contains("Main Breaker", ignoreCase = true) || line.contains("Incomer:", ignoreCase = true)) {
                val m = Regex("(\\d+)\\s*A").find(line)
                if (m != null) mainBreakerAmps = m.groupValues[1].toIntOrNull() ?: 100
            }
            if (line.contains("Diversity", ignoreCase = true)) {
                val m = Regex("(0\\.\\d+)").find(line)
                if (m != null) diversityFactor = m.groupValues[1].toDoubleOrNull() ?: 0.85
            }

            // Detect circuit row
            if (isCircuitLine(line)) {
                val cct = parseCircuitLine(line, circuits.size + 1)
                if (cct != null && !circuits.any { it.name == cct.name && it.circuitNumber == cct.circuitNumber }) {
                    circuits.add(cct)
                }
            }
        }

        // If line-by-line parsing was insufficient, try regex block scanning across full text
        if (circuits.isEmpty()) {
            val circuitRegex = Regex("(?:Circuit|Cct|#)\\s*(\\d+)[:\\s|]+([^|\\n,]+)(?:[|,]\\s*(\\d+)\\s*A)?(?:[|,]\\s*(L[123]|3P))?(?:[|,]\\s*(\\d+)\\s*W)?", RegexOption.IGNORE_CASE)
            for (m in circuitRegex.findAll(text)) {
                val num = m.groupValues[1].toIntOrNull() ?: (circuits.size + 1)
                val name = m.groupValues[2].trim()
                val amps = m.groupValues[3].toIntOrNull() ?: 16
                val phaseStr = m.groupValues[4]
                val powerW = m.groupValues[5].toDoubleOrNull() ?: (amps * 230.0 * 0.9)

                val phase = when {
                    phaseStr.equals("3P", ignoreCase = true) -> Phase.THREE_PHASE
                    phaseStr.equals("L1", ignoreCase = true) -> Phase.L1
                    phaseStr.equals("L2", ignoreCase = true) -> Phase.L2
                    phaseStr.equals("L3", ignoreCase = true) -> Phase.L3
                    else -> when ((num - 1) % 3) {
                        0 -> Phase.L1
                        1 -> Phase.L2
                        else -> Phase.L3
                    }
                }

                circuits.add(
                    CircuitBreaker(
                        id = UUID.randomUUID().toString(),
                        circuitNumber = num,
                        name = if (name.isNotBlank()) name else "Circuit $num",
                        poles = if (phase == Phase.THREE_PHASE) 3 else 1,
                        assignedPhase = phase,
                        ratingAmps = amps,
                        powerWatts = powerW
                    )
                )
            }
        }

        if (circuits.isEmpty()) {
            return ImportResult(
                success = false,
                fileName = fileName,
                sourceFormat = "Adobe PDF (.pdf)",
                errorMessage = "No valid electrical circuit schedule entries were detected in the PDF text."
            )
        }

        return ImportResult(
            success = true,
            panelName = panelName,
            panelTag = panelTag,
            location = location,
            systemVoltage = systemVoltage,
            mainBreakerAmps = mainBreakerAmps,
            diversityFactor = diversityFactor,
            circuits = circuits,
            sourceFormat = "Adobe PDF (.pdf)",
            fileName = fileName,
            warnings = warnings
        )
    }

    private fun isCircuitLine(line: String): Boolean {
        val lower = line.lowercase()
        if (lower.contains("table of") || lower.contains("page ") || lower.contains("total demand") || lower.contains("neutral current")) {
            return false
        }
        val hasCctIndicator = line.matches(Regex(".*(?:^\\d+\\b|\\bCircuit\\b|\\bCct\\b|\\bC\\d+\\b|#\\d+).*"))
        val hasElectricalUnit = line.matches(Regex(".*(?:\\b\\d+A\\b|\\b\\d+W\\b|\\bkW\\b|\\bmm²\\b|\\bmm2\\b|\\bL[123]\\b|\\b3P\\b).*"))
        return hasCctIndicator && hasElectricalUnit
    }

    private fun parseCircuitLine(line: String, defaultNum: Int): CircuitBreaker? {
        // Extract number
        val numMatch = Regex("(?:Circuit|Cct|#|C)?\\s*(\\d+)[:\\s|.-]", RegexOption.IGNORE_CASE).find(line)
        val num = numMatch?.groupValues?.get(1)?.toIntOrNull() ?: defaultNum

        // Extract rating: e.g. 16A, 20A, 32A
        val ratingMatch = Regex("(\\d+)\\s*A\\b", RegexOption.IGNORE_CASE).find(line)
        val rating = ratingMatch?.groupValues?.get(1)?.toIntOrNull() ?: 16

        // Extract phase
        val phase = when {
            line.contains("3P", ignoreCase = true) || line.contains("3-Phase", ignoreCase = true) || line.contains("Three Phase", ignoreCase = true) -> Phase.THREE_PHASE
            line.contains("L1") || line.contains("Phase 1", ignoreCase = true) -> Phase.L1
            line.contains("L2") || line.contains("Phase 2", ignoreCase = true) -> Phase.L2
            line.contains("L3") || line.contains("Phase 3", ignoreCase = true) -> Phase.L3
            else -> when ((num - 1) % 3) {
                0 -> Phase.L1
                1 -> Phase.L2
                else -> Phase.L3
            }
        }
        val poles = if (phase == Phase.THREE_PHASE) 3 else 1

        // Extract power: e.g. 3.5 kW or 2500 W
        val kwMatch = Regex("([0-9]+(?:\\.[0-9]+)?)\\s*kW\\b", RegexOption.IGNORE_CASE).find(line)
        val wMatch = Regex("(\\d+)\\s*W\\b", RegexOption.IGNORE_CASE).find(line)
        val powerW = when {
            kwMatch != null -> (kwMatch.groupValues[1].toDoubleOrNull() ?: 2.5) * 1000.0
            wMatch != null -> wMatch.groupValues[1].toDoubleOrNull() ?: 2300.0
            else -> rating * 230.0 * 0.9
        }

        // Extract cable: e.g. 2.5 mm²
        val cableMatch = Regex("([0-9]+(?:\\.[0-9]+)?)\\s*(?:mm²|mm2)", RegexOption.IGNORE_CASE).find(line)
        val cableMm2 = cableMatch?.groupValues?.get(1)?.toDoubleOrNull() ?: when {
            rating <= 10 -> 1.5
            rating <= 16 -> 2.5
            rating <= 25 -> 4.0
            rating <= 32 -> 6.0
            else -> 10.0
        }

        // Clean name
        var name = line
            .replace(Regex("(?:Circuit|Cct|#|C)?\\s*\\d+[:\\s|.-]*", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\b\\d+\\s*A\\b", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\b[0-9]+(?:\\.[0-9]+)?\\s*(?:kW|W)\\b", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\b[0-9]+(?:\\.[0-9]+)?\\s*(?:mm²|mm2)\\b", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\b(?:L[123]|3P|Phase\\s*[123])\\b", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\b(?:Type [BCD]|[BCD]\\d+)\\b", RegexOption.IGNORE_CASE), "")
            .replace(Regex("[|;,\\[\\]()]"), " ")
            .trim()

        if (name.isBlank() || name.length < 2) {
            name = "Circuit $num"
        }

        val loadType = when {
            name.contains("light", ignoreCase = true) -> LoadType.LIGHTING
            name.contains("socket", ignoreCase = true) || name.contains("outlet", ignoreCase = true) -> LoadType.SOCKETS
            name.contains("kitchen", ignoreCase = true) || name.contains("oven", ignoreCase = true) -> LoadType.KITCHEN
            name.contains("hvac", ignoreCase = true) || name.contains("ac", ignoreCase = true) -> LoadType.HVAC
            name.contains("water", ignoreCase = true) || name.contains("heater", ignoreCase = true) -> LoadType.WATER_HEATER
            name.contains("ev", ignoreCase = true) || name.contains("charger", ignoreCase = true) -> LoadType.EV_CHARGER
            name.contains("solar", ignoreCase = true) -> LoadType.SOLAR
            name.contains("pump", ignoreCase = true) || name.contains("motor", ignoreCase = true) -> LoadType.MOTOR_PUMP
            else -> LoadType.SOCKETS
        }

        return CircuitBreaker(
            id = UUID.randomUUID().toString(),
            circuitNumber = num,
            name = name,
            loadType = loadType,
            poles = poles,
            assignedPhase = phase,
            ratingAmps = rating,
            powerWatts = powerW,
            cableCrossSectionMm2 = cableMm2,
            cableLengthMeters = 20.0,
            breakerCurve = BreakerCurve.C,
            hasRcd = true,
            rcdRatingMa = 30,
            isOn = true
        )
    }
}
