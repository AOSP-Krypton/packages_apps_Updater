/*
 * Copyright (C) 2022 AOSP-Krypton Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.krypton.updater.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder

import com.google.accompanist.navigation.animation.AnimatedNavHost
import com.google.accompanist.navigation.animation.composable
import com.google.accompanist.navigation.animation.rememberAnimatedNavController
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import com.krypton.updater.ui.screens.ChangelogScreen
import com.krypton.updater.ui.screens.MainScreen
import com.krypton.updater.ui.screens.SettingsScreen
import com.krypton.updater.ui.states.rememberMainScreenState
import com.krypton.updater.ui.theme.AppTheme

import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val documentTreeContract =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) {
            if (it == null) {
                finish()
                return@registerForActivityResult
            }
            val flags =
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            contentResolver.takePersistableUriPermission(it, flags)
        }

    @OptIn(ExperimentalAnimationApi::class)
    @SuppressLint("SourceLockedOrientationActivity")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        setContent {
            AppTheme {
                val systemUiController = rememberSystemUiController()
                systemUiController.setSystemBarsColor(
                    color = MaterialTheme.colorScheme.surface,
                    darkIcons = !isSystemInDarkTheme()
                )
                val navHostController = rememberAnimatedNavController()
                AnimatedNavHost(
                    navController = navHostController,
                    startDestination = Routes.HOME
                ) {
                    composable(
                        Routes.HOME,
                        exitTransition = {
                            when (targetState.destination.route) {
                                Routes.SETTINGS, Routes.CHANGELOGS -> slideOutOfContainer(
                                    AnimatedContentScope.SlideDirection.Start,
                                    tween(TRANSITION_ANIMATION_DURATION)
                                )
                                else -> null
                            }
                        },
                        popEnterTransition = {
                            when (initialState.destination.route) {
                                Routes.SETTINGS, Routes.CHANGELOGS -> slideIntoContainer(
                                    AnimatedContentScope.SlideDirection.End,
                                    tween(TRANSITION_ANIMATION_DURATION)
                                )
                                else -> null
                            }
                        },
                    ) {
                        // We should update system bar colors since it might have bee
                        // changed from other screens
                        systemUiController.setSystemBarsColor(
                            color = MaterialTheme.colorScheme.surface,
                            darkIcons = !isSystemInDarkTheme()
                        )
                        val mainScreenState =
                            rememberMainScreenState(navHostController = navHostController)
                        MainScreen(mainScreenState)
                    }
                    animatedComposable(Routes.SETTINGS, Routes.HOME) {
                        SettingsScreen(
                            systemUiController = systemUiController,
                            navController = navHostController
                        )
                    }
                    animatedComposable(Routes.CHANGELOGS, Routes.HOME) {
                        ChangelogScreen(
                            changelogViewModel = hiltViewModel(),
                            systemUiController = systemUiController,
                            navHostController = navHostController
                        )
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalAnimationApi::class)
    fun NavGraphBuilder.animatedComposable(
        destinationRoute: String,
        startRoute: String,
        content: @Composable AnimatedVisibilityScope.(NavBackStackEntry) -> Unit
    ) {
        composable(
            route = destinationRoute,
            enterTransition = {
                when (initialState.destination.route) {
                    startRoute -> slideIntoContainer(
                        AnimatedContentScope.SlideDirection.Start,
                        tween(TRANSITION_ANIMATION_DURATION)
                    )
                    else -> null
                }
            },
            popExitTransition = {
                when (targetState.destination.route) {
                    startRoute -> slideOutOfContainer(
                        AnimatedContentScope.SlideDirection.End,
                        tween(TRANSITION_ANIMATION_DURATION)
                    )
                    else -> null
                }
            },
            content = content
        )
    }

    override fun onResume() {
        super.onResume()
        checkExportFolderPermission()
    }

    private fun checkExportFolderPermission() {
        val hasPerms = contentResolver.persistedUriPermissions.firstOrNull()?.takeIf {
            it.isReadPermission && it.isWritePermission
        } != null
        if (!hasPerms) {
            documentTreeContract.launch(null)
        }
    }

    companion object {
        private const val TRANSITION_ANIMATION_DURATION = 500
    }
}