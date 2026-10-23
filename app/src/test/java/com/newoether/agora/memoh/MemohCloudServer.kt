package com.newoether.agora.memoh

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.io.Closeable
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/** A real Cloud-shaped HTTP boundary, including the original missing-team failure. */
internal class MemohCloudServer : Closeable {
    data class Seen(val path: String, val method: String, val team: String?, val cookie: String?, val query: String?, val body: String)
    val requests = CopyOnWriteArrayList<Seen>()
    val missingTeamErrors = AtomicInteger()
    var memberships = """{"teams":[{"team":{"team_id":"a","name":"Alpha"},"role":"TEAM_ROLE_OWNER"},{"team":{"team_id":"b","name":"Beta"},"role":"TEAM_ROLE_MEMBER"}]}"""
    var userId = "user-1"
    var creationAllowed = true
    var handler: ((HttpExchange, Seen) -> Boolean)? = null
    private val executor = Executors.newCachedThreadPool()
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    val origin: String get() = "http://127.0.0.1:${server.address.port}"
    val gateway: String get() = "$origin/api/memoh"
    init {
        server.executor = executor
        server.createContext("/") { exchange ->
            val seen = Seen(exchange.requestURI.path, exchange.requestMethod,
                exchange.requestHeaders.getFirst("X-Team-ID"), exchange.requestHeaders.getFirst("Cookie"),
                exchange.requestURI.rawQuery, exchange.requestBody.bufferedReader().readText())
            requests += seen
            if (handler?.invoke(exchange, seen) == true) return@createContext
            val scoped = seen.path.startsWith("/api/memoh/") || seen.path == "/api/v1/ws-tickets"
            if (scoped && seen.team.isNullOrEmpty()) {
                missingTeamErrors.incrementAndGet()
                reply(exchange, """{"reason":"TEAM_REQUIRED","message":"team id is required"}""", 400)
                return@createContext
            }
            val response = when {
                seen.path == "/api/v1/auth/config" -> """{"login_method":"email"}"""
                seen.path == "/api/v1/auth/oauth-providers" -> "[]"
                seen.path == "/api/v1/auth/email-code/verify" -> {
                    exchange.responseHeaders.add("Set-Cookie", "sid=$userId; Path=/; HttpOnly")
                    "{}"
                }
                seen.path == "/api/v1/users/me" -> """{"user":{"id":"$userId"},"team_creation_allowed":$creationAllowed}"""
                seen.path == "/api/v1/teams" && seen.method == "POST" -> """{"team":{"team_id":"new","name":"New team"}}"""
                seen.path == "/api/v1/teams" -> memberships
                seen.path == "/api/v1/ws-tickets" -> """{"ticket":"ticket-${seen.team}"}"""
                seen.path.endsWith("/web/ws") -> { reply(exchange, "{}", 400); return@createContext }
                seen.path == "/api/memoh/bots" -> """{"items":[{"id":"shared","name":"${seen.team}"}]}"""
                seen.path.endsWith("/sessions/chat") -> """{"id":"chat","title":"Chat","bot_agent_id":"agent","runtime_type":"codex"}"""
                seen.path.endsWith("/fs/read") -> """{"content":"hello","revision":"r1"}"""
                seen.path.endsWith("/fs/write") -> """{"revision":"r2"}"""
                seen.path.endsWith("/fs/extract") -> """{"destination":"/data/unpacked"}"""
                seen.path.endsWith("/device/authorize") -> """{"login_id":"login"}"""
                seen.path.endsWith("/fs/download") || seen.path.endsWith("/fs/archive") || seen.path.endsWith("/backup/export") -> "downloaded bytes"
                else -> """{"items":[],"entries":[]}"""
            }
            reply(exchange, response)
        }
        server.start()
    }
    fun reply(exchange: HttpExchange, body: String, status: Int = 200) {
        val bytes = body.toByteArray()
        try {
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        } finally { exchange.close() }
    }
    override fun close() { server.stop(0); executor.shutdownNow() }
}
