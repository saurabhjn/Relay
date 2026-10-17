package com.thinvent.relay

import android.content.Context

/**
 * Decides on the phone whether an SMS is one we want to forward. Unmatched
 * messages (e.g. personal banking) are never sent — they stay on the device.
 *
 * FAIL-CLOSED: if we have no rules yet (never fetched / offline first run), we
 * do NOT forward. Better to hold a wanted message than to leak a personal one;
 * once the rules are fetched, held-back live messages resume normally.
 */
object MessageFilter {

    fun shouldForward(context: Context, from: String, body: String): Boolean {
        val rules = RulesRepository.getRules(context) ?: return false

        val sender = (from).lowercase()
        val haystack = "$from $body".lowercase()

        // 1. Always drop courier / tracking noise.
        if (rules.dropSenders.any { sender.contains(it) }) return false
        if (rules.dropText.any { haystack.contains(it) }) return false

        // 2. Keep if a wanted bank/portal sender OR a wanted keyword (incl. "otp") matches.
        if (rules.keepSenders.any { sender.contains(it) }) return true
        if (rules.keepKeywords.any { haystack.contains(it) }) return true

        // 3. Nothing matched -> not our message -> keep it on the phone.
        return false
    }
}
