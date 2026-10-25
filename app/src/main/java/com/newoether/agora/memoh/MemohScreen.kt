package com.newoether.agora.memoh

import com.newoether.agora.R

import androidx.compose.ui.platform.LocalContext

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
    val uiResources = LocalContext.current.resources
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
                uiResources.getString(R.string.memoh_ui_your_bots_in_a_native_android_chat),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(40.dp))

            if (vm.restoringSession) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text(uiResources.getString(R.string.memoh_ui_restoring_your_saved_session),
                        style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(16.dp))
            } else if (vm.sessionRestoreFailed) {
                OutlinedButton(onClick = vm::retrySessionRestore, enabled = idle) {
                    Text(uiResources.getString(R.string.memoh_ui_retry_saved_session))
                }
                Spacer(Modifier.height(16.dp))
            }

            OutlinedTextField(
                value = vm.login.server,
                onValueChange = { vm.login.server = it },
                enabled = idle,
                label = { Text(uiResources.getString(R.string.memoh_ui_api_address)) },
                singleLine = true,
                supportingText = {
                    Text(uiResources.getString(R.string.memoh_ui_official_cloud_https_app_memoh_net_self_hosted_your_server_address))
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
                    label = { Text(uiResources.getString(R.string.memoh_ui_email)) },
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
                        label = { Text(uiResources.getString(R.string.memoh_ui_email_code)) },
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
                        else Text(uiResources.getString(R.string.memoh_ui_send_code))
                    }
                } else {
                    Button(onClick = vm::loginWithCode, enabled = idle,
                        modifier = Modifier.fillMaxWidth().height(48.dp)) {
                        if (vm.login.signingIn) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Text(uiResources.getString(R.string.memoh_ui_verify_and_sign_in))
                    }
                    TextButton(onClick = vm::sendLoginCode, enabled = idle) {
                        Text(if (vm.login.sendingCode) uiResources.getString(R.string.memoh_ui_sending) else uiResources.getString(R.string.memoh_ui_resend_code))
                    }
                }
                if (vm.login.oauthProviders.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        uiResources.getString(R.string.memoh_ui_use_an_emailed_code_to_sign_in_here_browser_sign_in) +
                            vm.login.oauthProviders.joinToString(" / ") + uiResources.getString(R.string.memoh_ui_is_not_available_in_this_app_yet),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Spacer(Modifier.height(12.dp))
                Text(uiResources.getString(R.string.memoh_ui_this_server_uses_a_password_sign_in_memoh_cloud_itself_emails_a_code_ins),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = vm.login.username,
                    onValueChange = { vm.login.username = it },
                    enabled = idle,
                    label = { Text(uiResources.getString(R.string.memoh_ui_email_or_username)) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = vm.login.password,
                    onValueChange = { vm.login.password = it },
                    enabled = idle,
                    label = { Text(uiResources.getString(R.string.memoh_ui_password)) },
                    singleLine = true,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (passwordVisible) uiResources.getString(R.string.memoh_ui_hide_password) else uiResources.getString(R.string.memoh_ui_show_password)
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
                    else Text(uiResources.getString(R.string.memoh_ui_connect))
                }
            }

            Spacer(Modifier.height(32.dp))
            Text(
                uiResources.getString(R.string.memoh_ui_the_memoh_server_owns_every_conversation_and_tool_run_password_tokens_ar) +
                    uiResources.getString(R.string.memoh_ui_encrypted_on_this_device_an_email_code_session_ends_when_you_sign_out),
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
