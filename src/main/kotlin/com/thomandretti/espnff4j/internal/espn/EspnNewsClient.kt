package com.thomandretti.espnff4j.internal.espn

import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query

// Not on the fantasy API host: site.api.espn.com serves ESPN's player news, the same for every league and
// readable without cookies.
internal interface EspnNewsClient {
    @GET("news/players")
    fun getPlayerNews(
        @Query("playerId") playerId: Int,
        @Query("limit") limit: Int,
    ): Call<PlayerNewsResponse>
}
