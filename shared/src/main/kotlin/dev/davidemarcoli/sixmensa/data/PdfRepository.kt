package dev.davidemarcoli.sixmensa.data

import dev.davidemarcoli.sixmensa.core.Restaurant
import dev.davidemarcoli.sixmensa.data.local.JsonDiskCache
import dev.davidemarcoli.sixmensa.data.remote.MensaApi
import dev.davidemarcoli.sixmensa.data.remote.apiCall
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.time.Duration

class PdfRepository(
    private val api: MensaApi,
    private val cache: JsonDiskCache,
    private val httpClient: OkHttpClient,
    private val pdfDir: File,
) {
    private val serializer = MapSerializer(String.serializer(), String.serializer())

    /**
     * `{"HTP": url, "HT201": url}`. The Migros media path contains a rotating hash, so the
     * URL must always be resolved through the API and never hardcoded.
     */
    suspend fun links(): Result<Map<String, String>> {
        cache.read(KEY, serializer, TTL)?.takeIf { !it.isStale }?.let {
            return Result.success(it.value)
        }
        val result = apiCall { api.pdfLinks() }
        result.onSuccess { cache.write(KEY, serializer, it) }
        return result.recoverCatching {
            cache.read(KEY, serializer, TTL)?.value ?: throw it
        }
    }

    fun linkFor(links: Map<String, String>, restaurant: Restaurant): String? =
        links.entries.firstOrNull { it.key.equals(restaurant.wire, ignoreCase = true) }?.value

    /**
     * PdfRenderer needs a seekable file descriptor, so the PDF must land on disk in full —
     * it cannot be streamed.
     */
    suspend fun download(restaurant: Restaurant, url: String): Result<File> =
        withContext(Dispatchers.IO) {
            runCatching {
                pdfDir.mkdirs()
                val target = File(pdfDir, "${restaurant.wire}.pdf")
                val request = Request.Builder().url(url).build()
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                    val body = response.body ?: throw IOException("Empty body")
                    // Unique per call: concurrent downloads of the same plan must never
                    // interleave writes into one temp file.
                    val tmp = File.createTempFile(restaurant.wire, ".pdf.tmp", pdfDir)
                    try {
                        tmp.outputStream().use { out -> body.byteStream().copyTo(out) }
                        if (!tmp.renameTo(target)) {
                            tmp.copyTo(target, overwrite = true)
                        }
                    } finally {
                        tmp.delete()
                    }
                }
                target
            }
        }

    companion object {
        private const val KEY = "pdf_links"
        private val TTL: Duration = Duration.ofHours(24)
    }
}
