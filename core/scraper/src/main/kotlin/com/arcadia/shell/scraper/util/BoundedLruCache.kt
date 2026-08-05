package com.arcadia.shell.scraper.util

/**
 * Tiny thread-safe LRU map for in-memory insight / screenshot path caches.
 * Evicts eldest entries once [maxSize] is exceeded.
 */
class BoundedLruCache<K : Any, V : Any>(
    private val maxSize: Int,
) {
    private val map = object : LinkedHashMap<K, V>(
        (maxSize.coerceAtLeast(4) / 0.75f).toInt() + 1,
        0.75f,
        /* accessOrder = */ true,
    ) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean =
            size > maxSize.coerceAtLeast(1)
    }

    @Synchronized
    fun get(key: K): V? = map[key]

    @Synchronized
    fun put(key: K, value: V): V? = map.put(key, value)

    @Synchronized
    fun putIfAbsent(key: K, value: V): V? {
        val existing = map[key]
        if (existing != null) return existing
        map[key] = value
        return null
    }

    @Synchronized
    fun clear() {
        map.clear()
    }

    @Synchronized
    fun size(): Int = map.size
}
