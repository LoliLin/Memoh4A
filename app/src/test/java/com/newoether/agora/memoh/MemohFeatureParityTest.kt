package com.newoether.agora.memoh

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * Feature-parity additions against the Memoh web client: full queue management,
 * runtime controls, chat-time model preference, and delta merge extensions.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class MemohFeatureParityTest {
    private suspend fun awaitState(predicate: () -> Boolean) {
        withTimeout(10_000) { while (!predicate()) delay(10) }
    }

    private suspend fun awaitWithRetries(dispatcher: TestDispatcher, predicate: () -> Boolean) {
        withTimeout(10_000) {
            while (!predicate()) {
                dispatcher.scheduler.advanceTimeBy(1_000)
                dispatcher.scheduler.runCurrent()
                delay(10)
            }
        }
    }

    private fun withCloud(
        dispatcher: TestDispatcher = UnconfinedTestDispatcher(),
        test: suspend (MemohViewModel, MemohCloudServer) -> Unit,
    ) {
        Dispatchers.setMain(dispatcher)
        val app = RuntimeEnvironment.getApplication()
        val prefs = app.getSharedPreferences("memoh_native", 0)
        try {
            MemohCloudServer().use { server ->
                prefs.edit().clear().putString("server", server.gateway).commit()
                val vm = MemohViewModel(app)
                try { runBlocking { test(vm, server) } }
                finally { vm.logout(); prefs.edit().clear().commit() }
            }
        } finally { Dispatchers.resetMain() }
    }

    private suspend fun login(vm: MemohViewModel) {
        vm.login.email = "user@example.com"
        vm.login.code = "123456"
        vm.loginWithCode()
        awaitState { vm.signedIn && !vm.teams.busy && vm.bot != null && !vm.loadingSessions }
    }

    private fun queueState(type: String, vararg items: Pair<String, String>) = JSONObject()
        .put("steer_supported", type == "steer")
        .put(type, JSONArray().apply {
            items.forEach { (id, text) -> put(JSONObject().put("item_id", id).put("text", text).put("status", "queued")) }
        })

    @Test
    fun `editing a queued item patches its text on the right queue`() = withCloud { vm, server ->
        login(vm)
        vm.selectSession(MemohSession("chat", "Chat", "agent"))
        server.handler = { exchange, seen ->
            when {
                seen.path.endsWith("/queue") -> { server.reply(exchange, queueState("follow_up", "i1" to "first").toString()); true }
                seen.method == "PATCH" && seen.path.endsWith("/follow-up-queue/i1") -> {
                    assertEquals("second", JSONObject(seen.body).getString("text"))
                    server.reply(exchange, "{}"); true
                }
                else -> false
            }
        }
        vm.refreshQueue()
        awaitState { vm.queueState?.items?.size == 1 }
        vm.editQueued(vm.queueState!!.items.first(), "second")
        awaitState { server.requests.any { it.method == "PATCH" && it.path.endsWith("/follow-up-queue/i1") } }
    }

    @Test
    fun `reordering sends the item and its target ref`() = withCloud { vm, server ->
        login(vm)
        vm.selectSession(MemohSession("chat", "Chat", "agent"))
        server.handler = { exchange, seen ->
            when {
                seen.path.endsWith("/queue") -> {
                    val state = queueState("steer", "a" to "one", "b" to "two")
                    server.reply(exchange, state.toString()); true
                }
                seen.method == "PUT" && seen.path.endsWith("/steer-queue/reorder") -> {
                    val body = JSONObject(seen.body)
                    assertEquals("b", body.getJSONObject("item").getString("item_id"))
                    assertEquals("a", body.getJSONObject("before").getString("item_id"))
                    server.reply(exchange, "{}"); true
                }
                else -> false
            }
        }
        vm.refreshQueue()
        awaitState { vm.queueState?.items?.size == 2 }
        val itemB = vm.queueState!!.items.first { it.id == "b" }
        vm.reorderQueued(itemB, "a")
        awaitState { server.requests.any { it.method == "PUT" && it.path.endsWith("/steer-queue/reorder") } }
    }

    @Test
    fun `promoting a follow-up posts to the steer endpoint`() = withCloud { vm, server ->
        login(vm)
        vm.selectSession(MemohSession("chat", "Chat", "agent"))
        server.handler = { exchange, seen ->
            when {
                seen.path.endsWith("/queue") -> { server.reply(exchange, queueState("follow_up", "f1" to "later").toString()); true }
                seen.method == "POST" && seen.path.endsWith("/follow-up-queue/f1/steer") -> {
                    server.reply(exchange, "{}", 202); true
                }
                else -> false
            }
        }
        vm.refreshQueue()
        awaitState { vm.queueState?.items?.size == 1 }
        vm.promoteQueued(vm.queueState!!.items.first())
        awaitState { server.requests.any { it.method == "POST" && it.path.endsWith("/follow-up-queue/f1/steer") } }
    }

    @Test
    fun `runtime controls load modes goal and commands`() = withCloud { vm, server ->
        login(vm)
        vm.selectSession(MemohSession("chat", "Chat", "agent"))
        server.handler = { exchange, seen ->
            when {
                seen.path.endsWith("/runtime-controls") -> {
                    server.reply(exchange, JSONObject()
                        .put("session_id", "chat")
                        .put("modes", JSONObject()
                            .put("supported", true)
                            .put("current_mode_id", "default")
                            .put("available_modes", JSONArray()
                                .put(JSONObject().put("id", "default").put("name", "Default"))
                                .put(JSONObject().put("id", "read_only").put("name", "Read Only"))))
                        .put("capabilities", JSONObject().put("goal", true).put("permission_modes", true))
                        .toString()); true
                }
                seen.path.endsWith("/runtime-controls/goal") -> {
                    server.reply(exchange, JSONObject().put("goal", JSONObject()
                        .put("objective", "ship the app").put("status", "active")
                        .put("tokens_used", 120).put("token_budget", 500)).toString()); true
                }
                else -> false
            }
        }
        vm.refreshRuntimeControls()
        awaitState { vm.runtimeControlsJson != null }
        assertEquals("default", vm.runtimeControlsJson!!.getJSONObject("modes").getString("current_mode_id"))
        awaitState { vm.runtimeGoalJson != null }
        assertEquals("ship the app", vm.runtimeGoalJson!!.getJSONObject("goal").getString("objective"))
    }

    @Test
    fun `a picked model rides on the next message send`() = withCloud { vm, server ->
        var capturedPayload: JSONObject? = null
        server.handler = { exchange, seen ->
            if (seen.path.endsWith("/web/ws")) { server.reply(exchange, "{}", 400); true } else false
        }
        login(vm)
        vm.selectSession(MemohSession("chat", "Chat", "agent"))
        vm.agentController.setModelPreference("gpt-5.2-codex", "high")
        val sent = vm.send("hello")
        assertTrue(sent)
        awaitState { vm.pendingUserText != null }
        val invocation = vm.admissionInvocations.values.first()
        assertEquals("gpt-5.2-codex", invocation.getString("model_id"))
        assertEquals("high", invocation.getString("reasoning_effort"))
        capturedPayload = invocation
        assertNotNull(capturedPayload)
    }

    @Test
    fun `a tool upsert carrying only tool_call_id replaces the matching row`() {
        // The delta merge lives on the ViewModel; exercise it through a bare instance.
        val app = org.robolectric.RuntimeEnvironment.getApplication()
        val prefs = app.getSharedPreferences("memoh-native-merge", 0)
        prefs.edit().clear().commit()
        val vm = MemohViewModel(app)
        try {
            vm.upsertBlock(JSONObject().put("id", 1).put("type", "tool")
                .put("tool_call_id", "call-9").put("content", "started"))
            vm.upsertBlock(JSONObject().put("type", "tool")
                .put("tool_call_id", "call-9").put("content", "finished"))
            val merged = vm.liveJson
            assertEquals(1, merged.length())
            assertEquals(1, merged.optJSONObject(0).getInt("id"))
            assertEquals("finished", merged.optJSONObject(0).getString("content"))
            assertEquals("call-9", merged.optJSONObject(0).getString("tool_call_id"))
            vm.applyRuntimeDelta(JSONObject().put("message_appends", JSONArray()
                .put(JSONObject().put("id", 1).put("content", " updated"))), "chat")
            assertEquals(1, vm.liveJson.length())
            assertEquals("finished updated", vm.liveJson.getJSONObject(0).getString("content"))
        } finally { vm.logout(); prefs.edit().clear().commit() }
    }

    @Test
    fun `runtime delta keeps authoritative upserts and sorts blocks`() = withCloud { vm, _ ->
        vm.applyRunView(JSONObject().put("run_id", "run-1").put("turn_id", "turn-1")
            .put("status", "running")
            .put("request_user_turn", JSONObject().put("turn_id", "turn-1").put("text", "Question"))
            .put("messages", JSONArray().put(JSONObject().put("id", 2).put("type", "text").put("content", "old"))))
        vm.applyRuntimeDelta(JSONObject()
            .put("message_appends", JSONArray().put(JSONObject().put("id", 2).put("content", " append")))
            .put("message_upserts", JSONArray()
                .put(JSONObject().put("id", 2).put("type", "text").put("content", "authoritative"))
                .put(JSONObject().put("id", 1).put("type", "text").put("content", "first"))), "chat")
        assertEquals(listOf("first", "authoritative"), vm.liveBlocks.map { it.text })
        assertEquals("turn-1", vm.liveTurnId)
        assertEquals("Question", vm.liveUserTurns.single().text)
        vm.applyRuntimeDelta(JSONObject().put("run", JSONObject().put("status", "completed")), "chat")
        assertFalse(vm.running)
    }

    @Test
    fun `transient history failure retries the terminal handoff`() {
        val dispatcher = UnconfinedTestDispatcher()
        withCloud(dispatcher) { vm, server ->
            val failuresRemaining = java.util.concurrent.atomic.AtomicInteger(0)
            val failedRequests = java.util.concurrent.atomic.AtomicInteger(0)
            server.handler = { exchange, seen ->
                if (seen.path.endsWith("/messages")) {
                    if (failuresRemaining.getAndSet(0) > 0) {
                        failedRequests.incrementAndGet()
                        server.reply(exchange, "{\"message\":\"temporary failure\"}", 500)
                    } else {
                        val items = JSONArray()
                        if (failedRequests.get() > 0) {
                            items.put(JSONObject().put("id", "user-row").put("turn_id", "turn-1")
                                .put("role", "user").put("text", "hello"))
                            items.put(JSONObject().put("id", "answer-row").put("turn_id", "turn-1")
                                .put("role", "assistant").put("messages", JSONArray()
                                    .put(JSONObject().put("id", 1).put("type", "text").put("content", "answer"))))
                        }
                        server.reply(exchange, JSONObject().put("items", items).toString())
                    }
                    true
                } else false
            }
            login(vm)
            vm.selectSession(MemohSession("chat", "Chat", "agent"))
            awaitState { vm.loadedHistorySessionId == "chat" }
            assertTrue(vm.send("hello"))
            val invocation = vm.admissionInvocations.keys.single()
            val receive = MemohViewModel::class.java.getDeclaredMethod("receive", JSONObject::class.java)
                .apply { isAccessible = true }
            receive.invoke(vm, JSONObject().put("type", "run_accepted").put("session_id", "chat")
                .put("invocation_id", invocation).put("turn_id", "turn-1").put("run_id", "run-1"))
            vm.applyRunView(JSONObject().put("run_id", "run-1").put("turn_id", "turn-1")
                .put("status", "running").put("messages", JSONArray()
                    .put(JSONObject().put("id", 1).put("type", "text").put("content", "answer"))))
            failuresRemaining.set(1)
            vm.applyRuntimeDelta(JSONObject().put("run", JSONObject().put("status", "completed")), "chat")
            awaitState { failedRequests.get() == 1 }
            assertEquals("answer", vm.liveBlocks.single().text)
            awaitWithRetries(dispatcher) {
                vm.turns.any { it.role == "assistant" && it.turnId == "turn-1" } &&
                    vm.pendingUserText == null && vm.liveBlocks.isEmpty()
            }
            assertFalse(vm.sendStates.containsKey(invocation))
        }
    }

    @Test
    fun `stale successful history keeps the live answer until the assistant turn arrives`() {
        val dispatcher = UnconfinedTestDispatcher()
        withCloud(dispatcher) { vm, server ->
            val finished = java.util.concurrent.atomic.AtomicBoolean(false)
            val assistantReady = java.util.concurrent.atomic.AtomicBoolean(false)
            val staleResponses = java.util.concurrent.atomic.AtomicInteger(0)
            server.handler = { exchange, seen ->
                if (seen.path.endsWith("/messages")) {
                    val items = JSONArray()
                    if (finished.get()) {
                        items.put(JSONObject().put("id", "user-row").put("turn_id", "turn-1")
                            .put("role", "user").put("text", "hello"))
                        if (assistantReady.get()) {
                            items.put(JSONObject().put("id", "answer-row").put("turn_id", "turn-1")
                                .put("role", "assistant").put("messages", JSONArray()
                                    .put(JSONObject().put("id", 1).put("type", "text").put("content", "answer"))))
                        } else staleResponses.incrementAndGet()
                    }
                    server.reply(exchange, JSONObject().put("items", items).toString())
                    true
                } else false
            }
            login(vm)
            vm.selectSession(MemohSession("chat", "Chat", "agent"))
            awaitState { vm.loadedHistorySessionId == "chat" }
            assertTrue(vm.send("hello"))
            val invocation = vm.admissionInvocations.keys.single()
            val receive = MemohViewModel::class.java.getDeclaredMethod("receive", JSONObject::class.java)
                .apply { isAccessible = true }
            receive.invoke(vm, JSONObject().put("type", "run_accepted").put("session_id", "chat")
                .put("invocation_id", invocation).put("turn_id", "turn-1").put("run_id", "run-1"))
            vm.applyRunView(JSONObject().put("run_id", "run-1").put("turn_id", "turn-1")
                .put("status", "running").put("messages", JSONArray()
                    .put(JSONObject().put("id", 1).put("type", "text").put("content", "answer"))))
            finished.set(true)
            vm.applyRuntimeDelta(JSONObject().put("run", JSONObject().put("status", "completed")), "chat")
            awaitState { staleResponses.get() > 0 && vm.turns.any { it.role == "user" } && vm.pendingUserText == null }
            assertEquals("answer", vm.liveBlocks.single().text)
            val rows = memohTranscriptRows(vm.turns, vm.liveUserTurns, vm.pendingUserText,
                vm.pendingUserAttachments, vm.pendingUserTurnId, vm.liveTurnId, true, null)
            assertEquals(listOf("hello", ""), rows.map { it.text })
            assertEquals(MEMOH_LIVE_MESSAGE_ID, rows.last().id)

            assistantReady.set(true)
            awaitWithRetries(dispatcher) {
                vm.turns.any { it.role == "assistant" && it.turnId == "turn-1" } && vm.liveBlocks.isEmpty()
            }
            assertFalse(vm.sendStates.containsKey(invocation))
        }
    }

    @Test
    fun `settled user is not restored as pending after returning to a session`() = withCloud { vm, server ->
        val ready = java.util.concurrent.atomic.AtomicBoolean(false)
        server.handler = { exchange, seen ->
            if (seen.path.endsWith("/messages")) {
                val items = JSONArray()
                if (ready.get()) items.put(JSONObject().put("id", "server-row")
                    .put("turn_id", "turn-1").put("role", "user").put("text", "hello"))
                server.reply(exchange, JSONObject().put("items", items).toString()); true
            } else false
        }
        login(vm)
        val conversation = MemohSession("chat", "Chat", "agent")
        vm.selectSession(conversation)
        assertTrue(vm.send("hello"))
        val invocation = vm.admissionInvocations.keys.single()
        val receive = MemohViewModel::class.java.getDeclaredMethod("receive", JSONObject::class.java)
            .apply { isAccessible = true }
        receive.invoke(vm, JSONObject().put("type", "run_accepted").put("session_id", "chat")
            .put("invocation_id", invocation).put("turn_id", "turn-1").put("run_id", "run-1"))
        vm.newChat()
        ready.set(true)
        vm.selectSession(conversation)
        awaitState { vm.turns.any { it.turnId == "turn-1" } && vm.pendingUserText == null }
        assertFalse(vm.sendStates.containsKey(invocation))
        vm.newChat()
        vm.selectSession(conversation)
        assertNull(vm.pendingUserText)
    }

    @Test
    fun `queue refresh restores items after a mutation`() = withCloud { vm, server ->
        login(vm)
        vm.selectSession(MemohSession("chat", "Chat", "agent"))
        var mutationCount = 0
        server.handler = { exchange, seen ->
            when {
                seen.path.endsWith("/queue") -> {
                    val items = if (mutationCount == 0) listOf("x" to "draft") else emptyList<Pair<String, String>>()
                    val arr = JSONArray()
                    items.forEach { (id, text) -> arr.put(JSONObject().put("item_id", id).put("text", text).put("status", "queued")) }
                    server.reply(exchange, JSONObject().put("steer_supported", false).put("follow_up", arr).toString()); true
                }
                seen.method == "DELETE" && seen.path.endsWith("/follow-up-queue/x") -> { mutationCount++; server.reply(exchange, "", 204); true }
                else -> false
            }
        }
        vm.refreshQueue()
        awaitState { vm.queueState?.items?.size == 1 }
        vm.cancelQueued(vm.queueState!!.items.first())
        awaitState { vm.queueState?.items?.isEmpty() == true }
        assertEquals(1, mutationCount)
    }

    @Test
    fun `retrying a turn sends retry_message with the server turn id`() = withCloud { vm, server ->
        server.handler = { exchange, seen ->
            if (seen.path.endsWith("/messages")) {
                server.reply(exchange, JSONObject().put("items", JSONArray()
                    .put(JSONObject().put("turn_id", "srv-turn-7").put("role", "user").put("text", "old"))).toString())
                true
            } else false
        }
        login(vm)
        vm.selectSession(MemohSession("chat", "Chat", "agent"))
        awaitState { vm.turns.isNotEmpty() }
        assertEquals("srv-turn-7", vm.turns.first().turnId)
        val accepted = vm.mutateTurn("srv-turn-7", retry = true)
        assertTrue(accepted)
        val payload = vm.admissionInvocations.values.single()
        assertEquals("retry_message", payload.getString("type"))
        assertEquals("srv-turn-7", payload.getString("turn_id"))
        assertEquals("chat", payload.getString("session_id"))
    }

    @Test
    fun `editing a turn carries the new text and is refused while running`() = withCloud { vm, server ->
        server.handler = { exchange, seen ->
            if (seen.path.endsWith("/messages")) {
                server.reply(exchange, JSONObject().put("items", JSONArray()
                    .put(JSONObject().put("turn_id", "srv-turn-9").put("role", "user").put("text", "first"))).toString())
                true
            } else false
        }
        login(vm)
        vm.selectSession(MemohSession("chat", "Chat", "agent"))
        awaitState { vm.turns.isNotEmpty() }
        assertTrue(vm.mutateTurn("srv-turn-9", retry = false, text = "edited"))
        val payload = vm.admissionInvocations.values.single()
        assertEquals("edit_message", payload.getString("type"))
        assertEquals("edited", payload.getString("text"))
        assertFalse(vm.mutateTurn("srv-turn-9", retry = false, text = "again"))
    }

    @Test
    fun `forking from a turn posts turn_id and navigates to the forked session`() = withCloud { vm, server ->
        var forkBody: String? = null
        server.handler = { exchange, seen ->
            when {
                seen.path.endsWith("/fork") -> {
                    forkBody = seen.body
                    server.reply(exchange, JSONObject()
                        .put("id", "fork-1").put("title", "Forked")
                        .put("bot_agent_id", "agent").put("runtime_type", "codex").toString())
                    true
                }
                else -> false
            }
        }
        login(vm)
        vm.selectSession(MemohSession("chat", "Chat", "agent"))
        vm.forkFromTurn("srv-turn-3")
        awaitState { vm.session?.id == "fork-1" }
        assertEquals("""{"turn_id":"srv-turn-3"}""", forkBody)
        assertTrue(vm.sessions.any { it.id == "fork-1" })
    }
}
