package com.student.moviesearch

import android.os.SystemClock
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import android.view.accessibility.AccessibilityNodeInfo
import android.graphics.Bitmap
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SearchTest {
    @Test
    fun sameSearchAndDetailsWithBothMethods() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var first = ""
            for (method in listOf(R.id.retrofitRadio, R.id.volleyRadio)) {
                scenario.onActivity { activity ->
                    activity.findViewById<android.widget.RadioGroup>(R.id.methodGroup).check(method)
                    activity.findViewById<android.widget.EditText>(R.id.queryInput).setText("Orbit")
                    activity.findViewById<android.widget.Button>(R.id.searchButton).performClick()
                }
                val status = waitForSearch(scenario)
                assertTrue(status.contains("6 movies found"))
                assertTrue(status.contains("Sample data"))
                waitForPoster(scenario)
                saveScreenshot(if (method == R.id.retrofitRadio) "retrofit-results" else "volley-results")
                scenario.onActivity { activity ->
                    val list = activity.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.results)
                    assertEquals(2, (list.layoutManager as androidx.recyclerview.widget.GridLayoutManager).spanCount)
                    assertEquals(6, list.adapter!!.itemCount)
                    val title = list.getChildAt(0).findViewById<TextView>(R.id.title).text.toString()
                    if (first.isEmpty()) first = title else assertEquals(first, title)
                    list.getChildAt(0).findViewById<android.widget.ImageView>(R.id.poster).performClick()
                }
                saveScreenshot(if (method == R.id.retrofitRadio) "retrofit-details" else "volley-details")
                val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
                assertTrue(automation.rootInActiveWindow.findAccessibilityNodeInfosByText(first).any { it.text?.toString() == first })
                val close = automation.rootInActiveWindow.findAccessibilityNodeInfosByText("Close").first()
                assertTrue(close.performAction(AccessibilityNodeInfo.ACTION_CLICK))
                SystemClock.sleep(300)
            }
            scenario.onActivity { activity ->
                activity.findViewById<android.widget.EditText>(R.id.queryInput).setText("")
                activity.findViewById<android.widget.Button>(R.id.searchButton).performClick()
                assertEquals("Enter a movie title", activity.findViewById<android.widget.EditText>(R.id.queryInput).error.toString())
            }
            saveScreenshot("empty-query")
            scenario.onActivity { activity ->
                activity.findViewById<android.widget.EditText>(R.id.queryInput).setText("zzzz-no-movie-12345")
                activity.findViewById<android.widget.Button>(R.id.searchButton).performClick()
            }
            assertTrue(waitForSearch(scenario).contains("No movies found"))
            saveScreenshot("no-results")
            for (method in listOf(R.id.retrofitRadio, R.id.volleyRadio)) {
                scenario.onActivity { activity ->
                    activity.findViewById<android.widget.RadioGroup>(R.id.methodGroup).check(method)
                    activity.findViewById<android.widget.EditText>(R.id.queryInput).setText("x".repeat(201))
                    activity.findViewById<android.widget.Button>(R.id.searchButton).performClick()
                }
                assertTrue(waitForSearch(scenario).contains("Server error (400)"))
                saveScreenshot(if (method == R.id.retrofitRadio) "retrofit-error" else "volley-error")
            }
        }
    }

    private fun waitForPoster(scenario: ActivityScenario<MainActivity>) {
        val end = SystemClock.elapsedRealtime() + 15000
        while (SystemClock.elapsedRealtime() < end) {
            var loaded = false
            scenario.onActivity { activity ->
                val list = activity.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.results)
                loaded = list.getChildAt(0)?.findViewById<android.widget.ImageView>(R.id.poster)?.drawable is android.graphics.drawable.BitmapDrawable
            }
            if (loaded) return
            SystemClock.sleep(100)
        }
        throw AssertionError("Sample poster did not load")
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
