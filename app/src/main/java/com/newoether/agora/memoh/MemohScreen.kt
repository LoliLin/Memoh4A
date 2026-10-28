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
import androidx.compose.foundation.Image
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
    val motion = com.newoether.agora.ui.motion.LocalAgoraMotionPolicy.current

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
                .padding(horizontal = 24.dp, vertical = 40.dp)
                .widthIn(max = 420.dp),
        ) {
            Spacer(Modifier.height(24.dp))
            // The mark is the wordmark's companion, not an ornament: it sits centred above the
            // typed product name, at the size Agora gives its welcome title.
            Image(
                painter = painterResource(R.drawable.memoh_logo),
                contentDescription = null,
                modifier = Modifier.size(56.dp),
            )
            Spacer(Modifier.height(18.dp))
            // "Memoh for Android" types itself in once, the way Agora opens a surface.
            com.newoether.agora.ui.components.TypewriterText(
                text = uiResources.getString(R.string.memoh_ui_memoh_for_android),
                animationKey = "memoh-login-brand",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                typeSpeedMs = 70,
                initialDelayMs = 220,
                animate = motion.allowContinuousMotion,
                mode = com.newoether.agora.ui.components.TypewriterMode.TEXT_GRADIENT,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                uiResources.getString(R.string.memoh_ui_your_bots_in_a_native_android_chat),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(28.dp))

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

            // Agora's onboarding card: 28 dp on surfaceContainer, 2 dp tonal lift. The form
            // reads as one object instead of a stack of fields floating on the background.
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 22.dp)) {
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
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.fillMaxWidth().height(48.dp)) {
                        if (vm.login.sendingCode) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Text(uiResources.getString(R.string.memoh_ui_send_code))
                    }
                } else {
                    Button(onClick = vm::loginWithCode, enabled = idle,
                        shape = RoundedCornerShape(50),
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
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.fillMaxWidth().height(48.dp)) {
                    if (vm.login.signingIn) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Text(uiResources.getString(R.string.memoh_ui_connect))
                }
            }
                }
            }

            Spacer(Modifier.height(24.dp))
            Text(
                uiResources.getString(R.string.memoh_ui_the_memoh_server_owns_every_conversation_and_tool_run_password_tokens_ar) +
                    uiResources.getString(R.string.memoh_ui_encrypted_on_this_device_an_email_code_session_ends_when_you_sign_out),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

/**
 * What a returning launch shows while the saved session is verified: the same mark and
 * centred column as the entry surface, with the product name typed once. No fields, no
 * buttons — and once the server refuses the session, a retry instead of a silent rebuild.
 */
@Composable
private fun MemohRestoring(vm: MemohViewModel) {
    val uiResources = LocalContext.current.resources
    val motion = com.newoether.agora.ui.motion.LocalAgoraMotionPolicy.current
    Box(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = painterResource(R.drawable.memoh_logo),
                contentDescription = null,
                modifier = Modifier.size(56.dp),
            )
            Spacer(Modifier.height(18.dp))
            com.newoether.agora.ui.components.TypewriterText(
                text = uiResources.getString(R.string.memoh_ui_memoh_for_android),
                animationKey = "memoh-restore-brand",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                typeSpeedMs = 60,
                animate = motion.allowContinuousMotion,
                mode = com.newoether.agora.ui.components.TypewriterMode.TEXT_GRADIENT,
            )
            Spacer(Modifier.height(20.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (vm.restoringSession) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                }
                Text(
                    uiResources.getString(
                        if (vm.restoringSession) R.string.memoh_ui_restoring_your_saved_session
                        else R.string.memoh_ui_retry_saved_session,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (vm.sessionRestoreFailed) {
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = vm::retrySessionRestore) {
                    Text(uiResources.getString(R.string.memoh_ui_retry))
                }
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = vm::logout) {
                    Text(uiResources.getString(R.string.memoh_ui_sign_out))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MemohScreen(vm: MemohViewModel) {
    // Every avatar in this tree needs the signed-in client to read a private file.
    androidx.compose.runtime.CompositionLocalProvider(LocalMemohApi provides vm.api) {
        MemohScreenContent(vm)
    }
}

@Composable
private fun MemohScreenContent(vm: MemohViewModel) {
    if (!vm.signedIn) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            // Both flags are decided synchronously, so a first launch never renders this at
            // all: `sessionOnDisk` is known at construction, and a failed check deliberately
            // keeps the retry surface. Keying off `restoringSession` would flash the splash
            // for every launch, including one with nothing saved.
            if (vm.sessionOnDisk || vm.sessionRestoreFailed) MemohRestoring(vm) else MemohLogin(vm)
        }
        return
    }
    // A returning user already has a team on disk, or none at all. Only surface team setup
    // once the server's membership page has actually arrived and left nothing selected —
    // otherwise a cold start flashes the picker before the chat it is about to open.
    if (vm.teams.enabled && vm.teams.loaded && vm.teams.selectedId.isEmpty()) {
        MemohTeamSetup(vm)
        return
    }
    androidx.compose.runtime.key(vm.teams.selectedId) { MemohConversationScreen(vm) }
}
