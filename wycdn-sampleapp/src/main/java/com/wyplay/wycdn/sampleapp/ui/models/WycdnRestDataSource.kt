package com.wyplay.wycdn.sampleapp.ui.models

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL

/**
 * A data source for fetching data from a WyCDN REST route.
 *
 * @property url The URL of the REST route.
 */
class WycdnRestDataSource(private val url: String) {

    /**
     * Fetches the JSON data from the route.
     *
     * @return The data as a [JSONObject].
     * @throws WycdnRestDataSourceException If there is an error fetching the data.
     */
    suspend fun fetchJson(): JSONObject = withContext(Dispatchers.IO) {
        try {
            return@withContext JSONObject(fetch(url))
        } catch (e: Exception) {
            throw WycdnRestDataSourceException("Cannot fetch from route: $url (${e.message})", e)
        }
    }

    /**
     * Fetches the content from the specified URL.
     *
     * @param url The URL to fetch the content from.
     * @return The content retrieved from the URL as a string.
     * @throws IOException If an I/O error occurs while reading from the URL.
     * @throws MalformedURLException If the provided URL is not valid.
     * @throws SSLException If an SSL error occurs during the connection.
     */
    private fun fetch(url: String): String {
        val connection = URL(url).openConnection()
        return connection.inputStream.bufferedReader().use { it.readText() }
    }
}

/**
 * Exception thrown when there is an error related to the WyCDN REST data source.
 */
class WycdnRestDataSourceException(message: String, cause: Throwable? = null) : Exception(message, cause)
