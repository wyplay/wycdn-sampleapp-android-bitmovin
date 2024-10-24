/*
 * Copyright (C) 2024 Wyplay, All Rights Reserved.
 * This source code and any compilation or derivative thereof is the proprietary
 * information of Wyplay and is confidential in nature.
 * Under no circumstances is this software to be exposed to or placed
 * under an Open Source License of any type without the expressed written
 * permission of Wyplay.
 */

package com.wyplay.wycdn.sampleapp.ui.models

import androidx.lifecycle.ViewModel
import androidx.media3.common.TrackGroup
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * ViewModel for managing video resolution settings and track information for the UI.
 * It provides selected resolution, available resolution formats, and UI state flags.
 */
class ResolutionViewModel : ViewModel() {

    private val _selectedResolution = MutableStateFlow<Pair<Int, Int>>(Pair(1280, 720))
    /** Player selected resolution, with a default of 1280x720. */
    val selectedResolution: StateFlow<Pair<Int, Int>> = _selectedResolution

    /** Sets the selected resolution (width, height) for the player. */
    fun setSelectedResolution(resolution: Pair<Int, Int>) {
        _selectedResolution.value = resolution
    }

    private val _trackInfoList = MutableStateFlow<List<TrackInfo>>(emptyList())
    /** List of available track info from the player. */
    val trackInfoList: StateFlow<List<TrackInfo>> = _trackInfoList

    /** Adds a [TrackInfo] object to the track info list. */
    fun addTrackInfo(trackInfo: TrackInfo) {
        _trackInfoList.value += trackInfo
    }

    private val _formats = MutableStateFlow<Set<Pair<Int, Int>>>(emptySet())
    /** Set of available resolutions to display in the UI. */
    val formats: StateFlow<Set<Pair<Int, Int>>> = _formats

    /** Adds a resolution format (width x height) to the available formats. */
    fun addResolutionFormat(width: Int, height: Int) {
        _formats.value += Pair(width, height)
    }

    private val _formatStr = MutableStateFlow<String>(_selectedResolution.value.second.toString() + "p")
    /** Current resolution string displayed in the UI (default is "720p"). */
    val formatStr: StateFlow<String> = _formatStr

    /** Updates the displayed resolution string in the UI. */
    fun addResolutionFormatStr(selectedResolutionStr: String) {
        _formatStr.value = selectedResolutionStr
    }

    private val _loaderFlag = MutableStateFlow(false)
    /** Flag to control loading spinner visibility. */
    val loaderFlag: StateFlow<Boolean> = _loaderFlag

    /** Sets the loader flag to the given [flag] state. */
    fun setLoaderFlag(flag: Boolean) {
        _loaderFlag.value = flag
    }

    private val _showMenuFlagTV = MutableStateFlow(false)
    /** Flag to show the resolution menu on TV interface. */
    val menuFlagTV: StateFlow<Boolean> = _showMenuFlagTV

    /** Sets the menu visibility flag for TV to the given [flag] state. */
    fun setMenuFlagTV(flag: Boolean) {
        _showMenuFlagTV.value = flag
    }

    private val _showMenuFlagMobile = MutableStateFlow(false)
    /** Flag to show the resolution menu on mobile interface. */
    val menuFlagMobile: StateFlow<Boolean> = _showMenuFlagMobile

    /** Sets the menu visibility flag for mobile to the given [flag] state. */
    fun setMenuFlagMobile(flag: Boolean) {
        _showMenuFlagMobile.value = flag
    }

    private val _resolutionMenuFocus = MutableStateFlow(false)
    /** Flag to control focus state of the resolution menu. */
    val resolutionMenuFocus: StateFlow<Boolean> = _resolutionMenuFocus

    /** Sets the focus state of the resolution menu. */
    fun setFocusOnResolutionMenu(flag: Boolean) {
        _resolutionMenuFocus.value = flag
    }

    init {
        _trackInfoList.value = emptyList()
        _formats.value = setOf(Pair(0, 0)) // Automatic resolution is 0x0
    }

}

data class TrackInfo(
    val mediaTrackGroup: TrackGroup,
    val index: Int,
    val width: Int,
    val height: Int
)
