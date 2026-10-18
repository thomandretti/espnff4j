package com.thomandretti.espnff4j

import com.thomandretti.espnff4j.internal.espn.EspnFantasyFootballClient
import com.thomandretti.espnff4j.internal.espn.EspnNewsClient
import com.thomandretti.espnff4j.internal.espn.OBJECT_MAPPER
import com.thomandretti.espnff4j.internal.okhttp.EspnRetryInterceptor
import com.thomandretti.espnff4j.internal.okhttp.StaticCookieJar
import okhttp3.CookieJar
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.jackson.JacksonConverterFactory
import java.util.function.Consumer

const val ESPN_BASE_URL = "https://lm-api-reads.fantasy.espn.com/apis/v3/games/ffl/seasons/"
internal const val ESPN_NEWS_BASE_URL = "https://site.api.espn.com/apis/fantasy/v2/games/ffl/"

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
    @JvmSynthetic
    fun client(
        seasonId: Int,
        leagueId: Int,
        credentials: EspnCredentials? = null,
        baseUrl: String = ESPN_BASE_URL,
        configure: OkHttpClient.Builder.() -> Unit,
    ): FantasyFootballClient {
        val httpClient =
            OkHttpClient
                .Builder()
                .apply { credentials?.let { cookieJar(StaticCookieJar(it.swid, it.espnS2)) } }
                .addInterceptor(EspnRetryInterceptor())
                .apply(configure)
                .build()
        val espnClient = retrofit(baseUrl, httpClient).create(EspnFantasyFootballClient::class.java)
        // Player news is public, so it gets no account cookies. A custom baseUrl, such as a proxy or a
        // test server, serves it too.
        val newsClient =
            retrofit(
                if (baseUrl == ESPN_BASE_URL) ESPN_NEWS_BASE_URL else baseUrl,
                httpClient.newBuilder().cookieJar(CookieJar.NO_COOKIES).build(),
            ).create(EspnNewsClient::class.java)
        return FantasyFootballClient.create(espnClient, newsClient, seasonId, leagueId)
    }

    private fun retrofit(
        baseUrl: String,
        httpClient: OkHttpClient,
    ): Retrofit =
        Retrofit
            .Builder()
            .baseUrl(baseUrl)
            .client(httpClient)
            .addConverterFactory(JacksonConverterFactory.create(OBJECT_MAPPER))
            .build()

    /**
     * Builds a [FantasyFootballClient] for one league and season. This is the Java entry point, also
     * used from Kotlin when there is nothing to configure; Kotlin callers configuring OkHttp get the
     * receiver-lambda overload instead.
     */
    @JvmStatic
    @JvmOverloads
    fun client(
        seasonId: Int,
        leagueId: Int,
        credentials: EspnCredentials? = null,
        baseUrl: String = ESPN_BASE_URL,
        configure: Consumer<OkHttpClient.Builder> = Consumer {},
    ): FantasyFootballClient = client(seasonId, leagueId, credentials, baseUrl) { configure.accept(this) }
}
