package com.thinvent.relay

import android.content.Context
import android.content.Intent
import android.provider.Telephony
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Represents a message in the selectable UI list
data class SelectableSms(
    val id: Long,
    val from: String,
    val body: String,
    val timestamp: Long,
    val threadId: Long,
    val status: String,
    val selected: androidx.compose.runtime.MutableState<Boolean> = mutableStateOf(false)
)

class LogViewModel : ViewModel() {

    // Holds the list of messages displayed in the UI
    private val _selectableSmsList = MutableStateFlow<List<SelectableSms>>(emptyList())
    val selectableSmsList = _selectableSmsList.asStateFlow()

    // For the simple text log at the bottom
    private val _logMessages = MutableStateFlow<List<String>>(emptyList())
    val logMessages = _logMessages.asStateFlow()

    val currentUrl = ServerRepository.currentUrl

    init {
        // Connect the ViewModel to the LogRepository
        viewModelScope.launch {
            LogRepository.logMessages.collect { message ->
                _logMessages.value = (_logMessages.value + message).takeLast(50)
            }
        }
    }

    fun sendTestMessage(context: Context) {
        val serviceIntent = Intent(context, ApiForwardingService::class.java).apply {
            putExtra("messageBody", "This is a test message")
            putExtra("originatingAddress", "1234567890")
            putExtra("timestamp", System.currentTimeMillis())
            putExtra("isTest", true) // Test bypasses the keep/drop filter.
        }
        context.startService(serviceIntent)
    }

    fun clearProcessedMessages(context: Context) {
        ProcessedMessagesRepository.clearProcessedMessages(context)
        viewModelScope.launch {
            LogRepository.addLogMessage("Cleared list of processed messages.")
        }
        loadMessages(context) // Refresh the list
    }

    fun sendSelectedMessages(context: Context) {
        viewModelScope.launch {
            val selectedIds = _selectableSmsList.value.filter { it.selected.value }.map { it.id }
            if (selectedIds.isEmpty()) {
                LogRepository.addLogMessage("No messages selected to send.")
                return@launch
            }

            LogRepository.addLogMessage("Starting to send ${selectedIds.size} selected messages...")
            val serviceIntent = Intent(context, UnreadMessageService::class.java).apply {
                putExtra("action", UnreadMessageService.ACTION_SEND_SPECIFIC)
                putExtra("messageIds", selectedIds.toLongArray())
            }
            context.startService(serviceIntent)
        }
    }

    fun loadMessages(context: Context) {
        viewModelScope.launch {
            LogRepository.addLogMessage("Loading messages from the last 48 hours...")
            val smsList = mutableListOf<SelectableSms>()
            withContext(Dispatchers.IO) {
              try {
                val fortyEightHoursAgo = System.currentTimeMillis() - (48 * 60 * 60 * 1000)
                context.contentResolver.query(
                    Telephony.Sms.Inbox.CONTENT_URI,
                    null,
                    "${Telephony.Sms.DATE} >= ?",
                    arrayOf(fortyEightHoursAgo.toString()),
                    Telephony.Sms.DEFAULT_SORT_ORDER
                )?.use {
                    val idColumn = it.getColumnIndex(Telephony.Sms._ID)
                    val addressColumn = it.getColumnIndex(Telephony.Sms.ADDRESS)
                    val bodyColumn = it.getColumnIndex(Telephony.Sms.BODY)
                    val dateColumn = it.getColumnIndex(Telephony.Sms.DATE)
                    val threadIdColumn = it.getColumnIndex(Telephony.Sms.THREAD_ID)
                    val readColumn = it.getColumnIndex(Telephony.Sms.READ)

                    while (it.moveToNext()) {
                        val id = it.getLong(idColumn)
                        val address = it.getString(addressColumn)
                        val body = it.getString(bodyColumn)

                        if (address.isNullOrEmpty() || body.isNullOrEmpty()) continue

                        val isRead = it.getInt(readColumn) == 1
                        val isProcessed = ProcessedMessagesRepository.isProcessed(context, id)
                        val status = when {
                            isProcessed -> "(Sent)"
                            isRead -> "(Read)"
                            else -> "(Unread)"
                        }

                        smsList.add(SelectableSms(
                            id = id,
                            from = address,
                            body = body,
                            timestamp = it.getLong(dateColumn),
                            threadId = it.getLong(threadIdColumn),
                            status = status
                        ))
                    }
                }
                LogRepository.addLogMessage("Found ${smsList.size} messages.")
              } catch (e: Exception) {
                LogRepository.addLogMessage("Could not read SMS (is SMS permission granted?): ${e.message}")
              }
            }
            _selectableSmsList.value = smsList
        }
    }
}
