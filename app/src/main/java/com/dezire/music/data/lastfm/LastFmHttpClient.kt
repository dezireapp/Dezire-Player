package com.dezire.music.data.lastfm

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/*
 * Raw outcome of an HTTP GET. Modelled as a sealed type so the repository
 * (and its tests) can distinguish a transport failure from an HTTP status
 * without catching exceptions.
 */
sealed interface HttpOutcome {
    data class Ok(val body: String) : HttpOutcome
    data class HttpError(
        val code: Int,
        val body: String?,
        val retryAfterSeconds: Long? = null
    ) : HttpOutcome

    data class NetworkError(val cause: Throwable) : HttpOutcome
}

/*
 * Small transport abstraction. The production implementation uses the
 * platform's HttpURLConnection so no extra dependency is required; tests
 * provide an in-memory fake.
 */
interface LastFmHttpClient {
    suspend fun get(url: String): HttpOutcome
}

class UrlConnectionLastFmHttpClient(
    private val connectTimeoutMs: Int = 10_000,
    private val readTimeoutMs: Int = 15_000,
    private val userAgent: String = "Dezire/1.0 (Android; Last.fm metadata)"
) : LastFmHttpClient {

    override suspend fun get(url: String): HttpOutcome = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = connectTimeoutMs
                readTimeout = readTimeoutMs
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", userAgent)
                setRequestProperty("Accept", "application/json")
            }

            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }

            if (code in 200..299) {
                HttpOutcome.Ok(body.orEmpty())
            } else {
                HttpOutcome.HttpError(
                    code = code,
                    body = body,
                    retryAfterSeconds = connection.getHeaderField("Retry-After")?.trim()?.toLongOrNull()
                )
            }
        } catch (e: IOException) {
            HttpOutcome.NetworkError(e)
        } finally {
            connection?.disconnect()
        }
    }
}
