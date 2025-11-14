package com.thinvent.relay

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object ServerRepository {
    val PROD_URL: String
    val DEV_URL: String

    private val _currentUrl: MutableStateFlow<String>

    init {
        val properties = RelayApplication.properties
        PROD_URL = properties.getProperty("prod.url", "https://fallback.prod.url/api/relay")
        DEV_URL = properties.getProperty("dev.url", "https://fallback.dev.url/api/relay")
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
