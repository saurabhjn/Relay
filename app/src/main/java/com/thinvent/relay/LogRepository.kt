package com.thinvent.relay

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object LogRepository {
    private val _logMessages = MutableSharedFlow<String>(replay = 50)
    val logMessages = _logMessages.asSharedFlow()

    suspend fun addLogMessage(message: String) {
        _logMessages.emit(message)
    }
}
