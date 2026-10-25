package com.newoether.agora.memoh

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The copied report is pasted into public bug reports, so it must never carry credentials. */
class MemohDiagnosticsReportTest {
    @Test
    fun reportCarriesBuildAndConnectionFacts() {
        val report = buildDiagnosticsReport(
            version = "2.1.0",
            versionCode = 31,
            packageName = "in.kawaiis.memoh",
            authUrl = "https://memoh.example.com",
            cookieSession = true,
            connection = "Connected",
            bot = "Alpha",
            team = "Team One",
            olderPagesAvailable = true,
        )

        assertTrue(report.contains("version: 2.1.0 (31)"))
        assertTrue(report.contains("package: in.kawaiis.memoh"))
        assertTrue(report.contains("auth endpoint: https://memoh.example.com"))
        assertTrue(report.contains("cookie session: true"))
        assertTrue(report.contains("connection: Connected"))
        assertTrue(report.contains("bot: Alpha"))
        assertTrue(report.contains("team: Team One"))
        assertTrue(report.contains("older pages available: true"))
    }

    @Test
    fun missingValuesBecomeDashesInsteadOfBlankFields() {
        val report = buildDiagnosticsReport(
            version = "?",
            versionCode = 0,
            packageName = "in.kawaiis.memoh",
            authUrl = "",
            cookieSession = false,
            connection = "Disconnected",
            bot = "",
            team = "",
            olderPagesAvailable = false,
        )

        assertTrue(report.contains("bot: —"))
        assertTrue(report.contains("team: —"))
    }

    @Test
    fun reportNeverCarriesATokenOrCookie() {
        val report = buildDiagnosticsReport(
            version = "2.1.0",
            versionCode = 31,
            packageName = "in.kawaiis.memoh",
            authUrl = "https://memoh.example.com",
            cookieSession = true,
            connection = "Connected",
            bot = "Alpha",
            team = "Team One",
            olderPagesAvailable = false,
        )

        listOf("token", "Bearer", "cookie=", "jwt", "password").forEach { secret ->
            assertFalse("report must not mention $secret", report.contains(secret, ignoreCase = true))
        }
    }
}
