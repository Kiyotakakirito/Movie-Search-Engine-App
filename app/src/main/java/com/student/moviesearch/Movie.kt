package com.student.moviesearch

data class Movie(
    val id: String,
    val title: String,
    val year: Int?,
    val genres: List<String> = emptyList(),
    val rating: Double?,
    val votes: Int?,
    val runtime: Int?,
    val directors: List<String> = emptyList(),
    val cast: List<String> = emptyList(),
    val poster: String?,
    val description: String?
)

data class SearchResponse(val movies: List<Movie>, val total: Int, val limit: Int, val source: String = "IMDb dataset")
