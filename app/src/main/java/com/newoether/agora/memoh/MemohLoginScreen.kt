package com.newoether.agora.memoh

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.newoether.agora.ui.theme.ChatType

/**
 * Sign-in surface. Cloud emails a code; other deployments post username/password.
 * Kept beside [MemohScreen] to hold each file inside the 800-line source gate.
 */
@Composable
internal fun MemohLogin(vm: MemohViewModel) {
    LaunchedEffect(vm.login.server) { vm.probeLoginMethod() }
    var passwordVisible by remember { mutableStateOf(false) }
    val idle = !vm.login.signingIn && !vm.login.sendingCode
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            Text("Memoh", style = ChatType.brandTitle)
            Text("Your bots, in a native Android chat.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(value = vm.login.server, onValueChange = { vm.login.server = it }, enabled = idle,
                label = { Text("API address") }, singleLine = true,
                supportingText = {
                    Text("Official Cloud: https://app.memoh.net — self-hosted: your server address")
                },
                modifier = Modifier.fillMaxWidth())
            if (vm.login.method == MemohLoginMethod.EmailCode) {
                Text("Memoh Cloud signs in with a code sent to your email.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(value = vm.login.email, onValueChange = { vm.login.email = it }, enabled = idle,
                    label = { Text("Email") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth())
                if (vm.login.codeSent) {
                    OutlinedTextField(value = vm.login.code, onValueChange = { vm.login.code = it }, enabled = idle,
                        label = { Text("Email code") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth())
                }
                if (vm.error.isNotEmpty()) Text(vm.error, color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall)
                if (!vm.login.codeSent) {
                    Button(onClick = vm::sendLoginCode, enabled = !vm.login.sendingCode && !vm.login.signingIn,
                        modifier = Modifier.fillMaxWidth()) {
                        if (vm.login.sendingCode) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Text("Send code")
                    }
                } else {
                    Button(onClick = vm::loginWithCode, enabled = idle,
                        modifier = Modifier.fillMaxWidth()) {
                        if (vm.login.signingIn) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Text("Verify and sign in")
                    }
                    TextButton(onClick = vm::sendLoginCode, enabled = !vm.login.sendingCode && !vm.login.signingIn) {
                        Text(if (vm.login.sendingCode) "Sending" else "Resend code")
                    }
                }
                if (vm.login.oauthProviders.isNotEmpty()) {
                    Text(
                        "Use an emailed code to sign in here. Browser sign-in (" +
                            vm.login.oauthProviders.joinToString(" / ") + ") is not available in this app yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Text("This server uses a password sign-in. Memoh Cloud itself emails a code instead.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(value = vm.login.username, onValueChange = { vm.login.username = it }, enabled = idle,
                    label = { Text("Email or username") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = vm.login.password, onValueChange = { vm.login.password = it }, enabled = idle,
                    label = { Text("Password") }, singleLine = true,
                    visualTransformation = if (passwordVisible) VisualTransformation.None
                    else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Default.VisibilityOff
                                else Icons.Default.Visibility,
                                contentDescription = if (passwordVisible) "Hide password" else "Show password"
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth())
                if (vm.error.isNotEmpty()) Text(vm.error, color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall)
                Button(onClick = vm::login, enabled = !vm.login.signingIn,
                    modifier = Modifier.fillMaxWidth()) {
                    if (vm.login.signingIn) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Text("Connect")
                }
            }
            Text(
                "The Memoh server owns every conversation and tool run. Password tokens are " +
                    "encrypted on this device; an email-code session ends when you sign out.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
