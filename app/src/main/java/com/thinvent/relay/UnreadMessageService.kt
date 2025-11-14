package com.thinvent.relay

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.provider.Telephony
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.security.cert.X509Certificate
import javax.net.ssl.X509TrustManager

class UnreadMessageService : Service() {

    companion object {
        const val ACTION_SEND_SPECIFIC = "ACTION_SEND_SPECIFIC"
    }

    private val client = HttpClient(CIO) {
        engine {
            https {
                trustManager = object : X509TrustManager {
                    override fun checkClientTrusted(p0: Array<out X509Certificate>?, p1: String?) {}
                    override fun checkServerTrusted(p0: Array<out X509Certificate>?, p1: String?) {}
                    override fun getAcceptedIssuers(): Array<X509Certificate>? = null
                }
            }
        }
        install(ContentNegotiation) {
            json()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.getStringExtra("action")

        if (action == ACTION_SEND_SPECIFIC) {
            val messageIds = intent.getLongArrayExtra("messageIds")
            if (messageIds != null && messageIds.isNotEmpty()) {
                CoroutineScope(Dispatchers.IO).launch {
                    sendSpecificMessages(messageIds)
                    stopSelf()
                }
            }
        }

        return START_NOT_STICKY
    }

    private suspend fun sendSpecificMessages(messageIds: LongArray) {
        val url = ServerRepository.currentUrl.first()

        // Create a selection string for the query, e.g., "_id IN (?, ?, ?)"
        val selection = "${Telephony.Sms._ID} IN (${messageIds.joinToString(",") { "?" }})"
        val selectionArgs = messageIds.map { it.toString() }.toTypedArray()

        val cursor = contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            null,
            selection,
            selectionArgs,
            null
        )

        cursor?.use {
            LogRepository.addLogMessage("Processing ${it.count} selected messages.")

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

                val timestamp = it.getLong(dateColumn)
                val threadId = it.getLong(threadIdColumn)

                LogRepository.addLogMessage("Sending message from '$address'...")
                try {
                    val response = client.post(url) {
                        contentType(ContentType.Application.Json)
                        setBody(SmsData(address, body, timestamp, threadId))
                    }

                    if (response.status == HttpStatusCode.OK) {
                        LogRepository.addLogMessage("Successfully sent message from '$address'.")
                        ProcessedMessagesRepository.addProcessedMessage(this@UnreadMessageService, messageId)
                    } else {
                        LogRepository.addLogMessage("API call failed for '$address' with status: ${response.status}")
                    }
                } catch (e: Exception) {
                    LogRepository.addLogMessage("Error sending message from '$address': ${e.message}")
                }
            }
        } ?: LogRepository.addLogMessage("Could not query SMS content provider for selected messages.")
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
