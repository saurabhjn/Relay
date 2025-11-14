package com.thinvent.relay

import android.content.Context
import android.content.SharedPreferences

object ProcessedMessagesRepository {
    private const val PREFS_NAME = "RelayProcessedMessages"
    private const val PROCESSED_IDS_KEY = "processed_ids"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun addProcessedMessage(context: Context, messageId: Long) {
        val prefs = getPrefs(context)
        val currentIds = prefs.getStringSet(PROCESSED_IDS_KEY, emptySet()) ?: emptySet()
        val newIds = currentIds.toMutableSet()
        newIds.add(messageId.toString())
        prefs.edit().putStringSet(PROCESSED_IDS_KEY, newIds).apply()
    }

    fun isProcessed(context: Context, messageId: Long): Boolean {
        val prefs = getPrefs(context)
        val processedIds = prefs.getStringSet(PROCESSED_IDS_KEY, emptySet()) ?: emptySet()
        return processedIds.contains(messageId.toString())
    }

    fun clearProcessedMessages(context: Context) {
        val prefs = getPrefs(context)
        prefs.edit().remove(PROCESSED_IDS_KEY).apply()
    }
}
