package com.student.moviesearch

import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.ScrollView
import android.widget.TextView
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import com.android.volley.DefaultRetryPolicy
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.google.gson.Gson
import com.student.moviesearch.databinding.ActivityMainBinding
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val adapter = MovieAdapter({ serverUrl() }) { showDetails(it) }
    private val queue by lazy { Volley.newRequestQueue(applicationContext) }
    private var currentCall: Call<SearchResponse>? = null
    private var requestNumber = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.results.layoutManager = GridLayoutManager(this, 2)
        binding.results.adapter = adapter
        binding.searchButton.setOnClickListener { search() }
        binding.serverButton.setOnClickListener { serverSettings() }
        binding.queryInput.setOnEditorActionListener { _, action, _ ->
            if (action == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) { search(); true } else false
        }
    }

    private fun search() {
        val query = binding.queryInput.text.toString().trim()
        if (query.isEmpty()) {
            binding.queryInput.error = "Enter a movie title"
            Log.d("MovieSearch", "Empty query rejected")
            return
        }
        val number = ++requestNumber
        currentCall?.cancel()
        queue.cancelAll("search")
        val method = if (binding.volleyRadio.isChecked) "Volley" else "Retrofit"
        val baseUrl = serverUrl()
        val url = baseUrl.toHttpUrl().resolve("search")!!.newBuilder().addQueryParameter("q", query).build()
        Log.d("MovieSearch", "query=$query method=$method request=GET $url")
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(binding.queryInput.windowToken, 0)
        adapter.show(emptyList())
        binding.progress.visibility = View.VISIBLE
        binding.statusText.text = "Searching with $method…"
        if (method == "Volley") {
            val request = JsonObjectRequest(Request.Method.GET, url.toString(), null, { json ->
                try {
                    val response = Gson().fromJson(json.toString(), SearchResponse::class.java)
                    showResults(number, method, response)
                } catch (error: Exception) { showError(number, method, "Invalid server response", error) }
            }, { error ->
                val code = error.networkResponse?.statusCode
                showError(number, method, if (code != null) "Server error ($code)" else "Cannot reach movie server", error)
            })
            request.tag = "search"
            request.setShouldCache(false)
            request.retryPolicy = DefaultRetryPolicy(30000, 0, 1f)
            queue.add(request)
        } else {
            val api = Retrofit.Builder().baseUrl(baseUrl)
                .addConverterFactory(GsonConverterFactory.create()).build().create(MovieApi::class.java)
            currentCall = api.search(query)
            currentCall!!.enqueue(object : Callback<SearchResponse> {
                override fun onResponse(call: Call<SearchResponse>, response: Response<SearchResponse>) {
                    val body = response.body()
                    if (response.isSuccessful && body != null) showResults(number, method, body)
                    else showError(number, method, "Server error (${response.code()})")
                }
                override fun onFailure(call: Call<SearchResponse>, error: Throwable) {
                    if (!call.isCanceled) showError(number, method, "Cannot reach movie server", error)
                }
            })
        }
    }

    private fun showResults(number: Int, method: String, response: SearchResponse) {
        if (number != requestNumber || isDestroyed) return
        binding.progress.visibility = View.GONE
        adapter.show(response.movies)
        val status = when {
            response.movies.isEmpty() -> "No movies found. Try another title."
            response.total > response.movies.size -> "Showing ${response.movies.size} of ${response.total} movies · $method"
            else -> "${response.movies.size} movies found · $method"
        }
        binding.statusText.text = "$status · ${response.source}"
        Log.d("MovieSearch", "method=$method response/result count=${response.movies.size} total=${response.total}")
    }

    private fun showError(number: Int, method: String, message: String, error: Throwable? = null) {
        if (number != requestNumber || isDestroyed) return
        binding.progress.visibility = View.GONE
        binding.statusText.text = if (message == "Cannot reach movie server")
            "Cannot connect to ${serverUrl()}. Start the server and check Server settings."
        else "$message. Check the server and try again."
        Log.e("MovieSearch", "method=$method error=$message", error)
    }

    private fun serverUrl() = getSharedPreferences("connection", MODE_PRIVATE)
        .getString("server", BuildConfig.BASE_URL) ?: BuildConfig.BASE_URL

    private fun serverSettings() {
        val input = EditText(this).apply {
            setText(serverUrl())
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI
            setSingleLine()
            id = R.id.serverAddress
        }
        val dialog = AlertDialog.Builder(this).setTitle("Movie server")
            .setMessage("The Python server must be running. On a phone, use your computer's Wi-Fi IP, for example http://192.168.1.5:8000/. Connect both devices to the same Wi-Fi. On an emulator, use http://10.0.2.2:8000/.")
            .setView(input).setNegativeButton("Cancel", null).setPositiveButton("Save", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val url = input.text.toString().trim().toHttpUrlOrNull()
                if (url == null || url.query != null || url.fragment != null || url.username.isNotEmpty() || url.password.isNotEmpty()) {
                    input.error = "Enter an HTTP or HTTPS server address"
                } else {
                    val normalized = url.toString().trimEnd('/') + "/"
                    requestNumber++
                    currentCall?.cancel()
                    queue.cancelAll("search")
                    getSharedPreferences("connection", MODE_PRIVATE).edit().putString("server", normalized).apply()
                    adapter.show(emptyList())
                    binding.progress.visibility = View.GONE
                    binding.statusText.text = "Server saved. Enter a title and press Search."
                    dialog.dismiss()
                }
            }
        }
        dialog.show()
    }

    private fun showDetails(movie: Movie) {
        fun available(value: Any?) = value?.toString() ?: "Unavailable"
        fun names(value: List<String>) = value.joinToString(", ").ifEmpty { "Unavailable" }
        val details = """
            Year: ${available(movie.year)}
            Genre: ${names(movie.genres)}
            IMDb rating: ${available(movie.rating)}
            Votes: ${available(movie.votes)}
            Runtime: ${movie.runtime?.let { "$it minutes" } ?: "Unavailable"}

            Director: ${names(movie.directors)}

            Cast: ${names(movie.cast)}

            Description: ${movie.description ?: "Not supplied by the free IMDb dataset."}

            IMDb ID: ${movie.id}
        """.trimIndent()
        val text = TextView(this).apply {
            this.text = details
            textSize = 16f
            val padding = (24 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding / 2, padding, padding)
        }
        val scroll = ScrollView(this).apply { addView(text) }
        AlertDialog.Builder(this).setTitle(movie.title).setView(scroll).setPositiveButton("Close", null).show()
        Log.d("MovieSearch", "details id=${movie.id} title=${movie.title}")
    }

    override fun onDestroy() {
        requestNumber++
        currentCall?.cancel()
        queue.cancelAll("search")
        super.onDestroy()
    }
}
