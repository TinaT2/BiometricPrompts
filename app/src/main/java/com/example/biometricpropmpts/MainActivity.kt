package com.example.biometricpropmpts

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.example.biometricpropmpts.ui.theme.BiometricPropmptsTheme
import dagger.hilt.android.AndroidEntryPoint
import java.nio.charset.Charset
import javax.crypto.Cipher
import kotlin.reflect.KFunction1

@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    private lateinit var enrollLauncher: ActivityResultLauncher<Intent>
    private lateinit var keyguardLauncher: ActivityResultLauncher<Intent>
    val TAG = "MyBiometricMain"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BiometricPropmptsTheme {
                EnrollBiometric()
            }
        }
    }

    @Composable
    private fun EnrollBiometric() {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            val viewModel: MainViewModel by viewModels()
            val uiState = viewModel.uiState.collectAsState()

            enrollLauncher =
                rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                    if (result.resultCode == RESULT_OK) {
                        Toast.makeText(this, "Biometric enrolled!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "Enrollment canceled.", Toast.LENGTH_SHORT).show()
                    }
                }

            keyguardLauncher =
                rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                    Log.i("MY_APP_TAG", "keyguardLauncher result: ${result.resultCode}")
                    if (result.resultCode == RESULT_OK) {
                        Toast.makeText(this, "keyguard enrolled!", Toast.LENGTH_SHORT).show()
                        viewModel.encrypt(::authenticate)

                    } else {
                        Toast.makeText(this, "Enrollment canceled.", Toast.LENGTH_SHORT).show()
                    }
                }

            LoginPage(
                uiState.value,
                innerPadding,
                updateUIState = viewModel::updateUiState,
                enrollClicked = {
                    checkBiometricAvailability(onSuccessful = {
                        viewModel.encrypt(::authenticate)
                    })
                },
                decryptClicked = {
                    checkBiometricAvailability(onSuccessful = {
                        viewModel.decrypt(::authenticate)
                    })
                }
            )
        }
    }


    private fun authenticate(
        uiState: LoginUIState,
        cipher: Cipher,
        onSucceedEncrypt: ((encryptedPassword: ByteArray) -> Unit)? = null,
        onSucceedDecrypt: ((decryptedPassword: BiometricPrompt.AuthenticationResult) -> Unit)? = null
    ) {
        Log.i("MY_APP_TAG", "authenticate cipher: $cipher")

        val executor = ContextCompat.getMainExecutor(this)
        val biometricPrompt = BiometricPrompt(
            this, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    Toast.makeText(applicationContext, "Authentication error: $errString", Toast.LENGTH_SHORT).show()
                }

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onSucceedEncrypt?.let {
                        val encryptedPassword: ByteArray? =
                            result.cryptoObject?.cipher?.doFinal(uiState.password.text.toByteArray(Charset.defaultCharset()))

                        Toast.makeText(applicationContext, "Authentication Succeed", Toast.LENGTH_SHORT).show()

                        if (encryptedPassword != null)
                            onSucceedEncrypt(encryptedPassword)
                    }
                    onSucceedDecrypt?.let {
                        onSucceedDecrypt(result)
                    }
                }
            })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Biometric login for my app")
            .setSubtitle("Log in using your biometric credential")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            promptInfo.setAllowedAuthenticators(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)

        else
            promptInfo.setNegativeButtonText("Cancel") // Mandatory for API < 30


        biometricPrompt.authenticate(
            promptInfo.build(),
            BiometricPrompt.CryptoObject(cipher)
        )

    }

    fun checkBiometricAvailability(onSuccessful: () -> Unit) {
        val biometricManager = BiometricManager.from(this)
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            biometricManager.canAuthenticate(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
        } else {
            biometricManager.canAuthenticate() // ⚠️ Deprecated, but correct for API < 30
        }

        // todo Atine -> I have to check if the user do not use fingerPrint and use pattern/pin
        Log.i("MY_APP_TAG", "checkBiometricAvailability canAuthenticate: $result")

        when (result) {
            BiometricManager.BIOMETRIC_SUCCESS -> {
                onSuccessful()
                Log.d("MY_APP_TAG", "App can authenticate using biometrics.")
            }

            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE ->
                Log.e("MY_APP_TAG", "No biometric features available on this device.")

            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE ->
                Log.e("MY_APP_TAG", "Biometric features are currently unavailable.")

            BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED -> {
                Log.e("MY_APP_TAG", "Biometric features are incompatible with the current Android version.")
                showDeviceCredentialPrompt()
            }

            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
                // Prompts the user to create credentials that your app accepts.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val enrollIntent = Intent(Settings.ACTION_BIOMETRIC_ENROLL).apply {
                        putExtra(
                            Settings.EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED,
                            BIOMETRIC_STRONG or DEVICE_CREDENTIAL
                        )
                    }

                    enrollLauncher.launch(enrollIntent)
                } else {
                    val enrollIntent = Intent(Settings.ACTION_SECURITY_SETTINGS)
                    enrollLauncher.launch(enrollIntent)
                }
            }

        }
    }


    @Suppress("DEPRECATION")
    // KeyguardManager.createConfirmDeviceCredentialIntent(...) was deprecated in API 33 because Google now prefers BiometricPrompt, and
    // there is no replacement for this on API 23–29
    private fun showDeviceCredentialPrompt() {
        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        val intent = keyguardManager.createConfirmDeviceCredentialIntent(
            "Authentication Required",
            "Use your screen lock to continue"
        )

        intent?.let {
            keyguardLauncher.launch(it)
        } ?: run {
            // Device doesn't have secure lock screen
            Log.e("MY_APP_TAG", "Device doesn't have secure lock screen")
//            val enrollIntent = Intent(Settings.ACTION_SECURITY_SETTINGS)
//            enrollLauncher.launch(enrollIntent)
        }
    }
}


