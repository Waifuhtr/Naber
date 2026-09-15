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
 * WordPress paylasimli hostinglerinde WebSocket calismadigi icin uzun yoklama
 * kullanilir: tek istek sunucuda en fazla 25 saniye bekler, yeni bir sey
 * olunca hemen doner.
 */
class EventHub(private val api: ApiClient) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    private val _messages = MutableSharedFlow<Message>(extraBufferCapacity = 64)
    val messages: SharedFlow<Message> = _messages.asSharedFlow()

    private val _signals = MutableSharedFlow<Signal>(extraBufferCapacity = 64)
    val signals: SharedFlow<Signal> = _signals.asSharedFlow()

    private val _readStates = MutableSharedFlow<List<ReadState>>(extraBufferCapacity = 16)
    val readStates: SharedFlow<List<ReadState>> = _readStates.asSharedFlow()

    private val _typing = MutableStateFlow(TypingState())
    val typing: StateFlow<TypingState> = _typing.asStateFlow()

    private val _presence = MutableStateFlow<Map<Int, Presence>>(emptyMap())
    val presence: StateFlow<Map<Int, Presence>> = _presence.asStateFlow()

    private val _revisions = MutableStateFlow<Map<Int, Long>>(emptyMap())
    val revisions: StateFlow<Map<Int, Long>> = _revisions.asStateFlow()

    private val _incomingCall = MutableStateFlow<CallInfo?>(null)
    val incomingCall: StateFlow<CallInfo?> = _incomingCall.asStateFlow()

    private val _unreadTotal = MutableStateFlow(0)
    val unreadTotal: StateFlow<Int> = _unreadTotal.asStateFlow()

    private val _connected = MutableStateFlow(true)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    /** Acik olan sohbet; "yaziyor" bilgisi bu sohbet icin istenir. */
    @Volatile
    var activeConversationId: Int = 0

    private var sinceMessageId = 0
    private var sinceSignalId = 0
    private var typingSignature = ""
    private var presenceSignature = ""
    private var revisionSignature = ""

    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            while (isActive) {
                try {
                    val conversationId = activeConversationId.takeIf { it > 0 }
                    val batch = api.events(
                        sinceMessageId = sinceMessageId,
                        sinceSignalId = sinceSignalId,
                        conversationId = conversationId,
                        typingSignature = typingSignature,
                        presenceSignature = presenceSignature,
                        revisionSignature = revisionSignature
                    )
                    _connected.value = true
                    sinceMessageId = maxOf(sinceMessageId, batch.sinceMessageId)
                    sinceSignalId = maxOf(sinceSignalId, batch.sinceSignalId)
                    typingSignature = batch.typingSignature
                    presenceSignature = batch.presenceSignature
                    revisionSignature = batch.revisionSignature
                    _unreadTotal.value = batch.unreadTotal
                    batch.messages.forEach { _messages.emit(it) }
                    batch.signals.forEach { _signals.emit(it) }
                    if (batch.readStates.isNotEmpty()) _readStates.emit(batch.readStates)
                    _typing.value = TypingState(
                        conversationId = batch.typingConversationId,
                        users = batch.typing,
                        at = System.currentTimeMillis()
                    )
                    if (batch.presence.isNotEmpty()) {
                        _presence.value = batch.presence.associateBy { it.id }
                    }
                    if (batch.revisions.isNotEmpty()) {
                        _revisions.value = batch.revisions.associate { it.id to it.updatedAt }
                    }
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

    fun reset() {
        stop()
        sinceMessageId = 0
        sinceSignalId = 0
        typingSignature = ""
        presenceSignature = ""
        revisionSignature = ""
        _unreadTotal.value = 0
        _incomingCall.value = null
        _typing.value = TypingState()
        _presence.value = emptyMap()
        _revisions.value = emptyMap()
    }

    /** Sohbet degistiginde "yaziyor" bilgisi hemen sifirlanir. */
    fun clearTyping() {
        _typing.value = TypingState()
        typingSignature = ""
    }

    fun launchInScope(block: suspend () -> Unit) {
        scope.launch { runCatching { block() } }
    }
}
