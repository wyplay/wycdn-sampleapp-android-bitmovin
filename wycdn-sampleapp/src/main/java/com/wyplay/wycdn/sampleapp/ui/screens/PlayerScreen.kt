/*
 * Copyright (C) 2024 Wyplay, All Rights Reserved.
 * This source code and any compilation or derivative thereof is the proprietary
 * information of Wyplay and is confidential in nature.
 * Under no circumstances is this software to be exposed to or placed
 * under an Open Source License of any type without the expressed written
 * permission of Wyplay.
 */

package com.wyplay.wycdn.sampleapp.ui.screens

import android.util.Log
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color.Companion.Black
import androidx.compose.ui.graphics.Color.Companion.Transparent
import androidx.compose.ui.graphics.Color.Companion.White
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import com.wyplay.wycdn.sampleapp.MainActivity
import com.wyplay.wycdn.sampleapp.R
import com.wyplay.wycdn.sampleapp.ui.components.PlayerComponent
import com.wyplay.wycdn.sampleapp.ui.models.MediaListState
import com.wyplay.wycdn.sampleapp.ui.models.ResolutionViewModel
import com.wyplay.wycdn.sampleapp.ui.models.WycdnDebugInfo
import com.wyplay.wycdn.sampleapp.ui.models.WycdnDebugInfoState
import com.wyplay.wycdn.sampleapp.ui.models.WycdnViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter
import java.security.KeyStore
import java.util.concurrent.ConcurrentLinkedQueue
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManagerFactory

/**
 * Media player screen responsible for rendering the ExoPlayer view.
 *
 * @param mediaListState State of the media list, encapsulating whether the media list is loading,
 *                       has encountered an error, or is ready for display.
 * @param mediaIndex Index of the currently selected media item within the media list.
 * @param debugInfoState State of WyCDN debug information, encapsulating whether the debug info is loading,
 *                       is unavailable because of an error, or is ready for display.
 * @param playerInfoViewModel Player info view model.
 * @param modifier An optional [Modifier] for this composable.
 */
@Composable
fun PlayerScreen(
    mediaListState: MediaListState,
    mediaIndex: Int,
    debugInfoState: WycdnDebugInfoState,
    playerInfoViewModel: PlayerInfoViewModel,
    modifier: Modifier = Modifier
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
            PlayerSurface(
                mediaListState.mediaList,
                mediaIndex,
                debugInfoState,
                modifier,
                playerInfoViewModel
            )
        }
    }

}

class PlayerInfoSender(private var wycdnViewModel: WycdnViewModel)
{
    private val metricQueue: ConcurrentLinkedQueue<String> = ConcurrentLinkedQueue()

    init {
        startQueueProcessor()
    }

    private fun createTLSSocket(): SSLSocket {
        val sslContext = SSLContext.getInstance("TLS")
        val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        trustManagerFactory.init(null as KeyStore?)

        val trustManagers = trustManagerFactory.trustManagers
        sslContext.init(null, trustManagers, null)

        val factory = sslContext.socketFactory as SSLSocketFactory
        val socket = factory.createSocket(wycdnViewModel.influxdbHostname, 8094) as SSLSocket
        socket.soTimeout = 5000
        socket.sendBufferSize = 65536

        return socket
    }

