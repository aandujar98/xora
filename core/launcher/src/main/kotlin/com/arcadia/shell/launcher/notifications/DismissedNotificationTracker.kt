package com.arcadia.shell.launcher.notifications

import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory + persistable set of notification keys the user already cleared.
 * Survives process death and app updates so XOrA Network inbox items do not toast again.
 */
class DismissedNotificationTracker(
    private val persist: (Set<String>) -> Unit = {},
) {
    private val ids = ConcurrentHashMap.newKeySet<String>()

    fun seed(loaded: Collection<String>) {
        ids.addAll(loaded.map { it.trim() }.filter { it.isNotEmpty() })
    }

    fun isDismissed(id: String): Boolean {
        val key = id.trim()
        return key.isNotEmpty() && key in ids
    }

    fun isDismissed(keys: Collection<String>): Boolean =
        keys.any { isDismissed(it) }

    fun dismiss(keys: Collection<String>) {
        val next = keys.map { it.trim() }.filter { it.isNotEmpty() }
        if (next.isEmpty()) return
        val added = mutableListOf<String>()
        next.forEach { key ->
            if (ids.add(key)) added += key
        }
        if (added.isEmpty()) return
        while (ids.size > MAX_IDS) {
            val first = ids.firstOrNull() ?: break
            ids.remove(first)
        }
        persist(added.toSet())
    }

    fun snapshot(): Set<String> = ids.toSet()

    companion object {
        const val MAX_IDS = 400
    }
}
