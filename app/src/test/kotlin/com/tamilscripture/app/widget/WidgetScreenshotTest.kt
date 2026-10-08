package com.tamilscripture.app.widget

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.action.Action
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.compose
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.action.actionStartActivity
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The Classical widgets at the design's two sizes, light and dark, from sample data. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "xxhdpi")
class WidgetScreenshotTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val tap: Action = actionStartActivity(Intent())

    private val ta = VerseOfDay(
        "இன்றைய வசனம்", "உம்முடைய வசனம் என் கால்களுக்குத் தீபமும், என் பாதைக்கு வெளிச்சமுமாக இருக்கிறது.", true,
        "சங்கீதம் 119:105", "IRV", "புதன், 7 அக்டோபர்", "அதிகாரத்தை வாசி", true,
    )
    private val en = VerseOfDay(
        "VERSE OF THE DAY", "Your word is a lamp to my feet and a light to my path.", false,
        "Psalm 119:105", "BSB", "Wednesday, 7 October", "Read the chapter", false,
    )

    private fun plan(done: Int) = PlanDay(
        "WHOLE BIBLE · 1 YEAR", 280, "of 365 days", 77, "$done of 3 read", if (done == 3) "Done for today" else "${(3 - done) * 4} min left",
        "A 14-day streak", "TODAY'S READING", "min",
        listOf("Isaiah 32–33" to 8, "Psalm 92" to 4, "Ephesians 4" to 4).mapIndexed { i, (n, m) -> Reading(n, m, i < done, tap) },
    )

    private fun shot(name: String, w: Int, h: Int, night: Boolean = false, content: @androidx.compose.runtime.Composable () -> Unit) {
        val widget = object : GlanceAppWidget() {
            override val sizeMode = androidx.glance.appwidget.SizeMode.Exact
            override suspend fun provideGlance(context: Context, id: GlanceId) = provideContent { content() }
        }
        val ctx = if (night) {
            val c = android.content.res.Configuration(context.resources.configuration)
            c.uiMode = (c.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK.inv()) or android.content.res.Configuration.UI_MODE_NIGHT_YES
            context.createConfigurationContext(c)
        } else context
        val views = runBlocking { widget.compose(ctx, size = DpSize(w.dp, h.dp)) }
        val density = ctx.resources.displayMetrics.density
        val frame = FrameLayout(ctx).apply { setBackgroundColor(if (night) Color.parseColor("#3A3D44") else Color.parseColor("#C9D3DC")) }
        val view: View = views.apply(ctx, frame)
        val pw = (w * density).toInt()
        val ph = (h * density).toInt()
        val pad = (12 * density).toInt()
        frame.setPadding(pad, pad, pad, pad)
        frame.addView(view, FrameLayout.LayoutParams(pw, ph))
        frame.measure(View.MeasureSpec.makeMeasureSpec(pw + 2 * pad, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(ph + 2 * pad, View.MeasureSpec.EXACTLY))
        frame.layout(0, 0, frame.measuredWidth, frame.measuredHeight)
        val bitmap = android.graphics.Bitmap.createBitmap(frame.width, frame.height, android.graphics.Bitmap.Config.ARGB_8888)
        frame.draw(android.graphics.Canvas(bitmap))
        bitmap.captureRoboImage("src/test/screenshots/$name.png")
    }

    @Test fun verseSmallTamil() = shot("widget_verse_small_ta", 170, 170) { VerseWidget(ta, tap, tap) }
    @Test fun verseMediumTamil() = shot("widget_verse_medium_ta", 364, 170) { VerseWidget(ta, tap, tap) }
    @Test fun verseSmallEnglish() = shot("widget_verse_small_en", 170, 170) { VerseWidget(en, tap, tap) }
    @Test fun verseMediumDark() = shot("widget_verse_medium_dark_en", 364, 170, night = true) { VerseWidget(en, tap, tap) }
    @Test fun planSmall() = shot("widget_plan_small_en", 170, 170) { PlanWidgetContent(plan(1), "" to "", tap) }
    @Test fun planMedium() = shot("widget_plan_medium_en", 364, 170) { PlanWidgetContent(plan(1), "" to "", tap) }
    @Test fun planMediumDoneDark() = shot("widget_plan_medium_done_dark_en", 364, 170, night = true) { PlanWidgetContent(plan(3), "" to "", tap) }
    @Test fun planEmpty() = shot("widget_plan_empty_en", 170, 170) { PlanWidgetContent(null, "No reading plan" to "Choose a plan ›", tap) }
}
