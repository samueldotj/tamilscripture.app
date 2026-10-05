package com.tamilscripture.benchmark

import android.content.Intent
import android.net.Uri
import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.TraceSectionMetric
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val PACKAGE = "com.tamilscripture.app"

/**
 * John 3 through a website link, as a reader arrives from a shared verse. Launches the
 * app afresh: MainActivity is singleTask, so a second launch only delivers onNewIntent.
 */
private fun MacrobenchmarkScope.openJohn3() {
    killProcess()
    startActivityAndWait(
        // Straight to MainActivity: LinkActivity is a trampoline that never draws a frame.
        Intent(Intent.ACTION_VIEW, Uri.parse("https://www.tamilscripture.com/irvtam/john/3")).setClassName(PACKAGE, "$PACKAGE.MainActivity"),
    )
    device.wait(Until.hasObject(By.textContains("நிக்கொதேமு")), 10_000)
}

/** Swipes to the next chapter [times] times, waiting for each to paint. */
private fun MacrobenchmarkScope.swipeChapters(times: Int) {
    val w = device.displayWidth
    val h = device.displayHeight
    repeat(times) {
        device.swipe(w * 4 / 5, h / 2, w / 5, h / 2, 12)
        device.waitForIdle()
    }
}

/** NF-2: cold start to the home screen, with and without the Baseline Profile. */
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {
    @get:Rule val rule = MacrobenchmarkRule()

    private fun startup(mode: CompilationMode) = rule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        iterations = 8,
        startupMode = StartupMode.COLD,
        compilationMode = mode,
    ) {
        pressHome()
        startActivityAndWait()
    }

    @Test fun coldNoProfile() = startup(CompilationMode.None())

    @Test fun coldWithProfile() = startup(CompilationMode.Partial(BaselineProfileMode.Require))
}

/** Frame timing while swiping through chapters, the reader's main gesture. */
@RunWith(AndroidJUnit4::class)
class ChapterSwipeBenchmark {
    @get:Rule val rule = MacrobenchmarkRule()

    @Test fun swipeFiveChapters() = rule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        iterations = 5,
        startupMode = StartupMode.WARM,
        compilationMode = CompilationMode.Partial(BaselineProfileMode.UseIfAvailable),
        setupBlock = { openJohn3() },
    ) {
        swipeChapters(5)
    }
}

/**
 * Writes the Baseline Profile for the critical paths: start, open a chapter from a link,
 * swipe chapters. Copy the result from the device output into
 * app/src/main/baseline-prof.txt (no Gradle plugin, so AGP 9 needs nothing special).
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule val rule = BaselineProfileRule()

    @Test fun generate() = rule.collect(packageName = PACKAGE, includeInStartupProfile = true) {
        killProcess()
        startActivityAndWait()
        openJohn3()
        swipeChapters(3)
    }
}

/**
 * M3-12: on-device search (the FTS5 query in the downloaded Bible), Tamil words, a phrase and
 * romanised input, as people search on the website. Needs the IRV pack on the device.
 */
@OptIn(ExperimentalMetricApi::class)
@RunWith(AndroidJUnit4::class)
class SearchBenchmark {
    @get:Rule val rule = MacrobenchmarkRule()

    private fun search(query: String) = rule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(TraceSectionMetric("pack-search", TraceSectionMetric.Mode.Sum)),
        iterations = 5,
        startupMode = StartupMode.WARM,
        compilationMode = CompilationMode.Partial(BaselineProfileMode.UseIfAvailable),
    ) {
        startActivityAndWait(
            Intent(Intent.ACTION_VIEW, Uri.parse("https://www.tamilscripture.com/search?q=" + Uri.encode(query))).setClassName(PACKAGE, "$PACKAGE.MainActivity"),
        )
        device.wait(Until.hasObject(By.textContains("1")), 5_000)
        device.waitForIdle()
    }

    @Test fun tamilWord() = search("அன்பு")

    @Test fun tamilPhrase() = search("\"தேவனுடைய ராஜ்யம்\"")

    @Test fun romanised() = search("visuvaasam")
}
