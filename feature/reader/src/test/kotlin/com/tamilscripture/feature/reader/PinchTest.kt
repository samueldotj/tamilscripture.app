package com.tamilscripture.feature.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Two fingers resize the text; one finger still scrolls. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class PinchTest {
    @get:Rule val compose = createComposeRule()

    private var size = 21
    private val live = mutableListOf<Int?>()
    private val scroll = androidx.compose.foundation.ScrollState(0)

    private fun content() = compose.setContent {
        Box(Modifier.size(400.dp).testTag("reader").pinchFontSize({ size }, onLive = { live += it }, onDone = { size = it })) {
            Box(Modifier.size(400.dp).verticalScroll(scroll)) { Box(Modifier.size(400.dp, 2000.dp)) }
        }
    }

    @Test fun spreadingEnlargesPinchingShrinks() {
        content()
        compose.onNodeWithTag("reader").performTouchInput {
            pinch(Offset(180f, 300f), Offset(120f, 300f), Offset(220f, 300f), Offset(20f, 300f))
        }
        compose.waitForIdle()
        assertTrue("grew to $size", size > 21)
        assertEquals(null, live.last())
        val bigger = size
        compose.onNodeWithTag("reader").performTouchInput {
            pinch(Offset(100f, 300f), Offset(200f, 300f), Offset(300f, 300f), Offset(220f, 300f))
        }
        compose.waitForIdle()
        assertTrue("shrank to $size", size < bigger)
        assertTrue(size in 15..30)
        assertEquals(0, scroll.value)
    }

    @Test fun oneFingerStillScrolls() {
        content()
        compose.onNodeWithTag("reader").performTouchInput { swipeUp() }
        compose.waitForIdle()
        assertTrue(scroll.value > 0)
        assertEquals(21, size)
        assertTrue(live.isEmpty())
    }
}
