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
    val peer: User? = null,
    val isCaller: Boolean = false,
    val muted: Boolean = false,
    val speakerOn: Boolean = false,
    val startedAt: Long = 0L,
    val error: String? = null,
    val statusText: String = ""
)

/**
 * Sesli arama yonetimi.
 *
 * Ses trafigi dogrudan WebRTC ile gider; WordPress yalnizca offer/answer/ICE
 * mesajlarini tasiyan signaling gorevini yapar. P2P kurulamazsa sunucudan
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
    private var peerConnection: PeerConnection? = null
    private var audioSource: AudioSource? = null
    private var localTrack: AudioTrack? = null
    private var iceServers: List<IceServer> = emptyList()
    private val pendingCandidates = mutableListOf<IceCandidate>()
    private var remoteDescriptionSet = false
    private var audioManager: AudioManager? = null
    private var previousAudioMode = AudioManager.MODE_NORMAL

    init {
        scope.launch {
            events.signals.collect { handleSignal(it) }
        }
    }

    private fun ensureFactory(): PeerConnectionFactory {
        factory?.let { return it }
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context)
                .createInitializationOptions()
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

    // ------------------------------------------------------------ disari acik

    fun startOutgoing(peer: User) {
        if (_state.value.stage != CallStage.IDLE && _state.value.stage != CallStage.ENDED) return
        _state.value = CallUiState(stage = CallStage.DIALING, peer = peer, isCaller = true, statusText = "Araniyor...")
        scope.launch {
            try {
                val (call, servers) = api.startCall(peer.id)
                iceServers = servers
                _state.value = _state.value.copy(callId = call.id)
                createPeerConnection()
                createOffer(call.id)
                watchCallStatus(call.id)
            } catch (e: Exception) {
                fail(e.message ?: "Arama baslatilamadi.")
            }
        }
    }

    fun onIncoming(call: CallInfo) {
        if (_state.value.stage != CallStage.IDLE && _state.value.stage != CallStage.ENDED) return
        _state.value = CallUiState(
            stage = CallStage.INCOMING,
            callId = call.id,
            peer = call.caller,
            isCaller = false,
            statusText = "Gelen arama"
        )
    }

    fun accept() {
        val current = _state.value
        if (current.stage != CallStage.INCOMING) return
        _state.value = current.copy(stage = CallStage.CONNECTING, statusText = "Baglaniyor...")
        scope.launch {
            try {
                api.callAction(current.callId, "accept")
                iceServers = api.iceServers()
                createPeerConnection()
                // Arayan tarafin offer'i signaling akisindan gelecek.
                events.clearIncomingCall()
                watchCallStatus(current.callId)
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
            runCatching { api.callAction(current.callId, "end") }
            events.clearIncomingCall()
            cleanup("Arama sonlandirildi")
        }
    }

    fun toggleMute() {
        val muted = !_state.value.muted
        localTrack?.setEnabled(!muted)
        _state.value = _state.value.copy(muted = muted)
    }

    fun toggleSpeaker() {
        val on = !_state.value.speakerOn
        audioManager?.isSpeakerphoneOn = on
        _state.value = _state.value.copy(speakerOn = on)
    }

    fun dismissError() {
        if (_state.value.stage == CallStage.ENDED) {
            _state.value = CallUiState()
        }
    }

    // ------------------------------------------------------------ WebRTC

    private fun createPeerConnection() {
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

        val pcFactory = ensureFactory()
        peerConnection = pcFactory.createPeerConnection(config, object : PeerConnection.Observer {
            override fun onSignalingChange(state: PeerConnection.SignalingState?) {}

            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                scope.launch {
                    when (state) {
                        PeerConnection.IceConnectionState.CONNECTED,
                        PeerConnection.IceConnectionState.COMPLETED -> onConnected()
                        PeerConnection.IceConnectionState.FAILED ->
                            fail("Ses baglantisi kurulamadi. Aginiz P2P baglantiya izin vermiyor olabilir; TURN sunucusu tanimli degilse yoneticinizle gorusun.")
                        PeerConnection.IceConnectionState.DISCONNECTED -> {
                            _state.value = _state.value.copy(statusText = "Baglanti zayif...")
                        }
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
                sendSignal("ice", payload.toString())
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

        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
        }
        val source = pcFactory.createAudioSource(constraints)
        audioSource = source
        val track = pcFactory.createAudioTrack("naber_audio", source)
        localTrack = track
        peerConnection?.addTrack(track, listOf("naber_stream"))

        startAudioSession()
    }

    private fun createOffer(callId: Int) {
        val pc = peerConnection ?: return
        pc.createOffer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(description: SessionDescription?) {
                description ?: return
                pc.setLocalDescription(SimpleSdpObserver(), description)
                sendSignal("offer", JSONObject().put("sdp", description.description).toString(), callId)
            }
        }, MediaConstraints())
    }

    private fun handleSignal(signal: Signal) {
        val current = _state.value
        if (current.callId != 0 && signal.callId != current.callId) return
        val payload = runCatching { JSONObject(signal.payload) }.getOrElse { JSONObject() }

        when (signal.type) {
            "offer" -> {
                val pc = peerConnection ?: return
                val sdp = SessionDescription(SessionDescription.Type.OFFER, payload.optString("sdp"))
                pc.setRemoteDescription(object : SimpleSdpObserver() {
                    override fun onSetSuccess() {
                        remoteDescriptionSet = true
                        drainCandidates()
                        pc.createAnswer(object : SimpleSdpObserver() {
                            override fun onCreateSuccess(description: SessionDescription?) {
                                description ?: return
                                pc.setLocalDescription(SimpleSdpObserver(), description)
                                sendSignal("answer", JSONObject().put("sdp", description.description).toString())
                            }
                        }, MediaConstraints())
                    }
                }, sdp)
            }

            "answer" -> {
                val pc = peerConnection ?: return
                val sdp = SessionDescription(SessionDescription.Type.ANSWER, payload.optString("sdp"))
                pc.setRemoteDescription(object : SimpleSdpObserver() {
                    override fun onSetSuccess() {
                        remoteDescriptionSet = true
                        drainCandidates()
                    }
                }, sdp)
            }

            "ice" -> {
                val candidate = IceCandidate(
                    payload.optString("sdpMid"),
                    payload.optInt("sdpMLineIndex"),
                    payload.optString("candidate")
                )
                if (remoteDescriptionSet) {
                    peerConnection?.addIceCandidate(candidate)
                } else {
                    pendingCandidates.add(candidate)
                }
            }

            "state" -> {
                when (payload.optString("status")) {
                    "active" -> if (_state.value.stage == CallStage.DIALING) {
                        _state.value = _state.value.copy(stage = CallStage.CONNECTING, statusText = "Baglaniyor...")
                    }
                    "rejected" -> cleanup("Arama reddedildi")
                    "ended", "missed", "failed" -> cleanup("Arama sonlandi")
                }
            }
        }
    }

    private fun drainCandidates() {
        pendingCandidates.forEach { peerConnection?.addIceCandidate(it) }
        pendingCandidates.clear()
    }

    private fun sendSignal(type: String, payload: String, callId: Int = _state.value.callId) {
        if (callId == 0) return
        events.launchInScope { api.sendSignal(callId, type, payload) }
    }

    /** Arama durumunu (kabul/red) takip eder; signaling gecikirse yedek kontrol. */
    private fun watchCallStatus(callId: Int) {
        scope.launch {
            var since = 0
            while (_state.value.callId == callId &&
                _state.value.stage != CallStage.ENDED &&
                _state.value.stage != CallStage.IDLE
            ) {
                delay(2000)
                runCatching {
                    val (signals, info) = api.callSignals(callId, since)
                    signals.forEach {
                        since = maxOf(since, it.id)
                        handleSignal(it)
                    }
                    when (info?.status) {
                        "rejected" -> cleanup("Arama reddedildi")
                        "ended", "missed" -> if (_state.value.stage != CallStage.ACTIVE) cleanup("Arama sonlandi")
                        else -> Unit
                    }
                }
            }
        }
    }

    private fun onConnected() {
        if (_state.value.stage == CallStage.ACTIVE) return
        _state.value = _state.value.copy(
            stage = CallStage.ACTIVE,
            startedAt = System.currentTimeMillis(),
            statusText = ""
        )
    }

    private fun fail(message: String) {
        _state.value = _state.value.copy(stage = CallStage.ENDED, error = message, statusText = message)
        releaseResources()
        scope.launch {
            runCatching { api.callAction(_state.value.callId, "end") }
        }
    }

    private fun cleanup(reason: String) {
        releaseResources()
        _state.value = _state.value.copy(stage = CallStage.ENDED, statusText = reason)
        scope.launch {
            delay(1200)
            if (_state.value.stage == CallStage.ENDED) _state.value = CallUiState()
        }
    }

    private fun releaseResources() {
        runCatching { peerConnection?.close() }
        peerConnection = null
        runCatching { audioSource?.dispose() }
        audioSource = null
        localTrack = null
        pendingCandidates.clear()
        remoteDescriptionSet = false
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
