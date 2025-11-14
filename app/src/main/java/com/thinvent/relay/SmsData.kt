package com.thinvent.relay

import kotlinx.serialization.Serializable

@Serializable
data class SmsData(val from: String, val message: String, val timestamp: Long, val threadId: Long)
