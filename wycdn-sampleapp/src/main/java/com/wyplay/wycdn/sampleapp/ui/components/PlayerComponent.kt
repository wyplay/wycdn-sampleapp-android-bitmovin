/*
 * Copyright (C) 2024 Wyplay, All Rights Reserved.
 * This source code and any compilation or derivative thereof is the proprietary
 * information of Wyplay and is confidential in nature.
 * Under no circumstances is this software to be exposed to or placed
 * under an Open Source License of any type without the expressed written
 * permission of Wyplay.
 */

package com.wyplay.wycdn.sampleapp.ui.components

import androidx.annotation.OptIn
import androidx.compose.foundation.focusable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView

/**
 * Composable function to display a media player component.
 */
@OptIn(UnstableApi::class)
@Composable
fun PlayerComponent(
    mediaList: List<MediaItem>,
    mediaIndex: Int,
    onCurrentMediaMetadataChanged: (MediaMetadata) -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Get current context
    val context = LocalContext.current

    // Initialize player variable using remember to retain its state across recompositions
    var player: Player? by remember { mutableStateOf(null) }

    // Initialize state variables to keep track of player state
    var playWhenReady by remember { mutableStateOf(true) }
    var mediaItemIndex by remember { mutableIntStateOf(mediaIndex) }

    // Initialize errorMessage state variable to display in an alert dialog
    var errorMessage by remember { mutableStateOf("") }

    // Create PlayerView using the current player instance
    val playerView = createPlayerView(player)

    /**
     * Initializes and returns a media player.
     */
    fun initializePlayer() {
        player = ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(context).setLiveTargetOffsetMs(0))
            .build().apply {
                // Set the media items to play
                setMediaItems(mediaList, mediaItemIndex, C.TIME_UNSET)
                // Start playing when ready
                this.playWhenReady = playWhenReady
                // Add a listener to observe metadata changes
                addListener(object : Player.Listener {
                    override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                        // Invoke the callback with the new media metadata
                        onCurrentMediaMetadataChanged(mediaMetadata)
                    }
                    override fun onPlayerError(error: PlaybackException) {
                        // Auto-resume playback if a recoverable error occurs
                        when (error.errorCode) {
                            PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW,
                            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
                            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
                            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                            PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
                            PlaybackException.ERROR_CODE_DECODING_FAILED -> {
                                seekToDefaultPosition()
                                prepare()
                            }
                            else -> {
                                val rootCause = error.cause?.let { "Caused by: ${it::class.java.simpleName}: ${it.message}" } ?: ""
                                errorMessage = "${error.errorCodeName} (${error.errorCode})\nPlaybackException: ${error.message}\n$rootCause"
                            }
                        }
                    }
                })

            // Prepare the player
            prepare()
        }
    }

    // Function to release the player
    fun releasePlayer() {
        player?.let {
            // Save player state
            playWhenReady = it.playWhenReady
            mediaItemIndex = it.currentMediaItemIndex
            it.release()
        }
        player = null
    }

    // Hook Player and PlayerView lifecycle to composable lifecycle
    LifecycleEffect { _, event ->
        when (event) {
            // Initialize the player when activity starts or resume
            Lifecycle.Event.ON_START -> {
                initializePlayer()
                playerView.onResume()
            }
            // Release player resources when activity stops
            Lifecycle.Event.ON_STOP -> {
                playerView.onPause()
                releasePlayer()
            }
            else -> {}
        }
    }

    // Embed PlayerView into the Compose UI hierarchy using an AndroidView
    AndroidView(
        modifier = modifier.focusable()
            .onKeyEvent { playerView.dispatchKeyEvent(it.nativeKeyEvent) },
        factory = { playerView }
    )

    // Display an AlertDialog for error messages
    if (errorMessage.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { errorMessage = "" },
            confirmButton = {
                TextButton(onClick = { errorMessage = "" }) {
                    Text("OK")
                }
            },
            icon = { Icon(Icons.Default.Warning, contentDescription = "Warning") },
            title = { Text("Playback Error") },
            text = { Text(errorMessage) }
        )
    }
}

/**
 * A side effect of composition that must run on lifecycle event changes.
 * It observes the lifecycle events of the provided [lifecycleOwner] and executes [onEvent] accordingly.
 *
 * @param lifecycleOwner [LifecycleOwner] whose state should be observed.
 * @param onEvent Callback to run when the [LifecycleOwner] changes state.
 */
@Composable
fun LifecycleEffect(
    lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current,
    onEvent: (LifecycleOwner, Lifecycle.Event) -> Unit
) {
    // Setup a disposable effect to handle the lifecycle of the provided lifecycleOwner
    DisposableEffect(lifecycleOwner) {
        // Define an observer for lifecycle events
        val observer = LifecycleEventObserver { source, event ->
            // Call the provided event handler with the source and event
            onEvent(source, event)
        }
        // Add the observer to the lifecycle of the owner
        lifecycleOwner.lifecycle.addObserver(observer)

        // Remove the observer when this effect is disposed
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
}

/**
 * Creates and returns a [PlayerView] for the given player.
 */
@Composable
fun createPlayerView(player: Player?): PlayerView {
    // Get the current context
    val context = LocalContext.current

    // Create a PlayerView and remember it to retain its state across recompositions
    val playerView = remember {
        PlayerView(context).apply {
            // Set the player for the PlayerView
            this.player = player
        }
    }

    // Establish a disposable effect to handle player disposal
    DisposableEffect(player) {
        // Set the player for the PlayerView
        playerView.player = player

        // Remove the player from the PlayerView when this composable is disposed
        onDispose {
            playerView.player = null
        }
    }
    return playerView
}
