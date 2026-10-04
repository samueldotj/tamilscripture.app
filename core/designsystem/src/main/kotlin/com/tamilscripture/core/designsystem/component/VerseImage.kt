package com.tamilscripture.core.designsystem.component

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.res.ResourcesCompat
import com.tamilscripture.core.designsystem.R
import com.tamilscripture.core.designsystem.theme.LightTsColors

/**
 * A verse as a picture to share (roadmap M8-6): the app's paper and ink, the gold cross,
 * the verse in Mukta Malar shrunk until it fits, its reference and the site's address.
 * 1080 × 1350 (4:5), the shape messaging apps and social feeds show whole.
 */
object VerseImage {
    fun render(context: Context, text: String, reference: String, site: String = "tamilscripture.com"): Bitmap {
        val w = 1080
        val h = 1350
        val c = LightTsColors
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(c.bg.toArgb())
        val margin = 110f

        // The cross mark, as next to the app's name.
        val gold = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = c.accent.toArgb() }
        val cx = w / 2f
        val top = 120f
        canvas.drawRoundRect(RectF(cx - 9f, top, cx + 9f, top + 96f), 6f, 6f, gold)
        canvas.drawRoundRect(RectF(cx - 36f, top + 22f, cx + 36f, top + 40f), 6f, 6f, gold)

        val regular = ResourcesCompat.getFont(context, R.font.mukta_malar_regular)
        val semibold = ResourcesCompat.getFont(context, R.font.mukta_malar_semibold)
        val width = (w - 2 * margin).toInt()
        val available = h - 300f - 330f
        // The largest size from 66 px down at which the verse fits between the cross and the reference.
        var size = 66f
        var layout: StaticLayout
        while (true) {
            val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = c.ink.toArgb(); textSize = size; typeface = regular }
            layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setLineSpacing(0f, 1.32f)
                .build()
            if (layout.height <= available || size <= 30f) break
            size -= 2f
        }
        canvas.save()
        canvas.translate(margin, 300f + (available - layout.height) / 2f)
        layout.draw(canvas)
        canvas.restore()

        val refPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = c.accent.toArgb(); textSize = 44f; typeface = semibold; textAlign = Paint.Align.CENTER
        }
        canvas.drawText(reference, cx, h - 230f, refPaint)
        val sitePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = c.muted.toArgb(); textSize = 30f; typeface = regular; textAlign = Paint.Align.CENTER; letterSpacing = 0.06f
        }
        canvas.drawText(site, cx, h - 120f, sitePaint)
        return bitmap
    }
}
