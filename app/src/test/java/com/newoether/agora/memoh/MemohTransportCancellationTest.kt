package com.newoether.agora.memoh

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class MemohTransportCancellationTest {
    @Test
    fun `cancelling JSON while its response body is stalled releases the HTTP call`() = runBlocking {
        MemohCloudServer().use { server ->
            val reading = CountDownLatch(1)
            val release = CountDownLatch(1)
            server.handler = { exchange, request ->
                if (request.path.endsWith("/slow")) {
                    exchange.sendResponseHeaders(200, 0)
                    try {
                        exchange.responseBody.write("{".toByteArray())
                        exchange.responseBody.flush()
                        reading.countDown()
                        release.await(5, TimeUnit.SECONDS)
                        runCatching { exchange.responseBody.write("}".toByteArray()) }
                    } finally { exchange.close() }
                    true
                } else false
            }
            val api = MemohApi().apply { configure(server.gateway, "test-token"); selectTeam("a") }
            val job = async(Dispatchers.Default) { api.request("/slow") }
            try {
                withTimeout(3_000) { while (reading.count > 0L) delay(10) }
                withTimeout(2_000) { job.cancelAndJoin() }
                assertTrue(job.isCancelled)
                withTimeout(2_000) {
                    while (api.scope.identity.client.dispatcher.runningCallsCount() != 0) delay(10)
                }
                assertEquals(0, api.scope.identity.client.dispatcher.runningCallsCount())
            } finally { release.countDown(); job.cancelAndJoin() }
        }
    }
}
