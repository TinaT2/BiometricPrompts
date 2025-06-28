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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.example.biometricpropmpts.ui.theme.BiometricPropmptsTheme
import dagger.hilt.android.AndroidEntryPoint
import java.nio.charset.Charset
import java.util.concurrent.Executor
import javax.crypto.Cipher
import javax.crypto.SecretKey
import kotlin.reflect.KFunction1

@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    private lateinit var enrollLauncher: ActivityResultLauncher<Intent>
    private var biometricPrompt: BiometricPrompt? = null
    private lateinit var executor: Executor
    private lateinit var promptInfo: BiometricPrompt.PromptInfo
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

            LaunchedEffect(Unit) {
                viewModel.generateSecretKey(keyGenParameterSpec = viewModel.keyGenParameterSpec)
                checkBiometricAvailability(onSuccessful = { authenticate{
                    viewModel.login()
                } })
            }

            LoginPage(
                uiState.value,
                innerPadding,
                cipher = viewModel.getCipher(),
                secretKey = viewModel.getSecretKey(),
                biometricPrompt = biometricPrompt,
                promptInfo = promptInfo,
                updateUIState = viewModel::updateUiState
            )


        }
    }


    private fun authenticate(onSucceed:()->Unit) {
        executor = ContextCompat.getMainExecutor(this)
        biometricPrompt = BiometricPrompt(
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
                    val text = "I'mSecret"
                    val encryptedInfo: ByteArray? =
                        result.cryptoObject?.cipher?.doFinal(text.toByteArray(Charset.defaultCharset()))
                    Log.d(TAG, encryptedInfo.contentToString())

                    Toast.makeText(
                        applicationContext,
                        "Authentication Succeed",
                        Toast.LENGTH_SHORT
                    )
                        .show()
                }
            })
        promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Biometric login for my app")
            .setSubtitle("Log in using your biometric credential")
            .setNegativeButtonText("Use account password")
            .build()
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
    cipher: Cipher,
    secretKey: SecretKey,
    biometricPrompt: BiometricPrompt? = null,
    promptInfo: BiometricPrompt.PromptInfo? = null,
    updateUIState: KFunction1<LoginUIState.() -> LoginUIState, Unit>
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
    ) {
        Column(modifier = Modifier.align(Alignment.Center)) {
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
                modifier = Modifier,
                onClick = {
                    cipher.init(Cipher.ENCRYPT_MODE, secretKey)
                    promptInfo?.let {
                        biometricPrompt?.authenticate(
                            promptInfo,
                            BiometricPrompt.CryptoObject(cipher)
                        )
                    }

                }
            )
        }
    }
}

@Composable
fun EnrollBiometricButton(modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        Button(
            modifier = modifier.align(Alignment.Center),
            colors = ButtonDefaults.buttonColors().copy(containerColor = Color.Cyan), onClick = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    onClick()
                }
            }) {
            Text(
                text = "Enroll Biometric",
                color = Color.Black
            )
        }
    }
}


@Preview(showBackground = true)
@Composable
fun LoginPagePreview() {
    LoginPage(
        uiState = LoginUIState(),
        innerPadding = PaddingValues(),
        cipher = Cipher.getInstance(""),
        secretKey = object : SecretKey {
            override fun getAlgorithm(): String = ""

            override fun getFormat(): String = ""

            override fun getEncoded(): ByteArray = ByteArray(5)

        }, updateUIState = ::print

    )
}