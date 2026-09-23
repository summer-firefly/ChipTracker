package com.chiptrack.app.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class ChipRecord(
    val id: String = UUID.randomUUID().toString(),
    val playerId: String,
    val playerName: String,
    val action: ChipAction,
    val amount: Int,
    val takenAfter: Int,
    val returnedAfter: Int,
    val timestamp: Long = System.currentTimeMillis()
)
