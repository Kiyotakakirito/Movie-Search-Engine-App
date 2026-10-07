# Demonstration

These screenshots were captured from the running Android app on the Pixel_10 emulator using the local sample HTTP server. The six movies, posters and metadata are fictional demonstration data. Both methods send HTTP requests to the same search endpoint.

| Retrofit | Volley |
| --- | --- |
| ![Retrofit results](retrofit-results.png) | ![Volley results](volley-results.png) |
| ![Retrofit details](retrofit-details.png) | ![Volley details](volley-details.png) |

The main screen uses the required vertical LinearLayout, horizontal search row, radio method selector and a RecyclerView with two columns. Tapping a poster opens the details dialog.

![No matching results](no-results.png)

| Empty query | API error |
| --- | --- |
| ![Empty query](empty-query.png) | ![Volley API error](volley-error.png) |

Verified on 7 October 2026: `assembleDebug`, `lintDebug`, the Android instrumentation test and the backend unittest passed. Android lint reports warnings for classroom-project choices such as English strings in layouts and older dependency versions, with no errors. The full official IMDb dataset download was not completed during this demonstration; importer checks used compressed TSV fixtures. Real IMDb mode requires the import described in the project README.

Actual Logcat output is saved in `logcat.txt`. The project README contains setup commands, the data-source investigation and the Volley/Retrofit comparison.
