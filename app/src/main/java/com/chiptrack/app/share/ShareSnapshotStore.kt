package com.chiptrack.app.share

import android.content.Context
import com.chiptrack.app.model.GameSession
import com.chiptrack.app.model.Player
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class PlayerShareSnap(
    val taken: Int,
    val returned: Int
)

@Serializable
data class ShareSnapshot(
    val players: Map<String, PlayerShareSnap> = emptyMap()
)

data class PlayerShareDelta(
    val player: Player,
    val deltaTaken: Int,
    val deltaReturned: Int,
    val isNew: Boolean
) {
    /** 净变动：拿取增加为正，退还增加为负 */
    val deltaNet: Int get() = deltaTaken - deltaReturned

    val hasChange: Boolean
        get() = isNew || deltaTaken != 0 || deltaReturned != 0
}

/**
 * 记录上一次分享时的每人累计拿取/退还，用于下次分享标出变化。
 */
class ShareSnapshotStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun load(): ShareSnapshot? {
        val raw = prefs.getString(KEY_SNAPSHOT, null) ?: return null
        return runCatching { json.decodeFromString<ShareSnapshot>(raw) }.getOrNull()
    }

    fun saveFromSession(session: GameSession) {
        val snap = ShareSnapshot(
            players = session.players.associate { p ->
                p.id to PlayerShareSnap(p.totalTaken, p.totalReturned)
            }
        )
        prefs.edit().putString(KEY_SNAPSHOT, json.encodeToString(snap)).apply()
    }

    fun clear() {
        prefs.edit().remove(KEY_SNAPSHOT).apply()
    }

    fun deltasFor(session: GameSession): List<PlayerShareDelta> {
        val prev = load()?.players.orEmpty()
        if (prev.isEmpty()) return emptyList()
        return session.players.map { player ->
            val old = prev[player.id]
            if (old == null) {
                PlayerShareDelta(
                    player = player,
                    deltaTaken = player.totalTaken,
                    deltaReturned = player.totalReturned,
                    isNew = true
                )
            } else {
                PlayerShareDelta(
                    player = player,
                    deltaTaken = player.totalTaken - old.taken,
                    deltaReturned = player.totalReturned - old.returned,
                    isNew = false
                )
            }
        }
    }

    companion object {
        private const val PREFS_NAME = "chiptrack_share"
        private const val KEY_SNAPSHOT = "last_share_json"
    }
}
