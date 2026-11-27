package com.myna.player

import kotlinx.coroutines.flow.StateFlow

/** Where an in-app update is. */
sealed interface Update {
    data object Idle : Update
    data object Checking : Update
    data object UpToDate : Update
    data class Ready(val version: String, val notes: List<String>) : Update
    /** Android must first let MYNA install apps ("Install unknown apps"). */
    data class NeedsPermission(val version: String) : Update
    data class Downloading(val version: String, val percent: Int) : Update
    /** Handed to Android's installer, which asks the user to confirm. */
    data class Installing(val version: String) : Update
    data class Failed(val reason: String) : Update
}

/** Updates MYNA from its GitHub releases, like FireTheDJ does. */
interface Updater {
    val version: String
    val state: StateFlow<Update>
    fun check()
    fun install()
    fun allowInstalls()
    fun cancel()
}
