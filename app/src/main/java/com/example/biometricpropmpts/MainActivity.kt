package com.example.biometricpropmpts

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import com.example.biometricpropmpts.R
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
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

@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    private lateinit var enrollLauncher: ActivityResultLauncher<Intent>
    private lateinit var keyguardLauncher: ActivityResultLauncher<Intent>
    val TAG = "MyBiometricMain"
    var isEnrollClicked = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BiometricPropmptsTheme {
                EnrollBiometric()
            }
        }
    }

    private fun isBiometricEnrolled(): Boolean {
        val biometricManager = BiometricManager.from(this)
        val canAuthenticate = biometricManager.canAuthenticate(BIOMETRIC_STRONG)
        return  canAuthenticate == BiometricManager.BIOMETRIC_SUCCESS
    }

    @Composable
    private fun EnrollBiometric() {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(listOf(Color(0xFFA87FFB), Color(0xFF7B42F6)))
                ),
            containerColor = Color.Transparent
        ) { innerPadding ->

            val viewModel: MainViewModel by viewModels()
            viewModel.isBiometricEnrolled = isBiometricEnrolled()

            enrollLauncher =
                rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                    if (result.resultCode == RESULT_OK)
                        showToastMessage(this, R.string.biometric_enrolled)
                    else
                        showToastMessage(this, R.string.enrollment_canceled)

                }

            keyguardLauncher =
                rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                    if (result.resultCode == RESULT_OK) {
                        if (isEnrollClicked) {
                            viewModel.encrypt(null)
                            isEnrollClicked = false
                        } else
                            viewModel.decrypt(null)

                    } else
                        showToastMessage(this, R.string.enrollment_canceled)
                }

            StylishLoginScreen(
                modifier = Modifier.padding(innerPadding),
                viewModel = viewModel,
                onEnrollClick = {
                    isEnrollClicked = true
                    checkBiometricAvailability(
                        onSuccessful = { viewModel.encrypt(::authenticate) }
                    )
                },
                onShowDecryptionClick = {
                    checkBiometricAvailability(
                        onSuccessful = { viewModel.decrypt(::authenticate) }
                    )
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
                    showToastMessage(this@MainActivity, R.string.failed_authentication, errString.toString())
                }

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onSucceedEncrypt?.let {
                        val encryptedPassword: ByteArray? =
                            result.cryptoObject?.cipher?.doFinal(uiState.password.text.toByteArray(Charset.defaultCharset()))

                        showToastMessage(this@MainActivity, R.string.succeed_authentication)

                        if (encryptedPassword != null)
                            onSucceedEncrypt(encryptedPassword)
                    }

                    onSucceedDecrypt?.let { onSucceedDecrypt(result) }
                }
            }

        )


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

    private fun checkBiometricAvailability(onSuccessful: () -> Unit) {
        val biometricManager = BiometricManager.from(this)
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            biometricManager.canAuthenticate(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
        else
            biometricManager.canAuthenticate() // ⚠️ Deprecated, but correct for API < 30


        when (result) {
            BiometricManager.BIOMETRIC_SUCCESS -> {
                Log.d("MY_APP_TAG", "App can authenticate using biometrics.")
                onSuccessful()
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
                } else
                    showDeviceCredentialPrompt()
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
            showToastMessage(this@MainActivity, R.string.no_lockScreen)
            Log.e("MY_APP_TAG", "Device doesn't have secure lock screen")
        }
    }
}


@Composable
fun StylishLoginScreen(
    modifier: Modifier = Modifier,
    viewModel: MainViewModel,
    onEnrollClick: () -> Unit,
    onShowDecryptionClick: () -> Unit,
) {
    val uiState = viewModel.uiState.collectAsState()
    val context = LocalContext.current

    Log.d("StylishLoginScreen: ", "Recompose")
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {

        LaunchedEffect(Unit) {
            viewModel.feedback.collect { messageResId ->
                showToastMessage(context, messageResId)
            }
        }

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
                value = uiState.value.username,
                onValueChange = { viewModel.updateUiState { copy(username = it) } },
                icon = Icons.Default.Person,
                placeholder = "Enter your username"
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Password field
            TextFieldWithIcon(
                label = "Password",
                value = uiState.value.password,
                onValueChange = { viewModel.updateUiState { copy(password = it) } },
                icon = Icons.Default.Lock,
                placeholder = "Enter your password",
                isPassword = true
            )

            Spacer(modifier = Modifier.height(24.dp))

            StyledButton(
                text = "Enroll Biometric",
                painterId = R.drawable.fingerprint_24dp,
                colorId = 0xFF7c3aed,
                onEnrollClick
            )

            Spacer(modifier = Modifier.height(16.dp))

            StyledButton(
                text = "Decrypt Biometric",
                painterId = R.drawable.enhanced_encryption_24dp,
                colorId = 0xFF84cc16,
                onShowDecryptionClick,
            )

            Spacer(modifier = Modifier.height(16.dp))

            DecryptedPassword(uiState = uiState.value)

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
fun DecryptedPassword(uiState: LoginUIState) {
    Text(
        text = "Decrypted Password:",
        color = Color(0xFFFFFFFF),
        fontSize = 14.sp,
        textAlign = TextAlign.Center
    )

    uiState.decryptedPassword.apply {
        if (this.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = this,
                color = Color(0xFFFFFFFF),
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
        }
    }

}

@Composable
fun StyledButton(text: String, painterId: Int, colorId: Long, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(colorId),
            contentColor = Color.White
        ),
        shape = RoundedCornerShape(20),
        elevation = ButtonDefaults.elevatedButtonElevation()

    ) {
        Icon(
            painter = painterResource(painterId),
            contentDescription = "Decrypt",
            modifier = Modifier.padding(end = 8.dp)
        )
        Text(text = text, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun TextFieldWithIcon(
    label: String,
    value:TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
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

private fun showToastMessage(context: Context, messageResId: Int, extraMessage: String? = null) {
    Toast.makeText(context, context.getString(messageResId, extraMessage), Toast.LENGTH_SHORT).show()
}

@Preview(showBackground = true)
@Composable
fun LoginPagePreview() {
//    StylishLoginScreen(
//        modifier = Modifier,
//        uiState = LoginUIState(),
//        updateUIState = ::print,
//        onEnrollClick = { /* Handle biometric enroll */ },
//        onShowDecryptionClick = {}
//    )
}