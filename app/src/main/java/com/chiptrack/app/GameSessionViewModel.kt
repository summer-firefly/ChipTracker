package com.chiptrack.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.chiptrack.app.data.SessionRepository
import com.chiptrack.app.model.ChipRecord
import com.chiptrack.app.model.GameSession
import com.chiptrack.app.model.SessionPhase

class GameSessionViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = SessionRepository(application)

    private val _session = MutableLiveData(GameSession())
    val session: LiveData<GameSession> = _session

    private val _message = MutableLiveData<String?>()
    val message: LiveData<String?> = _message

    init {
        repository.load()?.let { saved ->
            if (saved.phase != SessionPhase.SETUP) {
                _session.value = saved
            }
        }
    }

    fun consumeMessage() {
        _message.value = null
    }

    fun startGame(names: List<String>, buyIn: Int) {
        runCatching {
            persist(GameSession().start(names, buyIn))
        }.onFailure { _message.value = it.message }
    }

    fun take(playerId: String, amount: Int) {
        mutate { it.take(playerId, amount) }
    }

    fun returnPoints(playerId: String, amount: Int) {
        mutate { it.returnPoints(playerId, amount) }
    }

    fun exitPlayer(playerId: String) {
        mutate { it.exitPlayer(playerId) }
    }

    fun addPlayer(name: String, buyIn: Int) {
        mutate { it.addPlayer(name, buyIn) }
    }

    fun finalizeReturnsAndSettle(amounts: Map<String, Int>) {
        mutate { it.finalizeReturnsAndSettle(amounts) }
    }

    fun newGame() {
        repository.clear()
        _session.value = GameSession()
    }

    fun records(playerId: String? = null): List<ChipRecord> {
        val current = _session.value ?: return emptyList()
        return if (playerId == null) current.recordsNewestFirst()
        else current.recordsFor(playerId)
    }

    private fun mutate(block: (GameSession) -> GameSession) {
        val current = _session.value ?: GameSession()
        runCatching {
            persist(block(current))
        }.onFailure { _message.value = it.message }
    }

    private fun persist(next: GameSession) {
        _session.value = next
        repository.save(next)
    }
}
