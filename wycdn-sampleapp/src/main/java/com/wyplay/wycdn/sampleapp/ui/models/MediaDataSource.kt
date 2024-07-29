/*
 * Copyright (C) 2024 Wyplay, All Rights Reserved.
 * This source code and any compilation or derivative thereof is the proprietary
 * information of Wyplay and is confidential in nature.
 * Under no circumstances is this software to be exposed to or placed
 * under an Open Source License of any type without the expressed written
 * permission of Wyplay.
 */

package com.wyplay.wycdn.sampleapp.ui.models

import androidx.media3.common.MediaItem

/**
 * Interface representing a data source to retrieve media content.
 */
interface MediaDataSource {

    /**
     * Fetches a list of media items from the data source.
     *
     * @return A list of [MediaItem] objects.
     * @throws MediaDataSourceException If there is an error fetching the media list.
     */
    suspend fun fetchMediaList(): List<MediaItem>
}

/**
 * Exception thrown when there is an error related to the media data source.
 */
class MediaDataSourceException(message: String, cause: Throwable? = null) : Exception(message, cause)
