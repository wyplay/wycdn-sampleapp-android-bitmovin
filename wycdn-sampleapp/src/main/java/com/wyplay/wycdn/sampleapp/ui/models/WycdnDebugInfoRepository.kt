/*
 * Copyright (C) 2024 Wyplay, All Rights Reserved.
 * This source code and any compilation or derivative thereof is the proprietary
 * information of Wyplay and is confidential in nature.
 * Under no circumstances is this software to be exposed to or placed
 * under an Open Source License of any type without the expressed written
 * permission of Wyplay.
 */

package com.wyplay.wycdn.sampleapp.ui.models

// See: https://developer.android.com/topic/architecture/data-layer

/**
 * A repository for accessing WyCDN debug information.
 */
class WycdnDebugInfoRepository() {

    private val detailsDataSource = WycdnRestDataSource("http://127.0.0.1:8000/wycdn/details")

    /**
     * Fetches WyCDN debug information.
     *
     * @return A [WycdnDebugInfo] object with debug info.
     * @throws WycdnRestDataSourceException If there is an error fetching the debug info.
     */
    suspend fun fetchDebugInfo(): WycdnDebugInfo {
        val json = detailsDataSource.fetchJson()

        fun tryGet(getJsonValue: () -> String): String {
            return try {
                getJsonValue()
            } catch (e: Exception) {
                "${e.message}"
            }
        }

        val peerId = tryGet { json.getString("peerId") }

        val peerAddress = tryGet {
            val peersArray = json.getJSONArray("peers")
            val firstPeer = peersArray.getJSONObject(0)
            val quicClientAgent = firstPeer.getJSONObject("quicClientAgent")
            quicClientAgent.getString("localAddress").split(":")[0]
        }

        val uploadBandwidth = tryGet { json.getString("uploadBandwidth") }
        val downloadBandwidth = tryGet { json.getString("downloadBandwidth") }
        val ping = tryGet { json.getString("ping") }

        return WycdnDebugInfo(peerId, peerAddress, uploadBandwidth, downloadBandwidth, ping)
    }
}

/**
 * A data class representing debug information for our WyCDN peer.
 */
data class WycdnDebugInfo(
    val peerId: String,
    val peerAddress: String,
    val uploadBandwidth: String,
    val downloadBandwidth: String,
    val ping: String
) {
    /**
     * Converts the debug information for display purposes.
     *
     * @return A list of key-value pairs.
     */
    fun toFieldList(): List<Pair<String, String>> {
        fun bandwidthToDisplay(value: String): String {
            return try {
                String.format("%s (%.2f MBps)", value, value.toLong() / 1000000.0)
            } catch (e: NumberFormatException) {
                value
            }
        }

        fun pingToDisplay(value: String): String {
            return try {
                val maxValue = 1000000u // 1s
                val ulongValue = value.toULong()
                if (ulongValue < maxValue)
                    String.format("%.2f ms", ulongValue.toDouble() / 1000.0)
                else
                    "N/A"
            } catch (e: NumberFormatException) {
                value
            }
        }

        return listOf(
            "Peer ID" to peerId,
            "Peer Address" to peerAddress,
            "Upload Bandwidth" to bandwidthToDisplay(uploadBandwidth),
            "Download Bandwidth" to bandwidthToDisplay(downloadBandwidth),
            "Ping" to pingToDisplay(ping)
        )
    }
}
