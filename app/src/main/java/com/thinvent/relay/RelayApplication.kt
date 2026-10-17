package com.thinvent.relay

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Properties

class RelayApplication : Application() {

    companion object {
        lateinit var properties: Properties
            private set
    }

    override fun onCreate() {
        super.onCreate()
        properties = Properties().apply {
            try {
                assets.open("config.properties").use { stream ->
                    load(stream)
                }
            } catch (e: Exception) {
                // Handle the case where the config file is not found
                // For simplicity, we can log this, but in a real app, you might have defaults
                android.util.Log.e("RelayApplication", "config.properties not found. Please create one in the assets folder.")
            }
        }

        // Fetch the keep/drop rules from the server so on-phone filtering works.
        // Fail-closed until this succeeds (MessageFilter forwards nothing without
        // rules), so a wanted message is only ever held, never a personal one leaked.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            RulesRepository.refresh(applicationContext)
        }
    }
}
