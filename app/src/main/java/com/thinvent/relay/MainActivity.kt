package com.thinvent.relay

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.thinvent.relay.ui.theme.RelayTheme

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        permissions.entries.forEach { (permission, isGranted) ->
            // Handle permission grant or denial
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RelayTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainScreen(
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }

        val permissionsToRequest = mutableListOf(
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val permissionsNotGranted = permissionsToRequest.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (permissionsNotGranted.isNotEmpty()) {
            requestPermissionLauncher.launch(permissionsNotGranted.toTypedArray())
        }
    }
}

@Composable
fun MainScreen(modifier: Modifier = Modifier, logViewModel: LogViewModel = viewModel()) {
    val logMessages by logViewModel.logMessages.collectAsState()
    val selectableSmsList by logViewModel.selectableSmsList.collectAsState()
    val context = LocalContext.current
    val logScrollState = rememberScrollState()

    // Auto-scroll the log view to the bottom
    LaunchedEffect(logMessages.size) {
        logScrollState.animateScrollTo(logScrollState.maxValue)
    }

    Column(modifier = modifier.padding(8.dp)) {
        // Test control (the Prod/Dev server toggle was removed — always Prod).
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { logViewModel.sendTestMessage(context) }, modifier = Modifier.fillMaxWidth()) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send Test Message",
                    modifier = Modifier.size(ButtonDefaults.IconSize)
                )
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text("Test")
            }
        }

        // Message Loading and Sending Controls
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { logViewModel.loadMessages(context) }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Refresh, contentDescription = "Load Messages")
            }
            OutlinedButton(onClick = { logViewModel.sendSelectedMessages(context) }, modifier = Modifier.weight(1f)) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send Selected")
            }
            OutlinedButton(onClick = { logViewModel.clearProcessedMessages(context) }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Delete, contentDescription = "Clear History")
            }
        }

        // Selectable Message List
        LazyColumn(
            modifier = Modifier
                .weight(2f) // Give more space to this list
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .border(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            items(selectableSmsList) { sms ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(4.dp)) {
                    Checkbox(checked = sms.selected.value, onCheckedChange = { sms.selected.value = it })
                    Column(modifier = Modifier.padding(horizontal = 8.dp)) {
                        Text(text = "From: ${sms.from} ${sms.status}", style = MaterialTheme.typography.bodyMedium)
                        Text(text = sms.body, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        // Simple Log View
        Box(
            modifier = Modifier
                .weight(1f) // Less space for this list
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .border(1.dp, MaterialTheme.colorScheme.outline)
                .padding(8.dp)
                .verticalScroll(logScrollState)
        ) {
            Text(text = logMessages.joinToString(" "), style = MaterialTheme.typography.bodySmall)
        }
    }
}
