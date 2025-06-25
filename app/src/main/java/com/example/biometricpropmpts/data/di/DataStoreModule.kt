package com.example.biometricpropmpts.data.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {

    const val USER_PREFS_DATASTORE_NAME = "biometricsDataStore"

//    @Provides
//    @Singleton
//    fun provideDataStore(@ApplicationContext context: Context):DataStore<Preferences>{
//        return PreferenceDataStoreFactory.create(
//            corruptionHandler = null,
//            scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
//        ){
//            context.dataStoreFile(USER_PREFS_DATASTORE_NAME)
//        }
//    }

}