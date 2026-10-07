package com.student.moviesearch

import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query

interface MovieApi {
    @GET("search/movie")
    fun search(@Header("Authorization") authorization: String, @Query("query") query: String,
               @Query("page") page: Int = 1, @Query("include_adult") adult: Boolean = false): Call<SearchResponse>

    @GET("movie/{id}")
    fun details(@Header("Authorization") authorization: String, @Path("id") id: Int,
                @Query("append_to_response") append: String = "credits"): Call<TmdbMovie>
}
