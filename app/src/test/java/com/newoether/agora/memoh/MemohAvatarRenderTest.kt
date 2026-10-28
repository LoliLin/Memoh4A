package com.newoether.agora.memoh

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.core.app.ApplicationProvider
import com.newoether.agora.ui.theme.AgoraTheme
import java.io.IOException
import java.util.Base64
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Dispatcher
import okhttp3.Request
import okhttp3.Response
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Exercise the real Application's image loader with the scoped Memoh transport. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = MemohApplication::class, qualifiers = "w411dp-h891dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MemohAvatarRenderTest {
    @get:Rule val compose = createComposeRule()

    @Test fun untaggedImageRequestsAreRejectedByTheScopedClient() {
        MemohCloudServer().use { server ->
            val api = MemohApi().apply { configure(server.gateway, "test-bearer") }
            val uncaught = AtomicReference<Throwable>()
            val reported = CountDownLatch(1)
            val executor = Executors.newSingleThreadExecutor { work ->
                Thread(work, "untagged-image-regression").apply {
                    isDaemon = true
                    uncaughtExceptionHandler = Thread.UncaughtExceptionHandler { _, failure ->
                        uncaught.set(failure)
                        reported.countDown()
                    }
                }
            }
            try {
                val client = api.scope.identity.client.newBuilder().dispatcher(Dispatcher(executor)).build()
                client.newCall(Request.Builder().url("${server.origin}/avatar.png").build()).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) = Unit
                    override fun onResponse(call: Call, response: Response) = response.close()
                })
                assertTrue("the old image request did not reach the uncaught exception handler", reported.await(5, TimeUnit.SECONDS))
                assertTrue(uncaught.get() is IllegalStateException)
                assertEquals("Memoh requests must capture their scope", uncaught.get().message)
            } finally {
                executor.shutdownNow()
            }
        }
    }

    @Test fun privateAvatarUsesScopedAuthenticatedDownloadWithoutCrashingTheImageLoader() {
        MemohCloudServer().use { server ->
            val app = ApplicationProvider.getApplicationContext<Application>()
            val prefs = app.getSharedPreferences("memoh_avatar_test", 0)
            prefs.edit().clear().commit()
            MemohTransport.cookiePrefs = prefs
            val api = MemohApi().apply { configure(server.gateway, "test-bearer") }
            val authorization = AtomicReference<String>()
            val png = Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII=",
            )
            server.handler = { exchange, request ->
                if (request.path == "/avatar.png") {
                    authorization.set(exchange.requestHeaders.getFirst("Authorization"))
                    exchange.responseHeaders.add("Content-Type", "image/png")
                    exchange.sendResponseHeaders(200, png.size.toLong())
                    exchange.responseBody.use { it.write(png) }
                    exchange.close()
                    true
                } else false
            }
            compose.setContent {
                AgoraTheme {
                    CompositionLocalProvider(LocalMemohApi provides api) {
                        MemohBotAvatar(MemohBot(id = "b", title = "Private bot", avatarUrl = "${server.origin}/avatar.png"))
                    }
                }
            }
            compose.waitUntil(10_000) { authorization.get() != null }
            assertEquals("Bearer test-bearer", authorization.get())
            compose.waitUntil(10_000) {
                api.mediaCacheDirectory(app.cacheDir, "avatars").listFiles()?.any { it.isFile && it.length() > 0 } == true
            }
            compose.onNodeWithContentDescription("Private bot").assertExists()
            assertTrue(server.requests.any { it.path == "/avatar.png" })
            prefs.edit().clear().commit()
        }
    }
}
