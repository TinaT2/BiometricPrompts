package com.example.biometricpropmpts

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.annotation.RequiresApi
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.biometricpropmpts.data.repository.UserPreferencesRepository
import com.google.protobuf.ByteString
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.inject.Inject


data class LoginUIState(
    val username: TextFieldValue = TextFieldValue(""),
    val password: TextFieldValue = TextFieldValue(""),
)

@HiltViewModel
class MainViewModel @Inject constructor(private val userPreferencesRepository: UserPreferencesRepository) :
    ViewModel() {
    val KEY_PROVIDER = "AndroidKeyStore"
    val KEY_ALIAS = "biometricSecretKey"
    val pass = "ThisIsTheBioPass"
    private val _uiState = MutableStateFlow(LoginUIState())
    val uiState: StateFlow<LoginUIState> = _uiState.asStateFlow()


    @RequiresApi(Build.VERSION_CODES.R)
    val keyGenParameterSpec = KeyGenParameterSpec.Builder(
        KEY_ALIAS,
        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
    ).setBlockModes(KeyProperties.BLOCK_MODE_CBC)
        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
        .setUserAuthenticationRequired(true)
        .setInvalidatedByBiometricEnrollment(true)
        .setUserAuthenticationParameters(0,KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL)
        .build()

    fun generateSecretKey(keyGenParameterSpec: KeyGenParameterSpec) {
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES, KEY_PROVIDER
        )
        keyGenerator.init(keyGenParameterSpec)
        keyGenerator.generateKey()

    }

    fun getSecretKey(): SecretKey? {
        val keyStore = KeyStore.getInstance(KEY_PROVIDER)
        keyStore.load(null)
        return keyStore.getKey(KEY_ALIAS, pass.toCharArray()) as SecretKey?
    }

    fun getCipher(): Cipher {
        return Cipher.getInstance("${KeyProperties.KEY_ALGORITHM_AES}/${KeyProperties.BLOCK_MODE_CBC}/${KeyProperties.ENCRYPTION_PADDING_PKCS7}")
    }

    fun login(encryptedUsername:ByteArray,encryptedPassword:ByteArray) {
        viewModelScope.launch {
                userPreferencesRepository.updateUserName(ByteString.copyFrom(encryptedUsername),ByteString.copyFrom(encryptedPassword))
        }
    }

    fun updateUiState(update: LoginUIState.() -> LoginUIState) {
        _uiState.update {currentState->
            currentState.update()
        }
    }


}