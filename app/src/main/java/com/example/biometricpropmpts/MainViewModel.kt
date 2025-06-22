package com.example.biometricpropmpts

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor() : ViewModel() {
    val KEY_PROVIDER = "AndroidKeyStore"
    val KEY_ALIAS = "biometricSecretKey"
    val pass = "ThisIsTheBioPass"
    val keyGenParameterSpec = KeyGenParameterSpec.Builder(
        KEY_ALIAS,
        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
    ).setBlockModes(KeyProperties.BLOCK_MODE_CBC)
        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
        .setUserAuthenticationRequired(true)
        .setInvalidatedByBiometricEnrollment(true)
        .build()

    fun generateSecretKey(keyGenParameterSpec: KeyGenParameterSpec) {
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES, KEY_PROVIDER
        )
        keyGenerator.init(keyGenParameterSpec)
        keyGenerator.generateKey()
    }

    fun getSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEY_PROVIDER)
        keyStore.load(null)
        return keyStore.getKey(KEY_ALIAS, pass.toCharArray()) as SecretKey
    }

    fun getCipher(): Cipher {
        return Cipher.getInstance("${KeyProperties.KEY_ALGORITHM_AES}/${KeyProperties.BLOCK_MODE_CBC}/${KeyProperties.ENCRYPTION_PADDING_PKCS7}")
    }

}