    private suspend fun sendMetrics() {
        try {
            val socket = createTLSSocket()

            if (!socket.isConnected)
                return

            val writer = OutputStreamWriter(socket.outputStream)

            while (metricQueue.isNotEmpty()) {
                val metric = metricQueue.poll() ?: break

                try {
                    withContext(Dispatchers.IO) {
                        writer.write(metric)
                        writer.write(10)
                        writer.flush()
                    }
                    delay(50L)
                } catch (e: Exception) {
                    metricQueue.offer(metric);
                    break
                }
            }

            withContext(Dispatchers.IO) {
                writer.close()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun startQueueProcessor() {
        GlobalScope.launch(Dispatchers.IO) {
            while (isActive) {
                sendMetrics()
                delay(10000L)
            }
        }
    }

    fun enqueueMetrics(playerInfo: PlayerInfo) {
        if (playerInfo.resolution == "0x0" || playerInfo.state == -1)
            return

        val metric =
            "player,peerId=${wycdnViewModel.peerId} resolution=\"${playerInfo.resolution}\",state=${playerInfo.state} ${System.currentTimeMillis() * 1000000}"

        metricQueue.offer(metric)
    }
}

data class PlayerInfo(var resolution: String, var state: Int)

class PlayerInfoViewModel(private val playerInfoSender: PlayerInfoSender) : ViewModel() {
    private val _playerInfo = MutableStateFlow(PlayerInfo("0x0", -1))
    val playerInfo: StateFlow<PlayerInfo> = _playerInfo.asStateFlow()

    fun updateResolution(resolution: String) {
        _playerInfo.value.resolution = resolution
        playerInfoSender.enqueueMetrics(_playerInfo.value)
    }

    fun updateState(state: Int) {
        _playerInfo.value.state = state
        playerInfoSender.enqueueMetrics(_playerInfo.value)
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

    val playerInfo by playerInfoViewModel.playerInfo.collectAsState(initial = PlayerInfo("0x0", -1))

    val resolutionViewModel: ResolutionViewModel = viewModel()
    val loaderFlag by resolutionViewModel.loaderFlag.collectAsState(initial = false)
    Box(
        modifier = modifier
            .background(color = Black)
            .fillMaxSize()
            .focusable(false),
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
                Log.e("player_metrics", "onVideoSizeChanged: ${videoSize.width}x${videoSize.height}")
                playerInfoViewModel.updateResolution("${videoSize.width}x${videoSize.height}")
            },
            onPlaybackStateChanged = { state ->
                Log.e("player_metrics", "onPlaybackStateChanged: ${state}")
                playerInfoViewModel.updateState(state)
            }
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

        if (loaderFlag) {
            CircularProgressIndicator(modifier = Modifier.size(50.dp), color = White)
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
    val playerInfo by playerInfoViewModel.playerInfo.collectAsState(initial = PlayerInfo("0x0", -1))
    val resolutionViewModel: ResolutionViewModel = viewModel()
    val showResolutionMenuFlag by resolutionViewModel.menuFlagMobile.collectAsState(initial = false)

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
                Button(
                    onClick = {
                        resolutionViewModel.setMenuFlagMobile(!showResolutionMenuFlag)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Transparent),
                    modifier = Modifier
                        .padding(1.dp)
                        .align(Alignment.End)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Resolution",
                        tint = White
                    )
                }
                if (showResolutionMenuFlag) {
                    ShowResolutionMenu()
                }
            }
        }
    }
}


@Preview(showBackground = true, backgroundColor = 0xFFCCCCCC)
@Composable
fun ShowResolutionMenu() {
    val resolutionViewModel: ResolutionViewModel = viewModel()
    val formats by resolutionViewModel.formats.collectAsState(initial = mutableSetOf())
    val selectedResolutionStr by resolutionViewModel.formatStr.collectAsState(initial = null)

    formats.add(Pair(0, 0)) //AUTO

    var selectedResolution by remember {
        mutableStateOf<Pair<Int, Int>?>(null)
    }

    val handleResolutionSelect: (Int, Int, String) -> Unit = { height, width, resolutionStr ->
        selectedResolution = Pair(height, width)
        resolutionViewModel.setMenuFlagMobile(false)
        resolutionViewModel.setLoaderFlag(true)
        resolutionViewModel.setSelectedResolution(Pair(height, width))
        resolutionViewModel.addResolutionFormatStr(resolutionStr)
    }

    Column(
        modifier = Modifier
            .width(200.dp)
            .background(Transparent)
    ) {
        LazyColumn(
            modifier = Modifier
                .background(
                    White.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(5.dp)
                )
                .width(120.dp)
                .align(Alignment.End)
        ) {
            items(formats.toList()) { resolution ->

                val resolutionString = if (resolution?.first == 0) {
                    "Auto"
                } else {
                    resolution?.first.toString() + "p "
                }

                Row(modifier = Modifier
                    .clickable {
                        handleResolutionSelect(
                            resolution?.first ?: 0,
                            resolution?.second ?: 0,
                            resolutionString
                        )
                    }
                    .padding(vertical = 1.dp)) {
                    Text(
                        text = resolutionString,
                        modifier = Modifier
                            .padding(10.dp)
                            .width(80.dp),
                        style = TextStyle(
                            color = White,
                            fontSize = 12.sp
                        )
                    )
                    if (resolutionString == selectedResolutionStr) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Selected",
                            tint = White,
                            modifier = Modifier
                                .size(10.dp)
                                .align(Alignment.CenterVertically)
                        )
                    }
                }
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
    val debugInfoState = WycdnDebugInfoState.Ready(
        WycdnDebugInfo(
            peerId = "12345",
            peerAddress = "192.168.1.1",
            uploadBandwidth = "10512345",
            downloadBandwidth = "20512345",
            ping = "50"
        )
    )

    Box(
        modifier = Modifier.size(250.dp, 300.dp)
    ) {
        DebugInfoChip(
            debugInfoState = debugInfoState,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(dimensionResource(R.dimen.padding_medium))
        )
    }
}
