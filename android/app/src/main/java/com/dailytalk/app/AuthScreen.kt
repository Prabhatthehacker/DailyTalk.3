package com.dailytalk.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private val Sun = Color(0xFFFFBE3D)
private val Cocoa = Color(0xFF3A2A22)
private val OrangeDeep = Color(0xFFC76A00)
private val WaveColor = Color(0xFFFFE9B8)

/** The two smiling speech bubbles (res/drawable-nodpi/dailytalk_logo.png). */
@Composable
fun BrandMark(size: androidx.compose.ui.unit.Dp = 72.dp) {
    Image(painterResource(R.drawable.dailytalk_logo), contentDescription = "Daily Talk", modifier = Modifier.size(size))
}

/** Bubbles + "Daily Talk" + tagline (res/drawable-nodpi/dailytalk_lockup.png). */
@Composable
fun Lockup(width: androidx.compose.ui.unit.Dp = 240.dp) {
    Image(
        painterResource(R.drawable.dailytalk_lockup), contentDescription = "Daily Talk. Small messages. Big connections.",
        modifier = Modifier.width(width).height(width * 567f / 640f),
    )
}

@Composable
fun AuthScreen() {
    var mode by remember { mutableStateOf("in") }
    var name by remember { mutableStateOf("") }; var email by remember { mutableStateOf("") }; var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }; var info by remember { mutableStateOf<String?>(null) }; var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun go(m: String) { mode = m; error = null; info = null }
    fun submit() {
        error = null; info = null; busy = true
        scope.launch {
            try {
                when (mode) {
                    "up" -> Repo.register(name, email, password)
                    "in" -> Repo.login(email, password)
                    else -> { Repo.resetPassword(email); info = "If an account exists for this email, a reset link is on its way." }
                }
            } catch (e: Exception) { error = Repo.authMessage(e) } finally { busy = false }
        }
    }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = Sun, unfocusedBorderColor = Palette.Line, focusedContainerColor = Color.White, unfocusedContainerColor = Color.White,
        cursorColor = Cocoa, focusedLeadingIconColor = Cocoa, unfocusedLeadingIconColor = Cocoa,
    )
    val fieldShape = RoundedCornerShape(16.dp)

    Box(Modifier.fillMaxSize().background(Palette.Cream)) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 90.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Lockup(230.dp)
            Column(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 14.dp)) {
                Text(
                    when (mode) { "in" -> "Welcome Back!"; "up" -> "Join Daily Talk"; else -> "Reset password" },
                    style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, color = Cocoa,
                )
                Text(
                    when (mode) {
                        "in" -> "Sign in to continue to Daily Talk"
                        "up" -> "Create your account. You get your own Daily Talk number."
                        else -> "Enter your email and we will send a reset link."
                    },
                    color = Palette.Muted,
                )
            }
            if (mode == "up") OutlinedTextField(
                name, { name = it.take(60) }, Modifier.fillMaxWidth().padding(bottom = 10.dp), placeholder = { Text("Your name") }, singleLine = true,
                leadingIcon = { Icon(Icons.Default.Person, null) }, shape = fieldShape, colors = fieldColors,
            )
            OutlinedTextField(
                email, { email = it }, Modifier.fillMaxWidth(), placeholder = { Text("Email address") }, singleLine = true,
                leadingIcon = { Icon(Icons.Default.Email, null) }, shape = fieldShape, colors = fieldColors,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            )
            if (mode != "reset") OutlinedTextField(
                password, { password = it }, Modifier.fillMaxWidth().padding(top = 10.dp), placeholder = { Text("Password") }, singleLine = true,
                leadingIcon = { Icon(Icons.Default.Lock, null) },
                trailingIcon = { TextButton({ showPassword = !showPassword }) { Text(if (showPassword) "Hide" else "Show", color = Cocoa) } },
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), shape = fieldShape, colors = fieldColors,
            )
            if (mode == "in") TextButton({ go("reset") }, Modifier.align(Alignment.End)) { Text("Forgot password?", color = OrangeDeep) }
            error?.let { Text(it, color = Palette.Danger, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) }
            info?.let { Text(it, color = Palette.BrownDeep, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) }

            Button(
                onClick = { submit() }, enabled = !busy && email.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(54.dp).padding(top = 0.dp),
                shape = RoundedCornerShape(50), colors = ButtonDefaults.buttonColors(containerColor = Sun, contentColor = Cocoa, disabledContainerColor = Sun.copy(alpha = 0.5f), disabledContentColor = Cocoa),
            ) { Text(if (busy) "Please wait…" else when (mode) { "in" -> "Sign In  →"; "up" -> "Create account  →"; else -> "Send reset link" }, fontWeight = FontWeight.Bold) }

            Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (mode == "in") "Don’t have an account?" else if (mode == "up") "Already have an account?" else "Remembered it?", color = Palette.Muted)
                TextButton({ go(if (mode == "in") "up" else "in") }) { Text(if (mode == "in") "Sign Up" else "Sign In", color = OrangeDeep, fontWeight = FontWeight.Bold) }
            }
        }
        Canvas(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(70.dp)) {
            val w = size.width; val h = size.height
            drawPath(Path().apply {
                moveTo(0f, h * 0.45f)
                cubicTo(w * 0.15f, h * 0.05f, w * 0.30f, h * 0.10f, w * 0.50f, h * 0.45f)
                cubicTo(w * 0.72f, h * 0.80f, w * 0.88f, h * 0.60f, w, h * 0.20f)
                lineTo(w, h); lineTo(0f, h); close()
            }, WaveColor)
        }
    }
}

@Composable
fun SetupNeeded() {
    Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        BrandMark(96.dp)
        Text("Almost there", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
        Text("This build has no Firebase settings. Add them to android/local.properties (see README) and rebuild.", color = Palette.Muted, modifier = Modifier.padding(top = 8.dp))
    }
}
