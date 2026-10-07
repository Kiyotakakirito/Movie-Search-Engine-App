package com.student.moviesearch

import com.google.gson.annotations.SerializedName

data class Movie(
    val id: Int,
    val title: String,
    val year: Int?,
    val poster: String?,
    val description: String?,
    val rating: Double?,
    val votes: Int?,
    val runtime: Int? = null,
    val genres: List<String> = emptyList(),
    val directors: List<String> = emptyList(),
    val cast: List<String> = emptyList(),
    val imdbId: String? = null
)

data class SearchResponse(
    val results: List<TmdbMovie>?,
    @SerializedName("total_results") val total: Int?,
    @SerializedName("total_pages") val pages: Int?
)

data class TmdbMovie(
    val id: Int?,
    val title: String?,
    @SerializedName("release_date") val releaseDate: String?,
    @SerializedName("poster_path") val posterPath: String?,
    val overview: String?,
    @SerializedName("vote_average") val rating: Double?,
    @SerializedName("vote_count") val votes: Int?,
    val runtime: Int?,
    val genres: List<Genre>?,
    val credits: Credits?,
    @SerializedName("imdb_id") val imdbId: String?
) {
    fun toMovie(): Movie? {
        val movieId = id ?: return null
        val movieTitle = title?.takeIf { it.isNotBlank() } ?: return null
        return Movie(
            movieId, movieTitle, releaseDate?.take(4)?.toIntOrNull(),
            posterPath?.takeIf { it.startsWith("/") }?.let { "https://image.tmdb.org/t/p/w342$it" },
            overview?.takeIf { it.isNotBlank() },
            rating?.takeIf { (votes ?: 0) > 0 }, votes, runtime?.takeIf { it > 0 },
            genres.orEmpty().mapNotNull { it.name },
            credits?.crew.orEmpty().filter { it.job == "Director" }.mapNotNull { it.name }.distinct(),
            credits?.cast.orEmpty().mapNotNull { it.name }.distinct(), imdbId
        )
    }
}

data class Genre(val name: String?)
data class Credits(val cast: List<Person>?, val crew: List<Person>?)
data class Person(val name: String?, val job: String?)
