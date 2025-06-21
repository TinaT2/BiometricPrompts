package com.example.biometricpropmpts

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.example.biometricpropmpts.ui.theme.BiometricPropmptsTheme
import java.util.concurrent.Executor

class MainActivity : FragmentActivity() {
    private lateinit var enrollLauncher: ActivityResultLauncher<Intent>
    private var biometricPrompt: BiometricPrompt? = null
    private lateinit var executor: Executor
    private lateinit var promptInfo: BiometricPrompt.PromptInfo

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enrollLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == Activity.RESULT_OK) {
                    Toast.makeText(this, "Biometric enrolled!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Enrollment canceled.", Toast.LENGTH_SHORT).show()

                }

            }

        checkBiometricAvailability(onSuccessful = {
            executor = ContextCompat.getMainExecutor(this)
            biometricPrompt = BiometricPrompt(
                this, executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        super.onAuthenticationError(errorCode, errString)
                        Toast.makeText(
                            applicationContext,
                            "Authentication error: $errString", Toast.LENGTH_SHORT
                        )
                            .show()

                    }

                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        super.onAuthenticationSucceeded(result)
                        Toast.makeText(applicationContext, "Authentication Succeed", Toast.LENGTH_SHORT)
                            .show()
                    }
                })
            promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle("Biometric login for my app")
                .setSubtitle("Log in using your biometric credential")
                .setNegativeButtonText("Use account password")
                .build()
        })




        enableEdgeToEdge()
        setContent {
            BiometricPropmptsTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Greeting(
                        name = "Android",
                        modifier = Modifier.padding(innerPadding),
                        onClick = { biometricPrompt?.authenticate(promptInfo)}
                    )
                }
            }
        }
    }

    @Composable
    fun Greeting(name: String, modifier: Modifier = Modifier,onClick: () -> Unit) {
        Box(Modifier.fillMaxSize()) {
            Button(modifier = modifier.align(Alignment.Center), onClick = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                   onClick()
                }
            }) {
                Text(
                    text = "Enroll Biometric",
                    modifier = Modifier
                        .background(color = Color.Cyan, shape = RoundedCornerShape(10))
                )
            }
        }
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

    private fun authenticate() {


    }
}


@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    BiometricPropmptsTheme {
//        Greeting("Android")
    }
}