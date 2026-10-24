package com.newoether.agora.sandbox

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Shared Agora screens still compile against an optional sandbox display contract. */
class SandboxSnackbarSourceContractTest {
    @Test
    fun `the reusable shell keeps optional one shot outcome collection`() {
        val contract = sourceFile("app/src/main/java/com/newoether/agora/sandbox/SandboxManager.kt")
        val activity = sourceFile("app/src/main/java/com/newoether/agora/MainActivity.kt")
        val viewModel = sourceFile("app/src/main/java/com/newoether/agora/viewmodel/ChatViewModel.kt")
        val cleanup = viewModel.substringAfter("override fun onCleared() {")
            .substringBefore("/** Nullable on purpose")
        val collector = activity.substringAfter("viewModel.sandboxManager?.snackbarMessage?.collect { msg ->")
            .substringBefore("LaunchedEffect(Unit) {\n        var snackbarJob: Job? = null\n        viewModel.snackbarMessage.collect")
        assertTrue(contract.contains("val snackbarMessage: Flow<String>"))
        assertFalse(contract.contains("val snackbarMessage: StateFlow<String?>"))
        assertFalse(contract.contains("fun close()"))
        assertFalse(cleanup.contains("sandboxManager"))
        assertTrue(collector.contains("snackbarHostState.showSnackbar("))
        assertTrue(collector.contains("sandboxSnackbarJob?.cancel()"))
        assertTrue(collector.contains("sandboxSnackbarJob = launch {"))
        assertFalse(collector.contains("snackbarMessage.value"))
    }

    private fun sourceFile(relativePath: String): String {
        var directory = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
        repeat(8) {
            val candidate = File(directory, relativePath)
            if (candidate.isFile) return candidate.readText().replace("\r\n", "\n")
            directory = directory.parentFile ?: error("Reached filesystem root")
        }
        error("Unable to locate $relativePath")
    }
}
