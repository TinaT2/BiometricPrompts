package com.example.biometricpropmpts

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
import androidx.compose.ui.graphics.Color
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
    val TAG = "MyBiometricMain"

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BiometricPropmptsTheme {
                EnrollBiometric()
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
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

            LoginPage(
                uiState.value,
                innerPadding,
                updateUIState = viewModel::updateUiState,
                enrollClicked = {
                    checkBiometricAvailability(onSuccessful = {
                        if (viewModel.getSecretKey() == null)
                            viewModel.generateSecretKey(keyGenParameterSpec = viewModel.keyGenParameterSpec)
                        val cipher = viewModel.getCipher()
                        viewModel.getSecretKey()?.let { secretKey ->
                            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
                            authenticate(uiState.value, cipher) { encryptedPassword ->
                                viewModel.login(
                                    uiState.value.username.text.toByteArray(
                                        Charset.defaultCharset()
                                    ), encryptedPassword
                                )
                            }
                        }
                    })


                }
            )


        }
    }


    private fun authenticate(
        uiState: LoginUIState,
        cipher: Cipher,
        onSucceed: (encryptedPassword: ByteArray) -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(this)
        val biometricPrompt = BiometricPrompt(
            this, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    Toast.makeText(
                        applicationContext,
                        "Authentication error: $errString", Toast.LENGTH_SHORT
                    ).show()
                }

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    val encryptedPassword: ByteArray? =
                        result.cryptoObject?.cipher?.doFinal(
                            uiState.password.text.toByteArray(
                                Charset.defaultCharset()
                            )
                        )

                    Toast.makeText(
                        applicationContext,
                        "Authentication Succeed",
                        Toast.LENGTH_SHORT
                    )
                        .show()

                    if (encryptedPassword != null)
                        onSucceed(encryptedPassword)
                }
            })
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Biometric login for my app")
            .setSubtitle("Log in using your biometric credential")
            .setAllowedAuthenticators(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
            .build()

        biometricPrompt.authenticate(
            promptInfo,
            BiometricPrompt.CryptoObject(cipher)
        )

    }

    @RequiresApi(Build.VERSION_CODES.R)
    fun checkBiometricAvailability(onSuccessful: () -> Unit) {
        val biometricManager = BiometricManager.from(this)
        when (biometricManager.canAuthenticate(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)) {
            BiometricManager.BIOMETRIC_SUCCESS -> {
                onSuccessful()
                Log.d("MY_APP_TAG", "App can authenticate using biometrics.")
            }

            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE ->
                Log.e("MY_APP_TAG", "No biometric features available on this device.")

            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE ->
                Log.e("MY_APP_TAG", "Biometric features are currently unavailable.")

            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
                // Prompts the user to create credentials that your app accepts.
                val enrollIntent = Intent(Settings.ACTION_BIOMETRIC_ENROLL).apply {
                    putExtra(
                        Settings.EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED,
                        BIOMETRIC_STRONG or DEVICE_CREDENTIAL
                    )
                }
                enrollLauncher.launch(enrollIntent)
            }

        }
    }
}


@Composable
fun LoginPage(
    uiState: LoginUIState,
    innerPadding: PaddingValues,
    updateUIState: KFunction1<LoginUIState.() -> LoginUIState, Unit>,
    enrollClicked: () -> Unit
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
                modifier = Modifier.padding(8.dp).align(Alignment.CenterHorizontally),
                onClick = {
                    enrollClicked()
                }
            )
        }
    }
}

@Composable
fun EnrollBiometricButton(modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        modifier = modifier, onClick = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                onClick()
            }
        }) {
        Text(
            text = "Enroll Biometric",
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