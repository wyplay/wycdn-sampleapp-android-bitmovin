/*
 * Copyright (C) 2024 Wyplay, All Rights Reserved.
 * This source code and any compilation or derivative thereof is the proprietary
 * information of Wyplay and is confidential in nature.
 * Under no circumstances is this software to be exposed to or placed
 * under an Open Source License of any type without the expressed written
 * permission of Wyplay.
 */

package com.wyplay.wycdn.sampleapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.wyplay.wycdn.sampleapp.ui.AppNavigation
import com.wyplay.wycdn.sampleapp.ui.theme.AppTheme

/**
 * Main activity acting as the entry point for the application UI.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initializes the UI, applying the app's theme and setting up navigation.
        setContent {
            AppTheme {
                AppNavigation()
            }
        }
    }

    /**
     * Sets full-screen mode by toggling the visibility of the system UI.
     *
     * @param fullscreen If true, shows the system UI. If false, hides the system UI.
     */
    fun setFullScreenMode(fullscreen: Boolean) {
        WindowCompat.setDecorFitsSystemWindows(window, !fullscreen)
        WindowInsetsControllerCompat(window, window.decorView).let { controller ->
            if (!fullscreen) {
                controller.show(WindowInsetsCompat.Type.systemBars())
            } else {
                controller.hide(WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
    }
}

