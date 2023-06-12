package com.tutpro.baresip

import android.content.Context
import com.tutpro.baresip.Api.module_event
import com.tutpro.baresip.Api.ua_register
import org.unifiedpush.android.connector.MessagingReceiver
import org.unifiedpush.android.connector.UnifiedPush.getDistributor

class UnifiedPushReceiver : MessagingReceiver() {
    override fun onMessage(context: Context, message: ByteArray, instance: String) {
        // Called when a new message is received.
        // The message contains the full POST body of the push message.
        val ua: UserAgent? = uaOrNull(instance)
        if(ua == null) {
            Log.w(TAG, """Received UnifiedPush notification for account not found.
                |account: $instance
                |message $message""".trimMargin()
            )
            return
        }
        if (ua_register(ua.uap) != 0)
            Log.e(TAG, """UnifiedPush notification triggered REGISTER failed.
                |account: $instance
                |message $message""".trimMargin()
            )
    }

    override fun onNewEndpoint(context: Context, endpoint: String, instance: String) {
        // Called when a new endpoint is to be used for sending push messages
        // You should send the endpoint to your application server
        // and sync for missing notifications.
        val ua: UserAgent? = uaOrNull(instance)
        if(ua == null) {
            Log.w(TAG, """Received UnifiedPush new endpoint for account not found.
                |account: $instance
                |endpoint $endpoint""".trimMargin()
            )
            return
        }
        pnsConfigUpdate(ua.uap, pnsConfig(getDistributor(context), endpoint))
    }

    override fun onRegistrationFailed(context: Context, instance: String) {
        // called when the registration is not possible, eg. no network
        Log.e(TAG, "UnifiedPush registration failure for $instance, cleaning up.")
        tryClearPnsConfig(instance)
    }

    override fun onUnregistered(context: Context, instance: String){
        // called when this application is unregistered from receiving push messages
        Log.w(TAG, "UnifiedPush registration for $instance was undone, cleaning up.")
        tryClearPnsConfig(instance)
    }

    companion object {
        private fun pnsConfig(provider: String = "", prid: String = "", param: String = "") =
            listOf(provider, prid, param).joinToString(",")

        private fun pnsConfigUpdate(uap: Long, message: String) =
            module_event("pns", "config_update", uap, message)

        fun uaOrNull(instance: String) =
            BaresipService.uas.singleOrNull { it.account.aor == instance }

        fun tryClearPnsConfig(instance: String) = uaOrNull(instance)?.let {
            pnsConfigUpdate(it.uap, pnsConfig())
        }
    }
}