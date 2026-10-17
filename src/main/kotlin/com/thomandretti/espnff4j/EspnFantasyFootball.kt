package com.thomandretti.espnff4j

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.thomandretti.espnff4j.espn.EspnFantasyFootballClient
import com.thomandretti.espnff4j.okhttp.EspnRetryInterceptor
import com.thomandretti.espnff4j.okhttp.StaticCookieJar
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.jackson.JacksonConverterFactory

const val ESPN_BASE_URL = "https://lm-api-reads.fantasy.espn.com/apis/v3/games/ffl/seasons/"

internal val OBJECT_MAPPER: ObjectMapper =
    ObjectMapper()
        .registerModules(KotlinModule.Builder().build())
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

/**
 * The `SWID` and `espn_s2` cookies from a logged-in ESPN session. Only private leagues need them.
 */
class EspnCredentials(
    val swid: String,
    val espnS2: String,
) {
    override fun toString(): String = "EspnCredentials(swid=***, espnS2=***)"
}

object EspnFantasyFootball {
    /**
     * Builds a [FantasyFootballClient] for one league and season.
     *
     * [configure] runs against the OkHttp builder after the library's own retry interceptor is added,
     * so callers can attach logging, metrics or timeouts without rebuilding the stack themselves.
     */
    fun client(
        seasonId: Int,
        leagueId: Int,
        credentials: EspnCredentials? = null,
        baseUrl: String = ESPN_BASE_URL,
        configure: OkHttpClient.Builder.() -> Unit = {},
    ): FantasyFootballClient {
        val httpClient =
            OkHttpClient
                .Builder()
                .apply { credentials?.let { cookieJar(StaticCookieJar(it.swid, it.espnS2)) } }
                .addInterceptor(EspnRetryInterceptor())
                .apply(configure)
                .build()
        val espnClient =
            Retrofit
                .Builder()
                .baseUrl(baseUrl)
                .client(httpClient)
                .addConverterFactory(JacksonConverterFactory.create(OBJECT_MAPPER))
                .build()
                .create(EspnFantasyFootballClient::class.java)
        return FantasyFootballClient(espnClient, seasonId, leagueId)
    }
}
