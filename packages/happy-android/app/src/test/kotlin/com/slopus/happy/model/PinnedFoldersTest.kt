package com.slopus.happy.model

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Project menu's pins: per computer, oldest first, toggled on and
 * off, written to the account's settings and read back, a damaged value read as none.
 */
class PinnedFoldersTest {
    @Test fun aStarTogglesOnAndOff() {
        val one = PinnedFolders().toggle("m1", "/a").toggle("m1", "/b")
        assertEquals(listOf("/a", "/b"), one.of("m1"))
        assertTrue(one.contains("m1", "/a"))
        assertFalse(one.contains("m2", "/a"))
        val off = one.toggle("m1", "/a")
        assertEquals(listOf("/b"), off.of("m1"))
        // The last pin gone, the computer goes with it.
        assertEquals(emptyMap<String, List<String>>(), off.toggle("m1", "/b").byMachine)
    }

    @Test fun blankKeysAndComputersChangeNothing() {
        val none = PinnedFolders()
        assertEquals(none, none.toggle("", "/a"))
        assertEquals(none, none.toggle("m1", "  "))
        assertEquals(emptyList<String>(), none.of(null))
    }

    @Test fun writtenToSettingsAndReadBack() {
        val pins = PinnedFolders().toggle("m1", "/a").toggle("m1", projectPlaceKey("p1")).toggle("m2", "~/b")
        assertEquals(
            """[{"machineId":"m1","path":"/a"},{"machineId":"m1","path":"project:p1"},{"machineId":"m2","path":"~/b"}]""",
            pins.toSettings().toString(),
        )
        assertEquals(pins, PinnedFolders.fromSettings(pins.toSettings()))
    }

    @Test fun aDamagedSettingIsReadAsNone() {
        assertEquals(PinnedFolders(), PinnedFolders.fromSettings(null))
        assertEquals(PinnedFolders(), PinnedFolders.fromSettings(JsonPrimitive("x")))
        assertEquals(PinnedFolders(), PinnedFolders.fromSettings(JsonObject(emptyMap())))
    }

    @Test fun orderingKeepsEveryRowOnce() {
        val rows = listOf(PickerOption("~", "~"), PickerOption("/h/x", "~/x", "~/x"), PickerOption("/h/y", "~/y", "~/y"))
        val ordered = orderProjectOptions(rows, listOf("/h/y", "/h/gone"), "/h")
        assertEquals(listOf("/h/y", "/h/gone", "~", "/h/x"), ordered.map { it.key })
        assertEquals(listOf(true, true, false, false), ordered.map { it.pinned })
        assertEquals("~/gone", ordered[1].name)
        // A pinned catalog project with nothing to name it by is left out.
        assertEquals(rows, orderProjectOptions(rows, listOf(projectPlaceKey("p9")), "/h"))
        assertEquals(rows, orderProjectOptions(rows, emptyList(), "/h"))
    }
}
