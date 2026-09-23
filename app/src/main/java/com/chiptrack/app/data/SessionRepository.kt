package com.chiptrack.app.data

import android.content.Context
import com.chiptrack.app.model.GameSession
import com.chiptrack.app.model.SessionPhase
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class SessionRepository(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun load(): GameSession? {
        val raw = prefs.getString(KEY_SESSION, null) ?: return null
        return runCatching { json.decodeFromString<GameSession>(raw) }.getOrNull()
    }

    fun save(session: GameSession) {
        if (session.phase == SessionPhase.SETUP && session.players.isEmpty()) {
            clear()
            return
        }
        prefs.edit().putString(KEY_SESSION, json.encodeToString(session)).apply()
    }

    fun clear() {
        prefs.edit().remove(KEY_SESSION).apply()
    }

    companion object {
        private const val PREFS_NAME = "chiptrack_session"
        private const val KEY_SESSION = "session_json"
    }
}
