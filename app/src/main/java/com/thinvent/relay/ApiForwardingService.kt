package com.thinvent.relay

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.provider.Telephony
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ApiForwardingService : Service() {

    private val client by lazy {
        HttpClient(CIO) {
            install(ContentNegotiation) {
                json()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val messageBody = intent?.getStringExtra("messageBody")
        val originatingAddress = intent?.getStringExtra("originatingAddress")
        val timestamp = intent?.getLongExtra("timestamp", System.currentTimeMillis()) ?: System.currentTimeMillis()

        if (messageBody != null && originatingAddress != null) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val url = ServerRepository.currentUrl.first()
                    val threadId = Telephony.Threads.getOrCreateThreadId(this@ApiForwardingService, originatingAddress)

                    LogRepository.addLogMessage("↗ to $url.")
                    client.post(url) {
                        contentType(ContentType.Application.Json)
                        if (ServerRepository.API_KEY.isNotEmpty()) {
                            header("X-API-Key", ServerRepository.API_KEY)
                        }
                        setBody(SmsData(originatingAddress, messageBody, timestamp, threadId))
                    }
                    LogRepository.addLogMessage("✅")
                } catch (e: Exception) {
                    LogRepository.addLogMessage("Error forwarding SMS: ${e.message}")
                } finally {
                    stopSelf(startId)
                }
            }
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        client.close()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
