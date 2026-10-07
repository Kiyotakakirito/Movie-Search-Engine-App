# Movie Search Engine

A Kotlin Android student project that searches real movies directly through TMDB. It works on a physical phone using Wi-Fi or mobile data. A laptop server is not required in version 2.0.

## Open and run

Open this folder in Android Studio. Use JDK 17 or newer and Android SDK 35.

Get your own TMDB **API Read Access Token** from your TMDB account's API settings. Paste it into the app's **API settings**, or configure it locally before building:

```properties
tmdb.token=YOUR_READ_ACCESS_TOKEN
```

Add that line to `local.properties`, alongside Android Studio's `sdk.dir` entry. This file is ignored by Git and is excluded from the source ZIP. Credentials are sent in the Authorization header and are never logged. The API key is not required when using a read access token.

A local build may embed the configured token in its APK. This is suitable for this personal classroom demonstration, but it does not conceal a token from someone inspecting the APK. Keep that configured APK private. The GitHub workflow builds without a token; its APK prompts for one in API settings. A publicly distributed application should use a hosted backend when credentials need to remain private.

```sh
./gradlew assembleDebug lintDebug
```

Install the APK, enter a title such as `Inception`, choose Retrofit or Volley and press Search. Tap a poster to fetch the complete available details. Use **Load more** to retrieve subsequent result pages. **About** contains the required official TMDB logo and attribution notice.

## Data source

Version 2.0 uses [TMDB](https://www.themoviedb.org), not IMDb. This is a change from the original assignment's source requirement. Ratings and vote counts are labelled as TMDB values. An IMDb ID may be available in the TMDB details response, but this does not make the data IMDb-sourced.

The [TMDB API](https://developer.themoviedb.org/docs/getting-started) is free for non-commercial use with required attribution. The app includes: "This product uses the TMDB API but is not endorsed or certified by TMDB." The bundled logo was downloaded unchanged from the [official branding page](https://www.themoviedb.org/about/logos-attribution).

| Operation | HTTPS request | Fields used |
| --- | --- | --- |
| Movie search | `/3/search/movie?query=...&page=...&include_adult=false` | ID, title, release date, poster path, overview, average vote, vote count, total results/pages |
| Movie details | `/3/movie/{id}?append_to_response=credits` | Runtime, genres, description, IMDb ID, rating, votes, cast and director credits |
| Poster image | `https://image.tmdb.org/t/p/w342/{poster_path}` | Poster fetched using Coil |

Missing posters use a film placeholder. Missing fields show Unavailable. The details dialog remains scrollable for long descriptions and cast lists. Search results use a vertical LinearLayout root, horizontal search row, radio method selector and RecyclerView with a two-column GridLayoutManager.

Both search and details are implemented with both networking libraries. Network operations are asynchronous. New searches cancel the previous request, and request numbers prevent stale callbacks from replacing current results. Closing a details dialog cancels its request. Load more keeps the networking method used for the current search and stops at TMDB's supported 500-page limit.

## Volley and Retrofit comparison

| Point | Volley implementation | Retrofit implementation |
| --- | --- | --- |
| Requests | Build encoded URLs and use JsonObjectRequest | Annotated MovieApi methods with Query and Path parameters |
| Authentication | Override getHeaders to send the bearer token | Header parameter sends the same bearer token |
| Parsing | Gson converts JSONObject text into Kotlin models | Gson converter parses directly into the same models |
| Asynchronous execution | RequestQueue | Call.enqueue |
| Errors | Map Volley HTTP status/network failure to user messages | Map unsuccessful Response/onFailure to the same user messages |
| Cancellation | Request.cancel | Call.cancel |
| Caching | Disabled for search/details | No HTTP response cache configured |
| Adding endpoints | Requires another URL and request | Usually another interface method |

Both methods return the same results from TMDB. No speed advantage is claimed. Coil fetches poster images independently of the selected networking method.

## Demonstration and checks

Captured [screenshots and Logcat output](demo/README.md) show real TMDB searches through both methods and the details dialogs. Filter Logcat by `MovieSearch` to see query, selected method, endpoint, page, counts and errors. Authorization values are excluded.

The instrumentation test needs internet access and a locally configured read access token:

```sh
./gradlew connectedDebugAndroidTest
```

It compares the live Inception result count and first title between methods, checks the two-column grid and downloaded poster, loads details through both methods, checks director/cast/runtime/IMDb ID, checks empty/no-match handling, tests rejected-token errors with both libraries, verifies pagination and opens the attribution dialog. Counts and ratings are not hard-coded because TMDB can update them.

## Earlier IMDb implementation

The Python scripts under `backend/` preserve the earlier official IMDb dataset importer and sample HTTP server. The current Android app does not call them. To use that older implementation, check out the earlier Git commit and follow the [IMDb version instructions](docs/IMDB-version.md). Those instructions apply to version 1.1, rather than the current TMDB app.
