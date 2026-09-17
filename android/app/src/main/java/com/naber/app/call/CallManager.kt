package com.naber.app.call

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import com.naber.app.data.ApiClient
import com.naber.app.data.CallInfo
import com.naber.app.data.EventHub
import com.naber.app.data.IceServer
import com.naber.app.data.Session
import com.naber.app.data.Signal
import com.naber.app.data.User
import com.naber.app.push.Notifications
import com.naber.app.push.SoundPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
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
import org.webrtc.RtpSender
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
    // Cagrinin sunucudaki olusturulma zamani (unix saniye); bayat cagriyi elemek icin.
    val createdAt: Long = 0L,
    val error: String? = null,
    val statusText: String = ""
)

/**
 * Sesli arama yonetimi.
 *
 * Her karsi taraf icin tek bir WebRTC baglantisi kurulur. Kim teklif (offer)
 * gonderecegi kullanici kimligine gore belirlenir: kucuk kimlikli taraf teklifi
 * gonderir, digeri bekler. Boylece ayni cift arasinda ikinci bir baglanti
 * acilmaz (ikinci baglanti sesin iki kez gidip gelmesine, yani yankiya yol acar).
 *
 * Ses dogrudan WebRTC ile akar; WordPress yalnizca offer/answer/ICE tasir.
 */
