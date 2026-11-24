package com.slopus.happy.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.slopus.happy.model.Pin
import com.slopus.happy.ui.components.Icon
import com.slopus.happy.ui.components.Ionicon
import com.slopus.happy.ui.theme.HappyTheme
import com.slopus.happy.ui.theme.happyTextStyle
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

object PinTags {
    fun action(projectId: String) = "pin.action.$projectId"
}

/** The pin swipe's spec: a 104 lane holding a rounded pill (8 inset), its glyph beside its word. */
object PinSwipeSpec {
    val lane = 104.dp
    val inset = 8.dp
    val pillHeight = 40.dp
    val icon = 16.dp
    val text = happyTextStyle(fontSize = 13, lineHeight = 18)
}

/**
 * A project card that swipes (#1707, Telegram's single action): dragging
 * left uncovers one rounded pill, a pin glyph beside "Pin" (or "Unpin"),
 * in the primary button's colors, and stops there. Releasing past half
 * way leaves it open; a tap on the pill pins and closes it.
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
) {
    val colors = HappyTheme.colors
    val lane = with(LocalDensity.current) { PinSwipeSpec.lane.toPx() }
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    Box(modifier = modifier.fillMaxWidth().clipToBounds()) {
        Box(
            modifier = Modifier.matchParentSize().drawWithContent { if (offset.value < 0f) drawContent() },
            contentAlignment = Alignment.CenterEnd,
        ) {
            Row(
                modifier = Modifier
                    .padding(end = PinSwipeSpec.inset)
                    .width(PinSwipeSpec.lane - PinSwipeSpec.inset * 2)
                    .height(PinSwipeSpec.pillHeight)
                    .clip(RoundedCornerShape(50))
                    .background(colors.button.primaryBackground)
                    .clickable(enabled = enabled, role = Role.Button) {
                        scope.launch { offset.animateTo(0f) }
                        onPin()
                    }
                    .semantics { contentDescription = label }
                    .testTag(actionTag),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(if (pinned) Ionicon.PinOutline else Ionicon.Pin, size = PinSwipeSpec.icon, color = colors.button.primaryTint)
                Text(label, style = PinSwipeSpec.text, color = colors.button.primaryTint, maxLines = 1, modifier = Modifier.padding(start = 6.dp))
            }
        }
        Box(
            modifier = Modifier
                .offset { IntOffset(offset.value.roundToInt(), 0) }
                .draggable(
                    enabled = enabled,
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        scope.launch { offset.snapTo((offset.value + delta).coerceIn(-lane, 0f)) }
                    },
                    onDragStopped = { velocity ->
                        val open = offset.value < -lane / 2 || velocity < -1000f
                        offset.animateTo(if (open) -lane else 0f)
                    },
                ),
            content = content,
        )
    }
}

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
