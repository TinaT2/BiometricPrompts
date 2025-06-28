package com.example.biometricpropmpts.data.repository

import android.util.Log
import androidx.datastore.core.DataStore
import com.example.biometricpropmpts.UserPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import java.io.IOException
import javax.inject.Inject

class UserPreferencesRepository @Inject constructor(private val userPreferencesStore: DataStore<UserPreferences>) {
    val TAG = "UserPreferencesRepo"
    val userPreferencesFlow: Flow<UserPreferences> = userPreferencesStore.data
        .catch { exception ->
            if (exception is IOException) {
                Log.e(TAG, "Error reading preferences.", exception)
                emit(UserPreferences.getDefaultInstance())
            } else {
                throw exception
            }
        }

    suspend fun updateUserName(username: String,password: String) {
        userPreferencesStore.updateData { preferences ->
            preferences.toBuilder().setUsername(username).setPassword(password).build()
        }
    }


}