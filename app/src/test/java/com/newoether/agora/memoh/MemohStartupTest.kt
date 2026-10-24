package com.newoether.agora.memoh

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.work.Configuration
import androidx.work.WorkManager
import com.newoether.agora.data.local.ChatDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import org.junit.rules.ExternalResource
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MemohStartupTest {
    // Start the auth fixture before the real launcher so startup never depends on the live Cloud.
    @get:Rule(order = 0) val localAuth = object : ExternalResource() {
        private var server: HttpServer? = null
        override fun before() {
            val local = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
            local.createContext("/") { exchange ->
                val text = if (exchange.requestURI.path.endsWith("/auth/config")) """{"login_method":"password"}""" else "[]"
                val bytes = text.toByteArray()
                exchange.sendResponseHeaders(200, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
            local.start()
            server = local
            RuntimeEnvironment.getApplication().getSharedPreferences("memoh_native", 0).edit()
                .clear().putString("server", "http://127.0.0.1:${local.address.port}").commit()
        }
        override fun after() {
            RuntimeEnvironment.getApplication().getSharedPreferences("memoh_native", 0).edit().clear().commit()
            server?.stop(0)
        }
    }
    // Use the real manifest and launcher instead of substituting a bare test Application.
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MemohActivity>()

    @Test
    fun `cold start renders login without opening the local Agora runtime`() {
        val app = compose.activity.application
        assertEquals(MemohApplication::class.java, app.javaClass)
        // Removing the auto-initializer must not break shared code that asks for WorkManager.
        assertNotNull(WorkManager.getInstance(app))
        assertNotNull((app as Configuration.Provider).workManagerConfiguration)
        compose.onNodeWithText("API address").assertIsDisplayed()
        compose.onNodeWithText("Connect").assertIsDisplayed()
        assertFalse(compose.activity.getDatabasePath(ChatDatabase.DB_NAME).exists())
    }

    @Test
    fun `shared foreground workers have a dataSync service declaration`() {
        val app = compose.activity.application
        val service = app.packageManager.getServiceInfo(
            android.content.ComponentName(app, "androidx.work.impl.foreground.SystemForegroundService"), 0,
        )
        assertEquals(android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC, service.foregroundServiceType)
        val permissions = app.packageManager.getPackageInfo(
            app.packageName, android.content.pm.PackageManager.GET_PERMISSIONS,
        ).requestedPermissions.orEmpty().toSet()
        assertTrue("android.permission.FOREGROUND_SERVICE" in permissions)
        assertTrue("android.permission.FOREGROUND_SERVICE_DATA_SYNC" in permissions)
        assertFalse(compose.activity.getDatabasePath(ChatDatabase.DB_NAME).exists())
    }

    @Test
    fun `recreating the launcher keeps the remote login usable`() {
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("API address").assertIsDisplayed()
        compose.onNodeWithText("Connect").assertIsDisplayed()
        assertFalse(compose.activity.getDatabasePath(ChatDatabase.DB_NAME).exists())
    }
}
