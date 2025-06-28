package com.example.biometricpropmpts.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.dataStoreFile
import com.example.biometricpropmpts.UserPreferences
import com.example.biometricpropmpts.data.model.UserPreferenceSerializer
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {

    const val USER_PREFS_DATASTORE_NAME = "biometricsDataStore.pb"

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<UserPreferences> {
        return DataStoreFactory.create(serializer = UserPreferenceSerializer, produceFile = {
            context.dataStoreFile(USER_PREFS_DATASTORE_NAME)
        })
    }

}