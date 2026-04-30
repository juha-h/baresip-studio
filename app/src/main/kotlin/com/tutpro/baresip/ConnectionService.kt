package com.tutpro.baresip

import android.content.Intent
import android.telecom.CallAudioState
import android.telecom.Connection
import android.telecom.ConnectionRequest
import android.telecom.ConnectionService
import android.telecom.DisconnectCause
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.net.Uri
import java.util.concurrent.ConcurrentHashMap

class ConnectionService : ConnectionService() {

    companion object {

        private const val TAG = "BaresipConnection"
        val connections = ConcurrentHashMap<Long, BaresipConnection>()
        var pendingOutgoingConnection: BaresipConnection? = null
        var lastDisconnectTime = 0L

        fun promoteOutgoingConnection(callp: Long) {
            pendingOutgoingConnection?.let {
                it.callp = callp
                connections[callp] = it
                pendingOutgoingConnection = null
            }
        }

        fun onCallClosed(callp: Long) {
            connections[callp]?.let {
                it.setDisconnected(DisconnectCause(DisconnectCause.REMOTE))
                it.destroy()
                connections.remove(callp)
            }
        }

        fun setOutput(callp: Long, speaker: Boolean) {
            connections[callp]?.let {
                Log.d(TAG, "Setting audio route for $callp to speaker=$speaker")
                @Suppress("DEPRECATION")
                val currentRoute = it.callAudioState?.route ?: CallAudioState.ROUTE_EARPIECE
                @Suppress("DEPRECATION")
                if (speaker) {
                    it.setAudioRoute(CallAudioState.ROUTE_SPEAKER)
                } else if (currentRoute == CallAudioState.ROUTE_SPEAKER) {
                    it.setAudioRoute(CallAudioState.ROUTE_EARPIECE)
                }
            }
        }
    }

