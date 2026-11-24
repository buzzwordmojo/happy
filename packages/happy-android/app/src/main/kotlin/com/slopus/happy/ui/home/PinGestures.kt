package com.slopus.happy.ui.home

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.slopus.happy.model.Pin
import com.slopus.happy.ui.components.Ionicon
import kotlin.math.abs

object PinTags {
    fun action(projectId: String) = "pin.action.$projectId"
}

/** The pin swipe's colour: iOS's own pin action (vault history) is tileOrange, #FF9500. */
val PinActionColor = Color(0xFFFF9500)

/**
 * A project card that swipes (#1707) the way a chat swipes to archive
 * ([SwipeAction]): dragging left uncovers one orange block, a pin glyph
 * over "Pin" (or "Unpin"); a tap on it pins and closes it.
 */
@Composable
internal fun SwipeToPin(
    enabled: Boolean,
    pinned: Boolean,
    label: String,
    actionTag: String,
    onPin: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) = SwipeAction(
    enabled = enabled,
    color = PinActionColor,
    icon = if (pinned) Ionicon.PinOutline else Ionicon.Pin,
    label = label,
    actionTag = actionTag,
    onAction = onPin,
    modifier = modifier,
    content = content,
)

/**
 * The Pinned section's drag (#1707): a long press lifts a pinned card,
 * which follows the finger over the others; passing the middle of another
 * pinned card moves it to that card's place in the order ([HomeListCallbacks.onPinDragTo],
 * a preview until the drop). The lifted card is drawn where the finger
 * holds it, whatever slot the list has moved it to, so it never jumps.
 */
@Stable
class PinDrag(private val listState: LazyListState, private val callbacks: HomeListCallbacks) {
    /** The pinned cards' list keys and pins, top to bottom; the list sets it as it composes. */
    var order by mutableStateOf<List<Pair<String, Pin>>>(emptyList())

    var heldKey by mutableStateOf<String?>(null)
        private set
    private var heldPin: Pin? = null
    private var startTop = 0f
    private var travel by mutableFloatStateOf(0f)
    private var distance = 0f

    fun isPinnedCard(key: Any): Boolean = order.any { it.first == key }

    private fun top(key: String): Float? = listState.layoutInfo.visibleItemsInfo.find { it.key == key }?.offset?.toFloat()

    fun start(key: String, pin: Pin) {
        heldKey = key
        heldPin = pin
        startTop = top(key) ?: 0f
        travel = 0f
        distance = 0f
        callbacks.onPinDragStart()
    }

    fun drag(dy: Float) {
        val key = heldKey ?: return
        val pin = heldPin ?: return
        travel += dy
        distance += abs(dy)
        val items = listState.layoutInfo.visibleItemsInfo
        val held = items.find { it.key == key } ?: return
        val center = startTop + travel + held.size / 2f
        val target = items.firstOrNull { it.key != key && isPinnedCard(it.key) && center > it.offset && center < it.offset + it.size } ?: return
        val index = order.indexOfFirst { it.first == target.key }
        if (index >= 0) callbacks.onPinDragTo(pin, index)
    }

    /** The card was let go; true when it moved further than [slop], false for a long press held still. */
    fun end(slop: Float): Boolean {
        val moved = distance > slop
        heldKey = null
        heldPin = null
        travel = 0f
        callbacks.onPinDragEnd()
        return moved
    }

    /** How far the held card is drawn from its slot: where the finger has it, less where the list put it. */
    fun translation(key: String): Float = (startTop + travel) - (top(key) ?: startTop)
}

val LocalPinDrag = staticCompositionLocalOf<PinDrag?> { null }

/** A pinned card's place in the list: lifted above the rest while held, sliding to its new slot otherwise. */
@Composable
fun LazyItemScope.pinnedCardItem(key: String): Modifier {
    val drag = LocalPinDrag.current ?: return Modifier
    if (!drag.isPinnedCard(key)) return Modifier
    return if (drag.heldKey == key) {
        Modifier.zIndex(1f).graphicsLayer {
            translationY = drag.translation(key)
            shadowElevation = 12.dp.toPx()
            scaleX = 1.02f
            scaleY = 1.02f
        }
    } else {
        Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null)
    }
}
