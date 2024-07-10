/*
 * Copyright (C) 2024 Wyplay, All Rights Reserved.
 * This source code and any compilation or derivative thereof is the proprietary
 * information of Wyplay and is confidential in nature.
 * Under no circumstances is this software to be exposed to or placed
 * under an Open Source License of any type without the expressed written
 * permission of Wyplay.
 */

package com.wyplay.wycdn.sampleapp.ui.models

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// See: https://developer.android.com/topic/libraries/architecture/datastore

/**
 * A property extension on [Context] to easily access the shared [DataStore] of [Preferences].
 *
 * @receiver Context The context from which the DataStore is accessed, typically an Activity or Application context.
 * @return [DataStore<Preferences>] An instance of DataStore configured for preferences.
 */
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * A repository for managing application settings.
 *
 * @property dataStore The [DataStore] instance used to persist and retrieve settings.
 * This DataStore is expected to be retrieved from [Context.dataStore].
 */
class SettingsRepository(
    private val dataStore: DataStore<Preferences>,
    private val wycdnEnvDataSource: WycdnEnvDataSource
) {

    /** WyCDN environment list. */
    val wycdnEnvironmentList: WycdnEnvList by lazy {
        wycdnEnvDataSource.getEnvList()
    }

    /**
     * A [Flow] of [WycdnEnv] representing the current WyCDN environment setting. This flow emits
     * the current environment value stored in the settings, allowing observers to react to changes.
     *
     * If the environment value does not exist or is invalid, [WycdnEnv.default] is emitted as a fallback.
     */
    val wycdnEnvironment: Flow<WycdnEnv> = dataStore.data.map { preferences ->
        val envId = preferences[WYCDN_ENVIRONMENT_KEY]
        wycdnEnvironmentList.envList.firstOrNull { it.id == envId } ?: wycdnEnvironmentList.defaultEnv
    }

    /**
     * Updates the WyCDN environment setting.
     *
     * @param env The [WycdnEnv] value to be stored as the new environment setting.
     */
    suspend fun setWycdnEnvironment(env: WycdnEnv) {
        dataStore.edit { preferences ->
            preferences[WYCDN_ENVIRONMENT_KEY] = env.id
        }
    }

    /**
     * A [Flow] of Boolean representing whether to enable WyCDN debug info. This flow emits
     * the current value stored in the settings, allowing observers to react to changes.
     *
     * If the value does not exist, false is emitted as a fallback.
     */
    val wycdnDebugInfoEnabled: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[WYCDN_DEBUG_INFO_ENABLED_KEY] ?: false
    }

    /**
     * Updates the WyCDN debug info enabled setting.
     *
     * @param enable The Boolean value to be stored as the new setting.
     */
    suspend fun setWycdnDebugInfoEnabled(enable: Boolean) {
        dataStore.edit { preferences ->
            preferences[WYCDN_DEBUG_INFO_ENABLED_KEY] = enable
        }
    }

    companion object {
        /** [Preferences.Key] used to store and retrieve the WyCDN environment setting. */
        internal val WYCDN_ENVIRONMENT_KEY = stringPreferencesKey("wycdn_environment")

        /** [Preferences.Key] used to store and retrieve the WyCDN debug info enabled setting. */
        internal val WYCDN_DEBUG_INFO_ENABLED_KEY = booleanPreferencesKey("wycdn_debug_info_enabled")
    }
}
