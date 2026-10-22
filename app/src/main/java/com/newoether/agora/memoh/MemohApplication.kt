package com.newoether.agora.memoh

import android.app.Application
import androidx.work.Configuration
import com.newoether.agora.util.CrashReporter

/**
 * Process entry for the remote Memoh client.
 *
 * Memoh's execution and durable storage live on the server, so startup deliberately does not
 * build Agora's container, Room database, tokenizer or embedded llama/PRoot runtimes. Only the
 * crash recorder is installed here.
 *
 * [Configuration.Provider] backs the removal of `WorkManagerInitializer` from the merged
 * manifest: nothing in the Memoh path schedules background work, but any shared upstream class
 * that reaches for `WorkManager.getInstance` still initializes on demand instead of throwing
 * "WorkManager is not initialized properly".
 */
class MemohApplication : Application(), Configuration.Provider {
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().build()

    override fun onCreate() {
        super.onCreate()
        CrashReporter.install(this)
    }
}
