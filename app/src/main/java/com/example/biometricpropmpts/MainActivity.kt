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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
                    onSucceedEncrypt?.let {
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
                onClick = {
                    enrollClicked()
                }
            )

            EnrollBiometricButton(
                "Decrypt Pass",
                modifier = Modifier
                    .padding(8.dp)
                    .align(Alignment.CenterHorizontally),
                onClick = {
                    decryptClicked()
                }
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
    Button(
        modifier = modifier, onClick = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                onClick()
            }
        }) {
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onPrimary
        )
    }
}

@Composable
fun StylishLoginScreen(
    username: String,
    password: String,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onEnrollClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    listOf(Color(0xFFA87FFB), Color(0xFF7B42F6))
                )
            )
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 400.dp)
                .background(
                    color = Color.White.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(20.dp)
                )
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Welcome Back",
                fontSize = 28.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            // Username field
            TextFieldWithIcon(
                label = "Username",
                value = username,
                onValueChange = onUsernameChange,
                icon = Icons.Default.Person,
                placeholder = "Enter your username"
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Password field
            TextFieldWithIcon(
                label = "Password",
                value = password,
                onValueChange = onPasswordChange,
                icon = Icons.Default.Lock,
                placeholder = "Enter your password",
                isPassword = true
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Enroll Button
            Button(
                onClick = onEnrollClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF7B42F6),
                    contentColor = Color.White
                ),
                elevation = ButtonDefaults.elevatedButtonElevation()
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Biometric",
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text(text = "Enroll Biometric", fontWeight = FontWeight.Medium)
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "By continuing, you agree to our Terms of Service.",
                color = Color(0xFFCCCCCC),
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )

        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(top = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Forgot password?",
                fontSize = 14.sp,
                color = Color(0xFFE0CFFF),
                modifier = Modifier.clickable { /* TODO: Handle click */ },
                textDecoration = TextDecoration.Underline
            )
        }
    }
}

@Composable
fun TextFieldWithIcon(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    icon: ImageVector,
    placeholder: String,
    isPassword: Boolean = false
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            leadingIcon = {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = Color.LightGray
                )
            },
            placeholder = {
                Text(text = placeholder, color = Color.LightGray)
            },
            singleLine = true,
            visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White.copy(alpha = 0.2f), shape = RoundedCornerShape(10.dp)),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = Color.Transparent,
                focusedBorderColor = Color(0xFFB693FD),
                cursorColor = Color.White,
                focusedLabelColor = Color.White
            ),
            textStyle = TextStyle(color = Color.White, fontSize = 14.sp)
        )
    }
}



@Preview(showBackground = true)
@Composable
fun LoginPagePreview() {
//    LoginPage(
//        uiState = LoginUIState(),
//        innerPadding = PaddingValues(),
//        updateUIState = ::print,
//        enrollClicked = {}
//    )

        var username by remember { mutableStateOf("asdf") }
        var password by remember { mutableStateOf("123") }

        StylishLoginScreen(
            username = username,
            password = password,
            onUsernameChange = { username = it },
            onPasswordChange = { password = it },
            onEnrollClick = { /* Handle biometric enroll */ }
        )

}