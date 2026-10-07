package com.student.moviesearch

import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import coil.load
import coil.decode.SvgDecoder

class MainActivity : AppCompatActivity() {
    private lateinit var binding: com.student.moviesearch.databinding.ActivityMainBinding
    private val requests by lazy { MovieRequests(this) }
    private val adapter = MovieAdapter { openDetails(it) }
    private var cancelSearch: (() -> Unit)? = null
    private var cancelDetails: (() -> Unit)? = null
    private var detailsDialog: AlertDialog? = null
    private var requestNumber = 0
    private var detailNumber = 0
    private var currentQuery = ""
    private var currentMethod = "Retrofit"
    private var currentPage = 0
    private var totalPages = 0
    private val movies = mutableListOf<Movie>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = com.student.moviesearch.databinding.ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.results.layoutManager = GridLayoutManager(this, 2)
        binding.results.adapter = adapter
        binding.searchButton.setOnClickListener { search() }
        binding.moreButton.setOnClickListener { search(true) }
        binding.apiButton.setOnClickListener { apiSettings() }
        binding.aboutButton.setOnClickListener { about() }
        binding.queryInput.setOnEditorActionListener { _, action, _ ->
            if (action == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) { search(); true } else false
        }
        if (token().isBlank()) binding.statusText.text = "Add your TMDB read access token in API settings."
    }

    private fun token() = getSharedPreferences("tmdb", MODE_PRIVATE)
        .getString("token", BuildConfig.TMDB_TOKEN) ?: BuildConfig.TMDB_TOKEN

    private fun selectedMethod() = if (binding.volleyRadio.isChecked) "Volley" else "Retrofit"

    private fun search(loadMore: Boolean = false) {
        val query = if (loadMore) currentQuery else binding.queryInput.text.toString().trim()
        if (query.isEmpty()) {
            binding.queryInput.error = "Enter a movie title"
            Log.d("MovieSearch", "Empty query rejected")
            return
        }
        if (token().isBlank()) {
            binding.statusText.text = "Add your TMDB read access token in API settings."
            apiSettings()
            return
        }
        binding.queryInput.error = null
        val number = ++requestNumber
        cancelSearch?.invoke()
        if (!loadMore) {
            currentQuery = query
            currentMethod = selectedMethod()
            currentPage = 0
            totalPages = 0
            movies.clear()
            adapter.show(emptyList())
        }
        val page = currentPage + 1
        val method = currentMethod
        Log.d("MovieSearch", "query=$query method=$method request=GET /3/search/movie page=$page")
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(binding.queryInput.windowToken, 0)
        binding.progress.visibility = View.VISIBLE
        binding.moreButton.visibility = View.GONE
        binding.statusText.text = "Searching with $method…"
        cancelSearch = requests.search(query, page, method, token(), { response ->
            if (number == requestNumber && !isDestroyed) {
                val found = response.results.orEmpty().mapNotNull { it.toMovie() }
                movies.addAll(found.filter { item -> movies.none { it.id == item.id } })
                currentPage = page
                totalPages = minOf(response.pages ?: 0, 500)
                adapter.show(movies.toList())
                binding.progress.visibility = View.GONE
                val total = response.total ?: movies.size
                binding.statusText.text = if (movies.isEmpty()) "No movies found. Try another title."
                    else "${movies.size} of $total movies · $method · TMDB"
                binding.moreButton.visibility = if (currentPage < totalPages) View.VISIBLE else View.GONE
                Log.d("MovieSearch", "method=$method response/result count=${found.size} total=$total page=$page")
            }
        }, { message ->
            if (number == requestNumber && !isDestroyed) {
                binding.progress.visibility = View.GONE
                binding.statusText.text = message
                binding.moreButton.visibility = if (currentPage < totalPages) View.VISIBLE else View.GONE
                Log.e("MovieSearch", "method=$method search error=$message")
            }
        })
    }

    private fun openDetails(movie: Movie) {
        detailsDialog?.dismiss()
        cancelDetails?.invoke()
        val number = ++detailNumber
        val method = selectedMethod()
        val text = TextView(this).apply {
            id = R.id.movieDetails
            this.text = "Loading movie details with $method…"
            textSize = 16f
            val padding = (24 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding / 2, padding, padding)
        }
        val dialog = AlertDialog.Builder(this).setTitle(movie.title)
            .setView(ScrollView(this).apply { addView(text) }).setPositiveButton("Close", null).create()
        detailsDialog = dialog
        dialog.setOnDismissListener {
            if (detailsDialog === dialog) {
                detailNumber++
                cancelDetails?.invoke()
                detailsDialog = null
            }
        }
        dialog.show()
        Log.d("MovieSearch", "method=$method details request=GET /3/movie/${movie.id}?append_to_response=credits")
        cancelDetails = requests.details(movie.id, method, token(), { response ->
            if (number == detailNumber && dialog.isShowing && !isDestroyed) {
                val details = response.toMovie()
                text.text = if (details != null) detailsText(details) else "Movie details unavailable."
                Log.d("MovieSearch", "method=$method details response id=${movie.id} cast count=${details?.cast?.size ?: 0}")
            }
        }, { message ->
            if (number == detailNumber && dialog.isShowing && !isDestroyed) {
                text.text = detailsText(movie) + "\n\nFull details could not be loaded: $message"
                Log.e("MovieSearch", "method=$method details error=$message")
            }
        })
    }

    private fun detailsText(movie: Movie): String {
        fun available(value: Any?) = value?.toString() ?: "Unavailable"
        fun names(value: List<String>) = value.joinToString(", ").ifEmpty { "Unavailable" }
        return """
            Year: ${available(movie.year)}
            Genre: ${names(movie.genres)}
            TMDB rating: ${movie.rating?.let { String.format(java.util.Locale.US, "%.1f / 10", it) } ?: "Unavailable"}
            TMDB votes: ${available(movie.votes)}
            Runtime: ${movie.runtime?.let { "$it minutes" } ?: "Unavailable"}

            Director: ${names(movie.directors)}

            Description: ${movie.description ?: "Unavailable"}

            Cast: ${names(movie.cast)}

            TMDB ID: ${movie.id}
            IMDb ID: ${available(movie.imdbId)}
        """.trimIndent()
    }

    private fun apiSettings() {
        val input = EditText(this).apply {
            id = R.id.apiToken
            hint = "API Read Access Token"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            setSingleLine()
        }
        val dialog = AlertDialog.Builder(this).setTitle("TMDB API access")
            .setMessage("${if (token().isBlank()) "No token configured." else "An access token is configured."} Paste a new API Read Access Token to replace it. Your app connects directly to TMDB over the internet.")
            .setView(input).setNegativeButton("Cancel", null).setPositiveButton("Save", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val value = input.text.toString().trim().removePrefix("Bearer ").trim()
                if (value.isEmpty() || value.any { it.isWhitespace() } || !value.matches(Regex("[A-Za-z0-9._-]+"))) {
                    input.error = "Paste a valid read access token"
                } else {
                    requestNumber++
                    cancelSearch?.invoke()
                    detailsDialog?.dismiss()
                    getSharedPreferences("tmdb", MODE_PRIVATE).edit().putString("token", value).apply()
                    binding.progress.visibility = View.GONE
                    binding.statusText.text = "Token saved. Enter a title and press Search."
                    dialog.dismiss()
                }
            }
        }
        dialog.show()
    }

    private fun about() {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val padding = (24 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
        }
        val logo = ImageView(this).apply {
            contentDescription = "The Movie Database logo"
            adjustViewBounds = true
            load("file:///android_asset/tmdb_logo.svg") { decoderFactory(SvgDecoder.Factory()) }
        }
        content.addView(logo, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (24 * resources.displayMetrics.density).toInt()))
        content.addView(TextView(this).apply {
            text = "This product uses the TMDB API but is not endorsed or certified by TMDB.\n\nMovie Search is a non-commercial student project using Volley and Retrofit. Ratings and votes come from TMDB.\n\nhttps://www.themoviedb.org"
            textSize = 16f
            setPadding(0, (20 * resources.displayMetrics.density).toInt(), 0, 0)
        })
        AlertDialog.Builder(this).setTitle("About Movie Search")
            .setView(content).setPositiveButton("Close", null).show()
    }

    override fun onDestroy() {
        requestNumber++
        detailNumber++
        cancelSearch?.invoke()
        cancelDetails?.invoke()
        detailsDialog?.dismiss()
        super.onDestroy()
    }
}
