package com.naber.app.call

import android.content.Context
import android.media.AudioManager
import com.naber.app.data.ApiClient
import com.naber.app.data.CallInfo
import com.naber.app.data.EventHub
import com.naber.app.data.IceServer
import com.naber.app.data.Signal
import com.naber.app.data.User
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.audio.JavaAudioDeviceModule

enum class CallStage { IDLE, DIALING, INCOMING, CONNECTING, ACTIVE, ENDED }

data class CallUiState(
    val stage: CallStage = CallStage.IDLE,
    val callId: Int = 0,
    val isGroup: Boolean = false,
    val title: String = "",
    val peer: User? = null,
    val participants: List<User> = emptyList(),
    val conversationId: Int = 0,
    val isCaller: Boolean = false,
    val muted: Boolean = false,
    val forceMuted: Boolean = false,
    val speakerOn: Boolean = false,
    val canModerate: Boolean = false,
    val startedAt: Long = 0L,
    val error: String? = null,
    val statusText: String = ""
)

/**
 * Sesli arama yonetimi.
 *
 * Birebir aramada tek, grup aramasinda her katilimci icin ayri bir WebRTC
 * baglantisi kurulur (mesh). Ses dogrudan cihazlar arasinda akar; WordPress
 * yalnizca offer/answer/ICE mesajlarini tasir. P2P kurulamazsa sunucudan
 * gelen TURN bilgileri devreye girer.
 */
