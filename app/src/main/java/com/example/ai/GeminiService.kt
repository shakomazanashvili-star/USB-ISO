package com.example.ai

import com.example.BuildConfig
import com.example.calculation.ElectricalCalculator
import com.example.model.PanelDesign
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

interface GeminiApi {
    @POST("v1beta/models/{model}:generateContent")
    suspend fun generateContent(
        @Path("model") model: String,
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): GeminiResponse
}

object GeminiClient {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    val api: GeminiApi = retrofit.create(GeminiApi::class.java)

    /**
     * Deep Electrical Engineering Audit with High Thinking mode.
     * Uses model 'gemini-3.1-pro-preview' with thinkingLevel = "high" as mandated.
     */
    suspend fun performHighThinkingAudit(panel: PanelDesign, customQuery: String = ""): Result<String> = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext Result.failure(Exception("Gemini API key is not configured. Please add your key in the AI Studio Secrets panel."))
        }

        val summary = ElectricalCalculator.calculatePanel(panel)
        val prompt = buildString {
            appendLine("You are an expert electrical design engineer specializing in IEC 60364 and NEC distribution board design.")
            appendLine("Perform an in-depth engineering audit of this electrical panel layout and calculation results.")
            appendLine()
            appendLine("PANEL PARAMETERS:")
            appendLine("- Name: ${panel.name} (${panel.tag})")
            appendLine("- System: ${panel.systemVoltage.label}")
            appendLine("- Main Breaker: ${panel.mainBreakerAmps}A (${panel.mainBreakerPoles}P)")
            appendLine("- Enclosure: ${panel.enclosureRows} rows, ${panel.modulesPerRow} modules/row (Used: ${panel.usedModules}/${panel.totalEnclosureCapacity})")
            appendLine("- Diversity Factor: ${panel.diversityFactor}")
            appendLine()
            appendLine("CALCULATION RESULTS:")
            appendLine("- Total Connected Load: ${summary.totalConnectedWatts / 1000.0} kW")
            appendLine("- Maximum Demand Load: ${summary.totalDemandWatts / 1000.0} kW (${summary.totalApparentVa / 1000.0} kVA)")
            appendLine("- Main Breaker Loading: ${summary.mainBreakerLoadingPercent}% (Overloaded: ${summary.isPanelOverloaded})")
            appendLine("- Phase Currents: L1: ${summary.phaseL1Amps}A, L2: ${summary.phaseL2Amps}A, L3: ${summary.phaseL3Amps}A")
            appendLine("- Neutral Current: ${summary.neutralCurrentAmps}A")
            appendLine("- Phase Imbalance: ${summary.unbalancePercent}%")
            appendLine()
            appendLine("CIRCUITS (${panel.circuits.size} active):")
            for (c in panel.circuits) {
                val calc = summary.circuitsSummary[c.id]
                appendLine("  Cct ${c.circuitNumber}: ${c.name} | ${c.assignedPhase.shortName} | ${c.ratingAmps}A ${c.breakerCurve} | ${c.powerWatts}W | Current: ${calc?.nominalCurrentAmps}A | Cable: ${c.cableCrossSectionMm2}mm² (${c.cableLengthMeters}m) | VoltDrop: ${calc?.voltageDropPercent}% | RCD: ${c.hasRcd}")
            }
            if (customQuery.isNotBlank()) {
                appendLine()
                appendLine("USER SPECIFIC QUESTION: $customQuery")
            }
            appendLine()
            appendLine("PROVIDE YOUR PROFESSIONAL AUDIT IN 4 STRUCTURED SECTIONS:")
            appendLine("1. Safety & Overload Assessment (Check busbars, main breaker loading, and RCD coverage)")
            appendLine("2. Phase Balancing & Harmonic Neutral Analysis (Evaluate the ${summary.unbalancePercent}% imbalance)")
            appendLine("3. Cable Sizing & Voltage Drop Verification (Flag any voltage drops exceeding standard limits)")
            appendLine("4. Actionable Engineering Recommendations (Specific steps to improve efficiency and safety)")
        }

        try {
            val request = GeminiRequest(
                contents = listOf(
                    GeminiContent(
                        parts = listOf(GeminiPart(text = prompt))
                    )
                ),
                generationConfig = GeminiGenerationConfig(
                    thinkingConfig = GeminiThinkingConfig(thinkingLevel = "high")
                )
            )

            val response = api.generateContent("gemini-3.1-pro-preview", apiKey, request)
            val output = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                ?: "No response generated from model."
            Result.success(output)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Grounded Technical Search with Google Search tool.
     * Uses model 'gemini-3.5-flash' with googleSearch as mandated.
     */
    suspend fun searchGroundingQuery(query: String, panelContext: String = ""): Result<Pair<String, List<String>>> = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext Result.failure(Exception("Gemini API key is not configured."))
        }

        val prompt = if (panelContext.isNotBlank()) {
            "Electrical Panel Context:\n$panelContext\n\nUser Question with Search Grounding:\n$query"
        } else {
            query
        }

        try {
            val request = GeminiRequest(
                contents = listOf(
                    GeminiContent(
                        parts = listOf(GeminiPart(text = prompt))
                    )
                ),
                tools = listOf(
                    GeminiTool(googleSearch = emptyMap())
                )
            )

            val response = api.generateContent("gemini-3.5-flash", apiKey, request)
            val candidate = response.candidates?.firstOrNull()
            val text = candidate?.content?.parts?.firstOrNull()?.text ?: "No response received."
            val webQueries = candidate?.groundingMetadata?.webSearchQueries ?: emptyList()
            Result.success(text to webQueries)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fast Assistant for quick circuit estimation and smart suggestions.
     * Uses model 'gemini-3.1-flash-lite-preview' as mandated.
     */
    suspend fun quickCircuitSuggestion(applianceDescription: String): Result<String> = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext Result.failure(Exception("Gemini API key is not configured."))
        }

        val prompt = """
            Given this electrical appliance or room description: "$applianceDescription"
            Return a brief JSON recommendation for a distribution board circuit:
            - Circuit Name
            - Load Type (LIGHTING, SOCKETS, KITCHEN, HVAC, WATER_HEATER, EV_CHARGER, MOTOR_PUMP, or SPARE)
            - Estimated Power in Watts
            - Power Factor
            - Recommended Breaker Rating in Amps (6, 10, 16, 20, 25, 32, 40, 50, 63)
            - Recommended Cable Cross-Section in mm² (1.5, 2.5, 4.0, 6.0, 10.0)
            - Breaker Curve (B, C, or D)
            Keep it strictly concise and practical.
        """.trimIndent()

        try {
            val request = GeminiRequest(
                contents = listOf(
                    GeminiContent(
                        parts = listOf(GeminiPart(text = prompt))
                    )
                )
            )
            val response = api.generateContent("gemini-3.1-flash-lite-preview", apiKey, request)
            val text = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: ""
            Result.success(text)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
