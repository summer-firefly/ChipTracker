package com.chiptrack.app.llm

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class LlmClient(
    private val baseUrl: String = LlmConfig.baseUrl,
    private val apiKey: String = LlmConfig.apiKey,
    private val model: String = LlmConfig.model
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    fun chat(system: String, user: String): String {
        require(LlmConfig.isConfigured) { "未配置大模型 API（请检查 .env）" }

        val body = ChatRequest(
            model = model,
            messages = listOf(
                ChatMessage(role = "system", content = system),
                ChatMessage(role = "user", content = user)
            ),
            temperature = 0.7
        )
        val payload = json.encodeToString(ChatRequest.serializer(), body)
        val request = Request.Builder()
            .url("$baseUrl/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(payload.toRequestBody(JSON))
            .build()

        client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                error("大模型请求失败 HTTP ${response.code}: ${raw.take(240)}")
            }
            val parsed = json.decodeFromString(ChatResponse.serializer(), raw)
            val text = parsed.choices.firstOrNull()?.message?.content?.trim().orEmpty()
            if (text.isEmpty()) error("大模型返回空内容")
            return text
        }
    }

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }
}

@Serializable
private data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val temperature: Double = 0.7
)

@Serializable
private data class ChatMessage(
    val role: String,
    val content: String
)

@Serializable
private data class ChatResponse(
    val choices: List<ChatChoice> = emptyList()
)

@Serializable
private data class ChatChoice(
    val message: ChatMessageContent? = null
)

@Serializable
private data class ChatMessageContent(
    val content: String? = null,
    @SerialName("role") val role: String? = null
)
