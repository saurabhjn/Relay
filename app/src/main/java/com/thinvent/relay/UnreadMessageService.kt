package com.thinvent.relay

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.provider.Telephony
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Bulk backfill: sends the specific messages selected in the UI. Each one goes
 * through RelaySender, which filters (skips unwanted), throttles (no 429s),
 * marks read and records it as processed.
 */
class UnreadMessageService : Service() {

    companion object {
        const val ACTION_SEND_SPECIFIC = "ACTION_SEND_SPECIFIC"
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.getStringExtra("action")
        if (action == ACTION_SEND_SPECIFIC) {
            val messageIds = intent.getLongArrayExtra("messageIds")
            if (messageIds != null && messageIds.isNotEmpty()) {
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        sendSpecificMessages(messageIds)
                    } catch (e: Exception) {
                        LogRepository.addLogMessage("Batch send failed: ${e.message}")
                    } finally {
                        stopSelf()
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    private suspend fun sendSpecificMessages(messageIds: LongArray) {
        val selection = "${Telephony.Sms._ID} IN (${messageIds.joinToString(",") { "?" }})"
        val selectionArgs = messageIds.map { it.toString() }.toTypedArray()

        contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI, null, selection, selectionArgs, null
        )?.use {
            LogRepository.addLogMessage("Processing ${it.count} selected messages...")
            val idColumn = it.getColumnIndex(Telephony.Sms._ID)
            val addressColumn = it.getColumnIndex(Telephony.Sms.ADDRESS)
            val bodyColumn = it.getColumnIndex(Telephony.Sms.BODY)
            val dateColumn = it.getColumnIndex(Telephony.Sms.DATE)
            val threadIdColumn = it.getColumnIndex(Telephony.Sms.THREAD_ID)

            while (it.moveToNext()) {
                val messageId = it.getLong(idColumn)
                val address = it.getString(addressColumn)
                val body = it.getString(bodyColumn)
                if (address.isNullOrEmpty() || body.isNullOrEmpty()) continue

                RelaySender.send(
                    context = this@UnreadMessageService,
                    from = address,
                    body = body,
                    timestamp = it.getLong(dateColumn),
                    threadId = it.getLong(threadIdColumn),
                    smsId = messageId
                )
            }
            LogRepository.addLogMessage("Finished the selected batch.")
        } ?: LogRepository.addLogMessage("Could not read the selected messages.")
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
