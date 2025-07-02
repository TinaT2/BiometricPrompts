package com.example.biometricpropmpts

import android.content.Context
import android.hardware.biometrics.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.security.keystore.UserNotAuthenticatedException
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.nio.charset.Charset
import java.security.KeyStore
import java.security.UnrecoverableKeyException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
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
    var isBiometricEnrolled = false

    init {
//        Log.e("MY_APP_TAG", "This is init -> getSecretKey: ${getSecretKey()} - isBiometricEnrolled: $isBiometricEnrolled")
//        if (getSecretKey() != null && isBiometricEnrolled)
//            deleteKey()
    }

    private fun keyGenParameterSpec(): KeyGenParameterSpec =
        KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        ).apply {
            setBlockModes(KeyProperties.BLOCK_MODE_CBC)
            setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
            setUserAuthenticationRequired(true)
            setInvalidatedByBiometricEnrollment(true)

            Log.w("MY_APP_TAG", "keyGenParameterSpec -> isBiometricEnrolled: $isBiometricEnrolled")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                // 0 means require auth for every use, change if you want some timeout
                setUserAuthenticationParameters(
                    0,
                    KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL
                )
            else if (!isBiometricEnrolled)
                setUserAuthenticationValidityDurationSeconds(0) // Required for pre-API 30 support

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

    private fun getCipher(): Cipher {
        return Cipher.getInstance("${KeyProperties.KEY_ALGORITHM_AES}/${KeyProperties.BLOCK_MODE_CBC}/${KeyProperties.ENCRYPTION_PADDING_PKCS7}")
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

    fun ensureValidSecretKey(): SecretKey? {
        try {
            val key = getSecretKey()
            val cipher = getCipher()
            cipher.init(Cipher.ENCRYPT_MODE, key)
            return key
        } catch (e: Exception) {
            when (e) {
                is KeyPermanentlyInvalidatedException, is UserNotAuthenticatedException -> {
                    Log.e("MY_APP_TAG", "ensureValidSecretKey -> Exception: $e")
                    deleteKey()
                    generateSecretKey(keyGenParameterSpec())
                    return getSecretKey()
                }
                else -> {
                    Log.e("MY_APP_TAG", "ensureValidSecretKey -> Exception: $e")
                    throw e // rethrow if it's something else}
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
        Log.w("MY_APP_TAG", "encrypt -> getSecretKey: ${getSecretKey()} - isBiometricEnrolled: $isBiometricEnrolled")
        if (getSecretKey() == null)
            generateSecretKey(keyGenParameterSpec = keyGenParameterSpec())
        val cipher = getCipher()
        Log.i("MY_APP_TAG", "encrypt -> getSecretKey: ${getSecretKey()} - cipher: $cipher")
        ensureValidSecretKey()?.let { secretKey ->
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            authenticate?.let { authenticate ->
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
        }
    } catch (e: Exception) {
        Log.e("MY_APP_TAG", "encrypt -> Exception: $e")
    }
}

//    @RequiresApi(Build.VERSION_CODES.R)
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
    Log.v("MY_APP_TAG", "decrypt -> secretKey: ${getSecretKey()} - cipher: $cipher")
    getSecretKey()?.let { secretKey ->
        viewModelScope.launch {
            try {
                userPreferencesRepository.userPreferencesFlow.collectLatest { data: UserPreferences ->
                    val decryptedPassword = data.password.toByteArray()

                    cipher.init(
                        Cipher.DECRYPT_MODE,
                        secretKey,
                        data.iv.toByteArray()?.let { IvParameterSpec(it) }
                    )

                    authenticate?.let { authenticate ->
                        authenticate(
                            uiState.value,
                            cipher,
                            {}
                        ) { result: BiometricPrompt.AuthenticationResult ->

                            result.cryptoObject?.cipher?.doFinal(decryptedPassword)?.let { decrypted ->
                                val decryptedString = String(decrypted, Charset.defaultCharset())
                                updateUiState {
                                    copy(decryptedPassword = decryptedString)
                                }
                                Log.d("MY_APP_TAG", "Decrypted Password: $decryptedString")
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
                }
            } catch (exception: Exception) {
                Log.e("MY_APP_TAG", "decrypt -> Exception: $exception")
            }
        }
    }
}

    fun isKeyInvalidated(): Boolean {
        return try {
            val key = getSecretKey()
            val cipher = getCipher()
            cipher.init(Cipher.ENCRYPT_MODE, key)
            false // No exception = key is valid
        } catch (e: KeyPermanentlyInvalidatedException) {
            Log.e("MY_APP_TAG", "isKeyInvalidated -> Key is permanently invalidated (e.g., new fingerprint enrolled)\n Exception: $e")
            true // Key is permanently invalidated (e.g., new fingerprint enrolled)
        } catch (e: UnrecoverableKeyException) {
            Log.e("MY_APP_TAG", "isKeyInvalidated -> Key can't be recovered from Keystore\n Exception: $e")
            true // Key can't be recovered from Keystore
        } catch (e: Exception) {
            Log.e("MY_APP_TAG", "isKeyInvalidated -> Some other issue (e.g., new fingerprint enrolled)\n Exception: $e")
            false // Some other issue (optional to log or handle differently)
        }
    }

    fun deleteKey() {
        try {
            val keyStore = KeyStore.getInstance("AndroidKeyStore").apply {
                load(null)
            }

            if (keyStore.containsAlias(KEY_ALIAS)) {
                keyStore.deleteEntry(KEY_ALIAS)
                Log.d("MY_APP_TAG", "Key '$KEY_ALIAS' deleted from Keystore.")
            } else {
                Log.w("MY_APP_TAG", "Key '$KEY_ALIAS' not found in Keystore.")
            }
        } catch (e: Exception) {
            Log.e("MY_APP_TAG", "Failed to delete key '$KEY_ALIAS': ${e.message}", e)
        }
    }

}