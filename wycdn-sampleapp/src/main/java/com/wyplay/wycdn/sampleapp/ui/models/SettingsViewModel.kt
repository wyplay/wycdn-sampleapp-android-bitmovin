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
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.wyplay.wycdn.sampleapp.SampleApp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * ViewModel providing access to the application settings for the UI.
 *
 * @property repository The [SettingsRepository] instance accessed by this ViewModel for settings data operations.
 * @companion Factory Provides a factory for creating [SettingsViewModel] instances, encapsulating dependency injection.
 */
class SettingsViewModel(private val repository: SettingsRepository) : ViewModel() {

    /**
     * Gets the environment to use for WyCDN configuration.
     */
    val wycdnEnvironment: Flow<WycdnEnv> = repository.wycdnEnvironment

    /**
     * Gets the environment to use for WyCDN configuration.
     */
    fun setWycdnEnvironment(env: WycdnEnv) {
        viewModelScope.launch {
            repository.setWycdnEnvironment(env)
        }
    }

    companion object {
        /**
         * A factory for creating instances of [SettingsViewModel] with required dependencies.
         *
         * To use this factory, pass it as an argument to the `viewModel()` function in a Composable context.
         */
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            // We use the implementation suggested from:
            // https://developer.android.com/topic/libraries/architecture/viewmodel/viewmodel-factories#creationextras
            initializer {
                val app = this[APPLICATION_KEY] as SampleApp
                SettingsViewModel(repository = app.settingsRepository)
            }
        }
    }
}
