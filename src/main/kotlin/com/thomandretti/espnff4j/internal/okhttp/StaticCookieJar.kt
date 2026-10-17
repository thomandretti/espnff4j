package com.thomandretti.espnff4j.internal.okhttp

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

internal object CookieJarConstants {
    const val ESPN_DOMAIN = "fantasy.espn.com"
    const val SWID_KEY = "SWID"
    const val ESPN_S2_KEY = "espn_s2"
}

internal class StaticCookieJar(
    private val swid: String,
    private val espnS2: String,
) : CookieJar {
    override fun saveFromResponse(
        url: HttpUrl,
        cookies: List<Cookie>,
    ) = Unit

    override fun loadForRequest(url: HttpUrl): MutableList<Cookie> =
        mutableListOf(
            Cookie
                .Builder()
                .domain(CookieJarConstants.ESPN_DOMAIN)
                .name(CookieJarConstants.SWID_KEY)
                .value(swid)
                .build(),
            Cookie
                .Builder()
                .domain(CookieJarConstants.ESPN_DOMAIN)
                .name(CookieJarConstants.ESPN_S2_KEY)
                .value(espnS2)
                .build(),
        )
}