    override fun onCreateIncomingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ): Connection {
        val extras = request?.extras
        val uap = extras?.getLong("uap") ?: 0L
        val callp = extras?.getLong("callp") ?: 0L
        val peerUri = extras?.getString("peerUri") ?: ""

        Log.d(TAG, "onCreateIncomingConnection for $peerUri")

        val connection = BaresipConnection(uap, callp)
        connections[callp] = connection
        connection.setAddress(Uri.fromParts("sip", peerUri, null), TelecomManager.PRESENTATION_ALLOWED)
        connection.connectionCapabilities = Connection.CAPABILITY_SUPPORT_HOLD or
                Connection.CAPABILITY_HOLD or
                Connection.CAPABILITY_MERGE_CONFERENCE or
                Connection.CAPABILITY_SWAP_CONFERENCE

        val call = Call.ofCallp(callp)
        if (call != null)
            BaresipService.instance?.handleIncomingCall(call)

        val ua = UserAgent.ofUap(uap)
        if (ua != null) {
            if (BaresipService.speakerPhone || BaresipService.speakerPhoneAuto) {
                BaresipService.speakerPhone = true
                @Suppress("DEPRECATION")
                connection.setAudioRoute(CallAudioState.ROUTE_SPEAKER)
                BaresipService.postServiceEvent(
                    ServiceEvent("speaker update,true",
                        arrayListOf(uap, callp),
                        System.nanoTime()
                    )
                )
            }
            if (ua.account.answerMode == Api.ANSWERMODE_AUTO) {
                Log.d(TAG, "Auto-answering call $callp")
                connection.onAnswer()
            } else {
                connection.setRinging()
            }
        }

        return connection
    }

    override fun onCreateIncomingConnectionFailed(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ) {
        Log.e(TAG, "onCreateIncomingConnectionFailed")
    }

    override fun onCreateOutgoingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ): Connection {
        val rootExtras = request?.extras
        val nestedExtras = rootExtras?.getBundle(TelecomManager.EXTRA_OUTGOING_CALL_EXTRAS)

        val uap = rootExtras?.getLong("uap", 0L).takeIf { it != 0L }
            ?: nestedExtras?.getLong("uap") ?: 0L

        val conferenceCall = rootExtras?.getBoolean("conferenceCall", false) ?:
        nestedExtras?.getBoolean("conferenceCall") ?: false

        val onHoldCallp = rootExtras?.getLong("onHoldCallp", 0L).takeIf { it != 0L }
            ?: nestedExtras?.getLong("onHoldCallp") ?: 0L

        val destination = request?.address?.schemeSpecificPart ?: ""

        Log.d(TAG, "onCreateOutgoingConnection to $destination (uap=$uap)")

        val connection = BaresipConnection(uap, 0L)
        pendingOutgoingConnection = connection

        if (BaresipService.speakerPhone || BaresipService.speakerPhoneAuto) {
            BaresipService.speakerPhone = true
            @Suppress("DEPRECATION")
            connection.setAudioRoute(CallAudioState.ROUTE_SPEAKER)
            BaresipService.postServiceEvent(
                ServiceEvent("speaker update,true",
                    arrayListOf(uap, 0L),
                    System.nanoTime()
                )
            )
        }

        connection.setAddress(request?.address, TelecomManager.PRESENTATION_ALLOWED)
        connection.connectionCapabilities = Connection.CAPABILITY_SUPPORT_HOLD or
                Connection.CAPABILITY_HOLD or
                Connection.CAPABILITY_MERGE_CONFERENCE or
                Connection.CAPABILITY_SWAP_CONFERENCE

        // Start the SIP connection logic
        if (uap != 0L) {
            val sipUri = if (destination.startsWith("sip:")) destination else "sip:$destination"
            BaresipService.instance?.runCall(uap, sipUri, conferenceCall, onHoldCallp)
        } else {
            Log.e(TAG, "Cannot start outgoing call: uap is 0")
            connection.setDisconnected(DisconnectCause(DisconnectCause.ERROR, "No Account"))
            connection.destroy()
            pendingOutgoingConnection = null
        }

        connection.setDialing()
        return connection
    }

    override fun onCreateOutgoingConnectionFailed(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ) {
        Log.e(TAG, "onCreateOutgoingConnectionFailed")
        pendingOutgoingConnection = null
    }

    inner class BaresipConnection(val uap: Long, var callp: Long) : Connection() {

        var isDisconnecting = false

        override fun onAnswer() {
            Log.d(TAG, "Telecom Connection onAnswer $callp")
            val answerIntent = Intent(this@ConnectionService, BaresipService::class.java)
            answerIntent.action = "Call Answer"
            answerIntent.putExtra("uap", uap)
            answerIntent.putExtra("callp", callp)
            startService(answerIntent)
            val intent = Intent(this@ConnectionService, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            intent.putExtra("action", "call answer")
            intent.putExtra("callp", callp)
            startActivity(intent)
            BaresipService.instance?.updateStatusNotification()
            setActive()
        }

        override fun onReject() {
            Log.d(TAG, "Telecom Connection onReject $callp")
            Api.ua_hangup(uap, callp, 486, "Rejected")
            setDisconnected(DisconnectCause(DisconnectCause.REJECTED))
            connections.remove(callp)
            destroy()
        }

        override fun onDisconnect() {
            if (isDisconnecting) {
                Log.d(TAG, "onDisconnect already in progress for $callp")
                return
            }

            Log.d(TAG, "Telecom Connection onDisconnect $callp")
            isDisconnecting = true
            lastDisconnectTime = System.currentTimeMillis()

            if (callp == 0L) {
                pendingOutgoingConnection = null
                setDisconnected(DisconnectCause(DisconnectCause.CANCELED))
                destroy()
                return
            }
            val call = Call.ofCallp(callp)
            if (call != null)
                Api.ua_hangup(uap, callp, 0, "")
            setDisconnected(DisconnectCause(DisconnectCause.LOCAL))
            destroy()
            // Allow other disconnects after a short period to prevent the "Telecom Cascade" effect
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                isDisconnecting = false
            }, 500)
        }

        override fun onAbort() {
            Log.d(TAG, "Telecom Connection onAbort $callp")
            if (callp != 0L) {
                Api.ua_hangup(uap, callp, 0, "")
                connections.remove(callp)
            } else {
                pendingOutgoingConnection = null
            }
            setDisconnected(DisconnectCause(DisconnectCause.CANCELED))
            destroy()
        }

        @Deprecated("Deprecated in Java")
        @Suppress("DEPRECATION")
        override fun onCallAudioStateChanged(state: CallAudioState?) {
            if (state == null || isDisconnecting || getState() == STATE_DISCONNECTED) return
            super.onCallAudioStateChanged(state)
            Log.d(TAG, "onCallAudioStateChanged: $state")
            state.let {
                if (BaresipService.isMicMuted != it.isMuted) {
                    BaresipService.isMicMuted = it.isMuted
                    Api.calls_mute(it.isMuted)
                    BaresipService.postServiceEvent(
                        ServiceEvent("mic muted,${it.isMuted}", arrayListOf(uap, callp),
                            System.nanoTime())
                    )
                }
                val isSpeaker = it.route == CallAudioState.ROUTE_SPEAKER
                val call = Call.ofCallp(callp)
                val status = call?.status?.value ?: "idle"
                val hasPendingOrActiveConnection = connections.isNotEmpty() || pendingOutgoingConnection != null
                if (isSpeaker != BaresipService.speakerPhone && (status != "connected" || hasPendingOrActiveConnection)) {
                    if (status != "connected") {
                        Log.d(TAG, "Suppressing speaker update,$isSpeaker during call setup (status=$status, intent=${BaresipService.speakerPhone})")
                        return@let
                    }
                }
                if (status == "connected" && BaresipService.speakerPhone != isSpeaker) {
                    Log.d(TAG, "Syncing speakerPhone variable to hardware state: $isSpeaker")
                    BaresipService.speakerPhone = isSpeaker
                }
                BaresipService.postServiceEvent(
                    ServiceEvent("speaker update,$isSpeaker",
                        arrayListOf(uap, callp),
                        System.nanoTime()
                    )
                )
            }
        }

        override fun onHold() {
            Log.d(TAG, "Telecom Connection onHold $callp")
            val call = Call.ofCallp(callp)
            if (call != null && !call.conferenceCall) {
                // 1. Force SIP Signaling
                if (Api.call_hold(call.callp, true)) {
                    // 2. Sync Call object state
                    call.onhold = true
                    call.callOnHold.value = true
                    call.showOnHoldNotice.value = true
                    // 3. Tell Telecom the move is complete
                    setOnHold()
                } else {
                    Log.e(TAG, "SIP Hold failed for $callp")
                }
            }
        }

        override fun onUnhold() {
            Log.d(TAG, "Telecom Connection onUnhold $callp")
            val call = Call.ofCallp(callp)
            if (call != null && !call.conferenceCall) {
                // 1. Force SIP Signaling
                if (Api.call_hold(call.callp, false)) {
                    // 2. Sync Call object state
                    call.onhold = false
                    call.callOnHold.value = false
                    call.showOnHoldNotice.value = false
                    // 3. Tell Telecom we are active
                    setActive()
                } else {
                    Log.e(TAG, "SIP Resume failed for $callp")
                }
            }
        }

        override fun onPlayDtmfTone(c: Char) {
            Log.d(TAG, "Telecom Connection onPlayDtmfTone $c")
            if (callp != 0L) {
                val call = Call.ofCallp(callp)
                call?.sendDigit(c)
            }
        }
    }
}
