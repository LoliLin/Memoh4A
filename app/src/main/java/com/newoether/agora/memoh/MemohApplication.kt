package com.newoether.agora.memoh

import android.app.Application
import androidx.work.Configuration
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.newoether.agora.util.CrashReporter
import java.io.File
import okhttp3.OkHttpClient

/**
 * Process entry for the remote Memoh client.
 *
 * Memoh's execution and durable storage live on the server, so startup deliberately does not
 * build Agora's container, Room database, tokenizer or embedded llama/PRoot runtimes. Only the
 * crash recorder and the image loader are installed here.
 *
 * [Configuration.Provider] backs the removal of `WorkManagerInitializer` from the merged
 * manifest: nothing in the Memoh path schedules background work, but any shared upstream class
 * that reaches for `WorkManager.getInstance` still initializes on demand instead of throwing
 * "WorkManager is not initialized properly".
 */
class MemohApplication : Application(), Configuration.Provider, ImageLoaderFactory {
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().build()

    override fun onCreate() {
        super.onCreate()
        CrashReporter.install(this)
    }

    /**
     * One loader for the process, with three properties the app needs:
     *
     * - Avatars and media are private files on the configured server, so requests must carry
     *   the current session. The client is resolved per call through [MemohImageHttp], which
     *   hands Coil whichever OkHttp client the active identity owns right now.
     * - Images survive a restart: a 5% disk slice means an avatar is not re-downloaded every
     *   cold start.
     * - Memory and disk caches are bounded, so a long-lived process cannot grow the picture
     *   cache without limit.
     */
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .okHttpClient { MemohImageHttp.client() }
        .memoryCache {
            MemoryCache.Builder(this)
                .maxSizePercent(0.15)
                .build()
        }
        .diskCache {
            DiskCache.Builder()
                .directory(File(cacheDir, "memoh_images").apply { mkdirs() })
                .maxSizeBytes(64L * 1024 * 1024)
                .build()
        }
        .crossfade(true)
        .respectCacheHeaders(false)
        .build()
}

/**
 * Resolves the OkHttp client Coil should use for the next request.
 *
 * The transport swaps its client whenever the identity or team changes; a loader that captured
 * one client at startup would keep sending the first account's cookie to the second account.
 */
internal object MemohImageHttp {
    @Volatile
    private var provider: () -> OkHttpClient? = { null }
    private val fallback by lazy { OkHttpClient() }

    fun install(resolve: () -> OkHttpClient?) {
        provider = resolve
    }

    fun client(): OkHttpClient = provider() ?: fallback
}
