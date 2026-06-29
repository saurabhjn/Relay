package com.thinvent.relay

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object ServerRepository {
    val PROD_URL: String
    val DEV_URL: String

    // Shared secret sent as the X-API-Key header on every forward. The server
    // (/api/relay) is fail-closed and rejects requests without it. Configured
    // via the `api.key` property in assets/config.properties.
    val API_KEY: String

    private val _currentUrl: MutableStateFlow<String>

    init {
        val properties = RelayApplication.properties
        PROD_URL = properties.getProperty("prod.url", "https://fallback.prod.url/api/relay")
        DEV_URL = properties.getProperty("dev.url", "https://fallback.dev.url/api/relay")
        API_KEY = properties.getProperty("api.key", "")
        _currentUrl = MutableStateFlow(PROD_URL)
    }

    val currentUrl = _currentUrl.asStateFlow()

    fun setServerToProd() {
        _currentUrl.value = PROD_URL
    }

    fun setServerToDev() {
        _currentUrl.value = DEV_URL
    }
}
