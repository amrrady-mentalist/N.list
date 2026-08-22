package com.notesapp.offline.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * The network half of the Inject feature — one plain URL used in both
 * directions:
 * - [fetchValue] GETs it and pulls out the "value" field, e.g. from
 *   `{"count":1322,"value":"Test","receiveCount":463,...}` this returns
 *   "Test". Every other field in that response is whatever the other
 *   "magic app" needs for its own purposes and is ignored here entirely —
 *   parsed as a loose JsonObject rather than a strict data class so
 *   unrelated fields never break this.
 * - [sendValue] POSTs `{"value": "<value>"}` to the same URL — the shape
 *   for outgoing data wasn't specified, so this is the simplest reasonable
 *   default; if the receiving side expects something else (a different
 *   field name, raw text, form fields), this is the one place to change.
 *
 * Deliberately implemented on plain java.net.HttpURLConnection rather than
 * pulling in a new HTTP library dependency — this app has no networking
 * anywhere else, so one small hand-rolled client is a smaller footprint
 * than adding Retrofit/OkHttp/Ktor for two simple calls.
 */
class InjectApiClient {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fetchValue(url: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
            }
            val text = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()
            val obj = json.parseToJsonElement(text) as? JsonObject ?: return@runCatching null
            obj["value"]?.jsonPrimitive?.content
        }.getOrNull()
    }

    suspend fun sendValue(url: String, value: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
            val body = "{\"value\":${jsonQuote(value)}}"
            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { it.write(body) }
            val code = connection.responseCode
            connection.disconnect()
            code in 200..299
        }.getOrDefault(false)
    }

    /** Minimal JSON string escaping — just enough for arbitrary note text
     *  (quotes, backslashes, newlines) to round-trip safely inside the
     *  hand-built request body above. */
    private fun jsonQuote(raw: String): String {
        val escaped = buildString {
            append('"')
            for (c in raw) {
                when (c) {
                    '"' -> append("\\\"")
                    '\\' -> append("\\\\")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(c)
                }
            }
            append('"')
        }
        return escaped
    }

    companion object {
        private const val TIMEOUT_MS = 4000
    }
}
