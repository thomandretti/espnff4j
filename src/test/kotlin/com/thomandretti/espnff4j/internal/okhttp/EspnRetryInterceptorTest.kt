package com.thomandretti.espnff4j.internal.okhttp

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.IOException

class EspnRetryInterceptorTest {
    private val server = MockWebServer()
    private val client =
        OkHttpClient
            .Builder()
            .addInterceptor(EspnRetryInterceptor())
            .build()

    @BeforeEach
    fun setUp() = server.start()

    @AfterEach
    fun tearDown() = server.shutdown()

    private fun get(): okhttp3.Response = client.newCall(Request.Builder().url(server.url("/")).build()).execute()

    @Test
    fun `succeeds on first attempt without retrying`() {
        server.enqueue(MockResponse().setResponseCode(200))

        val response = get()

        assertEquals(200, response.code)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `retries on 500 and returns successful response`() {
        server.enqueue(MockResponse().setResponseCode(500))
        server.enqueue(MockResponse().setResponseCode(200))

        val response = get()

        assertEquals(200, response.code)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `does not retry on 4xx`() {
        server.enqueue(MockResponse().setResponseCode(403))

        val response = get()

        assertEquals(403, response.code)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `returns last 5xx response after exhausting all attempts`() {
        repeat(3) { server.enqueue(MockResponse().setResponseCode(503)) }

        val response = get()

        assertEquals(503, response.code)
        assertEquals(3, server.requestCount)
    }

    @Test
    fun `retries on connection reset and returns successful response`() {
        server.enqueue(MockResponse().apply { socketPolicy = SocketPolicy.DISCONNECT_AT_START })
        server.enqueue(MockResponse().setResponseCode(200))

        val response = get()

        assertEquals(200, response.code)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `throws IOException after all attempts fail with connection errors`() {
        repeat(3) {
            server.enqueue(MockResponse().apply { socketPolicy = SocketPolicy.DISCONNECT_AT_START })
        }

        assertThrows<IOException> { get() }
        assertEquals(3, server.requestCount)
    }
}