class CallManager(
    private val context: Context,
    private val api: ApiClient,
    private val events: EventHub
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val _state = MutableStateFlow(CallUiState())
    val state: StateFlow<CallUiState> = _state.asStateFlow()

    private var factory: PeerConnectionFactory? = null
    private var audioSource: AudioSource? = null
    private var localTrack: AudioTrack? = null
    private var iceServers: List<IceServer> = emptyList()
    private var audioManager: AudioManager? = null
    private var previousAudioMode = AudioManager.MODE_NORMAL
    private var watchJob: Job? = null

    private val peers = HashMap<Int, PeerSession>()
    private val bufferedSignals = mutableListOf<Signal>()
    private var ready = false

    private inner class PeerSession(val userId: Int) {
        var connection: PeerConnection? = null
        var remoteSet = false
        val pending = mutableListOf<IceCandidate>()
    }

    init {
        scope.launch {
            events.signals.collect { handleSignal(it) }
        }
    }

    private fun myId(): Int = 0

    // ------------------------------------------------------------ disari acik

    fun startOutgoing(peer: User) = startCall(peerUser = peer, conversationId = 0, groupTitle = "")

    fun startGroupCall(conversationId: Int, title: String, canModerate: Boolean) =
        startCall(peerUser = null, conversationId = conversationId, groupTitle = title, canModerate = canModerate)

    private fun startCall(peerUser: User?, conversationId: Int, groupTitle: String, canModerate: Boolean = false) {
        val current = _state.value.stage
        if (current != CallStage.IDLE && current != CallStage.ENDED) return

        _state.value = CallUiState(
            stage = CallStage.DIALING,
            isGroup = conversationId > 0 && peerUser == null,
            title = if (peerUser != null) peerUser.displayName else groupTitle,
            peer = peerUser,
            conversationId = conversationId,
            isCaller = true,
            canModerate = canModerate,
            statusText = "Araniyor..."
        )

        scope.launch {
            try {
                val (call, joinedPeers, servers) = api.startCall(
                    userId = peerUser?.id ?: 0,
                    conversationId = conversationId
                )
                iceServers = servers
                applyCall(call)
                prepareAudio()

                if (call.isGroup) {
                    // Gruba katilirken halihazirda baglanti kurmus olanlara teklif gonderilir.
                    joinedPeers.forEach { offerTo(it) }
                    if (joinedPeers.isEmpty()) {
                        _state.value = _state.value.copy(statusText = "Katilimcilar bekleniyor...")
                    }
                } else {
                    offerTo(call.calleeId)
                }

                watchCall(call.id)
            } catch (e: Exception) {
                fail(e.message ?: "Arama baslatilamadi.")
            }
        }
    }

    fun onIncoming(call: CallInfo) {
        val current = _state.value.stage
        if (current != CallStage.IDLE && current != CallStage.ENDED) return
        if (_state.value.callId == call.id) return

        _state.value = CallUiState(
            stage = CallStage.INCOMING,
            callId = call.id,
            isGroup = call.isGroup,
            title = if (call.isGroup) call.groupTitle else (call.caller?.displayName ?: "Bilinmeyen"),
            peer = call.caller,
            participants = call.participants,
            conversationId = call.conversationId,
            isCaller = false,
            statusText = if (call.isGroup) "Grup aramasi" else "Gelen arama"
        )
    }

    fun accept() {
        val current = _state.value
        if (current.stage != CallStage.INCOMING) return
        _state.value = current.copy(stage = CallStage.CONNECTING, statusText = "Baglaniyor...")

        scope.launch {
            try {
                iceServers = api.iceServers()
                prepareAudio()
                val (call, joinedPeers) = api.callAction(current.callId, "accept")
                call?.let { applyCall(it) }
                events.clearIncomingCall()

                // Aramaya sonradan katilan taraf mevcut katilimcilara teklif gonderir.
                joinedPeers.forEach { offerTo(it) }

                // Yayinlanmis ama henuz islenmemis sinyalleri al.
                drainServerSignals(current.callId)
                watchCall(current.callId)
            } catch (e: Exception) {
                fail(e.message ?: "Arama kabul edilemedi.")
            }
        }
    }

    fun reject() {
        val current = _state.value
        scope.launch {
            runCatching { api.callAction(current.callId, "reject") }
            events.clearIncomingCall()
            cleanup("Arama reddedildi")
        }
    }

    fun hangUp() {
        val current = _state.value
        scope.launch {
            runCatching { api.callAction(current.callId, if (current.isGroup) "leave" else "end") }
            events.clearIncomingCall()
            cleanup(if (current.isGroup) "Aramadan ayrildiniz" else "Arama sonlandirildi")
        }
    }

    fun toggleMute() {
        if (_state.value.forceMuted) return
        val muted = !_state.value.muted
        localTrack?.setEnabled(!muted)
        _state.value = _state.value.copy(muted = muted)
    }

    fun toggleSpeaker() {
        val on = !_state.value.speakerOn
        audioManager?.isSpeakerphoneOn = on
        _state.value = _state.value.copy(speakerOn = on)
    }

    /** Grup aramasinda yonetici: katilimciyi susturur / susturmayi kaldirir. */
    fun moderateMute(userId: Int, muted: Boolean) {
        val callId = _state.value.callId
        scope.launch {
            runCatching { api.muteParticipant(callId, userId, muted) }
                .onSuccess { call -> call?.let { applyCall(it) } }
                .onFailure { _state.value = _state.value.copy(error = it.message) }
        }
    }

    /** Grup aramasinda yonetici: katilimciyi sesten atar. */
    fun moderateKick(userId: Int) {
        val callId = _state.value.callId
        scope.launch {
            runCatching { api.kickParticipant(callId, userId) }
                .onSuccess { call ->
                    closePeer(userId)
                    call?.let { applyCall(it) }
                }
                .onFailure { _state.value = _state.value.copy(error = it.message) }
        }
    }

    fun dismiss() {
        if (_state.value.stage == CallStage.ENDED) {
            _state.value = CallUiState()
        }
    }

    // ------------------------------------------------------------ WebRTC

    private fun ensureFactory(): PeerConnectionFactory {
        factory?.let { return it }
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context).createInitializationOptions()
        )
        val audioModule = JavaAudioDeviceModule.builder(context)
            .setUseHardwareAcousticEchoCanceler(true)
            .setUseHardwareNoiseSuppressor(true)
            .createAudioDeviceModule()
        val created = PeerConnectionFactory.builder()
            .setAudioDeviceModule(audioModule)
            .createPeerConnectionFactory()
        factory = created
        return created
    }

    private fun prepareAudio() {
        if (localTrack != null) {
            ready = true
            return
        }
        val pcFactory = ensureFactory()
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
        }
        val source = pcFactory.createAudioSource(constraints)
        audioSource = source
        localTrack = pcFactory.createAudioTrack("naber_audio", source)
        startAudioSession()
        ready = true

        // Hazir olmadan gelen sinyaller simdi islenir.
        val buffered = bufferedSignals.toList()
        bufferedSignals.clear()
        buffered.forEach { handleSignal(it) }
    }

    private fun peerFor(userId: Int): PeerSession {
        peers[userId]?.let { return it }

        val session = PeerSession(userId)
        val rtcServers = iceServers.map { server ->
            PeerConnection.IceServer.builder(server.urls)
                .setUsername(server.username)
                .setPassword(server.credential)
                .createIceServer()
        }.ifEmpty {
            listOf(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer())
        }

        val config = PeerConnection.RTCConfiguration(rtcServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
            rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }

        session.connection = ensureFactory().createPeerConnection(config, object : PeerConnection.Observer {
            override fun onSignalingChange(state: PeerConnection.SignalingState?) {}

            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                scope.launch {
                    when (state) {
                        PeerConnection.IceConnectionState.CONNECTED,
                        PeerConnection.IceConnectionState.COMPLETED -> onConnected()

                        PeerConnection.IceConnectionState.FAILED -> onPeerFailed(userId)

                        else -> Unit
                    }
                }
            }

            override fun onIceConnectionReceivingChange(receiving: Boolean) {}

            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {}

            override fun onIceCandidate(candidate: IceCandidate?) {
                candidate ?: return
                val payload = JSONObject()
                    .put("sdpMid", candidate.sdpMid)
                    .put("sdpMLineIndex", candidate.sdpMLineIndex)
                    .put("candidate", candidate.sdp)
                sendSignal(userId, "ice", payload.toString())
            }

            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}

            override fun onAddStream(stream: MediaStream?) {}

            override fun onRemoveStream(stream: MediaStream?) {}

            override fun onDataChannel(channel: DataChannel?) {}

            override fun onRenegotiationNeeded() {}

            override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {
                scope.launch { onConnected() }
            }
        })

        localTrack?.let { track ->
            session.connection?.addTrack(track, listOf("naber_stream_${myId()}"))
        }

        peers[userId] = session
        return session
    }

    private fun offerTo(userId: Int) {
        if (userId <= 0) return
        prepareAudio()
        val session = peerFor(userId)
        val connection = session.connection ?: return

        connection.createOffer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(description: SessionDescription?) {
                description ?: return
                connection.setLocalDescription(SimpleSdpObserver(), description)
                sendSignal(userId, "offer", JSONObject().put("sdp", description.description).toString())
            }
        }, MediaConstraints())
    }

    private fun handleSignal(signal: Signal) {
        val current = _state.value
        if (current.callId != 0 && signal.callId != current.callId) return
        if (!ready && signal.type != "state") {
            bufferedSignals.add(signal)
            return
        }

        val payload = runCatching { JSONObject(signal.payload) }.getOrElse { JSONObject() }
        val from = signal.senderId

        when (signal.type) {
            "offer" -> {
                val session = peerFor(from)
                val connection = session.connection ?: return
                val sdp = SessionDescription(SessionDescription.Type.OFFER, payload.optString("sdp"))
                connection.setRemoteDescription(object : SimpleSdpObserver() {
                    override fun onSetSuccess() {
                        session.remoteSet = true
                        drainCandidates(session)
                        connection.createAnswer(object : SimpleSdpObserver() {
                            override fun onCreateSuccess(description: SessionDescription?) {
                                description ?: return
                                connection.setLocalDescription(SimpleSdpObserver(), description)
                                sendSignal(from, "answer", JSONObject().put("sdp", description.description).toString())
                            }
                        }, MediaConstraints())
                    }
                }, sdp)
            }

            "answer" -> {
                val session = peers[from] ?: return
                val connection = session.connection ?: return
                val sdp = SessionDescription(SessionDescription.Type.ANSWER, payload.optString("sdp"))
                connection.setRemoteDescription(object : SimpleSdpObserver() {
                    override fun onSetSuccess() {
                        session.remoteSet = true
                        drainCandidates(session)
                    }
                }, sdp)
            }

            "ice" -> {
                val session = peers[from] ?: return
                val candidate = IceCandidate(
                    payload.optString("sdpMid"),
                    payload.optInt("sdpMLineIndex"),
                    payload.optString("candidate")
                )
                if (session.remoteSet) {
                    session.connection?.addIceCandidate(candidate)
                } else {
                    session.pending.add(candidate)
                }
            }

            "state" -> handleStateSignal(payload, from)
        }
    }

    private fun handleStateSignal(payload: JSONObject, from: Int) {
        when (payload.optString("status")) {
            "joined" -> {
                if (_state.value.stage == CallStage.DIALING) {
                    _state.value = _state.value.copy(stage = CallStage.CONNECTING, statusText = "Baglaniyor...")
                }
                refreshCall()
            }

            "force_muted" -> {
                localTrack?.setEnabled(false)
                _state.value = _state.value.copy(
                    muted = true,
                    forceMuted = true,
                    statusText = "Yonetici mikrofonunuzu kapatti"
                )
            }

            "force_unmuted" -> {
                localTrack?.setEnabled(true)
                _state.value = _state.value.copy(muted = false, forceMuted = false, statusText = "")
            }

            "kicked" -> {
                scope.launch { runCatching { api.callAction(_state.value.callId, "leave") } }
                cleanup("Yonetici sizi aramadan cikardi")
            }

            "left", "rejected" -> {
                closePeer(from)
                val userId = payload.optInt("user_id", from)
                closePeer(userId)
                if (!_state.value.isGroup) {
                    cleanup(if (payload.optString("status") == "rejected") "Arama reddedildi" else "Arama sonlandi")
                } else {
                    refreshCall()
                }
            }

            "ended" -> cleanup("Arama sonlandi")
        }
    }

    private fun drainCandidates(session: PeerSession) {
        session.pending.forEach { session.connection?.addIceCandidate(it) }
        session.pending.clear()
    }

    private fun sendSignal(to: Int, type: String, payload: String) {
        val callId = _state.value.callId
        if (callId == 0 || to <= 0) return
        events.launchInScope { api.sendSignal(callId, to, type, payload) }
    }

    private suspend fun drainServerSignals(callId: Int) {
        runCatching {
            val (signals, call, _) = api.callSignals(callId, 0)
            call?.let { applyCall(it) }
            signals.forEach { handleSignal(it) }
        }
    }

    /** Arama durumunu ve katilimci listesini duzenli olarak tazeler. */
    private fun watchCall(callId: Int) {
        watchJob?.cancel()
        watchJob = scope.launch {
            var since = 0
            while (_state.value.callId == callId && _state.value.stage != CallStage.ENDED && _state.value.stage != CallStage.IDLE) {
                delay(2500)
                runCatching {
                    val (signals, call, _) = api.callSignals(callId, since)
                    signals.forEach {
                        since = maxOf(since, it.id)
                        handleSignal(it)
                    }
                    call?.let { info ->
                        applyCall(info)
                        if (!info.isGroup && info.status == "rejected") cleanup("Arama reddedildi")
                        if (info.status == "ended" || info.status == "missed") {
                            if (_state.value.stage != CallStage.ACTIVE || !info.isGroup) cleanup("Arama sonlandi")
                        }
                    }
                }
            }
        }
    }

    private fun refreshCall() {
        val callId = _state.value.callId
        if (callId == 0) return
        scope.launch {
            runCatching { api.callAction(callId, "join") }
                .onSuccess { (call, _) -> call?.let { applyCall(it) } }
        }
    }

    private fun applyCall(call: CallInfo) {
        val me = _state.value
        _state.value = me.copy(
            callId = call.id,
            isGroup = call.isGroup,
            title = if (call.isGroup) call.groupTitle.ifBlank { me.title } else (call.caller?.displayName ?: me.title),
            participants = call.participants.ifEmpty { me.participants },
            conversationId = if (call.conversationId > 0) call.conversationId else me.conversationId
        )
    }

    private fun onConnected() {
        if (_state.value.stage == CallStage.ACTIVE) return
        _state.value = _state.value.copy(
            stage = CallStage.ACTIVE,
            startedAt = System.currentTimeMillis(),
            statusText = ""
        )
    }

    private fun onPeerFailed(userId: Int) {
        closePeer(userId)
        if (_state.value.isGroup) {
            if (peers.isEmpty()) {
                _state.value = _state.value.copy(statusText = "Katilimcilarla baglanti kurulamadi")
            }
            return
        }
        fail("Ses baglantisi kurulamadi. Aginiz dogrudan baglantiya izin vermiyor olabilir; TURN sunucusu tanimli degilse yoneticinizle gorusun.")
    }

    private fun fail(message: String) {
        _state.value = _state.value.copy(stage = CallStage.ENDED, error = message, statusText = message)
        val callId = _state.value.callId
        releaseResources()
        scope.launch { runCatching { api.callAction(callId, "end") } }
    }

    private fun cleanup(reason: String) {
        releaseResources()
        _state.value = _state.value.copy(stage = CallStage.ENDED, statusText = reason)
        scope.launch {
            delay(1400)
            if (_state.value.stage == CallStage.ENDED) _state.value = CallUiState()
        }
    }

    private fun closePeer(userId: Int) {
        peers.remove(userId)?.let { session ->
            runCatching { session.connection?.close() }
        }
    }

    private fun releaseResources() {
        watchJob?.cancel()
        watchJob = null
        peers.values.forEach { runCatching { it.connection?.close() } }
        peers.clear()
        bufferedSignals.clear()
        runCatching { audioSource?.dispose() }
        audioSource = null
        localTrack = null
        ready = false
        stopAudioSession()
    }

    private fun startAudioSession() {
        val manager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        audioManager = manager
        previousAudioMode = manager.mode
        manager.mode = AudioManager.MODE_IN_COMMUNICATION
        manager.isSpeakerphoneOn = false
    }

    private fun stopAudioSession() {
        audioManager?.let {
            it.mode = previousAudioMode
            it.isSpeakerphoneOn = false
        }
        audioManager = null
    }
}

/** Bos gecilen SdpObserver metodlarini tekrar yazmamak icin. */
open class SimpleSdpObserver : SdpObserver {
    override fun onCreateSuccess(description: SessionDescription?) {}
    override fun onSetSuccess() {}
    override fun onCreateFailure(error: String?) {}
    override fun onSetFailure(error: String?) {}
}
