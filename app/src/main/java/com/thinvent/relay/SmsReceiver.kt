package com.thinvent.relay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            val messageBody = messages.joinToString(separator = "") { it.messageBody }
            val originatingAddress = messages.firstOrNull()?.originatingAddress
            val timestamp = messages.firstOrNull()?.timestampMillis ?: System.currentTimeMillis()

            if (messageBody.isNotEmpty() && originatingAddress != null) {
                CoroutineScope(Dispatchers.IO).launch {
                    LogRepository.addLogMessage("\n\uD83D\uDCAC from '$originatingAddress'.")
                }
                val serviceIntent = Intent(context, ApiForwardingService::class.java).apply {
                    putExtra("messageBody", messageBody)
                    putExtra("originatingAddress", originatingAddress)
                    putExtra("timestamp", timestamp)
                    // threadId will be looked up in the service
                }
                context.startService(serviceIntent)
            }
        }
    }
}
