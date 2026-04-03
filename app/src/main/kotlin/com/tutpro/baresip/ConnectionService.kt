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

    private val TAG = "BaresipConnection"

    companion object {
        val connections = ConcurrentHashMap<Long, BaresipConnection>()
        var pendingOutgoingConnection: BaresipConnection? = null

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
        connection.connectionCapabilities = Connection.CAPABILITY_SUPPORT_HOLD or Connection.CAPABILITY_HOLD

        val call = Call.ofCallp(callp)
        if (call != null)
            BaresipService.instance?.handleIncomingCall(call)
        
        val ua = UserAgent.ofUap(uap)
        if (ua != null) {
            // Check speakerphone setting
            if (BaresipService.speakerPhone) {
                @Suppress("DEPRECATION")
                connection.setAudioRoute(CallAudioState.ROUTE_SPEAKER)
            }

            // Check for Auto-Answer
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

        // Try root extras first, then fallback to nested
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

        if (BaresipService.speakerPhone) {
            @Suppress("DEPRECATION")
            connection.setAudioRoute(CallAudioState.ROUTE_SPEAKER)
        }

        connection.setAddress(request?.address, TelecomManager.PRESENTATION_ALLOWED)
        connection.connectionCapabilities = Connection.CAPABILITY_SUPPORT_HOLD or Connection.CAPABILITY_HOLD

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

        override fun onAnswer() {
            Log.d(TAG, "Telecom Connection onAnswer $callp")
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
            Log.d(TAG, "Telecom Connection onDisconnect $callp")
            if (callp != 0L) {
                Api.ua_hangup(uap, callp, 0, "")
                connections.remove(callp)
            } else {
                pendingOutgoingConnection = null
            }
            setDisconnected(DisconnectCause(DisconnectCause.LOCAL))
            destroy()
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
            super.onCallAudioStateChanged(state)
            Log.d(TAG, "onCallAudioStateChanged: $state")
            state?.let {
                if (BaresipService.isMicMuted != it.isMuted) {
                    BaresipService.isMicMuted = it.isMuted
                    Api.calls_mute(it.isMuted)
                    BaresipService.postServiceEvent(
                        ServiceEvent("mic muted,${it.isMuted}", arrayListOf(uap, callp),
                            System.nanoTime())
                    )
                }
                val isSpeaker = it.route == CallAudioState.ROUTE_SPEAKER
                BaresipService.postServiceEvent(
                    ServiceEvent("speaker,${isSpeaker}", arrayListOf(uap, callp), System.nanoTime())
                )
            }
        }

        override fun onHold() {
            Log.d(TAG, "Telecom Connection onHold $callp")
            val c = Call.ofCallp(callp)
            if (c?.conferenceCall != true)
                c?.hold()
            setOnHold()
        }

        override fun onUnhold() {
            Log.d(TAG, "Telecom Connection onUnhold $callp")
            val c = Call.ofCallp(callp)
            if (c?.conferenceCall != true)
                c?.resume()
            setActive()
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
