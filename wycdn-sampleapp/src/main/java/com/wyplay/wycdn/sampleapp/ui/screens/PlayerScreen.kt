/*
 * Copyright (C) 2024 Wyplay, All Rights Reserved.
 * This source code and any compilation or derivative thereof is the proprietary
 * information of Wyplay and is confidential in nature.
 * Under no circumstances is this software to be exposed to or placed
 * under an Open Source License of any type without the expressed written
 * permission of Wyplay.
 */

package com.wyplay.wycdn.sampleapp.ui.screens

import android.os.Parcel
import android.os.Parcelable
import android.util.Log
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color.Companion.Black
import androidx.compose.ui.graphics.Color.Companion.White
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.livedata.observeAsState
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import com.wyplay.wycdn.sampleapp.MainActivity
import com.wyplay.wycdn.sampleapp.R
import com.wyplay.wycdn.sampleapp.ui.components.PlayerComponent
import com.wyplay.wycdn.sampleapp.ui.models.MediaListState
import com.wyplay.wycdn.sampleapp.ui.models.WycdnDebugInfo
import com.wyplay.wycdn.sampleapp.ui.models.WycdnDebugInfoState

/**
 * Media player screen responsible for rendering the ExoPlayer view.
 *
 * @param mediaListState State of the media list, encapsulating whether the media list is loading,
 *                       has encountered an error, or is ready for display.
 * @param mediaIndex Index of the currently selected media item within the media list.
 * @param debugInfoState State of WyCDN debug information, encapsulating whether the debug info is loading,
 *  *                    is unavailable because of an error, or is ready for display.
 * @param modifier An optional [Modifier] for this composable.
 */
@Composable
fun PlayerScreen(
    mediaListState: MediaListState,
    mediaIndex: Int,
    debugInfoState: WycdnDebugInfoState,
    modifier: Modifier = Modifier,
    playerInfoViewModel: PlayerInfoViewModel = viewModel(),
) {
    val activity = (LocalContext.current as? MainActivity)

    DisposableEffect(Unit) {
        // Enter full-screen mode when composable is initialized
        activity?.setFullScreenMode(true)
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Exit full-screen when composable is disposed
        onDispose {
            activity?.setFullScreenMode(false)
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    when (mediaListState) {
        is MediaListState.Loading -> {
            // Show loading indicator
            LoadingMessage(modifier)
        }
        is MediaListState.Error -> {
            // Error occurred
            ErrorMessage(mediaListState.e, modifier)
        }
        is MediaListState.Ready -> {
            // Show player
            PlayerSurface(mediaListState.mediaList, mediaIndex, debugInfoState, modifier, playerInfoViewModel)
        }
    }
}

data class PlayerInfo(val resolution: String)

class PlayerInfoViewModel : ViewModel() {
    private val _playerInfo = MutableLiveData(PlayerInfo("0x0"))
    val playerInfo: MutableLiveData<PlayerInfo> = _playerInfo

    fun updatePlayerInfo(newPlayerInfo: PlayerInfo) {
        _playerInfo.value = newPlayerInfo
    }
}

@Composable
private fun LoadingMessage(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator()
        Spacer(modifier = Modifier.height(dimensionResource(R.dimen.padding_small)))
        Text(stringResource(R.string.msg_fetching_media_list))
    }
}

@Composable
private fun ErrorMessage(e: Exception, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(stringResource(R.string.msg_error, e.message ?: ""))
    }
}

@Composable
private fun PlayerSurface(
    mediaList: List<MediaItem>,
    mediaIndex: Int,
    debugInfoState: WycdnDebugInfoState,
    modifier: Modifier = Modifier,
    playerInfoViewModel: PlayerInfoViewModel = viewModel(),
) {
    val playerInfo by playerInfoViewModel.playerInfo.observeAsState(PlayerInfo("0x0"))

    Box(
        modifier = modifier
            .background(color = Black)
            .fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // Keep track of media title across recompositions for the title chip
        var mediaTitle by remember { mutableStateOf(mediaList[mediaIndex].mediaMetadata.title.toString()) }

        // Player component
        PlayerComponent(
            mediaList = mediaList,
            mediaIndex = mediaIndex,
            onCurrentMediaMetadataChanged = { mediaMetadata ->
                // Update the title chip
                mediaTitle = mediaMetadata.title.toString()
            },
            onVideoSizeChanged = { videoSize ->
                playerInfoViewModel.updatePlayerInfo(PlayerInfo("${videoSize.width}x${videoSize.height}"))
                Log.e("PlayerSurface", "currentResolution: ${playerInfo.resolution}")
            },
        )
        // Title chip and optional Debug info chip
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd) // Align to the top end corner
                .padding(dimensionResource(R.dimen.padding_medium)),  // Padding from the edges of the Box
            verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.padding_small)) // Spacing between chips
        ) {
            TitleChip(
                title = mediaTitle,
                modifier = Modifier.align(Alignment.End)
            )
            DebugInfoChip(
                debugInfoState = debugInfoState,
                modifier = Modifier.align(Alignment.End),
                playerInfoViewModel = playerInfoViewModel
            )
        }
    }
}

