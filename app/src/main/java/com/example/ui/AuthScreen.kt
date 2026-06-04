package com.example.ui

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(onUnlock: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity ?: return
    val prefs = context.getSharedPreferences("maco_settings", Context.MODE_PRIVATE)

    var savedPin by remember { mutableStateOf(prefs.getString("auth_pin", null)) }
    var enteredPin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }

    val isSettingPin = savedPin == null

    val biometricManager = BiometricManager.from(context)
    val canAuthenticate = biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS

    var showBiometricPrompt by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!isSettingPin && canAuthenticate) {
            showBiometricPrompt = true
        }
    }

    if (showBiometricPrompt) {
        LaunchedEffect(showBiometricPrompt) {
            val executor = ContextCompat.getMainExecutor(context)
            val biometricPrompt = BiometricPrompt(activity, executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        super.onAuthenticationError(errorCode, errString)
                        showBiometricPrompt = false
                    }

                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        super.onAuthenticationSucceeded(result)
                        showBiometricPrompt = false
                        onUnlock()
                    }

                    override fun onAuthenticationFailed() {
                        super.onAuthenticationFailed()
                    }
                })

            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle("Autentikasi Keamanan")
                .setSubtitle("Gunakan biometrik untuk membuka")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                .build()

            biometricPrompt.authenticate(promptInfo)
        }
    }

    val isLight = com.example.ui.theme.isLightThemeGlobal
    val bgCol = if (isLight) Color(0xFFF7F4F0) else Color(0xFF101014)
    val textPrimary = if (isLight) Color(0xFF2C2520) else Color(0xFFF5F5F5)
    val textMuted = if (isLight) Color(0xFF7A7067) else Color(0xFFA0A0AA)
    val brandOrange = if (isLight) Color(0xFFD35A11) else Color(0xFFFFB347)
    val inputBorder = if (isLight) Color(0xFFE2DDD5) else Color(0xFF282830)
    val buttonText = if (isLight) Color(0xFFFFFFFF) else Color(0xFF101014)
    val iconBg = if (isLight) Color(0xFFE2DDD5) else Color(0xFF282830)

    Surface(
        color = bgCol,
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = brandOrange,
                modifier = Modifier
                    .size(80.dp)
                    .padding(bottom = 16.dp)
            )

            Text(
                text = if (isSettingPin) "Buat PIN 6-Digit" else "Masukkan PIN",
                color = textPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Text(
                text = if (isSettingPin) "Atur PIN sederhana untuk melindungi keuangan Anda" else "Untuk keamanan Anda",
                color = textMuted,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 32.dp)
            )

            OutlinedTextField(
                value = enteredPin,
                onValueChange = { 
                    if (it.length <= 6 && it.all { char -> char.isDigit() }) {
                        enteredPin = it
                        errorMessage = ""
                    }
                },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                placeholder = { Text("PIN", color = textMuted) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = textPrimary,
                    unfocusedTextColor = textPrimary,
                    focusedBorderColor = brandOrange,
                    unfocusedBorderColor = inputBorder,
                ),
                modifier = Modifier.fillMaxWidth()
            )

            if (errorMessage.isNotEmpty()) {
                Text(
                    text = errorMessage,
                    color = Color.Red,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    if (enteredPin.length < 6) {
                        errorMessage = "PIN harus 6 digit"
                        return@Button
                    }
                    if (isSettingPin) {
                        prefs.edit().putString("auth_pin", enteredPin).apply()
                        savedPin = enteredPin
                        onUnlock()
                    } else {
                        if (enteredPin == savedPin) {
                            onUnlock()
                        } else {
                            errorMessage = "PIN salah"
                            enteredPin = ""
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = brandOrange)
            ) {
                Text(
                    if (isSettingPin) "Simpan PIN & Lanjut" else "Masuk",
                    color = buttonText,
                    fontWeight = FontWeight.Bold
                )
            }

            if (!isSettingPin && canAuthenticate) {
                Spacer(modifier = Modifier.height(32.dp))
                IconButton(
                    onClick = { showBiometricPrompt = true },
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(iconBg)
                ) {
                    Icon(
                        imageVector = Icons.Default.Face,
                        contentDescription = "Gunakan Biometrik",
                        tint = brandOrange,
                        modifier = Modifier.size(32.dp)
                    )
                }
                Text(
                    text = "Gunakan Biometrik",
                    color = textMuted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}
