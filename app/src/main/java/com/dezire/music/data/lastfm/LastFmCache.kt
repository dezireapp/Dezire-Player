package com.dezire.music.data.lastfm

import java.io.File
import java.security.MessageDigest

/*
 * Best-effort cache for raw Last.fm JSON responses.
 *
 * A small in-memory LRU avoids refetching within a session, while an
 * optional on-disk directory keeps successful responses available across
 * restarts and while offline. Cache failures are always swallowed: the
 * cache must never break a metadata lookup.
 */
class LastFmCache(
    private val directory: File? = null,
    private val ttlMs: Long = DEFAULT_TTL_MS,
    private val maxEntries: Int = 128,
    private val clock: () -> Long = System::currentTimeMillis
) {

    private val memory = object : LinkedHashMap<String, Entry>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>): Boolean =
            size > maxEntries
    }

    fun get(key: String): String? {
        val now = clock()
        synchronized(memory) {
            val cached = memory[key]
            if (cached != null) {
                if (now - cached.storedAt <= ttlMs) return cached.body
                memory.remove(key)
            }
        }
        val file = fileFor(key) ?: return null
        return try {
            if (!file.exists()) return null
            if (now - file.lastModified() > ttlMs) {
                file.delete()
                return null
            }
            val body = file.readText()
            synchronized(memory) { memory[key] = Entry(body, file.lastModified()) }
            body
        } catch (t: Throwable) {
            null
        }
    }

    fun put(key: String, body: String) {
        val now = clock()
        synchronized(memory) { memory[key] = Entry(body, now) }
        val file = fileFor(key) ?: return
        try {
            file.parentFile?.mkdirs()
            val temp = File(file.parentFile, file.name + ".tmp")
            temp.writeText(body)
            if (!temp.renameTo(file)) {
                file.writeText(body)
                temp.delete()
            }
        } catch (t: Throwable) {
            // Cache writes are opportunistic; ignore failures.
        }
    }

    fun clear() {
        synchronized(memory) { memory.clear() }
        val dir = directory ?: return
        try {
            dir.listFiles()?.forEach { it.delete() }
        } catch (t: Throwable) {
            // ignore
        }
    }

    private fun fileFor(key: String): File? {
        val dir = directory ?: return null
        return File(dir, sha1(key) + ".json")
    }

    private fun sha1(value: String): String {
        val digest = MessageDigest.getInstance("SHA-1")
        val bytes = digest.digest(value.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private data class Entry(val body: String, val storedAt: Long)

    companion object {
        const val DEFAULT_TTL_MS: Long = 24L * 60L * 60L * 1000L
    }
}
