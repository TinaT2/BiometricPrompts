package com.example.biometricpropmpts

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.security.keystore.UserNotAuthenticatedException
import android.util.Log
import androidx.biometric.BiometricPrompt
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.biometricpropmpts.data.repository.UserPreferencesRepository
import com.google.protobuf.ByteString
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.nio.charset.Charset
import java.security.InvalidKeyException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject

data class LoginUIState(
    val username: TextFieldValue = TextFieldValue(""),
    val password: TextFieldValue = TextFieldValue(""),
    val decryptedPassword: String = "",
)

@HiltViewModel
class MainViewModel @Inject constructor(private val userPreferencesRepository: UserPreferencesRepository) :
    ViewModel() {
    private val KEY_PROVIDER = "AndroidKeyStore"
    private val KEY_ALIAS = "biometricSecretKey"
    private val pass = "ThisIsTheBioPass"
    private val tag = "MainViewModel"
    var isBiometricEnrolled = false

    private val _uiState = MutableStateFlow(LoginUIState())
    val uiState: StateFlow<LoginUIState> = _uiState.asStateFlow()

    private val _feedback = MutableSharedFlow<Int>()
    val feedback = _feedback.asSharedFlow()


    private fun keyGenParameterSpec(): KeyGenParameterSpec =
        KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        ).apply {
            setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            setUserAuthenticationRequired(true)
            setInvalidatedByBiometricEnrollment(true)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                setUserAuthenticationParameters(
                    0,
                    KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL
                )
            else if (!isBiometricEnrolled)
                setUserAuthenticationValidityDurationSeconds(5) // Required for pre-API 30 support

        }.build()

    private fun generateSecretKey(keyGenParameterSpec: KeyGenParameterSpec) {
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEY_PROVIDER)
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

    private fun ensureEncryptionValidSecretKey(cipher: Cipher) {
        try {
            val key = getSecretKey()
            cipher.init(Cipher.ENCRYPT_MODE, key)
        } catch (exception: Exception) {
            when (exception) {
                is KeyPermanentlyInvalidatedException, is UserNotAuthenticatedException, is InvalidKeyException -> {
                    deleteKey()
                    generateSecretKey(keyGenParameterSpec())
                    cipher.init(Cipher.ENCRYPT_MODE, getSecretKey())
                }

                else -> {
                    updateFeedback(R.string.something_wrong)
                    Log.e(tag, "Exception: $exception")
                    throw exception
                }
            }
        }
    }

    fun encrypt(
        authenticate: ((
            LoginUIState,
            Cipher,
            ((encryptedPassword: ByteArray) -> Unit)?,
            ((decryptedPassword: BiometricPrompt.AuthenticationResult) -> Unit)?
        ) -> Unit)?
    ) {
        try {
            if (getSecretKey() == null)
                generateSecretKey(keyGenParameterSpec = keyGenParameterSpec())
            val cipher = getCipher()
            ensureEncryptionValidSecretKey(cipher)
            authenticate?.let {
                authenticate(
                    uiState.value,
                    cipher,
                    { encryptedPassword ->
                        login(
                            uiState.value.username.text.toByteArray(Charset.defaultCharset()),
                            encryptedPassword,
                            cipher.iv
                        )
                    },
                    null
                )
            } ?: run {
                val encryptedPassword =
                    cipher.doFinal(uiState.value.password.text.toByteArray(Charset.defaultCharset()))
                login(
                    uiState.value.username.text.toByteArray(Charset.defaultCharset()),
                    encryptedPassword,
                    cipher.iv
                )
            }
        } catch (exception: Exception) {
            Log.e(tag, "Exception: $exception")
        }
    }

    fun decrypt(
        authenticate: ((
            LoginUIState,
            Cipher,
            ((encryptedPassword: ByteArray) -> Unit)?,
            ((decryptedPassword: BiometricPrompt.AuthenticationResult) -> Unit)?
        ) -> Unit)?
    ) {
        if (getSecretKey() == null)
            generateSecretKey(keyGenParameterSpec())
        val cipher = getCipher()
        getSecretKey()?.let { secretKey ->
            viewModelScope.launch {
                try {
                    val data = userPreferencesRepository.userPreferencesFlow.first()
                    val decryptedPassword = data.password.toByteArray()

                    cipher.init(
                        Cipher.DECRYPT_MODE,
                        secretKey,
                        data.iv.toByteArray()?.let { GCMParameterSpec(128, it) })

                    authenticate?.let { authenticate ->
                        authenticate(
                            uiState.value,
                            cipher,
                            null,
                        ) { result: BiometricPrompt.AuthenticationResult ->

                            try {
                                result.cryptoObject?.cipher?.doFinal(decryptedPassword)
                                    ?.let { decrypted ->
                                        val decryptedString =
                                            String(decrypted, Charset.defaultCharset())
                                        updateUiState { copy(decryptedPassword = decryptedString) }
                                    }
                            } catch (exception: Exception) {
                                Log.e(tag, "Exception: $exception")
                            }

                        }
                    } ?: run {
                        cipher.doFinal(decryptedPassword)?.let { decrypted ->
                            val decryptedString = String(decrypted, Charset.defaultCharset())
                            updateUiState {
                                copy(decryptedPassword = decryptedString)
                            }
                        }
                    }
                } catch (exception: Exception) {
                    when (exception) {
                        is KeyPermanentlyInvalidatedException, is UserNotAuthenticatedException -> {
                            updateFeedback(R.string.key_mismatch)
                            deleteKey()
                            /* When the user tries to decrypt with another key
                            * We must delete the key.
                            * Also delete the stored encrypted data (since it’s now useless)
                            * Prompt the user to create new credentials
                             */
                        }

                        else -> {
                            updateFeedback(R.string.something_wrong)
                            Log.e(tag, "Exception: $exception")
                        }
                    }
                }
            }
        }
    }


    private fun deleteKey() {
        try {
            val keyStore = KeyStore.getInstance(KEY_PROVIDER).apply { load(null) }

            if (keyStore.containsAlias(KEY_ALIAS))
                keyStore.deleteEntry(KEY_ALIAS)
            else
                Log.w(tag, "Key '$KEY_ALIAS' not found in Keystore.")

        } catch (e: Exception) {
            Log.e(tag, "Failed to delete key '$KEY_ALIAS': ${e.message}")
        }
    }


    private fun updateFeedback(messageResId: Int) {
        viewModelScope.launch {
            _feedback.emit(messageResId)
        }
    }
}