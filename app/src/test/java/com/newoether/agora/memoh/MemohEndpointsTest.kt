package com.newoether.agora.memoh

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Cookie
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MemohEndpointsTest {

    @Test
    fun `cloud origin resolves to the split auth and business prefixes`() {
        val endpoints = MemohEndpoints.fromInput("https://app.memoh.net")
        assertEquals("https://app.memoh.net/api/v1", endpoints.auth)
        assertEquals("https://app.memoh.net/api/memoh", endpoints.api)
        assertEquals("https://app.memoh.net", endpoints.origin)
    }

    @Test
    fun `a pasted api root still splits correctly instead of doubling the segment`() {
        val fromApi = MemohEndpoints.fromInput("https://app.memoh.net/api")
        assertEquals("https://app.memoh.net/api/v1", fromApi.auth)
        assertEquals("https://app.memoh.net/api/memoh", fromApi.api)

        val fromBusiness = MemohEndpoints.fromInput("https://app.memoh.net/api/memoh")
        assertEquals("https://app.memoh.net/api/v1", fromBusiness.auth)
        assertEquals("https://app.memoh.net/api/memoh", fromBusiness.api)
    }

    @Test
    fun `self hosted root keeps its own path`() {
        val endpoints = MemohEndpoints.fromInput("http://localhost:8080")
        assertEquals("http://localhost:8080/api/v1", endpoints.auth)
        assertEquals("http://localhost:8080/api/memoh", endpoints.api)
    }

    @Test
    fun `custom port survives into the origin`() {
        val endpoints = MemohEndpoints.fromInput("https://memoh.example.com:8443")
        assertEquals("https://memoh.example.com:8443", endpoints.origin)
        assertEquals("https://memoh.example.com:8443/api/v1", endpoints.auth)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `plain http is refused for a remote host`() {
        MemohEndpoints.fromInput("http://app.memoh.net")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `credentials inside the url are refused`() {
        MemohEndpoints.fromInput("https://someone:" + "secret" + "@app.memoh.net")
    }
}

class MemohCookieJarTest {

    private val url = "https://app.memoh.net/api/memoh/bots".toHttpUrl()

    private fun cookie(name: String, value: String, host: String = "app.memoh.net", path: String = "/"): Cookie =
        Cookie.Builder()
            .name(name)
            .value(value)
            .hostOnlyDomain(host)
            .path(path)
            .expiresAt(System.currentTimeMillis() + 60_000)
            .build()

    @Test
    fun `session cookies are sent back to the business api`() {
        val jar = MemohCookieJar()
        jar.saveFromResponse(url, listOf(cookie("sid", "abc")))

        assertTrue(jar.hasSession())
        assertEquals(listOf("abc"), jar.loadForRequest(url).map { it.value })
    }

    @Test
    fun `a reissued cookie replaces the previous value instead of duplicating it`() {
        val jar = MemohCookieJar()
        jar.saveFromResponse(url, listOf(cookie("sid", "old")))
        jar.saveFromResponse(url, listOf(cookie("sid", "new")))

        assertEquals(listOf("new"), jar.loadForRequest(url).map { it.value })
    }

    @Test
    fun `an already expired cookie ends the session instead of being stored`() {
        val jar = MemohCookieJar()
        jar.saveFromResponse(url, listOf(cookie("sid", "abc")))
        val expired = Cookie.Builder()
            .name("sid").value("").hostOnlyDomain("app.memoh.net").path("/")
            .expiresAt(0)
            .build()
        jar.saveFromResponse(url, listOf(expired))

        assertFalse(jar.hasSession())
        assertTrue(jar.loadForRequest(url).isEmpty())
    }

    @Test
    fun `cookies are not leaked to another host`() {
        val jar = MemohCookieJar()
        jar.saveFromResponse(url, listOf(cookie("sid", "abc")))

        val other = "https://evil.example.com/api/memoh/bots".toHttpUrl()
        assertTrue(jar.loadForRequest(other).isEmpty())
    }

    @Test
    fun `clear drops the session`() {
        val jar = MemohCookieJar()
        jar.saveFromResponse(url, listOf(cookie("sid", "abc")))
        jar.clear()

        assertFalse(jar.hasSession())
    }
}
