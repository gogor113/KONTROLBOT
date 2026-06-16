package com.example.data.api

import com.example.BuildConfig
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@JsonClass(generateAdapter = true)
data class GenerateContentRequest(
    val contents: List<Content>
)

@JsonClass(generateAdapter = true)
data class Content(
    val parts: List<Part>
)

@JsonClass(generateAdapter = true)
data class Part(
    val text: String
)

@JsonClass(generateAdapter = true)
data class GenerateContentResponse(
    val candidates: List<Candidate>?
)

@JsonClass(generateAdapter = true)
data class Candidate(
    val content: Content?
)

@JsonClass(generateAdapter = true)
data class GridRecommendation(
    val initialLot: Double,
    val stepPoints: Int,
    val recoveryThreshold: Int,
    val expMultiplier: Double,
    val maxSpread: Int,
    val rationale: String
)

interface GeminiApiService {
    @POST("v1beta/models/gemini-3.5-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GenerateContentRequest
    ): GenerateContentResponse
}

object RetrofitClient {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        })
        .build()

    val service: GeminiApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(GeminiApiService::class.java)
    }

    private val recommendationMoshi = Moshi.Builder().build()
    private val recommendationAdapter = recommendationMoshi.adapter(GridRecommendation::class.java)

    /**
     * Calls Gemini to get customized recommendation for GOGOR V12.24 parameters based on inputs.
     */
    suspend fun getOptimizedParameters(
        marketTrend: String, // BULLISH, BEARISH, VOLATILE, RANGE-BOUND
        riskStyle: String,   // Conservative, Moderate, High-Risk
        balance: Double
    ): GridRecommendation = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext GridRecommendation(
                initialLot = if (riskStyle == "Conservative") 0.01 else if (riskStyle == "Moderate") 0.02 else 0.05,
                stepPoints = if (riskStyle == "Conservative") 1300 else 1000,
                recoveryThreshold = 6,
                expMultiplier = 1.8,
                maxSpread = 400,
                rationale = "Demo Mode: API key is missing or default. Simulated optimized parameters are applied for safety."
            )
        }

        val prompt = """
            You are the core algorithmic strategist optimizing the "GOGOR V12.24" MT5 MT4 trading robot.
            The bot uses a Hybrid Layered Grid Strategy with three phases:
            1. Basic Linear grid step (Orders per step = 33)
            2. Pure Linear mode (PureLinearStep)
            3. Exponential Recovery mode (RecoveryThreshold, multiplier applied)
            
            We have a client user profile running GOGOR V12.24 with:
            - Client Account Balance: ${balance} USD
            - Market Trend Environment: ${marketTrend}
            - Chosen Manager Control Style: ${riskStyle}
            
            Based on these inputs, recommend precise values for the following variables:
            1. initialLot: Double (between 0.01 and 1.5, appropriate for ${balance} USD balance and ${riskStyle} risk profile. For conservative small balance < 10k, keep it tightly around 0.01-0.03).
            2. stepPoints: Int (distance between grid entries in points, usually 800 to 2500. Highly volatile needs wider grids, conservative style should use > 1500, extreme aggressive can use around 900).
            3. recoveryThreshold: Int (number of open layers before triggering exponential phase. Range 3 to 12. Larger is safer, e.g. 6 to 8, as it delays risky exponential martingale layering).
            4. expMultiplier: Double (lot multiplier in recovery phase, usually 1.2 to 2.5).
            5. maxSpread: Int (max allowed spread to execute layers, usually 200 to 800 points).
            6. rationale: String (Brief 2-sentence explanation in Indonesian explaining the mathematical logic of the risk setting).

            You MUST respond ONLY with a clean JSON object adhering strictly to the schema outline below. Avoid wrapping the response in markdown blocks like ```json or similar code fences. No extra text, no headers:
            {
              "initialLot": 0.02,
              "stepPoints": 1200,
              "recoveryThreshold": 6,
              "expMultiplier": 1.7,
              "maxSpread": 350,
              "rationale": "Analisis parameter untuk kondisi pasar ..."
            }
        """.trimIndent()

        val request = GenerateContentRequest(
            contents = listOf(Content(parts = listOf(Part(text = prompt))))
        )

        try {
            val response = service.generateContent(apiKey, request)
            val rawText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: ""
            
            // Clean up backticks if any
            val cleanedText = rawText.trim()
                .replace("```json", "")
                .replace("```", "")
                .trim()

            recommendationAdapter.fromJson(cleanedText) ?: throw Exception("JSON Parsing Failed")
        } catch (e: Exception) {
            e.printStackTrace()
            // High-quality fallback values corresponding to user's parameters
            val (lot, points, threshold, multiplier) = when (riskStyle) {
                "Conservative" -> Quadruple(0.01, 1500, 8, 1.5)
                "Moderate" -> Quadruple(0.02, 1111, 5, 2.0)
                else -> Quadruple(0.05, 900, 4, 2.2)
            }
            GridRecommendation(
                initialLot = lot,
                stepPoints = points,
                recoveryThreshold = threshold,
                expMultiplier = multiplier,
                maxSpread = 450,
                rationale = "Koneksi terputus/gagal parse. Fallback otomatis diaktifkan untuk gaya ${riskStyle} (${e.localizedMessage})."
            )
        }
    }
}

data class Quadruple<out A, out B, out C, out D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)
