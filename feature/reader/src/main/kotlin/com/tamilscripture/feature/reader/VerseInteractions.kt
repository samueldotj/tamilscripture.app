package com.tamilscripture.feature.reader

import android.content.ClipData
import android.view.View
import androidx.compose.foundation.draganddrop.dragAndDropSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.DropdownMenu
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropTransferData
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpOffset
import com.tamilscripture.core.designsystem.theme.Ts

/**
 * What a verse offers on large screens (M2-7, M2-8): a right-click menu at the pointer,
 * and dragging the verse out as text into another app or window. Touch keeps tap to
 * select; a long press starts the drag.
 */
class VerseInteractions(
    val menu: @Composable ColumnScope.(verse: Int, dismiss: () -> Unit) -> Unit,
    val dragText: (verse: Int) -> String,
)

@Composable
fun VerseInteractionBox(
    verse: Int,
    interactions: VerseInteractions?,
    content: @Composable (Modifier) -> Unit,
) {
    if (interactions == null) {
        content(Modifier)
        return
    }
    var menuAt by remember { mutableStateOf<Offset?>(null) }
    var height by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    Box(Modifier.onSizeChanged { height = it.height }) {
        content(
            Modifier
                .pointerInput(verse) {
                    awaitPointerEventScope {
                        while (true) {
                            val e = awaitPointerEvent()
                            if (e.type == PointerEventType.Press && e.buttons.isSecondaryPressed) {
                                menuAt = e.changes.first().position
                                e.changes.forEach { it.consume() }
                            }
                        }
                    }
                }
                .dragAndDropSource { _ ->
                    DragAndDropTransferData(
                        ClipData.newPlainText("verse", interactions.dragText(verse)),
                        flags = View.DRAG_FLAG_GLOBAL,
                    )
                },
        )
        val at = menuAt
        DropdownMenu(
            expanded = at != null,
            onDismissRequest = { menuAt = null },
            // The menu opens below its anchor; move it up to the pointer.
            offset = with(density) { DpOffset((at?.x ?: 0f).toDp(), ((at?.y ?: 0f) - height).toDp()) },
            containerColor = Ts.colors.surface,
        ) {
            interactions.menu(this, verse) { menuAt = null }
        }
    }
}
