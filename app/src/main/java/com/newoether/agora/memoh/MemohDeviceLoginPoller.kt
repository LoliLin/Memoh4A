package com.newoether.agora.memoh

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal enum class DeviceLoginPhase { Starting, Pending, Authorized, Expired, Failed, TimedOut, Unreachable }

internal data class DeviceLoginState(
    val agentId: String,
    val agentName: String,
    val phase: DeviceLoginPhase,
    val message: String,
    val login: MemohDeviceLogin? = null,
) {
    val pending: Boolean get() = phase == DeviceLoginPhase.Starting || phase == DeviceLoginPhase.Pending
}

/** One bot/API identity owns this helper. Closing or disposing invalidates even late responses. */
internal class MemohDeviceLoginPoller(
    private val scope: CoroutineScope,
    private val start: suspend (String) -> MemohDeviceLogin,
    private val poll: suspend (String, String) -> MemohDeviceLoginResult,
    private val cancel: suspend (String, String) -> Unit,
    private val refresh: suspend () -> Unit,
    private val isIdentityCurrent: () -> Boolean = { true },
    private val nowMillis: () -> Long = { System.nanoTime() / 1_000_000 },
) {
    private val mutableState = MutableStateFlow<DeviceLoginState?>(null)
    val state = mutableState.asStateFlow()
    private var generation = 0
    private var job: Job? = null

    fun start(agentId: String, agentName: String) {
        if (mutableState.value != null || !isIdentityCurrent()) return
        val attempt = ++generation
        mutableState.value = DeviceLoginState(agentId, agentName, DeviceLoginPhase.Starting, "Starting device sign-in…")
        job = scope.launch {
            fun current() = generation == attempt && isIdentityCurrent()
            fun update(phase: DeviceLoginPhase, message: String, login: MemohDeviceLogin? = mutableState.value?.login) {
                if (current()) mutableState.value = DeviceLoginState(agentId, agentName, phase, message, login)
            }
            val login = try {
                start(agentId).also { currentCoroutineContext().ensureActive() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                update(DeviceLoginPhase.Failed, "Device login failed to start: ${e.message ?: "unknown error"}")
                return@launch
            }
            if (!current()) return@launch
            update(DeviceLoginPhase.Pending, "Waiting for authorization in browser…", login)
            val startedAt = nowMillis()
            var failures = 0
            while (current()) {
                val remaining = TIMEOUT_MS - (nowMillis() - startedAt)
                if (remaining <= 0) {
                    update(DeviceLoginPhase.TimedOut, "Timed out waiting for authorization. Start the login again.")
                    return@launch
                }
                delay(minOf(POLL_MS, remaining))
                if (!current()) return@launch
                if (nowMillis() - startedAt >= TIMEOUT_MS) continue
                val result = try {
                    poll(agentId, login.loginId).also { currentCoroutineContext().ensureActive() }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    currentCoroutineContext().ensureActive()
                    if (!current()) return@launch
                    failures++
                    if (failures >= MAX_FAILURES) {
                        update(DeviceLoginPhase.Unreachable,
                            "Cannot reach the server for authorization (${e.message ?: "network error"}). Start the login again.")
                        return@launch
                    }
                    update(DeviceLoginPhase.Pending, "Waiting for authorization… (retrying after a network error)")
                    continue
                }
                if (!current()) return@launch
                failures = 0
                when (result.status) {
                    "success", "authorized" -> {
                        update(DeviceLoginPhase.Authorized, "Authorized successfully!")
                        // Authorization is final; refreshing the catalog must never restart polling.
                        try {
                            refresh()
                            currentCoroutineContext().ensureActive()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            currentCoroutineContext().ensureActive()
                            update(DeviceLoginPhase.Authorized,
                                "Authorized successfully! Could not refresh agents: ${e.message ?: "unknown error"}")
                        }
                        return@launch
                    }
                    "expired" -> {
                        update(DeviceLoginPhase.Expired, "Authorization expired." + result.messageSuffix())
                        return@launch
                    }
                    "failed" -> {
                        update(DeviceLoginPhase.Failed, "Authorization failed." + result.messageSuffix())
                        return@launch
                    }
                    else -> update(DeviceLoginPhase.Pending, "Waiting for authorization in browser…")
                }
            }
        }
    }

    fun close() {
        val previous = mutableState.value
        dispose()
        if (previous?.phase == DeviceLoginPhase.Pending && isIdentityCurrent()) {
            val login = previous.login ?: return
            scope.launch {
                try {
                    if (isIdentityCurrent()) cancel(previous.agentId, login.loginId)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // The local attempt is already closed, even if the server cannot be reached.
                }
            }
        }
    }

    fun dispose() {
        generation++
        job?.cancel()
        job = null
        mutableState.value = null
    }

    private fun MemohDeviceLoginResult.messageSuffix() = message.takeIf { it.isNotBlank() }?.let { " $it" }.orEmpty()

    private companion object {
        const val POLL_MS = 2_500L
        const val TIMEOUT_MS = 15 * 60 * 1000L
        const val MAX_FAILURES = 8
    }
}
