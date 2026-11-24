package com.slopus.happy.ui.home

import com.slopus.happy.model.Pin
import com.slopus.happy.model.PinnedFolders
import com.slopus.happy.model.Settings
import com.slopus.happy.model.projectPlaceKey
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The home list's pins: a swipe toggles one; a drag previews its order and writes it once, on the drop. */
class ProjectPinsTest {
    private val settings = MutableStateFlow(Settings.Defaults)
    private var writes = 0
    private val pins = ProjectPins(settings) { transform ->
        writes++
        settings.value = transform(settings.value)
    }

    private fun stored() = settings.value.pinnedFolders.entries

    @Test fun aSwipePinsAtTheEndAndASecondUnpins() {
        pins.toggle(Pin("m1", "/a"))
        pins.toggle(Pin("m2", "/b"))
        assertEquals(listOf(Pin("m1", "/a"), Pin("m2", "/b")), stored())
        pins.toggle(Pin("m1", "/a"))
        assertEquals(listOf(Pin("m2", "/b")), stored())
    }

    @Test fun aRigProjectUnpinsWhereverItWasPinned() {
        settings.value = settings.value.copy(pinnedFolders = PinnedFolders().toggle("m-cli", projectPlaceKey("p1")))
        // The home list names it by the Rig machine its chats run on.
        pins.toggle(Pin("m-rig", projectPlaceKey("p1")))
        assertEquals(emptyList<Pin>(), stored())
    }

    @Test fun aDragPreviewsAndWritesOnceOnTheDrop() {
        settings.value = settings.value.copy(pinnedFolders = PinnedFolders().toggle("m", "/a").toggle("m", "/b").toggle("m", "/c"))
        writes = 0
        pins.startDrag()
        pins.dragTo(Pin("m", "/c"), 1)
        pins.dragTo(Pin("m", "/c"), 0)
        assertEquals(listOf(Pin("m", "/c"), Pin("m", "/a"), Pin("m", "/b")), pins.preview.value?.entries)
        assertEquals("nothing written mid-drag", 0, writes)
        pins.endDrag()
        assertNull(pins.preview.value)
        assertEquals(listOf(Pin("m", "/c"), Pin("m", "/a"), Pin("m", "/b")), stored())
        assertEquals(1, writes)
    }

    @Test fun aLongPressHeldStillWritesNothing() {
        settings.value = settings.value.copy(pinnedFolders = PinnedFolders().toggle("m", "/a"))
        writes = 0
        pins.startDrag()
        pins.endDrag()
        assertEquals(0, writes)
        assertNull(pins.preview.value)
    }
}
