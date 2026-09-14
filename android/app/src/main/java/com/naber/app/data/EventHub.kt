package com.naber.app.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Canli olay akisi.
 *
 * WordPress paylasimli hostinglerde WebSocket calismadigi icin uzun yoklama
 * (long-polling) kullanilir: tek bir istek sunucuda en fazla 25 saniye bekler,
 * yeni bir sey olunca hemen doner. Boylece surekli istek yagmuru olmaz ama
 * mesajlar aninda gelir.
 */
class EventHub(private val api: ApiClient) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    private val _messages = MutableSharedFlow<Message>(extraBufferCapacity = 64)
    val messages: SharedFlow<Message> = _messages.asSharedFlow()

    private val _signals = MutableSharedFlow<Signal>(extraBufferCapacity = 64)
    val signals: SharedFlow<Signal> = _signals.asSharedFlow()

    private val _readReceipts = MutableSharedFlow<List<Int>>(extraBufferCapacity = 16)
    val readReceipts: SharedFlow<List<Int>> = _readReceipts.asSharedFlow()

    private val _typing = MutableStateFlow(0 to false)
    val typing: StateFlow<Pair<Int, Boolean>> = _typing.asStateFlow()

    private val _incomingCall = MutableStateFlow<CallInfo?>(null)
    val incomingCall: StateFlow<CallInfo?> = _incomingCall.asStateFlow()

    private val _unreadTotal = MutableStateFlow(0)
    val unreadTotal: StateFlow<Int> = _unreadTotal.asStateFlow()

    private val _connected = MutableStateFlow(true)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    /** Acik olan sohbet; sunucudan "yaziyor" bilgisi bu sohbet icin istenir. */
    @Volatile
    var activeConversationId: Int = 0

    private var sinceMessageId = 0
    private var sinceSignalId = 0

    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            while (isActive) {
                try {
                    val batch = api.events(sinceMessageId, sinceSignalId, activeConversationId.takeIf { it > 0 })
                    _connected.value = true
                    sinceMessageId = maxOf(sinceMessageId, batch.sinceMessageId)
                    sinceSignalId = maxOf(sinceSignalId, batch.sinceSignalId)
                    _unreadTotal.value = batch.unreadTotal
                    batch.messages.forEach { _messages.emit(it) }
                    batch.signals.forEach { _signals.emit(it) }
                    if (batch.readMessageIds.isNotEmpty()) _readReceipts.emit(batch.readMessageIds)
                    _typing.value = batch.typingConversationId to batch.typing
                    _incomingCall.value = batch.incomingCall
                } catch (e: Exception) {
                    _connected.value = false
                    delay(3000)
                }
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    fun clearIncomingCall() {
        _incomingCall.value = null
    }

    /** Oturum degistiginde sayaclari sifirla. */
    fun reset() {
        stop()
        sinceMessageId = 0
        sinceSignalId = 0
        _unreadTotal.value = 0
        _incomingCall.value = null
    }

    fun launchInScope(block: suspend () -> Unit) {
        scope.launch { runCatching { block() } }
    }
}
