package com.student.moviesearch

import android.content.Context
import com.android.volley.DefaultRetryPolicy
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.google.gson.Gson
import okhttp3.HttpUrl.Companion.toHttpUrl
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class MovieRequests(context: Context) {
    private val queue = Volley.newRequestQueue(context.applicationContext)
    private val gson = Gson()
    private val api = Retrofit.Builder().baseUrl(BASE_URL)
        .addConverterFactory(GsonConverterFactory.create()).build().create(MovieApi::class.java)

    fun search(query: String, page: Int, method: String, token: String,
               success: (SearchResponse) -> Unit, error: (String) -> Unit): () -> Unit {
        val url = BASE_URL.toHttpUrl().resolve("search/movie")!!.newBuilder()
            .addQueryParameter("query", query).addQueryParameter("page", page.toString())
            .addQueryParameter("include_adult", "false").build().toString()
        return if (method == "Volley") volley(url, token, SearchResponse::class.java, success, error)
        else retrofit(api.search("Bearer $token", query, page), success, error)
    }

    fun details(id: Int, method: String, token: String,
                success: (TmdbMovie) -> Unit, error: (String) -> Unit): () -> Unit {
        val url = "${BASE_URL}movie/$id?append_to_response=credits"
        return if (method == "Volley") volley(url, token, TmdbMovie::class.java, success, error)
        else retrofit(api.details("Bearer $token", id), success, error)
    }

    private fun <T> volley(url: String, token: String, type: Class<T>, success: (T) -> Unit,
                           error: (String) -> Unit): () -> Unit {
        val request = object : JsonObjectRequest(Request.Method.GET, url, null, { json ->
            try {
                success(gson.fromJson(json.toString(), type))
            } catch (_: Exception) { error("Invalid TMDB response") }
        }, { failure -> error(message(failure.networkResponse?.statusCode)) }) {
            override fun getHeaders() = mapOf("Authorization" to "Bearer $token", "Accept" to "application/json")
        }
        request.setShouldCache(false)
        request.retryPolicy = DefaultRetryPolicy(20000, 0, 1f)
        queue.add(request)
        return { request.cancel() }
    }

    private fun <T> retrofit(call: Call<T>, success: (T) -> Unit, error: (String) -> Unit): () -> Unit {
        call.enqueue(object : Callback<T> {
            override fun onResponse(call: Call<T>, response: Response<T>) {
                if (call.isCanceled) return
                val body = response.body()
                if (response.isSuccessful && body != null) success(body)
                else error(message(response.code()))
            }
            override fun onFailure(call: Call<T>, failure: Throwable) {
                if (!call.isCanceled) error("Cannot reach TMDB. Check your internet connection and try again.")
            }
        })
        return { call.cancel() }
    }

    private fun message(code: Int?) = when (code) {
        401, 403 -> "TMDB rejected the access token. Check API settings."
        429 -> "Too many requests. Wait a moment and try again."
        null -> "Cannot reach TMDB. Check your internet connection and try again."
        else -> "TMDB error ($code). Please try again."
    }

    companion object { const val BASE_URL = "https://api.themoviedb.org/3/" }
}
