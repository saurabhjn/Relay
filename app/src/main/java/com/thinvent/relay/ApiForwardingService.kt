package com.thinvent.relay

import android.app.Service
import android.content.Context
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
import java.security.cert.X509Certificate
import javax.net.ssl.X509TrustManager

class ApiForwardingService : Service() {

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
        val messageBody = intent?.getStringExtra("messageBody")
        val originatingAddress = intent?.getStringExtra("originatingAddress")
        val timestamp = intent?.getLongExtra("timestamp", System.currentTimeMillis()) ?: System.currentTimeMillis()

        if (messageBody != null && originatingAddress != null) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val url = ServerRepository.currentUrl.first()
                    val threadId = Telephony.Threads.getOrCreateThreadId(this@ApiForwardingService, originatingAddress)

                    LogRepository.addLogMessage("Forwarding to $url")
                    client.post(url) {
                        contentType(ContentType.Application.Json)
                        setBody(SmsData(originatingAddress, messageBody, timestamp, threadId))
                    }
                    LogRepository.addLogMessage("Success")
                } catch (e: Exception) {
                    LogRepository.addLogMessage("Error forwarding SMS: ${e.message}")
                }
            }
        }

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
