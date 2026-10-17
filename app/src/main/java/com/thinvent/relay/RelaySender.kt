package com.thinvent.relay

import android.content.ContentValues
import android.content.Context
import android.provider.Telephony
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The single place every SMS forward goes through (live receive, bulk backfill,
 * and test). It:
 *   1. FILTERS on the phone — only messages matching the server rules are sent;
 *      everything else stays on the device.
 *   2. THROTTLES — serialises sends at least MIN_INTERVAL_MS apart so a bulk
 *      backfill never trips the server's rate limit (30/min) and gets 429s.
 *   3. On success, MARKS the SMS read and records it as processed.
 */
object RelaySender {

    // Server allows ~30 req/min (burst 10). Serialise at 2.5s apart => ~24/min,
    // comfortably under the limit even during a big backfill.
    private const val MIN_INTERVAL_MS = 2500L

    private val gate = Mutex()
    @Volatile private var lastSentAt = 0L

    private val client by lazy {
        HttpClient(CIO) { install(ContentNegotiation) { json() } }
    }

    /**
     * Forward one SMS if it passes the filter. Returns true if actually sent.
     * @param smsId inbox _id when known (bulk) — used to mark it read reliably.
     * @param isTest bypasses the filter (the Test button).
     */
    suspend fun send(
        context: Context,
        from: String,
        body: String,
        timestamp: Long,
        threadId: Long,
        smsId: Long? = null,
        isTest: Boolean = false
    ): Boolean {
        if (!isTest && !MessageFilter.shouldForward(context, from, body)) {
            LogRepository.addLogMessage("⛔ kept on phone (not a wanted message) from '$from'.")
            return false
        }

        gate.withLock {
            val wait = MIN_INTERVAL_MS - (System.currentTimeMillis() - lastSentAt)
            if (wait > 0) delay(wait)
            try {
                val url = ServerRepository.currentUrl.first()
                LogRepository.addLogMessage("↗ '$from' → server")
                val resp = client.post(url) {
                    contentType(ContentType.Application.Json)
                    if (ServerRepository.API_KEY.isNotEmpty()) {
                        header("X-API-Key", ServerRepository.API_KEY)
                    }
                    setBody(SmsData(from, body, timestamp, threadId))
                }
                lastSentAt = System.currentTimeMillis()
                return if (resp.status == HttpStatusCode.OK) {
                    LogRepository.addLogMessage("✅")
                    if (smsId != null) {
                        ProcessedMessagesRepository.addProcessedMessage(context, smsId)
                        markRead(context, smsId)
                    } else {
                        markReadByContent(context, from, body)
                    }
                    true
                } else {
                    LogRepository.addLogMessage("server said ${resp.status}")
                    false
                }
            } catch (e: Exception) {
                lastSentAt = System.currentTimeMillis()
                LogRepository.addLogMessage("send error '$from': ${e.message}")
                return false
            }
        }
    }

    /** Mark a specific SMS row read. Best-effort: on modern Android the OS only
     *  honours this if the relay is the default SMS app; otherwise it's ignored. */
    private suspend fun markRead(context: Context, smsId: Long) {
        try {
            val values = ContentValues().apply {
                put(Telephony.Sms.READ, 1)
                put(Telephony.Sms.SEEN, 1)
            }
            context.contentResolver.update(
                Telephony.Sms.CONTENT_URI, values,
                "${Telephony.Sms._ID} = ?", arrayOf(smsId.toString())
            )
        } catch (e: Exception) {
            LogRepository.addLogMessage("mark-read failed (needs default SMS app?): ${e.message}")
        }
    }

    /** Live path has no _id yet — find the newest matching inbox row and mark it. */
    private suspend fun markReadByContent(context: Context, from: String, body: String) {
        try {
            context.contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                arrayOf(Telephony.Sms._ID),
                "${Telephony.Sms.ADDRESS} = ? AND ${Telephony.Sms.BODY} = ?",
                arrayOf(from, body),
                "${Telephony.Sms.DATE} DESC"
            )?.use {
                if (it.moveToFirst()) markRead(context, it.getLong(0))
            }
        } catch (e: Exception) {
            LogRepository.addLogMessage("mark-read (live) failed: ${e.message}")
        }
    }
}
