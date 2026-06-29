package com.loopsai.chat.internal

import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Tiny dependency-free HTTP helper (mirrors iOS's `URLSession` usage). The SDK
 * deliberately ships no networking dependency (OkHttp/Retrofit) so consumers
 * inherit nothing heavy — analytics delivery and session bootstrap both POST
 * JSON off the main thread via `HttpURLConnection`.
 */
internal object LoopsHttp {
    private val executor = Executors.newCachedThreadPool { r ->
        Thread(r, "loopsai-http").apply { isDaemon = true }
    }

    data class Response(val status: Int, val body: String?, val error: Throwable?)

    /** Fire-and-forget POST of a JSON body (analytics adapters). */
    fun postFireAndForget(url: String, jsonBody: String, headers: Map<String, String> = emptyMap()) {
        executor.execute { postBlocking(url, jsonBody, headers, timeoutMs = 15_000) }
    }

    /** POST a JSON body and deliver the response on the background thread. */
    fun post(
        url: String,
        jsonBody: String,
        headers: Map<String, String> = emptyMap(),
        timeoutMs: Int = 10_000,
        completion: (Response) -> Unit
    ) {
        executor.execute { completion(postBlocking(url, jsonBody, headers, timeoutMs)) }
    }

    private fun postBlocking(
        url: String,
        jsonBody: String,
        headers: Map<String, String>,
        timeoutMs: Int
    ): Response {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                headers.forEach { (k, v) -> setRequestProperty(k, v) }
            }
            connection.outputStream.use { it.write(jsonBody.toByteArray(Charsets.UTF_8)) }

            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use(BufferedReader::readText)
            Response(status, body, null)
        } catch (t: Throwable) {
            Response(0, null, t)
        } finally {
            connection?.disconnect()
        }
    }
}
