package dev.davidemarcoli.sixmensa.data.local

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.time.Duration
import java.time.Instant

/** A payload plus when it was fetched, so the UI can show "Updated N minutes ago". */
data class Cached<T>(
    val value: T,
    val fetchedAt: Instant,
    val isStale: Boolean,
)

@Serializable
private data class Envelope<T>(
    val fetchedAtEpochMillis: Long,
    val payload: T,
)

/**
 * Payload cache backed by plain JSON files.
 *
 * Deliberately not Room: every payload here is a single blob replaced wholesale on refresh
 * (one week of menus is ~5 KB), there is nothing relational, and a file can be read from the
 * Glance widget and the notification worker without opening a database — those run in a cold
 * process with no app state alive. It also keeps the project free of annotation processors.
 */
class JsonDiskCache(
    private val root: File,
    private val json: Json,
) {
    private fun fileFor(key: String) = File(root, "$key.json")

    suspend fun <T> read(
        key: String,
        serializer: KSerializer<T>,
        ttl: Duration,
    ): Cached<T>? = withContext(Dispatchers.IO) {
        val file = fileFor(key)
        if (!file.exists()) return@withContext null
        runCatching {
            val envelope = json.decodeFromString(Envelope.serializer(serializer), file.readText())
            val fetchedAt = Instant.ofEpochMilli(envelope.fetchedAtEpochMillis)
            Cached(
                value = envelope.payload,
                fetchedAt = fetchedAt,
                isStale = Duration.between(fetchedAt, Instant.now()) > ttl,
            )
        }.getOrElse {
            // A corrupt or schema-drifted entry is not worth surfacing; drop it and refetch.
            file.delete()
            null
        }
    }

    suspend fun <T> write(
        key: String,
        serializer: KSerializer<T>,
        value: T,
        now: Instant = Instant.now(),
    ) = withContext(Dispatchers.IO) {
        runCatching {
            root.mkdirs()
            val envelope = Envelope(now.toEpochMilli(), value)
            // Write-then-rename so a kill mid-write cannot leave a truncated file behind.
            val tmp = File(root, "$key.json.tmp")
            tmp.writeText(json.encodeToString(Envelope.serializer(serializer), envelope))
            tmp.renameTo(fileFor(key))
        }
        Unit
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        root.listFiles()?.forEach { it.delete() }
        Unit
    }
}