@Composable
fun LoginPage(
    uiState: LoginUIState,
    innerPadding: PaddingValues,
    updateUIState: KFunction1<LoginUIState.() -> LoginUIState, Unit>,
    enrollClicked: () -> Unit,
    decryptClicked: () -> Unit = {}
) {

    val gradientBrush = Brush.verticalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.inversePrimary,
            MaterialTheme.colorScheme.primary
        )
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(brush = gradientBrush)
            .padding(innerPadding)
    ) {
        Column(
            modifier = Modifier
                .wrapContentSize(Alignment.Center)
                .align(Alignment.Center)
        ) {
            OutlinedTextField(
                label = {
                    Text(stringResource(R.string.username))
                },
                value = uiState.username,
                modifier = Modifier.padding(8.dp),
                onValueChange = {
                    updateUIState {
                        copy(username = it)
                    }
                }
            )
            OutlinedTextField(
                label = {
                    Text(stringResource(R.string.password))
                },
                value = uiState.password,
                modifier = Modifier.padding(8.dp),
                onValueChange = {
                    updateUIState {
                        copy(password = it)
                    }
                }
            )

            EnrollBiometricButton(
                text = "Encrypt Store",
                modifier = Modifier
                    .padding(8.dp)
                    .align(Alignment.CenterHorizontally),
                onClick = enrollClicked
            )

            EnrollBiometricButton(
                "Decrypt Pass",
                modifier = Modifier
                    .padding(8.dp)
                    .align(Alignment.CenterHorizontally),
                onClick = decryptClicked
            )

            Text(
                text = uiState.decryptedPassword,
                modifier = Modifier
                    .padding(8.dp)
                    .align(Alignment.CenterHorizontally),
                color = MaterialTheme.colorScheme.onPrimary
            )
        }
    }
}

@Composable
fun EnrollBiometricButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(modifier = modifier, onClick = onClick) {
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onPrimary
        )
    }
}


@Preview(showBackground = true)
@Composable
fun LoginPagePreview() {
    LoginPage(
        uiState = LoginUIState(),
        innerPadding = PaddingValues(),
        updateUIState = ::print,
        enrollClicked = {}
    )
}