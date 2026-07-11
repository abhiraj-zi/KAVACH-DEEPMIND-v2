package com.example.api

import com.example.BuildConfig
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class GenerateContentRequest(
    val contents: List<Content>,
    val generationConfig: GenerationConfig? = null,
    val systemInstruction: Content? = null
)

@JsonClass(generateAdapter = true)
data class Content(
    val parts: List<Part>
)

@JsonClass(generateAdapter = true)
data class Part(
    val text: String? = null
)

@JsonClass(generateAdapter = true)
data class GenerationConfig(
    val temperature: Float? = null,
    val responseMimeType: String? = null
)

@JsonClass(generateAdapter = true)
data class GenerateContentResponse(
    val candidates: List<Candidate>? = null
)

@JsonClass(generateAdapter = true)
data class Candidate(
    val content: Content? = null
)

interface GeminiService {
    @POST("v1beta/models/gemini-3.5-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GenerateContentRequest
    ): GenerateContentResponse
}

object GeminiClient {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create())
        .build()

    val service: GeminiService = retrofit.create(GeminiService::class.java)

    suspend fun generateIncidentReport(
        triggerType: String,
        transcript: String,
        contactsNotified: String,
        routeTaken: String
    ): String {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return generateMockReport(triggerType, transcript, contactsNotified, routeTaken)
        }

        val prompt = """
            Please write a highly professional, detailed, and structured Emergency Incident Report based on the following details:
            - TRIGGER TYPE: $triggerType
            - AMBIENT TRANSCRIPT / DISTRESS VOICE: "$transcript"
            - EMERGENCY CONTACTS NOTIFIED: $contactsNotified
            - DEFENSE ROUTE TAKEN / ESCAPE PATH: $routeTaken
            
            The report must be formatted clearly with sections:
            1. EXECUTIVE SUMMARY (with incident status: RESOLVED, SAFE)
            2. CHRONOLOGY OF EVENTS (with timestamps and description)
            3. ACTIONS TAKEN & COUNTERMEASURES
            4. INCIDENT LOGS & METADATA
            
            Write in a crisp, professional, objective safety advisor or security dispatch style suitable for campus security, housing officials, or law enforcement record. Keep it realistic, supportive, and clear.
        """.trimIndent()

        val requestBody = GenerateContentRequest(
            contents = listOf(Content(parts = listOf(Part(text = prompt)))),
            systemInstruction = Content(parts = listOf(Part(text = "You are 'Scribe', the emergency incident reporting AI in the Kavach Zero-UI Safety System. You compose highly accurate, objective, professional incident logs for user safety audits and legal records."))),
            generationConfig = GenerationConfig(temperature = 0.4f)
        )

        return try {
            val response = service.generateContent(apiKey, requestBody)
            response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                ?: "Failed to generate structured report text from Gemini response."
        } catch (e: Exception) {
            "Network error generating incident report: ${e.localizedMessage}\n\nFallback Report Details:\n${generateMockReport(triggerType, transcript, contactsNotified, routeTaken)}"
        }
    }

    private fun generateMockReport(
        triggerType: String,
        transcript: String,
        contactsNotified: String,
        routeTaken: String
    ): String {
        return """
            KAVACH INCIDENT AUDIT REPORT
            Status: RESOLVED & SECURED
            
            1. EXECUTIVE SUMMARY
            On ${java.text.DateFormat.getDateTimeInstance().format(java.util.Date())}, the Kavach Autonomous Safety System triggered an emergency response. The host initiated the check-in protocol, which received no response, escalating the status to ACTIVE DEFENSE. The host was guided safely to a pre-configured safe zone. The incident concluded successfully with the host marking themselves as SAFE.
            
            2. CHRONOLOGY OF EVENTS
            - T-00:00: Triggered safety monitoring via $triggerType trigger.
            - T-00:05: Code phrase matched or manual activation initiated. Ambient transcript: "$transcript".
            - T-00:15: CHECKING_IN protocol played voice cue and beep. Silence window expired.
            - T-00:25: ACTIVE_DEFENSE dispatched. Ghost Operator initiated whisper navigation.
            - T-00:30: Emergency contacts notified with current location. Contacts list: $contactsNotified.
            - T-02:15: Safe zone arrived at: $routeTaken.
            - T-02:40: User input: "I'm safe" registered. System de-escalated to RESOLVED.
            
            3. DEFENSE COUNTERMEASURES
            - Dead-Screen Illusion successfully applied to prevent visibility of screen to nearby observers.
            - Real-time turn-by-turn whisper navigation spoken directly into the host's earpiece via TextToSpeech.
            - Secure tracking and backup location reports pushed to emergency contact contacts.
            
            4. SUGGESTED FOLLOW-UP
            - Share this report with local security or authority of $routeTaken.
            - Check device battery levels and ensure contacts are up to date.
        """.trimIndent()
    }
}
