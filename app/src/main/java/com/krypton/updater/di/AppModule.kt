package com.krypton.updater.di

import android.content.Context
import android.os.UpdateEngine

import androidx.room.Room

import com.krypton.updater.UpdaterApp
import com.krypton.updater.data.BatteryMonitor
import com.krypton.updater.data.DeviceInfo
import com.krypton.updater.data.room.AppDatabase
import com.krypton.updater.data.update.ABUpdateManager
import com.krypton.updater.data.update.AOnlyUpdateManager
import com.krypton.updater.data.update.OTAFileManager
import com.krypton.updater.data.update.UpdateManager

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent

import kotlinx.coroutines.CoroutineScope

@InstallIn(SingletonComponent::class)
@Module
object AppModule {
    @Provides
    fun provideAppDatabase(@ApplicationContext context: Context) = Room.databaseBuilder(
        context,
        AppDatabase::class.java,
        "updater_database"
    ).fallbackToDestructiveMigration()
        .build()

    @Provides
    fun provideApplicationScope(@ApplicationContext context: Context) =
        (context as UpdaterApp).applicationScope

    @Provides
    fun provideUpdateManager(
        @ApplicationContext context: Context,
        applicationScope: CoroutineScope,
        otaFileManager: OTAFileManager,
        batteryMonitor: BatteryMonitor
    ): UpdateManager =
        if (DeviceInfo.isAB()) {
            ABUpdateManager(
                context,
                applicationScope,
                otaFileManager,
                UpdateEngine(),
                batteryMonitor
            )
        } else {
            AOnlyUpdateManager(
                context,
                applicationScope,
                otaFileManager,
                batteryMonitor
            )
        }
}