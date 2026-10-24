package com.newoether.agora.memoh

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

/**
 * Sign-in surface styled after Agora's onboarding: an oversized brand headline over a
 * centered, width-capped form column. The server field stays first because self-hosted
 * users must point somewhere before any credential makes sense.
 */
@Composable
private fun MemohLogin(vm: MemohViewModel) {
    LaunchedEffect(vm.login.server) { vm.probeLoginMethod() }
    var passwordVisible by remember { mutableStateOf(false) }
    val idle = !vm.login.signingIn && !vm.login.sendingCode && !vm.restoringSession

    Box(
        Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 48.dp)
                .widthIn(max = 420.dp),
        ) {
            Spacer(Modifier.height(32.dp))
            Text(
                "Memoh",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Your bots, in a native Android chat.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(40.dp))

            if (vm.restoringSession) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text("Restoring your saved session…",
                        style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(16.dp))
            } else if (vm.sessionRestoreFailed) {
                OutlinedButton(onClick = vm::retrySessionRestore, enabled = idle) {
                    Text("Retry saved session")
                }
                Spacer(Modifier.height(16.dp))
            }

            OutlinedTextField(
                value = vm.login.server,
                onValueChange = { vm.login.server = it },
                enabled = idle,
                label = { Text("API address") },
                singleLine = true,
                supportingText = {
                    Text("Official Cloud: https://app.memoh.net — self-hosted: your server address")
                },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            )

            if (vm.login.method == MemohLoginMethod.EmailCode) {
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = vm.login.email,
                    onValueChange = { vm.login.email = it },
                    enabled = idle,
                    label = { Text("Email") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (vm.login.codeSent) {
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = vm.login.code,
                        onValueChange = { vm.login.code = it },
                        enabled = idle,
                        label = { Text("Email code") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (vm.error.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(vm.error, color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(20.dp))
                if (!vm.login.codeSent) {
                    Button(onClick = vm::sendLoginCode, enabled = idle,
                        modifier = Modifier.fillMaxWidth().height(48.dp)) {
                        if (vm.login.sendingCode) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Text("Send code")
                    }
                } else {
                    Button(onClick = vm::loginWithCode, enabled = idle,
                        modifier = Modifier.fillMaxWidth().height(48.dp)) {
                        if (vm.login.signingIn) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Text("Verify and sign in")
                    }
                    TextButton(onClick = vm::sendLoginCode, enabled = idle) {
                        Text(if (vm.login.sendingCode) "Sending" else "Resend code")
                    }
                }
                if (vm.login.oauthProviders.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Use an emailed code to sign in here. Browser sign-in (" +
                            vm.login.oauthProviders.joinToString(" / ") + ") is not available in this app yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Spacer(Modifier.height(12.dp))
                Text("This server uses a password sign-in. Memoh Cloud itself emails a code instead.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = vm.login.username,
                    onValueChange = { vm.login.username = it },
                    enabled = idle,
                    label = { Text("Email or username") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = vm.login.password,
                    onValueChange = { vm.login.password = it },
                    enabled = idle,
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (passwordVisible) "Hide password" else "Show password"
                            )
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (vm.error.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(vm.error, color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(20.dp))
                Button(onClick = vm::login, enabled = idle,
                    modifier = Modifier.fillMaxWidth().height(48.dp)) {
                    if (vm.login.signingIn) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Text("Connect")
                }
            }

            Spacer(Modifier.height(32.dp))
            Text(
                "The Memoh server owns every conversation and tool run. Password tokens are " +
                    "encrypted on this device; an email-code session ends when you sign out.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MemohScreen(vm: MemohViewModel) {
    if (!vm.signedIn) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            MemohLogin(vm)
        }
        return
    }
    if (vm.teams.enabled && vm.teams.selectedId.isEmpty()) {
        MemohTeamSetup(vm)
        return
    }
    androidx.compose.runtime.key(vm.teams.selectedId) { MemohConversationScreen(vm) }
}
