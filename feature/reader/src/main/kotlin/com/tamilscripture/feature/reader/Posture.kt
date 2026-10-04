package com.tamilscripture.feature.reader

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker

/** Heights in a tabletop posture (M2-4): from the hinge's top, and of the hinge itself. */
data class Tabletop(val belowHingeTop: Dp, val hinge: Dp)

/**
 * A foldable half open with a horizontal hinge, as on a table: the text stays above the
 * hinge and the controls go below it. Null in every other posture and on other devices.
 */
@Composable
fun rememberTabletop(): Tabletop? {
    val activity = LocalContext.current.findActivity() ?: return null
    val density = LocalDensity.current
    val flow = remember(activity) { WindowInfoTracker.getOrCreate(activity).windowLayoutInfo(activity) }
    val info by flow.collectAsState(initial = null)
    val fold = info?.displayFeatures?.filterIsInstance<FoldingFeature>()?.firstOrNull {
        it.state == FoldingFeature.State.HALF_OPENED && it.orientation == FoldingFeature.Orientation.HORIZONTAL
    } ?: return null
    val windowHeight = activity.window.decorView.height
    if (windowHeight <= 0) return null
    return with(density) { Tabletop((windowHeight - fold.bounds.top).toDp(), fold.bounds.height().toDp()) }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
