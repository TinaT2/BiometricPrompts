package com.example.biometricpropmpts

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.biometric.BiometricPrompt
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.biometricpropmpts.data.repository.UserPreferencesRepository
import com.google.protobuf.ByteString
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.nio.charset.Charset
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.inject.Inject

data class LoginUIState(
    val username: TextFieldValue = TextFieldValue(""),
    val password: TextFieldValue = TextFieldValue(""),
    val decryptedPassword: String = ""
)

@HiltViewModel
class MainViewModel @Inject constructor(private val userPreferencesRepository: UserPreferencesRepository) :
    ViewModel() {
    private val KEY_PROVIDER = "AndroidKeyStore"
    private val KEY_ALIAS = "biometricSecretKey"
    val pass = "ThisIsTheBioPass"
    private val _uiState = MutableStateFlow(LoginUIState())
    val uiState: StateFlow<LoginUIState> = _uiState.asStateFlow()
    private val tag = "MainViewModel"

    @RequiresApi(Build.VERSION_CODES.R)
    private val keyGenParameterSpec = KeyGenParameterSpec.Builder(
        KEY_ALIAS,
        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
    ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
        .setUserAuthenticationRequired(true)
        .setInvalidatedByBiometricEnrollment(true)
        .setUserAuthenticationParameters(
            0,
            KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL
        )
        .build()

    private fun generateSecretKey(keyGenParameterSpec: KeyGenParameterSpec) {
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES, KEY_PROVIDER
        )
        keyGenerator.init(keyGenParameterSpec)
        keyGenerator.generateKey()

    }

    private fun getSecretKey(): SecretKey? {
        val keyStore = KeyStore.getInstance(KEY_PROVIDER)
        keyStore.load(null)
        return keyStore.getKey(KEY_ALIAS, pass.toCharArray()) as SecretKey?
    }

    fun getCipher(): Cipher {
        return Cipher.getInstance("${KeyProperties.KEY_ALGORITHM_AES}/${KeyProperties.BLOCK_MODE_GCM}/${KeyProperties.ENCRYPTION_PADDING_NONE}")
    }

    private fun login(encryptedUsername: ByteArray, encryptedPassword: ByteArray, iv: ByteArray) {
        viewModelScope.launch {
            userPreferencesRepository.updateUserPreference(
                ByteString.copyFrom(encryptedUsername),
                ByteString.copyFrom(encryptedPassword),
                ByteString.copyFrom(iv),
            )
        }
    }

    fun updateUiState(update: LoginUIState.() -> LoginUIState) {
        _uiState.update { currentState ->
            currentState.update()
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    fun encrypt(authenticate: (LoginUIState, Cipher, ((encryptedPassword: ByteArray) -> Unit)?, ((decryptedPassword: BiometricPrompt.AuthenticationResult) -> Unit)?) -> Unit) {
        if (getSecretKey() == null)
            generateSecretKey(keyGenParameterSpec = keyGenParameterSpec)
        val cipher = getCipher()
        getSecretKey()?.let { secretKey ->
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            authenticate(uiState.value, cipher, { encryptedPassword ->
                login(
                    uiState.value.username.text.toByteArray(
                        Charset.defaultCharset()
                    ), encryptedPassword,
                    cipher.iv
                )
            }, null)
        }
    }


    @RequiresApi(Build.VERSION_CODES.R)
    fun decrypt(authenticate: (LoginUIState, Cipher, ((encryptedPassword: ByteArray) -> Unit)?, ((decryptedPassword: BiometricPrompt.AuthenticationResult) -> Unit)?) -> Unit) {
        if (getSecretKey() == null)
            generateSecretKey(keyGenParameterSpec)
        val cipher = getCipher()
        getSecretKey()?.let { secretKey ->
            viewModelScope.launch {
                try{
                val data = userPreferencesRepository.userPreferencesFlow.first()
                    cipher.init(Cipher.DECRYPT_MODE, secretKey, data.iv.toByteArray()?.let { GCMParameterSpec(128,it) })
                    authenticate(
                        uiState.value,
                        cipher,
                        null
                    ) { result: BiometricPrompt.AuthenticationResult ->

                        val decryptedPassword = data.password.toByteArray()
                        result.cryptoObject?.cipher?.doFinal(decryptedPassword)?.let { decrypted ->
                            val decryptedString = String(decrypted, Charset.defaultCharset())
                            updateUiState {
                                copy(decryptedPassword = decryptedString)
                            }
                            Log.d(tag, "Decrypted Password: $decryptedString")
                        }


                    }
                }catch (exception: Exception){
                    Log.d(tag, "Exception: $exception")
                }
            }
        }
    }


}