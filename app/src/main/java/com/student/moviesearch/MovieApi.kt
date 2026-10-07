package com.student.moviesearch

import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query

interface MovieApi {
    @GET("search")
    fun search(@Query("q") query: String): Call<SearchResponse>
}
