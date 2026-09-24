package com.chiptrack.app.llm

import com.chiptrack.app.BuildConfig

object LlmConfig {
    val baseUrl: String
        get() = BuildConfig.LLM_BASE_URL.trim().trimEnd('/')

    val apiKey: String
        get() = BuildConfig.LLM_API_KEY.trim()

    val model: String
        get() = BuildConfig.LLM_MODEL.trim()

    val isConfigured: Boolean
        get() = baseUrl.isNotBlank() &&
            apiKey.isNotBlank() &&
            apiKey != "your_api_key_here" &&
            model.isNotBlank()
}
