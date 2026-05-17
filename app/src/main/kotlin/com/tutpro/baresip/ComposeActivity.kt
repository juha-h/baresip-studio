package com.tutpro.baresip

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.core.content.IntentSanitizer

/**
 * Skeletal activity for SEND/SENDTO intents, required for Default SMS App eligibility.
 * Redirects to MainActivity for processing.
 */
class ComposeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Sanitize incoming intent to prevent intent redirection attacks
        val sanitizedIntent = IntentSanitizer.Builder()
            .allowAction(Intent.ACTION_SEND)
            .allowAction(Intent.ACTION_SENDTO)
            .allowType { it.startsWith("text/") }
            .allowData { uri ->
                uri.scheme in listOf("sms", "smsto", "mms", "mmsto")
            }
            .allowExtra(Intent.EXTRA_TEXT) { it is String || it is CharSequence }
            .allowExtra("sms_body") { it is String || it is CharSequence }
            .allowExtra("address") { it is String }
            .allowExtra(Intent.EXTRA_STREAM) { true }
            .allowExtra("exit_on_sent") { it is Boolean }
            .build()
            .sanitizeByFiltering(intent)

        // Redirect to MainActivity which handles dialer/chat UI
        sanitizedIntent.setClass(this, MainActivity::class.java)
        sanitizedIntent.addFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT)
        startActivity(sanitizedIntent)
        finish()
    }
}
