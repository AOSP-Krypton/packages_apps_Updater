/*
 * Copyright (C) 2022 FlamingoOS Project
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

package com.flamingo.updater.ui

import android.os.Bundle

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder

import com.google.accompanist.navigation.animation.AnimatedNavHost
import com.google.accompanist.navigation.animation.composable
import com.google.accompanist.navigation.animation.rememberAnimatedNavController
import com.flamingo.updater.ui.screens.ChangelogScreen
import com.flamingo.updater.ui.screens.MainScreen
import com.flamingo.updater.ui.screens.SettingsScreen
import com.flamingo.updater.ui.states.rememberMainScreenState
import com.flamingo.updater.ui.theme.AppTheme

class MainActivity : ComponentActivity() {

    @OptIn(ExperimentalAnimationApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            AppTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val navHostController = rememberAnimatedNavController()
                    AnimatedNavHost(
                        navController = navHostController,
                        startDestination = MAIN.HOME.path,
                        route = MAIN.path
                    ) {
                        composable(
                            MAIN.HOME.path,
                            exitTransition = {
                                when (targetState.destination.route) {
                                    MAIN.SETTINGS.path, MAIN.CHANGELOGS.path -> slideOutOfContainer(
                                        AnimatedContentScope.SlideDirection.Start,
                                        tween(TRANSITION_ANIMATION_DURATION)
                                    )
                                    else -> null
                                }
                            },
                            popEnterTransition = {
                                when (initialState.destination.route) {
                                    MAIN.SETTINGS.path, MAIN.CHANGELOGS.path -> slideIntoContainer(
                                        AnimatedContentScope.SlideDirection.End,
                                        tween(TRANSITION_ANIMATION_DURATION)
                                    )
                                    else -> null
                                }
                            },
                        ) {
                            val mainScreenState =
                                rememberMainScreenState(navHostController = navHostController)
                            LaunchedEffect(intent) {
                                intent?.data?.let {
                                    mainScreenState.startLocalUpgrade(it)
                                }
                            }
                            MainScreen(
                                mainScreenState,
                                modifier = Modifier
                                    .systemBarsPadding()
                                    .fillMaxSize()
                            )
                        }
                        animatedComposable(MAIN.SETTINGS.path, MAIN.HOME.path) {
                            SettingsScreen(
                                navController = navHostController,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        animatedComposable(MAIN.CHANGELOGS.path, MAIN.HOME.path) {
                            ChangelogScreen(
                                navHostController = navHostController,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
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

    companion object {
        private const val TRANSITION_ANIMATION_DURATION = 500
    }
}

sealed interface Route {
    val path: String
}

object MAIN : Route {
    override val path: String
        get() = "main"

    object HOME : Route {
        override val path: String
            get() = "home"
    }

    object SETTINGS : Route {
        override val path: String
            get() = "settings"
    }

    object CHANGELOGS : Route {
        override val path: String
            get() = "changelogs"
    }
}