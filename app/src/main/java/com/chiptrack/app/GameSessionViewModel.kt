package com.chiptrack.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.chiptrack.app.data.SessionRepository
import com.chiptrack.app.llm.GameReportGenerator
import com.chiptrack.app.llm.LlmConfig
import com.chiptrack.app.model.ChipRecord
import com.chiptrack.app.model.GameSession
import com.chiptrack.app.model.SessionPhase
import com.chiptrack.app.share.ShareSnapshotStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class AiReportUiState {
    data object Idle : AiReportUiState()
    data object Disabled : AiReportUiState()
    data object Loading : AiReportUiState()
    data class Ready(val text: String) : AiReportUiState()
    data class Error(val message: String) : AiReportUiState()
}

class GameSessionViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = SessionRepository(application)
    private val shareSnapshotStore = ShareSnapshotStore(application)

    private val _session = MutableLiveData(GameSession())
    val session: LiveData<GameSession> = _session

    private val _message = MutableLiveData<String?>()
    val message: LiveData<String?> = _message

    private val _aiReport = MutableLiveData<AiReportUiState>(
        if (LlmConfig.isConfigured) AiReportUiState.Idle else AiReportUiState.Disabled
    )
    val aiReport: LiveData<AiReportUiState> = _aiReport

    private var reportJob: Job? = null

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
            shareSnapshotStore.clear()
            clearAiReport()
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
        clearAiReport()
    }

    fun newGame() {
        repository.clear()
        shareSnapshotStore.clear()
        clearAiReport()
        _session.value = GameSession()
    }

    fun records(playerId: String? = null): List<ChipRecord> {
        val current = _session.value ?: return emptyList()
        return if (playerId == null) current.recordsNewestFirst()
        else current.recordsFor(playerId)
    }

    fun currentAiReportText(): String? =
        (_aiReport.value as? AiReportUiState.Ready)?.text

    fun generateAiReport(force: Boolean = false) {
        if (!LlmConfig.isConfigured) {
            _aiReport.value = AiReportUiState.Disabled
            return
        }
        val session = _session.value
        if (session == null || session.phase != SessionPhase.SETTLED) return
        if (!force && _aiReport.value is AiReportUiState.Ready) return
        if (_aiReport.value is AiReportUiState.Loading) return

        reportJob?.cancel()
        _aiReport.value = AiReportUiState.Loading
        reportJob = viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { GameReportGenerator.generate(session) }
            }
            result
                .onSuccess { _aiReport.value = AiReportUiState.Ready(it) }
                .onFailure {
                    _aiReport.value = AiReportUiState.Error(
                        it.message?.take(160) ?: "生成报告失败"
                    )
                }
        }
    }

    private fun clearAiReport() {
        reportJob?.cancel()
        reportJob = null
        _aiReport.value =
            if (LlmConfig.isConfigured) AiReportUiState.Idle else AiReportUiState.Disabled
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
