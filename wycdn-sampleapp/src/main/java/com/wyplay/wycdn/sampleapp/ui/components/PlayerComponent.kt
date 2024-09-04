/*
 * Copyright (C) 2024 Wyplay, All Rights Reserved.
 * This source code and any compilation or derivative thereof is the proprietary
 * information of Wyplay and is confidential in nature.
 * Under no circumstances is this software to be exposed to or placed
 * under an Open Source License of any type without the expressed written
 * permission of Wyplay.
 */

package com.wyplay.wycdn.sampleapp.ui.components

import android.util.Base64
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import com.bitmovin.player.PlayerView
import com.bitmovin.player.api.PlaybackConfig
import com.bitmovin.player.api.Player
import com.bitmovin.player.api.PlayerBuilder
import com.bitmovin.player.api.PlayerConfig
import com.bitmovin.player.api.event.PlayerEvent
import com.bitmovin.player.api.event.on
import com.bitmovin.player.api.live.SourceLiveConfig
import com.bitmovin.player.api.media.video.quality.VideoQuality
import com.bitmovin.player.api.source.Source
import com.bitmovin.player.api.source.SourceConfig
import com.wyplay.wycdn.sampleapp.BuildConfig

/**
 * Composable function to display a media player component.
 */
@Composable
fun PlayerComponent(
    mediaList: List<MediaItem>,
    mediaIndex: Int,
    modifier: Modifier = Modifier,
    onCurrentMediaChanged: (Source) -> Unit = {},
    onVideoQualityChanged: (VideoQuality) -> Unit = {},
) {
    // Get current context
    val context = LocalContext.current

    // Initialize player variable using remember to retain its state across recompositions
    var player: Player? by remember { mutableStateOf(null) }

    // Create PlayerView using the current player instance
    val playerView = createPlayerView(player)

    // Convert the media3 MediaItem to play into a Bitmovin Source
    val source = mediaList[mediaIndex].let { mediaItem ->
        Source(
            SourceConfig.fromUrl(mediaItem.localConfiguration?.uri.toString()).apply {
                this.title = mediaItem.mediaMetadata.title.toString()
                this.liveConfig = SourceLiveConfig(targetLatency = 15.0)
            }
        )
    }

    /**
     * Initializes and returns a media player.
     */
    fun initializePlayer() {
        player = PlayerBuilder(context)
            .setPlayerConfig(PlayerConfig(
                key = String(Base64.decode(BuildConfig.BUILD_ID, Base64.DEFAULT), Charsets.UTF_8),
                playbackConfig = PlaybackConfig(isAutoplayEnabled = true, isTunneledPlaybackEnabled = true)
            ))
            .disableAnalytics()
            .build().apply {
                // Set the source to play
                load(source)
                on<PlayerEvent.VideoPlaybackQualityChanged> { event ->
                    if (event.newVideoQuality != null) {
                        onVideoQualityChanged(event.newVideoQuality!!)
                    }
                }
                on<PlayerEvent.PlaylistTransition> { event ->
                    onCurrentMediaChanged(event.to)
                }
            }
    }

    // Function to release the player
    fun releasePlayer() {
        player = null
    }

    // Hook Player and PlayerView lifecycle to composable lifecycle
    LifecycleEffect { _, event ->
        when (event) {
            // Initialize the player when activity is created
            Lifecycle.Event.ON_CREATE -> {
                initializePlayer()
            }
            Lifecycle.Event.ON_START -> {
                playerView.onStart()
            }
            Lifecycle.Event.ON_RESUME -> {
                playerView.onResume()
            }
            Lifecycle.Event.ON_PAUSE -> {
                playerView.onPause()
            }
            Lifecycle.Event.ON_STOP -> {
                playerView.onStop()
            }
            // Release player resources when activity is destroyed
            Lifecycle.Event.ON_DESTROY -> {
                playerView.onDestroy()
                releasePlayer()
            }

            else -> {}
        }
    }

    // Embed PlayerView into the Compose UI hierarchy using an AndroidView
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center
    ) {
        AndroidView(
            modifier = modifier
                .focusable()
                .onKeyEvent { keyEvent ->
                    playerView.dispatchKeyEvent(keyEvent.nativeKeyEvent)
                },
            factory = {
                playerView
            }
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
        // Set the player for the PlayerView
        PlayerView(context, player)
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

