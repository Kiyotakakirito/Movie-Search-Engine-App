package com.student.moviesearch

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import okhttp3.HttpUrl.Companion.toHttpUrl
import com.student.moviesearch.databinding.ItemMovieBinding

class MovieAdapter(private val serverUrl: () -> String, private val onClick: (Movie) -> Unit) : RecyclerView.Adapter<MovieAdapter.MovieHolder>() {
    private var movies = emptyList<Movie>()

    fun show(items: List<Movie>) {
        movies = items
        notifyDataSetChanged()
    }

    class MovieHolder(val binding: ItemMovieBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = MovieHolder(
        ItemMovieBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun getItemCount() = movies.size

    override fun onBindViewHolder(holder: MovieHolder, position: Int) {
        val movie = movies[position]
        holder.binding.apply {
            title.text = movie.title
            year.text = movie.year?.toString() ?: "Year unavailable"
            poster.contentDescription = "Details for ${movie.title}"
            val posterUrl = movie.poster?.let { serverUrl().toHttpUrl().resolve(it)?.toString() }
            poster.load(posterUrl) {
                placeholder(R.drawable.poster_placeholder)
                error(R.drawable.poster_placeholder)
            }
            root.setOnClickListener { onClick(movie) }
            poster.setOnClickListener { onClick(movie) }
        }
    }
}
