package com.thinvent.relay

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.provider.Telephony
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Handles a single live SMS (from SmsReceiver) and the Test button. The actual
 * decision to send, the throttle, and mark-read all live in RelaySender.
 */
class ApiForwardingService : Service() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val messageBody = intent?.getStringExtra("messageBody")
        val originatingAddress = intent?.getStringExtra("originatingAddress")
        val timestamp = intent?.getLongExtra("timestamp", System.currentTimeMillis())
            ?: System.currentTimeMillis()
        val isTest = intent?.getBooleanExtra("isTest", false) ?: false

        if (messageBody != null && originatingAddress != null) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    // getOrCreateThreadId can throw on some phones (non-default SMS
                    // app, odd number) — never let it crash the app.
                    val threadId = try {
                        Telephony.Threads.getOrCreateThreadId(
                            this@ApiForwardingService, originatingAddress
                        )
                    } catch (e: Exception) {
                        0L
                    }
                    RelaySender.send(
                        context = this@ApiForwardingService,
                        from = originatingAddress,
                        body = messageBody,
                        timestamp = timestamp,
                        threadId = threadId,
                        smsId = null,
                        isTest = isTest
                    )
                } catch (e: Exception) {
                    LogRepository.addLogMessage("Forward failed: ${e.message}")
                } finally {
                    stopSelf(startId)
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
