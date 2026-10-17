package com.thomandretti.espnff4j.okhttp

import io.github.oshai.kotlinlogging.KotlinLogging
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

private const val MAX_ATTEMPTS = 3
private const val RETRY_DELAY_MS = 500L
private const val HTTP_SERVER_ERROR_MIN = 500

private val logger = KotlinLogging.logger {}

class EspnRetryInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        var lastException: IOException? = null
        for (attempt in 0 until MAX_ATTEMPTS) {
            try {
                val response = chain.proceed(request)
                val isRetriableError = !response.isSuccessful && response.code >= HTTP_SERVER_ERROR_MIN
                if (!isRetriableError || attempt == MAX_ATTEMPTS - 1) {
                    return response
                }
                response.close()
            } catch (e: IOException) {
                if (attempt == MAX_ATTEMPTS - 1) throw e
                lastException = e
            }
            logger.warn { "ESPN API error on attempt ${attempt + 1}/$MAX_ATTEMPTS, retrying" }
            Thread.sleep(RETRY_DELAY_MS * (attempt + 1))
        }
        throw lastException ?: IOException("ESPN API failed after $MAX_ATTEMPTS attempts")
    }
}
