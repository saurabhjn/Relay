package com.thinvent.relay

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
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
import androidx.compose.ui.text.style.TextAlign
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

        val permissionsToRequest = arrayOf(
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS,
            "android.permission.WRITE_SMS"
        )

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
    val currentServerUrl by logViewModel.currentUrl.collectAsState()
    val context = LocalContext.current
    val logListState = rememberLazyListState()

    // Auto-scroll for the simple text log
    LaunchedEffect(logMessages.size) {
        if (logMessages.isNotEmpty()) {
            logListState.animateScrollToItem(logMessages.size - 1)
        }
    }

    Column(modifier = modifier.padding(8.dp)) {
        // Server and Test Controls
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val isProd = currentServerUrl == ServerRepository.PROD_URL

            if (isProd) {
                Button(onClick = {}, modifier = Modifier.weight(1f)) { Text("Prod") }
            } else {
                OutlinedButton(onClick = { logViewModel.setServerToProd() }, modifier = Modifier.weight(1f)) { Text("Prod") }
            }

            if (!isProd) {
                Button(onClick = {}, modifier = Modifier.weight(1f)) { Text("Dev") }
            } else {
                OutlinedButton(onClick = { logViewModel.setServerToDev() }, modifier = Modifier.weight(1f)) { Text("Dev") }
            }

            OutlinedButton(onClick = { logViewModel.sendTestMessage(context) }, modifier = Modifier.weight(1f)) {
                Icon(
                    Icons.Default.Send,
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
                Icon(Icons.Default.Send, contentDescription = "Send Selected")
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
        LazyColumn(
            state = logListState,
            modifier = Modifier
                .weight(1f) // Less space for this list
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .border(1.dp, MaterialTheme.colorScheme.outline)
                .padding(8.dp)
        ) {
            items(logMessages) {
                Text(text = it, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
