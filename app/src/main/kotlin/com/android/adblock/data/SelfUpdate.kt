package com.android.adblock.data

sealed interface SelfUpdate {
    data object Checking : SelfUpdate
    data object UpToDate : SelfUpdate
    data object CheckFailed : SelfUpdate
    data object Available : SelfUpdate
    data class Downloading(val progress: Float) : SelfUpdate
    data object Ready : SelfUpdate
    data object Installing : SelfUpdate
    data object Failed : SelfUpdate
}

data class AppUpdate(val automatic: Boolean = false, val status: SelfUpdate = SelfUpdate.Checking)
