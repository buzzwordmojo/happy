package com.slopus.happy.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** One pinned folder: a computer (a [MachineChoice] id) and the folder's picker key there. */
@Immutable
data class Pin(val machineId: String, val key: String)

/**
 * The account's pinned folders, in the one order the person keeps them in:
 * each a directory as typed or reported, or `project:<id>` for a project
 * only the catalog places, on its computer. The new-session Project menu
 * offers a computer's pins first ([of]); the home list's Pinned section
 * shows every pin's project card in this order, which a long press drags.
 * Synced with the account's settings ([Settings.pinnedFolders]).
 */
@Immutable
data class PinnedFolders(val entries: List<Pin> = emptyList()) {
    /** A computer's pins, in order. */
    fun of(machineId: String?): List<String> = if (machineId == null) emptyList() else entries.filter { it.machineId == machineId }.map { it.key }

    /** The pins by computer, each computer's in order. */
    val byMachine: Map<String, List<String>> get() = entries.groupBy({ it.machineId }, { it.key })

    fun contains(machineId: String?, key: String): Boolean = entries.any { it.machineId == machineId && it.key == key }

    /** Pins [key] on [machineId] at the end, or unpins it if it already was. A blank key or computer changes nothing. */
    fun toggle(machineId: String, key: String): PinnedFolders {
        val folder = key.trim()
        if (machineId.isEmpty() || folder.isEmpty()) return this
        val pin = Pin(machineId, folder)
        return copy(entries = if (pin in entries) entries - pin else entries + pin)
    }

    /** [pin] moved to [index] among the pins (clamped); a pin that isn't there changes nothing. */
    fun moved(pin: Pin, index: Int): PinnedFolders {
        val from = entries.indexOf(pin)
        if (from < 0) return this
        val rest = entries - pin
        val to = index.coerceIn(0, rest.size)
        if (to == from) return this
        return copy(entries = rest.subList(0, to) + pin + rest.subList(to, rest.size))
    }

    /** The settings value: `[{machineId, path}]`, in order. */
    fun toSettings(): JsonArray = JsonArray(
        entries.map { JsonObject(mapOf("machineId" to JsonPrimitive(it.machineId), "path" to JsonPrimitive(it.key))) },
    )

    companion object {
        /** The account settings key ([Settings.pinnedFolders]). */
        const val SETTINGS_KEY = "pinnedMachinePaths"

        /**
         * A settings value read leniently: what isn't an array is none, and an
         * entry without a non-blank string `machineId` and `path` is dropped,
         * as is a repeat. It never fails, so it can't reset the other settings.
         */
        fun fromSettings(element: JsonElement?): PinnedFolders {
            val array = element as? JsonArray ?: return PinnedFolders()
            val entries = LinkedHashSet<Pin>()
            for (entry in array) {
                val o = entry as? JsonObject ?: continue
                val machineId = o["machineId"].nonBlankString() ?: continue
                val path = o["path"].nonBlankString() ?: continue
                entries += Pin(machineId, path)
            }
            return PinnedFolders(entries.toList())
        }

        private fun JsonElement?.nonBlankString(): String? =
            (this as? JsonPrimitive)?.takeIf { it.isString }?.content?.trim()?.takeIf(String::isNotEmpty)
    }
}

/**
 * The Project menu's rows with the pinned ones first, in the order they
 * were pinned, the rest in their own (most recent first) order. A pinned
 * directory no session has worked in lately still gets its row, named
 * relative to [homeDir]; a pinned catalog project gets one only when
 * [projectRow] can still name it.
 */
fun orderProjectOptions(
    options: List<PickerOption>,
    pins: List<String>,
    homeDir: String?,
    projectRow: (key: String) -> PickerOption? = { null },
): List<PickerOption> {
    if (pins.isEmpty()) return options
    val byKey = options.associateBy { it.key }
    val pinned = pins.mapNotNull { key ->
        val row = byKey[key] ?: if (readProjectPlaceKey(key) != null) {
            projectRow(key)
        } else {
            val relative = formatPathRelativeToHome(key, homeDir)
            // As resolveNewSessionOptions names a recent directory.
            PickerOption(key, relative, relative.takeUnless { it == key })
        }
        row?.copy(pinned = true)
    }
    val pinnedKeys = pinned.mapTo(HashSet()) { it.key }
    return pinned + options.filter { it.key !in pinnedKeys }
}
