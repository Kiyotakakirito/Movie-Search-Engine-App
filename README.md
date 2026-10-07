# Movie Search Engine

A Kotlin Android project with a simple movie search screen. Choose Retrofit or Volley, enter a title and press Search. Results use a two-column RecyclerView. Tap a poster to open the details dialog.

## Run the sample demonstration

Open this folder in Android Studio and let Gradle sync. Use JDK 17 or newer and Android SDK 35.

From a terminal in this folder:

```sh
python backend/create_sample.py
python backend/server.py --demo
```

Run the app on an Android emulator and search for `Orbit`. There are six fictional sample movies with generated sample posters. The results explicitly say **Sample data**. These records are only for demonstrating the screen and HTTP networking; they are not IMDb search results.

The emulator connects to the host computer at `http://10.0.2.2:8000/`. A real phone connected with USB can use `adb reverse tcp:8000 tcp:8000` and a build with `-PmovieServer=http://127.0.0.1:8000/`. Alternatively use your computer's LAN address. The server must keep running while the app is used. Local HTTP is enabled for this classroom demonstration; a hosted version should use HTTPS.

Version 1.1 adds **Server settings** in the app. You can save the address without rebuilding. On a physical phone, connect the phone and computer to the same Wi-Fi, start the Python server, then enter `http://YOUR_COMPUTER_IP:8000/`. Run `ipconfig` on Windows to find the Wi-Fi IPv4 address. Allow the Python server through your firewall on your private network if prompted. For USB forwarding, save `http://127.0.0.1:8000/` after running the adb reverse command. Installing the APK alone does not start the server or import the IMDb database. Sample mode matches the fictional `Orbit` movies; real titles require real-data mode.

## Use the real IMDb data

The supplied [data.imdb.com](https://data.imdb.com/) site describes commercial metadata. Its [non-commercial dataset documentation](https://data.imdb.com/non-commercial-datasets/) links the public UTF-8 TSV files at [datasets.imdbws.com](https://datasets.imdbws.com/). The files are gzip compressed and use `\N` for missing values. There is no free title-search JSON endpoint on the supplied site.

```sh
python backend/import_data.py
python backend/server.py
```

The importer streams the official files into SQLite and publishes the database when the import completes. Downloads and imports can take a long time and require several GB of disk space. `--skip-credits` imports titles and ratings only for a quicker setup. Run the importer before starting the server. The database and downloaded datasets are excluded from Git.

| Data file | Fields used |
| --- | --- |
| title.basics | IMDb ID, primary/original title, release year, runtime, genres |
| title.ratings | Average rating, vote count |
| title.crew | Director IDs |
| title.principals | Principal cast IDs and ordering |
| name.basics | Names for the director and cast IDs |

Only non-adult feature movies are indexed. A title search matches the primary or original title, ignores case and treats `%` and `_` literally. Exact matches appear first, then titles with more votes. At most 40 results are returned and the app shows the total when there are more. Cast comes from principal credits and is not an exhaustive cast list.

Posters and plots are absent from the free IMDb datasets. The app displays a film placeholder and states when a description is unavailable. For optional poster/plot enrichment, set `OMDB_API_KEY` in the server environment before starting the real-data server. IMDb stays the search source; the optional [OMDb API](https://www.omdbapi.com/) looks up extra fields by IMDb ID. Never commit an API key. The sample mode requires no key.

## Networking and response

Both implementations call `GET /search?q=...` on the local dataset server. Retrofit uses an annotated interface, `Call.enqueue` and Gson conversion. Volley uses `JsonObjectRequest` and converts its JSON response to the same Kotlin data classes with Gson. Both run requests asynchronously and deliver callbacks on the main thread. New searches cancel old requests, and an incrementing request number prevents a stale response from replacing a newer search.

The server returns `movies`, `total`, `limit` and `source`. Each movie contains `id`, `title`, `year`, `genres`, `rating`, `votes`, `runtime`, `directors`, `cast`, `poster` and `description`. Missing scalar fields are JSON null; missing lists are empty. An empty query returns HTTP 400, a missing database returns 503 and an unknown route returns 404.

## Volley and Retrofit comparison

| Point | Volley in this project | Retrofit in this project |
| --- | --- | --- |
| Request | Build an encoded URL and create a JsonObjectRequest | Call the annotated search function |
| Parsing | Explicit Gson conversion from JSONObject | Gson converter produces SearchResponse |
| Errors | VolleyError and optional HTTP status | HTTP Response code or onFailure |
| Cancellation | Cancel requests with the search tag | Cancel the active Call |
| Caching | Disabled so demonstrations reach the server | No response cache configured |
| Experience | Convenient for a small individual JSON request | Less repetitive when adding more typed endpoints |

Both produced the same results for the same endpoint. This project does not make a speed claim: the dataset query, connection and server workload affect timing more than this small difference in client code. Coil loads images independently of the selected search networking library.

## Demonstration

See the captured [screenshots and Logcat evidence](demo/README.md).

1. Start the sample server, launch the app and search `Orbit` with Retrofit selected.
2. Show the two-column grid and tap a poster. Show year, genre, rating, votes, runtime, director, cast and description, then close the dialog.
3. Select Volley and repeat the same query. The same six results should appear.
4. Try an empty title and an unmatched title. Stop the server and search again to show the error state.
5. In Android Studio Logcat filter by `MovieSearch`, or run `adb logcat -s MovieSearch:D`. Logs include query, method, request URL, result count, selected details and errors.

The instrumentation test runs the sample search using both methods, compares the first result and count, checks the grid column count and downloaded poster, opens both dialogs and checks empty/no-match handling. It also checks HTTP 400 error handling with both libraries. Start the sample server and an emulator before running:

```sh
./gradlew assembleDebug lintDebug connectedDebugAndroidTest
```

Backend parsing and search checks can be run with `python -m unittest discover -s backend -v`. They use tiny TSV fixtures to test the importer and query behavior; they do not replace the production IMDb datasets.

The GitHub workflow builds and runs lint, then uploads the debug APK. It does not claim to run emulator tests or download the IMDb datasets.
