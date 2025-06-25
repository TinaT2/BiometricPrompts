package com.example.biometricpropmpts.data.repository

import androidx.datastore.core.DataStore
import java.util.prefs.Preferences
import javax.inject.Inject

class UserPreferencesRepository @Inject constructor(private val dataStore:DataStore<Preferences>) {
}