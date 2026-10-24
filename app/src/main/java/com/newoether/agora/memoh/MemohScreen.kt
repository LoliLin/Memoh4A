package com.newoether.agora.memoh

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import com.newoether.agora.model.ChatConversation
import com.newoether.agora.model.StableMessageList
import com.newoether.agora.ui.chat.ChatDrawerHost
import com.newoether.agora.ui.chat.ChatTopBar
import com.newoether.agora.ui.chat.MessageList
import com.newoether.agora.ui.chat.bottombar.ChatComposerLayout
import com.newoether.agora.ui.chat.bottombar.ChatComposerSurface
import com.newoether.agora.ui.chat.bottombar.ComposerControlGroup
import com.newoether.agora.ui.chat.bottombar.ComposerSendButton
import com.newoether.agora.ui.chat.rememberChatDrawerState
import com.newoether.agora.ui.components.AgoraDropdownMenu
import com.newoether.agora.ui.components.AgoraDropdownMenuItem
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.m3.Markdown
import kotlinx.coroutines.launch
import com.newoether.agora.ui.common.LocalAgoraHaptics

@Composable
private fun MemohLogin(vm: MemohViewModel) {
    LaunchedEffect(vm.login.server) { vm.probeLoginMethod() }
    var passwordVisible by remember { mutableStateOf(false) }
    val idle = !vm.login.signingIn && !vm.login.sendingCode && !vm.restoringSession
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            Text("Memoh", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold)
            Text("Your bots, in a native Android chat.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            if (vm.restoringSession) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Text("Restoring your saved session…")
            } else if (vm.sessionRestoreFailed) {
                OutlinedButton(onClick = vm::retrySessionRestore, enabled = idle) {
                    Text("Retry saved session")
                }
            }
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
                    Button(onClick = vm::sendLoginCode, enabled = idle,
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
                    TextButton(onClick = vm::sendLoginCode, enabled = idle) {
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
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (passwordVisible) "Hide password" else "Show password"
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth())
                if (vm.error.isNotEmpty()) Text(vm.error, color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall)
                Button(onClick = vm::login, enabled = idle,
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
    key(vm.teams.selectedId) { MemohConversationScreen(vm) }
}
