package com.slopus.happy.ui.home

import com.slopus.happy.model.Pin
import com.slopus.happy.model.PinnedFolders
import com.slopus.happy.model.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The home list's pins, kept in the account's settings with the Project
 * menu's (one [PinnedFolders]). A drag works on a [preview] of the order,
 * which the list draws in place of the stored one; only the drop writes
 * it, so a drag across five cards is one settings change, not five.
 */
class ProjectPins(
    private val settings: StateFlow<Settings>,
    private val change: ((Settings) -> Settings) -> Unit,
) {
    private val dragOrder = MutableStateFlow<PinnedFolders?>(null)

    /** The order mid-drag; null when nothing is held. */
    val preview: StateFlow<PinnedFolders?> = dragOrder.asStateFlow()

    private fun current(): PinnedFolders = dragOrder.value ?: settings.value.pinnedFolders

    /** A card's swipe action: pins the project at the end of the Pinned section, or unpins it. */
    fun toggle(pin: Pin) {
        val stored = settings.value.pinnedFolders.storedPin(pin) ?: pin
        change { it.copy(pinnedFolders = it.pinnedFolders.toggle(stored.machineId, stored.key)) }
    }

    /** A long press lifted a pinned card. */
    fun startDrag() {
        if (dragOrder.value == null) dragOrder.value = settings.value.pinnedFolders
    }

    /** The held card passed another: [pin] now sits at [index] in the preview. */
    fun dragTo(pin: Pin, index: Int) {
        val order = current()
        val stored = order.storedPin(pin) ?: return
        val next = order.moved(stored, index)
        if (next != order) dragOrder.value = next
    }

    /** The card dropped: the preview's order is written, if it changed, and the list goes back to the stored one. */
    fun endDrag() {
        val order = dragOrder.value ?: return
        if (order != settings.value.pinnedFolders) change { it.copy(pinnedFolders = order) }
        dragOrder.value = null
    }
}
