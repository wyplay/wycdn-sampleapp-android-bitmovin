/*
 * Copyright (C) 2024 Wyplay, All Rights Reserved.
 * This source code and any compilation or derivative thereof is the proprietary
 * information of Wyplay and is confidential in nature.
 * Under no circumstances is this software to be exposed to or placed
 * under an Open Source License of any type without the expressed written
 * permission of Wyplay.
 */

package com.wyplay.wycdn.sampleapp.ui.models

import android.app.Application
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.wyplay.wycdn.WycdnServiceConnection
import com.wyplay.wycdn.sampleapp.SampleApp
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.launch

private const val TAG = "WycdnViewModel"

/**
 * ViewModel managing the WyCDN service connection.
 *
 * @property application The [Application] instance to use providing the Android context.
 * @companion Factory Provides a factory for creating [WycdnViewModel] instances, encapsulating dependency injection.
 */
class WycdnViewModel(application: Application) : AndroidViewModel(application) {

    /** Connection to the WyCDN service. */
    private val wycdn = WycdnServiceConnection(getApplication())

    /** WyCDN debug information. */
    private val debugInfoRepository = WycdnDebugInfoRepository()

    // Backing property for debug info state, initially set to Disabled.
    private val _debugInfoState = MutableStateFlow<WycdnDebugInfoState>(WycdnDebugInfoState.Disabled)

    /**
     * Property providing [WycdnDebugInfoState] as a [StateFlow], allowing the UI to observe changes to the debug info state.
     * This flow starts in the Disabled state and updates as the debug info is loaded or if an error occurs.
     */
    val debugInfoState: StateFlow<WycdnDebugInfoState> = _debugInfoState.asStateFlow()

    /** Identifier to use for our peer. */
    val peerId: String by lazy {
        val app: SampleApp = getApplication()
        val androidId = Settings.Secure.getString(app.contentResolver, Settings.Secure.ANDROID_ID)
        "${Build.PRODUCT}-$androidId"
    }

    init {
        // Start observing WyCDN environment settings changes
        observeWycdnSettingsChanges()

        // Update WyCDN debug information periodically
        updateWycdnDebugInfo()
    }

    override fun onCleared() {
        // Stop the WyCDN service when the view model is destroyed
        wycdn.unbindService()
    }

    /**
     * Combines multiple flows into a single flow that emits a list of the latest values
     * from each flow, with each flow contributing a value to the list.
     *
     * @param flows A variable number of flows to be combined.
     * @return A flow that emits a list of the latest values from each of the input flows.
     */
    private inline fun <reified T> combineFlows(vararg flows: Flow<T>): Flow<List<T>> {
        return combine(flows.toList()) { it.toList() }
    }

    /**
     * Monitors changes to the WyCDN settings and triggers a restart of the WyCDN service
     * whenever an update is observed.
     *
     * It also implicitly kick-starts the service when the ViewModel is first initialized.
     */
    private fun observeWycdnSettingsChanges() {
        // We use a viewModelScope to launch a coroutine, ensuring that the observation and
        // subsequent service restarts are bound to the lifecycle of the ViewModel.
        viewModelScope.launch {
            val app: SampleApp = getApplication()

            // Combine settings flows into one
            val combinedSettingsFlow = combineFlows(
                app.settingsRepository.wycdnEnvironment,
                app.settingsRepository.wycdnDownloadMetricsEnabled
            )

            // Collect the latest values of the combined settings whenever any of the flows emit a new value
            combinedSettingsFlow.collectLatest { settings ->
                // Destructure the list back into specific settings
                val wycdnEnv = settings[0] as WycdnEnv
                val wycdnDownloadMetricsEnabled = settings[1] as Boolean

                // Restart the WyCDN service with the new settings
                restartService(wycdnEnv, wycdnDownloadMetricsEnabled)
            }
        }
    }

    /**
     * Restarts the WyCDN service using the provided settings.
     * @param wycdnEnv The environment to use.
     * @param wycdnDownloadMetricsEnabled Boolean indicating if download metrics should be enabled.
     */
    private fun restartService(wycdnEnv: WycdnEnv, wycdnDownloadMetricsEnabled: Boolean) {
        // Stop the service
        wycdn.unbindService()

        // Set configuration properties based on provided environment
        wycdn.setConfigProperty("wycdn.agent.peer_id", peerId)
        wycdn.setConfigProperty("wycdn.agent.stun", wycdnEnv.stunHostname)
        wycdn.setConfigProperty("wycdn.peer.bootstrap", wycdnEnv.bootstrapHostname)
        wycdn.setConfigProperty("wycdn.influxdb.host", wycdnEnv.influxdbHostname)
        wycdn.setConfigProperty("wycdn.graylog.host", wycdnEnv.graylogHostname)

        // Set the download metrics enabled property
        wycdn.setConfigProperty(
            "wycdn.influxdb.send_download_metrics",
            if (wycdnDownloadMetricsEnabled) "1" else "0"
        )

        // Allow calling REST routes for debugging
        wycdn.setConfigProperty("wycdn.proxy.server_address", "0.0.0.0")

        // Start the service
        wycdn.bindService()
    }

    /**
     * Updates WyCDN debug info by fetching it periodically.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun updateWycdnDebugInfo() {
        val app: SampleApp = getApplication()
        val debugInfoEnabled: Flow<Boolean> = app.settingsRepository.wycdnDebugInfoEnabled

        viewModelScope.launch {
            // React to changes in debugInfoEnabled to fetch debug info
            debugInfoEnabled.mapLatest { enabled ->
                if (enabled) {
                    // If debug info is enabled, set state to Loading
                    _debugInfoState.value = WycdnDebugInfoState.Loading
                    while (true) {
                        // Trigger updates every 10 seconds
                        delay(10000)
                        try {
                            // Try fetching the debug info from the repository
                            val debugInfo = debugInfoRepository.fetchDebugInfo()
                            // If successful, set the state to Ready with the updated info
                            _debugInfoState.value = WycdnDebugInfoState.Ready(debugInfo)
                        } catch (e: Exception) {
                            // If an error occurs, log the error and set state to Error
                            Log.e(TAG, "Cannot fetch the debug info", e)
                            _debugInfoState.value = WycdnDebugInfoState.Error(e)
                        }
                    }
                } else {
                    // If debug info is not enabled, set state to Disabled
                    _debugInfoState.value = WycdnDebugInfoState.Disabled
                }
            }.collect()
        }
    }

    companion object {
        /**
         * A factory for creating instances of [WycdnViewModel] with required dependencies.
         *
         * To use this factory, pass it as an argument to the `viewModel()` function in a Composable context.
         */
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            // We use the implementation suggested from:
            // https://developer.android.com/topic/libraries/architecture/viewmodel/viewmodel-factories#creationextras
            initializer {
                WycdnViewModel(this[APPLICATION_KEY] as SampleApp)
            }
        }
    }
}

/**
 * Represents the state of WyCDN debug info, encapsulating different states for UI rendering.
 */
sealed interface WycdnDebugInfoState {
    data object Disabled : WycdnDebugInfoState
    data object Loading : WycdnDebugInfoState
    data class Error(val e: Exception) : WycdnDebugInfoState
    data class Ready(val debugInfo: WycdnDebugInfo) : WycdnDebugInfoState
}
