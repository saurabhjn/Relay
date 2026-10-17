package com.thinvent.relay

import android.content.Context
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The keep/drop rules the app uses to decide, ON THE PHONE, which SMS to forward.
 * They are fetched from the server (GET /api/relay/rules, authenticated with the
 * same X-API-Key we send on /api/relay) — so the rules are never baked into the
 * APK in the clear and never exposed on a public URL. Edit them once on the
 * server; every phone follows on its next refresh.
 */
@Serializable
data class RelayRules(
    val version: Int = 0,
    val keep_senders: List<String> = emptyList(),
    val keep_keywords: List<String> = emptyList(),
    val drop_senders: List<String> = emptyList(),
    val drop_text: List<String> = emptyList()
)

/** Lower-cased view of the rules for cheap matching. */
class Rules(r: RelayRules) {
    val version = r.version
    val keepSenders = r.keep_senders.map { it.lowercase() }
    val keepKeywords = r.keep_keywords.map { it.lowercase() }
    val dropSenders = r.drop_senders.map { it.lowercase() }
    val dropText = r.drop_text.map { it.lowercase() }
}

object RulesRepository {
    private const val PREFS = "relay_rules"
    private const val KEY_JSON = "rules_json"
    private val json = Json { ignoreUnknownKeys = true }

    @Volatile private var cached: Rules? = null

    /** Cached rules, or null if we've never successfully fetched them. */
    fun getRules(context: Context): Rules? {
        cached?.let { return it }
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_JSON, null) ?: return null
        return try {
            Rules(json.decodeFromString<RelayRules>(saved)).also { cached = it }
        } catch (e: Exception) {
            null
        }
    }

    /** Fetch the latest rules from the server and cache them. Returns success. */
    suspend fun refresh(context: Context): Boolean {
        val client = HttpClient(CIO)
        return try {
            val resp = client.get(ServerRepository.RULES_URL) {
                if (ServerRepository.API_KEY.isNotEmpty()) {
                    header("X-API-Key", ServerRepository.API_KEY)
                }
            }
            if (resp.status == HttpStatusCode.OK) {
                val text = resp.bodyAsText()
                val parsed = json.decodeFromString<RelayRules>(text)
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit().putString(KEY_JSON, text).apply()
                cached = Rules(parsed)
                LogRepository.addLogMessage(
                    "Rules updated (v${parsed.version}): ${parsed.keep_senders.size} senders, ${parsed.keep_keywords.size} keywords."
                )
                true
            } else {
                LogRepository.addLogMessage("Rules fetch: server said ${resp.status}")
                false
            }
        } catch (e: Exception) {
            LogRepository.addLogMessage("Rules fetch failed: ${e.message}")
            false
        } finally {
            client.close()
        }
    }
}
