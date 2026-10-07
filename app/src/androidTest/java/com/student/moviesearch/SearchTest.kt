package com.student.moviesearch

import android.os.SystemClock
import android.graphics.Bitmap
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SearchTest {
    @Test
    fun liveSearchAndDetailsWithBothMethods() {
        assertTrue("Configure tmdb.token in local.properties for live API tests", BuildConfig.TMDB_TOKEN.isNotBlank())
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.serviceInfo = automation.serviceInfo.apply {
            flags = flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var firstTitle = ""
            var firstCount = 0
            for (method in listOf(R.id.retrofitRadio, R.id.volleyRadio)) {
                runSearch(scenario, method, "Inception")
                val status = waitForSearch(scenario)
                assertTrue(status.contains("TMDB"))
                assertTrue(status.contains("movies"))
                waitForPoster(scenario)
                saveScreenshot(if (method == R.id.retrofitRadio) "retrofit-results" else "volley-results")
                scenario.onActivity { activity ->
                    val list = activity.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.results)
                    assertEquals(2, (list.layoutManager as androidx.recyclerview.widget.GridLayoutManager).spanCount)
                    val count = list.adapter!!.itemCount
                    assertTrue(count > 0)
                    val title = list.getChildAt(0).findViewById<TextView>(R.id.title).text.toString()
                    if (firstTitle.isEmpty()) { firstTitle = title; firstCount = count }
                    else { assertEquals(firstTitle, title); assertEquals(firstCount, count) }
                    list.getChildAt(0).findViewById<android.widget.ImageView>(R.id.poster).performClick()
                }
                val detail = waitForDetails()
                assertTrue(detail.contains("TMDB rating:"))
                assertTrue(detail.contains("Director: Christopher Nolan"))
                assertTrue(detail.contains("Leonardo DiCaprio"))
                assertTrue(detail.contains("148 minutes"))
                assertTrue(detail.contains("tt1375666"))
                saveScreenshot(if (method == R.id.retrofitRadio) "retrofit-details" else "volley-details")
                closeDialog()
            }
            scenario.onActivity { activity ->
                activity.findViewById<android.widget.EditText>(R.id.queryInput).setText("")
                activity.findViewById<android.widget.Button>(R.id.searchButton).performClick()
                assertEquals("Enter a movie title", activity.findViewById<android.widget.EditText>(R.id.queryInput).error.toString())
            }
            saveScreenshot("empty-query")
            runSearch(scenario, R.id.volleyRadio, "zzzz-no-movie-987654321-qaz")
            assertTrue(waitForSearch(scenario).contains("No movies found"))
            saveScreenshot("no-results")
            for (method in listOf(R.id.retrofitRadio, R.id.volleyRadio)) {
                scenario.onActivity { it.getSharedPreferences("tmdb", android.content.Context.MODE_PRIVATE).edit().putString("token", "invalid-test-token").apply() }
                runSearch(scenario, method, "Inception")
                assertTrue(waitForSearch(scenario).contains("rejected the access token"))
                saveScreenshot(if (method == R.id.retrofitRadio) "retrofit-error" else "volley-error")
            }
            scenario.onActivity { it.getSharedPreferences("tmdb", android.content.Context.MODE_PRIVATE).edit().remove("token").apply() }
            runSearch(scenario, R.id.retrofitRadio, "Batman")
            assertTrue(waitForSearch(scenario).contains("movies"))
            var previous = 0
            scenario.onActivity { activity ->
                previous = activity.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.results).adapter!!.itemCount
                activity.findViewById<android.widget.Button>(R.id.moreButton).performClick()
            }
            waitForSearch(scenario)
            scenario.onActivity { activity -> assertTrue(activity.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.results).adapter!!.itemCount > previous) }
            scenario.onActivity { it.findViewById<android.widget.Button>(R.id.aboutButton).performClick() }
            SystemClock.sleep(1000)
            assertTrue(automation.rootInActiveWindow.findAccessibilityNodeInfosByText("This product uses the TMDB API").isNotEmpty())
            saveScreenshot("about")
            closeDialog()
        }
    }

    private fun runSearch(scenario: ActivityScenario<MainActivity>, method: Int, query: String) {
        scenario.onActivity { activity ->
            activity.findViewById<android.widget.RadioGroup>(R.id.methodGroup).check(method)
            activity.findViewById<android.widget.EditText>(R.id.queryInput).setText(query)
            activity.findViewById<android.widget.Button>(R.id.searchButton).performClick()
        }
    }

    private fun waitForDetails(): String {
        val end = SystemClock.elapsedRealtime() + 35000
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        while (SystemClock.elapsedRealtime() < end) {
            val nodes = automation.rootInActiveWindow?.findAccessibilityNodeInfosByViewId("com.student.moviesearch:id/movieDetails").orEmpty()
            val text = nodes.firstOrNull()?.text?.toString().orEmpty()
            if (text.isNotEmpty() && !text.startsWith("Loading")) return text
            SystemClock.sleep(100)
        }
        throw AssertionError("Details did not complete")
    }

    private fun closeDialog() {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val close = automation.rootInActiveWindow.findAccessibilityNodeInfosByText("Close").first()
        assertTrue(close.performAction(AccessibilityNodeInfo.ACTION_CLICK))
        SystemClock.sleep(300)
    }

    private fun waitForPoster(scenario: ActivityScenario<MainActivity>) {
        val end = SystemClock.elapsedRealtime() + 20000
        while (SystemClock.elapsedRealtime() < end) {
            var loaded = false
            scenario.onActivity { activity ->
                val list = activity.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.results)
                loaded = list.getChildAt(0)?.findViewById<android.widget.ImageView>(R.id.poster)?.drawable is android.graphics.drawable.BitmapDrawable
            }
            if (loaded) return
            SystemClock.sleep(100)
        }
        throw AssertionError("Movie poster did not load")
    }

    private fun saveScreenshot(name: String) {
        SystemClock.sleep(600)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val file = File(instrumentation.targetContext.getExternalFilesDir(null), "$name.png")
        file.outputStream().use { instrumentation.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, it) }
        instrumentation.uiAutomation.executeShellCommand("cp ${file.absolutePath} /sdcard/Download/$name.png").use {
            android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes()
        }
    }

    private fun waitForSearch(scenario: ActivityScenario<MainActivity>): String {
        val end = SystemClock.elapsedRealtime() + 35000
        while (SystemClock.elapsedRealtime() < end) {
            var status = ""
            scenario.onActivity { status = it.findViewById<TextView>(R.id.statusText).text.toString() }
            if (!status.startsWith("Searching")) return status
            SystemClock.sleep(100)
        }
        throw AssertionError("Search did not complete")
    }
}
