# 🔐 BiometricPrompt Android

A sample project demonstrating **modern biometric authentication** on Android using:

- `BiometricPrompt` API (AndroidX)
- Android Keystore (`AES/GCM/NoPadding`)
- User authentication-bound keys
- Encrypted data storage
- `ActivityResultLauncher` for biometric enrollment flow

---

## 🔥 Features

✅ Secure AES key generation using Android Keystore  
✅ BiometricPrompt authentication (fingerprint / face / PIN)  
✅ AES-GCM encrypted data  
✅ Handles "not enrolled" biometrics case  
✅ Compatible with API 23+  
✅ Kotlin + Jetpack-ready

---

## 📸 Screenshots

> _Add screenshots of your biometric prompt, success state, and encrypted result UI here_  
📷 `BiometricPrompt` dialog | ✅ Success screen | 🔐 Encrypted output

---

## 🚀 Getting Started

### Prerequisites

- Android Studio Hedgehog or newer
- Min SDK 23+
- Kotlin DSL
- AndroidX Biometric library

---

## 📦 Dependencies

```groovy
dependencies {
    implementation "androidx.biometric:biometric:1.2.0-alpha05" // or latest stable
}
```
## 🧠 Architecture Overview

| File                  | Role                                                   |
|-----------------------|--------------------------------------------------------|
| `BiometricAuthManager.kt` | Handles biometric prompt lifecycle and authentication |
| `KeyStoreHelper.kt`   | Manages Android Keystore operations (AES key + Cipher) |
| `MainActivity.kt`     | Demonstrates integration and end-to-end data flow      |

---

## 🔑 Key Concepts

- **BiometricPrompt**: Secure system UI for fingerprint/face/PIN authentication
- **CryptoObject**: Wraps the Cipher used for encryption/decryption
- **GCMParameterSpec**: Provides IV and tag length for AES-GCM mode
- **ActivityResultLauncher**: Replaces deprecated `startActivityForResult()` for biometric enrollment
- **Android Keystore**: Secure storage for symmetric keys requiring biometric authentication

---

## ⚙️ Flow Diagram

```plaintext
[Check if biometric enrolled]
        |
   [Yes] | [No]
    |         |
[Show Prompt] | [Launch Settings.ACTION_BIOMETRIC_ENROLL]
    |
[Auth Success]
    |
[Use authenticated Cipher to encrypt/decrypt data]
```

## 🧪 Test Scenarios

- ✅ First-time biometric prompt flow
- ✅ Device with no biometrics enrolled
- ✅ Key generation and AES-GCM encryption
- ✅ Key invalidation (e.g. PIN or biometric change)
- ✅ Authentication success and failure handling
- ✅ Biometric enrollment redirect and retry logic
- ✅ Lifecycle-safe biometric prompts (rotation/background)


## 🤝 Contributions

Pull requests and suggestions are welcome!  
Let’s make this the go-to template for secure biometric-based encryption on Android.

If you find bugs or have ideas, feel free to open an issue or PR 🚀

## 📜 License

**MIT License**  
You are free to use this project in personal and commercial apps.  
Attribution is appreciated but not required.

