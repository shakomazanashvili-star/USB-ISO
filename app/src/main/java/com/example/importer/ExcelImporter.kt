package com.example.importer

import com.example.model.*
import java.util.UUID

object ExcelImporter {

    fun parse(fileName: String, content: String): ImportResult {
        val trimmed = content.trim()
        return if (trimmed.startsWith("<?xml") || trimmed.contains("<Workbook") || trimmed.contains("<Table")) {
            parseExcelXml(fileName, content)
        } else {
            parseCsv(fileName, content)
        }
    }

    /**
     * Parses Microsoft Excel 2003 XML Spreadsheet (.xls / .xml)
     */
    private fun parseExcelXml(fileName: String, xml: String): ImportResult {
        var panelName = "Imported Excel Panel"
        var panelTag = "DB-01"
        var location = "Main Switchroom"
        var systemVoltage = SystemVoltage.THREE_PHASE_400_230
        var mainBreakerAmps = 63
        var diversityFactor = 0.85

        val circuits = mutableListOf<CircuitBreaker>()
        val warnings = mutableListOf<String>()

        // Extract rows
        val rowRegex = Regex("<Row[^>]*>(.*?)</Row>", RegexOption.DOT_MATCHES_ALL)
        val cellRegex = Regex("<Cell[^>]*>.*?<Data[^>]*>(.*?)</Data>.*?</Cell>", RegexOption.DOT_MATCHES_ALL)

        val rows = rowRegex.findAll(xml).map { rowMatch ->
            cellRegex.findAll(rowMatch.groupValues[1]).map { it.groupValues[1].trim() }.toList()
        }.toList()

        var headerRowIndex = -1
        var columnMap = mapOf<String, Int>()

        for ((index, row) in rows.withIndex()) {
            if (row.isEmpty()) continue

            // Check metadata lines
            val firstCell = row.getOrNull(0) ?: ""
            val secondCell = row.getOrNull(1) ?: ""

            if (firstCell.contains("Panel Name", ignoreCase = true) && secondCell.isNotBlank()) {
                panelName = secondCell
            } else if (firstCell.contains("Panel Tag", ignoreCase = true) && secondCell.isNotBlank()) {
                panelTag = secondCell
            } else if (firstCell.contains("Location", ignoreCase = true) && secondCell.isNotBlank()) {
                location = secondCell
            } else if (firstCell.contains("System Voltage", ignoreCase = true) && secondCell.isNotBlank()) {
                systemVoltage = parseSystemVoltage(secondCell)
            } else if (firstCell.contains("Main Breaker", ignoreCase = true)) {
                mainBreakerAmps = extractInt(secondCell, 63)
            } else if (firstCell.contains("Diversity Factor", ignoreCase = true)) {
                diversityFactor = secondCell.toDoubleOrNull() ?: 0.85
            }

            // Look for table header
            if (row.any { it.contains("Circuit", ignoreCase = true) } &&
                row.any { it.contains("Description", ignoreCase = true) || it.contains("Name", ignoreCase = true) || it.contains("Load", ignoreCase = true) }) {
                headerRowIndex = index
                columnMap = buildColumnMap(row)
                break
            }
        }

        if (headerRowIndex >= 0) {
            for (i in (headerRowIndex + 1) until rows.size) {
                val row = rows[i]
                if (row.isEmpty() || row.all { it.isBlank() }) continue

                val cct = parseCircuitRow(row, columnMap, circuits.size + 1)
                if (cct != null) {
                    circuits.add(cct)
                }
            }
        } else {
            warnings.add("Table header not found in standard format; attempted heuristic parsing.")
            for (row in rows) {
                if (row.size >= 4 && row[0].toIntOrNull() != null) {
                    val cct = parseCircuitRowFallback(row, circuits.size + 1)
                    if (cct != null) circuits.add(cct)
                }
            }
        }

        if (circuits.isEmpty()) {
            return ImportResult(
                success = false,
                fileName = fileName,
                sourceFormat = "Excel XML",
                errorMessage = "No valid electrical circuit rows could be found in the Excel document."
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
            sourceFormat = "Excel XML (.xls)",
            fileName = fileName,
            warnings = warnings
        )
    }

    /**
     * Parses standard CSV format (.csv)
     */
    private fun parseCsv(fileName: String, csv: String): ImportResult {
        val lines = csv.lines().map { it.trim() }.filter { it.isNotBlank() }
        if (lines.isEmpty()) {
            return ImportResult(success = false, fileName = fileName, errorMessage = "The CSV file is empty.")
        }

        var panelName = "Imported CSV Panel"
        var panelTag = "DB-CSV"
        var location = "Main Distribution Room"
        var systemVoltage = SystemVoltage.THREE_PHASE_400_230
        var mainBreakerAmps = 63
        var diversityFactor = 0.85

        val circuits = mutableListOf<CircuitBreaker>()
        val warnings = mutableListOf<String>()

        var headerIndex = -1
        var columnMap = mapOf<String, Int>()

        for ((index, line) in lines.withIndex()) {
            val tokens = splitCsvLine(line)
            if (tokens.isEmpty()) continue

            val first = tokens[0].trim()
            val second = tokens.getOrNull(1)?.trim() ?: ""

            if (first.contains("Panel Name", ignoreCase = true) && second.isNotBlank()) {
                panelName = second
            } else if (first.contains("Panel Tag", ignoreCase = true) && second.isNotBlank()) {
                panelTag = second
            } else if (first.contains("Location", ignoreCase = true) && second.isNotBlank()) {
                location = second
            } else if (first.contains("System Voltage", ignoreCase = true) && second.isNotBlank()) {
                systemVoltage = parseSystemVoltage(second)
            } else if (first.contains("Main Breaker", ignoreCase = true)) {
                mainBreakerAmps = extractInt(second, 63)
            } else if (first.contains("Diversity Factor", ignoreCase = true)) {
                diversityFactor = second.toDoubleOrNull() ?: 0.85
            }

            // Check if this line is header
            if (tokens.any { it.contains("Circuit", ignoreCase = true) } &&
                tokens.any { it.contains("Description", ignoreCase = true) || it.contains("Name", ignoreCase = true) || it.contains("Load", ignoreCase = true) }) {
                headerIndex = index
                columnMap = buildColumnMap(tokens)
                break
            }
        }

        if (headerIndex >= 0) {
            for (i in (headerIndex + 1) until lines.size) {
                val tokens = splitCsvLine(lines[i])
                if (tokens.isEmpty()) continue

                val cct = parseCircuitRow(tokens, columnMap, circuits.size + 1)
                if (cct != null) {
                    circuits.add(cct)
                }
            }
        } else {
            // Heuristic attempt
            warnings.add("Header row was not found; parsed by column positions.")
            for (line in lines) {
                val tokens = splitCsvLine(line)
                if (tokens.size >= 4 && tokens[0].trim().toIntOrNull() != null) {
                    val cct = parseCircuitRowFallback(tokens, circuits.size + 1)
                    if (cct != null) circuits.add(cct)
                }
            }
        }

        if (circuits.isEmpty()) {
            return ImportResult(
                success = false,
                fileName = fileName,
                sourceFormat = "CSV",
                errorMessage = "No valid circuits could be extracted from the CSV file."
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
            sourceFormat = "Excel / CSV",
            fileName = fileName,
            warnings = warnings
        )
    }

    private fun buildColumnMap(headers: List<String>): Map<String, Int> {
        val map = mutableMapOf<String, Int>()
        for ((idx, h) in headers.withIndex()) {
            val lower = h.lowercase().trim()
            when {
                lower.contains("circuit #") || lower == "circuit" || lower == "cct" || lower == "#" || lower == "no" -> map["cct_num"] = idx
                lower.contains("description") || lower.contains("circuit name") || lower == "name" || lower == "load" -> map["name"] = idx
                lower.contains("load type") || lower.contains("type") -> map["type"] = idx
                lower.contains("pole") -> map["poles"] = idx
                lower.contains("phase") -> map["phase"] = idx
                lower.contains("rating") || lower.contains("breaker") || lower.contains("mcb") -> map["rating"] = idx
                lower.contains("curve") -> map["curve"] = idx
                lower.contains("power factor") || lower == "pf" || lower == "cos phi" -> map["pf"] = idx
                lower.contains("power") || lower.contains("watt") || lower == "w" || lower == "kw" -> map["power"] = idx
                lower.contains("cable") || lower.contains("size") || lower.contains("mm²") || lower.contains("mm2") -> map["cable"] = idx
                lower.contains("length") || lower.contains("meter") || lower == "m" -> map["length"] = idx
                lower.contains("rcd") || lower.contains("elcb") || lower.contains("residual") -> map["rcd"] = idx
                lower.contains("status") || lower.contains("state") -> map["status"] = idx
            }
        }
        return map
    }

    private fun parseCircuitRow(row: List<String>, columnMap: Map<String, Int>, defaultNumber: Int): CircuitBreaker? {
        val nameIdx = columnMap["name"] ?: 1
        val name = row.getOrNull(nameIdx)?.trim()
        if (name.isNullOrBlank() || name.equals("TOTAL", ignoreCase = true) || name.contains("SUM", ignoreCase = true)) {
            return null
        }

        val cctNum = columnMap["cct_num"]?.let { row.getOrNull(it)?.toIntOrNull() } ?: defaultNumber
        val typeIdx = columnMap["type"]
        val typeStr = typeIdx?.let { row.getOrNull(it) } ?: ""
        val loadType = parseLoadType(typeStr, name)

        val polesIdx = columnMap["poles"]
        val polesStr = polesIdx?.let { row.getOrNull(it) } ?: "1"
        val poles = extractInt(polesStr, if (name.contains("3P", ignoreCase = true) || name.contains("3-Phase", ignoreCase = true)) 3 else 1).coerceIn(1, 4)

        val phaseIdx = columnMap["phase"]
        val phaseStr = phaseIdx?.let { row.getOrNull(it) } ?: ""
        val assignedPhase = parsePhase(phaseStr, poles, defaultNumber)

        val ratingIdx = columnMap["rating"]
        val ratingStr = ratingIdx?.let { row.getOrNull(it) } ?: "16"
        val ratingAmps = extractInt(ratingStr, 16)

        val curveIdx = columnMap["curve"]
        val curveStr = curveIdx?.let { row.getOrNull(it) } ?: "C"
        val breakerCurve = when {
            curveStr.contains("B", ignoreCase = true) -> BreakerCurve.B
            curveStr.contains("D", ignoreCase = true) -> BreakerCurve.D
            else -> BreakerCurve.C
        }

        val powerIdx = columnMap["power"]
        val powerStr = powerIdx?.let { row.getOrNull(it) } ?: ""
        val powerWatts = parsePowerWatts(powerStr, loadType.defaultPowerW)

        val pfIdx = columnMap["pf"]
        val pfStr = pfIdx?.let { row.getOrNull(it) } ?: ""
        val powerFactor = pfStr.toDoubleOrNull()?.coerceIn(0.5, 1.0) ?: loadType.defaultPf

        val cableIdx = columnMap["cable"]
        val cableStr = cableIdx?.let { row.getOrNull(it) } ?: ""
        val cableMm2 = parseCableSize(cableStr, ratingAmps)

        val lengthIdx = columnMap["length"]
        val lengthStr = lengthIdx?.let { row.getOrNull(it) } ?: ""
        val lengthMeters = extractDouble(lengthStr, 20.0).coerceIn(1.0, 500.0)

        val rcdIdx = columnMap["rcd"]
        val rcdStr = rcdIdx?.let { row.getOrNull(it) } ?: ""
        val hasRcd = rcdStr.contains("Yes", ignoreCase = true) || rcdStr.contains("30", ignoreCase = true) || rcdStr.contains("100", ignoreCase = true) || rcdStr.contains("RCD", ignoreCase = true)
        val rcdRatingMa = extractInt(rcdStr, 30).let { if (it in listOf(10, 30, 100, 300)) it else 30 }

        val statusIdx = columnMap["status"]
        val statusStr = statusIdx?.let { row.getOrNull(it) } ?: "ACTIVE"
        val isOn = !statusStr.contains("OFF", ignoreCase = true) && !statusStr.contains("TRIP", ignoreCase = true)

        return CircuitBreaker(
            id = UUID.randomUUID().toString(),
            circuitNumber = cctNum,
            name = name,
            loadType = loadType,
            poles = poles,
            assignedPhase = assignedPhase,
            ratingAmps = ratingAmps,
            powerWatts = powerWatts,
            powerFactor = powerFactor,
            cableLengthMeters = lengthMeters,
            cableCrossSectionMm2 = cableMm2,
            cableMaterial = CableMaterial.COPPER,
            breakerCurve = breakerCurve,
            hasRcd = hasRcd,
            rcdRatingMa = rcdRatingMa,
            isOn = isOn
        )
    }

    private fun parseCircuitRowFallback(row: List<String>, fallbackNumber: Int): CircuitBreaker? {
        val cctNum = row.getOrNull(0)?.toIntOrNull() ?: fallbackNumber
        val name = row.getOrNull(1)?.trim() ?: "Circuit $cctNum"
        val ratingAmps = extractInt(row.getOrNull(2) ?: "16", 16)
        val powerWatts = extractDouble(row.getOrNull(3) ?: "2000", 2000.0)
        val phase = parsePhase(row.getOrNull(4) ?: "", 1, cctNum)

        return CircuitBreaker(
            id = UUID.randomUUID().toString(),
            circuitNumber = cctNum,
            name = name,
            loadType = parseLoadType("", name),
            ratingAmps = ratingAmps,
            powerWatts = powerWatts,
            assignedPhase = phase
        )
    }

    private fun parseLoadType(typeStr: String, name: String): LoadType {
        val combined = "$typeStr $name".lowercase()
        return when {
            combined.contains("light") || combined.contains("lamp") || combined.contains("led") -> LoadType.LIGHTING
            combined.contains("socket") || combined.contains("receptacle") || combined.contains("outlet") || combined.contains("plug") -> LoadType.SOCKETS
            combined.contains("kitchen") || combined.contains("cooker") || combined.contains("oven") || combined.contains("hob") -> LoadType.KITCHEN
            combined.contains("hvac") || combined.contains("ac") || combined.contains("air cond") || combined.contains("chiller") -> LoadType.HVAC
            combined.contains("water") || combined.contains("heater") || combined.contains("boiler") -> LoadType.WATER_HEATER
            combined.contains("ev") || combined.contains("charger") || combined.contains("vehicle") -> LoadType.EV_CHARGER
            combined.contains("solar") || combined.contains("pv") || combined.contains("inverter") -> LoadType.SOLAR
            combined.contains("pump") || combined.contains("motor") || combined.contains("lift") -> LoadType.MOTOR_PUMP
            combined.contains("feeder") || combined.contains("sub") || combined.contains("board") -> LoadType.SUB_FEEDER
            combined.contains("spare") || combined.contains("provision") -> LoadType.SPARE
            else -> LoadType.SOCKETS
        }
    }

    private fun parsePhase(phaseStr: String, poles: Int, circuitNumber: Int): Phase {
        val upper = phaseStr.uppercase().trim()
        if (poles >= 3 || upper.contains("3P") || upper.contains("3-PHASE") || upper.contains("ALL")) {
            return Phase.THREE_PHASE
        }
        return when {
            upper.contains("L1") || upper.contains("PH1") || upper.contains("PHASE 1") || upper.contains("R") -> Phase.L1
            upper.contains("L2") || upper.contains("PH2") || upper.contains("PHASE 2") || upper.contains("S") || upper.contains("Y") -> Phase.L2
            upper.contains("L3") || upper.contains("PH3") || upper.contains("PHASE 3") || upper.contains("T") || upper.contains("B") -> Phase.L3
            else -> when ((circuitNumber - 1) % 3) {
                0 -> Phase.L1
                1 -> Phase.L2
                else -> Phase.L3
            }
        }
    }

    private fun parsePowerWatts(powerStr: String, defaultW: Double): Double {
        if (powerStr.isBlank()) return defaultW
        val clean = powerStr.replace(",", ".").replace(Regex("[^0-9.]"), "")
        val rawNum = clean.toDoubleOrNull() ?: return defaultW
        return if (powerStr.contains("kW", ignoreCase = true) || (rawNum in 0.1..50.0 && !powerStr.contains("W", ignoreCase = true))) {
            rawNum * 1000.0
        } else {
            rawNum
        }
    }

    private fun parseCableSize(cableStr: String, ratingAmps: Int): Double {
        val clean = cableStr.replace(",", ".").replace(Regex("[^0-9.]"), "")
        val size = clean.toDoubleOrNull()
        if (size != null && size in listOf(1.5, 2.5, 4.0, 6.0, 10.0, 16.0, 25.0, 35.0, 50.0, 70.0, 95.0, 120.0, 150.0, 185.0, 240.0)) {
            return size
        }
        return when {
            ratingAmps <= 10 -> 1.5
            ratingAmps <= 16 -> 2.5
            ratingAmps <= 25 -> 4.0
            ratingAmps <= 32 -> 6.0
            ratingAmps <= 50 -> 10.0
            ratingAmps <= 63 -> 16.0
            else -> 25.0
        }
    }

    private fun parseSystemVoltage(str: String): SystemVoltage {
        return when {
            str.contains("415", ignoreCase = true) -> SystemVoltage.THREE_PHASE_415_240
            str.contains("208", ignoreCase = true) -> SystemVoltage.THREE_PHASE_208_120
            str.contains("1-Phase 230", ignoreCase = true) || str.contains("Single Phase", ignoreCase = true) -> SystemVoltage.SINGLE_PHASE_230
            str.contains("120", ignoreCase = true) -> SystemVoltage.SINGLE_PHASE_120
            else -> SystemVoltage.THREE_PHASE_400_230
        }
    }

    private fun extractInt(str: String, defaultVal: Int): Int {
        val match = Regex("\\d+").find(str)
        return match?.value?.toIntOrNull() ?: defaultVal
    }

    private fun extractDouble(str: String, defaultVal: Double): Double {
        val match = Regex("[0-9]+(?:\\.[0-9]+)?").find(str.replace(",", "."))
        return match?.value?.toDoubleOrNull() ?: defaultVal
    }

    private fun splitCsvLine(line: String): List<String> {
        val delimiter = if (line.contains(";") && !line.contains(",")) ';' else ','
        val tokens = mutableListOf<String>()
        var inQuotes = false
        val sb = StringBuilder()

        for (c in line) {
            when {
                c == '\"' -> inQuotes = !inQuotes
                c == delimiter && !inQuotes -> {
                    tokens.add(sb.toString().trim())
                    sb.clear()
                }
                else -> sb.append(c)
            }
        }
        tokens.add(sb.toString().trim())
        return tokens
    }
}
