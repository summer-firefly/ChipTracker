package com.chiptrack.app.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class PlayersPreset(
    val defaultBuyIn: Int? = null,
    val players: List<String> = emptyList()
)

/**
 * 从 assets/players.preset.json 读取本地预制名单。
 * 该文件已被 gitignore；仓库只保留 players.preset.json.example。
 */
object PlayersPresetLoader {
    private const val ASSET_NAME = "players.preset.json"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun load(context: Context): PlayersPreset? {
        return runCatching {
            context.assets.open(ASSET_NAME).bufferedReader().use { reader ->
                val preset = json.decodeFromString<PlayersPreset>(reader.readText())
                val names = preset.players.map { it.trim() }.filter { it.isNotEmpty() }
                if (names.isEmpty()) null
                else preset.copy(players = names)
            }
        }.getOrNull()
    }

    fun exists(context: Context): Boolean =
        runCatching {
            context.assets.open(ASSET_NAME).close()
            true
        }.getOrDefault(false)
}
