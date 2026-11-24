package com.slopus.happy.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * The folders pinned in the new-session Project menu, per computer (a
 * [MachineChoice] id), each by its picker key: a directory as typed or
 * reported, or `project:<id>` for a project only the catalog places.
 * Synced with the account's settings ([Settings.pinnedFolders]), oldest
 * pin first, so the menu's order holds still while the recent folders
 * under it move.
 */
@Immutable
data class PinnedFolders(val byMachine: Map<String, List<String>> = emptyMap()) {
    fun of(machineId: String?): List<String> = machineId?.let { byMachine[it] }.orEmpty()

    fun contains(machineId: String?, key: String): Boolean = key in of(machineId)

    /** Pins [key] on [machineId], or unpins it if it already was. A blank key or computer changes nothing. */
    fun toggle(machineId: String, key: String): PinnedFolders {
        val folder = key.trim()
        if (machineId.isEmpty() || folder.isEmpty()) return this
        val current = of(machineId)
        val next = if (folder in current) current - folder else current + folder
        return copy(byMachine = if (next.isEmpty()) byMachine - machineId else byMachine + (machineId to next))
    }

    /** The settings value: `[{machineId, path}]`, computer by computer, oldest pin first. */
    fun toSettings(): JsonArray = JsonArray(
        byMachine.flatMap { (machineId, keys) ->
            keys.map { JsonObject(mapOf("machineId" to JsonPrimitive(machineId), "path" to JsonPrimitive(it))) }
        },
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
            val entries = element as? JsonArray ?: return PinnedFolders()
            val byMachine = LinkedHashMap<String, MutableList<String>>()
            for (entry in entries) {
                val o = entry as? JsonObject ?: continue
                val machineId = o["machineId"].nonBlankString() ?: continue
                val path = o["path"].nonBlankString() ?: continue
                val keys = byMachine.getOrPut(machineId) { mutableListOf() }
                if (path !in keys) keys += path
            }
            return PinnedFolders(byMachine)
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
