package com.thomandretti.espnff4j.okhttp

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class StaticCookieJarTest {
    private val jar = StaticCookieJar("test-swid", "test-espn-s2")
    private val url = "https://fantasy.espn.com/".toHttpUrl()

    @Test
    fun `loadForRequest returns two cookies`() {
        val cookies = jar.loadForRequest(url)
        assertEquals(2, cookies.size)
    }

    @Test
    fun `loadForRequest returns SWID cookie with correct name and value`() {
        val cookies = jar.loadForRequest(url)
        val swid = cookies.first { it.name == "SWID" }
        assertEquals("SWID", swid.name)
        assertEquals("test-swid", swid.value)
    }

    @Test
    fun `loadForRequest returns espn_s2 cookie with correct name and value`() {
        val cookies = jar.loadForRequest(url)
        val espnS2 = cookies.first { it.name == "espn_s2" }
        assertEquals("espn_s2", espnS2.name)
        assertEquals("test-espn-s2", espnS2.value)
    }

    @Test
    fun `saveFromResponse is a no-op and does not throw`() {
        assertDoesNotThrow {
            jar.saveFromResponse(url, emptyList())
        }
    }
}