class CallManager(
    private val context: Context,
    private val api: ApiClient,
    private val events: EventHub,
    private val session: Session
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
    // API 26 oncesi surumlerde bu tip bulunmadigi icin alan tipi genel tutulur.
    private var focusRequest: Any? = null
    private var watchJob: Job? = null
    private var resetJob: Job? = null

    private val peers = HashMap<Int, PeerSession>()
    private val bufferedSignals = mutableListOf<Signal>()
    private val handledSignals = HashSet<Int>()
    // Bitmis aramalar tekrar "gelen arama" olarak acilmasin.
    private val finishedCalls = HashSet<Int>()
    private var ready = false
    private var micEnabled = true

    private inner class PeerSession(val userId: Int) {
        var connection: PeerConnection? = null
        var sender: RtpSender? = null
        var remoteSet = false
        var offering = false
        val pending = mutableListOf<IceCandidate>()
    }

    init {
        scope.launch {
            events.signals.collect { handleSignal(it) }
        }
    }

    private fun myId(): Int = session.user?.id ?: 0

    // ------------------------------------------------------------ disari acik

    fun startOutgoing(peer: User) = startCall(peerUser = peer, conversationId = 0, groupTitle = "")

    fun startGroupCall(conversationId: Int, title: String, canModerate: Boolean) =
        startCall(peerUser = null, conversationId = conversationId, groupTitle = title, canModerate = canModerate)

    private fun startCall(peerUser: User?, conversationId: Int, groupTitle: String, canModerate: Boolean = false) {
        val current = _state.value.stage
        if (current != CallStage.IDLE && current != CallStage.ENDED) return

        resetSession()
        resetJob?.cancel()
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
                CallService.start(context, _state.value.title.ifBlank { "Naber" })
                joinedPeers.forEach { maybeOffer(it) }
                if (call.isGroup && joinedPeers.isEmpty()) {
                    _state.value = _state.value.copy(statusText = "Katilimcilar bekleniyor...")
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
        if (call.id in finishedCalls) return
        if (call.status == "ended" || call.status == "missed" || call.status == "rejected") return
        // Kendi baslattigimiz arama bize "gelen arama" olarak donmemeli.
        if (call.callerId > 0 && call.callerId == myId()) {
            finishedCalls.add(call.id)
            return
        }
        // Sunucudan gec gelen ya da uygulama kapaliyken birikmis eski kayitlar
        // "hayalet arama" ekranina yol acar; zil suresi gecmisse hic acma.
        if (isStale(call)) {
            finishedCalls.add(call.id)
            return
        }

        resetSession()
        resetJob?.cancel()
        _state.value = CallUiState(
            stage = CallStage.INCOMING,
            callId = call.id,
            createdAt = call.createdAt,
            isGroup = call.isGroup,
            title = if (call.isGroup) call.groupTitle else (call.caller?.displayName ?: "Bilinmeyen"),
            peer = call.caller,
            participants = call.participants,
            conversationId = call.conversationId,
            isCaller = false,
            statusText = if (call.isGroup) "Grup aramasi" else "Gelen arama"
        )

        if (SoundPlayer.ringtoneAllowed(context)) {
            SoundPlayer.startRingtone(context)
        }
    }

    fun accept() {
        val current = _state.value
        if (current.stage != CallStage.INCOMING) return
        SoundPlayer.stopRingtone()
        Notifications.cancelCall(context)
        _state.value = current.copy(stage = CallStage.CONNECTING, statusText = "Baglaniyor...")
        CallService.start(context, current.title.ifBlank { "Naber" })

        scope.launch {
            try {
                iceServers = api.iceServers()
                prepareAudio()
                val (call, joinedPeers) = api.callAction(current.callId, "accept")
                call?.let { applyCall(it) }
                events.clearIncomingCall()
                joinedPeers.forEach { maybeOffer(it) }
                drainServerSignals(current.callId)
                watchCall(current.callId)
            } catch (e: Exception) {
                fail(e.message ?: "Arama kabul edilemedi.")
            }
        }
    }

    fun reject() {
        val callId = _state.value.callId
        // Ekran hemen kapanir, sunucu bildirimi arkada gider.
        SoundPlayer.stopRingtone()
        cleanup("Arama reddedildi")
        events.clearIncomingCall()
        scope.launch { runCatching { api.callAction(callId, "reject") } }
    }

    fun hangUp() {
        val current = _state.value
        val callId = current.callId
        val action = if (current.isGroup) "leave" else "end"
        cleanup(if (current.isGroup) "Aramadan ayrildiniz" else "Arama sonlandirildi")
        events.clearIncomingCall()
        scope.launch { runCatching { api.callAction(callId, action) } }
    }

    fun toggleMute() {
        if (_state.value.forceMuted) return
        val shouldMute = !_state.value.muted
        setMicEnabled(!shouldMute, userRequested = true)
    }

    private fun setMicEnabled(enabled: Boolean, userRequested: Boolean) {
        micEnabled = enabled
        localTrack?.setEnabled(enabled)
        // Her baglantidaki gondericiye de uygulanir (bazi cihazlarda gerekli).
        peers.values.forEach { session ->
            runCatching { session.sender?.track()?.setEnabled(enabled) }
        }
        audioManager?.isMicrophoneMute = !enabled
        if (userRequested) {
            _state.value = _state.value.copy(muted = !enabled)
        }
    }

    fun toggleSpeaker() {
        val on = !_state.value.speakerOn
        audioManager?.isSpeakerphoneOn = on
        _state.value = _state.value.copy(speakerOn = on)
    }

    /** Grup aramasinda yonetici: katilimciyi susturur / susturmayi kaldirir. */
    fun moderateMute(userId: Int, muted: Boolean) {
        val callId = _state.value.callId
        // Once ekranda goster, sonra sunucuya bildir.
        _state.value = _state.value.copy(
            participants = _state.value.participants.map {
                if (it.id == userId) it.copy(muted = muted) else it
            }
        )
        scope.launch {
            runCatching { api.muteParticipant(callId, userId, muted) }
                .onSuccess { call -> call?.let { applyCall(it) } }
                .onFailure { _state.value = _state.value.copy(error = it.message) }
        }
    }

    /** Grup aramasinda yonetici: katilimciyi sesten atar. */
    fun moderateKick(userId: Int) {
        val callId = _state.value.callId
        closePeer(userId)
        _state.value = _state.value.copy(
            participants = _state.value.participants.map {
                if (it.id == userId) it.copy(callStatus = "kicked") else it
            }
        )
        scope.launch {
            runCatching { api.kickParticipant(callId, userId) }
                .onSuccess { call -> call?.let { applyCall(it) } }
                .onFailure { _state.value = _state.value.copy(error = it.message) }
        }
    }

    fun dismiss() {
        if (_state.value.stage == CallStage.ENDED) {
            resetJob?.cancel()
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

        // Ses oturumu mikrofon acilmadan once ayarlanir; yankı gidericinin
        // dogru calismasi icin MODE_IN_COMMUNICATION onceden verilmeli.
        startAudioSession()

        val pcFactory = ensureFactory()
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation2", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googHighpassFilter", "true"))
        }
        val source = pcFactory.createAudioSource(constraints)
        audioSource = source
        localTrack = pcFactory.createAudioTrack("naber_audio", source).apply { setEnabled(micEnabled) }
        ready = true

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
            session.sender = session.connection?.addTrack(track, listOf("naber_stream"))
        }

        peers[userId] = session
        return session
    }

    /**
     * Ayni cift arasinda tek baglanti olsun diye teklifi kucuk kimlikli taraf gonderir.
     * Digeri "katildi" bildirimini alinca bekler.
     */
    private fun maybeOffer(peerId: Int) {
        if (peerId <= 0 || peerId == myId()) return
        prepareAudio()
        if (myId() < peerId) {
            offerTo(peerId)
        } else {
            // Karsi taraf teklif gonderecek; baglantiyi simdiden hazirla.
            peerFor(peerId)
        }
    }

    private fun offerTo(userId: Int) {
        val session = peerFor(userId)
        if (session.offering || session.remoteSet) return
        val connection = session.connection ?: return
        session.offering = true

        connection.createOffer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(description: SessionDescription?) {
                description ?: return
                connection.setLocalDescription(SimpleSdpObserver(), description)
                sendSignal(userId, "offer", JSONObject().put("sdp", description.description).toString())
            }

            override fun onCreateFailure(error: String?) {
                session.offering = false
            }
        }, MediaConstraints())
    }

    private fun handleSignal(signal: Signal) {
        val current = _state.value
        if (current.callId != 0 && signal.callId != current.callId) return

        // Ayni sinyal hem olay akisindan hem yedek yoklamadan gelebilir.
        if (signal.id > 0 && !handledSignals.add(signal.id)) return

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
        applyParticipants(payload.optJSONArray("participants"))
        val target = payload.optInt("user_id", from)

        when (payload.optString("status")) {
            "joined" -> {
                if (_state.value.stage == CallStage.DIALING) {
                    _state.value = _state.value.copy(stage = CallStage.CONNECTING, statusText = "Baglaniyor...")
                }
                if (target != myId()) maybeOffer(target)
            }

            "participant_updated" -> {
                if (target == myId()) {
                    val muted = payload.optBoolean("muted")
                    setMicEnabled(!muted, userRequested = false)
                    _state.value = _state.value.copy(
                        muted = muted,
                        forceMuted = muted,
                        statusText = if (muted) "Yonetici mikrofonunuzu kapatti" else ""
                    )
                }
            }

            "force_muted" -> {
                setMicEnabled(false, userRequested = false)
                _state.value = _state.value.copy(
                    muted = true,
                    forceMuted = true,
                    statusText = "Yonetici mikrofonunuzu kapatti"
                )
            }

            "force_unmuted" -> {
                setMicEnabled(true, userRequested = false)
                _state.value = _state.value.copy(muted = false, forceMuted = false, statusText = "")
            }

            "kicked" -> {
                if (target == myId()) {
                    cleanup("Yonetici sizi aramadan cikardi")
                }
            }

            "left", "rejected" -> {
                closePeer(target)
                if (!_state.value.isGroup) {
                    cleanup(if (payload.optString("status") == "rejected") "Arama reddedildi" else "Arama sonlandi")
                }
            }

            "ended" -> cleanup("Arama sonlandi")
        }
    }

    private fun applyParticipants(array: JSONArray?) {
        array ?: return
        val list = User.listFrom(array)
        if (list.isEmpty()) return
        _state.value = _state.value.copy(participants = list)

        // Yonetici bizi susturduysa listeden de anlasilir.
        list.firstOrNull { it.id == myId() }?.let { me ->
            if (me.muted && !_state.value.forceMuted) {
                setMicEnabled(false, userRequested = false)
                _state.value = _state.value.copy(muted = true, forceMuted = true, statusText = "Yonetici mikrofonunuzu kapatti")
            } else if (!me.muted && _state.value.forceMuted) {
                setMicEnabled(true, userRequested = false)
                _state.value = _state.value.copy(muted = false, forceMuted = false, statusText = "")
            }
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

    /** Olay akisi kesilirse diye yedek yoklama; sinyaller kimlige gore tekilllenir. */
    private fun watchCall(callId: Int) {
        watchJob?.cancel()
        watchJob = scope.launch {
            var since = 0
            while (_state.value.callId == callId &&
                _state.value.stage != CallStage.ENDED &&
                _state.value.stage != CallStage.IDLE
            ) {
                delay(2000)
                runCatching {
                    val (signals, call, _) = api.callSignals(callId, since)
                    signals.forEach {
                        since = maxOf(since, it.id)
                        handleSignal(it)
                    }
                    call?.let { info ->
                        applyCall(info)
                        when (info.status) {
                            "rejected" -> if (!info.isGroup) cleanup("Arama reddedildi")
                            "ended", "missed" -> cleanup("Arama sonlandi")
                            else -> Unit
                        }
                    }
                }
            }
        }
    }

    private fun applyCall(call: CallInfo) {
        val me = _state.value
        _state.value = me.copy(
            callId = call.id,
            isGroup = call.isGroup,
            title = if (call.isGroup) call.groupTitle.ifBlank { me.title } else (me.title.ifBlank { call.caller?.displayName ?: "" }),
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
        val callId = _state.value.callId
        if (callId > 0) finishedCalls.add(callId)
        releaseResources()
        _state.value = _state.value.copy(stage = CallStage.ENDED, error = message, statusText = message)
        scope.launch { runCatching { api.callAction(callId, "end") } }
        // Hata mesaji ekranda asili kalmasin; okunacak kadar bekleyip kendini temizler.
        scheduleIdleReset(ENDED_RESET_ERROR_MS)
    }

    private fun cleanup(reason: String) {
        if (_state.value.stage == CallStage.IDLE) return
        if (_state.value.callId > 0) finishedCalls.add(_state.value.callId)
        releaseResources()
        _state.value = _state.value.copy(stage = CallStage.ENDED, statusText = reason)
        scheduleIdleReset(ENDED_RESET_MS)
    }

    /**
     * "Arama sonlandi" ekrani belirli bir sure sonra kendiliginden kapanir.
     * Tek is parcasi kullanilir; ust uste cagrilarda eski sayac iptal olur.
     */
    private fun scheduleIdleReset(delayMs: Long) {
        resetJob?.cancel()
        resetJob = scope.launch {
            delay(delayMs)
            if (_state.value.stage == CallStage.ENDED) _state.value = CallUiState()
        }
    }

    /**
     * Uygulama yeniden acildiginda cagrilir.
     *
     * CallManager surec boyunca yasayan tek bir nesnedir; ekran yok edilip
     * yeniden olusturuldugunda eski ENDED durumu hafizada kalir ve uygulamaya
     * her girildiginde "hayalet" bir arama ekrani gorunur. Burada o artik
     * durum temizlenir.
     */
    fun clearStaleState() {
        val current = _state.value
        when (current.stage) {
            CallStage.IDLE -> Unit
            CallStage.ENDED -> {
                resetJob?.cancel()
                _state.value = CallUiState()
            }
            CallStage.INCOMING -> {
                // Cevaplanmamis eski bir cagri; suresi gectiyse dusur.
                if (isStaleTimestamp(current.createdAt)) {
                    SoundPlayer.stopRingtone()
                    if (current.callId > 0) finishedCalls.add(current.callId)
                    resetJob?.cancel()
                    _state.value = CallUiState()
                }
            }
            // Gercekten suren bir arama varsa dokunma.
            else -> Unit
        }
    }

    /**
     * Arama bayat mi (zil suresi gecmis mi)?
     *
     * Once sunucunun hesapladigi yasa bakilir. Telefon saatiyle hesap
     * yapmak guvenilir degil: saati geri kalmis bir telefonda hicbir arama
     * bayat sayilmiyor ve hayalet arama ekrani aciliyordu. Sunucu yasi
     * gondermediyse (eski surum) telefon saatine dusulur; zaman bilgisi
     * hic yoksa bayat sayilir — hicbir sey bilmiyorsak ekrani acmamak
     * dogru taraf.
     */
    private fun isStale(call: CallInfo): Boolean {
        if (call.ageSeconds >= 0) {
            return call.ageSeconds * 1000L > INCOMING_MAX_AGE_MS
        }
        return isStaleTimestamp(call.createdAt)
    }

    /**
     * Elde yalnizca zaman damgasi varken bayatlik karari.
     *
     * Zaman damgasi yoksa bayat sayilir: eskiden burada "bayat degil"
     * deniyordu, bu yuzden zamani bilinmeyen kayitlar arama ekranini
     * aciyordu.
     */
    private fun isStaleTimestamp(createdAtSeconds: Long): Boolean {
        if (createdAtSeconds <= 0L) return true
        val ageMs = System.currentTimeMillis() - createdAtSeconds * 1000L
        return ageMs > INCOMING_MAX_AGE_MS
    }

    private fun closePeer(userId: Int) {
        peers.remove(userId)?.let { session ->
            runCatching { session.connection?.close() }
        }
    }

    private fun resetSession() {
        handledSignals.clear()
        bufferedSignals.clear()
        micEnabled = true
    }

    private fun releaseResources() {
        SoundPlayer.stopRingtone()
        Notifications.cancelCall(context)
        CallService.stop(context)
        watchJob?.cancel()
        watchJob = null
        peers.values.forEach { runCatching { it.connection?.close() } }
        peers.clear()
        bufferedSignals.clear()
        handledSignals.clear()
        runCatching { audioSource?.dispose() }
        audioSource = null
        localTrack = null
        ready = false
        micEnabled = true
        stopAudioSession()
    }

    private fun startAudioSession() {
        val manager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        audioManager = manager
        previousAudioMode = manager.mode

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .build()
            focusRequest = request
            runCatching { manager.requestAudioFocus(request) }
        } else {
            @Suppress("DEPRECATION")
            runCatching {
                manager.requestAudioFocus(null, AudioManager.STREAM_VOICE_CALL, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            }
        }

        manager.mode = AudioManager.MODE_IN_COMMUNICATION
        manager.isMicrophoneMute = false
        // Hoparlor acikken mikrofon hoparloru duyar; varsayilan olarak kulaklik yolu kullanilir.
        manager.isSpeakerphoneOn = false
    }

    private fun stopAudioSession() {
        audioManager?.let { manager ->
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    (focusRequest as? AudioFocusRequest)?.let { manager.abandonAudioFocusRequest(it) }
                } else {
                    @Suppress("DEPRECATION")
                    manager.abandonAudioFocus(null)
                }
            }
            manager.isMicrophoneMute = false
            manager.mode = previousAudioMode
            manager.isSpeakerphoneOn = false
        }
        focusRequest = null
        audioManager = null
    }

    private companion object {
        /** "Arama sonlandi" yazisinin ekranda kalma suresi. */
        const val ENDED_RESET_MS = 1200L
        /** Hata mesaji biraz daha uzun kalsin ki okunabilsin. */
        const val ENDED_RESET_ERROR_MS = 2500L
        /** Bu sureden eski "gelen arama" kayitlari artik gecerli sayilmaz. */
        const val INCOMING_MAX_AGE_MS = 45_000L
    }
}

/** Bos gecilen SdpObserver metodlarini tekrar yazmamak icin. */
open class SimpleSdpObserver : SdpObserver {
    override fun onCreateSuccess(description: SessionDescription?) {}
    override fun onSetSuccess() {}
    override fun onCreateFailure(error: String?) {}
    override fun onSetFailure(error: String?) {}
}