@Composable
private fun TitleChip(
    title: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = title,
        color = White, // Text color for visibility
        modifier = modifier
            .background(
                color = Black.copy(alpha = 0.5f), // Lightly transparent background
                shape = RoundedCornerShape(50.dp) // Rounded corners for the chip
            )
            .padding( // Padding inside the chip
                horizontal = dimensionResource(R.dimen.padding_small),
                vertical = dimensionResource(R.dimen.padding_extra_small)
            ),
        style = MaterialTheme.typography.labelLarge,
        textAlign = TextAlign.Center
    )
}

@Preview(showBackground = true, backgroundColor = 0xFFCCCCCC)
@Composable
fun TitleChipPreview() {
    Box(
        modifier = Modifier.size(200.dp, 100.dp)
    ) {
        TitleChip(
            title = "Title Chip Preview",
            modifier = Modifier
                .align(Alignment.TopEnd) // Align to the top end corner
                .padding(dimensionResource(R.dimen.padding_medium)) // Padding from the edges of the Box
        )
    }
}

@Composable
fun DebugInfoChip(
    debugInfoState: WycdnDebugInfoState,
    modifier: Modifier = Modifier,
    playerInfoViewModel: PlayerInfoViewModel = viewModel(),
) {
    val playerInfo by playerInfoViewModel.playerInfo.observeAsState(PlayerInfo("0x0"))

    val chipModifier = modifier
        .background(
            color = Black.copy(alpha = 0.5f), // Lightly transparent background
            shape = RoundedCornerShape(5.dp) // Lightly rounded corners for the chip
        )
        .padding(horizontal = 8.dp, vertical = 4.dp) // Padding inside the chip

    when (debugInfoState) {
        is WycdnDebugInfoState.Disabled -> {
            // Show nothing
        }
        is WycdnDebugInfoState.Loading -> {
            Text(
                text = stringResource(R.string.msg_loading_wycdn_debug_info),
                color = White,
                style = MaterialTheme.typography.labelSmall,
                modifier = chipModifier
            )
        }
        is WycdnDebugInfoState.Error -> {
            Text(
                text = stringResource(R.string.msg_error, debugInfoState.e.message ?: ""),
                color = White,
                style = MaterialTheme.typography.labelSmall,
                modifier = chipModifier
            )
        }
        is WycdnDebugInfoState.Ready -> {
            Column(
                modifier = chipModifier,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                debugInfoState.debugInfo.toFieldList().forEach { (label, value) ->
                    Text(
                        text = "$label: $value",
                        color = White,
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Left
                    )
                }
                Text(
                    text = "Resolution: ${playerInfo.resolution}",
                    color = White,
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Left
                )
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFCCCCCC)
@Composable
fun DebugInfoChipLoadingPreview() {
    val debugInfoState = WycdnDebugInfoState.Loading

    Box(
        modifier = Modifier.size(250.dp, 100.dp)
    ) {
        DebugInfoChip(
            debugInfoState = debugInfoState,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(dimensionResource(R.dimen.padding_medium))
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFCCCCCC)
@Composable
fun DebugInfoChipErrorPreview() {
    val debugInfoState = WycdnDebugInfoState.Error(Exception("message"))

    Box(
        modifier = Modifier.size(250.dp, 100.dp)
    ) {
        DebugInfoChip(
            debugInfoState = debugInfoState,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(dimensionResource(R.dimen.padding_medium))
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFCCCCCC)
@Composable
fun DebugInfoChipPreview() {
    val debugInfoState = WycdnDebugInfoState.Ready(WycdnDebugInfo(
        peerId = "12345",
        peerAddress = "192.168.1.1",
        uploadBandwidth = "10512345",
        downloadBandwidth = "20512345",
        ping = "50"
    ))

    Box(
        modifier = Modifier.size(250.dp, 200.dp)
    ) {
        DebugInfoChip(
            debugInfoState = debugInfoState,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(dimensionResource(R.dimen.padding_medium))
        )
    }
}
