package com.example.importer

import com.example.model.*
import java.util.UUID

object VisioImporter {

    fun parse(fileName: String, content: String): ImportResult {
        val trimmed = content.trim()
        return if (trimmed.contains("<svg", ignoreCase = true) || trimmed.contains("xmlns=\"http://www.w3.org/2000/svg\"", ignoreCase = true)) {
            parseVisioSvg(fileName, content)
        } else {
            parseVisioVdx(fileName, content)
        }
    }

    /**
     * Parses Microsoft Visio XML Drawing (.vdx)
     */
    private fun parseVisioVdx(fileName: String, vdxXml: String): ImportResult {
        var panelName = "Imported Visio Panel"
        var panelTag = "DB-VISIO"
        var location = "Main Plant Room"
        var systemVoltage = SystemVoltage.THREE_PHASE_400_230
        var mainBreakerAmps = 100
        var diversityFactor = 0.85

        val circuits = mutableListOf<CircuitBreaker>()
        val warnings = mutableListOf<String>()

        // Check document title
        val titleMatch = Regex("<Title>(.*?)</Title>").find(vdxXml)
        if (titleMatch != null) {
            val titleText = titleMatch.groupValues[1]
            panelName = titleText.replace("Electrical Panel -", "").replace("Panel -", "").trim()
        }

        // Search for shapes in <Shapes>
        val shapeRegex = Regex("<Shape[^>]*ID=\"([^\"]+)\"[^>]*Name=\"([^\"]+)\"[^>]*>(.*?)</Shape>", RegexOption.DOT_MATCHES_ALL)
        val textRegex = Regex("<Text>(.*?)</Text>", RegexOption.DOT_MATCHES_ALL)

        val shapes = shapeRegex.findAll(vdxXml).toList()

        for (shape in shapes) {
            val shapeName = shape.groupValues[2]
            val shapeBody = shape.groupValues[3]
            val textMatch = textRegex.find(shapeBody)
            val text = textMatch?.groupValues?.get(1)?.replace(Regex("<[^>]+>"), " ")?.trim() ?: ""

            if (shapeName.contains("Title", ignoreCase = true) || text.contains("MAIN DISTRIBUTION", ignoreCase = true)) {
                // Extract panel specs from title block
                if (text.contains("Voltage:", ignoreCase = true)) {
                    systemVoltage = parseSystemVoltageFromText(text)
                }
                if (text.contains("Incomer:", ignoreCase = true) || text.contains("Main:", ignoreCase = true)) {
                    mainBreakerAmps = extractBreakerAmps(text, 100)
                }
                if (text.contains("Tag:", ignoreCase = true)) {
                    val tagMatch = Regex("Tag:\\s*([A-Za-z0-9_-]+)").find(text)
                    if (tagMatch != null) panelTag = tagMatch.groupValues[1]
                }
                continue
            }

            // Detect circuit breaker shape
            if (shapeName.contains("Circuit", ignoreCase = true) ||
                shapeName.contains("Breaker", ignoreCase = true) ||
                text.contains("A", ignoreCase = true) && (text.contains("L1") || text.contains("L2") || text.contains("L3") || text.contains("3P") || text.contains("W"))) {

                val cct = parseCircuitFromBlock(text, shapeName, circuits.size + 1)
                if (cct != null) {
                    circuits.add(cct)
                }
            }
        }

        if (circuits.isEmpty()) {
            // Fallback: heuristic scan over any <Text> tags
            val allTexts = textRegex.findAll(vdxXml).map { it.groupValues[1].replace(Regex("<[^>]+>"), " ").trim() }.filter { it.isNotBlank() }
            for (txt in allTexts) {
                if (txt.matches(Regex(".*(?:\\d+\\s*A|L[123]|\\d+\\s*W).*"))) {
                    val cct = parseCircuitFromBlock(txt, "Circuit", circuits.size + 1)
                    if (cct != null && !circuits.any { it.name == cct.name && it.circuitNumber == cct.circuitNumber }) {
                        circuits.add(cct)
                    }
                }
            }
        }

        if (circuits.isEmpty()) {
            return ImportResult(
                success = false,
                fileName = fileName,
                sourceFormat = "Visio XML (.vdx)",
                errorMessage = "No electrical circuit shapes were found in the Visio VDX drawing."
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
            sourceFormat = "Microsoft Visio XML (.vdx)",
            fileName = fileName,
            warnings = warnings
        )
    }

    /**
     * Parses Microsoft Visio-compatible SVG schematic (.svg)
     */
    private fun parseVisioSvg(fileName: String, svg: String): ImportResult {
        var panelName = "Imported Visio Schematic"
        var panelTag = "DB-SVG"
        var location = "Distribution Board Room"
        var systemVoltage = SystemVoltage.THREE_PHASE_400_230
        var mainBreakerAmps = 100
        var diversityFactor = 0.85

        val circuits = mutableListOf<CircuitBreaker>()
        val warnings = mutableListOf<String>()

        // Look for title text
        val titleMatch = Regex("<text[^>]*class=\"[^\"]*title[^\"]*\"[^>]*>(.*?)</text>", RegexOption.IGNORE_CASE).find(svg)
            ?: Regex("<text[^>]*font-weight=\"bold\"[^>]*>(.*?)</text>", RegexOption.IGNORE_CASE).find(svg)
        if (titleMatch != null) {
            val title = titleMatch.groupValues[1].replace(Regex("<[^>]+>"), "").trim()
            if (title.isNotBlank() && !title.contains("PHASE", ignoreCase = true)) {
                panelName = title
            }
        }

        // Look for grouped circuits <g ...> or circuit texts
        val groupRegex = Regex("<g[^>]*id=\"([^\"]+)\"[^>]*>(.*?)</g>", RegexOption.DOT_MATCHES_ALL)
        val textRegex = Regex("<text[^>]*>(.*?)</text>", RegexOption.DOT_MATCHES_ALL)

        val groups = groupRegex.findAll(svg).toList()
        for (g in groups) {
            val gId = g.groupValues[1]
            val gBody = g.groupValues[2]

            if (gId.contains("circuit", ignoreCase = true) || gId.contains("mcb", ignoreCase = true) || gId.contains("breaker", ignoreCase = true)) {
                val texts = textRegex.findAll(gBody).map { it.groupValues[1].replace(Regex("<[^>]+>"), "").trim() }.filter { it.isNotBlank() }.toList()
                val fullText = texts.joinToString(" • ")

                val cct = parseCircuitFromBlock(fullText, gId, circuits.size + 1)
                if (cct != null) {
                    circuits.add(cct)
                }
            }
        }

        // If no <g id="circuit..."> matched, scan all <text> elements
        if (circuits.isEmpty()) {
            val allTexts = textRegex.findAll(svg).map { it.groupValues[1].replace(Regex("<[^>]+>"), "").trim() }.filter { it.isNotBlank() }.toList()
            var currentNum = 1
            for (t in allTexts) {
                // If text looks like a circuit descriptor: e.g. "C1: Lighting", "16A L1 • 1200W"
                if (t.matches(Regex(".*(?:C\\d+:|\\b\\d+A\\b|Phase\\s*[123]|L[123]|\\b\\d+\\s*W\\b).*"))) {
                    val cct = parseCircuitFromBlock(t, "C$currentNum", currentNum)
                    if (cct != null && !circuits.any { it.name == cct.name && it.circuitNumber == cct.circuitNumber }) {
                        circuits.add(cct)
                        currentNum++
                    }
                }
            }
        }

        if (circuits.isEmpty()) {
            return ImportResult(
                success = false,
                fileName = fileName,
                sourceFormat = "Visio SVG",
                errorMessage = "No circuit schematic elements could be identified in the SVG file."
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
            sourceFormat = "Visio SVG (.svg)",
            fileName = fileName,
            warnings = warnings
        )
    }

    private fun parseCircuitFromBlock(text: String, identifier: String, defaultNum: Int): CircuitBreaker? {
        val clean = text.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&#10;", " ")
        if (clean.isBlank() || clean.equals("INCOMER", ignoreCase = true) || clean.contains("MAIN BREAKER", ignoreCase = true)) {
            return null
        }

        // Extract circuit number: e.g., C1, Circuit 2, #3
        val numMatch = Regex("(?:C|Circuit|#|No\\.?)\\s*(\\d+)", RegexOption.IGNORE_CASE).find(clean)
            ?: Regex("(?:cct|circuit)_(\\d+)", RegexOption.IGNORE_CASE).find(identifier)
        val cctNum = numMatch?.groupValues?.get(1)?.toIntOrNull() ?: defaultNum

        // Extract Name: e.g., "C1: Lighting Reception" or "Air Conditioning"
        var name = clean.substringBefore("•").substringBefore("|").substringBefore("\n").trim()
        name = name.replace(Regex("^(?:C|Circuit|#)\\s*\\d+[:\\-\\s]*", RegexOption.IGNORE_CASE), "").trim()
        if (name.isBlank() || name.matches(Regex("\\d+\\s*A.*"))) {
            name = "Circuit $cctNum"
        }

        // Extract rating: e.g., 16A, 20 A, 32A
        val ratingMatch = Regex("(\\d+)\\s*A\\b").find(clean)
        val ratingAmps = ratingMatch?.groupValues?.get(1)?.toIntOrNull() ?: 16

        // Extract phase: L1, L2, L3, 3P, 3-Phase
        val phase = when {
            clean.contains("3P", ignoreCase = true) || clean.contains("3-Phase", ignoreCase = true) || clean.contains("400V") -> Phase.THREE_PHASE
            clean.contains("L1") || clean.contains("Phase 1", ignoreCase = true) || clean.contains("PH 1", ignoreCase = true) -> Phase.L1
            clean.contains("L2") || clean.contains("Phase 2", ignoreCase = true) || clean.contains("PH 2", ignoreCase = true) -> Phase.L2
            clean.contains("L3") || clean.contains("Phase 3", ignoreCase = true) || clean.contains("PH 3", ignoreCase = true) -> Phase.L3
            else -> when ((cctNum - 1) % 3) {
                0 -> Phase.L1
                1 -> Phase.L2
                else -> Phase.L3
            }
        }
        val poles = if (phase == Phase.THREE_PHASE) 3 else 1

        // Extract power: e.g. 2300W or 3.5 kW
        val kwMatch = Regex("([0-9]+(?:\\.[0-9]+)?)\\s*kW\\b", RegexOption.IGNORE_CASE).find(clean)
        val wMatch = Regex("([0-9]+)\\s*W\\b", RegexOption.IGNORE_CASE).find(clean)
        val powerWatts = when {
            kwMatch != null -> (kwMatch.groupValues[1].toDoubleOrNull() ?: 2.0) * 1000.0
            wMatch != null -> wMatch.groupValues[1].toDoubleOrNull() ?: 2000.0
            else -> (ratingAmps * 230.0 * 0.9).coerceIn(500.0, 30000.0)
        }

        // Extract cable size: e.g. 2.5 mm² or 4.0mm2
        val cableMatch = Regex("([0-9]+(?:\\.[0-9]+)?)\\s*(?:mm²|mm2)\\b", RegexOption.IGNORE_CASE).find(clean)
        val cableSize = cableMatch?.groupValues?.get(1)?.toDoubleOrNull() ?: when {
            ratingAmps <= 10 -> 1.5
            ratingAmps <= 16 -> 2.5
            ratingAmps <= 25 -> 4.0
            ratingAmps <= 32 -> 6.0
            ratingAmps <= 50 -> 10.0
            else -> 16.0
        }

        // Extract curve: B, C, D
        val curve = when {
            clean.contains("Type B", ignoreCase = true) || clean.contains("B$ratingAmps") -> BreakerCurve.B
            clean.contains("Type D", ignoreCase = true) || clean.contains("D$ratingAmps") -> BreakerCurve.D
            else -> BreakerCurve.C
        }

        // Extract load type
        val loadType = when {
            name.contains("light", ignoreCase = true) -> LoadType.LIGHTING
            name.contains("socket", ignoreCase = true) || name.contains("outlet", ignoreCase = true) -> LoadType.SOCKETS
            name.contains("kitchen", ignoreCase = true) || name.contains("cooker", ignoreCase = true) -> LoadType.KITCHEN
            name.contains("hvac", ignoreCase = true) || name.contains("ac", ignoreCase = true) -> LoadType.HVAC
            name.contains("water", ignoreCase = true) || name.contains("heater", ignoreCase = true) -> LoadType.WATER_HEATER
            name.contains("ev", ignoreCase = true) || name.contains("charger", ignoreCase = true) -> LoadType.EV_CHARGER
            name.contains("solar", ignoreCase = true) || name.contains("pv", ignoreCase = true) -> LoadType.SOLAR
            name.contains("pump", ignoreCase = true) || name.contains("motor", ignoreCase = true) -> LoadType.MOTOR_PUMP
            name.contains("feeder", ignoreCase = true) -> LoadType.SUB_FEEDER
            else -> LoadType.SOCKETS
        }

        return CircuitBreaker(
            id = UUID.randomUUID().toString(),
            circuitNumber = cctNum,
            name = name,
            loadType = loadType,
            poles = poles,
            assignedPhase = phase,
            ratingAmps = ratingAmps,
            powerWatts = powerWatts,
            cableCrossSectionMm2 = cableSize,
            cableLengthMeters = 20.0,
            breakerCurve = curve,
            hasRcd = true,
            rcdRatingMa = 30,
            isOn = true
        )
    }

    private fun parseSystemVoltageFromText(text: String): SystemVoltage {
        return when {
            text.contains("415", ignoreCase = true) -> SystemVoltage.THREE_PHASE_415_240
            text.contains("208", ignoreCase = true) -> SystemVoltage.THREE_PHASE_208_120
            text.contains("1-Phase", ignoreCase = true) || text.contains("230V 1P", ignoreCase = true) -> SystemVoltage.SINGLE_PHASE_230
            else -> SystemVoltage.THREE_PHASE_400_230
        }
    }

    private fun extractBreakerAmps(text: String, defaultVal: Int): Int {
        val m = Regex("(\\d+)\\s*A\\b").find(text)
        return m?.groupValues?.get(1)?.toIntOrNull() ?: defaultVal
    }
}
