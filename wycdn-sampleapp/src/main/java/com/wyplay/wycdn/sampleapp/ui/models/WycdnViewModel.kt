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
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.wyplay.wycdn.WycdnServiceConnection
import com.wyplay.wycdn.sampleapp.SampleApp
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * ViewModel managing the WyCDN service connection.
 *
 * @property application The [Application] instance to use providing the Android context.
 * @companion Factory Provides a factory for creating [WycdnViewModel] instances, encapsulating dependency injection.
 */
class WycdnViewModel(application: Application) : AndroidViewModel(application) {

    /**
     * Connection to the WyCDN service.
     */
    private val wycdn = WycdnServiceConnection(getApplication())

    /**
     * Identifier to use for our peer.
     */
    val peerId: String by lazy {
        val app: SampleApp = getApplication()
        val androidId = Settings.Secure.getString(app.contentResolver, Settings.Secure.ANDROID_ID)
        "${Build.PRODUCT}-$androidId"
    }

    init {
        // Start observing WyCDN environment settings changes
        observeWycdnEnvChanges()
    }

    override fun onCleared() {
        // Stop the WyCDN service when the view model is destroyed
        wycdn.unbindService()
    }

    /**
     * Monitors changes to the WyCDN environment setting and triggers a restart of the WyCDN service
     * whenever an update to the environment setting is observed.
     *
     * It also implicitly kick-starts the service when the ViewModel is first initialized.
     */
    private fun observeWycdnEnvChanges() {
        // We use a viewModelScope to launch a coroutine, ensuring that the observation and
        // subsequent service restarts are bound to the lifecycle of the ViewModel.
        viewModelScope.launch {
            val app: SampleApp = getApplication()
            app.settingsRepository.wycdnEnvironment.collectLatest { wycdnEnv ->
                // Restart the WyCDN service with the new environment
                restartService(wycdnEnv)
            }
        }
    }

    /**
     * Restarts theWyCDN service using the provided environment.
     * @param wycdnEnv The environment to use.
     */
    private fun restartService(wycdnEnv: WycdnEnv) {
        // Stop the service
        wycdn.unbindService()

        // Set configuration properties based on provided environment
        wycdn.setConfigProperty("wycdn/agent/peer_id", peerId)
        wycdn.setConfigProperty("wycdn/agent/stun", wycdnEnv.stunHostname)
        wycdn.setConfigProperty("wycdn/peer/bootstrap", wycdnEnv.bootstrapHostname)
        wycdn.setConfigProperty("wycdn/influxdb/host", wycdnEnv.influxdbHostname)
        wycdn.setConfigProperty("wycdn/graylog/host", wycdnEnv.graylogHostname)

        // Start the service
        wycdn.bindService()
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
